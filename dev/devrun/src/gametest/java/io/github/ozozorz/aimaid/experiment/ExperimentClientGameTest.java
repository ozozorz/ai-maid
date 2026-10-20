package io.github.ozozorz.aimaid.experiment;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.ozozorz.aimaid.llm.config.LlmConfig;
import io.github.ozozorz.aimaid.llm.protocol.ChatProtocol;
import io.github.ozozorz.aimaid.llm.service.ExecutionPolicy;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real arena, HTTP, actions and exports. The scripted model is a harness test, not paper evidence. */
public final class ExperimentClientGameTest implements FabricClientGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("experiment-test");
    @Override public void runTest(ClientGameTestContext context) {
        var active = new AtomicReference<ExperimentRunner>();
        ServerTickEvents.START_SERVER_TICK.register(s -> { var r=active.get(); if(r!=null) r.tick(); });
        try (var model = new Model(); var world = context.worldBuilder().create()) {
            var server=world.getServer(); var connection=world.getConnection(); connection.waitForChunksRender();
            var original=server.computeOnServer(s -> connection.getServerPlayer().level().dimension());
            var root=FabricLoader.getInstance().getGameDir().resolve("experiment-test-results").resolve(UUID.randomUUID().toString());
            for (String variant : List.of("S4-A","S4-B","D1-A","D1-B","wrong-A","wrong-B","false-B","negative-B","timeout-A","stop-A",
                    "S1-A","S2-A","S3-A","S5-A","S5-B","M1-A","M2-A","M3-A","M3-B","M4-A","M5-A","D2-B","D3-B","D4-B","D5-B",
                    "N1-A","N2-A","N3-A","N4-A","N5-A")) {
                String selected=System.getProperty("aimaid.experiment.testCases","");
                if(!selected.isEmpty() && !Arrays.asList(selected.split(",")).contains(variant)) continue;
                model.reset(variant);
                var policy=variant.endsWith("A") ? ExecutionPolicy.FIXED_PLAN : ExecutionPolicy.STEPWISE_GOAL;
                String caseId=variant.matches("[SMDN][1-5]-[AB]") ? variant.substring(0,2) : variant.startsWith("negative") ? "N1" : "S4";
                var runner=server.computeOnServer(s -> new ExperimentRunner(s,connection.getServerPlayer(),model.config(),
                        ExperimentCases.schedule(List.of(ExperimentCases.get(caseId)),1,42,policy),42,
                        variant.startsWith("timeout") ? 1 : 60,root.resolve(variant),LOGGER::info));
                active.set(runner);
                if(variant.equals("stop-A")) {
                    server.waitFor(s -> !model.requests.isEmpty());
                    server.runOnServer(s -> runner.stop("operator_stopped")); model.release.countDown();
                }
                server.waitFor(s -> runner.done(), 4000);
                model.release.countDown(); context.waitTicks(5);
                var rows=server.computeOnServer(s -> runner.results());
                check(model.error==null,"Mock error: "+model.error); check(rows.size()==1,"Missing result: "+runner.status());
                var row=rows.getFirst();
                check(!row.get("end_reason").getAsString().equals("harness_error"),"Harness error: "+row);
                boolean expectedSuccess=variant.matches("[SM][1-5]-[AB]") || variant.matches("D[1-5]-B");
                check(row.get("success").getAsBoolean()==expectedSuccess,"Wrong world score for "+variant+": "+row);
                if(caseId.equals("S5") || caseId.equals("M3")) checkLumberResult(row,caseId);
                if(variant.equals("S4-A") || variant.equals("S4-B")) {
                    check(row.getAsJsonObject("final").get("wheat_a").getAsInt()==3,"No real transfer");
                    check(row.getAsJsonObject("final").get("wheat_held").getAsInt()==2,"Leftover lost");
                    check(row.get("actions_started").getAsInt()==1,"Incorrect action accounting");
                }
                if(variant.startsWith("D1")) {
                    check(row.get("disturbance_triggered").getAsBoolean(),"No event-triggered disturbance");
                    check(row.getAsJsonObject("final").get("wheat_a").getAsInt()==64,"Perturbation/deposit ordering wrong");
                    check(row.getAsJsonObject("final").get("external_wheat_a").getAsInt()==3,"Scripted cargo not separated");
                    check(row.getAsJsonObject("final").get("wheat_b").getAsInt()==(expectedSuccess?2:0),"Partial recovery mismatch");
                }
                if(variant.matches("D[2-5]-B")) check(row.get("disturbance_triggered").getAsBoolean(),"Missing disturbance "+variant);
                if(variant.matches("N[1-5]-A")) {
                    check(!row.get("unexpected_feasibility").getAsBoolean(),"Negative fixture is feasible: "+row);
                    check(row.get("task_reason").getAsString().equals(ExperimentCases.get(caseId).obstacle()),"Wrong negative fixture obstacle: "+row);
                }
                if(variant.startsWith("wrong")) check(row.get("actions_started").getAsInt()==0,"Forbidden tool changed work");
                if(variant.equals("false-B")) check(row.get("false_completion_signal").getAsBoolean(),"Planner false success was trusted");
                if(variant.equals("negative-B")) {
                    check(row.get("correct_blocked").isJsonNull(),"Guessed human review");
                    check(row.get("review_required").getAsBoolean(),"Missing refusal review");
                }
                if(variant.equals("timeout-A") || variant.equals("stop-A")) {
                    check(row.get("end_reason").getAsString().equals(variant.equals("timeout-A")?"timeout":"operator_stopped"),"Stop/deadline not enforced");
                    check(!row.get("usage_complete").getAsBoolean(),"Cancelled response became complete usage");
                    check(row.get("actions_started").getAsInt()==0,"Late request executed an action");
                    check(row.get("interrupted").getAsBoolean()==variant.equals("stop-A"),"Manual stop not distinguished from timeout");
                } else check(row.get("usage_complete").getAsBoolean(),"Usage lost");
                var raw=Files.readString(runner.directory().resolve("events.jsonl"));
                check(raw.contains("model_request") && raw.contains("scene_ready") && raw.contains("run_finished"),"Missing raw evidence");
                check(!raw.contains("test-secret"),"Secret leaked");
                server.runOnServer(s -> check(connection.getServerPlayer().level().dimension()==original,"Owner not restored"));
                active.set(null);
            }
            String selected=System.getProperty("aimaid.experiment.testCases","");
            if(selected.isEmpty() || Arrays.asList(selected.split(",")).contains("batch")) {
                model.reset("batch"); model.batch=true;
                var runner=server.computeOnServer(s -> new ExperimentRunner(s,connection.getServerPlayer(),model.config(),
                        ExperimentCases.schedule(List.of(ExperimentCases.get("S4")),2,42,null),42,60,root.resolve("batch"),LOGGER::info));
                active.set(runner); server.waitFor(s -> runner.done(),4000);
                var rows=server.computeOnServer(s -> runner.results());
                check(model.error==null && rows.size()==4,"Batch did not finish four paired runs: "+model.error);
                check(rows.stream().allMatch(r -> r.get("success").getAsBoolean() && r.get("actions_started").getAsInt()==1),"Scene/history leaked across trials");
                check(rows.stream().filter(r -> r.get("mode").getAsString().equals("A")).allMatch(r -> r.get("llm_calls").getAsInt()==3),"A call counter leaked");
                check(rows.stream().filter(r -> r.get("mode").getAsString().equals("B")).allMatch(r -> r.get("llm_calls").getAsInt()==4),"B call counter leaked");
                check(Files.readAllLines(runner.directory().resolve("results.csv")).size()==5,"Batch CSV incomplete");
                active.set(null);
            }
            LOGGER.info("Experiment harness evidence: {}", root.toAbsolutePath());
        } catch(Exception e) { throw new AssertionError(e); }
        finally { active.set(null); }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void checkLumberResult(JsonObject row,String caseId) {
        var world=row.getAsJsonObject("final");
        check(world.get("logs_remaining").getAsInt()==0,"Tree left standing");
        check(world.get("mainhand_damage").getAsInt()==3,"Three logs must consume three durability");
        check(world.get("oak_held").getAsInt()==(caseId.equals("S5")?3:0),"Real inventory does not match");
        if(caseId.equals("M3")) {
            check(world.get("oak_a").getAsInt()==3,"Three real logs not deposited");
            check(world.get("owner_distance_squared").getAsDouble()<=9,"Maid did not return");
        }
        JsonObject chop=null;
        for(var value:row.getAsJsonObject("task").getAsJsonArray("steps")) {
            var step=value.getAsJsonObject();
            if(step.get("action").getAsString().equals("ai-maid-lumber:chop_tree")) chop=step;
        }
        check(chop!=null && chop.get("state").getAsString().equals("succeeded"),"No successful chop receipt");
        var progress=chop.getAsJsonObject("progress");
        check(progress.get("broken").getAsInt()==3 && progress.get("collected").getAsInt()==3,"Broken logs are not all collected");
        for(String key:List.of("skipped","remaining_blocks","uncollected_entities","missing_drop_entities","pending_drop_items"))
            check(progress.get(key).getAsInt()==0,"Incomplete lumber result: "+key);
        check(row.get("actions_started").getAsInt()==(caseId.equals("S5")?1:4),"Unexpected workaround actions");
    }
    private static JsonObject json(String text) { return ChatProtocol.object(text); }
    private static JsonObject receipt(JsonObject request,String id) {
        for(var value:request.getAsJsonArray("messages")) {
            var message=value.getAsJsonObject();
            if(message.has("tool_call_id") && message.get("tool_call_id").getAsString().equals(id)) return json(message.get("content").getAsString());
        }
        throw new AssertionError("No receipt "+id);
    }
    private static JsonObject call(String id,String name,JsonObject args) {
        var fn=new JsonObject(); fn.addProperty("name",name); fn.addProperty("arguments",args.toString());
        var call=new JsonObject(); call.addProperty("id",id); call.addProperty("type","function"); call.add("function",fn); return call;
    }
    private static String reply(JsonObject... calls) {
        var msg=ChatProtocol.message("assistant",calls.length==0 ? "已提交；以游戏实际执行结果为准。" : "");
        if(calls.length>0) { var list=new JsonArray(); for(var c:calls) list.add(c); msg.add("tool_calls",list); }
        var choice=new JsonObject(); choice.addProperty("finish_reason",calls.length==0?"stop":"tool_calls"); choice.add("message",msg);
        var choices=new JsonArray(); choices.add(choice);
        var response=new JsonObject(); response.add("choices",choices); response.addProperty("model","mock-resolved");
        response.add("usage",json("{\"prompt_tokens\":20,\"completion_tokens\":5}")); return response.toString();
    }
    private static final class Model implements AutoCloseable {
        final HttpServer http;
        final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
        final List<JsonObject> requests=new CopyOnWriteArrayList<>();
        volatile String variant; volatile Throwable error;
        volatile boolean batch;
        int batchStep;
        volatile CountDownLatch release=new CountDownLatch(1);
        String a,b,area,tree,point,detourA,detourB;
        Model() throws Exception {
            http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); http.setExecutor(executor);
            http.createContext("/chat",exchange -> {
                try {
                    var request=json(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    if(batch && request.getAsJsonArray("messages").size()==3
                            && request.getAsJsonArray("messages").get(1).getAsJsonObject().get("role").getAsString().equals("system")) {
                        boolean fixed=false;
                        for(var value:request.getAsJsonArray("tools")) if(value.getAsJsonObject().getAsJsonObject("function").get("name").getAsString().equals("submit_plan")) fixed=true;
                        variant=fixed?"S4-A":"S4-B"; batchStep=0;
                    }
                    int phase=batch?batchStep++:requests.size(); requests.add(request);
                    var bytes=answer(request,phase).getBytes(StandardCharsets.UTF_8);
                    try { exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); }
                    catch(java.io.IOException e) { if(!variant.startsWith("timeout") && !variant.startsWith("stop")) throw e; }
                } catch(Throwable e) { error=e; } finally { exchange.close(); }
            }); http.start();
        }
        void reset(String next) { variant=next; batch=false; requests.clear(); error=null; a=b=area=tree=point=null; release=new CountDownLatch(1); }
        LlmConfig config() {
            return new LlmConfig("http://127.0.0.1:"+http.getAddress().getPort()+"/chat","mock","test-secret","test","max_tokens",10,512,0,0,1,4,64);
        }
        JsonObject deposit(String target,int count) {
            var step=json("{\"action\":\"ai-maid:deposit_items\",\"item\":\"minecraft:wheat\"}");
            step.addProperty("target_id",target); step.addProperty("count",count); return step;
        }
        JsonObject harvest() {
            var step=json("{\"action\":\"ai-maid-farming:harvest_crops\",\"item\":\"minecraft:wheat\",\"count\":3}");
            step.addProperty("target_id",area); return step;
        }
        String action(String id,JsonObject step) {
            var args=new JsonObject(); args.addProperty("request_id",id); args.add("step",step);
            return reply(call(id,"execute_action",args));
        }
        String finish() { return reply(call("finish","finish_goal",json("{\"outcome\":\"completed\",\"summary\":\"完成了。\"}"))); }
        String observe(boolean disturbed) {
            var first=call("a","observe_chest",json("{\"x\":6,\"y\":81,\"z\":6}"));
            return disturbed ? reply(first,call("b","observe_chest",json("{\"x\":9,\"y\":81,\"z\":6}")),call("area","observe_nearby",new JsonObject())) : reply(first);
        }
        String observeFixture() {
            return reply(call("a","observe_chest",json("{\"x\":6,\"y\":81,\"z\":6}")),
                    call("b","observe_chest",json("{\"x\":9,\"y\":81,\"z\":6}")),
                    call("area","observe_nearby",new JsonObject()),
                    call("point","observe_position",json("{\"x\":11,\"y\":81,\"z\":-6}")));
        }
        void fixtureTargets(JsonObject request) {
            a=receipt(request,"a").get("target_id").getAsString();
            b=receipt(request,"b").get("target_id").getAsString();
            point=receipt(request,"point").get("target_id").getAsString();
            for(var value:receipt(request,"area").getAsJsonArray("targets")) {
                var target=value.getAsJsonObject();
                if(target.get("kind").getAsString().equals("area")) area=target.get("target_id").getAsString();
                if(target.get("kind").getAsString().equals("tree")) tree=target.get("target_id").getAsString();
            }
        }
        JsonObject targeted(String action,String target) {
            var step=new JsonObject(); step.addProperty("action",action); step.addProperty("target_id",target); return step;
        }
        String fixtureAnswer(JsonObject request,int phase,String id,boolean fixed) {
            if(!fixed && phase==0) return reply(call("start","start_goal",new JsonObject()));
            int stage=fixed?phase:phase-1;
            if(stage==0) return observeFixture();
            if(stage==1) fixtureTargets(request);
            if(fixed) {
                if(stage>1) { check(receipt(request,"plan").get("ok").getAsBoolean(),"Fixture plan rejected"); return reply(); }
                var back=json("{\"action\":\"ai-maid:return_to_owner\"}");
                var equip=json("{\"action\":\"ai-maid:equip_tool\",\"item\":\"minecraft:iron_axe\",\"slot\":0}");
                var steps=new JsonArray();
                switch(id) {
                    case "S1" -> steps.add(equip);
                    case "S2" -> steps.add(targeted("ai-maid:move_to",point));
                    case "S3","N5" -> steps.add(harvest());
                    case "S5","N3" -> steps.add(targeted("ai-maid-lumber:chop_tree",tree));
                    case "M1" -> { steps.add(harvest()); steps.add(deposit(a,3)); steps.add(back); }
                    case "M2" -> {
                        var harvest=harvest(); harvest.addProperty("item","minecraft:carrot"); steps.add(harvest);
                        var deposit=deposit(a,3); deposit.addProperty("item","minecraft:carrot"); steps.add(deposit); steps.add(back);
                    }
                    case "M3" -> {
                        steps.add(equip); steps.add(targeted("ai-maid-lumber:chop_tree",tree));
                        var deposit=deposit(a,3); deposit.addProperty("item","minecraft:oak_log"); steps.add(deposit); steps.add(back);
                    }
                    case "M4" -> { steps.add(deposit(a,2)); steps.add(deposit(b,3)); steps.add(back); }
                    case "M5" -> {
                        steps.add(deposit(a,3)); var deposit=deposit(b,2); deposit.addProperty("item","minecraft:carrot");
                        steps.add(deposit); steps.add(back);
                    }
                    case "N1" -> steps.add(json("{\"action\":\"ai-maid-farming:collect_wheat\",\"count\":3}"));
                    case "N2","N4" -> steps.add(deposit(a,3));
                    default -> throw new AssertionError(id);
                }
                var args=new JsonObject(); args.addProperty("request_id","fixture"); args.add("steps",steps);
                return reply(call("plan","submit_plan",args));
            }
            var back=json("{\"action\":\"ai-maid:return_to_owner\"}");
            return switch(id) {
                case "S5" -> stage==1 ? action("chop",targeted("ai-maid-lumber:chop_tree",tree)) : finish();
                case "M3" -> switch(stage) {
                    case 1 -> action("equip",json("{\"action\":\"ai-maid:equip_tool\",\"item\":\"minecraft:iron_axe\",\"slot\":0}"));
                    case 2 -> action("chop",targeted("ai-maid-lumber:chop_tree",tree));
                    case 3 -> {
                        var deposit=deposit(a,3); deposit.addProperty("item","minecraft:oak_log");
                        yield action("deposit",deposit);
                    }
                    case 4 -> action("return",back);
                    default -> finish();
                };
                case "D2" -> switch(stage) {
                    case 1 -> action("first",deposit(a,3));
                    case 2 -> action("spare",deposit(b,3));
                    case 3 -> action("return",back);
                    default -> finish();
                };
                case "D3" -> switch(stage) {
                    case 1 -> action("first",deposit(a,3));
                    case 2 -> action("harvest",harvest());
                    case 3 -> action("retry",deposit(a,3));
                    case 4 -> action("return",back);
                    default -> finish();
                };
                case "D4" -> switch(stage) {
                    case 1 -> action("first",deposit(a,3));
                    case 2 -> action("return",back);
                    default -> finish();
                };
                case "D5" -> switch(stage) {
                    case 1 -> action("move",targeted("ai-maid:move_to",point));
                    case 2 -> {
                        check(!receipt(request,"move").get("ok").getAsBoolean(),"Expected blocked direct path");
                        yield reply(call("detour-a","observe_position",json("{\"x\":5,\"y\":81,\"z\":-10}")),
                                call("detour-b","observe_position",json("{\"x\":10,\"y\":81,\"z\":-10}")));
                    }
                    case 3 -> {
                        detourA=receipt(request,"detour-a").get("target_id").getAsString();
                        detourB=receipt(request,"detour-b").get("target_id").getAsString();
                        yield action("waypoint-a",targeted("ai-maid:move_to",detourA));
                    }
                    case 4 -> action("waypoint-b",targeted("ai-maid:move_to",detourB));
                    case 5 -> action("destination",targeted("ai-maid:move_to",point));
                    default -> finish();
                };
                default -> throw new AssertionError(id);
            };
        }
        void targets(JsonObject request,boolean disturbed) {
            a=receipt(request,"a").get("target_id").getAsString();
            if(disturbed) {
                b=receipt(request,"b").get("target_id").getAsString();
                for(var value:receipt(request,"area").getAsJsonArray("targets"))
                    if(value.getAsJsonObject().get("kind").getAsString().equals("area")) area=value.getAsJsonObject().get("target_id").getAsString();
                check(area!=null,"No area");
            }
        }
        String answer(JsonObject request,int phase) throws Exception {
            boolean fixed=variant.endsWith("A"), disturbed=variant.startsWith("D1");
            if(phase==0) {
                var names=new HashSet<String>(); request.getAsJsonArray("tools").forEach(v -> names.add(v.getAsJsonObject().getAsJsonObject("function").get("name").getAsString()));
                check(names.contains(fixed?"submit_plan":"start_goal") && !names.contains(fixed?"start_goal":"submit_plan"),"Wrong tool whitelist");
                check(!names.contains("select_command") && !names.contains("start_wheat_delivery"),"Work strategy bypass");
            }
            if(variant.startsWith("timeout") || variant.startsWith("stop")) {
                check(release.await(15,TimeUnit.SECONDS),"No release");
                return reply(call("late","submit_plan",json("{\"request_id\":\"late\",\"steps\":[{\"action\":\"ai-maid:return_to_owner\"}]}")));
            }
            if(variant.startsWith("wrong")) {
                if(phase==0) return reply(call("wrong",fixed?"start_goal":"submit_plan",new JsonObject()));
                check(receipt(request,"wrong").toString().contains("tool_not_allowed_by_execution_policy"),"Invented tool accepted"); return reply();
            }
            if(variant.startsWith("negative")) return reply();
            if(variant.matches("[SMN][1-5]-A") && !variant.equals("S4-A") || variant.matches("D[2-5]-B")
                    || Set.of("S5-B","M3-B").contains(variant))
                return fixtureAnswer(request,phase,variant.substring(0,2),fixed);
            if(fixed) {
                if(phase==0) return observe(disturbed);
                if(phase==1) {
                    targets(request,disturbed); var steps=new JsonArray();
                    if(disturbed) steps.add(harvest());
                    steps.add(deposit(a,3));
                    if(disturbed) steps.add(json("{\"action\":\"ai-maid:return_to_owner\"}"));
                    var args=new JsonObject(); args.addProperty("request_id","plan"); args.add("steps",steps);
                    return reply(call("plan","submit_plan",args));
                }
                check(receipt(request,"plan").get("ok").getAsBoolean(),"Plan not accepted"); return reply();
            }
            if(phase==0) return reply(call("start","start_goal",new JsonObject()));
            if(variant.equals("false-B")) return phase==1 ? action("return",json("{\"action\":\"ai-maid:return_to_owner\"}")) : finish();
            if(phase==1) return observe(disturbed);
            if(phase==2) { targets(request,disturbed); return disturbed?action("harvest",harvest()):action("deposit",deposit(a,3)); }
            if(!disturbed) return finish();
            return switch(phase) {
                case 3 -> action("first",deposit(a,3));
                case 4 -> {
                    var previous=receipt(request,"first"); check(!previous.get("ok").getAsBoolean(),"Did not fail partially");
                    check(previous.getAsJsonObject("action_result").getAsJsonObject("progress").get("delivered").getAsInt()==1,"No partial receipt");
                    yield action("spare",deposit(b,2));
                }
                case 5 -> action("return",json("{\"action\":\"ai-maid:return_to_owner\"}"));
                case 6 -> finish();
                default -> throw new AssertionError("Unexpected request "+phase);
            };
        }
        @Override public void close() { release.countDown(); http.stop(0); executor.shutdownNow(); }
    }
}
