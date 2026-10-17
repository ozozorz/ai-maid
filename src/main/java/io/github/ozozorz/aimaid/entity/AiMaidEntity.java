package io.github.ozozorz.aimaid.entity;

import net.minecraft.network.chat.Component;
import io.github.ozozorz.aimaid.entity.ai.memory.ModMemoryModuleTypes;

import org.jspecify.annotations.Nullable;

import io.github.ozozorz.aimaid.entity.ai.AiMaidAi;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import io.github.ozozorz.aimaid.entity.ai.sensing.MaidBrainSensors;
import io.github.ozozorz.aimaid.entity.inventory.MaidInventory;
import io.github.ozozorz.aimaid.entity.inventory.MaidItemTransfer;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommands;
import io.github.ozozorz.aimaid.menu.MaidMenuOpener;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// 女仆实体类
public class AiMaidEntity extends TamableAnimal implements InventoryCarrier, net.minecraft.world.entity.ContainerUser {
    // 原版容器开合计数使用的临时位置，不是另一份容器或物品数据。
    private net.minecraft.core.BlockPos openedContainerPos;

    public void setOpenedContainerPos(net.minecraft.core.BlockPos pos) {
        MaidServerAccess.requireServerThread(this);
        openedContainerPos = pos == null ? null : pos.immutable();
    }

