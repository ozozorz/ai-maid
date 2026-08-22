package io.github.ozozorz.aimaid.entity;

import com.google.common.collect.ImmutableList;

import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.level.Level;

// 女仆实体类
public class AiMaidEntity extends PathfinderMob {

    public AiMaidEntity(Level level) {
        this(ModEntityTypes.AI_MAID, level);
    }

    public AiMaidEntity(EntityType<? extends AiMaidEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    // Brain.Provider 不是 Brain。
    // Brain.Provider = “大脑生产说明书”。
    // Brain<AiMaidEntity> = 某一只具体 AiMaid 自己的大脑
    // Provider 这两个参数分别是什么？
    // 第一个：Sensor 列表。告诉 Provider：AiMaid 有哪些感知器。
    // 第二个：AiMaidAi::getActivities 是方法引用。当前 26.2 的：Brain.ActivitySupplier<E>
    // 是一个函数式接口：List<ActivityData<E>> createActivities(E body);
    private static final Brain.Provider<AiMaidEntity> BARIN_PROVIDER = Brain.provider(
            ImmutableList.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS), AiMaidAi::getActivities);

    // rain.Packed = 从存档读取出来、准备恢复进 Brain 的记忆包。
    @Override
    protected Brain<AiMaidEntity> makeBrain(Brain.Packed packedBrain) {
        return BARIN_PROVIDER.makeBrain(this, packedBrain);
    }

    // 给 getBrain() 一个准确的泛型返回类型
    @Override
    @SuppressWarnings("unchecked")
    public Brain<AiMaidEntity> getBrain() {
        return (Brain<AiMaidEntity>) super.getBrain();
    }

    // 最重要的方法：customServerAiStep 让 Brain 真正“活起来”的地方。
    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);

        AiMaidAi.updateActivity(this);

        super.customServerAiStep(level);

        /// DEBUG
        if (this.tickCount % 40 == 0) {
            Brain<AiMaidEntity> brain = this.getBrain();

            System.out.println(
                    "ACTIVE = "
                            + brain.getActiveNonCoreActivity());

            System.out.println(
                    "NEAREST_PLAYERS = "
                            + brain.getMemory(
                                    MemoryModuleType.NEAREST_PLAYERS));

            System.out.println(
                    "LOOK_TARGET = "
                            + brain.getMemory(
                                    MemoryModuleType.LOOK_TARGET));

            System.out.println(
                    "WALK_TARGET = "
                            + brain.getMemory(
                                    MemoryModuleType.WALK_TARGET));
        }
        /// DUBUG END
    }

    // @Override
    // protected void registerGoals() {
    // // 掉进水里 → 尝试浮起来
    // // 没事干 → 到处走走
    // // 看到玩家 → 看玩家
    // // 其他时候 → 随机转头
    // this.goalSelector.addGoal(0, new FloatGoal(this));
    // this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0));
    // this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
    // this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    // }

}
