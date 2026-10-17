# Addon API（Core 1.2.1）

## 依赖与加载

消费 `io.github.ozozorz.aimaid:ai-maid:1.2.1`。本仓库 `addon/lumber/settings.gradle` 演示 `includeBuild('../..')` 和 dependency substitution。客户端 API 在 Core 的完整 JAR 中；Composite Build 的普通 classes 变体只有 main，客户端扩展依赖应显式选择 JAR，见 Lumber Addon 的 `clientImplementation`。

模组 metadata 中声明 `ai-maid >=1.2.1`，并按使用的 Minecraft/Fabric 版本限制依赖。Core 的 Maven publishing 尚未配置远程仓库，第三方工程需要自行提供发布位置或使用本地组合构建。

通过 `ai-maid-addon` entrypoint 实现 `AiMaidAddonEntrypoint`，在 `register(AiMaidAddonRegistrar)` 贡献已注册的 SensorType。此时不能假设自己的普通 `ModInitializer` 已执行，应主动初始化需要的 Memory/Sensor 注册类。贡献完成后冻结，同一个 SensorType 去重；迟到注册会明确报错。

Command 必须注册到 `ModBuiltInRegistries.MAID_COMMAND`，并在第一只女仆创建 Brain 之前完成。每个 Command 的 `createActivities(maid)` 为这只女仆创建 Behavior；不能把有状态的 Behavior 放在 singleton 字段里。重复 Activity contribution 会报错。Sensor.requires 和 BehaviorControl.getRequiredMemories 负责声明 MemorySlot；Activity conditions 不能独自承担注册。

## 数据归属

| 数据 | 保存位置 |
| --- | --- |
| 注册 ID、显示名称、工作策略 | 共享 MaidCommand 定义 |
| 主人、装备 | 原版 TamableAnimal / EntityEquipment |
| 35 格携带物品 | 每只实体的 MaidInventory |
| 可持久的 Addon 参数 | 每只实体的 Fabric Attachment |
| 当前目标、任务队列、失败原因、临时进度 | 每只 Brain 的 Memory 或其 Behavior/Sensor 实例 |
| 客户端菜单 | 标准 Slot/DataSlot 和状态消息的镜像 |

合法但未知的 Command ID 会保留在存档中，有效运行策略暂时回退 FOLLOW。恢复该 Addon 后可重新解析原 ID。不要用 fallback 覆盖原始 ID，也不要把所有工作目标塞进 Core 的通用 WORK_TARGET。

## 命令与生命周期

`AiMaidEntity.selectMaidCommand(command)` 是当前选择入口；`selecetMaidCommand` 保留为 deprecated 兼容转发。新代码使用正确拼写。它要求所属服务器线程、存活实体和已注册命令。

| 情况 | 调用与语义 |
| --- | --- |
| 选择其他命令 / 重选当前命令 | 停止旧 Behavior、导航和物品使用，清理动作；旧命令 onDeselected；更新实体 ID 和 MAID_COMMAND；新命令 onSelected；更新 Activity |
| 临时 Activity 切换，例如工作自卫 | 不调用 onDeselected/onSelected；由 Addon 保留和恢复自己的任务 |
| 保存后重载 | 恢复命令 ID，不重放 onSelected；运行任务是否恢复由 Addon 明确设计 |
| 死亡 | 停止 Behavior、清动作，再按死亡策略处理物品；不是一次命令重选 |
| 卸载 / 跨维度移除 | 停止 Behavior、清动作，不掉落携带物，也不调用 onDeselected |

Vanilla 切 Activity 不等于立即停止全部正在运行的 Behavior。需要同 tick 中断时，应由 Core 的 MaidCombatSensor 有顺序地停止旧行为、通知命令、更新 Activity；不要依靠每个行为自行碰巧退出。

