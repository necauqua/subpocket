/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocket.StackSizeMode;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.impl.SubpocketStackImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.BufferUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.blaze3d.platform.GLX.*;
import static com.mojang.blaze3d.platform.GlStateManager.LogicOp.XOR;
import static dev.necauqua.mods.subpocket.SubpocketContainer.HEIGHT;
import static dev.necauqua.mods.subpocket.SubpocketContainer.WIDTH;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static java.lang.String.format;
import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

@OnlyIn(Dist.CLIENT)
public final class SubpocketScreen extends ContainerScreen<SubpocketContainer> {

    private static final ResourceLocation TEXTURE = ns("textures/gui/subpocket.png");

    private static final int X_OFF = 35, Y_OFF = 8;

    private static final Framebuffer framebuffer = new Framebuffer(WIDTH, HEIGHT, true, Minecraft.IS_RUNNING_ON_MAC);
    private static final FloatBuffer inputColor = BufferUtils.createFloatBuffer(4);
    private static final ByteBuffer outputColor = BufferUtils.createByteBuffer(4);
    private static final int ARMOR_OFFSET = 14;

    private StackSizeMode stackSizeMode;

    private final SubpocketContainer container;
    private final ISubpocket storage;

    private float localX = 0, localY = 0;
    private float scale = 1;
    private boolean mouseInside = false;
    private boolean usePixelPicking = isUsingFBOs() && !Config.Client.disablePixelPicking;

    private ISubpocketStack underMouse = SubpocketStackImpl.EMPTY;
    private int underMouseIndex = -1;

    private boolean debug = false;
    private final byte[] underMouseColor = new byte[3];

    // omg, 5 variables for dragging
    private ISubpocketStack dragging = SubpocketStackImpl.EMPTY;
    private int draggingIndex = -1;
    private float draggingOffX = 0, draggingOffY = 0;
    private boolean didDrag = false;

    // purely to make intellij shut up lol
    private Minecraft mc;

    public SubpocketScreen(SubpocketContainer container, PlayerInventory playerInv, ITextComponent name) {
        super(container, playerInv, name);
        this.container = container;
        storage = container.getStorage();
        stackSizeMode = storage.getStackSizeMode();

        xSize = 203;
        ySize = 177;
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        this.mc = mc;
        float newScale = (float) mc.mainWindow.getGuiScaleFactor();
        if (newScale != scale) {
            scale = newScale;
            framebuffer.func_216491_a((int) (WIDTH * scale), (int) (HEIGHT * scale), Minecraft.IS_RUNNING_ON_MAC);
        }
    }

