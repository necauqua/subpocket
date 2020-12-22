package dev.necauqua.mods.subpocket;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraft.command.arguments.IArgumentSerializer;
import net.minecraft.command.arguments.serializers.BrigadierSerializers;
import net.minecraft.network.PacketBuffer;

import java.util.concurrent.CompletableFuture;

import static dev.necauqua.mods.subpocket.Subpocket.ns;

public final class RelativeFloatArgumentType implements ArgumentType<RelativeFloatArgumentType.RelativeFloat> {
    private final float minimum;
    private final float maximum;

    static {
        ArgumentTypes.register(ns("rel_float"), RelativeFloatArgumentType.class, new Serializer());
    }

    private RelativeFloatArgumentType(float minimum, float maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public static RelativeFloatArgumentType relativeFloat() {
        return relativeFloat(Float.MIN_VALUE);
    }

    public static RelativeFloatArgumentType relativeFloat(float min) {
        return relativeFloat(min, Float.MAX_VALUE);
    }

    public static RelativeFloatArgumentType relativeFloat(float min, float max) {
        return new RelativeFloatArgumentType(min, max);
    }

    public static RelativeFloat getRelativeFloat(CommandContext<?> context, String name) {
        return context.getArgument(name, RelativeFloat.class);
    }

    public float getMinimum() {
        return minimum;
    }

    public float getMaximum() {
        return maximum;
    }

    @Override
    public RelativeFloat parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        boolean isRelative;
        if (isRelative = reader.canRead() && reader.peek() == '~') {
            reader.skip();
        }
        float result;
        try {
            result = reader.readFloat();
        } catch (CommandSyntaxException e) {
            // allow having just ~ and no number after it
            if (isRelative && e.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedFloat()) {
                return new RelativeFloat(0.0f, true);
            }
            throw e;
        }
        if (result < minimum) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.floatTooLow().createWithContext(reader, result, minimum);
        }
        if (result > maximum) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.floatTooHigh().createWithContext(reader, result, maximum);
        }
        return new RelativeFloat(result, isRelative);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return builder
                .suggest("~")
                .buildFuture();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RelativeFloatArgumentType that = (RelativeFloatArgumentType) o;

        return minimum == that.minimum && maximum == that.maximum;
    }

    @Override
    public int hashCode() {
        return 31 * (minimum != +0.0f ? Float.floatToIntBits(minimum) : 0) +
                (maximum != +0.0f ? Float.floatToIntBits(maximum) : 0);
    }

    @Override
    public String toString() {
        if (minimum == Float.MIN_VALUE && maximum == Float.MAX_VALUE) {
            return "relFloat()";
        } else if (maximum == Float.MAX_VALUE) {
            return "relFloat(" + minimum + ")";
        } else {
            return "relFloat(" + minimum + ", " + maximum + ")";
        }
    }

    public static final class RelativeFloat {

        private final float value;
        private final boolean isRelative;

        public RelativeFloat(float value, boolean isRelative) {
            this.value = value;
            this.isRelative = isRelative;
        }

        public float get() {
            return value;
        }

        public float get(float origin) {
            return isRelative ? origin + value : value;
        }

        public boolean isRelative() {
            return isRelative;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RelativeFloat that = (RelativeFloat) o;

            return value == that.value && isRelative == that.isRelative;
        }

        @Override
        public int hashCode() {
            return 31 * (value != +0.0f ? Float.floatToIntBits(value) : 0) + (isRelative ? 1 : 0);
        }

        @Override
        public String toString() {
            return isRelative ?
                    "~" + value :
                    Float.toString(value);
        }
    }

    private static final class Serializer implements IArgumentSerializer<RelativeFloatArgumentType> {
        public void write(RelativeFloatArgumentType argument, PacketBuffer buffer) {
            boolean min = argument.minimum != Float.MIN_VALUE;
            boolean max = argument.maximum != Float.MAX_VALUE;
            buffer.writeByte(BrigadierSerializers.minMaxFlags(min, max));
            if (min) {
                buffer.writeFloat(argument.minimum);
            }
            if (max) {
                buffer.writeFloat(argument.maximum);
            }
        }

        public RelativeFloatArgumentType read(PacketBuffer buffer) {
            byte minmax = buffer.readByte();
            float min = BrigadierSerializers.hasMin(minmax) ? buffer.readFloat() : Float.MIN_VALUE;
            float max = BrigadierSerializers.hasMax(minmax) ? buffer.readFloat() : Float.MAX_VALUE;
            return RelativeFloatArgumentType.relativeFloat(min, max);
        }

        public void func_212244_a(RelativeFloatArgumentType argument, JsonObject json) {
            if (argument.minimum != Float.MIN_VALUE) {
                json.addProperty("min", argument.minimum);
            }
            if (argument.maximum != Float.MAX_VALUE) {
                json.addProperty("max", argument.maximum);
            }
        }
    }
}

