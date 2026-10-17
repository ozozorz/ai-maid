package io.github.ozozorz.aimaid.entity.combat;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

public final class CombatProtection {
    private CombatProtection() {}

    public static void initialize() {
        // 包括已经离弦、之后才遇到友方的箭，不依赖女仆仍在战斗命令中。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
                !(source.getEntity() instanceof AiMaidEntity maid)
                || !CombatRules.isProtected(maid, victim));
    }
}
