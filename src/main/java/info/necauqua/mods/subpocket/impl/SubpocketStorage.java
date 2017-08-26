/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.impl;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.handlers.SubpocketConditions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SubpocketStorage implements ISubpocketStorage {

    private LinkedList<IPositionedBigStack> stacks = new LinkedList<>();
    private EntityPlayer player;

    public SubpocketStorage() {}

    public SubpocketStorage(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public boolean isAvailableToPlayer() {
        if(player == null) {
            Subpocket.logger.error("Called isAvailableToPlayer() with null player! This storage was never attached to player properly!");
            return false;
        }
        return SubpocketConditions.hasSubpocket(player);
    }

    @Override
    @Nonnull
    public List<IPositionedBigStack> getStacksView() {
        return Collections.unmodifiableList(stacks);
    }

    @Override
    @Nonnull
    public IPositionedBigStack get(int index) {
        if(index < 0 || index >= stacks.size()) {
            return PositionedBigStack.EMPTY;
        }
        return stacks.get(index);
    }

    @Override
    @Nonnull
    public Iterator<IPositionedBigStack> iterator() {
        return Iterators.unmodifiableIterator(stacks.iterator());
    }

    @Override
    public boolean add(@Nonnull IPositionedBigStack stack) {
        if(stack.isEmpty()) {
            return false;
        }
        IPositionedBigStack matching = find(stack.getRef());
        if(!matching.isEmpty()) {
            matching.setCount(matching.getCount().add(stack.getCount()));
            elevate(matching);
            return false;
        }
        stack.setBoundStorage(this);
        stacks.add(stack);
        return true;
    }

    @Override
    public boolean add(@Nonnull ItemStack stack) {
        return add(PositionedBigStackFactory.INSTANCE.create(stack));
    }

    @Override
    public boolean remove(@Nonnull IPositionedBigStack stack) {
        if(stacks.remove(stack)) {
            stack.setBoundStorage(null);
            return true;
        }
        return false;
    }

    @Nonnull
    public IPositionedBigStack find(@Nonnull ItemStack ref) {
        if(ref.isEmpty()) {
            return PositionedBigStack.EMPTY;
        }
        return stacks.stream().filter(stack -> stack.matches(ref)).findFirst().orElse(PositionedBigStack.EMPTY);
    }

    @Override
    public boolean elevate(@Nonnull IPositionedBigStack stack) {
        if(!stack.isEmpty() && stacks.remove(stack)) {
            stacks.add(stack);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        stacks = new LinkedList<>();
    }

    @Override
    public void cloneFrom(@Nonnull ISubpocketStorage storage) {
        stacks = Lists.newLinkedList(storage);
        stacks.forEach(s -> s.setBoundStorage(this));
    }

    @Override
    public NBTBase serializeNBT() {
        NBTTagList list = new NBTTagList();
        stacks.forEach(s -> list.appendTag(s.serializeNBT()));
        return list;
    }

    @Override
    public void deserializeNBT(NBTBase nbt) {
        clear();
        if(nbt instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) nbt;
            for(int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound stackNbt = list.getCompoundTagAt(i);
                IPositionedBigStack stack = new PositionedBigStack();
                stack.deserializeNBT(stackNbt);
                stack.setBoundStorage(this);
                stacks.add(stack);
            }
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilitySubpocket.IT;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilitySubpocket.IT ? CapabilitySubpocket.IT.cast(this) : null;
    }
}
