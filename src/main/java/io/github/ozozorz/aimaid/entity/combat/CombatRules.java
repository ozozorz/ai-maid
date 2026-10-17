package io.github.ozozorz.aimaid.entity.combat;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Core 的 PvE 规则。所有选敌和出手入口共用这些判断。 */
public final class CombatRules {
    public static final double SEARCH_RANGE = 16.0;
    public static final double LEASH_RANGE = 24.0;
    public static final double WORK_DEFENSE_RANGE = 12.0;
    public static final double RANGED_RANGE = 12.0;
    public static final int LOST_SIGHT_TICKS = 100;
    public static final int NO_PROGRESS_TICKS = 160;
    public static final int RETRY_TICKS = 200;
    public static final TagKey<EntityType<?>> EXTRA_TARGETS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("ai-maid", "combat_targets"));
    public static final TagKey<EntityType<?>> IGNORED_TARGETS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("ai-maid", "combat_ignored"));

    private CombatRules() {}

    public static boolean isEnabled(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand().getCombatMode() != MaidCombatMode.NONE;
    }

    public static LivingEntity owner(AiMaidEntity maid) {
        LivingEntity owner = maid.getOwner();
        return owner != null && owner.isAlive() && owner.level() == maid.level() ? owner : null;
    }

    public static boolean isProtected(AiMaidEntity maid, LivingEntity target) {
        return target == maid || target instanceof Player || maid.isOwnedBy(target)
                || maid.isAlliedTo(target)
                || target instanceof TamableAnimal animal && animal.isTame();
    }

    public static boolean canTarget(AiMaidEntity maid, LivingEntity target) {
        return target != null && target.isAlive() && target.level() == maid.level()
                && !isProtected(maid, target) && maid.canAttack(target)
                && !target.is(IGNORED_TARGETS);
    }

    public static boolean isHostile(LivingEntity target) {
        // 中立生物只在反击/协助时考虑；数据包可以补充主动目标。
        return target.is(EXTRA_TARGETS)
                || target instanceof Enemy && !(target instanceof NeutralMob);
    }

    public static boolean inBounds(AiMaidEntity maid, LivingEntity target, Vec3 fallbackAnchor) {
        if (WorkDefense.supports(maid.getMaidCommand())) {
            // 工作可以远离主人；以遇袭处为固定中心，换敌也不把追击圈向外推。
            double limit = WORK_DEFENSE_RANGE * WORK_DEFENSE_RANGE;
            return maid.position().distanceToSqr(fallbackAnchor) <= limit
                    && target.position().distanceToSqr(fallbackAnchor) <= limit
                    && maid.distanceToSqr(target) <= limit;
        }
        LivingEntity owner = owner(maid);
        Vec3 anchor = owner == null ? fallbackAnchor : owner.position();
        double limit = LEASH_RANGE * LEASH_RANGE;
        return maid.position().distanceToSqr(anchor) <= limit
                && target.position().distanceToSqr(anchor) <= limit
                && maid.distanceToSqr(target) <= limit;
    }

    public static boolean clearShot(AiMaidEntity maid, LivingEntity target) {
        if (!maid.getSensing().hasLineOfSight(target)) return false;
        Vec3 from = maid.getEyePosition();
        Vec3 to = target.getEyePosition();
        // 给弹道留出余量；发射后移动到弹道上的友方另由伤害事件保护。
        return maid.level().getEntitiesOfClass(LivingEntity.class,
                maid.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
                other -> other != maid && other != target && isProtected(maid, other))
                .stream().noneMatch(other -> other.getBoundingBox().inflate(0.8).clip(from, to).isPresent());
    }
}
