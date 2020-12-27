/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.SubpocketAPI;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD;

@Mod(MODID)
@EventBusSubscriber(modid = MODID, bus = MOD)
public final class Subpocket {
    public static final String MODID = "subpocket";

    public Subpocket() {
        SubpocketAPI.stackFactory = SubpocketStackFactoryImpl.INSTANCE;
        Config.init();
    }

    public static ResourceLocation ns(String path) {
        return new ResourceLocation(MODID, path);
    }
}
