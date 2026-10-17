package io.github.ozozorz.aimaid.integration;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.farming.*;
import io.github.ozozorz.aimaid.feeding.*;
import io.github.ozozorz.aimaid.hauling.*;
import io.github.ozozorz.aimaid.lumber.LumberAddon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class FormalAddonGameTests {
    private AiMaidEntity worker(GameTestHelper h, MaidCommand command) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,0,z,Blocks.STONE);
        var maid=h.spawn(ModEntityTypes.AI_MAID,3.5F,1,4.5F);maid.setTame(true,true);
        maid.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));maid.selectMaidCommand(command);return maid;
    }
    private void defend(GameTestHelper h,AiMaidEntity maid,Runnable duringFight) {
        var enemy=h.spawn(EntityTypes.ZOMBIE,5.5F,1,4.5F);enemy.setNoAi(true);enemy.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.NETHERITE_HELMET));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);enemy.setHealth(200);
        var command=maid.getMaidCommand();
        h.runAfterDelay(10,()->maid.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(enemy),1));
        h.runAfterDelay(20,()->{
            h.assertTrue(maid.getTarget()==enemy&&maid.getMaidCommand()==command,"Work did not enter self defense");duringFight.run();
        });
        h.runAfterDelay(35,()->enemy.discard());
    }
    @GameTest(maxTicks=150) public void farmingResumesAfterSelfDefense(GameTestHelper h) {
        var maid=worker(h,FarmingAddon.COMMAND);
        defend(h,maid,()->{h.setBlock(4,0,3,Blocks.FARMLAND);h.setBlock(4,1,3,((CropBlock)Blocks.WHEAT).getStateForAge(7));});
        h.succeedWhen(()->h.assertTrue(maid.getInventory().countMatching(s->s.is(Items.WHEAT))==1&&maid.getTarget()==null&&maid.getMaidCommand()==FarmingAddon.COMMAND,"Farming not resumed"));
    }
    @GameTest(maxTicks=150) public void feedingResumesAfterSelfDefense(GameTestHelper h) {
        var maid=worker(h,FeedingAddon.COMMAND);var cow=h.spawn(EntityTypes.COW,2.5F,1,4.5F);cow.setNoAi(true);
        defend(h,maid,()->{maid.getInventory().setItem(0,new ItemStack(Items.WHEAT,2));h.assertTrue(!cow.isInLove(),"Fed during combat");});
        h.succeedWhen(()->h.assertTrue(cow.isInLove()&&maid.getInventory().getItem(0).getCount()==1&&maid.getTarget()==null,"Feeding not resumed"));
    }
    @GameTest(maxTicks=260) public void haulingResumesAfterSelfDefense(GameTestHelper h) {
        var maid=worker(h,HaulingAddon.COMMAND);maid.setAttached(HaulingData.RADIUS,2);
        h.setBlock(2,1,3,Blocks.COPPER_CHEST.weathering().unaffected());h.setBlock(4,1,3,Blocks.CHEST);
        var source=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2,1,3)));
        var dest=(ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4,1,3)));
        defend(h,maid,()->source.setItem(0,new ItemStack(Items.APPLE,8)));
        h.succeedWhen(()->h.assertTrue(dest.getItem(0).is(Items.APPLE)&&dest.getItem(0).getCount()==8&&maid.getTarget()==null,"Hauling not resumed"));
    }
    @GameTest public void retiredIdsMigrateOnlyWhenReplacementExists(GameTestHelper h) {
        var maid=worker(h,MaidCommands.STAY);
        String[] ids={"testaddon:guard","testaddon:attack","testaddon:lumber","testaddon:patrol"};
        MaidCommand[] commands={MaidCommands.FOLLOW,MaidCommands.ATTACK,LumberAddon.LUMBER,MaidCommands.FOLLOW};
        for(int i=0;i<ids.length;i++) {
            var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());maid.saveWithoutId(out);
            var tag=out.buildResult();tag.putString("MaidCommand",ids[i]);
            var loaded=new AiMaidEntity(h.getLevel());loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag));
            h.assertTrue(loaded.getMaidCommand()==commands[i]&&loaded.getMaidCommandId().equals(commands[i].getRegistryId()),"Legacy ID not migrated: "+ids[i]);
        }
        h.assertTrue(!FabricLoader.getInstance().isModLoaded("testaddon"),"Retired TestAddon was loaded");h.succeed();
    }
}
