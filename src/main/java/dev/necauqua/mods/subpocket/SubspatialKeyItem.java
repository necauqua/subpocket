/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
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
public final class SubspatialKeyItem extends Item implements INamedContainerProvider {

    public static final SubspatialKeyItem INSTANCE = new SubspatialKeyItem();

    @SubscribeEvent
    public static void on(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(INSTANCE);
    }

    public SubspatialKeyItem() {
        super(new Properties()
                .group(ItemGroup.MISC)
                .maxStackSize(1)
                .rarity(Rarity.EPIC));
        setRegistryName(MODID, "key");
    }

    @Override
    public String getTranslationKey() {
        return "item." + MODID + ":key";
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        if (world.isRemote || !SubpocketCapability.get(player).isUnlocked()) {
            return new ActionResult<>(ActionResultType.PASS, player.getHeldItem(hand));
        }
        if (!(player instanceof ServerPlayerEntity)) { // idk may be some fake player or something
            return new ActionResult<>(ActionResultType.FAIL, player.getHeldItem(hand));
        }
        player.openContainer(this);
        return new ActionResult<>(ActionResultType.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.extinguish();
        if (!Config.subspatialKeyFrozen) {
            return false;
        }
        if (Config.subspatialKeyNoDespawn) {
            // handle fake items from 'makeFakeItem' method (e.g. from /give)
            if (entity.age == 5999) {
                entity.remove();
            }
        } else if (entity.age != -32768 && --entity.lifespan == 0) {
            //                                      ^ using lifespan instead of age here to keep the 'frozen' look
            entity.remove();
        }
        if (entity.pickupDelay > 0 && entity.pickupDelay != 32767) {
            --entity.pickupDelay;
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        PlayerEntity player = Minecraft.getInstance().player;
        return player != null && player.getCapability(SubpocketCapability.INSTANCE)
                .map(ISubpocket::isUnlocked)
                .orElse(false); // can actually be empty here (e.g. when dead)
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        if (!hasEffect(stack)) {
            tooltip.add(new TranslationTextComponent("item.subpocket:key.desc"));
        }
    }

    @Override
    public Container createMenu(int id, PlayerInventory playerInv, PlayerEntity player) {
        return new SubpocketContainer(id, playerInv);
    }

    @Override
    public ITextComponent getDisplayName() {
        return new StringTextComponent("");
    }

    @EventBusSubscriber(modid = MODID)
    private static final class Interactions {

        @SubscribeEvent
        public static void on(RightClickBlock e) {
            if (!Config.blockEnderChests || e.getWorld().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) {
                return;
            }
            PlayerEntity player = e.getPlayer();
            if (player.isCreative() || !SubpocketCapability.get(player).isUnlocked()) {
                return;
            }
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TranslationTextComponent("popup." + MODID + ":blocked_ender_chest"), true);
            }
            e.setUseBlock(Event.Result.DENY);
        }

        @SubscribeEvent
        public static void on(EntityJoinWorldEvent event) {
            Entity entity = event.getEntity();
            if (!entity.getClass().equals(ItemEntity.class)) {
                return;
            }
            ItemEntity entityItem = (ItemEntity) entity;
            if (entityItem.getItem().getItem() != INSTANCE) {
                return;
            }
            entity.setInvulnerable(true);
            if (Config.subspatialKeyNoDespawn) {
                entityItem.age = -32768;
            }
        }

        @SubscribeEvent
        public static void on(PlayerEvent.BreakSpeed e) {
            PlayerEntity player = e.getPlayer();
            if (player.dimension == DimensionType.THE_END
                    && e.getState().getBlock() == Blocks.ENDER_CHEST
                    && player.getHeldItemMainhand().getItem() == SubspatialKeyItem.INSTANCE
                    && player.world.getGameTime() % 20 == 0
                    && !SubpocketCapability.get(player).isUnlocked()) {
                player.attackEntityFrom(DamageSource.OUT_OF_WORLD, 1.0F);
            }
        }

        @SubscribeEvent
        public static void on(BlockEvent.BreakEvent e) {
            PlayerEntity player = e.getPlayer();
            if (player.dimension != DimensionType.THE_END
                    || e.getState().getBlock() != Blocks.ENDER_CHEST
                    || player.getHeldItemMainhand().getItem() != SubspatialKeyItem.INSTANCE) {
                return;
            }
            ISubpocket storage = SubpocketCapability.get(player);
            if (storage.isUnlocked()) {
                return;
            }
            World world = player.world;
            if (world.isRemote) {
                return;
            }
            BlockPos p = e.getPos();
            LightningBoltEntity lightning = new LightningBoltEntity(world, p.getX(), p.getY(), p.getZ(), false);
            world.addEntity(lightning);
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
        public static boolean forceDefaultSpeedCondition(BlockState state, PlayerEntity player, IBlockReader blockReader, BlockPos pos) {
            float hardness = state.getBlockHardness(blockReader, pos);
            return (hardness >= 0.0F || Config.allowBreakingUnbreakable)
                    && player.getHeldItemMainhand().getItem() == SubspatialKeyItem.INSTANCE
                    && (state.getBlock() != Blocks.ENDER_CHEST
                    || player.dimension != DimensionType.THE_END
                    || SubpocketCapability.get(player).isUnlocked());
        }
    }
}
