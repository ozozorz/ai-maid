package io.github.ozozorz.aimaid.entity.ai.sensing;

import java.util.function.Supplier;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class ModSensorTypes {

    public static final SensorType<AiMaidOwnerSensor> OWNER = register("owner", AiMaidOwnerSensor::new);

    private ModSensorTypes() {
    }

    private static <T extends Sensor<?>> SensorType<T> register(String name, Supplier<T> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SENSOR_TYPE, id, new SensorType<>(factory));
    }

    public static void initialize() {
    }

}
