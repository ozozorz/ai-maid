package io.github.ozozorz.aimaid.menu;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 只发给当前菜单的查看者；容器 ID 和实体 ID 一起防止过期包写入其他菜单。 */
public record MaidStatusPayload(int containerId, int entityId, Component message) implements CustomPacketPayload {
    public static final Type<MaidStatusPayload> TYPE = new Type<>(AIMaid.id("menu_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaidStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MaidStatusPayload::containerId,
            ByteBufCodecs.VAR_INT, MaidStatusPayload::entityId,
            ComponentSerialization.STREAM_CODEC, MaidStatusPayload::message, MaidStatusPayload::new);
    @Override public Type<MaidStatusPayload> type() { return TYPE; }
}
