package io.github.ozozorz.testaddon.command;

import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class TestAddonCommands {

    public static final MaidCommand PATROL = 
        Registry.register(
            ModBuiltInRegistries.MAID_COMMAND,
            Identifier.fromNamespaceAndPath("testaddon", "patrol"), 
            new TestPatrolCommand()
        );

    public static final MaidCommand LUMBER = 
        Registry.register(
            ModBuiltInRegistries.MAID_COMMAND, 
            Identifier.fromNamespaceAndPath("testaddon", "lumber"), 
            new TestLumberCommand()
        );

    public static void initialize() {

    }

}
