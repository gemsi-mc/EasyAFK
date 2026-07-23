package com.gemsi.easyafk.platform;

import com.gemsi.easyafk.ColorParser;
import com.gemsi.easyafk.Config;
import com.gemsi.easyafk.platform.services.IConfigService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FabricConfigService implements IConfigService {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "easyafk.json");

    @Override
    public void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    applyConfig(data);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to read EasyAFK config from {}", CONFIG_FILE, e);
            }
        } else {
            save();
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath(), StandardCharsets.UTF_8)) {
            ConfigData data = new ConfigData();
            data.afkTimeout = Config.afkTimeout;
            data.autoKickTimeout = Config.autoKickTimeout;
            data.movementThreshold = Config.movementThreshold;
            data.combatCooldown = Config.combatCooldown;
            data.damageCooldown = Config.damageCooldown;
            data.broadcastAFKMessages = Config.broadcastAFKMessages;
            data.showAFKInTab = Config.showAFKInTab;
            data.sendKickWarning = Config.sendKickWarning;
            data.kickWarningTime = Config.kickWarningTime;
            data.preventFallDamage = Config.preventFallDamage;
            data.floatOnWater = Config.floatOnWater;
            data.freezeHunger = Config.freezeHunger;
            data.freezeHealth = Config.freezeHealth;
            data.freezePotionEffects = Config.freezePotionEffects;
            data.exemptPlayers = new ArrayList<>(Config.exemptPlayers);
            data.minPermissionLevel = Config.minPermissionLevel;
            data.msgAfkEnter = Config.msgAfkEnter;
            data.msgAfkExit = Config.msgAfkExit;
            data.msgTitleAfk = Config.msgTitleAfk;
            data.msgSubtitleAfk = Config.msgSubtitleAfk;
            data.msgCannotDoWhileAfk = Config.msgCannotDoWhileAfk;
            data.msgCannotAfkFalling = Config.msgCannotAfkFalling;
            data.msgCannotAfkJumping = Config.msgCannotAfkJumping;
            data.msgCannotAfkCombat = Config.msgCannotAfkCombat;
            data.msgCannotAfkDamage = Config.msgCannotAfkDamage;
            data.msgCannotAfkRiding = Config.msgCannotAfkRiding;
            data.msgCannotAfkDangerous = Config.msgCannotAfkDangerous;
            data.afkPrefix = Config.afkPrefix;

            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write EasyAFK config to {}", CONFIG_FILE, e);
        }
    }

    private void applyConfig(ConfigData data) {
        Config.afkTimeout = data.afkTimeout;
        Config.autoKickTimeout = data.autoKickTimeout;
        Config.movementThreshold = data.movementThreshold;
        Config.combatCooldown = data.combatCooldown;
        Config.damageCooldown = data.damageCooldown;
        Config.broadcastAFKMessages = data.broadcastAFKMessages;
        Config.showAFKInTab = data.showAFKInTab;
        Config.sendKickWarning = data.sendKickWarning;
        Config.kickWarningTime = data.kickWarningTime;
        Config.preventFallDamage = data.preventFallDamage;
        Config.floatOnWater = data.floatOnWater;
        Config.freezeHunger = data.freezeHunger;
        Config.freezeHealth = data.freezeHealth;
        Config.freezePotionEffects = data.freezePotionEffects;
        Config.exemptPlayers = data.exemptPlayers != null ? data.exemptPlayers : new ArrayList<>();
        Config.minPermissionLevel = data.minPermissionLevel;
        Config.msgAfkEnter = keepOnNull(data.msgAfkEnter, Config.msgAfkEnter);
        Config.msgAfkExit = keepOnNull(data.msgAfkExit, Config.msgAfkExit);
        Config.msgTitleAfk = keepOnNull(data.msgTitleAfk, Config.msgTitleAfk);
        Config.msgSubtitleAfk = keepOnNull(data.msgSubtitleAfk, Config.msgSubtitleAfk);
        Config.msgCannotDoWhileAfk = keepOnNull(data.msgCannotDoWhileAfk, Config.msgCannotDoWhileAfk);
        Config.msgCannotAfkFalling = keepOnNull(data.msgCannotAfkFalling, Config.msgCannotAfkFalling);
        Config.msgCannotAfkJumping = keepOnNull(data.msgCannotAfkJumping, Config.msgCannotAfkJumping);
        Config.msgCannotAfkCombat = keepOnNull(data.msgCannotAfkCombat, Config.msgCannotAfkCombat);
        Config.msgCannotAfkDamage = keepOnNull(data.msgCannotAfkDamage, Config.msgCannotAfkDamage);
        Config.msgCannotAfkRiding = keepOnNull(data.msgCannotAfkRiding, Config.msgCannotAfkRiding);
        Config.msgCannotAfkDangerous = keepOnNull(data.msgCannotAfkDangerous, Config.msgCannotAfkDangerous);
        Config.afkPrefix = keepOnNull(data.afkPrefix, Config.afkPrefix);
        Config.colorAfkPlayerName = ColorParser.parseColor("#FFFFFF");
    }

    private static String keepOnNull(String value, String fallback) {
        return value != null ? value : fallback;
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