    @Override public boolean hasContainerOpen(net.minecraft.world.level.block.entity.ContainerOpenersCounter counter,
            net.minecraft.core.BlockPos pos) {
        if (openedContainerPos == null) return false;
        if (openedContainerPos.equals(pos)) return true;
        var state = level().getBlockState(openedContainerPos);
        return state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValueOrElse(net.minecraft.world.level.block.ChestBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
                        != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                && net.minecraft.world.level.block.ChestBlock.getConnectedBlockPos(openedContainerPos, state).equals(pos);
    }

    @Override public double getContainerInteractionRange() { return 3.0; }

    // 定义一个 用来访问“女仆当前命令”这份同步数据的Key，通过它去entityData里读取真正的数据
    // SynchedEntityData.defineId(...) 告诉 Minecraft：我要给某一种实体类型注册一个新的同步数据槽。
    // 给 AiMaidEntity 定义一个 String 类型的网络同步数据槽。
    private static final EntityDataAccessor<String> MAID_COMMAND_ENTITY_DATA_ACCESSOR = SynchedEntityData.defineId(AiMaidEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION_ID = SynchedEntityData.defineId(AiMaidEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> ANIMATION_START = SynchedEntityData.defineId(AiMaidEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ANIMATION_DURATION = SynchedEntityData.defineId(AiMaidEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIMATION_SEQUENCE = SynchedEntityData.defineId(AiMaidEntity.class, EntityDataSerializers.INT);

    public AiMaidEntity(Level level) {
        this(ModEntityTypes.AI_MAID, level);
    }

    public AiMaidEntity(EntityType<? extends AiMaidEntity> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(true);
    }

    // 女仆默认属性：最大生命值 20，移动速度 0.3。
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            // 女仆自身的基础攻击伤害
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.ATTACK_SPEED, 4.0)
            .add(Attributes.BLOCK_BREAK_SPEED)
            .add(Attributes.MINING_EFFICIENCY)
            .add(Attributes.SUBMERGED_MINING_SPEED)
            .add(Attributes.BLOCK_INTERACTION_RANGE);
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
        private static final Brain.Provider<AiMaidEntity> INSTANCE = Brain.provider(MaidBrainSensors.getSensorTypes(), AiMaidAi::getActivities);
    }

    // rain.Packed = 从存档读取出来、准备恢复进 Brain 的记忆包。
    @Override
    protected Brain<AiMaidEntity> makeBrain(Brain.Packed packedBrain) {
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
        // 已驯服：
        // Owner Shift + 右键（任意手持物品）
        // 打开 Maid GUI。
        // ==============================
        if (this.isTame() && this.isOwnedBy(player) && player.isShiftKeyDown()) {
            // 客户端只负责确认交互已经被消费。
            // 真正 openMenu 必须服务器执行。
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                MaidMenuOpener.open(serverPlayer, this);
            }

            return InteractionResult.SUCCESS;
        }
        
        if (!player.isShiftKeyDown() && isAlive() && isTame() && isOwnedBy(player)
                && !player.isSpectator() && stack.is(MaidFeeding.HEALING_FOODS)) {
            if (!this.level().isClientSide()) MaidFeeding.feed(this, player, hand);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

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
        builder.define(ANIMATION_ID, MaidAnimationAction.NONE.toString());
        builder.define(ANIMATION_START, 0L);
        builder.define(ANIMATION_DURATION, 0);
        builder.define(ANIMATION_SEQUENCE, 0);
    }

    public MaidAnimationAction getAnimationAction() {
        Identifier id = Identifier.tryParse(this.entityData.get(ANIMATION_ID));
        return new MaidAnimationAction(id == null ? MaidAnimationAction.NONE : id,
                this.entityData.get(ANIMATION_START), this.entityData.get(ANIMATION_DURATION),
                this.entityData.get(ANIMATION_SEQUENCE));
    }

    /** 返回本次播放的 token；同 tick 重播也能被客户端区分。 */
    public int playAnimationAction(Identifier id, int durationTicks) {
        MaidServerAccess.requireServerThread(this);
        if (id == null || id.equals(MaidAnimationAction.NONE) || durationTicks < 1 || durationTicks > MaidAnimationAction.MAX_DURATION) {
            throw new IllegalArgumentException("Invalid maid animation action or duration");
        }
        int token = this.entityData.get(ANIMATION_SEQUENCE) + 1;
        if (!this.isAlive()) return token;
        this.entityData.set(ANIMATION_ID, id.toString());
        this.entityData.set(ANIMATION_START, this.level().getGameTime());
        this.entityData.set(ANIMATION_DURATION, durationTicks);
        this.entityData.set(ANIMATION_SEQUENCE, token);
        return token;
    }

    public void stopAnimationAction(Identifier id, int token) {
        MaidServerAccess.requireServerThread(this);
        if (this.getAnimationAction().id().equals(id)
                && this.entityData.get(ANIMATION_SEQUENCE) == token) clearAnimationAction();
    }

    private void clearAnimationAction() {
        this.entityData.set(ANIMATION_ID, MaidAnimationAction.NONE.toString());
        this.entityData.set(ANIMATION_DURATION, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && !getAnimationAction().isActive(this.level().getGameTime())) clearAnimationAction();
    }

    @Override
    public void aiStep() {
        // 26.2 的 Animal/TamableAnimal 不调用此方法；人形女仆需要像 Monster/Player
        // 一样在两端推进挥手计时，否则 swing 包只会设置 swinging 而没有 attackAnim。
        this.updateSwingTime();
        super.aiStep();
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

    /** 状态由真实服务器状态派生，不保存第二份 Command/Activity。 */
    public Component getMaidStatus() {
        MaidServerAccess.requireServerThread(this);
        if (ModBuiltInRegistries.MAID_COMMAND.getOptional(getMaidCommandId()).isEmpty()) {
            return Component.translatable("status.ai-maid.missing_command", getMaidCommandId().toString());
        }
        var message = getMaidCommand().getStatus(this);
        if (inventory.isCompletelyFull()) {
            var full = Component.translatable("status.ai-maid.storage_full");
            return message.equals(Component.empty()) ? full : message.copy().append("; ").append(full);
        }
        return message;
    }

    // 设置MaidCommand时，规则是：能成为 Maid 当前命令的 MaidCommand，必须先进入 Registry。
    public void selectMaidCommand(MaidCommand maidCommand) {
        ServerLevel level = MaidServerAccess.requireServerThread(this);
        if (!isAlive()) throw new IllegalStateException("Cannot select a command for a dead maid");
        Identifier id = ModBuiltInRegistries.MAID_COMMAND.getKey(maidCommand);
        if (id == null) {
            throw new IllegalArgumentException("Unregistered MaidCommand: " + maidCommand);
        }
        // 先停止旧行为，让持续动作能清理裂纹、蓄力和导航；重选也会重新开始。
        this.getBrain().stopAll(level, this);
        this.getNavigation().stop();
        this.stopUsingItem();
        clearAnimationAction();
        this.getMaidCommand().onDeselected(this);
        // ① 先真正切换状态
        this.entityData.set(MAID_COMMAND_ENTITY_DATA_ACCESSOR, id.toString());
        // ② 再通知新 Command：
        // “你现在已经被选中了”
        this.getBrain().setMemory(ModMemoryModuleTypes.MAID_COMMAND, maidCommand);
        maidCommand.onSelected(this);
        AiMaidAi.updateActivity(this);
    }

    /** @deprecated 拼写兼容入口；使用 {@link #selectMaidCommand(MaidCommand)}。 */
    @Deprecated(forRemoval = false)
    public void selecetMaidCommand(MaidCommand command) {
        selectMaidCommand(command);
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
        clearAnimationAction();
        // AiMaid 固有能力：
        // 始终启用 Vanilla Mob pickup infrastructure。
        // 是否实际接受某个物品由 wantsToPickUp / MaidCommand policy 决定。
        this.setCanPickUpLoot(true);

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
        id = MaidCommands.migrateLegacyId(id);
        this.entityData.set(MAID_COMMAND_ENTITY_DATA_ACCESSOR, id.toString());
    }

    // ========inventory相关=========
    private final MaidInventory inventory = new MaidInventory();

    @Override
    public MaidInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) this.getBrain().stopAll(level, this);
        if (!this.level().isClientSide()) clearAnimationAction();
        super.die(source);
    }

    @Override
    public void onRemoval(Entity.RemovalReason reason) {
        // 卸载/跨维度也要停止持续动作，但它们不属于死亡，不掉落携带物。
        if (this.level() instanceof ServerLevel level) this.getBrain().stopAll(level, this);
        if (!this.level().isClientSide()) clearAnimationAction();
        super.onRemoval(reason);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        // Mob 的此实现会按概率掉装备并随机改变耐久。
        // 女仆的携带物统一在 dropEquipment 转移；不要在这里再执行 Mob 的装备掉落。
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        // 先从源位置取走，再生成 ItemEntity。重复回调时源位置已经为空。
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            dropCarriedStack(level, this.inventory.removeItemNoUpdate(slot));
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!MaidItemTransfer.supportsEquipmentSlot(slot)) continue;
            ItemStack stack = this.getItemBySlot(slot);
            this.setItemSlot(slot, ItemStack.EMPTY);
            dropCarriedStack(level, stack);
        }
        this.inventory.setChanged();
    }

