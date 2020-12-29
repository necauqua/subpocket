/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocket.StackSizeMode;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.impl.SubpocketStackImpl;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.client.gui.GuiUtils;
import org.lwjgl.BufferUtils;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;

import static com.mojang.blaze3d.platform.GlStateManager.LogicOp.XOR;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static dev.necauqua.mods.subpocket.SubpocketContainer.HEIGHT;
import static dev.necauqua.mods.subpocket.SubpocketContainer.WIDTH;
import static java.lang.String.format;
import static net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType.GUI;
import static net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX;
import static net.minecraft.inventory.container.PlayerContainer.LOCATION_BLOCKS_TEXTURE;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

@OnlyIn(Dist.CLIENT)
public final class SubpocketScreen extends ContainerScreen<SubpocketContainer> {

    private static final ResourceLocation TEXTURE = ns("textures/gui/subpocket.png");

    private static final int X_OFF = 35, Y_OFF = 8;

    private static final Framebuffer framebuffer = new Framebuffer(WIDTH, HEIGHT, false, Minecraft.IS_RUNNING_ON_MAC);

    private static final FloatBuffer inputColor = BufferUtils.createFloatBuffer(4);
    private static final ByteBuffer outputColor = BufferUtils.createByteBuffer(4);
    private static final int ARMOR_OFFSET = 14;
    private static final int ITEM_LIGHT = 15728880;

    private StackSizeMode stackSizeMode;

    private final SubpocketContainer container;
    private final ISubpocket storage;

    private float localX = 0, localY = 0;
    private float scale = 1;
    private boolean mouseInside = false;
    private boolean usePixelPicking = !Config.Client.disablePixelPicking;

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
        float newScale = (float) mc.getMainWindow().getGuiScaleFactor();
        if (newScale != scale) {
            scale = newScale;
            framebuffer.resize((int) (WIDTH * scale), (int) (HEIGHT * scale), Minecraft.IS_RUNNING_ON_MAC);
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

        debug = glfwGetKey(mc.getMainWindow().getHandle(), GLFW_KEY_GRAVE_ACCENT) == GLFW_PRESS;

        if (!Config.Client.disablePixelPicking) {
            int leftAltState = glfwGetKey(mc.getMainWindow().getHandle(), GLFW_KEY_LEFT_ALT);
            int rightAltState = glfwGetKey(mc.getMainWindow().getHandle(), GLFW_KEY_RIGHT_ALT);
            usePixelPicking = leftAltState != GLFW_PRESS && rightAltState != GLFW_PRESS;
        }

        mc.getTextureManager().bindTexture(TEXTURE);
        blit(guiLeft, guiTop, 0, 0, xSize, ySize);

        // left it for fun, this is a "debug mode", heh (although it did help me a lot)
        if (debug) {
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

        RenderSystem.glMultiTexCoord2f(GL_TEXTURE2, 240.0F, 240.0F);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();

        glEnable(GL_SCISSOR_TEST); // yay scissors!
        glScissor(
                (int) ((guiLeft + X_OFF) * scale),
                (int) (mc.getMainWindow().getHeight() - (guiTop + Y_OFF + HEIGHT) * scale),
                (int) (WIDTH * scale),
                (int) (HEIGHT * scale)
        );

        if (debug) {
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
            RenderSystem.pushMatrix();
            RenderSystem.translatef( // custom movement for smoothness as here we are in render tick
                    MathHelper.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX),
                    MathHelper.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY),
                    0
            );
            drawStack(dragging, false);
            RenderSystem.popMatrix();
        }

        glDisable(GL_SCISSOR_TEST);

