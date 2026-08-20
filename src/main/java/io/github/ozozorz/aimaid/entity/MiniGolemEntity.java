package io.github.ozozorz.aimaid.entity;

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

}
