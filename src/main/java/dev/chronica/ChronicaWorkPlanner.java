package dev.chronica;

final class ChronicaWorkPlanner {
    private ChronicaWorkPlanner() {
    }

    static ChronicaWorkPlan planFor(ChronicaWorldData data, Civilization civ, ChronicaNPCData npc) {
        ChronicaNeed need = dominantNeed(civ);
        ChronicaNpcProfession profession = professionFor(npc.role, civ, need);
        ChronicaWorkType work = workFor(profession, need);

        return new ChronicaWorkPlan(
                profession,
                need,
                work,
                priorityFor(need),
                "need=" + need + ", role=" + npc.role
        );
    }

    private static ChronicaNeed dominantNeed(Civilization civ) {
        int food = civ.stockpile.getOrDefault(ResourceType.FOOD, 0);
        int wood = civ.stockpile.getOrDefault(ResourceType.WOOD, 0);
        int stone = civ.stockpile.getOrDefault(ResourceType.STONE, 0);
        int iron = civ.stockpile.getOrDefault(ResourceType.IRON, 0);
        int housingFree = civ.maxPopulation - civ.population;
        int expectedDefense = Math.max(12, civ.population / 9);

        if (food < civ.population * 3) return ChronicaNeed.food;
        if (housingFree < 20) return ChronicaNeed.housing;
        if (wood < 180) return ChronicaNeed.wood;
        if (stone < 160) return ChronicaNeed.stone;
        if (iron < 90 && civ.techTier.level >= TechTier.COPPER.level) return ChronicaNeed.iron;
        if (civ.militaryStrength < expectedDefense) return ChronicaNeed.security;
        if (civ.techProgress < 120 + civ.techTier.level * 180) return ChronicaNeed.knowledge;
        if (civ.activeTradeRoutes.isEmpty() && civ.population > 120) return ChronicaNeed.trade;
        if (civ.population > civ.maxPopulation * 0.82) return ChronicaNeed.expansion;

        return ChronicaNeed.none;
    }

    private static ChronicaNpcProfession professionFor(NPCRole role, Civilization civ, ChronicaNeed need) {
        ChronicaNpcProfession roleProfession = ChronicaNpcProfession.fromRole(role);

        if (roleProfession != ChronicaNpcProfession.idle) {
            return roleProfession;
        }

        return switch (need) {
            case food -> ChronicaNpcProfession.farmer;
            case wood -> ChronicaNpcProfession.lumberjack;
            case stone, iron -> ChronicaNpcProfession.miner;
            case housing -> ChronicaNpcProfession.builder;
            case security -> ChronicaNpcProfession.guard;
            case knowledge -> ChronicaNpcProfession.scholar;
            case trade -> ChronicaNpcProfession.merchant;
            case diplomacy -> ChronicaNpcProfession.diplomat;
            case expansion -> ChronicaNpcProfession.scout;
            case none -> fallbackForTrait(civ.primaryTrait);
        };
    }

    private static ChronicaNpcProfession fallbackForTrait(CultureTrait trait) {
        return switch (trait) {
            case MERCANTILE -> ChronicaNpcProfession.merchant;
            case SCHOLARLY, SPIRITUAL -> ChronicaNpcProfession.scholar;
            case MILITARIST, AGGRESSIVE -> ChronicaNpcProfession.guard;
            case NOMADIC, EXPANSIONIST -> ChronicaNpcProfession.scout;
            case ISOLATIONIST -> ChronicaNpcProfession.builder;
        };
    }

    private static ChronicaWorkType workFor(ChronicaNpcProfession profession, ChronicaNeed need) {
        return switch (profession) {
            case farmer -> ChronicaWorkType.gather_food;
            case lumberjack -> ChronicaWorkType.chop_wood;
            case miner -> need == ChronicaNeed.iron ? ChronicaWorkType.mine_iron : ChronicaWorkType.mine_stone;
            case builder -> ChronicaWorkType.build_housing;
            case guard, general -> ChronicaWorkType.train_guards;
            case scholar, elder -> ChronicaWorkType.research;
            case merchant -> ChronicaWorkType.manage_trade;
            case scout -> ChronicaWorkType.scout_territory;
            case diplomat -> ChronicaWorkType.diplomacy;
            case idle -> ChronicaWorkType.rest;
        };
    }

    private static int priorityFor(ChronicaNeed need) {
        return switch (need) {
            case food -> 100;
            case security -> 90;
            case housing -> 80;
            case wood, stone, iron -> 70;
            case knowledge -> 55;
            case trade, diplomacy, expansion -> 45;
            case none -> 10;
        };
    }
}
