/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import info.necauqua.mods.subpocket.gui.ContainerSubpocket;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class PacketStackMoved extends Packet {

    private float x, y;
    private int index;

    public PacketStackMoved() {}

    public PacketStackMoved(float x, float y, int index) {
        this.x = x;
        this.y = y;
        this.index = index;
    }

    @Override
    public void onServer(EntityPlayerMP player) {
        if(player.openContainer instanceof ContainerSubpocket) {
            ((ContainerSubpocket) player.openContainer).stackMoved(x, y, index);
        }
    }

    @Override
    public void read(PacketBuffer buf) {
        x = buf.readFloat();
        y = buf.readFloat();
        index = buf.readInt();
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeFloat(x).writeFloat(y).writeInt(index);
    }
}
