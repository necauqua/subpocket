package dev.necauqua.mods.subpocket.mixin;

import dev.necauqua.mods.subpocket.SubpocketCapability;
import dev.necauqua.mods.subpocket.SubspatialKeyItem;
import dev.necauqua.mods.subpocket.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public final class BlockBehaviourMixin {

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    void getDestroyProgress(BlockState state, Player player, BlockGetter blockGetter, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        // if player uses the key
        if (player.getMainHandItem().getItem() != SubspatialKeyItem.INSTANCE.get()) {
            return;
        }
        // if block is not unbreakable or config allows unbreakables
        if (state.getDestroySpeed(blockGetter, pos) < 0.0F && !Config.allowBreakingUnbreakable.get()) {
            return;
        }
        // if not performing the ritual
        if (state.getBlock() == Blocks.ENDER_CHEST
            && player.level.dimension() == Level.END
            && !SubpocketCapability.get(player).isUnlocked()) {
            return;
        }
        // force a constant speed value
        cir.setReturnValue(1.0f / 30.0f);
    }
}
