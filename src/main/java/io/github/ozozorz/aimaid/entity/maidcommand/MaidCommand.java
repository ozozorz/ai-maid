package io.github.ozozorz.aimaid.entity.maidcommand;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.ozozorz.aimaid.command.MaidCommandCommandApi;
import io.github.ozozorz.aimaid.command.maidtargetresolver.MaidTargetResolver;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

// 任何可以注册到 Maid Command Registry 的对象，
// 都必须是一个 MaidCommand。
public interface MaidCommand {

    // =========================
    // AI
    // =========================
    // 创建这只 Maid 的 Brain 时，我这个 Command 要往 Brain 里安装哪些 ActivityData？
    default List<ActivityData<AiMaidEntity>> createActivities(AiMaidEntity maid) {
        return List.of();
    }

    // Brain 已经创建好了。现在这只 Maid 使用我这个 Command 时，应该按什么顺序尝试 Activity？
    List<Activity> getActivityCandidates(AiMaidEntity maid);

    // =========================
    // Registry / Display
    // =========================
    // MaidCommand object -> 询问 Registry -> “我的注册 ID 是什么？”
    default Identifier getRegistryId() {
        Identifier id = ModBuiltInRegistries.MAID_COMMAND.getKey(this);
        if (id == null) {
            throw new IllegalStateException("Unregistered MaidCommand: " + this);
        }
        return id;
    }

    // 例如: ai_maid:free 生成 maid_command.ai_maid.free
    // testaddon:patrol 生产 maid_command.testaddon.patrol
    default String getTranslationKey() {
        return this.getRegistryId().toLanguageKey("maid_command");
    }

    // 假设某个 Addon 作者忘记添加 maid_command.testaddon.patrol
    // 玩家不会看到很难看的 maid_command.testaddon.patrol
    // 而会 fallback： testaddon:patrol
    default Component getDisplayName() {
        Identifier id = this.getRegistryId();
        return Component.translatableWithFallback(this.getTranslationKey(), id.toString());
    }

    // =========================
    // Player menu
    // =========================
    // “Registry 中存在” 不一定等于：“玩家可以在普通命令菜单里看到”
    // 以后完全可能有：internal:panic、debug:reset、scripted:sleep 这种内部命令。
    // 它们可以：Registry 中存在、Brain 能使用、程序能 setMaidCommand(...)
    // 但普通玩家菜单不应该显示。
    default boolean isVisibleInCommandMenu() {
        return true;
    }

    // 例如一个 Addon 的 PATROL 要求：
    // 必须先设置至少两个巡逻点
    // 那么它仍然可以显示在菜单中：巡逻
    // 但暂时不可选择。以后 GUI 可以把它画成灰色。
    // 还有一个重要的安全原则：canPlayerSelect() 不是用来替代 maid.isOwnedBy(player)
    // 主人权限属于整个 Maid 命令系统的通用授权，应该由实体交互/以后网络包的服务端处理统一验证。
    default boolean canPlayerSelect(AiMaidEntity maid, Player player) {
        return true;
    }

    // Registry 本身不是为 UI 排序需求设计的
    // 不能把 注册先后顺序 偷偷变成 玩家菜单顺序
    // 所以给 MaidCommand 明确一个 UI 排序值
    default int getMenuOrder() {
        return 1000;
    }

    default void buildServerCommand(LiteralArgumentBuilder<CommandSourceStack> node, CommandBuildContext buildContext,
            MaidTargetResolver targetResolver) {
        node.executes(context -> MaidCommandCommandApi.executeSelection(context, targetResolver, this));
    }

    default void onSelected(AiMaidEntity maid) {

    }

    default boolean allowsItemPickup(AiMaidEntity maid, ServerLevel level, ItemStack itemStack) {
        return false;
    }
}
