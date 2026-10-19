package io.github.ozozorz.aimaid.llm.network;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChatEvent(UUID maid, String kind, String text, boolean busy) implements CustomPacketPayload {
    public static final Type<ChatEvent> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ai-maid-llm", "event"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatEvent> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ChatEvent::maid, ByteBufCodecs.stringUtf8(16), ChatEvent::kind,
            ByteBufCodecs.stringUtf8(8192), ChatEvent::text, ByteBufCodecs.BOOL, ChatEvent::busy, ChatEvent::new);
    @Override public Type<ChatEvent> type() { return TYPE; }
}
