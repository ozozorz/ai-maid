package io.github.ozozorz.aimaid.menu;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class MaidMenuOpener {

    // 和 Vanilla entity container 类似的额外交互 buffer。
    private static final double INTERACTION_BUFFER = 4.0;

    private MaidMenuOpener() {
    }

    public static boolean canOpen(ServerPlayer player, AiMaidEntity maid) {
        return 
            player.level() == maid.level()

            && maid.isAlive()

            && maid.isTame()

            && maid.isOwnedBy(player)

            && player.isWithinEntityInteractionRange(maid, INTERACTION_BUFFER);
    }

    public static boolean open(ServerPlayer player, AiMaidEntity maid) {
        if (!canOpen(player, maid)) {
            return false;
        }

        ExtendedMenuProvider<MaidMenuData> provider = new ExtendedMenuProvider<>() {
            
            @Override
            public Component getDisplayName() {
                return maid.getDisplayName();
            }

            @Override
            public MaidMenu createMenu(int containerId, Inventory inventory, Player openingPlayer) {
                // 真正 Menu 再做一次服务器验证。
                if (!(openingPlayer instanceof ServerPlayer serverPlayer)) {
                    return null;
                }
                if (!canOpen(player, maid)) {
                    return null;
                }
                return new MaidMenu(containerId, inventory, maid);
            }

            @Override
            public MaidMenuData getScreenOpeningData(ServerPlayer openingPlayer) {
                // 只额外同步实体 ID。35 个 ItemStack 不需要自己发，AbstractContainerMenu 会同步。
                return new MaidMenuData(maid.getId());
            }
        };

        return player.openMenu(provider).isPresent();
    }

}
