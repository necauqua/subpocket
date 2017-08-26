/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.gui;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Subpocket;
import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.handlers.SubpocketConditions;
import info.necauqua.mods.subpocket.handlers.SubpocketSync;
import info.necauqua.mods.subpocket.impl.PositionedBigStackFactory;
import info.necauqua.mods.subpocket.util.ClickState;
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

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = new EntityEquipmentSlot[] {
        EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };

    private EntityPlayer player;
    private ISubpocketStorage storage;

    public ContainerSubpocket(EntityPlayer player) {
        this.player = player;
        storage = CapabilitySubpocket.get(player);

        // sync on every open
        SubpocketSync.sync(player);

        InventoryPlayer playerInv = player.inventory;
        for(int x = 0; x < 9; ++x) { // hotbar
            addSlotToContainer(new Slot(playerInv, x, x * 18 + 35, 142));
        }
        for(int y = 0; y < 3; ++y) { // main inventory
            for(int x = 0; x < 9; ++x) {
                addSlotToContainer(new Slot(playerInv, y * 9 + x + 9, x * 18 + 35, y * 18 + 84));
            }
        }
        for(int k = 0; k < 4; ++k) { // armor slots
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

    public ISubpocketStorage getStorage() {
        return storage;
    }

    public void stackMoved(float mx, float my, int index) {
        IPositionedBigStack stack = storage.get(index);
        mx = MathHelper.clamp(mx, -17, WIDTH - 1);
        my = MathHelper.clamp(my, -17, HEIGHT - 1);
        if(!stack.isEmpty()) {
            stack.setPos(mx, my);
            storage.elevate(stack);
        }
    }

    private Stream<ItemStack> getStacks(IInventory inventory, boolean reversed) {
        int size = inventory.getSizeInventory();
        return (reversed ? IntStream.range(0, size).map(i -> size - i - 1) : IntStream.range(0, size))
                  .mapToObj(inventory::getStackInSlot);
    }

    private ItemStack findMatchingStack(IPositionedBigStack ref, boolean reversed) {
        return getStacks(player.inventory, reversed)
                 .filter(s -> s.getCount() < s.getMaxStackSize() && ref.matches(s))
                 .findFirst()
                 .orElseGet(() -> getStacks(player.inventory, reversed).filter(ref::matches)
                                    .findFirst()
                                    .orElse(ItemStack.EMPTY));
    }

    private boolean tryToAddToPlayer(IPositionedBigStack stack, ItemStack retrieved) {
        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(retrieved);
        if(player.getItemStackFromSlot(slot).isEmpty()) {
            player.setItemStackToSlot(slot, retrieved);
        }else {
            boolean stored = player.capabilities.isCreativeMode; // this! is! stupid!
            player.capabilities.isCreativeMode = false;          // ^
            boolean flag = player.inventory.addItemStackToInventory(retrieved);
            player.capabilities.isCreativeMode = stored;         //             ^
            if(!flag) {
                flag = stack.isEmpty(); // welp.. at least i documented such case (also reusing flag, omg how smart)
                stack.setCount(stack.getCount().add(BigInteger.valueOf(retrieved.getCount())));
                if(flag) {
                    storage.add(stack); // even though stack was emptied, it still stores its position, so this works
                }
                return false;
            }
        }
        return true;
    }

    // this method is super unreadable but it works :D
    // i've tried to add some comments
    public void processClick(float mx, float my, ClickState click, int index) {
        IPositionedBigStack stack = storage.get(index);
        ItemStack hand = player.inventory.getItemStack();
        switch(click.getButton()) {
            case 4: // wheel-up [from player inventory into storage]
                if(!hand.isEmpty()) { // filter by what is in hand
                    IPositionedBigStack hstack = PositionedBigStackFactory.INSTANCE
                                                     .create(hand, (int) mx - 8, (int) my - 8);
                    if(click.isCtrl()) { // move items from inv slot by slot
                        ItemStack slot = findMatchingStack(hstack, true);
                        if(!slot.isEmpty()) {
                            storage.add(PositionedBigStackFactory.INSTANCE
                                    .create(slot, (int) mx - 8, (int) my - 8));
                            slot.setCount(0);
                        }
                    }else if(click.isShift()) {
                        ItemStack slot = findMatchingStack(hstack, true);
                        if(!slot.isEmpty()) { // move items from inv one by one
                            storage.add(PositionedBigStackFactory.INSTANCE
                                            .create(slot.splitStack(1), (int) mx - 8, (int) my - 8));
                        }
                    }else { // move items from hand one by one
                        storage.add(PositionedBigStackFactory.INSTANCE
                                        .create(hand.splitStack(1), (int) mx - 8, (int) my - 8));
                    }
                }else if(!stack.isEmpty()) { // filter by hovered
                    ItemStack slot = findMatchingStack(stack, true);
                    if(!slot.isEmpty()) {
                        if(click.isCtrl()) { // move items from inv slot by slot
                            storage.add(PositionedBigStackFactory.INSTANCE
                                    .create(slot, (int) mx - 8, (int) my - 8));
                            slot.setCount(0);
                        }else if(click.isShift()) { // move items from inv one by one
                            storage.add(PositionedBigStackFactory.INSTANCE
                                            .create(slot.splitStack(1), (int) mx - 8, (int) my - 8));
                        }
                    }
                }
                break;
            case 5: // wheel-down [from storage into player inventory]:
                IPositionedBigStack handling = stack.isEmpty() ? storage.find(hand) : stack;
                if(click.isCtrl()) {
                    // try to either fill existing stack to max or if it fails to simply add max stack
                    if(!handling.fill(findMatchingStack(handling, false))) {
                        tryToAddToPlayer(handling, handling.retrieveMax());
                    }
                }else if(click.isShift()) {
                    // try to either fill existing stack one by one or if it fails to simply add one item
                    if(!handling.feed(findMatchingStack(handling, false), 1)) {
                        tryToAddToPlayer(handling, handling.retrieve(1));
                    }
                }else if(hand.isEmpty()) {
                    player.inventory.setItemStack(handling.retrieve(1)); // get first item in hand
                }else {
                    handling.feed(hand, 1); // try to fill hand one by one
                }
                storage.elevate(handling); // elevate filtered one
                return; // and return so it wont elevate either already elevated or empty stack
            case 1: // left click
                if(!hand.isEmpty()) {
                    if(!stack.fill(hand)) {
                        storage.add(PositionedBigStackFactory.INSTANCE.create(hand, (int) mx - 8, (int) my - 8));
                        hand.setCount(0);
                    }
                }else if(!stack.isEmpty()) {
                    if(click.isCtrl()) {
                        while(tryToAddToPlayer(stack, stack.retrieveMax())) {}
                    }else if(click.isShift()) {
                        if(!stack.fill(findMatchingStack(stack, false))) {
                            tryToAddToPlayer(stack, stack.retrieveMax());
                        }
                    }else {
                        player.inventory.setItemStack(stack.retrieveMax());
                    }
                }
                break;
            case 3: // right click
                if(!hand.isEmpty()) {
                    int x = hand.getCount();
                    x = x - x / 2;
                    storage.add(PositionedBigStackFactory.INSTANCE
                                    .create(hand.splitStack(x), (int) mx - 8, (int) my - 8));
                }else if(!stack.isEmpty()) {
                    if(click.isCtrl()) {
                        while(tryToAddToPlayer(stack, stack.retrieveMax())) {}
                    }else if(click.isShift()) {
                        ItemStack matching = findMatchingStack(stack, false);
                        int x = matching.getMaxStackSize();
                        x = x - x / 2; // ceiled div 2
                        if(!stack.feed(matching, x)) {
                            tryToAddToPlayer(stack, stack.retrieve(x));
                        }
                    }else {
                        int x = stack.getRef().getMaxStackSize();
                        x = x - x / 2;
                        player.inventory.setItemStack(stack.retrieve(x));
                    }
                }
                break;
        }
        storage.elevate(stack); // just do it in any condition
    }

    // middle-clicking inventory slot
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if(clickType == ClickType.CLONE && !player.capabilities.isCreativeMode && slotId >= 0) {
            Slot slot = inventorySlots.get(slotId);
            ItemStack stack = slot != null ? slot.getStack() : ItemStack.EMPTY;
            if(!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
                storage.find(stack).fill(stack);
            }
            return ItemStack.EMPTY;
        }else {
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
        boolean has = SubpocketConditions.hasSubpocket(player);
        if(!has) { // this (i think this) closes the gui from time to time
            Subpocket.logger.warn("GUI WAS OPEN BUT NO SUBPOCKET IS AWAILABLE NOW!");
        }
        return has;
    }
}
