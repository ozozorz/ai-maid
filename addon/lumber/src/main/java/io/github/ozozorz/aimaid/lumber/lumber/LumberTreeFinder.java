package io.github.ozozorz.aimaid.lumber.lumber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.github.ozozorz.aimaid.lumber.LumberAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** 有界、保守的自然树启发式；方块本身没有“由树生成”的永久身份。 */
public final class LumberTreeFinder {
    public static final int MAX_LOGS = 128;
    public static final int MAX_HEIGHT = 32;
    public static final int MAX_RADIUS = 6;
    public static final TagKey<Block> LOGS = TagKey.create(Registries.BLOCK, LumberAddon.id("lumber_logs"));
    public static final TagKey<Block> SOIL = TagKey.create(Registries.BLOCK, LumberAddon.id("lumber_soil"));
    private LumberTreeFinder() {}

    public static Optional<Map<BlockPos, BlockState>> find(ServerLevel level, BlockPos origin, boolean single) {
        int rootsChecked = 0;
        for (BlockPos cursor : BlockPos.withinManhattan(origin, 8, 4, 8)) {
            if (!level.hasChunkAt(cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            if (single) {
                if (state.is(BlockTags.LOGS)) return Optional.of(Map.of(cursor.immutable(), state));
            } else if (state.is(LOGS) && isRoot(level, cursor, state)) {
                var tree = collect(level, cursor.immutable());
                if (tree.isPresent()) return tree;
                if (++rootsChecked >= 32) break;
            }
        }
        return Optional.empty();
    }

    private static boolean isRoot(ServerLevel level, BlockPos pos, BlockState state) {
        return level.hasChunkAt(pos.below()) && level.getBlockState(pos.below()).is(SOIL)
                && (!state.hasProperty(RotatedPillarBlock.AXIS)
                    || state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y);
    }

    public static Optional<Map<BlockPos, BlockState>> collect(ServerLevel level, BlockPos root) {
        BlockState rootState = level.getBlockState(root);
        if (!rootState.is(LOGS) || !isRoot(level, root, rootState)) return Optional.empty();
        Map<BlockPos, BlockState> found = new LinkedHashMap<>();
        var queue = new ArrayDeque<BlockPos>();
        found.put(root, rootState);
        queue.add(root);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            // 对角连接涵盖弯曲树干和分枝；只追踪同一种原木，不沿树叶漫游森林。
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                BlockPos next = pos.offset(x, y, z);
                if (!level.hasChunkAt(next)) return Optional.empty();
                if (found.containsKey(next)) continue;
                BlockState state = level.getBlockState(next);
                if (!state.is(rootState.getBlock())) continue;
                if (Math.abs(next.getX() - root.getX()) > MAX_RADIUS || Math.abs(next.getZ() - root.getZ()) > MAX_RADIUS
                        || next.getY() < root.getY() || next.getY() - root.getY() >= MAX_HEIGHT
                        || found.size() >= MAX_LOGS) return Optional.empty();
                found.put(next, state);
                queue.addLast(next);
            }
        }
        int top = found.keySet().stream().mapToInt(BlockPos::getY).max().orElse(root.getY());
        if (top - root.getY() < 2) return Optional.empty();
        List<BlockPos> roots = found.entrySet().stream().filter(e -> isRoot(level, e.getKey(), e.getValue()))
                .map(Map.Entry::getKey).toList();
        // 一根树干或完整 2×2 树干。额外的落地树干意味着可能连到了别的树/建筑。
        if (roots.size() != 1 && roots.size() != 4) return Optional.empty();
        if (roots.stream().anyMatch(pos -> pos.getY() != root.getY())) return Optional.empty();
        if (roots.size() == 4) {
            int minX = roots.stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int minZ = roots.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            if (roots.stream().anyMatch(pos -> pos.getX() > minX + 1 || pos.getZ() > minZ + 1)) return Optional.empty();
        }
        var leaves = new java.util.HashSet<BlockPos>();
        for (BlockPos pos : found.keySet()) {
            if (pos.getY() < top - 3) continue;
            for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
                if (!level.hasChunkAt(nearby)) continue;
                BlockState state = level.getBlockState(nearby);
                if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT)
                        && !state.getValue(LeavesBlock.PERSISTENT)) leaves.add(nearby.immutable());
            }
        }
        if (leaves.size() < 3) return Optional.empty();
        var ordered = new LinkedHashMap<BlockPos, BlockState>();
        var positions = new ArrayList<>(found.keySet());
        positions.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed().thenComparingLong(BlockPos::asLong));
        positions.forEach(pos -> ordered.put(pos, found.get(pos)));
        return Optional.of(ordered);
    }
}
