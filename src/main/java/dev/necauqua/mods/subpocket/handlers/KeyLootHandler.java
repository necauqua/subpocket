/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.handlers;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryTable;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@EventBusSubscriber(modid = MODID)
public final class KeyLootHandler {

    private static final Set<String> injectedPools = new HashSet<>();

    static {
        injectedPools.add("stronghold_corridor");
        injectedPools.add("stronghold_crossing");
        injectedPools.add("simple_dungeon");
        injectedPools.add("enderman");
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent e) {
        ResourceLocation name = e.getName();
        String path = name.getResourcePath();
        String shortName = path.substring(path.indexOf('/') + 1);
        if (!"minecraft".equals(name.getResourceDomain()) || !injectedPools.contains(shortName)) {
            return;
        }
        e.getTable().addPool(new LootPool(
                new LootEntry[]{
                        new LootEntryTable(
                                new ResourceLocation(MODID, shortName),
                                1,
                                0,
                                new LootCondition[0],
                                "subpocket_injected_entry"
                        )
                },
                new LootCondition[0],
                new RandomValueRange(1),
                new RandomValueRange(0, 1),
                "subpocket_injected_pool"
        ));
    }
}
