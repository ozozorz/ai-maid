package io.github.ozozorz.aimaid.client;

import io.github.ozozorz.aimaid.client.entity.model.ModEntityModelLayers;
import io.github.ozozorz.aimaid.client.entity.renderer.AiMaidEntityRenderer;
import io.github.ozozorz.aimaid.client.entity.renderer.MiniGolemEntityRenderer;
import io.github.ozozorz.aimaid.client.screen.AiMaidScreen;
import io.github.ozozorz.aimaid.entity.ModEntityTypes;
import io.github.ozozorz.aimaid.menu.ModMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class AIMaidClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as
		// rendering.
		ModEntityModelLayers.registerModelLayers();
		EntityRenderers.register(ModEntityTypes.MINI_GOLEM, MiniGolemEntityRenderer::new);
		EntityRenderers.register(ModEntityTypes.AI_MAID, AiMaidEntityRenderer::new);

		MenuScreens.register(ModMenuTypes.MAID, AiMaidScreen::new);
	}
}