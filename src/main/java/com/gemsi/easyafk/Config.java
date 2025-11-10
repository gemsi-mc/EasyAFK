package com.gemsi.easyafk;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = EasyAFK.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // AFK Settings
    private static final ModConfigSpec.IntValue AFK_TIMEOUT = BUILDER
            .comment("Time in seconds before a player is automatically marked as AFK (default: 300)")
            .defineInRange("afkTimeout", 300, 10, 3600);

    private static final ModConfigSpec.IntValue AUTO_KICK_TIMEOUT = BUILDER
            .comment("Time in seconds before an AFK player is kicked (0 to disable, default: 1800)")
            .defineInRange("autoKickTimeout", 1800, 0, 7200);

    private static final ModConfigSpec.DoubleValue MOVEMENT_THRESHOLD = BUILDER
            .comment("Minimum movement distance to reset AFK timer (default: 0.1)")
            .defineInRange("movementThreshold", 0.1, 0.01, 5.0);

    // Combat Settings
    private static final ModConfigSpec.IntValue COMBAT_COOLDOWN = BUILDER
            .comment("Combat cooldown duration in milliseconds (default: 15000)")
            .defineInRange("combatCooldown", 15000, 1000, 60000);

    private static final ModConfigSpec.IntValue DAMAGE_COOLDOWN = BUILDER
            .comment("Damage cooldown duration in milliseconds (default: 15000)")
            .defineInRange("damageCooldown", 15000, 1000, 60000);

    // Message Settings
    private static final ModConfigSpec.BooleanValue BROADCAST_AFK_MESSAGES = BUILDER
            .comment("Whether to broadcast when players go AFK or return (default: true)")
            .define("broadcastAFKMessages", true);

    private static final ModConfigSpec.BooleanValue SHOW_AFK_IN_TAB = BUILDER
            .comment("Whether to show [AFK] prefix in tab list (default: true)")
            .define("showAFKInTab", true);

    private static final ModConfigSpec.BooleanValue SEND_KICK_WARNING = BUILDER
            .comment("Whether to warn players before kicking them for AFK (default: true)")
            .define("sendKickWarning", true);

    private static final ModConfigSpec.IntValue KICK_WARNING_TIME = BUILDER
            .comment("Seconds before kick to send warning (default: 60)")
            .defineInRange("kickWarningTime", 60, 10, 300);

    // Protection Settings
    private static final ModConfigSpec.BooleanValue PREVENT_FALL_DAMAGE = BUILDER
            .comment("Whether AFK players are protected from fall damage (default: true)")
            .define("preventFallDamage", true);

    private static final ModConfigSpec.BooleanValue FLOAT_ON_WATER = BUILDER
            .comment("Whether AFK players float on water to prevent drowning (default: true)")
            .define("floatOnWater", true);

    private static final ModConfigSpec.BooleanValue FREEZE_HUNGER = BUILDER
            .comment("Whether to freeze hunger while AFK (default: true)")
            .define("freezeHunger", true);

    private static final ModConfigSpec.BooleanValue FREEZE_HEALTH = BUILDER
            .comment("Whether to freeze health while AFK (default: true)")
            .define("freezeHealth", true);

    private static final ModConfigSpec.BooleanValue FREEZE_POTION_EFFECTS = BUILDER
            .comment("Whether to freeze potion effects while AFK (default: true)")
            .define("freezePotionEffects", true);

    // Permission Settings
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXEMPT_PLAYERS = BUILDER
            .comment("List of player UUIDs exempt from auto-AFK (admins, etc.)")
            .defineList("exemptPlayers", List.of(), obj -> obj instanceof String);

    private static final ModConfigSpec.IntValue MIN_PERMISSION_LEVEL = BUILDER
            .comment("Minimum permission level to be exempt from auto-AFK (0-4, 0 disables, default: 3)")
            .defineInRange("minPermissionLevel", 3, 0, 4);

    static final ModConfigSpec SPEC = BUILDER.build();

    // Static getters for easy access
    public static int afkTimeout;
    public static int autoKickTimeout;
    public static double movementThreshold;
    public static int combatCooldown;
    public static int damageCooldown;
    public static boolean broadcastAFKMessages;
    public static boolean showAFKInTab;
    public static boolean sendKickWarning;
    public static int kickWarningTime;
    public static boolean preventFallDamage;
    public static boolean floatOnWater;
    public static boolean freezeHunger;
    public static boolean freezeHealth;
    public static boolean freezePotionEffects;
    public static List<? extends String> exemptPlayers;
    public static int minPermissionLevel;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        afkTimeout = AFK_TIMEOUT.get();
        autoKickTimeout = AUTO_KICK_TIMEOUT.get();
        movementThreshold = MOVEMENT_THRESHOLD.get();
        combatCooldown = COMBAT_COOLDOWN.get();
        damageCooldown = DAMAGE_COOLDOWN.get();
        broadcastAFKMessages = BROADCAST_AFK_MESSAGES.get();
        showAFKInTab = SHOW_AFK_IN_TAB.get();
        sendKickWarning = SEND_KICK_WARNING.get();
        kickWarningTime = KICK_WARNING_TIME.get();
        preventFallDamage = PREVENT_FALL_DAMAGE.get();
        floatOnWater = FLOAT_ON_WATER.get();
        freezeHunger = FREEZE_HUNGER.get();
        freezeHealth = FREEZE_HEALTH.get();
        freezePotionEffects = FREEZE_POTION_EFFECTS.get();
        exemptPlayers = EXEMPT_PLAYERS.get();
        minPermissionLevel = MIN_PERMISSION_LEVEL.get();
    }
}
