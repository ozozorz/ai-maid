# LLM 接入与验收

版本：LLM Addon 0.1.0，Core 1.2.2，四个工作 Addon 1.2.1。日期：2026-10-18。

## 职责与真实调用链

1. Fabric 创建 `LlmAddon`，注册命令、网络包和服务器生命周期。服务器启动时加载配置，关闭时取消请求并释放 HTTP 客户端。Core 不依赖 LLM。
2. 主人通过 `/maid chat` 打开 `MaidChatScreen`。客户端只发送目标 UUID、操作和文本；服务器重新检查存活、主人、世界和距离，客户端不提交工具调用或 API 配置。
3. `LlmService` 找到女仆与主人的会话，在服务器线程读取 `MaidTools.snapshot` 和实际可用命令。把角色风格、游戏快照、完整历史轮次和用户消息组成纯 JSON 后交给 `LlmClient`。
4. `LlmClient` 使用 Java 25 HttpClient 异步请求兼容 Chat Completions 的端点。不会在网络线程读取实体、Brain 或世界，也不会阻塞服务器等待模型。
5. `ChatProtocol` 校验响应结构、结束原因、调用数量和参数长度。`length` 等截断响应不执行动作。网络失败和 HTTP 错误变成有限的错误码，不把远端错误正文发给玩家。
6. 回调回到服务器线程，核对原会话、同一个实体实例、主人和命令版本。`MaidTools` 再次检查权限、Registry、可见性、选择 policy 和参数，生成由已校验 ID/UUID/整数/枚举构成的固定命令，调用现有 Brigadier 入口。
7. 各 Addon 的 `buildServerCommand → MaidCommandCommandApi → selectMaidCommand → onSelected` 正常运行。工具没有复制耕种、喂养、搬运或伐木业务，也没有提升玩家权限。
8. 结果带原 `tool_call_id` 回传模型。模型可继续查询或给出最终回复；工具轮数、响应体和历史都有上限。成功完整轮次才进入模型历史。

## Core 的小改动

`AiMaidEntity.getMaidCommandRevision()` 提供运行期版本号。每次选择（包括重选同一命令）、读取存档都会递增。它不持久化、不与客户端同步；只有当前实体实例上的服务端比较有意义。

LLM 记录发起时的版本。主人即使重新点了同一个工作按钮，旧模型响应也不能再切换工作。工具成功选择后更新预期版本，以便同一轮继续查询结果。保存后重新创建的同 UUID 实体还需通过实例身份校验。

LLM 的最小 Core 依赖升到 1.2.2，避免旧 1.2.1 运行时缺少 API。四个工作 Addon 保持 1.2.1，工作逻辑未改。

## 会话与工具约束

- 共享配置、连接和能力描述；历史、请求、去重记录按会话隔离。Command singleton 不存聊天状态。
- 每只女仆一次处理一条消息，全服并发和每主人冷却另行限制。不无限排队或自动重试。
- 每轮最多接受一个工作策略。动作参数按键排序后去重；只读查询每次重新读取状态，不能复用改命令前的旧快照。
- 同一调用编号跨轮重复时终止；一批最多 4 个调用，默认最多执行 4 批后再取得最终回复。
- 状态同时提供可读文字、翻译 key 和参数，不依赖客户端 Brain，也不通过中文文本反推状态。
- HTTP 不跟随重定向，响应体最多 256 KiB，请求 JSON 最多 128 Ki 字符。配置文件最多 64 KiB。输出文本最多 8192 字符，用户输入/每只女仆角色设定最多 2000 字符。
- 聊天、选择和查询均受主人、同世界与距离限制。没有任意服务器命令、文件、网页或通用 Brain 写入工具。
- 服务器动作记录与模型回复分开显示。模型文字仍可能不准确，最终以真实状态及动作记录为准。

## 参考依据

