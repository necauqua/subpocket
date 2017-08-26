/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.List;

/**
  * Implementation of this interface does NOTHING network-related!
  * So methods should be called on both sides to function properly and not desynchronize.
  *
  * Example usage:
  * <pre>
  * &#64;CapabilityInject(ISubpocketStorage.class)
  * public static Capability&lt;ISubpocketStorage&gt; SUBPOCKET_STORAGE_CAPABILITY;
  *
  * void method(EntityPlayer player) {
  *     if(SUBPOCKET_STORAGE_CAPABILITY != null) { // if the mod was loaded
  *         ISubpocketStorage storage = player.getCapability(SUBPOCKET_STORAGE_CAPABILITY, null);
  *         // if player doesn't have it, something gone so wrong that NPE here would be fine
  *         doStuffWith(storage);
  *     }
  * }
  * </pre>
  **/
public interface ISubpocketStorage extends ICapabilitySerializable<NBTBase>, Iterable<IPositionedBigStack> {

    /**
      * Until player unlocks the pocket by doing specific actions, this method will return false.
      * Always returns true for creative players.
      * @return true if this storage is available to the player.
      **/
    boolean isAvailableToPlayer();

    /**
      * Returns an unmodifiable view of current stacks in this storage.
      * Used mostly for rendering.
      * @return list of current stacks.
      **/
    List<IPositionedBigStack> getStacksView();

    /**
      * Shortcut for getStacksView().get(index) with index check.
      * If index is out of bounds returns empty stack.
      * @param index index of the stack.
      * @return stack at given index or empty stack if index was out of bounds.
      **/
    IPositionedBigStack get(int index);

    /**
      * Adds a positioned big stack to this storage.
      * If it is merged with existing one - that existing stack will be
      * {@link #elevate elevated} to the top and method would return false.
      * Otherwise, given stack would be added to stack list and bound to
      * this storage and method would return true.
      * @param stack stack to be added.
      * @return true if stack was not merged with existing one.
      **/
    boolean add(@Nonnull IPositionedBigStack stack);

    /**
      * A shortcut for adding unwrapped stacks with no positional context.
      * Without matching stack present in the storage adds new stack at random position.
      * Random range is [1;(width or height respectively) - 17]
      * @param stack stack to be added.
      * @return true if stack was not merged with existing one.
      * @see #add(IPositionedBigStack)
      **/
    boolean add(@Nonnull ItemStack stack);

    /**
      * Removes a positioned big stack from this storage.
      * @param stack stack to be removed.
      * @return true if that stack was in this storage and it was removed.
      **/
    boolean remove(@Nonnull IPositionedBigStack stack);

    /**
      * Searches this storage for matching big stack.
      * @param ref reference stack to search.
      * @return found stack or empty.
      **/
    IPositionedBigStack find(@Nonnull ItemStack ref);

    /**
      * Removes this stack from storage and then puts it back at the end.
      * Used in {@link #add} (so, in any stack addition) and in stack dragging.
      * Even if stack was already at the top this still returns true.
      * @param stack stack to be moved to the top.
      * @return true if stack was in this storage.
      **/
    boolean elevate(@Nonnull IPositionedBigStack stack);

    /**
      * Removes all stacks from this storage.
      **/
    void clear();

    /**
      * Replaces all data with one from given storage.
      * Used for persisting it over player cloning.
      * @param storage old storage.
      **/
    void cloneFrom(@Nonnull ISubpocketStorage storage);
}
