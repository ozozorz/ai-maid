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
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    @Override public void onInitialize() { FarmingData.initialize(); }
}
