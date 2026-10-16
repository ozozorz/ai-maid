package io.github.ozozorz.testaddon;

import io.github.ozozorz.aimaid.client.entity.animation.MaidAnimations;
import io.github.ozozorz.aimaid.client.entity.renderer.AiMaidEntityRenderer;
import io.github.ozozorz.aimaid.client.entity.state.AiMaidEntityRenderState;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ai.behavior.MaidRangedAttack;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.testaddon.animation.TestAddonAnimationIds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import static io.github.ozozorz.testaddon.AnimationPoseChecks.check;

/** 实体数据包、真实挥手/使用物品、追踪范围和 GUI 预览的客户端集成验收。 */
public class AnimationClientGameTest implements FabricClientGameTest {
    private record Scene(int maid, int other, int target, BlockPos base) {}

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
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 12; z++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int y = 1; y < 9; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                owner.setGameMode(GameType.CREATIVE);
                owner.connection.teleport(base.getX() + 0.5, base.getY() + 1, base.getZ() - 3.5, 0, 10);
                var maid = new AiMaidEntity(level);
                maid.setPos(base.getX() - 1, base.getY() + 1, base.getZ() + 2.5);
                maid.setTame(true, true); maid.setOwner(owner); maid.setNoAi(true);
                maid.setYRot(180); maid.setYBodyRot(180); maid.setYHeadRot(180);
                maid.selecetMaidCommand(MaidCommands.STAY);
                maid.setCustomName(Component.literal("Animation / armored"));
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                maid.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                maid.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                maid.getInventory().setItem(0, new ItemStack(Items.ARROW, 32));
                level.addFreshEntity(maid);
                var other = new AiMaidEntity(level);
                other.setPos(base.getX() + 2, base.getY() + 1, base.getZ() + 2.5);
                other.setTame(true, true); other.setOwner(owner); other.setNoAi(true);
                other.setYRot(180); other.setYBodyRot(180); other.setYHeadRot(180);
                other.setCustomName(Component.literal("Independent / idle"));
                other.selecetMaidCommand(MaidCommands.STAY);
                level.addFreshEntity(other);
                var target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                target.setPos(base.getX() - 1, base.getY() + 1, base.getZ() + 11);
                target.setNoAi(true); target.setInvulnerable(true);
                target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                level.addFreshEntity(target);
                return new Scene(maid.getId(), other.getId(), target.getId(), base);
            });
            context.waitFor(client -> client.level.getEntity(scene.maid()) instanceof AiMaidEntity
                    && client.level.getEntity(scene.other()) instanceof AiMaidEntity);
            context.waitTicks(5);
            context.runOnClient(AnimationPoseChecks::verify);
            context.takeScreenshot("animation-idle");

            int token = server.computeOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .playAnimationAction(TestAddonAnimationIds.LUMBER, 600));
            waitForAction(context, scene.maid(), TestAddonAnimationIds.LUMBER, 0.95F);
            context.runOnClient(client -> {
                var first = state(client, scene.maid());
                check(state(client, scene.other()).animation.layers().isEmpty(), "An idle maid inherited another maid's action");
                var repeat = state(client, scene.maid());
                check(first.animation.equals(repeat.animation), "Extracting the same frame twice advanced its transition");
                var renderer = (AiMaidEntityRenderer) client.getEntityRenderDispatcher().getRenderer(maid(client, scene.maid()));
                renderer.getModel().setupAnim(first);
                var pose = AnimationPoseChecks.capture(renderer.getModel());
                renderer.getModel().setupAnim(state(client, scene.other()));
                renderer.getModel().setupAnim(first);
                AnimationPoseChecks.equal(pose, AnimationPoseChecks.capture(renderer.getModel()), "Shared renderer leaked another maid's pose");
            });
            context.takeScreenshot("animation-lumber-armored");

            // 打开真实预览，并检查它没有把实体中的时钟/装备改写掉。
            server.runOnServer(minecraftServer -> {
                var owner = connection.getServerPlayer();
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                owner.connection.teleport(maid.getX(), maid.getY(), maid.getZ() - 3, 0, 8);
            });
            context.waitTicks(3);
            server.runOnServer(minecraftServer -> check(MaidMenuOpener.open(connection.getServerPlayer(),
                    (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())), "Cannot open animation preview"));
            context.waitFor(client -> client.player.containerMenu instanceof MaidMenu menu && menu.slots.size() == 77
                    && menu.getSelectedCommand() == MaidCommands.STAY);
            context.waitTicks(2);
            context.takeScreenshot("animation-menu-preview");
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(maid.getAnimationAction().sequence() == token && maid.getMainHandItem().is(Items.IRON_AXE), "Preview changed server animation or equipment");
            });
            context.runOnClient(client -> client.player.closeContainer());

            // 使用武器优先于工作动作；停止使用后恢复原工作时间轴。
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)));
            context.waitFor(client -> maid(client, scene.maid()).getMainHandItem().is(Items.BOW));
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .startUsingItem(InteractionHand.MAIN_HAND));
            waitForAction(context, scene.maid(), MaidAnimations.BOW, 0.95F);
            context.runOnClient(client -> check(state(client, scene.maid()).animation.action().equals(MaidAnimations.BOW)
                    && maid(client, scene.maid()).getAnimationAction().sequence() == token, "Weapon use did not override work without replacing its clock"));
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.stopUsingItem();
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            });
            waitForAction(context, scene.maid(), TestAddonAnimationIds.LUMBER, 0.95F);

            // 真正退出实体追踪范围再返回：客户端新实体应从原时间轴中途加入。
            long started = server.computeOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).getAnimationAction().startedAt());
            server.runOnServer(minecraftServer -> {
                // 此用例只测追踪变化，必须保留服务器实体；区块卸载则理应清理动作。
                minecraftServer.getCommands().performPrefixedCommand(minecraftServer.createCommandSourceStack(),
                        "forceload add " + (scene.base().getX() - 1) + " " + (scene.base().getZ() + 2));
                var owner = connection.getServerPlayer();
                owner.setGameMode(GameType.SPECTATOR);
                owner.connection.teleport(scene.base().getX() + 400, scene.base().getY() + 30, scene.base().getZ(), 0, 10);
            });
            context.waitFor(client -> client.level.getEntity(scene.maid()) == null);
            context.waitTicks(45);
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                check(maid != null && maid.getAnimationAction().sequence() == token
                        && maid.getAnimationAction().isActive(connection.getServerLevel().getGameTime()),
                        "Tracking fixture lost/expired the server action before returning");
            });
            server.runOnServer(minecraftServer -> connection.getServerPlayer().connection.teleport(
                    scene.base().getX() + 0.5, scene.base().getY() + 1, scene.base().getZ() - 3.5, 0, 10));
            waitForAction(context, scene.maid(), TestAddonAnimationIds.LUMBER, 0.95F);
            context.runOnClient(client -> {
                var action = maid(client, scene.maid()).getAnimationAction();
                check(action.startedAt() == started && action.sequence() == token
                        && action.elapsed(client.level.getGameTime(), 1) > 45, "Entering view restarted an existing animation");
            });
            server.runOnServer(minecraftServer -> {
                connection.getServerPlayer().setGameMode(GameType.CREATIVE);
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.stopAnimationAction(TestAddonAnimationIds.LUMBER, token);
                minecraftServer.getCommands().performPrefixedCommand(minecraftServer.createCommandSourceStack(),
                        "forceload remove " + (scene.base().getX() - 1) + " " + (scene.base().getZ() + 2));
            });
            context.waitFor(client -> state(client, scene.maid()).animation.layers().isEmpty());

            // 网络未知 ID 只回退表现；不应该让模型或界面崩溃。
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .playAnimationAction(Identifier.fromNamespaceAndPath("missing_addon", "clip"), 20));
            context.waitTicks(4);
            context.runOnClient(client -> check(state(client, scene.maid()).animation.layers().isEmpty(), "Unknown action must use the locomotion fallback"));
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.selecetMaidCommand(MaidCommands.STAY);
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            });
            context.waitTicks(3);
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).swing(InteractionHand.MAIN_HAND));
            waitForAction(context, scene.maid(), MaidAnimations.MELEE, 0.25F);
            context.takeScreenshot("animation-melee");
            context.waitTicks(12);

            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.setLeftHanded(true);
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            });
            context.waitFor(client -> maid(client, scene.maid()).getMainHandItem().is(Items.BOW)
                    && maid(client, scene.maid()).getMainArm() == HumanoidArm.LEFT);
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).startUsingItem(InteractionHand.MAIN_HAND));
            waitForAction(context, scene.maid(), MaidAnimations.BOW, 0.95F);
            context.runOnClient(client -> check(state(client, scene.maid()).animation.layers().stream()
                    .anyMatch(layer -> layer.id().equals(MaidAnimations.BOW) && layer.arm() == HumanoidArm.LEFT), "Left-handed bow selected the wrong arm"));
            context.takeScreenshot("animation-left-bow");
            context.waitTicks(22);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                maid.stopUsingItem();
                check(MaidRangedAttack.shootBow(level, maid, (net.minecraft.world.entity.LivingEntity) level.getEntity(scene.target()), 20), "Bow failed to shoot");
            });
            waitForAction(context, scene.maid(), MaidAnimationAction.BOW_RELEASE, 0.25F);
            context.takeScreenshot("animation-bow-release");
            context.waitTicks(15);

            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.setLeftHanded(false);
                maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            });
            context.waitFor(client -> maid(client, scene.maid()).getMainHandItem().is(Items.CROSSBOW));
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).startUsingItem(InteractionHand.MAIN_HAND));
            waitForAction(context, scene.maid(), MaidAnimations.CROSSBOW_CHARGE, 0.9F);
            context.takeScreenshot("animation-crossbow-charge");
            context.waitTicks(30);
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid())).stopUsingItem());
            waitForAction(context, scene.maid(), MaidAnimations.CROSSBOW_HOLD, 0.9F);
            context.takeScreenshot("animation-crossbow-hold");
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                check(MaidRangedAttack.shootCrossbow(level, (AiMaidEntity) level.getEntity(scene.maid()),
                        (net.minecraft.world.entity.LivingEntity) level.getEntity(scene.target())), "Crossbow failed to shoot");
            });
            waitForAction(context, scene.maid(), MaidAnimationAction.CROSSBOW_FIRE, 0.25F);
            context.takeScreenshot("animation-crossbow-fire");
            context.waitTicks(12);

            // 原版导航、速度包和受伤包确实驱动移动/空中/受伤层。
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                // 明确建立远距离导航；不依赖闲逛行为的随机调度来启动移动。
                connection.getServerPlayer().connection.teleport(scene.base().getX() + 0.5,
                        scene.base().getY() + 1, scene.base().getZ() - 14.5, 0, 8);
                maid.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.15);
                maid.setNoAi(false);
                maid.selecetMaidCommand(MaidCommands.FOLLOW);
            });
            float[] maximumSpeed = {0};
            try {
                context.waitFor(client -> {
                    float speed = state(client, scene.maid()).walkAnimationSpeed;
                    maximumSpeed[0] = Math.max(maximumSpeed[0], speed);
                    return speed > 0.12F;
                });
            } catch (AssertionError failure) {
                String diagnostic = server.computeOnServer(minecraftServer -> {
                    var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                    return "maid=" + maid.position() + ", owner=" + connection.getServerPlayer().position()
                            + ", command=" + maid.getMaidCommandId() + ", activity=" + maid.getBrain().getActiveNonCoreActivity()
                            + ", target=" + maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                            + ", path=" + maid.getNavigation().getPath() + ", grounded=" + maid.onGround() + ", noAI=" + maid.isNoAi();
                });
                throw new AssertionError("Movement sampling: maximum=" + maximumSpeed[0] + "; " + diagnostic, failure);
            }
            context.takeScreenshot("animation-walk");
            // 恢复女仆正常移动属性，仍沿同一导航路径验证实际速度驱动的过渡。
            server.runOnServer(minecraftServer -> ((AiMaidEntity) connection.getServerLevel().getEntity(scene.maid()))
                    .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.3));
            context.waitFor(client -> state(client, scene.maid()).animation.run() > 0.25F);
            context.takeScreenshot("animation-fast-movement");
            server.runOnServer(minecraftServer -> {
                var maid = (AiMaidEntity) connection.getServerLevel().getEntity(scene.maid());
                maid.selecetMaidCommand(MaidCommands.STAY);
                connection.getServerPlayer().connection.teleport(maid.getX(), maid.getY(), maid.getZ() - 5, 0, 8);
                // NoAI 会连原版运动模拟一起关闭；此处要实际起跳并落地。
                maid.setNoAi(false);
                maid.setOnGround(false);
                maid.setDeltaMovement(new Vec3(0, 0.65, 0));
                maid.hurtMarked = true;
            });
            context.waitFor(client -> state(client, scene.maid()).animation.airborne() > 0.4F);
            context.takeScreenshot("animation-jump");
            context.waitFor(client -> maid(client, scene.maid()).onGround());
            context.waitTicks(10);
            BlockPos pool = server.computeOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                var pos = maid.blockPosition();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(pos.offset(x, 0, z), Blocks.WATER.defaultBlockState());
                }
                return pos;
            });
            context.waitFor(client -> state(client, scene.maid()).isInWater && state(client, scene.maid()).animation.water() > 0.5F);
            context.takeScreenshot("animation-water");
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    if (level.getBlockState(pool.offset(x, 0, z)).is(Blocks.WATER)) {
                        level.setBlockAndUpdate(pool.offset(x, 0, z), Blocks.AIR.defaultBlockState());
                    }
                }
            });
            context.waitTicks(10);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                maid.invulnerableTime = 0;
                check(maid.hurtServer(level, level.damageSources().generic(), 1), "Hurt test must deal real damage");
            });
            context.waitFor(client -> state(client, scene.maid()).animation.hurt() > 0.2F);
            context.takeScreenshot("animation-hurt");
            context.waitTicks(12);
            server.runOnServer(minecraftServer -> {
                var level = connection.getServerLevel();
                var maid = (AiMaidEntity) level.getEntity(scene.maid());
                maid.invulnerableTime = 0;
                maid.playAnimationAction(TestAddonAnimationIds.LUMBER, 100);
                maid.hurtServer(level, level.damageSources().generic(), 1000);
            });
            context.waitFor(client -> state(client, scene.maid()).deathTime > 2);
            context.runOnClient(client -> check(state(client, scene.maid()).animation.layers().isEmpty(), "Death retained an active action layer"));
            context.takeScreenshot("animation-death");
        }
    }

    private static AiMaidEntity maid(Minecraft client, int id) { return (AiMaidEntity) client.level.getEntity(id); }
    private static AiMaidEntityRenderState state(Minecraft client, int id) {
        var maid = maid(client, id);
        var renderer = (AiMaidEntityRenderer) client.getEntityRenderDispatcher().getRenderer(maid);
        var state = renderer.createRenderState();
        renderer.extractRenderState(maid, state, 1);
        return state;
    }
    private static void waitForAction(ClientGameTestContext context, int id, Identifier action, float minimumWeight) {
        context.waitFor(client -> client.level.getEntity(id) instanceof AiMaidEntity
                && state(client, id).animation.layers().stream().anyMatch(layer -> layer.id().equals(action) && layer.weight() > minimumWeight));
    }
}
