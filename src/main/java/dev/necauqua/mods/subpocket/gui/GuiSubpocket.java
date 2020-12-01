/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.gui;

import dev.necauqua.mods.subpocket.Config;
import dev.necauqua.mods.subpocket.Network;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocket.StackSizeMode;
import dev.necauqua.mods.subpocket.impl.SubpocketStackImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.gui.ContainerSubpocket.HEIGHT;
import static dev.necauqua.mods.subpocket.gui.ContainerSubpocket.WIDTH;
import static java.lang.String.format;
import static net.minecraft.client.renderer.OpenGlHelper.*;
import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX;
import static org.lwjgl.opengl.GL11.*;

public class GuiSubpocket extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(MODID, "textures/gui/subpocket.png");

    private static final int X_OFF = 35, Y_OFF = 8;

    private static final Framebuffer framebuffer = new Framebuffer(WIDTH, HEIGHT, true);
    private static final FloatBuffer inputColor = BufferUtils.createFloatBuffer(4);
    private static final ByteBuffer outputColor = BufferUtils.createByteBuffer(4);
    private static final int ARMOR_OFFSET = 14;

    private StackSizeMode stackSizeMode;

    private final ContainerSubpocket container;
    private final ISubpocket storage;

    private float localX = 0, localY = 0;
    private int scale = 1;
    private boolean mouseInside = false;
    private boolean usePixelPicking = framebufferSupported && !Config.disablePixelPicking;

    private ISubpocketStack underMouse = SubpocketStackImpl.EMPTY;
    private int underMouseIndex = -1;

    private boolean debug = false;
    private final byte[] underMouseColor = new byte[3];

    // omg, 5 variables for dragging
    private ISubpocketStack dragging = SubpocketStackImpl.EMPTY;
    private int draggingIndex = -1;
    private float draggingOffX = 0, draggingOffY = 0;
    private boolean didDrag = false;

    public GuiSubpocket(ContainerSubpocket container) {
        super(container);
        this.container = container;
        storage = container.getStorage();
        stackSizeMode = storage.getStackSizeMode();

        xSize = 203;
        ySize = 177;
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft -= ARMOR_OFFSET;
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        super.setWorldAndResolution(mc, width, height);
        int newScale = new ScaledResolution(mc).getScaleFactor();
        if (newScale != scale) {
            scale = newScale;
            framebuffer.createBindFramebuffer(WIDTH * scale, HEIGHT * scale);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        // get global (not downscaled) mouse pos and 'downscale' it to floats, keeping precision
        localX = Mouse.getX() / (float) scale - guiLeft - X_OFF;
        localY = (mc.displayHeight - Mouse.getY() - 1) / (float) scale - guiTop - Y_OFF;

        // exclude edges here because they behave weirdly sometimes
        mouseInside = localX > 0 && localX < WIDTH && localY > 0 && localY < HEIGHT;

        debug = framebufferSupported && Keyboard.isKeyDown(41);

        drawDefaultBackground();

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (debug) {
            String[] debug = {
                    "§fdebug:",
                    format("§f  color under mouse: [%d, %d, %d]", underMouseColor[0] & 0xff, underMouseColor[1] & 0xff, underMouseColor[2] & 0xff),
                    "§f  computed index: " + underMouseIndex,
                    underMouse.isEmpty() ? "" : format("§f  hovered stack pos: %.2f, %.2f", underMouse.getX(), underMouse.getY()),
            };
            for (int i = 0; i < debug.length; i++) {
                fontRenderer.drawStringWithShadow(debug[i],
                        guiLeft + 28,
                        guiTop - fontRenderer.FONT_HEIGHT * (debug.length - i + 1),
                        0);
            }
        }

        renderHoveredToolTip(mouseX, mouseY);

        if (underMouse.isEmpty() || !mc.player.inventory.getItemStack().isEmpty()) {
            return;
        }

        ItemStack ref = underMouse.getRef();
        List<String> tooltip = getItemToolTip(ref);

        if (stackSizeMode != StackSizeMode.ALL || underMouse.getCount().compareTo(ISubpocketStack.THOUSAND) >= 0) {
            tooltip.add(1, I18n.format("gui.subpocket:it.quantity", underMouse.getCount().toString()));
        }
        FontRenderer font = ref.getItem().getFontRenderer(ref);
        GuiUtils.drawHoveringText(ref, tooltip, mouseX + 12, mouseY, width, height, -1, font == null ? fontRenderer : font);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // left it for fun, this is a "debug mode", heh (although it did help me a lot)
        if (framebufferSupported && debug) {
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
            underMouse = SubpocketStackImpl.EMPTY;
            underMouseIndex = -1;
        }

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();

        glEnable(GL_SCISSOR_TEST); // yay scissors!
        glScissor(
                (guiLeft + X_OFF) * scale, mc.displayHeight - (guiTop + Y_OFF + HEIGHT) * scale,
                WIDTH * scale, HEIGHT * scale
        );

        if (framebufferSupported && debug) {
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
            GlStateManager.translate( // custom movement for smoothness as here we are in render tick
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

    private void drawStack(ISubpocketStack stack, boolean translate) {
        ItemStack ref = stack.getRef();

        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;

        // push-translate-renderAt00-pop is so that we can render at float coords
        GlStateManager.pushMatrix();
        GlStateManager.translate(
                guiLeft + X_OFF + (translate ? stack.getX() : 0),
                guiTop + Y_OFF + (translate ? stack.getY() : 0),
                0
        );

        itemRender.renderItemAndEffectIntoGUI(mc.player, ref, 0, 0);
        if (!usePixelPicking && stack == underMouse) {
            glEnable(GL_COLOR_LOGIC_OP);
            glLogicOp(GL_XOR);
            mc.getTextureManager().bindTexture(TEXTURE);
            drawTexturedModalRect(-1, -1, 203, 0, 18, 18);
            glDisable(GL_COLOR_LOGIC_OP);
        }
        itemRender.renderItemOverlayIntoGUI(fontRenderer, ref, 0, 0,
                stackSizeMode == StackSizeMode.ALL || stackSizeMode == StackSizeMode.HOVERED && stack == underMouse ? stack.getShortNumberString() : "");

        GlStateManager.popMatrix();

        // this line is literally the best, it fixed the thing i was facing for like 4 days
        // i was thinking of using shaders already when that simple thought made its way into my mind
        GlStateManager.clear(GL_DEPTH_BUFFER_BIT);
        // so since no shaders this still could run on calculators

        // ^ makes it so that we can render 3d items (like itemblocks) as 2d sprites on top of each other
        // so that they never cross or zfight each other

        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dwheel = Mouse.getEventDWheel();
        if (dwheel != 0) {
            if (!mouseInside) {
                return;
            }
            ClickState click = new ClickState(
                    dwheel > 0 ? 4 : 5,
                    isShiftKeyDown(), isCtrlKeyDown(), isAltKeyDown()
            );
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendClickToServer(localX, localY, underMouseIndex, click);

            // fix for processClick calling elevate which changes index (there can be multiple mouse events per
            // render tick, so findStackUnderMouse might not be called to fix that yet)
            if (!underMouse.isEmpty()) {
                underMouseIndex = storage.getStacksView().indexOf(underMouse);
            }
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();

        switch (Keyboard.getEventKey()) {
            case 56:
            case 184: // alt
                if (framebufferSupported && !Config.disablePixelPicking) {
                    usePixelPicking = !Keyboard.getEventKeyState();
                }
                break;
            case 41: // ~
                debug = Keyboard.getEventKeyState();
                break;
            case 15: // tab
                if (!Keyboard.getEventKeyState()) {
                    stackSizeMode = StackSizeMode.values()[(stackSizeMode.ordinal() + 1) % StackSizeMode.values().length];
                    Network.sendSetStackSizeModeToServer(stackSizeMode);
                }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!mouseInside) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }
        if (draggingIndex != -1 || underMouse.isEmpty() || !mc.player.inventory.getItemStack().isEmpty()) {
            return;
        }
        dragging = underMouse;
        draggingIndex = underMouseIndex;
        draggingOffX = underMouse.getX() - localX;
        draggingOffY = underMouse.getY() - localY;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (!mouseInside) {
            super.mouseClickMove(mouseX, mouseY, button, time);
            return;
        }
        if (draggingIndex != -1) {
            didDrag = true;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
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
                    button == 0 ? 1 : button == 1 ? 3 : button,
                    isShiftKeyDown(), isCtrlKeyDown(), isAltKeyDown()
            );
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendClickToServer(localX, localY, underMouseIndex, click);
        } else {
            super.mouseReleased(mouseX, mouseY, button);
        }
        // just reset it regardless
        draggingIndex = -1;
        dragging = SubpocketStackImpl.EMPTY;
    }

    private void findStackUnderMouse() {
        if (!mouseInside) {
            underMouse = SubpocketStackImpl.EMPTY;
            underMouseIndex = -1;
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
            underMouse = SubpocketStackImpl.EMPTY;
            underMouseIndex = -1;
            return;
        }
        framebuffer.bindFramebuffer(true);

        // using white instead of black for background so that debug view is more usable eheh
        GlStateManager.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.clearDepth(1.0F);
        GlStateManager.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        GlStateManager.matrixMode(GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(
                0.0D, framebuffer.framebufferWidth,
                framebuffer.framebufferHeight, 0.0D,
                1000.0D, 3000.0D
        );
        GlStateManager.matrixMode(GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        // ^ 6 lines copied and tweaked from EntityRenderer#setupOverlayRendering

        GlStateManager.scale(scale, scale, 1); // uh-oh

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
        GlStateManager.blendState.blend.currentState = true;
        // but still this hack is second best thing is did after clearing depth buffer one

        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;

        List<ISubpocketStack> stacksView = storage.getStacksView();
        for (int i = 0; i < stacksView.size(); i++) {
            ISubpocketStack stack = stacksView.get(i);

            inputColor
                    .put((i >> 16 & 0xff) / 255.0F)
                    .put((i >> 8 & 0xff) / 255.0F)
                    .put((i & 0xff) / 255.0F)
                    .put(1.0F)
                    .rewind();
            glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, inputColor);

            // float coords same as in drawStack
            GlStateManager.pushMatrix();
            GlStateManager.translate(stack.getX(), stack.getY(), 0);
            itemRender.renderItemAndEffectIntoGUI(mc.player, stack.getRef(), 0, 0);
            GlStateManager.popMatrix();

            // didn't disable depth at all because vanilla enchantment glint
            // uses depth-hacking to work as it works and without depth it breaks
            GlStateManager.clear(GL_DEPTH_BUFFER_BIT);
        }
        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;

        // reset back blending hack
        GlStateManager.blendState.blend.currentState = false;
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
        mc.entityRenderer.setupOverlayRendering();

        outputColor.get(underMouseColor).rewind();

        int picked = (underMouseColor[0] & 0xff) << 16
                | (underMouseColor[1] & 0xff) << 8
                | underMouseColor[2] & 0xff;

        if (picked != 0xFFFFFF) {
            // when we pick the white background
            underMouse = storage.get(underMouseIndex = picked);
        } else {
            underMouseIndex = -1;
            underMouse = SubpocketStackImpl.EMPTY;
        }
    }
}
