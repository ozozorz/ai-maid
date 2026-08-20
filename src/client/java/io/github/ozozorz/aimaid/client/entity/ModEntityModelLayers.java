package io.github.ozozorz.aimaid.client.entity;

import io.github.ozozorz.aimaid.AIMaid;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

// 现在，我们需要在客户端包中创建一个 ModEntityModelLayers 类。 
// 这个实体只有一个纹理层，但其他实体可能会使用多个纹理层；可以想想 Player 这类实体的第二皮肤层，或 Spider 的眼睛。
// 随后，必须在模组的客户端初始化器中初始化这个类。
public class ModEntityModelLayers {

    public static final ModelLayerLocation MINI_GOLEM = createMain("mini_golem");

    private static ModelLayerLocation createMain(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name), "main");
    }

    public static void registerModelLayers() {
        ModelLayerRegistry.registerModelLayer(ModEntityModelLayers.MINI_GOLEM,
                MiniGolemEntityModel::getTexturedModelData);
    }

}
