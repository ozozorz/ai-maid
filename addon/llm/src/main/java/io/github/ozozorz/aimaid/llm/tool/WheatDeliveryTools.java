package io.github.ozozorz.aimaid.llm.tool;

import com.google.gson.*;
import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.MaidTasks;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional addon bridge: no compile/runtime dependency on the farming addon. */
public final class WheatDeliveryTools {
    private static final Identifier COMMAND = Identifier.parse("ai-maid-farming:deliver_wheat");
    private record Target(UUID owner, String token, GlobalPos position, BlockEntity entity, long expires) {}
    private static final AttachmentType<Target> TARGET = AttachmentRegistry.create(Identifier.parse("ai-maid-llm:delivery_target"));
    private WheatDeliveryTools() {}
    public static void initialize() {}
    public static boolean available() { return ModBuiltInRegistries.MAID_COMMAND.getOptional(COMMAND).isPresent(); }

    public static void definitions(JsonArray tools) {
        if (!available()) return;
        var coordinates = new JsonObject();
        for (String axis : List.of("x", "y", "z")) coordinates.add(axis, MaidTools.schema("integer"));
        tools.add(MaidTools.function("observe_wheat_delivery", "查询背包小麦、当前位置周围16格内的成熟小麦和主人指定的箱子。默认使用主人准星6格内指向的箱子；只有主人明确提供坐标时才传入完整x/y/z。返回短期有效的target_id。", coordinates, List.of()));
        var props = new JsonObject();
        var count = MaidTools.schema("integer"); count.addProperty("minimum", 1); count.addProperty("maximum", 64);
        props.add("count", count); props.add("target_id", MaidTools.schema("string"));
        tools.add(MaidTools.function("start_wheat_delivery", "启动一次有限任务：先用背包已有小麦，收获补种以补足指定数量，向观察到的指定箱子存入恰好count个，再步行返回主人。count=1..64，箱子距女仆不超过32格。成功返回仅表示任务已接受；完成或失败由任务执行器另行报告。缺资源/满箱/不可达会停止，已完成的物品转移不回滚。", props, List.of("count", "target_id")));
        tools.add(MaidTools.function("cancel_task", "取消当前有限任务并原地待命；已收获或已存入的物品保留。", new JsonObject(), List.of()));
    }

    public static JsonObject execute(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (!available()) return ChatProtocol.error("task_unavailable");
        if (name.equals("observe_wheat_delivery")) return observe(args, maid, player);
        if (name.equals("cancel_task")) {
            if (!args.isEmpty()) return ChatProtocol.error("unexpected_arguments");
            boolean changed = MaidTasks.cancel(maid, "cancelled");
            if (changed) maid.selectMaidCommand(MaidCommands.STAY);
            var result = MaidTools.snapshot(maid); result.addProperty("task_cancelled", changed); return result;
        }
        if (!args.keySet().equals(Set.of("count", "target_id"))) return ChatProtocol.error("unexpected_arguments");
        int count = integer(args, "count");
        if (count < 1 || count > 64) return ChatProtocol.error("count_out_of_range");
        String token = ChatProtocol.string(args, "target_id", 64);
        var target = maid.getAttached(TARGET);
        if (target == null || !target.owner().equals(player.getUUID()) || !target.token().equals(token)
                || target.position().dimension() != maid.level().dimension()
                || player.level().getGameTime() > target.expires()) return ChatProtocol.error("observe_target_again");
        var pos = target.position().pos();
        if (!player.level().hasChunkAt(pos) || player.level().getBlockEntity(pos) != target.entity()
                || MaidContainers.resolveChest(player.level(), pos) == null) return ChatProtocol.error("chest_changed");
        try {
            int accepted = player.level().getServer().getCommands().getDispatcher().execute(
                    "maid uuid " + maid.getUUID() + " command " + COMMAND + " " + count + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                    player.createCommandSourceStack().withSuppressedOutput());
            if (accepted <= 0) return ChatProtocol.error("task_rejected_check_owner_range_chest_or_active_task");
            var result = MaidTools.snapshot(maid); result.addProperty("task_started", true); result.addProperty("work_completed", false); return result;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return ChatProtocol.error("task_command_rejected"); }
    }

    private static JsonObject observe(JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (!args.isEmpty() && !args.keySet().equals(Set.of("x", "y", "z"))) return ChatProtocol.error("provide_all_coordinates_or_none");
        maid.removeAttached(TARGET);
        BlockPos pos = args.isEmpty() ? MaidContainers.lookedAtChest(player)
                : new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        var result = new JsonObject(); result.addProperty("ok", true);
        result.addProperty("wheat_in_inventory", maid.getInventory().countMatching(s -> s.is(Items.WHEAT)));
        result.addProperty("search_radius", 16); result.addProperty("farm_center", maid.blockPosition().toShortString());
        int mature = 0;
        var level = player.level(); var center = maid.blockPosition();
        for (var p : BlockPos.betweenClosed(center.offset(-16, -2, -16), center.offset(16, 2, 16))) {
            if (level.hasChunkAt(p) && level.getWorldBorder().isWithinBounds(p) && level.getBlockState(p.below()).is(Blocks.FARMLAND)
                    && level.getBlockState(p).is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).isMaxAge(level.getBlockState(p))) mature++;
        }
        result.addProperty("mature_wheat_in_area", mature);
        if (pos == null || pos.distToCenterSqr(maid.position()) > 32 * 32 || MaidContainers.resolveChest(level, pos) == null) {
            result.addProperty("destination_error", "point_at_accessible_chest_or_provide_coordinates_within_32_blocks"); return result;
        }
        String token = UUID.randomUUID().toString();
        maid.setAttached(TARGET, new Target(player.getUUID(), token, GlobalPos.of(level.dimension(), pos.immutable()),
                level.getBlockEntity(pos), level.getGameTime() + 1200));
        result.addProperty("target_id", token); result.addProperty("chest_position", pos.toShortString()); return result;
    }

    private static int integer(JsonObject args, String key) {
        var value = args.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return value.getAsBigDecimal().intValueExact();
    }
}
