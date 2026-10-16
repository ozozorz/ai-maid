package io.github.ozozorz.aimaid.entity;

import net.minecraft.server.level.ServerLevel;

/** 修改实体的公共 API 必须在所属服务器线程调用；读取和客户端镜像不受此限制。 */
public final class MaidServerAccess {
    private MaidServerAccess() {}

    public static ServerLevel requireServerThread(AiMaidEntity maid) {
        if (!(maid.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Maid mutations require the owning server thread");
        }
        return level;
    }
}
