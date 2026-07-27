package com.gemsi.easyafk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The single description of every EasyAFK setting: its config path, its type, its
 * default and its bounds, plus how to read and write it on {@link Config}.
 *
 * <p>Each loader's config service is driven entirely by this table, so a new setting is
 * added in exactly one place. {@code ConfigSchemaTest} asserts the table stays in sync
 * with {@link Config} in both directions.
 *
 * <p>Deliberately free of Minecraft types so it stays unit testable.
 */
public final class ConfigSchema {

    public enum Type {
        BOOL, INT, DOUBLE, STRING, STRING_LIST, COLOR
    }

    /**
     * @param field   name of the backing {@link Config} field, used only by the drift test
     * @param path    dotted path written to the config file
     * @param min     lower bound for INT/DOUBLE, ignored otherwise
     * @param max     upper bound for INT/DOUBLE, ignored otherwise
     * @param comment lines written above the entry in file formats that support comments
     */
    public record Entry(String field,
                        String path,
                        Type type,
                        Object defaultValue,
                        double min,
                        double max,
                        List<String> comment,
                        Supplier<Object> getter,
                        Consumer<Object> setter) {

        public int defaultInt() {
            return (Integer) defaultValue;
        }

        public double defaultDouble() {
            return (Double) defaultValue;
        }

        public boolean defaultBool() {
            return (Boolean) defaultValue;
        }

        public String defaultString() {
            return (String) defaultValue;
        }

        @SuppressWarnings("unchecked")
        public List<String> defaultStringList() {
            return (List<String>) defaultValue;
        }
    }

    private static final String MESSAGE_HEADER_COMMENT = String.join("\n",
            "===========================================",
            "MESSAGE CUSTOMIZATION",
            "===========================================",
            "All messages support color formatting:",
            "  - Minecraft codes: &c, &4, &l (bold), &n (underline), etc.",
            "  - Hex colors: &#RRGGBB (e.g., &#FF5050)",
            "  - Gradients: <gradient:#FF0000:#0000FF>text</gradient>",
            "  - Rainbow: <rainbow>text</rainbow>",
            "  - Use {player} for player name in broadcast messages",
            "===========================================");

    private static final List<Entry> ENTRIES = build();

