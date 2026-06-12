package dev.chronica;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ChronicaSimulationManager {
    private static final Map<ServerLevel, ChronicaSimulationManager> MANAGERS = new IdentityHashMap<>();

    private final ServerLevel level;
    private final ChronicaWorldData data;
    private final CivilizationSimulator civSimulator;
    private final EconomySimulator economySimulator;
    private final DiplomacySimulator diplomacySimulator;
    private final TradeRouteSimulator tradeRouteSimulator;
    private final QuestEngine questEngine;
    private long tickCount = 0L;

    private ChronicaSimulationManager(ServerLevel level) {
        this.level = level;
        this.data = ChronicaWorldData.getOrCreate(level);
        this.civSimulator = new CivilizationSimulator(level, data);
        this.economySimulator = new EconomySimulator(level, data);
        this.diplomacySimulator = new DiplomacySimulator(level, data);
        this.tradeRouteSimulator = new TradeRouteSimulator(level, data);
        this.questEngine = new QuestEngine(level, data);
    }

    public static ChronicaSimulationManager forLevel(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, ChronicaSimulationManager::new);
    }

    public void tick() {
        assert level.getServer().isSameThread() : "CHRONICA simulation must run on server thread";
        tickCount++;

        if (!data.initialized) {
            ChronicaWorldInitializer.initialize(level, data);
            data.setDirty();
        }

        long fullBudgetNanos = Math.max(100_000L, (long) (ChronicaCommonConfig.CONFIG.tickBudgetMs.get() * 1_000_000.0));
        long started = System.nanoTime();

        if (tickCount % ChronicaCommonConfig.CONFIG.civTickInterval.get() == 0) {
            civSimulator.tick(Math.max(50_000L, fullBudgetNanos * 8 / 30));
        }
        if (System.nanoTime() - started >= fullBudgetNanos) return;

        if (tickCount % ChronicaCommonConfig.CONFIG.tradeRouteTickInterval.get() == 0) {
            tradeRouteSimulator.tick(Math.max(50_000L, fullBudgetNanos * 6 / 30));
        }
        if (System.nanoTime() - started >= fullBudgetNanos) return;

        if (tickCount % ChronicaCommonConfig.CONFIG.questTickInterval.get() == 0) {
            questEngine.tick(Math.max(50_000L, fullBudgetNanos * 4 / 30));
        }
        if (System.nanoTime() - started >= fullBudgetNanos) return;

        if (tickCount % ChronicaCommonConfig.CONFIG.economyTickInterval.get() == 0) {
            economySimulator.tick(Math.max(50_000L, fullBudgetNanos * 5 / 30));
        }
        if (System.nanoTime() - started >= fullBudgetNanos) return;

        if (tickCount % ChronicaCommonConfig.CONFIG.diplomacyTickInterval.get() == 0) {
            diplomacySimulator.tick(Math.max(50_000L, fullBudgetNanos * 4 / 30));
        }
    }

    public ChronicaWorldData data() {
        return data;
    }

    public static final class ServerEvents {
        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
            if (overworld != null) forLevel(overworld);
        }

        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Post event) {
            MinecraftServer server = event.getServer();
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) return;
            forLevel(overworld).tick();
        }
    }
}

abstract class ChronicaSubSimulator {
    protected final ServerLevel level;
    protected final ChronicaWorldData data;
    protected int processingCursor = 0;

    protected ChronicaSubSimulator(ServerLevel level, ChronicaWorldData data) {
        this.level = level;
        this.data = data;
    }

    public final void tick(long budgetNanos) {
        assert level.getServer().isSameThread() : "CHRONICA sub-simulator must run on server thread";
        doTick(budgetNanos);
    }

    protected abstract void doTick(long budgetNanos);
}
