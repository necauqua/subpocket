/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.handlers;

import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.Network;
import dev.necauqua.mods.subpocket.Subpocket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

@EventBusSubscriber(modid = Subpocket.MODID)
public class SyncHandler {
    public static void sync(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            Network.syncToClient((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        CapabilitySubpocket.get(e.getEntityPlayer())
                .cloneFrom(CapabilitySubpocket.get(e.getOriginal()));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerLoggedInEvent e) {
        sync(e.player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerChangedDimensionEvent e) {
        sync(e.player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent e) {
        sync(e.player);
    }
}
