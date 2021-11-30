/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.impl.SubpocketImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION;

@EventBusSubscriber(modid = MODID)
public final class SubpocketCapability {

    public static final Capability<ISubpocket> INSTANCE = CapabilityManager.get(new CapabilityToken<>(){});

    public static ISubpocket get(Player player) {
        return player.getCapability(INSTANCE)
            .orElseThrow(() -> new IllegalStateException("Player had no subpocket capability"));
    }

    @EventBusSubscriber(modid = MODID, bus = Bus.MOD)
    private static final class Register {

        @SubscribeEvent
        public static void on(RegisterCapabilitiesEvent e) {
            e.register(ISubpocket.class);
        }
    }

    @SubscribeEvent
    public static void on(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) {
            e.addCapability(ns("storage"), new SubpocketImpl());
        }
    }

    @SubscribeEvent
    public static void on(PlayerEvent.Clone e) {
        var original = e.getOriginal();
        var hack = !original.isAlive();
        if (hack) {
            original.revive();
        }
        get(e.getPlayer()).cloneFrom(get(original));
        if (hack) {
            original.remove(CHANGED_DIMENSION);
        }
    }

    @SubscribeEvent
    public static void on(PlayerLoggedInEvent e) {
        Network.syncToClient(e.getPlayer());
    }

    @SubscribeEvent
    public static void on(PlayerChangedDimensionEvent e) {
        Network.syncToClient(e.getPlayer());
    }

    @SubscribeEvent
    public static void on(PlayerRespawnEvent e) {
        Network.syncToClient(e.getPlayer());
    }
}
