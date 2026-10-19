package io.github.ozozorz.aimaid.llm.tool;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Structured plans only: no generated JavaScript, raw commands, or addon class dependencies. */
public final class PlanTools {
    private record Target(UUID owner, MaidActions.ChestTarget chest, long expires) {}
    private record Submission(UUID owner, JsonObject arguments, MaidPlanTask task) {}
    private static final AttachmentType<LinkedHashMap<String, Target>> TARGETS = AttachmentRegistry.create(Identifier.parse("ai-maid-llm:plan_targets"));
    private static final AttachmentType<LinkedHashMap<String, Submission>> HISTORY = AttachmentRegistry.create(Identifier.parse("ai-maid-llm:plan_receipts"));
    private PlanTools() {}
    public static void initialize() {}
    public static void definitions(JsonArray tools) {
        tools.add(MaidTools.function("list_actions", "列出当前实际安装、可组成有限计划的动作及参数。collect_wheat=背包补足目标数量；deposit_wheat=仅存入现有小麦；return_to_owner=步行返回。", new JsonObject(), List.of()));
        var coordinates = new JsonObject();
        for (String axis : List.of("x", "y", "z")) coordinates.add(axis, MaidTools.schema("integer"));
        tools.add(MaidTools.function("observe_chest", "观察主人准星6格内指定的箱子；仅当主人明确给坐标时传入完整x/y/z。返回限定此主人和女仆的target_id，可同时保存多个不同箱子，60秒内提交计划。箱子须距女仆32格内。", coordinates, List.of()));
        var props = new JsonObject(); var id = MaidTools.schema("string");
        id.addProperty("minLength", 1); id.addProperty("maxLength", 64);
        id.addProperty("pattern", "^[a-zA-Z0-9_-]{1,64}$");
        id.addProperty("description", "为这次请求生成唯一标识；重试必须复用同一标识和计划。新的明确请求用新标识。最近16个已接受计划在实体卸载前去重。");
        props.add("request_id", id);
        var steps = MaidTools.schema("array"); steps.addProperty("minItems", 1); steps.addProperty("maxItems", MaidActions.MAX_STEPS);
        var variants = new JsonArray();
        for (var action : MaidActions.definitions()) {
            var item = MaidTools.schema("object"); var fields = new JsonObject(); var required = new JsonArray();
            var name = MaidTools.schema("string"); var names = new JsonArray(); names.add(action.id().toString()); name.add("enum", names);
            fields.add("action", name); required.add("action");
            if (action.count()) { var count = MaidTools.schema("integer"); count.addProperty("minimum", 1); count.addProperty("maximum", MaidActions.MAX_COUNT); fields.add("count", count); required.add("count"); }
            if (action.chest()) { fields.add("target_id", MaidTools.schema("string")); required.add("target_id"); }
            item.add("properties", fields); item.add("required", required); item.addProperty("additionalProperties", false); variants.add(item);
        }
        var items = new JsonObject(); items.add("oneOf", variants); steps.add("items", items); props.add("steps", steps);
        tools.add(MaidTools.function("submit_plan", "提交1..8步顺序计划，按主人要求组合实际存在的动作。每个动作完成后才执行下一步。任一步失败停止，保留实际收获和转移的数量。全部步骤预检通过才启动。接受不代表完成；禁止默默重试部分完成的计划。", props, List.of("request_id", "steps")));
        tools.add(MaidTools.function("cancel_task", "取消当前有限任务并原地待命，后续步骤全部跳过；已收获或已存入的物品保留。", new JsonObject(), List.of()));
    }
    public static JsonObject catalog() {
        var result = new JsonObject(); result.addProperty("ok", true); var actions = new JsonArray();
        for (var type : MaidActions.definitions()) {
            var row = new JsonObject(); row.addProperty("action", type.id().toString()); row.addProperty("description", type.description().getString());
            row.addProperty("requires_count", type.count()); row.addProperty("requires_observed_chest", type.chest()); actions.add(row);
        }
        result.add("actions", actions); result.addProperty("max_steps", MaidActions.MAX_STEPS); result.addProperty("max_count_per_step", MaidActions.MAX_COUNT);
        return result;
    }
    public static JsonObject execute(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (name.equals("observe_chest")) return observe(args, maid, player);
        if (name.equals("submit_plan")) return submit(args, maid, player);
        if (!args.isEmpty()) return ChatProtocol.error("unexpected_arguments");
        if (name.equals("list_actions")) return catalog();
        boolean changed = MaidTasks.cancel(maid, "cancelled");
        if (changed) maid.selectMaidCommand(MaidCommands.STAY);
        var result = MaidTools.snapshot(maid); result.addProperty("task_cancelled", changed); return result;
    }
    private static JsonObject observe(JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (!args.isEmpty() && !args.keySet().equals(Set.of("x", "y", "z"))) return ChatProtocol.error("provide_all_coordinates_or_none");
        BlockPos pos = args.isEmpty() ? MaidContainers.lookedAtChest(player) : new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        var level = player.level();
        if (pos == null || pos.distToCenterSqr(maid.position()) > 32 * 32 || MaidContainers.resolveChest(level, pos) == null)
            return ChatProtocol.error("point_at_accessible_chest_or_provide_coordinates_within_32_blocks");
        var targets = maid.getAttachedOrCreate(TARGETS, LinkedHashMap::new);
        targets.values().removeIf(t -> !t.owner().equals(player.getUUID()) || level.getGameTime() > t.expires());
        while (targets.size() >= 16) targets.remove(targets.keySet().iterator().next());
        var token = UUID.randomUUID().toString(); var target = new MaidActions.ChestTarget(GlobalPos.of(level.dimension(), pos.immutable()), level.getBlockEntity(pos));
        targets.put(token, new Target(player.getUUID(), target, level.getGameTime() + 1200));
        var result = new JsonObject(); result.addProperty("ok", true); result.addProperty("target_id", token);
        result.addProperty("position", pos.toShortString()); result.addProperty("dimension", level.dimension().identifier().toString());
        var chest = MaidContainers.resolveChest(level, pos); int wheat = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(net.minecraft.world.item.Items.WHEAT)) wheat += chest.getItem(i).getCount();
        result.addProperty("wheat_in_chest", wheat); return result;
    }
    private static JsonObject submit(JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (!args.keySet().equals(Set.of("request_id", "steps"))) return ChatProtocol.error("unexpected_arguments");
        String id = ChatProtocol.string(args, "request_id", 64);
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) return ChatProtocol.error("invalid_request_id");
        var history = maid.getAttached(HISTORY);
        var previous = history == null ? null : history.get(id);
        if (previous != null && previous.owner().equals(player.getUUID())) {
            if (!previous.arguments().equals(args)) return ChatProtocol.error("request_id_conflict");
            var result = MaidTools.snapshot(maid); result.add("task", MaidTools.taskSnapshot(maid, previous.task()));
            result.addProperty("already_submitted", true); result.addProperty("task_started", false);
            result.addProperty("work_completed", previous.task().snapshot(maid).state() == MaidTask.State.SUCCEEDED); return result;
        }
        if (!args.get("steps").isJsonArray() || args.getAsJsonArray("steps").isEmpty() || args.getAsJsonArray("steps").size() > MaidActions.MAX_STEPS)
            return ChatProtocol.error("step_count_out_of_range");
        var running = MaidTasks.current(maid);
        if (running != null && !running.snapshot(maid).state().terminal()) return ChatProtocol.error("task_busy_cancel_before_replacing");
        var requests = new ArrayList<MaidActions.Request>();
        for (var value : args.getAsJsonArray("steps")) {
            if (!value.isJsonObject()) return ChatProtocol.error("invalid_step");
            var step = value.getAsJsonObject(); var action = Identifier.tryParse(ChatProtocol.string(step, "action", 200));
            var type = action == null ? null : MaidActions.get(action);
            if (type == null) return ChatProtocol.error("action_unavailable");
            var fields = new HashSet<>(Set.of("action"));
            if (type.count()) fields.add("count"); if (type.chest()) fields.add("target_id");
            if (!fields.equals(step.keySet())) return ChatProtocol.error("invalid_step_arguments");
            int count = type.count() ? integer(step, "count") : 0;
            if (type.count() && (count < 1 || count > MaidActions.MAX_COUNT)) return ChatProtocol.error("count_out_of_range");
            MaidActions.ChestTarget chest = null;
            if (type.chest()) {
                var targets = maid.getAttached(TARGETS); String token = ChatProtocol.string(step, "target_id", 64);
                var target = targets == null ? null : targets.get(token);
                if (target == null || !target.owner().equals(player.getUUID()) || player.level().getGameTime() > target.expires())
                    return ChatProtocol.error("observe_target_again");
                chest = target.chest();
            }
            requests.add(new MaidActions.Request(action, count, chest));
        }
        if (!MaidTools.allowed(maid, player, 32) || !MaidCommands.PLAN.canPlayerSelect(maid, player)) return ChatProtocol.error("maid_unavailable_or_not_owner");
        final MaidPlanTask task;
        try { task = new MaidPlanTask(maid, id, requests); }
        catch (IllegalArgumentException e) { return ChatProtocol.error("plan_invalid_or_chest_changed"); }
        maid.selectMaidCommand(MaidCommands.PLAN); MaidTasks.install(maid, task); AiMaidAi.updateActivity(maid);
        history = maid.getAttachedOrCreate(HISTORY, LinkedHashMap::new);
        history.values().removeIf(s -> !s.owner().equals(player.getUUID()));
        while (history.size() >= 16) history.remove(history.keySet().iterator().next());
        history.put(id, new Submission(player.getUUID(), args.deepCopy(), task));
        var result = MaidTools.snapshot(maid); result.addProperty("task_started", true); result.addProperty("work_completed", false); return result;
    }
    private static int integer(JsonObject args, String key) {
        var value = args.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return value.getAsBigDecimal().intValueExact();
    }
}
