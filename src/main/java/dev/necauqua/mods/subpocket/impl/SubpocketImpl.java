/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.impl;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import dev.necauqua.mods.subpocket.SubpocketCapability;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
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
        var matching = find(stack.getRef());
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
        var matching = find(stack);
        if (!matching.isEmpty()) {
            matching.setCount(matching.getCount().add(BigInteger.valueOf(stack.getCount())));
            elevate(matching);
            return false;
        }
        var newStack = SubpocketStackFactoryImpl.INSTANCE.create(stack);
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
    }

    @Override
    public CompoundTag serializeNBT() {
        var nbt = new CompoundTag();
        var list = new ListTag();
        stacks.forEach(s -> list.add(s.serializeNBT()));
        nbt.put("storage", list);
        nbt.putBoolean("unlocked", unlocked);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        clear();
        unlocked = nbt.getBoolean("unlocked");
        var listBase = nbt.get("storage");
        if (!(listBase instanceof ListTag list)) {
            return;
        }
        for (var i = 0; i < list.size(); i++) {
            var stack = SubpocketStackFactoryImpl.INSTANCE
                .create(list.getCompound(i));
            if (stack.isEmpty()) {
                continue; // in case we had some kind of malformed nbt, idk
            }
            stack.setBoundStorage(this);
            stacks.add(stack);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction direction) {
        return capability == SubpocketCapability.INSTANCE ?
                container.cast() :
                LazyOptional.empty();
    }
}
