# 动画系统 1.0

适用于 Core / 正式 Lumber Addon 1.2.1。当前安装见 [Addon 使用说明](ADDONS_1_2.md)。

本轮建立在当前 Alex/Slim 人形、原版 RenderState、已有战斗和持续伐木机制上。动画定义和过渡在客户端；攻击、破坏、掉落、弹药与耐久仍由服务端原有逻辑决定。

## 当前覆盖

| 场景 | 驱动数据 | 表现 |
| --- | --- | --- |
| 站立、观察 | 实体年龄、UUID 相位、原版头部角度 | 轻微呼吸、手部起伏、看向目标 |
| 行走、较快移动 | 原版 walkAnimation 的距离和速度 | 对向摆臂、步态、身体起伏、速度增大时前倾 |
| 跳起、下落、落地 | onGround、垂直速度 | 起跳收腿、下落放腿、平滑回到地面姿势 |
| 水中、骑乘、蹲下、滑翔 | 原版实体状态 | 水中轻微摆动；保留原版特殊姿势 |
| 普通近战 | 原版 swing/attackTime | 主手挥击、躯干转动、收手；支持左右手 |
| 弓 | 原版使用物品状态 + 射击成功事件 | 拉弓、跟随视线瞄准、放箭收手 |
| 弩 | 原版装填进度、已装填组件 + 射击成功事件 | 装弩、持弩、射击后坐 |
| 伐木 | Lumber Addon 的真实破坏 Behavior | 独立抬斧—下砍循环、身体配合，停止时淡出 |
| 受伤、死亡 | 原版伤害同步和 deathTime | 受伤短暂摇晃，死亡停止其他动作并放松四肢 |
| 护甲、手持、皮肤第二层、GUI | 同一帧状态和骨骼规则 | 跟随身体姿势，预览不改写世界实体 |

快走/跑动的混合依据实际位移速度，不能仅凭 FOLLOW、ATTACK 或某个 Activity 名称判断。LUMBER 命令中的寻路阶段也不会播放伐木循环，只有真正开始破坏时才播放。

目前的素材面向现有六部件人形骨架。它提供动作切换、过渡、身体部位覆盖和 Addon 注册，不包含面部表情、头发/裙摆物理、脚底 IK 或另一套自定义骨架模型资源。新增这些素材时可以独立扩展；本轮没有引入外部动画库。

## 从服务端行为到这一帧

```text
服务端 Behavior / 原版实体机制
    ├─ 位置、头部朝向、装备、使用物品、挥手、受伤、死亡
    └─ playAnimationAction(id, duration) / stopAnimationAction(id, token)
                         ↓ 原版实体数据包
每只客户端 AiMaidEntity
                         ↓ Renderer.extractRenderState
每只实体的 MaidAnimationController
                         ↓ 只读 MaidAnimationFrame
AiMaidEntityRenderState
                         ↓ Model.setupAnim
原版基础姿势 → 移动层 → 动作层混合 → 受伤叠加
                         ↓
皮肤模型 / 各部位护甲模型 / 手持物 / 容器预览
```

`MaidAnimationClip` 是所有女仆共享的动作说明；它没有“正在播到第几帧”的字段。每只客户端实体的控制器保存自己的过渡权重。控制器通过弱引用键关联实体，值中不保存实体引用，实体离开客户端后可以回收。这里使用 Guava `MapMaker.weakKeys` 的对象身份比较，因为原版 `Entity.equals` 比较网络 ID，普通 `WeakHashMap` 可能把 ID 相同的新客户端实例误认成旧实例。

`MaidAnimationFrame` 是一次提取的只读数据。一个 tick 内再次提取同一只女仆，只进行采样，不再推进过渡。因此渲染世界、护甲或者打开 GUI，不会让动作加速。模型对象可以被所有女仆复用：每次 `setupAnim` 先复位，再从传入的状态生成姿势，没有上一只女仆的播放状态。

`AiMaidEntityModel` 为每个模型缓存绑定到本模型骨骼的原版 `KeyframeAnimation`。护甲有各自的模型，分别对同一个 RenderState 应用相同规则。26.2 的皮肤第二层是对应骨骼的子部件，会自然继承父骨骼变换。手持物通过 `translateToHand` 继承根部和手臂变换，并保留细手臂的半像素对齐。

GUI 仍调用原版 `InventoryScreen.extractEntityInInventoryFollowsMouse`，使用世界中的客户端实体和已有 Renderer。预览调整的是自己的 RenderState 视线；Menu 的装备镜像没有被写回实体。

## 动作仲裁与部位覆盖

优先级越高，越优先成为当前动作。切换时旧动作逐渐淡出、新动作逐渐淡入，最多保留四条过渡轨道，并归一化动作权重。死亡、睡眠和自动旋转攻击直接清理普通动作轨道，特殊姿势交给对应处理。

| 动作 | 优先级 |
| --- | --- |
| 弓/弩射击后的短动作 | 350 |
| 正在使用弓/弩 | 300 |
| Addon 动作 | 0–290，由 Addon 选择；当前伐木为 260 |
| 普通近战 | 220 |
| 已装填弩的持弩 | 100 |

普通行走和呼吸一直构成基础层，不参与上表竞争。动作只覆盖其定义的部件；当前近战和伐木使用上身，因此下半身可以继续行走。受伤反馈最后叠加，死亡则独占姿势。

