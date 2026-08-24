package io.github.ozozorz.aimaid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;
import io.github.ozozorz.aimaid.entity.ai.sensing.ModSensorTypes;
import io.github.ozozorz.aimaid.entity.schedule.ModActivities;
import io.github.ozozorz.aimaid.item.ModItems;
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

		ModMemoryModuleTypes.initialize();
		ModSensorTypes.initialize();
		ModActivities.initialize();

		ModItems.initialize();

		ModEntityTypes.registerModEntityTypes();
		ModEntityTypes.registerAttributes();

		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
