package dev.necauqua.mods.subpocket.mixin;

import dev.necauqua.mods.subpocket.SubspatialKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerGameMode.class)
public final class ServerPlayerGameModeMixin {

    @Redirect(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;isCreative()Z", ordinal = 1))
    boolean breakEvenInCreativeLul(ServerPlayerGameMode instance, BlockPos pos) {
        return instance.isCreative() && SubspatialKeyItem.allowCreativeDestroy(player, level, pos);
    }

    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;
}
