package io.github.ozozorz.aimaid.client.entity.animation;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartNames;

// 现在，我们继续处理动画本身。 我们会创建 MiniGolemAnimations 类，并添加一个 AnimationDefinition，用于定义动画应如何应用到实体上。

// 这里包含了不少内容，请注意以下关键点：

// withLength(1) 会让动画持续 1 秒。
// looping() 会让动画反复循环播放。
// 随后是一系列 addAnimation 调用，它们会添加针对各个模型部件的独立动画。 这里，我们为头部、左腿和右腿分别设置了不同的动画。
// 每个动画都会作用于该模型部件的特定属性。在本例中，我们每次修改的都是模型部件的旋转。
// 一个动画由一组关键帧组成。 当动画时间（经过的秒数）等于某个关键帧的时间时，我们所作用的属性值就会等于该关键帧中指定的值（在本例中即旋转值）。
// 当动画时间位于两个关键帧之间时，该属性值会在相邻两个关键帧之间进行插值（混合）。
// 我们使用了线性插值，这是最简单的插值方式，会使属性值（在本例中是模型部件的旋转）以恒定速率从一个关键帧变化到下一个关键帧。 原版还提供了 Catmull-Rom 样条插值，可在关键帧之间产生更平滑的过渡。
// 模组开发者也可以创建自定义插值类型。
// 为了使渲染器能够访问我们的动画状态，我们将它的副本存储在 MiniGolemEntityRenderState 中。
public class MiniGolemAnimations {
    public static final AnimationDefinition DANCING = AnimationDefinition.Builder.withLength(1)
            .looping()
            .addAnimation(PartNames.HEAD, new AnimationChannel(
                    AnimationChannel.Targets.ROTATION,
                    new Keyframe(0, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.2f, KeyframeAnimations.degreeVec(0, 0, 45), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.6f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.8f, KeyframeAnimations.degreeVec(0, 0, -45), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation(PartNames.LEFT_LEG, new AnimationChannel(
                    AnimationChannel.Targets.ROTATION,
                    new Keyframe(0, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.2f, KeyframeAnimations.degreeVec(0, 0, 45), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.4f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR)))
            .addAnimation(PartNames.RIGHT_LEG, new AnimationChannel(
                    AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.5f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.7f, KeyframeAnimations.degreeVec(0, 0, -45), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.9f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR)))
            .build();
}
