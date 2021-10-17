package dev.necauqua.mods.subpocket.impl;

import dev.necauqua.mods.subpocket.Network;
import dev.necauqua.mods.subpocket.api.ISubpocketAPI;
import dev.necauqua.mods.subpocket.api.ISubpocketStackFactory;
import net.minecraft.server.level.ServerPlayer;

public final class SubpocketAPIImpl implements ISubpocketAPI {

    public static final ISubpocketAPI INSTANCE = new SubpocketAPIImpl();

    @Override
    public ISubpocketStackFactory getStackFactory() {
        return SubpocketStackFactoryImpl.INSTANCE;
    }

    @Override
    public void syncToClient(ServerPlayer serverPlayer) {
        Network.syncToClient(serverPlayer);
    }
}
