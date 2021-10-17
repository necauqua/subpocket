/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;
import static net.minecraft.world.inventory.InventoryMenu.*;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class SubpocketContainer extends AbstractContainerMenu {

    public static final int WIDTH = 160, HEIGHT = 70;

    public static final MenuType<SubpocketContainer> TYPE = new MenuType<>(SubpocketContainer::new);

    private static final ResourceLocation[] ARMOR_SLOT_TEXTURES = {
        EMPTY_ARMOR_SLOT_BOOTS,
        EMPTY_ARMOR_SLOT_LEGGINGS,
        EMPTY_ARMOR_SLOT_CHESTPLATE,
        EMPTY_ARMOR_SLOT_HELMET
    };
    private static final EquipmentSlot[] VALID_EQUIPMENT_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private final Player player;
    private final ISubpocket storage;

    @SubscribeEvent
    public static void on(RegistryEvent.Register<MenuType<?>> e) {
        e.getRegistry().register(TYPE.setRegistryName(ns("container")));
    }

    public SubpocketContainer(int windowId, Inventory playerInv) {
        super(TYPE, windowId);
        this.player = playerInv.player;
        storage = SubpocketCapability.get(player);

        // sync on every open
        Network.syncToClient(player);

        for (var x = 0; x < 9; ++x) { // hotbar
            addSlot(new Slot(playerInv, x, x * 18 + 35, 142));
        }
        for (var y = 0; y < 3; ++y) { // main inventory
            for (var x = 0; x < 9; ++x) {
                addSlot(new Slot(playerInv, y * 9 + x + 9, x * 18 + 35, y * 18 + 84));
            }
        }
        for (var k = 0; k < 4; ++k) { // armor slots
            var slotType = VALID_EQUIPMENT_SLOTS[k];
            addSlot(new Slot(playerInv, 39 - k, 8, k * 18 + 26) { // yay for non-existent ArmorSlot class
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.canEquip(slotType, player);
                }

                @Override
                public boolean mayPickup(Player player) {
                    var itemstack = this.getItem();
                    return itemstack.isEmpty()
                        || player.isCreative()
                        || !EnchantmentHelper.hasBindingCurse(itemstack);
                }
            }.setBackground(InventoryMenu.BLOCK_ATLAS, ARMOR_SLOT_TEXTURES[slotType.getIndex()]));
        }
        addSlot(new Slot(playerInv, 40, 8, 102)
            .setBackground(InventoryMenu.BLOCK_ATLAS, EMPTY_ARMOR_SLOT_SHIELD));
    }

    public ISubpocket getStorage() {
        return storage;
    }

    public void stackMoved(float mx, float my, int index) {
        var stack = storage.get(index);
        mx = Mth.clamp(mx, -17, WIDTH - 1);
        my = Mth.clamp(my, -17, HEIGHT - 1);
        if (!stack.isEmpty()) {
            stack.setPos(mx, my);
            storage.elevate(stack);
        }
    }

    private Stream<ItemStack> getStacks(Inventory inventory, boolean reversed) {
        var size = inventory.getContainerSize();
        return (reversed ? IntStream.range(0, size).map(i -> size - i - 1) : IntStream.range(0, size))
            .mapToObj(inventory::getItem);
    }

    private ItemStack findMatchingStack(ISubpocketStack ref, boolean reversed) {
        return getStacks(player.getInventory(), reversed)
            .filter(s -> s.getCount() < s.getMaxStackSize() && ref.matches(s))
            .findFirst()
            .orElseGet(() -> getStacks(player.getInventory(), reversed).filter(ref::matches)
                .findFirst()
                .orElse(ItemStack.EMPTY));
    }

    private boolean tryToAddToPlayer(ISubpocketStack stack, ItemStack retrieved) {
        var slot = Mob.getEquipmentSlotForItem(retrieved);
        if (player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, retrieved);
            return true;
        }
        var abilities = player.getAbilities();
        var stored = abilities.instabuild; // this! is! stupid!
        abilities.instabuild = false;                // ^
        var flag = player.getInventory().add(retrieved);
        abilities.instabuild = stored;               // ^
        if (flag) {
            return true;
        }
        flag = stack.isEmpty(); // welp.. at least I documented such case (also reusing flag, omg how smart)
        stack.setCount(stack.getCount().add(BigInteger.valueOf(retrieved.getCount())));
        if (flag) {
            storage.add(stack); // even though stack was emptied, it still stores its position, so this works
        }
        return false;
    }

    public void processClick(float mx, float my, ClickState click, int index) {
        var hovered = storage.get(index);
        var hand = getCarried();
        switch (click.getButton()) {
            // left click
            case 1 -> {
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
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax())) ;
                    break;
                }
                if (!click.isShift()) {
                    setCarried(hovered.retrieveMax());
                } else if (!hovered.fill(findMatchingStack(hovered, false))) {
                    tryToAddToPlayer(hovered, hovered.retrieveMax());
                }
            }
            // right click
            case 3 -> {
                if (!hand.isEmpty()) {
                    var x = hand.getCount();
                    x = x - x / 2;
                    storage.add(SubpocketStackFactoryImpl.INSTANCE
                        .create(hand.split(x), (int) mx - 8, (int) my - 8));
                    break;
                }
                if (hovered.isEmpty()) {
                    break;
                }
                if (click.isCtrl()) {
                    while (tryToAddToPlayer(hovered, hovered.retrieveMax())) ;
                    break;
                }
                if (!click.isShift()) {
                    var x = Math.min(hovered.getCount().intValue(), hovered.getRef().getMaxStackSize());
                    x = x - x / 2;
                    setCarried(hovered.retrieve(x));
                    break;
                }
                var matching = findMatchingStack(hovered, false);
                var x = matching.getMaxStackSize();
                x = x - x / 2; // ceiled div 2
                if (!hovered.feed(matching, x)) {
                    tryToAddToPlayer(hovered, hovered.retrieve(x));
                }
            }
            // wheel-up [from player inventory into storage]
            case 4 -> {
                if (!hand.isEmpty()) { // filter by what is in hand
                    var adding = SubpocketStackFactoryImpl.INSTANCE
                        .create(hand, (int) mx - 8, (int) my - 8);
                    if (click.isCtrl()) { // move items from inv slot by slot
                        var slot = findMatchingStack(adding, true);
                        if (!slot.isEmpty()) {
                            storage.add(SubpocketStackFactoryImpl.INSTANCE
                                .create(slot, (int) mx - 8, (int) my - 8));
                            slot.setCount(0);
                        }
                        break;
                    }
                    if (click.isShift()) {
                        var slot = findMatchingStack(adding, true);
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
                var slot = findMatchingStack(hovered, true);
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
            }
            // wheel-down [from storage into player inventory]:
            case 5 -> {
                var managed = hand.isEmpty() ? hovered : storage.find(hand);
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
                    setCarried(managed.retrieve(1)); // get first item in hand
                } else {
                    managed.feed(hand, 1); // try to fill hand one by one
                }
                storage.elevate(managed); // elevate managed stack
                return; // and return, so it won't elevate either already elevated or empty stack
            }
        }
        // always elevate stack under mouse unless we did an early return somewhere
        storage.elevate(hovered);
    }

    // middle-clicking inventory slot
    @Override
    public void doClick(int slotId, int dragType, ClickType clickType, Player player) {
        if (clickType != ClickType.CLONE || player.isCreative() || slotId < 0) {
            super.doClick(slotId, dragType, clickType, player);
            return;
        }
        var slot = slots.get(slotId);
        var stack = slot.getItem();
        if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
            storage.find(stack).fill(stack);
        }
    }

    // shift-clicking inventory slot
    @Override
    @Nonnull
    public ItemStack quickMoveStack(Player player, int index) {
        var slot = getSlot(index);
        storage.add(slot.getItem());
        slot.set(ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@Nullable Player player) {
        return true;
    }
}
