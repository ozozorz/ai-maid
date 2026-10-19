package io.github.ozozorz.aimaid.farming;
import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.gamerules.GameRules;

/** One bounded cultivation operation, shared by the continuous mode and finite tasks. */
public final class FarmingActions {
    static final Map<Block, Item> SEEDS = Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT, Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
    private FarmingActions() {}
    public static String cultivate(ServerLevel level, AiMaidEntity maid, BlockPos pos) {
        io.github.ozozorz.aimaid.entity.MaidServerAccess.requireServerThread(maid);
        if (!maid.isAlive() || maid.level() != level || !level.hasChunkAt(pos)
                || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return "no_work";
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return "disabled";
        if (!MaidWorkNavigation.canReach(maid, pos, 3.0)) return "unreachable";
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
}
