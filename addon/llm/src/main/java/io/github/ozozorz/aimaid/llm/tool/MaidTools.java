package io.github.ozozorz.aimaid.llm.tool;

import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** 小而明确的能力目录。可选工作只按 Registry ID 适配，不加载可选 Addon 的类。 */
public final class MaidTools {
    private record CommandInfo(String description, int radiusMax, boolean lumber) {}
    private static final Map<String, CommandInfo> KNOWN = Map.of(
        "ai-maid:follow", new CommandInfo("跟随主人并护主；可使用近战或弓弩。", 0, false),
        "ai-maid:free", new CommandInfo("自由活动。", 0, false),
        "ai-maid:stay", new CommandInfo("停止工作，原地待命。", 0, false),
        "ai-maid:attack", new CommandInfo("主动攻击附近可攻击的 PvE 敌人。", 0, false),
        "ai-maid-lumber:lumber", new CommandInfo("砍一棵树中可走到伸手可及处的原木，高处够不到则跳过；mode=single 仅砍一块。需要斧头。不是无限森林清除。", 0, true),
        "ai-maid-farming:farm", new CommandInfo("在女仆当前位置为中心的现有耕地收获并补种小麦、胡萝卜、马铃薯、甜菜。需种植物品；不自动开垦。radius=2..16。", 16, false),
        "ai-maid-feeding:feed", new CommandInfo("以女仆当前位置为中心，用背包中的食物喂养可繁殖的成年家畜。radius=2..16。", 16, false),
        "ai-maid-hauling:haul", new CommandInfo("以女仆当前位置为中心，从铜箱取货，送往空的或已有同类物品的普通/陷阱箱，类似铜傀儡。不是任意两容器搬运。radius=2..32。", 32, false)
    );
    private MaidTools() {}

    public static JsonArray definitions(AiMaidEntity maid, ServerPlayer player) {
        var tools = new JsonArray();
        tools.add(function("query_maid", "查询当前状态、停工原因、位置、装备、背包；状态是快照，不是任务成功证明。", new JsonObject(), List.of()));
        tools.add(function("list_commands", "查询此服务器实际安装的可见女仆命令及其参数和限制。", new JsonObject(), List.of()));
        var props = new JsonObject(); var id = schema("string"); var ids = new JsonArray();
        commands(maid, player).forEach(c -> ids.add(c.getAsJsonObject().get("id")));
        id.add("enum", ids); props.add("command_id", id);
        var radius = schema("integer"); radius.addProperty("minimum", 2); radius.addProperty("maximum", 32); props.add("radius", radius);
        var mode = schema("string"); var modes = new JsonArray(); modes.add("tree"); modes.add("single"); mode.add("enum", modes); props.add("mode", mode);
        var restart = schema("boolean"); restart.addProperty("description", "只在主人明确要求重新开始/重设中心时使用 true。重复相同指令默认不重启。"); props.add("restart", restart);
        tools.add(function("select_command", "选择一个工作策略。radius 仅限 farm/feed/haul；mode 仅限 lumber。成功表示已选中，不表示工作已完成。不能排队多个工作。", props, List.of("command_id")));
        WheatDeliveryTools.definitions(tools);
        PlanTools.definitions(tools);
        return tools;
    }
    public static JsonObject schema(String type) { var s = new JsonObject(); s.addProperty("type", type); return s; }
    public static JsonObject function(String name, String description, JsonObject props, List<String> required) {
        var parameters = schema("object"); parameters.add("properties", props); parameters.addProperty("additionalProperties", false);
        var array = new JsonArray(); required.forEach(array::add); parameters.add("required", array);
        var fn = new JsonObject(); fn.addProperty("name", name); fn.addProperty("description", description); fn.add("parameters", parameters);
        var tool = new JsonObject(); tool.addProperty("type", "function"); tool.add("function", fn); return tool;
    }
    public static JsonArray commands(AiMaidEntity maid, ServerPlayer player) {
        var array = new JsonArray();
        for (var command : ModBuiltInRegistries.MAID_COMMAND) {
            if (!command.isVisibleInCommandMenu()) continue;
            String id = command.getRegistryId().toString(); var known = KNOWN.get(id);
            var row = new JsonObject(); row.addProperty("id", id); row.addProperty("selectable", command.canPlayerSelect(maid, player));
            row.addProperty("description", known == null ? "第三方命令：" + command.getDisplayName().getString() + "。只支持无参数选择；具体能力未知，勿猜测。" : known.description());
            array.add(row);
        }
        return array;
    }

