package io.github.ozozorz.testaddon;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimationFrame;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimationController;
import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations;
import io.github.ozozorz.aimaid.client.entity.model.AiMaidEntityModel;
import io.github.ozozorz.aimaid.client.entity.state.AiMaidEntityRenderState;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.SwingAnimationType;

/** 在真实客户端的已烘焙模型上扫过动画阶段，补充截图无法证明的姿势一致性。 */
final class AnimationPoseChecks {
    static void verify(Minecraft client) {
        // 原版实体 equals 使用网络 ID。新实例即使 ID 相同，也不能继承旧过渡。
        var firstInstance = new AiMaidEntity(client.level);
        var replacementInstance = new AiMaidEntity(client.level);
        // 测试对象不加入世界，模拟两个独立入站生成包分配了同一 ID。
        firstInstance.setId(123456789);
        replacementInstance.setId(firstInstance.getId());
        var usingBow = state();
        usingBow.isUsingItem = true;
        usingBow.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.BOW_AND_ARROW;
        check(!MaidAnimationController.extract(firstInstance, usingBow, 1).layers().isEmpty(), "Identity fixture must contain an active old action");
        check(firstInstance.equals(replacementInstance)
                && MaidAnimationController.extract(replacementInstance, state(), 1).layers().isEmpty(),
                "A new client entity with a reused network ID inherited an old action");

        var skin = new AiMaidEntityModel(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM));
        var armor = ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, client.getEntityModels(), AiMaidEntityModel::new);
        var idle = state();
        skin.setupAnim(idle);
        var baseline = capture(skin);
        idle.ageInTicks = 15;
        skin.setupAnim(idle);
        check(Math.abs(skin.root().y - baseline[1]) > 0.01F, "Idle breathing must move the model");

        var actions = List.of(MaidAnimations.MELEE, TestAddonAnimationIds.LUMBER, MaidAnimations.BOW,
                MaidAnimations.CROSSBOW_CHARGE, MaidAnimations.CROSSBOW_HOLD,
                MaidAnimationAction.BOW_RELEASE, MaidAnimationAction.CROSSBOW_FIRE);
        for (var id : actions) for (var arm : HumanoidArm.values()) for (int phase = 0; phase <= 20; phase++) {
            var sample = state();
            sample.walkAnimationPos = phase * 0.6F;
            sample.walkAnimationSpeed = 0.7F;
            sample.xRot = 20;
            sample.yRot = 25;
            sample.mainArm = arm;
            sample.animation = frame(id, arm, phase * 0.3F, 1);
            skin.setupAnim(sample);
            var expected = capture(skin);
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                var layer = armor.get(slot);
                layer.setupAnim(sample);
                sameTransforms(skin, layer);
            }
            var poseStack = new PoseStack();
            skin.translateToHand(sample, arm, poseStack);
            check(poseStack.last().pose().isFinite(), "Held item transform must remain finite");
            equal(expected, capture(skin), "Held item rendering must not mutate the shared model");
            skin.setupAnim(idle);
            skin.setupAnim(sample);
            equal(expected, capture(skin), "Rendering another maid must not leak pose into this one");
            sample.animation = new MaidAnimationFrame(0.4F, 0, 0, 0, 0, 0, MaidAnimationAction.NONE, List.of());
            skin.setupAnim(sample);
            var walking = capture(skin);
            // 当前所有上身动作都不能覆盖下身步态。
            for (int i = 5 * 9; i < expected.length; i++) near(expected[i], walking[i], "Action overwrote a walking leg");
        }

        var mirrored = state();
        mirrored.animation = frame(TestAddonAnimationIds.LUMBER, HumanoidArm.RIGHT, 2, 1);
        skin.setupAnim(mirrored);
        float x = skin.rightArm.xRot, y = skin.rightArm.yRot, z = skin.rightArm.zRot;
        mirrored.animation = frame(TestAddonAnimationIds.LUMBER, HumanoidArm.LEFT, 2, 1);
        skin.setupAnim(mirrored);
        near(skin.leftArm.xRot, x, "Left-handed action lost its pitch");
        near(skin.leftArm.yRot, -y, "Left-handed action did not mirror yaw");
        near(skin.leftArm.zRot, -z, "Left-handed action did not mirror roll");
        float beforeLook = skin.leftArm.yRot;
        mirrored.yRot = 25;
        skin.setupAnim(mirrored);
        check(skin.leftArm.yRot > beforeLook, "Left-handed aiming must follow the head, not turn away");

        var airborne = state();
        airborne.animation = new MaidAnimationFrame(0, 1, 1, 0, 0, 0, MaidAnimationAction.NONE, List.of());
        skin.setupAnim(airborne);
        float risingLeg = skin.rightLeg.xRot;
        airborne.animation = new MaidAnimationFrame(0, 1, 0, 0, 0, 0, MaidAnimationAction.NONE, List.of());
        skin.setupAnim(airborne);
        check(Math.abs(skin.rightLeg.xRot - risingLeg) > 0.2F, "Jump rise and fall must have different silhouettes");

        var dying = state();
        dying.deathTime = 10;
        dying.animation = frame(TestAddonAnimationIds.LUMBER, HumanoidArm.RIGHT, 2, 1);
        skin.setupAnim(dying);
        var death = capture(skin);
        dying.animation = MaidAnimationFrame.EMPTY;
        skin.setupAnim(dying);
        equal(death, capture(skin), "Death must override all unfinished actions");
    }

    private static AiMaidEntityRenderState state() {
        var state = new AiMaidEntityRenderState();
        state.mainArm = state.attackArm = HumanoidArm.RIGHT;
        state.swingAnimationType = SwingAnimationType.WHACK;
        return state;
    }

    private static MaidAnimationFrame frame(Identifier id, HumanoidArm arm, float time, float weight) {
        return new MaidAnimationFrame(0.4F, 0, 0, 0, 0, 0, id,
                List.of(new MaidAnimationFrame.Layer(id, weight, time, arm, time * 5, 25)));
    }

    private static ModelPart[] parts(AiMaidEntityModel model) {
        return new ModelPart[]{model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    static float[] capture(AiMaidEntityModel model) {
        List<Float> values = new ArrayList<>();
        for (var p : parts(model)) for (float f : new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale}) {
            check(Float.isFinite(f), "Animation produced a non-finite bone transform");
            values.add(f);
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private static void sameTransforms(AiMaidEntityModel skin, AiMaidEntityModel armor) {
        var a = parts(skin); var b = parts(armor);
        for (int i = 0; i < a.length; i++) {
            near(a[i].xRot, b[i].xRot, "Armor pitch does not follow skin");
            near(a[i].yRot, b[i].yRot, "Armor yaw does not follow skin");
            near(a[i].zRot, b[i].zRot, "Armor roll does not follow skin");
            near(a[i].x - a[i].getInitialPose().x(), b[i].x - b[i].getInitialPose().x(), "Armor shoulder X drift");
            near(a[i].y - a[i].getInitialPose().y(), b[i].y - b[i].getInitialPose().y(), "Armor Y drift");
            near(a[i].z - a[i].getInitialPose().z(), b[i].z - b[i].getInitialPose().z(), "Armor shoulder Z drift");
        }
    }

    static void equal(float[] expected, float[] actual, String message) {
        for (int i = 0; i < expected.length; i++) near(expected[i], actual[i], message + " at " + i);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < 0.0001F, message + ": " + expected + " / " + actual);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
