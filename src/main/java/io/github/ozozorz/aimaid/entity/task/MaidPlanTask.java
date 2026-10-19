package io.github.ozozorz.aimaid.entity.task;

import java.util.*;
import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** A bounded, ordered plan. Each action commits once; failure skips all later actions. */
public class MaidPlanTask implements MaidTask {
    private final UUID id = UUID.randomUUID(), ownerId;
    private final GlobalPos origin;
    private final String requestId;
    private final List<MaidActions.Request> requests;
    private final List<MaidAction> operations;
    private final List<ActionSnapshot> receipts = new ArrayList<>();
    private State state = State.RUNNING;
    private String reason = "";
    private int index, activeTicks, stepTicks;

    public MaidPlanTask(AiMaidEntity maid, String requestId, List<MaidActions.Request> requests) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (maid.getOwner() == null) throw new IllegalArgumentException("owner_unavailable");
        this.requestId = Objects.requireNonNull(requestId);
        this.requests = List.copyOf(requests);
        this.operations = MaidActions.prepare(maid, this.requests);
        ownerId = maid.getOwner().getUUID(); origin = GlobalPos.of(level.dimension(), maid.blockPosition());
    }
    public String requestId() { return requestId; }
    public List<MaidActions.Request> requests() { return requests; }
    public boolean active() { return !state.terminal(); }
    private int currentIndex() { return Math.min(index, requests.size() - 1); }
    protected String type() { return AIMaid.id("plan").toString(); }
    protected String step() { return requests.get(currentIndex()).action().toString(); }
    @Override public Snapshot snapshot(AiMaidEntity maid) {
        int harvested = 0, delivered = 0, requested = 0;
        for (var action : actions(maid)) {
            harvested += action.progress().getOrDefault("harvested", 0);
            delivered += action.progress().getOrDefault("delivered", 0);
        }
        for (var request : requests) if (request.action().equals(MaidActions.DEPOSIT_WHEAT)) requested += request.count();
        var target = requests.get(currentIndex()).target();
        if (target == null) target = requests.stream().map(MaidActions.Request::target).filter(Objects::nonNull).findFirst().orElse(null);
        return new Snapshot(id, type(), state, step(), Map.of("requested", requested,
                "available", maid.getInventory().countMatching(s -> s.is(Items.WHEAT)), "harvested", harvested, "delivered", delivered,
                "steps_completed", index, "steps_total", requests.size()), reason, target == null ? null : target.position());
    }
    private ActionSnapshot receipt(AiMaidEntity maid, int i, String actionState, String why) {
        var request = requests.get(i);
        return new ActionSnapshot(i + 1, request.action().toString(), actionState, request.count(),
                request.target() == null ? null : request.target().position(), operations.get(i).result(maid).progress(), why);
    }
    @Override public List<ActionSnapshot> actions(AiMaidEntity maid) {
        var result = new ArrayList<>(receipts);
        for (int i = result.size(); i < requests.size(); i++) {
            boolean current = i == index;
            String phase = current ? state.name().toLowerCase(Locale.ROOT) : "pending";
            result.add(receipt(maid, i, phase, current ? reason.isEmpty() ? operations.get(i).result(maid).reason() : reason : ""));
        }
        return List.copyOf(result);
    }
    @Override public Component status(AiMaidEntity maid) {
        var snap = snapshot(maid);
        var request = requests.get(currentIndex());
        var label = Component.translatable(request.action().toLanguageKey("action"));
        if (request.count() > 0) label.append(" ×" + request.count());
        if (request.target() != null) label.append(" @ " + request.target().position().pos().toShortString());
        return Component.translatable("status.ai-maid.plan.progress", Component.translatable("status.ai-maid.plan.state." + state.name().toLowerCase(Locale.ROOT)),
                Math.min(index + 1, requests.size()), requests.size(), label,
                snap.progress().get("delivered"), snap.progress().get("requested"),
                reason.isEmpty() ? Component.empty() : Component.translatable("status.ai-maid.plan.reason." + reason));
    }
    public void tick(AiMaidEntity maid, long now) {
        var level = MaidServerAccess.requireServerThread(maid);
        if (state != State.RUNNING) return;
        var owner = maid.getOwner();
        if (!maid.isAlive() || !maid.isTame() || owner == null || !owner.isAlive() || owner.isSpectator()
                || !owner.getUUID().equals(ownerId) || owner.level() != level || level.dimension() != origin.dimension()) {
            finish(maid, State.CANCELLED, "owner_unavailable"); return;
        }
        if (++activeTicks > 12000 || ++stepTicks > 2400) { finish(maid, State.FAILED, "timeout"); return; }
        if (maid.isPassenger() || maid.isLeashed()) { finish(maid, State.FAILED, "restrained"); return; }
        if (!maid.onGround()) return;
        var action = operations.get(index);
        action.tick(maid, now);
        var result = action.result(maid); reason = result.reason();
        if (result.state() == State.SUCCEEDED) {
            receipts.add(receipt(maid, index, "succeeded", result.reason()));
            action.stop(maid); index++; stepTicks = 0; reason = "";
            if (index == operations.size()) finish(maid, State.SUCCEEDED, "completed");
        } else if (result.state().terminal()) finish(maid, State.FAILED, result.reason());
    }
    private void finish(AiMaidEntity maid, State outcome, String why) {
        if (state.terminal()) return;
        state = outcome; reason = why;
        if (index < operations.size()) {
            receipts.add(receipt(maid, index, outcome.name().toLowerCase(Locale.ROOT), why));
            for (int i = index + 1; i < operations.size(); i++) receipts.add(receipt(maid, i, "skipped", "previous_step_stopped"));
        }
        clearInteraction(maid);
        if (maid.getOwner() instanceof ServerPlayer owner && owner.connection != null && owner.getUUID().equals(ownerId))
            owner.sendSystemMessage(Component.translatable("message.ai-maid.plan.finished", maid.getDisplayName(), status(maid)));
    }
    @Override public void cancel(AiMaidEntity maid, String why) { MaidServerAccess.requireServerThread(maid); finish(maid, State.CANCELLED, why); }
    public void pause(AiMaidEntity maid) {
        if (active()) { state = State.PAUSED; reason = "combat"; clearInteraction(maid); }
    }
    public void resume(AiMaidEntity maid) {
        if (state == State.PAUSED) { state = State.RUNNING; reason = ""; clearInteraction(maid); }
    }
    public void clearInteraction(AiMaidEntity maid) { operations.get(currentIndex()).stop(maid); }
}
