package dev.chronica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ChronicaNpcBrain extends ChronicaSubSimulator {
    private static final int WORK_CADENCE_TICKS = 1_200;

    public ChronicaNpcBrain(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long started = System.nanoTime();
        List<ChronicaNPCData> npcs = new ArrayList<>(data.namedNpcs.values());
        npcs.sort(Comparator.comparing(n -> n.id));

        while (processingCursor < npcs.size()) {
            if (System.nanoTime() - started > budgetNanos) break;

            ChronicaNPCData npc = npcs.get(processingCursor);
            tickNpc(npc);

            processingCursor++;
        }

        if (processingCursor >= npcs.size()) {
            processingCursor = 0;
        }
    }

    private void tickNpc(ChronicaNPCData npc) {
        if (npc == null || !npc.alive) return;

        long now = level.getGameTime();
        int offset = Math.floorMod(npc.id.hashCode(), WORK_CADENCE_TICKS);
        if ((now + offset) % WORK_CADENCE_TICKS != 0) return;

        Civilization civ = data.civilizations.get(npc.civilization);
        if (civ == null || civ.status == CivStatus.COLLAPSED) return;

        ChronicaWorkPlan plan = ChronicaWorkPlanner.planFor(data, civ, npc);
        applyWork(civ, npc, plan);

        Settlement home = primarySettlement(civ);
        if (home != null) {
            npc.homeSettlementId = home.id;
            npc.lastKnownPos = home.center;
        }

        npc.age = Math.max(npc.age, (int) (level.getDayTime() / 24_000L));
        data.setDirty();
    }

    private void applyWork(Civilization civ, ChronicaNPCData npc, ChronicaWorkPlan plan) {
        int power = workPower(civ, npc);

        switch (plan.workType()) {
            case gather_food -> {
                addResource(civ, ResourceType.FOOD, power * 5);
                addResource(civ, ResourceType.HERBS, Math.max(1, power / 2));
            }
            case chop_wood -> addResource(civ, ResourceType.WOOD, power * 4);
            case mine_stone -> {
                addResource(civ, ResourceType.STONE, power * 4);
                addResource(civ, ResourceType.CLAY, Math.max(1, power));
            }
            case mine_iron -> {
                addResource(civ, ResourceType.STONE, power * 2);
                addResource(civ, ResourceType.IRON, Math.max(1, power * 2));
                addResource(civ, ResourceType.COAL, Math.max(1, power));
            }
            case build_housing -> improveSettlement(civ, power);
            case train_guards -> {
                civ.garrisonCount = Math.min(500, civ.garrisonCount + Math.max(1, power / 2));
                civ.militaryStrength = Math.min(5_000, civ.militaryStrength + power);
            }
            case research -> civ.techProgress = Math.min(20_000, civ.techProgress + power * 3);
            case manage_trade -> {
                addResource(civ, ResourceType.GOLD, Math.max(1, power));
                addResource(civ, ResourceType.FOOD, power);
            }
            case scout_territory -> civ.techProgress = Math.min(20_000, civ.techProgress + Math.max(1, power));
            case diplomacy -> civ.techProgress = Math.min(20_000, civ.techProgress + Math.max(1, power / 2));
            case rest -> {
                addResource(civ, ResourceType.FOOD, Math.max(1, power / 2));
                npc.age = Math.max(0, npc.age);
            }
        }
    }

    private static int workPower(Civilization civ, ChronicaNPCData npc) {
        int base = switch (npc.role) {
            case ELDER -> 3;
            case MERCHANT, DIPLOMAT, SCOUT -> 4;
            case GUARD_CAPTAIN, GENERAL -> 5;
            case CITIZEN -> 3;
        };

        int techBonus = Math.max(0, civ.techTier.level);
        int populationBonus = civ.population > 200 ? 2 : civ.population > 100 ? 1 : 0;
        int traitBonus = civ.primaryTrait == CultureTrait.SCHOLARLY || civ.primaryTrait == CultureTrait.MERCANTILE ? 1 : 0;

        return Math.max(1, base + techBonus + populationBonus + traitBonus);
    }

    private static void addResource(Civilization civ, ResourceType type, int amount) {
        int current = civ.stockpile.getOrDefault(type, 0);
        civ.stockpile.put(type, Math.max(-100, Math.min(50_000, current + amount)));
    }

    private void improveSettlement(Civilization civ, int power) {
        Settlement settlement = primarySettlement(civ);
        if (settlement == null) return;

        if (settlement.structures.size() < 96) {
            int index = settlement.structures.size();
            BlockPos anchor = settlement.center.offset((index % 9) - 4, 0, (index / 9) - 4);
            settlement.structures.add(anchor);
        }

        settlement.populationLocal = Math.min(10_000, settlement.populationLocal + Math.max(1, power));
        civ.maxPopulation = Math.min(100_000, civ.maxPopulation + Math.max(2, power * 2));

        addResource(civ, ResourceType.WOOD, -Math.min(civ.stockpile.getOrDefault(ResourceType.WOOD, 0), Math.max(1, power)));
        addResource(civ, ResourceType.STONE, -Math.min(civ.stockpile.getOrDefault(ResourceType.STONE, 0), Math.max(1, power / 2)));
    }

    private static Settlement primarySettlement(Civilization civ) {
        for (Settlement settlement : civ.settlements) {
            if (settlement.type == SettlementType.CAPITAL) return settlement;
        }

        for (Settlement settlement : civ.settlements) {
            if (settlement.type != SettlementType.RUIN) return settlement;
        }

        return civ.settlements.isEmpty() ? null : civ.settlements.get(0);
    }
}
