/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.impl;

import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.IPositionedBigStackFactory;
import info.necauqua.mods.subpocket.gui.ContainerSubpocket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Random;

public class PositionedBigStackFactory implements IPositionedBigStackFactory {

    public static final PositionedBigStackFactory INSTANCE = new PositionedBigStackFactory();

    private final Random rand = new Random();

    private PositionedBigStackFactory() {}

    @Nonnull
    @Override
    public IPositionedBigStack empty() {
        return PositionedBigStack.EMPTY;
    }

    @Nonnull
    @Override
    public IPositionedBigStack create(ItemStack ref, BigInteger count, float x, float y) {
        return new PositionedBigStack(ref, count, x, y);
    }

    @Nonnull
    @Override
    public IPositionedBigStack create(ItemStack stack, float x, float y) {
        return create(stack, BigInteger.valueOf(stack.getCount()), x, y);
    }

    @Nonnull
    @Override
    public IPositionedBigStack create(ItemStack stack, BigInteger count) {
        int x = rand.nextInt(ContainerSubpocket.WIDTH - 18) + 1;
        int y = rand.nextInt(ContainerSubpocket.HEIGHT - 18) + 1;
        return create(stack, count, x, y);
    }

    @Nonnull
    @Override
    public IPositionedBigStack create(ItemStack stack) {
        return create(stack, BigInteger.valueOf(stack.getCount()));
    }

    @Nonnull
    @Override
    public IPositionedBigStack create(NBTTagCompound nbt) {
        PositionedBigStack stack = new PositionedBigStack();
        stack.deserializeNBT(nbt);
        return stack;
    }
}
