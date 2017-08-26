/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.math.BigInteger;

public interface IPositionedBigStack extends INBTSerializable<NBTTagCompound> {

    /**
      * Each positioned stack is bound to storage it exists in.
      * This is done so when stack is emptied (count set to zero or ref stack is empty)
      * is automatically removes itself from that storage.
      * One stack could be only bound to one storage for this to behave properly.
      * @return bound storage or null.
      **/
    @Nullable
    ISubpocketStorage getBoundStorage();

    /**
      * Sets bound storage for this stack.
      * @param storage bound storage or null.
      **/
    void setBoundStorage(@Nullable ISubpocketStorage storage);

    /**
      * Similar to ItemStack#isEmpty, this returns true if reference stack is empty or if count is zero.
      * Such stack can't be held in a storage, if it is then something is broken.
      * @return true if this stack is empty.
      **/
    boolean isEmpty();

    /**
      * Gives a <b>copy</b> of a reference item stack.
      * A copy is so that actual reference cannot be changed.
      * This is used for matching and to create actual ItemStacks from this big stack.
      * NOTE: count of returned copy is set to min(maxStackSize, intCount)
      * where intCount is count of this stack clamped to integer.
      * This is done for items whose rendering depend on stack size.
      * That also means that if count is zero, both {@link #isEmpty()}
      * and getRef().{@link ItemStack#isEmpty() isEmpty()} are true.
      * Since this returns a copy, that copy can itself be modified freely.
      * @return a copy of reference stack.
      **/
    ItemStack getRef();

    /**
      * Checks if given stack matches the reference stack.
      * Basically, {@link ItemStack#areItemStacksEqual} without count check.
      * @param stack ItemStack to match against.
      * @return true is such item matches this stack.
      **/
    boolean matches(ItemStack stack);

    /**
      * So true infinites are not available in out finite computers, but bigints are good enough
      * as their maximum in Java 8 docs said to be at least 2^Integer.MAX_VALUE which is a humongous number.
      * @return count of this stack.
      **/
    BigInteger getCount();

    /**
      * Sets the new count to this stack.
      * If count is not positive, then this stack is marked as empty (by setting count to zero)
      * and it is removed from bound storage if there was any.
      * @param count new count to set.
      **/
    void setCount(BigInteger count);

    /**
      * Integer version of {@link #setCount(BigInteger)}
      * @param count new count to set.
      **/
    void setCount(int count);

    /**
      * Creates a copy of ref with given number of items and subtracts this number from count.
      * If count is less than or equal to that number then read {@link #setCount setCount} for non-positive counts.
      * If given number is not positive returns an empty itemstack with count set to 0
      * (default ItemStack.EMPTY has count set to 1).
      * @param n number of items.
      * @return retrieved ItemStack.
      **/
    ItemStack retrieve(int n);

    /**
      * Save as {@link #retrieve(int)}, but uses stacks max size automatically.
      * @return retrieved ItemStack.
      **/
    ItemStack retrieveMax();

    /**
      * Checked shortcut for a commonly used construct:
      * <pre>
      * stack.grow(matching.retrieve(stack.getMaxStackSize() - stack.getCount()).getCount())
      * </pre>
      * where matching is this big stack when it matches given stack.
      * @param stack stack to fill from this big stack.
      * @return true if it matched and was filled by at least one item.
      **/
    boolean fill(ItemStack stack);

    /**
      * Similar to {@link #fill}, this is a checked shortcut for
      * <pre>
      * stack.grow(matching.retrieve(Math.min(n, stack.getMaxStackSize() - stack.getCount())).getCount())
      * </pre>
      * where matching is this big stack when it matches given stack.
      * @param stack stack to feed from this big stack.
      * @return true if it matched and was filled by at least one item.
      **/
    boolean feed(ItemStack stack, int n);

    /**
      * Relative X coordinate of upper-left corner of this stack in subpocket field.
      * @return stacks X coordinante.
      **/
    float getX();

    /**
      * Relative Y coordinate of upper-left corner of this stack in subpocket field.
      * @return stacks Y coordinante.
      **/
    float getY();

    /**
      * Sets the new position of this stack.
      * Position is clamped in range [-15;(width or height respectively)-1].
      * @param x new X coordinate.
      * @param y new Y coordinate.
      **/
    void setPos(float x, float y);

    /**
      * Formats count of this stack to 4-character string, used in stack rendering.
      * @return 4-character string representation.
      **/
    String getShortNumberString();
}
