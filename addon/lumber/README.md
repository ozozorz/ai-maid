# Ai Maid Lumber 1.3.0

独立 Fabric 模组：`ai-maid-lumber`，依赖 Core 1.3.0、Minecraft 26.2、Java 25。

在本目录执行 `gradlew.bat runClient` 只加载 Core 与本 Addon；`gradlew.bat build` 构建发布 JAR 并运行独立服务端验收。客户端和服务器都需要安装对应 JAR。

使用方法、参数、限制与验收结果见 [工作 Addon 使用说明](../../docs/ADDONS_1_2.md)。

另提供 `ai-maid-lumber:chop_tree` 有限动作和自然树观察：处理指定树木快照，使用当前斧头逐块破坏，并走近拾取掉落物；砍断、跳过、实际拾取分别记录。可通过 Core 执行器组合，也可由 LLM 根据真实结果逐步安排，见 [自主任务架构](../../docs/AUTONOMOUS_TASKS.md)。
