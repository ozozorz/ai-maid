package io.github.ozozorz.aimaid.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.Slot;

/**
 * Minecraft 26.2：女仆容器的客户端表现层。
 */
public class AiMaidScreen extends AbstractContainerScreen<MaidMenu> {
    private static final int IMAGE_WIDTH = 318;
    private static final int IMAGE_HEIGHT = 236;
    private static final int TEXT_COLOR = 0xFF404040;

    // 预览窗口：相对于容器左上角的坐标。
    private static final int PREVIEW_LEFT = 10;
    private static final int PREVIEW_TOP = 26;
    private static final int PREVIEW_WIDTH = 56;
    private static final int PREVIEW_HEIGHT = 100;
    private static final int PREVIEW_SCALE = 40;

    private static final int STATUS_LEFT = 80;
    private static final int STATUS_TOP = 28;
    private static final int STATUS_WIDTH = 88;

    private static final int COMMAND_COLUMNS = 2;
    private static final int COMMAND_ROWS = 3;
    private static final int COMMANDS_PER_PAGE = COMMAND_COLUMNS * COMMAND_ROWS;
    private static final int COMMAND_LEFT = 12;
    private static final int COMMAND_TOP = 160;
    private static final int COMMAND_BUTTON_WIDTH = 55;
    private static final int COMMAND_BUTTON_HEIGHT = 20;
    private static final int COMMAND_GAP_X = 6;
    private static final int COMMAND_GAP_Y = 2;

    // 顺序对应现有 MaidEquipmentContainer，而不是 EquipmentSlot.ordinal()。
    private static final String[] EQUIPMENT_NAME_KEYS = {
            "gui.ai-maid.equipment.head",
            "gui.ai-maid.equipment.chest",
            "gui.ai-maid.equipment.legs",
            "gui.ai-maid.equipment.feet",
            "gui.ai-maid.equipment.mainhand",
            "gui.ai-maid.equipment.offhand"
    };

    private int commandPage;
    private final List<CommandButtonEntry> commandButtons = new ArrayList<>();

