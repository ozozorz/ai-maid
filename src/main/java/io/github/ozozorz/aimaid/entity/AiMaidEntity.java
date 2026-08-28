package io.github.ozozorz.aimaid.entity;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.ai.sensing.MaidBrainSensors;
import io.github.ozozorz.aimaid.entity.inventory.MaidInventory;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommandMenu;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// 女仆实体类
public class AiMaidEntity extends TamableAnimal implements InventoryCarrier {

    // 定义一个 用来访问“女仆当前命令”这份同步数据的Key，通过它去entityData里读取真正的数据
    // SynchedEntityData.defineId(...) 告诉 Minecraft：我要给某一种实体类型注册一个新的同步数据槽。
    // 给 AiMaidEntity 定义一个 String 类型的网络同步数据槽。
    private static final EntityDataAccessor<String> MAID_COMMAND_ENTITY_DATA_ACCESSOR = SynchedEntityData.defineId(
            AiMaidEntity.class,
            EntityDataSerializers.STRING);

    public AiMaidEntity(Level level) {
        this(ModEntityTypes.AI_MAID, level);
    }

    public AiMaidEntity(EntityType<? extends AiMaidEntity> entityType, Level level) {
        super(entityType, level);
    }

    // 定义女仆实例化出来有哪些默认属性：最大生命值20，移动速度3
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    // Brain.Provider 不是 Brain。
    // Brain.Provider = “大脑生产说明书”。
    // Brain<AiMaidEntity> = 某一只具体 AiMaid 自己的大脑
    // Provider 这两个参数分别是什么？
    // 第一个：Sensor 列表。告诉 Provider：AiMaid 有哪些感知器。
    // 第二个：AiMaidAi::getActivities 是方法引用。当前 26.2 的：Brain.ActivitySupplier<E>
    // 是一个函数式接口：List<ActivityData<E>> createActivities(E body);
    // private static final Brain.Provider<AiMaidEntity> BRAIN_PROVIDER =
    // Brain.provider(
    // ImmutableList.of(
    // SensorType.NEAREST_LIVING_ENTITIES,
    // SensorType.NEAREST_PLAYERS,
    // ModSensorTypes.OWNER,
    // ModSensorTypes.MAID_COMMAND),
    // AiMaidAi::getActivities);
    private static final class BrainProviderHolder {
        private static final Brain.Provider<AiMaidEntity> INSTANCE = Brain.provider(MaidBrainSensors.getSensorTypes(),
                AiMaidAi::getActivities);
    }

    // rain.Packed = 从存档读取出来、准备恢复进 Brain 的记忆包。
    @Override
    protected Brain<AiMaidEntity> makeBrain(Brain.Packed packedBrain) {
        // return BRAIN_PROVIDER.makeBrain(this, packedBrain);
        return BrainProviderHolder.INSTANCE.makeBrain(this, packedBrain);
    }

    // 给 getBrain() 一个准确的泛型返回类型
    @Override
    @SuppressWarnings("unchecked")
    public Brain<AiMaidEntity> getBrain() {
        return (Brain<AiMaidEntity>) super.getBrain();
    }

    // 最重要的方法：customServerAiStep 让 Brain 真正“活起来”的地方。
    @Override
    protected void customServerAiStep(ServerLevel level) {
        this.getBrain().tick(level, this);

        AiMaidAi.updateActivity(this);

        super.customServerAiStep(level);

        /// DEBUG
        if (this.tickCount % 40 == 0) {
            this.brainTickDebug(level);
        }
        /// DUBUG END
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel arg0, AgeableMob arg1) {
        return null;
    }

    // 判断是不是可驯服物品
    private boolean isTamingItem(ItemStack stack) {
        return stack.getItem() == Items.COOKIE;
    }

