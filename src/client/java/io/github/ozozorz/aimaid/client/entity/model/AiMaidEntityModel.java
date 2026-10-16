package io.github.ozozorz.aimaid.client.entity.model;

import io.github.ozozorz.aimaid.client.entity.state.AiMaidEntityRenderState;
import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimationFrame;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations;
import io.github.ozozorz.aimaid.client.entity.animation.MaidModelPose;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.SwingAnimationType;

public class AiMaidEntityModel extends HumanoidModel<AiMaidEntityRenderState> {
    // 缓存绑定到此模型骨骼的不可变关键帧；不保存任何女仆的播放进度。
    private final Map<Identifier, KeyframeAnimation> clips = new HashMap<>();

    public AiMaidEntityModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(AiMaidEntityRenderState state) {
        super.setupAnim(state);
        if (state.hasPose(Pose.SLEEPING) || state.isAutoSpinAttack) return;
        if (state.deathTime > 0) {
            death(state);
            return;
        }
        locomotion(state);
        if (!state.animation.layers().isEmpty()) {
            MaidModelPose base = MaidModelPose.capture(this);
            MaidModelPose result = base;
            for (MaidAnimationFrame.Layer layer : state.animation.layers()) {
                if (layer.weight() <= 0) continue;
                base.apply(this);
                MaidModelPose target = action(state, layer, base);
                result = result.addWeightedDifference(base, target, layer.weight());
            }
            result.apply(this);
        }
        // 受伤反馈可与持弓/工作叠加；死亡在上面独占处理。
        float hurt = state.animation.hurt();
        this.root.zRot += 0.07F * hurt;
        this.body.xRot -= 0.10F * hurt;
        this.head.xRot -= 0.06F * hurt;
        // 原版刺击已经移动过肩部，不能再绕躯干转第二次。
        if (state.attackTime <= 0 || state.swingAnimationType == SwingAnimationType.WHACK) connectShoulders();
    }

    @Override
    protected void setupAttackAnimation(AiMaidEntityRenderState state) {
        // WHACK 由本系统的动作层播放；26.2 的矛刺仍保留原版特殊姿势。
        if (state.swingAnimationType != SwingAnimationType.WHACK) super.setupAttackAnimation(state);
    }

    private void locomotion(AiMaidEntityRenderState state) {
        var frame = state.animation;
        float movement = Mth.clamp(state.walkAnimationSpeed, 0, 1);
        float gait = state.walkAnimationPos * 0.6662F;
        float standing = state.isPassenger || state.isFallFlying || state.swimAmount > 0 ? 0 : 1;
        float ground = standing * (1 - frame.airborne()) * (1 - frame.water());
        float breath = Mth.sin(state.ageInTicks * 0.09F + frame.idlePhase());
        float run = frame.run();
        if (standing > 0) {
            this.root.y += ground * (breath * 0.12F * (1 - movement) - Math.abs(Mth.sin(gait)) * movement * (0.25F + 0.3F * run));
            this.body.xRot += ground * movement * (0.025F + 0.10F * run);
            this.body.zRot += ground * Mth.sin(gait) * movement * 0.025F;
            this.rightLeg.xRot *= 0.88F + 0.20F * run;
            this.leftLeg.xRot *= 0.88F + 0.20F * run;
        }
        neutralArm(this.rightArm, state.rightArmPose, state.rightHandItemStack.isEmpty(), gait + Mth.PI, movement, breath, 1, state);
        neutralArm(this.leftArm, state.leftArmPose, state.leftHandItemStack.isEmpty(), gait, movement, breath, -1, state);
        if (standing > 0) {
            float air = frame.airborne();
            float rise = frame.rising();
            this.rightLeg.xRot = Mth.lerp(air, this.rightLeg.xRot, -0.20F - 0.40F * rise);
            this.leftLeg.xRot = Mth.lerp(air, this.leftLeg.xRot, 0.20F + 0.30F * rise);
            this.rightArm.zRot += air * 0.18F;
            this.leftArm.zRot -= air * 0.18F;
            this.body.xRot += air * (0.06F - 0.12F * rise);
            float water = frame.water();
            this.rightLeg.xRot = Mth.lerp(water, this.rightLeg.xRot, Mth.sin(state.ageInTicks * 0.22F) * 0.22F);
            this.leftLeg.xRot = Mth.lerp(water, this.leftLeg.xRot, -Mth.sin(state.ageInTicks * 0.22F) * 0.22F);
            this.rightArm.zRot += water * (0.25F + breath * 0.06F);
            this.leftArm.zRot -= water * (0.25F + breath * 0.06F);
        }
    }

    private void neutralArm(ModelPart arm, ArmPose pose, boolean empty, float gait, float movement, float breath, int side, AiMaidEntityRenderState state) {
        if (state.isPassenger || state.isFallFlying || state.swimAmount > 0) return;
        if (pose != ArmPose.EMPTY && pose != ArmPose.ITEM && pose != ArmPose.BOW_AND_ARROW
                && pose != ArmPose.CROSSBOW_CHARGE && pose != ArmPose.CROSSBOW_HOLD) return;
        arm.xRot = Mth.cos(gait) * movement * (0.75F + state.animation.run() * 0.25F);
        if (!empty) arm.xRot = arm.xRot * 0.5F - 0.31415927F;
        if (state.isCrouching) arm.xRot += 0.4F;
        arm.yRot = 0;
        arm.zRot = side * (0.055F + breath * 0.012F);
        arm.xRot += breath * 0.015F * side;
    }

