package io.github.ozozorz.aimaid.farming;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.task.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Compatibility preset: the original command now uses the same independent actions as LLM plans. */
public final class WheatDeliveryTask extends MaidPlanTask {
    public static final int MAX_COUNT = MaidActions.MAX_COUNT, SEARCH_RADIUS = CollectWheatAction.SEARCH_RADIUS,
            DESTINATION_RANGE = MaidActions.DESTINATION_RANGE;
    private final int requested;
    private final BlockPos chest;
    WheatDeliveryTask(AiMaidEntity maid, int count, BlockPos chest) {
        super(maid, UUID.randomUUID().toString(), List.of(
                new MaidActions.Request(FarmingAddon.COLLECT_WHEAT, count, null),
                new MaidActions.Request(MaidActions.DEPOSIT_WHEAT, count, new MaidActions.ChestTarget(
                        GlobalPos.of(maid.level().dimension(), chest.immutable()), maid.level().getBlockEntity(chest))),
                new MaidActions.Request(MaidActions.RETURN_TO_OWNER, 0, null)));
        requested = count; this.chest = chest.immutable();
    }
    public static int wheat(AiMaidEntity maid) { return CollectWheatAction.wheat(maid); }
    public static List<BlockPos> matureWheat(ServerLevel level, BlockPos center) { return CollectWheatAction.matureWheat(level, center); }
    public boolean sameRequest(int count, BlockPos chest) { return requested == count && this.chest.equals(chest); }
    @Override protected String type() { return FarmingAddon.id("deliver_wheat").toString(); }
    @Override protected String step() {
        return switch (super.step()) {
            case "ai-maid-farming:collect_wheat" -> "collect";
            case "ai-maid:deposit_wheat" -> "deposit";
            default -> "return";
        };
    }
    @Override public Component status(AiMaidEntity maid) {
        var data = snapshot(maid);
        return Component.translatable("status.ai-maid-farming.delivery.progress",
                Component.translatable("status.ai-maid-farming.delivery.state." + data.state().name().toLowerCase(Locale.ROOT)),
                Component.translatable("status.ai-maid-farming.delivery.step." + data.step()),
                wheat(maid), requested, data.progress().get("delivered"), requested,
                data.reason().isEmpty() ? Component.empty() : Component.translatable("status.ai-maid-farming.delivery.reason." + data.reason()));
    }
}
