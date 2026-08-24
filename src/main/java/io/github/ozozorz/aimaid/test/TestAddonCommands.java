package io.github.ozozorz.aimaid.test;

import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class TestAddonCommands {

    public static final MaidCommand PATROL = Registry.register(ModBuiltInRegistries.MAID_COMMAND,
            Identifier.fromNamespaceAndPath("testaddon", "patrol"), new TestPatrolCommand());

    public static void initialize() {

    }

}
