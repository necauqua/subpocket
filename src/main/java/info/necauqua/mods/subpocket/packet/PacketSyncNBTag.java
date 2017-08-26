/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.packet;

import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.util.NBT;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

public class PacketSyncNBTag extends Packet {

    private String key;
    private NBTBase tag;

    public PacketSyncNBTag() {}

    public PacketSyncNBTag(String key, NBTBase tag) {
        this.key = key;
        this.tag = tag;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onClient(EntityPlayerSP player) {
        NBT.get(player).setTag(key, tag);
    }

    @Override
    public void read(PacketBuffer buf) {
        key = buf.readString(16);
        try {
            NBTTagCompound holder = buf.readCompoundTag();
            if(holder != null && holder.hasKey("tag")) {
                tag = holder.getTag("tag");
            }else {
                Subpocket.logger.error("Corrupted NBT was read from NBT-sync packet!");
            }
        }catch(IOException ioe) {
            Subpocket.logger.error("Failed reading NBT-sync packet! Was it corrupted?", ioe);
        }
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(key);
        NBTTagCompound holder = new NBTTagCompound();
        holder.setTag("tag", tag);
        buf.writeCompoundTag(holder);
    }
}
