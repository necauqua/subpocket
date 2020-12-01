/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.handlers;

import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.Config;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

public final class EnderChestBlockingHandler {

    public static void init() {
        if (Config.blockEnderChests) {
            MinecraftForge.EVENT_BUS.register(EnderChestBlockingHandler.class);
        }
    }

    @SubscribeEvent
    public static void onBlockRightClock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getWorld().getBlockState(e.getPos()).getBlock() != Blocks.ENDER_CHEST) {
            return;
        }
        EntityPlayer player = e.getEntityPlayer();
        if (player.capabilities.isCreativeMode || !CapabilitySubpocket.get(player).isUnlocked()) {
            return;
        }
        if (!player.world.isRemote) {
            player.sendStatusMessage(new TextComponentTranslation(MODID + ".popup.blocked_ender_chest"), true);
        }
        e.setUseBlock(Event.Result.DENY);
    }
}
