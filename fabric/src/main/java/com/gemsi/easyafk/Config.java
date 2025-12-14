package com.gemsi.easyafk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "easyafk.json");

    // AFK Settings
    public static int afkTimeout = 300;
    public static int autoKickTimeout = 1800;
    public static double movementThreshold = 0.1;

    // Combat Settings
    public static int combatCooldown = 15000;
    public static int damageCooldown = 15000;

    // Message Settings
    public static boolean broadcastAFKMessages = true;
    public static boolean showAFKInTab = true;
    public static boolean sendKickWarning = true;
    public static int kickWarningTime = 60;

    // Protection Settings
    public static boolean preventFallDamage = true;
    public static boolean floatOnWater = true;
    public static boolean freezeHunger = true;
    public static boolean freezeHealth = true;
    public static boolean freezePotionEffects = true;

    // Permission Settings
    public static List<String> exemptPlayers = new ArrayList<>();
    public static int minPermissionLevel = 3;

    // Message Customization
    public static String msgAfkEnter = "&c{player} is now AFK.";
    public static String msgAfkExit = "&a{player} is no longer AFK.";
    public static String msgTitleAfk = "&cYou are &4AFK";
    public static String msgSubtitleAfk = "&#EDEDEDType /afk to exit AFK mode";
    public static String msgCannotDoWhileAfk = "&cYou cannot do this while AFK!";
    public static String msgCannotAfkFalling = "&cYou cannot go into AFK whilst falling!";
    public static String msgCannotAfkJumping = "&cYou cannot go into AFK whilst jumping!";
    public static String msgCannotAfkCombat = "&cYou cannot go into AFK whilst you are in combat!";
    public static String msgCannotAfkDamage = "&cYou can't go AFK! Stay alert, danger is everywhere!";
    public static String msgCannotAfkRiding = "&cYou cannot go AFK while riding an entity!";
    public static String msgCannotAfkDangerous = "&cYou cannot go AFK in a dangerous location!";
    public static String afkPrefix = "&7[AFK] ";
    public static int colorAfkPlayerName = 0xFFFFFF;

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    applyConfig(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            ConfigData data = new ConfigData();
            data.afkTimeout = afkTimeout;
            data.autoKickTimeout = autoKickTimeout;
            data.movementThreshold = movementThreshold;
            data.combatCooldown = combatCooldown;
            data.damageCooldown = damageCooldown;
            data.broadcastAFKMessages = broadcastAFKMessages;
            data.showAFKInTab = showAFKInTab;
            data.sendKickWarning = sendKickWarning;
            data.kickWarningTime = kickWarningTime;
            data.preventFallDamage = preventFallDamage;
            data.floatOnWater = floatOnWater;
            data.freezeHunger = freezeHunger;
            data.freezeHealth = freezeHealth;
            data.freezePotionEffects = freezePotionEffects;
            data.exemptPlayers = exemptPlayers;
            data.minPermissionLevel = minPermissionLevel;
            data.msgAfkEnter = msgAfkEnter;
            data.msgAfkExit = msgAfkExit;
            data.msgTitleAfk = msgTitleAfk;
            data.msgSubtitleAfk = msgSubtitleAfk;
            data.msgCannotDoWhileAfk = msgCannotDoWhileAfk;
            data.msgCannotAfkFalling = msgCannotAfkFalling;
            data.msgCannotAfkJumping = msgCannotAfkJumping;
            data.msgCannotAfkCombat = msgCannotAfkCombat;
            data.msgCannotAfkDamage = msgCannotAfkDamage;
            data.msgCannotAfkRiding = msgCannotAfkRiding;
            data.msgCannotAfkDangerous = msgCannotAfkDangerous;
            data.afkPrefix = afkPrefix;

            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void applyConfig(ConfigData data) {
        afkTimeout = data.afkTimeout;
        autoKickTimeout = data.autoKickTimeout;
        movementThreshold = data.movementThreshold;
        combatCooldown = data.combatCooldown;
        damageCooldown = data.damageCooldown;
        broadcastAFKMessages = data.broadcastAFKMessages;
        showAFKInTab = data.showAFKInTab;
        sendKickWarning = data.sendKickWarning;
        kickWarningTime = data.kickWarningTime;
        preventFallDamage = data.preventFallDamage;
        floatOnWater = data.floatOnWater;
        freezeHunger = data.freezeHunger;
        freezeHealth = data.freezeHealth;
        freezePotionEffects = data.freezePotionEffects;
        exemptPlayers = data.exemptPlayers != null ? data.exemptPlayers : new ArrayList<>();
        minPermissionLevel = data.minPermissionLevel;
        msgAfkEnter = data.msgAfkEnter;
        msgAfkExit = data.msgAfkExit;
        msgTitleAfk = data.msgTitleAfk;
        msgSubtitleAfk = data.msgSubtitleAfk;
        msgCannotDoWhileAfk = data.msgCannotDoWhileAfk;
        msgCannotAfkFalling = data.msgCannotAfkFalling;
        msgCannotAfkJumping = data.msgCannotAfkJumping;
        msgCannotAfkCombat = data.msgCannotAfkCombat;
        msgCannotAfkDamage = data.msgCannotAfkDamage;
        msgCannotAfkRiding = data.msgCannotAfkRiding;
        msgCannotAfkDangerous = data.msgCannotAfkDangerous;
        afkPrefix = data.afkPrefix;
        colorAfkPlayerName = ColorParser.parseColor("#FFFFFF");
    }

    private static class ConfigData {
        int afkTimeout = 300;
        int autoKickTimeout = 1800;
        double movementThreshold = 0.1;
        int combatCooldown = 15000;
        int damageCooldown = 15000;
        boolean broadcastAFKMessages = true;
        boolean showAFKInTab = true;
        boolean sendKickWarning = true;
        int kickWarningTime = 60;
        boolean preventFallDamage = true;
        boolean floatOnWater = true;
        boolean freezeHunger = true;
        boolean freezeHealth = true;
        boolean freezePotionEffects = true;
        List<String> exemptPlayers = new ArrayList<>();
        int minPermissionLevel = 3;
        String msgAfkEnter = "&c{player} is now AFK.";
        String msgAfkExit = "&a{player} is no longer AFK.";
        String msgTitleAfk = "&cYou are &4AFK";
        String msgSubtitleAfk = "&#EDEDEDType /afk to exit AFK mode";
        String msgCannotDoWhileAfk = "&cYou cannot do this while AFK!";
        String msgCannotAfkFalling = "&cYou cannot go into AFK whilst falling!";
        String msgCannotAfkJumping = "&cYou cannot go into AFK whilst jumping!";
        String msgCannotAfkCombat = "&cYou cannot go into AFK whilst you are in combat!";
        String msgCannotAfkDamage = "&cYou can't go AFK! Stay alert, danger is everywhere!";
        String msgCannotAfkRiding = "&cYou cannot go AFK while riding an entity!";
        String msgCannotAfkDangerous = "&cYou cannot go AFK in a dangerous location!";
        String afkPrefix = "&7[AFK] ";
    }
}
