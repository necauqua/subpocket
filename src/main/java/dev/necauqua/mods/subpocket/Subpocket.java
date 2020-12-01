/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.SubpocketAPI;
import dev.necauqua.mods.subpocket.gui.ContainerSubpocket;
import dev.necauqua.mods.subpocket.gui.GuiSubpocket;
import dev.necauqua.mods.subpocket.handlers.EnderChestBlockingHandler;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;

import java.util.UUID;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

@Mod(modid = MODID,
        version = "@VERSION@",
        acceptedMinecraftVersions = "@MC_VERSION_RANGE@",
        updateJSON = "https://raw.githubusercontent.com/wiki/necauqua/subpocket/updates.json",
        certificateFingerprint = "c677c954974252994736eb15e855e1e6fc5a2e62",
        useMetadata = true)
@Mod.EventBusSubscriber
public final class Subpocket {
    public static final String MODID = "subpocket";

    @Instance
    public static Subpocket instance;

    public static final SubspatialKeyItem SUBSPATIAL_KEY = new SubspatialKeyItem();

    @SubscribeEvent
    public static void on(RegistryEvent.Register<Item> e) {
        e.getRegistry().register(SUBSPATIAL_KEY);
    }

    @SubscribeEvent
    public static void on(ModelRegistryEvent e) {
        ModelResourceLocation mrl = new ModelResourceLocation(new ResourceLocation(MODID, "key"), "inventory");
        ModelLoader.setCustomModelResourceLocation(SUBSPATIAL_KEY, 0, mrl);
    }

    // classic
    @SubscribeEvent
    public static void on(PlayerEvent.NameFormat e) {
        UUID id = e.getEntityPlayer().getGameProfile().getId();

        if (id.getMostSignificantBits() == 0xf98e93652c5248c5L &&
                id.getLeastSignificantBits() == 0x86476662f70b7e3dL) {
            e.setDisplayname("§o§dnecauqua§r");
        }
    }

    @EventHandler
    public void on(FMLFingerprintViolationEvent e) {
        LogManager.getLogger(MODID)
                .warn("FINGERPRINT VIOLATED: you're running some unauthorized modification of the mod, be warned. " +
                        "No support will be provided for any issues encountered while using this jar.");
    }

    @EventHandler
    public void on(FMLPreInitializationEvent e) {
        SubpocketAPI.stackFactory = SubpocketStackFactoryImpl.INSTANCE;

        Network.init();
        CapabilitySubpocket.init();
        EnderChestBlockingHandler.init();

        NetworkRegistry.INSTANCE.registerGuiHandler(this, new IGuiHandler() {
            @Override
            @SideOnly(Side.CLIENT)
            public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
                return new GuiSubpocket(new ContainerSubpocket(player));
            }

            @Override
            public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
                return new ContainerSubpocket(player);
            }
        });
    }

    @EventHandler
    public void on(FMLServerStartingEvent e) {
        e.registerServerCommand(new SubpocketCommand());
    }
}
