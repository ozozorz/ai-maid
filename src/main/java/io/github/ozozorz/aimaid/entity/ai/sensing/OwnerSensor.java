package io.github.ozozorz.aimaid.entity.ai.sensing;

import io.github.ozozorz.aimaid.entity.ai.behavior.FollowOwner;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class OwnerSensor extends Sensor<AiMaidEntity> {
    public OwnerSensor() { super(1); }

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        LivingEntity owner = maid.getOwner();
        LivingEntity previous = maid.getBrain().getMemory(ModMemoryModuleTypes.OWNER).orElse(null);
        if (FollowOwner.available(maid, owner)) {
            maid.getBrain().setMemory(ModMemoryModuleTypes.OWNER, owner);
        } else {
            maid.getBrain().eraseMemory(ModMemoryModuleTypes.OWNER);
        }
        LivingEntity current = maid.getBrain().getMemory(ModMemoryModuleTypes.OWNER).orElse(null);
        var brain = maid.getBrain();
        if (previous != current && (brain.isActive(ModActivities.FOLLOW_OWNER)
                || brain.isActive(ModActivities.WAIT_FOR_OWNER))) {
            brain.stopAll(level, maid);
            maid.getNavigation().stop();
            AiMaidAi.updateActivity(maid);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(ModMemoryModuleTypes.OWNER);
    }

}
