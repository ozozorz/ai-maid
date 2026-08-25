package io.github.ozozorz.aimaid.entity.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.FollowOwner;
import io.github.ozozorz.aimaid.entity.ai.behavior.RandomStrollAroundOwner;
import io.github.ozozorz.aimaid.entity.ai.behavior.SetOwnerLookTarget;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomLookAround;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;

public class AiMaidAi {

    private AiMaidAi() {
    }

    public static List<ActivityData<AiMaidEntity>> getActivities(AiMaidEntity maid) {
        LinkedHashMap<Activity, ActivityData<AiMaidEntity>> activities = new LinkedHashMap<>();
        addActivity(activities, initCoreActivity(), "built-in CORE");
        addActivity(activities, initIdleActivity(), "built-in IDLE");
        for (MaidCommand maidCommand : ModBuiltInRegistries.MAID_COMMAND) {
            Identifier maidCommandId = ModBuiltInRegistries.MAID_COMMAND.getKey(maidCommand);
            for (ActivityData<AiMaidEntity> activityData : maidCommand.createActivities(maid)) {
                addActivity(activities, activityData, String.valueOf(maidCommandId));
            }
        }
        return List.copyOf(activities.values());
    }

    public static void addActivity(Map<Activity, ActivityData<AiMaidEntity>> activities,
            ActivityData<AiMaidEntity> activityData, String source) {
        Activity activity = activityData.activityType();
        ActivityData<AiMaidEntity> existing = activities.putIfAbsent(activity, activityData);
        if (existing != null) {
            throw new IllegalStateException("Duplicate AiMaid activity " + activity + " contributed by " + source);
        }
    }

    /// MaidCommand 决定“应该考虑哪些模式”，ActivityData 决定“这个模式此刻是否具备运行条件”。
    public static void updateActivity(AiMaidEntity maid) {
        Brain<AiMaidEntity> brain = maid.getBrain();
        // 新 Brain 创建后的最初时刻，CommandSensor 可能还没有把 Memory 填好, Memory 暂时没值, 直接读取实体持久命令
        MaidCommand maidCommand = brain.getMemory(ModMemoryModuleTypes.MAID_COMMAND).orElseGet(maid::getMaidCommand);
        List<Activity> candidates = maidCommand.getActivityCandidates(maid);
        if (candidates.isEmpty()) {
            candidates = List.of(Activity.IDLE);
        }
        brain.setActiveActivityToFirstValid(candidates);
    }

    private static ActivityData<AiMaidEntity> initCoreActivity() {
        return ActivityData.create(Activity.CORE, 0, ImmutableList.<BehaviorControl<? super AiMaidEntity>>of(
                new Swim<AiMaidEntity>(0.8F), new LookAtTargetSink(45, 90), new MoveToTargetSink()));
    }

    private static ActivityData<AiMaidEntity> initIdleActivity() {
        return ActivityData.create(Activity.IDLE, 10, ImmutableList.of(createIdleBehaviors()));
    }

    private static RunOne<AiMaidEntity> createIdleBehaviors() {
        return new RunOne<>(ImmutableList.of(Pair.of(SetEntityLookTarget.create(EntityTypes.PLAYER, 8.0F), 2),
                Pair.of(RandomStroll.stroll(1.0F), 2), Pair.of(new DoNothing(30, 60), 1)));
    }

    public static ActivityData<AiMaidEntity> createFollowOwnerActivity() {
        return ActivityData.create(
                ModActivities.FOLLOW_OWNER,
                ActivityData.createPriorityPairs(10, ImmutableList.of(
                        FollowOwner.create(1.0F, 6.0, 3.0),
                        createFollowAmbientBehaviors())),
                Set.of(Pair.of(ModMemoryModuleTypes.OWNER, MemoryStatus.VALUE_PRESENT)),
                Set.of(MemoryModuleType.WALK_TARGET, ModMemoryModuleTypes.FOLLOWING_OWNER));
    }

    private static RunOne<AiMaidEntity> createFollowAmbientBehaviors() {
        return new RunOne<>(
                Map.of(
                        ModMemoryModuleTypes.FOLLOWING_OWNER,
                        MemoryStatus.VALUE_ABSENT),
                ImmutableList.of(
                        Pair.of(SetOwnerLookTarget.create(8.0F), 4),
                        Pair.of(new RandomLookAround(UniformInt.of(40, 80), 45.0F, -15.0F, 20.0F), 3),
                        Pair.of(RandomStrollAroundOwner.create(0.6F, 2.0, 5.0), 1),
                        Pair.of(new DoNothing(30, 60), 4)));
    }

}

// 可以把 Activity 理解成：
// “我目前处在哪一种 AI 工作模式？”

// 以后可能有：
// CORE
// IDLE
// FOLLOW_OWNER
// STAY
// WORK
// FIGHT
// SLEEP

// 但是 CORE 比较特殊。
// 它更像：
// 不管你现在处于什么模式，都需要存在的基础执行层。
// 例如以后 AiMaid 在：
// IDLE
// 需要走路。
// 在：
// FOLLOW_OWNER
// 也需要走路。
// 在：
// WORK
// 还是需要走路。
// 那么：
// MoveToTargetSink
// 就不应该分别复制到：
// IDLE
// FOLLOW_OWNER
// WORK
// 三个 Activity。
// 而应该放：
// CORE
// 里。
// 所以我们现在的设计是：
// CORE:
// LookAtTargetSink
// MoveToTargetSink
// Swim
// ↑
// │ 消费意图
// │
// IDLE
// 看玩家 / 随机走 / 发呆
// 这就是 Brain 非常漂亮的地方。

// ActivityData 到底是什么？
// 这一行：
// ActivityData.create(
// Activity.CORE,
// 0,
// ...
// )
// 不要把 ActivityData 理解成：
// “正在运行的 Activity 对象”。
// 它更像：
// 这个 Activity 的配置说明书。
// 26.2 当前的 ActivityData 里面包含 Activity 类型、Behavior 与优先级的配对、启动条件、退出时需要清除的 Memory
// 等；它也提供我们现在使用的便捷 create(Activity, priority, behaviors) 方法。

// 因此：
// ActivityData.create(
// Activity.CORE,
// 0,
// behaviors
// )
// 是在描述：
// Activity：
// CORE
// 第一组 Behavior priority：
// 0
// 里面有哪些 Behavior：
// Swim
// LookAtTargetSink
// MoveToTargetSin