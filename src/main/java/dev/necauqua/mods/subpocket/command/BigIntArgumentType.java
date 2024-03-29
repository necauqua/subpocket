package dev.necauqua.mods.subpocket.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import javax.annotation.Nullable;
import java.math.BigInteger;

import static com.mojang.brigadier.StringReader.isAllowedNumber;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public record BigIntArgumentType(@Nullable BigInteger minimum,
                                 @Nullable BigInteger maximum) implements ArgumentType<BigInteger>, ArgumentTypeInfo.Template<BigIntArgumentType> {

    @SubscribeEvent
    public static void on(RegisterEvent e) {
        e.register(ForgeRegistries.Keys.COMMAND_ARGUMENT_TYPES, ns("bigint"), () -> BigIntArgumentTypeInfo.INSTANCE);
        ArgumentTypeInfos.registerByClass(BigIntArgumentType.class, BigIntArgumentTypeInfo.INSTANCE);
    }

    public static BigIntArgumentType bigint() {
        return bigint(null);
    }

    public static BigIntArgumentType bigint(@Nullable BigInteger min) {
        return bigint(min, null);
    }

    public static BigIntArgumentType bigint(@Nullable BigInteger min, @Nullable BigInteger max) {
        return new BigIntArgumentType(min, max);
    }

    public static BigInteger getBigInt(CommandContext<?> context, String name) {
        return context.getArgument(name, BigInteger.class);
    }

    private static BigInteger readBigInt(StringReader reader) throws CommandSyntaxException {
        var start = reader.getCursor();
        var last = '\0';
        while (reader.canRead() && isAllowedNumber(last = reader.peek()) && last != '-') {
            reader.skip();
        }
        var number = reader.getString().substring(start, reader.getCursor());
        var power = ISubpocketStack.LEGEND.indexOf(last) + 1;
        if (power != 0) {
            reader.skip();
        }
        if (number.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
        }
        try {
            return power == 0 ?
                new BigInteger(number) :
                power == 1 ? // idk if this is necessary
                    new BigInteger(number).multiply(ISubpocketStack.THOUSAND) :
                    new BigInteger(number).multiply(ISubpocketStack.THOUSAND.pow(power));
        } catch (final NumberFormatException ex) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, number);
        }
    }

    @Override
    public BigInteger parse(StringReader reader) throws CommandSyntaxException {
        var start = reader.getCursor();
        var result = readBigInt(reader);
        if (minimum != null && result.compareTo(minimum) < 0) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, result, minimum);
        }
        if (maximum != null && result.compareTo(maximum) > 0) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().createWithContext(reader, result, maximum);
        }
        return result;
    }

    @Override
    public BigIntArgumentType instantiate(CommandBuildContext ctx) {
        return this;
    }

    @Override
    public ArgumentTypeInfo<BigIntArgumentType, ?> type() {
        return BigIntArgumentTypeInfo.INSTANCE;
    }

    @Override
    public String toString() {
        if (minimum == null && maximum == null) {
            return "bigint()";
        } else if (maximum == null) {
            return "bigint(" + minimum + ")";
        } else {
            return "bigint(" + minimum + ", " + maximum + ")";
        }
    }

    private static final class BigIntArgumentTypeInfo implements ArgumentTypeInfo<BigIntArgumentType, BigIntArgumentType> {

        public static final BigIntArgumentTypeInfo INSTANCE = new BigIntArgumentTypeInfo();

        private static final byte[] EMPTY = new byte[0];

        @Override
        public void serializeToNetwork(BigIntArgumentType argument, FriendlyByteBuf buffer) {
            buffer.writeByteArray(argument.minimum != null ? argument.minimum.toByteArray() : EMPTY);
            buffer.writeByteArray(argument.maximum != null ? argument.maximum.toByteArray() : EMPTY);
        }

        @Override
        public BigIntArgumentType deserializeFromNetwork(FriendlyByteBuf buffer) {
            var minimumBytes = buffer.readByteArray();
            var minimum = minimumBytes.length != 0 ? new BigInteger(minimumBytes) : null;
            var maximumBytes = buffer.readByteArray();
            var maximum = maximumBytes.length != 0 ? new BigInteger(maximumBytes) : null;
            return new BigIntArgumentType(minimum, maximum);
        }

        @Override
        public void serializeToJson(BigIntArgumentType argument, JsonObject json) {
            if (argument.minimum != null) {
                json.addProperty("min", argument.minimum.toString());
            }
            if (argument.maximum != null) {
                json.addProperty("max", argument.maximum.toString());
            }
        }

        @Override
        public BigIntArgumentType unpack(BigIntArgumentType argument) {
            return argument;
        }
    }
}
