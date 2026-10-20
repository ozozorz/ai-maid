# 验收记录

## 动作接口与自主任务（2026-10-20）

版本：Core / Farming / Lumber 1.3.0，LLM 0.3.0，Feeding / Hauling 1.2.1。Windows、Java 25、Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.158.0+26.2。

先完成 [架构设计](AUTONOMOUS_TASKS.md)，再实现 Core 动作/观察/目标执行器、Farming/Lumber 动作提供者与 LLM 异步反馈循环。世界副作用由服务器 Brain 执行，模型等待实际结果后再决策。

| 验证入口 | 结果 | 日志（相对仓库根目录） |
| --- | --- | --- |
| Farming `build :ai-maid:build` | Core 60/60、Farming 29/29 | `addon/farming/build/goal-farming-core.log` |
| Lumber `build` | 18/18 | `addon/lumber/build/goal-lumber.log` |
| LLM `build runClientGameTest` | 8/8服务端、11/11单元测试、1组真实客户端流程 | `addon/llm/build/goal-llm.log` |
| devrun `build runClientGameTest` | 76/76服务端、7组真实客户端流程 | `dev/devrun/build/goal-integration-final.log` |

五套服务端共191项，包含各套件的Fabric启动检查；独立LLM的11项为协议、配置与HTTP单元测试。客户端用真实临时世界、真实游戏库存与网络包，只有模型响应由本机HTTP模拟服务提供。Feeding/Hauling本次没有改实现，已随整合场景回归，没有把其历史独立套件重复列入本次数量。

新增15项整合服务端测试覆盖：

- 胡萝卜收获、补种、库存净增加与精确存放；任意物品的部分存放结果；实际移动到达。
- 背包工具槽变化时拒绝装备；装备斧头、渐进砍伐、工具耐久与实际拾取分别确认；树被替换和满背包时不误破坏。
- 错误目标类型拒绝；目标等待决策、没有证据不能宣称完成；请求去重、动作/失败/时间预算；手动模式切换和卸载取消。
- 实际遇袭暂停目标与当前动作，暂停期间无交付、不扣游戏计时，战斗结束继续同一个动作。

新增自主目标客户端场景验证：

1. 背包已有1个小麦，模型观察农田和两只箱子；收获2个后才收到成功结果。
2. 重复同一动作请求取得原记录，既不重复收割，也不额外计入动作预算。
3. 第一只箱子只存入1个，模型收到失败及实际数量，按剩余2个继续存入主人明确授权的备用箱。
4. 主人离开初始聊天距离，已授权的目标仍能执行并实际步行返回；完成记录与库存、箱内数量一致。
5. 取消、手动切换、服务重载后，迟到模型回复不能装备工具或恢复任务。
6. 只查询不执行的循环达到预算后停止；HTTP错误结束目标；无动作预算设为1仍允许观察后执行。

首轮发现砍树掉落物尚未落地时误报不可达，修正后真实破坏、拾取和耐久断言通过。最终截图另检查整体目标终态，避免将嵌套子动作的成功误认成整个目标成功。

整体目标终态的客户端复查通过，日志为 `dev/devrun/build/goal-ui-final.log`。

证据归档：`build/goal-acceptance/logs/`、`build/goal-acceptance/junit/`、`build/goal-acceptance/screenshots/`。原始日志存在Windows性能计数器、离线测试账户和Gradle弃用告警；不影响上述断言结果。

安装包：[ai-maid-autonomous-0.3.0-mods.zip](../build/releases/ai-maid-autonomous-0.3.0-mods.zip)，包含6个生产JAR；[SHA-256](../build/releases/ai-maid-autonomous-0.3.0-SHA256SUMS)包含各JAR与ZIP校验值。已检查版本、Core依赖下限、入口类、JSON资源、核心新类与测试代码隔离，不含测试Mixin、测试夹具或真实服务配置。Minecraft、Fabric Loader、Fabric API需另行安装。没有向远程发布或提交Git。

README、LLM配置与调用链、Addon API、使用/测试场景、伐木和工作恢复文档已同步；旧验收数据保留在下方历史区，已过时的“只能选择模式”“取消仅影响HTTP”等当前使用描述已更新。

验证边界：没有调用真实提供商模型，因此不把模拟流程当作真实模型成功率、成本或延迟结果；没有多玩家长期压力测试。自然语言目标的语义完成由模型判断，实际动作效果由服务器记录。任务不跨卸载或重启恢复。复杂地形和第三方保护模组仍需实服验证。

## 历史：1.2.1工作调度里程碑

以下为2026-10-17保留的历史记录，不代表当前版本或本轮测试数量。2026-10-19的场地机制见 [测试场地](TEST_SCENE.md)。

