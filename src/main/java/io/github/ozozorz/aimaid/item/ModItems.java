package io.github.ozozorz.aimaid.item;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.CompostableRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ModItems {

    public static final Item SUSPICIOUS_SUBSTANCE = register(ModItemIds.SUSPICIOUS_SUBSTANCE, Item::new,
            new Item.Properties());

    public static Item register(ResourceKey<Item> itemKey, Function<Item.Properties, Item> itemFactory,
            Item.Properties settings) {
        // 创建物品实例
        Item item = itemFactory.apply(settings.setId(itemKey));

        // 注册物品
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        return item;
    }

    public static void initialize() {
        // 获取修改配料组条目的事件。
        // 并注册一个事件处理程序，将我们的可疑项目添加到成分组中。
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register((creativeTab) -> creativeTab.accept(ModItems.SUSPICIOUS_SUBSTANCE));

        // 将可疑物质添加到堆肥登记处，有 30% 的机会提高堆肥水平。
        CompostableRegistry.INSTANCE.add(ModItems.SUSPICIOUS_SUBSTANCE, 0.3f);

        // 将可疑物质添加到燃料登记中，燃烧时间为 30 秒。
        // 请记住，《我的世界》使用刻度来处理基于逻辑的时间。
        // 20 个刻度 = 1 秒。
        FuelValueEvents.BUILD.register((builder, context) -> {
            builder.add(ModItems.SUSPICIOUS_SUBSTANCE, 30 * 20);
        });
    }

}
