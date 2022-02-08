package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static net.minecraft.ChatFormatting.GRAY;
import static net.minecraft.ChatFormatting.ITALIC;

public final class ErrorPopup extends GuiComponent implements GuiEventListener, Widget, NarratableEntry {

    private final Map<String, Throwable> pickingErrors;
    private final SubpocketScreen screen;
    private boolean suppressed, isHovered, shiftDown, ctrlDown;
    private int x, y;

    private final Minecraft mc = Minecraft.getInstance();

    public ErrorPopup(Map<String, Throwable> pickingErrors, SubpocketScreen screen) {
        this.pickingErrors = pickingErrors;
        this.screen = screen;
    }

    public ErrorPopup withPos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public void render(PoseStack poseStack, int mx, int my, float partialTicks) {
        if (suppressed || pickingErrors.isEmpty()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, SubpocketScreen.TEXTURE);

        var tock = mc.level != null && mc.level.getGameTime() % 40 < 20;
        blit(poseStack, x, y, 203 + (tock ? 5 : 0), 18, 5, 5);

        isHovered = isMouseOver(mx, my);
        if (!isHovered) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(new TranslatableComponent("gui.subpocket:it.error.title"));
        if (shiftDown) {
            for (var entry : pickingErrors.entrySet()) {
                var id = entry.getKey();
                var idx = id.indexOf('{');
                if (idx != -1) {
                    id = id.substring(0, idx);
                }
                var item = new TextComponent(id).withStyle(GRAY);
                lines.add(new TranslatableComponent("gui.subpocket:it.error.line", item));
            }
        } else {
            var shiftName = mc.options.keyShift.getKey().getDisplayName().copy().withStyle(GRAY, ITALIC);
            var ctrlName = mc.options.keySprint.getKey().getDisplayName().copy().withStyle(GRAY, ITALIC);
            lines.add(new TranslatableComponent("gui.subpocket:it.error.report"));
            lines.add(new TextComponent(""));
            lines.add(new TranslatableComponent("gui.subpocket:it.error.desc.use_alt"));
            lines.add(new TextComponent(""));
            lines.add(new TranslatableComponent("gui.subpocket:it.error.desc.hold_shift", shiftName));
            lines.add(new TranslatableComponent("gui.subpocket:it.error.desc.shift_click", shiftName));
            lines.add(new TranslatableComponent("gui.subpocket:it.error.desc.ctrl_click", ctrlName));
        }
        screen.renderTooltip(poseStack, lines, Optional.empty(), x, y);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        isHovered = isMouseOver(mx, my);
        if (!isHovered || pickingErrors.isEmpty()) {
            return false;
        }
        if (shiftDown) {
            suppressed = !suppressed;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (ctrlDown) {
            var crashes = new StringBuilder();
            for (var entry : pickingErrors.entrySet()) {
                crashes.append("# ").append(entry.getKey()).append('\n');
                entry.getValue().printStackTrace(new PrintWriter(new StringBuilderWriter(crashes)));
            }
            Util.getPlatform().openUri("https://github.com/necauqua/subpocket/issues/new?" + URLEncodedUtils.format(Arrays.asList(
                new BasicNameValuePair("title", "Weird modded item report"),
                new BasicNameValuePair("body", "When I use my subpocket, I can't interact with certain items without holding Alt, " +
                    "and a red dot appears that I just ctrl+clicked on.\n" +
                    "\n" +
                    "<details>\n" +
                    "<summary>The crash(es)</summary>\n" +
                    "\n" +
                    "```\n" + crashes + "\n```\n</details>"),
                new BasicNameValuePair("labels", "autogenerated,bug"),
                new BasicNameValuePair("assignee", "necauqua")
            ), StandardCharsets.UTF_8));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (mc.options.keyShift.matches(key, scanCode)) {
            shiftDown = true;
        }
        if (mc.options.keySprint.matches(key, scanCode)) {
            ctrlDown = true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (mc.options.keyShift.matches(key, scanCode)) {
            shiftDown = false;
        }
        if (mc.options.keySprint.matches(key, scanCode)) {
            ctrlDown = false;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= x && mx < x + 5 && my >= y && my < y + 5;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return isHovered ? NarrationPriority.HOVERED : NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narration) {}
}
