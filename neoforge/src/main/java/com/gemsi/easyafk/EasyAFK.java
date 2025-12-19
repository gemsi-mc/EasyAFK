package com.gemsi.easyafk;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(EasyAFK.MODID)
public class EasyAFK
{
    public static final String MODID = "easyafk";

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    public EasyAFK(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new AFKListener());

        LOGGER.info("Initialised successfully!");

    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {}

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("Start up complete.");
    }

}
