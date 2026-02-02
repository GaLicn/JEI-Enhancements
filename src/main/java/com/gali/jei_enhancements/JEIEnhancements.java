package com.gali.jei_enhancements;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(JEIEnhancements.MODID)
public class JEIEnhancements {
    public static final String MODID = "jei_enhancements";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JEIEnhancements() {
        LOGGER.info("JEI Enhancements loaded");
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> JEIEnhancementsClient::init);
    }
}
