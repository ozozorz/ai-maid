# 整合开发与验证

独立 Gradle Build，通过 Composite Build 加载 Core、伐木、耕种、喂养、搬运和 LLM。

在仓库根目录执行：

```powershell
.\gradlew.bat -p dev/devrun runClient
.\gradlew.bat -p dev/devrun build
.\gradlew.bat -p dev/devrun runClientGameTest
```

依赖版本分别由 `core_version`、`farming_version`、`lumber_version`（均1.3.0）、`aimaid_version`（Feeding / Hauling 1.2.1）和 `llm_version`（0.3.0）控制。LLM 默认关闭；日常运行在本目录 `run/config/ai-maid-llm.json` 配置，通过 `/maid llm reload` 重载。

服务端 GameTest 验证工作模式、工具适配、有限动作、固定计划、自主任务状态机、真实物品数量、目标变化、预算与战斗恢复。客户端 GameTest 使用临时世界和本机模拟模型，覆盖旧小麦预设、固定计划，以及“部分存入→根据实际剩余数量选择授权备用箱→返回”的自主循环、请求去重和迟到回复取消。

独立 LLM Build 的 `runClientGameTest` 另覆盖普通聊天、协议、权限与会话隔离。测试不会读取真实密钥或调用真实模型。实际构建结果见 [验收记录](../../docs/ACCEPTANCE.md)，配置见 [LLM 使用说明](../../addon/llm/README.md)。
