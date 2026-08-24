package io.github.ozozorz.aimaid.registries;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.entity.maidcommand.MaidCommand;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class ModBuiltInRegistries {

    // 这个 ResourceKey 指向的东西，是一个存放 MaidCommand 的 Registry。也就是 ai_maid:maid_command -> Registry<MaidCommand>
    public static final ResourceKey<Registry<MaidCommand>> MAID_COMMAND_KEY = ResourceKey
            .createRegistryKey(Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, "maid_command"));

    // .attribute(RegistryAttribute.SYNCED) 表示我们希望这个自定义静态 Registry 参与客户端 Registry 同步。
    public static final Registry<MaidCommand> MAID_COMMAND = FabricRegistryBuilder.create(MAID_COMMAND_KEY)
            .attribute(RegistryAttribute.SYNCED).buildAndRegister();

    private ModBuiltInRegistries() {
    }

    public static void initialize() {

    }
}
