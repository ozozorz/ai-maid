package io.github.ozozorz.aimaid.entity.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.TransportItemTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Shared chest access; callers must also validate ownership and physical reach. */
public final class MaidContainers {
    private MaidContainers() {}

    public static BlockPos lookedAtChest(ServerPlayer player) {
        var hit = player.pick(6.0, 1.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && resolveChest(player.level(), block.getBlockPos()) != null ? block.getBlockPos() : null;
    }

    public static Container resolveChest(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
        var state = level.getBlockState(pos);
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.TRAPPED_CHEST) && !state.is(BlockTags.COPPER_CHESTS)) return null;
        if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity chest) || chest.isLocked()) return null;
        if (state.getValueOrElse(ChestBlock.TYPE, ChestType.SINGLE) != ChestType.SINGLE) {
            var otherPos = ChestBlock.getConnectedBlockPos(pos, state);
            if (!level.hasChunkAt(otherPos) || !(level.getBlockEntity(otherPos) instanceof BaseContainerBlockEntity other)
                    || other.isLocked()) return null;
        }
        var target = TransportItemTarget.tryCreatePossibleTarget(pos, level);
        return target == null ? null : target.container();
    }

    /** Consumes only the actual inserted amount from the real source stack. */
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
