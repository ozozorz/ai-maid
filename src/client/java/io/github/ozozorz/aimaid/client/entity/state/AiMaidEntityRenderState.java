package io.github.ozozorz.aimaid.client.entity.state;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimationFrame;

public class AiMaidEntityRenderState extends HumanoidRenderState {
    public MaidAnimationFrame animation = MaidAnimationFrame.EMPTY;
}
