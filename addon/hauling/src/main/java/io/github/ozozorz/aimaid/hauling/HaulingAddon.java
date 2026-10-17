package io.github.ozozorz.aimaid.hauling;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;

public final class HaulingAddon implements ModInitializer {
    public static final String ID = "ai-maid-hauling";
    public static final Activity ACTIVITY = Registry.register(BuiltInRegistries.ACTIVITY, id("haul"), new Activity(id("haul").toString()));
    public static final HaulingCommand COMMAND = Registry.register(ModBuiltInRegistries.MAID_COMMAND, id("haul"), new HaulingCommand());
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    @Override public void onInitialize() { HaulingData.initialize(); HaulingCargo.initialize(); }
}
