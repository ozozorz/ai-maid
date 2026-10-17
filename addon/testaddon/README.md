# TestAddon 已退役

从 1.2.1 起，本目录不再构建或加载模组。旧代码可在 Git 历史中查看。

- 战斗迁入 Core；守卫合并到跟随。
- 伐木迁入 `../lumber`。
- 耕种、喂养和分类搬运分别位于 `../farming`、`../feeding`、`../hauling`。
- 巡逻只保留为 `dev/devrun/src/gametest` 的隐藏测试夹具，不进入正式 JAR。
- 旧回归测试已迁入 Core、伐木 Addon 和 devrun 的 gametest 源集。

启动整体开发环境：在仓库根执行 `gradlew.bat -p dev/devrun runClient`。
请从客户端和服务器的 mods 目录删除旧 `testaddon-*.jar`，不要同时安装旧版测试模组。

安装、迁移和验收说明见 [正式 Addon 1.2](../../docs/ADDONS_1_2.md)。
