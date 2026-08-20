package io.github.ozozorz.aimaid.client;

import io.github.ozozorz.aimaid.client.entity.model.ModEntityModelLayers;
import io.github.ozozorz.aimaid.client.entity.renderer.MiniGolemEntityRenderer;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class AIMaidClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as
		// rendering.
		ModEntityModelLayers.registerModelLayers();
		EntityRenderers.register(ModEntityTypes.MINI_GOLEM, MiniGolemEntityRenderer::new);
	}
}