package io.github.ozozorz.testaddon.client;

import net.fabricmc.api.ClientModInitializer;

public class TestAddonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        TestAddonAnimations.initialize();
	}
}
