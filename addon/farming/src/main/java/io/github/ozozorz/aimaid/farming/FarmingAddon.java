package io.github.ozozorz.aimaid.farming;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;

public final class FarmingAddon implements ModInitializer {
    public static final String ID = "ai-maid-farming";
    public static final Activity ACTIVITY = Registry.register(BuiltInRegistries.ACTIVITY, id("farm"), new Activity(id("farm").toString()));
    public static final FarmingCommand COMMAND = Registry.register(ModBuiltInRegistries.MAID_COMMAND, id("farm"), new FarmingCommand());
    public static final Activity DELIVERY_ACTIVITY = Registry.register(BuiltInRegistries.ACTIVITY, id("deliver_wheat"), new Activity(id("deliver_wheat").toString()));
    public static final WheatDeliveryCommand DELIVERY = Registry.register(ModBuiltInRegistries.MAID_COMMAND, id("deliver_wheat"), new WheatDeliveryCommand());
    public static final Identifier COLLECT_WHEAT = id("collect_wheat");
    public static final Identifier HARVEST_CROPS = id("harvest_crops");
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    @Override public void onInitialize() {
        FarmingData.initialize();
        io.github.ozozorz.aimaid.entity.task.MaidActions.register(new io.github.ozozorz.aimaid.entity.task.MaidActions.Definition(
                COLLECT_WHEAT, net.minecraft.network.chat.Component.translatableWithFallback("action.ai-maid-farming.collect_wheat.description", "Ensure inventory holds count wheat, using existing stock first and harvesting/replanting only the shortfall. Search mature wheat on farmland within 16 blocks horizontally and 2 vertically of the plan origin; do not wait for growth."),
                true, false, (maid, request) -> new CollectWheatAction(maid, request.count())));
        io.github.ozozorz.aimaid.entity.task.MaidActions.register(new io.github.ozozorz.aimaid.entity.task.MaidActions.Definition(
                HARVEST_CROPS, net.minecraft.network.chat.Component.translatableWithFallback("action.ai-maid-farming.harvest_crops.description", "Ensure inventory holds count (1..64) of item, using existing stock first. Supported items: minecraft:wheat, minecraft:carrot, minecraft:potato, minecraft:beetroot. Harvest and replant mature crops in the observed area, within 2 vertical blocks; do not wait for growth."),
                true, false, true, false, io.github.ozozorz.aimaid.entity.task.MaidObservations.Kind.AREA,
                (maid, request) -> new HarvestCropsAction(maid, request.count(), net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(request.item()),
                        request.focus().position().pos(), request.focus().radius())));
        io.github.ozozorz.aimaid.entity.task.MaidObservations.register((level, maid, radius) -> {
            var counts = new java.util.LinkedHashMap<String, Integer>();
            HarvestCropsAction.CROPS.forEach((item, crop) -> counts.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),
                    HarvestCropsAction.matureCrops(level, maid.blockPosition(), radius, crop).size()));
            if (counts.values().stream().allMatch(count -> count == 0)) return java.util.List.of();
            return java.util.List.of(new io.github.ozozorz.aimaid.entity.task.MaidObservations.Observation(
                    new io.github.ozozorz.aimaid.entity.task.MaidObservations.Target(io.github.ozozorz.aimaid.entity.task.MaidObservations.Kind.AREA,
                            net.minecraft.core.GlobalPos.of(level.dimension(), maid.blockPosition()), radius, null, java.util.Map.of()), counts));
        });
    }
}
