package dev.necauqua.mods.subpocket.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public record RelativeFloatArgumentType(float minimum,
                                        float maximum) implements ArgumentType<RelativeFloatArgumentType.RelativeFloat>, ArgumentTypeInfo.Template<RelativeFloatArgumentType> {

    @SubscribeEvent
    public static void on(RegisterEvent e) {
        e.register(ForgeRegistries.Keys.COMMAND_ARGUMENT_TYPES, ns("rel_float"), () -> RelativeFloatArgumentTypeInfo.INSTANCE);
        ArgumentTypeInfos.registerByClass(RelativeFloatArgumentType.class, RelativeFloatArgumentTypeInfo.INSTANCE);
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
    public RelativeFloatArgumentType instantiate(CommandBuildContext p_235378_) {
        return this;
    }

    @Override
    public ArgumentTypeInfo<RelativeFloatArgumentType, ?> type() {
        return RelativeFloatArgumentTypeInfo.INSTANCE;
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

    private static final class RelativeFloatArgumentTypeInfo implements ArgumentTypeInfo<RelativeFloatArgumentType, RelativeFloatArgumentType> {

        public static final RelativeFloatArgumentTypeInfo INSTANCE = new RelativeFloatArgumentTypeInfo();

        @Override
        public void serializeToNetwork(RelativeFloatArgumentType argument, FriendlyByteBuf buffer) {
            var min = argument.minimum != Float.MIN_VALUE;
            var max = argument.maximum != Float.MAX_VALUE;
            buffer.writeByte(ArgumentUtils.createNumberFlags(min, max));
            if (min) {
                buffer.writeFloat(argument.minimum);
            }
            if (max) {
                buffer.writeFloat(argument.maximum);
            }
        }

        @Override
        public RelativeFloatArgumentType deserializeFromNetwork(FriendlyByteBuf buffer) {
            var minmax = buffer.readByte();
            var min = ArgumentUtils.numberHasMin(minmax) ? buffer.readFloat() : Float.MIN_VALUE;
            var max = ArgumentUtils.numberHasMax(minmax) ? buffer.readFloat() : Float.MAX_VALUE;
            return RelativeFloatArgumentType.relativeFloat(min, max);
        }

        @Override
        public void serializeToJson(RelativeFloatArgumentType argument, JsonObject json) {
            if (argument.minimum != Float.MIN_VALUE) {
                json.addProperty("min", argument.minimum);
            }
            if (argument.maximum != Float.MAX_VALUE) {
                json.addProperty("max", argument.maximum);
            }
        }

        @Override
        public RelativeFloatArgumentType unpack(RelativeFloatArgumentType argument) {
            return argument;
        }
    }
}

