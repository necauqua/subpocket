/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries.Keys;
import net.minecraftforge.registries.RegisterEvent;

import javax.annotation.Nonnull;
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
            .setBonusRolls(UniformGenerator.between(0.0F, 1.0F))
            .build());
    }


    @EventBusSubscriber(modid = MODID, bus = Bus.MOD)
    private static final class ClearDropsModifier extends LootModifier {

        public static final Codec<ClearDropsModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, ClearDropsModifier::new));

        private ClearDropsModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Nonnull
        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            return new ObjectArrayList<>();
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }

        @SubscribeEvent
        public static void on(RegisterEvent evt) {
            evt.register(Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ns("clear_drops"), () -> ClearDropsModifier.CODEC);
        }
    }
}
