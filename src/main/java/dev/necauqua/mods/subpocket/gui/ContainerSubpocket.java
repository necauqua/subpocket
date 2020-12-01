/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.gui;

import dev.necauqua.mods.subpocket.CapabilitySubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.handlers.SyncHandler;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ContainerSubpocket extends Container {
    public static final int WIDTH = 160, HEIGHT = 70;

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[]{
            EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };

    private final EntityPlayer player;
    private final ISubpocket storage;

    public ContainerSubpocket(EntityPlayer player) {
        this.player = player;
        storage = CapabilitySubpocket.get(player);

        // sync on every open
        SyncHandler.sync(player);

        InventoryPlayer playerInv = player.inventory;
        for (int x = 0; x < 9; ++x) { // hotbar
            addSlotToContainer(new Slot(playerInv, x, x * 18 + 35, 142));
        }
        for (int y = 0; y < 3; ++y) { // main inventory
            for (int x = 0; x < 9; ++x) {
                addSlotToContainer(new Slot(playerInv, y * 9 + x + 9, x * 18 + 35, y * 18 + 84));
            }
        }
        for (int k = 0; k < 4; ++k) { // armor slots
            EntityEquipmentSlot armorType = ARMOR_SLOTS[k];
            addSlotToContainer(new Slot(playerInv, 39 - k, 8, k * 18 + 26) { // yay for ArmorSlot class which
                // DOES NOT EXIST !!1!
                public int getSlotStackLimit() {
                    return 1;
                }

                public boolean isItemValid(ItemStack stack) {
                    return stack.getItem().isValidArmor(stack, armorType, player);
                }

                public boolean canTakeStack(EntityPlayer player) {
                    ItemStack itemstack = getStack();
                    return itemstack.isEmpty()
                            || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(itemstack)
                            && super.canTakeStack(player);
                }

                @SideOnly(Side.CLIENT)
                public String getSlotTexture() {
                    return ItemArmor.EMPTY_SLOT_NAMES[armorType.getIndex()];
                }
            });
        }
        addSlotToContainer(new Slot(playerInv, 40, 8, 102) { // shield slot
            @SideOnly(Side.CLIENT)
            public String getSlotTexture() {
                return "minecraft:items/empty_armor_slot_shield";
            }
        });
    }

    public ISubpocket getStorage() {
        return storage;
    }

    public void stackMoved(float mx, float my, int index) {
        ISubpocketStack stack = storage.get(index);
        mx = MathHelper.clamp(mx, -17, WIDTH - 1);
        my = MathHelper.clamp(my, -17, HEIGHT - 1);
        if (!stack.isEmpty()) {
            stack.setPos(mx, my);
            storage.elevate(stack);
        }
    }

    private Stream<ItemStack> getStacks(IInventory inventory, boolean reversed) {
        int size = inventory.getSizeInventory();
        return (reversed ? IntStream.range(0, size).map(i -> size - i - 1) : IntStream.range(0, size))
                .mapToObj(inventory::getStackInSlot);
    }

    private ItemStack findMatchingStack(ISubpocketStack ref, boolean reversed) {
        return getStacks(player.inventory, reversed)
                .filter(s -> s.getCount() < s.getMaxStackSize() && ref.matches(s))
                .findFirst()
                .orElseGet(() -> getStacks(player.inventory, reversed).filter(ref::matches)
                        .findFirst()
                        .orElse(ItemStack.EMPTY));
    }

    private boolean tryToAddToPlayer(ISubpocketStack stack, ItemStack retrieved) {
        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(retrieved);
        if (player.getItemStackFromSlot(slot).isEmpty()) {
            player.setItemStackToSlot(slot, retrieved);
        } else {
            boolean stored = player.capabilities.isCreativeMode; // this! is! stupid!
            player.capabilities.isCreativeMode = false;          // ^
            boolean flag = player.inventory.addItemStackToInventory(retrieved);
            player.capabilities.isCreativeMode = stored;         //             ^
            if (!flag) {
                flag = stack.isEmpty(); // welp.. at least i documented such case (also reusing flag, omg how smart)
                stack.setCount(stack.getCount().add(BigInteger.valueOf(retrieved.getCount())));
                if (flag) {
                    storage.add(stack); // even though stack was emptied, it still stores its position, so this works
                }
                return false;
            }
        }
        return true;
    }

    public void processClick(float mx, float my, ClickState click, int index) {
        ISubpocketStack hovered = storage.get(index);
        ItemStack hand = player.inventory.getItemStack();
        switch (click.getButton()) {

            // left click
            case 1: {
                if (!hand.isEmpty()) {
                    if (!hovered.fill(hand)) {
                        storage.add(SubpocketStackFactoryImpl.INSTANCE.create(hand, (int) mx - 8, (int) my - 8));
                        hand.setCount(0);
                    }
                    return; // one condition where we don't want to elevate clicked stack
                }
                if (hovered.isEmpty()) {
                    break;
                }
                if (click.isCtrl()) {
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax())) {}
                    break;
                }
                if (!click.isShift()) {
                    player.inventory.setItemStack(hovered.retrieveMax());
                } else if (!hovered.fill(findMatchingStack(hovered, false))) {
                    tryToAddToPlayer(hovered, hovered.retrieveMax());
                }
                break;
            }

            // right click
            case 3: {
                if (!hand.isEmpty()) {
                    int x = hand.getCount();
                    x = x - x / 2;
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                            .create(hand.splitStack(x), (int) mx - 8, (int) my - 8));
                    break;
                }
                if (hovered.isEmpty()) {
                    break;
                }
                if (click.isCtrl()) {
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax())) {}
                    break;
                }
                if (!click.isShift()) {
                    int x = hovered.getRef().getMaxStackSize();
                    x = x - x / 2;
                    player.inventory.setItemStack(hovered.retrieve(x));
                    break;
                }
                ItemStack matching = findMatchingStack(hovered, false);
                int x = matching.getMaxStackSize();
                x = x - x / 2; // ceiled div 2
                if (!hovered.feed(matching, x)) {
                    tryToAddToPlayer(hovered, hovered.retrieve(x));
                }
                break;
            }

            // wheel-up [from player inventory into storage]
            case 4: {
                if (!hand.isEmpty()) { // filter by what is in hand
                    ISubpocketStack adding = SubpocketStackFactoryImpl.INSTANCE
                            .create(hand, (int) mx - 8, (int) my - 8);
                    if (click.isCtrl()) { // move items from inv slot by slot
                        ItemStack slot = findMatchingStack(adding, true);
                        if (!slot.isEmpty()) {
                            storage.add(SubpocketStackFactoryImpl.INSTANCE
                                    .create(slot, (int) mx - 8, (int) my - 8));
                            slot.setCount(0);
                        }
                        break;
                    }
                    if (click.isShift()) {
                        ItemStack slot = findMatchingStack(adding, true);
                        if (!slot.isEmpty()) { // move items from inv one by one
                            storage.add(SubpocketStackFactoryImpl.INSTANCE
                                    .create(slot.splitStack(1), (int) mx - 8, (int) my - 8));
                        }
                        break;
                    }
                    // move items from hand one by one
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                            .create(hand.splitStack(1), (int) mx - 8, (int) my - 8));
                    return;
                }
                // then filter by hovered
                if (hovered.isEmpty()) {
                    break;
                }
                ItemStack slot = findMatchingStack(hovered, true);
                if (slot.isEmpty()) {
                    break;
                }
                if (click.isCtrl()) { // move items from inv slot by slot
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                            .create(slot, (int) mx - 8, (int) my - 8));
                    slot.setCount(0);
                } else if (click.isShift()) { // move items from inv one by one
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                            .create(slot.splitStack(1), (int) mx - 8, (int) my - 8));
                }
                break;
            }

            // wheel-down [from storage into player inventory]:
            case 5: {
                ISubpocketStack managed = hand.isEmpty() ? hovered : storage.find(hand);
                if (click.isCtrl()) {
                    // try to either fill existing stack to max or if it fails to simply add max stack
                    if (!managed.fill(findMatchingStack(managed, false))) {
                        tryToAddToPlayer(managed, managed.retrieveMax());
                    }
                } else if (click.isShift()) {
                    // try to either fill existing stack one by one or if it fails to simply add one item
                    if (!managed.feed(findMatchingStack(managed, false), 1)) {
                        tryToAddToPlayer(managed, managed.retrieve(1));
                    }
                } else if (hand.isEmpty()) {
                    player.inventory.setItemStack(managed.retrieve(1)); // get first item in hand
                } else {
                    managed.feed(hand, 1); // try to fill hand one by one
                }
                storage.elevate(managed); // elevate managed stack
                return; // and return so it wont elevate either already elevated or empty stack
            }
        }
        // always elevate stack under mouse unless we did an early return somewhere
        storage.elevate(hovered);
    }

    // middle-clicking inventory slot
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (clickType == ClickType.CLONE && !player.capabilities.isCreativeMode && slotId >= 0) {
            Slot slot = inventorySlots.get(slotId);
            ItemStack stack = slot != null ? slot.getStack() : ItemStack.EMPTY;
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
                storage.find(stack).fill(stack);
            }
            return ItemStack.EMPTY;
        } else {
            return super.slotClick(slotId, dragType, clickType, player);
        }
    }

    // shift-clicking inventory slot
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = getSlot(index);
        storage.add(slot.getStack());
        slot.putStack(ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(@Nullable EntityPlayer player) {
        return true;
    }
}
