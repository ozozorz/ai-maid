package io.github.ozozorz.testaddon.ai;

import java.util.Optional;

import com.mojang.datafixers.util.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class TestAddonMemoryModuleTypes {

    public static final MemoryModuleType<BlockPos> PATROL_TARGET = register("patrol_target");

    public static final MemoryModuleType<Unit> PATROL_PAUSE = register("patrol_pause");

    public static final MemoryModuleType<GlobalPos> PATROL_CENTER = register("patrol_center");

    public static final MemoryModuleType<Integer> PATROL_RADIUS = register("patrol_radius");

    public static final MemoryModuleType<BlockPos> LUMBER_TARGET = register("lumber_target");
    
    // 纯 runtime maker: 这一轮 lumber 已经结束
    public static final MemoryModuleType<Unit> LUMBER_DONE = register("lumber_done");
    
    private static <T> MemoryModuleType<T> register(String name) {
        return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE,
                Identifier.fromNamespaceAndPath("testaddon", name), new MemoryModuleType<>(Optional.empty())
        );
    }

    public static void initialize() {

    }

}
