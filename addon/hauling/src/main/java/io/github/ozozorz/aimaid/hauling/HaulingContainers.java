package io.github.ozozorz.aimaid.hauling;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** 复用原版双箱解析，额外在每次实际转移前检查两半的锁和加载状态。 */
public final class HaulingContainers {
    private HaulingContainers() {}
    public static Container resolve(ServerLevel level, BlockPos pos, boolean source) {
        if (!level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        if (source ? !state.is(BlockTags.COPPER_CHESTS) : !(state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))) return null;
        return io.github.ozozorz.aimaid.entity.inventory.MaidContainers.resolveChest(level, pos);
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
        return io.github.ozozorz.aimaid.entity.inventory.MaidContainers.insert(container, cargo, limit);
    }
}
