# Ai Maid LLM 0.3.0

可选的聊天、自然语言工作控制和自主任务 Addon，依赖 **Core 1.3.0+、Minecraft 26.2、Java 25**。客户端与服务器都安装；模型请求由服务器发送。Farming / Lumber 1.3.0 提供收获与伐木动作，未安装时不会出现在模型能力目录。

## 配置与启动

首次进入世界或启动服务器，在该运行目录的 `config/ai-maid-llm.json` 创建关闭模板。修改后由管理员执行 `/maid llm reload`；普通主人聊天不需要 OP。

```json
{
  "enabled": true,
  "apiUrl": "http://127.0.0.1:8080/v1/chat/completions",
  "model": "your-model-id",
  "apiKey": "",
  "systemPrompt": "你是一位友善的 Minecraft 女仆。使用主人的语言简洁回答。",
  "tokenField": "max_tokens",
  "timeoutSeconds": 45,
  "maxTokens": 1024,
  "historyTurns": 8,
  "cooldownSeconds": 2,
  "maxConcurrent": 4,
  "maxToolRounds": 4,
  "chatDistance": 32,
  "goalMaxActions": 16,
  "goalMaxFailures": 3,
  "goalTimeoutSeconds": 600,
  "goalRadius": 64
}
```

地址是本机服务占位，不会自动安装模型。填写完整 Chat Completions HTTP 端点；服务需要支持 `messages/tools/tool_calls/tool_call_id` 和动作工具的嵌套 JSON Schema。按提供商要求选择 `max_tokens` 或 `max_completion_tokens`。空密钥用于本地免认证服务；非空密钥使用 Bearer 认证，远程服务宜使用 HTTPS。

旧配置仍然有效，新增字段使用默认值；不会覆盖已有文件。非法重载保留原服务。配置只在服务器保存，客户端不会获得密钥。发送给配置服务的数据包括主人消息、有限会话历史、角色设定、女仆状态与背包、观察到的附近目标及动作记录，不上传整个世界。

| 字段 | 范围、默认值与含义 |
| --- | --- |
| timeoutSeconds | 1..120，默认45；每次 HTTP 请求与响应体读取的超时 |
| maxTokens | 64..8192，默认1024；每次输出上限 |
| historyTurns | 0..20，默认8；完整普通聊天轮数，另限24000字符 |
| cooldownSeconds | 0..60，默认2；主人跨女仆共用冷却 |
| maxConcurrent | 1..16，默认4；同时活动的普通请求或自主目标数 |
| maxToolRounds | 1..8，默认4；普通聊天工具批次上限；自主任务每次动作之间没有产生新动作的决策批次预算 |
| chatDistance | 4..64，默认32；开始聊天或下达目标的距离 |
| goalMaxActions | 1..64，默认16；单个目标最多接受的动作数，成功和失败均计入 |
| goalMaxFailures | 1..8，默认3；达到此实际动作失败次数后停止 |
| goalTimeoutSeconds | 5..3600，默认600；以20 tick/秒计算的目标运行预算，战斗暂停不扣除 |
| goalRadius | 16..128，默认64；目标起点周围的执行范围 |
| systemPrompt | 最多4000字符；全局角色风格 |

另有固定保护：单动作2400个活动tick；连续等待决策2400tick；一次观察半径最多16格；每次模型消息最多4个工具调用；整个目标的决策请求最多 `min(128, (goalMaxActions+1)×(maxToolRounds+1))` 次。实际动作完成前不会再次调用模型。

## 使用

```text
/maid chat
/maid chat 收集8个小麦，放到坐标x y z的箱子，然后回来
/maid uuid <uuid> chat
/maid llm cancel
/maid llm clear
/maid llm persona 你是一位安静耐心的园丁
/maid llm persona reset
/maid llm reload
```

`chat/cancel/clear/persona` 支持默认最近女仆、nearest/name/uuid/selector 等既有目标形式。非 OP 可使用默认、名字和 UUID；选择器遵循原版权限。界面支持发送、Enter、滚轮、取消、清空。关闭聊天窗口不会取消任务，再次打开可看近期记录。

三种工作方式并存：

- 持续模式：“照料附近半径6格的农田”“跟我来”“按分类搬运模式工作”。模型选择一个已有策略，由 Java 持续运行。
- 固定计划：一次提交1..8个有限动作，依次执行，第一次失败就停止，不自动询问模型改计划。
- 自主目标：“收集8个小麦，放进我指定的箱子，然后回来”。模型先观察，逐个执行，收到真实结果后再决定下一步。若希望箱满时改用备用箱，必须说明允许的备用目标。模型对这类授权的理解仍需人工核对。

