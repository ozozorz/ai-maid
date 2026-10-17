# 1.2.1 验收记录

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
