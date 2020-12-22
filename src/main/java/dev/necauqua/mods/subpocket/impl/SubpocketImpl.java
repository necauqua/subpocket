/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.impl;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.INBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class SubpocketImpl implements ISubpocket {

    private LazyOptional<ISubpocket> container;

    private LinkedList<ISubpocketStack> stacks = new LinkedList<>();
    private boolean unlocked = false;
    private StackSizeMode stackSizeMode = StackSizeMode.ALL;

    public SubpocketImpl() {
        onInvalidated(LazyOptional.empty());
    }

    private void onInvalidated(LazyOptional<ISubpocket> invalid) {
        LazyOptional<ISubpocket> newContainer = LazyOptional.of(() -> this);
        newContainer.addListener(this::onInvalidated);
        container = newContainer;
    }

    @Override
    public boolean isUnlocked() {
        return unlocked;
    }

    @Override
    public void unlock() {
        this.unlocked = true;
    }

    @Override
    public void lock() {
        this.unlocked = false;
    }

    @Override
    public StackSizeMode getStackSizeMode() {
        return stackSizeMode;
    }

    @Override
    public void setStackSizeMode(StackSizeMode stackSizeMode) {
        this.stackSizeMode = stackSizeMode;
    }

    @Override
    @Nonnull
    public List<ISubpocketStack> getStacksView() {
        return Collections.unmodifiableList(stacks);
    }

    @Override
    @Nonnull
    public ISubpocketStack get(int index) {
        return index >= 0 && index < stacks.size() ?
                stacks.get(index) :
                SubpocketStackImpl.EMPTY;
    }

    @Override
    @Nonnull
    public Iterator<ISubpocketStack> iterator() {
        return Iterators.unmodifiableIterator(stacks.iterator());
    }

    @Override
    public boolean add(@Nonnull ISubpocketStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ISubpocketStack matching = find(stack.getRef());
        if (!matching.isEmpty()) {
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
        if (stack.isEmpty()) {
            return false;
        }
        ISubpocketStack matching = find(stack);
        if (!matching.isEmpty()) {
            matching.setCount(matching.getCount().add(BigInteger.valueOf(stack.getCount())));
            elevate(matching);
            return false;
        }
        ISubpocketStack newStack = SubpocketStackFactoryImpl.INSTANCE.create(stack);
        newStack.setBoundStorage(this);
        stacks.add(newStack);
        return true;
    }

    @Override
    public boolean remove(@Nonnull ISubpocketStack stack) {
        if (stacks.remove(stack)) {
            stack.setBoundStorage(null);
            return true;
        }
        return false;
    }

    @Nonnull
    public ISubpocketStack find(@Nonnull ItemStack ref) {
        if (ref.isEmpty()) {
            return SubpocketStackImpl.EMPTY;
        }
        return stacks.stream()
                .filter(stack -> stack.matches(ref))
                .findFirst()
                .orElse(SubpocketStackImpl.EMPTY);
    }

    @Override
    public boolean elevate(@Nonnull ISubpocketStack stack) {
        if (stack.isEmpty() || !stacks.remove(stack)) {
            return false;
        }
        stacks.add(stack);
        return true;
    }

    @Override
    public void clear() {
        stacks = new LinkedList<>();
    }

    @Override
    public void cloneFrom(@Nonnull ISubpocket storage) {
        stacks = Lists.newLinkedList(storage);
        stacks.forEach(s -> s.setBoundStorage(this));
        unlocked = storage.isUnlocked();
        stackSizeMode = storage.getStackSizeMode();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        stacks.forEach(s -> list.add(s.serializeNBT()));
        nbt.setTag("storage", list);
        nbt.setBoolean("unlocked", unlocked);
        nbt.setByte("stackSizeMode", (byte) stackSizeMode.ordinal());
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        clear();
        unlocked = nbt.getBoolean("unlocked");
        stackSizeMode = StackSizeMode.values()[nbt.getByte("stackSizeMode") % StackSizeMode.values().length];
        INBTBase listBase = nbt.getTag("storage");
        if (!(listBase instanceof NBTTagList)) {
            return;
        }
        NBTTagList list = (NBTTagList) listBase;
        for (int i = 0; i < list.size(); i++) {
            ISubpocketStack stack = SubpocketStackFactoryImpl.INSTANCE
                    .create(list.getCompound(i));
            if (stack.isEmpty()) {
                continue; // in case we had some kind of malformed nbt, idk
            }
            stack.setBoundStorage(this);
            stacks.add(stack);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CapabilitySubpocket.INSTANCE ?
                container.cast() :
                LazyOptional.empty();
    }
}
