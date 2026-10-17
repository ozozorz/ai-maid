# Ai Maid LLM

可选 LLM Addon 的教学骨架，当前版本 `0.1.0-SNAPSHOT`。依赖 Core `1.2.1`、Minecraft `26.2` 和 Java `25`。

当前仅有独立构建、Fabric 模组入口和加载日志。聊天、模型请求、配置文件和行为工具将逐步实现；启动不会访问模型服务。

## 构建与运行

本目录是独立 Gradle Build，可以直接在 IDEA 中打开。通过 Maven coordinate 消费 Core，本地 `includeBuild('../..')` 将其替换为 Core 源码。

在本目录执行：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

也可以从仓库根目录执行：

```powershell
.\gradlew.bat -p addon/llm build
.\gradlew.bat -p addon/llm runClient       # Core + LLM 骨架
.\gradlew.bat -p dev/devrun runClient     # Core + 四个工作 Addon + LLM 骨架
```

日志中应出现 `Ai Maid LLM initialized (scaffold)`。独立客户端的日志位于本目录 `run/logs/latest.log`，构建产物位于 `build/libs`。当前 `build` 验证编译与打包；还没有 LLM 功能测试。

## 骨架验收（2026-10-17）

- 独立 `build` 通过，JAR 中的入口类、版本替换、Core 依赖与许可证已核对。
- 集成 GameTest 的真实服务端成功加载 `ai-maid-llm` 并输出初始化日志。
- 集成测试首次 34/35 通过，既有 `lumberFailureExplainsAndReselectClearsReason` 用例未在时限内完成；未改代码复跑后 35/35 通过。这次没有修复该用例的运行波动。
- 尚未做客户端人工验收或实际模型联调。

## 先理解入口

- `settings.gradle`：定义这个独立 Build，并配置 Core 的本地源码替换。
- `build.gradle`：配置编译、依赖、开发启动和打包。
- `gradle.properties`：区分本 Addon 版本与依赖的 Core 版本。
- `fabric.mod.json`：让 Fabric Loader 识别模组、检查依赖并找到入口类。
- `LlmAddon`：公共初始化入口。Fabric Loader 调用它的 `onInitialize()`；它属于模组级初始化，不会为每只女仆执行一次。

## 后续教学顺序

1. 配置对象与配置读取：区分请求地址、模型名、密钥以及配置的生命周期。
2. 一次异步文字请求：构造请求、读取响应、处理超时和错误，并将结果交回服务器线程。
3. 女仆聊天会话：绑定真实玩家与女仆，隔离每只女仆的历史和请求状态。
4. 只读游戏上下文与工具协议：让模型读取真实状态，解析受约束的工具参数。
5. 自然语言工作控制：复用服务端授权和命令选择，适配各工作 Addon 的参数，并处理重复调用与过期响应。
6. 聊天界面与实际模型验收：逐步完善交互，验证请求结果和女仆真实行为一致。

每一步完成并验收后再继续。会话数据属于每只女仆，不能放进共享的 `MaidCommand` 对象；模型连接与游戏工作执行保持各自职责。
