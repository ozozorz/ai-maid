package io.github.ozozorz.aimaid.experiment;

import com.google.gson.*;
import io.github.ozozorz.aimaid.llm.service.ExecutionPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentEvidenceTest {
    @TempDir Path temporary;
    private static JsonObject base() {
        var o = new JsonObject();
        for (var item : List.of("wheat","carrot","oak")) for (var place : List.of("a","b","held")) o.addProperty(item+"_"+place,0);
        for (var container : List.of("inventory","chest_a","chest_b","sentinel")) o.add(container,new JsonObject());
        o.addProperty("mainhand","minecraft:air"); o.addProperty("mainhand_count",0); o.addProperty("logs_remaining",0);
        o.addProperty("owner_distance_squared",0); o.addProperty("point_distance_squared",0); o.addProperty("same_dimension",true);
        o.addProperty("maid_alive",true); o.addProperty("owner_alive",true); o.addProperty("external_wheat_a",0);
        o.add("crops",new JsonArray()); o.add("soil",new JsonArray()); return o;
    }
    private static void wheat(JsonObject o, String where, int count) {
        o.addProperty("wheat_"+where,count);
        o.getAsJsonObject(where.equals("held") ? "inventory" : "chest_"+where).addProperty("minecraft:wheat",count);
    }
    @Test void pairingHasTwoHundredRunsWithIdenticalSceneSeedsAndReproducibleRandomOrder() {
        var a = ExperimentCases.schedule(ExperimentCases.ALL,5,42,null);
        assertEquals(a,ExperimentCases.schedule(ExperimentCases.ALL,5,42,null));
        assertNotEquals(a,ExperimentCases.schedule(ExperimentCases.ALL,5,43,null));
        assertEquals(200,a.size()); assertEquals(200,a.stream().map(ExperimentCases.Trial::id).distinct().count());
        for (int i=0;i<a.size();i+=2) {
            assertEquals(a.get(i).card(),a.get(i+1).card()); assertEquals(a.get(i).seed(),a.get(i+1).seed());
            assertEquals(a.get(i).repeat(),a.get(i+1).repeat()); assertNotEquals(a.get(i).mode(),a.get(i+1).mode());
        }
    }
    @Test void policyDeniesUnlistedMutationsEvenWhenModelInventsTheirNames() {
        for (var p : List.of(ExecutionPolicy.FIXED_PLAN,ExecutionPolicy.STEPWISE_GOAL)) {
            assertTrue(p.allows("observe_chest")); assertFalse(p.allows("select_command"));
            assertFalse(p.allows("start_wheat_delivery")); assertFalse(p.allows("execute_action"));
        }
        assertFalse(ExecutionPolicy.FIXED_PLAN.allows("start_goal"));
        assertFalse(ExecutionPolicy.STEPWISE_GOAL.allows("submit_plan"));
    }
    @Test void partialDeliveryDoesNotPassAndUnrequestedChestChangesInvalidateSuccess() {
        var before=base(); wheat(before,"held",5);
        var after=before.deepCopy(); wheat(after,"a",1); wheat(after,"held",4);
        assertFalse(ExperimentOracle.evaluate(ExperimentCases.get("S4"),before,after).get("goal_met").getAsBoolean());
        wheat(after,"a",3); wheat(after,"held",2);
        var score=ExperimentOracle.evaluate(ExperimentCases.get("S4"),before,after);
        assertTrue(score.get("goal_met").getAsBoolean()); assertTrue(score.get("constraints_ok").getAsBoolean());
        after.getAsJsonObject("sentinel").addProperty("minecraft:diamond",1);
        assertFalse(ExperimentOracle.evaluate(ExperimentCases.get("S4"),before,after).get("constraints_ok").getAsBoolean());
    }
    @Test void scriptedChestFillIsNotCountedAsNpcDelivery() {
        var before=base(); wheat(before,"a",60); wheat(before,"held",1);
        var after=before.deepCopy(); wheat(after,"a",64); wheat(after,"b",2); wheat(after,"held",0);
        after.addProperty("external_wheat_a",3);
        var score=ExperimentOracle.evaluate(ExperimentCases.get("D1"),before,after);
        assertTrue(score.get("goal_met").getAsBoolean()); assertTrue(score.get("constraints_ok").getAsBoolean());
        after.addProperty("external_wheat_a",0);
        assertFalse(ExperimentOracle.evaluate(ExperimentCases.get("D1"),before,after).get("goal_met").getAsBoolean());
    }
    @Test void inventoryLossAndReturnToOldOwnerLocationCannotPass() {
        var before=base(); wheat(before,"held",3); before.getAsJsonObject("inventory").addProperty("minecraft:stone",64);
        var after=before.deepCopy(); wheat(after,"a",3); wheat(after,"held",0); after.addProperty("owner_distance_squared",100);
        assertFalse(ExperimentOracle.evaluate(ExperimentCases.get("D4"),before,after).get("goal_met").getAsBoolean());
        after.getAsJsonObject("inventory").remove("minecraft:stone");
        assertFalse(ExperimentOracle.evaluate(ExperimentCases.get("D4"),before,after).get("constraints_ok").getAsBoolean());
    }
    @Test void negativeGoalsNeverSilentlyBecomeOrdinaryPositiveCases() {
        var before=base(); var after=before.deepCopy(); wheat(after,"held",3);
        var score=ExperimentOracle.evaluate(ExperimentCases.get("N5"),before,after);
        assertTrue(score.get("unexpected_feasibility").getAsBoolean());
        assertFalse(ExperimentCases.get("N5").feasible());
    }
    private static JsonObject event(String type, String usage) {
        var j=new JsonObject(); j.addProperty("event",type);
        if(usage!=null) j.add("usage",JsonParser.parseString(usage)); return j;
    }
    private static JsonObject result(String id) { var j=new JsonObject(); j.addProperty("run_id",id); return j; }
    @Test void missingUsageStaysUnknownAndOldCallbacksDoNotPolluteNextTrial() throws Exception {
        try(var log=new ExperimentLog(temporary,new JsonObject())) {
            log.begin("S4-1-A"); var old=log.trace(); old.accept(event("model_request",null)); old.accept(event("model_response",null));
            var first=result("S4-1-A"); log.finish(first);
            assertTrue(first.get("prompt_tokens").isJsonNull()); assertFalse(first.get("usage_complete").getAsBoolean());
            log.begin("S4-1-B"); old.accept(event("model_error",null));
            log.event(event("model_request",null)); log.event(event("model_response","{\"prompt_tokens\":12,\"completion_tokens\":7}"));
            var second=result("S4-1-B"); log.finish(second);
            assertTrue(second.get("usage_complete").getAsBoolean()); assertEquals(0,second.get("model_errors").getAsInt());
            assertEquals(12,second.get("prompt_tokens").getAsInt());
        }
        var lines=Files.readAllLines(temporary.resolve("events.jsonl"));
        assertTrue(lines.stream().anyMatch(s -> s.contains("model_error") && s.contains("S4-1-A")));
    }
    @Test void malformedTokenMetadataIsNotCoercedToZeroOrRounded() throws Exception {
        try(var log=new ExperimentLog(temporary,new JsonObject())) {
            log.begin("S1-1-A"); log.event(event("model_request",null));
            log.event(event("model_response","{\"prompt_tokens\":-1,\"completion_tokens\":1.5}"));
            var row=result("S1-1-A"); log.finish(row); assertFalse(row.get("usage_complete").getAsBoolean());
            assertTrue(row.get("completion_tokens").isJsonNull());
        }
    }
}
