package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.ColorParser;
import com.gemsi.easyafk.Config;
import com.gemsi.easyafk.EasyAFK;
import com.gemsi.easyafk.platform.services.IConfigService;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = EasyAFK.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeConfigService implements IConfigService {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // AFK Settings
    private static final ForgeConfigSpec.IntValue AFK_TIMEOUT = BUILDER
            .comment("Time in seconds before a player is automatically marked as AFK (default: 300)")
            .defineInRange("afkTimeout", 300, 10, 3600);

    private static final ForgeConfigSpec.IntValue AUTO_KICK_TIMEOUT = BUILDER
            .comment("Time in seconds before an AFK player is kicked (0 to disable, default: 1800)")
            .defineInRange("autoKickTimeout", 1800, 0, 7200);

    private static final ForgeConfigSpec.DoubleValue MOVEMENT_THRESHOLD = BUILDER
            .comment("Minimum movement distance to reset AFK timer (default: 0.1)")
            .defineInRange("movementThreshold", 0.1, 0.01, 5.0);

    // Combat Settings
    private static final ForgeConfigSpec.IntValue COMBAT_COOLDOWN = BUILDER
            .comment("Combat cooldown duration in milliseconds (default: 15000)")
            .defineInRange("combatCooldown", 15000, 1000, 60000);

    private static final ForgeConfigSpec.IntValue DAMAGE_COOLDOWN = BUILDER
            .comment("Damage cooldown duration in milliseconds (default: 15000)")
            .defineInRange("damageCooldown", 15000, 1000, 60000);

    // Message Settings
    private static final ForgeConfigSpec.BooleanValue BROADCAST_AFK_MESSAGES = BUILDER
            .comment("Whether to broadcast when players go AFK or return (default: true)")
            .define("broadcastAFKMessages", true);

    private static final ForgeConfigSpec.BooleanValue SHOW_AFK_IN_TAB = BUILDER
            .comment("Whether to show [AFK] prefix in tab list (default: true)")
            .define("showAFKInTab", true);

    private static final ForgeConfigSpec.BooleanValue SEND_KICK_WARNING = BUILDER
            .comment("Whether to warn players before kicking them for AFK (default: true)")
            .define("sendKickWarning", true);

    private static final ForgeConfigSpec.IntValue KICK_WARNING_TIME = BUILDER
            .comment("Seconds before kick to send warning (default: 60)")
            .defineInRange("kickWarningTime", 60, 10, 300);

    // Protection Settings
    private static final ForgeConfigSpec.BooleanValue PREVENT_FALL_DAMAGE = BUILDER
            .comment("Whether AFK players are protected from fall damage (default: true)")
            .define("preventFallDamage", true);

    private static final ForgeConfigSpec.BooleanValue FLOAT_ON_WATER = BUILDER
            .comment("Whether AFK players float on water to prevent drowning (default: true)")
            .define("floatOnWater", true);

    private static final ForgeConfigSpec.BooleanValue FREEZE_HUNGER = BUILDER
            .comment("Whether to freeze hunger while AFK (default: true)")
            .define("freezeHunger", true);

    private static final ForgeConfigSpec.BooleanValue FREEZE_HEALTH = BUILDER
            .comment("Whether to freeze health while AFK (default: true)")
            .define("freezeHealth", true);

    private static final ForgeConfigSpec.BooleanValue FREEZE_POTION_EFFECTS = BUILDER
            .comment("Whether to freeze potion effects while AFK (default: true)")
            .define("freezePotionEffects", true);

    // Permission Settings
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXEMPT_PLAYERS = BUILDER
            .comment("List of player UUIDs exempt from auto-AFK (admins, etc.)")
            .defineList("exemptPlayers", List.of(), obj -> obj instanceof String);

    private static final ForgeConfigSpec.IntValue MIN_PERMISSION_LEVEL = BUILDER
            .comment("Minimum permission level to be exempt from auto-AFK (0-4, 0 disables, default: 3)")
            .defineInRange("minPermissionLevel", 3, 0, 4);

    // Message Customization
    private static final ForgeConfigSpec.ConfigValue<String> MSG_AFK_ENTER = BUILDER
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

    private static final ForgeConfigSpec.ConfigValue<String> MSG_AFK_EXIT = BUILDER
            .comment("Message when player exits AFK")
            .define("messages.afkExit", "&a{player} is no longer AFK.");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_TITLE_AFK = BUILDER
            .comment("Title text shown to AFK player")
            .define("messages.titleAfk", "&cYou are &4AFK");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_SUBTITLE_AFK = BUILDER
            .comment("Subtitle text shown to AFK player")
            .define("messages.subtitleAfk", "&#EDEDEDType /afk to exit AFK mode");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_DO_WHILE_AFK = BUILDER
            .comment("Message shown when trying to perform action while AFK")
            .define("messages.cannotDoWhileAfk", "&cYou cannot do this while AFK!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_FALLING = BUILDER
            .comment("Message when trying to go AFK while falling")
            .define("messages.cannotAfkFalling", "&cYou cannot go into AFK whilst falling!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_JUMPING = BUILDER
            .comment("Message when trying to go AFK while jumping")
            .define("messages.cannotAfkJumping", "&cYou cannot go into AFK whilst jumping!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_COMBAT = BUILDER
            .comment("Message when trying to go AFK while in combat")
            .define("messages.cannotAfkCombat", "&cYou cannot go into AFK whilst you are in combat!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_DAMAGE = BUILDER
            .comment("Message when trying to go AFK after taking damage")
            .define("messages.cannotAfkDamage", "&cYou can't go AFK! Stay alert, danger is everywhere!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_RIDING = BUILDER
            .comment("Message when trying to go AFK while riding entity")
            .define("messages.cannotAfkRiding", "&cYou cannot go AFK while riding an entity!");

    private static final ForgeConfigSpec.ConfigValue<String> MSG_CANNOT_AFK_DANGEROUS = BUILDER
            .comment("Message when trying to go AFK in dangerous location")
            .define("messages.cannotAfkDangerous", "&cYou cannot go AFK in a dangerous location!");

    private static final ForgeConfigSpec.ConfigValue<String> AFK_PREFIX = BUILDER
            .comment("Prefix shown before player name in tab list when AFK")
            .define("messages.afkPrefix", "&7[AFK] ");

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    @Override
    public void load() {
        // No-op: values are populated by the ModConfigEvent listener below.
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Loading) {
            Config.afkTimeout = AFK_TIMEOUT.get();
            Config.autoKickTimeout = AUTO_KICK_TIMEOUT.get();
            Config.movementThreshold = MOVEMENT_THRESHOLD.get();
            Config.combatCooldown = COMBAT_COOLDOWN.get();
            Config.damageCooldown = DAMAGE_COOLDOWN.get();
            Config.broadcastAFKMessages = BROADCAST_AFK_MESSAGES.get();
            Config.showAFKInTab = SHOW_AFK_IN_TAB.get();
            Config.sendKickWarning = SEND_KICK_WARNING.get();
            Config.kickWarningTime = KICK_WARNING_TIME.get();
            Config.preventFallDamage = PREVENT_FALL_DAMAGE.get();
            Config.floatOnWater = FLOAT_ON_WATER.get();
            Config.freezeHunger = FREEZE_HUNGER.get();
            Config.freezeHealth = FREEZE_HEALTH.get();
            Config.freezePotionEffects = FREEZE_POTION_EFFECTS.get();
            Config.exemptPlayers = EXEMPT_PLAYERS.get();
            Config.minPermissionLevel = MIN_PERMISSION_LEVEL.get();

            Config.msgAfkEnter = MSG_AFK_ENTER.get();
            Config.msgAfkExit = MSG_AFK_EXIT.get();
            Config.msgTitleAfk = MSG_TITLE_AFK.get();
            Config.msgSubtitleAfk = MSG_SUBTITLE_AFK.get();
            Config.msgCannotDoWhileAfk = MSG_CANNOT_DO_WHILE_AFK.get();
            Config.msgCannotAfkFalling = MSG_CANNOT_AFK_FALLING.get();
            Config.msgCannotAfkJumping = MSG_CANNOT_AFK_JUMPING.get();
            Config.msgCannotAfkCombat = MSG_CANNOT_AFK_COMBAT.get();
            Config.msgCannotAfkDamage = MSG_CANNOT_AFK_DAMAGE.get();
            Config.msgCannotAfkRiding = MSG_CANNOT_AFK_RIDING.get();
            Config.msgCannotAfkDangerous = MSG_CANNOT_AFK_DANGEROUS.get();
            Config.afkPrefix = AFK_PREFIX.get();

            Config.colorAfkPlayerName = ColorParser.parseColor("#FFFFFF");
        }
    }
}
