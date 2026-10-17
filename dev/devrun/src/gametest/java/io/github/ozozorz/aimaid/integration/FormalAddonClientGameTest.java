package io.github.ozozorz.aimaid.integration;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.farming.FarmingAddon;
import io.github.ozozorz.aimaid.feeding.FeedingAddon;
import io.github.ozozorz.aimaid.hauling.HaulingAddon;
import io.github.ozozorz.aimaid.hauling.HaulingData;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** 三个正式工作在真实客户端/服务器中运行，并通过原版菜单按钮包切换。 */
public class FormalAddonClientGameTest implements FabricClientGameTest {
    private record Scene(int maid, int cow, BlockPos crop, BlockPos source, BlockPos destination) {}
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            var connection=world.getConnection();var server=world.getServer();connection.waitForChunksRender();
            Scene scene=server.computeOnServer(s->{
                var level=connection.getServerLevel();var owner=connection.getServerPlayer();var base=owner.blockPosition().below();
                for(int x=-4;x<9;x++)for(int z=-4;z<9;z++){
                    level.setBlockAndUpdate(base.offset(x,0,z),Blocks.DIRT.defaultBlockState());
                    for(int y=1;y<5;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
                }
                owner.connection.teleport(base.getX()+3.5,base.getY()+1,base.getZ()+0.5,0,15);
                var maid=new AiMaidEntity(level);maid.setPos(base.getX()+3.5,base.getY()+1,base.getZ()+3.5);
                maid.setTame(true,true);maid.setOwner(owner);maid.setCustomName(Component.literal("Official addons"));
                maid.selectMaidCommand(MaidCommands.STAY);level.addFreshEntity(maid);
                var crop=base.offset(4,1,3);level.setBlockAndUpdate(crop.below(),Blocks.FARMLAND.defaultBlockState());
                level.setBlockAndUpdate(crop,((CropBlock)Blocks.WHEAT).getStateForAge(7));
                for(var other:java.util.List.of(crop.south(),crop.south().west())) {
                    level.setBlockAndUpdate(other.below(),Blocks.FARMLAND.defaultBlockState());
                    level.setBlockAndUpdate(other,((CropBlock)Blocks.WHEAT).getStateForAge(7));
                }
                var cow=EntityTypes.COW.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                cow.setPos(base.getX()+2.5,base.getY()+1,base.getZ()+3.5);cow.setNoAi(true);level.addFreshEntity(cow);
                var source=base.offset(2,1,5);var dest=base.offset(4,1,5);
                level.setBlockAndUpdate(source,Blocks.COPPER_CHEST.weathering().unaffected().defaultBlockState());level.setBlockAndUpdate(dest,Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity)level.getBlockEntity(source)).setItem(0,new ItemStack(Items.APPLE,8));
                if(!MaidMenuOpener.open(owner,maid))throw new AssertionError("Cannot open worker menu");
                return new Scene(maid.getId(),cow.getId(),crop,source,dest);
            });
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu);
            context.runOnClient(c->{var menu=(MaidMenu)c.player.containerMenu;
                if(menu.getVisibleCommands().size()!=8||menu.getVisibleCommands().stream().anyMatch(m->m.getRegistryId().getNamespace().equals("testaddon")))throw new AssertionError("Production command menu contains retired fixture");
            });
            select(context,FarmingAddon.COMMAND);
            server.waitFor(s->{var state=connection.getServerLevel().getBlockState(scene.crop());return state.is(Blocks.WHEAT)&&!((CropBlock)Blocks.WHEAT).isMaxAge(state);});
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu m&&m.getSelectedCommand()==FarmingAddon.COMMAND
                    &&m.getActiveActivity()==FarmingAddon.ACTIVITY&&countStorage(m,Items.WHEAT)==3);
            context.takeScreenshot("official-farming-menu");
            context.runOnClient(c->c.player.closeContainer());context.waitTicks(5);
            context.takeScreenshot("scheduling-farmed-patch-world");
            server.runOnServer(s->MaidMenuOpener.open(connection.getServerPlayer(),(AiMaidEntity)connection.getServerLevel().getEntity(scene.maid())));
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu);
            server.runOnServer(s->{var maid=(AiMaidEntity)connection.getServerLevel().getEntity(scene.maid());maid.getInventory().setItem(0,new ItemStack(Items.WHEAT,2));});
            select(context,FeedingAddon.COMMAND);
            server.waitFor(s->((Animal)connection.getServerLevel().getEntity(scene.cow())).isInLove());
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu m&&m.getSelectedCommand()==FeedingAddon.COMMAND
                    &&m.getActiveActivity()==FeedingAddon.ACTIVITY&&countStorage(m,Items.WHEAT)==1);
            context.clickScreenButton(">");context.waitTicks(3);context.takeScreenshot("official-feeding-menu");
            context.runOnClient(c->c.player.closeContainer());context.waitTicks(50);
            context.takeScreenshot("scheduling-feeding-wait-world");
            int userId=server.computeOnServer(s->{
                var level=connection.getServerLevel();var user=new AiMaidEntity(level);
                user.setPos(scene.source().getX()+0.5,scene.source().getY(),scene.source().getZ()+1.5);
                user.setNoAi(true);level.addFreshEntity(user);
                var chest=(ChestBlockEntity)level.getBlockEntity(scene.source());user.setOpenedContainerPos(scene.source());chest.startOpen(user);
                MaidMenuOpener.open(connection.getServerPlayer(),(AiMaidEntity)level.getEntity(scene.maid()));return user.getId();
            });
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu);
            select(context,HaulingAddon.COMMAND);
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu m
                    &&m.getStatusMessage().getString().contains(Component.translatable("status.ai-maid-hauling.busy").getString()));
            context.takeScreenshot("scheduling-hauling-busy-menu");
            server.runOnServer(s->{
                var level=connection.getServerLevel();var user=(AiMaidEntity)level.getEntity(userId);
                ((ChestBlockEntity)level.getBlockEntity(scene.source())).stopOpen(user);user.setOpenedContainerPos(null);user.discard();
            });
            server.waitFor(s->HaulingData.status((AiMaidEntity)connection.getServerLevel().getEntity(scene.maid()))
                    .equals(Component.translatable("status.ai-maid-hauling.picking")));
            context.runOnClient(c->c.player.closeContainer());context.waitTicks(5);
            context.takeScreenshot("scheduling-chest-interaction-world");
            server.runOnServer(s->MaidMenuOpener.open(connection.getServerPlayer(),(AiMaidEntity)connection.getServerLevel().getEntity(scene.maid())));
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu);
            server.waitFor(s->{var maid=(AiMaidEntity)connection.getServerLevel().getEntity(scene.maid());return maid.getInventory().countMatching(i->i.is(Items.APPLE))==8;});
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu m&&m.getSelectedCommand()==HaulingAddon.COMMAND
                    &&m.getActiveActivity()==HaulingAddon.ACTIVITY&&countStorage(m,Items.APPLE)==8);
            context.takeScreenshot("official-hauling-cargo-menu");
            server.waitFor(s->((ChestBlockEntity)connection.getServerLevel().getBlockEntity(scene.destination())).getItem(0).getCount()==8);
            server.runOnServer(s->{var maid=(AiMaidEntity)connection.getServerLevel().getEntity(scene.maid());
                if(maid.getInventory().countMatching(i->i.is(Items.APPLE))!=0)throw new AssertionError("Cargo was not removed after delivery");
            });
            context.waitFor(c->c.player.containerMenu instanceof MaidMenu m&&countStorage(m,Items.APPLE)==0);
            context.takeScreenshot("official-hauling-complete-menu");
            context.runOnClient(c->c.player.closeContainer());
        }
    }
    // 等待客户端 Slot 镜像真正收到物品变化，避免只验证服务器结果或截图早一帧。
    private static int countStorage(MaidMenu menu,Item item) {
        return menu.slots.subList(MaidMenu.MAID_SLOT_START,MaidMenu.MAID_SLOT_END).stream()
                .map(slot->slot.getItem()).filter(stack->stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private void select(ClientGameTestContext context,MaidCommand command) {
        context.runOnClient(c->{var menu=(MaidMenu)c.player.containerMenu;
            c.gameMode.handleInventoryButtonClick(menu.containerId,ModBuiltInRegistries.MAID_COMMAND.getId(command));
        });
        context.waitFor(c->c.player.containerMenu instanceof MaidMenu menu&&menu.getSelectedCommand()==command);
    }
}
