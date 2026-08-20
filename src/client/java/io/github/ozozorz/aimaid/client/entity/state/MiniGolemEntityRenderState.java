package io.github.ozozorz.aimaid.client.entity.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

// 创建渲染
// 渲染是指将方块、实体和环境等游戏数据转换为玩家屏幕上可见画面的过程。 这包括决定对象如何被照亮、着色和渲染贴图。

// INFO
// 实体渲染始终在客户端处理。 服务器负责管理实体的逻辑和行为，而客户端负责显示实体的模型、纹理和动画。

// 渲染包含多个步骤，并会涉及各自对应的类。我们先从 EntityRenderState 类开始。
public class MiniGolemEntityRenderState extends LivingEntityRenderState {

    // 为了使渲染器能够访问我们的动画状态，我们将它的副本存储在 MiniGolemEntityRenderState 中。
    public final AnimationState dancingAnimationState = new AnimationState();
}
