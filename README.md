# AI Maid

Minecraft Fabric 女仆模组。Core 提供主人关系、Brain、35 格背包与六槽装备、GUI、喂食回血、动画，以及近战和弓弩 PvE 战斗。正式 Addon 分别提供伐木、耕种、喂养和分类搬运。

当前版本：**Core 1.2.2、工作 Addon 1.2.1、LLM Addon 0.1.0**；环境：**Minecraft 26.2、Java 25、Fabric Loader 0.19.3、Fabric API 0.158.0+26.2**，Loom 1.17-SNAPSHOT，unobfuscated / no-remap。

## 使用

- 曲奇概率驯服；主人 Shift+右键打开菜单，不要求空手。
- 主人普通右键喂曲奇、苹果或面包，每次回复 4 点生命；满血与创造模式不会消耗。
- Core 命令：自由、跟随与护主、待命、主动战斗。跟随会自卫、保护主人并协助主人攻击；主动战斗额外清理附近敌对生物。PvE 规则保护玩家、队友和已驯服宠物。
- 安装对应 Addon 后，GUI 自动增加正式工作命令；工作中遭到攻击会自卫，结束后恢复。
- 伐木处理一棵树内能走近且伸手可及的原木；耕种照料四种普通作物；喂养使用真实饲料；分类搬运从铜箱取货，送入空箱或同种物品箱。
- 可选 LLM Addon 提供 `/maid chat` 聊天界面、角色设定、状态查询和自然语言工作控制。默认关闭，在服务端配置兼容 Chat Completions 的服务后启用。
- 管理员在主世界输入 `/maid test-scene`，在上方空中生成 31×31 测试平台并传送上去，配有自己的待命女仆、工具材料和四种工作场景。每次执行新建，不覆盖原有地形；操作说明见 [测试场地](docs/TEST_SCENE.md)。

具体范围、参数和升级办法见 [正式 Addon 1.2 使用与验收](docs/ADDONS_1_2.md)。客户端与服务器安装相同版本的 Core 和所选 Addon。**删除旧 TestAddon JAR**，无需安装全部四个 Addon。

## 独立构建

根目录、四个工作 Addon、`addon/llm` 和 `dev/devrun` 都是独立 Gradle Build，可分别在 IDEA 打开。Addon 通过 Maven coordinate 消费 Core，本地 Composite Build 替换为源码。

```powershell
.\gradlew.bat runClient                         # Core，含战斗
.\gradlew.bat -p addon/lumber runClient         # Core + 伐木
.\gradlew.bat -p addon/farming runClient        # Core + 耕种
.\gradlew.bat -p addon/feeding runClient        # Core + 喂养
.\gradlew.bat -p addon/hauling runClient        # Core + 分类搬运
.\gradlew.bat -p addon/llm runClient            # Core + LLM
.\gradlew.bat -p dev/devrun runClient           # Core + 四个工作 Addon + LLM
.\gradlew.bat build
.\gradlew.bat -p addon/lumber build             # 其他 Addon 同理
.\gradlew.bat -p dev/devrun build
```

各 Build 的 `build/libs` 产生 JAR；测试夹具不会进入发布包。四个工作 Addon 的 `build` 包含其独立服务端 GameTest，devrun 包含跨模组回归。远程 Maven 发布仓库尚未配置。

[LLM Addon 使用与配置](addon/llm/README.md)；其 `build` 包含协议、模拟 HTTP 和独立服务端 GameTest，`runClientGameTest` 用真实客户端与本机模拟模型验证完整流程。真实服务配置由服主提供。

## 验收

```powershell
.\gradlew.bat runGameTest
.\gradlew.bat runGameTest -PaddonFixtures
.\gradlew.bat -p addon/hauling runGameTest
.\gradlew.bat -p dev/devrun runGameTest
.\gradlew.bat runClientGameTest
.\gradlew.bat -p addon/lumber runClientGameTest
.\gradlew.bat -p dev/devrun runClientGameTest
```

使用临时世界。真实客户端截图在各 Build 的 `build/run/clientGameTest/screenshots`；后续构建可能清理这些文件。最新运行结果、日志与截图见 [验收记录](docs/ACCEPTANCE.md)。

- [工作调度与 TLM 参考说明](docs/WORK_SCHEDULING.md)
- [LLM 架构、TLM 参考与验收](docs/LLM.md)
- [Addon 公共 API 与生命周期](docs/ADDON_API.md)
- [Core 1.1 历史里程碑](docs/CORE_1_1.md)
- [战斗设计](docs/COMBAT.md)
- [持续破坏、整树伐木与死亡物品](docs/LUMBER_AND_DEATH.md)
- [动画设计](docs/ANIMATION.md)
- [工作自卫与恢复](docs/WORK_DEFENSE.md)

问题反馈：[GitHub Issues](https://github.com/ozozorz/ai-maid/issues)。许可：[CC0-1.0](LICENSE)。
