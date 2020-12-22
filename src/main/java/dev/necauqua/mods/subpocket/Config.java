package dev.necauqua.mods.subpocket;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.stream.JsonReader;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.ModConfigEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraftforge.fml.config.ModConfig.Type.CLIENT;
import static net.minecraftforge.fml.config.ModConfig.Type.SERVER;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class Config {

    // clientside
    public static final class Client {
        public static boolean disablePixelPicking = false;
    }

    // serverside and synced to client
    public static boolean blockEnderChests = true;
    public static boolean allowBreakingUnbreakable = true;
    public static boolean subspatialKeyFrozen = true;
    public static boolean subspatialKeyNoDespawn = true;

    public static void init() {
        Map<String, String> strings = readDefaultLocaleStrings();
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(CLIENT, define(Config.Client.class, strings));
        context.registerConfig(SERVER, define(Config.class, strings));
    }

    @SubscribeEvent
    public static void on(ModConfigEvent e) { // on both loading and reloading events, huh
        ModConfig config = e.getConfig();
        switch (config.getType()) {
            case CLIENT:
                load(Client.class, config.getConfigData());
                break;
            case SERVER:
                load(Config.class, config.getConfigData());
                break;
        }
    }

    private static void load(Class<?> holder, CommentedConfig config) {
        try {
            for (Field field : holder.getFields()) {
                field.set(null, config.get(LOWER_CAMEL.to(LOWER_UNDERSCORE, field.getName())));
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static ForgeConfigSpec define(Class<?> holder, Map<String, String> strings) {
        try {
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            for (Field field : holder.getFields()) {
                String name = LOWER_CAMEL.to(LOWER_UNDERSCORE, field.getName());
                builder.comment(strings.get("config." + MODID + ":" + name + ".tooltip"))
                        .translation("config." + MODID + ":subspatial_key_no_despawn")
                        .define(name, field.get(null));
            }
            return builder.build();
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // yes I am adapting those now unused .tooltip strings so I wont have
    // to duplicate (well, no longer, but whatever) them in config comments
    private static Map<String, String> readDefaultLocaleStrings() {
        try (InputStream lang = Subpocket.class.getResourceAsStream("/assets/subpocket/lang/en_us.json")) {
            Map<String, String> strings = new HashMap<>();
            if (lang == null) {
                throw new IOException(); // jump to catch
            }
            JsonReader reader = new JsonReader(new InputStreamReader(lang));
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