日期：2026-10-17。环境：Windows、Java 25、Minecraft 26.2、Fabric Loader 0.19.3、Fabric API 0.158.0+26.2。验收对象是当前工作区的 Core 与四个正式 Addon，未提交或向远程发布。

## 服务端与构建

| 入口 | 通过数 | 日志（相对仓库根目录） |
| --- | ---: | --- |
| Core 独立 build | 55 | `build/scheduling-core-final.log` |
| Lumber 独立 build | 18 | `build/scheduling-lumber-final.log` |
| Farming 独立 build | 16 | `build/scheduling-farming-final.log` |
| Feeding 独立 build | 12 | `build/scheduling-feeding-final.log` |
| Hauling 独立 build | 20 | `build/scheduling-hauling-final.log` |
| devrun 整合 build | 35 | `build/scheduling-integration-final.log` |
| Core + 两个 Sensor 注册夹具 | 55 | `build/scheduling-core-fixtures-final.log` |

六组共 **156 项通过**，含 150 个项目用例和每组一个 Fabric 启动检查。Core 的另一注册配置重复验证 55 项，不重复计入总数。五个生产模块及整合工程的编译、资源处理和构建均通过。

相对 1.2.0 新增 15 项场景回归：

- 耕种 7 项：一片近邻作物连续完成，交互前仍注视未修改的作物，长期等待后补料唤醒，区域内观察／缓步，准备时目标消失，持续移动超过 200 tick 后完成，以及新工作接管正在执行的空闲移动。
- 喂养 5 项：最近动物被墙遮挡，可见动物被封闭围栏阻挡，近邻动物连续喂食，准备时动物移走后重新接近，以及新出现的墙阻止远程扣料。
- 搬运 3 项：来源箱持续占用 450 tick 后关闭并恢复交付，跳过忙碌来源使用另一箱子，以及 60 tick 交互期间保持注视、停止移动。

旧用例继续覆盖四作物与真实种子、满包、动物年龄和冷却、双女仆竞争、世界规则、锁箱／阻挡、部分存入、components、货物替换／重载／死亡、武器及箭的保留，以及工作自卫和恢复。严格触及的有限整树伐木保持原有回归。

## 真实客户端

**7 组流程全部通过**：Core 3 组、Lumber 1 组、整合 3 组。使用真实客户端、集成服务器与临时世界。

| 入口 | 日志 | 截图归档 |
| --- | --- | --- |
| Core runClientGameTest | `build/scheduling-core-client.log` | `build/scheduling-acceptance/core/`，7 张 |
| Lumber runClientGameTest | `build/scheduling-lumber-client.log` | `build/scheduling-acceptance/lumber/`，3 张 |
| devrun runClientGameTest | `build/scheduling-integration-client.log` | `build/scheduling-acceptance/integration/`，28 张 |

共 38 张截图。正式 Addon 流程验证原版菜单按钮包、连续三株收获、客户端 Slot 同步、喂养、无任务等待、忙碌箱子的状态消息、开箱交互、在途货物和最终交付。代表性的世界与菜单截图已查看。动画流程包含 294 个烘焙模型姿势组合检查；原有保存后重进、战斗、裂纹清理和战后恢复流程一起回归。

测试专用的 `dev/testsupport` 关闭兼容 Mixin 仍只进入 GameTest 客户端，用于处理 26.2 IntegratedServer 关闭同步与 Fabric 测试 Phaser 的互等；保存后重进流程验证关闭结果。发布 JAR 不包含该 Mixin。

## 制品与文档

- 安装包：`build/releases/ai-maid-1.2.1-mods.zip`，包含 Core、Lumber、Farming、Feeding、Hauling 五个生产 JAR。
- SHA-256：`build/releases/ai-maid-1.2.1-SHA256SUMS`，列出五个 JAR 和 ZIP 的校验值。
- 五个 JAR 均检查版本、JSON 资源、必要类和测试代码隔离；不含 GameTest、测试 Mixin、隐藏巡逻夹具或旧 TestAddon 实现。
- README、Addon 使用说明、API、战斗、伐木、动画和工作恢复文档更新到正式模块入口；最新验收统一引用本文。Core 1.1 记录明确保留为历史资料。
- [工作调度说明](WORK_SCHEDULING.md)记录确认的问题、运行阶段、TLM 参考提交与边界。

ZIP 不含 Minecraft、Fabric Loader 或 Fabric API，也未上传发布平台。两端安装相同版本 Core 和所选 Addon；只需要某种工作时，可只安装对应 Addon。

自动化场景验证真实 Brain、导航、世界和物品变更；双女仆争用测试不等于多个真实联网客户端的长期压力测试。复杂地形、第三方保护模组、特殊机器和模组动物仍需分别适配验收。
