/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class SubpocketContainer extends Container {
    public static final int WIDTH = 160, HEIGHT = 70;

    public static final ContainerType<SubpocketContainer> TYPE = new ContainerType<>(SubpocketContainer::new);

    private static final EquipmentSlotType[] ARMOR_SLOTS = new EquipmentSlotType[]{
            EquipmentSlotType.HEAD, EquipmentSlotType.CHEST, EquipmentSlotType.LEGS, EquipmentSlotType.FEET
    };
    private static final String[] EMPTY_ARMOR_SLOT_NAMES = new String[]{
            "item/empty_armor_slot_boots",
            "item/empty_armor_slot_leggings",
            "item/empty_armor_slot_chestplate",
            "item/empty_armor_slot_helmet"
    };

    private final PlayerEntity player;
    private final ISubpocket storage;

    @SubscribeEvent
    public static void on(RegistryEvent.Register<ContainerType<?>> e) {
        e.getRegistry().register(TYPE.setRegistryName(ns("container")));
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void on(FMLClientSetupEvent e) {
        ScreenManager.registerFactory(TYPE, SubpocketScreen::new);
    }

    public SubpocketContainer(int windowId, PlayerInventory playerInv) {
        super(TYPE, windowId);
        this.player = playerInv.player;
        storage = SubpocketCapability.get(player);

        // sync on every open
        Network.syncToClient(player);

        for (int x = 0; x < 9; ++x) { // hotbar
            addSlot(new Slot(playerInv, x, x * 18 + 35, 142));
        }
        for (int y = 0; y < 3; ++y) { // main inventory
            for (int x = 0; x < 9; ++x) {
                addSlot(new Slot(playerInv, y * 9 + x + 9, x * 18 + 35, y * 18 + 84));
            }
        }
        for (int k = 0; k < 4; ++k) { // armor slots
            EquipmentSlotType armorType = ARMOR_SLOTS[k];
            addSlot(new Slot(playerInv, 39 - k, 8, k * 18 + 26) { // yay for non-existent ArmorSlot class
                public int getSlotStackLimit() {
                    return 1;
                }

                public boolean isItemValid(ItemStack stack) {
                    return stack.canEquip(armorType, player);
                }

                public boolean canTakeStack(PlayerEntity player) {
                    ItemStack itemstack = this.getStack();
                    return itemstack.isEmpty()
                            || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(itemstack);
                }

                @OnlyIn(Dist.CLIENT)
                public String getSlotTexture() {
                    return EMPTY_ARMOR_SLOT_NAMES[armorType.getIndex()];
                }
            });
        }
        Slot shieldSlot = new Slot(playerInv, 40, 8, 102);
        shieldSlot.setBackgroundName("item/empty_armor_slot_shield");
        addSlot(shieldSlot);
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
        EquipmentSlotType slot = MobEntity.getSlotForItemStack(retrieved);
        if (player.getItemStackFromSlot(slot).isEmpty()) {
            player.setItemStackToSlot(slot, retrieved);
            return true;
        }
        boolean stored = player.abilities.isCreativeMode; // this! is! stupid!
        player.abilities.isCreativeMode = false;          // ^
        boolean flag = player.inventory.addItemStackToInventory(retrieved);
        player.abilities.isCreativeMode = stored;         //                ^
        if (flag) {
            return true;
        }
        flag = stack.isEmpty(); // welp.. at least i documented such case (also reusing flag, omg how smart)
        stack.setCount(stack.getCount().add(BigInteger.valueOf(retrieved.getCount())));
        if (flag) {
            storage.add(stack); // even though stack was emptied, it still stores its position, so this works
        }
        return false;
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
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax()));
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
                            .create(hand.split(x), (int) mx - 8, (int) my - 8));
                    break;
                }
                if (hovered.isEmpty()) {
                    break;
                }
                if (click.isCtrl()) {
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax()));
                    break;
                }
                if (!click.isShift()) {
                    int x = Math.min(hovered.getCount().intValue(), hovered.getRef().getMaxStackSize());
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
                                    .create(slot.split(1), (int) mx - 8, (int) my - 8));
                        }
                        break;
                    }
                    // move items from hand one by one
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                            .create(hand.split(1), (int) mx - 8, (int) my - 8));
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
                            .create(slot.split(1), (int) mx - 8, (int) my - 8));
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
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.CLONE || player.isCreative() || slotId < 0) {
            return super.slotClick(slotId, dragType, clickType, player);
        }
        Slot slot = inventorySlots.get(slotId);
        ItemStack stack = slot != null ? slot.getStack() : ItemStack.EMPTY;
        if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
            storage.find(stack).fill(stack);
        }
        return ItemStack.EMPTY;
    }

    // shift-clicking inventory slot
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(PlayerEntity player, int index) {
        Slot slot = getSlot(index);
        storage.add(slot.getStack());
        slot.putStack(ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(@Nullable PlayerEntity player) {
        return true;
    }
}
