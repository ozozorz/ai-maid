package io.github.ozozorz.aimaid.entity.ai.sensing;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class MaidCommandSensor extends Sensor<AiMaidEntity> {

    public MaidCommandSensor() {
        super(1);
    }

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        maid.getBrain().setMemory(ModMemoryModuleTypes.MAID_COMMAND, maid.getMaidCommand());
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(ModMemoryModuleTypes.MAID_COMMAND);
    }

}
