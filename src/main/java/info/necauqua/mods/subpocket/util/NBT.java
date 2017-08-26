/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.util;

import info.necauqua.mods.subpocket.Subpocket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

// whole separate util class for those two methods
public final class NBT {

    private NBT() {}

    public static NBTTagCompound get(EntityPlayer player) {
        return getset(getset(player.getEntityData(), EntityPlayer.PERSISTED_NBT_TAG), Subpocket.MODID);
    }

    private static NBTTagCompound getset(NBTTagCompound nbt, String key) {
        NBTBase tag = nbt.getTag(key);
        //noinspection ConstantConditions // it freaking CAN BE NULL, stupid wrong annotations
        if(tag == null || tag.getId() != 10) {
            tag = new NBTTagCompound();
            nbt.setTag(key, tag);
        }
        try {
            return (NBTTagCompound) tag;
        }catch(ClassCastException cce) {
            throw new IllegalStateException("NBT tag with id 10 was not NBT compound tag! Complete nonsense!", cce);
        }
    }
}
