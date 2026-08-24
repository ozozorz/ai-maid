package io.github.ozozorz.aimaid.entity.schedule;

import io.github.ozozorz.aimaid.AIMaid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.schedule.Activity;

public class ModActivities {

    public static final Activity FOLLOW_OWNER = register("follow_owner");

    private ModActivities() {
    }

    private static Activity register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name);
        return Registry.register(BuiltInRegistries.ACTIVITY, id, new Activity(id.toString()));
    }

    public static void initialize() {

    }

}
