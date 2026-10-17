package io.github.ozozorz.aimaid.farming;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.gamerules.GameRules;

/** 只照料现有耕地。每只 Brain 独立拥有目标和失败冷却。 */
public final class FarmingBehavior extends Behavior<AiMaidEntity> {
    private static final Map<Block, Item> SEEDS = Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT, Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
    private final Map<BlockPos, Long> retry = new HashMap<>();
    private final List<BlockPos> candidates = new ArrayList<>();
    private final MaidWorkNavigation.Progress progress = new MaidWorkNavigation.Progress();
    private BlockPos target, feet, lastWorked;
    private GlobalPos area;
    private int areaRadius, interaction, searchDelay = 40;
    private long nextSearch, inventoryVersion = -1;
    private boolean waiting;

    public boolean isWaiting() { return waiting; }

    public FarmingBehavior() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }
    @Override protected boolean timedOut(long now) { return false; }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, AiMaidEntity maid) { return active(maid); }
    @Override protected boolean canStillUse(ServerLevel level, AiMaidEntity maid, long now) { return active(maid); }
    private boolean active(AiMaidEntity maid) {
        return maid.isAlive() && maid.isTame() && maid.getMaidCommand() == FarmingAddon.COMMAND
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }
    @Override protected void tick(ServerLevel level, AiMaidEntity maid, long now) {
        var center = maid.getAttached(FarmingData.CENTER);
        if (center == null || center.dimension() != level.dimension()) { pause(maid, "wrong_dimension"); return; }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) { pause(maid, "disabled"); return; }
        if (maid.isPassenger() || maid.isLeashed()) { pause(maid, "restrained"); return; }
        if (!center.equals(area) || areaRadius != FarmingData.radius(maid)) {
            clear(maid); candidates.clear(); retry.clear(); lastWorked = null; nextSearch = 0;
            area = center; areaRadius = FarmingData.radius(maid);
        }
        long version = maid.getInventory().getChangeVersion();
        if (inventoryVersion != version) { inventoryVersion = version; nextSearch = 0; searchDelay = 40; }
        if (!maid.onGround()) return;
        retry.entrySet().removeIf(e -> e.getValue() <= now);
        if (target != null && (!level.hasChunkAt(target) || !wanted(level, maid, target))) clear(maid);
        if (target == null) {
            if (now < nextSearch) return;
            if (candidates.isEmpty()) {
                for (BlockPos p : BlockPos.betweenClosed(center.pos().offset(-areaRadius,-2,-areaRadius), center.pos().offset(areaRadius,2,areaRadius))) {
                    if (level.hasChunkAt(p) && level.getWorldBorder().isWithinBounds(p) && !retry.containsKey(p) && wanted(level, maid, p))
                        candidates.add(p.immutable());
                }
            }
            candidates.removeIf(p -> !level.hasChunkAt(p) || retry.containsKey(p) || !wanted(level, maid, p));
            // 连续照料最近的一片田；只有本批用完才重新扫描世界。
            candidates.sort(Comparator.comparingDouble(p -> lastWorked == null
                    ? p.distToCenterSqr(maid.position()) : p.distSqr(lastWorked)));
            var batch = candidates.stream().limit(16).toList();
            var chosen = MaidWorkNavigation.chooseWorkPosition(maid, batch, 3.0);
            if (chosen.isPresent()) {
                target = chosen.get().target(); feet = chosen.get().feet(); candidates.remove(target);
                interaction = 0; waiting = false; searchDelay = 40; progress.reset(maid, now);
            } else {
                for (BlockPos p : batch) reject(p, now);
                candidates.removeAll(batch);
            }
            if (target == null) {
                waiting = true;
                FarmingData.status(maid, batch.isEmpty() ? (!retry.isEmpty() ? "unreachable" : hasSeed(maid) ? "no_work" : "no_seeds") : "unreachable");
                nextSearch = now + (candidates.isEmpty() ? searchDelay + maid.getRandom().nextInt(10) : 1);
                searchDelay = Math.min(160, searchDelay * 2);
                return;
            }
        }
        if (!MaidWorkNavigation.canReach(maid, target, 3.0)) {
            interaction = 0;
            if (progress.stalled(maid, now, 80)) {
                reject(target, now); clear(maid); FarmingData.status(maid, "unreachable"); return;
            }
            if (!MaidWorkNavigation.canStand(maid, feet)
                    || maid.getNavigation().isDone() && feet.distToCenterSqr(maid.position()) < 2) {
                var stand = MaidWorkNavigation.chooseStand(maid, target, 3.0);
                if (stand.isEmpty()) { reject(target, now); clear(maid); return; }
                feet = stand.get();
            }
            MaidWorkNavigation.walk(maid, feet, target); FarmingData.status(maid, "walking"); return;
        }
        MaidWorkNavigation.haltMovement(maid);
        MaidWorkNavigation.lookAt(maid, target);
        FarmingData.status(maid, "preparing");
        if (interaction++ == 0) maid.swing(InteractionHand.MAIN_HAND);
        if (interaction < 6) return;
        String result = cultivate(level, maid, target);
        FarmingData.status(maid, result);
        if (result.equals("working")) lastWorked = target;
        clear(maid);
        if (result.equals("full") || result.equals("no_seeds")) {
            candidates.clear(); waiting = true; nextSearch = now + 80;
        } else nextSearch = now + 1;
    }
    private static boolean hasSeed(AiMaidEntity maid) {
        return maid.getInventory().findFirstMatchingSlot(s -> SEEDS.containsValue(s.getItem())).isPresent();
    }
    private static boolean wanted(ServerLevel level, AiMaidEntity maid, BlockPos pos) {
        if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        var state = level.getBlockState(pos);
        return SEEDS.containsKey(state.getBlock()) && ((CropBlock) state.getBlock()).isMaxAge(state)
                || state.isAir() && hasSeed(maid);
    }
    private static String cultivate(ServerLevel level, AiMaidEntity maid, BlockPos pos) {
        var state = level.getBlockState(pos);
        Item seed;
        CropBlock crop;
        var simulated = new SimpleContainer(maid.getInventory().getContainerSize());
        for (int i = 0; i < simulated.getContainerSize(); i++) simulated.setItem(i, maid.getInventory().getItem(i).copy());
        var drops = new ArrayList<ItemStack>();
        if (SEEDS.containsKey(state.getBlock()) && ((CropBlock) state.getBlock()).isMaxAge(state)) {
            crop = (CropBlock) state.getBlock(); seed = SEEDS.get(crop);
            drops.addAll(Block.getDrops(state, level, pos, level.getBlockEntity(pos), maid, maid.getMainHandItem()));
        } else if (state.isAir()) {
            int slot = maid.getInventory().findFirstMatchingSlot(s -> SEEDS.containsValue(s.getItem())).orElse(-1);
            if (slot < 0) return "no_seeds";
            seed = maid.getInventory().getItem(slot).getItem();
            crop = (CropBlock) SEEDS.entrySet().stream().filter(e -> e.getValue() == seed).findFirst().orElseThrow().getKey();
        } else return "searching";
        // 优先用背包的种子；没有时从这次实际掉落中扣一份，余物必须全部放得下。
        boolean paid = false;
        for (int i = 0; i < simulated.getContainerSize(); i++) if (simulated.getItem(i).is(seed)) {
            simulated.getItem(i).shrink(1); paid = true; break;
        }
        if (!paid) for (ItemStack drop : drops) if (drop.is(seed)) { drop.shrink(1); paid = true; break; }
        if (!paid) return "no_seeds";
        for (ItemStack drop : drops) if (!simulated.addItem(drop).isEmpty()) return "full";
        var planted = crop.getStateForAge(0);
        if (!planted.canSurvive(level, pos) || !level.setBlock(pos, planted, 3)) return "no_work";
        // 世界修改成功才提交物品；没有预先生成实体掉落，也不再第二次调用 dropResources。
        for (int i = 0; i < simulated.getContainerSize(); i++) maid.getInventory().setItem(i, simulated.getItem(i));
        maid.swing(InteractionHand.MAIN_HAND);
        if (!state.isAir()) level.levelEvent(2001, pos, Block.getId(state));
        return "working";
    }
    private void reject(BlockPos pos, long now) { retry.put(pos, now + 100); }
    private void clear(AiMaidEntity maid) {
        if (target != null) MaidWorkNavigation.haltMovement(maid);
        target = feet = null; interaction = 0; waiting = false;
    }
    private void pause(AiMaidEntity maid, String status) { clear(maid); FarmingData.status(maid, status); }
    @Override protected void stop(ServerLevel level, AiMaidEntity maid, long now) {
        clear(maid); MaidWorkNavigation.stop(maid); retry.clear(); candidates.clear(); area = null; nextSearch = 0;
    }
}