例如已有3个小麦时，收集动作补足到8个；存放动作才向箱子转移8个。若只装入2个，动作会返回 `delivered=2, remaining=6`。模型可继续处理剩余6个，或说明无法继续。开始任务的确认消息不表示已完成。

## 工具与动作

| 工具 | 用途 |
| --- | --- |
| query_maid / list_commands / select_command | 查询真实状态与工作能力，或选择已有工作模式 |
| list_actions | 枚举当前实际安装的有限动作、参数及语义 |
| observe_nearby | 观察女仆附近已加载的箱子、成熟作物区域与自然树，提供位置、数量、短期目标ID和寻路估计 |
| observe_chest / observe_position | 观察主人准星6格内目标，或主人明确提供的坐标；目标距女仆须在32格内 |
| start_goal | 接受主人本轮原话作为目标，启动独立反馈循环；只能从普通聊天启动 |
| execute_action | 自主目标内提交一个动作，单独占一条模型消息；真正执行结束后才返回结果 |
| finish_goal | 自主目标内报告完成或阻塞，保留动作证据与模型结论 |
| submit_plan / cancel_task | 提交固定计划或取消当前有限任务 |
| observe_wheat_delivery / start_wheat_delivery | 安装 Farming 时保留的旧小麦配送兼容入口 |

| 动作ID | 参数与完成条件 |
| --- | --- |
| ai-maid:move_to | target_id；实际走到目标附近 |
| ai-maid:equip_tool | slot、item；核对物品后换到主手，旧装备换回背包 |
| ai-maid:deposit_items | target_id、item、count；实际向指定箱子转移数量，count=1..64 |
| ai-maid:return_to_owner | 无参数；步行返回主人当前位置 |
| ai-maid-farming:harvest_crops | target_id（区域）、item、count；背包达到指定作物数量，收获并补种差额 |
| ai-maid-lumber:chop_tree | target_id（树）；需斧头，分别记录破坏、跳过和实际拾取 |
| ai-maid-farming:collect_wheat / ai-maid:deposit_wheat | 保留旧的小麦动作 |

收获支持 `minecraft:wheat/carrot/potato/beetroot`，只处理成熟且已有的农田，不开垦、不等待生长。砍树只处理观察快照中的自然树原木，不搭建、不远程连锁破坏；树判断是启发式。存放支持普通、陷阱和铜箱的可访问容器，包括可用的双箱；不等于通用容器取货或合成能力。喂养和分类搬运仍作为持续模式提供，没有新增有限动作。

## 进度、取消与生命周期

任务进度独立于模型文字，包含当前动作、数量、暂停/失败原因。任务与模型等待期间界面保持忙碌，取消按钮始终可用。

- 取消按钮或 `llm cancel` 会停止待处理请求、固定计划或自主目标；已收集/已存放物品保留。取消正在运行的有限任务不要求主人仍在聊天距离内。
- 持续的种田、搬运等旧工作模式须切换为“待命”才能停工。
- 手动切换或重选工作、死亡、卸载、换主人、主人离线/跨维度、LLM服务重载都会终止自主目标，迟到回复不能启动新动作。
- 自卫暂停当前动作并保留队列及数量，战后重新检查再继续。HTTP超时仍独立生效。
- 清空聊天/修改角色设定会终止当前自主目标并清理会话；不回滚物品，也不主动停止旧的固定计划或持续工作模式。
- 会话、任务、观察ID与去重记录均驻留内存；服务器重启或实体卸载后不恢复、不重放自主任务。持久角色设定保留。

后续自主决策绑定同一主人、实体实例、目标实例和命令版本，允许主人离开最初聊天距离；女仆仍受起点范围、加载区块和交互距离约束。模型判断“自然语言目标是否完成”，服务器证明“每个动作实际做了什么”，两者分别记录。

## 构建与验证

在仓库根目录执行：

```powershell
.\gradlew.bat -p addon/llm build
.\gradlew.bat -p addon/llm runClientGameTest
.\gradlew.bat -p dev/devrun runGameTest runClientGameTest
.\gradlew.bat -p dev/devrun runClient
```

各 Build 的日常 `run/config` 分开。自动化测试使用临时世界与随机本机端口，不读取真实密钥或调用真实模型。自动化验证了协议及游戏执行闭环，真实模型的任务理解、兼容性、延迟和成本仍需配置后实测。

开发架构见 [自主任务](../../docs/AUTONOMOUS_TASKS.md)、[LLM 调用链](../../docs/LLM.md)；实测日志与结果见 [验收记录](../../docs/ACCEPTANCE.md)。