    private MaidModelPose action(AiMaidEntityRenderState state, MaidAnimationFrame.Layer layer, MaidModelPose base) {
        Identifier id = layer.id();
        boolean right = layer.arm() == HumanoidArm.RIGHT;
        if (id.equals(MaidAnimations.BOW) || id.equals(MaidAnimationAction.BOW_RELEASE)) {
            this.rightArm.xRot = this.leftArm.xRot = -Mth.HALF_PI + this.head.xRot;
            this.rightArm.yRot = this.head.yRot + (right ? -0.1F : -0.5F);
            this.leftArm.yRot = this.head.yRot + (right ? 0.5F : 0.1F);
            this.rightArm.zRot = this.leftArm.zRot = 0;
            if (id.equals(MaidAnimationAction.BOW_RELEASE)) {
                float release = Mth.sin(Mth.clamp(layer.elapsedTicks() / 8, 0, 1) * Mth.PI);
                getArm(layer.arm()).xRot -= release * 0.15F;
                getArm(layer.arm()).yRot += (right ? -1 : 1) * release * 0.22F;
            }
        } else if (id.equals(MaidAnimations.CROSSBOW_CHARGE)) {
            AnimationUtils.animateCrossbowCharge(this.rightArm, this.leftArm, layer.chargeTicks(), layer.useTicks(), right);
            this.rightArm.zRot = this.leftArm.zRot = 0;
        } else if (id.equals(MaidAnimations.CROSSBOW_HOLD) || id.equals(MaidAnimationAction.CROSSBOW_FIRE)) {
            AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, right);
            this.rightArm.zRot = this.leftArm.zRot = 0;
            if (id.equals(MaidAnimationAction.CROSSBOW_FIRE)) {
                float recoil = Mth.sin(Mth.clamp(layer.elapsedTicks() / 6, 0, 1) * Mth.PI);
                this.rightArm.xRot -= recoil * 0.16F;
                this.leftArm.xRot -= recoil * 0.16F;
            }
        } else {
            var clip = MaidAnimations.get(id);
            if (clip == null) return base;
            for (String bone : clip.definition().boneAnimations().keySet()) {
                if (bone.endsWith("_arm") || bone.endsWith("_leg")) this.root.getChild(bone).setRotation(0, 0, 0);
            }
            clips.computeIfAbsent(id, ignored -> clip.definition().bake(this.root)).apply((long) (layer.elapsedTicks() * 50), 1);
            if (clip.aimAtLookTarget()) {
                this.rightArm.xRot += Mth.clamp(this.head.xRot, -1, 1) * 0.65F;
                float mirror = clip.mirrorMainHand() && !right ? -1 : 1;
                this.rightArm.yRot += Mth.clamp(this.head.yRot, -0.8F, 0.8F) * 0.4F * mirror;
            }
            MaidModelPose target = MaidModelPose.capture(this);
            return clip.mirrorMainHand() && !right ? target.mirrorClip(base, clip.definition().boneAnimations().keySet()) : target;
        }
        return MaidModelPose.capture(this);
    }

    private void connectShoulders() {
        // Humanoid 的手臂和躯干是同级部件，转身时显式让肩部绕躯干旋转。
        float angle = this.body.yRot;
        float rx = this.rightArm.x, rz = this.rightArm.z;
        float lx = this.leftArm.x, lz = this.leftArm.z;
        this.rightArm.x = rx * Mth.cos(angle) + rz * Mth.sin(angle);
        this.rightArm.z = rz * Mth.cos(angle) - rx * Mth.sin(angle);
        this.leftArm.x = lx * Mth.cos(angle) + lz * Mth.sin(angle);
        this.leftArm.z = lz * Mth.cos(angle) - lx * Mth.sin(angle);
    }

    private void death(AiMaidEntityRenderState state) {
        this.resetPose();
        float collapse = Mth.clamp(state.deathTime / 12, 0, 1);
        this.head.xRot = collapse * 0.22F;
        this.body.xRot = collapse * 0.10F;
        this.rightArm.xRot = this.leftArm.xRot = -collapse * 0.25F;
        this.rightArm.zRot = collapse * 0.14F;
        this.leftArm.zRot = -collapse * 0.14F;
        this.rightLeg.xRot = collapse * 0.10F;
        this.leftLeg.xRot = -collapse * 0.06F;
        // 整体倒下仍由 LivingEntityRenderer 的 deathTime 旋转完成。
    }

    @Override
    public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
        this.root.translateAndRotate(poseStack);
        // Alex 手臂比普通人形窄一像素，手持物对齐细手臂中心；不修改共享 ModelPart。
        poseStack.translate((arm == HumanoidArm.RIGHT ? 0.5F : -0.5F) / 16, 0, 0);
        this.getArm(arm).translateAndRotate(poseStack);
    }
}
