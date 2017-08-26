/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket;

import info.necauqua.mods.subpocket.api.IPositionedBigStackFactory;
import info.necauqua.mods.subpocket.api.SubpocketAPI;
import info.necauqua.mods.subpocket.api.SubpocketAPI.ISubpocketAPI;
import info.necauqua.mods.subpocket.gui.GuiHandler;
import info.necauqua.mods.subpocket.handlers.CodeGuiOpener;
import info.necauqua.mods.subpocket.impl.PositionedBigStackFactory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

@Mod(modid = Subpocket.MODID /* @ACCEPTED_MC_VERSIONS@ */)
public class Subpocket implements ISubpocketAPI {

    public static final String MODID = "subpocket";

    public static final Logger logger = LogManager.getFormatterLogger(MODID);

    @Instance
    public static Subpocket instance;

    @Override
    @Nonnull
    public IPositionedBigStackFactory getStackFactory() {
        return PositionedBigStackFactory.INSTANCE;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        SubpocketAPI.instance = instance; // this

        Network.init();
        CapabilitySubpocket.init();

        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

        if(e.getSide().isClient()) {
            //noinspection MethodCallSideOnly
            CodeGuiOpener.init();
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new SubpocketCommand());
    }
}
