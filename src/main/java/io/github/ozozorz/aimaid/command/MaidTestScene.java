package io.github.ozozorz.aimaid.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** 只搭建原版方块和实体的手动测试夹具；工作仍由各 Addon 的真实行为执行。 */
public final class MaidTestScene {
    public static final int RADIUS = 15;
    public static final int HEIGHT = 8;
    public static final String MAID_NAME = "Test Maid";
    public static final String ENTITY_TAG = "ai-maid.test-scene";
    private final ServerLevel level;
    private final BlockPos floor;

    private MaidTestScene(ServerLevel level, BlockPos floor) {
        this.level = level;
        this.floor = floor.immutable();
    }

    public static BlockPos findFloor(ServerLevel level, BlockPos playerPos) throws CommandSyntaxException {
        var min = playerPos.offset(-RADIUS, 0, -RADIUS);
        var max = playerPos.offset(RADIUS, 0, RADIUS);
        checkColumns(level, min, max);
        int y = playerPos.getY() + 6;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                y = Math.max(y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 6);
            }
        }
        if (y + HEIGHT > level.getMaxY()) throw MaidTestSceneCommand.error("height");
        return new BlockPos(playerPos.getX(), y, playerPos.getZ());
    }

    private static void checkColumns(ServerLevel level, BlockPos min, BlockPos max) throws CommandSyntaxException {
        if (!level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max))
            throw MaidTestSceneCommand.error("border");
        // 不为测试场地同步生成远处区块。
        if (!level.hasChunksAt(min.getX(), min.getZ(), max.getX(), max.getZ()))
            throw MaidTestSceneCommand.error("chunks");
    }

    public static AiMaidEntity create(ServerLevel level, BlockPos floor, Player owner) throws CommandSyntaxException {
        var min = floor.offset(-RADIUS, -1, -RADIUS);
        var max = floor.offset(RADIUS, HEIGHT, RADIUS);
        checkColumns(level, min, max);
        if (min.getY() < level.getMinY() || max.getY() > level.getMaxY()) throw MaidTestSceneCommand.error("height");
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) throw MaidTestSceneCommand.error("occupied");
        }
        if (!level.getEntities(null, AABB.encapsulatingFullBlocks(min, max)).isEmpty())
            throw MaidTestSceneCommand.error("occupied");

        var scene = new MaidTestScene(level, floor);
        scene.platform();
        scene.lumber();
        scene.farming();
        scene.feeding();
        scene.hauling();
        scene.supplies();
        scene.sign(2, -1, "welcome");
        var maid = new AiMaidEntity(level);
        maid.snapTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 1.5, 180, 0);
        maid.setTame(true, true);
        maid.setOwner(owner);
        maid.setPersistenceRequired();
        maid.setCustomName(Component.literal(MAID_NAME));
        maid.setCustomNameVisible(true);
        maid.addTag(ENTITY_TAG);
        maid.selectMaidCommand(MaidCommands.STAY);
        ItemStack[] items = {new ItemStack(Items.IRON_AXE), new ItemStack(Items.WHEAT_SEEDS, 32),
                new ItemStack(Items.CARROT, 32), new ItemStack(Items.POTATO, 32),
                new ItemStack(Items.BEETROOT_SEEDS, 32), new ItemStack(Items.WHEAT, 32)};
        for (int i = 0; i < items.length; i++) maid.getInventory().setItem(i, items[i]);
        level.addFreshEntity(maid);
        return maid;
    }

    private void platform() {
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            put(x, -1, z, Blocks.STONE_BRICKS);
            put(x, 0, z, Math.abs(x) <= 1 || Math.abs(z) <= 1 ? Blocks.SMOOTH_STONE : Blocks.GRASS_BLOCK);
            if (x % 6 == 0 && z % 6 == 0) put(x, 0, z, Blocks.SEA_LANTERN);
            if (Math.abs(x) == RADIUS || Math.abs(z) == RADIUS) {
                put(x, 0, z, Blocks.STONE_BRICKS);
                put(x, 1, z, Blocks.OAK_FENCE);
            }
        }
        marker(-4, -3, Blocks.CONCRETE.pick(DyeColor.ORANGE));
        marker(4, -3, Blocks.CONCRETE.pick(DyeColor.LIME));
        marker(-4, 3, Blocks.CONCRETE.pick(DyeColor.YELLOW));
        marker(4, 3, Blocks.CONCRETE.pick(DyeColor.LIGHT_BLUE));
    }

    private void marker(int x, int z, Block block) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(x + dx, 0, z + dz, block);
    }

    private void lumber() {
        put(-5, 1, -5, Blocks.OAK_LOG); // 单块模式用；树模式会跳过它。
        tree(-8, -8, 3);
        tree(-12, -12, 6); // 高处原木仍需遵守真实可达距离，不授予远程砍树。
        sign(-5, -2, "lumber");
    }

    private void tree(int x, int z, int height) {
        put(x, 0, z, Blocks.DIRT);
        for (int y = 1; y <= height; y++) put(x, y, z, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1);
        for (int y = height; y <= height + 1; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (y == height && dx == 0 && dz == 0) continue;
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            put(x + dx, y, z + dz, leaves);
        }
    }

    private void farming() {
        // 顶部含水半砖提供灌溉，也避免玩家和女仆掉进水渠。
        var water = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)
                .setValue(SlabBlock.WATERLOGGED, true);
        for (int z = -12; z <= -4; z++) put(8, 0, z, water);
        CropBlock[] crops = {(CropBlock) Blocks.WHEAT, (CropBlock) Blocks.CARROTS,
                (CropBlock) Blocks.POTATOES, (CropBlock) Blocks.BEETROOTS};
        int[] rows = {4, 6, 10, 12};
        for (int i = 0; i < crops.length; i++) for (int z = -11; z <= -5; z += 2) {
            put(rows[i], 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            if (z != -5) put(rows[i], 1, z, crops[i].getStateForAge(z == -7 ? 0 : crops[i].getMaxAge()));
        }
        for (int x = 4; x <= 12; x += 4) put(x, 0, -13, Blocks.SEA_LANTERN);
        sign(5, -2, "farming");
    }

    private void feeding() {
        for (int x = -12; x <= -4; x++) for (int z = 5; z <= 11; z++) {
            if (x == -12 || x == -4 || z == 5 || z == 11) put(x, 1, z, Blocks.OAK_FENCE);
        }
        put(-8, 1, 5, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{-10, -6}) {
            var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
            if (cow == null) throw new IllegalStateException("Cannot create test cow");
            cow.snapTo(floor.getX() + x + 0.5, floor.getY() + 1, floor.getZ() + 8.5, 180, 0);
            cow.setAge(0);
            cow.setPersistenceRequired();
            cow.addTag(ENTITY_TAG);
            level.addFreshEntity(cow);
        }
        sign(-5, 2, "feeding");
    }

    private void hauling() {
        container(6, 6, Blocks.COPPER_CHEST.weathering().unaffected().defaultBlockState(),
                new ItemStack(Items.APPLE, 16), new ItemStack(Items.COBBLESTONE, 16));
        container(11, 5, Blocks.CHEST.defaultBlockState(), new ItemStack(Items.APPLE));
        container(11, 10, Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COBBLESTONE));
        sign(5, 2, "hauling");
        sign(6, 7, "source");
        sign(11, 6, "apples");
        sign(11, 11, "stone");
    }

    private void supplies() {
        // 木桶不属于分类搬运的目标箱，不会干扰测试路线。
        container(2, -3, Blocks.BARREL.defaultBlockState(), new ItemStack(Items.IRON_AXE),
                new ItemStack(Items.WHEAT_SEEDS, 64), new ItemStack(Items.CARROT, 64),
                new ItemStack(Items.POTATO, 64), new ItemStack(Items.BEETROOT_SEEDS, 64),
                new ItemStack(Items.WHEAT, 64), new ItemStack(Items.BREAD, 16));
        sign(3, -3, "supplies");
    }

    private void container(int x, int z, BlockState state, ItemStack... items) {
        put(x, 1, z, state);
        if (!(level.getBlockEntity(floor.offset(x, 1, z)) instanceof Container container))
            throw new IllegalStateException("Test container did not initialize");
        for (int i = 0; i < items.length; i++) container.setItem(i, items[i]);
        container.setChanged();
    }

    private void sign(int x, int z, String name) {
        put(x, 1, z, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(floor.offset(x, 1, z)) instanceof SignBlockEntity sign) {
            var text = new SignText().setColor(DyeColor.BLACK);
            for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.translatable("test_scene.ai-maid." + name + "." + i));
            sign.setText(text, true);
            sign.setText(text, false);
            sign.setWaxed(true);
        }
    }

    private void put(int x, int y, int z, Block block) { put(x, y, z, block.defaultBlockState()); }
    private void put(int x, int y, int z, BlockState state) { level.setBlockAndUpdate(floor.offset(x, y, z), state); }
}
