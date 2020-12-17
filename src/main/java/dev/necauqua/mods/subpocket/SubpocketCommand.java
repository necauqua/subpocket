/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import dev.necauqua.mods.subpocket.api.ISubpocket;
import dev.necauqua.mods.subpocket.gui.ContainerSubpocket;
import dev.necauqua.mods.subpocket.handlers.SyncHandler;
import dev.necauqua.mods.subpocket.impl.SubpocketStackFactoryImpl;
import net.minecraft.command.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static java.util.Collections.emptyList;
import static net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE;

public class SubpocketCommand extends CommandBase {

    private final Subcommand[] subcommands = {
            new Subcommand("help") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    if (args.length == 0) {
                        for (Subcommand subcommand : subcommands) {
                            notifyCommandListener(sender, parent, "§c%s", new TextComponentTranslation(subcommand.prefix + "usage"));
                            notifyCommandListener(sender, parent, subcommand.prefix + "desc");
                        }
                    } else {
                        if (args[0].equals("debug") && sender instanceof EntityPlayer) {
                            EntityPlayer player = (EntityPlayer) sender;

                            NonNullList<ItemStack> tab = NonNullList.create();
                            for (Item item : Item.REGISTRY) {
                                item.getSubItems(CreativeTabs.BUILDING_BLOCKS, tab);
                                item.getSubItems(CreativeTabs.MATERIALS, tab);
                                item.getSubItems(CreativeTabs.MISC, tab);
                            }

                            ISubpocket storage = CapabilitySubpocket.get(player);
                            for (ItemStack ref : tab) {
                                BigInteger number = BigInteger.valueOf(ThreadLocalRandom.current().nextInt(9999) + 1);
                                storage.add(SubpocketStackFactoryImpl.INSTANCE.create(ref, number));
                            }

                            SyncHandler.sync(player);
                            return;
                        }
                        String prefix = findSubcommand(args[0]).prefix;
                        notifyCommandListener(sender, parent, "§c%s", new TextComponentTranslation(prefix + "usage"));
                        notifyCommandListener(sender, parent, prefix + "desc");
                    }
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ? getListOfStringsMatchingLastWord(args, names) : emptyList();
                }
            },
            new Subcommand("add") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    if (args.length < 2) {
                        wrong();
                    }
                    EntityPlayer player = getPlayer(server, sender, args[0]);
                    Item item = getItemByText(sender, args[1]);

                    int x = 0, y = 0, off = 0;
                    if (args.length > 2 && "at".equalsIgnoreCase(args[2])) {
                        if (args.length < 5) {
                            wrong();
                        }
                        x = parseInt(args[3], 1, ContainerSubpocket.WIDTH - 17);
                        y = parseInt(args[4], 1, ContainerSubpocket.HEIGHT - 17);
                        off = 3;
                    }

                    BigInteger count = args.length > 2 + off ? parsePosBigInt(args[2 + off]) : BigInteger.ONE;
                    int meta = args.length > 3 + off ? parseInt(args[3 + off]) : 0;

                    ItemStack ref = new ItemStack(item, 1, meta);

                    fetchNBT(ref, args, 4 + off);

                    ISubpocketStack stack = off != 0 ? SubpocketStackFactoryImpl.INSTANCE.create(ref, count, x, y) :
                            SubpocketStackFactoryImpl.INSTANCE.create(ref, count);

                    CapabilitySubpocket.get(player).add(stack);
                    SyncHandler.sync(player);

                    answer("success", stack.getRef().getTextComponent(), count.toString(), player.getDisplayName());
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                            args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) :
                                    args.length == 3 ? getListOfStringsMatchingLastWord(args, "at") : emptyList();
                }
            },
            new Subcommand("remove") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    if (args.length < 2) {
                        wrong();
                    }
                    EntityPlayer player = getPlayer(server, sender, args[0]);
                    Item item = getItemByText(sender, args[1]);

                    BigInteger count = args.length > 2 ? "all".equalsIgnoreCase(args[2]) ? null : parsePosBigInt(args[2]) : BigInteger.ONE;
                    int meta = args.length > 3 ? parseInt(args[3]) : 0;

                    ItemStack ref = new ItemStack(item, 1, meta);

                    fetchNBT(ref, args, 4);

                    ISubpocket storage = CapabilitySubpocket.get(player);

                    List<ISubpocketStack> toRemove = new LinkedList<>();

                    if (count == null) { // gone functional
                        toRemove = storage.getStacksView().stream()
                                .filter(s -> s.matches(ref))
                                .collect(Collectors.toList());
                        count = BigInteger.ZERO;
                    } else {
                        for (ISubpocketStack stack : storage) {
                            if (stack.matches(ref)) {
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
                    }

                    toRemove.forEach(storage::remove);

                    SyncHandler.sync(player);

                    BigInteger removed = toRemove.stream()
                            .map(ISubpocketStack::getCount)
                            .reduce(BigInteger.ZERO, BigInteger::add);

                    answer("success", removed.add(count).toString(), player.getDisplayName());
                    // adding count which may contain leftover from partial stack removal from else branch
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                            args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) :
                                    args.length == 3 ? getListOfStringsMatchingLastWord(args, "all") : emptyList();
                }
            },
            new Subcommand("clear") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    EntityPlayer player = args.length == 0 ? getCommandSenderAsPlayer(sender) : getPlayer(server, sender, args[0]);
                    CapabilitySubpocket.get(player).clear();
                    SyncHandler.sync(player);
                    answer("success", sender.getDisplayName());
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : emptyList();
                }
            },
            new Subcommand("move") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    if (args.length < 4) {
                        wrong();
                    }
                    EntityPlayer player = getPlayer(server, sender, args[0]);
                    Item item = getItemByText(sender, args[1]);

                    String sx = args[2], sy = args[3];
                    boolean rx = false, ry = false;
                    if (sx.startsWith("~")) {
                        rx = true;
                        sx = sx.substring(1);
                    }
                    if (sy.startsWith("~")) {
                        ry = true;
                        sy = sy.substring(1);
                    }
                    int x = rx && sx.length() == 0 ? 0 : parseInt(sx, 1, ContainerSubpocket.WIDTH - 17);
                    int y = ry && sy.length() == 0 ? 0 : parseInt(sy, 1, ContainerSubpocket.HEIGHT - 17);

                    int meta = args.length > 4 ? parseInt(args[4]) : 0;

                    ItemStack ref = new ItemStack(item, 1, meta);
                    fetchNBT(ref, args, 5);

                    ISubpocket storage = CapabilitySubpocket.get(player);
                    List<ISubpocketStack> toElevate = new LinkedList<>();

                    for (ISubpocketStack stack : storage) {
                        ItemStack copy = stack.getRef();
                        if (copy.getItem() == ref.getItem()
                                && (ref.getItemDamage() == WILDCARD_VALUE || copy.getItemDamage() == ref.getItemDamage())
                                && (ref.getTagCompound() == null || Objects.equals(ref.getTagCompound(), copy.getTagCompound()))) {

                            stack.setPos(x + (rx ? stack.getX() : 0), y + (ry ? stack.getY() : 0));
                            toElevate.add(stack);

                            answer("success", copy.getTextComponent(), player.getDisplayName());
                        }
                    }
                    toElevate.forEach(storage::elevate);

                    if (toElevate.isEmpty()) {
                        answer("failure", player.getDisplayName(), ref.getTextComponent());
                    } else {
                        SyncHandler.sync(player);
                    }
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                            args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) : emptyList();
                }
            },
            new Subcommand("unlock") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    EntityPlayer player = args.length == 0 ?
                            getCommandSenderAsPlayer(sender) :
                            getPlayer(server, sender, args[0]);
                    ISubpocket storage = CapabilitySubpocket.get(player);
                    if (storage.isUnlocked()) {
                        answer("failure", player.getDisplayName());
                    } else {
                        answer("success", player.getDisplayName());
                        storage.unlock();
                        SyncHandler.sync(player);
                    }
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ?
                            getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                            emptyList();
                }
            },
            new Subcommand("lock") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    EntityPlayer player = args.length == 0 ?
                            getCommandSenderAsPlayer(sender) :
                            getPlayer(server, sender, args[0]);
                    answer("success", player.getDisplayName());
                    ISubpocket storage = CapabilitySubpocket.get(player);
                    if (storage.isUnlocked()) {
                        answer("success", player.getDisplayName());
                        storage.lock();
                        SyncHandler.sync(player);
                    } else {
                        answer("failure", player.getDisplayName());
                    }
                }

                @Override
                public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                    return args.length == 1 ?
                            getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                            emptyList();
                }
            },
            new Subcommand("open") {
                @Override
                public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                    EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                    player.openGui(Subpocket.instance, 0, player.world, 0, 0, 0);
                }
            }
    };

    private final List<String> names = Arrays.stream(subcommands).map(Subcommand::getName).collect(Collectors.toList());

    private static void fetchNBT(ItemStack stack, String[] args, int pos) throws CommandException {
        if (args.length <= pos) {
            return;
        }
        String s = buildString(args, pos);
        try {
            stack.setTagCompound(JsonToNBT.getTagFromJson(s));
        } catch (NBTException e) {
            throw new CommandException("commands.give.tagError", e.getMessage());
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return "subpocket";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "command." + MODID + ":meta.usage";
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        } else {
            findSubcommand(args[0]).execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
        }
    }

    @Nonnull
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos look) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, names);
        } else if (args.length > 1) {
            try {
                return findSubcommand(args[0]).getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length));
            } catch (CommandException e) {
                // NOOP - if nah then pass to tail return
            }
        }
        return emptyList();
    }

    private BigInteger parsePosBigInt(String str) throws NumberInvalidException {
        try {
            BigInteger bigint = new BigInteger(str);
            if (bigint.signum() > 0) {
                return bigint;
            } else {
                throw new NumberInvalidException("commands.generic.num.tooSmall", bigint.toString(), 1);
            }
        } catch (NumberFormatException nfe) {
            throw new NumberInvalidException("commands.generic.num.invalid", str);
        }
    }

    private Subcommand findSubcommand(String name) throws CommandException {
        for (Subcommand subcommand : subcommands) {
            if (subcommand.getName().equals(name)) {
                return subcommand;
            }
        }
        throw new WrongUsageException("command." + MODID + ":meta.no_subcommand", name);
    }

    private abstract class Subcommand {

        protected final SubpocketCommand parent = SubpocketCommand.this; // super-stupid alias, lol
        protected final String prefix; // another dumb alias huh

        private final String name;

        public Subcommand(String name) {
            this.name = name;
            prefix = "command." + MODID + ":" + name + ".";
        }

        public String getName() {
            return name;
        }

        private final ThreadLocal<ICommandSender> sender = new ThreadLocal<>();

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (sender.canUseCommand(2, parent.getName() + " " + name)) {
                this.sender.set(sender);
                call(server, sender, args);
            } else {
                throw new CommandException("commands.generic.permission");
            }
        }

        protected void answer(String key, Object... args) {
            notifyCommandListener(sender.get(), parent, prefix + key, args);
        }

        protected void wrong() throws WrongUsageException {
            throw new WrongUsageException(prefix + "usage");
        }

        public abstract void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;

        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
            return emptyList();
        }
    }
}
