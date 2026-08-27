package io.github.ozozorz.aimaid.test;

import java.util.Optional;

import com.mojang.datafixers.util.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class TestAddonMemoryModuleTypes {

    public static final MemoryModuleType<BlockPos> PATROL_TARGET = register("patrol_target");

    public static final MemoryModuleType<Unit> PATROL_PAUSE = register("patrol_pause");

    private static <T> MemoryModuleType<T> register(String name) {
        return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE,
                Identifier.fromNamespaceAndPath("testaddon", name), new MemoryModuleType<>(Optional.empty()));
    }

    public static void initialize() {

    }

}
