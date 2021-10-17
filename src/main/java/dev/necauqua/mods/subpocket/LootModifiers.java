/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static net.minecraft.world.level.storage.loot.LootPool.lootPool;
import static net.minecraft.world.level.storage.loot.entries.LootTableReference.lootTableReference;

@EventBusSubscriber(modid = MODID)
public final class LootModifiers {

    private static final Set<String> injectedPools = Set.of(
        "entities/enderman",
        "chests/stronghold_corridor",
        "chests/stronghold_crossing",
        "chests/simple_dungeon"
    );

    @SubscribeEvent
    public static void on(LootTableLoadEvent e) {
        var name = e.getName();
        var path = name.getPath();
        if (!"minecraft".equals(name.getNamespace()) || !injectedPools.contains(path)) {
            return;
        }
        e.getTable().addPool(lootPool()
            .name("subpocket_injected_pool")
            .add(lootTableReference(ns(path.substring(path.indexOf('/') + 1))))
            .bonusRolls(0.0F, 1.0F)
            .build());
    }

    private static final class ClearDropsModifier extends LootModifier {

        private ClearDropsModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Nonnull
        @Override
        protected List<ItemStack> doApply(List<ItemStack> generatedLoot, LootContext context) {
            return new ArrayList<>();
        }

        private LootItemCondition[] getConditions() {
            return conditions;
        }
    }

    @EventBusSubscriber(modid = MODID, bus = Bus.MOD)
    private static final class ClearDropsModifierSerializer extends GlobalLootModifierSerializer<ClearDropsModifier> {

        @SubscribeEvent
        public static void on(RegistryEvent.Register<GlobalLootModifierSerializer<?>> evt) {
            evt.getRegistry().register(new ClearDropsModifierSerializer().setRegistryName(ns("clear_drops")));
        }

        @Override
        public ClearDropsModifier read(ResourceLocation location, JsonObject object, LootItemCondition[] conditions) {
            return new ClearDropsModifier(conditions);
        }

        @Override
        public JsonObject write(ClearDropsModifier instance) {
            return makeConditions(instance.getConditions());
        }
    }
}
