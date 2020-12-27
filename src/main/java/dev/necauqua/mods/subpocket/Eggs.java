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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.UUID;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static java.util.Collections.emptyMap;

@EventBusSubscriber(modid = MODID)
public final class Eggs {

    private static final UUID NECAUQUA = new UUID(0xf98e93652c5248c5L, 0x86476662f70b7e3dL);

    @Nullable
    private static ResourceLocation cachedCape;
    @Nullable
    private static PlayerEntity authorPlayer;

    // classic (literally not wired up to anything in this forge, lul)
    @SubscribeEvent
    public static void on(PlayerEvent.NameFormat e) {
        if (NECAUQUA.equals(e.getPlayer().getGameProfile().getId())) {
            e.setDisplayname("§o§dnecauqua§r");
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
