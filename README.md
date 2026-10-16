# AI Maid

Minecraft Fabric 女仆模组。Core 提供实体、主人关系、Brain、背包与装备、菜单、跟随、喂食和动画机制；Addon 决定工作与战斗策略。

当前开发版本 **1.1.0**，目标环境为 **Minecraft 26.2、Java 25、Fabric Loader 0.19.3、Fabric API 0.158.0+26.2**。构建使用 `net.fabricmc.fabric-loom` 1.17-SNAPSHOT 和当前 unobfuscated / no-remap API。

## 使用

- 曲奇概率驯服。主人 Shift+右键打开菜单，手中可以拿物品。
- 受伤时，主人普通右键投喂曲奇、苹果或面包，每次回复 4 点生命。满血不消耗；创造模式不消耗。
- Core 提供自由、跟随和待命。跟随时主人不可用会原地等待，持续卡路时尝试受限的同维度安全归队。
- 背包有 35 个存储槽，装备使用原版六槽。菜单会显示命令、Activity 和具体提示。
- TestAddon 增加巡逻、有限整树伐木、近战与弓弩 PvE 战斗，以及工作中自卫和战后恢复。

客户端与服务端应安装相同版本的 Core 及需要的 Addon。TestAddon 1.1.0 要求 Core 1.1.0 或更新版本；跨版本网络兼容不作保证。

## 开发与构建

仓库根、`addon/testaddon`、`dev/devrun` 是三个独立 Gradle Build，以 Composite Build 替换本地 Maven 依赖。都可以单独在 IDEA 中打开。

在仓库根执行，Windows 使用 `gradlew.bat`，其他平台使用 `./gradlew`：

```powershell
.\gradlew.bat runClient                         # 只加载 Core
.\gradlew.bat -p addon/testaddon runClient      # Core + TestAddon
.\gradlew.bat -p dev/devrun runClient           # 整体开发入口
.\gradlew.bat build
.\gradlew.bat -p addon/testaddon build
.\gradlew.bat -p dev/devrun build
```

Core 和 TestAddon 的 `build` 包含各自服务端 GameTest。发布 JAR 在各 Build 的 `build/libs`，测试代码不进入发布 JAR。仓库配置了 Maven publication，尚未配置远程发布仓库。

## 验收与文档

```powershell
.\gradlew.bat runGameTest                       # 独立 Core
.\gradlew.bat runGameTest -PaddonFixtures       # 两个独立 Addon 注册入口夹具
.\gradlew.bat runClientGameTest                 # Core 真实客户端流程
.\gradlew.bat -p addon/testaddon runClientGameTest
```

测试使用临时测试世界。客户端截图位于对应 Build 的 `build/run/clientGameTest/screenshots`；再次构建可能清理这些输出，应先归档需要保留的截图。

- [Core 1.1 完善内容与验收](docs/CORE_1_1.md)
- [Addon 公共 API 与生命周期约定](docs/ADDON_API.md)
- [战斗系统](docs/COMBAT.md)
- [死亡携带物、持续破坏与整树伐木](docs/LUMBER_AND_DEATH.md)
- [动画系统](docs/ANIMATION.md)
- [工作中自卫与战后恢复](docs/WORK_DEFENSE.md)

旧里程碑文档的测试数量属于当时记录。当前实现和最新验收以 Core 1.1 文档为准。问题反馈见 [GitHub Issues](https://github.com/ozozorz/ai-maid/issues)。

## License

沿用仓库现有 [CC0-1.0](LICENSE) 许可。本轮没有更改许可。
