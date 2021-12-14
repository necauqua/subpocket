package dev.necauqua.mods.subpocket.eggs;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static dev.necauqua.mods.subpocket.Subpocket.MODID;
import static net.minecraft.ChatFormatting.LIGHT_PURPLE;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;
import static net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT;

@EventBusSubscriber(value = Dist.CLIENT, modid = MODID)
@OnlyIn(Dist.CLIENT)
public final class Name {

    public static final UUID NECAUQUA = new UUID(0xf98e93652c5248c5L, 0x86476662f70b7e3dL);
    private static final String USERNAME = "necauqua";
    private static final TextColor COLOR = TextColor.fromLegacyFormat(LIGHT_PURPLE);

    private static boolean incoming = false;
    private static final Set<GuiMessage<FormattedCharSequence>> handled = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Entity, Component> nameplates = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void on(PlayerEvent.NameFormat e) {
        if (NECAUQUA.equals(e.getPlayer().getGameProfile().getId())) {
            // sadly there is no reason to just return CoolComponent here
            // instead of doing cringe chat stuff below
            // because it does not survive being sent through the network as there are
            // no custom component support and thus no custom component serializers
            e.setDisplayname(e.getDisplayname().copy().withStyle(LIGHT_PURPLE));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void on(RenderNameplateEvent e) {
        if (USERNAME.equals(e.getOriginalContent().getString())) {
            e.setContent(nameplates.computeIfAbsent(e.getEntity(), $ -> new CoolComponent()));
        }
    }

    @SubscribeEvent
    public static void on(ClientChatReceivedEvent e) {
        // 'run code on the next tick'
        // so that drawnGuiMessages are populated
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

        var mc = Minecraft.getInstance();
        var ingameGUI = mc.gui;
        for (var chatLine : ingameGUI.getChat().trimmedMessages) {
            if (handled.add(chatLine) && extractString(chatLine.getMessage()).contains(USERNAME)) {
                chatLine.message = new TickReplacer(chatLine.message, () -> obfuscateName(USERNAME));
            }
        }
    }

    private static String extractString(FormattedCharSequence reorderingProcessor) {
        var sb = new StringBuilder();
        reorderingProcessor.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    @SuppressWarnings("SameParameterValue")
    private static FormattedCharSequence obfuscateName(String name) {
        var style = Style.EMPTY
            .withColor(COLOR)
            .withInsertion(USERNAME)
            .withClickEvent(new ClickEvent(SUGGEST_COMMAND, "/msg " + USERNAME + " "));

        // an easter egg inside of an easter egg, lol
        var observer = Minecraft.getInstance().player;
        if (observer != null && !observer.getGameProfile().getId().equals(NECAUQUA)) { // prevent infinite recursion because of NameFormat
            var hovered = new TextComponent("hello, ").append(observer.getDisplayName());
            style = style.withHoverEvent(new HoverEvent(SHOW_TEXT, hovered));
        }

        var codePoints = name.codePoints().toArray();
        var rng = ThreadLocalRandom.current();

        // add a possibility of clean username to show up once in a tick
        if (rng.nextInt(codePoints.length) == 0) {
            return FormattedCharSequence.forward(name, style);
        }

        // up to half of the characters are obf
        var indices = new int[codePoints.length / 2];
        Arrays.setAll(indices, i -> rng.nextInt(codePoints.length));

        var finalStyle = style;
        var obfuscated = style.setObfuscated(true);
        return consumer -> {
            var ptr = indices.length - 1;
            for (var i = 0; i < codePoints.length; i++) {
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

    private static final class CoolComponent extends BaseComponent {

        private final TickReplacer visual = new TickReplacer(null, () -> obfuscateName(USERNAME));

        @Override
        public String getContents() {
            return USERNAME;
        }

        @Override
        public FormattedCharSequence getVisualOrderText() {
            return visual;
        }

        @Override
        public BaseComponent plainCopy() {
            return new CoolComponent();
        }
    }

    private static final class TickReplacer implements FormattedCharSequence {

        @Nullable
        private final FormattedCharSequence original;
        private final Supplier<FormattedCharSequence> replacementSupplier;

        private FormattedCharSequence replacement;
        private long lastTime;

        public TickReplacer(@Nullable FormattedCharSequence original, Supplier<FormattedCharSequence> replacementSupplier) {
            this.original = original;
            this.replacementSupplier = replacementSupplier;

            replacement = replacementSupplier.get();
            var mc = Minecraft.getInstance();
            lastTime = mc.level != null ? mc.level.getGameTime() : 0;
        }

        @Override
        public boolean accept(FormattedCharSink consumer) {
            var mc = Minecraft.getInstance();
            var currentTime = mc.level != null ? mc.level.getGameTime() : 0;
            if (currentTime != lastTime) {
                lastTime = currentTime;
                replacement = replacementSupplier.get();
            }
            return original != null ?
                original.accept(new ReplacingConsumer(consumer, USERNAME, replacement)) :
                replacement.accept(consumer);
        }
    }

    private static final class ReplacingConsumer implements FormattedCharSink {

        private final FormattedCharSink consumer;

        private final int[] buffer1;
        private final Style[] buffer2;
        private final int[] buffer3;

        private final int[] target;
        private final FormattedCharSequence replacement;

        private int bufferPointer = 0;

        public ReplacingConsumer(FormattedCharSink consumer, String target, FormattedCharSequence replacement) {
            this.consumer = consumer;
            this.target = target.codePoints().toArray();
            var length = this.target.length;
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
                for (var i = 0; i < bufferPointer; i++) {
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

