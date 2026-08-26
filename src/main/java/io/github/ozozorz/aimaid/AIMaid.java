package io.github.ozozorz.aimaid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ozozorz.aimaid.command.ModServerCommands;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import io.github.ozozorz.aimaid.entity.ai.sensing.ModSensorTypes;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.item.ModItems;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import io.github.ozozorz.aimaid.test.TestAddonActivities;
import io.github.ozozorz.aimaid.test.TestAddonCommands;
import io.github.ozozorz.aimaid.test.TestAddonMaidData;
import io.github.ozozorz.aimaid.test.TestAddonMemoryModuleTypes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class AIMaid implements ModInitializer {
	public static final String MOD_ID = "ai-maid";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// 先建立Registry本身
		ModBuiltInRegistries.initialize();

		// 再往Registry注册核心entries
		MaidCommands.initialize();

		// Brain 类型
		ModMemoryModuleTypes.initialize();
		ModSensorTypes.initialize();
		ModActivities.initialize();

		// 其他原有初始化
		ModItems.initialize();

		// 物品初始化
		ModEntityTypes.registerModEntityTypes();
		ModEntityTypes.registerAttributes();

		// 指令初始化
		ModServerCommands.initialize();

		// Test加载
		TestAddonMaidData.initialize();
		TestAddonCommands.initialize();
		TestAddonMemoryModuleTypes.initialize();
		TestAddonActivities.initialize();

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
