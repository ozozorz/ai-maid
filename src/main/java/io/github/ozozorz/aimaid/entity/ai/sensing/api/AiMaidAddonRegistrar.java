package io.github.ozozorz.aimaid.entity.ai.sensing.api;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public interface AiMaidAddonRegistrar {

    void registerBrainSensor(SensorType<? extends Sensor<? super AiMaidEntity>> sensorType);

}
