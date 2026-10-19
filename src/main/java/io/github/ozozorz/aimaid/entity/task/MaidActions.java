package io.github.ozozorz.aimaid.entity.task;

import java.util.*;
import java.util.function.BiFunction;
import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Addons register bounded actions at initialization; the LLM addon consumes this catalog without linking them. */
public final class MaidActions {
    public static final int MAX_STEPS = 8, MAX_COUNT = 64, DESTINATION_RANGE = 32;
    public static final Identifier DEPOSIT_WHEAT = AIMaid.id("deposit_wheat"), RETURN_TO_OWNER = AIMaid.id("return_to_owner");
    public record ChestTarget(GlobalPos position, BlockEntity entity) {}
    public record Request(Identifier action, int count, ChestTarget target) {}
    public record Definition(Identifier id, Component description, boolean count, boolean chest,
                             BiFunction<AiMaidEntity, Request, MaidAction> factory) {}
    private static final Map<Identifier, Definition> TYPES = new LinkedHashMap<>();
    static {
        register(new Definition(DEPOSIT_WHEAT, Component.translatable("action.ai-maid.deposit_wheat.description"), true, true,
                (maid, request) -> new DepositWheatAction(request.count(), request.target())));
        register(new Definition(RETURN_TO_OWNER, Component.translatable("action.ai-maid.return_to_owner.description"), false, false,
                (maid, request) -> new ReturnToOwnerAction()));
    }
    private MaidActions() {}
    public static void initialize() {}
    public static void register(Definition definition) {
        if (TYPES.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate maid action: " + definition.id());
    }
    public static List<Definition> definitions() { return List.copyOf(TYPES.values()); }
    public static Definition get(Identifier id) { return TYPES.get(id); }

    /** Validate every step before selecting work or creating any world effect. */
    static List<MaidAction> prepare(AiMaidEntity maid, List<Request> requests) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (requests.isEmpty() || requests.size() > MAX_STEPS) throw new IllegalArgumentException("step_count_out_of_range");
        for (var request : requests) {
            var type = get(request.action());
            if (type == null) throw new IllegalArgumentException("action_unavailable");
            if (type.count() ? request.count() < 1 || request.count() > MAX_COUNT : request.count() != 0)
                throw new IllegalArgumentException("invalid_action_count");
            if (type.chest() != (request.target() != null)) throw new IllegalArgumentException("invalid_action_target");
            if (request.target() != null) {
                var target = request.target(); var pos = target.position().pos();
                if (target.position().dimension() != level.dimension() || pos.distToCenterSqr(maid.position()) > DESTINATION_RANGE * DESTINATION_RANGE
                        || !level.hasChunkAt(pos) || target.entity() == null || level.getBlockEntity(pos) != target.entity()
                        || MaidContainers.resolveChest(level, pos) == null) throw new IllegalArgumentException("chest_changed_or_unavailable");
            }
        }
        return requests.stream().map(request -> get(request.action()).factory().apply(maid, request)).toList();
    }
}