原版 `swing` 仍用于通知正在挥手。26.2 的 `Animal/TamableAnimal` 不会自动调用 `updateSwingTime`，所以女仆在 `aiStep` 中像原版 Monster/Player 一样主动推进它；否则 `swinging` 可以一直为真，实际 `attackAnim` 却不前进。伐木的自定义动作覆盖本轮普通挥击，停止伐木后不会补播同一挥手包的近战尾巴。26.2 的矛刺仍调用原版特殊攻击姿势；不会把所有物品都硬套成斧头挥击。

关键帧使用原版 `AnimationDefinition` / `AnimationChannel` / `KeyframeAnimation`。手臂、腿的旋转作为覆盖姿势，头和躯干在基础姿势上叠加；位置和缩放使用原版增量。`mirrorMainHand` 把右手素材镜像给左手，`aimAtLookTarget` 把视线修正施加到主手，要求定义包含 `right_arm`。

## Addon 如何接入

公共代码只声明 namespaced ID，不导入任何 `net.minecraft.client` 类型：

```java
public static final Identifier CHOP = Identifier.fromNamespaceAndPath("my_addon", "chop");
```

在 Addon 的 `ClientModInitializer` 注册共享素材。所有注册必须在 Renderer 创建前完成；重复 ID、保留 ID、未知部件和不合法时长/优先级会立即报错。原版关键帧对象注册后按只读数据使用，不要动态改写：

```java
MaidAnimations.register(CHOP, new MaidAnimationClip(
    AnimationDefinition.Builder.withLength(0.3F).looping()
        .addAnimation("right_arm", MaidAnimations.rotation(
            MaidAnimations.key(0.0F, -55, -8, -12),
            MaidAnimations.key(0.1F, -140, -12, -8),
            MaidAnimations.key(0.3F, -55, -8, -12)))
        .build(),
    260,   // 动作优先级
    2,     // 过渡响应时间，单位 tick；并非两 tick 后突然切换
    true,  // 随主手镜像
    true   // 手臂适当跟随视线
));
```

服务端在一次真实行为开始时保存 token，在停止时带着同一 token 清理：

```java
// 字段属于本只女仆的 Behavior 实例，不能放在共享 Command 对象中。
private int animationToken;

// start：时间上限必须与业务自身的退出条件配合。
animationToken = maid.playAnimationAction(CHOP, 1200);

// stop：旧行为的 stop 不会清掉后来新开的动作。
maid.stopAnimationAction(CHOP, animationToken);
```

当前同步四项：动作 Identifier、开始的世界 gameTime、持续上限、播放序号。持续范围为 1–1200 tick，时间到自动失效；本轮单块伐木自身也有 1200 tick 上限。相同 tick 重播也会获得新序号。短射击事件分别为放箭 8 tick、弩发射 6 tick。

客户端中途开始追踪一个仍在服务器运行的实体，会使用同步的开始时间恢复当前阶段。服务器真正卸载、切换命令、重选命令或死亡时会清理动作；存档重载不会复活一个旧工作动画。未知 ID 保留同步数据，但客户端没有对应素材时使用基础姿势。

不要从动画关键帧回调中破坏方块或扣血。`MaidRangedAttack` 只在真实发射成功后发出表现事件；`BreakLumberTarget` 在真实到位开工时开始循环，在结束/中断时停止。伤害和掉落没有因为动画延迟而重新结算。

Lumber Addon 仍然通过 Maven coordinate 消费 Core。本地 composite build 默认的 `classes` 变体只有 `main`，所以客户端依赖显式要求 `LibraryElements.JAR`，取得同时含公共类和客户端类的完整模组产物；没有硬编码跨工程源码路径。

## 验收与阅读入口

服务端测试验证动作 token、超时、中断清理及真实业务触发。整合客户端流程在烘焙模型上检查 294 个姿势组合，并验证网络同步、共享渲染器、护甲、手持物和 GUI 预览。最新运行结果见 [验收记录](ACCEPTANCE.md)。

- [动作值](../src/main/java/io/github/ozozorz/aimaid/entity/animation/MaidAnimationAction.java)、[实体生命周期](../src/main/java/io/github/ozozorz/aimaid/entity/AiMaidEntity.java)
- [客户端控制器](../src/client/java/io/github/ozozorz/aimaid/client/entity/animation/MaidAnimationController.java)、[一帧数据](../src/client/java/io/github/ozozorz/aimaid/client/entity/animation/MaidAnimationFrame.java)
- [素材注册](../src/client/java/io/github/ozozorz/aimaid/client/entity/animation/MaidAnimations.java)、[素材参数](../src/client/java/io/github/ozozorz/aimaid/client/entity/animation/MaidAnimationClip.java)
- [模型应用](../src/client/java/io/github/ozozorz/aimaid/client/entity/model/AiMaidEntityModel.java)、[姿势混合](../src/client/java/io/github/ozozorz/aimaid/client/entity/animation/MaidModelPose.java)
- [伐木素材](../addon/lumber/src/client/java/io/github/ozozorz/aimaid/lumber/client/LumberAnimations.java)、[业务触发](../addon/lumber/src/main/java/io/github/ozozorz/aimaid/lumber/ai/behavior/BreakLumberTarget.java)
- [服务端测试](../dev/devrun/src/gametest/java/io/github/ozozorz/aimaid/integration/AnimationGameTests.java)、[客户端测试](../dev/devrun/src/gametest/java/io/github/ozozorz/aimaid/integration/AnimationClientGameTest.java)、[模型检查](../dev/devrun/src/gametest/java/io/github/ozozorz/aimaid/integration/AnimationPoseChecks.java)
