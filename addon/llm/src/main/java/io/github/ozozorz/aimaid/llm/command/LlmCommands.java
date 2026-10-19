package io.github.ozozorz.aimaid.llm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ozozorz.aimaid.command.maidtargetresolver.*;
import io.github.ozozorz.aimaid.llm.LlmAddon;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class LlmCommands {
    private LlmCommands() {}
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, build, selection) -> {
            var root = target(Commands.literal("maid"), NearestMaidTargetResolver.INSTANCE);
            root.then(target(Commands.literal("nearest"), NearestMaidTargetResolver.INSTANCE));
            var entity = Commands.argument("maid", EntityArgument.entity());
            entity.then(chat(new EntityArgumentMaidTargetResolver("maid"))).then(control(new EntityArgumentMaidTargetResolver("maid")));
            root.then(entity);
            root.then(Commands.literal("uuid").then(Commands.argument("maid_uuid", UuidArgument.uuid())
                    .suggests(MaidTargetSuggestions::suggestUuids).then(chat(new UuidMaidTargetResolver("maid_uuid"))).then(control(new UuidMaidTargetResolver("maid_uuid")))));
            root.then(Commands.literal("name").then(Commands.argument("maid_name", StringArgumentType.string())
                    .suggests(MaidTargetSuggestions::suggestNames).then(chat(new NameMaidTargetResolver("maid_name"))).then(control(new NameMaidTargetResolver("maid_name")))));
            root.then(Commands.literal("llm").then(Commands.literal("reload").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> {
                        boolean ok = LlmAddon.reload(c.getSource().getServer());
                        c.getSource().sendSuccess(() -> Component.translatable("llm.ai-maid.message." + (ok ? "reloaded" : "config_error")), false);
                        return ok ? 1 : 0;
                    })));
            dispatcher.register(root);
        });
    }
    private static LiteralArgumentBuilder<CommandSourceStack> target(LiteralArgumentBuilder<CommandSourceStack> node, MaidTargetResolver resolver) {
        return node.then(chat(resolver)).then(control(resolver));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> chat(MaidTargetResolver resolver) {
        return Commands.literal("chat").executes(c -> action(c, resolver, "open", ""))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> action(c, resolver, "send", StringArgumentType.getString(c, "message"))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> control(MaidTargetResolver resolver) {
        return Commands.literal("llm")
                .then(Commands.literal("cancel").executes(c -> action(c, resolver, "cancel", "")))
                .then(Commands.literal("clear").executes(c -> action(c, resolver, "clear", "")))
                .then(Commands.literal("persona").then(Commands.literal("reset").executes(c -> action(c, resolver, "persona", "")))
                        .then(Commands.argument("persona", StringArgumentType.greedyString()).executes(c -> action(c, resolver, "persona", StringArgumentType.getString(c, "persona")))));
    }
    private static int action(CommandContext<CommandSourceStack> context, MaidTargetResolver resolver, String action, String text) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException(); var maid = resolver.resolve(context);
        var service = LlmAddon.service(context.getSource().getServer());
        if (service == null) return 0;
        if (service.resolve(player, maid.getUUID()) != maid) { service.error(player, maid.getUUID(), "unavailable"); return 0; }
        switch (action) {
            case "open" -> service.open(player, maid);
            case "send" -> { return service.chat(player, maid, text) ? 1 : 0; }
            case "cancel" -> service.cancel(player, maid, false);
            case "clear" -> service.cancel(player, maid, true);
            case "persona" -> service.persona(player, maid, text);
        }
        return 1;
    }
}
