package com.gemsi.easyafk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class VersionChecker {
    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final String VERSION_CHECK_URL = "https://raw.githubusercontent.com/gemsi-mc/EasyAFK/refs/heads/1.21.1/version.txt";

    /**
     * Asks GitHub whether a newer release exists and logs the result.
     *
     * <p>This is the only outbound request EasyAFK makes, so it is gated on
     * {@code checkForUpdates} and does nothing at all when that is off.
     */
    public static void checkForUpdates(String currentVersion) {
        if (!Config.checkForUpdates) {
            LOGGER.debug("Update check skipped (checkForUpdates is disabled).");
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(VERSION_CHECK_URL).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() != 200) {
                    LOGGER.debug("Could not check for updates: HTTP {}", connection.getResponseCode());
                    return;
                }

                String latestVersion;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String firstLine = reader.readLine();
                    latestVersion = firstLine == null ? null : firstLine.trim();
                }

                if (latestVersion == null || latestVersion.isEmpty()) {
                    LOGGER.debug("Could not check for updates: version file was empty");
                } else if (!currentVersion.equals(latestVersion)) {
                    LOGGER.warn("================================================");
                    LOGGER.warn("EasyAFK Update Available!");
                    LOGGER.warn("Current Version: {}", currentVersion);
                    LOGGER.warn("Latest Version: {}", latestVersion);
                    LOGGER.warn("Download:");
                    LOGGER.warn("   Curseforge: https://curseforge.com/minecraft/mc-mods/easyafk");
                    LOGGER.warn("   Modrinth: https://modrinth.com/mod/easyafk");
                    LOGGER.warn("================================================");
                } else {
                    LOGGER.info("EasyAFK is up to date! (Version: {})", currentVersion);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not check for updates: {}", e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "EasyAFK-VersionChecker").start();
    }
}
