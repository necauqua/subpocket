/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Subpocket;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketOpenPocket extends Packet {

    @Override
    public void onServer(EntityPlayerMP player) {
        if(CapabilitySubpocket.get(player).isAvailableToPlayer()) {
            player.openGui(Subpocket.instance, 0, player.world, 0, 0, 0);
        }
    }
}
