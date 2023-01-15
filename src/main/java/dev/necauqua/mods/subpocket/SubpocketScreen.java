/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.config.Config;
import dev.necauqua.mods.subpocket.config.PickingMode;
import dev.necauqua.mods.subpocket.config.StackCountCondition;
import dev.necauqua.mods.subpocket.config.StackCountSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions.FontContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import static com.mojang.blaze3d.platform.GlStateManager.LogicOp.XOR;
import static com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX;
import static com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static dev.necauqua.mods.subpocket.SubpocketContainer.HEIGHT;
import static dev.necauqua.mods.subpocket.SubpocketContainer.WIDTH;
import static java.lang.String.format;
import static net.minecraft.ChatFormatting.BOLD;
import static net.minecraft.ChatFormatting.DARK_PURPLE;
import static net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
import static net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI;
import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.opengl.GL11.*;

// all the direct mode operations
// funny how MC itself uses all of them in item rendering,
// so I am still not the odd one out yet
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MODID, bus = Bus.MOD, value = Dist.CLIENT)
public final class SubpocketScreen extends AbstractContainerScreen<SubpocketContainer> {

    private static final Logger LOGGER = LogManager.getLogger();

    public static final ResourceLocation TEXTURE = ns("textures/gui/subpocket.png");

    private static final ModelResourceLocation TRIDENT_LOCATION = ModelResourceLocation.vanilla("trident", "inventory");
    private static final ModelResourceLocation SPYGLASS_LOCATION = ModelResourceLocation.vanilla("spyglass", "inventory");

    // white color that you get when nothing was picked
    private static final int NONE = 0xFFFFFF;
    private static final int X_OFF = 35, Y_OFF = 8;
    private static final int ARMOR_OFFSET = 14;

    private static final TextureTarget framebuffer = new TextureTarget(WIDTH, HEIGHT, false, Minecraft.ON_OSX);

    private static final ByteBuffer outputColor = BufferUtils.createByteBuffer(4);
    private static final byte[] underMouseColor = new byte[3];

    private static final Map<String, Throwable> pickingErrors = new LinkedHashMap<>();

    private final MnemonicButton<StackCountCondition> stackCountCondition;
    private final MnemonicButton<StackCountSize> stackCountSize;
    private final MnemonicButton<PickingMode> pickingMode;
    private final ErrorPopup errorPopup;

    private PickingMode effectivePickingMode; // for holding alt

    private boolean isTabDown = false;
    private boolean debug = false;

    private final SubpocketContainer container;
    private final ISubpocket storage;

    private float localX = 0, localY = 0;
    private float scale = 1;
    private boolean mouseInside = false;

    private int underMouseIndex = NONE;
    private int draggingIndex = NONE;
    private float draggingOffX = 0, draggingOffY = 0;
    private boolean didDrag = false;

