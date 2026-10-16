package io.github.ozozorz.aimaid.client.entity.animation;

import java.util.Set;
import net.minecraft.client.animation.AnimationDefinition;

/** 客户端共享动作定义。关键帧中的部件就是覆盖范围；注册后不要修改原版关键帧数据。 */
public record MaidAnimationClip(AnimationDefinition definition, int priority, float transitionTicks,
        boolean mirrorMainHand, boolean aimAtLookTarget) {
    public static final Set<String> BONES = Set.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    public MaidAnimationClip {
        if (definition == null || !Float.isFinite(definition.lengthInSeconds()) || definition.lengthInSeconds() <= 0
                || !Float.isFinite(transitionTicks) || transitionTicks < 1 || transitionTicks > 20
                || priority < 0 || priority > 290 || definition.boneAnimations().isEmpty()
                || !BONES.containsAll(definition.boneAnimations().keySet())
                || aimAtLookTarget && !definition.boneAnimations().containsKey("right_arm")) {
            throw new IllegalArgumentException("Invalid maid animation clip");
        }
    }
}
