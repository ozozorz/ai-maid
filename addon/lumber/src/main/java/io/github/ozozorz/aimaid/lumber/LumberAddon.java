package io.github.ozozorz.aimaid.lumber;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.lumber.command.LumberCommand;
import io.github.ozozorz.aimaid.lumber.ai.LumberMemories;
import io.github.ozozorz.aimaid.lumber.ai.LumberActivities;
public final class LumberAddon implements ModInitializer {
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("ai-maid-lumber", path); }
    public static final LumberCommand LUMBER = Registry.register(ModBuiltInRegistries.MAID_COMMAND, Identifier.parse("ai-maid-lumber:lumber"), new LumberCommand());
    @Override public void onInitialize() {
        LumberMemories.initialize(); LumberActivities.initialize();
        MaidCommands.registerLegacyAlias(Identifier.parse("testaddon:lumber"), LUMBER.getRegistryId());
    }
}
