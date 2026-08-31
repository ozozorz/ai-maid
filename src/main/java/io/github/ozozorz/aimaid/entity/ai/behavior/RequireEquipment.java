package io.github.ozozorz.aimaid.entity.ai.behavior;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidEquipmentHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class RequireEquipment {

    private RequireEquipment() {
    }
    
    public static <E extends AiMaidEntity> BehaviorControl<E> create(EquipmentSlot equipmentSlot, Predicate<ItemStack> predicate, BehaviorControl<E> delegate) {
        // 变量合法性判断
        Objects.requireNonNull(equipmentSlot);
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(delegate);
        
        return new BehaviorControl<>() {

            @Override
            public Behavior.Status getStatus() {
                return delegate.getStatus();
            }

            @Override
            public Set<MemoryModuleType<?>> getRequiredMemories() {
                return delegate.getRequiredMemories();
            }

            @Override
            public boolean tryStart(ServerLevel level, E maid, long timestamp) {
                if (!MaidEquipmentHelper.ensureEquipment(maid, equipmentSlot, predicate)) {
                    return false;
                }
                return delegate.tryStart(level, maid, timestamp);
            }

            @Override
            public void tickOrStop(ServerLevel level, E maid, long timestamp) {
                if (!MaidEquipmentHelper.ensureEquipment(maid, equipmentSlot, predicate)) {
                    delegate.doStop(level, maid, timestamp);
                    return;
                }
                delegate.tickOrStop(level, maid, timestamp);
            }

            @Override
            public void doStop(ServerLevel level, E maid, long timestamp) {
                delegate.doStop(level, maid, timestamp);
            }

            @Override
            public String debugString() {
                return "RequireEquipment[" + equipmentSlot + " -> " + delegate.debugString() + "]";
            }
        };
    }
}
