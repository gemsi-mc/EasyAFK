package com.gemsi.easyafk;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EasyAFK.MODID)
public class EasyAFK
{
    public static final String MODID = "easyafk";

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    public EasyAFK()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModList.get().getModContainerById(MODID).ifPresent(container -> {
            container.addConfig(new ModConfig(ModConfig.Type.SERVER, Config.SPEC, container));
        });

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new AFKListener());

        LOGGER.info("Initialised successfully!");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {}

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("Start up complete.");
        String version = ModList.get()
                .getModContainerById("easyafk")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("UNKNOWN");
        VersionChecker.checkForUpdates(version);
    }
}