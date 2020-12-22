/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.minecraft.world.storage.loot.LootTableManager.GSON_INSTANCE;

@EventBusSubscriber(modid = MODID)
public final class LootModifiers {

    private static final Set<String> injectedPools = new HashSet<>();

    static {
        injectedPools.add("entities/enderman");
        injectedPools.add("chests/stronghold_corridor");
        injectedPools.add("chests/stronghold_crossing");
        injectedPools.add("chests/simple_dungeon");
    }

    private static void injectTable(ResourceLocation name, LootTable table) {
        String path = name.getPath();
        String shortName = path.substring(path.indexOf('/') + 1);
        LootPool pool = new LootPool(
                new LootEntry[]{
                        new LootEntryTable(
                                ns(shortName),
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
        );
        table.addPool(pool);
        pool.freeze();
    }

    @SubscribeEvent
    public static void on(FMLServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        LootTableManager lootTableManager = server.getLootTableManager();

        Field isFrozen;
        try {
            isFrozen = LootTable.class.getDeclaredField("isFrozen");
            isFrozen.setAccessible(true);
        } catch (NoSuchFieldException e2) {
            throw new AssertionError(e2);
        }

        server.getResourceManager().addReloadListener(resourceManager -> {
            for (String injectedPool : injectedPools) {
                ResourceLocation name = new ResourceLocation(injectedPool);
                LootTable table = lootTableManager.getLootTableFromLocation(name);
                if (table == LootTable.EMPTY_LOOT_TABLE) {
                    continue;
                }

                // more dumb hacks for it to finally work
                // we read, load and register the loot table completely manually
                // because mod loot tables are no longer visible for mc now
                // (it is referenced in with a LootEntryTable 'injectTable')
                String path = name.getPath();
                String shortName = path.substring(path.indexOf('/') + 1);
                String data;
                try (InputStream resource = Subpocket.class.getResourceAsStream("/assets/subpocket/loot_tables/" + shortName + ".json")) {
                    data = IOUtils.toString(resource, UTF_8);
                } catch (IOException e2) {
                    throw new AssertionError(e2);
                }
                lootTableManager.registeredLootTables.put(ns(shortName),
                        ForgeHooks.loadLootTable(
                                GSON_INSTANCE,
                                ns(shortName),
                                data,
                                true,
                                lootTableManager));

                try {
                    isFrozen.set(table, false);
                    injectTable(name, table);
                    isFrozen.set(table, true);
                } catch (IllegalAccessException e2) {
                    throw new AssertionError(e2);
                }
            }
        });
    }

//    Forge is shit so the below code does not work until 1.14 iirc
//
//    @SubscribeEvent
//    public static void on(LootTableLoadEvent e) {
//        ResourceLocation name = e.getName();
//        if ("minecraft".equals(name.getNamespace()) && injectedPools.contains(name.getPath())) {
//            injectTable(name, e.getTable());
//        }
//    }
}
