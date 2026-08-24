package io.github.ozozorz.aimaid.entity.ai.behavior;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class SetOwnerLookTarget {

    private SetOwnerLookTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create(double maxDistance) {
        return BehaviorBuilder.create(instance -> instance.group(
                instance.present(ModMemoryModuleTypes.OWNER),
                instance.absent(MemoryModuleType.LOOK_TARGET))
                .apply(instance, (ownerMemoryAccessor, lookTargetMemoryAccessor) -> {
                    return (level, maid, timestamp) -> {
                        LivingEntity owner = instance.get(ownerMemoryAccessor);
                        if (!owner.isAlive() || owner.level() != level) {
                            return false;
                        }
                        double maxDistanceSqr = maxDistance * maxDistance;
                        if (maid.distanceToSqr(owner) > maxDistanceSqr) {
                            return false;
                        }
                        if (!BehaviorUtils.canSee(maid, owner)) {
                            return false;
                        }
                        lookTargetMemoryAccessor.set(new EntityTracker(owner, true));
                        return true;
                    };
                }));
    }

}
