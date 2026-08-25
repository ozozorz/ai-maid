package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.schedule.Activity;

// 任何可以注册到 Maid Command Registry 的对象，
// 都必须是一个 MaidCommand。
public interface MaidCommand {
    // 创建这只 Maid 的 Brain 时，我这个 Command 要往 Brain 里安装哪些 ActivityData？
    default List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of();
    }

    // Brain 已经创建好了。现在这只 Maid 使用我这个 Command 时，应该按什么顺序尝试 Activity？
    List<Activity> getActivityCandidates(AiMaidEntity maid);
}
