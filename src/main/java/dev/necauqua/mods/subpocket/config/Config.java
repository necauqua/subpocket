package dev.necauqua.mods.subpocket.config;

import com.google.gson.stream.JsonReader;
import dev.necauqua.mods.subpocket.Subpocket;
import dev.necauqua.mods.subpocket.api.ISubpocketStack.OverflowType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraftforge.fml.config.ModConfig.Type.CLIENT;
import static net.minecraftforge.fml.config.ModConfig.Type.SERVER;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class Config {

    // clientside
    public static ConfigValue<OverflowType> overflowType;
    public static ConfigValue<StackCountCondition> stackCountCondition;
    public static ConfigValue<StackCountSize> stackCountSize;
    public static ConfigValue<PickingMode> pickingMode;

    // serverside and synced to client
    public static ConfigValue<Boolean> blockEnderChests;
    public static ConfigValue<Boolean> allowBreakingUnbreakable;
    public static ConfigValue<Boolean> subspatialKeyFrozen;
    public static ConfigValue<Boolean> subspatialKeyNoDespawn;

    private static final Map<String, String> strings = readDefaultLocaleStrings();

    private static Builder adjust(Builder builder, String name) {
        var key = "config." + MODID + ":" + name + ".tooltip";
        return builder
            .comment(strings.getOrDefault(key, key))
            .translation("config." + MODID + ":" + name);
    }

    private static <E extends Enum<E>> ConfigValue<E> defineEnum(ForgeConfigSpec.Builder builder, String name, E defaultValue) {
        return adjust(builder, name).defineEnum(name, defaultValue);
    }

    private static <T> ConfigValue<T> define(ForgeConfigSpec.Builder builder, String name, T defaultValue) {
        return adjust(builder, name).define(name, defaultValue);
    }

    public static ForgeConfigSpec defineClient() {
        var clientConfig = new ForgeConfigSpec.Builder();
        overflowType = defineEnum(clientConfig, "overflow_type", OverflowType.SCIENTIFIC);
        stackCountCondition = defineEnum(clientConfig, "stack_count_condition", StackCountCondition.ALWAYS);
        stackCountSize = defineEnum(clientConfig, "stack_count_size", StackCountSize.LARGE);
        pickingMode = defineEnum(clientConfig, "picking_mode", PickingMode.PIXEL);
        return clientConfig.build();
    }

    public static ForgeConfigSpec defineCommon() {
        var clientConfig = new ForgeConfigSpec.Builder();
        blockEnderChests = define(clientConfig, "block_ender_chests", true);
        allowBreakingUnbreakable = define(clientConfig, "allow_breaking_unbreakable", true);
        subspatialKeyFrozen = define(clientConfig, "subspatial_key_frozen", true);
        subspatialKeyNoDespawn = define(clientConfig, "subspatial_key_no_despawn", false);
        return clientConfig.build();
    }

    public static void init() {
        var context = ModLoadingContext.get();
        context.registerConfig(CLIENT, defineClient());
        context.registerConfig(SERVER, defineCommon());
    }

    // yes I am adapting those now unused .tooltip strings, so I won't have
    // to duplicate (well, no longer, but whatever) them in config comments
    private static Map<String, String> readDefaultLocaleStrings() {
        try (var lang = Subpocket.class.getResourceAsStream("/assets/subpocket/lang/en_us.json")) {
            var strings = new HashMap<String, String>();
            if (lang == null) {
                throw new IOException(); // jump to catch
            }
            var reader = new JsonReader(new InputStreamReader(lang));
            reader.beginObject();
            while (reader.hasNext()) {
                strings.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
            return strings;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the default localization from mod's JAR file");
        }
    }
}
