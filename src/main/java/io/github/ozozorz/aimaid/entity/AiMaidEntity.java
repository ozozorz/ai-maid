package io.github.ozozorz.aimaid.entity;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import io.github.ozozorz.aimaid.entity.ai.sensing.ModSensorTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

// 女仆实体类
public class AiMaidEntity extends TamableAnimal {

    public AiMaidEntity(Level level) {
        this(ModEntityTypes.AI_MAID, level);
    }

    public AiMaidEntity(EntityType<? extends AiMaidEntity> entityType, Level level) {
        super(entityType, level);
    }

    // 定义女仆实例化出来有哪些默认属性：最大生命值20，移动速度3
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
    private static final Brain.Provider<AiMaidEntity> BRAIN_PROVIDER = Brain.provider(
            ImmutableList.of(
                    SensorType.NEAREST_LIVING_ENTITIES,
                    SensorType.NEAREST_PLAYERS,
                    ModSensorTypes.OWNER),
            AiMaidAi::getActivities);

    // rain.Packed = 从存档读取出来、准备恢复进 Brain 的记忆包。
    @Override
    protected Brain<AiMaidEntity> makeBrain(Brain.Packed packedBrain) {
        return BRAIN_PROVIDER.makeBrain(this, packedBrain);
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
                    "following = "
                            + brain.hasMemoryValue(
                                    ModMemoryModuleTypes.FOLLOWING_OWNER));

            System.out.println(
                    "walk = "
                            + brain.getMemory(
                                    MemoryModuleType.WALK_TARGET));

            System.out.println(
                    "look = "
                            + brain.getMemory(
                                    MemoryModuleType.LOOK_TARGET));
        }
        /// DUBUG END
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel arg0, AgeableMob arg1) {
        return null;
    }

    // 判断是不是可驯服物品
    private boolean isTamingItem(ItemStack stack) {
        return stack.getItem() == Items.COOKIE;
    }

    // 右键交互逻辑 - 驯服逻辑
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame() && this.isTamingItem(stack)) {
            if (!this.level().isClientSide()) {
                stack.consume(1, player);

                boolean success = this.getRandom().nextInt(3) == 0;

                if (success) {
                    this.tame(player);
                }

                ServerLevel serverLevel = (ServerLevel) this.level();

                serverLevel.sendParticles(success ? ParticleTypes.HEART : ParticleTypes.SMOKE, this.getX(),
                        this.getY() + this.getBbHeight() * 0.5, this.getZ(), 7, this.getBbWidth() * 0.5,
                        this.getBbHeight() * 0.5, this.getBbWidth() * 0.5, 0.02);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

}
