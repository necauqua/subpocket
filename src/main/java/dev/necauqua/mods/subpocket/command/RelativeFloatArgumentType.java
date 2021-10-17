package dev.necauqua.mods.subpocket.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.commands.synchronization.brigadier.BrigadierArgumentSerializers;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.CompletableFuture;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

public record RelativeFloatArgumentType(float minimum,
                                        float maximum) implements ArgumentType<RelativeFloatArgumentType.RelativeFloat> {

    static {
        ArgumentTypes.register(MODID + ":rel_float", RelativeFloatArgumentType.class, new Serializer());
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

    @Override
    public RelativeFloat parse(StringReader reader) throws CommandSyntaxException {
        var start = reader.getCursor();
        var isRelative = reader.canRead() && reader.peek() == '~';
        if (isRelative) {
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
    public String toString() {
        if (minimum == Float.MIN_VALUE && maximum == Float.MAX_VALUE) {
            return "relFloat()";
        } else if (maximum == Float.MAX_VALUE) {
            return "relFloat(" + minimum + ")";
        } else {
            return "relFloat(" + minimum + ", " + maximum + ")";
        }
    }

    public record RelativeFloat(float value, boolean isRelative) {

        public float get() {
            return value;
        }

        public float get(float origin) {
            return isRelative ? origin + value : value;
        }

        @Override
        public String toString() {
            return isRelative ?
                "~" + value :
                Float.toString(value);
        }
    }

    private static final class Serializer implements ArgumentSerializer<RelativeFloatArgumentType> {

        public void serializeToNetwork(RelativeFloatArgumentType argument, FriendlyByteBuf buffer) {
            var min = argument.minimum != Float.MIN_VALUE;
            var max = argument.maximum != Float.MAX_VALUE;
            buffer.writeByte(BrigadierArgumentSerializers.createNumberFlags(min, max));
            if (min) {
                buffer.writeFloat(argument.minimum);
            }
            if (max) {
                buffer.writeFloat(argument.maximum);
            }
        }

        public RelativeFloatArgumentType deserializeFromNetwork(FriendlyByteBuf buffer) {
            var minmax = buffer.readByte();
            var min = BrigadierArgumentSerializers.numberHasMin(minmax) ? buffer.readFloat() : Float.MIN_VALUE;
            var max = BrigadierArgumentSerializers.numberHasMax(minmax) ? buffer.readFloat() : Float.MAX_VALUE;
            return RelativeFloatArgumentType.relativeFloat(min, max);
        }

        public void serializeToJson(RelativeFloatArgumentType argument, JsonObject json) {
            if (argument.minimum != Float.MIN_VALUE) {
                json.addProperty("min", argument.minimum);
            }
            if (argument.maximum != Float.MAX_VALUE) {
                json.addProperty("max", argument.maximum);
            }
        }
    }
}

