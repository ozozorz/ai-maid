package io.github.ozozorz.testaddon.client;

import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimationClip;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import net.minecraft.client.animation.AnimationDefinition;
import static io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations.key;
import static io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations.rotation;

public final class TestAddonAnimations {
    private TestAddonAnimations() {}
    public static void initialize() {
        MaidAnimations.register(TestAddonAnimationIds.LUMBER, new MaidAnimationClip(
                AnimationDefinition.Builder.withLength(0.3F).looping()
                        .addAnimation("right_arm", rotation(key(0, -55, -8, -12), key(0.10F, -140, -12, -8),
                                key(0.17F, -85, 8, -16), key(0.23F, -55, -8, -12), key(0.3F, -55, -8, -12)))
                        .addAnimation("left_arm", rotation(key(0, -12, 0, -8), key(0.10F, -25, 4, -12),
                                key(0.23F, -12, 0, -8), key(0.3F, -12, 0, -8)))
                        .addAnimation("body", rotation(key(0, 3, 0, 0), key(0.10F, -3, -6, 0),
                                key(0.17F, 8, 5, 0), key(0.3F, 3, 0, 0)))
                        .build(), 260, 2, true, true));
    }
}
