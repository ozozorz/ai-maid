# Ai Maid 集成开发环境

本目录是独立 Gradle Build，通过 Composite Build 加载 Core、伐木、耕种、喂养、搬运，以及正在教学开发的 LLM Addon。

在本目录执行：

```powershell
.\gradlew.bat runClient
.\gradlew.bat runGameTest
```

也可以在仓库根目录使用 `.\gradlew.bat -p dev/devrun runClient`。客户端日志位于本目录 `run/logs/latest.log`。

Core 与工作 Addon 的依赖版本由 `aimaid_version` 控制；LLM Addon 单独使用 `llm_version`。LLM 当前仅输出 `Ai Maid LLM initialized (scaffold)`，不发起模型请求，详见 [LLM 教学骨架](../../addon/llm/README.md)。

服务端 GameTest 覆盖既有跨模组行为；将 LLM 骨架一起加载仅验证其能正常初始化，不代表已经完成模型接入验收。
