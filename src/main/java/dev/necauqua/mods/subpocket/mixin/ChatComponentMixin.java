package dev.necauqua.mods.subpocket.mixin;

import dev.necauqua.mods.subpocket.eggs.Name;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ChatComponent.class)
public final class ChatComponentMixin {

    @Redirect(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    Object addMessage(List<Object> instance, int i) {
        return Name.enhance((FormattedCharSequence) instance.get(i));
    }
}
