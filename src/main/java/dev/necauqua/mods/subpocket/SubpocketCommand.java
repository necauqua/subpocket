/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.necauqua.mods.subpocket.RelativeFloatArgumentType.RelativeFloat;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static dev.necauqua.mods.subpocket.BigIntArgumentType.bigint;
import static dev.necauqua.mods.subpocket.BigIntArgumentType.getBigInt;
import static dev.necauqua.mods.subpocket.RelativeFloatArgumentType.getRelativeFloat;
import static dev.necauqua.mods.subpocket.RelativeFloatArgumentType.relativeFloat;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;
import static net.minecraft.command.arguments.EntityArgument.getPlayers;
import static net.minecraft.command.arguments.EntityArgument.players;
import static net.minecraft.command.arguments.ItemArgument.getItem;
import static net.minecraft.command.arguments.ItemArgument.item;

@EventBusSubscriber(modid = MODID)
public final class SubpocketCommand {

    private static final List<LiteralArgumentBuilder<CommandSource>> SUBCOMMANDS = asList(
            literal("add")
                    .then(argument("player", players())
                            .then(argument("item", item())
                                    .executes(context -> add(
                                            context.getSource(),
                                            getItem(context, "item").createStack(1, false),
                                            BigInteger.ONE,
                                            getPlayers(context, "player"), 0, 0))
                                    .then(argument("count", bigint(BigInteger.ONE))
                                            .executes(context -> add(
                                                    context.getSource(),
                                                    getItem(context, "item").createStack(1, false),
                                                    getBigInt(context, "count"),
                                                    getPlayers(context, "player"), 0, 0))
                                            .then(literal("at")
                                                    .then(argument("x", integer(1, SubpocketContainer.WIDTH - 17))
                                                            .then(argument("y", integer(1, SubpocketContainer.HEIGHT - 17))
                                                                    .executes(context -> add(
                                                                            context.getSource(),
                                                                            getItem(context, "item").createStack(1, false),
                                                                            getBigInt(context, "count"),
                                                                            getPlayers(context, "player"),
                                                                            getInteger(context, "x"),
                                                                            getInteger(context, "y"))))))))),

            literal("remove")
                    .then(argument("player", players())
                            .then(argument("item", item())
                                    .executes(context -> remove(
                                            context.getSource(),
                                            getItem(context, "item").createStack(1, false),
                                            BigInteger.ONE,
                                            getPlayers(context, "player")))
                                    .then(literal("all")
                                            .executes(context -> remove(
                                                    context.getSource(),
                                                    getItem(context, "item").createStack(1, false),
                                                    null,
                                                    getPlayers(context, "player"))))
                                    .then(argument("count", bigint(BigInteger.ONE))
                                            .executes(context -> remove(
                                                    context.getSource(),
                                                    getItem(context, "item").createStack(1, false),
                                                    getBigInt(context, "count"),
                                                    getPlayers(context, "player")))))),

            literal("clear")
                    .executes(context -> clear(context.getSource(), singleton(context.getSource().asPlayer())))
                    .then(argument("player", players())
                            .executes(context -> clear(context.getSource(), getPlayers(context, "player")))),

            literal("move")
                    .then(argument("player", players())
                            .then(argument("item", item())
                                    .then(argument("x", relativeFloat())
                                            .then(argument("y", relativeFloat())
                                                    .executes(context -> move(
                                                            context.getSource(),
                                                            getItem(context, "item").createStack(1, false),
                                                            getPlayers(context, "player"),
                                                            getRelativeFloat(context, "x"),
                                                            getRelativeFloat(context, "y"))))))),

            literal("unlock")
                    .executes(context -> unlock(context.getSource(), singleton(context.getSource().asPlayer())))
                    .then(argument("player", players())
                            .executes(context -> unlock(context.getSource(), getPlayers(context, "player")))),

            literal("lock")
                    .executes(context -> lock(context.getSource(), singleton(context.getSource().asPlayer())))
                    .then(argument("player", players())
                            .executes(context -> lock(context.getSource(), getPlayers(context, "player")))),

            literal("open")
                    .executes(context -> open(context.getSource(), singleton(context.getSource().asPlayer()), false))
                    .then(argument("player", players())
                            .executes(context -> open(context.getSource(), getPlayers(context, "player"), false))
                            .then(literal("force")
                                    .executes(context -> open(context.getSource(), getPlayers(context, "player"), true))))

    );

