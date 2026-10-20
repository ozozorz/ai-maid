package io.github.ozozorz.aimaid.experiment;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Append-only raw evidence plus machine-readable results. Safe for HTTP callback threads. */
public final class ExperimentLog implements AutoCloseable {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> COLUMNS = List.of("run_id", "case_id", "group", "repeat", "mode", "seed",
            "feasible", "goal_met", "constraints_ok", "success", "correct_blocked", "review_required",
            "completion_signal", "false_completion_signal", "end_reason", "task_reason", "disturbance_triggered",
            "elapsed_ms", "llm_calls", "model_errors", "actions_started", "prompt_tokens", "completion_tokens", "usage_complete");
    private final Path directory;
    private final BufferedWriter events, csv, reviews;
    private final long epoch = System.nanoTime();
    private IOException failure;
    private boolean closed;
    private String run = "";
    private int calls, errors, responses, promptKnown, completionKnown;
    private long promptTokens, completionTokens;

    public ExperimentLog(Path directory, JsonObject manifest) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
        // CREATE_NEW prevents silently mixing two sessions.
        Files.writeString(directory.resolve("manifest.json"), PRETTY.toJson(manifest), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        events = Files.newBufferedWriter(directory.resolve("events.jsonl"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        csv = Files.newBufferedWriter(directory.resolve("results.csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        reviews = Files.newBufferedWriter(directory.resolve("reviews.csv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        csv.write(String.join(",", COLUMNS)); csv.newLine(); csv.flush();
        reviews.write("run_id,correct_blocked,claimed_completed,notes"); reviews.newLine(); reviews.flush();
    }
    public Path directory() { return directory; }
    public synchronized IOException failure() { return failure; }
    public synchronized void begin(String id) {
        run = id; calls = errors = responses = promptKnown = completionKnown = 0;
        promptTokens = completionTokens = 0;
    }
    /** Capture the run ID, so a late cancelled response never contaminates the following trial. */
    public synchronized java.util.function.Consumer<JsonObject> trace() {
        String id = run;
        return event -> append(id, event);
    }
    public synchronized void event(JsonObject event) { append(run, event); }
    private synchronized void append(String id, JsonObject source) {
        if (closed) return;
        var row = source.deepCopy();
        row.addProperty("run_id", id);
        row.addProperty("utc", Instant.now().toString());
        row.addProperty("session_ms", (System.nanoTime() - epoch) / 1_000_000);
        if (id.equals(run)) {
            String kind = row.has("event") ? row.get("event").getAsString() : "";
            if (kind.equals("model_request")) calls++;
            if (kind.equals("model_error")) errors++;
            if (kind.equals("model_response")) {
                responses++;
                var usage = row.has("usage") && row.get("usage").isJsonObject() ? row.getAsJsonObject("usage") : new JsonObject();
                Long p = tokenCount(usage, "prompt_tokens"), c = tokenCount(usage, "completion_tokens");
                if (p != null) { promptTokens += p; promptKnown++; }
                if (c != null) { completionTokens += c; completionKnown++; }
            }
        }
        try { events.write(row.toString()); events.newLine(); events.flush(); }
        catch (IOException e) { failure = e; }
    }
    private static Long tokenCount(JsonObject usage, String key) {
        try {
            if (!usage.has(key) || !usage.get(key).isJsonPrimitive() || !usage.getAsJsonPrimitive(key).isNumber()) return null;
            long count = usage.get(key).getAsBigDecimal().longValueExact();
            return count >= 0 ? count : null;
        } catch (ArithmeticException | NumberFormatException e) { return null; }
    }
    public synchronized void finish(JsonObject result) throws IOException {
        if (failure != null) throw failure;
        result.addProperty("llm_calls", calls); result.addProperty("model_errors", errors);
        boolean complete = calls > 0 && responses == calls && errors == 0 && promptKnown == responses && completionKnown == responses;
        result.addProperty("usage_complete", complete);
        result.add("prompt_tokens", complete ? new JsonPrimitive(promptTokens) : JsonNull.INSTANCE);
        result.add("completion_tokens", complete ? new JsonPrimitive(completionTokens) : JsonNull.INSTANCE);
        result.addProperty("known_prompt_tokens", promptTokens); result.addProperty("known_completion_tokens", completionTokens);
        String id = result.get("run_id").getAsString();
        if (!id.matches("[A-Z][0-9]+-[0-9]+-[AB]")) throw new IOException("Invalid run ID");
        Files.writeString(directory.resolve(id + ".json"), PRETTY.toJson(result), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        csv.write(String.join(",", COLUMNS.stream().map(k -> cell(result.get(k))).toList())); csv.newLine(); csv.flush();
        reviews.write(cell(new JsonPrimitive(id)) + ",,,"); reviews.newLine(); reviews.flush();
        var event = new JsonObject(); event.addProperty("event", "run_finished"); event.add("result", result.deepCopy()); event(event);
        if (failure != null) throw failure;
    }
    public static String cell(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        String text = value.isJsonPrimitive() ? value.getAsString() : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { events.close(); } finally { try { csv.close(); } finally { reviews.close(); } }
    }
}
