package io.github.ozozorz.aimaid.lumber.ai;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

public class LumberActivities {


    
    // LumberAddon 自己的工作 Activity
    public static final Activity LUMBER = register("lumber");



    private static Activity register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("ai-maid-lumber", name);
        return Registry.register(BuiltInRegistries.ACTIVITY, id, new Activity(id.toString()));
    }

    public static void initialize() {

    }

}
