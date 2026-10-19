package io.github.ozozorz.aimaid.entity.maidcommand;

import io.github.ozozorz.aimaid.AIMaid;
import io.github.ozozorz.aimaid.registries.ModBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class MaidCommands {

    public static final Identifier FREE_ID = id("free");
    public static final Identifier FOLLOW_ID = id("follow");
    public static final Identifier STAY_ID = id("stay");

    public static final MaidCommand FREE = register(FREE_ID, new FreeMaidCommand());
    public static final MaidCommand FOLLOW = register(FOLLOW_ID, new FollowMaidCommand());
    public static final MaidCommand STAY = register(STAY_ID, new StayMaidCommand());

    public static final MaidCommand ATTACK = register(id("attack"), new AttackMaidCommand());
    public static final MaidCommand PLAN = register(id("plan"), new PlanMaidCommand());
    private static final java.util.Map<Identifier, Identifier> LEGACY_IDS = new java.util.HashMap<>();
    static {
        registerLegacyAlias(Identifier.parse("testaddon:guard"), FOLLOW_ID);
        registerLegacyAlias(Identifier.parse("testaddon:attack"), id("attack"));
        registerLegacyAlias(Identifier.parse("testaddon:patrol"), FOLLOW_ID);
    }
    /** 启动注册阶段使用。旧 ID 仅在新命令存在时迁移，否则原样保留。 */
    public static void registerLegacyAlias(Identifier oldId, Identifier newId) {
        if (LEGACY_IDS.putIfAbsent(oldId, newId) != null) throw new IllegalArgumentException("Duplicate command alias: " + oldId);
    }
    public static Identifier migrateLegacyId(Identifier id) {
        Identifier replacement = LEGACY_IDS.get(id);
        return replacement != null && ModBuiltInRegistries.MAID_COMMAND.containsKey(replacement) ? replacement : id;
    }

    private MaidCommands() {
    }

    private static MaidCommand register(Identifier id, MaidCommand maidCommand) {
        return Registry.register(ModBuiltInRegistries.MAID_COMMAND, id, maidCommand);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(AIMaid.MOD_ID, name);
    }

    public static void initialize() {

    }

}
