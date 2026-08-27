package io.github.ozozorz.testaddon.ai;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import io.github.ozozorz.testaddon.data.TestAddonMaidData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class TestPatrolConfigSensor extends Sensor<AiMaidEntity> {

    public TestPatrolConfigSensor() {
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
        if (maid.getMaidCommand() != TestAddonCommands.PATROL) {
            brain.eraseMemory(TestAddonMemoryModuleTypes.PATROL_CENTER);
            brain.eraseMemory(TestAddonMemoryModuleTypes.PATROL_RADIUS);
            return;
        }
        GlobalPos center = TestAddonMaidData.getPatrolCenter(maid);
        if (center == null || !center.dimension().equals(level.dimension())) {
            brain.eraseMemory(TestAddonMemoryModuleTypes.PATROL_CENTER);
            brain.eraseMemory(TestAddonMemoryModuleTypes.PATROL_RADIUS);
            return;
        }
        brain.setMemory(TestAddonMemoryModuleTypes.PATROL_CENTER, center);
        brain.setMemory(TestAddonMemoryModuleTypes.PATROL_RADIUS, TestAddonMaidData.getPatrolRadius(maid));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(TestAddonMemoryModuleTypes.PATROL_CENTER, TestAddonMemoryModuleTypes.PATROL_RADIUS);
    }

}
