/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.JarVersionLookupHandler;
import net.minecraftforge.network.NetworkEvent.ClientCustomPayloadEvent;
import net.minecraftforge.network.NetworkEvent.ServerCustomPayloadEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nullable;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class Network {

    private static final ResourceLocation CHANNEL = ns("channel");

    @SubscribeEvent
    public static void on(FMLCommonSetupEvent e) {
        var version = JarVersionLookupHandler.getImplementationVersion(Subpocket.class).orElse("DEBUG");
        NetworkRegistry.ChannelBuilder
            .named(CHANNEL)
            .clientAcceptedVersions(version::equals)
            .serverAcceptedVersions(version::equals)
            .networkProtocolVersion(() -> version)
            .eventNetworkChannel()
            .registerObject(Handlers.class);
    }

    // need to be in separate class because of some weird Forge check
    private static final class Handlers {

        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void on(ServerCustomPayloadEvent e) {
            var nbt = e.getPayload().readNbt();
            var ctx = e.getSource().get();
            ctx.enqueueWork(() -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    SubpocketCapability.get(player).deserializeNBT(nbt);
                } else {
                    LogManager.getLogger(Subpocket.class).warn("Clientside player was null on sync!");
                }
            });
            ctx.setPacketHandled(true);
        }

        @SubscribeEvent
        public static void on(ClientCustomPayloadEvent e) {
            var payload = e.getPayload();
            var ctx = e.getSource().get();
            var player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (!(player.containerMenu instanceof SubpocketContainer container)) {
                return;
            }
            var x = payload.readFloat();
            var y = payload.readFloat();
            var index = payload.readInt();
            var b = payload.readByte();
            if (b == 0) {
                ctx.enqueueWork(() -> container.stackMoved(x, y, index));
            } else {
                ctx.enqueueWork(() -> container.processClick(x, y, new ClickState(b), index));
            }
            ctx.setPacketHandled(true);
        }
    }

    public static void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncToClient(serverPlayer);
        }
    }

    public static void syncToClient(ServerPlayer player) {
        var payload = new FriendlyByteBuf(Unpooled.buffer());
        payload.writeNbt(SubpocketCapability.get(player).serializeNBT());
        player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL, payload));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendClickToServer(float x, float y, int index, @Nullable ClickState state) {
        var payload = new FriendlyByteBuf(Unpooled.buffer());
        payload.writeFloat(x);
        payload.writeFloat(y);
        payload.writeInt(index);
        payload.writeByte(state != null ? state.toByte() : 0);
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("No client-to-server connection");
        }
        connection.send(new ServerboundCustomPayloadPacket(CHANNEL, payload));
    }
}
