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
        io.github.ozozorz.aimaid.entity.task.MaidActions.register(new io.github.ozozorz.aimaid.entity.task.MaidActions.Definition(
                id("chop_tree"), net.minecraft.network.chat.Component.translatableWithFallback("action.ai-maid-lumber.chop_tree.description", "Chop only logs in the observed natural tree snapshot with an equipped axe, then physically collect its drops. Reports broken, skipped and collected separately; inaccessible logs or missing drops are partial failure."),
                false, false, false, false, io.github.ozozorz.aimaid.entity.task.MaidObservations.Kind.TREE,
                (maid, request) -> new io.github.ozozorz.aimaid.lumber.lumber.ChopTreeAction(maid, request.focus())));
        io.github.ozozorz.aimaid.entity.task.MaidObservations.register((level, maid, radius) -> {
            var result = new java.util.ArrayList<io.github.ozozorz.aimaid.entity.task.MaidObservations.Observation>();
            var seen = new java.util.HashSet<net.minecraft.core.BlockPos>(); int checked = 0;
            for (var pos : net.minecraft.core.BlockPos.withinManhattan(maid.blockPosition(), radius, 4, radius)) {
                if (result.size() >= 8 || checked >= 32) break;
                if (seen.contains(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !level.getBlockState(pos).is(io.github.ozozorz.aimaid.lumber.lumber.LumberTreeFinder.LOGS)) continue;
                checked++;
                var tree = io.github.ozozorz.aimaid.lumber.lumber.LumberTreeFinder.collect(level, pos.immutable());
                if (tree.isEmpty()) continue;
                seen.addAll(tree.get().keySet());
                result.add(new io.github.ozozorz.aimaid.entity.task.MaidObservations.Observation(
                        new io.github.ozozorz.aimaid.entity.task.MaidObservations.Target(io.github.ozozorz.aimaid.entity.task.MaidObservations.Kind.TREE,
                                net.minecraft.core.GlobalPos.of(level.dimension(), pos.immutable()), 0, null, tree.get()),
                        java.util.Map.of("logs", tree.get().size())));
            }
            return result;
        });
    }
}
