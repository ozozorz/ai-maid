# TestAddon

AI Maid 的独立参考 Addon，提供巡逻、单块/有限整树伐木、近战和弓弩 PvE、自卫护主，以及工作中自卫和战后恢复。

当前版本 1.1.0，需要 Core 1.1.0。具体规则见 [战斗](../../docs/COMBAT.md)、[伐木](../../docs/LUMBER_AND_DEATH.md)、[动画](../../docs/ANIMATION.md)、[工作自卫](../../docs/WORK_DEFENSE.md) 和 [Core 接入约定](../../docs/ADDON_API.md)。

在本目录执行 `gradlew.bat runClient` 加载 Core + TestAddon，`gradlew.bat build` 构建并执行服务端验收，`gradlew.bat runClientGameTest` 执行真实客户端验收。它通过 Maven coordinate 消费 Core，settings.gradle 将本地依赖替换为 Core 源码。

本 Addon 同时作为 Core 集成回归用例；测试源码位于 src/gametest，不打入发布 JAR。沿用现有 CC0-1.0 许可。
