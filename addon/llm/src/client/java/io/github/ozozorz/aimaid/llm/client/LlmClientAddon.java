package io.github.ozozorz.aimaid.llm.client;

import io.github.ozozorz.aimaid.llm.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class LlmClientAddon implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChatEvent.TYPE, (event, context) -> {
            var client = context.client();
            if (event.kind().equals("open")) {
                // 先正常关闭容器，交回鼠标上的物品，再显示普通聊天 Screen。
                context.player().closeContainer();
                client.gui.setScreen(new MaidChatScreen(event.maid(), event.text(), event.busy()));
            } else if (client.gui.screen() instanceof MaidChatScreen screen && screen.maid().equals(event.maid())) screen.accept(event);
            else if (!event.kind().equals("busy") && !event.kind().equals("user")) context.player().sendSystemMessage(LlmText.line(event.kind(), event.text()));
        });
    }
}
