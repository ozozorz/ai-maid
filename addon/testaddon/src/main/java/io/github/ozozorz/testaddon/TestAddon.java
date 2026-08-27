package io.github.ozozorz.testaddon;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ozozorz.testaddon.ai.TestAddonActivities;
import io.github.ozozorz.testaddon.ai.TestAddonMemoryModuleTypes;
import io.github.ozozorz.testaddon.ai.TestAddonSensorTypes;
import io.github.ozozorz.testaddon.command.TestAddonCommands;
import io.github.ozozorz.testaddon.data.TestAddonMaidData;



public class TestAddon implements ModInitializer {
	public static final String MOD_ID = "testaddon";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		TestAddonMemoryModuleTypes.initialize();
		TestAddonSensorTypes.initialize();

		TestAddonMaidData.initialize();
		TestAddonActivities.initialize();
		TestAddonCommands.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
