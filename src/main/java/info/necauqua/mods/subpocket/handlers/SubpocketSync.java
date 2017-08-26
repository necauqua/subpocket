/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.handlers;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Network;
import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.packet.PacketSyncStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

import java.io.IOException;

@EventBusSubscriber(modid = Subpocket.MODID)
public class SubpocketSync {

    @SubscribeEvent
    public static void onPlayerSaved(PlayerEvent.SaveToFile e) {
        try {
            CompressedStreamTools.safeWrite(CapabilitySubpocket.toNBT(e.getEntityPlayer()), e.getPlayerFile(Subpocket.MODID));
        }catch(IOException ioe) {
            Subpocket.logger.error("Failed saving storage NBT to file!", ioe);
//            throw new RuntimeException(ioe);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoaded(PlayerEvent.LoadFromFile e) {
        try {
            CapabilitySubpocket.fromNBT(e.getEntityPlayer(), CompressedStreamTools.read(e.getPlayerFile(Subpocket.MODID)));
        }catch(IOException ioe) {
            Subpocket.logger.error("Failed reading storage NBT from file! Corrupted world?", ioe);
//            throw new RuntimeException(ioe);
        }
    }

    public static void sync(EntityPlayer player) {
        if(player instanceof EntityPlayerMP) {
            Network.sendTo(new PacketSyncStorage(player), (EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent e) {
        sync(e.player);
        SubpocketConditions.stager.sync(e.player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone e) {
        EntityPlayer player = e.getEntityPlayer();
        CapabilitySubpocket.get(player).cloneFrom(CapabilitySubpocket.get(e.getOriginal()));
    }

    // so in clone event we 'clone' the data, but we sync it to client
    // in respawn/changedim events, as network is weird(?) in clone event context

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent e) {
        sync(e.player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerChangedDimensionEvent e) {
        sync(e.player);
    }
}
