package io.github.ozozorz.aimaid.llm;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LLM Addon 的公共入口，由 Fabric Loader 创建并调用。 */
public final class LlmAddon implements ModInitializer {
    public static final String MOD_ID = "ai-maid-llm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 当前只确认模组已经加载；后续逐步加入配置与服务初始化。
        LOGGER.info("Ai Maid LLM initialized (scaffold)");
    }
}
