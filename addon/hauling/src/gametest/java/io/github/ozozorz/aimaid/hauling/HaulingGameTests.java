package io.github.ozozorz.aimaid.hauling;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.combat.CombatEquipment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class HaulingGameTests {
    private AiMaidEntity worker(GameTestHelper h) {
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,0,z,Blocks.STONE);
        var maid=h.spawn(ModEntityTypes.AI_MAID,3.5F,1,4.5F);maid.setTame(true,true);
        var owner=h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER,1.5F,1,1.5F);owner.setNoAi(true);maid.setOwner(owner);
        maid.selectMaidCommand(HaulingAddon.COMMAND);maid.setAttached(HaulingData.RADIUS,2);return maid;
    }
    private ChestBlockEntity chest(GameTestHelper h,int x,boolean copper) {
        h.setBlock(x,1,3,copper?Blocks.COPPER_CHEST.weathering().unaffected():Blocks.CHEST);
        return (ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x,1,3)));
    }
    private int count(net.minecraft.world.Container c,net.minecraft.world.item.Item item) {
        int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;
    }
    @GameTest(maxTicks=340) public void sortsCopperChestIntoMatchingChestInBatches(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,32));
        var dest=chest(h,4,false);dest.setItem(0,new ItemStack(Items.APPLE,1));
        h.runAfterDelay(80,()->h.assertTrue(count(source,Items.APPLE)==16&&count(maid.getInventory(),Items.APPLE)==16,"Pickup must be one batch of sixteen"));
        h.succeedWhen(()->h.assertTrue(count(dest,Items.APPLE)==33&&source.isEmpty()&&maid.getInventory().isEmpty(),"Sorting did not conserve items"));
    }
    @GameTest(maxTicks=180) public void emptyChestAcceptsCargo(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.DIAMOND,7));var dest=chest(h,4,false);
        h.succeedWhen(()->h.assertTrue(count(dest,Items.DIAMOND)==7&&source.isEmpty()&&maid.getInventory().isEmpty(),"Empty chest not used"));
    }
    @GameTest(maxTicks=220) public void differentItemChestDoesNotAcceptCargo(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);dest.setItem(0,new ItemStack(Items.STONE,1));
        h.runAfterDelay(180,()->{h.assertTrue(count(dest,Items.APPLE)==0&&count(maid.getInventory(),Items.APPLE)==8&&HaulingCargo.current(maid)!=null,"Cargo was lost or mis-sorted");h.succeed();});
    }
    @GameTest public void partialInsertionPreservesCountAndComponents(GameTestHelper h) {
        var dest=new SimpleContainer(2);var named=new ItemStack(Items.APPLE,16);named.set(DataComponents.CUSTOM_NAME,Component.literal("Cargo"));
        dest.setItem(0,named.copyWithCount(60));dest.setItem(1,new ItemStack(Items.STONE,64));
        int moved=HaulingContainers.insert(dest,named,16);
        h.assertTrue(moved==4&&named.getCount()==12&&dest.getItem(0).getCount()==64&&ItemStack.isSameItemSameComponents(named,dest.getItem(0)),"Partial transfer lost count/components");h.succeed();
    }
    @GameTest(maxTicks=180) public void componentsSurviveActualSorting(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);var cargo=new ItemStack(Items.IRON_SWORD);cargo.setDamageValue(19);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Do not equip"));source.setItem(0,cargo);var dest=chest(h,4,false);
        h.succeedWhen(()->{h.assertTrue(dest.getItem(0).is(Items.IRON_SWORD)&&dest.getItem(0).getDamageValue()==19,"Damaged cargo missing");
            h.assertTrue(dest.getItem(0).getHoverName().getString().equals("Do not equip")&&maid.getMainHandItem().isEmpty(),"Cargo was equipped or lost components");});
    }
    @GameTest(maxTicks=120) public void fullInventoryDoesNotTakeFromSource(GameTestHelper h) {
        var maid=worker(h);for(int i=0;i<35;i++)maid.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,16));chest(h,4,false);
        h.runAfterDelay(90,()->{h.assertTrue(count(source,Items.APPLE)==16&&HaulingCargo.current(maid)==null,"Full worker took cargo");h.succeed();});
    }
    @GameTest(maxTicks=150) public void blockedCopperChestIsNotLooted(GameTestHelper h) {
        worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,16));h.setBlock(2,2,3,Blocks.STONE);chest(h,4,false);
        h.runAfterDelay(100,()->{h.assertTrue(count(source,Items.APPLE)==16,"Blocked chest was opened");h.succeed();});
    }
    @GameTest(maxTicks=150) public void lockedCopperChestIsNotLooted(GameTestHelper h) {
        worker(h);var source=chest(h,2,true);
        var data=new net.minecraft.nbt.CompoundTag();var lock=new net.minecraft.nbt.CompoundTag();lock.putString("items","minecraft:diamond");data.put("lock",lock);
        source.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),data));
        h.assertTrue(source.isLocked(),"Lock fixture invalid");source.setItem(0,new ItemStack(Items.APPLE,16));chest(h,4,false);
        h.runAfterDelay(100,()->{h.assertTrue(count(source,Items.APPLE)==16,"Locked chest was looted");h.succeed();});
    }
    @GameTest(maxTicks=240) public void reloadKeepsRealCargoAndResumesDelivery(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.DIAMOND,9));var dest=chest(h,4,false);
        h.runAfterDelay(80,()->{
            h.assertTrue(HaulingCargo.current(maid)!=null&&count(maid.getInventory(),Items.DIAMOND)==9,"Save must occur with actual cargo");
            var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());maid.saveWithoutId(out);maid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var restored=new AiMaidEntity(h.getLevel());restored.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),out.buildResult()));h.getLevel().addFreshEntity(restored);
            h.assertTrue(HaulingCargo.current(restored)!=null,"Cargo receipt not restored");
            h.succeedWhen(()->h.assertTrue(count(dest,Items.DIAMOND)==9&&restored.getInventory().isEmpty()&&source.isEmpty(),"Reload duplicated or lost cargo"));
        });
    }
    @GameTest(maxTicks=240) public void manualCommandChangeKeepsCargoForLaterDelivery(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);
        h.runAfterDelay(80,()->maid.selectMaidCommand(MaidCommands.STAY));
        h.runAfterDelay(110,()->{h.assertTrue(count(maid.getInventory(),Items.APPLE)==8&&dest.isEmpty(),"Changing command lost cargo");maid.selectMaidCommand(HaulingAddon.COMMAND);});
        h.succeedWhen(()->h.assertTrue(count(dest,Items.APPLE)==8&&maid.getInventory().isEmpty(),"Reselection did not deliver retained cargo"));
    }
    @GameTest(maxTicks=200) public void twoMaidsConserveSourceAndDestinationTotals(GameTestHelper h) {
        var a=worker(h);var b=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,16));var dest=chest(h,4,false);
        h.succeedWhen(()->h.assertTrue(count(dest,Items.APPLE)==16&&source.isEmpty()&&a.getInventory().isEmpty()&&b.getInventory().isEmpty(),"Concurrent sorting lost/duplicated cargo"));
    }
    @GameTest public void combatCannotBorrowReservedCargoWeapon(GameTestHelper h) {
        var maid=worker(h);var sword=new ItemStack(Items.NETHERITE_SWORD);maid.getInventory().setItem(0,sword);
        maid.setAttached(HaulingCargo.TYPE,new HaulingCargo(0,ItemStackTemplate.fromNonEmptyStack(sword),1));
        CombatEquipment.selectWeapon(maid,1);
        h.assertTrue(maid.getMainHandItem().isEmpty()&&maid.getInventory().getItem(0)==sword,"Combat borrowed cargo weapon");h.succeed();
    }
    @GameTest public void combatCannotConsumeReservedCargoArrows(GameTestHelper h) {
        var maid=worker(h);var arrows=new ItemStack(Items.ARROW,16);maid.getInventory().setItem(0,arrows);
        maid.setAttached(HaulingCargo.TYPE,new HaulingCargo(0,ItemStackTemplate.fromNonEmptyStack(arrows).withCount(1),16));
        h.assertTrue(maid.getProjectile(new ItemStack(Items.BOW)).isEmpty(),"Combat borrowed cargo arrows");
        maid.getInventory().setItem(1,new ItemStack(Items.ARROW,2));
        h.assertTrue(maid.getProjectile(new ItemStack(Items.BOW))==maid.getInventory().getItem(1),"Own ammunition became unavailable");h.succeed();
    }
    @GameTest(maxTicks=180) public void playerReplacingCargoDoesNotDepositTheirNewItem(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);
        h.runAfterDelay(80,()->{var c=HaulingCargo.current(maid);h.assertTrue(c!=null,"No cargo to replace");maid.getInventory().setItem(c.slot(),new ItemStack(Items.DIAMOND,3));});
        h.runAfterDelay(150,()->{h.assertTrue(dest.isEmpty()&&count(maid.getInventory(),Items.DIAMOND)==3&&HaulingCargo.current(maid)==null,"Stale receipt moved unrelated item");h.succeed();});
    }
    @GameTest(maxTicks=120) public void deathDropsActualCargoExactlyOnce(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));chest(h,4,false);
        h.runAfterDelay(80,()->{
            h.assertTrue(HaulingCargo.current(maid)!=null,"No cargo before death");
            h.hurt(maid,h.getLevel().damageSources().fall(),1000);
            int total=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,h.getBounds()).stream()
                    .filter(e->e.getItem().is(Items.APPLE)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(total==8&&maid.getInventory().isEmpty()&&source.isEmpty(),"Cargo receipt duplicated death drops");h.succeed();
        });
    }
    @GameTest(maxTicks=280) public void fullDestinationWaitsThenResumesAfterSpaceIsFreed(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);
        for(int i=0;i<dest.getContainerSize();i++)dest.setItem(i,new ItemStack(Items.APPLE,64));
        h.runAfterDelay(90,()->{h.assertTrue(count(maid.getInventory(),Items.APPLE)==8,"Full destination lost cargo");dest.setItem(0,ItemStack.EMPTY);});
        h.succeedWhen(()->h.assertTrue(dest.getItem(0).getCount()==8&&maid.getInventory().isEmpty(),"Did not retry destination after freeing space"));
    }
    @GameTest(maxTicks=720) public void busyChestReopensWithoutFiveMinutePathPenalty(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);
        var user=h.spawn(ModEntityTypes.AI_MAID,1.5F,1,3.5F);user.setNoAi(true);
        user.setOpenedContainerPos(source.getBlockPos());source.startOpen(user);
        h.runAfterDelay(450,()->{
            h.assertTrue(count(source,Items.APPLE)==8&&maid.getInventory().isEmpty(),"Busy source was accessed");
            source.stopOpen(user);user.setOpenedContainerPos(null);user.discard();
        });
        h.succeedWhen(()->h.assertTrue(count(dest,Items.APPLE)==8&&maid.getInventory().isEmpty(),"Busy chest received a path failure penalty"));
    }
    @GameTest(maxTicks=200) public void busySourceDoesNotBlockAnotherSource(GameTestHelper h) {
        var maid=worker(h);var busy=chest(h,2,true);busy.setItem(0,new ItemStack(Items.APPLE,8));
        var available=chest(h,4,true);available.setItem(0,new ItemStack(Items.DIAMOND,3));var dest=chest(h,5,false);
        var user=h.spawn(ModEntityTypes.AI_MAID,1.5F,1,3.5F);user.setNoAi(true);
        user.setOpenedContainerPos(busy.getBlockPos());busy.startOpen(user);
        h.succeedWhen(()->{
            h.assertTrue(count(dest,Items.DIAMOND)==3&&maid.getInventory().isEmpty(),"Busy nearest source blocked available source");
            h.assertTrue(count(busy,Items.APPLE)==8,"Busy source was modified");
        });
    }
    @GameTest(maxTicks=180) public void chestInteractionKeepsLookTargetAndStopsFeet(GameTestHelper h) {
        var maid=worker(h);var source=chest(h,2,true);source.setItem(0,new ItemStack(Items.APPLE,8));var dest=chest(h,4,false);
        boolean[] observed={false};
        h.onEachTick(()->{
            if(HaulingData.status(maid).equals(Component.translatable("status.ai-maid-hauling.picking"))&&!source.isEmpty()) {
                var look=maid.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).orElse(null);
                h.assertTrue(look!=null&&look.currentBlockPosition().equals(source.getBlockPos()),"Chest interaction lost its look target");
                h.assertTrue(!maid.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET),"Chest interaction was moving");observed[0]=true;
            }
        });
        h.succeedWhen(()->h.assertTrue(observed[0]&&count(dest,Items.APPLE)==8,"No complete interaction sequence"));
    }
}