`onSelected/onDeselected` 只在服务器执行。Behavior.stop 必须能在正常结束、被打断、死亡或卸载时安全清理。临时进度不要默认持久化；当前伐木重载后需要重选才能开始新任务。

## 授权与线程

实体 API 为服务器程序提供机制，不自动授予玩家操作权限。玩家入口必须先验证主人、存活等条件；GUI 还验证同维度和距离。Brigadier 使用 `resolveSelectableTarget` / `executeSelection`；`finishSelection` 也重新检查主人和 Command policy，拒绝时返回 0，Addon 应避免在失败后继续修改参数。

`canPlayerSelect` 与 `getStatus` 都必须便宜、无副作用。菜单会反复查询；不能在其中选目标、换工具、消耗物品或开始任务。

选择命令、MaidItemTransfer、MaidEquipmentHelper、MaidFeeding、启动/停止动作 API 均要求所属服务器线程。客户端只读取同步状态。异步计算完成后必须把实体修改排回服务器线程。

## 装备

`MaidEquipmentRules` 是 GUI 与 AI 的共享查询：支持 MAINHAND/OFFHAND/HEAD/CHEST/LEGS/FEET，检查 canUseSlot 和可装备性；护甲一件，手部不超过物品和容器堆叠上限。

六槽都尊重 PREVENT_ARMOR_CHANGE。AI 没有创造模式例外；GUI 中创造模式主人可以取下。死亡走独立规则：绑定物品正常掉落，带 PREVENT_EQUIPMENT_DROP 的物品消失。

使用 `MaidItemTransfer` 移动、交换或 `swapHands`；使用 `MaidEquipmentHelper.ensureEquipment` 保证最终装备满足 Predicate。满包仍支持一对一交换。可堆叠护甲换装时，先检查旧装备是否能存回，失败则不改动任何来源；不会覆盖或丢弃余量。

这些方法不提供“强制忽略政策”的业务入口。继承的 Vanilla `setItemSlot` 和底层容器仍是低级写入机制，存档、生成、同步和死亡需要使用它们；Addon 的日常换装应走上述公共 API。不能宣称所有第三方直接写入都会被自动拦截。

## 状态与 GUI

实现 `MaidCommand.getStatus(AiMaidEntity)`，返回可翻译 Component；没有额外提示返回 `Component.empty()`。需要记录一次失败时，用 Addon 自己的 Memory 保存，并在重选或相应任务生命周期中清理。示例见 LumberCommand 和 LUMBER_RESULT。

Core 从服务器派生消息；背包完全装满时追加提示。只有当前有效菜单的查看者收到 `MaidStatusPayload`，内容变化才发送。客户端按 containerId + entityId 校验并更新 Menu 镜像，缺少客户端世界实体仍能显示。消息不是聊天广播，也不能反向控制实体。

命令/Activity 当前继续使用 Registry raw ID 的 DataSlot。26.2 的 ContainerSetData 索引和值都是有符号 short，Core 对注册数量和菜单数据数量做边界检查；按钮包的 buttonId 是 VarInt。可见性和排序必须在两端得到一致的列表，实例选择权限用 canPlayerSelect 表达。

GUI 为 318×236；存储起点 (182,34)，装备 (106,70)，玩家 (146,154)，总计 77 槽。模型和菜单装备镜像仍是独立同步通道，不要把镜像反写客户端实体。

## 机制边界

Core 提供破坏速度、距离/视线检查、真实工具破坏与掉落、远程发射和动画等机制。PvE 选敌与自卫中断属于 Core；工作队列、范围、任务暂停/恢复和失败业务规则归 Addon。当前方块破坏没有 fake player，不能据此宣称覆盖所有领地或第三方玩家事件。

农耕、补种、喂养和搬运的策略位于对应 Addon，不进入 Core。自动进食、工资和高级皮肤仍不在本版范围。

## Core 战斗与工作自卫（1.2）

