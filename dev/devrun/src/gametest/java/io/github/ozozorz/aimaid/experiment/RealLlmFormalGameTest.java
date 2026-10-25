package io.github.ozozorz.aimaid.experiment;

import io.github.ozozorz.aimaid.llm.config.LlmConfigLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit opt-in formal session: twenty cases, five paired A/B repeats, unchanged scoring. */
public final class RealLlmFormalGameTest implements FabricClientGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("real-llm-formal");
    private static final int EXPECTED_RUNS = 200;
    private static final long ORDER_SEED = 20261020L;

    @Override public void runTest(ClientGameTestContext context) {
        String configPath = System.getProperty("aimaid.experiment.realFormalConfig", "");
        String outputPath = System.getProperty("aimaid.experiment.realFormalOutput", "");
        if (configPath.isBlank() && outputPath.isBlank()) {
            LOGGER.info("Formal real LLM experiment skipped: explicit configuration and output paths are required.");
            return;
        }
        if (configPath.isBlank() || outputPath.isBlank())
            throw new IllegalArgumentException("Both realFormalConfig and realFormalOutput are required");
        var configFile = Path.of(configPath).toAbsolutePath();
        var output = Path.of(outputPath).toAbsolutePath();
        if (!Files.isRegularFile(configFile) || Files.exists(output.resolve("manifest.json")))
            throw new IllegalArgumentException("Formal experiment requires an existing configuration and unused output session");

        var active = new AtomicReference<ExperimentRunner>();
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var runner = active.get();
            if (runner != null) runner.tick();
        });
        try {
            var config = LlmConfigLoader.load(configFile)
                    .orElseThrow(() -> new IllegalArgumentException("Real LLM is disabled"));
            var schedule = ExperimentCases.schedule(ExperimentCases.ALL, 5, ORDER_SEED, null);
            if (schedule.size() != EXPECTED_RUNS
                    || schedule.stream().filter(t -> t.mode().equals("A")).count() != 100
                    || schedule.stream().filter(t -> t.mode().equals("B")).count() != 100)
                throw new AssertionError("Formal session must contain 100 A and 100 B trials");
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                var connection = world.getConnection();
                connection.waitForChunksRender();
                var runner = server.computeOnServer(s -> new ExperimentRunner(s, connection.getServerPlayer(), config,
                        schedule, ORDER_SEED, ExperimentRunner.DEFAULT_SECONDS, output, LOGGER::info));
                active.set(runner);
                try {
                    // Per-trial wall deadlines remain 300 seconds. This tick limit bounds only the whole session.
                    server.waitFor(s -> runner.done(), EXPECTED_RUNS * (ExperimentRunner.DEFAULT_SECONDS + 10) * 20);
                    var rows = server.computeOnServer(s -> runner.results());
                    if (rows.size() != EXPECTED_RUNS || rows.stream().anyMatch(row -> row.get("interrupted").getAsBoolean()))
                        throw new AssertionError("Formal experiment interrupted: " + runner.status());
                    LOGGER.info("Formal real LLM experiment completed: {} trials. Evidence: {}", EXPECTED_RUNS, output);
                } finally {
                    server.runOnServer(s -> { if (!runner.done()) runner.stop("formal_driver_stopped"); });
                    active.set(null);
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Formal real LLM experiment could not complete", e);
        } finally {
            active.set(null);
        }
    }
}
