package io.github.ozozorz.aimaid.testsupport.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 仅在 GameTest 模组中加载。
 * 26.2 halt 的 executeBlocking 会在渲染线程等待服务器，而 Fabric 的服务器此时
 * 停在测试 Phaser。先排队，让 disconnect 后面的原版等待循环推进测试线程屏障。
 * 正常 halt/保存/玩家移除仍执行；Core 的真实保存后重进测试覆盖退出结果。
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerCloseMixin {
    @Redirect(method = "halt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void aimaid$queueTestClose(IntegratedServer server, Runnable action) {
        server.execute(action);
    }
}
