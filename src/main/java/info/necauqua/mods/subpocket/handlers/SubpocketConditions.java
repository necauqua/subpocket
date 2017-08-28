/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.handlers;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Config;
import info.necauqua.mods.subpocket.Network;
import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.packet.PacketSyncNBTag;
import info.necauqua.mods.subpocket.util.NBT;
import info.necauqua.mods.subpocket.util.StagedAction;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

@EventBusSubscriber(modid = Subpocket.MODID)
public class SubpocketConditions {

    private static final int HOURS_TO_TICKS = 72000;

    public static final StagedAction stager = new StagedAction(Subpocket.MODID, 3);

    public static final String NOTIME_TAG = "no_time_check";
    private static final String DRAGON_DEATH_TAG = "dragon_death";
    private static final String DRAGON_DAMAGE_TAG = "dragon_damage";

    private static final int COND_WITHER  = 1;
    private static final int COND_DIAMOND = 2;
    private static final int COND_DRAGON  = 3;

    // player should be alive for [config|default is 3] real hours
    // kill a wither with bare hand last attack
    // eat a diamond
    // get killed by a dragon | get half your health eaten by dragon(hardcore/config)
    // after that u'll have a paper with the code
    public static boolean hasSubpocket(EntityPlayer player) {
        return player.capabilities.isCreativeMode || stager.completed(player);
    }

    public static int getCode(UUID uuid) {
        return Math.abs((int) (uuid.getLeastSignificantBits() % 10000));
    }

    public static void setNoTimeTag(EntityPlayer player, boolean flag) {
        NBTTagCompound tag = NBT.get(player);
        tag.setBoolean(NOTIME_TAG, flag);
        if(player instanceof EntityPlayerMP) {
            Network.sendTo(new PacketSyncNBTag(NOTIME_TAG, new NBTTagByte((byte) (flag ? 1 : 0))), (EntityPlayerMP) player);
        }
    }

    private static boolean aliveEnoughTime(EntityPlayer player) {
        return player.ticksExisted > Config.hoursToLive * HOURS_TO_TICKS || NBT.get(player).getBoolean(NOTIME_TAG);
    }

    private static boolean needDeathByDragon(World world) {
        return Config.needDeathByDragon && !world.getWorldInfo().isHardcoreModeEnabled();
    }

    @SideOnly(Side.CLIENT)
    private static int currentHiddenLetter;

    @SideOnly(Side.CLIENT)
    private static int nextTimeout;

