package dev.chronica;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class CivilizationSimulator extends ChronicaSubSimulator {
    private static final int COLLAPSE_THRESHOLD = 8;
    private static final int RUIN_THRESHOLD = 1;
    private static final int EXPANSION_COOLDOWN_TICKS = 12_000;

    public CivilizationSimulator(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long start = System.nanoTime();
        List<Civilization> civs = new ArrayList<>(data.civilizations.values());
        while (processingCursor < civs.size()) {
            if (System.nanoTime() - start > budgetNanos) break;
            processCiv(civs.get(processingCursor));
            processingCursor++;
        }
        if (processingCursor >= civs.size()) processingCursor = 0;
    }

    public void processCiv(Civilization civ) {
        if (civ.status == CivStatus.COLLAPSED) return;
        long now = level.getGameTime();

        updateCapacity(civ);
        updateResources(civ);
        updatePopulation(civ);
        updateMilitary(civ);
        updateTechnology(civ, now);

        if (ChronicaCommonConfig.CONFIG.enableExpansion.get()) {
            maybeExpand(civ, now);
        }
        if (ChronicaCommonConfig.CONFIG.enableCollapse.get()) {
            maybeCollapse(civ, now);
        }
        data.setDirty();
    }

    private static void updateCapacity(Civilization civ) {
        civ.maxPopulation = Math.max(40, 60 + civ.territory.size() * 12 + civ.settlements.size() * 35 + civ.techTier.level * 60);
    }

    private static void updateResources(Civilization civ) {
        double consumptionMultiplier = ChronicaCommonConfig.CONFIG.resourceConsumptionRate.get();
        for (ResourceType type : ResourceType.values()) {
            int production = civ.productionPerCycle.getOrDefault(type, 0);
            int consumption = (int) Math.ceil(civ.consumptionPerCycle.getOrDefault(type, 0) * consumptionMultiplier);
            int next = civ.stockpile.getOrDefault(type, 0) + production - consumption;
            civ.stockpile.put(type, Math.max(-100, Math.min(50_000, next)));
        }
    }

    private static void updatePopulation(Civilization civ) {
        int food = civ.stockpile.getOrDefault(ResourceType.FOOD, 0);
        int housingFree = Math.max(0, civ.maxPopulation - civ.population);
        double peaceModifier = civ.status == CivStatus.DECLINING ? 0.55 : 1.0;
        double foodModifier = food >= 0 ? 1.0 + Math.min(0.4, food / 1000.0) : Math.max(0.25, 1.0 + food / 300.0);
        double birthRate = civ.population * ChronicaCommonConfig.CONFIG.populationGrowthRate.get() * foodModifier * peaceModifier;
        double deathRate = food < 0 ? Math.max(0.25, Math.abs(food) / 90.0) : Math.max(0.0, civ.population * 0.0015);
        int delta = (int) Math.floor(birthRate - deathRate);
        if (delta == 0 && food > 50 && housingFree > 5) delta = 1;
        civ.population = Math.max(0, Math.min(civ.maxPopulation, civ.population + delta));

        int foodConsumption = Math.max(1, civ.population / 24);
        civ.consumptionPerCycle.put(ResourceType.FOOD, foodConsumption);
    }

    private void updateMilitary(Civilization civ) {
        int food = civ.stockpile.getOrDefault(ResourceType.FOOD, 0);
        int iron = civ.stockpile.getOrDefault(ResourceType.IRON, 0) + civ.stockpile.getOrDefault(ResourceType.STEEL, 0) * 2;
        int base = civ.garrisonCount + civ.population / 18 + civ.techTier.level * 6;
        int supplyPenalty = food < 0 ? Math.min(25, Math.abs(food) / 20) : 0;
        int equipmentBonus = Math.min(35, iron / 30);
        civ.militaryStrength = Math.max(0, base + equipmentBonus - supplyPenalty);
    }

    private void updateTechnology(Civilization civ, long now) {
        if (civ.techTier == TechTier.ARCANE) return;
        int scholarly = civ.primaryTrait == CultureTrait.SCHOLARLY || civ.secondaryTrait == CultureTrait.SCHOLARLY ? 2 : 1;
        int prosperity = civ.stockpile.getOrDefault(ResourceType.FOOD, 0) > 100 && civ.population > 50 ? 1 : 0;
        civ.techProgress += scholarly + prosperity + civ.settlements.size();
        int threshold = 150 + civ.techTier.level * 200;
        if (civ.techProgress >= threshold) {
            Optional<TechTier> next = civ.techTier.next();
            if (next.isPresent()) {
                civ.techTier = next.get();
                civ.techProgress = 0;
                for (Settlement settlement : civ.settlements) settlement.tier = civ.techTier;
                data.addHistory(new HistoryEvent.TechAdvanced(UUID.randomUUID(), now, civ.id, civ.techTier));
            }
        }
    }

    private void maybeExpand(Civilization civ, long now) {
        if (civ.population < civ.maxPopulation * 0.9) return;
        if (civ.territory.size() >= ChronicaCommonConfig.CONFIG.maxTerritoryChunksPerCiv.get()) return;
        if (now < civ.expansionCooldownUntil) return;
        Long candidate = findAdjacentUnclaimedChunk(civ);
        if (candidate == null) return;

        civ.territory.add(candidate);
        data.territoryMap.claim(civ.id, candidate);
        civ.expansionCooldownUntil = now + EXPANSION_COOLDOWN_TICKS;

        if (civ.territory.size() % 9 == 0) {
            Settlement outpost = new Settlement();
            outpost.owner = civ.id;
            outpost.type = SettlementType.OUTPOST;
            outpost.tier = civ.techTier;
            ChunkPos chunk = new ChunkPos(candidate);
            outpost.center = chunk.getWorldPosition().offset(8, level.getSeaLevel() + 8, 8);
            outpost.populationLocal = Math.max(8, civ.population / 20);
            outpost.name = NameGenerator.create(level.getSeed()).generateSettlementName(civ.id, candidate);
            civ.settlements.add(outpost);
        }
    }

    private Long findAdjacentUnclaimedChunk(Civilization civ) {
        for (long claimed : civ.territory) {
            ChunkPos center = new ChunkPos(claimed);
            ChunkPos[] neighbors = new ChunkPos[] {
                    new ChunkPos(center.x + 1, center.z), new ChunkPos(center.x - 1, center.z),
                    new ChunkPos(center.x, center.z + 1), new ChunkPos(center.x, center.z - 1)
            };
            for (ChunkPos neighbor : neighbors) {
                long encoded = neighbor.toLong();
                if (!data.territoryMap.isClaimed(encoded)) return encoded;
            }
        }
        return null;
    }

    private void maybeCollapse(Civilization civ, long now) {
        boolean capitalLost = civ.territory.stream().noneMatch(chunk -> new ChunkPos(chunk).equals(new ChunkPos(civ.capital)));
        if (civ.population < COLLAPSE_THRESHOLD || capitalLost) {
            civ.status = CivStatus.DECLINING;
        }
        if (civ.population <= RUIN_THRESHOLD || (capitalLost && civ.population < COLLAPSE_THRESHOLD)) {
            civ.status = CivStatus.COLLAPSED;
            for (Settlement settlement : civ.settlements) settlement.type = SettlementType.RUIN;
            data.addHistory(new HistoryEvent.CivCollapsed(UUID.randomUUID(), now, civ.id, capitalLost ? "loss of the capital" : "hunger and depopulation"));
        }
    }
}

