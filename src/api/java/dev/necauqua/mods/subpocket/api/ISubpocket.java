/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Implementation of this interface does NOTHING network-related!<br>
 * You should either work in a context where equivalent code is executed on both sides
 * (which is still desync-prone) or work on the server side and use the {@link ISubpocketAPI#syncToClient(ServerPlayer)}
 * API.
 * <p>
 * Example usage:
 * <pre>
 * public static final Capability&lt;ISubpocket&gt; SUBPOCKET_CAPABILITY = CapabilityManager.get(new CapabilityToken&lt;&gt;(){});
 *
 * void method(Player player) {
 *     if (SUBPOCKET_CAPABILITY != null) { // if the mod was loaded
 *         player.getCapability(SUBPOCKET_CAPABILITY)
 *             .ifPresent(storage -&gt; { // should always be present, but we're safe
 *                 doStuffWith(storage);
 *             });
 *     }
 * }
 * </pre>
 **/
public interface ISubpocket extends ICapabilitySerializable<CompoundTag>, Iterable<ISubpocketStack> {

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
     * @return true if that stack was in this storage, and it was removed.
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
}
