package io.github.ozozorz.aimaid.llm.network;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChatRequest(UUID maid, String action, String text) implements CustomPacketPayload {
    public static final Type<ChatRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ai-maid-llm", "request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatRequest> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ChatRequest::maid, ByteBufCodecs.stringUtf8(16), ChatRequest::action,
            ByteBufCodecs.stringUtf8(2000), ChatRequest::text, ChatRequest::new);
    @Override public Type<ChatRequest> type() { return TYPE; }
}
