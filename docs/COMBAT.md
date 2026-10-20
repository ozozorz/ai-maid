# Core PvE 战斗（Minecraft 26.2 / 1.3.0）

战斗随 Core 独立运行。跟随包含自卫、护主和协助攻击，主动战斗增加清怪；四个正式工作 Addon 接入[工作自卫与恢复](WORK_DEFENSE.md)。

GUI 可以选择“跟随与护主”和“战斗”；命令也支持原有 nearest、selector、uuid、name 入口：

```text
/maid nearest command ai-maid:follow
/maid nearest command ai-maid:attack
/maid nearest gui
```

| 模式 | 会主动做什么 | 共同能力 |
| --- | --- | --- |
| 护卫 `ai-maid:follow` | 跟随主人，收到新的伤害/攻击记录时参战 | 自卫、护主、协助主人攻击、近战、弓、弩、换武器、脱战 |
| 战斗 `ai-maid:attack` | 在护卫基础上，主动寻找附近可见的敌对生物 | 同上 |

把剑、斧等带 `WEAPON` 组件的近战武器、弓或弩放进女仆 storage 或手部槽；箭可以放 storage 或副手。每次装填消耗真实箭，武器使用真实耐久。无限弓仍需至少一支普通箭；光灵箭、药箭和附魔效果交给原版处理。弩自动射击只接受箭，不发射烟花；副手烟花也会阻止该弩进入自动装填流程，请收回 storage。

远处优先使用有弹药的远程武器，靠近后优先近战。当前持近战武器时，超过 6 格才尝试切远程；当前持远程武器时，低于 3 格才尝试切近战，避免在一个边界来回切换。没有可用远程武器或箭时继续近战；没有近战武器时仍能空手或用现有手持物攻击。有多把近战武器时按主手攻击属性选择，不计算所有附魔与敌人组合的最优 DPS。

换装复用 `MaidItemTransfer`，交换的是原来的两个 ItemStack，因此满背包也能交换，名字、组件、附魔、耐久不会重建丢失。正在拉弓/装弩时不主动换武器；外部取走或更换武器会取消当前使用。装好的弩在停工时保留 `CHARGED_PROJECTILES`，其中的箭不会因为取消命令而消失。Core 1.1 已统一 GUI、工作和战斗的换装规则，六槽均尊重 `PREVENT_ARMOR_CHANGE`，创造模式主人可手动取下。详见 [公共 API](ADDON_API.md)。

选敌优先级是新的自卫目标、伤害主人的目标、主人攻击的目标、主动扫描的敌人。同等优先级保持当前目标，较高优先级可以中断当前目标。不会攻击玩家、主人、同队友方或已驯服的宠物。主动扫描默认选 `Enemy` 且非 `NeutralMob` 的生物；中立生物可以通过自卫或协助触发。

| 参数 | 当前值 | 含义 |
| --- | --- | --- |
| 主动搜索 | 16 格 | 只选当前可见目标 |
| 追击边界 | 24 格 | 女仆和目标都不能离主人过远，彼此也不能超过此距离 |
| 远程射程 | 12 格 | 超出后通过导航接近 |
| 连续失去视线 | 100 tick | 超时放弃 |
| 无接近进展 | 160 tick | 可见但接近不了也会放弃 |
| 单次目标上限 | 1200 tick | 避免永远纠缠同一目标 |
| 失败目标冷却 | 200 tick | 放弃后暂时不重新追逐该目标 |

以上 tick 按正常 20 TPS 运行。主人不可用时不主动清怪，也不继续原来的护主追击；新的自卫以女仆开战位置作为边界。目标死亡、跨维度、离开范围、路径失败、切换命令都会结束追击。脱战后按距离选择归队、拾取或待机。手动切换或重选命令会停止旧战斗。四个工作命令只因自身遇袭临时自卫，战后恢复工作。

箭发射前检查友方是否挡在射线上；发射后又进入弹道的友方由服务器伤害事件保护。这是 Core 的 PvE 政策，无法据此宣称兼容任意模组的自定义伤害实现。这里没有 PvP、盾牌格挡、投掷三叉戟或 Boss 专用战术。

数据包可用两个实体类型标签扩展目标规则：

```text
data/ai-maid/tags/entity_type/combat_targets.json
data/ai-maid/tags/entity_type/combat_ignored.json
```

`combat_targets` 增加主动清怪的实体类型，`combat_ignored` 排除所有选敌入口中的实体类型。示例文件结构为 `{"replace": false, "values": ["othermod:hostile"]}`。排除标签和友方保护优先于主动目标标签，不允许用标签打开 PvP。

