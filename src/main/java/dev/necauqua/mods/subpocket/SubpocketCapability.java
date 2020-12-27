/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.impl.SubpocketImpl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class SubpocketCapability implements Capability.IStorage<ISubpocket> {

    @CapabilityInject(ISubpocket.class)
    public static Capability<ISubpocket> INSTANCE;

    public static ISubpocket get(PlayerEntity player) {
        return player.getCapability(INSTANCE)
                .orElseThrow(() -> new IllegalStateException("Player had no subpocket capability"));
    }

    @SubscribeEvent
    public static void on(FMLCommonSetupEvent e2) {
        CapabilityManager.INSTANCE.register(ISubpocket.class, new SubpocketCapability(), SubpocketImpl::new);

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, (AttachCapabilitiesEvent<Entity> e) -> {
            if (e.getObject() instanceof PlayerEntity) {
                e.addCapability(ns("storage"), new SubpocketImpl());
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            PlayerEntity original = e.getOriginal();
            boolean hack = !original.isAlive();
            if (hack) {
                original.revive();
            }
            get(e.getPlayer()).cloneFrom(get(original));
            if (hack) {
                original.remove();
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerLoggedInEvent e) -> Network.syncToClient(e.getPlayer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerChangedDimensionEvent e) -> Network.syncToClient(e.getPlayer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerRespawnEvent e) -> Network.syncToClient(e.getPlayer()));
    }

    @Override
    public INBT writeNBT(Capability<ISubpocket> capability, ISubpocket instance, Direction side) {
        return instance.serializeNBT();
    }

    @Override
    public void readNBT(Capability<ISubpocket> capability, ISubpocket instance, Direction side, INBT nbt) {
        if (nbt instanceof CompoundNBT) {
            instance.deserializeNBT((CompoundNBT) nbt);
        }
    }
}