    // 右键交互逻辑 - 驯服逻辑
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // ==============================
        // 未驯服：使用驯服物品
        // ==============================
        if (!this.isTame() && this.isTamingItem(stack)) {
            if (!this.level().isClientSide()) {
                stack.consume(1, player);

                boolean success = this.getRandom().nextInt(3) == 0;

                if (success) {
                    this.tame(player);
                }

                ServerLevel serverLevel = (ServerLevel) this.level();

                serverLevel.sendParticles(success ? ParticleTypes.HEART : ParticleTypes.SMOKE, this.getX(),
                        this.getY() + this.getBbHeight() * 0.5, this.getZ(), 7, this.getBbWidth() * 0.5,
                        this.getBbHeight() * 0.5, this.getBbWidth() * 0.5, 0.02);
            }
            return InteractionResult.SUCCESS;
        }

        // ==============================
        // 木棍DEBUG：
        // ==============================
        if (this.isTame() && this.isOwnedBy(player) && !player.isShiftKeyDown() && stack.is(Items.STICK)) {
            if (!this.level().isClientSide()) {
                this.stickDebug((ServerLevel) this.level());
            }
            return InteractionResult.SUCCESS;
        }
        // ==============================
        // 木锄头DEBUG：
        // ==============================
        if (this.isTame() && this.isOwnedBy(player) && !player.isShiftKeyDown() && stack.is(Items.WOODEN_HOE)) {
            if (!this.level().isClientSide()) {
                this.hoeDebug();
            }
            return InteractionResult.SUCCESS;
        }

        // ==============================
        // 已驯服：
        // Owner Shift + 空手右键
        // 切换 MaidCommand
        // ==============================
        if (this.isTame() && this.isOwnedBy(player) && player.isShiftKeyDown() && stack.isEmpty()) {
            // 客户端：
            // 吃掉这次交互，不再继续尝试其他手
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            /// 去抖
            long currentTick = this.level().getGameTime();
            long interval = currentTick - this.lastCommandInteractTick;
            // 每次收到交互都更新
            this.lastCommandInteractTick = currentTick;
            // 过滤右键长按产生的连续触发
            if (interval <= COMMAND_INTERACT_GAP_TICKS) {
                return InteractionResult.SUCCESS;
            }
            /// 切换MaidCommand
            this.cycleMaidCommand(player);
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    /// 右键交互去抖用
    private static final long COMMAND_INTERACT_GAP_TICKS = 5L;
    private long lastCommandInteractTick = -1000L;

    // 告诉每一只新创建的女仆: 你身上要有 MAID_COMMAND_ENTITY_DATA_ACCESSOR 这项同步数据，而且它的初始值是 FOLLOW
    // 命令的 ID。
    // 创建一只新的 AiMaidEntity -> Minecraft 开始建立它的同步数据表 -> 调用 defineSynchedData(builder)
    // -> 在这里告诉 builder：
    // “这只实体有哪些同步字段，以及它们默认是什么”
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        // 在当前这只女仆的同步数据表里，创建 MAID_COMMAND_ENTITY_DATA_ACCESSOR 这一项，并把默认值设置为
        // "你的模组id:follow"。
        builder.define(MAID_COMMAND_ENTITY_DATA_ACCESSOR, MaidCommands.FOLLOW_ID.toString());
    }

