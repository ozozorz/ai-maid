package io.github.ozozorz.testaddon;

import io.github.ozozorz.aimaid.entity.ai.sensing.api.AiMaidAddonEntrypoint;
import io.github.ozozorz.aimaid.entity.ai.sensing.api.AiMaidAddonRegistrar;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.ai.TestAddonSensorTypes;

public class TestAiMaidAddon implements AiMaidAddonEntrypoint {
    @Override
    public void register(AiMaidAddonRegistrar registrar) {
        // 不依赖 TestAddon.onInitialize()
        // 是否已经执行。
        TestAddonMemoryModuleTypes.initialize();
        TestAddonSensorTypes.initialize();

        registrar.registerBrainSensor(TestAddonSensorTypes.PATROL_CONFIG);
    }
}
