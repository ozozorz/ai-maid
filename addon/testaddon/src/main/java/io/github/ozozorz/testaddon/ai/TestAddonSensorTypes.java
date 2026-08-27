package io.github.ozozorz.testaddon.ai;

import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class TestAddonSensorTypes {
    public static final SensorType<TestPatrolConfigSensor> PATROL_CONFIG = register("patrol_config",
            TestPatrolConfigSensor::new);

    private TestAddonSensorTypes() {
    }

    private static <T extends Sensor<?>> SensorType<T> register(String name, Supplier<T> factory) {
        return Registry.register(BuiltInRegistries.SENSOR_TYPE, Identifier.fromNamespaceAndPath("testaddon", name),
                new SensorType<>(factory));
    }

    public static void initialize() {
    }
}
