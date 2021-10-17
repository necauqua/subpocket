/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.necauqua.mods.subpocket.Network;
import dev.necauqua.mods.subpocket.SubpocketCapability;
import dev.necauqua.mods.subpocket.SubpocketContainer;
import dev.necauqua.mods.subpocket.SubspatialKeyItem;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.command.RelativeFloatArgumentType.RelativeFloat;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.command.BigIntArgumentType.bigint;
import static dev.necauqua.mods.subpocket.command.BigIntArgumentType.getBigInt;
import static dev.necauqua.mods.subpocket.command.RelativeFloatArgumentType.getRelativeFloat;
import static dev.necauqua.mods.subpocket.command.RelativeFloatArgumentType.relativeFloat;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.arguments.EntityArgument.getPlayers;
import static net.minecraft.commands.arguments.EntityArgument.players;
import static net.minecraft.commands.arguments.item.ItemArgument.getItem;
import static net.minecraft.commands.arguments.item.ItemArgument.item;

@EventBusSubscriber(modid = MODID)
public final class SubpocketCommand {

    private static final List<LiteralArgumentBuilder<CommandSourceStack>> SUBCOMMANDS = asList(
        literal("add")
            .then(argument("player", players())
                .then(argument("item", item())
                    .executes(context -> add(
                        context.getSource(),
                        getItem(context, "item").createItemStack(1, false),
                        BigInteger.ONE,
                        getPlayers(context, "player"), 0, 0))
                    .then(argument("count", bigint(BigInteger.ONE))
                        .executes(context -> add(
                            context.getSource(),
                            getItem(context, "item").createItemStack(1, false),
                            getBigInt(context, "count"),
                            getPlayers(context, "player"), 0, 0))
                        .then(literal("at")
                            .then(argument("x", integer(1, SubpocketContainer.WIDTH - 17))
                                .then(argument("y", integer(1, SubpocketContainer.HEIGHT - 17))
                                    .executes(context -> add(
                                        context.getSource(),
                                        getItem(context, "item").createItemStack(1, false),
                                        getBigInt(context, "count"),
                                        getPlayers(context, "player"),
                                        getInteger(context, "x"),
                                        getInteger(context, "y"))))))))),

        literal("remove")
            .then(argument("player", players())
                .then(argument("item", item())
                    .executes(context -> remove(
                        context.getSource(),
                        getItem(context, "item").createItemStack(1, false),
                        BigInteger.ONE,
                        getPlayers(context, "player")))
                    .then(literal("all")
                        .executes(context -> remove(
                            context.getSource(),
                            getItem(context, "item").createItemStack(1, false),
                            null,
                            getPlayers(context, "player"))))
                    .then(argument("count", bigint(BigInteger.ONE))
                        .executes(context -> remove(
                            context.getSource(),
                            getItem(context, "item").createItemStack(1, false),
                            getBigInt(context, "count"),
                            getPlayers(context, "player")))))),

        literal("clear")
            .executes(context -> clear(context.getSource(), singleton(context.getSource().getPlayerOrException())))
            .then(argument("player", players())
                .executes(context -> clear(context.getSource(), getPlayers(context, "player")))),

        literal("move")
            .then(argument("player", players())
                .then(argument("item", item())
                    .then(argument("x", relativeFloat())
                        .then(argument("y", relativeFloat())
                            .executes(context -> move(
                                context.getSource(),
                                getItem(context, "item").createItemStack(1, false),
                                getPlayers(context, "player"),
                                getRelativeFloat(context, "x"),
                                getRelativeFloat(context, "y"))))))),

        literal("unlock")
            .executes(context -> unlock(context.getSource(), singleton(context.getSource().getPlayerOrException())))
            .then(argument("player", players())
                .executes(context -> unlock(context.getSource(), getPlayers(context, "player")))),

        literal("lock")
            .executes(context -> lock(context.getSource(), singleton(context.getSource().getPlayerOrException())))
            .then(argument("player", players())
                .executes(context -> lock(context.getSource(), getPlayers(context, "player")))),

        literal("open")
            .executes(context -> open(context.getSource(), singleton(context.getSource().getPlayerOrException()), false))
            .then(argument("player", players())
                .executes(context -> open(context.getSource(), getPlayers(context, "player"), false))
                .then(literal("force")
                    .executes(context -> open(context.getSource(), getPlayers(context, "player"), true))))

    );