    private static final LiteralArgumentBuilder<CommandSource> HELP =
            literal("help")
                    .executes(context -> {
                        context.getSource().sendFeedback(helpCommandTitle(SubpocketCommand.HELP), true);
                        context.getSource().sendFeedback(new TranslationTextComponent("command.subpocket:help.desc"), true);
                        for (LiteralArgumentBuilder<CommandSource> subcommand : SUBCOMMANDS) {
                            context.getSource().sendFeedback(helpCommandTitle(subcommand), true);
                            context.getSource().sendFeedback(new TranslationTextComponent("command.subpocket:" + subcommand.getLiteral() + ".desc"), true);
                        }
                        return 1;
                    })
                    .then(literal("debug")
                            .executes(context -> {
                                PlayerEntity player = context.getSource().asPlayer();

                                NonNullList<ItemStack> tab = NonNullList.create();
                                for (Item item : ForgeRegistries.ITEMS) {
                                    item.fillItemGroup(ItemGroup.BUILDING_BLOCKS, tab);
                                    item.fillItemGroup(ItemGroup.MATERIALS, tab);
                                    item.fillItemGroup(ItemGroup.MISC, tab);
                                }

                                ISubpocket storage = SubpocketCapability.get(player);
                                for (ItemStack ref : tab) {
                                    BigInteger number = BigInteger.valueOf(ThreadLocalRandom.current().nextInt(9999) + 1);
                                    storage.add(SubpocketStackFactoryImpl.INSTANCE.create(ref, number));
                                }

                                Network.syncToClient(player);
                                return 1;
                            }));

    private static ITextComponent helpCommandTitle(LiteralArgumentBuilder<CommandSource> subcommand) {
        return new StringTextComponent("/subpocket " + subcommand.getLiteral())
                .mergeStyle(TextFormatting.GOLD)
                .appendString(":");
    }

    @SubscribeEvent
    public static void on(FMLServerStartingEvent e) {
        LiteralArgumentBuilder<CommandSource> subpocket = literal("subpocket")
                .requires(src -> src.hasPermissionLevel(2));

        SUBCOMMANDS.forEach(subcommand ->
                HELP.then(literal(subcommand.getLiteral())
                        .executes(context -> {
                            context.getSource().sendFeedback(new TranslationTextComponent("command.subpocket:" + subcommand.getLiteral() + ".desc"), true);
                            return 1;
                        })));

        subpocket.then(HELP);
        SUBCOMMANDS.forEach(subpocket::then);

        e.getServer().getCommandManager().getDispatcher().register(subpocket);
    }

    private static int add(CommandSource src, ItemStack ref, BigInteger count, Collection<ServerPlayerEntity> players, int x, int y) {
        ISubpocketStack stack = x != 0 || y != 0 ?
                SubpocketStackFactoryImpl.INSTANCE.create(ref, count, x, y) :
                SubpocketStackFactoryImpl.INSTANCE.create(ref, count); // use the pseudorandom positioning algorithm from that method
        for (ServerPlayerEntity player : players) {
            SubpocketCapability.get(player).add(stack);
            Network.syncToClient(player);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:add.success", stack.getRef().getTextComponent(), count.toString(), player.getDisplayName()), true);
        }
        return players.size();
    }

