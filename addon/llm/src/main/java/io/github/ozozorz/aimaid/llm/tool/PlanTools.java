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
    private record Submission(UUID owner, JsonObject arguments, MaidPlanTask task) {}
    private static final AttachmentType<LinkedHashMap<String, Submission>> HISTORY = AttachmentRegistry.create(Identifier.parse("ai-maid-llm:plan_receipts"));
    private PlanTools() {}
    public static void initialize() { ActionTools.initialize(); }
    public static void definitions(JsonArray tools) {
        tools.add(MaidTools.function("list_actions", "列出当前实际安装的有限动作及参数：移动、装备、收获、伐木、存放、返回。每个动作的目标类型、数量和物品规则见目录。", new JsonObject(), List.of()));
        ActionTools.observationDefinitions(tools);
        var props = new JsonObject(); var id = MaidTools.schema("string");
        id.addProperty("minLength", 1); id.addProperty("maxLength", 64);
        id.addProperty("pattern", "^[a-zA-Z0-9_-]{1,64}$");
        id.addProperty("description", "为这次请求生成唯一标识；重试必须复用同一标识和计划。新的明确请求用新标识。最近16个已接受计划在实体卸载前去重。");
        props.add("request_id", id);
        var steps = MaidTools.schema("array"); steps.addProperty("minItems", 1); steps.addProperty("maxItems", MaidActions.MAX_STEPS);
        steps.add("items", ActionTools.actionSchema()); props.add("steps", steps);
        tools.add(MaidTools.function("submit_plan", "提交1..8步顺序计划，按主人要求组合实际存在的动作。每个动作完成后才执行下一步。任一步失败停止，保留实际收获和转移的数量。全部步骤预检通过才启动。接受不代表完成；禁止默默重试部分完成的计划。", props, List.of("request_id", "steps")));
        tools.add(MaidTools.function("cancel_task", "取消当前有限任务并原地待命，后续步骤全部跳过；已收获或已存入的物品保留。", new JsonObject(), List.of()));
    }
    public static JsonObject catalog() {
        var result = new JsonObject(); result.addProperty("ok", true); var actions = new JsonArray();
        for (var type : MaidActions.definitions()) {
            var row = new JsonObject(); row.addProperty("action", type.id().toString()); row.addProperty("description", type.description().getString());
            row.addProperty("requires_count", type.count()); row.addProperty("requires_observed_chest", type.chest()); row.addProperty("target_kind", type.focus().name().toLowerCase(Locale.ROOT));
            row.addProperty("requires_item", type.item()); row.addProperty("requires_inventory_slot", type.slot()); actions.add(row);
        }
        result.add("actions", actions); result.addProperty("max_steps", MaidActions.MAX_STEPS); result.addProperty("max_count_per_step", MaidActions.MAX_COUNT);
        return result;
    }
    public static JsonObject execute(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        if (Set.of("observe_chest", "observe_nearby", "observe_position").contains(name)) return ActionTools.observe(name, args, maid, player);
        if (name.equals("submit_plan")) return submit(args, maid, player);
        if (!args.isEmpty()) return ChatProtocol.error("unexpected_arguments");
        if (name.equals("list_actions")) return catalog();
        boolean changed = MaidTasks.cancel(maid, "cancelled");
        if (changed) maid.selectMaidCommand(MaidCommands.STAY);
        var result = MaidTools.snapshot(maid); result.addProperty("task_cancelled", changed); return result;
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
            requests.add(ActionTools.parse(value.getAsJsonObject(), maid, player));
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
}
