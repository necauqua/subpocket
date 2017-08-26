/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.impl;

import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.gui.ContainerSubpocket;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;

public class PositionedBigStack implements IPositionedBigStack {

    // semi-easter-egg (well, how does one get to omg-illion items?) - Clicker Heroes legend is used here
    // (except that `O` is replaced by `o` because in minecraft font it is too similar to zero)
    public static final String LEGEND = "KMBTqQsSoNdUD!@#$%^&*[]{};':\"<>?/\\|~`_=-+";
    public static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    public static final ItemStack ZEROEMPTY = new ItemStack(Blocks.AIR, 0);
    public static final IPositionedBigStack EMPTY = new PositionedBigStack(ZEROEMPTY, BigInteger.ZERO, 0, 0);

    private ISubpocketStorage storage;
    private ItemStack ref;
    private BigInteger count;
    private float x, y;

    public PositionedBigStack() {} // null-constructor for (nbt-)serialization

    public PositionedBigStack(ItemStack ref, BigInteger count, float x, float y) {
        this.ref = ref.copy();
        this.ref.setCount(1);
        this.count = count;
        this.x = x;
        this.y = y;
    }

    @Override
    public ISubpocketStorage getBoundStorage() {
        return storage;
    }

    @Override
    public void setBoundStorage(@Nullable ISubpocketStorage storage) {
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
        if(count.compareTo(BigInteger.valueOf(copy.getMaxStackSize())) > 0) {
            copy.setCount(copy.getMaxStackSize());
        }else {
            copy.setCount(count.intValue());
        }
        return copy;
    }

    @Override
    public boolean matches(ItemStack stack) {
        boolean empty = isEmpty();
        if(empty ^ stack.isEmpty()) {
            return false;
        }
        if(empty) {
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
        if(count.signum() <= 0) {
            if(storage != null) {
                storage.remove(this);
            }
            this.count = BigInteger.ZERO;
        }else {
            this.count = count;
        }
    }

    @Override
    public void setCount(int count) {
        setCount(BigInteger.valueOf(count));
    }

    @Override
    @Nonnull
    public ItemStack retrieve(int n) {
        if(n <= 0) {
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
    public boolean fill(ItemStack stack) {
        if(!stack.isEmpty() && matches(stack) && stack.getCount() < stack.getMaxStackSize()) {
            stack.grow(retrieve(stack.getMaxStackSize() - stack.getCount()).getCount());
            return true;
        }
        return false;
    }

    @Override
    public boolean feed(ItemStack stack, int n) {
        if(!stack.isEmpty() && matches(stack) && stack.getCount() < stack.getMaxStackSize()) {
            stack.grow(retrieve(Math.min(n, stack.getMaxStackSize() - stack.getCount())).getCount());
            return true;
        }
        return false;
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
    @Nonnull
    public String getShortNumberString() {
        if(count.equals(BigInteger.ONE)) {
            return "";
        }
        String s = count.toString();
        if(count.compareTo(THOUSAND) < 0) {
            return s;
        }
        int m = ((s.length() - 1) % 3) + 1;
        int d = s.length() / 3 - (s.length() % 3 == 0 ? 2 : 1);
        return d < LEGEND.length() ? s.substring(0, m) + LEGEND.charAt(d) : "ALOT";
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagCompound refNbt = new NBTTagCompound();
        ref.writeToNBT(refNbt);
        refNbt.removeTag("Count"); // eh, but why to store it, huh
        nbt.setTag("ref", refNbt);
        nbt.setByteArray("count", count.toByteArray());
        nbt.setFloat("x", x);
        nbt.setFloat("y", y);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagCompound refNbt = nbt.getCompoundTag("ref");
        refNbt.setByte("Count", (byte) 1); // as above      ^
        ref = new ItemStack(refNbt);
        count = nbt.hasKey("count", 7) ? new BigInteger(nbt.getByteArray("count")) : BigInteger.ONE;
        setPos(nbt.getFloat("x"), nbt.getFloat("y"));
    }
}
