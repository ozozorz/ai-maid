package io.github.ozozorz.aimaid.command.maidtargetresolver;

import java.util.List;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import net.minecraft.server.level.ServerPlayer;

public class MaidTargetQueries {

    public static final double DEFAULT_SEARCH_RADIUS = 128.0D;

    private MaidTargetQueries() {
    }

    // 输入：
    // ServerPlayer
    //
    // 输出：
    // 附近、已加载、活着、属于这个玩家的 Maid
    public static List<AiMaidEntity> findOwnedMaidsNearby(ServerPlayer player) {
        return findOwnedMaidsNearby(player, DEFAULT_SEARCH_RADIUS);
    }

    private static List<AiMaidEntity> findOwnedMaidsNearby(ServerPlayer player, double radius) {
        return player.level()
                .getEntitiesOfClass(AiMaidEntity.class, player.getBoundingBox().inflate(radius),
                        maid -> maid.isAlive() && maid.isTame() && maid.isOwnedBy(player));
    }

}