    private static int remove(CommandSource src, ItemStack ref, @Nullable BigInteger count, Collection<ServerPlayerEntity> players) {
        int result = 0;
        for (ServerPlayerEntity player : players) {
            ISubpocket storage = SubpocketCapability.get(player);

            List<ISubpocketStack> toRemove = new ArrayList<>();

            if (count == null) { // gone functional
                toRemove = storage.getStacksView().stream()
                        .filter(s -> s.matches(ref))
                        .collect(Collectors.toList());
                count = BigInteger.ZERO;
            } else {
                for (ISubpocketStack stack : storage) {
                    if (!stack.matches(ref)) {
                        continue;
                    }
                    int cmp = count.compareTo(stack.getCount());
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
                src.sendFeedback(new TranslationTextComponent("command.subpocket:remove.failure", player.getDisplayName(), ref.getTextComponent()), true);
                continue;
            }
            toRemove.forEach(storage::remove);
            Network.syncToClient(player);
            BigInteger removed = toRemove.stream()
                    .map(ISubpocketStack::getCount)
                    .reduce(BigInteger.ZERO, BigInteger::add);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:remove.success", removed, player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int clear(CommandSource src, Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            SubpocketCapability.get(player).clear();
            Network.syncToClient(player);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:clear.success", player.getDisplayName()), true);
        }
        return players.size();
    }

    private static int move(CommandSource src, ItemStack ref, Collection<ServerPlayerEntity> players, RelativeFloat x, RelativeFloat y) {
        int result = 0;
        for (ServerPlayerEntity player : players) {
            ISubpocket storage = SubpocketCapability.get(player);
            List<ISubpocketStack> toElevate = new LinkedList<>();

            for (ISubpocketStack stack : storage) {
                ItemStack copy = stack.getRef();
                if (copy.getItem() == ref.getItem() && (ref.getTag() == null || Objects.equals(ref.getTag(), copy.getTag()))) {
                    stack.setPos(x.get(stack.getX()), y.get(stack.getY()));
                    toElevate.add(stack);

                    src.sendFeedback(new TranslationTextComponent("command.subpocket:move.success", copy.getTextComponent(), player.getDisplayName()), true);
                }
            }
            toElevate.forEach(storage::elevate);

            if (toElevate.isEmpty()) {
                src.sendFeedback(new TranslationTextComponent("command.subpocket:move.failure", player.getDisplayName(), ref.getTextComponent()), true);
            } else {
                Network.syncToClient(player);
            }
        }
        return result;
    }

    private static int unlock(CommandSource src, Collection<ServerPlayerEntity> players) {
        int result = 0;
        for (ServerPlayerEntity player : players) {
            ISubpocket storage = SubpocketCapability.get(player);
            if (storage.isUnlocked()) {
                src.sendFeedback(new TranslationTextComponent("command.subpocket:unlock.failure", player.getDisplayName()), true);
                continue;
            }
            storage.unlock();
            Network.syncToClient(player);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:unlock.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int lock(CommandSource src, Collection<ServerPlayerEntity> players) {
        int result = 0;
        for (ServerPlayerEntity player : players) {
            ISubpocket storage = SubpocketCapability.get(player);
            if (!storage.isUnlocked()) {
                src.sendFeedback(new TranslationTextComponent("command.subpocket:unlock.failure", player.getDisplayName()), true);
                continue;
            }
            storage.lock();
            Network.syncToClient(player);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:unlock.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }

    private static int open(CommandSource src, Collection<ServerPlayerEntity> players, boolean force) {
        int result = 0;
        for (ServerPlayerEntity player : players) {
            if (!force && !SubpocketCapability.get(player).isUnlocked()) {
                src.sendFeedback(new TranslationTextComponent("command.subpocket:open.failure", player.getDisplayName()), true);
                continue;
            }
            player.openContainer(SubspatialKeyItem.INSTANCE);
            src.sendFeedback(new TranslationTextComponent("command.subpocket:open.success", player.getDisplayName()), true);
            result++;
        }
        return result;
    }
}
