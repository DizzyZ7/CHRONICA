package dev.chronica;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class ChronicaWorldInitializer {
    private ChronicaWorldInitializer() {}

    public static void initialize(ServerLevel level, ChronicaWorldData data) {
        data.worldSeed = level.getSeed();
        NameGenerator names = NameGenerator.create(data.worldSeed);
        Random random = new Random(data.worldSeed ^ 0x4348524F4E494341L);
        int min = ChronicaCommonConfig.CONFIG.minCivilizations.get();
        int max = ChronicaCommonConfig.CONFIG.maxCivilizations.get();
        int count = min + random.nextInt(Math.max(1, max - min + 1));
        int separation = ChronicaCommonConfig.CONFIG.civSeparationBlocks.get();
        List<BlockPos> capitals = new ArrayList<>();
        BlockPos worldSpawn = level.getSharedSpawnPos();

        for (int i = 0; i < count; i++) {
            BlockPos capital = findCapitalCandidate(random, capitals, separation, worldSpawn, i);
            capitals.add(capital);
            Civilization civ = createCivilization(level, data, names, random, capital, i);
            data.civilizations.put(civ.id, civ);
            for (long chunk : civ.territory) data.territoryMap.claim(civ.id, chunk);
            data.addHistory(new HistoryEvent.CivFounded(UUID.randomUUID(), 0L, civ.id, civ.capital, names.generateNPCName(civ.id, NPCRole.ELDER, i)));
        }

        createInitialRelations(data);
        runCompressedPrehistory(level, data);
        data.initialized = true;
        data.setDirty();
    }

    private static Civilization createCivilization(ServerLevel level, ChronicaWorldData data, NameGenerator names, Random random, BlockPos capital, int index) {
        Civilization civ = new Civilization();
        civ.id = CivId.random();
        civ.name = names.generateCivName(index * 31L + random.nextLong());
        civ.primaryTrait = CultureTrait.values()[random.nextInt(CultureTrait.values().length)];
        do {
            civ.secondaryTrait = CultureTrait.values()[random.nextInt(CultureTrait.values().length)];
        } while (civ.secondaryTrait == civ.primaryTrait);
        civ.techTier = TechTier.STONE;
        civ.status = CivStatus.RISING;
        civ.capital = capital;
        civ.population = 120 + random.nextInt(140);
        civ.garrisonCount = 10 + random.nextInt(16);
        civ.stockpile = new EnumMap<>(ResourceType.class);
        civ.productionPerCycle = new EnumMap<>(ResourceType.class);
        civ.consumptionPerCycle = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            civ.stockpile.put(resource, random.nextInt(120));
            civ.productionPerCycle.put(resource, productionFor(civ.primaryTrait, resource, random));
            civ.consumptionPerCycle.put(resource, resource == ResourceType.FOOD ? Math.max(1, civ.population / 18) : random.nextInt(3));
        }

        ChunkPos capitalChunk = new ChunkPos(capital);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                civ.territory.add(new ChunkPos(capitalChunk.x + dx, capitalChunk.z + dz).toLong());
            }
        }

        Settlement settlement = new Settlement();
        settlement.owner = civ.id;
        settlement.type = SettlementType.CAPITAL;
        settlement.center = capital;
        settlement.name = names.generateSettlementName(civ.id, index);
        settlement.populationLocal = civ.population;
        settlement.tier = civ.techTier;
        civ.settlements.add(settlement);

        createNamedNpc(data, civ, settlement, names, NPCRole.ELDER, index * 41L);
        createNamedNpc(data, civ, settlement, names, NPCRole.MERCHANT, index * 43L);
        if (civ.primaryTrait == CultureTrait.MILITARIST || civ.primaryTrait == CultureTrait.AGGRESSIVE) {
            createNamedNpc(data, civ, settlement, names, NPCRole.GENERAL, index * 47L);
        } else {
            createNamedNpc(data, civ, settlement, names, NPCRole.DIPLOMAT, index * 53L);
        }
        return civ;
    }

    private static void createNamedNpc(ChronicaWorldData data, Civilization civ, Settlement settlement, NameGenerator names, NPCRole role, long seed) {
        UUID id = UUID.randomUUID();
        civ.namedNPCs.add(id);
        ChronicaNPCData npc = new ChronicaNPCData();
        npc.id = id;
        npc.civilization = civ.id;
        npc.homeSettlementId = settlement.id;
        npc.role = role;
        npc.name = names.generateNPCName(civ.id, role, seed);
        npc.lastKnownPos = settlement.center;
        data.namedNpcs.put(id, npc);
    }

    private static int productionFor(CultureTrait trait, ResourceType resource, Random random) {
        int base = switch (resource) {
            case FOOD, WOOD, STONE -> 4 + random.nextInt(5);
            case IRON, COAL, CLAY, FISH, HERBS -> random.nextInt(4);
            case GOLD, GEMS, WOOL, LEATHER -> random.nextInt(3);
            case STEEL, ENCHANTED_GOODS, ARTIFACTS -> 0;
        };
        if (trait == CultureTrait.MERCANTILE && (resource == ResourceType.GOLD || resource == ResourceType.GEMS)) base += 2;
        if (trait == CultureTrait.SCHOLARLY && resource == ResourceType.ENCHANTED_GOODS) base += 1;
        if (trait == CultureTrait.NOMADIC && (resource == ResourceType.LEATHER || resource == ResourceType.WOOL)) base += 2;
        return base;
    }

    private static BlockPos findCapitalCandidate(Random random, List<BlockPos> existing, int separation, BlockPos spawn, int index) {
        int safeSeparation = Math.max(450, Math.min(separation, 1200));

        for (int attempt = 0; attempt < 300; attempt++) {
            double angle = (Math.PI * 2.0 * (index + attempt * 0.17) / Math.max(4, existing.size() + 4)) + random.nextDouble() * 0.55;
            int radius = 650 + index * 420 + random.nextInt(650) + attempt * 10;

            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos pos = new BlockPos(x, 96, z);

            boolean ok = existing.stream().allMatch(other -> other.distSqr(pos) >= (double) safeSeparation * safeSeparation);
            if (ok) return pos;
        }

        int fallbackRadius = 900 + index * 700;
        return spawn.offset(fallbackRadius, 0, fallbackRadius);
    }

    private static void createInitialRelations(ChronicaWorldData data) {
        List<Civilization> civs = new ArrayList<>(data.civilizations.values());
        for (int i = 0; i < civs.size(); i++) {
            for (int j = i + 1; j < civs.size(); j++) {
                DiplomaticRelation rel = DiplomaticRelation.neutral(0L);
                rel.trustScore = (int) ((data.worldSeed + i * 31L - j * 17L) % 61L) - 30;
                data.diplomacyMatrix.setRelation(civs.get(i).id, civs.get(j).id, rel);
            }
        }
    }

    private static void runCompressedPrehistory(ServerLevel level, ChronicaWorldData data) {
        int years = ChronicaCommonConfig.CONFIG.simulatedHistoryYears.get();
        CivilizationSimulator civSim = new CivilizationSimulator(level, data);
        DiplomacySimulator diplomacy = new DiplomacySimulator(level, data);
        EconomySimulator economy = new EconomySimulator(level, data);
        for (int year = 0; year < years; year++) {
            for (Civilization civ : data.civilizations.values()) civSim.processCiv(civ);
            economy.tick(10_000_000L);
            diplomacy.tick(10_000_000L);
            data.chronologicalAge++;
        }
    }
}






