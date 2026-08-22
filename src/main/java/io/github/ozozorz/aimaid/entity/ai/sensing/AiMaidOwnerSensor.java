package io.github.ozozorz.aimaid.entity.ai.sensing;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class AiMaidOwnerSensor extends Sensor<AiMaidEntity> {

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        LivingEntity owner = maid.getOwner();

        if (owner != null && owner.isAlive() && owner.level() == level) {
            maid.getBrain().setMemory(ModMemoryModuleTypes.OWNER, owner);
        } else {
            maid.getBrain().eraseMemory(ModMemoryModuleTypes.OWNER);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(ModMemoryModuleTypes.OWNER);
    }

}
