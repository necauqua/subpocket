/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.util;

import info.necauqua.mods.subpocket.Network;
import info.necauqua.mods.subpocket.packet.PacketSyncNBTag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagByte;

public class StagedAction {

    private final String name;
    private final int size;

    public StagedAction(String name, int size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public boolean completed(EntityPlayer player) {
        return getState(player) >= size;
    }

    public boolean did(EntityPlayer player, int action) {
        return getState(player) >= action;
    }

    public boolean advance(EntityPlayer player, int action) {
        int pos = getState(player);
        if(action == pos + 1){
            setState(player, action);
            return true;
        }
        return false;
    }

    public boolean lock(EntityPlayer player, int action) {
        if(getState(player) >= action && action > 0) {
            setState(player, action - 1);
            return true;
        }
        return false;
    }

    public boolean unlock(EntityPlayer player, int action) {
        if(getState(player) < action) {
            setState(player, action);
            return true;
        }
        return false;
    }

    public int getState(EntityPlayer player) {
        return NBT.get(player).getByte(name);
    }

    public void setState(EntityPlayer player, int number) {
        NBT.get(player).setByte(name, (byte) number);
    }

    public void sync(EntityPlayer player) {
        if(player instanceof EntityPlayerMP) {
            Network.sendTo(new PacketSyncNBTag(name, new NBTTagByte((byte) getState(player))), (EntityPlayerMP) player);
        }
    }
}
