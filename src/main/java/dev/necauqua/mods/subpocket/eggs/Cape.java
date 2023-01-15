package dev.necauqua.mods.subpocket.eggs;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

@EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Bus.MOD)
@OnlyIn(Dist.CLIENT)
public final class Cape {

    private static final List<UUID> CAPED = Collections.singletonList(NECAUQUA);

    @SubscribeEvent
    public static void on(FMLClientSetupEvent e) {
        MinecraftForge.EVENT_BUS.register(Name.class);

        var skinManager = Minecraft.getInstance().getSkinManager();
        var original = skinManager.sessionService;
        skinManager.sessionService = new MinecraftSessionService() {

            @Override
            public Map<Type, MinecraftProfileTexture> getTextures(GameProfile profile, boolean requireSecure) {
                var textures = original.getTextures(profile, requireSecure);
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

            @Override
            public String getSecurePropertyValue(Property property) throws InsecurePublicKeyException {
                return original.getSecurePropertyValue(property);
            }
        };
    }
}
