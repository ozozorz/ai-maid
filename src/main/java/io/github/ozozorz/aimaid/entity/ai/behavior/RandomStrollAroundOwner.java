package io.github.ozozorz.aimaid.entity.ai.behavior;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class RandomStrollAroundOwner {

    private RandomStrollAroundOwner() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier, double minRadius, double maxRadius) {
        return BehaviorBuilder.create(instance -> instance.group(
                instance.present(ModMemoryModuleTypes.OWNER),
                instance.absent(MemoryModuleType.WALK_TARGET))
                .apply(instance, (ownerMemoryAccessor, walkTargetMemoryAccessor) -> {
                    return (level, maid, timestamp) -> {
                        LivingEntity owner = instance.get(ownerMemoryAccessor);
                        if (!owner.isAlive() || owner.level() != level) {
                            return false;
                        }
                        Vec3 target = findPositionAroundOwner(maid, owner, minRadius, maxRadius);
                        if (target == null) {
                            return false;
                        }
                        walkTargetMemoryAccessor.set(new WalkTarget(target, speedModifier, 1));
                        return true;
                    };
                }));
    }

    private static Vec3 findPositionAroundOwner(AiMaidEntity maid, LivingEntity owner, double minRadius,
            double maxRadius) {
        double minRadiusSqr = minRadius * minRadius;
        double maxRadiusSqr = maxRadius * maxRadius;
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = minRadius + maid.getRandom().nextDouble() * (maxRadius - minRadius);
            double targetX = owner.getX() + Math.cos(angle) * radius;
            double targetZ = owner.getZ() + Math.sin(angle) * radius;
            Vec3 desiredPosition = new Vec3(targetX, owner.getY(), targetZ);
            Vec3 candidate = LandRandomPos.getPosTowards(maid, (int) Math.ceil(maxRadius + 2.0), 2, desiredPosition);
            if (candidate == null) {
                continue;
            }
            double ownerDistanceSqr = candidate.distanceToSqr(owner.position());
            if (ownerDistanceSqr < minRadiusSqr || ownerDistanceSqr > maxRadiusSqr) {
                continue;
            }

            // 防止选到几乎就在Maid脚下的位置，
            // 否则看起来像根本没有走动。
            if (candidate.distanceToSqr(maid.position()) < 2.25) {
                continue;
            }
            return candidate;
        }
        return null;

    }

}
