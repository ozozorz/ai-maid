# AI Maid

[简体中文](README.md) | [한국어](README.ko.md) | [English](README.en.md)

A maid companion mod for Minecraft Fabric. Core provides ownership, Brain AI, a 35-slot inventory, six equipment slots, a GUI, healing through food, animations, and PvE combat with melee weapons, bows, and crossbows. The work Addons provide woodcutting, farming, animal feeding, and item sorting and hauling.

Current versions: **Core / Farming / Lumber 1.3.0, Feeding / Hauling 1.2.1, LLM Addon 0.3.0**. Environment: **Minecraft 26.2, Java 25, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2**, Loom 1.17-SNAPSHOT, unobfuscated / no-remap.

The detailed guides linked below are currently in Chinese.

## Usage

- Use cookies to tame a maid; each attempt has a chance of success. The owner can Shift + right-click to open the menu, even while holding an item.
- The owner can right-click normally with a cookie, apple, or bread to restore 4 health points. Food is not consumed at full health or in Creative mode.
- Core modes include free roam, follow and guard, standby, and active combat. In follow mode, the maid defends herself, protects the owner, and assists the owner's attacks. Active combat also targets nearby hostile mobs. PvE rules protect players, teammates, and tamed pets.
- Installing a work Addon automatically adds its work commands to the GUI. If attacked while working, the maid defends herself and then resumes work after combat.
- Woodcutting handles the logs in a single tree that the maid can walk up to and reach. Farming tends four common crops. Animal feeding consumes actual feed items. Sorting and hauling takes items from copper chests and delivers them to empty chests or chests containing the same item type.
- The optional LLM Addon provides chat and autonomous tasks: the model observes the environment, selects one action from a limited set, waits for the execution result, and then decides what to do next. Supported actions include moving, equipping tools, cutting down a specified tree, harvesting four crop types, storing items in a specified chest, and returning to the owner. The existing continuous work modes remain available.
- In `/maid chat`, you can say, "Collect 8 wheat, put them in the chest at x y z, then come back." Collection uses items already in the inventory. Failures such as a full chest include the amount actually stored. The model should only switch to a backup chest if the owner allows it. The LLM Addon is disabled by default; see the [LLM usage guide](addon/llm/README.md) for configuration.
- An administrator can run `/maid test-scene` in the Overworld to create a 31×31 platform in the air above them and teleport onto it. The platform includes a maid assigned to the administrator in standby mode, tools and materials, and four work scenarios. Each run creates a new platform without replacing existing terrain. See the [test scene guide](docs/TEST_SCENE.md).

See the [work Addon guide](docs/ADDONS_1_2.md) for continuous work ranges and parameters, and [action interfaces and autonomous tasks](docs/AUTONOMOUS_TASKS.md) for autonomous task actions, budgets, and lifecycles. Install matching Core and selected Addon versions on both the client and the server. **Remove the old TestAddon JAR.** You do not need to install all four work Addons.

## Independent Builds

The root project, the four work Addons, `addon/llm`, and `dev/devrun` are independent Gradle builds. Each can be opened separately in IntelliJ IDEA. Addons depend on Core through Maven coordinates; local composite builds substitute the Core source project for that dependency.

```powershell
.\gradlew.bat runClient                         # Core, including combat
.\gradlew.bat -p addon/lumber runClient         # Core + woodcutting
.\gradlew.bat -p addon/farming runClient        # Core + farming
.\gradlew.bat -p addon/feeding runClient        # Core + animal feeding
.\gradlew.bat -p addon/hauling runClient        # Core + sorting and hauling
.\gradlew.bat -p addon/llm runClient            # Core + LLM
.\gradlew.bat -p dev/devrun runClient           # Core + four work Addons + LLM
.\gradlew.bat build
.\gradlew.bat -p addon/lumber build             # The same applies to other Addons
.\gradlew.bat -p dev/devrun build
```

JAR files are generated in each build's `build/libs` directory. Test fixtures are excluded from release artifacts. The `build` task for each of the four work Addons includes its dedicated-server GameTests; devrun includes regression tests across mods. A remote Maven publishing repository has not yet been configured.

See [LLM Addon usage and configuration](addon/llm/README.md). Its `build` task includes protocol tests, mock HTTP tests, and dedicated-server GameTests. `runClientGameTest` verifies the complete flow using a real client and a local mock model. The server owner supplies the configuration for a real model service.

## Verification

```powershell
.\gradlew.bat runGameTest
.\gradlew.bat runGameTest -PaddonFixtures
.\gradlew.bat -p addon/hauling runGameTest
.\gradlew.bat -p dev/devrun runGameTest
.\gradlew.bat runClientGameTest
.\gradlew.bat -p addon/lumber runClientGameTest
.\gradlew.bat -p dev/devrun runClientGameTest
```

Use a temporary world. Screenshots from real-client tests are saved in each build's `build/run/clientGameTest/screenshots` directory; later builds may clear these files. See the [verification records](docs/ACCEPTANCE.md) for the latest recorded results, logs, and screenshots.

- [Work scheduling and TLM reference notes](docs/WORK_SCHEDULING.md)
- [Action interfaces and autonomous task architecture](docs/AUTONOMOUS_TASKS.md)
- [LLM architecture, TLM references, and verification](docs/LLM.md)
- [Public Addon API and lifecycle](docs/ADDON_API.md)
- [Core 1.1 historical milestone](docs/CORE_1_1.md)
- [Combat design](docs/COMBAT.md)
- [Progressive block breaking, whole-tree woodcutting, and death drops](docs/LUMBER_AND_DEATH.md)
- [Animation design](docs/ANIMATION.md)
- [Self-defense and work resumption](docs/WORK_DEFENSE.md)

Report issues on [GitHub Issues](https://github.com/ozozorz/ai-maid/issues). License: [CC0-1.0](LICENSE).
