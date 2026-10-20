# Ai Maid Farming 1.3.0

独立 Fabric 模组：`ai-maid-farming`，依赖 Core 1.3.0、Minecraft 26.2、Java 25。

在本目录执行 `gradlew.bat runClient` 只加载 Core 与本 Addon；`gradlew.bat build` 构建发布 JAR 并运行独立服务端验收。客户端和服务器都需要安装对应 JAR。

使用方法、参数、限制与验收结果见 [工作 Addon 使用说明](../../docs/ADDONS_1_2.md)。

另提供 `ai-maid-farming:harvest_crops` 有限动作和成熟作物区域观察：支持小麦、胡萝卜、马铃薯、甜菜根，补足指定库存数量并补种；保留 `collect_wheat` 与旧小麦配送预设。可通过 Core 执行器组合，也可由 LLM 根据真实结果逐步安排，见 [自主任务架构](../../docs/AUTONOMOUS_TASKS.md)。
