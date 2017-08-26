/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.gui;

import info.necauqua.mods.subpocket.Network;
import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.impl.PositionedBigStack;
import info.necauqua.mods.subpocket.packet.PacketMouseReleased;
import info.necauqua.mods.subpocket.packet.PacketStackMoved;
import info.necauqua.mods.subpocket.util.ClickState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;

import static info.necauqua.mods.subpocket.gui.ContainerSubpocket.HEIGHT;
import static info.necauqua.mods.subpocket.gui.ContainerSubpocket.WIDTH;
import static net.minecraft.client.renderer.OpenGlHelper.*;
import static org.lwjgl.opengl.GL11.*;

public class GuiSubpocket extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Subpocket.MODID, "textures/gui/subpocket.png");

    private static final int X_OFF = 35, Y_OFF = 8;

    // how many shades use per RGB-component. So this number cubed is how many item types can be picked
    private static final int INDICES = 64; // ^3 = 262 144, enough to lag the hell out of your game (maybe) so stick with it.
    // well, for int32-precision colors that number can go up to 255, but float-precision, yada-yada, whatever, 64

    private static Framebuffer framebuffer = new Framebuffer(WIDTH, HEIGHT, true);
    private static FloatBuffer color = BufferUtils.createFloatBuffer(4);
    private static int scale = 1;

    private final ContainerSubpocket container;
    private final ISubpocketStorage storage;

    private float localX = 0, localY = 0;
    private boolean inside = false;
    private boolean usePicking = false;

    private IPositionedBigStack underMouse = PositionedBigStack.EMPTY;
    private int underMouseIndex = -1;

    // omg, 5 variables for dragging
    private IPositionedBigStack dragging = PositionedBigStack.EMPTY;
    private int draggingIndex = -1;
    private float draggingOffX = 0, draggingOffY = 0;
    private boolean didDrag = false;

    public GuiSubpocket(ContainerSubpocket container) {
        super(container);
        this.container = container;
        storage = container.getStorage();

        xSize = 203;
        ySize = 177;
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft -= 14; // armor thingy de-offset
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        super.setWorldAndResolution(mc, width, height);
        int newScale = new ScaledResolution(mc).getScaleFactor();
        if(newScale != scale) {
            scale = newScale;
            framebuffer.createBindFramebuffer(WIDTH * scale, HEIGHT * scale);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        localX = Mouse.getX() / (float) scale - guiLeft - X_OFF;
        localY = (mc.displayHeight - Mouse.getY() - 1) / (float) scale - guiTop - Y_OFF;
        inside = localX >= 0 && localX <= WIDTH && localY >= 0 && localY <= HEIGHT;

        if(OpenGlHelper.framebufferSupported) {
            usePicking = !isAltKeyDown();
        }

        if(draggingIndex == -1) {
            findStackUnderMouse();
        }else if(inside) {
            underMouse = dragging;
            underMouseIndex = draggingIndex;
        }else {
            underMouse = PositionedBigStack.EMPTY;
            underMouseIndex = -1;
        }

        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        renderHoveredToolTip(mouseX, mouseY);

        if(!underMouse.isEmpty() && mc.player.inventory.getItemStack().isEmpty()) {
            ItemStack ref = underMouse.getRef();
            List<String> tooltip = getItemToolTip(ref);
            if(underMouse.getCount().compareTo(PositionedBigStack.THOUSAND) >= 0) {
                tooltip.add(1, I18n.format("subpocket.tooltip.quantity", underMouse.getCount().toString()));
            }
            FontRenderer font = ref.getItem().getFontRenderer(ref);
            GuiUtils.drawHoveringText(ref, tooltip, mouseX, mouseY, width, height, -1, font == null ? fontRenderer : font);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // left it for fun, this is a "debug mode", heh (although it did help me a lot)
        if(mc.gameSettings.advancedItemTooltips && OpenGlHelper.framebufferSupported && Keyboard.isKeyDown(41)) {
            framebuffer.bindFramebufferTexture();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder bb = tess.getBuffer();
            bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            bb.pos(guiLeft + X_OFF,  guiTop + Y_OFF + HEIGHT, 0).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF + HEIGHT, 0).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF, 0).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF,  guiTop + Y_OFF, 0).color(255, 255, 255, 255).endVertex();
            tess.draw();
            bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            bb.pos(guiLeft + X_OFF,  guiTop + Y_OFF + HEIGHT, 0).tex(0, 0).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF + HEIGHT, 0).tex(1, 0).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF + WIDTH, guiTop + Y_OFF, 0).tex(1, 1).color(255, 255, 255, 255).endVertex();
            bb.pos(guiLeft + X_OFF,  guiTop + Y_OFF, 0).tex(0, 1).color(255, 255, 255, 255).endVertex();
            tess.draw();
            framebuffer.unbindFramebufferTexture();
        }else {
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

            for(IPositionedBigStack stack : storage) {
                if(stack != dragging) { // dragged stack is drawn separately
                    drawStack(stack, !usePicking, true);
                }
            }

            if(draggingIndex != -1) {
                GlStateManager.pushMatrix();
                GlStateManager.translate( // custom movement for smoothness as here we are in render tick
                    MathHelper.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX),
                    MathHelper.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY),
                    0
                );
                drawStack(dragging, !usePicking, false);
                GlStateManager.popMatrix();
            }

            glDisable(GL_SCISSOR_TEST);

            RenderHelper.disableStandardItemLighting();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dwheel = Mouse.getEventDWheel();
        if(dwheel != 0) {
            mouseWheelChanged(dwheel);
        }
    }

    protected void mouseWheelChanged(int dwheel) {
        if(inside) {
            ClickState click = new ClickState(
                dwheel > 0 ? 4 : 5,
                isShiftKeyDown(), isCtrlKeyDown(), isAltKeyDown()
            );
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendToServer(new PacketMouseReleased(localX, localY, click, underMouseIndex));

            // fix for processClick calling elevate which changes index (there can be multiple mouse events per
            // render tick, so findStackUnderMouse might not be called to fix that yet)
            if(!underMouse.isEmpty()) {
                underMouseIndex = storage.getStacksView().indexOf(underMouse);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if(inside) {
            if(draggingIndex == -1 && !underMouse.isEmpty() && mc.player.inventory.getItemStack().isEmpty()) {
                dragging = underMouse;
                draggingIndex = underMouseIndex;
                draggingOffX = underMouse.getX() - localX;
                draggingOffY = underMouse.getY() - localY;
            }
        }else {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if(inside) {
            if(draggingIndex != -1) {
                didDrag = true;
            }
        }else {
            super.mouseClickMove(mouseX, mouseY, button, time);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if(didDrag) {
            float x = MathHelper.clamp(localX + draggingOffX, draggingOffX, WIDTH + draggingOffX);
            float y = MathHelper.clamp(localY + draggingOffY, draggingOffY, HEIGHT + draggingOffY);
            container.stackMoved(x, y, draggingIndex);
            Network.sendToServer(new PacketStackMoved(x, y, draggingIndex));
            didDrag = false;
        }else if(inside) {
            ClickState click = new ClickState(
                button == 0 ? 1 : button == 1 ? 3 : button,
                isShiftKeyDown(), isCtrlKeyDown(), isAltKeyDown()
            );
            container.processClick(localX, localY, click, underMouseIndex);
            Network.sendToServer(new PacketMouseReleased(localX, localY, click, underMouseIndex));
        }else {
            super.mouseReleased(mouseX, mouseY, button);
        }
        // just reset it regardless
        draggingIndex = -1;
        dragging = PositionedBigStack.EMPTY;
    }

    private void drawStack(IPositionedBigStack stack, boolean box, boolean move) {
        ItemStack is = stack.getRef();

        zLevel = 100.0F;
        itemRender.zLevel = 100.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(
            guiLeft + X_OFF + (move ? stack.getX() : 0),
            guiTop  + Y_OFF + (move ? stack.getY() : 0),
            0
        );

        itemRender.renderItemAndEffectIntoGUI(mc.player, is, 0, 0);
        if(box) {
            mc.getTextureManager().bindTexture(TEXTURE);
            drawTexturedModalRect(-1, -1, 203 + (stack == underMouse ? 18 : 0), 0, 18, 18);
        }
        itemRender.renderItemOverlayIntoGUI(fontRenderer, is, 0, 0, stack.getShortNumberString());

        GlStateManager.popMatrix();

        // this line is literally the best, it fixed the thing i was facing for like 4 days
        // i was thinking of using shaders already when that simple thought made its way into my mind
        GlStateManager.clear(GL_DEPTH_BUFFER_BIT);
        // so since no shaders this still could run on calculators

        itemRender.zLevel = 0.0F;
        zLevel = 0.0F;
    }

    private void findStackUnderMouse() {
        // exclude edges here because they behave weirdly sometimes
        if(localX > 0 && localX < WIDTH && localY > 0 && localY < HEIGHT) {
            if(usePicking) {
                framebuffer.bindFramebuffer(true);
                GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
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

                int index = 0;

                for(IPositionedBigStack stack : storage.getStacksView()) {
                    zLevel = 100.0F;
                    itemRender.zLevel = 100.0F;

                    ++index;

                    // while writing this i've realized that it is a 3-digit number in base INDICES
                    // (then normalized to [0;1]) that probably shouldn't have surprized me that much
                    color.put((index % INDICES) / (float) INDICES)
                         .put(((index / INDICES) % INDICES) / (float) INDICES)
                         .put(((index / INDICES / INDICES) % INDICES) / (float) INDICES).put(1.0F)
                         .flip();
                    glTexEnv(GL_TEXTURE_ENV, GL_TEXTURE_ENV_COLOR, color);

                    GlStateManager.pushMatrix();
                    GlStateManager.translate(stack.getX(), stack.getY(), 0);
                    itemRender.renderItemAndEffectIntoGUI(mc.player, stack.getRef(), 0, 0);
                    GlStateManager.popMatrix();

                    // didn't disable depth at all because vanilla enchantment glint
                    // uses depth-hacking to work as it works and without depth it breaks
                    GlStateManager.clear(GL_DEPTH_BUFFER_BIT);

                    itemRender.zLevel = 0.0F;
                    zLevel = 0.0F;
                }

                // reset back blending hack
                GlStateManager.blendState.blend.currentState = false;
                GlStateManager.enableBlend();

                // reset back texture combiner
                glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

                // read pixel under mouse
                glReadPixels(
                    (int) (localX * scale), (int) ((HEIGHT - localY) * scale),
                    1, 1, GL_RGB, GL_FLOAT,
                    color
                );

                mc.getFramebuffer().bindFramebuffer(true);
                mc.entityRenderer.setupOverlayRendering();

                index = ((int) (color.get(0) * INDICES
                              + color.get(1) * INDICES * INDICES
                              + color.get(2) * INDICES * INDICES * INDICES)) - 1;

                underMouse = storage.get(index);
                underMouseIndex = underMouse.isEmpty() ? -1 : index;
            }else {
                for(int i = storage.getStacksView().size() - 1; i >= 0; i--) {
                    IPositionedBigStack stack = storage.getStacksView().get(i);
                    if(localX >= stack.getX() && localX <= stack.getX() + 16
                            && localY >= stack.getY() && localY <= stack.getY() + 16) {
                        underMouse = stack;
                        underMouseIndex = i;
                        return;
                    }
                }
                underMouse = PositionedBigStack.EMPTY;
                underMouseIndex = -1;
            }
        }else {
            underMouse = PositionedBigStack.EMPTY;
            underMouseIndex = -1;
        }
    }
}
