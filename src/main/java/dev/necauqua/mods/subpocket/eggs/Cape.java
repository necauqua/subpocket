package dev.necauqua.mods.subpocket.eggs;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.resources.SkinManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.eggs.Name.NECAUQUA;
import static java.util.Collections.emptyMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Bus.MOD)
public final class Cape {

    private static final List<UUID> CAPED = Collections.singletonList(NECAUQUA);

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        // avoid colliding with similar code from that mod (whenever I update it)
        if (ModList.get().isLoaded("chiseled_me")) {
            return;
        }
        // and optionally registering the event handlers for chat as well (meh, whatever)
        MinecraftForge.EVENT_BUS.register(Name.class);

        SkinManager skinManager = e.getMinecraftSupplier().get().getSkinManager();
        MinecraftSessionService original = skinManager.sessionService;
        skinManager.sessionService = new MinecraftSessionService() {

            @Override
            public Map<Type, MinecraftProfileTexture> getTextures(GameProfile profile, boolean requireSecure) {
                Map<Type, MinecraftProfileTexture> textures = original.getTextures(profile, requireSecure);
                if (CAPED.contains(profile.getId())) {
                    textures.put(Type.CAPE, new MinecraftProfileTexture("https://necauqua.dev/images/cape.png", emptyMap()));
                }
                return textures;
            }

            @Override
            public void joinServer(GameProfile profile, String authenticationToken, String serverId) throws AuthenticationException {
                original.joinServer(profile, authenticationToken, serverId);
            }

            @Override
            public GameProfile hasJoinedServer(GameProfile user, String serverId, InetAddress address) throws AuthenticationUnavailableException {
                return original.hasJoinedServer(user, serverId, address);
            }

            @Override
            public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
                return original.fillProfileProperties(profile, requireSecure);
            }
        };
    }
}
