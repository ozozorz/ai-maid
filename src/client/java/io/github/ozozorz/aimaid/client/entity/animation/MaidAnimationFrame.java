package io.github.ozozorz.aimaid.client.entity.animation;

import java.util.List;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/** 一帧的只读快照，世界渲染、护甲和 GUI 都只读取它，不推进播放时钟。 */
public record MaidAnimationFrame(float run, float airborne, float rising, float water,
        float hurt, float idlePhase, Identifier action, List<Layer> layers) {
    public static final MaidAnimationFrame EMPTY = new MaidAnimationFrame(0, 0, 0, 0, 0, 0,
            MaidAnimationAction.NONE, List.of());
    public MaidAnimationFrame { layers = List.copyOf(layers); }
    public record Layer(Identifier id, float weight, float elapsedTicks, HumanoidArm arm,
            float useTicks, float chargeTicks) {}
}
