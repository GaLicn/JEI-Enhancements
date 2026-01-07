package com.gali.jei_enhancements;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(JEIEnhancements.MODID)
public class JEIEnhancements {
    public static final String MODID = "jei_enhancements";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JEIEnhancements(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("JEI Enhancements loaded");
    }
}