    private ConfigSchema() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * Validates a raw value read from a config file and converts it to the type the
     * entry's setter expects, clamping numbers to the entry's range.
     *
     * @throws IllegalArgumentException if the value cannot represent this entry's type
     */
    public static Object coerce(Entry entry, Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("'" + entry.path() + "' has no value");
        }
        return switch (entry.type()) {
            case BOOL -> requireType(entry, raw, Boolean.class);
            case INT -> (int) Math.round(clamp(requireType(entry, raw, Number.class).doubleValue(), entry));
            case DOUBLE -> clamp(requireType(entry, raw, Number.class).doubleValue(), entry);
            case STRING, COLOR -> requireType(entry, raw, String.class);
            case STRING_LIST -> requireType(entry, raw, List.class).stream()
                    .map(String::valueOf)
                    .toList();
        };
    }

    private static <T> T requireType(Entry entry, Object raw, Class<T> expected) {
        if (!expected.isInstance(raw)) {
            throw new IllegalArgumentException("'" + entry.path() + "' expects "
                    + expected.getSimpleName().toLowerCase() + " but got: " + raw);
        }
        return expected.cast(raw);
    }

    private static double clamp(double value, Entry entry) {
        return Math.max(entry.min(), Math.min(entry.max(), value));
    }

    /** Resets every setting on {@link Config} back to its schema default. */
    public static void applyDefaults() {
        for (Entry entry : ENTRIES) {
            entry.setter().accept(entry.defaultValue());
        }
    }

    private static List<Entry> build() {
        List<Entry> entries = new ArrayList<>();

        // AFK Settings
        entries.add(intEntry("afkTimeout", "afkTimeout", 300, 10, 3600,
                "Time in seconds before a player is automatically marked as AFK (default: 300)",
                () -> Config.afkTimeout, v -> Config.afkTimeout = v));
        entries.add(intEntry("autoKickTimeout", "autoKickTimeout", 1800, 0, 7200,
                "Time in seconds before an AFK player is kicked (0 to disable, default: 1800)",
                () -> Config.autoKickTimeout, v -> Config.autoKickTimeout = v));
        entries.add(doubleEntry("movementThreshold", "movementThreshold", 0.1, 0.01, 5.0,
                "Minimum movement distance to reset AFK timer (default: 0.1)",
                () -> Config.movementThreshold, v -> Config.movementThreshold = v));
        entries.add(boolEntry("exitAFKOnJump", "exitAFKOnJump", true,
                "Whether upward movement takes a player out of AFK (default: true). Turn this off if "
                        + "items or mods push players upwards and knock them out of AFK on their own.",
                () -> Config.exitAFKOnJump, v -> Config.exitAFKOnJump = v));

        // Networking
        entries.add(boolEntry("checkForUpdates", "checkForUpdates", true,
                "Whether to ask GitHub for the latest EasyAFK version on server start (default: true). "
                        + "Turn this off to stop the mod making any outbound network request.",
                () -> Config.checkForUpdates, v -> Config.checkForUpdates = v));

        // Combat Settings
        entries.add(intEntry("combatCooldown", "combatCooldown", 15000, 1000, 60000,
                "Combat cooldown duration in milliseconds (default: 15000)",
                () -> Config.combatCooldown, v -> Config.combatCooldown = v));
        entries.add(intEntry("damageCooldown", "damageCooldown", 15000, 1000, 60000,
                "Damage cooldown duration in milliseconds (default: 15000)",
                () -> Config.damageCooldown, v -> Config.damageCooldown = v));

        // Message Settings
        entries.add(boolEntry("broadcastAFKMessages", "broadcastAFKMessages", true,
                "Whether to broadcast when players go AFK or return (default: true)",
                () -> Config.broadcastAFKMessages, v -> Config.broadcastAFKMessages = v));
        entries.add(boolEntry("showAFKInTab", "showAFKInTab", true,
                "Whether to show [AFK] prefix in tab list (default: true)",
                () -> Config.showAFKInTab, v -> Config.showAFKInTab = v));
        entries.add(boolEntry("showAFKDurationInTab", "showAFKDurationInTab", true,
                "Whether to show how long a player has been AFK in the tab list (default: true)",
                () -> Config.showAFKDurationInTab, v -> Config.showAFKDurationInTab = v));
        entries.add(boolEntry("sendKickWarning", "sendKickWarning", true,
                "Whether to warn players before kicking them for AFK (default: true)",
                () -> Config.sendKickWarning, v -> Config.sendKickWarning = v));
        entries.add(intEntry("kickWarningTime", "kickWarningTime", 60, 10, 300,
                "Seconds before kick to send warning (default: 60)",
                () -> Config.kickWarningTime, v -> Config.kickWarningTime = v));

        // Protection Settings
        entries.add(boolEntry("invulnerableWhileAFK", "invulnerableWhileAFK", true,
                "Whether AFK players take no damage at all (default: true). Turn this off to keep "
                        + "AFK players vulnerable to mobs, lava and drowning; preventFallDamage still applies.",
                () -> Config.invulnerableWhileAFK, v -> Config.invulnerableWhileAFK = v));
        entries.add(boolEntry("preventFallDamage", "preventFallDamage", true,
                "Whether AFK players are protected from fall damage (default: true)",
                () -> Config.preventFallDamage, v -> Config.preventFallDamage = v));
        entries.add(boolEntry("floatOnWater", "floatOnWater", true,
                "Whether AFK players float on water to prevent drowning (default: true)",
                () -> Config.floatOnWater, v -> Config.floatOnWater = v));
        entries.add(boolEntry("freezeHunger", "freezeHunger", true,
                "Whether to freeze hunger while AFK (default: true)",
                () -> Config.freezeHunger, v -> Config.freezeHunger = v));
        entries.add(boolEntry("freezeHealth", "freezeHealth", true,
                "Whether to freeze health while AFK (default: true)",
                () -> Config.freezeHealth, v -> Config.freezeHealth = v));
        entries.add(boolEntry("freezePotionEffects", "freezePotionEffects", true,
                "Whether to freeze potion effects while AFK (default: true)",
                () -> Config.freezePotionEffects, v -> Config.freezePotionEffects = v));

        // Permission Settings
        entries.add(stringListEntry("exemptPlayers", "exemptPlayers",
                "List of player UUIDs exempt from auto-AFK (admins, etc.)",
                () -> Config.exemptPlayers, v -> Config.exemptPlayers = v));
        entries.add(intEntry("minPermissionLevel", "minPermissionLevel", 3, 0, 4,
                "Permission level at or above which players are exempt from auto-AFK (0-4, 0 disables, default: 3)",
                () -> Config.minPermissionLevel, v -> Config.minPermissionLevel = v));

        // Message Customization
        entries.add(stringEntry("msgAfkEnter", "messages.afkEnter", "&c{player} is now AFK.",
                MESSAGE_HEADER_COMMENT + "\n\nMessage when player enters AFK",
                () -> Config.msgAfkEnter, v -> Config.msgAfkEnter = v));
        entries.add(stringEntry("msgAfkExit", "messages.afkExit", "&a{player} is no longer AFK.",
                "Message when player exits AFK",
                () -> Config.msgAfkExit, v -> Config.msgAfkExit = v));
        entries.add(stringEntry("msgTitleAfk", "messages.titleAfk", "&cYou are &4AFK",
                "Title text shown to AFK player",
                () -> Config.msgTitleAfk, v -> Config.msgTitleAfk = v));
        entries.add(stringEntry("msgSubtitleAfk", "messages.subtitleAfk", "&#EDEDEDType /afk to exit AFK mode",
                "Subtitle text shown to AFK player",
                () -> Config.msgSubtitleAfk, v -> Config.msgSubtitleAfk = v));
        entries.add(stringEntry("msgCannotDoWhileAfk", "messages.cannotDoWhileAfk", "&cYou cannot do this while AFK!",
                "Message shown when trying to perform action while AFK",
                () -> Config.msgCannotDoWhileAfk, v -> Config.msgCannotDoWhileAfk = v));
        entries.add(stringEntry("msgCannotAfkFalling", "messages.cannotAfkFalling", "&cYou cannot go into AFK whilst falling!",
                "Message when trying to go AFK while falling",
                () -> Config.msgCannotAfkFalling, v -> Config.msgCannotAfkFalling = v));
        entries.add(stringEntry("msgCannotAfkJumping", "messages.cannotAfkJumping", "&cYou cannot go into AFK whilst jumping!",
                "Message when trying to go AFK while jumping",
                () -> Config.msgCannotAfkJumping, v -> Config.msgCannotAfkJumping = v));
        entries.add(stringEntry("msgCannotAfkCombat", "messages.cannotAfkCombat", "&cYou cannot go into AFK whilst you are in combat!",
                "Message when trying to go AFK while in combat",
                () -> Config.msgCannotAfkCombat, v -> Config.msgCannotAfkCombat = v));
        entries.add(stringEntry("msgCannotAfkDamage", "messages.cannotAfkDamage", "&cYou can't go AFK! Stay alert, danger is everywhere!",
                "Message when trying to go AFK after taking damage",
                () -> Config.msgCannotAfkDamage, v -> Config.msgCannotAfkDamage = v));
        entries.add(stringEntry("msgCannotAfkRiding", "messages.cannotAfkRiding", "&cYou cannot go AFK while riding an entity!",
                "Message when trying to go AFK while riding entity",
                () -> Config.msgCannotAfkRiding, v -> Config.msgCannotAfkRiding = v));
        entries.add(stringEntry("msgCannotAfkDangerous", "messages.cannotAfkDangerous", "&cYou cannot go AFK in a dangerous location!",
                "Message when trying to go AFK in dangerous location",
                () -> Config.msgCannotAfkDangerous, v -> Config.msgCannotAfkDangerous = v));
        entries.add(stringEntry("msgKickWarning", "messages.kickWarning",
                "&eYou will be kicked for being AFK in {seconds} seconds!",
                "Warning sent before an AFK kick; {seconds} is replaced with the time remaining",
                () -> Config.msgKickWarning, v -> Config.msgKickWarning = v));
        entries.add(stringEntry("msgKicked", "messages.kicked", "&cYou have been kicked for being AFK too long.",
                "Disconnect reason shown when a player is kicked for being AFK",
                () -> Config.msgKicked, v -> Config.msgKicked = v));
        entries.add(stringEntry("afkPrefix", "messages.afkPrefix", "&7[AFK] ",
                "Prefix shown before player name in tab list when AFK",
                () -> Config.afkPrefix, v -> Config.afkPrefix = v));
        entries.add(stringEntry("afkDurationFormat", "messages.afkDurationFormat", "&7 ({time})",
                "Format for the AFK duration in the tab list; {time} is replaced with the elapsed time (e.g. 5m 12s)",
                () -> Config.afkDurationFormat, v -> Config.afkDurationFormat = v));
        entries.add(colorEntry("colorAfkPlayerName", "messages.colorAfkPlayerName", "#FFFFFF",
                "Colour of the player's own name in the tab list while AFK, as #RRGGBB",
                () -> Config.colorAfkPlayerName, v -> Config.colorAfkPlayerName = v));

        return List.copyOf(entries);
    }

    private static Entry boolEntry(String field, String path, boolean def, String comment,
                                   Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new Entry(field, path, Type.BOOL, def, 0, 0, comment(comment),
                getter::get, v -> setter.accept((Boolean) v));
    }

    private static Entry intEntry(String field, String path, int def, int min, int max, String comment,
                                  Supplier<Integer> getter, Consumer<Integer> setter) {
        return new Entry(field, path, Type.INT, def, min, max, comment(comment),
                getter::get, v -> setter.accept(((Number) v).intValue()));
    }

    private static Entry doubleEntry(String field, String path, double def, double min, double max, String comment,
                                     Supplier<Double> getter, Consumer<Double> setter) {
        return new Entry(field, path, Type.DOUBLE, def, min, max, comment(comment),
                getter::get, v -> setter.accept(((Number) v).doubleValue()));
    }

    private static Entry stringEntry(String field, String path, String def, String comment,
                                     Supplier<String> getter, Consumer<String> setter) {
        return new Entry(field, path, Type.STRING, def, 0, 0, comment(comment),
                getter::get, v -> setter.accept((String) v));
    }

    private static Entry stringListEntry(String field, String path, String comment,
                                         Supplier<List<? extends String>> getter,
                                         Consumer<List<? extends String>> setter) {
        return new Entry(field, path, Type.STRING_LIST, List.<String>of(), 0, 0, comment(comment),
                getter::get, v -> setter.accept(asStringList(v)));
    }

    /**
     * Stored in the config file as a {@code #RRGGBB} string but held on {@link Config} as a
     * packed RGB int, which is what the text components need.
     */
    private static Entry colorEntry(String field, String path, String def, String comment,
                                    Supplier<Integer> getter, Consumer<Integer> setter) {
        return new Entry(field, path, Type.COLOR, def, 0, 0, comment(comment),
                () -> HexColor.format(getter.get()),
                v -> setter.accept(HexColor.parse((String) v)));
    }

    @SuppressWarnings("unchecked")
    private static List<? extends String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<?>) value).stream().map(String::valueOf).map(s -> (String) s).toList();
    }

    private static List<String> comment(String comment) {
        return List.of(comment.split("\n"));
    }
}
