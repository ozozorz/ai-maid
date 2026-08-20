package io.github.ozozorz.aimaid.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// 准备你的第一个实体
// 创建自定义实体的第一步，是定义它的类，并将其注册到游戏中。
// 我们会为该实体创建 MiniGolemEntity 类，并首先为它设置属性。 属性决定了实体的多项内容，包括最大生命值、移动速度和生物引诱范围。
public class MiniGolemEntity extends PathfinderMob {

    public MiniGolemEntity(Level world) {
        this(ModEntityTypes.MINI_GOLEM, world);
    }

    public MiniGolemEntity(EntityType<? extends MiniGolemEntity> entityType, Level world) {
        super(entityType, world);
    }

    public static AttributeSupplier.Builder createCubeAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 5)
                .add(Attributes.TEMPT_RANGE, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    // 添加目标
    // 目标是用于处理实体目的或行为意图的系统，它会为实体提供一组明确的行为。 目标具有一定的优先级：优先级数值较低的目标，会优先于数值较高的目标执行。
    // 若要为实体添加目标，需要在实体类中创建一个 registerGoals 方法，用于定义该实体的目标。
    @Override
    protected void registerGoals() {
        // TemptGoal - 使实体被手持特定物品的玩家吸引。
        // RandomStrollGoal - 使实体在世界中随机行走或游荡。
        // LookAtPlayerGoal - 尽管名称如此，但它可以接受任意实体。 这里用于注视 Cow 实体。
        // RandomLookAroundGoal - 使实体随机朝不同方向看。
        this.goalSelector.addGoal(0, new TemptGoal(this, 1, Ingredient.of(Items.WHEAT), false));
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Cow.class, 4));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // 为实体添加数据
    // 若要在实体上存储数据，通常的做法是在实体类中直接添加字段。

    // 有时，你需要将服务端实体中的数据同步到客户端实体。 关于客户端-服务端架构的更多信息，请参阅网络通信页面。 为此，我们可以通过定义
    // EntityDataAccessor 来使用 synched data [原文如此]。

    // 在本例中，我们希望实体每隔一段时间跳舞一次，因此需要创建一个会在客户端之间同步的跳舞状态，以便之后为其播放动画。
    // 不过，跳舞冷却时间不需要与客户端同步，因为动画由服务器触发。
    private static final EntityDataAccessor<Boolean> DANCING = SynchedEntityData.defineId(MiniGolemEntity.class,
            EntityDataSerializers.BOOLEAN);
    private int dancingTimeLeft;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DANCING, false);
    }

    public boolean isDancing() {
        return entityData.get(DANCING);
    }

    private void setDancing(boolean dancing) {
        entityData.set(DANCING, dancing);
    }

    // 如你所见，我们添加了一个 tick 方法来控制跳舞状态。
    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            if (this.isDancing()) {
                if (this.dancingTimeLeft-- <= 0) {
                    this.setDancing(false);
                }
            } else {
                if (this.random.nextInt(1000) == 0) {
                    this.setDancing(true);
                    this.dancingTimeLeft = 100 + this.random.nextInt(100);
                }
            }
        }
    }

    // 将数据存储到 NBT
    // 对于需要在游戏关闭后仍然保存的持久数据，我们会在 MiniGolemEntity 中重写 addAdditionalSaveData 和
    // readAdditionalSaveData 方法。 我们可以用它们来存储跳舞动画剩余的时间。
    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);
        valueOutput.putInt("dancing_time_left", this.dancingTimeLeft);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);
        this.dancingTimeLeft = valueInput.getInt("dancing_time_left").orElse(0);
        this.setDancing(this.dancingTimeLeft > 0);
    }

    // 添加动画
    // 为实体添加动画的第一步，是在实体类中添加动画状态。 我们会创建一个动画状态，用于让实体跳舞。
    public final AnimationState dancingAnimationState = new AnimationState();

    // 我们重写了 onSyncedDataUpdated 方法。 每当同步数据在服务器或客户端更新时，该方法都会被调用。 这里的 if
    // 语句会检查被更新的同步数据是否为跳舞同步数据。
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);

        if (data == DANCING) {
            this.dancingAnimationState.animateWhen(this.isDancing(), this.tickCount);
        }
    }

}
