package io.github.ozozorz.aimaid.entity.ai.sensing.api;

/** 初始化阶段贡献 Sensor；不要依赖本 Addon 的普通 main entrypoint 已经运行。 */
public interface AiMaidAddonEntrypoint {

    void register(AiMaidAddonRegistrar registrar);

}
