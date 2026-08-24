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

    public static BehaviorControl<AiMaidEntity> create(float speedModifier, double startDistance, double stopDistance) {
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

                        if (!isFollowing) {
                            // 现在没有处于追赶状态
                            if (distanceSqr > startDistanceSqr) {
                                followingOwnerMemoryAccessor.set(Unit.INSTANCE);
                                walkTargetMemoryAccessor.set(new WalkTarget(owner, speedModifier, (int) stopDistance));
                            }

                            // 注意：
                            // 距离不够远时，什么都不做。
                            // 不要 walkTarget.erase()
                        }

                        if (isFollowing) {
                            if (distanceSqr <= stopDistanceSqr) {
                                followingOwnerMemoryAccessor.erase();
                                walkTargetMemoryAccessor.erase();
                            } else {
                                walkTargetMemoryAccessor.set(new WalkTarget(owner, speedModifier, (int) stopDistance));
                            }
                        }

                        return true;
                    };
                }));
    }

}
