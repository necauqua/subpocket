/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket;

import info.necauqua.mods.subpocket.api.IPositionedBigStack;
import info.necauqua.mods.subpocket.api.ISubpocketStorage;
import info.necauqua.mods.subpocket.gui.ContainerSubpocket;
import info.necauqua.mods.subpocket.handlers.SubpocketConditions;
import info.necauqua.mods.subpocket.handlers.SubpocketSync;
import info.necauqua.mods.subpocket.impl.PositionedBigStackFactory;
import info.necauqua.mods.subpocket.util.NBT;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class SubpocketCommand extends CommandBase {

    private final Subcommand[] subcommands = {
        new Subcommand("help", false) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if(args.length == 0) {
                    for(Subcommand subcommand : subcommands) {
                        notifyCommandListener(sender, parent, "§c%s", new TextComponentTranslation(subcommand.prefix + "usage"));
                        notifyCommandListener(sender, parent, subcommand.prefix + "desc");
                    }
                }else {
                    String prefix = findSubcommand(args[0]).prefix;
                    notifyCommandListener(sender, parent, "§c%s", new TextComponentTranslation(prefix + "usage"));
                    notifyCommandListener(sender, parent, prefix + "desc");
                }
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, names) : Collections.emptyList();
            }
        },
        new Subcommand("add", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if(args.length < 2) {
                    wrong();
                }
                EntityPlayer player = getPlayer(server, sender, args[0]);
                Item item = getItemByText(sender, args[1]);

                int x = 0, y = 0, off = 0;
                if(args.length > 2 && "at".equalsIgnoreCase(args[2])) {
                    if(args.length < 5) {
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

                IPositionedBigStack stack = off != 0 ? PositionedBigStackFactory.INSTANCE.create(ref, count, x, y) :
                                                       PositionedBigStackFactory.INSTANCE.create(ref, count);

                CapabilitySubpocket.get(player).add(stack);
                SubpocketSync.sync(player);

                answer("success", stack.getRef().getTextComponent(), count.toString(), player.getDisplayName());
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) :
                       args.length == 3 ? getListOfStringsMatchingLastWord(args, "at") : Collections.emptyList();
            }
        },
        new Subcommand("remove", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if(args.length < 2) {
                    wrong();
                }
                EntityPlayer player = getPlayer(server, sender, args[0]);
                Item item = getItemByText(sender, args[1]);

                BigInteger count = args.length > 2 ? "all".equalsIgnoreCase(args[2]) ? null : parsePosBigInt(args[2]) : BigInteger.ONE;
                int meta = args.length > 3 ? parseInt(args[3]) : 0;

                ItemStack ref = new ItemStack(item, 1, meta);

                fetchNBT(ref, args, 4);

                ISubpocketStorage storage = CapabilitySubpocket.get(player);

                List<IPositionedBigStack> toRemove = new LinkedList<>();

                if(count == null) { // gone functional
                    toRemove = storage.getStacksView().stream()
                        .filter(s -> s.matches(ref))
                        .collect(Collectors.toList());
                    count = BigInteger.ZERO;
                }else {
                    for(IPositionedBigStack stack : storage) {
                        if(stack.matches(ref)) {
                            int cmp = count.compareTo(stack.getCount());
                            if(cmp > 0) {
                                toRemove.add(stack);
                                count = count.subtract(stack.getCount());
                            }else if(cmp == 0) {
                                toRemove.add(stack);
                                count = BigInteger.ZERO;
                                break;
                            }else {
                                stack.setCount(stack.getCount().subtract(count));
                                break;
                            }
                        }
                    }
                }

                toRemove.forEach(storage::remove);

                SubpocketSync.sync(player);

                BigInteger removed = toRemove.stream()
                    .map(IPositionedBigStack::getCount)
                    .reduce(BigInteger.ZERO, BigInteger::add);

                answer("success", removed.add(count).toString(), player.getDisplayName());
                // adding count which may contain leftover from partial stack removal from else branch
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) :
                       args.length == 3 ? getListOfStringsMatchingLastWord(args, "all") : Collections.emptyList();
            }
        },
        new Subcommand("clear", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                EntityPlayer player = args.length == 0 ? getCommandSenderAsPlayer(sender) : getPlayer(server, sender, args[0]);
                CapabilitySubpocket.get(player).clear();
                SubpocketSync.sync(player);
                answer("success", sender.getDisplayName());
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : Collections.emptyList();
            }
        },
        new Subcommand("move", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if(args.length < 4) {
                    wrong();
                }
                EntityPlayer player = getPlayer(server, sender, args[0]);
                Item item = getItemByText(sender, args[1]);

                String sx = args[2], sy = args[3];
                boolean rx = false, ry = false;
                if(sx.startsWith("~")) {
                    rx = true;
                    sx = sx.substring(1, sx.length());
                }
                if(sy.startsWith("~")) {
                    ry = true;
                    sy = sy.substring(1, sy.length());
                }
                int x = rx && sx.length() == 0 ? 0 : parseInt(sx, 1, ContainerSubpocket.WIDTH - 17);
                int y = ry && sy.length() == 0 ? 0 : parseInt(sy, 1, ContainerSubpocket.HEIGHT - 17);

                int meta = args.length > 4 ? parseInt(args[4]) : 0;

                ItemStack ref = new ItemStack(item, 1, meta);
                fetchNBT(ref, args, 5);

                ISubpocketStorage storage = CapabilitySubpocket.get(player);
                List<IPositionedBigStack> toElevate = new LinkedList<>();

                for(IPositionedBigStack stack : storage) {
                    ItemStack copy = stack.getRef();
                    if(copy.getItem() == ref.getItem()
                            && (ref.getItemDamage() == OreDictionary.WILDCARD_VALUE || copy.getItemDamage() == ref.getItemDamage())
                            && (ref.getTagCompound() == null || ref.getTagCompound().equals(copy.getTagCompound()))) {
                        stack.setPos(x + (rx ? stack.getX() : 0), y + (ry ? stack.getY() : 0));
                        toElevate.add(stack);

                        answer("success", copy.getTextComponent(), player.getDisplayName());
                    }
                }
                toElevate.forEach(storage::elevate);

                if(toElevate.isEmpty()) {
                    answer("failure", player.getDisplayName(), ref.getTextComponent());
                }else {
                    SubpocketSync.sync(player);
                }
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys()) : Collections.emptyList();
            }
        },
        new Subcommand("unlock", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                EntityPlayer player = args.length == 0 ? getCommandSenderAsPlayer(sender) : getPlayer(server, sender, args[0]);
                int level = args.length > 1 ? parseInt(args[1], 0, 3) : 3;
                boolean flag;
                if(level == 0) { // yup, hardcoded, meh
                    flag = !NBT.get(player).getBoolean(SubpocketConditions.NOTIME_TAG);
                    SubpocketConditions.setNoTimeTag(player, true);
                }else {
                    flag = SubpocketConditions.stager.unlock(player, level);
                }
                answer(flag ? "success" : "already", level, sender.getDisplayName());
                SubpocketConditions.stager.sync(player);
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, "0", "1", "2", "3") : Collections.emptyList();
            }
        },
        new Subcommand("lock", true) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                EntityPlayer player = args.length == 0 ? getCommandSenderAsPlayer(sender) : getPlayer(server, sender, args[0]);
                int level = args.length > 1 ? parseInt(args[1], 0, 3) : 0;
                boolean flag;
                if(level == 0) { // yup, hardcoded, meh (and also copied along with the code from above lol)
                    SubpocketConditions.setNoTimeTag(player, false);
                    flag = SubpocketConditions.stager.lock(player, 1);
                }else {
                    flag = SubpocketConditions.stager.lock(player, level);
                }
                answer(flag ? "success" : "already", level, sender.getDisplayName());
                SubpocketConditions.stager.sync(player);
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, "0", "1", "2", "3") : Collections.emptyList();
            }
        },
        new Subcommand("get", false) {

            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if(args.length == 0) {
                    wrong();
                }else {
                    EntityPlayer player = args.length == 1 ? getCommandSenderAsPlayer(sender) : getPlayer(server, sender, args[1]);
                    if("stage".equalsIgnoreCase(args[0])) {
                        answer("stage", player.getDisplayName(), SubpocketConditions.stager.getState(player));
                    }else if("code".equalsIgnoreCase(args[0])) {
                        answer("code", player.getDisplayName(), SubpocketConditions.getCode(player.getGameProfile().getId()));
                    }else {
                        wrong();
                    }
                }
            }

            @Override
            public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
                return args.length == 1 ? getListOfStringsMatchingLastWord(args, "stage", "code") :
                       args.length == 2 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : Collections.emptyList();
            }
        },
        new Subcommand("show", false) {
            @Override
            public void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                player.openGui(Subpocket.instance, 0, player.world, 0, 0, 0);
            }
        }
    };


    private final List<String> names = Arrays.stream(subcommands).map(Subcommand::getName).collect(Collectors.toList());

    private static void fetchNBT(ItemStack stack, String[] args, int pos) throws CommandException {
        if(args.length > pos) {
            String s = buildString(args, pos);
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(s));
            }catch (NBTException e) {
                throw new CommandException("commands.give.tagError", e.getMessage());
            }
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
        return Subpocket.MODID + ".command.usage";
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if(args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }else {
            findSubcommand(args[0]).execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
        }
    }

    @Nonnull
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos look) {
        if(args.length == 1) {
            return getListOfStringsMatchingLastWord(args, names);
        }else if(args.length > 1) {
            try {
                return findSubcommand(args[0]).getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length));
            }catch(CommandException e) {
                // NOOP - if nah then pass to tail return
            }
        }
        return Collections.emptyList();
    }

    private BigInteger parsePosBigInt(String str) throws NumberInvalidException {
        try {
            BigInteger bigint = new BigInteger(str);
            if(bigint.signum() > 0) {
                return bigint;
            }else {
                throw new NumberInvalidException("commands.generic.num.tooSmall", bigint.toString(), 1);
            }
        }catch (NumberFormatException nfe) {
            throw new NumberInvalidException("commands.generic.num.invalid", str);
        }
    }

    private Subcommand findSubcommand(String name) throws CommandException {
        for(Subcommand subcommand : subcommands) {
            if(subcommand.getName().equals(name)) {
                return subcommand;
            }
        }
        throw new WrongUsageException(Subpocket.MODID + ".command.no_subcommand", name);
    }

    private abstract class Subcommand {

        protected final SubpocketCommand parent = SubpocketCommand.this; // super-stupid alias, lol
        protected final String prefix; // another dumb alias huh

        private final String name;
        private final boolean op;

        public Subcommand(String name, boolean op) {
            this.name = name;
            prefix = Subpocket.MODID + ".command." + name + ".";
            this.op = op;
        }

        public String getName() {
            return name;
        }

        private ThreadLocal<ICommandSender> sender = new ThreadLocal<>();

        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if(!op || sender.canUseCommand(2, parent.getName() + " " + name)) {
                this.sender.set(sender);
                call(server, sender, args);
            }else {
                throw new CommandException("commands.generic.permission");
            }
        }

        protected void answer(String key, Object... args) {
            notifyCommandListener(sender.get(), parent, String.format("%s.command.%s.%s", Subpocket.MODID, name, key), args);
        }

        protected void wrong() throws WrongUsageException {
            throw new WrongUsageException(prefix + "usage");
        }

        public abstract void call(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;

        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
            return Collections.emptyList();
        }
    }
}
