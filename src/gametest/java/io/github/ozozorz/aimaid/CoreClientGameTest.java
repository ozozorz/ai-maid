package io.github.ozozorz.aimaid;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.menu.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** 独立 Core 的真实客户端/服务器交互、状态数据包和菜单渲染。 */
public class CoreClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer(); var connection = world.getConnection();
            connection.waitForChunksRender();
            int id = server.computeOnServer(s -> {
                var level = connection.getServerLevel(); var owner = connection.getServerPlayer();
                var base = owner.blockPosition().below();
                for (int x = -4; x <= 8; x++) for (int z = -4; z <= 8; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                    for (int y = 1; y <= 5; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                owner.setGameMode(GameType.SURVIVAL);
                owner.connection.teleport(base.getX() + 4.5, base.getY() + 1, base.getZ() + 1.5, 90, 5);
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
                var maid = new AiMaidEntity(level); maid.setPos(base.getX() + 2.5, base.getY() + 1, base.getZ() + 1.5);
                maid.setNoAi(true); maid.setTame(true, true); maid.setOwner(owner); maid.setHealth(12);
                maid.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                maid.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
                level.addFreshEntity(maid); return maid.getId();
            });
            context.waitFor(c -> c.level.getEntity(id) instanceof AiMaidEntity && c.player.getMainHandItem().is(Items.BREAD));
            context.runOnClient(c -> {
                var maid = (AiMaidEntity)c.level.getEntity(id);
                c.gameMode.interact(c.player, maid, new net.minecraft.world.phys.EntityHitResult(maid), InteractionHand.MAIN_HAND);
                reject(() -> maid.selectMaidCommand(MaidCommands.STAY));
                reject(() -> MaidItemTransfer.swapHands(maid));
                reject(() -> maid.playAnimationAction(AIMaid.id("test"), 20));
            });
            server.waitFor(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(id)).getHealth() == 16);
            context.waitFor(c -> ((AiMaidEntity)c.level.getEntity(id)).getHealth() == 16 && c.player.getMainHandItem().getCount() == 2);
            context.takeScreenshot("core-feeding-health");
            String longStatus = "Waiting for a safe route: this long server-provided explanation must remain readable in the tooltip.";
            server.runOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(id);
                maid.selectMaidCommand(CoreTestCommands.STATUS); maid.setCustomName(Component.literal("Core acceptance"));
                maid.setAttached(CoreTestCommands.MESSAGE, Component.literal(longStatus));
                var owner = connection.getServerPlayer(); owner.setShiftKeyDown(true);
                maid.mobInteract(owner, InteractionHand.MAIN_HAND); owner.setShiftKeyDown(false);
                check(owner.getMainHandItem().getCount() == 2 && maid.getHealth() == 16, "Shift feeding consumed food");
            });
            context.waitFor(c -> c.player.containerMenu instanceof MaidMenu m && m.getStatusMessage().getString().equals(longStatus)
                    && m.slots.size() == 77 && m.getSlot(39).getItem().is(Items.IRON_SWORD));
            context.waitTicks(3);
            context.takeScreenshot("core-status-long");
            var cursor = context.computeOnClient(c -> new double[]{
                    ((c.gui.screen().width - 318) / 2.0 + 40) * c.getWindow().getGuiScale(),
                    ((c.gui.screen().height - 236) / 2.0 + 132) * c.getWindow().getGuiScale()});
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(2);
            context.takeScreenshot("core-status-tooltip");
            context.runOnClient(c -> {
                var menu = (MaidMenu)c.player.containerMenu;
                menu.acceptStatus(new MaidStatusPayload(menu.containerId + 1, id, Component.literal("stale")));
                menu.acceptStatus(new MaidStatusPayload(menu.containerId, id + 1, Component.literal("stale")));
                check(menu.getStatusMessage().getString().equals(longStatus), "Stale status packet accepted");
            });
            server.runOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(id);
                MaidItemTransfer.swapInventoryWithEquipment(maid, 0, EquipmentSlot.MAINHAND);
                maid.setAttached(CoreTestCommands.MESSAGE, Component.literal("Equipment changed while the owner kept the menu open"));
            });
            context.waitFor(c -> c.player.containerMenu instanceof MaidMenu m && m.getSlot(39).getItem().is(Items.IRON_AXE)
                    && m.getSlot(0).getItem().is(Items.IRON_SWORD) && m.getStatusMessage().getString().startsWith("Equipment changed"));
            context.takeScreenshot("core-live-equipment-status");
            context.runOnClient(c -> c.level.removeEntity(id, Entity.RemovalReason.UNLOADED_TO_CHUNK));
            server.runOnServer(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(id)).setAttached(CoreTestCommands.MESSAGE, Component.literal("Status still arrives without a client entity")));
            context.waitFor(c -> c.player.containerMenu instanceof MaidMenu m && m.getStatusMessage().getString().startsWith("Status still"));
            context.takeScreenshot("core-status-without-client-entity");
            // 真实点击包拿起工具，再关闭菜单；由 ServerPlayer 回收光标物品。
            context.runOnClient(c -> c.gameMode.handleContainerInput(c.player.containerMenu.containerId,
                    39, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, c.player));
            context.waitFor(c -> c.player.containerMenu instanceof MaidMenu m && m.getCarried().is(Items.IRON_AXE));
            context.runOnClient(c -> c.player.closeContainer());
            server.waitFor(s -> connection.getServerPlayer().getInventory().countItem(Items.IRON_AXE) == 1);
            server.runOnServer(s -> check(((AiMaidEntity)connection.getServerLevel().getEntity(id)).getMainHandItem().isEmpty(), "Closing duplicated cursor equipment"));
        }
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Client mutation was accepted");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
