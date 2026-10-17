package io.github.ozozorz.aimaid.integration.ai;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

public class FixtureActivities {

    public static final Activity PATROL = register("patrol");
    
    // TestAddon 自己的工作 Activity
    public static final Activity LUMBER = io.github.ozozorz.aimaid.lumber.ai.LumberActivities.LUMBER;

    public static final Activity GUARD_FIGHT = io.github.ozozorz.aimaid.entity.schedule.ModActivities.FIGHT;

    private static Activity register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("ai-maid-integration-tests", name);
        return Registry.register(BuiltInRegistries.ACTIVITY, id, new Activity(id.toString()));
    }

    public static void initialize() {

    }

}
