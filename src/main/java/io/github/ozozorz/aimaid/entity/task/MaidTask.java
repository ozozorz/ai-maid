package io.github.ozozorz.aimaid.entity.task;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

/** A finite server-side job. Acknowledgement and completion are different outcomes. */
public interface MaidTask {
    enum State {
        RUNNING, PAUSED, SUCCEEDED, FAILED, CANCELLED;
        public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
    }

    record Snapshot(UUID id, String type, State state, String step, Map<String, Integer> progress,
                    String reason, GlobalPos destination) {
        public Snapshot { progress = Map.copyOf(progress); }
    }

    Snapshot snapshot(AiMaidEntity maid);
    record ActionSnapshot(int index, String action, String state, int count, GlobalPos destination,
                          Map<String, Integer> progress, String reason, String item, int slot) {
        public ActionSnapshot { progress = Map.copyOf(progress); }
        public ActionSnapshot(int index, String action, String state, int count, GlobalPos destination, Map<String, Integer> progress, String reason) {
            this(index, action, state, count, destination, progress, reason, "", -1);
        }
    }
    default List<ActionSnapshot> actions(AiMaidEntity maid) { return List.of(); }
    Component status(AiMaidEntity maid);
    void cancel(AiMaidEntity maid, String reason);
}
