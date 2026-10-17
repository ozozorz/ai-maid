package io.github.ozozorz.aimaid.hauling;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.TransportItemTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** 复用原版双箱解析，额外在每次实际转移前检查两半的锁和加载状态。 */
public final class HaulingContainers {
    private HaulingContainers() {}
    public static Container resolve(ServerLevel level, BlockPos pos, boolean source) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        var state = level.getBlockState(pos);
        if (source ? !state.is(BlockTags.COPPER_CHESTS) : !(state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))) return null;
        if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity chest) || chest.isLocked()) return null;
        if (state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
            var connected = ChestBlock.getConnectedBlockPos(pos, state);
            if (!level.hasChunkAt(connected) || !(level.getBlockEntity(connected) instanceof BaseContainerBlockEntity other) || other.isLocked()) return null;
        }
        var target = TransportItemTarget.tryCreatePossibleTarget(pos, level);
        return target == null ? null : target.container();
    }
    public static boolean matches(Container container, ItemStack cargo) {
        if (container.isEmpty()) return true;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(cargo.getItem())) return true;
        return false;
    }
    public static boolean hasSpace(Container container, ItemStack cargo) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var current = container.getItem(i);
            if (container.canPlaceItem(i, cargo) && (current.isEmpty()
                    || ItemStack.isSameItemSameComponents(current, cargo) && current.getCount() < container.getMaxStackSize(cargo))) return true;
        }
        return false;
    }
    /** 从真实货物栈拆分，严格保留 components 和部分剩余量。 */
    public static int insert(Container container, ItemStack cargo, int limit) {
        int moved = 0;
        for (int i = 0; i < container.getContainerSize() && moved < limit && !cargo.isEmpty(); i++) {
            if (!container.canPlaceItem(i, cargo)) continue;
            ItemStack current = container.getItem(i);
            int max = container.getMaxStackSize(cargo);
            int count = Math.min(limit - moved, Math.min(cargo.getCount(), current.isEmpty() ? max : max - current.getCount()));
            if (count <= 0 || !current.isEmpty() && !ItemStack.isSameItemSameComponents(current, cargo)) continue;
            if (current.isEmpty()) container.setItem(i, cargo.split(count));
            else { current.grow(count); cargo.shrink(count); container.setItem(i, current); }
            moved += count;
        }
        if (moved > 0) container.setChanged();
        return moved;
    }
}
