package io.github.ozozorz.aimaid.llm.client;

import io.github.ozozorz.aimaid.llm.network.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** 聊天界面只有镜像文本；发给服务器的只有目标 UUID、操作和玩家输入。 */
public final class MaidChatScreen extends Screen {
    private final UUID maid;
    private final List<Component> messages = new ArrayList<>();
    private EditBox input;
    private Button send, cancel;
    private boolean busy;
    private int left, panelWidth, scroll;
    private String draft = "";
    public MaidChatScreen(UUID maid, String name, boolean busy) {
        super(Component.translatable("llm.ai-maid.title", Component.literal(name))); this.maid = maid; this.busy = busy;
    }
    public UUID maid() { return maid; }
    public boolean busy() { return busy; }
    public List<Component> messages() { return List.copyOf(messages); }
    @Override protected void init() {
        if (input != null) draft = input.getValue();
        panelWidth = Math.min(520, width - 24); left = (width - panelWidth) / 2;
        input = addRenderableWidget(new EditBox(font, left + 8, height - 62, panelWidth - 80, 20, Component.translatable("llm.ai-maid.input")));
        input.setMaxLength(2000); input.setValue(draft);
        send = addRenderableWidget(Button.builder(Component.translatable("llm.ai-maid.send"), b -> submit()).bounds(left + panelWidth - 66, height - 62, 58, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("llm.ai-maid.cancel"), b -> request("cancel", "")).bounds(left + 8, height - 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("llm.ai-maid.clear"), b -> request("clear", "")).bounds(left + 94, height - 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + panelWidth - 86, height - 34, 78, 20).build());
        input.setResponder(value -> updateButtons()); setInitialFocus(input); updateButtons();
    }
    private void updateButtons() { if (send != null) { send.active = !busy && !input.getValue().isBlank(); cancel.active = busy; input.setEditable(!busy); } }
    public void setInput(String value) { input.setValue(value); }
    public void submit() {
        if (busy || input.getValue().isBlank()) return;
        request("send", input.getValue()); busy = true; updateButtons();
    }
    public void accept(ChatEvent event) {
        if (!maid.equals(event.maid())) return;
        busy = event.busy();
        if (event.kind().equals("user") && input != null && event.text().equals(input.getValue())) input.setValue("");
        if (event.kind().equals("clear")) messages.clear();
        if (!event.kind().equals("busy")) {
            messages.add(LlmText.line(event.kind(), event.text()));
            while (messages.size() > 40) messages.removeFirst(); scroll = 0;
        }
        updateButtons();
    }
    private void request(String action, String text) { ClientPlayNetworking.send(new ChatRequest(maid, action, text)); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) { submit(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll + (int) (scrollY * 3)); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(left, 12, left + panelWidth, height - 8, 0xEE18202C);
        String heading = title.getString();
        if (font.width(heading) > panelWidth - 20) heading = font.plainSubstrByWidth(heading, panelWidth - 32) + "...";
        graphics.text(font, heading, left + 10, 22, 0xFFF2F4FA, false);
        graphics.fill(left + 6, 40, left + panelWidth - 6, height - 88, 0xBB10161F);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (var message : messages) { lines.addAll(font.split(message, panelWidth - 28)); lines.add(FormattedCharSequence.EMPTY); }
        int visible = Math.max(1, (height - 138) / 11);
        scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
        int start = Math.max(0, lines.size() - visible - scroll);
        graphics.enableScissor(left + 8, 43, left + panelWidth - 8, height - 90);
        for (int i = start, y = 47; i < Math.min(lines.size(), start + visible); i++, y += 11)
            graphics.text(font, lines.get(i), left + 14, y, 0xFFE0E8F5, false);
        graphics.disableScissor();
        String hint = Component.translatable("llm.ai-maid." + (busy ? "thinking" : "hint")).getString();
        graphics.text(font, font.plainSubstrByWidth(hint, panelWidth - 20), left + 10, height - 79, 0xFFADBBD0, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