    /** 调用者先验证会话权限；本层在每次真正修改前再次检查。 */
    public static JsonObject execute(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player, int distance) {
        MaidServerAccess.requireServerThread(maid);
        if (!(name.equals("cancel_task") ? allowedCancellation(maid, player, distance) : allowed(maid, player, distance)))
            return ChatProtocol.error("maid_unavailable_or_not_owner");
        return executeAuthorized(name, args, maid, player, distance);
    }
    public static JsonObject executeForGoal(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player,
            io.github.ozozorz.aimaid.entity.task.MaidGoalTask goal) {
        MaidServerAccess.requireServerThread(maid);
        if (!Set.of("query_maid", "list_actions", "observe_chest", "observe_position", "observe_nearby").contains(name))
            return ChatProtocol.error("tool_unavailable_in_goal");
        if (io.github.ozozorz.aimaid.entity.task.MaidTasks.current(maid) != goal || !goal.active()
                || !maid.isAlive() || maid.isRemoved() || !maid.isTame() || !maid.isOwnedBy(player)
                || !player.isAlive() || player.isSpectator() || maid.level() != player.level()
                || !goal.withinScope(net.minecraft.core.GlobalPos.of(maid.level().dimension(), maid.blockPosition())))
            return ChatProtocol.error("goal_authorization_expired");
        return executeAuthorized(name, args, maid, player, 32);
    }
    private static JsonObject executeAuthorized(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player, int distance) {
        try {
            if (Set.of("list_actions", "observe_chest", "observe_position", "observe_nearby", "submit_plan", "cancel_task").contains(name))
                return PlanTools.execute(name, args, maid, player);
            if (Set.of("observe_wheat_delivery", "start_wheat_delivery").contains(name))
                return WheatDeliveryTools.execute(name, args, maid, player);
            if (name.equals("query_maid") || name.equals("list_commands")) {
                if (!args.isEmpty()) return ChatProtocol.error("unexpected_arguments");
                if (name.equals("query_maid")) return snapshot(maid);
                var result = new JsonObject(); result.add("commands", commands(maid, player)); return result;
            }
            if (!name.equals("select_command")) return ChatProtocol.error("unknown_tool");
            if (!Set.of("command_id", "radius", "mode", "restart").containsAll(args.keySet())) return ChatProtocol.error("unexpected_arguments");
            String id = ChatProtocol.string(args, "command_id", 200);
            var identifier = Identifier.tryParse(id);
            var command = identifier == null ? null : ModBuiltInRegistries.MAID_COMMAND.getOptional(identifier).orElse(null);
            if (command == null || !command.isVisibleInCommandMenu() || !command.canPlayerSelect(maid, player)) return ChatProtocol.error("command_unavailable");
            var known = KNOWN.get(id); String suffix = "";
            if (args.has("radius")) {
                if (known == null || known.radiusMax() == 0) return ChatProtocol.error("radius_not_supported");
                var value = args.get("radius");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return ChatProtocol.error("invalid_radius");
                int radius = value.getAsBigDecimal().intValueExact();
                if (radius < 2 || radius > known.radiusMax()) return ChatProtocol.error("radius_out_of_range");
                suffix = " " + radius;
            }
            if (args.has("mode")) {
                if (known == null || !known.lumber()) return ChatProtocol.error("mode_not_supported");
                String mode = ChatProtocol.string(args, "mode", 16);
                if (!mode.equals("tree") && !mode.equals("single")) return ChatProtocol.error("invalid_mode");
                if (mode.equals("single")) suffix = " single";
            }
            boolean restart = false;
            if (args.has("restart")) {
                var value = args.get("restart");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return ChatProtocol.error("invalid_restart");
                restart = value.getAsBoolean();
            }
            if (maid.getMaidCommand() == command && !restart && !args.has("radius") && !args.has("mode")) {
                var result = snapshot(maid); result.addProperty("selection", "already_selected"); return result;
            }
            // 字符串仅由 Registry 中的 ID、真实 UUID、已校验整数/枚举组成。
            // 不接受模型提供的原始服务器命令，不提升玩家权限。
            String literal = identifier.getNamespace().equals("ai-maid") ? identifier.getPath() : identifier.toString();
            var server = player.level().getServer();
            int selected = server.getCommands().getDispatcher().execute("maid uuid " + maid.getUUID() + " command " + literal + suffix,
                    player.createCommandSourceStack().withSuppressedOutput());
            if (selected <= 0) return ChatProtocol.error("selection_rejected");
            var result = snapshot(maid); result.addProperty("selection", "accepted"); result.addProperty("work_completed", false); return result;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return ChatProtocol.error("command_syntax_unsupported"); }
        catch (IllegalArgumentException | ArithmeticException | IllegalStateException e) { return ChatProtocol.error("invalid_arguments"); }
    }
    public static boolean allowed(AiMaidEntity maid, ServerPlayer player, int distance) {
        return maid.isAlive() && !maid.isRemoved() && player.isAlive() && !player.isSpectator()
                && maid.level() == player.level() && maid.isTame() && maid.isOwnedBy(player)
                && maid.distanceToSqr(player) <= (double) distance * distance;
    }
    /** Revoking a running task remains possible after its owner walks beyond chat range. */
    public static boolean allowedCancellation(AiMaidEntity maid, ServerPlayer player, int distance) {
        if (allowed(maid, player, distance)) return true;
        var task = io.github.ozozorz.aimaid.entity.task.MaidTasks.current(maid);
        return maid.isAlive() && !maid.isRemoved() && player.isAlive() && !player.isSpectator()
                && maid.isTame() && maid.isOwnedBy(player) && task != null && !task.snapshot(maid).state().terminal();
    }
    public static JsonObject snapshot(AiMaidEntity maid) {
        MaidServerAccess.requireServerThread(maid);
        var state = new JsonObject(); state.addProperty("ok", true);
        state.addProperty("name", clipped(maid.getName().getString(), 80));
        state.addProperty("command", maid.getMaidCommandId().toString());
        maid.getBrain().getActiveNonCoreActivity().ifPresent(a -> state.addProperty("activity", BuiltInRegistries.ACTIVITY.getKey(a).toString()));
        state.add("status", component(maid.getMaidStatus(), 0));
        state.addProperty("health", maid.getHealth()); state.addProperty("max_health", maid.getMaxHealth());
        state.addProperty("dimension", maid.level().dimension().identifier().toString());
        state.addProperty("position", maid.blockPosition().toShortString());
        var equipment = new JsonObject();
        for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
            equipment.add(slot.getName(), stack(maid.getItemBySlot(slot)));
        state.add("equipment", equipment);
        var inventory = new JsonArray();
        for (int i = 0; i < maid.getInventory().getContainerSize(); i++) {
            var item = maid.getInventory().getItem(i); if (item.isEmpty()) continue;
            var row = stack(item); row.addProperty("slot", i); inventory.add(row);
        }
        state.add("inventory", inventory);
        var task = io.github.ozozorz.aimaid.entity.task.MaidTasks.current(maid);
        if (task != null) state.add("task", taskSnapshot(maid));
        return state;
    }
    public static JsonObject taskSnapshot(AiMaidEntity maid) {
        return taskSnapshot(maid, io.github.ozozorz.aimaid.entity.task.MaidTasks.current(maid));
    }
    public static JsonObject taskSnapshot(AiMaidEntity maid, io.github.ozozorz.aimaid.entity.task.MaidTask task) {
        MaidServerAccess.requireServerThread(maid);
        var data = task.snapshot(maid);
        var result = new JsonObject();
        result.addProperty("id", data.id().toString()); result.addProperty("type", data.type());
        result.addProperty("state", data.state().name().toLowerCase(Locale.ROOT)); result.addProperty("step", data.step());
        result.addProperty("reason", data.reason());
        var progress = new JsonObject(); data.progress().forEach(progress::addProperty); result.add("progress", progress);
        if (data.destination() != null) {
            result.addProperty("destination", data.destination().pos().toShortString());
            result.addProperty("dimension", data.destination().dimension().identifier().toString());
        }
        var actions = new JsonArray();
        for (var action : task.actions(maid)) {
            var row = new JsonObject(); row.addProperty("index", action.index()); row.addProperty("action", action.action());
            row.addProperty("state", action.state()); row.addProperty("count", action.count()); row.addProperty("reason", action.reason());
            if (!action.item().isEmpty()) row.addProperty("item", action.item());
            if (action.slot() >= 0) row.addProperty("slot", action.slot());
            if (action.destination() != null) {
                row.addProperty("destination", action.destination().pos().toShortString());
                row.addProperty("dimension", action.destination().dimension().identifier().toString());
            }
            var metrics = new JsonObject(); action.progress().forEach(metrics::addProperty); row.add("progress", metrics); actions.add(row);
        }
        if (!actions.isEmpty()) result.add("steps", actions);
        if (task instanceof io.github.ozozorz.aimaid.entity.task.MaidPlanTask plan) result.addProperty("request_id", plan.requestId());
        if (task instanceof io.github.ozozorz.aimaid.entity.task.MaidGoalTask goal) {
            result.addProperty("goal", goal.goal()); result.addProperty("conclusion", goal.conclusion());
            result.addProperty("completion_source", "planner_with_server_action_receipts");
            result.addProperty("execution_radius", goal.limits().radius());
        }
        return result;
    }
    public static boolean mutatesWork(String name) { return Set.of("select_command", "start_wheat_delivery", "submit_plan", "start_goal", "cancel_task").contains(name); }
    public static boolean changedWork(JsonObject result) {
        return result.has("selection") || result.has("task_started") && result.get("task_started").getAsBoolean()
                || result.has("task_cancelled") && result.get("task_cancelled").getAsBoolean();
    }
    private static JsonObject stack(ItemStack item) {
        var result = new JsonObject(); result.addProperty("id", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        result.addProperty("count", item.getCount()); result.addProperty("damage", item.getDamageValue());
        result.addProperty("max_damage", item.getMaxDamage()); return result;
    }
    private static JsonObject component(Component text, int depth) {
        var result = new JsonObject(); result.addProperty("text", clipped(text.getString(), 512));
        if (depth > 4) return result;
        if (text.getContents() instanceof TranslatableContents translatable) {
            result.addProperty("translation_key", translatable.getKey());
            var args = new JsonArray();
            for (var arg : translatable.getArgs()) {
                if (arg instanceof Component c) args.add(component(c, depth + 1)); else args.add(clipped(String.valueOf(arg), 128));
            }
            result.add("arguments", args);
        }
        var siblings = new JsonArray(); text.getSiblings().stream().limit(8).forEach(c -> siblings.add(component(c, depth + 1)));
        if (!siblings.isEmpty()) result.add("siblings", siblings);
        return result;
    }
    public static String clipped(String text, int max) { return text.length() > max ? text.substring(0, max) : text; }
}
