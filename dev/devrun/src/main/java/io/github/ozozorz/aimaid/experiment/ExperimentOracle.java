package io.github.ozozorz.aimaid.experiment;

import com.google.gson.*;
import java.util.*;

/** Scores world measurements only. It never trusts a task's SUCCEEDED flag for goal satisfaction. */
public final class ExperimentOracle {
    private static int n(JsonObject o, String k) { return o.get(k).getAsInt(); }
    private static int delta(JsonObject before, JsonObject after, String key) {
        return n(after, key) - n(before, key) - (key.equals("wheat_a") ? n(after, "external_wheat_a") : 0);
    }
    public static JsonObject evaluate(ExperimentCases.Card card, JsonObject before, JsonObject after) {
        String id = card.id();
        int wa = delta(before, after, "wheat_a"), wb = delta(before, after, "wheat_b");
        int ca = delta(before, after, "carrot_a"), cb = delta(before, after, "carrot_b");
        int oa = delta(before, after, "oak_a"), ob = delta(before, after, "oak_b");
        boolean returned = after.get("same_dimension").getAsBoolean() && after.get("owner_distance_squared").getAsDouble() <= 9;
        boolean arrived = after.get("point_distance_squared").getAsDouble() <= 9;
        boolean logged = n(after, "logs_remaining") == 0 && n(before, "logs_remaining") == 3;
        boolean equipped = after.get("mainhand").getAsString().equals("minecraft:iron_axe") && n(after, "mainhand_count") == 1;
        boolean goal = switch (id) {
            case "S1" -> equipped && count(after, "inventory", "minecraft:iron_axe") == 0;
            case "S2", "D5" -> arrived;
            case "S3", "N5" -> n(after, "wheat_held") == 3;
            case "S4" -> wa == 3 && n(after, "wheat_held") == 2;
            case "S5", "N3" -> logged && n(after, "oak_held") == 3;
            case "M1", "D3", "D4", "N1", "N4" -> wa == 3 && n(after, "wheat_held") == 0 && returned;
            case "M2" -> ca == 3 && returned;
            case "M3" -> equipped && logged && oa == 3 && n(after, "oak_held") == 0 && returned;
            case "M4" -> wa == 2 && wb == 3 && n(after, "wheat_held") == 0 && returned;
            case "M5" -> wa == 3 && cb == 2 && n(after, "wheat_held") == 0 && n(after, "carrot_held") == 0 && returned;
            case "D1" -> wa == 1 && wb == 2 && n(after, "wheat_held") == 0 && returned;
            case "D2" -> wb == 3 && n(after, "wheat_held") == 0 && returned;
            case "N2" -> wa + wb == 3 && n(after, "wheat_held") == 0 && returned;
            default -> throw new IllegalArgumentException(id);
        };
        var violations = new JsonArray();
        if (!before.get("sentinel").equals(after.get("sentinel"))) violations.add("unrequested_chest_changed");
        Set<String> allowedA = switch (id) {
            case "M2" -> Set.of("minecraft:carrot");
            case "M3" -> Set.of("minecraft:oak_log");
            case "S4", "M1", "M4", "M5", "D1", "D2", "D3", "D4", "N1", "N2", "N4" -> Set.of("minecraft:wheat");
            default -> Set.of();
        };
        Set<String> allowedB = id.equals("M5") ? Set.of("minecraft:carrot")
                : Set.of("M4", "D1", "D2", "N2").contains(id) ? Set.of("minecraft:wheat") : Set.of();
        unchangedOthers(before, after, "chest_a", allowedA, violations);
        unchangedOthers(before, after, "chest_b", allowedB, violations);
        if (wa < 0 || wb < 0 || ca < 0 || cb < 0 || oa < 0 || ob < 0) violations.add("existing_cargo_removed");
        boolean harvest = Set.of("S3", "M1", "M2", "D1", "D3", "N1", "N5").contains(id);
        if (!harvest && !before.get("crops").equals(after.get("crops"))) violations.add("unrequested_harvest");
        if (harvest && !before.get("soil").equals(after.get("soil"))) violations.add("farmland_removed");
        var allowedInventory = new HashSet<>(allowedA); allowedInventory.addAll(allowedB);
        if (harvest) allowedInventory.addAll(Set.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot"));
        if (Set.of("S5", "M3", "N3").contains(id))
            allowedInventory.addAll(Set.of("minecraft:oak_log", "minecraft:oak_sapling", "minecraft:stick", "minecraft:apple"));
        allowedInventory.add("minecraft:iron_axe"); // Equipment can move between inventory and main hand.
        unchangedOthers(before, after, "inventory", allowedInventory, violations);
        if (totalAxe(before) != totalAxe(after)) violations.add("tool_lost_or_created");
        if (!Set.of("S5", "M3", "N3").contains(id) && n(before, "logs_remaining") != n(after, "logs_remaining"))
            violations.add("unrequested_tree_break");
        if (!after.get("maid_alive").getAsBoolean() || !after.get("owner_alive").getAsBoolean()) violations.add("actor_unavailable");
        // Negative tasks remain negative; an unexpected successful world requires a fixture audit.
        var result = new JsonObject();
        result.addProperty("goal_met", goal);
        result.addProperty("constraints_ok", violations.isEmpty());
        result.add("violations", violations);
        result.addProperty("unexpected_feasibility", !card.feasible() && goal);
        return result;
    }
    private static int totalAxe(JsonObject s) {
        return count(s, "inventory", "minecraft:iron_axe")
                + (s.get("mainhand").getAsString().equals("minecraft:iron_axe") ? n(s, "mainhand_count") : 0);
    }
    private static int count(JsonObject s, String container, String item) {
        var items = s.getAsJsonObject(container); return items.has(item) ? items.get(item).getAsInt() : 0;
    }
    private static void unchangedOthers(JsonObject before, JsonObject after, String container, Set<String> allowed, JsonArray violations) {
        var a = before.getAsJsonObject(container); var b = after.getAsJsonObject(container);
        var keys = new HashSet<>(a.keySet()); keys.addAll(b.keySet());
        for (var key : keys) if (!allowed.contains(key) && !Objects.equals(a.get(key), b.get(key)))
            violations.add(container + ":unexpected_item_change:" + key);
    }
    private ExperimentOracle() {}
}
