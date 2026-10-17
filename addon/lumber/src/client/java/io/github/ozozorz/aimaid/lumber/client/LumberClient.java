package io.github.ozozorz.aimaid.lumber.client;

import net.fabricmc.api.ClientModInitializer;

public class LumberClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
        LumberAnimations.initialize();
	}
}
