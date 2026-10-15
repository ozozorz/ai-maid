package io.github.ozozorz.testaddon.command;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

public class TestGuardCommand implements MaidCommand {

    // 第一版的护卫范围，不是近战命中距离。
    private static final double MAX_DISTANCE_SQR = 16.0 * 16.0;

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(
            AiMaidEntity maid) {
        return List.of(createFightActivity(maid));
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        return List.of(
            TestAddonActivities.GUARD_FIGHT,
            ModActivities.FOLLOW_OWNER,
            Activity.IDLE
        );
    }

    @Override
    public int getMenuOrder() {
        return 600;
    }

    public static boolean isGuarding(AiMaidEntity maid) {
        return maid.isTame()
            && maid.getMaidCommand() == TestAddonCommands.GUARD;
    }

    // 获取目标时和战斗过程中，都使用同一套策略检查。
    public static boolean canDefendAgainst(
            AiMaidEntity maid, LivingEntity target) {
        if (!isGuarding(maid)
                || target == null
                || target == maid
                || !target.isAlive()
                || target.level() != maid.level()) {
            return false;
        }

        LivingEntity owner = maid.getOwner();

        if (owner == null
                || !owner.isAlive()
                || owner.level() != maid.level()) {
            return false;
        }

        // 第一版只做 PvE，并排除原版认定的友方。
        if (target instanceof Player
                || !maid.canAttack(target)
                || maid.isAlliedTo(target)) {
            return false;
        }

        // 同一主人的女仆、狼等驯服生物，也不能成为目标。
        if (target instanceof TamableAnimal animal
                && animal.isTame()
                && animal.isOwnedBy(owner)) {
            return false;
        }

        return maid.distanceToSqr(owner) <= MAX_DISTANCE_SQR
            && owner.distanceToSqr(target) <= MAX_DISTANCE_SQR
            && maid.distanceToSqr(target) <= MAX_DISTANCE_SQR;
    }

    private ActivityData<AiMaidEntity> createFightActivity(
            AiMaidEntity maid) {
        return ActivityData.create(
            TestAddonActivities.GUARD_FIGHT,
            ActivityData.createPriorityPairs(
                10,
                ImmutableList.<BehaviorControl<? super AiMaidEntity>>of(
                    // 先判断是否应该结束战斗。
                    StopAttackingIfTargetInvalid.<AiMaidEntity>create(
                        (level, target) ->
                            !canDefendAgainst(maid, target)
                    ),

                    // 发布追击意图，实际导航仍由 CORE 执行。
                    SetWalkTargetFromAttackTargetIfTargetOutOfReach
                        .create(1.0F),

                    // 原版负责挥手、近战攻击和冷却。
                    MeleeAttack.<AiMaidEntity>create(
                        TestGuardCommand::isGuarding,
                        20
                    )
                )
            ),
            Set.of(
                Pair.of(
                    MemoryModuleType.ATTACK_TARGET,
                    MemoryStatus.VALUE_PRESENT
                )
            ),
            Set.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
            )
        );
    }
}
