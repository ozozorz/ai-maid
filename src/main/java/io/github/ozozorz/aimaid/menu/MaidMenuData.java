package io.github.ozozorz.aimaid.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 打开 Maid GUI 时额外发给客户端的数据 
 * MaidMenuData
 * @param maidEntityId 这个 Menu 对应哪个实体？
 */
public record MaidMenuData(int maidEntityId) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MaidMenuData> STREAM_CODEC = 
        StreamCodec.composite(ByteBufCodecs.VAR_INT, MaidMenuData::maidEntityId, MaidMenuData::new);

}
