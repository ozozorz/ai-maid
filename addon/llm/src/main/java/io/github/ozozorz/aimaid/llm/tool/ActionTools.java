package io.github.ozozorz.aimaid.llm.tool;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import io.github.ozozorz.aimaid.entity.task.*;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;

/** Shared typed schema, bounded observations and opaque target tokens for both execution modes. */
public final class ActionTools {
    private record Observed(UUID owner, MaidObservations.Target target, long expires) {}
    private static final AttachmentType<LinkedHashMap<String, Observed>> TARGETS = AttachmentRegistry.create(Identifier.parse("ai-maid-llm:action_targets"));
    private ActionTools() {}
    public static JsonObject validationError(RuntimeException error) {
        String reason = error.getMessage();
        return ChatProtocol.error(reason != null && Set.of("goal_not_ready", "request_id_conflict", "action_budget_or_busy", "outside_goal_area",
                "observe_target_again", "target_kind_mismatch", "action_unavailable", "invalid_step_arguments", "invalid_integer", "invalid_request_id",
                "invalid_action_count", "count_out_of_range", "invalid_item", "invalid_slot", "invalid_target_kind", "target_unavailable",
                "chest_changed_or_unavailable", "unsupported_crop", "completion_requires_successful_evidence", "action_still_running").contains(reason)
                ? reason : "invalid_action_arguments");
    }
    public static void initialize() {}
    public static JsonObject actionSchema() {
        var variants = new JsonArray();
        for (var action : MaidActions.definitions()) {
            var fields = new JsonObject(); var required = new JsonArray();
            var name = MaidTools.schema("string"); var names = new JsonArray(); names.add(action.id().toString()); name.add("enum", names);
            fields.add("action", name); required.add("action");
            if (action.count()) { var count = MaidTools.schema("integer"); count.addProperty("minimum", 1); count.addProperty("maximum", 64); fields.add("count", count); required.add("count"); }
            if (action.chest() || action.focus() != MaidObservations.Kind.NONE) { fields.add("target_id", MaidTools.schema("string")); required.add("target_id"); }
            if (action.item()) { fields.add("item", MaidTools.schema("string")); required.add("item"); }
            if (action.slot()) { var slot = MaidTools.schema("integer"); slot.addProperty("minimum", 0); fields.add("slot", slot); required.add("slot"); }
            var variant = MaidTools.schema("object"); variant.add("properties", fields); variant.add("required", required); variant.addProperty("additionalProperties", false); variants.add(variant);
        }
        var schema = new JsonObject(); schema.add("oneOf", variants); return schema;
    }
    public static JsonObject requestIdSchema() {
        var id = MaidTools.schema("string"); id.addProperty("pattern", "^[a-zA-Z0-9_-]{1,64}$"); id.addProperty("maxLength", 64); return id;
    }
    public static String requestId(JsonObject args) {
        String id = ChatProtocol.string(args, "request_id", 64);
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid_request_id"); return id;
    }
    public static void observationDefinitions(JsonArray tools) {
        var coords = new JsonObject(); for (var axis : List.of("x", "y", "z")) coords.add(axis, MaidTools.schema("integer"));
        tools.add(MaidTools.function("observe_chest", "观察主人准星6格内的箱子；仅当主人明确指定坐标时传完整x/y/z。返回短期target_id。", coords, List.of()));
        tools.add(MaidTools.function("observe_position", "观察主人指定的位置：默认准星6格内指向的方块，也可传主人明确给出的x/y/z。只供步行移动，不授权破坏。", coords, List.of()));
        var props = new JsonObject(); var radius = MaidTools.schema("integer"); radius.addProperty("minimum", 2); radius.addProperty("maximum", 16); props.add("radius", radius);
        tools.add(MaidTools.function("observe_nearby", "观察女仆附近已加载的箱子、成熟作物区域和自然树，返回真实目标ID、数量、位置及可访问状态。radius默认8，最大16。能力随安装的插件变化。", props, List.of()));
    }
    public static JsonObject observe(String name, JsonObject args, AiMaidEntity maid, ServerPlayer player) {
        var result = new JsonObject(); result.addProperty("ok", true);
        if (name.equals("observe_nearby")) {
            if (!Set.of("radius").containsAll(args.keySet())) return ChatProtocol.error("unexpected_arguments");
            int radius = args.has("radius") ? integer(args, "radius") : 8;
            if (radius < 2 || radius > 16) return ChatProtocol.error("radius_out_of_range");
            var targets = new JsonArray();
            for (var observation : MaidObservations.nearby(maid, radius)) {
                if (MaidTasks.current(maid) instanceof MaidGoalTask goal && !goal.withinScope(observation.target().position())) continue;
                var row = remember(maid, player, observation.target()); var facts = new JsonObject(); observation.facts().forEach(facts::addProperty);
                row.add("facts", facts); targets.add(row);
            }
            result.add("targets", targets); result.add("maid", MaidTools.snapshot(maid)); return result;
        }
        if (!args.isEmpty() && !args.keySet().equals(Set.of("x", "y", "z"))) return ChatProtocol.error("provide_all_coordinates_or_none");
        BlockPos pos = null;
        if (!args.isEmpty()) pos = new BlockPos(integer(args, "x"), integer(args, "y"), integer(args, "z"));
        else if (player.pick(6, 1, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) pos = hit.getBlockPos();
        var level = player.level(); boolean chest = name.equals("observe_chest");
        if (pos == null || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.distToCenterSqr(maid.position()) > 32 * 32
                || chest && MaidContainers.resolveChest(level, pos) == null) return ChatProtocol.error("target_unavailable_within_32_blocks");
        var target = new MaidObservations.Target(chest ? MaidObservations.Kind.CHEST : MaidObservations.Kind.POSITION,
                GlobalPos.of(level.dimension(), pos.immutable()), 0, chest ? level.getBlockEntity(pos) : null, Map.of());
        if (MaidTasks.current(maid) instanceof MaidGoalTask goal && !goal.withinScope(target.position())) return ChatProtocol.error("outside_goal_area");
        return remember(maid, player, target);
    }
    private static JsonObject remember(AiMaidEntity maid, ServerPlayer player, MaidObservations.Target target) {
        var targets = maid.getAttachedOrCreate(TARGETS, LinkedHashMap::new); long now = maid.level().getGameTime();
        targets.values().removeIf(t -> !t.owner().equals(player.getUUID()) || now > t.expires());
        // Keep IDs stable across repeated observations of the same unchanged object/area.
        String token = targets.entrySet().stream().filter(e -> e.getValue().target().equals(target)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (token == null) { while (targets.size() >= 64) targets.remove(targets.keySet().iterator().next()); token = UUID.randomUUID().toString(); }
        targets.put(token, new Observed(player.getUUID(), target, now + 1200));
        var row = new JsonObject(); row.addProperty("ok", true); row.addProperty("target_id", token);
        row.addProperty("kind", target.kind().name().toLowerCase(Locale.ROOT)); row.addProperty("position", target.position().pos().toShortString());
        row.addProperty("dimension", target.position().dimension().identifier().toString()); row.addProperty("radius", target.radius());
        row.addProperty("distance", Math.sqrt(target.position().pos().distToCenterSqr(maid.position())));
        row.addProperty("accessible", target.kind() != MaidObservations.Kind.CHEST || MaidContainers.resolveChest(player.level(), target.position().pos()) != null);
        row.addProperty("path_reachable", io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation.chooseStand(maid, target.position().pos(), 3).isPresent());
        if (target.kind() == MaidObservations.Kind.CHEST) {
            var contents = new JsonObject(); var chest = MaidContainers.resolveChest(player.level(), target.position().pos());
            if (chest != null) for (int i = 0; i < chest.getContainerSize(); i++) {
                var stack = chest.getItem(i); if (stack.isEmpty()) continue;
                String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                contents.addProperty(item, (contents.has(item) ? contents.get(item).getAsInt() : 0) + stack.getCount());
            }
            row.add("contents", contents);
        }
        return row;
    }
    public static MaidActions.Request parse(JsonObject step, AiMaidEntity maid, ServerPlayer player) {
        var id = Identifier.tryParse(ChatProtocol.string(step, "action", 200)); var type = id == null ? null : MaidActions.get(id);
        if (type == null) throw new IllegalArgumentException("action_unavailable");
        var fields = new HashSet<>(Set.of("action"));
        if (type.count()) fields.add("count"); if (type.item()) fields.add("item"); if (type.slot()) fields.add("slot");
        if (type.chest() || type.focus() != MaidObservations.Kind.NONE) fields.add("target_id");
        if (!fields.equals(step.keySet())) throw new IllegalArgumentException("invalid_step_arguments");
        int count = type.count() ? integer(step, "count") : 0;
        if (type.count() && (count < 1 || count > 64)) throw new IllegalArgumentException("count_out_of_range");
        var item = type.item() ? Identifier.tryParse(ChatProtocol.string(step, "item", 200)) : null;
        if (type.item() && item == null) throw new IllegalArgumentException("invalid_item");
        MaidObservations.Target focus = null; MaidActions.ChestTarget chest = null;
        if (fields.contains("target_id")) {
            var targets = maid.getAttached(TARGETS); String token = ChatProtocol.string(step, "target_id", 64);
            var target = targets == null ? null : targets.get(token);
            if (target == null || !target.owner().equals(player.getUUID()) || maid.level().getGameTime() > target.expires())
                throw new IllegalArgumentException("observe_target_again");
            if (type.chest()) {
                if (target.target().kind() != MaidObservations.Kind.CHEST) throw new IllegalArgumentException("target_kind_mismatch");
                chest = new MaidActions.ChestTarget(target.target().position(), target.target().entity());
            } else focus = target.target();
        }
        return new MaidActions.Request(id, count, chest, item, type.slot() ? integer(step, "slot") : -1, focus);
    }
    public static int integer(JsonObject args, String key) {
        var value = args.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid_integer");
        return value.getAsBigDecimal().intValueExact();
    }
}
