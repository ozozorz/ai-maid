package io.github.ozozorz.aimaid.experiment;

import java.util.*;
import com.google.gson.*;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.task.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/** This bounded arena exists only in the development runner's dedicated void dimension. */
public final class ExperimentScene implements AutoCloseable {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.parse("aimaid-devrun:experiments"));
    public static final BlockPos A = new BlockPos(6, 81, 6), B = new BlockPos(9, 81, 6),
            SENTINEL = new BlockPos(-3, 81, 5), TREE = new BlockPos(6, 81, -3), POINT = new BlockPos(11, 81, -6);
    private static final AABB BOUNDS = new AABB(-12, 80, -12, 19, 89, 19);
    public final ServerLevel level;
    public final ServerPlayer owner;
    public final AiMaidEntity maid;
    public final ExperimentCases.Trial trial;
    public final JsonObject initial;
    private final List<ChunkPos> forced = new ArrayList<>();
    private final List<BlockPos> crops = List.of(new BlockPos(3,81,4), new BlockPos(4,81,4), new BlockPos(5,81,4));
    private final List<BlockPos> cropSoil = crops.stream().map(BlockPos::below).toList();
    private final List<BlockPos> treeBlocks = new ArrayList<>();
    public boolean disturbed;
    public int externalWheatA;
    public int externalInventoryWheat;

    public ExperimentScene(ServerLevel level, ServerPlayer owner, ExperimentCases.Trial trial) {
        if (level.dimension() != DIMENSION) throw new IllegalArgumentException("Experiment arena requires its dedicated dimension");
        this.level = level; this.owner = owner; this.trial = trial;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (level.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
            level.getChunk(x, z);
        }
        for (var entity : level.getEntitiesOfClass(Entity.class, BOUNDS, e -> !(e instanceof ServerPlayer))) entity.discard();
        for (var pos : BlockPos.betweenClosed(-12, 80, -12, 18, 88, 18)) {
            var block = pos.getY() == 80 ? Blocks.SEA_LANTERN
                    : pos.getY() <= 84 && (pos.getX() == -12 || pos.getX() == 18 || pos.getZ() == -12 || pos.getZ() == 18)
                    ? Blocks.GLASS : Blocks.AIR;
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        level.getRandom().setSeed(trial.seed());
        teleport(owner, level, .5, 81, .5);
        owner.setDeltaMovement(0, 0, 0);
        for (var pos : List.of(A, B, SENTINEL)) level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        chest(SENTINEL).setItem(0, new ItemStack(Items.DIAMOND, 7));
        maid = new AiMaidEntity(level);
        maid.setPos(2.5, 81, 2.5); maid.setTame(true, true); maid.setOwner(owner);
        maid.setInvulnerable(true); maid.setPersistenceRequired(); maid.getRandom().setSeed(trial.seed());
        maid.selectMaidCommand(MaidCommands.STAY);
        String id = trial.card().id();
        switch (id) {
            case "S1", "M3" -> put(0, Items.IRON_AXE, 1);
            case "S3", "M1", "D1" -> { put(0, Items.WHEAT, 1); put(1, Items.WHEAT_SEEDS, 8); }
            case "S4", "M4" -> put(0, Items.WHEAT, 5);
            case "M5" -> { put(0, Items.WHEAT, 3); put(1, Items.CARROT, 2); }
            case "D2", "D4", "N2", "N4" -> put(0, Items.WHEAT, 3);
            case "D3" -> { put(0, Items.WHEAT, 3); put(1, Items.WHEAT_SEEDS, 8); }
            case "N5" -> {
                for (int i = 0; i < maid.getInventory().getContainerSize(); i++) put(i, Items.STONE, 64);
                put(34, Items.WHEAT_SEEDS, 64);
            }
            default -> {}
        }
        if (id.equals("S5")) maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        if (Set.of("S5", "M3", "N3").contains(id)) tree();
        if (Set.of("S3", "M1", "D1", "D3", "N5", "M2").contains(id)) {
            var crop = (CropBlock) (id.equals("M2") ? Blocks.CARROTS : Blocks.WHEAT);
            for (var pos : crops) {
                level.setBlockAndUpdate(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
                level.setBlockAndUpdate(pos, crop.getStateForAge(7));
            }
        }
        if (id.equals("D1")) fill(A, 60);
        if (id.equals("N2")) { fill(A, 64); fill(B, 64); }
        if (id.equals("N4")) {
            for (int x = 5; x <= 7; x++) for (int z = 5; z <= 7; z++) if (x != 6 || z != 6)
                for (int y = 81; y <= 84; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        }
        if (id.equals("D5")) wall(false);
        level.addFreshEntity(maid);
        initial = snapshot();
    }
    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
    }
    private void put(int slot, Item item, int count) { maid.getInventory().setItem(slot, new ItemStack(item, count)); }
    public ChestBlockEntity chest(BlockPos pos) { return level.getBlockEntity(pos) instanceof ChestBlockEntity c ? c : null; }
    private void fill(BlockPos pos, int wheat) {
        var chest = chest(pos);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        chest.setItem(0, new ItemStack(Items.WHEAT, wheat)); chest.setChanged();
    }
    private void tree() {
        level.setBlockAndUpdate(TREE.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 3; y++) { var p = TREE.above(y); treeBlocks.add(p); level.setBlockAndUpdate(p, Blocks.OAK_LOG.defaultBlockState()); }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            level.setBlockAndUpdate(TREE.offset(x, 3, z), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1));
    }
    private void wall(boolean closed) {
        for (int z = -8; z <= 8; z++) for (int y = 81; y <= 84; y++)
            level.setBlockAndUpdate(new BlockPos(7, y, z), (!closed && Math.abs(z) <= 1 ? Blocks.AIR : Blocks.STONE).defaultBlockState());
    }
    /** Called on START_SERVER_TICK, before the NPC's next action tick/transfer. */
    public JsonObject perturb() {
        if (disturbed || !trial.card().group().equals("disturbed")) return null;
        var task = MaidTasks.current(maid);
        var active = task == null ? Optional.<MaidTask.ActionSnapshot>empty() : task.actions(maid).stream()
                .filter(a -> a.state().equals("running")).findFirst();
        boolean depositA = active.isPresent() && Set.of("ai-maid:deposit_items", "ai-maid:deposit_wheat").contains(active.get().action())
                && active.get().destination() != null && active.get().destination().pos().equals(A);
        String id = trial.card().id();
        boolean trigger = switch (id) {
            case "D1", "D2", "D3" -> depositA;
            case "D4" -> count(chest(A), Items.WHEAT) - initial.get("wheat_a").getAsInt() >= 3;
            case "D5" -> active.isPresent() && active.get().action().equals("ai-maid:move_to");
            default -> false;
        };
        if (!trigger) return null;
        var event = new JsonObject(); event.addProperty("event", "disturbance"); event.addProperty("case_id", id); event.add("before", snapshot());
        switch (id) {
            case "D1" -> { externalWheatA = 63 - count(chest(A), Items.WHEAT); fill(A, 63); }
            case "D2" -> level.setBlockAndUpdate(A, Blocks.AIR.defaultBlockState());
            case "D3" -> {
                int remaining = 2;
                for (int i = 0; i < maid.getInventory().getContainerSize() && remaining > 0; i++) {
                    var stack = maid.getInventory().getItem(i);
                    if (stack.is(Items.WHEAT)) { int n = Math.min(remaining, stack.getCount()); stack.shrink(n); remaining -= n; }
                }
                externalInventoryWheat = -(2 - remaining); maid.getInventory().setChanged();
            }
            case "D4" -> teleport(owner, level, 15.5, 81, .5);
            case "D5" -> wall(true);
            default -> throw new IllegalStateException(id);
        }
        disturbed = true; event.add("after", snapshot()); return event;
    }
    public JsonObject snapshot() {
        var result = new JsonObject();
        for (var entry : Map.of("wheat", Items.WHEAT, "carrot", Items.CARROT, "oak", Items.OAK_LOG).entrySet()) {
            result.addProperty(entry.getKey() + "_a", count(chest(A), entry.getValue()));
            result.addProperty(entry.getKey() + "_b", count(chest(B), entry.getValue()));
            result.addProperty(entry.getKey() + "_held", count(maid.getInventory(), entry.getValue()));
        }
        result.add("inventory", contents(maid.getInventory())); result.add("chest_a", contents(chest(A)));
        result.add("chest_b", contents(chest(B))); result.add("sentinel", contents(chest(SENTINEL)));
        result.addProperty("mainhand", BuiltInRegistries.ITEM.getKey(maid.getMainHandItem().getItem()).toString());
        result.addProperty("mainhand_count", maid.getMainHandItem().getCount());
        result.addProperty("mainhand_damage", maid.getMainHandItem().getDamageValue());
        result.addProperty("logs_remaining", treeBlocks.stream().filter(p -> level.getBlockState(p).is(Blocks.OAK_LOG)).count());
        result.addProperty("owner_distance_squared", maid.distanceToSqr(owner));
        result.addProperty("point_distance_squared", POINT.distToCenterSqr(maid.position()));
        result.addProperty("same_dimension", maid.level() == owner.level());
        result.addProperty("maid_alive", maid.isAlive() && !maid.isRemoved());
        result.addProperty("owner_alive", owner.isAlive() && !owner.isRemoved());
        result.addProperty("external_wheat_a", externalWheatA);
        result.addProperty("external_inventory_wheat", externalInventoryWheat);
        result.addProperty("disturbance_triggered", disturbed);
        result.add("maid_position", position(maid)); result.add("owner_position", position(owner));
        var plants = new JsonArray(); for (var p : crops) plants.add(level.getBlockState(p).toString()); result.add("crops", plants);
        var soil = new JsonArray(); for (var p : cropSoil) soil.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString()); result.add("soil", soil);
        return result;
    }
    private static JsonArray position(Entity e) { var a = new JsonArray(); a.add(e.getX()); a.add(e.getY()); a.add(e.getZ()); return a; }
    public static int count(Container container, Item item) {
        int result = 0;
        if (container != null) for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i); if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    public static JsonObject contents(Container container) {
        var result = new JsonObject();
        if (container != null) for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i); if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            result.addProperty(id, (result.has(id) ? result.get(id).getAsInt() : 0) + stack.getCount());
        }
        return result;
    }
    @Override public void close() {
        MaidTasks.cancel(maid, "experiment_closed"); maid.discard();
        for (var chunk : forced) level.setChunkForced(chunk.x(), chunk.z(), false);
    }
}