参考了 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) 1.20 分支提交 `8a3ac5e9eacf0c6d63adf8832949f3ce81484fe6` 的结构：每只女仆的 `MaidAIChatManager` 管理历史与角色提示；`LLMOpenAIClient` 处理异步 HTTP；`LLMCallback` 处理工具调用、服务器线程执行、结果回填和轮数限制；`ToolRegister`、`SwitchWorkTaskTool` 连接能力描述与实际策略选择。

本实现借鉴调用链和职责划分，使用项目已有 Fabric 26.2 与 MaidCommand API 重写，未复制 TLM 的 Forge/NeoForge 代码、素材或 UI。

协议核对：[Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[Function calling](https://developers.openai.com/api/docs/guides/function-calling)。使用当前 `tools/tool_calls` 格式，不使用旧 `functions/function_call`。

## 验收记录

本轮所有构建和以下检查通过。所有 HTTP 场景仅访问测试创建的本机模拟服务；按用户选择，尚未连接真实提供商。

| 检查 | 结果 | 日志/报告（仓库根目录下） |
| --- | --- | --- |
| Core 独立 build | 55/55 服务端 GameTest | `build/llm-core-final.log` |
| LLM 独立 build | 10/10 JUnit + 7/7 服务端 GameTest | `build/llm-final.log`、`addon/llm/build/reports/tests/test/index.html` |
| devrun 整合 build | 41/41 服务端 GameTest | `build/llm-integration-final.log` |
| LLM runClientGameTest | 完整客户端流程通过，19 次本机模型请求 | `build/llm-final.log` |

三组 GameTest 共 103 项，包含各组一个 Fabric 启动检查。整合组新增 6 项：耕种参数与中心、喂养/搬运参数隔离、伐木 single/tree 生命周期、非法参数无副作用、由工具启动耕种并实际消耗种子，以及停工原因/能力目录。

10 项协议和 HTTP 用例覆盖默认模板与旧配置、严格类型/范围、密钥不进入错误信息、非法 JSON、截断响应、重复调用编号、UTF-8、认证与 token 字段、HTTP 错误、超时/取消、响应大小和不跟随重定向。

真实客户端流程经过界面按钮与 C2S/S2C 包，检查工具结果回传、同轮重复动作只执行一次、上下文连续性、长文本换行、人工重选使旧响应失效、取消、清空、异常响应、女仆之间历史隔离、角色设定、服务重载/关闭、查询实时性、一次一项工作、工具轮数上限、超距/失去驯服状态、全局并发和跨女仆冷却。另有服务端用例验证角色设定保存/重载和非主人拒绝。

5 张截图归档于 `build/llm-acceptance/screenshots/`，聊天与长回复截图已查看；中英文翻译键一致性检查通过。临时测试环境仍有既有 Windows 性能计数器/离线账户告警，不影响上述断言与构建结果。

安装包：`build/releases/ai-maid-llm-0.1.0-mods.zip`。包含 Core 1.2.2、LLM 0.1.0 和四个可选工作 Addon 1.2.1；安装时替换旧版本的同名模组，不同时保留两版。`ai-maid-llm-0.1.0-SHA256SUMS` 记录六个 JAR 与 ZIP 的 SHA-256。已检查六个 JAR 的 metadata、入口类、JSON 和测试代码隔离，不含测试类、测试 Mixin 或本机服务配置。未提交 Git，也未向远程发布。

## 下一次真实模型验收

配置并重载后验证：普通聊天延续上下文；“待命/跟随”触发一次工具选择；四个工作使用正确参数；“为什么停工”读取真实状态；工具和材料不足时不声称已完成；连续工作请求不伪装成队列；等待期间手动切换工作后旧响应不执行。

本版提供文字聊天与工作控制闭环。语音识别/合成、流式文字、联网百科、向量记忆和跨工作任务规划未包含。当前没有真实多客户端长期压力测试；单个真实客户端、真实服务器、所有权测试与模拟服务覆盖不等于该项验证。
