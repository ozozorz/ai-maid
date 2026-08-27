package io.github.ozozorz.testaddon.ai;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

public class TestAddonActivities {

    public static final Activity PATROL = register("patrol");

    private static Activity register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("testaddon", name);
        return Registry.register(BuiltInRegistries.ACTIVITY, id, new Activity(id.toString()));
    }

    public static void initialize() {

    }

}
