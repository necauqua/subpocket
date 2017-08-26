/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Subpocket;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

public class PacketSyncStorage extends Packet {

    private NBTTagCompound nbt;

    public PacketSyncStorage() {}

    public PacketSyncStorage(EntityPlayer player) {
        nbt = CapabilitySubpocket.toNBT(player);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onClient(EntityPlayerSP player) {
        CapabilitySubpocket.fromNBT(player, nbt);
    }

    @Override
    public void read(PacketBuffer buf) {
        try {
            nbt = buf.readCompoundTag();
        }catch(IOException ioe) {
            Subpocket.logger.error("Failed reading storage NBT from sync packet! Corrupted packet?", ioe);
        }
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeCompoundTag(nbt);
    }
}
