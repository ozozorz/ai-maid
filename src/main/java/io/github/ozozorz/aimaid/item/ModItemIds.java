package io.github.ozozorz.aimaid.item;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItemIds {

    public static final ResourceKey<Item> SUSPICIOUS_SUBSTANCE = create("suspicious_substance");

    public static ResourceKey<Item> create(String name) {
        // 创建物品资源键
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name));
    }

}
