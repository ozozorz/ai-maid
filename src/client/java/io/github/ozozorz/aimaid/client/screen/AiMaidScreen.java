package io.github.ozozorz.aimaid.client.screen;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.menu.MaidMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class AiMaidScreen extends AbstractContainerScreen<MaidMenu> {

    private static final int IMAGE_WIDTH = 250;
    private static final int IMAGE_HEIGHT = 222;

    private static final int STATUS_LEFT = 10;
    private static final int STATUS_TOP = 26;

    public AiMaidScreen(MaidMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;

        // 整个窗口背景。第一版只验证布局，以后再换正式 texture。
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);

        // 左上：Maid 状态区域。
        // 以后这里可以放：3D Maid 模型\HP\MaidCommand\其他状态
        graphics.fill(x + 6, y + 20, x + 100, y + 118, 0xFFE0E0E0);

        // 右上：35 格 MaidInventory。
        graphics.fill(x + 104, y + 20, x + 238, y + 118, 0xFFE0E0E0);
        
        // 下方：玩家背包。
        graphics.fill(x + 38, y + 132, x + 212, y + 216, 0xFFE0E0E0);

        // 暂时给所有 Slot 画一个简单槽位背景。
        // 真正 ItemStack 图标仍由 AbstractContainerScreen 自动绘制。
        for (Slot slot : this.menu.slots) {
            int slotX = x + slot.x;
            int slotY = y + slot.y;

            // 槽位外框
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);

            // 槽位内部
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // extractLabels 使用的是 Screen 内部局部坐标。
        graphics.text(this.font, this.title, STATUS_LEFT, 7, 0x404040, false);
        graphics.text(this.font, Component.translatable("gui.ai-maid.maid_inventory"), MaidMenu.MAID_INV_LEFT, 14, 0x404040, false);
        graphics.text(this.font, this.playerInventoryTitle, MaidMenu.PLAYER_INV_LEFT, 126, 0x404040, false);
        graphics.text(this.font, Component.translatable("gui.ai-maid.status"), STATUS_LEFT, STATUS_TOP, 0x404040, false);

        AiMaidEntity maid = getClientMaid();

        // 很重要：
        // 不要默认客户端一定能找到实体。实体可能刚好死亡、unload、dimension change、packet timing 发生变化
        if (maid == null) {
            graphics.text(this.font, Component.translatable("gui.ai-maid.entity_unavailable"), STATUS_LEFT, STATUS_TOP + 18, 0x404040, false);
        }

        String health = String.format(Locale.ROOT, "%.1f / %.1f", maid.getHealth(), maid.getMaxHealth());

        graphics.text( this.font, Component.translatable("gui.ai-maid.health", health), STATUS_LEFT, STATUS_TOP + 18, 0x404040, false);
        graphics.text(this.font, Component.translatable("gui.ai-maid.command"), STATUS_LEFT, STATUS_TOP + 36, 0x404040, false);
        graphics.text(this.font, maid.getMaidCommand().getDisplayName(), STATUS_LEFT, STATUS_TOP + 48, 0x404040, false);
    }

    private @Nullable AiMaidEntity getClientMaid() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }

        Entity entity = this.minecraft.level.getEntity(this.menu.getMaidEntityId());

        if (entity instanceof AiMaidEntity maid) {
            return maid;
        }

        return null;
    }

}
