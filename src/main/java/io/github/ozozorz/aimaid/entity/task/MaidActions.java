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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/** Addons register bounded actions at initialization; the LLM addon consumes this catalog without linking them. */
public final class MaidActions {
    public static final int MAX_STEPS = 8, MAX_COUNT = 64, DESTINATION_RANGE = 32;
    public static final Identifier DEPOSIT_WHEAT = AIMaid.id("deposit_wheat"), RETURN_TO_OWNER = AIMaid.id("return_to_owner");
    public static final Identifier DEPOSIT_ITEMS = AIMaid.id("deposit_items"), MOVE_TO = AIMaid.id("move_to"), EQUIP_TOOL = AIMaid.id("equip_tool");
    public record ChestTarget(GlobalPos position, BlockEntity entity) {}
    public record Request(Identifier action, int count, ChestTarget target, Identifier item, int slot, MaidObservations.Target focus) {
        public Request(Identifier action, int count, ChestTarget target) { this(action, count, target, null, -1, null); }
    }
    public record Definition(Identifier id, Component description, boolean count, boolean chest, boolean item, boolean slot,
                             MaidObservations.Kind focus, BiFunction<AiMaidEntity, Request, MaidAction> factory) {
        public Definition(Identifier id, Component description, boolean count, boolean chest, BiFunction<AiMaidEntity, Request, MaidAction> factory) {
            this(id, description, count, chest, false, false, MaidObservations.Kind.NONE, factory);
        }
    }
    private static final Map<Identifier, Definition> TYPES = new LinkedHashMap<>();
    static {
        register(new Definition(DEPOSIT_WHEAT, Component.translatableWithFallback("action.ai-maid.deposit_wheat.description", "Walk to an observed chest and deposit exactly count wheat already in inventory (1..64). Does not collect. Chest must be within 32 blocks of the maid when the plan is submitted."), true, true,
                (maid, request) -> new DepositItemsAction(request.count(), request.target(), Items.WHEAT)));
        register(new Definition(RETURN_TO_OWNER, Component.translatableWithFallback("action.ai-maid.return_to_owner.description", "Walk to the owner current position, then wait. No parameters; never teleports."), false, false,
                (maid, request) -> new ReturnToOwnerAction()));
        register(new Definition(DEPOSIT_ITEMS, Component.translatableWithFallback("action.ai-maid.deposit_items.description", "Walk to the observed chest and transfer exactly count (1..64) of item already held in inventory. Reports actual delivered quantity even on partial failure. Does not collect."), true, true, true, false,
                MaidObservations.Kind.NONE, (maid, request) -> new DepositItemsAction(request.count(), request.target(), BuiltInRegistries.ITEM.getValue(request.item()))));
        register(new Definition(MOVE_TO, Component.translatableWithFallback("action.ai-maid.move_to.description", "Walk to within interaction range of an observed position, chest, area or tree. Success means actual arrival; never teleports."), false, false, false, false,
                MaidObservations.Kind.ANY, (maid, request) -> new MoveToAction(request.focus())));
        register(new Definition(EQUIP_TOOL, Component.translatableWithFallback("action.ai-maid.equip_tool.description", "Equip the tool from inventory slot into the main hand after checking its expected item ID. Swap the previous held item into that slot."), false, false, true, true,
                MaidObservations.Kind.NONE, (maid, request) -> new EquipToolAction(request.slot(), request.item())));
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
            if (type.item() != (request.item() != null) || type.item() && (BuiltInRegistries.ITEM.getOptional(request.item()).isEmpty()
                    || BuiltInRegistries.ITEM.getValue(request.item()) == Items.AIR)) throw new IllegalArgumentException("invalid_item");
            if (type.slot() ? request.slot() < 0 || request.slot() >= maid.getInventory().getContainerSize() : request.slot() != -1)
                throw new IllegalArgumentException("invalid_slot");
            if ((type.focus() != MaidObservations.Kind.NONE) != (request.focus() != null)) throw new IllegalArgumentException("invalid_target_kind");
            if (request.focus() != null) {
                var focus = request.focus(); var pos = focus.position().pos();
                if (type.focus() != MaidObservations.Kind.ANY && type.focus() != focus.kind()
                        || focus.position().dimension() != level.dimension() || !level.hasChunkAt(pos)
                        || !level.getWorldBorder().isWithinBounds(pos) || pos.distToCenterSqr(maid.position()) > 32 * 32
                        || focus.radius() < 0 || focus.radius() > 16 || focus.blocks().size() > 128)
                    throw new IllegalArgumentException("target_unavailable");
            }
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
