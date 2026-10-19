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
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    @Override public void onInitialize() {
        FarmingData.initialize();
        io.github.ozozorz.aimaid.entity.task.MaidActions.register(new io.github.ozozorz.aimaid.entity.task.MaidActions.Definition(
                COLLECT_WHEAT, net.minecraft.network.chat.Component.translatable("action.ai-maid-farming.collect_wheat.description"),
                true, false, (maid, request) -> new CollectWheatAction(maid, request.count())));
    }
}
