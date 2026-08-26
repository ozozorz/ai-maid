package io.github.ozozorz.aimaid.test;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public class TestAddonMaidData {

    public static final int DEFAULT_PATROL_RADIUS = 4;

    // TestAddonMaidData.PATROL_RADIUS 的类型是 AttachmentType<Integer>
    // 它不是 radius = 4 本身
    // 它表达的是：存在一种叫 testaddon:patrol_radius、值类型为 Integer 的 Entity Attachment。
    public static final AttachmentType<Integer> PATROL_RADIUS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("testaddon", "patrol_radius"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.all()));

    public static final AttachmentType<GlobalPos> PATROL_CENTER = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath("testaddon", "patrol_center"),
            GlobalPos.CODEC);

    private TestAddonMaidData() {
    }

    public static void initialize() {
    }

    public static int getPatrolRadius(AiMaidEntity maid) {
        return maid.getAttachedOrElse(PATROL_RADIUS, DEFAULT_PATROL_RADIUS);
    }

    public static void setPatrolRadius(AiMaidEntity maid, int radius) {
        // 表示：这一只 Maid -> testaddon:patrol_radius -> 4
        maid.setAttached(PATROL_RADIUS, radius);
    }

    @Nullable
    public static GlobalPos getPatrolCentere(AiMaidEntity maid) {
        return maid.getAttached(PATROL_CENTER);
    }

    public static void setPatrolCenter(AiMaidEntity maid) {
        maid.setAttached(PATROL_CENTER, GlobalPos.of(maid.level().dimension(), maid.blockPosition()));
    }

}
