package io.github.ozozorz.aimaid.feeding;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;

public final class FeedingAddon implements ModInitializer {
    public static final String ID = "ai-maid-feeding";
    public static final Activity ACTIVITY = Registry.register(BuiltInRegistries.ACTIVITY, id("feed"), new Activity(id("feed").toString()));
    public static final FeedingCommand COMMAND = Registry.register(ModBuiltInRegistries.MAID_COMMAND, id("feed"), new FeedingCommand());
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    @Override public void onInitialize() { FeedingData.initialize(); }
}
