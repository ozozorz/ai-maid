package io.github.ozozorz.aimaid.integration.command;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
public final class FixtureCommands {
    public static final MaidCommand PATROL = Registry.register(ModBuiltInRegistries.MAID_COMMAND, Identifier.parse("ai-maid-integration-tests:patrol"), new FixturePatrolCommand());
    public static final MaidCommand GUARD = MaidCommands.FOLLOW;
    public static final MaidCommand ATTACK = MaidCommands.ATTACK;
    public static final MaidCommand LUMBER = io.github.ozozorz.aimaid.lumber.LumberAddon.LUMBER;
    public static void initialize() {}
}
