package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.Comparator;
import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.world.entity.player.Player;

public class MaidCommandMenu {

    private MaidCommandMenu() {
    }

    private static final Comparator<MaidCommand> MAID_COMMAND_ORDER = Comparator.comparing(MaidCommand::getMenuOrder)
            .thenComparing(MaidCommand::getRegistryId);

    public static List<MaidCommand> getVisibleCommands() {
        return ModBuiltInRegistries.MAID_COMMAND
                .stream()
                .filter(MaidCommand::isVisibleInCommandMenu)
                .sorted(MAID_COMMAND_ORDER)
                .toList();
    }

    public static List<MaidCommand> getSelectableCommands(AiMaidEntity maid, Player player) {
        return getVisibleCommands()
                .stream()
                .filter(maidCommand -> maidCommand.canPlayerSelect(maid, player))
                .toList();
    }

}
