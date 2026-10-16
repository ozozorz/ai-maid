package io.github.ozozorz.aimaid.client.entity.animation;

import java.util.LinkedHashMap;
import java.util.Map;
import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.resources.Identifier;

/** Addon 在自己的 ClientModInitializer 注册；创建 Renderer 时冻结，重复 ID 直接报错。 */
public final class MaidAnimations {
    public static final Identifier MELEE = AIMaid.id("melee");
    public static final Identifier BOW = AIMaid.id("bow");
    public static final Identifier CROSSBOW_CHARGE = AIMaid.id("crossbow_charge");
    public static final Identifier CROSSBOW_HOLD = AIMaid.id("crossbow_hold");
    private static final Map<Identifier, MaidAnimationClip> CLIPS = new LinkedHashMap<>();
    private static boolean frozen;

    static {
        // 服务器已经在 swing 时判定命中；动画从挥击开始，接着收手，不额外插入伤害延迟。
        register(MELEE, new MaidAnimationClip(AnimationDefinition.Builder.withLength(0.3F)
                .addAnimation("right_arm", rotation(
                        key(0, -85, -28, -15), key(0.05F, -65, 25, -28),
                        key(0.15F, -22, 18, -12), key(0.3F, 0, 0, 0)))
                .addAnimation("body", rotation(key(0, 3, -10, 0), key(0.08F, 6, 12, 0), key(0.3F, 0, 0, 0)))
                .build(), 220, 1, true, true));
    }

    private MaidAnimations() {}
    public static void register(Identifier id, MaidAnimationClip clip) {
        if (frozen) throw new IllegalStateException("Maid animations already frozen");
        if (id == null || clip == null) throw new IllegalArgumentException("Missing animation id/clip");
        if (id.equals(BOW) || id.equals(CROSSBOW_CHARGE) || id.equals(CROSSBOW_HOLD)
                || id.equals(io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction.NONE)
                || id.equals(io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction.BOW_RELEASE)
                || id.equals(io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction.CROSSBOW_FIRE)) {
            throw new IllegalArgumentException("Reserved maid animation id: " + id);
        }
        if (CLIPS.putIfAbsent(id, clip) != null) throw new IllegalArgumentException("Duplicate maid animation: " + id);
    }
    public static MaidAnimationClip get(Identifier id) { return CLIPS.get(id); }
    public static void freeze() { frozen = true; }

    public static AnimationChannel rotation(Keyframe... frames) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, frames);
    }
    public static Keyframe key(float seconds, float x, float y, float z) {
        return new Keyframe(seconds, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }
}
