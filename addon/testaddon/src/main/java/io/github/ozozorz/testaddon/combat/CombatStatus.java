package io.github.ozozorz.testaddon.combat;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** 纯查询；GUI 反复读取时不能换武器、寻路或修改任务。 */
public final class CombatStatus {
    private CombatStatus() {}
    public static Component describe(AiMaidEntity maid) {
        if (maid.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                .map(t -> maid.level().getGameTime() - t > 40).orElse(false))
            return Component.translatable("status.testaddon.target_unreachable");
        var held = maid.getMainHandItem();
        if (CombatEquipment.isRanged(held) && !CombatEquipment.canShoot(maid, held))
            return Component.translatable("status.testaddon.no_ammo");
        if (!CombatEquipment.isMelee(held) && !CombatEquipment.canShoot(maid, held))
            return Component.translatable("status.testaddon.unarmed");
        return Component.translatable(WorkDefense.supports(maid.getMaidCommand())
                ? "status.testaddon.defending_work" : "status.testaddon.fighting");
    }
}
