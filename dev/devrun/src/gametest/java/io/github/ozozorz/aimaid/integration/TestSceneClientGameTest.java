package io.github.ozozorz.aimaid.integration;

import io.github.ozozorz.aimaid.command.MaidTestScene;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.lumber.lumber.LumberTreeFinder;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.AABB;

/** 用真实命令和真实工作验收用户拿到的场地；不读取模型配置或调用网络服务。 */
public class TestSceneClientGameTest implements FabricClientGameTest {
    private record Scene(BlockPos floor, int maid) {}
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection();
            connection.waitForChunksRender();
            Scene scene = server.computeOnServer(s -> {
                var owner = connection.getServerPlayer();
                var source = owner.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
                try { check(s.getCommands().getDispatcher().execute("maid test-scene", source) == 1, "Scene command failed"); }
                catch (Exception e) { throw new AssertionError("Scene command failed", e); }
                var floor = new BlockPos(owner.blockPosition().getX(), owner.blockPosition().getY() - 1, owner.blockPosition().getZ() + 3);
                var maid = connection.getServerLevel().getEntitiesOfClass(AiMaidEntity.class, new AABB(floor).inflate(15),
                        m -> m.isOwnedBy(owner) && m.entityTags().contains(MaidTestScene.ENTITY_TAG)).getFirst();
                check(maid.getMaidCommand() == MaidCommands.STAY, "Scene began work without instruction");
                check(LumberTreeFinder.collect(connection.getServerLevel(), floor.offset(-8, 1, -8)).isPresent(), "Generated small tree is not recognized");
                check(LumberTreeFinder.collect(connection.getServerLevel(), floor.offset(-12, 1, -12)).isPresent(), "Generated tall tree is not recognized");
                return new Scene(floor, maid.getId());
            });
            context.waitFor(c -> c.level.getEntity(scene.maid()) instanceof AiMaidEntity);
            connection.waitForChunksRender(); context.waitTicks(20);
            server.runOnServer(s -> {
                var f = scene.floor();
                connection.getServerPlayer().connection.teleport(f.getX() + 0.5, f.getY() + 24, f.getZ() + 19.5, 180, 55);
                connection.getServerPlayer().setNoGravity(true);
            });
            context.runOnClient(c -> { if (!c.gui.hud.isHidden()) c.gui.hud.toggle(); });
            context.waitTicks(10); context.takeScreenshot("test-scene-overview");
            context.runOnClient(c -> { if (c.gui.hud.isHidden()) c.gui.hud.toggle(); });
            server.runOnServer(s -> {
                var f = scene.floor(); var owner = connection.getServerPlayer(); owner.setNoGravity(false);
                owner.connection.teleport(f.getX() + 0.5, f.getY() + 1, f.getZ() - 2.5, 0, 10); owner.resetFallDistance();
                MaidMenuOpener.open(owner, (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()));
            });
            context.waitFor(c -> c.player.containerMenu instanceof MaidMenu m && !m.getSlot(0).getItem().isEmpty());
            context.takeScreenshot("test-scene-equipped-maid");
            context.runOnClient(c -> c.player.closeContainer());

            // 单块原木用真实 Brigadier 参数，检查实际世界变化。
            select(server, connection, scene, -4, -4, "ai-maid-lumber:lumber single");
            server.waitFor(s -> connection.getServerLevel().getBlockState(scene.floor().offset(-5, 1, -5)).isAir());
            select(server, connection, scene, 5, -8, "ai-maid-farming:farm 8");
            server.waitFor(s -> {
                var state = connection.getServerLevel().getBlockState(scene.floor().offset(4, 1, -11));
                return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
            });
            server.runOnServer(s -> {
                var pos = scene.floor().offset(-8, 1, 5); var level = connection.getServerLevel();
                level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(FenceGateBlock.OPEN, true));
            });
            select(server, connection, scene, -8, 7, "ai-maid-feeding:feed 8");
            server.waitFor(s -> connection.getServerLevel().getEntitiesOfClass(Animal.class,
                    new AABB(scene.floor()).inflate(15, 8, 15), a -> a.getType() == EntityTypes.COW)
                    .stream().anyMatch(a -> a.isInLove() || a.isBaby()));
            select(server, connection, scene, 7, 8, "ai-maid-hauling:haul 12");
            // 每次取/放都有 60 tick 交互；按批次核对进展，不把两批四次交互塞进默认 200 tick。
            server.waitFor(s -> ((Container) connection.getServerLevel().getBlockEntity(scene.floor().offset(6, 1, 6)))
                    .countItem(net.minecraft.world.item.Items.APPLE) == 0);
            server.waitFor(s -> ((Container) connection.getServerLevel().getBlockEntity(scene.floor().offset(11, 1, 5)))
                    .countItem(net.minecraft.world.item.Items.APPLE) == 17);
            server.waitFor(s -> ((Container) connection.getServerLevel().getBlockEntity(scene.floor().offset(6, 1, 6))).isEmpty());
            server.waitFor(s -> {
                var level = connection.getServerLevel();
                return ((Container) level.getBlockEntity(scene.floor().offset(11, 1, 5))).countItem(net.minecraft.world.item.Items.APPLE) == 17
                        && ((Container) level.getBlockEntity(scene.floor().offset(11, 1, 10))).countItem(net.minecraft.world.item.Items.COBBLESTONE) == 17;
            });
            server.runOnServer(s -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(maid.getInventory().countMatching(i -> i.is(net.minecraft.world.item.Items.APPLE)
                        || i.is(net.minecraft.world.item.Items.COBBLESTONE)) == 0, "Delivered cargo remained in maid inventory");
            });
            context.takeScreenshot("test-scene-work-complete");
        }
    }

    private void select(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                        net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection,
                        Scene scene, int x, int z, String command) {
        server.runOnServer(s -> {
            var f = scene.floor(); var owner = connection.getServerPlayer();
            var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
            maid.selectMaidCommand(MaidCommands.STAY);
            maid.getNavigation().stop(); maid.snapTo(f.getX() + x + 0.5, f.getY() + 1, f.getZ() + z + 0.5);
            owner.connection.teleport(f.getX() + x + 0.5, f.getY() + 1, f.getZ() + z - 1.5, 0, 10);
            s.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "maid uuid " + maid.getUUID() + " command " + command);
        });
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
