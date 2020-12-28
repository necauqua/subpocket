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

    private static ITextComponent base(String prefix) {
        return new StringTextComponent(prefix).applyTextStyle(LIGHT_PURPLE);
    }

    private static ITextComponent obf(char str) {
        return new StringTextComponent(Character.toString(str)).applyTextStyle(OBFUSCATED);
    }

    private static final ITextComponent[] USERNAME_VARIATIONS = {
            base("necauqua"),
            base("").appendSibling(obf('n')).appendText("ecauqua"),
            base("n").appendSibling(obf('e')).appendText("cauqua"),
            base("ne").appendSibling(obf('c')).appendText("auqua"),
            base("nec").appendSibling(obf('a')).appendText("uqua"),
            base("neca").appendSibling(obf('u')).appendText("qua"),
            base("necau").appendSibling(obf('q')).appendText("ua"),
            base("necauq").appendSibling(obf('u')).appendText("a"),
            base("necauqu").appendSibling(obf('a')),
    };

    private static final UUID NECAUQUA = new UUID(0xf98e93652c5248c5L, 0x86476662f70b7e3dL);

    @Nullable
    private static ResourceLocation cachedCape;
    @Nullable
    private static PlayerEntity authorPlayer;

    @SubscribeEvent
    public static void on(PlayerEvent.NameFormat e) {
        if (NECAUQUA.equals(e.getPlayer().getGameProfile().getId())) {
            e.setDisplaynameComponent(USERNAME_VARIATIONS[0]);
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
                    .add(0, (type, message) -> {
                        if ((!(message instanceof TranslationTextComponent))) {
                            return;
                        }
                        TranslationTextComponent translated = (TranslationTextComponent) message;
                        Object[] args = translated.getFormatArgs();
                        if (!"chat.type.text".equals(translated.getKey()) || args.length != 2 || !(args[0] instanceof StringTextComponent)) {
                            return;
                        }
                        StringTextComponent firstArg = (StringTextComponent) args[0];
                        if (!"necauqua".equals(firstArg.getString())) {
                            return;
                        }
                        int idx = ThreadLocalRandom.current().nextInt(12);
                        ITextComponent replacement = USERNAME_VARIATIONS[idx >= 8 ? 0 : idx + 1].deepCopy();

                        Style style = firstArg.getStyle().createDeepCopy();

                        ClientPlayerEntity observer = mc.player;
                        if (observer != null) {
                            ITextComponent text = new StringTextComponent("Trying to peep at\nmy entity, ")
                                    .appendSibling(observer.getDisplayName())
                                    .appendText("?\n\n   ")
                                    .appendSibling(new StringTextComponent("Naughty.").applyTextStyle(ITALIC));
                            style.setHoverEvent(new HoverEvent(SHOW_TEXT, text));
                        }

                        style.setParentStyle(replacement.getStyle());
                        replacement.setStyle(style);

                        args[0] = replacement;

                        // force it to get reinited so its children are recreated with the new argument
                        translated.lastTranslationUpdateTimeInMilliseconds = -1;
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
        if (cachedCape != null) {
            return;
        }
        Minecraft.getInstance().getSkinManager().loadSkin(
                new MinecraftProfileTexture("https://necauqua.dev/images/cape.png", emptyMap()),
                Type.CAPE,
                (type, resourceLocation, _profileTexture) -> cachedCape = resourceLocation
        );
    }
}
