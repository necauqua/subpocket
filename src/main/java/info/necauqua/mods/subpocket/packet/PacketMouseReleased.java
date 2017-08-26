/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import info.necauqua.mods.subpocket.gui.ContainerSubpocket;
import info.necauqua.mods.subpocket.util.ClickState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

public class PacketMouseReleased extends Packet {

    private float x, y;
    private ClickState click;
    private int index;

    public PacketMouseReleased() {}

    public PacketMouseReleased(float x, float y, ClickState click, int index) {
        this.x = x;
        this.y = y;
        this.click = click;
        this.index = index;
    }

    @Override
    public void onServer(EntityPlayerMP player) {
        if(player.openContainer instanceof ContainerSubpocket) {
            ((ContainerSubpocket) player.openContainer).processClick(x, y, click, index);
        }
    }

    @Override
    public void read(PacketBuffer buf) {
        x = buf.readFloat();
        y = buf.readFloat();
        click = new ClickState(buf.readByte());
        index = buf.readInt();
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeFloat(x).writeFloat(y).writeByte(click.toByte()).writeInt(index);
    }
}
