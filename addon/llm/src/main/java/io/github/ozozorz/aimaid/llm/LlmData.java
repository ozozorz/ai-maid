package io.github.ozozorz.aimaid.llm;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.resources.Identifier;

/** 角色设定属于女仆实例；不保存聊天原文或密钥到世界存档。 */
public final class LlmData {
    public static final AttachmentType<String> PERSONA = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(LlmAddon.MOD_ID, "persona"), Codec.string(0, 2000));
    public static void initialize() {}
    private LlmData() {}
}
