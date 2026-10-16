package io.github.ozozorz.aimaid.client.entity.animation;

import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** 临时姿势值，不包含实体或播放历史。用于把多个动作相对同一基础姿势加权。 */
public final class MaidModelPose {
    private static final String[] NAMES = {"root", "head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private final float[][] values;
    private MaidModelPose(float[][] values) { this.values = values; }

    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    public static MaidModelPose capture(HumanoidModel<?> model) {
        var parts = parts(model);
        float[][] values = new float[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            values[i] = new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale};
        }
        return new MaidModelPose(values);
    }

    public void apply(HumanoidModel<?> model) {
        var parts = parts(model);
        for (int i = 0; i < parts.length; i++) {
            var p = parts[i]; var v = values[i];
            p.setPos(v[0], v[1], v[2]); p.setRotation(v[3], v[4], v[5]);
            p.xScale = v[6]; p.yScale = v[7]; p.zScale = v[8];
        }
    }

    public MaidModelPose addWeightedDifference(MaidModelPose base, MaidModelPose target, float weight) {
        float[][] result = copy();
        for (int i = 0; i < result.length; i++) for (int j = 0; j < result[i].length; j++) {
            float difference = target.values[i][j] - base.values[i][j];
            if (j >= 3 && j <= 5) difference = Mth.wrapDegrees(difference * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            result[i][j] += difference * weight;
        }
        return new MaidModelPose(result);
    }

    public MaidModelPose mirrorClip(MaidModelPose base, Set<String> animatedBones) {
        float[][] result = base.copy();
        for (int i = 1; i < NAMES.length; i++) {
            if (!animatedBones.contains(NAMES[i])) continue;
            int destination = switch (i) { case 3 -> 4; case 4 -> 3; case 5 -> 6; case 6 -> 5; default -> i; };
            for (int j = 0; j < values[i].length; j++) {
                float sign = j == 0 || j == 4 || j == 5 ? -1 : 1;
                // 四肢旋转是动作的绝对姿势；躯干/头的旋转是对当前姿势的增量。
                result[destination][j] = i >= 3 && j >= 3 && j <= 5 ? values[i][j] * sign
                        : base.values[destination][j] + (values[i][j] - base.values[i][j]) * sign;
            }
        }
        return new MaidModelPose(result);
    }

    private float[][] copy() {
        float[][] result = new float[values.length][];
        for (int i = 0; i < values.length; i++) result[i] = values[i].clone();
        return result;
    }
}
