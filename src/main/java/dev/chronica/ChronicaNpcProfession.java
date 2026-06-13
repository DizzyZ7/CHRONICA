package dev.chronica;

enum ChronicaNpcProfession {
    farmer,
    lumberjack,
    miner,
    builder,
    guard,
    merchant,
    scholar,
    scout,
    diplomat,
    elder,
    general,
    idle;

    static ChronicaNpcProfession fromRole(NPCRole role) {
        return switch (role) {
            case ELDER -> elder;
            case MERCHANT -> merchant;
            case GUARD_CAPTAIN -> guard;
            case SCOUT -> scout;
            case DIPLOMAT -> diplomat;
            case GENERAL -> general;
            case CITIZEN -> idle;
        };
    }
}

enum ChronicaNeed {
    food,
    wood,
    stone,
    iron,
    housing,
    security,
    knowledge,
    trade,
    diplomacy,
    expansion,
    none
}

enum ChronicaWorkType {
    gather_food,
    chop_wood,
    mine_stone,
    mine_iron,
    build_housing,
    train_guards,
    research,
    manage_trade,
    scout_territory,
    diplomacy,
    rest
}

record ChronicaWorkPlan(
        ChronicaNpcProfession profession,
        ChronicaNeed need,
        ChronicaWorkType workType,
        int priority,
        String reason
) {
}
