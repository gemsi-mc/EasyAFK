package com.gemsi.easyafk;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gemsi.easyafk.platform.NeoForgeConfigService;


@Mod(EasyAFK.MODID)
public class EasyAFK
{
    public static final String MODID = "easyafk";

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    public EasyAFK(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeConfigService.SPEC);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new AFKListener());

        LOGGER.info("Initialised successfully!");

    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event)
    {
        AFKCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("Start up complete.");
        String version = ModList.get()
                .getModContainerById(MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("UNKNOWN");
        VersionChecker.checkForUpdates(version);
    }

}
