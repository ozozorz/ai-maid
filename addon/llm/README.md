# Ai Maid LLM 0.1.0

可选的文字聊天与自然语言命令 Addon，依赖 **Core 1.2.2+、Minecraft 26.2、Java 25**。客户端和服务器都安装；模型请求由服务器发送。Core 可以独立使用，四个工作 Addon 也按需安装。

## 配置与启动

第一次启动服务器或进入单人世界时，在该运行目录的 `config/ai-maid-llm.json` 创建关闭的模板。修改后可用 `/maid llm reload` 重载。重载需要管理员权限，普通主人聊天不需要 OP。

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
  "chatDistance": 32
}
```

地址示例只是本机服务占位，不会自动安装模型。`apiUrl` 填完整的 **Chat Completions HTTP 端点**，不是网站首页或仅 `/v1`；服务必须支持 `messages`、`tools`、`tool_calls`、`tool_call_id`。若提供商需要 `max_completion_tokens`，修改 `tokenField`。本地免认证服务允许空密钥，其他服务使用 Bearer 认证。远程服务建议使用 HTTPS。

你之前写的 `enabled/apiUrl/model/apiKey` 四字段配置仍然有效，新字段取默认值；不会覆盖已有文件。关闭时允许连接信息为空。非法配置重载失败会保留现有服务；启动时非法配置则保持关闭。日志不输出密钥、原始 HTTP 错误正文或聊天内容。

| 字段 | 范围与含义 |
| --- | --- |
| timeoutSeconds | 1..120，每次请求及响应体读取的最长等待秒数 |
| maxTokens | 64..8192，每次模型输出上限；计费以服务提供商为准 |
| historyTurns | 0..20，内存中的完整对话轮数，另有总字符上限 |
| cooldownSeconds | 0..60，同一个主人跨所有女仆共用的发送冷却 |
| maxConcurrent | 1..16，全服务器同时处理的对话数 |
| maxToolRounds | 1..8，每轮对话最多执行的工具批次数；每批最多 4 个调用 |
| chatDistance | 4..64，同世界允许聊天的最远距离 |
| systemPrompt | 最多 4000 字符的全局角色风格 |

配置只保存在服务器。本功能会将用户消息、有限对话历史、女仆名称、位置、工作状态、装备和背包物品摘要发送到你配置的服务。不会发送密钥给客户端，也不会上传整个世界或其他玩家聊天。

## 使用

需要测试场景时，管理员在主世界执行 `/maid test-scene`。Core 会生成带待命女仆、工具材料、树木、农田、成年牛和分类箱的空中平台，详见 [测试场地](../../docs/TEST_SCENE.md)。完整工作测试请运行 `dev/devrun`；场地命令不会配置或请求模型服务。

主人在女仆附近运行：

```text
/maid chat                         打开聊天界面
/maid chat 跟我来，保护我           直接发一条消息
/maid nearest chat
/maid name "Rem" chat
/maid uuid <uuid> chat
/maid @e[...] chat
/maid llm cancel                   取消模型请求
/maid llm clear                    取消并清空该女仆的会话
/maid llm persona 你是一位安静耐心的园丁
/maid llm persona reset
/maid llm reload                   管理员重载配置
```

`llm cancel/clear/persona` 也支持 `nearest/name/uuid/selector` 目标形式。选择器本身遵循原版权限；非 OP 可以使用默认、名字和 UUID 目标。聊天界面支持发送、Enter、滚轮历史、取消和清空。关闭界面后请求继续，完成时在普通聊天中显示回复；再次打开可读取近期会话。

可以尝试：“跟我来”“在这里待命”“附近有哪些工作能做”“你为什么停工”“查看你的工具和背包”。安装对应工作 Addon 后，可以说：“在你现在的位置照料半径 6 格的耕地”“喂养附近的成年家畜”“按铜傀儡的方式搬运物品”“砍一棵能走近的树”“只砍一块原木”。

| 工具 | 行为 |
| --- | --- |
| query_maid | 读取真实服务端命令、Activity、停工原因、生命、位置、六槽装备、35 格背包 |
| list_commands | 枚举实际注册且可见的命令，返回选择政策和能力说明 |
| select_command | 选择 Registry ID；支持耕种/喂养半径 2..16、搬运半径 2..32、伐木 tree/single |

一次用户消息最多接受一种工作策略，不实现工作队列。树木高处够不到仍会跳过；耕种不会自动开垦；搬运仍从铜箱取货。未安装的命令不会出现在工具列表。其他 Addon 的可见命令可进行无参数选择，模型只获知其名称，不假定未知业务能力。

服务器会显示独立的“命令已接受”记录。它表示策略已选中，**不代表已经砍完、种完或搬完**。重复选择相同命令默认保持任务，明确要求重新开始、重设中心或改变参数时才重选。模型重复发出同一动作不会在同一轮重复执行。

取消模型请求不会回滚已执行的动作；停止正在执行的工作请手动选择“待命”，也可发“停止工作，在这里待命”。模型等待期间手动切换/重选工作会使旧请求失效。女仆死亡、卸载、离开范围、失去主人、玩家断开及服务器关闭也会取消待处理请求。

## 数据生命周期

`LlmConfigLoader` 在服务器启动或管理员重载时读取 JSON，创建不可变 `LlmConfig`。每个服务器持有一个 `LlmService` 和 HTTP 客户端；服务按 **女仆 UUID + 主人 UUID** 管理会话。

会话仅驻留内存：最多 128 个、闲置 30 分钟清理，断开连接、服务器关闭或重载后清空。模型历史按完整轮次裁剪到配置数量与 24000 字符；界面保留最多 40 条、24000 字符。失败的半截工具协议不写入历史，已执行动作保留在界面记录中。角色设定使用女仆自己的 Fabric Attachment 持久化，清空会话不删除角色设定。

## 构建与验收

在仓库根目录执行：

```powershell
.\gradlew.bat -p addon/llm build                # 编译、协议/模拟 HTTP、独立服务端 GameTest
.\gradlew.bat -p addon/llm runClientGameTest    # 真客户端 + 本机模拟模型
.\gradlew.bat -p addon/llm runClient            # Core + LLM
.\gradlew.bat -p dev/devrun build              # 四个工作 Addon 的工具适配及整合回归
.\gradlew.bat -p dev/devrun runClient
```

本目录可单独在 IDEA 打开，运行自己的 Gradle Wrapper。日常单独运行和 devrun 使用不同的 `run/config`，需在实际使用的运行目录里配置服务。

自动化测试使用随机本机端口和临时世界，不读取真实密钥、不请求真实模型、不修改日常存档配置。架构、参考来源与验收见 [LLM 接入说明](../../docs/LLM.md)。本轮完成模拟服务验收；模型理解能力、提供商兼容性、费用和网络延迟留待真实服务配置后验证。
