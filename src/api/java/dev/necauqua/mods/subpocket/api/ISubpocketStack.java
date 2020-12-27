/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;

public interface ISubpocketStack extends INBTSerializable<CompoundNBT> {

    // semi-easter-egg (well, how does one get to omg-illion items?) - Clicker Heroes legend is used here
    // (except that `O` is replaced by `o` because in minecraft font it is too similar to zero)
    String LEGEND = "KMBTqQsSoNdUD!@#$%^&*[]{};':\"<>?/\\|~`_=-+";
    BigInteger THOUSAND = BigInteger.valueOf(1000);

    /**
     * Each positioned stack is bound to storage it exists in.
     * This is done so when stack is emptied (count set to zero or ref stack is empty)
     * is automatically removes itself from that storage.
     * One stack could be only bound to one storage for this to behave properly.
     *
     * @return bound storage or null.
     **/
    @Nullable
    ISubpocket getBoundStorage();

    /**
     * Sets bound storage for this stack.
     *
     * @param storage bound storage or null.
     **/
    void setBoundStorage(@Nullable ISubpocket storage);

    /**
     * Similar to ItemStack#isEmpty, this returns true if reference stack is empty or if count is zero.
     * Such stack can't be held in a storage, if it is then something is broken.
     *
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
     *
     * @return a copy of reference stack.
     **/
    ItemStack getRef();

    /**
     * Checks if given stack matches the reference stack.
     * Basically, {@link ItemStack#areItemStacksEqual} without count check.
     *
     * @param stack ItemStack to match against.
     * @return true is such item matches this stack.
     **/
    boolean matches(ItemStack stack);

    /**
     * So true infinites are not available in out finite computers, but bigints are good enough
     * as their maximum in Java 8 docs said to be at least 2^Integer.MAX_VALUE which is a humongous number.
     *
     * @return count of this stack.
     **/
    BigInteger getCount();

    /**
     * Sets the new count to this stack.
     * If count is not positive, then this stack is marked as empty (by setting count to zero)
     * and it is removed from bound storage if there was any.
     *
     * @param count new count to set.
     **/
    void setCount(BigInteger count);

    /**
     * Integer version of {@link #setCount(BigInteger)}
     *
     * @param count new count to set.
     **/
    default void setCount(long count) {
        setCount(BigInteger.valueOf(count));
    }

    /**
     * Creates a copy of ref with given number of items and subtracts this number from count.
     * If count is less than or equal to that number then read {@link #setCount setCount} for non-positive counts.
     * If given number is not positive returns an empty itemstack with count set to 0
     * (default ItemStack.EMPTY has count set to 1).
     *
     * @param n number of items.
     * @return retrieved ItemStack.
     **/
    ItemStack retrieve(int n);

    /**
     * Save as {@link #retrieve(int)}, but uses stacks max size automatically.
     *
     * @return retrieved ItemStack.
     **/
    ItemStack retrieveMax();

    /**
     * Checked shortcut for a commonly used construct:
     * <pre>
     * stack.grow(matching.retrieve(stack.getMaxStackSize() - stack.getCount()).getCount())
     * </pre>
     * where matching is this big stack when it matches given stack.
     *
     * @param stack stack to fill from this big stack.
     * @return true if it matched and was filled by at least one item.
     **/
    default boolean fill(ItemStack stack) {
        if (stack.isEmpty() || isEmpty() || !matches(stack) || stack.getCount() >= stack.getMaxStackSize()) {
            return false;
        }
        stack.grow(retrieve(stack.getMaxStackSize() - stack.getCount()).getCount());
        return true;
    }

    /**
     * Similar to {@link #fill}, this is a checked shortcut for
     * <pre>
     * stack.grow(matching.retrieve(Math.min(n, stack.getMaxStackSize() - stack.getCount())).getCount())
     * </pre>
     * where matching is this big stack when it matches given stack.
     *
     * @param stack stack to feed from this big stack.
     * @return true if it matched and was filled by at least one item.
     **/
    default boolean feed(ItemStack stack, int n) {
        if (stack.isEmpty() || isEmpty() || stack.getCount() >= stack.getMaxStackSize() || !matches(stack)) {
            return false;
        }
        stack.grow(retrieve(Math.min(n, stack.getMaxStackSize() - stack.getCount())).getCount());
        return true;
    }

    /**
     * Relative X coordinate of upper-left corner of this stack in the subpocket field.
     *
     * @return stacks X coordinante.
     **/
    float getX();

    /**
     * Relative Y coordinate of upper-left corner of this stack in the subpocket field.
     *
     * @return stacks Y coordinante.
     **/
    float getY();

    /**
     * Sets the new position of this stack.
     * Position is clamped in range [-15;(width or height respectively)-1].
     *
     * @param x new X coordinate.
     * @param y new Y coordinate.
     **/
    void setPos(float x, float y);

    /**
     * Formats count of this stack to 4-character string, used in stack rendering.
     *
     * @return 4-character string representation.
     **/
    @Nonnull
    default String getShortNumberString() {
        BigInteger count = getCount();
        if (count.compareTo(BigInteger.ONE) <= 0) {
            return "";
        }
        String s = count.toString();
        if (count.compareTo(THOUSAND) < 0) {
            return s;
        }
        int m = ((s.length() - 1) % 3) + 1;
        int d = s.length() / 3 - (s.length() % 3 == 0 ? 2 : 1);
        return d < LEGEND.length() ? s.substring(0, m) + LEGEND.charAt(d) : "ALOT";
    }
}
