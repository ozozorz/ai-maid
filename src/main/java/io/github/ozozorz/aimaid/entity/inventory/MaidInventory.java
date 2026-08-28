package io.github.ozozorz.aimaid.entity.inventory;

import net.minecraft.world.SimpleContainer;

public class MaidInventory extends SimpleContainer {

    public static final int STORAGE_SIZE = 35;

    public MaidInventory() {
        super(STORAGE_SIZE);
    }

}