运行链可以按下面的顺序阅读：

```mermaid
flowchart TD
    Command[命令：护卫或战斗] --> Sensor[MaidCombatSensor 每 tick 观察新事件]
    Sensor --> Memory[本只 Brain 的 ATTACK_TARGET]
    Memory --> Activity[FIGHT Activity]
    Activity --> Behavior[本只 Brain 的 MaidCombatBehavior]
    Behavior --> Equipment[CombatEquipment 选择真实武器]
    Behavior --> Walk[WALK_TARGET → MoveToTargetSink → Navigation]
    Behavior --> Melee[AiMaidEntity.doHurtTarget → Vanilla 近战]
    Behavior --> Ranged[MaidRangedAttack → Vanilla 弓弩投射物]
    Memory --> Menu[Menu DataSlot → 客户端状态文字]
```

`MaidCommands.FOLLOW` 与 `MaidCommands.ATTACK` 是共享策略定义，分别返回 GUARD 和 HUNT。Core 的 `AiMaidAi` 只贡献一次 FIGHT Activity；工作 Addon 将它放进候选列表，不重复注册。菜单自动枚举命令。

每只女仆创建 Brain 时，SensorType 创建自己的 `MaidCombatSensor`，`AiMaidAi.getActivities(maid)` 创建自己的 `MaidCombatBehavior`。Sensor 保存受伤时间戳、追击进展和失败目标冷却，Behavior 保存本次蓄力武器和目标；这些运行状态没有放在 Command singleton 中，也不持久化为永久仇恨。Sensor 在切换模式和主人时更新事件基线，所以切回护卫不会重放以前的一次受伤。

Sensor 决定“打谁”，写入 `ATTACK_TARGET`。现在进入/退出战斗时，Sensor 会先停止旧行为并按当前命令切换 Activity，让工作中断发生在行为执行前；`AiMaidAi.updateActivity` 仍在 Brain.tick 之后做常规候选更新。Behavior 再决定靠近、近战、拉弓、装弩或射击。移动只写 `WALK_TARGET`，实际导航仍由 Core 的 `MoveToTargetSink` 执行。

近战调用 `AiMaidEntity.doHurtTarget`，父类 Mob 负责攻击属性、附魔、击退和伤害。26.2 的 Mob 缺少玩家攻击链末尾的 `postHurtEnemy` 耐久步骤，所以实体补上它。攻击冷却放在本只 Brain 的 `ATTACK_COOLING_DOWN`，由攻击速度决定且至少间隔 10 tick；重新点命令不能绕过冷却。

弓不是直接调用玩家专用的 `releaseUsing`。`MaidRangedAttack` 通过一个很小的 Mixin Invoker 调用 `ProjectileWeaponItem.draw/shoot`，保留原版弹药拆分、无限、多重射击、投射物创建、附魔和耐久逻辑。弓拉满 20 tick 再射击。弩由 `LivingEntity` 正常驱动 `CrossbowItem.onUseTick` 完成装填，再通过 `performShooting` 发射；快速装填附魔仍能影响原版装填时间。两种远程武器射击后有 20 tick 冷却。

`AiMaidEntity.getProjectile` 返回手部或 storage 中真正的弹药栈；不是 `copy()`，也不会凭空生成箭。原版装填对这个栈进行拆分，剩余数量留在原处。`getTarget()` 将 Brain 目标提供给原版生物接口；Core 独立运行也包含完整战斗链。

客户端只读取实体已有的手持与使用状态来生成拉弓、装弩、持弩姿势。装备包和使用状态包独立同步，因此停止使用、换装和再次使用要留出 tick 边界。GUI 的装备镜像没有写回世界实体；Activity 依然来自服务端 Menu DataSlot。

测试入口为 Core 的 `runGameTest` / `runClientGameTest`，工作恢复在 `dev/devrun` 回归。最新实际结果见 [验收记录](ACCEPTANCE.md)。

## 阅读入口

- [选敌与追击](../src/main/java/io/github/ozozorz/aimaid/entity/combat/MaidCombatSensor.java)、[战斗执行](../src/main/java/io/github/ozozorz/aimaid/entity/combat/MaidCombatBehavior.java)
- [武器选择](../src/main/java/io/github/ozozorz/aimaid/entity/combat/CombatEquipment.java)、[目标规则](../src/main/java/io/github/ozozorz/aimaid/entity/combat/CombatRules.java)
- [服务端测试](../src/gametest/java/io/github/ozozorz/aimaid/CombatGameTests.java)、[客户端测试](../src/gametest/java/io/github/ozozorz/aimaid/CombatClientGameTest.java)