    private void dropCarriedStack(ServerLevel level, ItemStack stack) {
        // 与玩家背包相同，消失诅咒也作用于放在 storage 内的物品。
        // keepInventory 只控制玩家；mobDrops 不应吞掉女仆已经携带的物品。
        if (!stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            this.spawnAtLocation(level, stack);
        }
    }

    // 返回真实弹药栈，Vanilla 装填会直接拆分它，不凭空补箭。
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) {
            return ItemStack.EMPTY;
        }
        ItemStack held = ProjectileWeaponItem.getHeldProjectile(this,
                projectileWeapon.getSupportedHeldProjectiles());
        if (!held.isEmpty()) {
            return held;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (getMaidCommand().canUseInventoryItem(this, slot)
                    && projectileWeapon.getAllSupportedProjectiles().test(candidate)) return candidate;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUseNonMeleeWeapon(ItemStack weapon) {
        return weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem;
    }

    // Brain 是攻击目标的权威来源，Core 的 Combat Sensor 注册这个 Memory。
    @Override
    public @Nullable LivingEntity getTarget() {
        return this.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                ? this.getTargetFromBrain() : super.getTarget();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        ItemStack weapon = this.getMainHandItem();
        boolean hit = super.doHurtTarget(level, target);
        // 26.2 Mob 调用 hurtEnemy，但没有 Player 的 postHurtEnemy 耐久步骤。
        if (hit && target instanceof LivingEntity livingTarget && !weapon.isEmpty()) {
            weapon.postHurtEnemy(livingTarget, this);
        }
        return hit;
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
        if (itemStack.isEmpty())
            return false;
        if (!this.getInventory().canAddItem(itemStack))
            return false;
        return this.getMaidCommand().allowsItemPickup(this, level, itemStack);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        InventoryCarrier.pickUpItem(level, this, this, entity);
        Brain<AiMaidEntity> brain = this.getBrain();
        boolean isCurrentWantedItem = brain.isMemoryValue(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, entity);
        if (!isCurrentWantedItem) {
            return;
        }
        boolean targetInvalid = entity.isRemoved() || !this.wantsToPickUp(level, entity.getItem());
        if (targetInvalid) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        }
    }

}
