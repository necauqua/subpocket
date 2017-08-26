/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.math.BigInteger;

public interface IPositionedBigStackFactory {

    /**
      * Returns constant empty positioned big stack.
      * WARNING: please do not modify empty stack. Neither here nor vanilla one.
      * @return empty stack.
      **/
    @Nonnull
    IPositionedBigStack empty();

    /**
      * Full constructor of positioned big stacks.
      * It copies given reference stack and sets size of a copy to 1.
      * @param ref stack given for reference.
      * @param count given count of items.
      * @param x X coordinate.
      * @param y Y coordinate
      * @return stack instance.
      **/
    @Nonnull
    IPositionedBigStack create(ItemStack ref, BigInteger count, float x, float y);

    /**
      * Creates positioned big stack from common ItemStack.
      * It sets count to count of given stack and reference
      * to 1-sized copy of given stack.
      * @param stack stack given for reference and count.
      * @param x X coordinate.
      * @param y Y coordinate
      * @return stack instance.
      **/
    @Nonnull
    IPositionedBigStack create(ItemStack stack, float x, float y);

    /**
      * Creates positioned big stack with random coordinates.
      * @param ref stack given for reference.
      * @param count given count of items.
      * @return stack instance.
      **/
    @Nonnull
    IPositionedBigStack create(ItemStack ref, BigInteger count);

    /**
      * Creates positioned big stack with random coordinates
      * and count set from given stack.
      * @param stack stack given for reference.
      * @return stack instance.
      **/
    @Nonnull
    IPositionedBigStack create(ItemStack stack);

    /**
      * Deserializes positioned big stack from given NBT.
      * @param nbt given NBT.
      * @return stack instance.
      **/
    @Nonnull
    IPositionedBigStack create(NBTTagCompound nbt);
}
