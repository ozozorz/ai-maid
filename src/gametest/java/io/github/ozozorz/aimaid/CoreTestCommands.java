package io.github.ozozorz.aimaid;

import java.util.List;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

/** 仅存在于测试模组；策略对象本身仍不保存单只女仆的数据。 */
public class CoreTestCommands implements ModInitializer {
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Component> MESSAGE =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(AIMaid.id("test_message"));
    public static final MaidCommand STATUS = new MaidCommand() {
        @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) { return List.of(Activity.IDLE); }
        @Override public void onSelected(AiMaidEntity maid) { append(maid, "S"); }
        @Override public void onDeselected(AiMaidEntity maid) { append(maid, "D"); }
        @Override public Component getStatus(AiMaidEntity maid) { return maid.getAttachedOrElse(MESSAGE, maid.getDisplayName()).copy(); }
    };
    public static final MaidCommand HIDDEN = new MaidCommand() {
        @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) { return List.of(Activity.IDLE); }
        @Override public boolean isVisibleInCommandMenu() { return false; }
    };
    public static final MaidCommand DENIED = new MaidCommand() {
        @Override public List<Activity> getActivityCandidates(AiMaidEntity maid) { return List.of(Activity.IDLE); }
        @Override public boolean canPlayerSelect(AiMaidEntity maid, Player player) { return false; }
    };
    private static void append(AiMaidEntity maid, String suffix) {
        maid.setCustomName(Component.literal((maid.hasCustomName() ? maid.getCustomName().getString() : "") + suffix));
    }
    @Override public void onInitialize() {
        Registry.register(ModBuiltInRegistries.MAID_COMMAND, AIMaid.id("test_status"), STATUS);
        Registry.register(ModBuiltInRegistries.MAID_COMMAND, AIMaid.id("test_hidden"), HIDDEN);
        Registry.register(ModBuiltInRegistries.MAID_COMMAND, AIMaid.id("test_denied"), DENIED);
    }
}
