package io.github.ozozorz.aimaid.experiment;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.llm.service.ExecutionPolicy;

/** Versioned task cards, not model answers. Both policies receive the exact same instruction. */
public final class ExperimentCases {
    public static final String VERSION = "maid-tasks-v1";
    public record Card(String id, String group, String instruction, String obstacle) {
        public boolean feasible() { return !group.equals("blocked"); }
    }
    public record Trial(Card card, int repeat, ExecutionPolicy policy, long seed) {
        public Trial {
            Objects.requireNonNull(card);
            if (repeat < 1 || policy == null || policy == ExecutionPolicy.AUTO)
                throw new IllegalArgumentException("Experiments require an explicit A/B policy and positive repeat");
        }
        public String mode() { return policy == ExecutionPolicy.FIXED_PLAN ? "A" : "B"; }
        public String id() { return card.id() + "-" + repeat + "-" + mode(); }
    }
    private static final String A = "(6,81,6)", B = "(9,81,6)", TREE = "(6,81,-3)", POINT = "(11,81,-6)";
    public static final List<Card> ALL = List.of(
            new Card("S1", "single", "把背包里的铁斧装备到主手。", ""),
            new Card("S2", "single", "走到位置" + POINT + "附近。", ""),
            new Card("S3", "single", "准备共3个小麦，已有的也算，放在你的背包里。", ""),
            new Card("S4", "single", "把背包中的3个小麦放入箱子" + A + "，其余小麦留在背包。", ""),
            new Card("S5", "single", "用已经装备的斧头砍下" + TREE + "处的小树，并收集它的全部原木。", ""),
            new Card("M1", "multi", "准备共3个小麦，已有的也算，放入箱子" + A + "，然后回来。", ""),
            new Card("M2", "multi", "采收胡萝卜，将其中3根放入箱子" + A + "，然后回来。多余胡萝卜留在背包。", ""),
            new Card("M3", "multi", "装备背包里的铁斧，砍下" + TREE + "处的小树并收集全部3个原木，把这3个原木放入箱子" + A + "，然后回来。", ""),
            new Card("M4", "multi", "把背包里的5个小麦分装：2个放入箱子" + A + "，3个放入箱子" + B + "，然后回来。", ""),
            new Card("M5", "multi", "把背包中的3个小麦放入箱子" + A + "，2根胡萝卜放入箱子" + B + "，然后回来。", ""),
            new Card("D1", "disturbed", "准备共3个小麦，已有的也算，先放入箱子" + A + "；装不下的剩余小麦可以放入备用箱子" + B + "，然后回来。", ""),
            new Card("D2", "disturbed", "把背包中的3个小麦放入箱子" + A + "；如果这个箱子不可用了，就改放备用箱子" + B + "，然后回来。", ""),
            new Card("D3", "disturbed", "准备共3个小麦放入箱子" + A + "，如果背包不足可以从附近成熟小麦补足，然后回来。", ""),
            new Card("D4", "disturbed", "把背包中的3个小麦放入箱子" + A + "，然后回到我身边。", ""),
            new Card("D5", "disturbed", "走到位置" + POINT + "附近。如果直达路线被阻挡，可以依次经过绕行点(5,81,-10)、(10,81,-10)再去目标。", ""),
            new Card("N1", "blocked", "准备共3个小麦，放入箱子" + A + "，然后回来。不等待作物生长。", "insufficient_wheat"),
            new Card("N2", "blocked", "把背包中的3个小麦放入箱子" + A + "，装不下的可以放入备用箱子" + B + "，然后回来。", "chest_full"),
            new Card("N3", "blocked", "砍下" + TREE + "处的小树并收集它的全部3个原木。", "missing_axe"),
            new Card("N4", "blocked", "把背包中的3个小麦放入箱子" + A + "，然后回来。", "chest_unreachable"),
            new Card("N5", "blocked", "采收小麦，让背包中有3个小麦。不要存放、丢弃或破坏已有物品。", "inventory_full")
    );
    public static Card get(String id) {
        return ALL.stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown case: " + id));
    }
    public static String instruction(Card card) {
        return card.instruction() + " 只使用指令中指定的箱子；不能完成时说明实际障碍并停止。";
    }
    public static List<Trial> schedule(List<Card> cards, int repeats, long seed, ExecutionPolicy singleMode) {
        if (cards.isEmpty() || repeats < 1 || repeats > 20) throw new IllegalArgumentException("repeats must be 1..20");
        var random = new Random(seed);
        record Pair(Card card, int repeat) {}
        var pairs = new ArrayList<Pair>();
        for (var card : cards) for (int r = 1; r <= repeats; r++) pairs.add(new Pair(card, r));
        Collections.shuffle(pairs, random);
        var result = new ArrayList<Trial>();
        for (var pair : pairs) {
            long sceneSeed = random.nextLong();
            var modes = singleMode == null
                    ? new ArrayList<>(List.of(ExecutionPolicy.FIXED_PLAN, ExecutionPolicy.STEPWISE_GOAL))
                    : new ArrayList<>(List.of(singleMode));
            Collections.shuffle(modes, random);
            for (var mode : modes) result.add(new Trial(pair.card(), pair.repeat(), mode, sceneSeed));
        }
        return List.copyOf(result);
    }
    public static JsonArray cards() {
        var result = new JsonArray();
        for (var card : ALL) {
            var row = new JsonObject();
            row.addProperty("id", card.id()); row.addProperty("group", card.group());
            row.addProperty("instruction", instruction(card)); row.addProperty("feasible", card.feasible());
            row.addProperty("expected_obstacle", card.obstacle()); result.add(row);
        }
        return result;
    }
    private ExperimentCases() {}
}
