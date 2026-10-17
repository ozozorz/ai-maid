package io.github.ozozorz.aimaid.hauling;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;

/** 配置持久化；原因文本是当前服务端运行结果，不保存旧寻路或进度。 */
public final class HaulingData {
    public static final AttachmentType<GlobalPos> CENTER = AttachmentRegistry.createPersistent(HaulingAddon.id("center"), GlobalPos.CODEC);
    public static final AttachmentType<Integer> RADIUS = AttachmentRegistry.createPersistent(HaulingAddon.id("radius"), Codec.intRange(2, 32));
    public static final AttachmentType<Component> STATUS = AttachmentRegistry.create(HaulingAddon.id("status"));
    public static int radius(AiMaidEntity maid) { return maid.getAttachedOrElse(RADIUS, 32); }
    public static Component status(AiMaidEntity maid) { return maid.getAttachedOrElse(STATUS, Component.translatable("status.ai-maid-hauling.searching")); }
    public static void status(AiMaidEntity maid, String reason) { maid.setAttached(STATUS, Component.translatable("status.ai-maid-hauling." + reason)); }
    public static void initialize() {}
}
