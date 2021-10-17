/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Random;

public interface ISubpocketStackFactory {

    /**
     * Returns constant empty positioned big stack.
     * WARNING: please do not modify empty stack. Neither here nor vanilla one.
     *
     * @return empty stack.
     **/
    @Nonnull
    ISubpocketStack empty();

    /**
     * Full constructor of positioned big stacks.
     * It copies given reference stack and sets size of a copy to 1.
     *
     * @param ref   stack given for reference.
     * @param count given count of items.
     * @param x     X coordinate.
     * @param y     Y coordinate
     * @return stack instance.
     **/
    @Nonnull
    ISubpocketStack create(ItemStack ref, BigInteger count, float x, float y);

    /**
     * Creates positioned big stack from common ItemStack.
     * It sets count to count of given stack and reference
     * to 1-sized copy of given stack.
     *
     * @param stack stack given for reference and count.
     * @param x     X coordinate.
     * @param y     Y coordinate
     * @return stack instance.
     **/
    @Nonnull
    default ISubpocketStack create(ItemStack stack, float x, float y) {
        return create(stack, BigInteger.valueOf(stack.getCount()), x, y);
    }

    /**
     * Creates positioned big stack with random coordinates.
     * This is used when shift-clicking a new item into the subpocket.
     *
     * @param ref stack given for reference.
     * @param count given count of items.
     * @return stack instance.
     **/
    @Nonnull
    default ISubpocketStack create(ItemStack ref, BigInteger count) {
        var a = Objects.hashCode(ref.getItem().getRegistryName());
        var b = Objects.hashCode(ref.getTag());
        var rand = new Random(31L * a + b); // meh
        var x = rand.nextInt(160 - 18) + 1;
        var y = rand.nextInt(70 - 18) + 1;
        return create(ref, count, x, y);
    }

    /**
     * Creates positioned big stack with random coordinates
     * and count set from given stack.
     *
     * @param ref stack given for reference.
     * @return stack instance.
     **/
    @Nonnull
    default ISubpocketStack create(ItemStack ref) {
        return create(ref, BigInteger.valueOf(ref.getCount()));
    }

    /**
     * Deserializes positioned big stack from given NBT.
     *
     * @param nbt given NBT.
     * @return stack instance.
     **/
    @Nonnull
    ISubpocketStack create(CompoundTag nbt);
}
