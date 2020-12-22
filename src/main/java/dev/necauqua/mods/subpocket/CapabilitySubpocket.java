/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.impl.SubpocketImpl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.INBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class CapabilitySubpocket implements Capability.IStorage<ISubpocket> {

    @CapabilityInject(ISubpocket.class)
    public static Capability<ISubpocket> INSTANCE;

    public static ISubpocket get(EntityPlayer player) {
        return player.getCapability(INSTANCE)
                .orElseThrow(() -> new IllegalStateException("Player had no subpocket capability"));
    }

    @SubscribeEvent
    public static void on(FMLCommonSetupEvent e2) {
        CapabilityManager.INSTANCE.register(ISubpocket.class, new CapabilitySubpocket(), SubpocketImpl::new);

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> e) -> {
            if (e.getObject() instanceof EntityPlayer) {
                e.addCapability(ns("storage"), new SubpocketImpl());
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            EntityPlayer original = e.getOriginal();
            boolean hack = !original.isAlive();
            if (hack) {
                original.revive();
            }
            get(e.getEntityPlayer()).cloneFrom(get(original));
            if (hack) {
                original.remove();
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerLoggedInEvent e) -> Network.syncToClient(e.getPlayer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerChangedDimensionEvent e) -> Network.syncToClient(e.getPlayer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerRespawnEvent e) -> Network.syncToClient(e.getPlayer()));
    }

    @Override
    public INBTBase writeNBT(Capability<ISubpocket> capability, ISubpocket instance, EnumFacing side) {
        return instance.serializeNBT();
    }

    @Override
    public void readNBT(Capability<ISubpocket> capability, ISubpocket instance, EnumFacing side, INBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            instance.deserializeNBT((NBTTagCompound) nbt);
        }
    }
}
