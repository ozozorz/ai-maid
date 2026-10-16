# Addon API（Core 1.1.0）

## 依赖与加载

消费 `io.github.ozozorz.aimaid:ai-maid:1.1.0`。本仓库 `addon/testaddon/settings.gradle` 演示 `includeBuild('../..')` 和 dependency substitution。客户端 API 在 Core 的完整 JAR 中；Composite Build 的普通 classes 变体只有 main，客户端扩展依赖应显式选择 JAR，见 TestAddon 的 `clientImplementation`。

模组 metadata 中声明 `ai-maid >=1.1.0`，并按使用的 Minecraft/Fabric 版本限制依赖。Core 的 Maven publishing 尚未配置远程仓库，第三方工程需要自行提供发布位置或使用本地组合构建。

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

Vanilla 切 Activity 不等于立即停止全部正在运行的 Behavior。需要同 tick 中断时，应像 TestAddon 的 WorkDefense 那样有顺序地停止旧行为、修改任务状态、更新 Activity；不要依靠每个行为自行碰巧退出。

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

实现 `MaidCommand.getStatus(AiMaidEntity)`，返回可翻译 Component；没有额外提示返回 `Component.empty()`。需要记录一次失败时，用 Addon 自己的 Memory 保存，并在重选或相应任务生命周期中清理。示例见 TestLumberCommand 和 LUMBER_RESULT。

Core 从服务器派生消息；背包完全装满时追加提示。只有当前有效菜单的查看者收到 `MaidStatusPayload`，内容变化才发送。客户端按 containerId + entityId 校验并更新 Menu 镜像，缺少客户端世界实体仍能显示。消息不是聊天广播，也不能反向控制实体。

命令/Activity 当前继续使用 Registry raw ID 的 DataSlot。26.2 的 ContainerSetData 索引和值都是有符号 short，Core 对注册数量和菜单数据数量做边界检查；按钮包的 buttonId 是 VarInt。可见性和排序必须在两端得到一致的列表，实例选择权限用 canPlayerSelect 表达。

GUI 为 318×236；存储起点 (182,34)，装备 (106,70)，玩家 (146,154)，总计 77 槽。模型和菜单装备镜像仍是独立同步通道，不要把镜像反写客户端实体。

## 机制边界

Core 提供破坏速度、距离/视线检查、真实工具破坏与掉落、远程发射和动画等机制。选敌、工作队列、范围、暂停/恢复和失败业务规则归 Addon。当前方块破坏没有 fake player，不能据此宣称覆盖所有领地或第三方玩家事件。

自动回血、饥饿、工资、工作职业、农耕、补种、搬运和高级皮肤不是本次公共 API。需要时围绕实际调用场景逐项扩展。
