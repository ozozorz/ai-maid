package io.github.ozozorz.aimaid.integration.ai;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.integration.command.FixtureCommands;
import io.github.ozozorz.aimaid.integration.data.FixtureData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class FixturePatrolSensor extends Sensor<AiMaidEntity> {

    public FixturePatrolSensor() {
        super(1);
    }

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        Brain<AiMaidEntity> brain = maid.getBrain();

        /*
         * 当前不是 PATROL：
         * 不需要把 Patrol 配置暴露给 Brain。
         *
         * Persistent Attachment 仍然保留。
         */
        if (maid.getMaidCommand() != FixtureCommands.PATROL) {
            brain.eraseMemory(FixtureMemories.PATROL_CENTER);
            brain.eraseMemory(FixtureMemories.PATROL_RADIUS);
            return;
        }
        GlobalPos center = FixtureData.getPatrolCenter(maid);
        if (center == null || !center.dimension().equals(level.dimension())) {
            brain.eraseMemory(FixtureMemories.PATROL_CENTER);
            brain.eraseMemory(FixtureMemories.PATROL_RADIUS);
            return;
        }
        brain.setMemory(FixtureMemories.PATROL_CENTER, center);
        brain.setMemory(FixtureMemories.PATROL_RADIUS, FixtureData.getPatrolRadius(maid));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(FixtureMemories.PATROL_CENTER, FixtureMemories.PATROL_RADIUS);
    }

}
