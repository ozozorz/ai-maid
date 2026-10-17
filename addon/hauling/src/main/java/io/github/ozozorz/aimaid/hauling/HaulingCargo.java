package io.github.ozozorz.aimaid.hauling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;

/** 这只是货物位置/种类/数量的凭据；唯一真实物品仍在 MaidInventory 中。 */
public record HaulingCargo(int slot, ItemStackTemplate kind, int count) {
    public static final Codec<HaulingCargo> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0,34).fieldOf("slot").forGetter(HaulingCargo::slot),
            ItemStackTemplate.CODEC.fieldOf("kind").forGetter(HaulingCargo::kind),
            Codec.intRange(1,16).fieldOf("count").forGetter(HaulingCargo::count)).apply(i, HaulingCargo::new));
    public static final AttachmentType<HaulingCargo> TYPE = AttachmentRegistry.createPersistent(HaulingAddon.id("cargo"), CODEC);
    public boolean matches(AiMaidEntity maid) {
        ItemStack stack = maid.getInventory().getItem(slot);
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, kind.create());
    }
    public static HaulingCargo current(AiMaidEntity maid) {
        var cargo = maid.getAttached(TYPE);
        return cargo != null && cargo.matches(maid) ? cargo : null;
    }
    public static void initialize() {}
}
