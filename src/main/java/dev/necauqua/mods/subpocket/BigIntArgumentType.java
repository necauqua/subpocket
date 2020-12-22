package dev.necauqua.mods.subpocket;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.necauqua.mods.subpocket.api.ISubpocketStack;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraft.command.arguments.IArgumentSerializer;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Objects;

import static com.mojang.brigadier.StringReader.isAllowedNumber;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

public final class BigIntArgumentType implements ArgumentType<BigInteger> {
    @Nullable
    private final BigInteger minimum;

    @Nullable
    private final BigInteger maximum;

    static {
        ArgumentTypes.register(ns("bigint"), BigIntArgumentType.class, new Serializer());
    }

    private BigIntArgumentType(@Nullable BigInteger minimum, @Nullable BigInteger maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
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

    @Nullable
    public BigInteger getMinimum() {
        return minimum;
    }

    @Nullable
    public BigInteger getMaximum() {
        return maximum;
    }

    private static BigInteger readBigInt(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        char last = '\0';
        while (reader.canRead() && isAllowedNumber(last = reader.peek()) && last != '-') {
            reader.skip();
        }
        String number = reader.getString().substring(start, reader.getCursor());
        int power = ISubpocketStack.LEGEND.indexOf(last) + 1;
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
        int start = reader.getCursor();
        BigInteger result = readBigInt(reader);
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BigIntArgumentType)) return false;

        BigIntArgumentType that = (BigIntArgumentType) o;
        return Objects.equals(maximum, that.maximum) && Objects.equals(minimum, that.minimum);
    }

    @Override
    public int hashCode() {
        return 31 * (minimum != null ? minimum.hashCode() : 0) + (maximum != null ? maximum.hashCode() : 0);
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

    private static final class Serializer implements IArgumentSerializer<BigIntArgumentType> {

        private static final byte[] EMPTY = new byte[0];

        @Override
        public void write(BigIntArgumentType argument, PacketBuffer buffer) {
            buffer.writeByteArray(argument.minimum != null ? argument.minimum.toByteArray() : EMPTY);
            buffer.writeByteArray(argument.maximum != null ? argument.maximum.toByteArray() : EMPTY);
        }

        @Override
        public BigIntArgumentType read(PacketBuffer buffer) {
            byte[] minimumBytes = buffer.readByteArray();
            BigInteger minimum = minimumBytes.length != 0 ? new BigInteger(minimumBytes) : null;
            byte[] maximumBytes = buffer.readByteArray();
            BigInteger maximum = maximumBytes.length != 0 ? new BigInteger(maximumBytes) : null;
            return new BigIntArgumentType(minimum, maximum);
        }

        @Override
        public void func_212244_a(BigIntArgumentType argument, JsonObject json) {
            if (argument.minimum != null) {
                json.addProperty("min", argument.minimum.toString());
            }
            if (argument.maximum != null) {
                json.addProperty("max", argument.maximum.toString());
            }
        }
    }
}
