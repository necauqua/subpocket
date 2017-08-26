/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class Packet {

    public void read(PacketBuffer buf) {}

    public void write(PacketBuffer buf) {}

    @SideOnly(Side.CLIENT)
    public void onClient(EntityPlayerSP player) {}

    public void onServer(EntityPlayerMP player) {}
}
