package io.github.ozozorz.aimaid.integration.ai;

import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class FixtureSensors {
    public static final SensorType<FixturePatrolSensor> PATROL_CONFIG = 
        register("patrol_config", FixturePatrolSensor::new);



    private FixtureSensors() {
    }

    private static <T extends Sensor<?>> SensorType<T> register(String name, Supplier<T> factory) {
        return Registry.register(BuiltInRegistries.SENSOR_TYPE, Identifier.fromNamespaceAndPath("ai-maid-integration-tests", name), new SensorType<>(factory));
    }

    public static void initialize() {
    }
}
