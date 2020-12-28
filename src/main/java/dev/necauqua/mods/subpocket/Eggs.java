package dev.necauqua.mods.subpocket;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static java.util.Collections.emptyMap;
import static net.minecraft.util.text.TextFormatting.*;
import static net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT;

@EventBusSubscriber(modid = MODID)
public final class Eggs {

    private static IFormattableTextComponent base(String prefix) {
        return new StringTextComponent(prefix).mergeStyle(LIGHT_PURPLE);
    }

    private static IFormattableTextComponent obf(char str) {
        return new StringTextComponent(Character.toString(str)).mergeStyle(OBFUSCATED);
    }

    private static final IFormattableTextComponent[] USERNAME_VARIATIONS = {
            base("necauqua"),
            base("").append(obf('n')).appendString("ecauqua"),
            base("n").append(obf('e')).appendString("cauqua"),
            base("ne").append(obf('c')).appendString("auqua"),
            base("nec").append(obf('a')).appendString("uqua"),
            base("neca").append(obf('u')).appendString("qua"),
            base("necau").append(obf('q')).appendString("ua"),
            base("necauq").append(obf('u')).appendString("a"),
            base("necauqu").append(obf('a')),
    };

    private static final UUID NECAUQUA = new UUID(0xf98e93652c5248c5L, 0x86476662f70b7e3dL);

    @Nullable
    private static ResourceLocation cachedCape;
    @Nullable
    private static PlayerEntity authorPlayer;

    @SubscribeEvent
    public static void on(PlayerEvent.NameFormat e) {
        if (NECAUQUA.equals(e.getPlayer().getGameProfile().getId())) {
            e.setDisplayname(USERNAME_VARIATIONS[0]);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Bus.MOD)
    private static final class OnClientSetup {

        // can't just do that in PlayerEvent.NameFormat event because it is cached
        @SubscribeEvent
        public static void on(FMLClientSetupEvent e) {
            Minecraft mc = Minecraft.getInstance();
            mc.ingameGUI.chatListeners.get(ChatType.CHAT)
                    .add(0, (type, message, sender) -> {
                        if (!NECAUQUA.equals(sender)) {
                            return;
                        }
                        if ((!(message instanceof TranslationTextComponent))) {
                            return;
                        }
                        TranslationTextComponent translated = (TranslationTextComponent) message;
                        Object[] args = translated.getFormatArgs();
                        if (!"chat.type.text".equals(translated.getKey()) || args.length != 2 || !(args[0] instanceof StringTextComponent)) {
                            return;
                        }
                        int idx = ThreadLocalRandom.current().nextInt(12);
                        IFormattableTextComponent replacement = USERNAME_VARIATIONS[idx >= 8 ? 0 : idx + 1].deepCopy();

                        PlayerEntity observer = mc.player;
                        if (observer != null) {
                            IFormattableTextComponent text = new StringTextComponent("Trying to peep at\nmy entity, ")
                                    .append(observer.getDisplayName())
                                    .appendString("?\n\n   ")
                                    .append(new StringTextComponent("Naughty.").mergeStyle(ITALIC));
                            replacement.mergeStyle(Style.EMPTY.setHoverEvent(new HoverEvent(SHOW_TEXT, text)));
                        }

                        replacement.mergeStyle(((StringTextComponent) args[0]).getStyle());

                        args[0] = replacement;

                        // force it to get reinited so its children are recreated with the new argument
                        translated.field_240756_i_ = null;
                    });
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void on(TickEvent.ClientTickEvent e) {
        if (authorPlayer == null || cachedCape == null) {
            return;
        }
        ClientPlayNetHandler connection = Minecraft.getInstance().getConnection();
        assert connection != null;
        NetworkPlayerInfo playerInfo = connection.getPlayerInfo(authorPlayer.getUniqueID());
        if (playerInfo != null) {
            playerInfo.playerTextures.put(Type.CAPE, cachedCape);
            authorPlayer = null;
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void on(EntityJoinWorldEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof ClientPlayerEntity)) {
            return;
        }
        PlayerEntity player = ((ClientPlayerEntity) entity);
        if (!NECAUQUA.equals(player.getGameProfile().getId())) {
            return;
        }
        authorPlayer = player;
        if (cachedCape == null) {
            cachedCape = Minecraft.getInstance().getSkinManager()
                    .loadSkin(new MinecraftProfileTexture("https://necauqua.dev/images/cape.png", emptyMap()), Type.CAPE);
        }
    }
}
