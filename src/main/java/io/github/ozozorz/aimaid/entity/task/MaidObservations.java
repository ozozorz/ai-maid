package io.github.ozozorz.aimaid.entity.task;

import java.util.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import io.github.ozozorz.aimaid.entity.inventory.MaidContainers;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded world snapshots; tokens/owner authorization belong to the calling adapter. */
public final class MaidObservations {
    public enum Kind { NONE, ANY, POSITION, CHEST, AREA, TREE }
    public record Target(Kind kind, GlobalPos position, int radius, BlockEntity entity, Map<BlockPos, BlockState> blocks) {
        public Target { blocks = Map.copyOf(blocks); }
    }
    public record Observation(Target target, Map<String, Integer> facts) {
        public Observation { facts = Map.copyOf(facts); }
    }
    @FunctionalInterface public interface Provider {
        List<Observation> observe(ServerLevel level, AiMaidEntity maid, int radius);
    }
    private static final List<Provider> PROVIDERS = new ArrayList<>();
    private MaidObservations() {}
    public static void register(Provider provider) { PROVIDERS.add(Objects.requireNonNull(provider)); }
    public static List<Observation> nearby(AiMaidEntity maid, int radius) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (radius < 2 || radius > 16) throw new IllegalArgumentException("observation_radius");
        var result = new ArrayList<Observation>(); var center = maid.blockPosition();
        for (var pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            if (result.size() >= 24) break;
            if (MaidContainers.resolveChest(level, pos) != null)
                result.add(new Observation(new Target(Kind.CHEST, GlobalPos.of(level.dimension(), pos.immutable()), 0,
                        level.getBlockEntity(pos), Map.of()), Map.of()));
        }
        for (var provider : PROVIDERS) {
            for (var observation : provider.observe(level, maid, radius)) {
                if (result.size() >= 48) return List.copyOf(result);
                result.add(observation);
            }
        }
        return List.copyOf(result);
    }
}
