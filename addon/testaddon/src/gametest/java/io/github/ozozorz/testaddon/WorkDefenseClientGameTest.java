package io.github.ozozorz.testaddon;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** 实际网络包与菜单投影：砍树 → 清裂纹自卫 → 换回工具 → 重新出现裂纹 → 完成。 */
public class WorkDefenseClientGameTest implements FabricClientGameTest {
    private record Scene(int maid, int enemy, BlockPos log) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var connection = world.getConnection();
            var server = world.getServer();
            connection.waitForChunksRender();
            Scene scene = server.computeOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var owner = connection.getServerPlayer();
                var base = owner.blockPosition().below();
                for (int x = -4; x <= 10; x++) for (int z = -4; z <= 10; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
                    for (int y = 1; y <= 6; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                owner.setGameMode(GameType.SURVIVAL);
                owner.connection.teleport(base.getX() + 6.5, base.getY() + 1, base.getZ() - 1.5, 35, 12);
                var maid = new AiMaidEntity(level);
                maid.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 3.5);
                maid.setTame(true, true); maid.setOwner(owner);
                maid.setCustomName(Component.literal("Work defense acceptance"));
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
                maid.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
                maid.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.15);
                maid.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                level.addFreshEntity(maid);
                var enemy = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                enemy.setNoAi(true);
                enemy.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 1.5);
                enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                enemy.setHealth(1000);
                level.addFreshEntity(enemy);
                var log = base.offset(4, 1, 3);
                level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
                maid.selecetMaidCommand(TestAddonCommands.LUMBER);
                maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
                return new Scene(maid.getId(), enemy.getId(), log);
            });
            context.waitFor(client -> crack(client.level, scene) >= 4, 600);
            context.takeScreenshot("work-defense-before-interruption");
            int oldToken = server.computeOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel()
                    .getEntity(scene.maid())).getAnimationAction().sequence());
            var job = server.computeOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel()
                    .getEntity(scene.maid())).getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElseThrow());
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                var enemy = (LivingEntity) level.getEntity(scene.enemy());
                check(maid.hurtServer(level, level.damageSources().mobAttack(enemy), 1), "Self-defense trigger must deal actual damage");
            });
            context.waitFor(client -> crack(client.level, scene) == -1);
            context.waitFor(client -> client.level.getEntity(scene.maid()) instanceof AiMaidEntity maid
                    && maid.getMainHandItem().is(Items.NETHERITE_SWORD)
                    && !maid.getAnimationAction().id().equals(TestAddonAnimationIds.LUMBER));
            server.waitFor(minecraftServer -> ((LivingEntity) connection.getServerLevel().getEntity(scene.enemy())).getHealth() < 1000);
            context.waitFor(client -> client.level.getEntity(scene.maid()) instanceof AiMaidEntity maid && maid.getAttackAnim(1) > 0.1F);
            context.takeScreenshot("work-defense-melee");
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_JOB).orElseThrow() == job,
                        "Combat replaced the work queue");
                check(MaidMenuOpener.open(connection.getServerPlayer(), maid), "Owner must be able to inspect defense");
            });
            context.waitFor(client -> client.player.containerMenu instanceof MaidMenu menu
                    && menu.getSelectedCommand() == TestAddonCommands.LUMBER
                    && menu.getActiveActivity() == TestAddonActivities.GUARD_FIGHT
                    && menu.getSlot(39).getItem().is(Items.NETHERITE_SWORD)
                    && menu.getSlot(0).getItem().is(Items.WOODEN_AXE));
            waitForSelectedWorkButton(context);
            context.takeScreenshot("work-defense-command-still-lumber");
            context.runOnClient(client -> client.player.closeContainer());
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                ((LivingEntity) level.getEntity(scene.enemy())).hurtServer(level, level.damageSources().fall(), 10000);
            });
            context.waitFor(client -> client.level.getEntity(scene.maid()) instanceof AiMaidEntity maid
                    && maid.getMainHandItem().is(Items.WOODEN_AXE)
                    && maid.getAnimationAction().id().equals(TestAddonAnimationIds.LUMBER)
                    && maid.getAnimationAction().sequence() != oldToken);
            context.waitFor(client -> crack(client.level, scene) >= 0);
            context.runOnClient(client -> check(crack(client.level, scene) < 4, "Interrupted block must restart progress instead of restoring old cracks"));
            context.waitFor(client -> client.level.getEntity(scene.enemy()) == null);
            context.takeScreenshot("work-defense-resumed");
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(!maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && !maid.isUsingItem(), "Resume kept combat state");
                check(MaidMenuOpener.open(connection.getServerPlayer(), maid), "Cannot inspect resumed work");
            });
            context.waitFor(client -> client.player.containerMenu instanceof MaidMenu menu
                    && menu.getSelectedCommand() == TestAddonCommands.LUMBER
                    && menu.getActiveActivity() == TestAddonActivities.LUMBER
                    && menu.getSlot(39).getItem().is(Items.WOODEN_AXE)
                    && menu.getSlot(0).getItem().is(Items.NETHERITE_SWORD));
            waitForSelectedWorkButton(context);
            context.takeScreenshot("work-defense-resumed-menu");
            context.runOnClient(client -> client.player.closeContainer());
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1));
            context.waitFor(client -> client.level.getBlockState(scene.log()).isAir() && crack(client.level, scene) == -1);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                int logs = maid.getInventory().countMatching(stack -> stack.is(Items.OAK_LOG))
                        + level.getEntitiesOfClass(ItemEntity.class, maid.getBoundingBox().inflate(8)).stream()
                        .filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
                check(logs == 1 && maid.getMainHandItem().getDamageValue() == 1 && job.broken == 1,
                        "Interrupted work must produce exactly one drop and one tool use");
            });
        }
    }

    private int crack(ClientLevel level, Scene scene) {
        // 调用者均在客户端线程；只读取真实 ClientLevel 收到的破坏进度包。
        var cracks = level.destructionProgress().get(scene.log().asLong());
        return cracks == null ? -1 : cracks.stream().filter(value -> value.getId() == scene.maid())
                .mapToInt(value -> value.getProgress()).max().orElse(-1);
    }

    private void waitForSelectedWorkButton(ClientGameTestContext context) {
        // DataSlot 到达和 containerTick 更新 Widget 不是同一步；验收实际显示后的按钮。
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().children().stream()
                .anyMatch(child -> child instanceof Button button && button.active && button.getMessage().getString()
                        .equals("> " + TestAddonCommands.LUMBER.getDisplayName().getString())));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
