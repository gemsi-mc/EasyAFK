package com.gemsi.easyafk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionChecker {
    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");
    private static final String VERSION_CHECK_URL = "https://raw.githubusercontent.com/gemsi-mc/EasyAFK/refs/heads/1.21.1/version.txt";

    public static void checkForUpdates(String currentVersion) {
        new Thread(() -> {
            try {
                URL url = new URL(VERSION_CHECK_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String latestVersion = reader.readLine().trim();
                    reader.close();

                    if (!currentVersion.equals(latestVersion)) {
                        LOGGER.warn("================================================");
                        LOGGER.warn("EasyAFK Update Available!");
                        LOGGER.warn("Current Version: " + currentVersion);
                        LOGGER.warn("Latest Version: " + latestVersion);
                        LOGGER.warn("Download:");
                        LOGGER.warn("   Curseforge: https://curseforge.com/minecraft/mc-mods/easyafk");
                        LOGGER.warn("   Modrinth: https://modrinth.com/mod/easyafk");
                        LOGGER.warn("================================================");
                    } else {
                        LOGGER.info("EasyAFK is up to date! (Version: " + currentVersion + ")");
                    }
                }

                connection.disconnect();
            } catch (Exception e) {
                LOGGER.debug("Could not check for updates: " + e.getMessage());
            }
        }, "EasyAFK-VersionChecker").start();
    }
}