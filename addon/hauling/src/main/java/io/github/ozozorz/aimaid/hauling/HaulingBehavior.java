package io.github.ozozorz.aimaid.hauling;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;

/** 铜箱 -> 空箱/同种物品箱，16 件一批；箱子忙碌、没有任务与寻路失败使用不同等待策略。 */
public final class HaulingBehavior extends Behavior<AiMaidEntity> {
    private BlockPos target, feet;
    private GlobalPos area;
    private long nextSearch, nextChestScan, inventoryVersion = -1;
    private int interacting, areaRadius;
    private boolean picking, waiting;
    private Container opened;
    private final List<BlockPos> chests = new ArrayList<>();
    private final MaidWorkNavigation.Progress progress = new MaidWorkNavigation.Progress();
    private final Set<BlockPos> visited = new HashSet<>();
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    public boolean isWaiting() { return waiting; }
    public HaulingBehavior() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }
    @Override protected boolean timedOut(long now) { return false; }
    private boolean active(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand() == HaulingAddon.COMMAND
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) { return active(maid); }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) { return active(maid); }
    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        var center = maid.getAttached(HaulingData.CENTER);
        if (center == null || center.dimension() != level.dimension()) { pause(maid, "wrong_dimension"); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { pause(maid, "disabled"); return; }
        if (maid.isPassenger() || maid.isLeashed()) { pause(maid, "restrained"); return; }
        if (!center.equals(area) || areaRadius != HaulingData.radius(maid)) {
            clear(maid); chests.clear(); visited.clear(); unreachable.clear(); nextSearch = nextChestScan = 0;
            area = center; areaRadius = HaulingData.radius(maid);
        }
        long version = maid.getInventory().getChangeVersion();
        if (inventoryVersion != version) { inventoryVersion = version; nextSearch = 0; }
        if (!maid.onGround()) return;
        unreachable.entrySet().removeIf(entry -> entry.getValue() <= now);
        var cargo = HaulingCargo.current(maid);
        if (cargo == null) maid.removeAttached(HaulingCargo.TYPE);
        if (target != null && picking != (cargo == null)) clear(maid);
        if (cargo == null && emptySlot(maid) < 0) { pause(maid, "full"); waiting = true; return; }
        if (target == null) {
            if (now < nextSearch) return;
            picking = cargo == null;
            var positions = new ArrayList<BlockPos>();
            if (now >= nextChestScan) {
                scanChests(level); nextChestScan = now + 80;
            }
            boolean busy = false, blocked = false;
            for (BlockPos pos : chests) {
                Container container = HaulingContainers.resolve(level, pos, picking);
                if (container == null || !(picking ? !container.isEmpty()
                        : HaulingContainers.matches(container, maid.getInventory().getItem(cargo.slot()))
                        && HaulingContainers.hasSpace(container, maid.getInventory().getItem(cargo.slot())))) continue;
                if (busy(container, maid)) { busy = true; continue; }
                if (unreachable.containsKey(pos)) { blocked = true; continue; }
                if (!visited.contains(pos)) positions.add(pos);
            }
            positions.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(maid.position())));
            var batch = positions.stream().limit(Math.max(0, 10 - visited.size())).toList();
            var chosen = MaidWorkNavigation.chooseWorkPosition(maid, batch, 2.5);
            if (chosen.isPresent()) {
                target = chosen.get().target(); feet = chosen.get().feet(); visited.add(target);
            } else {
                for (BlockPos pos : batch) markUnreachable(pos, now);
                blocked |= !batch.isEmpty();
            }
            if (target == null) {
                clear(maid); visited.clear(); waiting = true;
                // 正被打开的箱子很快再看；不能把等待开箱的人误判成卡路五分钟。
                nextSearch = now + (busy ? 20 : blocked ? 40 : 140);
                HaulingData.status(maid, busy ? "busy" : blocked ? "unreachable" : picking ? "no_source" : "no_destination"); return;
            }
            waiting = false; progress.reset(maid, now); interacting = 0;
        }
        Container container = HaulingContainers.resolve(level, target, picking);
        if (container == null) { clear(maid); return; }
        if (busy(container, maid)) {
            visited.remove(target); clear(maid); HaulingData.status(maid, "busy"); nextSearch = now + 1; return;
        }
        if (!MaidWorkNavigation.canReach(maid, target, 2.5)) {
            if (progress.stalled(maid, now, 80)) { markUnreachable(target, now); pause(maid, "unreachable"); return; }
            if (!MaidWorkNavigation.canStand(maid, feet)
                    || maid.getNavigation().isDone() && feet.distToCenterSqr(maid.position()) < 2) {
                var stand = MaidWorkNavigation.chooseStand(maid, target, 2.5);
                if (stand.isEmpty()) { markUnreachable(target, now); pause(maid, "unreachable"); return; }
                feet = stand.get();
            }
            close(maid); interacting = 0; MaidWorkNavigation.walk(maid, feet, target); HaulingData.status(maid, "walking"); return;
        }
        progress.reset(maid, now);
        MaidWorkNavigation.haltMovement(maid);
        MaidWorkNavigation.lookAt(maid, target);
        if (opened == null) {
            maid.setOpenedContainerPos(target); container.startOpen(maid); opened = container;
        }
        HaulingData.status(maid, picking ? "picking" : "placing");
        if (interacting % 30 == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (++interacting < 60) return;
        // 此刻重新取得的容器和实际背包才是权威；没有保存容器引用或一份复制货物。
        if (picking) pickup(maid, container);
        else deposit(maid, container);
        maid.swing(InteractionHand.MAIN_HAND);
        clear(maid); nextSearch = now + 1;
    }
    private void scanChests(ServerLevel level) {
        chests.clear();
        for (ChunkPos cp : ChunkPos.rangeClosed(ChunkPos.containing(area.pos()), areaRadius / 16 + 1).toList()) {
            var chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
            if (chunk == null) continue;
            for (var be : chunk.getBlockEntities().values()) {
                var pos = be.getBlockPos();
                if (be instanceof ChestBlockEntity && Math.abs(pos.getX() - area.pos().getX()) <= areaRadius
                        && Math.abs(pos.getZ() - area.pos().getZ()) <= areaRadius && Math.abs(pos.getY() - area.pos().getY()) <= 8)
                    chests.add(pos.immutable());
            }
        }
    }
    private static boolean busy(Container container, AiMaidEntity maid) {
        return container.getEntitiesWithContainerOpen().stream().anyMatch(user -> user != maid);
    }
    private void pickup(AiMaidEntity maid, Container source) {
        int destination = emptySlot(maid);
        if (destination < 0 || HaulingCargo.current(maid) != null) return;
        for (int i = 0; i < source.getContainerSize(); i++) {
            var stack = source.getItem(i);
            if (stack.isEmpty() || !source.canTakeItem(maid.getInventory(), i, stack)) continue;
            var cargo = source.removeItem(i, Math.min(16, Math.min(stack.getCount(), stack.getMaxStackSize())));
            if (cargo.isEmpty()) continue;
            maid.getInventory().setItem(destination, cargo);
            maid.setAttached(HaulingCargo.TYPE, new HaulingCargo(destination, ItemStackTemplate.fromNonEmptyStack(cargo).withCount(1), cargo.getCount()));
            source.setChanged(); visited.clear(); return;
        }
    }
    private void deposit(AiMaidEntity maid, Container destination) {
        var cargo = HaulingCargo.current(maid);
        if (cargo == null) return;
        var stack = maid.getInventory().getItem(cargo.slot());
        if (!HaulingContainers.matches(destination, stack)) return;
        int count = Math.min(stack.getCount(), cargo.count());
        int moved = HaulingContainers.insert(destination, stack, count);
        maid.getInventory().setChanged();
        if (moved >= count) { maid.removeAttached(HaulingCargo.TYPE); visited.clear(); }
        else maid.setAttached(HaulingCargo.TYPE, new HaulingCargo(cargo.slot(), cargo.kind(), count - moved));
    }
    private static int emptySlot(AiMaidEntity maid) {
        for (int i = 0; i < maid.getInventory().getContainerSize(); i++) if (maid.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }
    private void close(AiMaidEntity maid) {
        if (opened != null) { opened.stopOpen(maid); opened = null; }
        maid.setOpenedContainerPos(null);
    }
    private void markUnreachable(BlockPos pos, long now) {
        if (unreachable.size() >= 50) unreachable.clear();
        unreachable.put(pos, now + 100);
    }
    private void clear(AiMaidEntity maid) {
        close(maid);
        if (target != null) MaidWorkNavigation.haltMovement(maid);
        target = feet = null; interacting = 0; waiting = false;
    }
    private void pause(AiMaidEntity maid, String reason) { clear(maid); HaulingData.status(maid, reason); }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        clear(maid); MaidWorkNavigation.stop(maid); visited.clear(); unreachable.clear(); chests.clear(); area = null;
        nextSearch = nextChestScan = 0;
    }
}
