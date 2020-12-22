/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.impl;

import dev.necauqua.mods.subpocket.ContainerSubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;

public final class SubpocketStackImpl implements ISubpocketStack {

    public static final ItemStack ZEROEMPTY = new ItemStack(Blocks.AIR, 0);
    public static final ISubpocketStack EMPTY = new SubpocketStackImpl(ZEROEMPTY, BigInteger.ZERO, 0, 0);

    private ISubpocket storage;
    private ItemStack ref;
    private BigInteger count;
    private float x, y;

    SubpocketStackImpl() {} // null-constructor for (nbt-)serialization

    public SubpocketStackImpl(ItemStack ref, BigInteger count, float x, float y) {
        this.ref = ref.copy();
        this.ref.setCount(1);
        this.count = count;
        this.x = x;
        this.y = y;
    }

    @Override
    public ISubpocket getBoundStorage() {
        return storage;
    }

    @Override
    public void setBoundStorage(@Nullable ISubpocket storage) {
        this.storage = storage;
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || ref.isEmpty() || count.signum() <= 0;
    }

    @Override
    @Nonnull
    public ItemStack getRef() {
        ItemStack copy = ref.copy();
        if (count.compareTo(BigInteger.valueOf(copy.getMaxStackSize())) > 0) {
            copy.setCount(copy.getMaxStackSize());
        } else {
            copy.setCount(count.intValue());
        }
        return copy;
    }

    @Override
    public boolean matches(ItemStack stack) {
        boolean empty = isEmpty();
        if (empty ^ stack.isEmpty()) {
            return false;
        }
        if (empty) {
            return true;
        }
        ItemStack copy = ref.copy();
        copy.setCount(stack.getCount());
        return ItemStack.areItemStacksEqual(stack, copy);
    }

    @Override
    @Nonnull
    public BigInteger getCount() {
        return count;
    }

    @Override
    public void setCount(BigInteger count) {
        if (count.signum() > 0) {
            this.count = count;
            return;
        }
        if (storage != null) {
            storage.remove(this);
        }
        this.count = BigInteger.ZERO;
    }

    @Override
    @Nonnull
    public ItemStack retrieve(int n) {
        if (n <= 0) {
            return ZEROEMPTY;
        }
        ItemStack stack = ref.copy();
        BigInteger size = count.min(BigInteger.valueOf(n));
        setCount(count.subtract(size));
        stack.setCount(size.intValue());
        return stack;
    }

    @Override
    @Nonnull
    public ItemStack retrieveMax() {
        return retrieve(ref.getMaxStackSize());
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public void setPos(float x, float y) {
        this.x = MathHelper.clamp(x, -15, ContainerSubpocket.WIDTH - 1);
        this.y = MathHelper.clamp(y, -15, ContainerSubpocket.HEIGHT - 1);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagCompound refNbt = new NBTTagCompound();
        ref.write(refNbt);
        refNbt.removeTag("Count"); // eh, but why to store it, huh
        nbt.setTag("ref", refNbt);
        nbt.setByteArray("count", count.toByteArray());
        nbt.setFloat("x", x);
        nbt.setFloat("y", y);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagCompound refNbt = nbt.getCompound("ref");
        refNbt.setByte("Count", (byte) 1); // as above      ^
        ref = ItemStack.read(refNbt);
        count = nbt.hasKey("count") && nbt.getTagId("count") == 7 ?
                new BigInteger(nbt.getByteArray("count")) :
                BigInteger.ONE;
        setPos(nbt.getFloat("x"), nbt.getFloat("y"));
    }
}
