package io.github.ozozorz.testaddon;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import io.github.ozozorz.testaddon.combat.CombatRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 在真实服务端 tick 中验收行为，不直接调用 Sensor/Behavior 绕过调度。 */
public class CombatGameTests {
    private record Arena(AiMaidEntity maid, Mob owner, Mob enemy) {}

    private Arena arena(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.STONE);
        Mob owner = h.spawn(EntityTypes.VILLAGER, 1.5F, 1.0F, 1.5F);
        owner.setNoAi(true);
        AiMaidEntity maid = h.spawn(ModEntityTypes.AI_MAID, 1.5F, 1.0F, 3.5F);
        maid.setTame(true, true);
        maid.setOwner(owner);
        maid.selecetMaidCommand(TestAddonCommands.GUARD);
        Mob enemy = h.spawn(EntityTypes.ZOMBIE, 6.5F, 1.0F, 3.5F);
        enemy.setNoAi(true);
        enemy.setPersistenceRequired();
        // Barrier 测试边界不保证遮光，排除日晒造成的假阳性伤害。
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        enemy.setHealth(100.0F);
        return new Arena(maid, owner, enemy);
    }

    private void provoke(GameTestHelper h, Arena a) {
        h.runAfterDelay(5, () -> h.hurt(a.maid(), h.getLevel().damageSources().mobAttack(a.enemy()), 1.0F));
    }

    private LivingEntity target(AiMaidEntity maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    @GameTest(maxTicks = 60)
    public void guardDoesNotHuntUnprovoked(GameTestHelper h) {
        Arena a = arena(h);
        h.runAfterDelay(40, () -> {
            h.assertTrue(target(a.maid()) == null && a.enemy().getHealth() == 100, "Guard must remain peaceful without a trigger");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 180)
    public void selfDefenseNavigatesAndDamages(GameTestHelper h) {
        Arena a = arena(h);
        provoke(h, a);
        h.succeedWhen(() -> h.assertTrue(a.enemy().getHealth() < 100, "Self defense must deal real melee damage"));
    }

    @GameTest(maxTicks = 180)
    public void defendsInjuredOwner(GameTestHelper h) {
        Arena a = arena(h);
        h.runAfterDelay(5, () -> h.hurt(a.owner(), h.getLevel().damageSources().mobAttack(a.enemy()), 1.0F));
        h.succeedWhen(() -> h.assertTrue(a.enemy().getHealth() < 100, "Owner damage must trigger defense"));
    }

    @GameTest(maxTicks = 180)
    public void assistsOwnersAttack(GameTestHelper h) {
        Arena a = arena(h);
        h.runAfterDelay(5, () -> a.owner().setLastHurtMob(a.enemy()));
        h.succeedWhen(() -> h.assertTrue(a.enemy().getHealth() < 100, "Owner attack record must trigger assistance"));
    }

    @GameTest(maxTicks = 180)
    public void combatCommandHuntsHostiles(GameTestHelper h) {
        Arena a = arena(h);
        a.maid().selecetMaidCommand(TestAddonCommands.ATTACK);
        h.succeedWhen(() -> h.assertTrue(a.enemy().getHealth() < 100, "Combat command must acquire a visible hostile"));
    }

    @GameTest(maxTicks = 180)
    public void bowConsumesAmmoAndDamages(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack bow = new ItemStack(Items.BOW);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, bow);
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.enemy().getHealth() < 100, "Bow projectile must hit the target");
            h.assertTrue(a.maid().getInventory().getItem(0).getCount() < 8, "Bow must consume real ammo");
            h.assertTrue(bow.getDamageValue() > 0, "Bow must consume durability");
        });
    }

    @GameTest(maxTicks = 200)
    public void crossbowLoadsConsumesAndDamages(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack bow = new ItemStack(Items.CROSSBOW);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, bow);
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.enemy().getHealth() < 100, "Crossbow projectile must hit");
            h.assertTrue(a.maid().getInventory().getItem(0).getCount() < 8, "Crossbow loading must consume ammo");
            h.assertTrue(bow.getDamageValue() > 0, "Crossbow shot must consume durability");
        });
    }

    @GameTest(maxTicks = 80)
    public void fullInventorySwapPreservesBothStacks(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack bow = new ItemStack(Items.BOW);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Acceptance blade"));
        sword.setDamageValue(3);
        for (int slot = 0; slot < 35; slot++) a.maid().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        a.maid().getInventory().setItem(0, sword);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, bow);
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.maid().getMainHandItem() == sword, "Empty bow must switch to a real sword even with full storage");
            h.assertTrue(a.maid().getInventory().getItem(0) == bow, "Old bow must be preserved in the vacated slot");
            h.assertTrue(a.maid().getMainHandItem().get(DataComponents.CUSTOM_NAME).getString().equals("Acceptance blade"), "Components must survive transfer");
        });
    }

    @GameTest(maxTicks = 220)
    public void ammunitionExhaustionSwitchesToMelee(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack bow = new ItemStack(Items.BOW);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, bow);
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW));
        a.maid().getInventory().setItem(1, sword);
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.maid().getInventory().getItem(0).isEmpty(), "The final arrow must be consumed");
            h.assertTrue(a.maid().getMainHandItem() == sword && sword.getDamageValue() > 0,
                    "After the final shot, equip the sword and land a melee hit");
            h.assertTrue(a.maid().getInventory().getItem(1) == bow, "Empty bow must survive switching");
        });
    }

    @GameTest(maxTicks = 220)
    public void multishotConsumesOneArrowAndWearsCrossbow(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(h.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT), 1);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(crossbow.getDamageValue() == 3, "Multishot must fire three real projectiles");
            h.assertTrue(a.maid().getInventory().getItem(0).getCount() == 7,
                    "Three projectiles must only consume one inventory arrow");
        });
    }

    @GameTest(maxTicks = 200)
    public void brokenMeleeWeaponGetsReplaced(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack iron = new ItemStack(Items.IRON_SWORD);
        iron.setDamageValue(iron.getMaxDamage() - 1);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, iron);
        a.maid().getInventory().setItem(0, new ItemStack(Items.STONE_SWORD));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(iron.isEmpty(), "Successful melee hit must break the worn sword");
            h.assertTrue(a.maid().getMainHandItem().is(Items.STONE_SWORD), "Spare sword must be equipped");
        });
    }

    @GameTest(maxTicks = 120)
    public void switchingCommandCancelsBowDraw(GameTestHelper h) {
        Arena a = arena(h);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW, 8));
        provoke(h, a);
        boolean[] switched = {false};
        h.onEachTick(() -> {
            if (!switched[0] && a.maid().isUsingItem()) {
                switched[0] = true;
                a.maid().selecetMaidCommand(MaidCommands.STAY);
                h.runAfterDelay(35, () -> {
                    h.assertTrue(!a.maid().isUsingItem() && target(a.maid()) == null, "Leaving combat must cancel charge and target");
                    h.assertTrue(a.maid().getInventory().getItem(0).getCount() == 8, "Cancelled draw consumed ammo: " + a.maid().getInventory().getItem(0));
                    h.assertTrue(a.enemy().getHealth() == 100, "Cancelled draw target health: " + a.enemy().getHealth());
                    h.succeed();
                });
            }
        });
    }

    @GameTest(maxTicks = 70)
    public void leavingCombatPreservesLoadedCrossbow(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        h.runAfterDelay(10, () -> a.maid().selecetMaidCommand(MaidCommands.STAY));
        h.runAfterDelay(30, () -> {
            h.assertTrue(!crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty(), "Paid ammunition stays inside the stopped crossbow");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 240)
    public void blockedTargetIsAbandonedWithoutOldHitLoop(GameTestHelper h) {
        Arena a = arena(h);
        a.maid().getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        provoke(h, a);
        h.runAfterDelay(15, () -> {
            for (int y = 1; y < 7; y++) for (int z = 0; z < 8; z++) h.setBlock(4, y, z, Blocks.STONE);
        });
        h.runAfterDelay(180, () -> {
            h.assertTrue(target(a.maid()) == null, "Lost sight target must be abandoned");
            h.runAfterDelay(30, () -> {
                h.assertTrue(target(a.maid()) == null, "Old damage must not re-create the target");
                h.succeed();
            });
        });
    }

    @GameTest(maxTicks = 50)
    public void ownerLeavingStopsCombat(GameTestHelper h) {
        Arena a = arena(h);
        provoke(h, a);
        h.runAfterDelay(12, () -> a.owner().discard());
        h.runAfterDelay(20, () -> {
            h.assertTrue(target(a.maid()) == null && !a.maid().isUsingItem(), "Owner disappearance must end existing combat");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 50)
    public void twoMaidsHaveIndependentTargets(GameTestHelper h) {
        Arena a = arena(h);
        AiMaidEntity second = h.spawn(ModEntityTypes.AI_MAID, 1.5F, 1.0F, 6.5F);
        second.setTame(true, true);
        second.setOwner(a.owner());
        second.selecetMaidCommand(TestAddonCommands.GUARD);
        Mob otherEnemy = h.spawn(EntityTypes.ZOMBIE, 6.5F, 1.0F, 6.5F);
        otherEnemy.setNoAi(true);
        provoke(h, a);
        h.runAfterDelay(5, () -> h.hurt(second, h.getLevel().damageSources().mobAttack(otherEnemy), 1.0F));
        h.runAfterDelay(10, () -> {
            h.assertTrue(target(a.maid()) == a.enemy() && target(second) == otherEnemy, "Targets must not leak between Brain instances");
            h.succeed();
        });
    }

    @GameTest
    public void friendlyProjectilesCannotDamagePetsOrOwner(GameTestHelper h) {
        Arena a = arena(h);
        TamableAnimal pet = h.spawn(EntityTypes.WOLF, 3.5F, 1.0F, 3.5F);
        pet.setTame(true, true);
        pet.setOwner(a.owner());
        float petHealth = pet.getHealth();
        float ownerHealth = a.owner().getHealth();
        Arrow arrow = new Arrow(h.getLevel(), a.maid(), new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        h.hurt(pet, h.getLevel().damageSources().arrow(arrow, a.maid()), 6.0F);
        h.hurt(a.owner(), h.getLevel().damageSources().arrow(arrow, a.maid()), 6.0F);
        h.assertTrue(pet.getHealth() == petHealth && a.owner().getHealth() == ownerHealth, "Friendly projectile damage must be rejected");
        h.assertTrue(!CombatRules.canTarget(a.maid(), h.makeMockPlayer(GameType.SURVIVAL)), "PvE must exclude all players");
        h.assertTrue(!CombatRules.clearShot(a.maid(), a.enemy()), "Pet in firing lane must block a shot");
        h.succeed();
    }

    @GameTest(maxTicks = 50)
    public void rangeLimitStopsChase(GameTestHelper h) {
        Arena a = arena(h);
        provoke(h, a);
        h.runAfterDelay(12, () -> {
            Vec3 far = h.absoluteVec(new Vec3(40, 1, 3));
            a.enemy().snapTo(far.x, far.y, far.z, 0, 0);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(target(a.maid()) == null, "Out-of-bounds target must be dropped");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 220)
    public void infinityBowPreservesItsRequiredArrow(GameTestHelper h) {
        Arena a = arena(h);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY), 1);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, bow);
        a.maid().getInventory().setItem(0, new ItemStack(Items.ARROW));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.enemy().getHealth() < 100, "Infinity arrow must hit");
            h.assertTrue(a.maid().getInventory().getItem(0).getCount() == 1, "Vanilla infinity must retain the real arrow");
        });
    }

    @GameTest(maxTicks = 220)
    public void spectralArrowRetainsItsEffect(GameTestHelper h) {
        Arena a = arena(h);
        a.maid().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        a.maid().getInventory().setItem(0, new ItemStack(Items.SPECTRAL_ARROW, 4));
        provoke(h, a);
        h.succeedWhen(() -> {
            h.assertTrue(a.enemy().hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Selected spectral ammo must create a spectral arrow");
            h.assertTrue(a.maid().getInventory().getItem(0).getCount() < 4, "Spectral ammo must be consumed");
        });
    }

    @GameTest(maxTicks = 70)
    public void oldInjuryDoesNotStartNewGuardSession(GameTestHelper h) {
        Arena a = arena(h);
        a.maid().selecetMaidCommand(MaidCommands.STAY);
        h.runAfterDelay(5, () -> h.hurt(a.maid(), h.getLevel().damageSources().mobAttack(a.enemy()), 1.0F));
        h.runAfterDelay(10, () -> a.maid().selecetMaidCommand(TestAddonCommands.GUARD));
        h.runAfterDelay(40, () -> {
            h.assertTrue(target(a.maid()) == null, "Entering guard must not replay earlier injury");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 250)
    public void visibleUnreachableHostileGetsRetryCooldown(GameTestHelper h) {
        Arena a = arena(h);
        for (int x = 5; x < 8; x++) for (int z = 2; z < 5; z++) h.setBlock(x, 4, z, Blocks.STONE);
        Vec3 high = h.absoluteVec(new Vec3(6.5, 5, 3.5));
        a.enemy().snapTo(high.x, high.y, high.z, 0, 0);
        a.maid().selecetMaidCommand(TestAddonCommands.ATTACK);
        boolean[] acquired = {false};
        h.onEachTick(() -> { if (target(a.maid()) == a.enemy()) acquired[0] = true; });
        h.runAfterDelay(220, () -> {
            h.assertTrue(acquired[0], "Test must first acquire the visible hostile");
            h.assertTrue(target(a.maid()) == null, "Reachable-looking but inaccessible target must enter backoff");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void deadTargetClearsCombatState(GameTestHelper h) {
        Arena a = arena(h);
        provoke(h, a);
        h.runAfterDelay(12, () -> h.kill(a.enemy()));
        h.runAfterDelay(25, () -> {
            h.assertTrue(target(a.maid()) == null && !a.maid().isAggressive() && !a.maid().isUsingItem(), "Dead targets must release combat state");
            h.succeed();
        });
    }
}
