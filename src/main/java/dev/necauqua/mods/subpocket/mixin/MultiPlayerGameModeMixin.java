package dev.necauqua.mods.subpocket.mixin;

import dev.necauqua.mods.subpocket.SubspatialKeyItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
public final class MultiPlayerGameModeMixin {

    @Redirect(method = {
        "startDestroyBlock",
        "continueDestroyBlock"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameType;isCreative()Z"))
    boolean destroyBlock(GameType instance, BlockPos pos) {
        return instance.isCreative() && SubspatialKeyItem.allowCreativeDestroy(minecraft.player, minecraft.level, pos);
    }

    @Shadow
    @Final
    private Minecraft minecraft;
}
