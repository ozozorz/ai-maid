package io.github.ozozorz.testaddon.ai;

import java.util.Set;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.testaddon.command.TestGuardCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class TestGuardSensor extends Sensor<AiMaidEntity> {

    // 这些字段属于这一只女仆的 Sensor 实例。
    private boolean wasGuarding;
    private int lastSelfHurtTimestamp;
    private LivingEntity lastOwner;
    private int lastOwnerHurtTimestamp;

    public TestGuardSensor() {
        // 只读取少量现成状态，每 tick 检查一次。
        super(1);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
    }

    @Override
    protected void doTick(ServerLevel level, AiMaidEntity maid) {
        LivingEntity owner = maid.getOwner();

        int selfTimestamp = maid.getLastHurtByMobTimestamp();
        int ownerTimestamp = owner == null
            ? 0
            : owner.getLastHurtByMobTimestamp();

        boolean guarding = TestGuardCommand.isGuarding(maid);

        // 首次进入护卫时建立基线，不追究开启前的旧袭击。
        boolean canReact = guarding && this.wasGuarding;

        boolean newSelfHit = canReact
            && selfTimestamp != this.lastSelfHurtTimestamp;

        // 主人重生或重新加载后，不能沿用旧对象的时间戳。
        boolean newOwnerHit = canReact
            && owner != null
            && owner == this.lastOwner
            && ownerTimestamp != this.lastOwnerHurtTimestamp;

        // 即使没有进入战斗，也推进观察记录。
        this.wasGuarding = guarding;
        this.lastSelfHurtTimestamp = selfTimestamp;
        this.lastOwner = owner;
        this.lastOwnerHurtTimestamp = ownerTimestamp;

        if (!guarding) {
            return;
        }

        // 第一版保持当前目标，不在战斗中频繁换敌人。
        if (maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET)) {
            return;
        }

        // 同一轮观察中，优先处理女仆自身遭受的袭击。
        LivingEntity target = newSelfHit
            ? maid.getLastHurtByMob()
            : null;

        if (!TestGuardCommand.canDefendAgainst(maid, target)) {
            target = newOwnerHit && owner != null
                ? owner.getLastHurtByMob()
                : null;
        }

        if (!TestGuardCommand.canDefendAgainst(maid, target)) {
            return;
        }

        maid.getBrain().setMemory(
            MemoryModuleType.ATTACK_TARGET,
            target
        );

        // 新目标不能继承先前移动留下的失败计时。
        maid.getBrain().eraseMemory(
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
    }
}