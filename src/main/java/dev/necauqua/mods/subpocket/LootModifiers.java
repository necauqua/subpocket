/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.TableLootEntry;
import net.minecraft.world.storage.loot.conditions.ILootCondition;
import net.minecraft.world.storage.loot.conditions.LootConditionManager;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID)
public final class LootModifiers {

    private static final Set<String> injectedPools = Sets.newHashSet(
            "entities/enderman",
            "chests/stronghold_corridor",
            "chests/stronghold_crossing",
            "chests/simple_dungeon"
    );

    @SubscribeEvent
    public static void on(LootTableLoadEvent e) {
        ResourceLocation name = e.getName();
        String path = name.getPath();
        if (!"minecraft".equals(name.getNamespace()) || !injectedPools.contains(path)) {
            return;
        }
        e.getTable().addPool(LootPool.builder()
                .name("subpocket_injected_pool")
                .addEntry(TableLootEntry.builder(ns(path.substring(path.indexOf('/') + 1))))
                .bonusRolls(0.0F, 1.0F)
                .build());
    }

    @EventBusSubscriber(modid = MODID, bus = Bus.MOD)
    private static final class ClearDropsModifierSerializer extends GlobalLootModifierSerializer<IGlobalLootModifier> {

        @SubscribeEvent
        public static void on(RegistryEvent.Register<GlobalLootModifierSerializer<?>> evt) {
            evt.getRegistry().register(new ClearDropsModifierSerializer().setRegistryName(ns("clear_drops")));
        }

        @Override
        public IGlobalLootModifier read(ResourceLocation location, JsonObject object, ILootCondition[] conditions) {
            Predicate<LootContext> combined = LootConditionManager.and(conditions);
            return (generatedLoot, context) -> combined.test(context) ? new ArrayList<>() : generatedLoot;
        }
    }
}
