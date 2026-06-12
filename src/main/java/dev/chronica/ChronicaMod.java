package dev.chronica;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ChronicaMod.MOD_ID)
public final class ChronicaMod {
    public static final String MOD_ID = "chronica";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChronicaMod(IEventBus modBus, ModContainer modContainer) {
        ChronicaRegistration.BLOCKS.register(modBus);
        ChronicaRegistration.ITEMS.register(modBus);
        ChronicaRegistration.ENTITIES.register(modBus);

        modBus.register(new ChronicaRegistration.ModBusEvents());
        modBus.register(new ChronicaNetwork.ModBusEvents());

        NeoForge.EVENT_BUS.register(new ChronicaSimulationManager.ServerEvents());
        NeoForge.EVENT_BUS.register(new ChronicaServerEvents());
        NeoForge.EVENT_BUS.register(new ChronicaCommands());

        modContainer.registerConfig(ModConfig.Type.COMMON, ChronicaCommonConfig.SPEC, "chronica-common.toml");
        LOGGER.info("CHRONICA initialized for Minecraft 1.21.1 / NeoForge 21.1.x");
    }
}
