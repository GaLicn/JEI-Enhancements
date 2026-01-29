package com.gali.jei_enhancements;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Jei_enhancements.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Jei_enhancementsClient {
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        
        Jei_enhancements.LOGGER.info("JEI Enhancements Client initialized");
    }
}
