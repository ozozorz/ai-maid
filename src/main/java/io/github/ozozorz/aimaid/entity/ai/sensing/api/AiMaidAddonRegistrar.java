package io.github.ozozorz.aimaid.entity.ai.sensing.api;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

public interface AiMaidAddonRegistrar {

    /** SensorType 须已进入 Vanilla Registry；重复项去重，初始化冻结后拒绝注册。 */
    void registerBrainSensor(SensorType<? extends Sensor<? super AiMaidEntity>> sensorType);

}
