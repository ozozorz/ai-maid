package io.github.ozozorz.aimaid.entity.ai.behavior;

import com.mojang.datafixers.util.Unit;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class FollowOwner {

    private FollowOwner() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier, double startDistance, double stopDistance,
            int walkCloseEnoughDistance) {
        return BehaviorBuilder.create(instance -> instance.group(
                instance.present(ModMemoryModuleTypes.OWNER),
                instance.registered(MemoryModuleType.WALK_TARGET),
                instance.registered(ModMemoryModuleTypes.FOLLOWING_OWNER))
                .apply(instance, (ownerMemoryAccessor, walkTargetMemoryAccessor, followingOwnerMemoryAccessor) -> {
                    return (level, maid, timestamp) -> {

                        LivingEntity owner = instance.get(ownerMemoryAccessor);

                        if (!owner.isAlive() || owner.level() != level) {

                            followingOwnerMemoryAccessor.erase();
                            walkTargetMemoryAccessor.erase();

                            return false;
                        }

                        double distanceSqr = maid.distanceToSqr(owner);

                        double startDistanceSqr = startDistance * startDistance;

                        double stopDistanceSqr = stopDistance * stopDistance;

                        boolean isFollowing = maid.getBrain().hasMemoryValue(ModMemoryModuleTypes.FOLLOWING_OWNER);

                        // =========================
                        // 已经处于追赶状态
                        // =========================
                        if (isFollowing) {
                            if (distanceSqr <= stopDistanceSqr) {
                                followingOwnerMemoryAccessor.erase();
                                walkTargetMemoryAccessor.erase();
                                return true;
                            }
                            walkTargetMemoryAccessor.set(new WalkTarget(owner, speedModifier, walkCloseEnoughDistance));
                            return true;
                        }

                        // =========================
                        // 当前没有追赶
                        // =========================
                        if (distanceSqr > startDistanceSqr) {
                            followingOwnerMemoryAccessor.set(Unit.INSTANCE);
                            walkTargetMemoryAccessor.set(new WalkTarget(owner, speedModifier, walkCloseEnoughDistance));
                        }
                        // 主人在 startDistance 以内：
                        // 什么都不要做。
                        // 尤其不要 set WALK_TARGET，也不要 erase WALK_TARGET。

                        return true;
                    };
                }));
    }

}
