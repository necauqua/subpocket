package dev.necauqua.mods.subpocket.eggs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ICharacterConsumer;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static net.minecraft.util.text.TextFormatting.LIGHT_PURPLE;
import static net.minecraft.util.text.event.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.util.text.event.HoverEvent.Action.SHOW_TEXT;

@OnlyIn(Dist.CLIENT)
public final class Name {

    public static final UUID NECAUQUA = new UUID(0xf98e93652c5248c5L, 0x86476662f70b7e3dL);

    private static final String USERNAME = "necauqua";

    private static final Color COLOR = Color.fromTextFormatting(LIGHT_PURPLE);

    private static boolean incoming = false;
    private static final Set<ChatLine<IReorderingProcessor>> handled = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void on(PlayerEvent.NameFormat e) {
        // this is a mini version of this for various non-chat things
        if (NECAUQUA.equals(e.getPlayer().getGameProfile().getId())) {
            e.setDisplayname(e.getDisplayname().deepCopy().mergeStyle(LIGHT_PURPLE));
        }
    }

    @SubscribeEvent
    public static void on(ClientChatReceivedEvent e) {
        // 'run code on the next tick'
        // so that drawnChatLines are populated
        // with recreated split text components
        incoming = true;
    }

    @SubscribeEvent
    public static void on(ClientTickEvent e) {
        if (e.phase == Phase.START) {
            return;
        }

        // and then short circuit out unless we got
        // scheduled from that chat received event
        if (!incoming) {
            return;
        }
        incoming = false;

        Minecraft mc = Minecraft.getInstance();
        IngameGui ingameGUI = mc.ingameGUI;
        if (ingameGUI != null) {
            for (ChatLine<IReorderingProcessor> chatLine : ingameGUI.getChatGUI().drawnChatLines) {
                if (handled.add(chatLine) && extractString(chatLine.getLineString()).contains(USERNAME)) {
                    chatLine.lineString = new TickReplacer(chatLine.lineString, mc, () -> obfuscateName(USERNAME));
                }
            }
        }
    }

    private static String extractString(IReorderingProcessor reorderingProcessor) {
        StringBuilder sb = new StringBuilder();
        reorderingProcessor.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    @SuppressWarnings("SameParameterValue")
    private static IReorderingProcessor obfuscateName(String name) {
        Style style = Style.EMPTY
            .setColor(COLOR)
            .setInsertion(USERNAME)
            .setClickEvent(new ClickEvent(SUGGEST_COMMAND, "/msg " + USERNAME + " "));

        // an easter egg inside of an easter egg, lol
        PlayerEntity observer = Minecraft.getInstance().player;
        if (observer != null) {
            IFormattableTextComponent hovered = new StringTextComponent("hello, ").appendSibling(observer.getDisplayName());
            style = style.setHoverEvent(new HoverEvent(SHOW_TEXT, hovered));
        }

        int[] codePoints = name.codePoints().toArray();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // add a possibility of clean username to show up once in a tick
        if (rng.nextInt(codePoints.length) == 0) {
            return IReorderingProcessor.fromString(name, style);
        }

        // up to half of the characters are obf
        int[] indices = new int[codePoints.length / 2];
        Arrays.setAll(indices, i -> rng.nextInt(codePoints.length));

        Style finalStyle = style;
        Style obfuscated = style.setObfuscated(true);
        return consumer -> {
            int ptr = indices.length - 1;
            for (int i = 0; i < codePoints.length; i++) {
                // if there are obf indices left and we hit one - make it obf:
                //   this is going to miss a few of them since indices are unsorted
                //   but this is exactly what I want - *up to* half the characters, not *always*
                Style s;
                if (ptr != 0 && indices[ptr] == i) {
                    --ptr;
                    s = obfuscated;
                } else {
                    s = finalStyle;
                }
                if (!consumer.accept(i, s, codePoints[i])) {
                    return false;
                }
            }
            return true;
        };
    }

    private static final class TickReplacer implements IReorderingProcessor {

        private final IReorderingProcessor original;
        private final Minecraft mc;
        private final Supplier<IReorderingProcessor> replacementSupplier;

        private IReorderingProcessor replacement;
        private long lastTime;

        public TickReplacer(IReorderingProcessor original, Minecraft mc, Supplier<IReorderingProcessor> replacementSupplier) {
            this.original = original;
            this.mc = mc;
            this.replacementSupplier = replacementSupplier;

            replacement = replacementSupplier.get();
            lastTime = mc.world != null ? mc.world.getGameTime() : 0;
        }

        @Override
        public boolean accept(ICharacterConsumer consumer) {
            long currentTime = mc.world != null ? mc.world.getGameTime() : 0;
            if (currentTime != lastTime) {
                lastTime = currentTime;
                replacement = replacementSupplier.get();
            }
            return original.accept(new ReplacingConsumer(consumer, USERNAME, replacement));
        }
    }

    private static final class ReplacingConsumer implements ICharacterConsumer {

        private final ICharacterConsumer consumer;

        private final int[] buffer1;
        private final Style[] buffer2;
        private final int[] buffer3;

        private final int[] target;
        private final IReorderingProcessor replacement;

        private int bufferPointer = 0;

        public ReplacingConsumer(ICharacterConsumer consumer, String target, IReorderingProcessor replacement) {
            this.consumer = consumer;
            this.target = target.codePoints().toArray();
            int length = this.target.length;
            buffer1 = new int[length];
            buffer2 = new Style[length];
            buffer3 = new int[length];
            this.replacement = replacement;
        }

        @Override
        public boolean accept(int index, Style style, int codePoint) {
            // if code point didn't match current expectation..
            if (codePoint != target[bufferPointer]) {
                // .. we dump the buffer of previously matched calls ..
                for (int i = 0; i < bufferPointer; i++) {
                    if (!consumer.accept(buffer1[i], buffer2[i], buffer3[i])) {
                        return false;
                    }
                }
                // .. and reset that buffer for the future ..
                bufferPointer = 0;
                // .. and don't forget to send the code point that failed us
                return consumer.accept(index, style, codePoint);
            }
            // else, we are either done and then we send in a replacement ..
            if (bufferPointer == target.length - 1) {
                if (!replacement.accept(consumer)) {
                    return false;
                }
                bufferPointer = 0; // don't forget to reset the buffer
            } else {
                // .. or push current call to the buffer in case this is only a prefix
                buffer1[bufferPointer] = index;
                buffer2[bufferPointer] = style;
                buffer3[bufferPointer] = codePoint;
                bufferPointer += 1;
            }
            return true;
        }
    }
}

