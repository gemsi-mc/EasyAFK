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

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EasyAFK.MODID)
public class EasyAFK
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "easyafk";

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    // No-argument constructor required by Forge 1.21.1
    public EasyAFK()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register config using the mod container from ModList
        ModList.get().getModContainerById(MODID).ifPresent(container -> {
            container.addConfig(new ModConfig(ModConfig.Type.SERVER, Config.SPEC, container));
        });

        // Register ourselves for server and other game events we are interested in.
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new AFKListener());  // Register your listener here

        LOGGER.info("Initialised successfully!");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        //LOGGER.info("Common setup complete");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("Start up complete.");
    }
}