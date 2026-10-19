package io.github.ozozorz.aimaid.integration;

import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.farming.*;
import io.github.ozozorz.aimaid.feeding.*;
import io.github.ozozorz.aimaid.hauling.*;
import io.github.ozozorz.aimaid.lumber.LumberAddon;
import io.github.ozozorz.aimaid.lumber.ai.LumberMemories;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;

/** 验证 LLM 工具经过正式命令入口，而非重新实现各 Addon 的业务。 */
public class LlmAddonGameTests {
    private record Pair(AiMaidEntity maid, ServerPlayer owner) {}
    private Pair pair(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 0, z, Blocks.DIRT);
        var maid = h.spawn(ModEntityTypes.AI_MAID, 3.5F, 1, 3.5F); maid.setNoAi(true);
        var owner = (ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL); owner.setPos(maid.position());
        maid.setTame(true, true); maid.setOwner(owner); return new Pair(maid, owner);
    }
    private void select(GameTestHelper h, Pair p, String json) {
        var result = MaidTools.execute("select_command", ChatProtocol.object(json), p.maid(), p.owner(), 32);
        h.assertTrue(result.has("selection"), "LLM tool rejected a supported addon command: " + result);
    }
    @GameTest public void farmingRadiusAndCenterUseExistingAddonState(GameTestHelper h) {
        var p = pair(h); select(h, p, "{\"command_id\":\"ai-maid-farming:farm\",\"radius\":6}");
        h.assertTrue(p.maid().getMaidCommand() == FarmingAddon.COMMAND && FarmingData.radius(p.maid()) == 6
                && p.maid().getAttached(FarmingData.CENTER).pos().equals(p.maid().blockPosition()), "Farming config was not applied"); h.succeed();
    }
    @GameTest public void feedingAndHaulingKeepSeparateParameters(GameTestHelper h) {
        var p = pair(h); select(h, p, "{\"command_id\":\"ai-maid-feeding:feed\",\"radius\":9}");
        select(h, p, "{\"command_id\":\"ai-maid-hauling:haul\",\"radius\":24}");
        h.assertTrue(p.maid().getMaidCommand() == HaulingAddon.COMMAND && FeedingData.radius(p.maid()) == 9 && HaulingData.radius(p.maid()) == 24, "Parameters leaked between addons"); h.succeed();
    }
    @GameTest public void lumberSingleAndTreeModesFollowRealCommandLifecycle(GameTestHelper h) {
        var p = pair(h); select(h, p, "{\"command_id\":\"ai-maid-lumber:lumber\",\"mode\":\"single\"}");
        h.assertTrue(p.maid().getBrain().getMemory(LumberMemories.LUMBER_REQUEST).orElse(false), "Single mode not set");
        select(h, p, "{\"command_id\":\"ai-maid-lumber:lumber\",\"mode\":\"tree\"}");
        h.assertTrue(p.maid().getMaidCommand() == LumberAddon.LUMBER && !p.maid().getBrain().getMemory(LumberMemories.LUMBER_REQUEST).orElse(true), "Tree reset failed"); h.succeed();
    }
    @GameTest public void invalidAddonParametersDoNotChangeWork(GameTestHelper h) {
        var p = pair(h); p.maid().selectMaidCommand(MaidCommands.STAY); long revision = p.maid().getMaidCommandRevision();
        for (String json : new String[]{"{\"command_id\":\"ai-maid-farming:farm\",\"radius\":17}",
                "{\"command_id\":\"ai-maid-feeding:feed\",\"radius\":2.1}", "{\"command_id\":\"ai-maid-hauling:haul\",\"radius\":\"12\"}",
                "{\"command_id\":\"ai-maid-lumber:lumber\",\"radius\":8}"})
            h.assertTrue(MaidTools.execute("select_command", ChatProtocol.object(json), p.maid(), p.owner(), 32).has("error"), "Invalid addon parameter accepted");
        h.assertTrue(p.maid().getMaidCommandRevision() == revision, "Rejected parameters restarted work"); h.succeed();
    }
    @GameTest(maxTicks = 160) public void modelToolActuallyStartsFarmingAndUsesRealSeed(GameTestHelper h) {
        var p = pair(h); var maid = p.maid(); maid.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        h.setBlock(new BlockPos(4, 0, 3), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        select(h, p, "{\"command_id\":\"ai-maid-farming:farm\",\"radius\":3}"); maid.setNoAi(false);
        h.succeedWhen(() -> { h.assertBlockPresent(Blocks.WHEAT, new BlockPos(4, 1, 3));
            h.assertTrue(maid.getInventory().getItem(0).getCount() == 1, "LLM-started work bypassed real inventory"); });
    }
    @GameTest public void contextIncludesActualStopReasonAndInstalledCapabilities(GameTestHelper h) {
        var p = pair(h); select(h, p, "{\"command_id\":\"ai-maid-feeding:feed\"}"); FeedingData.status(p.maid(), "no_food");
        var context = MaidTools.snapshot(p.maid());
        h.assertTrue(context.toString().contains("status.ai-maid-feeding.no_food"), "Status translation key lost");
        String commands = MaidTools.commands(p.maid(), p.owner()).toString();
        for (String id : new String[]{"ai-maid-lumber:lumber", "ai-maid-farming:farm", "ai-maid-feeding:feed", "ai-maid-hauling:haul"})
            h.assertTrue(commands.contains(id), "Installed work not exposed: " + id);
        h.succeed();
    }
}
