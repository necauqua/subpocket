/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.SubpocketAPI;
import dev.necauqua.mods.subpocket.config.Config;
import dev.necauqua.mods.subpocket.impl.SubpocketAPIImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@Mod(MODID)
public final class Subpocket {

    public static final String MODID = "subpocket";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public Subpocket() {
        SubpocketAPI.instance = SubpocketAPIImpl.INSTANCE;
        Config.init();

        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
    }

    public static ResourceLocation ns(String path) {
        return new ResourceLocation(MODID, path);
    }
}
