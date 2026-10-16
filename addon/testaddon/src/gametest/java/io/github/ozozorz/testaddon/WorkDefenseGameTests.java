package io.github.ozozorz.testaddon;

import java.util.Map;
import java.util.function.Consumer;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import io.github.ozozorz.testaddon.data.TestAddonMaidData;
import io.github.ozozorz.testaddon.lumber.LumberJob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** 让真实 Sensor、Activity 和 Behavior 运行，验收一次工作的中断与恢复。 */
public class WorkDefenseGameTests {
    private static final BlockPos LOG = new BlockPos(4, 1, 4);
    private record Scene(AiMaidEntity maid, Mob owner, Mob enemy, ItemStack axe, ItemStack sword) {}

    private Scene scene(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var owner = h.spawn(EntityTypes.VILLAGER, 0.5F, 1, 0.5F);
        owner.setNoAi(true);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 2.5F, 1, 4.5F);
        maid.setTame(true, true);
        maid.setOwner(owner);
        maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var axe = new ItemStack(Items.WOODEN_AXE);
        axe.set(DataComponents.CUSTOM_NAME, Component.literal("Work tool"));
        var sword = new ItemStack(Items.NETHERITE_SWORD);
        maid.setItemSlot(EquipmentSlot.MAINHAND, axe);
        maid.getInventory().setItem(0, sword);
        var enemy = enemy(h, 2.5F, 1.5F);
        h.setBlock(LOG, Blocks.OAK_LOG);
        single(maid);
        return new Scene(maid, owner, enemy, axe, sword);
    }

    private Mob enemy(GameTestHelper h, float x, float z) {
        Mob enemy = h.spawn(EntityTypes.ZOMBIE, x, 1, z);
        enemy.setNoAi(true);
        enemy.setPersistenceRequired();
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        enemy.setHealth(100);
        return enemy;
    }

    private void single(AiMaidEntity maid) {
        maid.selecetMaidCommand(TestAddonCommands.LUMBER);
        maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
    }

    private LivingEntity target(AiMaidEntity maid) { return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null); }
    private LumberJob job(AiMaidEntity maid) { return maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElse(null); }
    private boolean done(AiMaidEntity maid) { return maid.getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.LUMBER_DONE); }
    private boolean mining(AiMaidEntity maid) { return maid.getAnimationAction().id().equals(TestAddonAnimationIds.LUMBER); }
    private void hit(GameTestHelper h, Scene s) { h.hurt(s.maid(), h.getLevel().damageSources().mobAttack(s.enemy()), 1); }
    private int logs(GameTestHelper h, AiMaidEntity maid) {
        return maid.getInventory().countMatching(stack -> stack.is(Items.OAK_LOG))
                + h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream()
                .filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
    }

    private void whenMining(GameTestHelper h, Scene s, Consumer<LumberJob> action) {
        boolean[] ran = {false};
        h.onEachTick(() -> {
            if (!ran[0] && mining(s.maid())) {
                ran[0] = true;
                action.accept(job(s.maid()));
            }
        });
    }

    @GameTest(maxTicks = 360)
    public void wholeTreeResumesSameQueueAfterRealCombatWithFullInventory(GameTestHelper h) {
        var s = scene(h);
        for (int y = 1; y <= 4; y++) h.setBlock(4, y, 4, Blocks.OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) {
            h.setBlock(new BlockPos(4 + dx, 4, 4 + dz), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1));
        }
        for (int slot = 1; slot < 35; slot++) s.maid().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        s.maid().selecetMaidCommand(TestAddonCommands.LUMBER);
        LumberJob[] interrupted = {null};
        boolean[] fought = {false};
        h.onEachTick(() -> {
            if (interrupted[0] == null && mining(s.maid()) && job(s.maid()).broken == 1) {
                interrupted[0] = job(s.maid());
                hit(h, s);
            }
            if (target(s.maid()) != null) {
                h.assertTrue(job(s.maid()) == interrupted[0] && job(s.maid()).broken == 1, "Combat must retain this tree and its completed blocks");
                h.assertTrue(!mining(s.maid()) && s.axe().getDamageValue() == 1, "Mining must stop before combat ticks");
                h.assertTrue(s.maid().getMaidCommand() == TestAddonCommands.LUMBER, "Defense must not change the command");
            }
            if (!fought[0] && s.enemy().getHealth() < 100) {
                fought[0] = true;
                h.assertTrue(s.maid().getMainHandItem() == s.sword() && s.maid().getInventory().getItem(0) == s.axe(), "Full storage must preserve the actual tool stack while fighting");
                h.kill(s.enemy());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(fought[0] && done(s.maid()), "Combat and resumed tree must finish");
            h.assertTrue(interrupted[0].broken == 4 && interrupted[0].skipped == 0 && logs(h, s.maid()) == 4, "Resumed tree must drop exactly four logs");
            h.assertTrue(s.maid().getMainHandItem() == s.axe() && s.axe().getDamageValue() == 4
                    && s.maid().getInventory().getItem(0) == s.sword(), "Restore the real axe without duplication or lost durability");
        });
    }

    @GameTest(maxTicks = 240)
    public void interruptedDrawReturnsAxeFromOffhandWithoutSpendingAmmo(GameTestHelper h) {
        var s = scene(h);
        var bow = new ItemStack(Items.BOW);
        s.maid().getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        s.maid().setItemSlot(EquipmentSlot.OFFHAND, bow);
        Vec3 far = h.absoluteVec(new Vec3(7.5, 1, 0.5));
        s.enemy().snapTo(far.x, far.y, far.z, 0, 0);
        whenMining(h, s, job -> hit(h, s));
        boolean[] drawing = {false};
        h.onEachTick(() -> {
            if (!drawing[0] && s.maid().isUsingItem()) {
                drawing[0] = true;
                h.assertTrue(s.maid().getOffhandItem() == s.axe(), "Combat must have moved the work tool into offhand");
                h.kill(s.enemy());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(drawing[0] && done(s.maid()), "Draw cancellation must resume the original task");
            h.assertTrue(s.maid().getMainHandItem() == s.axe() && s.maid().getOffhandItem() == bow && !s.maid().isUsingItem(), "Both hands must return to valid working state");
            h.assertTrue(s.maid().getInventory().getItem(0).getCount() == 8 && bow.getDamageValue() == 0, "Unfinished draw must not fire on resume");
            h.assertTrue(logs(h, s.maid()) == 1 && s.axe().getDamageValue() == 1, "Single log must complete once");
        });
    }

    @GameTest(maxTicks = 180)
    public void commandCancellationAbandonsSuspendedWorkImmediately(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] cancelled = {false};
        h.onEachTick(() -> {
            if (!cancelled[0] && target(s.maid()) != null) {
                cancelled[0] = true;
                s.maid().selecetMaidCommand(MaidCommands.STAY);
                h.assertTrue(target(s.maid()) == null && job(s.maid()) == null, "Explicit selection must release the old target and queue immediately");
                h.kill(s.enemy());
                h.runAfterDelay(60, () -> {
                    h.assertBlockPresent(Blocks.OAK_LOG, LOG);
                    h.assertTrue(s.maid().getMaidCommand() == MaidCommands.STAY && !mining(s.maid())
                            && logs(h, s.maid()) == 0 && s.axe().getDamageValue() == 0, "An abandoned task must never return after combat");
                    h.succeed();
                });
            }
        });
    }

    @GameTest(maxTicks = 180)
    public void reselectingWorkCreatesNewRequestInsteadOfRevivingOldQueue(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] restarted = {false};
        h.onEachTick(() -> {
            if (!restarted[0] && target(s.maid()) != null) {
                restarted[0] = true;
                var old = job(s.maid());
                h.setBlock(LOG, Blocks.STONE);
                single(s.maid());
                h.assertTrue(old != null && job(s.maid()) == null && target(s.maid()) == null, "Reselect must discard suspended state, even with the same Command singleton");
                h.kill(s.enemy());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(restarted[0] && done(s.maid()) && logs(h, s.maid()) == 0, "A new empty request must finish without stale work");
            h.assertBlockPresent(Blocks.STONE, LOG);
        });
    }

    @GameTest(maxTicks = 240)
    public void combatTimeDoesNotConsumeRemainingWorkBudget(GameTestHelper h) {
        var s = scene(h);
        s.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        whenMining(h, s, old -> {
            long now = h.getLevel().getGameTime();
            var nearTimeout = new LumberJob(Map.copyOf(old.remaining()), now - 5950);
            s.maid().getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_JOB, nearTimeout);
            hit(h, s);
            h.runAfterDelay(75, () -> {
                h.assertTrue(target(s.maid()) != null && nearTimeout.activeTicks(h.getLevel().getGameTime()) < 5960, "Combat time must be excluded from the 6000-tick work budget");
                h.kill(s.enemy());
            });
        });
        h.succeedWhen(() -> h.assertTrue(done(s.maid()) && logs(h, s.maid()) == 1, "Task near timeout must still complete after defense"));
    }

    @GameTest(maxTicks = 180)
    public void changedLogIsRevalidatedAfterCombat(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] changed = {false};
        h.onEachTick(() -> {
            if (!changed[0] && target(s.maid()) != null) {
                changed[0] = true;
                h.setBlock(LOG, Blocks.STONE);
                h.kill(s.enemy());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(changed[0] && done(s.maid()), "Changed task must end after revalidation");
            h.assertBlockPresent(Blocks.STONE, LOG);
            h.assertTrue(logs(h, s.maid()) == 0 && s.axe().getDamageValue() == 0, "A replacement block must not inherit progress or consume the axe");
        });
    }

    @GameTest(maxTicks = 180)
    public void missingAxeAfterCombatEndsWorkWithoutRecreatingIt(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] removed = {false};
        h.onEachTick(() -> {
            if (!removed[0] && target(s.maid()) != null && s.maid().getMainHandItem() == s.sword()) {
                removed[0] = true;
                int slot = s.maid().getInventory().findFirstMatchingSlot(stack -> stack.is(ItemTags.AXES)).orElseThrow();
                s.maid().getInventory().removeItemNoUpdate(slot);
                h.kill(s.enemy());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(removed[0] && done(s.maid()) && logs(h, s.maid()) == 0, "Missing tool must finish safely");
            h.assertBlockPresent(Blocks.OAK_LOG, LOG);
            h.assertTrue(s.maid().getMainHandItem() == s.sword() && s.maid().getInventory().countMatching(stack -> stack.is(ItemTags.AXES)) == 0, "Resume cannot recreate a cached tool");
        });
    }

    @GameTest(maxTicks = 200)
    public void distantOwnerDoesNotPreventLocalSelfDefense(GameTestHelper h) {
        var s = scene(h);
        Vec3 far = h.absoluteVec(new Vec3(45, 1, 45));
        s.owner().snapTo(far.x, far.y, far.z, 0, 0);
        whenMining(h, s, job -> hit(h, s));
        h.succeedWhen(() -> h.assertTrue(s.enemy().getHealth() < 100 && s.maid().getMaidCommand() == TestAddonCommands.LUMBER, "Work defense must use the workplace, not the remote owner, as chase anchor"));
    }

    @GameTest(maxTicks = 220)
    public void escapedEnemyReleasesWorkWithoutReplayingOldInjury(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] escaped = {false};
        h.onEachTick(() -> {
            if (!escaped[0] && target(s.maid()) != null) {
                escaped[0] = true;
                Vec3 far = h.absoluteVec(new Vec3(20, 1, 4));
                s.enemy().snapTo(far.x, far.y, far.z, 0, 0);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(escaped[0] && done(s.maid()) && target(s.maid()) == null && logs(h, s.maid()) == 1, "Escaped enemy must release the same task");
            h.assertTrue(s.maid().position().distanceToSqr(h.absoluteVec(new Vec3(2.5, 1, 4.5))) < 144,
                    "Self defense must not pursue beyond its local leash");
        });
    }

    @GameTest(maxTicks = 260)
    public void lostSightResumesWorkAndDoesNotLoopOnStaleHit(GameTestHelper h) {
        var s = scene(h);
        s.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        whenMining(h, s, job -> hit(h, s));
        boolean[] blocked = {false};
        h.onEachTick(() -> {
            if (!blocked[0] && target(s.maid()) != null) {
                blocked[0] = true;
                for (int x = 0; x < 8; x++) for (int y = 1; y < 7; y++) h.setBlock(x, y, 2, Blocks.STONE);
            }
        });
        h.succeedWhen(() -> h.assertTrue(blocked[0] && h.getTick() > 160 && done(s.maid())
                && target(s.maid()) == null && logs(h, s.maid()) == 1, "Lost-sight backoff must resume work without reacquiring an old injury"));
    }

    @GameTest(maxTicks = 280)
    public void patrolKeepsCenterRadiusAndWaypointAndRestoresTool(GameTestHelper h) {
        var s = scene(h);
        s.maid().selecetMaidCommand(TestAddonCommands.PATROL);
        TestAddonMaidData.setPatrolRadius(s.maid(), 6);
        var center = TestAddonMaidData.getPatrolCenter(s.maid());
        var waypoint = h.absolutePos(new BlockPos(6, 1, 6));
        s.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Vec3 close = h.absoluteVec(new Vec3(2.5, 1, 3.2));
        s.enemy().snapTo(close.x, close.y, close.z, 0, 0);
        h.runAfterDelay(5, () -> {
            s.maid().getBrain().setMemory(TestAddonMemoryModuleTypes.PATROL_TARGET, waypoint);
            hit(h, s);
        });
        boolean[] fought = {false};
        h.onEachTick(() -> {
            if (target(s.maid()) != null) h.assertTrue(s.maid().getBrain().isMemoryValue(TestAddonMemoryModuleTypes.PATROL_TARGET, waypoint), "Combat must preserve the patrol waypoint");
            if (!fought[0] && s.enemy().getHealth() < 100) {
                fought[0] = true;
                h.kill(s.enemy());
                s.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(fought[0] && s.maid().getBrain().hasMemoryValue(TestAddonMemoryModuleTypes.PATROL_PAUSE)
                    && s.maid().blockPosition().distManhattan(waypoint) <= 1, "Patrol must actually walk back to the saved waypoint");
            h.assertTrue(center.equals(TestAddonMaidData.getPatrolCenter(s.maid())) && TestAddonMaidData.getPatrolRadius(s.maid()) == 6, "Resume must not run onSelected and reset the patrol center");
            h.assertTrue(s.maid().getMainHandItem() == s.axe() && s.maid().getBrain().isActive(TestAddonActivities.PATROL), "Patrol tool and activity must resume");
        });
    }

    @GameTest(maxTicks = 100)
    public void workDoesNotHuntAssistOrDefendOwner(GameTestHelper h) {
        var s = scene(h);
        var patrol = h.spawn(ModEntityTypes.AI_MAID, 6.5F, 1, 6.5F);
        patrol.setTame(true, true); patrol.setOwner(s.owner());
        patrol.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        patrol.selecetMaidCommand(TestAddonCommands.PATROL);
        h.runAfterDelay(5, () -> {
            h.hurt(s.owner(), h.getLevel().damageSources().mobAttack(s.enemy()), 1);
            s.owner().setLastHurtMob(s.enemy());
        });
        h.onEachTick(() -> h.assertTrue(target(s.maid()) == null && target(patrol) == null, "Working maids respond only to their own new injury"));
        h.runAfterDelay(70, () -> {
            h.assertTrue(s.enemy().getHealth() == 100 && done(s.maid()), "Nearby hostiles and owner events must not interrupt work");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 180)
    public void reloadDuringDefenseDoesNotRestoreTemporaryCombatOrWork(GameTestHelper h) {
        var s = scene(h);
        whenMining(h, s, job -> hit(h, s));
        boolean[] reloaded = {false};
        h.onEachTick(() -> {
            if (!reloaded[0] && target(s.maid()) != null) {
                reloaded[0] = true;
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
                s.maid().saveWithoutId(output);
                s.maid().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                var restored = new AiMaidEntity(h.getLevel());
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
                h.getLevel().addFreshEntity(restored);
                h.runAfterDelay(50, () -> {
                    h.assertTrue(restored.getMaidCommand() == TestAddonCommands.LUMBER && done(restored)
                            && target(restored) == null && job(restored) == null, "Reload retains the command but must not resurrect an interrupted runtime job");
                    h.assertBlockPresent(Blocks.OAK_LOG, LOG);
                    h.assertTrue(logs(h, restored) == 0 && !mining(restored), "Reload must not finish stale mining");
                    h.succeed();
                });
            }
        });
    }

    @GameTest(maxTicks = 260)
    public void walkingJobReplansWhenOldWorkstationBecomesBlocked(GameTestHelper h) {
        var s = scene(h);
        Vec3 start = h.absoluteVec(new Vec3(0.5, 1, 7.5));
        s.maid().snapTo(start.x, start.y, start.z, 0, 0);
        BlockPos[] oldFeet = {null};
        boolean[] blocked = {false};
        boolean[] replanned = {false};
        h.onEachTick(() -> {
            if (oldFeet[0] == null && job(s.maid()) != null && !mining(s.maid())
                    && s.maid().getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                oldFeet[0] = s.maid().getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_WORK_POS).orElseThrow();
                hit(h, s);
            }
            if (!blocked[0] && target(s.maid()) != null) {
                blocked[0] = true;
                h.getLevel().setBlockAndUpdate(oldFeet[0], Blocks.STONE.defaultBlockState());
                h.kill(s.enemy());
            }
            if (blocked[0] && mining(s.maid())) {
                replanned[0] = true;
                h.assertTrue(!s.maid().blockPosition().equals(oldFeet[0]), "Resume must choose a currently usable workstation");
            }
        });
        h.succeedWhen(() -> h.assertTrue(replanned[0] && done(s.maid()) && logs(h, s.maid()) == 1,
                "Interrupted travel must replan and finish its original log"));
    }

    @GameTest(maxTicks = 300)
    public void repeatedInterruptionsKeepOneJobAndNeverDuplicateCompletion(GameTestHelper h) {
        var s = scene(h);
        s.maid().getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.5);
        var secondEnemy = enemy(h, 1.5F, 2.5F);
        int[] phase = {0};
        LumberJob[] saved = {null};
        h.onEachTick(() -> {
            if (phase[0] == 0 && mining(s.maid())) {
                saved[0] = job(s.maid());
                hit(h, s); phase[0] = 1;
            } else if (phase[0] == 1 && target(s.maid()) != null) {
                h.kill(s.enemy()); phase[0] = 2;
            } else if (phase[0] == 2 && mining(s.maid())) {
                h.assertTrue(job(s.maid()) == saved[0], "First resume replaced the task");
                s.maid().invulnerableTime = 0;
                h.hurt(s.maid(), h.getLevel().damageSources().mobAttack(secondEnemy), 1);
                phase[0] = 3;
            } else if (phase[0] == 3 && target(s.maid()) == secondEnemy) {
                h.assertTrue(job(s.maid()) == saved[0] && saved[0].broken == 0, "Second suspension lost or completed the task");
                h.kill(secondEnemy); phase[0] = 4;
            }
        });
        h.succeedWhen(() -> h.assertTrue(phase[0] == 4 && done(s.maid()) && logs(h, s.maid()) == 1
                && s.axe().getDamageValue() == 1 && saved[0].broken == 1, "Repeated interruption state: phase=" + phase[0]
                + ", done=" + done(s.maid()) + ", logs=" + logs(h, s.maid()) + ", axe=" + s.axe().getDamageValue()
                + ", target=" + target(s.maid()) + ", job=" + job(s.maid()) + ", lastAttacker=" + s.maid().getLastHurtByMob()
                + ", stamp=" + s.maid().getLastHurtByMobTimestamp() + ", second=" + secondEnemy.getId()));
    }

    @GameTest(maxTicks = 220)
    public void oneWorkersDefenseDoesNotPauseAnotherWorkersJob(GameTestHelper h) {
        var s = scene(h);
        s.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var second = h.spawn(ModEntityTypes.AI_MAID, 6.5F, 1, 6.5F);
        second.setTame(true, true); second.setOwner(s.owner());
        second.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
        h.setBlock(7, 1, 7, Blocks.OAK_LOG);
        single(second);
        whenMining(h, s, job -> hit(h, s));
        h.runAfterDelay(65, () -> {
            h.assertTrue(target(s.maid()) == s.enemy() && job(s.maid()) != null && !done(s.maid()), "First worker must still be interrupted");
            h.assertTrue(target(second) == null && done(second), "Second Brain must complete independently during the first fight");
            h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, 7));
            h.assertBlockPresent(Blocks.OAK_LOG, LOG);
            h.succeed();
        });
    }

    @GameTest(maxTicks = 220)
    public void defenseAfterCompletionDoesNotStartAnotherTree(GameTestHelper h) {
        var s = scene(h);
        boolean[] triggered = {false};
        boolean[] fought = {false};
        h.onEachTick(() -> {
            if (!triggered[0] && done(s.maid())) {
                triggered[0] = true;
                h.setBlock(LOG, Blocks.OAK_LOG);
                hit(h, s);
            }
            if (!fought[0] && target(s.maid()) != null) {
                fought[0] = true;
                h.kill(s.enemy());
                h.runAfterDelay(60, () -> {
                    h.assertTrue(done(s.maid()) && job(s.maid()) == null && s.axe().getDamageValue() == 1,
                            "Returning from defense must retain a completed task's DONE marker");
                    h.assertBlockPresent(Blocks.OAK_LOG, LOG);
                    h.succeed();
                });
            }
        });
    }
}
