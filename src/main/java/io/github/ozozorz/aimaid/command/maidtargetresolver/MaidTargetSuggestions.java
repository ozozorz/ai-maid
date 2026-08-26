package io.github.ozozorz.aimaid.command.maidtargetresolver;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class MaidTargetSuggestions {

    private MaidTargetSuggestions() {
    }

    public static CompletableFuture<Suggestions> suggestNames(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) throws CommandSyntaxException {

        ServerPlayer player = context.getSource().getPlayerOrException();

        List<String> names = MaidTargetQueries
                .findOwnedMaidsNearby(player)
                .stream()
                .filter(AiMaidEntity::hasCustomName)
                .sorted(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .map(maid -> maid.getCustomName().getString())
                .distinct()
                .map(StringArgumentType::escapeIfRequired)
                .toList();

        for (String name : names) {
            builder.suggest(name);
        }

        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggestUuids(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) throws CommandSyntaxException {

        ServerPlayer player = context.getSource().getPlayerOrException();

        MaidTargetQueries
                .findOwnedMaidsNearby(player)
                .stream()
                .sorted(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .forEach(maid -> {
                    String uuid = maid.getUUID().toString();
                    Component tooltip = Component.literal(maid.hasCustomName() ? maid.getCustomName().getString()
                            : maid.getDisplayName().getString());
                    builder.suggest(uuid, tooltip);
                });

        return builder.buildFuture();
    }

}
