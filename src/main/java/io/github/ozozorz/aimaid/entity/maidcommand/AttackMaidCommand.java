package io.github.ozozorz.aimaid.entity.maidcommand;
import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.combat.MaidCombatMode;
import net.minecraft.world.entity.ai.ActivityData;
/** 跟随、护主之外，主动攻击范围内符合 PvE 规则的敌对生物。 */
public final class AttackMaidCommand extends FollowMaidCommand {
    @Override public MaidCombatMode getCombatMode() { return MaidCombatMode.HUNT; }
    @Override public int getMenuOrder() { return 300; }
    @Override public List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) { return List.of(); }
}
