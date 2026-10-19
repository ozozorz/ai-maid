package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;

/** One bounded operation, ticked only on the server. Constructing an action must not change the world. */
public interface MaidAction {
    record Result(MaidTask.State state, String reason, Map<String, Integer> progress) {
        public Result { progress = Map.copyOf(progress); }
    }
    void tick(AiMaidEntity maid, long now);
    Result result(AiMaidEntity maid);
    /** Release movement/container interaction, including when paused; keep committed effects. */
    void stop(AiMaidEntity maid);
}
