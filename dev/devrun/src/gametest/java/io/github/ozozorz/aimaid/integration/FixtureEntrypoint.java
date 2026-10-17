package io.github.ozozorz.aimaid.integration;
import net.fabricmc.api.ModInitializer;
import io.github.ozozorz.aimaid.entity.ai.sensing.api.*;
import io.github.ozozorz.aimaid.integration.ai.*;
import io.github.ozozorz.aimaid.integration.command.FixtureCommands;
public final class FixtureEntrypoint implements ModInitializer, AiMaidAddonEntrypoint {
    @Override public void register(AiMaidAddonRegistrar registrar) {
        FixtureMemories.initialize();
        registrar.registerBrainSensor(FixtureSensors.PATROL_CONFIG);
    }
    @Override public void onInitialize() {
        FixtureActivities.initialize();
        io.github.ozozorz.aimaid.integration.data.FixtureData.initialize();
        FixtureCommands.initialize();
    }
}
