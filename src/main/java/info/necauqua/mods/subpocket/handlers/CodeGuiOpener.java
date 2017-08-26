/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.handlers;

import info.necauqua.mods.subpocket.CapabilitySubpocket;
import info.necauqua.mods.subpocket.Network;
import info.necauqua.mods.subpocket.packet.PacketOpenPocket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class CodeGuiOpener {

    private static int code;
    private static int pressed;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(CodeGuiOpener.class);
        code = SubpocketConditions.getCode(Minecraft.getMinecraft().getSession().getProfile().getId());
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.KeyInputEvent e) {
        if(Keyboard.getEventKeyState()) {
            int key = Keyboard.getEventKey();
            if(key >= 2 && key <= 10) { // 'hotbar' keys
                key -= 1;
            }else if(key == 82) { // and also those weird numpad codes
                key = 0;
            }else if(key >= 79 && key <= 81) {
                key -= 78;
            }else if(key >= 75 && key <= 77) {
                key -= 71;
            }else if(key >= 71 && key <= 73) {
                key -= 64;
            }else {
                return;
            }
            pressed = (pressed * 10) % 10000 + key;
            if(pressed == code && CapabilitySubpocket.get(Minecraft.getMinecraft().player).isAvailableToPlayer()) {
                Network.sendToServer(new PacketOpenPocket());
            }
        }
    }
}
