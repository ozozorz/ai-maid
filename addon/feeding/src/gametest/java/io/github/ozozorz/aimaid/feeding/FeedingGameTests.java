package io.github.ozozorz.aimaid.feeding;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class FeedingGameTests {
    private AiMaidEntity worker(GameTestHelper h) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,0,z,Blocks.DIRT);
        var maid=h.spawn(ModEntityTypes.AI_MAID,3.5F,1,3.5F);maid.setTame(true,true);
        maid.getInventory().setItem(0,new ItemStack(Items.WHEAT,4));maid.selectMaidCommand(FeedingAddon.COMMAND);return maid;
    }
    private Animal cow(GameTestHelper h) {var cow=h.spawn(EntityTypes.COW,4.5F,1,3.5F);cow.setNoAi(true);return cow;}
    @GameTest(maxTicks=100) public void adultGetsOneRealFoodAndNativeLoveState(GameTestHelper h) {
        var maid=worker(h);var cow=cow(h);
        h.runAfterDelay(70,()->{h.assertTrue(cow.isInLove(),"Adult was not fed");h.assertTrue(maid.getInventory().getItem(0).getCount()==3,"Food was duplicated or repeatedly consumed");h.succeed();});
    }
    @GameTest(maxTicks=100) public void babiesAndCooldownAdultsAreSkipped(GameTestHelper h) {
        var maid=worker(h);var baby=cow(h);baby.setAge(-24000);var adult=cow(h);adult.setAge(6000);
        h.runAfterDelay(70,()->{h.assertTrue(!baby.isInLove()&&!adult.isInLove()&&maid.getInventory().getItem(0).getCount()==4,"Fed an ineligible animal");h.succeed();});
    }
    @GameTest(maxTicks=100) public void wrongFoodIsNotConsumed(GameTestHelper h) {
        var maid=worker(h);maid.getInventory().setItem(0,new ItemStack(Items.APPLE,4));var cow=cow(h);
        h.runAfterDelay(70,()->{h.assertTrue(!cow.isInLove()&&maid.getInventory().getItem(0).getCount()==4,"Wrong food accepted");h.succeed();});
    }
    @GameTest(maxTicks=100) public void twoWorkersOnlyPayOnceForSameAnimal(GameTestHelper h) {
        var a=worker(h);var b=worker(h);var cow=cow(h);
        h.runAfterDelay(70,()->{h.assertTrue(cow.isInLove()&&a.getInventory().getItem(0).getCount()+b.getInventory().getItem(0).getCount()==7,"Concurrent feeding duplicated payment");h.succeed();});
    }
    @GameTest(maxTicks=100) public void outOfAreaAnimalsAreSkipped(GameTestHelper h) {
        var maid=worker(h);maid.setAttached(FeedingData.RADIUS,2);var cow=cow(h);cow.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(7,1,7)));
        h.runAfterDelay(70,()->{h.assertTrue(!cow.isInLove()&&maid.getInventory().getItem(0).getCount()==4,"Fed outside area");h.succeed();});
    }
    @GameTest(maxTicks=100) public void newlySuppliedFoodRestartsWaitingWorker(GameTestHelper h) {
        var maid=worker(h);maid.getInventory().clearContent();var cow=cow(h);
        h.runAfterDelay(30,()->{h.assertTrue(!cow.isInLove(),"Fed without food");maid.getInventory().setItem(0,new ItemStack(Items.WHEAT,1));});
        h.succeedWhen(()->h.assertTrue(cow.isInLove()&&maid.getInventory().isEmpty(),"Did not resume after food supply"));
    }
    @GameTest(maxTicks=150) public void hiddenNearestAnimalDoesNotStarveVisibleAnimal(GameTestHelper h) {
        var maid=worker(h);
        var hidden=h.spawn(EntityTypes.COW,5.5F,1,3.5F);hidden.setNoAi(true);
        // 完整遮挡视线；另一只位于侧后方，距离更远但可直接靠近。
        for(int z=2;z<=4;z++)for(int y=1;y<=3;y++)h.setBlock(4,y,z,Blocks.STONE);
        var visible=h.spawn(EntityTypes.COW,1F,1,3.5F);visible.setNoAi(true);
        h.succeedWhen(()->{
            h.assertTrue(visible.isInLove(),"Blocked nearest animal monopolized feeding");
            h.assertTrue(!hidden.isInLove()&&maid.getInventory().getItem(0).getCount()==3,"Fed through wall or paid twice");
        });
    }
    @GameTest(maxTicks=180) public void visibleAnimalInsideClosedPenDoesNotMonopolizeWork(GameTestHelper h) {
        var maid=worker(h);maid.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5,1,3.5)));
        var enclosed=h.spawn(EntityTypes.COW,5.5F,1,3.5F);enclosed.setNoAi(true);
        for(int x=3;x<=7;x++){h.setBlock(x,1,1,Blocks.OAK_FENCE);h.setBlock(x,1,5,Blocks.OAK_FENCE);}
        for(int z=1;z<=5;z++){h.setBlock(3,1,z,Blocks.OAK_FENCE);h.setBlock(7,1,z,Blocks.OAK_FENCE);}
        var available=h.spawn(EntityTypes.COW,0.5F,1,7F);available.setNoAi(true);
        h.assertTrue(maid.getSensing().hasLineOfSight(enclosed),"Pen must allow eye contact while blocking approach");
        h.succeedWhen(()->{
            h.assertTrue(available.isInLove(),"Unreachable visible animal monopolized feeding");
            h.assertTrue(!enclosed.isInLove()&&maid.getInventory().getItem(0).getCount()==3,"Fed beyond reach or paid twice");
        });
    }
    @GameTest(maxTicks=100) public void reachableAnimalsAreFedWithoutFixedIdleGap(GameTestHelper h) {
        var maid=worker(h);var a=cow(h);var b=h.spawn(EntityTypes.COW,2.5F,1,3.5F);b.setNoAi(true);
        long[] first={-1};
        h.onEachTick(()->{
            if((a.isInLove()||b.isInLove())&&first[0]<0)first[0]=h.getLevel().getGameTime();
            if(a.isInLove()&&b.isInLove())h.assertTrue(h.getLevel().getGameTime()-first[0]<=12,"Feeding retained a fixed idle gap");
        });
        h.succeedWhen(()->h.assertTrue(a.isInLove()&&b.isInLove()&&maid.getInventory().getItem(0).getCount()==2,"Did not feed both animals"));
    }
    @GameTest(maxTicks=200) public void movingAnimalIsReplannedBeforeFoodIsSpent(GameTestHelper h) {
        var maid=worker(h);var animal=cow(h);boolean[] moved={false};
        h.onEachTick(()->{
            if(!moved[0]&&FeedingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-feeding.preparing"))) {
                h.assertTrue(maid.getInventory().getItem(0).getCount()==4,"Food spent before preparation");
                animal.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(7,1,6)));moved[0]=true;
            }
        });
        h.succeedWhen(()->h.assertTrue(moved[0]&&animal.isInLove()&&maid.getInventory().getItem(0).getCount()==3,"Moving target was not reached and fed once: "+FeedingData.status(maid)+", maid="+maid.position()+", animal="+animal.position()+", walk="+maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w->w.getTarget().currentBlockPosition()).orElse(null)));
    }
    @GameTest(maxTicks=120) public void closingBarrierDuringPreparationPreventsRemoteFeeding(GameTestHelper h) {
        var maid=worker(h);var animal=cow(h);boolean[] blocked={false};
        h.onEachTick(()->{
            if(!blocked[0]&&FeedingData.status(maid).equals(net.minecraft.network.chat.Component.translatable("status.ai-maid-feeding.preparing"))) {
                animal.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5,1,3.5)));
                for(int z=0;z<8;z++)for(int y=1;y<=3;y++)h.setBlock(4,y,z,Blocks.STONE);
                blocked[0]=true;
            }
        });
        h.runAfterDelay(90,()->{h.assertTrue(blocked[0]&&!animal.isInLove()&&maid.getInventory().getItem(0).getCount()==4,"Fed through a new obstacle");h.succeed();});
    }
}
