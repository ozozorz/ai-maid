package io.github.ozozorz.aimaid.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.github.ozozorz.aimaid.menu.MaidStatusPayload;
import io.github.ozozorz.aimaid.menu.MaidMenu;

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
		ClientPlayNetworking.registerGlobalReceiver(
				MaidStatusPayload.TYPE, (payload, context) -> {
					if (context.player().containerMenu instanceof MaidMenu menu) menu.acceptStatus(payload);
				});
	}
}
