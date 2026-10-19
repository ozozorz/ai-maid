package io.github.ozozorz.aimaid.entity.task;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.MaidServerAccess;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Transient: unloading never replays partially completed world mutations. */
public final class MaidTasks {
    private static final AttachmentType<MaidTask> CURRENT = AttachmentRegistry.create(AIMaid.id("task"));
    private MaidTasks() {}
    public static void initialize() {}
    public static MaidTask current(AiMaidEntity maid) { return maid.getAttached(CURRENT); }
    public static void install(AiMaidEntity maid, MaidTask task) {
        MaidServerAccess.requireServerThread(maid);
        cancel(maid, "replaced");
        maid.setAttached(CURRENT, task);
    }
    public static boolean cancel(AiMaidEntity maid, String reason) {
        MaidServerAccess.requireServerThread(maid);
        var task = current(maid);
        if (task == null || task.snapshot(maid).state().terminal()) return false;
        task.cancel(maid, reason);
        return true;
    }
}
