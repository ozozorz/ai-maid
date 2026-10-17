package io.github.ozozorz.aimaid.feeding;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.gamerules.GameRules;

/** 使用 Animal.isFood / setInLove；不直接生成幼崽，不缩短原版繁殖冷却。 */
public final class FeedingBehavior extends Behavior<AiMaidEntity> {
    public static final TagKey<EntityType<?>> ANIMALS = TagKey.create(Registries.ENTITY_TYPE, FeedingAddon.id("feedable_animals"));
    private final Map<UUID, Long> retry = new HashMap<>();
    private final MaidWorkNavigation.Progress progress = new MaidWorkNavigation.Progress();
    private Animal target;
    private BlockPos feet, plannedAnimalPos;
    private GlobalPos area;
    private int areaRadius, interaction, searchDelay = 40;
    private long nextSearch, nextPlan, inventoryVersion = -1;
    private boolean waiting;

    public boolean isWaiting() { return waiting; }
    public FeedingBehavior() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }
    @Override protected boolean timedOut(long now) { return false; }
    private boolean active(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand() == FeedingAddon.COMMAND
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) { return active(maid); }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) { return active(maid); }
    private boolean eligible(AiMaidEntity maid, Animal animal) {
        var center = maid.getAttached(FeedingData.CENTER);
        return center != null && center.dimension() == animal.level().dimension() && animal.isAlive()
                && animal.is(ANIMALS) && animal.getAge() == 0 && animal.canFallInLove()
                && !(animal instanceof TamableAnimal pet && (!pet.isTame() || !Objects.equals(pet.getOwnerReference(), maid.getOwnerReference())))
                && new AABB(center.pos()).inflate(FeedingData.radius(maid), 4, FeedingData.radius(maid)).contains(animal.position());
    }
    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        var center = maid.getAttached(FeedingData.CENTER);
        if (center == null || center.dimension() != level.dimension()) { pause(maid, "wrong_dimension"); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { pause(maid, "disabled"); return; }
        if (maid.isPassenger() || maid.isLeashed()) { pause(maid, "restrained"); return; }
        if (!center.equals(area) || areaRadius != FeedingData.radius(maid)) {
            clear(maid); retry.clear(); nextSearch = 0; area = center; areaRadius = FeedingData.radius(maid);
        }
        long version = maid.getInventory().getChangeVersion();
        if (inventoryVersion != version) { inventoryVersion = version; nextSearch = 0; searchDelay = 40; }
        if (!maid.onGround()) return;
        retry.entrySet().removeIf(e -> e.getValue() <= now);
        if (target != null && (!eligible(maid, target) || maid.getInventory().findFirstMatchingSlot(target::isFood).isEmpty())) clear(maid);
        if (target == null) {
            if (now < nextSearch) return;
            var animals = level.getEntitiesOfClass(Animal.class,
                    new AABB(center.pos()).inflate(areaRadius, 4, areaRadius), a -> eligible(maid, a));
            animals.sort(Comparator.comparingDouble(maid::distanceToSqr));
            boolean hasFood = false;
            int paths = 0;
            for (Animal animal : animals) {
                if (maid.getInventory().findFirstMatchingSlot(animal::isFood).isEmpty()) continue;
                hasFood = true;
                if (retry.containsKey(animal.getUUID())) continue;
                // 先过滤看不见的目标，再验证可操作站位；最近一只不能长期占据任务。
                if (!maid.getSensing().hasLineOfSight(animal)) { retry.put(animal.getUUID(), now + 40); continue; }
                if (++paths > 16) break;
                var stand = MaidWorkNavigation.chooseStand(maid, animal, 2.5);
                if (stand.isEmpty()) { retry.put(animal.getUUID(), now + 100); continue; }
                target = animal; feet = stand.get(); plannedAnimalPos = animal.blockPosition(); nextPlan = now + 20;
                interaction = 0; waiting = false; searchDelay = 40; progress.reset(maid, now); break;
            }
            if (target == null) {
                waiting = true;
                FeedingData.status(maid, animals.isEmpty() ? "no_animals" : hasFood ? "unreachable" : "no_food");
                nextSearch = now + searchDelay + maid.getRandom().nextInt(10);
                searchDelay = Math.min(160, searchDelay * 2); return;
            }
        }
        MaidWorkNavigation.lookAt(maid, target);
        if (!MaidWorkNavigation.canReach(maid, target, 2.5)) {
            interaction = 0;
            if (progress.stalled(maid, now, 80)) {
                retry.put(target.getUUID(), now + 100); clear(maid); FeedingData.status(maid, "unreachable"); return;
            }
            if (now >= nextPlan && (!target.blockPosition().equals(plannedAnimalPos)
                    || maid.getNavigation().isDone() || !MaidWorkNavigation.canStand(maid, feet))) {
                var stand = MaidWorkNavigation.chooseStand(maid, target, 2.5);
                if (stand.isEmpty()) { retry.put(target.getUUID(), now + 100); clear(maid); return; }
                feet = stand.get(); plannedAnimalPos = target.blockPosition(); nextPlan = now + 20;
            }
            MaidWorkNavigation.walk(maid, feet); FeedingData.status(maid, "walking"); return;
        }
        MaidWorkNavigation.haltMovement(maid);
        FeedingData.status(maid, "preparing");
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 6) return;
        int slot = maid.getInventory().findFirstMatchingSlot(target::isFood).orElse(-1);
        // 临到喂食再次检查：另一只女仆可能已喂过，或玩家已拿走饲料。
        if (slot < 0 || !eligible(maid, target)) { clear(maid); return; }
        var food = maid.getInventory().getItem(slot);
        var remainder = food.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
        int before = food.getCount();
        food.shrink(1);
        var extras = new ArrayList<net.minecraft.world.item.ItemStack>();
        if (remainder != null) food = remainder.convertIntoRemainder(food, before, false, extras::add);
        maid.getInventory().setItem(slot, food);
        for (var extra : extras) {
            var left = maid.getInventory().addItem(extra);
            if (!left.isEmpty()) maid.spawnAtLocation(level, left);
        }
        target.setInLove(maid.getOwner() instanceof Player player ? player : null);
        FeedingData.status(maid, "working");
        clear(maid); nextSearch = now + 1;
    }
    private void clear(AiMaidEntity maid) {
        if (target != null) MaidWorkNavigation.haltMovement(maid);
        target = null; feet = plannedAnimalPos = null; interaction = 0; waiting = false;
    }
    private void pause(AiMaidEntity maid, String reason) { clear(maid); FeedingData.status(maid, reason); }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        clear(maid); MaidWorkNavigation.stop(maid); retry.clear(); area = null; nextSearch = 0;
    }
}