    static {
        if(FMLCommonHandler.instance().getSide().isClient()) { // eh
            currentHiddenLetter = 1;
            nextTimeout = 40;
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onItemTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        EntityPlayer player = e.getEntityPlayer();
        if(player != null) { // is null at 620 line of the Minecraft class (startup NPE)
            if(stack.getItem() == Items.CLOCK
                    && Config.hoursToLive > 0
                    && aliveEnoughTime(player)
                    && !stager.did(player, COND_WITHER)) {
                String sub = Config.hoursToLive == 1 ? ".one" : "";
                e.getToolTip().add(I18n.format(Subpocket.MODID + ".tooltip.clock" + sub, Config.hoursToLive));
            }else if(stack.getItem() == Items.PAPER
                    && stack.getTagCompound() != null // not hasCompound because intellij's null-warns
                    && stack.getTagCompound().hasKey(Subpocket.MODID, 4)) {

                long playerBits = player.getGameProfile().getId().getLeastSignificantBits();

                if(playerBits != stack.getTagCompound().getLong(Subpocket.MODID)) {
                    e.getToolTip().add(I18n.format(Subpocket.MODID + ".tooltip.paper.magic"));
                }
                if(playerBits < 0) {
                    playerBits = -playerBits;
                }
                if(player.ticksExisted % nextTimeout == 0) {
                    currentHiddenLetter = player.world.rand.nextInt(4) + 1;
                    nextTimeout = player.world.rand.nextInt(20) + 10;
                }

                e.getToolTip().add(I18n.format(Subpocket.MODID + ".tooltip.paper.code",
                    I18n.format(Subpocket.MODID + ".tooltip.code." + currentHiddenLetter,
                        ((playerBits / 1000) % 10),
                        ((playerBits / 100) % 10),
                        ((playerBits / 10) % 10),
                        (playerBits % 10)
                    )
                ));
            }
        }
    }

    private static void giveThePaper(EntityPlayer player) {
        ItemStack paper = new ItemStack(Items.PAPER);

        paper.setTranslatableName(Subpocket.MODID + ".name.paper");
        //noinspection ConstantConditions // here is is surely not null because line above it - bad code, i know
        paper.getTagCompound().setLong(Subpocket.MODID, player.getGameProfile().getId().getLeastSignificantBits());
        // using event instead of lore because localization
        // also for *magic tricks*

        if(!player.addItemStackToInventory(paper)) {
            EntityItem dropped = player.dropItem(paper, false);

            if(dropped != null) {
                dropped.setNoPickupDelay();
                dropped.setOwner(player.getName());
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent e) {
        EntityLivingBase living = e.getEntityLiving();
        if(living instanceof EntityWither) {
            Entity src = e.getSource().getTrueSource();
            if(src instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) src;
                if(aliveEnoughTime(player) && player.getHeldItemMainhand().isEmpty()) {
                    stager.advance(player, COND_WITHER);
                    stager.sync(player);
                }
            }
        }else if(needDeathByDragon(living.world)
                && living instanceof EntityPlayer
                && e.getSource().getTrueSource() instanceof EntityDragon) {
            EntityPlayer player = (EntityPlayer) living;
            if(stager.advance(player, COND_DRAGON)) {
                NBT.get(player).setBoolean(DRAGON_DEATH_TAG, true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent e) {
        EntityPlayer player = e.player;
        NBTTagCompound tag = NBT.get(player);
        if(tag.getBoolean(DRAGON_DEATH_TAG)) {
            tag.removeTag(DRAGON_DEATH_TAG);
            stager.sync(player);
            giveThePaper(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent e) {
        if(!needDeathByDragon(e.getEntityLiving().world)
                && e.getEntityLiving() instanceof EntityPlayer
                && e.getSource().getTrueSource() instanceof EntityDragon) {
            EntityPlayer player = (EntityPlayer) e.getEntityLiving();
            if(stager.getState(player) == COND_DIAMOND) {
                NBTTagCompound tag = NBT.get(player);
                float dmg = tag.getFloat(DRAGON_DAMAGE_TAG) + e.getAmount();
                if(dmg >= player.getMaxHealth() / 2) {
                    tag.removeTag(DRAGON_DAMAGE_TAG);
                    stager.advance(player, COND_DRAGON);
                    stager.sync(player);
                    giveThePaper(player);
                }else {
                    tag.setFloat(DRAGON_DAMAGE_TAG, dmg);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemClicked(RightClickItem e) {
        ItemStack stack = e.getItemStack();
        EntityPlayer player = e.getEntityPlayer();
        if(stack.getItem() == Items.DIAMOND && stager.advance(player, COND_DIAMOND)) {
            stager.sync(player);
            if(player instanceof EntityPlayerMP) {
                CriteriaTriggers.CONSUME_ITEM.trigger((EntityPlayerMP) player, stack);
            }
            player.world.playSound( // straight from ItemFood
                null,
                player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS,
                0.5F, player.world.rand.nextFloat() * 0.1F + 0.9F
            );
            //noinspection ConstantConditions // this thing is @Nullable for whatever reasons
            player.addStat(StatList.getObjectUseStats(stack.getItem()));
            if(!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            e.setCancellationResult(EnumActionResult.SUCCESS);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockRightClock(RightClickBlock e) {
        if(e.getWorld().getBlockState(e.getPos()).getBlock() == Blocks.ENDER_CHEST) {
            EntityPlayer player = e.getEntityPlayer();
            if(Config.blockEnderChests && !player.capabilities.isCreativeMode && CapabilitySubpocket.get(player).isAvailableToPlayer()) {
                if(!player.world.isRemote) {
                    player.sendStatusMessage(new TextComponentTranslation(Subpocket.MODID + ".popup.blocked_ender_chest"), true);
                }
                e.setUseBlock(Event.Result.DENY);
            }
        }
    }
}
