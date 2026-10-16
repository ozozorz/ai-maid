package io.github.ozozorz.aimaid.entity.ai.behavior;

import io.github.ozozorz.aimaid.entity.MaidServerAccess;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 通用的生物破坏机制；选什么方块、任务队列和进度状态属于调用者。 */
public final class MaidBlockBreaking {
    private MaidBlockBreaking() {}

    public static boolean canReach(ServerLevel level, AiMaidEntity maid, BlockPos pos) {
        return canReachFrom(level, maid, maid.getEyePosition(), pos);
    }

    public static boolean canReachFrom(ServerLevel level, AiMaidEntity maid, Vec3 eye, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        Vec3 center = Vec3.atCenterOf(pos);
        double range = maid.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (eye.distanceToSqr(center) > range * range) return false;
        var hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public static boolean canBreak(ServerLevel level, AiMaidEntity maid, BlockPos pos, BlockState state, ItemStack tool) {
        return maid.level() == level && maid.isAlive() && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getGameRules().get(GameRules.MOB_GRIEFING) && !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0
                && tool.getItem().canDestroyBlock(tool, state, level, pos, maid);
    }

    public static float progressPerTick(ServerLevel level, AiMaidEntity maid, BlockPos pos, BlockState state, ItemStack tool) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 0;
        if (hardness == 0) return 1;
        // 对齐 26.2 Player.getDestroySpeed 的可用于 LivingEntity 的计算。
        float speed = tool.getDestroySpeed(state);
        if (speed > 1) speed += (float) maid.getAttributeValue(Attributes.MINING_EFFICIENCY);
        if (MobEffectUtil.hasDigSpeed(maid)) speed *= 1 + (MobEffectUtil.getDigSpeedAmplification(maid) + 1) * 0.2F;
        if (maid.hasEffect(MobEffects.MINING_FATIGUE)) {
            speed *= switch (maid.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 0.00081F;
            };
        }
        speed *= (float) maid.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (maid.isEyeInFluid(FluidTags.WATER)) speed *= (float) maid.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        if (!maid.onGround()) speed /= 5;
        boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return speed / hardness / (correct ? 30 : 100);
    }

    public static boolean destroy(ServerLevel level, AiMaidEntity maid, BlockPos pos, BlockState expected, ItemStack tool) {
        MaidServerAccess.requireServerThread(maid);
        // 完成时仍重查真实世界、距离、视线和政策，不能凭旧进度远程破坏。
        if (level.getBlockState(pos) != expected || !canReach(level, maid, pos)
                || !canBreak(level, maid, pos, expected, tool)) return false;
        var blockEntity = level.getBlockEntity(pos);
        ItemStack lootTool = tool.copy();
        boolean correct = !expected.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(expected);
        if (!level.destroyBlock(pos, false, maid)) return false;
        expected.getBlock().destroy(level, pos, expected);
        tool.getItem().mineBlock(tool, level, expected, pos, maid);
        if (correct) Block.dropResources(expected, level, pos, blockEntity, maid, lootTool);
        return true;
    }
}