        RenderHelper.disableStandardItemLighting();
    }

    private boolean shouldntDrawTooltip() {
        return underMouse.isEmpty() || mc.player == null || !mc.player.inventory.getItemStack().isEmpty();
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        renderBackground();
        super.render(mouseX, mouseY, partialTicks);

        if (debug && mouseInside) {
            List<String> lines = new ArrayList<>();
            lines.add("§l§5debug:");
            lines.add(format("scale factor: %.2f", scale));
            lines.add(format("local mouse coords: [%.2f, %.2f]", localX, localY));
            if (usePixelPicking) {
                lines.add(format("color under mouse: [%d, %d, %d]", underMouseColor[0] & 0xff, underMouseColor[1] & 0xff, underMouseColor[2] & 0xff));
            } else {
                lines.add(format("expected color: [%d, %d, %d]",
                        underMouseIndex >> 16 & 0xff,
                        underMouseIndex >> 8 & 0xff,
                        underMouseIndex & 0xff));
            }
            lines.add(format("computed index: %d", underMouseIndex));
            if (!underMouse.isEmpty()) {
                lines.add(format("hovered stack pos: %.2f, %.2f", underMouse.getX(), underMouse.getY()));
            }
            GuiUtils.drawHoveringText(lines, guiLeft + X_OFF - 10, guiTop + HEIGHT + Y_OFF + 20, width, height, -1, font);
        }

        renderHoveredToolTip(mouseX, mouseY);

        if (shouldntDrawTooltip()) {
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
        RenderSystem.pushMatrix();
        RenderSystem.translatef(
                guiLeft + X_OFF + (translate ? stack.getX() : 0),
                guiTop + Y_OFF + (translate ? stack.getY() : 0),
                0
        );

        itemRenderer.renderItemAndEffectIntoGUI(mc.player, ref, 0, 0);
        if (!usePixelPicking && stack == underMouse) {
            RenderSystem.enableColorLogicOp();
            RenderSystem.logicOp(XOR);
            mc.getTextureManager().bindTexture(TEXTURE);
            blit(-1, -1, 203, 0, 18, 18);
            RenderSystem.disableColorLogicOp();
        }
        itemRenderer.renderItemOverlayIntoGUI(font, ref, 0, 0,
                stackSizeMode == StackSizeMode.ALL || stackSizeMode == StackSizeMode.HOVERED && stack == underMouse ? stack.getShortNumberString() : "");

        RenderSystem.popMatrix();

        // this line is literally the best, it fixed the thing i was facing for like 4 days
        // i was thinking of using shaders already when that simple thought made its way into my mind
        RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
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
        if (draggingIndex != -1 || shouldntDrawTooltip()) {
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

        // using white instead of default black to avoid offetting indiced and so that debug view is nicer
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.clear(GL_COLOR_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);

        // setup same projection matrix as MC uses for inventory item rendering,
        // but width/height are changed to ours
        RenderSystem.matrixMode(GL_PROJECTION);
        RenderSystem.loadIdentity();
        RenderSystem.ortho(
                0.0D, framebuffer.framebufferWidth,
                framebuffer.framebufferHeight, 0.0D,
                1000.0D, 3000.0D
        );
        RenderSystem.matrixMode(GL_MODELVIEW); // don't forget to set the matrix mode back

        // Here I use old and deprecated texture combiners to get alpha from the texture,
        // but set own RGB part for pixel-picking
        // But this can run even on some calculators, nice!
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_COMBINE);
        glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_RGB, GL_REPLACE);
        glTexEnvi(GL_TEXTURE_ENV, GL_COMBINE_ALPHA, GL_REPLACE);

        glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_RGB, GL_CONSTANT);  // set own RGB from env color
        glTexEnvi(GL_TEXTURE_ENV, GL_SOURCE0_ALPHA, GL_TEXTURE); // but keep textures own alpha

        List<ISubpocketStack> stacksView = storage.getStacksView();

        MatrixStack matrixStack = new MatrixStack();

        matrixStack.scale(scale, scale, 1.0F);

        for (int i = 0; i < stacksView.size(); i++) {
            ISubpocketStack stack = stacksView.get(i);

            // set the texture combiner constant color to a shade computed from the index
            inputColor
                    .put((i >> 16 & 0xff) / 255.0F)
                    .put((i >> 8 & 0xff) / 255.0F)
                    .put((i & 0xff) / 255.0F)
                    .put(1.0F)
                    .rewind();
            glTexEnvfv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, inputColor);

            // below is a bunch of dumb inlines and refactors of vanilla rendering routines, seems to work

            matrixStack.push();

            matrixStack.translate(stack.getX() + 8.0F, stack.getY() + 8.0F, 0.0F);
            matrixStack.scale(16.0F, -16.0F, 16.0F);

            ItemStack ref = stack.getRef();
            IBakedModel bakedmodel = itemRenderer.getItemModelWithOverrides(ref, null, mc.player);

            if (ref.getItem() == Items.TRIDENT) { // vanilla, wtf
                bakedmodel = itemRenderer.getItemModelMesher().getModelManager().getModel(new ModelResourceLocation("minecraft:trident#inventory"));
            }

            bakedmodel = ForgeHooksClient.handleCameraTransforms(matrixStack, bakedmodel, GUI, false);

            matrixStack.translate(-0.5D, -0.5D, -0.5D);

            IRenderTypeBuffer.Impl bufferSource = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();

            if (bakedmodel.isBuiltInRenderer()) {
                ref.getItem().getItemStackTileEntityRenderer().render(
                        ref,
                        matrixStack,
                        renderType -> {
                            ResourceLocation texture;
                            if (renderType instanceof RenderType.Type) {
                                texture = ((RenderType.Type) renderType).renderState.texture.texture
                                        .orElse(LOCATION_BLOCKS_TEXTURE);
                            } else {
                                texture = LOCATION_BLOCKS_TEXTURE;
                            }
                            return bufferSource.getBuffer(SilhouetteRenderType.get(texture));
                        },
                        ITEM_LIGHT,
                        OverlayTexture.NO_OVERLAY);
            } else {
                IVertexBuilder buffer = bufferSource.getBuffer(SilhouetteRenderType.get(LOCATION_BLOCKS_TEXTURE));
                itemRenderer.renderModel(bakedmodel, ref, ITEM_LIGHT, OverlayTexture.NO_OVERLAY, matrixStack, buffer);
            }
            bufferSource.finish(); // semi-immediate mode, but this is how vanilla does it so this is ok

            matrixStack.pop();

            // clear the depth so that everything renders on top of each other in order of this loop
            // but we still have the depth for 3d items and blocks to be rendered correctly
            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.IS_RUNNING_ON_MAC);
        }

        // reset the texture combiner
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        // read pixel under mouse
        glReadPixels(
                (int) (localX * scale), (int) ((HEIGHT - localY + 1) * scale),
                1, 1, GL_RGBA, GL_UNSIGNED_BYTE,
                outputColor
        );

        mc.getFramebuffer().bindFramebuffer(true);

        // return the projection matrix to MC defaults (used to be a method, now copying some lines)
        RenderSystem.matrixMode(GL_PROJECTION);
        RenderSystem.loadIdentity();
        MainWindow window = mc.getMainWindow();
        double w = window.getFramebufferWidth() / window.getGuiScaleFactor();
        double h = window.getFramebufferHeight() / window.getGuiScaleFactor();
        RenderSystem.ortho(0.0D, w, h, 0.0D, 1000.0D, 3000.0D);
        RenderSystem.matrixMode(GL_MODELVIEW); // don't forget to set the matrix mode back here tyoo

        // now we are finally free to do whatever so we just compute and set the index

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

    // just make a class to access protected constants instead of like AT entries and stuff, lol
    private static final class SilhouetteRenderType extends RenderType {

        private static final Map<ResourceLocation, RenderType> types = new HashMap<>();

        // idk what some of the parameters mean, this is just RenderType.CUTOUT without lighting/shading
        // that was messing everything up, as well as with default alpha test to keep the original alpha behavior
        public static RenderType get(@Nullable ResourceLocation texture) {
            return types.computeIfAbsent(texture, tex -> makeType("subpocket_item_silhouette",
                    DefaultVertexFormats.BLOCK,
                    GL_QUADS,
                    131072,
                    true,
                    false,
                    State.getBuilder()
                            .texture(tex != null ? new TextureState(tex, false, false) : NO_TEXTURE)
                            .alpha(DEFAULT_ALPHA)
                            .build(false)));
        }

        private SilhouetteRenderType(String name, VertexFormat format, int drawMode, int bufferSize, boolean useDelegate, boolean needsSorting, Runnable setupTask, Runnable clearTask) {
            super(name, format, drawMode, bufferSize, useDelegate, needsSorting, setupTask, clearTask);
        }
    }
}