    public AiMaidScreen(MaidMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        // 先让父类计算 leftPos / topPos，再放置使用屏幕坐标的按钮。
        super.init();
        this.commandButtons.clear();
        this.commandPage = Math.clamp(this.commandPage, 0, getMaxCommandPage());
        addCommandButtons();
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick
    ) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // 仅绘制背景；不在这里改变任何 Maid 状态。
        drawPanel(graphics, x, y, IMAGE_WIDTH, IMAGE_HEIGHT, 0xFFC6C6C6);
        drawPanel(graphics, x + 6, y + 22, 64, 110, 0xFF515965);
        drawPanel(graphics, x + 74, y + 22, 100, 110, 0xFFE0E0E0);
        drawPanel(graphics, x + 178, y + 22, 134, 110, 0xFFE0E0E0);
        drawPanel(graphics, x + 6, y + 140, 128, 90, 0xFFE0E0E0);
        drawPanel(graphics, x + 140, y + 140, 172, 92, 0xFFE0E0E0);

        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            // 原版 inventory.png：第一个玩家存储槽外框位于 (7, 83)。
            // 这里引用游戏自带资源，不向模组中复制 Minecraft 纹理文件。
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    INVENTORY_LOCATION,
                    x + slot.x - 1, y + slot.y - 1,
                    7.0F, 83.0F,
                    18, 18,
                    256, 256
            );
        }

        extractMaidPreview(graphics, mouseX, mouseY);
    }

    private static void drawPanel(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill
    ) {
        graphics.fill(x, y, x + width, y + height, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFF4F4F4);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFF4F4F4);
        graphics.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF707070);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, 0xFF707070);
    }

    private void extractMaidPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        AiMaidEntity maid = getClientMaid();
        if (maid == null) {
            return; // 只缺少预览，不影响 Menu、按钮或背包。
        }

        int left = this.leftPos + PREVIEW_LEFT;
        int top = this.topPos + PREVIEW_TOP;
        int right = left + PREVIEW_WIDTH;
        int bottom = top + PREVIEW_HEIGHT;

        // 限制视线偏转，避免鼠标移到屏幕远端时预览侧转过大。
        int lookX = Math.clamp(mouseX, left, right);
        int lookY = Math.clamp(mouseY, top, bottom);

        InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                left, top, right, bottom,
                PREVIEW_SCALE,
                0.0625F,
                lookX, lookY,
                maid
        );
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // 父类已经平移到 leftPos / topPos；下面使用容器局部坐标。
        drawTextLine(graphics, this.title, 10, 7, IMAGE_WIDTH - 20, mouseX, mouseY);
        drawTextLine(graphics, Component.translatable("gui.ai-maid.maid_inventory"),
                MaidMenu.MAID_INV_LEFT, 26, 124, mouseX, mouseY);
        drawTextLine(graphics, this.playerInventoryTitle,
                MaidMenu.PLAYER_INV_LEFT, 142, 160, mouseX, mouseY);
        drawTextLine(graphics, Component.translatable("gui.ai-maid.equipment"),
                STATUS_LEFT, 64, STATUS_WIDTH, mouseX, mouseY);

        var commandsTitle = Component.translatable("gui.ai-maid.commands");
        if (getMaxCommandPage() > 0) {
            commandsTitle.append(" " + (this.commandPage + 1) + "/" + (getMaxCommandPage() + 1));
        }
        drawTextLine(graphics, commandsTitle, COMMAND_LEFT, 146, 82, mouseX, mouseY);

        AiMaidEntity maid = getClientMaid();
        Component healthLine;
        if (maid == null) {
            healthLine = Component.translatable("gui.ai-maid.entity_unavailable");
        } else {
            String health = String.format(Locale.ROOT, "%.1f / %.1f", maid.getHealth(), maid.getMaxHealth());
            healthLine = Component.translatable("gui.ai-maid.health", health);
        }
        drawTextLine(graphics, healthLine, STATUS_LEFT, STATUS_TOP, STATUS_WIDTH, mouseX, mouseY);

        // Command / Activity 来自 Menu 镜像，不依赖客户端实体是否刚好已加载。
        MaidCommand selected = this.menu.getSelectedCommand();
        Component selectedName = selected == null
                ? Component.translatable("gui.ai-maid.command_unknown")
                : selected.getDisplayName();
        drawTextLine(graphics, Component.translatable("gui.ai-maid.command_value", selectedName),
                STATUS_LEFT, STATUS_TOP + 12, STATUS_WIDTH, mouseX, mouseY);
        drawTextLine(graphics, Component.translatable("gui.ai-maid.activity_value",
                        getActivityDisplayName(this.menu.getActiveActivity())),
                STATUS_LEFT, STATUS_TOP + 24, STATUS_WIDTH, mouseX, mouseY);

        drawEmptyHandLabel(graphics, MaidMenu.EQUIPMENT_SLOT_START + 4, "gui.ai-maid.main_hand_short");
        drawEmptyHandLabel(graphics, MaidMenu.EQUIPMENT_SLOT_START + 5, "gui.ai-maid.off_hand_short");
        extractEmptyEquipmentTooltip(graphics, mouseX, mouseY);
    }

    /** 限宽显示；保持样式；鼠标悬停在被省略的文本上时显示原文。 */
    private void drawTextLine(
            GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth,
            int mouseX, int mouseY
    ) {
        boolean shortened = this.font.width(text) > maxWidth;
        graphics.enableScissor(x, y, x + maxWidth, y + this.font.lineHeight + 1);
        try {
            if (shortened) {
                Component ellipsis = Component.literal("...");
                FormattedText head = this.font.substrByWidth(text,
                        Math.max(0, maxWidth - this.font.width(ellipsis)));
                FormattedText displayed = FormattedText.composite(head, ellipsis);
                graphics.text(this.font, Language.getInstance().getVisualOrder(displayed),
                        x, y, TEXT_COLOR, false);
            } else {
                graphics.text(this.font, text, x, y, TEXT_COLOR, false);
            }
        } finally {
            graphics.disableScissor();
        }

        if (shortened && this.menu.getCarried().isEmpty()
                && isOverLocalRect(mouseX, mouseY, x, y, maxWidth, this.font.lineHeight)) {
            // Tooltip 用屏幕鼠标坐标；不要再给 mouseX / mouseY 加 leftPos / topPos。
            graphics.setTooltipForNextFrame(this.font, text, mouseX, mouseY);
        }
    }

    private void drawEmptyHandLabel(GuiGraphicsExtractor graphics, int menuSlotIndex, String key) {
        Slot slot = this.menu.slots.get(menuSlotIndex);
        if (!slot.isActive() || slot.hasItem()) {
            return; // 必须只在空槽时画，不能假定物品会完全覆盖占位文字。
        }
        Component text = Component.translatable(key);
        graphics.enableScissor(slot.x, slot.y, slot.x + 16, slot.y + 16);
        try {
            graphics.text(this.font, text,
                    slot.x + (16 - this.font.width(text)) / 2,
                    slot.y + 4, 0xFF505050, false);
        } finally {
            graphics.disableScissor();
        }
    }

    private void extractEmptyEquipmentTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        for (int index = 0; index < EQUIPMENT_NAME_KEYS.length; index++) {
            Slot slot = this.menu.slots.get(MaidMenu.EQUIPMENT_SLOT_START + index);
            if (slot.isActive() && !slot.hasItem()
                    && isOverLocalRect(mouseX, mouseY, slot.x, slot.y, 16, 16)) {
                graphics.setTooltipForNextFrame(this.font,
                        Component.translatable(EQUIPMENT_NAME_KEYS[index]), mouseX, mouseY);
                return;
            }
        }
    }

    private boolean isOverLocalRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        int localX = mouseX - this.leftPos;
        int localY = mouseY - this.topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    private @Nullable AiMaidEntity getClientMaid() {
        if (this.minecraft.level == null) {
            return null;
        }
        Entity entity = this.minecraft.level.getEntity(this.menu.getMaidEntityId());
        return entity instanceof AiMaidEntity maid && maid.isAlive() ? maid : null;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (CommandButtonEntry entry : this.commandButtons) {
            entry.button().active = this.menu.isCommandSelectable(entry.visibleCommandIndex());
            entry.button().setMessage(getCommandButtonLabel(entry.command()));
        }
    }

    private void addCommandButtons() {
        List<MaidCommand> commands = this.menu.getVisibleCommands();
        int start = this.commandPage * COMMANDS_PER_PAGE;
        int end = Math.min(start + COMMANDS_PER_PAGE, commands.size());
        for (int index = start; index < end; index++) {
            MaidCommand command = commands.get(index);
            int localIndex = index - start;
            int x = this.leftPos + COMMAND_LEFT
                    + (localIndex % COMMAND_COLUMNS) * (COMMAND_BUTTON_WIDTH + COMMAND_GAP_X);
            int y = this.topPos + COMMAND_TOP
                    + (localIndex / COMMAND_COLUMNS) * (COMMAND_BUTTON_HEIGHT + COMMAND_GAP_Y);

            var tooltipText = command.getDisplayName().copy();
            Identifier id = ModBuiltInRegistries.MAID_COMMAND.getKey(command);
            if (id != null) {
                tooltipText.append("\n").append(id.toString());
            }
            Button button = Button.builder(getCommandButtonLabel(command),
                            ignored -> pressCommandButton(command))
                    .bounds(x, y, COMMAND_BUTTON_WIDTH, COMMAND_BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(tooltipText))
                    .build();
            button.active = this.menu.isCommandSelectable(index);
            this.addRenderableWidget(button);
            this.commandButtons.add(new CommandButtonEntry(index, command, button));
        }
        addCommandPageButtons();
    }

    private void pressCommandButton(MaidCommand command) {
        if (this.minecraft.gameMode == null) {
            return;
        }
        int rawId = ModBuiltInRegistries.MAID_COMMAND.getId(command);
        if (rawId >= 0) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, rawId);
        }
    }

    private Component getCommandButtonLabel(MaidCommand command) {
        // 已选中不等于禁用；重复选择仍然可以触发 onSelected()。
        return this.menu.isSelectedCommand(command)
                ? Component.literal("> ").append(command.getDisplayName())
                : command.getDisplayName();
    }

    private int getMaxCommandPage() {
        return Math.max(0, (this.menu.getVisibleCommands().size() - 1) / COMMANDS_PER_PAGE);
    }

    private void addCommandPageButtons() {
        if (getMaxCommandPage() == 0) {
            return;
        }
        Button previous = Button.builder(Component.literal("<"), ignored -> {
            this.commandPage = Math.max(0, this.commandPage - 1);
            this.rebuildWidgets();
        }).bounds(this.leftPos + 96, this.topPos + 144, 14, 14).build();
        previous.active = this.commandPage > 0;
        this.addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), ignored -> {
            this.commandPage = Math.min(getMaxCommandPage(), this.commandPage + 1);
            this.rebuildWidgets();
        }).bounds(this.leftPos + 114, this.topPos + 144, 14, 14).build();
        next.active = this.commandPage < getMaxCommandPage();
        this.addRenderableWidget(next);
    }

    private Component getActivityDisplayName(Activity activity) {
        Identifier id = BuiltInRegistries.ACTIVITY.getKey(activity);
        return id == null
                ? Component.translatable("gui.ai-maid.activity_unknown")
                : Component.translatableWithFallback(id.toLanguageKey("maid_activity"), id.toString());
    }

    private record CommandButtonEntry(int visibleCommandIndex, MaidCommand command, Button button) {
    }
}
