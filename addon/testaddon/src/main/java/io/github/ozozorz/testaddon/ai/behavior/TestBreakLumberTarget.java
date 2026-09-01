package io.github.ozozorz.testaddon.ai.behavior;

import com.mojang.datafixers.util.Unit;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

public class TestBreakLumberTarget {

    private TestBreakLumberTarget() {
    }

    public static BehaviorControl<AiMaidEntity> create() {
        return BehaviorBuilder.create(i -> {
            var memories = i.group(
                // 真正准备破坏的原木
                i.present(TestAddonMemoryModuleTypes.LUMBER_TARGET),
                // 计划好的工作站位
                i.present(TestAddonMemoryModuleTypes.LUMBER_WORK_POS),
                i.absent(TestAddonMemoryModuleTypes.LUMBER_DONE)
            );
            return memories.apply(i, (lumberTarget, lumberWorkPos, lumberDone) -> {
                return (level, maid, timestamp) -> {
                    
                    BlockPos targetPos = i.get(lumberTarget);
                    BlockPos workPos = i.get(lumberWorkPos);

                    BlockState state = level.getBlockState(targetPos);

                    // 作时再次验证世界状态
                    if (!state.is(BlockTags.LOGS) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    // 两个 Memory 必须表示：水平相邻的原木与站位。
                    // 这也是对 Memory 数据一致性的保护。
                    if (targetPos.distManhattan(workPos) != 1) {
                        lumberDone.set(Unit.INSTANCE);
                        return true;
                    }

                    // 不再使用模糊的 2 格球形距离。
                    // Maid 必须真正站到规划出来的 LUMBER_WORK_POS。
                    if (!workPos.equals(maid.blockPosition())) {
                        return false;
                    }

                    ItemStack tool = maid.getItemBySlot(EquipmentSlot.MAINHAND);

                    if (!tool.is(ItemTags.AXES)) {
                        return false;
                    }

                    destroyWithLivingEntity(level, maid, targetPos, state, tool);

                    // 一次 command 仍然只处理一块原木
                    lumberDone.set(Unit.INSTANCE);

                    return true;
                };
            });
        });
    }

    private static boolean destroyWithLivingEntity(ServerLevel level, AiMaidEntity maid, BlockPos pos,
            BlockState state, ItemStack tool) {
        // 不可破坏方块
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        /*
         * 26.2 Item 底层 API 接受 LivingEntity，
         * 所以直接让真实 Maid 参与工具逻辑。
         */
        if (!tool.getItem().canDestroyBlock(tool, state, level, pos, maid)) {
            return false;
        }

        Block block = state.getBlock();

        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        /*
         * Vanilla 玩家破坏链也是先保留一份工具状态，
         * 后面拿它作为 loot context 中的 TOOL。
         */
        ItemStack destroyedWith = tool.copy();

        boolean correctToolForDrops = tool.isCorrectToolForDrops(state);
        
        /*
         * false：
         * 不让 Level.destroyBlock 使用 ItemStack.EMPTY 自动掉落。
         *
         * 但仍复用 Vanilla 的：
         * 方块移除
         * neighbor update
         * break effect
         * BLOCK_DESTROY game event
         */
        boolean destroyed = level.destroyBlock(pos, false, maid);

        if (!destroyed) {
            return false;
        }

        // 对应 Vanilla block 被真正移除后的通用 callback。
        block.destroy(level, pos, state);

        /*
         * 使用真实工具执行 mining 后处理。
         * 当前 26.2 会根据 TOOL component 正常损耗耐久。
         */
        tool.getItem().mineBlock(tool, level, state, pos, maid);

        /*
         * 正确工具时，用破坏前复制的真实工具进入 loot context。
         */
        if (correctToolForDrops) {
            Block.dropResources(state, level, pos, blockEntity, maid, destroyedWith);
        }

        return true;
    }

}
