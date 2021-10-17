package dev.necauqua.mods.subpocket;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.necauqua.mods.subpocket.config.IMnenonic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.function.BooleanSupplier;

public final class MnemonicButton<M extends IMnenonic<M>> implements GuiEventListener, Widget, NarratableEntry {

    private final BooleanSupplier enabled;
    private final ConfigValue<M> value;

    private boolean isHovered = false;
    private int x, y;

    private final Minecraft mc = Minecraft.getInstance();

    public MnemonicButton(BooleanSupplier enabled, ConfigValue<M> value) {
        this.enabled = enabled;
        this.value = value;
    }

    public MnemonicButton<M> withPos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public M get() {
        return value.get();
    }

    @Override
    public void render(PoseStack poseStack, int mx, int my, float partialTicks) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        isHovered = isMouseOver(mx, my);
        mc.font.draw(poseStack, value.get().mnemonic(), x, y, isHovered ? 0x404040 : 0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled.getAsBoolean()) {
            return false;
        }
        isHovered = isMouseOver(mx, my);
        if (!isHovered) {
            return false;
        }
        value.set(value.get().next());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= x && mx < x + mc.font.width(value.get().mnemonic()) && my >= y && my < y + mc.font.lineHeight;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return this.isHovered ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput narration) {}
}
