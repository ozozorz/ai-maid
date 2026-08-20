package io.github.ozozorz.aimaid.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

// 设置模型
// MiniGolemEntityModel 类用于描述实体的形状和组成部分，从而定义实体的外观。 模型通常会在 Blockbench 等第三方工具中创建，而不是手写。 不过，本教程仍会通过一个手写示例来展示其工作方式。
// MiniGolemEntityModel 类定义了迷你傀儡实体的视觉模型。 它继承自 EntityModel，并指定实体各个身体部件（身体、头部、左腿和右腿）的名称。
public class MiniGolemEntityModel extends EntityModel<MiniGolemEntityRenderState> {
    private final ModelPart head;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public MiniGolemEntityModel(ModelPart root) {
        super(root);
        this.head = root.getChild(PartNames.HEAD);
        this.leftLeg = root.getChild(PartNames.LEFT_LEG);
        this.rightLeg = root.getChild(PartNames.RIGHT_LEG);
    }

    // 该方法通过将迷你傀儡的身体、头部和腿创建为长方体，设置它们的位置和纹理映射，并返回用于渲染的 LayerDefinition，从而定义迷你傀儡的 3D
    // 模型。
    // 每个部件都会以一个偏移点添加，该偏移点是应用到该部件的所有变换的原点。 模型部件中的其他所有坐标，都是相对于这个偏移点进行测量的。
    // WARNING
    // 模型中更大的 Y 值对应实体的底部。 这与游戏内坐标相反。
    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(
                PartNames.BODY,
                CubeListBuilder.create().addBox(
                        /* x: */ -6,
                        /* y: */ -6,
                        /* z: */ -6,
                        /* width: */ 12,
                        /* height: */ 12,
                        /* depth: */ 12),
                PartPose.offset(0, 8, 0));
        root.addOrReplaceChild(
                PartNames.HEAD,
                CubeListBuilder.create().texOffs(36, 0).addBox(-3, -6, -3, 6, 6, 6),
                PartPose.offset(0, 2, 0));
        root.addOrReplaceChild(
                PartNames.LEFT_LEG,
                CubeListBuilder.create().texOffs(48, 12).addBox(-2, 0, -2, 4, 10, 4),
                PartPose.offset(-2.5f, 14, 0));
        root.addOrReplaceChild(
                PartNames.RIGHT_LEG,
                CubeListBuilder.create().texOffs(48, 12).addBox(-2, 0, -2, 4, 10, 4),
                PartPose.offset(2.5f, 14, 0));
        return LayerDefinition.create(modelData, 64, 32);
    }

    @Override
    public void setupAnim(MiniGolemEntityRenderState state) {
        super.setupAnim(state);
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
        float limbSwingAmplitude = state.walkAnimationSpeed;
        float limbSwingAnimationProgress = state.walkAnimationPos;
        this.leftLeg.xRot = Mth.cos(limbSwingAnimationProgress * 0.2f + Mth.PI) * 1.4f * limbSwingAmplitude;
        this.rightLeg.xRot = Mth.cos(limbSwingAnimationProgress * 0.2f) * 1.4f * limbSwingAmplitude;
    }

}
