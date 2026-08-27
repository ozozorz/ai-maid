package io.github.ozozorz.aimaid.test;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DebugRandomLookAround extends RandomLookAround {
    public DebugRandomLookAround(
            IntProvider interval,
            float maxYaw,
            float minPitch,
            float maxPitch) {
        super(
                interval,
                maxYaw,
                minPitch,
                maxPitch);
    }

    @Override
    protected void start(
            ServerLevel level,
            Mob body,
            long timestamp) {
        Brain<?> brain = body.getBrain();

        System.out.println(
                "===== RandomLookAround START =====");

        System.out.println(
                "before LOOK_TARGET = "
                        + brain.getMemory(
                                MemoryModuleType.LOOK_TARGET));

        System.out.println(
                "before lookControl = "
                        + body.getLookControl().getWantedX()
                        + ", "
                        + body.getLookControl().getWantedY()
                        + ", "
                        + body.getLookControl().getWantedZ());

        super.start(
                level,
                body,
                timestamp);

        System.out.println(
                "after LOOK_TARGET = "
                        + brain.getMemory(
                                MemoryModuleType.LOOK_TARGET));

        System.out.println(
                "after lookControl = "
                        + body.getLookControl().getWantedX()
                        + ", "
                        + body.getLookControl().getWantedY()
                        + ", "
                        + body.getLookControl().getWantedZ());
    }
}
