package io.github.ozozorz.aimaid.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidInventory;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommandMenu;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MaidMenu extends AbstractContainerMenu {

    public static final int MAID_SLOT_COUNT = MaidInventory.STORAGE_SIZE;

    // 35 格：7 x 5
    public static final int MAID_COLUMNS = 7;
    public static final int MAID_ROWS = 5;

    // 整个 Screen 计划宽 250。左侧留给 Maid 信息、以后的人物模型、MaidCommand。右上放 MaidInventory。
    public static final int MAID_INV_LEFT = 108;
    public static final int MAID_INV_TOP = 26;

    // 玩家 9 x 3 + hotbar 放在整个窗口下半部分。
    public static final int PLAYER_INV_LEFT = 44;
    public static final int PLAYER_INV_TOP = 138;

    private MaidInventory maidInventory;

    // opening data 里的实体 ID。Server / Client 两边都有
    private int maidEntityId;

    // 只有服务器 Menu 真正持有 Maid
    private @Nullable AiMaidEntity serverMaid;
    
    // 这个 Menu 中应该展示哪些 Command。
    private List<MaidCommand> visiableCommands; 

    // 每个 visibleCommand 对应一个：0 = 当前不可选择 1 = 当前可以选择
    private List<DataSlot> commandSelectableData = new ArrayList<>();

    private DataSlot selectedCommandData;

    // 客户端构造器，ExtendedMenuType 会调用这个构造器
    public MaidMenu(int containerId, Inventory playerInventory, MaidMenuData data) {
        // 客户端创建一个“镜像容器”。真正内容随后由 AbstractContainerMenu 的标准 Slot 同步填进来。
        this(containerId, playerInventory, new MaidInventory(), data.maidEntityId(), null);
    }

    // 服务器构造器，这里直接引用真实 MaidInventory
    public MaidMenu(int containerId, Inventory playerInventory, AiMaidEntity maid) {
        this(containerId, playerInventory, maid.getInventory(), maid.getId(), maid);
    }

    private MaidMenu(int containerId, Inventory playerInventory, MaidInventory maidInventory, int maidEntityId, @Nullable AiMaidEntity serverMaid) {
        super(ModMenuTypes.MAID, containerId);
        checkContainerSize(maidInventory, MaidInventory.STORAGE_SIZE);
        this.maidInventory = maidInventory;
        this.maidEntityId = maidEntityId;
        this.serverMaid = serverMaid;

        // isVisibleInCommandMenu() + getMenuOrder()
        this.visiableCommands = MaidCommandMenu.getVisibleCommands();

        // DataSlot #0：当前选中的 MaidCommand。
        this.selectedCommandData = this.addDataSlot(createSelectedCommandData(serverMaid));

        // DataSlot #1 ... #N：每个 Command 当前是否允许选择。
        for (MaidCommand command : this.visiableCommands) {
            this.commandSelectableData.add(this.addDataSlot(createSelectableCommandData(serverMaid, playerInventory.player, command)));
        }

        // 通知 Container: 玩家开始打开它。
        // SimpleContainer 当前没有特殊逻辑，但遵守 Vanilla Container 生命周期。
        this.maidInventory.startOpen(playerInventory.player);
        
        addMaidInventorySlots();
        
        // Vanilla 26.2 已经提供：27 格玩家 inventory + 9 格 hotbar
        this.addStandardInventorySlots(playerInventory, PLAYER_INV_LEFT, PLAYER_INV_TOP);
    }

    private static DataSlot createSelectedCommandData(@Nullable AiMaidEntity serverMaid) {
        // Client: 只是一个普通 int 容器，等服务器 Menu sync 来 set()。
        if (serverMaid == null) {
            return DataSlot.standalone();
        }

        // Server: 不保存另一份 selectedCommand。每次 get() 都从真实 Maid 状态读取。
        return new DataSlot() {
            @Override
            public int get() {
                return ModBuiltInRegistries.MAID_COMMAND.getId(serverMaid.getMaidCommand());
            }

            @Override
            public void set(int value) {
                // Server 端不接受别人通过 DataSlot 直接写 MaidCommand。
            }
        };
    }

    private static DataSlot createSelectableCommandData(@Nullable AiMaidEntity serverMaid, Player player, MaidCommand command) {
        if (serverMaid == null) {
            return DataSlot.standalone();
        }

        return new DataSlot() {
            @Override
            public int get() {
                return command.canPlayerSelect(serverMaid, player) ? 1 : 0;
            }
            
            @Override
            public void set(int value) {
            }
        };
    }

    private void addMaidInventorySlots() {
        for (int row = 0; row < MAID_ROWS; row ++) {
            for (int column = 0; column < MAID_COLUMNS; column ++) {
                int slotIndex = column + row * MAID_COLUMNS;
                int x = MAID_INV_LEFT + column * SLOT_SIZE;
                int y = MAID_INV_TOP + row * SLOT_SIZE;
                this.addSlot(new Slot(maidInventory, slotIndex, x, y));
            }
        }
    }

    public int getMaidEntityId() {
        return maidEntityId;
    }

    public List<MaidCommand> getVisibleCommands() {
        return this.visiableCommands;
    }

    public boolean isCommandSelectable(int visibleCommandIndex) {
        if (visibleCommandIndex < 0 || visibleCommandIndex >= this.commandSelectableData.size()) {
            return false;
        }
        return this.commandSelectableData.get(visibleCommandIndex).get() != 0;
    }

    public int getSelectedCommandRawId() {
        return this.selectedCommandData.get();
    }

    public boolean isSelectedCommand(MaidCommand command) {
        int rawId = ModBuiltInRegistries.MAID_COMMAND.getId(command);
        return rawId == this.selectedCommandData.get();
    }

    public @Nullable MaidCommand getSelectedCommand() {
        return ModBuiltInRegistries.MAID_COMMAND.byId(this.selectedCommandData.get());
    }

    // Shift + 点击 基本按照 Vanilla ChestMenu 的语义：Maid -> Player / Player -> Maid
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        // 前 35 个 Slot：MaidInventory；后面：Player Inventory
        if (slotIndex < MAID_SLOT_COUNT) {
            // Maid -> Player
            if (!this.moveItemStackTo(stack, MAID_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> Maid
            if (!this.moveItemStackTo(stack, 0, MAID_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    // 这个方法不是单纯 UI 判断。ServerPlayer 每 tick 都会检查当前 Menu 是否仍然有效。 
    @Override
    public boolean stillValid(Player player) {
        // 客户端没有权限判断服务器安全。最终权威始终在 server Menu。
        if (serverMaid == null) {
            return true;
        }
        return 

            // 不能跨维度远程操作
            player.level() == serverMaid.level() &&

            // 实体仍存在
            serverMaid.isAlive() &&

             // 仍然是可驯服且属于这个玩家
            serverMaid.isTame() &&
            serverMaid.isOwnedBy(player) &&

            // 参考 Vanilla mount inventory。能打开之后跑到几百米外，GUI 仍然操作 MaidInventory。
            player.isWithinEntityInteractionRange(serverMaid, 4.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        maidInventory.stopOpen(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        // Client MaidMenu 永远不负责真正改状态。
        if (this.serverMaid == null) {
            return false;
        }

        // 网络层本身已经调用过 stillValid()，
        // 这里再次检查属于防御性验证。
        if (!this.stillValid(player)) {
            return false;
        }

        // buttonId 就是 MaidCommand Registry raw ID。
        MaidCommand command = ModBuiltInRegistries.MAID_COMMAND.byId(buttonId);

        // 恶意客户端完全可以手写一个不存在的数字。
        if (command == null) {
            return false;
        }

        // Registry 中存在不等于 普通玩家 GUI 中应该能选择。
        // 防止客户端直接构造隐藏 Command 的 ID。
        if (!this.visiableCommands.contains(command)) {
            return false;
        }

        //  最终 policy 判断必须服务器重新执行。客户端按钮变灰只是 UX，绝对不是安全机制。
        if (!command.canPlayerSelect(this.serverMaid, player)) {
            return false;
        }

        // 终于进入现有 MaidCommand mechanism。
        this.serverMaid.selecetMaidCommand(command);
        
        return true;
    }

}
