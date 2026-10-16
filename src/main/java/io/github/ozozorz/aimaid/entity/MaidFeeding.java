package io.github.ozozorz.aimaid.entity;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 主人手动投喂；固定回复量，接受物品由数据标签决定。 */
public final class MaidFeeding {
    public static final TagKey<Item> HEALING_FOODS = TagKey.create(Registries.ITEM, AIMaid.id("healing_foods"));
    public static final float HEAL_AMOUNT = 4.0F;
    private MaidFeeding() {}

    public static boolean canFeed(AiMaidEntity maid, Player player, ItemStack stack) {
        return maid.isAlive() && player.isAlive() && maid.isTame() && maid.isOwnedBy(player) && !player.isSpectator()
                && player.level() == maid.level() && player.isWithinEntityInteractionRange(maid, 0)
                && maid.getHealth() < maid.getMaxHealth() && stack.is(HEALING_FOODS);
    }

    public static boolean feed(AiMaidEntity maid, Player player, InteractionHand hand) {
        var level = MaidServerAccess.requireServerThread(maid);
        ItemStack stack = player.getItemInHand(hand);
        if (!canFeed(maid, player, stack)) return false;
        int before = stack.getCount();
        var remainder = stack.get(DataComponents.USE_REMAINDER);
        stack.consume(1, player);
        if (remainder != null) {
            player.setItemInHand(hand, remainder.convertIntoRemainder(stack, before,
                    player.hasInfiniteMaterials(), player::handleExtraItemsCreatedOnUse));
        }
        maid.heal(HEAL_AMOUNT);
        maid.playSound(SoundEvents.GENERIC_EAT.value(), 0.7F, 1.0F);
        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY() + maid.getBbHeight() * 0.7,
                maid.getZ(), 3, 0.2, 0.15, 0.2, 0.01);
        return true;
    }
}
