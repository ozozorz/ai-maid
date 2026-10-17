package io.github.ozozorz.aimaid;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class DeathLootGameTests {
    private AiMaidEntity maid(GameTestHelper h) {
        h.setBlock(4, 0, 4, Blocks.STONE);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 4.5F, 1, 4.5F);
        maid.setNoAi(true);
        return maid;
    }

    private List<ItemStack> drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds())
                .stream().map(ItemEntity::getItem).toList();
    }

    @GameTest
    public void environmentDeathDropsEveryStorageAndEquipmentSlot(GameTestHelper h) {
        var maid = maid(h);
        for (int i = 0; i < 35; i++) maid.getInventory().setItem(i, new ItemStack(Items.DIAMOND, i + 1));
        var loaded = new ItemStack(Items.CROSSBOW);
        loaded.setDamageValue(47);
        loaded.set(DataComponents.CUSTOM_NAME, Component.literal("Loaded heirloom"));
        loaded.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.SPECTRAL_ARROW))));
        maid.setItemSlot(EquipmentSlot.MAINHAND, loaded);
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 13));
        maid.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        maid.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        maid.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        maid.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        ItemStack snapshot = loaded.copy();
        h.hurt(maid, h.getLevel().damageSources().fall(), 1000);
        h.assertTrue(maid.isDeadOrDying(), "Fixture must actually die");
        h.assertTrue(drops(h).size() == 41, "All 35 storage and six equipment slots must produce one drop");
        h.assertTrue(drops(h).stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 630,
                "Storage quantities must survive death");
        h.assertTrue(drops(h).stream().anyMatch(s -> ItemStack.matches(snapshot, s)), "Loaded crossbow components and durability changed");
        h.assertTrue(maid.getInventory().isEmpty() && maid.getMainHandItem().isEmpty(), "Drop source must be emptied");
        maid.die(h.getLevel().damageSources().fall());
        h.assertTrue(drops(h).size() == 41, "Repeated death duplicated inventory");
        h.succeed();
    }

    @GameTest
    public void monsterKillDropsCarriedEquipmentWithoutPlayerCredit(GameTestHelper h) {
        var maid = maid(h);
        var zombie = h.spawn(EntityTypes.ZOMBIE, 6.5F, 1, 4.5F);
        zombie.setNoAi(true);
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(9);
        maid.setItemSlot(EquipmentSlot.MAINHAND, sword);
        h.hurt(maid, h.getLevel().damageSources().mobAttack(zombie), 1000);
        h.assertTrue(drops(h).stream().anyMatch(s -> ItemStack.matches(s, sword)), "Monster kill must preserve carried equipment");
        h.succeed();
    }

    @GameTest
    public void vanishingAppliesToStorageAndEquipmentButBindingStillDrops(GameTestHelper h) {
        var maid = maid(h);
        var enchantments = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(enchantments.getOrThrow(Enchantments.VANISHING_CURSE), 1);
        var helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(enchantments.getOrThrow(Enchantments.VANISHING_CURSE), 1);
        var boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.enchant(enchantments.getOrThrow(Enchantments.BINDING_CURSE), 1);
        maid.getInventory().setItem(0, sword);
        maid.setItemSlot(EquipmentSlot.HEAD, helmet);
        maid.setItemSlot(EquipmentSlot.FEET, boots);
        h.hurt(maid, h.getLevel().damageSources().fall(), 1000);
        h.assertTrue(drops(h).size() == 1 && ItemStack.matches(drops(h).getFirst(), boots), "Wrong curse death policy");
        h.assertTrue(maid.getInventory().isEmpty() && maid.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Vanishing items must also leave source slots");
        h.succeed();
    }

    @GameTest
    public void playerGameRulesDoNotDeleteMaidPossessions(GameTestHelper h) {
        var maid = maid(h);
        maid.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        var rules = h.getLevel().getGameRules();
        boolean drops = rules.get(GameRules.MOB_DROPS), keep = rules.get(GameRules.KEEP_INVENTORY);
        // 同一服务端调用内还原，不让并行场景观察到临时规则。
        try {
            rules.set(GameRules.MOB_DROPS, false, h.getLevel().getServer());
            rules.set(GameRules.KEEP_INVENTORY, true, h.getLevel().getServer());
            h.hurt(maid, h.getLevel().damageSources().fall(), 1000);
            h.assertTrue(drops(h).size() == 2, "Carried items must drop independently of player keepInventory/mob loot");
        } finally {
            rules.set(GameRules.MOB_DROPS, drops, h.getLevel().getServer());
            rules.set(GameRules.KEEP_INVENTORY, keep, h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest
    public void saveAndUnloadPreserveItemsWithoutDropping(GameTestHelper h) {
        var maid = maid(h);
        var axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(12);
        axe.set(DataComponents.CUSTOM_NAME, Component.literal("Persistent tool"));
        maid.getInventory().setItem(34, new ItemStack(Items.DIAMOND, 17));
        maid.setItemSlot(EquipmentSlot.MAINHAND, axe);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        maid.saveWithoutId(output);
        maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(drops(h).isEmpty(), "Unload must not drop inventory");
        var restored = new AiMaidEntity(h.getLevel());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(restored.getInventory().getItem(34).getCount() == 17, "Storage slot did not survive save/load");
        h.assertTrue(ItemStack.matches(restored.getMainHandItem(), axe), "Equipment did not survive save/load");
        h.succeed();
    }
}
