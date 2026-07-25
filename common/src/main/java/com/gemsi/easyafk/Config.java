package com.gemsi.easyafk;

import com.gemsi.easyafk.platform.Services;

import java.util.ArrayList;
import java.util.List;

public class Config {

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
    public static boolean showAFKDurationInTab = true;
    public static boolean sendKickWarning = true;
    public static int kickWarningTime = 60;

    // Protection Settings
    public static boolean invulnerableWhileAFK = true;
    public static boolean preventFallDamage = true;
    public static boolean floatOnWater = true;
    public static boolean freezeHunger = true;
    public static boolean freezeHealth = true;
    public static boolean freezePotionEffects = true;

    // Permission Settings
    public static List<? extends String> exemptPlayers = new ArrayList<>();
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
    public static String msgKickWarning = "&eYou will be kicked for being AFK in {seconds} seconds!";
    public static String msgKicked = "&cYou have been kicked for being AFK too long.";
    public static String afkPrefix = "&7[AFK] ";
    public static String afkDurationFormat = "&7 ({time})";
    public static int colorAfkPlayerName = 0xFFFFFF;

    public static void load() {
        Services.CONFIG.load();
    }
}