    private static final LiteralArgumentBuilder<CommandSourceStack> HELP =
        literal("help")
            .executes(context -> {
                context.getSource().sendSuccess(helpCommandTitle(SubpocketCommand.HELP), true);
                context.getSource().sendSuccess(new TranslatableComponent("command.subpocket:help.desc"), true);
                for (var subcommand : SUBCOMMANDS) {
                    context.getSource().sendSuccess(helpCommandTitle(subcommand), true);
                    context.getSource().sendSuccess(new TranslatableComponent("command.subpocket:" + subcommand.getLiteral() + ".desc"), true);
                }
                return 1;
            })
            .then(literal("debug")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();

                    NonNullList<ItemStack> tab = NonNullList.create();
                    for (var item : ForgeRegistries.ITEMS) {
                        item.fillItemCategory(CreativeModeTab.TAB_BUILDING_BLOCKS, tab);
                        item.fillItemCategory(CreativeModeTab.TAB_MATERIALS, tab);
                        item.fillItemCategory(CreativeModeTab.TAB_MISC, tab);
                    }

                    var storage = SubpocketCapability.get(player);
                    for (var ref : tab) {
                        var number = BigInteger.valueOf(ThreadLocalRandom.current().nextInt(9999) + 1);
                        storage.add(SubpocketStackFactoryImpl.INSTANCE.create(ref, number));
                    }

                    Network.syncToClient(player);
                    return 1;
                }));

    private static Component helpCommandTitle(LiteralArgumentBuilder<CommandSourceStack> subcommand) {
        return new TextComponent("/subpocket " + subcommand.getLiteral())
            .withStyle(ChatFormatting.GOLD)
            .append(":");
    }

    @SubscribeEvent
    public static void on(RegisterCommandsEvent e) {
        var subpocket = literal("subpocket")
            .requires(src -> src.hasPermission(2));

        SUBCOMMANDS.forEach(subcommand ->
            HELP.then(literal(subcommand.getLiteral())
                .executes(context -> {
                    context.getSource().sendSuccess(new TranslatableComponent("command.subpocket:" + subcommand.getLiteral() + ".desc"), true);
                    return 1;
                })));

        subpocket.then(HELP);
        SUBCOMMANDS.forEach(subpocket::then);

        e.getDispatcher().register(subpocket);
    }

    private static int add(CommandSourceStack src, ItemStack ref, BigInteger count, Collection<ServerPlayer> players, int x, int y) {
        var stack = x != 0 || y != 0 ?
            SubpocketStackFactoryImpl.INSTANCE.create(ref, count, x, y) :
            SubpocketStackFactoryImpl.INSTANCE.create(ref, count); // use the pseudorandom positioning algorithm from that method
        for (var player : players) {
            SubpocketCapability.get(player).add(stack);
            Network.syncToClient(player);
            src.sendSuccess(new TranslatableComponent("command.subpocket:add.success", stack.getRef().getDisplayName(), count.toString(), player.getDisplayName()), true);
        }
        return players.size();
    }

    private static int remove(CommandSourceStack src, ItemStack ref, @Nullable BigInteger count, Collection<ServerPlayer> players) {
        var result = 0;
        for (var player : players) {
            var storage = SubpocketCapability.get(player);

            List<ISubpocketStack> toRemove = new ArrayList<>();

            if (count == null) { // gone functional
                toRemove = storage.getStacksView().stream()
                    .filter(s -> s.matches(ref))
                    .collect(Collectors.toList());
                count = BigInteger.ZERO;
            } else {
                for (var stack : storage) {
                    if (!stack.matches(ref)) {
                        continue;
                    }
                    var cmp = count.compareTo(stack.getCount());
                    if (cmp > 0) {
                        toRemove.add(stack);
                        count = count.subtract(stack.getCount());
                    } else if (cmp == 0) {
                        toRemove.add(stack);
                        count = BigInteger.ZERO;
                        break;
                    } else {
                        stack.setCount(stack.getCount().subtract(count));
                        break;
                    }
                }
            }
            if (toRemove.isEmpty()) {
                src.sendSuccess(new TranslatableComponent("command.subpocket:remove.failure", player.getDisplayName(), ref.getDisplayName()), true);
                continue;
            }
            toRemove.forEach(storage::remove);
            Network.syncToClient(player);
            var removed = toRemove.stream()
                .map(ISubpocketStack::getCount)
                .reduce(BigInteger.ZERO, BigInteger::add);
            src.sendSuccess(new TranslatableComponent("command.subpocket:remove.success", removed, player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int clear(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (var player : players) {
            SubpocketCapability.get(player).clear();
            Network.syncToClient(player);
            src.sendSuccess(new TranslatableComponent("command.subpocket:clear.success", player.getDisplayName()), true);
        }
        return players.size();
    }

    private static int move(CommandSourceStack src, ItemStack ref, Collection<ServerPlayer> players, RelativeFloat x, RelativeFloat y) {
        var result = 0;
        for (var player : players) {
            var storage = SubpocketCapability.get(player);
            List<ISubpocketStack> toElevate = new LinkedList<>();

            for (var stack : storage) {
                var copy = stack.getRef();
                if (copy.getItem() == ref.getItem() && (ref.getTag() == null || Objects.equals(ref.getTag(), copy.getTag()))) {
                    stack.setPos(x.get(stack.getX()), y.get(stack.getY()));
                    toElevate.add(stack);

                    src.sendSuccess(new TranslatableComponent("command.subpocket:move.success", copy.getDisplayName(), player.getDisplayName()), true);
                }
            }
            toElevate.forEach(storage::elevate);

            if (toElevate.isEmpty()) {
                src.sendSuccess(new TranslatableComponent("command.subpocket:move.failure", player.getDisplayName(), ref.getDisplayName()), true);
            } else {
                Network.syncToClient(player);
            }
        }
        return result;
    }

    private static int unlock(CommandSourceStack src, Collection<ServerPlayer> players) {
        var result = 0;
        for (var player : players) {
            var storage = SubpocketCapability.get(player);
            if (storage.isUnlocked()) {
                src.sendSuccess(new TranslatableComponent("command.subpocket:unlock.failure", player.getDisplayName()), true);
                continue;
            }
            storage.unlock();
            Network.syncToClient(player);
            src.sendSuccess(new TranslatableComponent("command.subpocket:unlock.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int lock(CommandSourceStack src, Collection<ServerPlayer> players) {
        var result = 0;
        for (var player : players) {
            var storage = SubpocketCapability.get(player);
            if (!storage.isUnlocked()) {
                src.sendSuccess(new TranslatableComponent("command.subpocket:lock.failure", player.getDisplayName()), true);
                continue;
            }
            storage.lock();
            Network.syncToClient(player);
            src.sendSuccess(new TranslatableComponent("command.subpocket:lock.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int open(CommandSourceStack src, Collection<ServerPlayer> players, boolean force) {
        var result = 0;
        for (var player : players) {
            if (!force && !SubpocketCapability.get(player).isUnlocked()) {
                src.sendSuccess(new TranslatableComponent("command.subpocket:open.failure", player.getDisplayName()), true);
                continue;
            }
            player.openMenu(SubspatialKeyItem.INSTANCE);
            src.sendSuccess(new TranslatableComponent("command.subpocket:open.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }
}
