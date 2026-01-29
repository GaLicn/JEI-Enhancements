package com.gali.jei_enhancements;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Jei_enhancements.MODID)
public class Jei_enhancements {
    public static final String MODID = "jei_enhancements";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Jei_enhancements() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        
        LOGGER.info("JEI Enhancements loaded");
    }
}
