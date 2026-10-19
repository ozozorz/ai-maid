package io.github.ozozorz.aimaid;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ozozorz.aimaid.command.MaidTestScene;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

public class TestSceneGameTests {
    private static final String STRUCTURE = "ai-maid-core-tests:test_scene";
    private BlockPos floor(GameTestHelper h) { return h.absolutePos(new BlockPos(17, 3, 17)); }

    @GameTest(structure = STRUCTURE)
    public void sceneContainsOwnedIdleMaidAndUsableWorkFixtures(GameTestHelper h) throws CommandSyntaxException {
        var owner = h.makeMockPlayer(GameType.CREATIVE);
        var origin = floor(h);
        var maid = MaidTestScene.create(h.getLevel(), origin, owner);
        h.assertTrue(maid.isOwnedBy(owner) && maid.getMaidCommand() == MaidCommands.STAY && !maid.isNoAi(), "Maid is not owned/idle/active");
        h.assertTrue(maid.getInventory().countMatching(s -> s.is(Items.IRON_AXE)) == 1
                && maid.getInventory().countMatching(s -> s.is(Items.WHEAT)) == 32, "Worker materials missing");
        var cows = h.getLevel().getEntitiesOfClass(Animal.class, new AABB(origin).inflate(15, 8, 15), a -> a.getType() == EntityTypes.COW);
        h.assertTrue(cows.size() == 2 && cows.stream().allMatch(c -> c.getAge() == 0 && c.canFallInLove() && !c.isNoAi()), "Cows cannot breed");
        var source = (Container) h.getLevel().getBlockEntity(origin.offset(6, 1, 6));
        var apples = (Container) h.getLevel().getBlockEntity(origin.offset(11, 1, 5));
        var stone = (Container) h.getLevel().getBlockEntity(origin.offset(11, 1, 10));
        h.assertTrue(source.getItem(0).getCount() == 16 && source.getItem(1).is(Items.COBBLESTONE)
                && apples.getItem(0).is(Items.APPLE) && stone.getItem(0).is(Items.COBBLESTONE), "Sorting fixtures missing");
        var crop = h.getLevel().getBlockState(origin.offset(4, 1, -11));
        h.assertTrue(crop.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).isMaxAge(crop)
                && h.getLevel().getBlockState(origin.offset(4, 1, -5)).isAir(), "Harvest/plant samples missing");
        h.assertTrue(h.getLevel().getFluidState(origin.offset(8, 0, -8)).is(Fluids.WATER), "Irrigation missing");
        h.assertTrue(h.getLevel().getBlockEntity(origin.offset(2, 1, -1)) instanceof SignBlockEntity sign
                && sign.getFrontText().getMessage(0, false).getString().length() > 0, "Instructions missing");
        h.succeed();
    }

    @GameTest(structure = STRUCTURE)
    public void occupiedAreaIsRejectedBeforeAnyBlocksOrInventoryChange(GameTestHelper h) throws CommandSyntaxException {
        var origin = floor(h); var pos = origin.offset(12, 1, 12);
        h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var chest = (Container) h.getLevel().getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        reject(h, () -> MaidTestScene.create(h.getLevel(), origin, h.makeMockPlayer(GameType.CREATIVE)));
        h.assertTrue(chest.getItem(0).getCount() == 7 && h.getLevel().getBlockState(origin).isAir(), "Rejected scene modified existing blocks/items");
        h.assertTrue(h.getLevel().getEntitiesOfClass(AiMaidEntity.class, new AABB(origin).inflate(15)).isEmpty(), "Rejected scene spawned maid");
        h.succeed();
    }

    @GameTest(structure = STRUCTURE)
    public void occupiedEntityAndRepeatedCreationAreRejected(GameTestHelper h) throws CommandSyntaxException {
        var origin = floor(h);
        var cow = h.spawn(EntityTypes.COW, 17, 4, 17);
        reject(h, () -> MaidTestScene.create(h.getLevel(), origin, h.makeMockPlayer(GameType.CREATIVE)));
        h.assertTrue(h.getLevel().getBlockState(origin).isAir() && cow.isAlive(), "Existing entity was overwritten");
        cow.discard();
        var maid = MaidTestScene.create(h.getLevel(), origin, h.makeMockPlayer(GameType.CREATIVE));
        reject(h, () -> MaidTestScene.create(h.getLevel(), origin, h.makeMockPlayer(GameType.CREATIVE)));
        h.assertTrue(maid.isAlive() && h.getLevel().getEntitiesOfClass(AiMaidEntity.class, new AABB(origin).inflate(15)).size() == 1, "Repeated generation duplicated maid");
        h.succeed();
    }

    @GameTest(structure = STRUCTURE)
    public void floorSearchPreservesHighestStructureAndChecksBuildLimit(GameTestHelper h) throws CommandSyntaxException {
        var origin = floor(h);
        var high = origin.offset(14, 7, 14);
        h.getLevel().setBlockAndUpdate(high, Blocks.GOLD_BLOCK.defaultBlockState());
        var selected = MaidTestScene.findFloor(h.getLevel(), origin);
        h.assertTrue(selected.getY() > high.getY() + 5 && h.getLevel().getBlockState(high).is(Blocks.GOLD_BLOCK), "Platform overlaps existing structure");
        var tooHigh = new BlockPos(origin.getX(), h.getLevel().getMaxY() - 2, origin.getZ());
        reject(h, () -> MaidTestScene.findFloor(h.getLevel(), tooHigh));
        reject(h, () -> MaidTestScene.create(h.getLevel(), tooHigh, h.makeMockPlayer(GameType.CREATIVE)));
        h.succeed();
    }

    @GameTest
    public void realCommandRejectsUnprivilegedPlayerAndConsole(GameTestHelper h) {
        var owner = (ServerPlayer) h.makeMockServerPlayer(GameType.CREATIVE);
        var server = h.getLevel().getServer(); var dispatcher = server.getCommands().getDispatcher();
        var base = server.createCommandSourceStack().withLevel(h.getLevel()).withPosition(h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
        reject(h, () -> dispatcher.execute("maid test-scene", base.withEntity(owner).withPermission(PermissionSet.NO_PERMISSIONS)));
        reject(h, () -> dispatcher.execute("maid test-scene", base.withPermission(PermissionSet.ALL_PERMISSIONS)));
        h.succeed();
    }

    private interface Checked { void run() throws CommandSyntaxException; }
    private void reject(GameTestHelper h, Checked action) {
        try { action.run(); } catch (CommandSyntaxException expected) { return; }
        h.fail("Unsafe test-scene action was accepted");
    }
}
