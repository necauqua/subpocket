/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket.StackSizeMode;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.JarVersionLookupHandler;
import net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent;
import net.minecraftforge.fml.network.NetworkEvent.Context;
import net.minecraftforge.fml.network.NetworkEvent.ServerCustomPayloadEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;

import javax.annotation.Nullable;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static dev.necauqua.mods.subpocket.Subpocket.ns;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public final class Network {

    private static final ResourceLocation CHANNEL = ns("channel");

    @SubscribeEvent
    public static void on(FMLCommonSetupEvent e2) {
        String version = JarVersionLookupHandler.getImplementationVersion(Subpocket.class).orElse("DEBUG");
        NetworkRegistry.newEventChannel(
                CHANNEL,
                () -> version,
                version::equals,
                version::equals
        ).registerObject(Network.class);
    }

    @OnlyIn(Dist.CLIENT)
    @EventBusSubscriber(value = Dist.CLIENT, modid = MODID)
    private static final class Hack {

        @Nullable
        public static CompoundNBT serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti;

        @SubscribeEvent
        public static void onEntityJoinedWorld(EntityJoinWorldEvent e) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            CompoundNBT nbt = serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti;
            if (nbt != null && e.getEntity() == player) {
                LogManager.getLogger(Subpocket.class).warn("DUMB WORKAROUND WORKED, PFEW");
                serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti = null;
                SubpocketCapability.get(player).deserializeNBT(nbt);
            }
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClient(ServerCustomPayloadEvent e) {
        CompoundNBT nbt = e.getPayload().readCompoundTag();
        Context ctx = e.getSource().get();
        ctx.enqueueWork(() -> {
            PlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                SubpocketCapability.get(player).deserializeNBT(nbt);
            } else {
                LogManager.getLogger(Subpocket.class).warn("CLIENTSIDE PLAYER WAS NULL ON SYNC, USING DUMB WORKAROUND");
                Hack.serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti = nbt;
            }
        });
        ctx.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void onServerReceive(ClientCustomPayloadEvent e) {
        PacketBuffer payload = e.getPayload();
        Context ctx = e.getSource().get();
        ServerPlayerEntity player = ctx.getSender();
        if (player == null) {
            return;
        }
        switch (payload.readByte()) {
            case 0:
                if (!(player.openContainer instanceof SubpocketContainer)) {
                    return;
                }
                SubpocketContainer container = (SubpocketContainer) player.openContainer;
                float x = payload.readFloat();
                float y = payload.readFloat();
                int index = payload.readInt();
                byte b = payload.readByte();
                if (b == 0) {
                    ctx.enqueueWork(() -> container.stackMoved(x, y, index));
                } else {
                    ctx.enqueueWork(() -> container.processClick(x, y, new ClickState(b), index));
                }
                ctx.setPacketHandled(true);
                break;
            case 1:
                StackSizeMode stackSizeMode = StackSizeMode.values()[payload.readByte() % StackSizeMode.values().length];
                ctx.enqueueWork(() -> SubpocketCapability.get(player).setStackSizeMode(stackSizeMode));
                ctx.setPacketHandled(true);
        }
    }

    public static void syncToClient(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity) {
            syncToClient((ServerPlayerEntity) player);
        }
    }

    public static void syncToClient(ServerPlayerEntity player) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeCompoundTag(SubpocketCapability.get(player).serializeNBT());
        player.connection.sendPacket(new SCustomPayloadPlayPacket(CHANNEL, payload));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendClickToServer(float x, float y, int index, @Nullable ClickState state) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeByte(0);
        payload.writeFloat(x);
        payload.writeFloat(y);
        payload.writeInt(index);
        payload.writeByte(state != null ? state.toByte() : 0);
        ClientPlayNetHandler connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("No client-to-server connection");
        }
        connection.sendPacket(new CCustomPayloadPacket(CHANNEL, payload));
    }

    @OnlyIn(Dist.CLIENT)
    public static void sendSetStackSizeModeToServer(StackSizeMode stackSizeMode) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeByte(1);
        payload.writeByte(stackSizeMode.ordinal());
        ClientPlayNetHandler connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("No client-to-server connection");
        }
        connection.sendPacket(new CCustomPayloadPacket(CHANNEL, payload));
    }
}
