package io.github.ozozorz.aimaid.entity;

import io.github.ozozorz.aimaid.AIMaid;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

// 若要注册你的实体，建议创建一个单独的 ModEntityTypes 类，用于注册所有实体类型、设置它们的尺寸，并注册它们的属性。
public class ModEntityTypes {

    // 注册女仆的实体类型
    public static final EntityType<AiMaidEntity> AI_MAID = register("ai_maid",
            EntityType.Builder.<AiMaidEntity>of(AiMaidEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f));

    public static final EntityType<MiniGolemEntity> MINI_GOLEM = register("mini_golem",
            EntityType.Builder.<MiniGolemEntity>of(MiniGolemEntity::new, MobCategory.MISC).sized(0.75f, 1.75f));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void registerModEntityTypes() {
        AIMaid.LOGGER.info("Registering EntityTypes for " + AIMaid.MOD_ID);
    }

    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(AI_MAID, AiMaidEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MINI_GOLEM, MiniGolemEntity.createCubeAttributes());
    }

}
