package io.github.ozozorz.testaddon;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidRangedAttack;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** 专用服务器上检查动作生命周期与真实业务连接，不加载任何客户端动画类。 */
public class AnimationGameTests {
    private AiMaidEntity maid(GameTestHelper h, float z) {
        for (int x = 0; x < 8; x++) for (int bz = 0; bz < 8; bz++) h.setBlock(x, 0, bz, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 2.5F, 1, z);
        maid.setTame(true, true);
        maid.setNoAi(true);
        maid.selectMaidCommand(MaidCommands.STAY);
        return maid;
    }

    @GameTest(maxTicks = 30)
    public void nativeSwingAdvancesAndFinishes(GameTestHelper h) {
        var maid = maid(h, 3.5F);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        h.runAfterDelay(3, () -> h.assertTrue(maid.getAttackAnim(1) > 0, "TamableAnimal must explicitly advance the humanoid swing clock"));
        h.runAfterDelay(10, () -> {
            h.assertTrue(!maid.swinging && maid.getAttackAnim(1) == 0, "One swing must return to idle without another action");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 30)
    public void replayHasNewTokenAndOldStopCannotCancelIt(GameTestHelper h) {
        var maid = maid(h, 3.5F);
        int old = maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 20);
        int current = maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 8);
        maid.stopAnimationAction(TestAddonAnimationIds.LUMBER, old);
        h.assertTrue(old != current && maid.getAnimationAction().isActive(h.getLevel().getGameTime()),
                "An old behavior must not stop the new playback, even when both start on the same tick");
        h.runAfterDelay(12, () -> {
            h.assertTrue(maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "A forgotten stop must expire on server ticks");
            h.succeed();
        });
    }

    @GameTest
    public void commandDeathAndUnloadClearPlayback(GameTestHelper h) {
        var maid = maid(h, 3.5F);
        maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
        maid.selectMaidCommand(MaidCommands.STAY);
        h.assertTrue(maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "Re-selecting the same command must stop its old action");
        maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
        maid.hurtServer(h.getLevel(), h.getLevel().damageSources().fall(), 1000);
        h.assertTrue(!maid.isAlive() && maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "Death must clear playback");
        var unloading = maid(h, 5.5F);
        unloading.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
        unloading.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(unloading.getAnimationAction().id().equals(MaidAnimationAction.NONE), "Unload must clear playback");
        h.succeed();
    }

    @GameTest
    public void loadingInventoryDoesNotRestoreAnOldAction(GameTestHelper h) {
        var maid = maid(h, 3.5F);
        maid.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 3));
        maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        maid.saveWithoutId(output);
        var restored = new AiMaidEntity(h.getLevel());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(restored.getInventory().countMatching(s -> s.is(Items.DIAMOND)) == 3
                && restored.getAnimationAction().id().equals(MaidAnimationAction.NONE), "Loading must restore gameplay data without resuming a cosmetic task");
        h.succeed();
    }

    @GameTest
    public void recoilStartsOnlyAfterARealShot(GameTestHelper h) {
        var maid = maid(h, 3.5F);
        var target = h.spawn(EntityTypes.ZOMBIE, 6.5F, 1, 3.5F);
        target.setNoAi(true);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        h.assertTrue(!MaidRangedAttack.shootBow(h.getLevel(), maid, target, 20)
                && maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "No ammunition must not produce a release animation");
        maid.getInventory().setItem(0, new ItemStack(Items.ARROW, 2));
        h.assertTrue(MaidRangedAttack.shootBow(h.getLevel(), maid, target, 20)
                && maid.getAnimationAction().id().equals(MaidAnimationAction.BOW_RELEASE)
                && maid.getInventory().getItem(0).getCount() == 1 && maid.getMainHandItem().getDamageValue() == 1,
                "Bow release must accompany the actual ammo/durability transaction");
        maid.selectMaidCommand(MaidCommands.STAY);
        var crossbow = new ItemStack(Items.CROSSBOW);
        maid.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        h.assertTrue(!MaidRangedAttack.shootCrossbow(h.getLevel(), maid, target)
                && maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "An empty crossbow must not recoil");
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        h.assertTrue(MaidRangedAttack.shootCrossbow(h.getLevel(), maid, target)
                && maid.getAnimationAction().id().equals(MaidAnimationAction.CROSSBOW_FIRE)
                && crossbow.getDamageValue() == 1, "Loaded crossbow must fire and start recoil");
        h.succeed();
    }

    @GameTest(maxTicks = 100)
    public void cancellingRealMiningClearsItsAnimation(GameTestHelper h) {
        var maid = maid(h, 4.5F);
        maid.setNoAi(false);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        maid.selectMaidCommand(TestAddonCommands.LUMBER);
        maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
        h.runAfterDelay(10, () -> {
            h.assertTrue(maid.getAnimationAction().id().equals(TestAddonAnimationIds.LUMBER), "Real mining must start the Addon animation");
            maid.selectMaidCommand(MaidCommands.STAY);
        });
        h.runAfterDelay(65, () -> {
            h.assertTrue(maid.getAnimationAction().id().equals(MaidAnimationAction.NONE), "Cancel must stop the looping animation");
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
            h.assertTrue(maid.getMainHandItem().getDamageValue() == 0, "Cosmetic cleanup must not finish mining");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void eachMaidOwnsHerOwnTimeline(GameTestHelper h) {
        var first = maid(h, 2.5F);
        var second = maid(h, 5.5F);
        int token = first.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
        h.runAfterDelay(5, () -> second.playAnimationAction(TestAddonAnimationIds.LUMBER, 100));
        h.runAfterDelay(10, () -> {
            h.assertTrue(second.getAnimationAction().startedAt() > first.getAnimationAction().startedAt(), "Two maids must have independent action clocks");
            first.stopAnimationAction(TestAddonAnimationIds.LUMBER, token);
            h.assertTrue(second.getAnimationAction().isActive(h.getLevel().getGameTime()), "Stopping one maid must not stop another");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 45)
    public void cosmeticPlaybackCannotBreakBlocksOrCreateLoot(GameTestHelper h) {
        var maid = maid(h, 4.5F);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        h.setBlock(4, 1, 4, Blocks.OAK_LOG);
        maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 20);
        h.runAfterDelay(30, () -> {
            h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(4, 1, 4));
            h.assertTrue(maid.getMainHandItem().getDamageValue() == 0
                    && h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).isEmpty(),
                    "Playing a clip alone must have no gameplay side effects");
            h.succeed();
        });
    }
}
