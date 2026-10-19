package io.github.ozozorz.aimaid.llm;

import io.github.ozozorz.aimaid.entity.*;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.llm.tool.MaidTools;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class LlmGameTests {
    private record Pair(AiMaidEntity maid, ServerPlayer owner) {}
    private Pair pair(GameTestHelper h) {
        var maid = h.spawn(ModEntityTypes.AI_MAID, 2, 2, 2); maid.setNoAi(true);
        var owner = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        owner.setPos(maid.position()); maid.setTame(true, true); maid.setOwner(owner); return new Pair(maid, owner);
    }
    @GameTest public void commandRevisionIncludesSameCommandReselection(GameTestHelper h) {
        var p = pair(h); long old = p.maid().getMaidCommandRevision();
        p.maid().selectMaidCommand(MaidCommands.FOLLOW); p.maid().selectMaidCommand(MaidCommands.FOLLOW);
        h.assertTrue(p.maid().getMaidCommandRevision() == old + 2, "Same command reselection must invalidate async snapshots"); h.succeed();
    }
    @GameTest public void toolSelectionUsesBrigadierAndAvoidsAccidentalRestart(GameTestHelper h) {
        var p = pair(h); var args = ChatProtocol.object("{\"command_id\":\"ai-maid:stay\"}");
        var result = MaidTools.execute("select_command", args, p.maid(), p.owner(), 32);
        h.assertTrue(result.has("selection") && p.maid().getMaidCommand() == MaidCommands.STAY, "Tool did not select through real server command: " + result);
        long revision = p.maid().getMaidCommandRevision();
        MaidTools.execute("select_command", args, p.maid(), p.owner(), 32);
        h.assertTrue(p.maid().getMaidCommandRevision() == revision, "Duplicate selection restarted work");
        args.addProperty("restart", true); MaidTools.execute("select_command", args, p.maid(), p.owner(), 32);
        h.assertTrue(p.maid().getMaidCommandRevision() == revision + 1, "Explicit restart ignored"); h.succeed();
    }
    @GameTest public void ownerAndDistanceAreRecheckedForTools(GameTestHelper h) {
        var p = pair(h); var stranger = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL); stranger.setPos(p.maid().position());
        var args = ChatProtocol.object("{\"command_id\":\"ai-maid:stay\"}");
        h.assertTrue(MaidTools.execute("select_command", args, p.maid(), stranger, 32).has("error"), "Stranger selected command");
        p.owner().setPos(p.maid().position().add(33, 0, 0));
        h.assertTrue(MaidTools.execute("select_command", args, p.maid(), p.owner(), 32).has("error"), "Remote owner selected command");
        h.assertTrue(p.maid().getMaidCommand() == MaidCommands.FOLLOW, "Rejected actions changed command"); h.succeed();
    }
    @GameTest public void unsupportedToolsAndParametersCannotRunServerCommands(GameTestHelper h) {
        var p = pair(h);
        for (String json : new String[]{"{\"command_id\":\"ai-maid:stay; op test\"}", "{\"command_id\":\"ai-maid:stay\",\"radius\":4}",
                "{\"command_id\":\"ai-maid:stay\",\"restart\":\"true\"}", "{\"command_id\":\"ai-maid:stay\",\"mode\":\"single\"}",
                "{\"command_id\":\"ai-maid:stay\",\"command\":\"op test\"}"})
            h.assertTrue(MaidTools.execute("select_command", ChatProtocol.object(json), p.maid(), p.owner(), 32).has("error"), "Invalid args accepted: " + json);
        h.assertTrue(MaidTools.execute("run_command", ChatProtocol.object("{}"), p.maid(), p.owner(), 32).has("error"), "Unknown tool accepted");
        h.assertTrue(p.maid().getMaidCommand() == MaidCommands.FOLLOW, "Invalid action changed command"); h.succeed();
    }
    @GameTest public void coreOnlyCatalogDoesNotInventOptionalWork(GameTestHelper h) {
        var p = pair(h); String commands = MaidTools.commands(p.maid(), p.owner()).toString();
        h.assertTrue(commands.contains("ai-maid:follow") && !commands.contains("ai-maid-farming:farm"), "Missing addons advertised");
        var data = MaidTools.snapshot(p.maid()); h.assertTrue(data.has("status") && data.has("inventory") && data.has("equipment"), "Missing authoritative context"); h.succeed();
    }
    @GameTest public void personaIsSavedOnMaidAndNotSharedByCommand(GameTestHelper h) {
        var p = pair(h); p.maid().setAttached(LlmData.PERSONA, "A patient gardener");
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        p.maid().saveWithoutId(output);
        var loaded = new AiMaidEntity(h.getLevel());
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(loaded.getAttachedOrElse(LlmData.PERSONA, "").equals("A patient gardener"), "Persona was not persisted");
        var other = pair(h); h.assertTrue(other.maid().getAttachedOrElse(LlmData.PERSONA, "").isEmpty(), "Persona leaked to another maid"); h.succeed();
    }
}