    @Override
    public void init() {
        super.init();
        guiLeft -= ARMOR_OFFSET;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // get global (not downscaled) mouse pos and 'downscale' it to floats, keeping precision
        localX = (float) (mc.mouseHelper.getMouseX() / scale - guiLeft - X_OFF);
        localY = (float) (mc.mouseHelper.getMouseY() / scale - guiTop - Y_OFF);

        // exclude edges here because they behave weirdly sometimes
        mouseInside = localX > 0 && localX < WIDTH && localY > 0 && localY < HEIGHT;

        debug = isUsingFBOs() && glfwGetKey(mc.mainWindow.getHandle(), GLFW_KEY_GRAVE_ACCENT) == GLFW_PRESS;

        if (isUsingFBOs() && !Config.Client.disablePixelPicking) {
            int leftAltState = glfwGetKey(mc.mainWindow.getHandle(), GLFW_KEY_LEFT_ALT);
            int rightAltState = glfwGetKey(mc.mainWindow.getHandle(), GLFW_KEY_RIGHT_ALT);
            usePixelPicking = leftAltState != GLFW_PRESS && rightAltState != GLFW_PRESS;
        }

        mc.getTextureManager().bindTexture(TEXTURE);
        blit(guiLeft, guiTop, 0, 0, xSize, ySize);

        // left it for fun, this is a "debug mode", heh (although it did help me a lot)
        if (debug && isUsingFBOs()) {
            framebuffer.bindFramebufferTexture();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder bb = tess.getBuffer();
            bb.begin(GL_QUADS, POSITION_TEX);
            bb.pos(guiLeft + X_OFF, guiTop + Y_OFF + HEIGHT, 0).tex(0, 0).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF + HEIGHT, 0).tex(1, 0).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF, 0).tex(1, 1).endVertex();
            bb.pos(guiLeft + X_OFF, guiTop + Y_OFF, 0).tex(0, 1).endVertex();
            tess.draw();
            framebuffer.unbindFramebufferTexture();
        }

        if (draggingIndex == -1) {
            findStackUnderMouse();
        } else if (mouseInside) {
            underMouse = dragging;
            underMouseIndex = draggingIndex;
        } else {
            resetUnderMouseStack();
        }

        RenderHelper.enableGUIStandardItemLighting();
        GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, 240.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepthTest();

        glEnable(GL_SCISSOR_TEST); // yay scissors!
        glScissor(
                (int) ((guiLeft + X_OFF) * scale),
                (int) (mc.mainWindow.getHeight() - (guiTop + Y_OFF + HEIGHT) * scale),
                (int) (WIDTH * scale),
                (int) (HEIGHT * scale)
        );

        if (isUsingFBOs() && debug) {
            if (!underMouse.isEmpty() && underMouse != dragging) {
                drawStack(underMouse, true);
            }
        } else {
            for (ISubpocketStack stack : storage) {
                if (stack != dragging) { // dragged stack is drawn separately
                    drawStack(stack, true);
                }
            }
        }

        if (draggingIndex != -1) {
            GlStateManager.pushMatrix();
            GlStateManager.translatef( // custom movement for smoothness as here we are in render tick
                    MathHelper.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX),
                    MathHelper.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY),
                    0
            );
            drawStack(dragging, false);
            GlStateManager.popMatrix();
        }

        glDisable(GL_SCISSOR_TEST);

        RenderHelper.disableStandardItemLighting();
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        renderBackground();
        super.render(mouseX, mouseY, partialTicks);

        if (debug && mouseInside) {
            List<String> lines = new ArrayList<>();
            lines.add("§l§5debug:");
            lines.add(format("scale factor: %.2f", scale));
            lines.add(format("local mouse coords: [%.2f, %.2f]", localX, localY));
            lines.add(format("color under mouse: [%d, %d, %d]", underMouseColor[0] & 0xff, underMouseColor[1] & 0xff, underMouseColor[2] & 0xff));
            lines.add(format("computed index: %d", underMouseIndex));
            if (!underMouse.isEmpty()) {
                lines.add(format("§f  hovered stack pos: %.2f, %.2f", underMouse.getX(), underMouse.getY()));
            }
            GuiUtils.drawHoveringText(lines, guiLeft + X_OFF - 10, guiTop + HEIGHT + Y_OFF + 20, width, height, -1, font);
        }

        renderHoveredToolTip(mouseX, mouseY);

        if (underMouse.isEmpty() || !mc.player.inventory.getItemStack().isEmpty()) {
            return;
        }

        ItemStack ref = underMouse.getRef();
        List<String> tooltip = getTooltipFromItem(ref);

        if (underMouse.getCount().compareTo(BigInteger.ONE) > 0) {
            tooltip.add(1, I18n.format("gui.subpocket:it.quantity",
                    hasShiftDown() ? underMouse.getCount().toString() : underMouse.getShortNumberString()));
        }
        FontRenderer font = ref.getItem().getFontRenderer(ref);
        GuiUtils.drawHoveringText(ref, tooltip, mouseX + 12, mouseY, width, height, -1, font != null ? font : this.font);
    }

    private void drawStack(ISubpocketStack stack, boolean translate) {
        ItemStack ref = stack.getRef();

        // push-translate-renderAt00-pop is so that we can render at float coords
        GlStateManager.pushMatrix();
        GlStateManager.translatef(
                guiLeft + X_OFF + (translate ? stack.getX() : 0),
                guiTop + Y_OFF + (translate ? stack.getY() : 0),
                0
        );

        itemRenderer.renderItemAndEffectIntoGUI(mc.player, ref, 0, 0);
        if (!usePixelPicking && stack == underMouse) {
            GlStateManager.enableColorLogicOp();
            GlStateManager.logicOp(XOR);
            mc.getTextureManager().bindTexture(TEXTURE);
            blit(-1, -1, 203, 0, 18, 18);
            GlStateManager.disableColorLogicOp();
        }
        itemRenderer.renderItemOverlayIntoGUI(font, ref, 0, 0,
                stackSizeMode == StackSizeMode.ALL || stackSizeMode == StackSizeMode.HOVERED && stack == underMouse ? stack.getShortNumberString() : "");

        GlStateManager.popMatrix();

        // this line is literally the best, it fixed the thing i was facing for like 4 days
        // i was thinking of using shaders already when that simple thought made its way into my mind
        GlStateManager.clear(GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
        // so since no shaders this still could run on calculators

        // ^ makes it so that we can render 3d items (like itemblocks) as 2d sprites on top of each other
        // so that they never cross or zfight each other
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dwheel) {
        if (dwheel == 0.0 || !mouseInside) {
            return false;
        }
        ClickState click = new ClickState(
                dwheel > 0.0 ? 4 : 5,
                hasShiftDown(), hasControlDown(), hasAltDown()
        );
        container.processClick(localX, localY, click, underMouseIndex);
        Network.sendClickToServer(localX, localY, underMouseIndex, click);

        // fix for processClick calling elevate which changes index (there can be multiple mouse events per
        // render tick, so findStackUnderMouse might not be called to fix that yet)
        if (!underMouse.isEmpty()) {
            underMouseIndex = storage.getStacksView().indexOf(underMouse);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != GLFW_KEY_TAB) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        stackSizeMode = StackSizeMode.values()[(stackSizeMode.ordinal() + 1) % StackSizeMode.values().length];
        Network.sendSetStackSizeModeToServer(stackSizeMode);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (!mouseInside) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return false;
        }
        if (draggingIndex != -1 || underMouse.isEmpty() || !mc.player.inventory.getItemStack().isEmpty()) {
            return false;
        }
        dragging = underMouse;
        draggingIndex = underMouseIndex;
        draggingOffX = underMouse.getX() - localX;
        draggingOffY = underMouse.getY() - localY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        if (!mouseInside) {
            return super.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
        }
        if (draggingIndex != -1) {
            didDrag = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (didDrag) {
            float x = MathHelper.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX);
            float y = MathHelper.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY);
            container.stackMoved(x, y, draggingIndex);
            Network.sendClickToServer(x, y, draggingIndex, null);
            didDrag = false;
        } else if (mouseInside) {
            // mapping the button index to a more logical one I guess..
            // and so that 0-byte is no click
            ClickState click = new ClickState(
                    mouseButton == 0 ? 1 : mouseButton == 1 ? 3 : mouseButton,
                    hasShiftDown(), hasControlDown(), hasAltDown()
            );
            // apparently it differs by 0.5 sometimes causing inconsistent item placement
            float localX = (float) (mouseX - guiLeft - X_OFF);
            float localY = (float) (mouseY - guiTop - Y_OFF);
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendClickToServer(localX, localY, underMouseIndex, click);
        } else {
            return super.mouseReleased(mouseX, mouseY, mouseButton);
        }
        // just reset it regardless
        draggingIndex = -1;
        dragging = SubpocketStackImpl.EMPTY;
        return true;
    }

    private void resetUnderMouseStack() {
        underMouse = SubpocketStackImpl.EMPTY;
        underMouseIndex = -1;
    }

    private void findStackUnderMouse() {
        if (!mouseInside) {
            resetUnderMouseStack();
            return;
        }
        if (!usePixelPicking) {
            for (int i = storage.getStacksView().size() - 1; i >= 0; i--) {
                ISubpocketStack stack = storage.getStacksView().get(i);
                if (localX >= stack.getX() && localX <= stack.getX() + 16
                        && localY >= stack.getY() && localY <= stack.getY() + 16) {
                    underMouse = stack;
                    underMouseIndex = i;
                    return;
                }
            }
            resetUnderMouseStack();
            return;
        }
        framebuffer.bindFramebuffer(true);

        // using white instead of black for background so that debug view is more usable eheh
        GlStateManager.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.clearDepth(1.0F);
        GlStateManager.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);

        GlStateManager.matrixMode(GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(
                0.0D, framebuffer.framebufferWidth,
                framebuffer.framebufferHeight, 0.0D,
                1000.0D, 3000.0D
        );
        GlStateManager.matrixMode(GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
        // ^ almost MainWindow#loadGUIRenderMatrix but ortho width/height are changed to ours

        GlStateManager.scalef(scale, scale, 1); // uh-oh

        // here i use old and deprecated texture combiners to get alpha from the texture,
        // but set own RGB part for pixel-picking.
        // But this can run even on some calculators, yo!
        // previously i ran this whole thing for every item separately only checking for nonzero alpha
        // and obviously this new algorithm is much more efficient even by common sense
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_COMBINE);
        glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB, GL_REPLACE);
        glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_ALPHA, GL_REPLACE);

        glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_RGB, GL_CONSTANT);  // set own RGB from env color
        glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_ALPHA, GL_TEXTURE); // but keep texture's own alpha

        // ugh, but i need this so items are drawn without any blending available
        // since all i need is their silhouettes with CONSTANT indexing color(and simple on/off alpha)
        // this works only because GlStateManager exists and sadly mods are not forced to use it
        GlStateManager.disableBlend();
        GlStateManager.BLEND.field_179213_a.field_179201_b = true;
        // but still this hack is second best thing is did after clearing depth buffer one

        List<ISubpocketStack> stacksView = storage.getStacksView();
        for (int i = 0; i < stacksView.size(); i++) {
            ISubpocketStack stack = stacksView.get(i);

            inputColor
                    .put((i >> 16 & 0xff) / 255.0F)
                    .put((i >> 8 & 0xff) / 255.0F)
                    .put((i & 0xff) / 255.0F)
                    .put(1.0F)
                    .rewind();
            glTexEnvfv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, inputColor);

            // float coords same as in drawStack
            GlStateManager.pushMatrix();
            GlStateManager.translatef(stack.getX(), stack.getY(), 0);
            itemRenderer.renderItemAndEffectIntoGUI(mc.player, stack.getRef(), 0, 0);
            GlStateManager.popMatrix();

            // didn't disable depth at all because vanilla enchantment glint
            // uses depth-hacking to work as it works and without depth it breaks
            GlStateManager.clear(GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
        }

        // reset back blending hack
        GlStateManager.BLEND.field_179213_a.field_179201_b = false;
        GlStateManager.enableBlend();

        // reset back texture combiner
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        // read pixel under mouse
        glReadPixels(
                (int) (localX * scale), (int) ((HEIGHT - localY) * scale),
                1, 1, GL_RGB, GL_UNSIGNED_BYTE,
                outputColor
        );

        mc.getFramebuffer().bindFramebuffer(true);
        mc.mainWindow.loadGUIRenderMatrix(Minecraft.IS_RUNNING_ON_MAC);

        outputColor.get(underMouseColor).rewind();

        int picked = (underMouseColor[0] & 0xff) << 16
                | (underMouseColor[1] & 0xff) << 8
                | underMouseColor[2] & 0xff;

        if (picked != 0xFFFFFF) {
            underMouse = storage.get(underMouseIndex = picked);
        } else { // when we pick the white background
            resetUnderMouseStack();
        }
    }
}
