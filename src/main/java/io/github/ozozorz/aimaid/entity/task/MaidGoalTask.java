package io.github.ozozorz.aimaid.entity.task;

import java.util.*;
import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

/** Game-owned goal lifetime; the optional planner can only supply one validated next action. */
public final class MaidGoalTask implements MaidTaskRunner {
    public record Limits(int actions, int failures, int ticks, int radius) {
        public static final Limits DEFAULT = new Limits(16, 3, 12000, 64);
        public Limits {
            if (actions < 1 || actions > 64 || failures < 1 || failures > 8 || ticks < 100 || ticks > 72000 || radius < 16 || radius > 128)
                throw new IllegalArgumentException("goal_limits");
        }
    }
    private record Attempt(MaidActions.Request request, MaidPlanTask task) {}
    private final UUID id = UUID.randomUUID(), owner;
    private final GlobalPos origin;
    private final String goal;
    private final Limits limits;
    private final List<ActionSnapshot> receipts = new ArrayList<>();
    private final Map<String, Attempt> attempts = new LinkedHashMap<>();
    private MaidPlanTask current;
    private State state = State.RUNNING;
    private String reason = "", conclusion = "";
    private int ticks, failures, thinkingTicks;
    private boolean recorded;

    public MaidGoalTask(AiMaidEntity maid, String goal, Limits limits) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (maid.getOwner() == null || goal.isBlank() || goal.length() > 2000) throw new IllegalArgumentException("invalid_goal");
        this.goal = goal; this.limits = limits; owner = maid.getOwner().getUUID();
        origin = GlobalPos.of(level.dimension(), maid.blockPosition());
    }
    public String goal() { return goal; }
    public GlobalPos origin() { return origin; }
    public Limits limits() { return limits; }
    public String conclusion() { return conclusion; }
    public int completedActions() { return receipts.size(); }
    public boolean deciding() { return active() && (current == null || recorded); }
    @Override public boolean active() { return !state.terminal(); }
    public boolean withinScope(GlobalPos target) {
        return target.dimension() == origin.dimension() && target.pos().distSqr(origin.pos()) <= (double) limits.radius() * limits.radius();
    }
    /** Idempotent for the lifetime of this goal, including already completed actions. */
    public MaidPlanTask submit(AiMaidEntity maid, String requestId, MaidActions.Request request) {
        MaidServerAccess.requireServerThread(maid);
        if (!active() || state == State.PAUSED) throw new IllegalStateException("goal_not_ready");
        var previous = attempts.get(requestId);
        if (previous != null) {
            if (!previous.request().equals(request)) throw new IllegalArgumentException("request_id_conflict");
            return previous.task();
        }
        if (!deciding() || attempts.size() >= limits.actions()) throw new IllegalStateException("action_budget_or_busy");
        var target = request.target() != null ? request.target().position() : request.focus() != null ? request.focus().position() : null;
        if (target != null && !withinScope(target)) throw new IllegalArgumentException("outside_goal_area");
        var prepared = new MaidPlanTask(maid, requestId, List.of(request), false);
        current = prepared; recorded = false; thinkingTicks = 0;
        attempts.put(requestId, new Attempt(request, prepared));
        return prepared;
    }
    @Override public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state != State.RUNNING) return;
        var player = maid.getOwner();
        if (!maid.isAlive() || !maid.isTame() || player == null || !player.isAlive() || player.isSpectator()
                || !player.getUUID().equals(owner) || player.level() != level || !withinScope(GlobalPos.of(level.dimension(), maid.blockPosition()))) {
            cancel(maid, "owner_or_scope_unavailable"); return;
        }
        if (++ticks > limits.ticks() || deciding() && ++thinkingTicks > 2400) { fail(maid, "goal_timeout"); return; }
        if (current == null || recorded) return;
        current.tick(maid, now);
        var snapshot = current.snapshot(maid);
        if (!snapshot.state().terminal()) return;
        recordCurrent(maid);
        if (snapshot.state() == State.CANCELLED) { cancel(maid, snapshot.reason()); return; }
        if (snapshot.state() == State.FAILED && ++failures >= limits.failures()) fail(maid, "failure_budget");
        thinkingTicks = 0;
    }
    private void recordCurrent(AiMaidEntity maid) {
        if (current == null || recorded) return;
        var result = current.actions(maid).getFirst();
        receipts.add(new ActionSnapshot(receipts.size() + 1, result.action(), result.state(), result.count(), result.destination(),
                result.progress(), result.reason(), result.item(), result.slot()));
        recorded = true;
    }
    /** The planner's semantic conclusion is separate from the verified action ledger. */
    public void conclude(AiMaidEntity maid, boolean completed, String summary) {
        MaidServerAccess.requireServerThread(maid);
        if (!deciding() || state == State.PAUSED) throw new IllegalStateException("action_still_running");
        if (completed && (receipts.isEmpty() || !receipts.getLast().state().equals("succeeded")))
            throw new IllegalArgumentException("completion_requires_successful_evidence");
        conclusion = summary; state = completed ? State.SUCCEEDED : State.FAILED;
        reason = completed ? "planner_completed" : "planner_blocked"; clearInteraction(maid);
    }
    public void fail(AiMaidEntity maid, String why) { finish(maid, State.FAILED, why); }
    @Override public void cancel(AiMaidEntity maid, String why) { finish(maid, State.CANCELLED, why); }
    private void finish(AiMaidEntity maid, State outcome, String why) {
        MaidServerAccess.requireServerThread(maid);
        if (!active()) return;
        if (current != null && !recorded) { current.cancel(maid, why); recordCurrent(maid); }
        state = outcome; reason = why; clearInteraction(maid);
    }
    @Override public void pause(AiMaidEntity maid) {
        if (!active()) return;
        state = State.PAUSED;
        if (current != null && !recorded) current.pause(maid);
    }
    @Override public void resume(AiMaidEntity maid) {
        if (state != State.PAUSED) return;
        state = State.RUNNING;
        if (current != null && !recorded) current.resume(maid);
    }
    @Override public void clearInteraction(AiMaidEntity maid) {
        if (current != null) current.clearInteraction(maid);
        else io.github.ozozorz.aimaid.entity.ai.behavior.MaidWorkNavigation.stop(maid);
    }
    @Override public List<ActionSnapshot> actions(AiMaidEntity maid) {
        var result = new ArrayList<>(receipts);
        if (current != null && !recorded) {
            var action = current.actions(maid).getFirst();
            result.add(new ActionSnapshot(result.size() + 1, action.action(), action.state(), action.count(), action.destination(),
                    action.progress(), action.reason(), action.item(), action.slot()));
        }
        return List.copyOf(result);
    }
    @Override public Snapshot snapshot(AiMaidEntity maid) {
        return new Snapshot(id, AIMaid.id("goal").toString(), state, deciding() ? "deciding" : current == null ? "finished" : current.snapshot(maid).step(),
                Map.of("steps_completed", receipts.size(), "actions_started", attempts.size(), "remaining_actions", limits.actions() - attempts.size(),
                        "failures", failures, "remaining_failures", Math.max(0, limits.failures() - failures), "active_ticks", ticks), reason, origin);
    }
    @Override public Component status(AiMaidEntity maid) {
        Component detail = current == null ? Component.translatable("status.ai-maid.goal." + (state.terminal() ? "finished" : "deciding"))
                : recorded && !state.terminal() ? Component.translatable("status.ai-maid.goal.deciding_after", current.status(maid)) : current.status(maid);
        return Component.translatable("status.ai-maid.goal.progress",
                Component.translatable("status.ai-maid.plan.state." + state.name().toLowerCase(Locale.ROOT)), receipts.size(), limits.actions(), detail,
                reason.isEmpty() ? Component.empty() : Component.translatable("status.ai-maid.goal.reason." + reason));
    }
}
