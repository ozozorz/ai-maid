package io.github.ozozorz.aimaid.farming;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.gamerules.GameRules;

public class FarmingGameTests {
    private AiMaidEntity worker(GameTestHelper h) {
        for (int x=0;x<8;x++) for (int z=0;z<8;z++) h.setBlock(x,0,z,Blocks.DIRT);
        var maid=h.spawn(ModEntityTypes.AI_MAID,3.5F,1,3.5F);maid.setTame(true,true);
        maid.selectMaidCommand(FarmingAddon.COMMAND);return maid;
    }
    private void crop(GameTestHelper h,int x,int z,CropBlock crop,int age) {
        h.setBlock(new BlockPos(x,0,z),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7));
        h.setBlock(new BlockPos(x,1,z),crop.getStateForAge(age));
    }
    @GameTest(maxTicks=180) public void harvestsAllFourCropsAndReplants(GameTestHelper h) {
        var maid=worker(h);
        var crops=new CropBlock[]{(CropBlock)Blocks.WHEAT,(CropBlock)Blocks.CARROTS,(CropBlock)Blocks.POTATOES,(CropBlock)Blocks.BEETROOTS};
        for(int i=0;i<4;i++) crop(h,2+i,5,crops[i],crops[i].getMaxAge());
        h.succeedWhen(() -> {
            for(int i=0;i<4;i++) {
                var state=h.getBlockState(new BlockPos(2+i,1,5));
                h.assertTrue(state.getBlock()==crops[i]&&!crops[i].isMaxAge(state),"Crop not replanted");
            }
            h.assertTrue(maid.getInventory().countMatching(s->s.is(Items.WHEAT))>=1,"Harvest missing");
        });
    }
    @GameTest(maxTicks=100) public void plantsEmptyFarmlandWithExactlyOneSeed(GameTestHelper h) {
        var maid=worker(h);maid.getInventory().setItem(0,new ItemStack(Items.WHEAT_SEEDS,3));
        h.setBlock(new BlockPos(4,0,3),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7));
        h.succeedWhen(() -> {h.assertBlockPresent(Blocks.WHEAT,new BlockPos(4,1,3));
            h.assertTrue(maid.getInventory().getItem(0).getCount()==2,"Incorrect seed cost");});
    }
    @GameTest(maxTicks=100) public void immatureAndNonFarmlandAreNotDestroyed(GameTestHelper h) {
        var maid=worker(h);crop(h,4,3,(CropBlock)Blocks.WHEAT,1);maid.getInventory().setItem(0,new ItemStack(Items.CARROT,3));
        h.runAfterDelay(60,()->{h.assertBlockPresent(Blocks.WHEAT,new BlockPos(4,1,3));
            h.assertTrue(maid.getInventory().getItem(0).getCount()==3,"Idle farming spent seeds");
            h.assertBlockPresent(Blocks.DIRT,new BlockPos(3,0,4));h.succeed();});
    }
    @GameTest(maxTicks=100) public void fullInventoryLeavesMatureCropIntact(GameTestHelper h) {
        var maid=worker(h);for(int i=0;i<35;i++)maid.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        crop(h,4,3,(CropBlock)Blocks.WHEAT,7);
        h.runAfterDelay(60,()->{h.assertTrue(((CropBlock)Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(4,1,3))),"Full inventory destroyed harvest");h.succeed();});
    }
    @GameTest public void gameruleBlocksMutation(GameTestHelper h) {
        var maid=worker(h);crop(h,4,3,(CropBlock)Blocks.WHEAT,7);
        var rules=h.getLevel().getGameRules();boolean before=rules.get(GameRules.MOB_GRIEFING);
        try {rules.set(GameRules.MOB_GRIEFING,false,h.getLevel().getServer());var b=new FarmingBehavior();b.tryStart(h.getLevel(),maid,0);b.tickOrStop(h.getLevel(),maid,1);
            h.assertTrue(FarmingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-farming.disabled")),"Rule not enforced");
        } finally {rules.set(GameRules.MOB_GRIEFING,before,h.getLevel().getServer());}
        h.succeed();
    }
    @GameTest(maxTicks=100) public void switchingToStayStopsCultivation(GameTestHelper h) {
        var maid=worker(h);maid.getInventory().setItem(0,new ItemStack(Items.CARROT,4));
        maid.selectMaidCommand(MaidCommands.STAY);
        h.setBlock(new BlockPos(4,0,3),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7));
        h.runAfterDelay(60,()->{h.assertBlockPresent(Blocks.AIR,new BlockPos(4,1,3));h.assertTrue(maid.getInventory().getItem(0).getCount()==4,"Old work continued");h.succeed();});
    }
    @GameTest(maxTicks=100) public void radiusBoundsAreRespected(GameTestHelper h) {
        var maid=worker(h);maid.setAttached(FarmingData.RADIUS,2);crop(h,7,7,(CropBlock)Blocks.WHEAT,7);
        h.runAfterDelay(60,()->{h.assertTrue(((CropBlock)Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(7,1,7))),"Worked outside area");h.succeed();});
    }
    @GameTest(maxTicks=100) public void twoWorkersDoNotDuplicateHarvest(GameTestHelper h) {
        var first=worker(h);var second=worker(h);crop(h,4,3,(CropBlock)Blocks.WHEAT,7);
        h.runAfterDelay(70,()->{h.assertTrue(first.getInventory().countMatching(s->s.is(Items.WHEAT))+second.getInventory().countMatching(s->s.is(Items.WHEAT))==1,"Duplicated crop loot");h.succeed();});
    }
    @GameTest(maxTicks=100) public void nearbyPatchIsWorkedContinuously(GameTestHelper h) {
        var maid=worker(h);
        for(int x=2;x<=4;x++)crop(h,x,4,(CropBlock)Blocks.WHEAT,7);
        long[] last={-1};int[] harvested={0};
        h.onEachTick(()->{
            int current=maid.getInventory().countMatching(s->s.is(Items.WHEAT));
            if(current>harvested[0]) {
                if(last[0]>=0)h.assertTrue(h.getLevel().getGameTime()-last[0]<=12,"Nearby crops still have a fixed idle gap");
                last[0]=h.getLevel().getGameTime();harvested[0]=current;
            }
        });
        h.succeedWhen(()->h.assertTrue(harvested[0]==3,"Patch did not finish"));
    }
    @GameTest(maxTicks=100) public void interactionKeepsLookingAtUnmodifiedCrop(GameTestHelper h) {
        var maid=worker(h);crop(h,4,3,(CropBlock)Blocks.WHEAT,7);boolean[] prepared={false};
        h.onEachTick(()->{
            if(FarmingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-farming.preparing"))) {
                var look=maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).orElse(null);
                h.assertTrue(look!=null&&look.currentBlockPosition().equals(h.absolutePos(new BlockPos(4,1,3))),"Interaction lost its look target");
                h.assertTrue(!maid.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET),"Interaction was moving");
                h.assertTrue(((CropBlock)Blocks.WHEAT).isMaxAge(h.getBlockState(new BlockPos(4,1,3))),"Crop changed before interaction completed");
                prepared[0]=true;
            }
        });
        h.succeedWhen(()->h.assertTrue(prepared[0]&&maid.getInventory().countMatching(s->s.is(Items.WHEAT))==1,"No real preparation/harvest sequence"));
    }
    @GameTest(maxTicks=290) public void supplyWakesWorkerDuringLongSearchBackoff(GameTestHelper h) {
        var maid=worker(h);maid.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
        h.setBlock(new BlockPos(4,0,3),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7));
        h.runAfterDelay(230,()->maid.getInventory().setItem(0,new ItemStack(Items.WHEAT_SEEDS,1)));
        h.runAfterDelay(250,()->{h.assertBlockPresent(Blocks.WHEAT,new BlockPos(4,1,3));h.assertTrue(maid.getInventory().isEmpty(),"Supply was not consumed once");h.succeed();});
    }
    @GameTest(maxTicks=300) public void waitingLooksAndStrollsInsideWorkArea(GameTestHelper h) {
        var maid=worker(h);maid.setAttached(FarmingData.RADIUS,2);boolean[] looked={false},walked={false};
        h.onEachTick(()->{
            var brain=maid.getBrain();
            looked[0]|=brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            walked[0]|=brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w->w.getSpeedModifier()==0.45F).orElse(false);
            var p=maid.blockPosition();var c=maid.getAttached(FarmingData.CENTER).pos();
            h.assertTrue(Math.abs(p.getX()-c.getX())<=2&&Math.abs(p.getZ()-c.getZ())<=2,"Idle stroll escaped work area");
        });
        h.runAfterDelay(260,()->{h.assertTrue(looked[0]&&walked[0],"Waiting worker remained frozen");h.succeed();});
    }
    @GameTest(maxTicks=160) public void removingCropDuringPreparationDoesNotSpendSeed(GameTestHelper h) {
        var maid=worker(h);maid.getInventory().setItem(0,new ItemStack(Items.WHEAT_SEEDS,2));crop(h,4,3,(CropBlock)Blocks.WHEAT,7);
        boolean[] removed={false};
        h.onEachTick(()->{
            if(!removed[0]&&FarmingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-farming.preparing"))) {
                h.setBlock(4,0,3,Blocks.STONE);h.setBlock(4,1,3,Blocks.AIR);removed[0]=true;
            }
        });
        h.runAfterDelay(80,()->{h.assertTrue(removed[0]&&maid.getInventory().getItem(0).getCount()==2,"Stale crop consumed seeds");h.succeed();});
    }
    @GameTest(maxTicks=700) public void slowButMovingWorkerIsNotTimedOut(GameTestHelper h) {
        var maid=worker(h);maid.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5,1,3.5)));
        maid.selectMaidCommand(FarmingAddon.COMMAND);
        maid.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.1);
        crop(h,7,3,(CropBlock)Blocks.WHEAT,7);
        boolean[] sustained={false};
        h.runAfterDelay(210,()->{
            h.assertTrue(FarmingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-farming.walking"))
                    &&maid.position().distanceToSqr(h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5,1,3.5)))>1,
                    "Slow active path was interrupted: "+FarmingData.status(maid));sustained[0]=true;
        });
        h.succeedWhen(()->h.assertTrue(sustained[0]&&maid.getInventory().countMatching(s->s.is(Items.WHEAT))==1,"Slow worker never reached crop"));
    }
    @GameTest(maxTicks=350) public void newWorkTakesOverAnActiveIdleWalk(GameTestHelper h) {
        var maid=worker(h);h.setBlock(new BlockPos(7,0,7),Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,7));
        boolean[] supplied={false};
        h.onEachTick(()->{
            if(!supplied[0]&&maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .map(w->w.getSpeedModifier()==0.45F).orElse(false)) {
                maid.getInventory().setItem(0,new ItemStack(Items.WHEAT_SEEDS,1));supplied[0]=true;
            }
        });
        h.succeedWhen(()->{h.assertTrue(supplied[0],"No idle walk was interrupted");h.assertBlockPresent(Blocks.WHEAT,new BlockPos(7,1,7));
            h.assertTrue(maid.getInventory().isEmpty(),"Work takeover did not conserve the seed");});
    }
}
