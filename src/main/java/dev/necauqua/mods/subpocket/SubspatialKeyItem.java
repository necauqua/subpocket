/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD;

@EventBusSubscriber(modid = MODID, bus = MOD)
public final class SubspatialKeyItem extends Item implements IInteractionObject {

    public static final SubspatialKeyItem INSTANCE = new SubspatialKeyItem();

    @SubscribeEvent
    public static void on(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(INSTANCE);
    }

    public SubspatialKeyItem() {
        super(new Properties()
                .group(ItemGroup.MISC)
                .maxStackSize(1)
                .rarity(EnumRarity.EPIC));
        setRegistryName(MODID, "key");
    }

    @Override
    public String getTranslationKey() {
        return "item." + MODID + ":key";
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand handIn) {
        if (world.isRemote || !CapabilitySubpocket.get(player).isUnlocked()) {
            return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(handIn));
        }
        if (!(player instanceof EntityPlayerMP)) { // idk may be some fake player or something
            return new ActionResult<>(EnumActionResult.FAIL, player.getHeldItem(handIn));
        }
        NetworkHooks.openGui((EntityPlayerMP) player, this);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(handIn));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, EntityItem entity) {
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
        EntityPlayer player = Minecraft.getInstance().player;
        return player != null && player.getCapability(CapabilitySubpocket.INSTANCE)
                .map(ISubpocket::isUnlocked)
                .orElse(false); // can actually be empty here (e.g. when dead)
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        if (!hasEffect(stack)) {
            tooltip.add(new TextComponentTranslation("item.subpocket:key.desc"));
        }
    }

    @Override
    public Container createContainer(InventoryPlayer playerInventory, EntityPlayer player) {
        return new ContainerSubpocket(player);
    }

    @Override
    public String getGuiID() {
        return MODID + ":it";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Nullable
    @Override
    public ITextComponent getCustomName() {
        return null;
    }

    @EventBusSubscriber(modid = MODID)
    private static final class Interactions {

        @SubscribeEvent
        public static void on(RightClickBlock e) {
            if (!Config.blockEnderChests || e.getWorld().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) {
                return;
            }
            EntityPlayer player = e.getEntityPlayer();
            if (player.isCreative() || !CapabilitySubpocket.get(player).isUnlocked()) {
                return;
            }
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("popup." + MODID + ":blocked_ender_chest"), true);
            }
            e.setUseBlock(Event.Result.DENY);
        }

        @SubscribeEvent
        public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
            Entity entity = event.getEntity();
            if (!entity.getClass().equals(EntityItem.class)) {
                return;
            }
            EntityItem entityItem = (EntityItem) entity;
            if (entityItem.getItem().getItem() != INSTANCE) {
                return;
            }
            entity.setInvulnerable(true);
            entityItem.isImmuneToFire = true;
            if (Config.subspatialKeyNoDespawn) {
                entityItem.age = -32768;
            }
        }

        @SubscribeEvent
        public static void on(PlayerEvent.BreakSpeed e) {
            EntityPlayer player = e.getEntityPlayer();
            if (player.dimension == DimensionType.THE_END
                    && e.getState().getBlock() == Blocks.ENDER_CHEST
                    && player.getHeldItemMainhand().getItem() == SubspatialKeyItem.INSTANCE
                    && player.world.getGameTime() % 20 == 0
                    && !CapabilitySubpocket.get(player).isUnlocked()) {
                player.attackEntityFrom(DamageSource.OUT_OF_WORLD, 1.0F);
            }
        }

        @SubscribeEvent
        public static void on(BlockEvent.BreakEvent e) {
            EntityPlayer player = e.getPlayer();
            if (player.dimension != DimensionType.THE_END
                    || e.getState().getBlock() != Blocks.ENDER_CHEST
                    || player.getHeldItemMainhand().getItem() != SubspatialKeyItem.INSTANCE) {
                return;
            }
            ISubpocket storage = CapabilitySubpocket.get(player);
            if (storage.isUnlocked()) {
                return;
            }
            World world = player.world;
            if (world.isRemote) {
                return;
            }
            BlockPos p = e.getPos();
            EntityLightningBolt lightning = new EntityLightningBolt(world, p.getX(), p.getY(), p.getZ(), false);
            world.spawnEntity(lightning);
            storage.unlock();
            Network.syncToClient(player);
        }

        @SubscribeEvent
        public static void on(BlockEvent.HarvestDropsEvent e) {
            EntityPlayer player = e.getHarvester();
            if (player != null && player.getHeldItemMainhand().getItem() == SubspatialKeyItem.INSTANCE) {
                e.getDrops().clear();
            }
        }

        @SuppressWarnings("unused") // called from the coremod
        public static boolean forceDefaultSpeedCondition(IBlockState state, EntityPlayer player, IBlockReader blockReader, BlockPos pos) {
            float hardness = state.getBlockHardness(blockReader, pos);
            return (hardness >= 0.0F || Config.allowBreakingUnbreakable)
                    && player.getHeldItemMainhand().getItem() == SubspatialKeyItem.INSTANCE
                    && (state.getBlock() != Blocks.ENDER_CHEST
                    || player.dimension != DimensionType.THE_END
                    || CapabilitySubpocket.get(player).isUnlocked());
        }
    }
}