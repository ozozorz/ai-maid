package io.github.ozozorz.aimaid.llm.network;

import io.github.ozozorz.aimaid.llm.LlmAddon;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class LlmNetworking {
    private LlmNetworking() {}
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(ChatRequest.TYPE, ChatRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChatEvent.TYPE, ChatEvent.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChatRequest.TYPE, (packet, context) -> {
            var service = LlmAddon.service(context.server());
            if (service == null) return;
            var player = context.player();
            var maid = packet.action().equals("cancel") ? service.resolveCancellation(player, packet.maid()) : service.resolve(player, packet.maid());
            if (maid == null) { service.error(player, packet.maid(), "unavailable"); return; }
            switch (packet.action()) {
                case "open" -> service.open(player, maid);
                case "send" -> service.chat(player, maid, packet.text());
                case "cancel" -> service.cancel(player, maid, false);
                case "clear" -> service.cancel(player, maid, true);
                case "persona" -> service.persona(player, maid, packet.text());
                default -> service.error(player, packet.maid(), "invalid_message");
            }
        });
    }
    public static void send(ServerPlayer player, UUID maid, String kind, String text, boolean busy) {
        if (ServerPlayNetworking.canSend(player, ChatEvent.TYPE)) ServerPlayNetworking.send(player, new ChatEvent(maid, kind, text, busy));
        else if (!kind.equals("busy") && !kind.equals("open")) player.sendSystemMessage(LlmText.line(kind, text));
    }
}
