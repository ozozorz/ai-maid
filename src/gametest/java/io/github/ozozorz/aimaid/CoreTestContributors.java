package io.github.ozozorz.aimaid;

import java.util.Optional;
import java.util.Set;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.sensing.api.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

/** 可选的两个独立入口夹具：注册顺序、重复 Sensor、自动 MemorySlot 与冻结。 */
public final class CoreTestContributors {
    public static final MemoryModuleType<Integer> FIRST_MEMORY = Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE,
            AIMaid.id("test_first_memory"), new MemoryModuleType<>(Optional.empty()));
    public static final MemoryModuleType<Integer> SECOND_MEMORY = Registry.register(BuiltInRegistries.MEMORY_MODULE_TYPE,
            AIMaid.id("test_second_memory"), new MemoryModuleType<>(Optional.empty()));
    public static final SensorType<CountingSensor> FIRST = Registry.register(BuiltInRegistries.SENSOR_TYPE,
            AIMaid.id("test_first_sensor"), new SensorType<>(() -> new CountingSensor(FIRST_MEMORY)));
    public static final SensorType<CountingSensor> SECOND = Registry.register(BuiltInRegistries.SENSOR_TYPE,
            AIMaid.id("test_second_sensor"), new SensorType<>(() -> new CountingSensor(SECOND_MEMORY)));
    public static AiMaidAddonRegistrar captured;
    public static class First implements AiMaidAddonEntrypoint {
        @Override public void register(AiMaidAddonRegistrar registrar) {
            if (!Boolean.getBoolean("ai-maid.testContributors")) return;
            captured = registrar; registrar.registerBrainSensor(FIRST); registrar.registerBrainSensor(FIRST);
        }
    }
    public static class Second implements AiMaidAddonEntrypoint {
        @Override public void register(AiMaidAddonRegistrar registrar) {
            if (!Boolean.getBoolean("ai-maid.testContributors")) return;
            registrar.registerBrainSensor(SECOND); registrar.registerBrainSensor(FIRST);
        }
    }
    public static final class CountingSensor extends Sensor<AiMaidEntity> {
        private final MemoryModuleType<Integer> memory;
        CountingSensor(MemoryModuleType<Integer> memory) { super(1); this.memory = memory; }
        @Override public Set<MemoryModuleType<?>> requires() { return Set.of(memory); }
        @Override protected void doTick(ServerLevel level, AiMaidEntity maid) {
            var brain = maid.getBrain(); brain.setMemory(memory, brain.getMemory(memory).orElse(0) + 1);
        }
    }
}
