# Ai Maid 集成开发环境

本目录是独立 Gradle Build，通过 Composite Build 加载 Core、伐木、耕种、喂养、搬运与可选 LLM Addon。

```powershell
.\gradlew.bat runClient
.\gradlew.bat build
.\gradlew.bat runClientGameTest
```

也可在仓库根目录使用 `.\gradlew.bat -p dev/devrun runClient`。客户端日志位于本目录 `run/logs/latest.log`。

依赖版本分别由 `core_version`（Core 1.2.2）、`aimaid_version`（工作 Addon 1.2.1）和 `llm_version`（LLM 0.1.0）控制。LLM 默认关闭，在本目录的 `run/config/ai-maid-llm.json` 配置后，用 `/maid llm reload` 重载。详见 [LLM 使用说明](../../addon/llm/README.md)。

服务端 GameTest 覆盖跨模组工作行为，并检验 LLM 工具对四个正式命令的参数适配、真实工作执行与停工状态查询。LLM 的完整客户端/模拟服务测试放在独立 Build，使用 `.\gradlew.bat -p addon/llm runClientGameTest` 从仓库根目录执行。验收边界和结果见 [LLM 接入与验收](../../docs/LLM.md)。
