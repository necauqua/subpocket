/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket;

import info.necauqua.mods.subpocket.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Network {

    private static FMLEventChannel chan = NetworkRegistry.INSTANCE.newEventDrivenChannel(Subpocket.MODID);
    private static List<Supplier<Packet>> packetFactories = new LinkedList<>();
    private static Map<Class<Packet>, Integer> packetIds = new HashMap<>();

    public static void init() {
        chan.register(Network.class);

        register(PacketOpenPocket::new);
        register(PacketSyncNBTag::new);
        register(PacketSyncStorage::new);
        register(PacketMouseReleased::new);
        register(PacketStackMoved::new);
    }

    @SuppressWarnings("unchecked")
    private static void register(Supplier<Packet> factory) {
        int id = packetFactories.size();
        packetFactories.add(factory);
        packetIds.put((Class<Packet>) factory.get().getClass(), id);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static FMLProxyPacket wrap(Packet packet) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        Integer id = packetIds.get(packet.getClass());
        if(id == null) {
            throw new IllegalArgumentException("Trying to wrap unregistered packet. " +
                    "This should NEVER EVER happen unless horrific corruptions.");
        }
        buffer.writeByte(id);
        packet.write(buffer);
        return new FMLProxyPacket(buffer, Subpocket.MODID);
    }

    @Nullable
    private static Packet unwrap(FMLProxyPacket proxyPacket) {
        PacketBuffer buf = new PacketBuffer(proxyPacket.payload());
        int id = buf.readByte();
        if(id < 0 || id >= packetFactories.size()) {
            Subpocket.logger.warn("Wrong packet discriminator (%d). " +
                    "Received corrupted packet it seems... Discarding.", id);
            return null;
        }
        Packet packet = packetFactories.get(id).get();
        packet.read(buf);
        return packet;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClientPacket(ClientCustomPacketEvent e) {
        Packet packet = unwrap(e.getPacket());
        if(packet != null) {
            packet.onClient(Minecraft.getMinecraft().player);
        }
    }

    @SubscribeEvent
    public static void onServerPacket(ServerCustomPacketEvent e) {
        Packet packet = unwrap(e.getPacket());
        if(packet != null) {
            packet.onServer(((NetHandlerPlayServer) e.getHandler()).player);
        }
    }

    public static void sendTo(Packet packet, EntityPlayerMP player) {
        chan.sendTo(wrap(packet), player);
    }

//    public static void sendToAll(Packet packet) {
//        chan.sendToAll(wrap(packet));
//    }

//    public static void sendToAllAround(Packet packet, int dim, double x, double y, double z, double range) {
//        chan.sendToAllAround(wrap(packet), new NetworkRegistry.TargetPoint(dim, x, y, z, range));
//    }


//    public static void sendToDimension(Packet packet, int dim) {
//        chan.sendToDimension(wrap(packet), dim);
//    }

//    @SideOnly(Side.CLIENT)
    public static void sendToServer(Packet packet) {
        chan.sendToServer(wrap(packet));
    }
}
