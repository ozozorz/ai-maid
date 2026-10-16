package io.github.ozozorz.aimaid.entity.animation;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.resources.Identifier;

/** 同步的是动作时间轴，不是骨骼坐标；本数据不写入存档。 */
public record MaidAnimationAction(Identifier id, long startedAt, int durationTicks, int sequence) {
    public static final Identifier NONE = AIMaid.id("none");
    public static final Identifier BOW_RELEASE = AIMaid.id("bow_release");
    public static final Identifier CROSSBOW_FIRE = AIMaid.id("crossbow_fire");
    public static final int MAX_DURATION = 1200;

    public boolean isActive(long now) {
        return !id.equals(NONE) && durationTicks > 0 && now < startedAt + durationTicks;
    }

    public float elapsed(long now, float partialTick) {
        return Math.max(0, (float) (now - startedAt) + partialTick);
    }
}
