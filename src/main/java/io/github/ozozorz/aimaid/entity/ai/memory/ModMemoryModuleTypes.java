package io.github.ozozorz.aimaid.entity.ai.memory;

import java.util.Optional;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class ModMemoryModuleTypes {

    public static final MemoryModuleType<LivingEntity> OWNER = register("owner");

    private ModMemoryModuleTypes() {
    }

    private static <T> MemoryModuleType<T> register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name);
        return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE, id, new MemoryModuleType<>(Optional.empty()));
    }

    public static void initialize() {

    }

}