    private Minecraft mc;
    private Font smolFont;

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(SubpocketContainer.TYPE, SubpocketScreen::new));
    }

    public SubpocketScreen(SubpocketContainer container, Inventory playerInv, Component title) {
        super(container, playerInv, title);
        this.container = container;
        storage = container.getStorage();

        imageWidth = 203;
        imageHeight = 166;
        stackCountCondition = new MnemonicButton<>(() -> isTabDown, Config.stackCountCondition);
        stackCountSize = new MnemonicButton<>(() -> isTabDown, Config.stackCountSize);
        pickingMode = new MnemonicButton<>(() -> isTabDown, Config.pickingMode);
        errorPopup = new ErrorPopup(pickingErrors, this);
    }

    @Override
    public void init() {
        // our own non-nullable mc instance, lol
        mc = Objects.requireNonNull(super.minecraft);

        smolFont = new Font(mc.font.fonts, false) {
            @Override
            public int drawInBatch(String text, float x, float y, int color, boolean dropShadow, Matrix4f transform, MultiBufferSource bufferSource, boolean seeThrough, int effectColor, int packedLightCoords, boolean rtl) {
                var adjusted = transform.scale(0.5F, 0.5F, 1, new Matrix4f());
                return super.drawInBatch(text,
                    x * 2 + font.width(text),
                    y * 2 + font.lineHeight / 2.0F,
                    color, dropShadow, adjusted, bufferSource, seeThrough, effectColor, packedLightCoords, rtl);
            }
        };

        var newScale = (float) mc.getWindow().getGuiScale();
        if (newScale != scale) {
            scale = newScale;
            framebuffer.resize((int) (WIDTH * scale), (int) (HEIGHT * scale), Minecraft.ON_OSX);
        }
        super.init();
        leftPos -= ARMOR_OFFSET;

        addRenderableWidget(stackCountCondition.withPos(leftPos + 9, topPos + 129));
        addRenderableWidget(stackCountSize.withPos(leftPos + 9, topPos + 139));
        addRenderableWidget(pickingMode.withPos(leftPos + 9, topPos + 149));
        addRenderableWidget(errorPopup.withPos(leftPos + 195, topPos + 3));
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int x, int y) {}

    // prevent it from drawing the titles
    @Override
    protected void renderLabels(PoseStack poseStack, int x, int y) {}

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(poseStack);

        // get global (not downscaled) mouse pos and 'downscale' it to floats, keeping precision
        localX = (float) (mc.mouseHandler.xpos() / scale - leftPos - X_OFF);
        localY = (float) (mc.mouseHandler.ypos() / scale - topPos - Y_OFF);

        // exclude edges here because they behave weirdly sometimes
        mouseInside = localX > 0 && localX < WIDTH && localY > 0 && localY < HEIGHT;
        effectivePickingMode = hasAltDown() ? PickingMode.ALTERNATIVE : pickingMode.get();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (isTabDown) {
            blit(poseStack, leftPos, topPos + 126, 0, imageHeight, 27, 40);
        }

        // left it for fun, this is a "debug mode", heh (although it did help me a lot)
        if (debug) {
            RenderSystem.setShaderTexture(0, framebuffer.getColorTextureId());
            var tess = Tesselator.getInstance();
            var bb = tess.getBuilder();
            bb.begin(QUADS, POSITION_TEX);
            bb.vertex(leftPos + X_OFF, topPos + Y_OFF + HEIGHT, 0).uv(0, 0).endVertex();
            bb.vertex(leftPos + X_OFF + WIDTH, topPos + Y_OFF + HEIGHT, 0).uv(1, 0).endVertex();
            bb.vertex(leftPos + X_OFF + WIDTH, topPos + Y_OFF, 0).uv(1, 1).endVertex();
            bb.vertex(leftPos + X_OFF, topPos + Y_OFF, 0).uv(0, 1).endVertex();
            tess.end();
        }

        mc.getProfiler().popPush("subpocket");

        underMouseIndex = mouseInside ?
            draggingIndex == NONE ?
                effectivePickingMode.isAlt() ?
                    altPick(storage.getStacksView(), localX, localY) :
                    pixelPick() :
                draggingIndex :
            NONE;

        RenderSystem.enableScissor( // yay, scissors!
            (int) ((leftPos + X_OFF) * scale),
            (int) (mc.getWindow().getHeight() - (topPos + Y_OFF + HEIGHT) * scale),
            (int) (WIDTH * scale),
            (int) (HEIGHT * scale)
        );

        if (debug) {
            if (underMouseIndex != NONE && underMouseIndex != draggingIndex) {
                drawStack(poseStack, storage.get(underMouseIndex), true, true);
            }
        } else {
            var stacksView = storage.getStacksView();
            for (var i = 0; i < stacksView.size(); ++i) {
                // dragged stack is drawn below separately
                if (i != draggingIndex) {
                    drawStack(poseStack, stacksView.get(i), i == underMouseIndex, true);
                }
            }
        }

        mc.getProfiler().popPush("gameRenderer");

        if (draggingIndex != NONE) {
            var modelview = RenderSystem.getModelViewStack();
            modelview.pushPose();
            modelview.translate( // custom movement for sub-mc-pixel smoothness
                Mth.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX),
                Mth.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY),
                0
            );
            RenderSystem.applyModelViewMatrix();
            drawStack(poseStack, storage.get(draggingIndex), true, false);
            modelview.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        RenderSystem.disableScissor();

        super.render(poseStack, mouseX, mouseY, partialTicks);

        var underMouse = storage.get(underMouseIndex);

        if (debug && mouseInside) {
            List<Component> lines = new ArrayList<>();

            lines.add(Component.literal("debug:").withStyle(DARK_PURPLE, BOLD));
            lines.add(Component.literal(format("scale factor: %.2f", scale)));
            lines.add(Component.literal(format("local mouse coords: [%.2f, %.2f]", localX, localY)));

            if (effectivePickingMode == PickingMode.PIXEL) {
                lines.add(Component.literal(format("color under mouse: [%d, %d, %d]",
                    underMouseColor[0] & 0xff,
                    underMouseColor[1] & 0xff,
                    underMouseColor[2] & 0xff)));
            } else {
                lines.add(Component.literal(format("expected color: [%d, %d, %d]",
                    underMouseIndex >> 16 & 0xff,
                    underMouseIndex >> 8 & 0xff,
                    underMouseIndex & 0xff)));
            }

            if (underMouseIndex != NONE) {
                lines.add(Component.literal(format("computed index: %d", underMouseIndex)));
                lines.add(Component.literal(format("hovered stack pos: %.2f, %.2f", underMouse.getX(), underMouse.getY())));
            }

            renderTooltip(poseStack, lines, Optional.empty(), leftPos + 25, topPos + 98, font);
        }

        renderTooltip(poseStack, mouseX, mouseY);

        if (underMouseIndex == NONE || !container.getCarried().isEmpty()) {
            return;
        }

        var ref = underMouse.getRef();
        var tooltip = getTooltipFromItem(ref);

        if (underMouse.getCount().compareTo(BigInteger.ONE) > 0) {
            tooltip.add(1, Component.translatable("gui.subpocket:it.quantity",
                hasShiftDown() ? underMouse.getCount().toString() :
                    underMouse.getShortNumberString(Config.overflowType.get())));
        }
        var font = IClientItemExtensions.of(ref).getFont(ref, FontContext.TOOLTIP);
        renderTooltip(poseStack, tooltip, ref.getTooltipImage(), mouseX + 12, mouseY, font != null ? font : this.font);
    }

    private void drawStack(PoseStack poseStack, ISubpocketStack stack, boolean hovered, boolean translate) {
        // push-translate-renderAt00-pop is so that we can render at float coords
        var movelview = RenderSystem.getModelViewStack();
        movelview.pushPose();
        movelview.translate(
            leftPos + X_OFF + (translate ? stack.getX() : 0),
            topPos + Y_OFF + (translate ? stack.getY() : 0),
            0
        );
        RenderSystem.applyModelViewMatrix();

        var ref = stack.getRef();
        //noinspection ConstantConditions -- mc.player is nullable, but the method is too, fix ur annotation mojang
        itemRenderer.renderAndDecorateItem(mc.player, ref, 0, 0, 0);
        if (hovered && effectivePickingMode.isAlt()) {
            RenderSystem.enableColorLogicOp();
            RenderSystem.logicOp(XOR);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShaderTexture(0, TEXTURE);
            blit(poseStack, -1, -1, 203, 0, 18, 18);
            RenderSystem.disableColorLogicOp();
        }
        if (stackCountCondition.get().applies(hovered)) {
            var s = stack.getShortNumberString(Config.overflowType.get());
            var f = stackCountSize.get().applies(s.length() <= 2) ? font : smolFont;
            itemRenderer.renderGuiItemDecorations(f, ref, 0, 0, s);
        }

        movelview.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        // ^ makes it so that we can render 3d items (like itemblocks) as 2d sprites on top of each other
        // so that they never cross or zfight each other
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (!mouseInside) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return false;
        }
        if (draggingIndex != NONE || underMouseIndex == NONE || !container.getCarried().isEmpty()) {
            return false;
        }
        var underMouse = storage.get(underMouseIndex);
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
        if (draggingIndex == NONE) {
            return false;
        }
        didDrag = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (didDrag) {
            var x = Mth.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX);
            var y = Mth.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY);
            container.stackMoved(x, y, draggingIndex);
            Network.sendClickToServer(x, y, draggingIndex, null);
            didDrag = false;
        } else if (mouseInside) {
            // mapping the button index to a more logical one I guess
            // and so that 0-byte is no click
            var click = new ClickState(
                mouseButton == 0 ? 1 : mouseButton == 1 ? 3 : mouseButton,
                hasShiftDown(), hasControlDown(), hasAltDown()
            );
            // apparently it differs by 0.5 sometimes causing inconsistent item placement
            var localX = (float) (mouseX - leftPos - X_OFF);
            var localY = (float) (mouseY - topPos - Y_OFF);
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendClickToServer(localX, localY, underMouseIndex, click);
        } else {
            return super.mouseReleased(mouseX, mouseY, mouseButton);
        }
        // just reset it all regardless
        draggingIndex = NONE;
        underMouseIndex = NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dwheel) {
        if (dwheel == 0.0 || !mouseInside) {
            return false;
        }
        var click = new ClickState(
            dwheel > 0.0 ? 4 : 5,
            hasShiftDown(), hasControlDown(), hasAltDown()
        );

        var underMouse = storage.get(underMouseIndex);

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
        switch (key) {
            case GLFW_KEY_TAB -> isTabDown = true;
            case GLFW_KEY_GRAVE_ACCENT -> debug = true;
            default -> {
                errorPopup.keyPressed(key, scanCode, modifiers);
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW_KEY_TAB -> isTabDown = false;
            case GLFW_KEY_GRAVE_ACCENT -> debug = false;
            default -> {
                errorPopup.keyReleased(key, scanCode, modifiers);
                return super.keyReleased(key, scanCode, modifiers);
            }
        }
        return true;
    }

    private static int altPick(List<ISubpocketStack> stacks, float mouseX, float mouseY) {
        for (var i = stacks.size() - 1; i >= 0; i--) {
            var stack = stacks.get(i);
            if (mouseX >= stack.getX() && mouseX <= stack.getX() + 16
                && mouseY >= stack.getY() && mouseY <= stack.getY() + 16) {
                return i;
            }
        }
        return NONE;
    }

    private int pixelPick() {
        framebuffer.bindWrite(true);

        // using white instead of default black to avoid offetting indices and so that debug view is nicer
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.clear(GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

        var originalProjection = RenderSystem.getProjectionMatrix();
        // setup same projection matrix as MC uses for inventory item rendering,
        // but width/height are changed to ours
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0.0F, framebuffer.width, framebuffer.height, 0.0F, 1000.0F, 3000.0F));

        mc.getProfiler().popPush("subpocket.silhouettes");

        var stacksView = storage.getStacksView();
        for (var i = 0; i < stacksView.size(); ++i) {
            RenderSystem.setShaderColor((i >> 16 & 0xff) / 255.0F, (i >> 8 & 0xff) / 255.0F, (i & 0xff) / 255.0F, 1.0F);

            var stack = stacksView.get(i);

            // inlining and specializing a specific case of vanilla item rendering so that we can wrap the buffer source, seems to work

            var modelview = RenderSystem.getModelViewStack();
            modelview.pushPose();
            modelview.scale(scale, scale, 1.0F);
            modelview.translate(stack.getX() + 8.0F, stack.getY() + 8.0F, 0.0F);
            modelview.scale(16.0F, -16.0F, 16.0F);
            RenderSystem.applyModelViewMatrix();

            var ref = stack.getRef();
            var model = itemRenderer.getModel(ref, null, mc.player, 0);
            var poseStack = new PoseStack();
            poseStack.pushPose(); // allow mod renderers to pop the stack once (e.g. sophisticated backpacks)

            var itemRenderer = mc.getItemRenderer();

            // wtf is this, vanilla
            if (ref.is(Items.TRIDENT)) {
                model = itemRenderer.getItemModelShaper().getModelManager().getModel(TRIDENT_LOCATION);
            } else if (ref.is(Items.SPYGLASS)) {
                model = itemRenderer.getItemModelShaper().getModelManager().getModel(SPYGLASS_LOCATION);
            }

            //noinspection UnstableApiUsage - well, we're inlining vanilla code here ¯\_(ツ)_/¯
            model = ForgeHooksClient.handleCameraTransforms(poseStack, model, GUI, false);

            poseStack.translate(-0.5D, -0.5D, -0.5D);

            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var wrapper = (MultiBufferSource) renderType -> bufferSource.getBuffer(SilhouetteRenderType.get(renderType));

            try {
                if (model.isCustomRenderer()) {
                    IClientItemExtensions.of(ref).getCustomRenderer().renderByItem(ref, GUI, poseStack, wrapper, FULL_BRIGHT, NO_OVERLAY);
                } else {
                    for (var pass : model.getRenderPasses(ref, false)) {
                        for (var renderType : pass.getRenderTypes(ref, false)) {
                            itemRenderer.renderModelLists(pass, ref, FULL_BRIGHT, NO_OVERLAY, poseStack, wrapper.getBuffer(renderType));
                        }
                    }
                }
            } catch (Throwable throwable) {
                var id = String.valueOf(ForgeRegistries.ITEMS.getKey(ref.getItem()));
                if (ref.hasTag()) {
                    assert ref.getTag() != null;
                    id += ref.getTag().toString();
                }
                if (pickingErrors.put(id, throwable) == null) {
                    LOGGER.error("Failed to render the silhouette of {}", id, throwable);
                }
            }

            bufferSource.endBatch();

            modelview.popPose();
            RenderSystem.applyModelViewMatrix();

            // clear the depth so that everything renders on top of each other in order of this loop,
            // but we still have the depth for 3d items and blocks to be rendered correctly
            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }

        mc.getProfiler().popPush("subpocket");

        // read pixel under mouse
        RenderSystem.readPixels(
            (int) (localX * scale), (int) ((HEIGHT - localY) * scale),
            1, 1, GL_RGBA, GL_UNSIGNED_BYTE,
            outputColor
        );

        mc.getMainRenderTarget().bindWrite(true);

        // return the projection matrix to MC defaults
        RenderSystem.setProjectionMatrix(originalProjection);

        // now we are finally free to do whatever, so we just compute and return the index

        outputColor.get(underMouseColor).rewind();
        return (underMouseColor[0] & 0xff) << 16
            | (underMouseColor[1] & 0xff) << 8
            | underMouseColor[2] & 0xff;
    }

    @SubscribeEvent
    public static void on(RegisterShadersEvent e) throws IOException {
        e.registerShader(
            new ShaderInstance(e.getResourceProvider(), ns("item_silhouette"), DefaultVertexFormat.BLOCK),
            shader -> SilhouetteRenderType.silhouette = shader
        );
    }

    @SubscribeEvent
    public static void on(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((preparationBarrier, b, c, d, f, g) ->
            preparationBarrier.wait(true)
                .thenAccept(v -> {
                    LogManager.getLogger(Subpocket.class).info("Clearing cached silhouette render types");
                    SilhouetteRenderType.types.clear();
                }));
    }

    // just make a class to access protected constants instead of like AT entries and stuff, lol
    public static final class SilhouetteRenderType extends RenderType {

        private static final Map<RenderType, RenderType> types = new HashMap<>();

        private static ShaderInstance silhouette;

        private static final RenderStateShard.ShaderStateShard SILHOUETTE_SHADER = new RenderStateShard.ShaderStateShard(() -> silhouette);

        // idek what some parameters mean, this is just based on removing
        // fog/lighting from RenderType.CUTOUT and ignoring texture rgb
        public static RenderType get(RenderType renderType) {
            return types.computeIfAbsent(renderType, $ -> create("subpocket_item_silhouette",
                DefaultVertexFormat.BLOCK,
                QUADS,
                SMALL_BUFFER_SIZE,
                true,
                false,
                CompositeState.builder()
                    .setShaderState(SILHOUETTE_SHADER)
                    .setTextureState(renderType instanceof CompositeRenderType composite ?
                        composite.state.textureState :
                        RenderType.NO_TEXTURE)
                    .createCompositeState(false)));
        }

        private SilhouetteRenderType(String name, VertexFormat format, Mode drawMode, int bufferSize, boolean useDelegate, boolean needsSorting, Runnable setupTask, Runnable clearTask) {
            super(name, format, drawMode, bufferSize, useDelegate, needsSorting, setupTask, clearTask);
        }
    }
}
