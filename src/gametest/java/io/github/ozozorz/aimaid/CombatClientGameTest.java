package io.github.ozozorz.aimaid;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;

/** 真正客户端 + 集成服务器：验证实体同步、渲染姿势和菜单按钮数据往返。 */
public class CombatClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var connection = world.getConnection();
            var server = world.getServer();
            connection.waitForChunksRender();
            int id = server.computeOnServer(minecraftServer -> {
                var owner = connection.getServerPlayer();
                var level = connection.getServerLevel();
                AiMaidEntity maid = new AiMaidEntity(level);
                maid.setPos(owner.getX() + 2, owner.getY(), owner.getZ());
                maid.setTame(true, true);
                maid.setOwner(owner);
                maid.setNoAi(true);
                maid.selectMaidCommand(MaidCommands.FOLLOW);
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                maid.getInventory().setItem(0, new ItemStack(Items.ARROW, 16));
                level.addFreshEntity(maid);
                maid.startUsingItem(InteractionHand.MAIN_HAND);
                return maid.getId();
            });
            waitForPose(context, id, HumanoidModel.ArmPose.BOW_AND_ARROW);

            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(id);
                maid.stopUsingItem();
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            });
            // 装备包与使用状态包是两条同步；先观察换装，再开始下一次使用。
            context.waitFor(client -> client.level.getEntity(id) instanceof AiMaidEntity maid
                    && maid.getMainHandItem().is(Items.CROSSBOW) && !maid.isUsingItem());
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(id);
                maid.startUsingItem(InteractionHand.MAIN_HAND);
            });
            waitForPose(context, id, HumanoidModel.ArmPose.CROSSBOW_CHARGE);
            context.waitTicks(35);
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(id);
                maid.stopUsingItem();
                if (!maid.getMainHandItem().getOrDefault(DataComponents.CHARGED_PROJECTILES,
                        ChargedProjectiles.EMPTY).contains(Items.ARROW)) {
                    throw new AssertionError("客户端运行期间弩未完成真实装填");
                }
            });
            waitForPose(context, id, HumanoidModel.ArmPose.CROSSBOW_HOLD);

            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(id);
                if (!MaidMenuOpener.open(connection.getServerPlayer(), maid)) {
                    throw new AssertionError("主人无法打开女仆菜单");
                }
            });
            context.waitForScreen(AbstractContainerScreen.class);
            context.waitFor(client -> connection.getClientPlayer().containerMenu instanceof MaidMenu menu
                    && menu.slots.size() == 77 && menu.getSelectedCommand() == MaidCommands.FOLLOW
                    && menu.getVisibleCommands().contains(MaidCommands.ATTACK));
            context.clickScreenButton(MaidCommands.ATTACK.getTranslationKey());
            server.waitFor(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(id))
                    .getMaidCommand() == MaidCommands.ATTACK);
            context.waitFor(client -> connection.getClientPlayer().containerMenu instanceof MaidMenu menu
                    && menu.getSelectedCommand() == MaidCommands.ATTACK);
            context.waitTicks(2);
            context.takeScreenshot("combat-menu");
            context.runOnClient(client -> connection.getClientPlayer().closeContainer());

            // 真实 ServerPlayer 的伤害记录也必须触发护主，不能只依赖服务端假主人场景。
            int enemyId = server.computeOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var owner = connection.getServerPlayer();
                owner.setGameMode(GameType.SURVIVAL);
                var maid = (AiMaidEntity) level.getEntity(id);
                maid.setNoAi(false);
                maid.selectMaidCommand(MaidCommands.FOLLOW);
                var enemy = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                enemy.setPos(maid.getX() + 5, maid.getY(), maid.getZ());
                enemy.setNoAi(true);
                enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                enemy.setHealth(100);
                level.addFreshEntity(enemy);
                return enemy.getId();
            });
            context.waitTicks(5);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var enemy = (net.minecraft.world.entity.LivingEntity) level.getEntity(enemyId);
                if (!connection.getServerPlayer().hurtServer(level, level.damageSources().mobAttack(enemy), 1)) {
                    throw new AssertionError("验收主人必须实际受伤");
                }
            });
            server.waitFor(minecraftServer -> ((net.minecraft.world.entity.LivingEntity) connection.getServerLevel()
                    .getEntity(enemyId)).getHealth() < 100);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(id);
                var owner = connection.getServerPlayer();
                float health = owner.getHealth();
                owner.invulnerableTime = 0;
                Arrow arrow = new Arrow(level, maid, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
                owner.hurtServer(level, level.damageSources().arrow(arrow, maid), 4);
                if (owner.getHealth() != health) throw new AssertionError("女仆箭矢误伤了生存玩家");
                if (!MaidMenuOpener.open(owner, maid)) throw new AssertionError("战斗时无法打开菜单");
            });
            context.waitForScreen(AbstractContainerScreen.class);
            context.waitFor(client -> connection.getClientPlayer().containerMenu instanceof MaidMenu menu
                    && menu.getActiveActivity() == ModActivities.FIGHT);
            context.waitFor(client -> client.gui.screen().children().stream().anyMatch(child ->
                    child instanceof Button button && button.getMessage().getString()
                            .equals("> " + MaidCommands.FOLLOW.getDisplayName().getString())));
            context.takeScreenshot("combat-active");
            context.runOnClient(client -> connection.getClientPlayer().closeContainer());

            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(id);
                maid.selectMaidCommand(MaidCommands.FOLLOW);
            });
            context.waitTicks(3);
            server.runOnServer(minecraftServer -> {
                var owner = connection.getServerPlayer();
                var enemy = (net.minecraft.world.entity.LivingEntity) connection.getServerLevel().getEntity(enemyId);
                enemy.setPos(owner.getX(), owner.getY(), owner.getZ() + 1.5);
                // 上一轮箭伤的无敌帧不能吞掉本次用于触发协助的玩家攻击。
                enemy.invulnerableTime = 0;
                owner.attack(enemy);
                if (owner.getLastHurtMob() != enemy) throw new AssertionError("主人攻击未生成原版攻击记录");
            });
            server.waitFor(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(id))
                    .getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)
                    == connection.getServerLevel().getEntity(enemyId));
        }
    }

    private void waitForPose(ClientGameTestContext context, int id, HumanoidModel.ArmPose expected) {
        context.waitFor(client -> {
            if (client.level == null || !(client.level.getEntity(id) instanceof AiMaidEntity maid)) return false;
            return armPose(client.getEntityRenderDispatcher().getRenderer(maid), maid) == expected;
        });
    }

    private <S extends EntityRenderState> HumanoidModel.ArmPose armPose(
            EntityRenderer<? super AiMaidEntity, S> renderer, AiMaidEntity maid) {
        S state = renderer.createRenderState();
        renderer.extractRenderState(maid, state, 1.0F);
        return ((HumanoidRenderState) state).rightArmPose;
    }
}