`MaidCommand.getCombatMode()` 返回 NONE / GUARD / HUNT / SELF_DEFENSE。默认 NONE；FOLLOW 为 GUARD，ATTACK 为 HUNT，四个工作命令为 SELF_DEFENSE。工作模式只响应自身的新伤害记录，不因主人在远处战斗而离开工作。

Core 安装一次 `ModActivities.FIGHT` 和独立于 Addon 的 Combat Sensor。支持自卫的工作命令把 FIGHT 放在候选列表首位，并为工作 Activity 声明 ATTACK_TARGET 不存在的条件。不要重复贡献同一个 FIGHT Activity。

中断顺序是 stopAll → `onCombatStarted(maid, now)` → 建立 ATTACK_TARGET → 切换 Activity。战斗结束且仍使用原工作命令时，调用 `onCombatEnded(maid, now)`。普通循环工作可在 Behavior.stop 中清理目标，结束后重新扫描；有限伐木任务通过回调暂停/恢复计时并保留剩余队列。显式重选仍走 onDeselected/onSelected。

`canUseInventoryItem(maid, slot)` 是便宜的纯查询，供 Core 自动换装和弹药查找使用。搬运用它保留当前货物格；它不禁止 GUI 手动移动、不替代所有权检查，也不拦截任意底层容器写入。

`MaidWorkNavigation` 提供有加载/边界检查的邻近站位规划、距离/视线判断与 WALK_TARGET 写入。它不选作物、不选动物、不选箱子。`AiMaidEntity` 实现原版 ContainerUser，Addon 在 startOpen/stopOpen 两侧维护临时 openedContainerPos，并在 stop 中可靠关闭。

已知旧命令可在启动注册阶段用 `MaidCommands.registerLegacyAlias(oldId, newId)` 声明迁移；只在目标命令存在时转换保存 ID。不建立通用未知命令重写规则。

## 工作移动与等待（1.2.1）

- `MaidWorkNavigation.chooseWorkPosition(maid, targets, range)`：调用方提供有限、有序的方块列表；一次多目标寻路返回目标与脚部站位。规划检查加载、碰撞、距离和视线；到达后仍须重新验证真实世界。交互范围由业务传入，不能把“路径能到”当成“操作一定成功”。
- `chooseStand` 有方块、实体目标重载；旧的双参数方块入口仍保留，默认范围 3。当前规划针对站在地面的短距离交互，不负责开门、攀爬或强制加载区块。
- `walk` 写入移动意图，由 `MoveToTargetSink` 执行；同一意图不每 tick 重建。目标或速度变更时停止旧路径，让 sink 接管新意图。
- 到位调用 `haltMovement`，再用 `lookAt` 保持注视；退出整个任务调用 `stop`。`lookAt` 的 Memory 有 10 tick 到期时间，业务在交互期间刷新。
- 每个 Behavior 创建自己的 `Progress`，新目标 `reset`，移动阶段用 `stalled` 检查是否长期没有实际位移。它不计算业务超时，也不能保证检测一切绕圈路径。
- `MaidWorkIdle` 接受本只 Behavior 的等待条件、工作中心和半径；工作把它放在更低优先级。它只在允许的等待阶段观察和短距离移动，限制整个空闲路径在工作区域内。停止时只清理仍属于自己的 Memory，不删除随后新工作写入的目标。
- `MaidInventory.getChangeVersion()` 是服务端运行期修改计数，用于提前结束缺料等待；不持久化、不参与 Slot 网络同步。通过容器 API 修改会增加计数；直接改变取出的可变 ItemStack 后，调用者仍须 `setChanged()`。

等料、无目标、箱子占用、路径不可达由 Addon 区分；Core 不替业务设置统一失败冷却。不在 Command singleton 保存队列、随机计时、目标或 `MaidWorkIdle` 实例。循环工作被战斗中断后丢弃短期候选，再从真实世界选取；伐木的有限队列继续使用自己的恢复契约。
