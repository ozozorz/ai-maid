package io.github.ozozorz.aimaid.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/** 只开放 Vanilla 的装填和发射机制，不修改其计算规则。 */
@Mixin(ProjectileWeaponItem.class)
public interface ProjectileWeaponItemInvoker {
    @Invoker("draw")
    static List<ItemStack> aimaid$draw(ItemStack weapon, ItemStack ammunition, LivingEntity shooter) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("shoot")
    void aimaid$shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand,
            ItemStack weapon, List<ItemStack> projectiles, float power, float uncertainty,
            boolean critical, LivingEntity target);
}
