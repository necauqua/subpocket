/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.handlers;

import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.Network;
import dev.necauqua.mods.subpocket.Subpocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = Subpocket.MODID)
public class SyncHandler {

    @Nullable
    @SideOnly(Side.CLIENT)
    public static NBTTagCompound serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onEntityJoinedWorld(EntityJoinWorldEvent e) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        NBTTagCompound nbt = serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti;
        if (nbt != null && e.getEntity() == player) {
            serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti = null;
            CapabilitySubpocket.get(player).deserializeNBT(nbt);
        }
    }

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
