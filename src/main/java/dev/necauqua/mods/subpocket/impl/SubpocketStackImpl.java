/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.impl;

import dev.necauqua.mods.subpocket.SubpocketContainer;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

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
        var copy = ref.copy();
        if (count.compareTo(BigInteger.valueOf(copy.getMaxStackSize())) > 0) {
            copy.setCount(copy.getMaxStackSize());
        } else {
            copy.setCount(count.intValue());
        }
        return copy;
    }

    @Override
    public boolean matches(ItemStack stack) {
        var empty = isEmpty();
        if (empty ^ stack.isEmpty()) {
            return false;
        }
        if (empty) {
            return true;
        }
        var copy = ref.copy();
        copy.setCount(stack.getCount());
        return ItemStack.matches(stack, copy);
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
        var stack = ref.copy();
        var size = count.min(BigInteger.valueOf(n));
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
        this.x = Mth.clamp(x, -15, SubpocketContainer.WIDTH - 1);
        this.y = Mth.clamp(y, -15, SubpocketContainer.HEIGHT - 1);
    }

    @Override
    public CompoundTag serializeNBT() {
        var nbt = new CompoundTag();
        var refNbt = ref.serializeNBT();
        refNbt.remove("Count"); // eh, but why to store it, huh
        nbt.put("ref", refNbt);
        nbt.putByteArray("count", count.toByteArray());
        nbt.putFloat("x", x);
        nbt.putFloat("y", y);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        var refNbt = nbt.getCompound("ref");
        refNbt.putByte("Count", (byte) 1); // as above      ^
        ref = ItemStack.of(refNbt);
        count = nbt.contains("count", 7) ?
            new BigInteger(nbt.getByteArray("count")) :
            BigInteger.ONE;
        setPos(nbt.getFloat("x"), nbt.getFloat("y"));
    }
}
