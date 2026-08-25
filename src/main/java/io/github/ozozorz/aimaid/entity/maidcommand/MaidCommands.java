package io.github.ozozorz.aimaid.entity.maidcommand;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class MaidCommands {

    public static final Identifier FREE_ID = id("free");
    public static final Identifier FOLLOW_ID = id("follow");
    public static final Identifier STAY_ID = id("stay");

    public static final MaidCommand FREE = register(FREE_ID, new FreeMaidCommand());
    public static final MaidCommand FOLLOW = register(FOLLOW_ID, new FollowMaidCommand());
    public static final MaidCommand STAY = register(STAY_ID, new StayMaidCommand());

    private MaidCommands() {
    }

    private static MaidCommand register(Identifier id, MaidCommand maidCommand) {
        return Registry.register(ModBuiltInRegistries.MAID_COMMAND, id, maidCommand);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name);
    }

    public static void initialize() {

    }

}
