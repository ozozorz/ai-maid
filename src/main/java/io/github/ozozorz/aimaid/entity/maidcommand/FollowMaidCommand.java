package io.github.ozozorz.aimaid.entity.maidcommand;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.ai.behavior.FollowOwner;
import io.github.ozozorz.aimaid.entity.combat.CombatStatus;

import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.combat.MaidCombatMode;

import io.github.ozozorz.aimaid.entity.combat.WorkDefense;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

/** 共享策略对象不保存单只女仆的战斗状态。 */
public class FollowMaidCommand implements MaidCommand {
    @Override public MaidCombatMode getCombatMode() { return MaidCombatMode.GUARD; }

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of(AiMaidAi.createFollowOwnerActivity());
    }

    @Override
    public List<Activity> getActivityCandidates(AiMaidEntity maid) {
        if (!maid.isTame()) return List.of(Activity.IDLE);
        if (maid.getOwner() == null || maid.distanceToSqr(maid.getOwner()) > 64.0) {
            return List.of(ModActivities.FIGHT, ModActivities.FOLLOW_OWNER, ModActivities.WAIT_FOR_OWNER, Activity.IDLE);
        }
        return List.of(ModActivities.FIGHT, ModActivities.PICK_UP_ITEM,
                ModActivities.FOLLOW_OWNER, ModActivities.WAIT_FOR_OWNER, Activity.IDLE);
    }

    @Override public Component getStatus(AiMaidEntity maid) {
        if (maid.getTarget() != null) return CombatStatus.describe(maid);
        return FollowOwner.status(maid);
    }

    @Override
    public int getMenuOrder() { return 200; }

    @Override
    public boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack stack) {
        // 战斗中不为掉落分心；离主人过远时优先归队。
        return !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && maid.getOwner() != null && maid.distanceToSqr(maid.getOwner()) <= 64.0;
    }

    @Override
    public void onSelected(AiMaidEntity maid) {
        // 重选命令也能主动结束当前目标，但不能跳过攻击冷却。
        WorkDefense.clearCombat(maid);
    }

    @Override
    public void onDeselected(AiMaidEntity maid) {
        WorkDefense.clearCombat(maid);
    }
}
