package io.github.ozozorz.aimaid.client.entity.renderer;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.client.entity.model.AiMaidEntityModel;
import io.github.ozozorz.aimaid.client.entity.state.AiMaidEntityRenderState;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

// AiMaidEntity
// extends PathfinderMob
//         ↓
// AiMaidEntityRenderer
// extends HumanoidMobRenderer
//         ↓
// AiMaidEntityRenderState
// extends HumanoidRenderState
//         ↓
// AiMaidEntityModel
// extends HumanoidModel<AiMaidRenderState>
//         ↓
// ModelLayers.PLAYER_SLIM
//         ↓
// Alex 细手臂模型 + 玩家第二层皮肤
//         ↓
// ai_maid.png

//AiMaidEntity=现实世界中的那个Mob, AiMaidEntityRenderState=这一帧需要传给渲染系统的数据, AiMaidEntityModel=如何把这些数据转换成模型姿势
public class AiMaidEntityRenderer
        extends HumanoidMobRenderer<AiMaidEntity, AiMaidEntityRenderState, AiMaidEntityModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AIMaid.MOD_ID,
            "textures/entity/ai_maid.png");

    public AiMaidEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new AiMaidEntityModel(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5f);
        // 渲染护甲
        this.addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, context.getModelSet(), AiMaidEntityModel::new),
                context.getEquipmentRenderer()));
    }

    @Override
    public AiMaidEntityRenderState createRenderState() {
        return new AiMaidEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(AiMaidEntityRenderState state) {
        return TEXTURE;
    }

}
