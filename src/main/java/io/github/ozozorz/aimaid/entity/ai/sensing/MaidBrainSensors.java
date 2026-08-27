package io.github.ozozorz.aimaid.entity.ai.sensing;

import java.util.LinkedHashSet;
import java.util.List;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.sensing.api.AiMaidAddonEntrypoint;
import io.github.ozozorz.aimaid.entity.ai.sensing.api.AiMaidAddonRegistrar;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public class MaidBrainSensors {

    private static final LinkedHashSet<SensorType<? extends Sensor<? super AiMaidEntity>>> SENSOR_TYPES = new LinkedHashSet<>();

    private static boolean initialized = false;
    private static boolean frozen = false;

    private MaidBrainSensors() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        // 核心 Maid 自己需要的 Sensor
        registerInternal(SensorType.NEAREST_LIVING_ENTITIES, AIMaid.MOD_ID);
        registerInternal(SensorType.NEAREST_PLAYERS, AIMaid.MOD_ID);
        registerInternal(ModSensorTypes.OWNER, AIMaid.MOD_ID);
        registerInternal(ModSensorTypes.MAID_COMMAND, AIMaid.MOD_ID);
        loadAddonEntrypoints();
        frozen = true;
    }

    public static List<SensorType<? extends Sensor<? super AiMaidEntity>>> getSensorTypes() {
        if (!initialized || !frozen) {
            throw new IllegalStateException("MaidBrainSensors has not finished initialization");
        }
        return List.copyOf(SENSOR_TYPES);
    }

    private static void loadAddonEntrypoints() {
        for (EntrypointContainer<AiMaidAddonEntrypoint> container : FabricLoader.getInstance()
                .getEntrypointContainers("ai-maid-addon", AiMaidAddonEntrypoint.class)) {
            String modId = container.getProvider().getMetadata().getId();
            AiMaidAddonRegistrar registrar = sensortype -> registerInternal(sensortype, modId);
            container.getEntrypoint().register(registrar);
        }
    }

    private static void registerInternal(SensorType<? extends Sensor<? super AiMaidEntity>> sensorType,
            String sourceModId) {
        if (frozen) {
            throw new IllegalStateException(
                    "Cannot register Maid Brain Sensor after freeze: " + sensorType);
        }

        Identifier id = BuiltInRegistries.SENSOR_TYPE.getKey(sensorType);

        if (id == null) {
            throw new IllegalArgumentException(
                    "Mod " + sourceModId + " tried to contribute an unregistered SeneorType: " + sensorType);
        }

        SENSOR_TYPES.add(sensorType);
    }

}
