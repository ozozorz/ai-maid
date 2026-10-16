package io.github.ozozorz.aimaid;

import java.util.UUID;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 关闭并重新打开真实世界，验证主人解析与实体持久数据。 */
public class CoreReloadClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        UUID maidId;
        try (var world = context.worldBuilder().create()) {
            save = world.getWorldSave(); var connection = world.getConnection();
            connection.waitForChunksRender();
            maidId = world.getServer().computeOnServer(s -> {
                var owner = connection.getServerPlayer(); var level = connection.getServerLevel();
                var base = owner.blockPosition();
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                    level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                var maid = new AiMaidEntity(level); maid.setPos(owner.position().add(2, 0, 0));
                maid.setTame(true, true); maid.setOwner(owner); maid.selectMaidCommand(MaidCommands.STAY);
                maid.setHealth(13); maid.getInventory().setItem(34, new ItemStack(Items.DIAMOND, 7));
                var sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(19);
                maid.setItemSlot(EquipmentSlot.MAINHAND, sword); level.addFreshEntity(maid);
                return maid.getUUID();
            });
            context.waitTicks(5);
        }
        try (var reopened = save.open()) {
            var connection = reopened.getConnection(); connection.waitForChunksRender();
            reopened.getServer().waitFor(s -> connection.getServerLevel().getEntity(maidId) instanceof AiMaidEntity);
            int entityId = reopened.getServer().computeOnServer(s -> {
                var maid = (AiMaidEntity)connection.getServerLevel().getEntity(maidId);
                if (maid.getOwner() != connection.getServerPlayer() || maid.getMaidCommand() != MaidCommands.STAY
                        || maid.getHealth() != 13 || maid.getInventory().getItem(34).getCount() != 7
                        || maid.getMainHandItem().getDamageValue() != 19) throw new AssertionError("Reconnect changed owner, command or carried items");
                maid.selectMaidCommand(MaidCommands.FOLLOW); return maid.getId();
            });
            reopened.getServer().waitFor(s -> ((AiMaidEntity)connection.getServerLevel().getEntity(entityId))
                    .getBrain().isActive(ModActivities.FOLLOW_OWNER));
            context.waitFor(c -> c.level.getEntity(entityId) instanceof AiMaidEntity m && m.getMaidCommand() == MaidCommands.FOLLOW);
        }
    }
}
