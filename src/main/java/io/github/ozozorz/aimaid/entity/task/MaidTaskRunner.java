package io.github.ozozorz.aimaid.entity.task;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;

/** Brain-facing lifecycle shared by fixed plans and feedback-driven goals. */
public interface MaidTaskRunner extends MaidTask {
    boolean active();
    void tick(AiMaidEntity maid, long now);
    void pause(AiMaidEntity maid);
    void resume(AiMaidEntity maid);
    void clearInteraction(AiMaidEntity maid);
}
