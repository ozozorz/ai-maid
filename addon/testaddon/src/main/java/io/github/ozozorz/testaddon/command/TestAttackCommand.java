package io.github.ozozorz.testaddon.command;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.world.entity.ai.ActivityData;

/** 与护卫共用战斗 Activity，只增加主动清怪策略。 */
public class TestAttackCommand extends TestGuardCommand {
    @Override
    public boolean huntsHostiles() { return true; }

    @Override
    public int getMenuOrder() { return 700; }

    @Override
    public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of();
    }
}