    // 这个方法回答：这只 Maid 当前记录的命令 ID 是什么？
    public Identifier getMaidCommandId() {
        String raw = this.entityData.get(MAID_COMMAND_ENTITY_DATA_ACCESSOR);
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return MaidCommands.FOLLOW_ID;
        }
        return id;
    }

    // 这个方法回答：把 ID 放进 Registry 后，得到的真正 MaidCommand 对象是什么？
    public MaidCommand getMaidCommand() {
        Identifier id = this.getMaidCommandId();
        return ModBuiltInRegistries.MAID_COMMAND.getOptional(id).orElse(MaidCommands.FOLLOW);
    }

    // 设置MaidCommand时，规则是：能成为 Maid 当前命令的 MaidCommand，必须先进入 Registry。
    public void selecetMaidCommand(MaidCommand maidCommand) {
        Identifier id = ModBuiltInRegistries.MAID_COMMAND.getKey(maidCommand);
        if (id == null) {
            throw new IllegalArgumentException("Unregistered MaidCommand: " + maidCommand);
        }
        // ① 先真正切换状态
        this.entityData.set(MAID_COMMAND_ENTITY_DATA_ACCESSOR, id.toString());
        // ② 再通知新 Command：
        // “你现在已经被选中了”
        if (!this.level().isClientSide()) {
            maidCommand.onSelected(this);
        }
    }

    // 持久化命令ID
    // 当 Minecraft 要把这只女仆保存进世界存档时，把她当前的 MaidCommand ID 也一起保存进去。
    // Minecraft 保存实体 -> Entity 自己保存位置、UUID 等数据 ->
    // AiMaidEntity.addAdditionalSaveData(...) -> 我们保存女仆自己的额外数据
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        // 背包数据存入存档
        this.writeInventoryToTag(output);

        output.putString("MaidCommand", this.getMaidCommandId().toString());
    }

    // 当 Minecraft 从世界存档中重新创建这只女仆时，把之前保存的 MaidCommand 重新读回来。
    // 读取实体存档 -> 创建 AiMaidEntity ->
    // readAdditionalSaveData(...) -> 读出 "ai_maid:stay" -> 重新放进 DATA_MAID_COMMAND
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        // 从存档读取背包数据
        this.readInventoryFromTag(input);

        // 请读取 "MaidCommand" 这一项。如果它不存在，就给我 FOLLOW。
        // 解决了一个很实际的问题：旧存档兼容。假设这是一个旧存档，根本没有MaidCommand，这时候就会返回默认值
        String raw = input.getStringOr("MaidCommand", MaidCommands.FOLLOW_ID.toString());
        // 如果存档里意外出现"MaidCommand" = "%%%坏掉的数据%%%"，Identifier.tryParse(raw)返回null
        // 如果存档里的命令 ID 连合法的 Identifier 都解析不出来，那就别用了，安全地退回 FOLLOW。
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            id = MaidCommands.FOLLOW_ID;
        }
        this.restoreMaidCommandId(id);
    }

    private void restoreMaidCommandId(Identifier id) {
        this.entityData.set(MAID_COMMAND_ENTITY_DATA_ACCESSOR, id.toString());
    }

    private void cycleMaidCommand(Player player) {
        List<MaidCommand> commands = MaidCommandMenu.getSelectableCommands(this, player);
        if (commands.isEmpty()) {
            return;
        }
        MaidCommand currentMaidCommand = this.getMaidCommand();
        int currentIndex = commands.indexOf(currentMaidCommand);
        int nextIndex;
        if (currentIndex < 0) {
            nextIndex = 0;
        } else {
            nextIndex = (currentIndex + 1) % commands.size();
        }
        MaidCommand nextMaidCommand = commands.get(nextIndex);
        this.selecetMaidCommand(nextMaidCommand);
        player.sendOverlayMessage(
                Component.translatable("message.ai-maid.maid_command_changed", nextMaidCommand.getDisplayName()));
    }

    private void brainTickDebug(ServerLevel level) {
        Optional<ItemEntity> wantedItem = this.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        System.out.println("maid = " + this.getUUID() + ", wanted item = "
                + wantedItem.map(itemEntity -> itemEntity.getItem().toString()).orElse("empty"));
    }

    private void stickDebug(ServerLevel level) {
        this.getInventory().clearContent();
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.AIR));
        for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
            this.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private void hoeDebug() {
        this.getInventory().setItem(0, new ItemStack(Items.APPLE, 63));
    }

    // ========inventory相关=========
    private final MaidInventory inventory = new MaidInventory();

    @Override
    public MaidInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void writeInventoryToTag(ValueOutput output) {
        ContainerHelper.saveAllItems(output.child(InventoryCarrier.TAG_INVENTORY), this.inventory.getItems());
    }

    @Override
    public void readInventoryFromTag(ValueInput input) {
        this.inventory.clearContent();
        ContainerHelper.loadAllItems(input.childOrEmpty(InventoryCarrier.TAG_INVENTORY), this.inventory.getItems());
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        // Maid 的 inventory 物理上有没有能力接收至少一部分这个物品？
        return !itemStack.isEmpty() && this.getInventory().canAddItem(itemStack);
    }

}
