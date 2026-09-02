package io.github.ozozorz.aimaid.menu;

import io.github.ozozorz.aimaid.AIMaid;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModMenuTypes {

    public static final ExtendedMenuType<MaidMenu, MaidMenuData> MAID = 
        Registry.register(BuiltInRegistries.MENU, AIMaid.id("maid"), 
            new ExtendedMenuType<>(MaidMenu::new, MaidMenuData.STREAM_CODEC));

    private ModMenuTypes() {
    }

    public static void initialize() {
        // 触发类加载与静态注册
    }

}
