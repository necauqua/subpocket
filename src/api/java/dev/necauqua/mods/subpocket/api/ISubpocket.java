/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Implementation of this interface does NOTHING network-related!<br>
 * You should call methods on both sides to use this properly and not desync.
 * <p>
 * Example usage:
 * <pre>
 * &#64;CapabilityInject(ISubpocket.class)
 * public static Capability&lt;ISubpocket&gt; SUBPOCKET_CAPABILITY;
 *
 * void method(PlayerEntity player) {
 *     if (SUBPOCKET_STORAGE_CAPABILITY != null) { // if the mod was loaded
 *         player.getCapability(SUBPOCKET_STORAGE_CAPABILITY)
 *             .ifPresent(storage -&gt; {
 *                 doStuffWith(storage);
 *             });
 *     }
 * }
 * </pre>
 **/
public interface ISubpocket extends ICapabilitySerializable<CompoundNBT>, Iterable<ISubpocketStack> {

    /**
     * Returns the state of the players subpocket.
     * Always returns true for creative players.
     *
     * @return true if this storage is available to the player.
     **/
    boolean isUnlocked();

    /**
     * Locks the subpocket, as if it was never unlocked.
     */
    void lock();

    /**
     * Unlocks the subpocket, making it available to the player.
     */
    void unlock();

    /**
     * Returns current display of stack sizes set by the subpocket owner.
     */
    StackSizeMode getStackSizeMode();

    /**
     * Sets the display of stack sizes for the subpocket owner.
     */
    void setStackSizeMode(StackSizeMode stackSizeMode);

    /**
     * Returns an unmodifiable view of current stacks in this storage.
     * Used mostly for rendering.
     *
     * @return list of current stacks.
     **/
    List<ISubpocketStack> getStacksView();

    /**
     * Shortcut for getStacksView().get(index) with index check.
     * If index is out of bounds returns empty stack.
     *
     * @param index index of the stack.
     * @return stack at given index or empty stack if index was out of bounds.
     **/
    ISubpocketStack get(int index);

    /**
     * Adds a positioned big stack to this storage.
     * If it is merged with existing one - that existing stack will be
     * {@link #elevate elevated} to the top and method would return false.
     * Otherwise, given stack would be added to stack list and bound to
     * this storage and method would return true.
     *
     * @param stack stack to be added.
     * @return true if stack was not merged with existing one.
     **/
    boolean add(@Nonnull ISubpocketStack stack);

    /**
     * A shortcut for adding unwrapped stacks with no positional context.
     * Without matching stack present in the storage adds new stack at random position.
     * Random range is [1;(width or height respectively) - 17]
     *
     * @param stack stack to be added.
     * @return true if stack was not merged with existing one.
     * @see #add(ISubpocketStack)
     **/
    boolean add(@Nonnull ItemStack stack);

    /**
     * Removes a positioned big stack from this storage.
     * <p>
     * Note - this works by reference equality and does not remove stacks
     * that are just matching, nor does it any stack size calculations.
     * For that, you should use {@link #find} and then modify the found stack,
     * e.g. <code>find(new ItemStack(Items.EGG)).retrieve(1)</code> to fetch one
     * item from the subpocket.
     *
     * @param stack stack to be removed.
     * @return true if that stack was in this storage and it was removed.
     **/
    boolean remove(@Nonnull ISubpocketStack stack);

    /**
     * Searches this storage for matching big stack.
     *
     * @param ref reference stack to search.
     * @return found stack or empty.
     **/
    ISubpocketStack find(@Nonnull ItemStack ref);

    /**
     * Removes this stack from storage and then puts it back at the end.
     * Used in {@link #add} (so, in any stack addition) and in stack dragging.
     * Even if stack was already at the top this still returns true.
     *
     * @param stack stack to be moved to the top.
     * @return true if stack was in this storage.
     **/
    boolean elevate(@Nonnull ISubpocketStack stack);

    /**
     * Removes all stacks from this storage.
     **/
    void clear();

    /**
     * Replaces all data with one from given storage.
     * Used for persisting it over player cloning.
     *
     * @param storage old storage.
     **/
    void cloneFrom(@Nonnull ISubpocket storage);

    /**
     * Variants of how stack sizes can be shown in the subpocket field.
     */
    enum StackSizeMode {
        /**
         * Show all numbers. The default, works nicely at the beggining
         * where there is not a lot of item types stored, but very
         * obstructive later on.
         */
        ALL,
        /**
         * Show the number only for hovered items, matter of preference
         * between this or {@link #NONE}.
         */
        HOVERED,
        /**
         * Don't show numbers at items at all, only way to know the number
         * of items in subpocket stack is by the tooltip.
         */
        NONE,
    }
}
