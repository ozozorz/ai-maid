package io.github.ozozorz.aimaid.client.entity.renderer;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.client.entity.model.MiniGolemEntityModel;
import io.github.ozozorz.aimaid.client.entity.model.ModEntityModelLayers;
import io.github.ozozorz.aimaid.client.entity.state.MiniGolemEntityRenderState;
import io.github.ozozorz.aimaid.entity.MiniGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

// 创建渲染器
// 实体的渲染器使你能够在游戏中看到该实体。 
// 我们会创建一个新的 MiniGolemEntityRenderer 类，用于告诉 Minecraft 该实体应使用哪种纹理、模型和实体渲染状态。
// 这里也会设置阴影半径。对于该实体，阴影半径为 0.375f。
// 随后，必须在模组的客户端初始化器中注册这个渲染器。
public class MiniGolemEntityRenderer
        extends MobRenderer<MiniGolemEntity, MiniGolemEntityRenderState, MiniGolemEntityModel> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AIMaid.MOD_ID,
            "textures/entity/mini_golem.png");

    public MiniGolemEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new MiniGolemEntityModel(context.bakeLayer(ModEntityModelLayers.MINI_GOLEM)), 0.375f); // 0.375
                                                                                                              // shadow
                                                                                                              // radius
    }

    @Override
    public MiniGolemEntityRenderState createRenderState() {
        return new MiniGolemEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(MiniGolemEntityRenderState state) {
        return TEXTURE;
    }

    // 为了执行复制操作，我们在实体渲染器中重写 extractRenderState 方法。
    @Override
    public void extractRenderState(MiniGolemEntity entity, MiniGolemEntityRenderState state, float tickProgress) {
        super.extractRenderState(entity, state, tickProgress);
        state.dancingAnimationState.copyFrom(entity.dancingAnimationState);
    }

}
