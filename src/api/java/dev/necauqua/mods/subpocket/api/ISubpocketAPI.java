package dev.necauqua.mods.subpocket.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * An interface exposing a number of subpocket methods/objects that can be used to interact with it.
 **/
public interface ISubpocketAPI {

    /**
     * @return a {@link ISubpocketStackFactory} instance that can be used to create an {@link ISubpocketStack} instances.
     **/
    ISubpocketStackFactory getStackFactory();

    /**
     * A shortcut for {@link #syncToClient(ServerPlayer)} which checks if given player is a server sided one.
     * Can be used in any context, the syncing packet will only be sent from the server to the client.
     *
     * @param player player that may or may ont be the server sided one
     **/
    default void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    /**
     * Synchronizes the subpocket content from the server to the client.
     * <p>
     * If you are in a server-only context, you can make changes to the players {@link ISubpocket} and then
     * call this method to sync them to the client
     *
     * @param serverPlayer the server player being synced
     **/
    void syncToClient(ServerPlayer serverPlayer);
}
