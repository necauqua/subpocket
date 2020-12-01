/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.impl;

import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.api.ISubpocketStackFactory;
import dev.necauqua.mods.subpocket.gui.ContainerSubpocket;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;

public enum SubpocketStackFactoryImpl implements ISubpocketStackFactory {
    INSTANCE;

    @Nonnull
    @Override
    public ISubpocketStack empty() {
        return SubpocketStackImpl.EMPTY;
    }

    @Nonnull
    @Override
    public ISubpocketStack create(ItemStack ref, BigInteger count, float x, float y) {
        return new SubpocketStackImpl(ref, count, x, y);
    }

    @Nonnull
    @Override
    public ISubpocketStack create(NBTTagCompound nbt) {
        SubpocketStackImpl stack = new SubpocketStackImpl();
        stack.deserializeNBT(nbt);
        return stack;
    }
}
