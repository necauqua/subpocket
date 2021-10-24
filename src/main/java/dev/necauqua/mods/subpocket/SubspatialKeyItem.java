/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.List;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD;

@EventBusSubscriber(modid = MODID, bus = MOD)
public final class SubspatialKeyItem extends Item implements MenuProvider {

    public static final SubspatialKeyItem INSTANCE = new SubspatialKeyItem();

    @SubscribeEvent
    public static void on(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(INSTANCE);
    }

    public SubspatialKeyItem() {
        super(new Properties()
            .tab(CreativeModeTab.TAB_MISC)
            .stacksTo(1)
            .rarity(Rarity.EPIC));
        setRegistryName(MODID, "key");
    }

    @Override
    public String getDescriptionId() {
        return "item." + MODID + ":key";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || !SubpocketCapability.get(player).isUnlocked()) {
            return new InteractionResultHolder<>(InteractionResult.PASS, player.getItemInHand(hand));
        }
        if (!(player instanceof ServerPlayer)) { // idk may be some fake player or something
            return new InteractionResultHolder<>(InteractionResult.FAIL, player.getItemInHand(hand));
        }
        player.openMenu(this);
        return new InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.clearFire();
        if (!Config.subspatialKeyFrozen.get()) {
            return false;
        }
        if (entity.age >= 5999 // remove the fake item from the 'makeFakeItem' method (e.g. from /give)
            || !Config.subspatialKeyNoDespawn.get() && entity.age != -32768 && --entity.lifespan == 0) {
            // shortening the lifespan instead of growing the age here to keep the 'frozen' look ^
            entity.discard();
        }
        if (entity.pickupDelay > 0 && entity.pickupDelay != 32767) {
            --entity.pickupDelay;
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isFoil(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getCapability(SubpocketCapability.INSTANCE)
            .map(ISubpocket::isUnlocked)
            .orElse(false); // can actually be empty here (e.g. when dead)
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!isFoil(stack)) {
            tooltip.add(new TranslatableComponent("item." + MODID + ":key.desc"));
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SubpocketContainer(id, inventory);
    }

    @Override
    public Component getDisplayName() {
        return new TextComponent("");
    }

    @EventBusSubscriber(modid = MODID)
    private static final class Interactions {

        @SubscribeEvent
        public static void on(RightClickBlock e) {
            if (!Config.blockEnderChests.get() || e.getWorld().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) {
                return;
            }
            var player = e.getPlayer();
            if (player.isCreative() || !SubpocketCapability.get(player).isUnlocked()) {
                return;
            }
            if (!player.level.isClientSide) {
                player.displayClientMessage(new TranslatableComponent("popup." + MODID + ":blocked_ender_chest"), true);
            }
            e.setUseBlock(Event.Result.DENY);
        }

        @SubscribeEvent
        public static void on(EntityJoinWorldEvent event) {
            var entity = event.getEntity();
            if (!(entity instanceof ItemEntity entityItem) || entityItem.getItem().getItem() != INSTANCE) {
                return;
            }
            entity.setInvulnerable(true);
            if (Config.subspatialKeyNoDespawn.get()) {
                entityItem.age = -32768;
            }
        }

        @SubscribeEvent
        public static void on(PlayerEvent.BreakSpeed e) {
            var player = e.getPlayer();
            if (player.level.dimension() == Level.END
                && e.getState().getBlock() == Blocks.ENDER_CHEST
                && player.getMainHandItem().getItem() == SubspatialKeyItem.INSTANCE
                && player.level.getGameTime() % 20 == 0
                && !SubpocketCapability.get(player).isUnlocked()) {
                player.hurt(DamageSource.OUT_OF_WORLD, 1.0F);
            }
        }

        @SubscribeEvent
        public static void on(BlockEvent.BreakEvent e) {
            var player = e.getPlayer();
            if (player.level.dimension() != Level.END
                || e.getState().getBlock() != Blocks.ENDER_CHEST
                || player.getMainHandItem().getItem() != SubspatialKeyItem.INSTANCE) {
                return;
            }
            var storage = SubpocketCapability.get(player);
            if (storage.isUnlocked()) {
                return;
            }
            var level = player.level;
            if (level.isClientSide) {
                return;
            }
            var p = e.getPos();
            var lightning = EntityType.LIGHTNING_BOLT.create(level);
            assert lightning != null; // wtf
            lightning.setPos(p.getX(), p.getY(), p.getZ());
            level.addFreshEntity(lightning);
            storage.unlock();
            Network.syncToClient(player);
        }

        @SubscribeEvent
        public static void on(PlayerInteractEvent.LeftClickBlock e) {
            if (e.getItemStack().getItem() == INSTANCE) {
                e.setUseBlock(Event.Result.DENY);
            }
        }

        @SuppressWarnings("unused") // called from the coremod
        public static boolean forceDefaultSpeedCondition(BlockState state, Player player, BlockGetter blockGetter, BlockPos pos) {
            var destroySpeed = state.getDestroySpeed(blockGetter, pos);
            return (destroySpeed >= 0.0F || Config.allowBreakingUnbreakable.get())
                && player.getMainHandItem().getItem() == SubspatialKeyItem.INSTANCE
                && (state.getBlock() != Blocks.ENDER_CHEST
                || player.level.dimension() != Level.END
                || SubpocketCapability.get(player).isUnlocked());
        }
    }
}
