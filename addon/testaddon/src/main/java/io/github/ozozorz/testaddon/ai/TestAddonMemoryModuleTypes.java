package io.github.ozozorz.testaddon.ai;

import net.minecraft.network.chat.Component;

import java.util.Optional;

import com.mojang.datafixers.util.Unit;
import io.github.ozozorz.testaddon.lumber.LumberJob;

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

    // Maid 工作时脚部所在的方块坐标
    public static final MemoryModuleType<BlockPos> LUMBER_WORK_POS = register("lumber_work_pos");
    
    // 纯 runtime marker: 这一轮 lumber 已经结束
    public static final MemoryModuleType<Unit> LUMBER_DONE = register("lumber_done");

    // false = 整树，true = 明确请求的一块原木；无值表示未请求/重载后不自动续工。
    public static final MemoryModuleType<Boolean> LUMBER_REQUEST = register("lumber_request");
    public static final MemoryModuleType<LumberJob> LUMBER_JOB = register("lumber_job");
    public static final MemoryModuleType<Component> LUMBER_RESULT = register("lumber_result");


    private static <T> MemoryModuleType<T> register(String name) {
        return Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE,
                Identifier.fromNamespaceAndPath("testaddon", name), new MemoryModuleType<>(Optional.empty())
        );
    }

    public static void initialize() {

    }

}
