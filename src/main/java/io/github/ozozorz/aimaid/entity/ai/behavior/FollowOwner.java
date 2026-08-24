package io.github.ozozorz.aimaid.entity.ai.behavior;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.mojang.datafixers.Products;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.IdF;
import com.mojang.datafixers.kinds.OptionalBox;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder.Mu;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class FollowOwner {

    private FollowOwner() {
    }

    public static BehaviorControl<AiMaidEntity> create(float speedModifier, double stopDistance) {
        Function<BehaviorBuilder.Instance<AiMaidEntity>, App<BehaviorBuilder.Mu<AiMaidEntity>, Trigger<AiMaidEntity>>> builderFunction = instance -> {
            BehaviorBuilder<AiMaidEntity, MemoryAccessor<IdF.Mu, LivingEntity>> ownerRequirement = instance
                    .present(ModMemoryModuleTypes.OWNER);
            BehaviorBuilder<AiMaidEntity, MemoryAccessor<OptionalBox.Mu, WalkTarget>> walkTargetRequirement = instance
                    .registered(MemoryModuleType.WALK_TARGET);
            Products.P2<BehaviorBuilder.Mu<AiMaidEntity>, MemoryAccessor<IdF.Mu, LivingEntity>, MemoryAccessor<OptionalBox.Mu, WalkTarget>> memoryGroup = instance
                    .group(ownerRequirement, walkTargetRequirement);

            BiFunction<MemoryAccessor<IdF.Mu, LivingEntity>, MemoryAccessor<OptionalBox.Mu, WalkTarget>, Trigger<AiMaidEntity>> triggerFactory = (
                    ownerMemoryAccessor, walkTargetMemoryAccessor) -> {
                Trigger<AiMaidEntity> trigger = (level, maid, timestamp) -> {
                    LivingEntity owner = instance.get(ownerMemoryAccessor);
                    if (!owner.isAlive() || owner.level() != level) {
                        walkTargetMemoryAccessor.erase();
                        return false;
                    }
                    double stopDistanceSqr = stopDistance * stopDistance;
                    if (maid.distanceToSqr(owner) <= stopDistanceSqr) {
                        walkTargetMemoryAccessor.erase();
                    } else {
                        WalkTarget newWalkTarget = new WalkTarget(owner, speedModifier, (int) stopDistance);
                        walkTargetMemoryAccessor.set(newWalkTarget);
                    }
                    return true;
                };
                return trigger;
            };

            App<Mu<AiMaidEntity>, Trigger<AiMaidEntity>> app = memoryGroup.apply(instance, triggerFactory);
            return app;
        };
        BehaviorControl<AiMaidEntity> oneShot = BehaviorBuilder.create(builderFunction);
        return oneShot;
    }

}
