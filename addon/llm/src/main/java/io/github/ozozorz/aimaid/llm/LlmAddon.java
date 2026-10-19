package io.github.ozozorz.aimaid.llm;

import io.github.ozozorz.aimaid.llm.command.LlmCommands;
import io.github.ozozorz.aimaid.llm.config.*;
import io.github.ozozorz.aimaid.llm.network.LlmNetworking;
import io.github.ozozorz.aimaid.llm.service.LlmService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.*;
import java.util.*;

/** 服务由服务器生命周期创建和销毁；退出单人存档不会留下旧服务器会话。 */
public final class LlmAddon implements ModInitializer {
    public static final String MOD_ID = "ai-maid-llm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<MinecraftServer, LlmService> SERVICES = new IdentityHashMap<>();
    @Override public void onInitialize() {
        LlmData.initialize(); LlmNetworking.initialize(); LlmCommands.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> { if (!reload(server)) install(server, null); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var service = service(server); if (service != null) service.tick(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var service = SERVICES.remove(server); if (service != null) service.close(); });
        LOGGER.info("Ai Maid LLM initialized");
    }
    public static LlmService service(MinecraftServer server) { return SERVICES.get(server); }
    public static boolean reload(MinecraftServer server) {
        try {
            var config = LlmConfigLoader.load(FabricLoader.getInstance().getConfigDir().resolve("ai-maid-llm.json"));
            install(server, config.orElse(null)); LOGGER.info("LLM config loaded; enabled={}", config.isPresent()); return true;
        } catch (java.io.IOException | RuntimeException e) {
            // 解析器、HTTP URL 等异常可能包含配置原文；日志只输出异常类别。
            LOGGER.error("LLM config rejected ({}); existing service retained", e.getClass().getSimpleName()); return false;
        }
    }
    static void install(MinecraftServer server, LlmConfig config) {
        if (!server.isSameThread()) throw new IllegalStateException("LLM install requires server thread");
        var replacement = new LlmService(server, config, LlmNetworking::send);
        var old = SERVICES.put(server, replacement); if (old != null) old.close();
    }
}
