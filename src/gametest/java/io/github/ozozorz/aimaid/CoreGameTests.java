package io.github.ozozorz.aimaid;

import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.inventory.*;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.menu.*;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class CoreGameTests {
    private record Pair(AiMaidEntity maid, Player owner) {}
    private Pair pair(GameTestHelper h, GameType mode) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 3.5F, 1, 3.5F);
        maid.setNoAi(true);
        var owner = h.makeMockPlayer(mode);
        mode.updatePlayerAbilities(owner.getAbilities());
        owner.setPos(maid.position().add(1, 0, 0));
        maid.setTame(true, true); maid.setOwner(owner);
        return new Pair(maid, owner);
    }
    private ItemStack bound(GameTestHelper h, net.minecraft.world.item.Item item) {
        var stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        return stack;
    }
    private MaidMenu menu(Pair p) { return new MaidMenu(7, p.owner().getInventory(), p.maid()); }

    @GameTest public void workerThreadCannotMutateCommand(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid();
        boolean rejected = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { maid.selectMaidCommand(MaidCommands.STAY); return false; }
            catch (IllegalStateException expected) { return true; }
        }).join();
        h.assertTrue(rejected && maid.getMaidCommand() == MaidCommands.FOLLOW, "Worker thread mutated authoritative state"); h.succeed();
    }

    @GameTest public void feedingPreservesUseRemainder(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); p.maid().setHealth(10);
        var apple = new ItemStack(Items.APPLE);
        apple.set(DataComponents.USE_REMAINDER, new ItemStack(Items.MUSHROOM_STEW).get(DataComponents.USE_REMAINDER));
        p.owner().setItemInHand(InteractionHand.MAIN_HAND, apple);
        MaidFeeding.feed(p.maid(), p.owner(), InteractionHand.MAIN_HAND);
        h.assertTrue(p.owner().getMainHandItem().is(Items.BOWL) && p.maid().getHealth() == 14, "Use remainder lost"); h.succeed();
    }

    @GameTest(maxTicks = 40) public void independentAddonContributionsAndFrozenRegistration(GameTestHelper h) {
        AIMaid.LOGGER.info("Core addon registration fixtures enabled: {}", Boolean.getBoolean("ai-maid.testContributors"));
        h.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("testaddon"), "Core suite must be independent of TestAddon");
        if (!Boolean.getBoolean("ai-maid.testContributors")) { h.succeed(); return; }
        var one = pair(h, GameType.SURVIVAL).maid(); var two = pair(h, GameType.SURVIVAL).maid();
        one.setNoAi(false); one.selectMaidCommand(MaidCommands.STAY);
        var types = io.github.ozozorz.aimaid.entity.ai.sensing.MaidBrainSensors.getSensorTypes();
        h.assertTrue(java.util.Collections.frequency(types, CoreTestContributors.FIRST) == 1
                && java.util.Collections.frequency(types, CoreTestContributors.SECOND) == 1, "Duplicate contribution installed twice");
        boolean rejected = false;
        try { CoreTestContributors.captured.registerBrainSensor(CoreTestContributors.FIRST); }
        catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "Late sensor registration accepted");
        h.runAfterDelay(10, () -> {
            h.assertTrue(one.getBrain().getMemory(CoreTestContributors.FIRST_MEMORY).orElse(0) > 0
                    && one.getBrain().getMemory(CoreTestContributors.SECOND_MEMORY).orElse(0) > 0, "Required memory was not registered");
            h.assertTrue(two.getBrain().getMemory(CoreTestContributors.FIRST_MEMORY).isEmpty(), "Per-maid memory leaked"); h.succeed();
        });
    }

    @GameTest public void emptyArmorSlotTakesOneAndPreservesRemainder(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL);
        var pumpkins = new ItemStack(Items.CARVED_PUMPKIN, 3);
        pumpkins.set(DataComponents.CUSTOM_NAME, Component.literal("Three hats"));
        p.maid().getInventory().setItem(0, pumpkins);
        h.assertTrue(MaidItemTransfer.moveInventoryToEquipment(p.maid(), 0, EquipmentSlot.HEAD) == 1, "Armor must take one");
        h.assertTrue(pumpkins.getCount() == 2 && p.maid().getItemBySlot(EquipmentSlot.HEAD).getCount() == 1, "Wrong split");
        h.assertTrue(ItemStack.isSameItemSameComponents(pumpkins, p.maid().getItemBySlot(EquipmentSlot.HEAD)), "Components lost");
        h.succeed();
    }

    @GameTest public void partialArmorSwapIsAtomicWhenFull(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var maid = p.maid();
        for (int i = 0; i < 35; i++) maid.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        maid.getInventory().setItem(0, new ItemStack(Items.CARVED_PUMPKIN, 3));
        var helmet = new ItemStack(Items.IRON_HELMET); helmet.setDamageValue(17);
        maid.setItemSlot(EquipmentSlot.HEAD, helmet);
        h.assertTrue(!MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.HEAD), "No room for old helmet");
        h.assertTrue(maid.getItemBySlot(EquipmentSlot.HEAD) == helmet && maid.getInventory().getItem(0).getCount() == 3, "Failed swap mutated items");
        maid.getInventory().setItem(1, ItemStack.EMPTY);
        h.assertTrue(MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.HEAD), "Swap should now fit");
        h.assertTrue(maid.getInventory().getItem(0).getCount() == 2 && maid.getInventory().getItem(1).getDamageValue() == 17, "Lost remainder or durability");
        h.succeed();
    }

    @GameTest public void fullInventoryStillSwapsOneForOne(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid();
        for (int i = 0; i < 35; i++) maid.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var axe = new ItemStack(Items.IRON_AXE); var sword = new ItemStack(Items.IRON_SWORD);
        axe.setDamageValue(9); maid.getInventory().setItem(0, axe); maid.setItemSlot(EquipmentSlot.MAINHAND, sword);
        h.assertTrue(MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.MAINHAND), "Full inventory blocked exact swap");
        h.assertTrue(maid.getMainHandItem() == axe && maid.getInventory().getItem(0) == sword, "Real stack identity lost");
        h.succeed();
    }

    @GameTest public void bindingBlocksAiAndAllSurvivalMenuMoves(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var maid = p.maid();
        var helmet = bound(h, Items.IRON_HELMET); maid.setItemSlot(EquipmentSlot.HEAD, helmet);
        maid.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
        h.assertTrue(!MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.HEAD)
                && MaidItemTransfer.moveEquipmentToInventory(maid, EquipmentSlot.HEAD) == 0, "AI ignored binding");
        var menu = menu(p);
        menu.clicked(35, 0, ContainerInput.PICKUP, p.owner());
        menu.clicked(35, 0, ContainerInput.QUICK_MOVE, p.owner());
        menu.clicked(35, 0, ContainerInput.SWAP, p.owner());
        h.assertTrue(menu.getCarried().isEmpty() && maid.getItemBySlot(EquipmentSlot.HEAD) == helmet, "Menu bypassed binding");
        h.succeed();
    }

    @GameTest public void creativeOwnerCanRemoveBinding(GameTestHelper h) {
        var p = pair(h, GameType.CREATIVE); p.maid().setItemSlot(EquipmentSlot.HEAD, bound(h, Items.IRON_HELMET));
        var menu = menu(p); menu.clicked(35, 0, ContainerInput.PICKUP, p.owner());
        h.assertTrue(p.maid().getItemBySlot(EquipmentSlot.HEAD).isEmpty() && menu.getCarried().is(Items.IRON_HELMET), "Creative exception missing");
        h.succeed();
    }

    @GameTest public void handsRespectSameLockAndSwapRules(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var maid = p.maid();
        maid.setItemSlot(EquipmentSlot.MAINHAND, bound(h, Items.IRON_SWORD));
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
        h.assertTrue(!MaidItemTransfer.swapHands(maid) && !menu(p).getSlot(39).mayPickup(p.owner()), "Hand policy differs");
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.APPLE, 32));
        h.assertTrue(MaidItemTransfer.swapHands(maid) && maid.getOffhandItem().getCount() == 32, "Ordinary hand stacks must survive");
        h.succeed();
    }

    @GameTest public void unsupportedAndWrongSlotsRejectWithoutMutation(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid(); maid.getInventory().setItem(0, new ItemStack(Items.APPLE, 8));
        h.assertTrue(MaidItemTransfer.moveInventoryToEquipment(maid, 0, EquipmentSlot.HEAD) == 0
                && MaidItemTransfer.moveInventoryToEquipment(maid, 0, EquipmentSlot.BODY) == 0, "Unsupported equipment accepted");
        h.assertTrue(maid.getInventory().getItem(0).getCount() == 8, "Rejected transfer consumed source"); h.succeed();
    }

    @GameTest public void feedingConsumesOneHealsAndCaps(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); p.maid().setHealth(17);
        p.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
        p.maid().mobInteract(p.owner(), InteractionHand.MAIN_HAND);
        h.assertTrue(p.maid().getHealth() == 20 && p.owner().getMainHandItem().getCount() == 2, "Feeding failed or overconsumed");
        p.maid().mobInteract(p.owner(), InteractionHand.MAIN_HAND);
        h.assertTrue(p.owner().getMainHandItem().getCount() == 2, "Full health consumed food"); h.succeed();
    }

    @GameTest public void creativeFeedingAndOffhand(GameTestHelper h) {
        var p = pair(h, GameType.CREATIVE); p.maid().setHealth(10);
        p.owner().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COOKIE, 2));
        h.assertTrue(MaidFeeding.feed(p.maid(), p.owner(), InteractionHand.OFF_HAND), "Offhand feeding rejected");
        h.assertTrue(p.maid().getHealth() == 14 && p.owner().getOffhandItem().getCount() == 2, "Creative food consumed"); h.succeed();
    }

    @GameTest public void feedingRejectsStrangerDistanceWrongItemAndDeadMaid(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); p.maid().setHealth(8);
        var stranger = h.makeMockPlayer(GameType.SURVIVAL); stranger.setPos(p.owner().position());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        h.assertTrue(!MaidFeeding.feed(p.maid(), stranger, InteractionHand.MAIN_HAND), "Stranger fed maid");
        p.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        h.assertTrue(!MaidFeeding.feed(p.maid(), p.owner(), InteractionHand.MAIN_HAND), "Wrong item accepted");
        p.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        p.owner().setPos(p.owner().position().add(50, 0, 0));
        h.assertTrue(!MaidFeeding.feed(p.maid(), p.owner(), InteractionHand.MAIN_HAND), "Remote feeding accepted");
        p.owner().setPos(p.maid().position()); p.maid().setHealth(0);
        h.assertTrue(!MaidFeeding.feed(p.maid(), p.owner(), InteractionHand.MAIN_HAND), "Feeding revived dead maid"); h.succeed();
    }

    @GameTest public void shiftInteractionKeepsFood(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); p.maid().setHealth(8); p.owner().setShiftKeyDown(true);
        p.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        p.maid().mobInteract(p.owner(), InteractionHand.MAIN_HAND);
        h.assertTrue(p.maid().getHealth() == 8 && p.owner().getMainHandItem().getCount() == 2, "Shift opened feeding path"); h.succeed();
    }

    @GameTest public void menuRechecksOwnerDistanceDeathAndCommandPolicy(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var menu = menu(p);
        var other = h.makeMockPlayer(GameType.CREATIVE); other.setPos(p.owner().position());
        h.assertTrue(!menu.stillValid(other), "Non-owner can open inventory");
        for (int id : new int[]{-1, 999999, ModBuiltInRegistries.MAID_COMMAND.getId(CoreTestCommands.HIDDEN),
                ModBuiltInRegistries.MAID_COMMAND.getId(CoreTestCommands.DENIED)})
            h.assertTrue(!menu.clickMenuButton(p.owner(), id), "Invalid/hidden/denied command accepted");
        p.owner().setPos(p.owner().position().add(50, 0, 0));
        h.assertTrue(!menu.stillValid(p.owner()) && !menu.clickMenuButton(p.owner(), ModBuiltInRegistries.MAID_COMMAND.getId(MaidCommands.STAY)), "Distant menu remained valid");
        p.owner().setPos(p.maid().position()); p.maid().setHealth(0);
        h.assertTrue(!menu.stillValid(p.owner()), "Dead maid menu remained valid"); h.succeed();
    }

    @GameTest public void menuAndAiShareOneEquipmentStorage(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var maid = p.maid(); var menu = menu(p);
        maid.getInventory().setItem(0, new ItemStack(Items.IRON_AXE)); maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.MAINHAND);
        h.assertTrue(menu.getSlot(39).getItem() == maid.getMainHandItem(), "Menu is not an adapter");
        menu.clicked(39, 0, ContainerInput.PICKUP, p.owner());
        h.assertTrue(menu.getCarried().is(Items.IRON_AXE) && maid.getMainHandItem().isEmpty()
                && maid.getInventory().countMatching(s -> s.is(Items.IRON_SWORD)) == 1, "Concurrent serialized operations lost items");
        // 真正关闭时的光标回收由后面的 ServerPlayer/客户端流程验收。
        h.succeed();
    }

    @GameTest public void commandAliasReselectAndLifecycleContract(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid();
        maid.selectMaidCommand(CoreTestCommands.STATUS); maid.selecetMaidCommand(CoreTestCommands.STATUS);
        h.assertTrue(maid.getCustomName().getString().equals("SDS"), "Reselect did not release old task before selection");
        maid.getBrain().setActiveActivityToFirstValid(java.util.List.of(net.minecraft.world.entity.schedule.Activity.IDLE));
        h.assertTrue(maid.getCustomName().getString().equals("SDS"), "Activity change called command callback");
        maid.selectMaidCommand(MaidCommands.STAY);
        h.assertTrue(maid.getCustomName().getString().equals("SDSD"), "Deselect missing"); h.succeed();
    }

    @GameTest public void unknownCommandSurvivesSaveAndFallback(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid();
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); maid.saveWithoutId(out);
        var tag = out.buildResult(); tag.putString("MaidCommand", "missing-addon:work");
        var loaded = new AiMaidEntity(h.getLevel()); loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
        h.assertTrue(loaded.getMaidCommandId().toString().equals("missing-addon:work") && loaded.getMaidCommand() == MaidCommands.FOLLOW, "Unknown ID erased");
        h.assertTrue(loaded.getMaidStatus().getString().contains("missing-addon:work"), "Missing addon not explained");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); loaded.saveWithoutId(saved);
        h.assertTrue(saved.buildResult().getStringOr("MaidCommand", "").equals("missing-addon:work"), "Saving replaced raw ID with fallback"); h.succeed();
    }

    @GameTest public void reloadDoesNotReplaySelection(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid(); maid.selectMaidCommand(CoreTestCommands.STATUS);
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); maid.saveWithoutId(out);
        maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var loaded = new AiMaidEntity(h.getLevel()); loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), out.buildResult()));
        h.assertTrue(loaded.getMaidCommand() == CoreTestCommands.STATUS && loaded.getCustomName().getString().equals("S"), "Reload replayed onSelected"); h.succeed();
    }

    @GameTest(maxTicks = 100) public void unavailableOwnerWaitsInsteadOfWandering(GameTestHelper h) {
        var p = pair(h, GameType.SURVIVAL); var maid = p.maid(); maid.setNoAi(false); maid.selectMaidCommand(MaidCommands.FOLLOW);
        p.owner().discard();
        var start = maid.position();
        h.runAfterDelay(70, () -> {
            h.assertTrue(maid.getBrain().isActive(ModActivities.WAIT_FOR_OWNER) && maid.position().distanceToSqr(start) < 0.2, "Absent owner caused wandering");
            h.assertTrue(!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Waiting kept navigation"); h.succeed();
        });
    }

    @GameTest(maxTicks = 100) public void ownerReturnAndLossSwitchWithoutStaleNavigation(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid(); maid.setNoAi(false); maid.selectMaidCommand(MaidCommands.FOLLOW);
        h.runAfterDelay(10, () -> {
            var owner = h.spawn(EntityTypes.VILLAGER, 6.5F, 1, 3.5F); owner.setNoAi(true); maid.setOwner(owner);
            h.runAfterDelay(10, () -> {
                h.assertTrue(maid.getBrain().isActive(ModActivities.FOLLOW_OWNER), "Owner return did not resume follow");
                owner.discard();
                h.runAfterDelay(10, () -> {
                    h.assertTrue(maid.getBrain().isActive(ModActivities.WAIT_FOR_OWNER)
                            && !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Owner loss kept old activity"); h.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 50) public void otherDimensionOwnerDoesNotEnableFollowing(GameTestHelper h) {
        var maid = pair(h, GameType.SURVIVAL).maid();
        var other = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var remote = EntityTypes.VILLAGER.create(other, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        remote.setPos(maid.position()); maid.setOwner(remote); maid.setNoAi(false); maid.selectMaidCommand(MaidCommands.FOLLOW);
        h.runAfterDelay(20, () -> {
            h.assertTrue(maid.getBrain().isActive(ModActivities.WAIT_FOR_OWNER)
                    && maid.getMaidStatus().equals(Component.translatable("status.ai-maid.waiting_owner")), "Cross-dimension owner activated follow"); h.succeed();
        });
    }

    @GameTest(structure = "ai-maid-core-tests:follow", maxTicks = 180)
    public void ridingMaidNeverTeleportsOutOfVehicle(GameTestHelper h) {
        var maid = stuckFollower(h);
        var vehicle = h.spawn(EntityTypes.PIG, 3.5F, 1, 5.5F); vehicle.setNoAi(true);
        h.assertTrue(maid.startRiding(vehicle, true, false), "Fixture failed to mount");
        h.runAfterDelay(150, () -> {
            h.assertTrue(maid.getVehicle() == vehicle && maid.distanceToSqr(maid.getOwner()) > 100, "Follow broke vehicle relation"); h.succeed();
        });
    }

    private AiMaidEntity stuckFollower(GameTestHelper h) {
        for (int x = 0; x < 24; x++) for (int z = 0; z < 12; z++) h.setBlock(x, 0, z, Blocks.STONE);
        var owner = h.spawn(EntityTypes.VILLAGER, 19.5F, 1, 5.5F); owner.setNoAi(true);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 3.5F, 1, 5.5F);
        maid.setTame(true, true); maid.setOwner(owner); maid.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        maid.selectMaidCommand(MaidCommands.FOLLOW); return maid;
    }

    @GameTest(structure = "ai-maid-core-tests:follow", maxTicks = 180)
    public void stuckFollowerReturnsToSafeLoadedGround(GameTestHelper h) {
        var maid = stuckFollower(h);
        h.succeedWhen(() -> h.assertTrue(maid.distanceToSqr(maid.getOwner()) < 25, "Stuck follower failed safe return"));
    }

    @GameTest(structure = "ai-maid-core-tests:follow", maxTicks = 180)
    public void unsafeDestinationNeverTeleports(GameTestHelper h) {
        var maid = stuckFollower(h); var start = maid.position();
        for (int x = 15; x < 24; x++) for (int z = 1; z < 10; z++) {
            h.setBlock(x, 0, z, Blocks.LAVA);
            for (int y = 1; y < 5; y++) h.setBlock(x, y, z, Blocks.STONE);
        }
        maid.getOwner().setInvulnerable(true);
        h.runAfterDelay(150, () -> { h.assertTrue(maid.position().distanceToSqr(start) < 1, "Unsafe return teleported into blocks/fluid"); h.succeed(); });
    }

    @GameTest(structure = "ai-maid-core-tests:follow", maxTicks = 180)
    public void stayNeverUsesFollowTeleport(GameTestHelper h) {
        var maid = stuckFollower(h); var start = maid.position(); maid.selectMaidCommand(MaidCommands.STAY);
        h.runAfterDelay(150, () -> { h.assertTrue(maid.position().distanceToSqr(start) < 1, "Stay teleported to distant owner"); h.succeed(); });
    }
}
