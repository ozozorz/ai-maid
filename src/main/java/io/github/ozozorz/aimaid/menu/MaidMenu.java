package io.github.ozozorz.aimaid.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.inventory.MaidInventory;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommandMenu;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public class MaidMenu extends AbstractContainerMenu {
    // 35 格：7 x 5
    public static final int MAID_COLUMNS = 7;
    public static final int MAID_ROWS = 5;

    // 35 格 MaidInventory
    public static final int MAID_INV_LEFT = 172;
    public static final int MAID_INV_TOP = 38;

    // 6 格 Equipment：
    // HEAD     CHEST
    // LEGS     FEET
    // MAINHAND OFFHAND
    public static final int EQUIPMENT_LEFT = 116;
    public static final int EQUIPMENT_TOP = 76;

    // Player inventory
    public static final int PLAYER_INV_LEFT = 75;
    public static final int PLAYER_INV_TOP = 158;

    // 定义 Menu slot range
    // Menu slot 0 .. 34 = MaidInventory
    // 35 .. 40 = Equipment
    // 41 .. 76 = Player
    public static final int MAID_SLOT_COUNT = MaidInventory.STORAGE_SIZE;
    public static final int EQUIPMENT_SLOT_COUNT = 6;
    public static final int MAID_SLOT_START = 0;
    public static final int MAID_SLOT_END = MAID_SLOT_START + MAID_SLOT_COUNT;
    public static final int EQUIPMENT_SLOT_START = MAID_SLOT_END;
    public static final int EQUIPMENT_SLOT_END = EQUIPMENT_SLOT_START + EQUIPMENT_SLOT_COUNT;
    public static final int PLAYER_SLOT_START = EQUIPMENT_SLOT_END;

    private MaidInventory maidInventory;
    
    private Container equipmentContainer;

    // opening data 里的实体 ID。Server / Client 两边都有
    private int maidEntityId;

    // 只有服务器 Menu 真正持有 Maid
    private @Nullable AiMaidEntity serverMaid;
    
    // 这个 Menu 中应该展示哪些 Command。
    private List<MaidCommand> visiableCommands; 

    // 每个 visibleCommand 对应一个：0 = 当前不可选择 1 = 当前可以选择
    private List<DataSlot> commandSelectableData = new ArrayList<>();

    private DataSlot selectedCommandData;

    private DataSlot activeActivityData;

    // 客户端构造器，ExtendedMenuType 会调用这个构造器
    public MaidMenu(int containerId, Inventory playerInventory, MaidMenuData data) {
        // 客户端创建一个“镜像容器”。真正内容随后由 AbstractContainerMenu 的标准 Slot 同步填进来。
        this(containerId, playerInventory, new MaidInventory(), new SimpleContainer(EQUIPMENT_SLOT_COUNT), data.maidEntityId(), null);
    }

    // 服务器构造器，这里直接引用真实 MaidInventory
    public MaidMenu(int containerId, Inventory playerInventory, AiMaidEntity maid) {
        this(containerId, playerInventory, maid.getInventory(), new MaidEquipmentContainer(maid), maid.getId(), maid);
    }

    private MaidMenu(int containerId, Inventory playerInventory, MaidInventory maidInventory, Container equipmentContainer, int maidEntityId, @Nullable AiMaidEntity serverMaid) {
        super(ModMenuTypes.MAID, containerId);
        checkContainerSize(maidInventory, MaidInventory.STORAGE_SIZE);
        checkContainerSize(equipmentContainer, EQUIPMENT_SLOT_COUNT);
        this.maidInventory = maidInventory;
        this.equipmentContainer = equipmentContainer;
        this.maidEntityId = maidEntityId;
        this.serverMaid = serverMaid;
        this.maidInventory.startOpen(playerInventory.player);
        addMaidInventorySlots();
        addEquipmentSlots();
        this.addStandardInventorySlots(playerInventory, PLAYER_INV_LEFT, PLAYER_INV_TOP);

        // isVisibleInCommandMenu() + getMenuOrder()
        this.visiableCommands = MaidCommandMenu.getVisibleCommands();

        // DataSlot #0：当前选中的 MaidCommand。
        this.selectedCommandData = this.addDataSlot(createSelectedCommandData(serverMaid));

        // DataSlot #1 ... #N：每个 Command 当前是否允许选择。
        for (MaidCommand command : this.visiableCommands) {
            this.commandSelectableData.add(this.addDataSlot(createSelectableCommandData(serverMaid, playerInventory.player, command)));
        }

        this.activeActivityData = this.addDataSlot(createActiveActivityData(serverMaid));

        // 通知 Container: 玩家开始打开它。
        // SimpleContainer 当前没有特殊逻辑，但遵守 Vanilla Container 生命周期。
        this.maidInventory.startOpen(playerInventory.player);
        
        addMaidInventorySlots();
        
        // Vanilla 26.2 已经提供：27 格玩家 inventory + 9 格 hotbar
        this.addStandardInventorySlots(playerInventory, PLAYER_INV_LEFT, PLAYER_INV_TOP);
    }

    private void addEquipmentSlots() {
        for (int index = 0; index < EQUIPMENT_SLOT_COUNT; index++) {
            EquipmentSlot equipmentSlot = MaidEquipmentContainer.getEquipmentSlot(index);
            int column = index % 2;
            int row = index / 2;
            int x = EQUIPMENT_LEFT + column * SLOT_SIZE;
            int y = EQUIPMENT_TOP + row * SLOT_SIZE;
            this.addSlot(new MaidEquipmentSlot(equipmentContainer, index, equipmentSlot, serverMaid, x, y));
        }
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

    public Activity getActiveActivity() {
        Activity activity = BuiltInRegistries.ACTIVITY.byId(this.activeActivityData.get());
        if (activity == null) {
            return Activity.IDLE;
        }
        return activity;
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

        /*
         * ============================
         * MaidInventory -> Player
         * ============================
         */
        if (slotIndex >= MAID_SLOT_START && slotIndex < MAID_SLOT_END) {
            if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }

        /*
         * ============================
         * Equipment -> MaidInventory
         *
         * Maid 背包满时再尝试给 Player。
         * ============================
         */
        } else if (slotIndex >= EQUIPMENT_SLOT_START && slotIndex < EQUIPMENT_SLOT_END) {
            if (!this.moveItemStackTo(stack, MAID_SLOT_START, MAID_SLOT_END, false)) {
                if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }

        /*
         * ============================
         * Player -> Maid
         * ============================
         */
        } else {
            // 先尝试 Vanilla-style：
            // 如果物品明确有 EQUIPPABLE component，
            // 并且对应装备槽为空，
            // Shift 点击直接装备。
            if (!tryMoveToEquipment(stack)) {
                // 不能自动装备 → 普通放进 MaidInventory。
                if (!this.moveItemStackTo(stack, MAID_SLOT_START, MAID_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        // 和 ChestMenu 一样：如果完全没有实际移动，返回 EMPTY。
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        return original;
    }

    private boolean tryMoveToEquipment(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        // 非 Equippable：
        // axe / apple 等不要因为 
        // getEquipmentSlotForItem() fallback MAINHAND
        // 就被 Shift 点击自动拿到手上。
        if (equippable == null) {
            return false;
        }

        EquipmentSlot equipmentSlot = equippable.slot();
        int relativeIndex = findEquipmentSlotIndex(equipmentSlot);
        if (relativeIndex < 0) {
            // BODY / SADDLE 等不属于 AiMaid GUI 支持范围。
            return false;
        }
        int menuSlotIndex = EQUIPMENT_SLOT_START + relativeIndex;
        Slot target = this.slots.get(menuSlotIndex);
        if (target.hasItem() || !target.mayPlace(stack)) {
            return false;
        }
        return this.moveItemStackTo(stack, menuSlotIndex, menuSlotIndex + 1, false);
    }

    private int findEquipmentSlotIndex(EquipmentSlot equipmentSlot) {
        for (int index = 0; index < MaidEquipmentContainer.EQUIPMENT_SLOTS.length; index++) {
            if (MaidEquipmentContainer.EQUIPMENT_SLOTS[index] == equipmentSlot) {
                return index;
            }
        }
        return -1;
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

    private static DataSlot createActiveActivityData(@Nullable AiMaidEntity serverMaid) {
        // Client: 只是服务器同步值的镜像。
        if (serverMaid == null) {
            return DataSlot.standalone();
        }

        // Sever: 不自己保存 Activity。每次 DataSlot.get() 都直接读取 Maid 真正的 Server Brain。
        return new DataSlot() {
            @Override
            public int get() {
                Activity activity = serverMaid.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
                int rawId = BuiltInRegistries.ACTIVITY.getId(activity);
                // 正常情况下 Activity 一定已经注册。防御性 fallback 到 IDLE。
                if (rawId < 0) {
                    return BuiltInRegistries.ACTIVITY.getId(Activity.IDLE);
                }
                return rawId;
            }

            @Override
            public void set(int value) {
                // Server 不允许通过 DataSlot 反向修改 Brain。
            }
        };
        
    }

}
