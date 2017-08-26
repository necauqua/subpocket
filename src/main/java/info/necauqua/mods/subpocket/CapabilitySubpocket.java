/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket;

import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.impl.SubpocketStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber(modid = Subpocket.MODID)
public class CapabilitySubpocket {

    @CapabilityInject(ISubpocketStorage.class)
    public static Capability<ISubpocketStorage> IT;

    public static ISubpocketStorage get(EntityPlayer player) {
        ISubpocketStorage storage = player.getCapability(IT, null);
        if(storage == null) {
            throw new NullPointerException("Player storage capability was null! " +
                    "If that happened, something was really wrong, enough to crash the game!");
        }
        return storage;
    }

    public static NBTTagCompound toNBT(EntityPlayer player) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("storage", get(player).serializeNBT());
        return nbt;
    }

    public static void fromNBT(EntityPlayer player, NBTTagCompound nbt) {
        if(nbt != null && nbt.hasKey("storage")) {
            get(player).deserializeNBT(nbt.getTag("storage"));
        }
    }

    public static void init() {
        CapabilityManager.INSTANCE.register(ISubpocketStorage.class, new Capability.IStorage<ISubpocketStorage>() {

            @Override
            public NBTBase writeNBT(Capability<ISubpocketStorage> capability, ISubpocketStorage instance, EnumFacing side) {
                return instance.serializeNBT();
            }

            @Override
            public void readNBT(Capability<ISubpocketStorage> capability, ISubpocketStorage instance, EnumFacing side, NBTBase nbt) {
                instance.deserializeNBT(nbt);
            }
        }, SubpocketStorage::new);
    }

    @SubscribeEvent
    public static void onEntityCaps(AttachCapabilitiesEvent<Entity> e) {
        if(e.getObject() instanceof EntityPlayer) {
            e.addCapability(
                new ResourceLocation(Subpocket.MODID, "storage"),
                new SubpocketStorage((EntityPlayer) e.getObject())
            );
        }
    }
}
