/*
 * Copyright (c) 2017-2020 Anton Bulakh <self@necauqua.dev>
 * Licensed under MIT, see the LICENSE file for details.
 */

package dev.necauqua.mods.subpocket;

import dev.necauqua.mods.subpocket.api.ISubpocket.StackSizeMode;
import dev.necauqua.mods.subpocket.gui.ClickState;
import dev.necauqua.mods.subpocket.gui.ContainerSubpocket;
import dev.necauqua.mods.subpocket.handlers.SyncHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.IOException;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;

public final class Network {
    private static final FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(MODID);

    private Network() {}

    public static void init() {
        channel.register(Network.class);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClient(ClientCustomPacketEvent e) throws IOException {
        PacketBuffer payload = new PacketBuffer(e.getPacket().payload());
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            CapabilitySubpocket.get(player).deserializeNBT(payload.readCompoundTag());
        } else {
            SyncHandler.serverSyncedBeforeMinecraftPlayerWasThereOmgMcAndForgeCodeAreSpaghetti = payload.readCompoundTag();
        }
    }

    @SubscribeEvent
    public static void onServerReceive(ServerCustomPacketEvent e) {
        PacketBuffer payload = new PacketBuffer(e.getPacket().payload());
        EntityPlayerMP player = ((NetHandlerPlayServer) e.getHandler()).player;
        switch (payload.readByte()) {
            case 0:
                if (!(player.openContainer instanceof ContainerSubpocket)) {
                    return;
                }
                ContainerSubpocket container = (ContainerSubpocket) player.openContainer;
                float x = payload.readFloat();
                float y = payload.readFloat();
                int index = payload.readInt();
                byte b = payload.readByte();
                if (b == 0) {
                    container.stackMoved(x, y, index);
                } else {
                    container.processClick(x, y, new ClickState(b), index);
                }
                break;
            case 1:
                StackSizeMode stackSizeMode = StackSizeMode.values()[payload.readByte() % StackSizeMode.values().length];
                CapabilitySubpocket.get(player).setStackSizeMode(stackSizeMode);
        }
    }

    public static void syncToClient(EntityPlayerMP player) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeCompoundTag(CapabilitySubpocket.get(player).serializeNBT());
        channel.sendTo(new FMLProxyPacket(payload, MODID), player);
    }

    public static void sendClickToServer(float x, float y, int index, @Nullable ClickState state) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeByte(0);
        payload.writeFloat(x);
        payload.writeFloat(y);
        payload.writeInt(index);
        payload.writeByte(state != null ? state.toByte() : 0);
        channel.sendToServer(new FMLProxyPacket(payload, MODID));
    }

    public static void sendSetStackSizeModeToServer(StackSizeMode stackSizeMode) {
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
        payload.writeByte(1);
        payload.writeByte(stackSizeMode.ordinal());
        channel.sendToServer(new FMLProxyPacket(payload, MODID));
    }
}
