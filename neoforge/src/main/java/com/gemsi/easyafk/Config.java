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

    // Message Customization
    private static final ModConfigSpec.ConfigValue<String> MSG_AFK_ENTER = BUILDER
            .comment("",
                    "===========================================",
                    "MESSAGE CUSTOMIZATION",
                    "===========================================",
                    "All messages support color formatting:",
                    "  - Minecraft codes: &c, &4, &l (bold), &n (underline), etc.",
                    "  - Hex colors: &#RRGGBB (e.g., &#FF5050)",
                    "  - Gradients: <gradient:#FF0000:#0000FF>text</gradient>",
                    "  - Rainbow: <rainbow>text</rainbow>",
                    "  - Use {player} for player name in broadcast messages",
                    "===========================================",
                    "",
                    "Message when player enters AFK")
            .define("messages.afkEnter", "&c{player} is now AFK.");

    private static final ModConfigSpec.ConfigValue<String> MSG_AFK_EXIT = BUILDER
            .comment("Message when player exits AFK")
            .define("messages.afkExit", "&a{player} is no longer AFK.");

    private static final ModConfigSpec.ConfigValue<String> MSG_TITLE_AFK = BUILDER
            .comment("Title text shown to AFK player")
            .define("messages.titleAfk", "&cYou are &4AFK");

    private static final ModConfigSpec.ConfigValue<String> MSG_SUBTITLE_AFK = BUILDER
            .comment("Subtitle text shown to AFK player")
            .define("messages.subtitleAfk", "&#EDEDEDType /afk to exit AFK mode");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_DO_WHILE_AFK = BUILDER
            .comment("Message shown when trying to perform action while AFK")
            .define("messages.cannotDoWhileAfk", "&cYou cannot do this while AFK!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_FALLING = BUILDER
            .comment("Message when trying to go AFK while falling")
            .define("messages.cannotAfkFalling", "&cYou cannot go into AFK whilst falling!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_JUMPING = BUILDER
            .comment("Message when trying to go AFK while jumping")
            .define("messages.cannotAfkJumping", "&cYou cannot go into AFK whilst jumping!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_COMBAT = BUILDER
            .comment("Message when trying to go AFK while in combat")
            .define("messages.cannotAfkCombat", "&cYou cannot go into AFK whilst you are in combat!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_DAMAGE = BUILDER
            .comment("Message when trying to go AFK after taking damage")
            .define("messages.cannotAfkDamage", "&cYou can't go AFK! Stay alert, danger is everywhere!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_RIDING = BUILDER
            .comment("Message when trying to go AFK while riding entity")
            .define("messages.cannotAfkRiding", "&cYou cannot go AFK while riding an entity!");

    private static final ModConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_DANGEROUS = BUILDER
            .comment("Message when trying to go AFK in dangerous location")
            .define("messages.cannotAfkDangerous", "&cYou cannot go AFK in a dangerous location!");

    private static final ModConfigSpec.ConfigValue<String> AFK_PREFIX = BUILDER
            .comment("Prefix shown before player name in tab list when AFK")
            .define("messages.afkPrefix", "&7[AFK] ");

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

    // Message strings
    public static String msgAfkEnter;
    public static String msgAfkExit;
    public static String msgTitleAfk;
    public static String msgSubtitleAfk;
    public static String msgCannotDoWhileAfk;
    public static String msgCannotAfkFalling;
    public static String msgCannotAfkJumping;
    public static String msgCannotAfkCombat;
    public static String msgCannotAfkDamage;
    public static String msgCannotAfkRiding;
    public static String msgCannotAfkDangerous;
    public static String afkPrefix;
    public static int colorAfkPlayerName;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Loading) {
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

            // Load messages
            msgAfkEnter = MSG_AFK_ENTER.get();
            msgAfkExit = MSG_AFK_EXIT.get();
            msgTitleAfk = MSG_TITLE_AFK.get();
            msgSubtitleAfk = MSG_SUBTITLE_AFK.get();
            msgCannotDoWhileAfk = MSG_CANNOT_DO_WHILE_AFK.get();
            msgCannotAfkFalling = MSG_CANNOT_AFK_FALLING.get();
            msgCannotAfkJumping = MSG_CANNOT_AFK_JUMPING.get();
            msgCannotAfkCombat = MSG_CANNOT_AFK_COMBAT.get();
            msgCannotAfkDamage = MSG_CANNOT_AFK_DAMAGE.get();
            msgCannotAfkRiding = MSG_CANNOT_AFK_RIDING.get();
            msgCannotAfkDangerous = MSG_CANNOT_AFK_DANGEROUS.get();
            afkPrefix = AFK_PREFIX.get();

            // Only keep player name color as it's not part of the color-coded prefix string
            colorAfkPlayerName = parseColor("#FFFFFF"); // Default white
        }
    }

    /**
     * Parse a hex color string (#RRGGBB) to an RGB integer
     */
    private static int parseColor(String hexColor) {
        return ColorParser.parseColor(hexColor);
    }
}