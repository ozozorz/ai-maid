package io.github.ozozorz.aimaid.farming;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;

/** Compatibility adapter for the original wheat action. */
public final class CollectWheatAction extends HarvestCropsAction {
    public CollectWheatAction(AiMaidEntity maid, int count) { super(maid, count, Items.WHEAT, maid.blockPosition(), SEARCH_RADIUS); }
    public static int wheat(AiMaidEntity maid) { return maid.getInventory().countMatching(s -> s.is(Items.WHEAT)); }
    public static List<BlockPos> matureWheat(ServerLevel level, BlockPos center) {
        return matureCrops(level, center, SEARCH_RADIUS, (CropBlock) Blocks.WHEAT);
    }
}
