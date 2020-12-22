/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.SubpocketAPI;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void on(FMLClientSetupEvent e) {

        // cannot put this listener in GuiSubpocket (as I wanted to)
        // because in its <clinit> I create the framebuffer used for pixel-picking
        // and it happens way too early because of class getting loaded early

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.GUIFACTORY, () ->
                msg -> new GuiSubpocket(new ContainerSubpocket(Minecraft.getInstance().player)));
    }

    public static ResourceLocation ns(String path) {
        return new ResourceLocation(MODID, path);
    }
}
