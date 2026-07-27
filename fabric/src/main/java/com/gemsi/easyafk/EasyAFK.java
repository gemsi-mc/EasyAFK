package com.gemsi.easyafk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EasyAFK implements ModInitializer {
    public static final String MODID = "easyafk";
    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    @Override
    public void onInitialize() {
        Config.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AFKCommands.register(dispatcher));

        AFKListener.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Start up complete.");
            String version = FabricLoader.getInstance()
                    .getModContainer(MODID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("UNKNOWN");

            VersionChecker.checkForUpdates(version);
        });

        LOGGER.info("Initialised successfully!");
    }
}
