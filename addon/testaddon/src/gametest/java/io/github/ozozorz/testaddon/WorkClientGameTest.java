package io.github.ozozorz.testaddon;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** 实际客户端包往返：裂纹、挥手、停工、卸载和死亡时已打开的容器。 */
public class WorkClientGameTest implements FabricClientGameTest {
    private record Scene(int maidId, BlockPos log) {}

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
                owner.connection.teleport(base.getX() + 3.5, base.getY() + 1, base.getZ() - 1.5, 0, 12);
                var maid = new AiMaidEntity(level);
                maid.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 3.5);
                maid.setTame(true, true);
                maid.setOwner(owner);
                maid.setCustomName(Component.literal("Lumber acceptance"));
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
                // 延长可观察窗口，仍通过同一真实进度公式推进。
                maid.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.15);
                level.addFreshEntity(maid);
                var log = base.offset(4, 1, 3);
                level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
                minecraftServer.getCommands().performPrefixedCommand(owner.createCommandSourceStack(),
                        "maid uuid " + maid.getUUID() + " command testaddon:lumber single");
                if (maid.getMaidCommand() != TestAddonCommands.LUMBER
                        || !maid.getBrain().getMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST).orElse(false)) {
                    throw new AssertionError("带 single 参数的主人命令没有建立单块请求");
                }
                return new Scene(maid.getId(), log);
            });
            waitForCracks(context, scene, true);
            context.waitFor(client -> client.level.destructionProgress().get(scene.log().asLong()).stream()
                    .anyMatch(progress -> progress.getId() == scene.maidId() && progress.getProgress() >= 5));
            context.waitFor(client -> client.level.getEntity(scene.maidId()) instanceof AiMaidEntity maid && maid.swinging);
            context.takeScreenshot("lumber-progress");
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maidId());
                maid.selectMaidCommand(MaidCommands.STAY);
            });
            waitForCracks(context, scene, false);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maidId());
                if (!level.getBlockState(scene.log()).is(Blocks.OAK_LOG) || maid.getMainHandItem().getDamageValue() != 0) {
                    throw new AssertionError("停工已经消耗了未完成的方块或耐久");
                }
                single(maid);
            });
            waitForCracks(context, scene, true);

            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maidId()))
                    .getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1));
            server.waitFor(minecraftServer -> connection.getServerLevel().getBlockState(scene.log()).isAir());
            context.waitFor(client -> client.level.getBlockState(scene.log()).isAir());
            waitForCracks(context, scene, false);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maidId());
                int logs = maid.getInventory().countMatching(stack -> stack.is(Items.OAK_LOG))
                        + level.getEntitiesOfClass(ItemEntity.class, maid.getBoundingBox().inflate(8)).stream()
                        .filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
                if (logs != 1 || maid.getMainHandItem().getDamageValue() != 1) throw new AssertionError("客户端环境中完成破坏的物品/耐久不正确");
                level.setBlockAndUpdate(scene.log(), Blocks.OAK_LOG.defaultBlockState());
                maid.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.15);
                single(maid);
            });
            waitForCracks(context, scene, true);

            // 卸载清除客户端裂纹，但不能触发死亡掉落。
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maidId());
                maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                if (level.getEntitiesOfClass(ItemEntity.class, maid.getBoundingBox().inflate(4)).stream()
                        .anyMatch(item -> item.getItem().is(Items.WOODEN_AXE))) {
                    throw new AssertionError("卸载产生了携带物品掉落");
                }
            });
            waitForCracks(context, scene, false);

            Scene death = server.computeOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = new AiMaidEntity(level);
                maid.setPos(scene.log().getX() - 1.5, scene.log().getY(), scene.log().getZ() + 0.5);
                maid.setTame(true, true);
                maid.setOwner(connection.getServerPlayer());
                var axe = new ItemStack(Items.WOODEN_AXE);
                axe.set(DataComponents.CUSTOM_NAME, Component.literal("Death test axe"));
                maid.setItemSlot(EquipmentSlot.MAINHAND, axe);
                maid.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(0.03);
                maid.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                maid.getInventory().setItem(1, new ItemStack(Items.EMERALD, 11));
                level.addFreshEntity(maid);
                single(maid);
                return new Scene(maid.getId(), scene.log());
            });
            waitForCracks(context, death, true);
            server.runOnServer(minecraftServer -> {
                if (!MaidMenuOpener.open(connection.getServerPlayer(),
                        (AiMaidEntity) connection.getServerLevel().getEntity(death.maidId()))) throw new AssertionError("无法打开作业女仆的容器");
            });
            context.waitFor(client -> client.player.containerMenu instanceof MaidMenu menu && menu.getSlot(0).getItem().getCount() == 7);
            context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.containerMenu.containerId,
                    0, 0, ContainerInput.PICKUP, client.player));
            server.waitFor(minecraftServer -> connection.getServerPlayer().containerMenu.getCarried().is(Items.DIAMOND));
            context.takeScreenshot("lumber-open-menu");
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(death.maidId());
                if (!maid.hurtServer(level, level.damageSources().fall(), 1000)) throw new AssertionError("死亡验收必须实际受伤");
            });
            context.waitFor(client -> client.player.containerMenu == client.player.inventoryMenu && client.gui.screen() == null);
            waitForCracks(context, death, false);
            server.runOnServer(minecraftServer -> {
                var owner = connection.getServerPlayer();
                var level = connection.getServerLevel();
                var items = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(12));
                int diamonds = owner.getInventory().countItem(Items.DIAMOND) + items.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
                int emeralds = owner.getInventory().countItem(Items.EMERALD) + items.stream().filter(e -> e.getItem().is(Items.EMERALD)).mapToInt(e -> e.getItem().getCount()).sum();
                long axes = items.stream().filter(e -> e.getItem().is(Items.WOODEN_AXE) && e.getItem().getDamageValue() == 0).count();
                if (diamonds != 7 || emeralds != 11 || axes != 1 || !owner.containerMenu.getCarried().isEmpty()) {
                    throw new AssertionError("死亡关闭容器后，光标/存储/装备物品未守恒：" + diamonds + "/" + emeralds + "/" + axes);
                }
                if (!level.getBlockState(death.log()).is(Blocks.OAK_LOG)) throw new AssertionError("死亡后仍然完成了破坏");
            });
            context.takeScreenshot("lumber-death-cleanup");
        }
    }

    private void single(AiMaidEntity maid) {
        maid.selectMaidCommand(TestAddonCommands.LUMBER);
        maid.getBrain().setMemory(TestAddonMemoryModuleTypes.LUMBER_REQUEST, true);
    }

    private void waitForCracks(ClientGameTestContext context, Scene scene, boolean present) {
        context.waitFor(client -> {
            var cracks = client.level.destructionProgress().get(scene.log().asLong());
            boolean found = cracks != null && cracks.stream().anyMatch(progress -> progress.getId() == scene.maidId()
                    && (!present || progress.getProgress() >= 1));
            return found == present;
        }, 600);
    }
}
