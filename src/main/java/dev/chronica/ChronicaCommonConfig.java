package dev.chronica;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ChronicaCommonConfig {
    public static final ChronicaCommonConfig CONFIG;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.IntValue minCivilizations;
    public final ModConfigSpec.IntValue maxCivilizations;
    public final ModConfigSpec.IntValue simulatedHistoryYears;
    public final ModConfigSpec.IntValue worldAgeCyclesPerRealDay;
    public final ModConfigSpec.IntValue civSeparationBlocks;

    public final ModConfigSpec.DoubleValue tickBudgetMs;
    public final ModConfigSpec.IntValue civTickInterval;
    public final ModConfigSpec.IntValue economyTickInterval;
    public final ModConfigSpec.IntValue diplomacyTickInterval;
    public final ModConfigSpec.IntValue tradeRouteTickInterval;
    public final ModConfigSpec.IntValue questTickInterval;

    public final ModConfigSpec.BooleanValue enableWar;
    public final ModConfigSpec.BooleanValue enableCollapse;
    public final ModConfigSpec.BooleanValue enableExpansion;
    public final ModConfigSpec.IntValue maxTerritoryChunksPerCiv;
    public final ModConfigSpec.DoubleValue populationGrowthRate;
    public final ModConfigSpec.DoubleValue resourceConsumptionRate;

    public final ModConfigSpec.IntValue maxActiveQuestsPerPlayer;
    public final ModConfigSpec.IntValue questExpiryCycles;
    public final ModConfigSpec.DoubleValue questRewardScaling;
    public final ModConfigSpec.BooleanValue enableDiplomacyQuests;
    public final ModConfigSpec.IntValue globalActiveQuestCap;
    public final ModConfigSpec.LongValue perCivTypeCooldownTicks;

    public final ModConfigSpec.IntValue lazyInjectRate;
    public final ModConfigSpec.BooleanValue enableRuins;
    public final ModConfigSpec.DoubleValue ruinLootMultiplier;

    public final ModConfigSpec.BooleanValue minecoloniesIntegration;
    public final ModConfigSpec.BooleanValue createModCaravans;
    public final ModConfigSpec.BooleanValue xaerosMinimapWaypoints;
    public final ModConfigSpec.BooleanValue disableNpcGrief;

    public final ModConfigSpec.BooleanValue logSimulationTicks;
    public final ModConfigSpec.BooleanValue showTerritoryBorders;
    public final ModConfigSpec.BooleanValue enableDevCommands;

    static {
        Pair<ChronicaCommonConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ChronicaCommonConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    private ChronicaCommonConfig(ModConfigSpec.Builder builder) {
        builder.push("world");
        minCivilizations = builder.comment("Minimum civilizations generated on first world initialization.").defineInRange("min_civilizations", 3, 1, 64);
        maxCivilizations = builder.comment("Maximum civilizations generated on first world initialization.").defineInRange("max_civilizations", 7, 1, 128);
        simulatedHistoryYears = builder.comment("Compressed pre-history years simulated once on world creation.").defineInRange("simulated_history_years", 200, 0, 10000);
        worldAgeCyclesPerRealDay = builder.defineInRange("world_age_cycles_per_real_day", 10, 1, 1000);
        civSeparationBlocks = builder.defineInRange("civ_separation_blocks", 1500, 256, 30000);
        builder.pop();

        builder.push("simulation");
        tickBudgetMs = builder.comment("Hard CPU budget per server tick for CHRONICA.").defineInRange("tick_budget_ms", 3.0, 0.1, 20.0);
        civTickInterval = builder.defineInRange("civ_tick_interval", 20, 1, 1200);
        economyTickInterval = builder.defineInRange("economy_tick_interval", 100, 1, 2400);
        diplomacyTickInterval = builder.defineInRange("diplomacy_tick_interval", 200, 1, 4800);
        tradeRouteTickInterval = builder.defineInRange("tradeRoute_tick_interval", 40, 1, 2400);
        questTickInterval = builder.defineInRange("quest_tick_interval", 60, 1, 2400);
        builder.pop();

        builder.push("civilizations");
        enableWar = builder.define("enable_war", true);
        enableCollapse = builder.define("enable_collapse", true);
        enableExpansion = builder.define("enable_expansion", true);
        maxTerritoryChunksPerCiv = builder.defineInRange("max_territory_chunks_per_civ", 500, 1, 100000);
        populationGrowthRate = builder.defineInRange("population_growth_rate", 0.02, 0.0, 2.0);
        resourceConsumptionRate = builder.defineInRange("resource_consumption_rate", 1.0, 0.0, 100.0);
        builder.pop();

        builder.push("quests");
        maxActiveQuestsPerPlayer = builder.defineInRange("max_active_quests_per_player", 5, 0, 100);
        questExpiryCycles = builder.defineInRange("quest_expiry_cycles", 100, 1, 100000);
        questRewardScaling = builder.defineInRange("quest_reward_scaling", 1.0, 0.0, 100.0);
        enableDiplomacyQuests = builder.define("enable_diplomacy_quests", true);
        globalActiveQuestCap = builder.defineInRange("global_active_quest_cap", 50, 0, 10000);
        perCivTypeCooldownTicks = builder.defineInRange("per_civ_type_cooldown_ticks", 72000L, 0L, 1728000L);
        builder.pop();

        builder.push("structures");
        lazyInjectRate = builder.defineInRange("lazy_inject_rate", 1, 0, 100);
        enableRuins = builder.define("enable_ruins", true);
        ruinLootMultiplier = builder.defineInRange("ruin_loot_multiplier", 1.0, 0.0, 100.0);
        builder.pop();

        builder.push("compatibility");
        minecoloniesIntegration = builder.define("minecolonies_integration", true);
        createModCaravans = builder.define("create_mod_caravans", false);
        xaerosMinimapWaypoints = builder.define("xaeros_minimap_waypoints", true);
        disableNpcGrief = builder.define("disable_npc_grief", true);
        builder.pop();

        builder.push("debug");
        logSimulationTicks = builder.define("log_simulation_ticks", false);
        showTerritoryBorders = builder.define("show_territory_borders", false);
        enableDevCommands = builder.define("enable_dev_commands", false);
        builder.pop();
    }
}
