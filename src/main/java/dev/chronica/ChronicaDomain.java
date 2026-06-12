package dev.chronica;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

record CivId(UUID value) {
    static CivId random() {
        return new CivId(UUID.randomUUID());
    }
}

record TradeRouteId(UUID value) {
    static TradeRouteId random() {
        return new TradeRouteId(UUID.randomUUID());
    }
}

enum CultureTrait {
    AGGRESSIVE, MERCANTILE, ISOLATIONIST, NOMADIC,
    SCHOLARLY, MILITARIST, EXPANSIONIST, SPIRITUAL
}

enum TechTier {
    STONE(0), COPPER(1), IRON(2), STEEL(3), ARCANE(4);

    final int level;

    TechTier(int level) {
        this.level = level;
    }

    Optional<TechTier> next() {
        int target = level + 1;
        for (TechTier tier : values()) {
            if (tier.level == target) return Optional.of(tier);
        }
        return Optional.empty();
    }
}

enum CivStatus { RISING, STABLE, DECLINING, COLLAPSED }

enum SettlementType { CAPITAL, VILLAGE, OUTPOST, RUIN }

enum NPCRole { ELDER, MERCHANT, GUARD_CAPTAIN, SCOUT, DIPLOMAT, GENERAL, CITIZEN }

enum MemoryType { HELPED, ATTACKED, TRADED, IGNORED, QUESTED }

enum RelationType { NEUTRAL, ALLIANCE, TRADE_PACT, COLD_WAR, WAR, VASSAL }

enum TradeRouteStatus { ACTIVE, BLOCKED, DESTROYED }

enum QuestType { ESCORT, DELIVERY, DIPLOMACY, RESCUE, SCOUTING, RETRIEVAL }

enum QuestStatus { AVAILABLE, ACTIVE, COMPLETED, FAILED, EXPIRED }

enum DestructionCause { HOSTILE_CIV, MOB, PLAYER, UNKNOWN }

enum ResourceType {
    FOOD(2, 0.4f), WOOD(3, 1.0f), STONE(2, 1.4f), IRON(8, 1.6f), WOOL(3, 0.5f), LEATHER(4, 0.4f), COAL(5, 1.2f),
    GOLD(15, 1.4f), GEMS(25, 0.2f), HERBS(7, 0.1f), FISH(3, 0.3f), CLAY(2, 1.2f),
    STEEL(18, 1.8f), ENCHANTED_GOODS(40, 0.6f), ARTIFACTS(120, 0.8f);

    private final int baseValue;
    private final float weight;

    ResourceType(int baseValue, float weight) {
        this.baseValue = baseValue;
        this.weight = weight;
    }

    int baseValue() {
        return baseValue;
    }

    float weight() {
        return weight;
    }
}

final class NbtUtil {
    private NbtUtil() {}

    static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    static CompoundTag writeResourceMap(Map<ResourceType, Integer> values) {
        CompoundTag tag = new CompoundTag();
        for (ResourceType type : ResourceType.values()) {
            tag.putInt(type.name(), values.getOrDefault(type, 0));
        }
        return tag;
    }

    static Map<ResourceType, Integer> readResourceMap(CompoundTag tag) {
        Map<ResourceType, Integer> values = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            values.put(type, tag.getInt(type.name()));
        }
        return values;
    }

    static CompoundTag writeCivRepMap(Map<CivId, Integer> values) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<CivId, Integer> entry : values.entrySet()) {
            tag.putInt(entry.getKey().value().toString(), entry.getValue());
        }
        return tag;
    }

    static Map<CivId, Integer> readCivRepMap(CompoundTag tag) {
        Map<CivId, Integer> values = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            values.put(new CivId(UUID.fromString(key)), tag.getInt(key));
        }
        return values;
    }

    static ListTag writeUuidList(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            list.add(tag);
        }
        return list;
    }

    static List<UUID> readUuidList(ListTag list) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) ids.add(list.getCompound(i).getUUID("id"));
        return ids;
    }
}

final class Civilization {
    CivId id = CivId.random();
    String name = "Unnamed";
    CultureTrait primaryTrait = CultureTrait.MERCANTILE;
    CultureTrait secondaryTrait = CultureTrait.SCHOLARLY;
    TechTier techTier = TechTier.STONE;
    CivStatus status = CivStatus.RISING;
    Set<Long> territory = new HashSet<>();
    BlockPos capital = BlockPos.ZERO;
    int population = 80;
    int maxPopulation = 120;
    Map<ResourceType, Integer> stockpile = new EnumMap<>(ResourceType.class);
    Map<ResourceType, Integer> productionPerCycle = new EnumMap<>(ResourceType.class);
    Map<ResourceType, Integer> consumptionPerCycle = new EnumMap<>(ResourceType.class);
    int militaryStrength = 10;
    int garrisonCount = 8;
    int techProgress = 0;
    long expansionCooldownUntil = 0;
    List<UUID> namedNPCs = new ArrayList<>();
    List<Settlement> settlements = new ArrayList<>();
    List<TradeRouteId> activeTradeRoutes = new ArrayList<>();

    Civilization() {
        for (ResourceType type : ResourceType.values()) {
            stockpile.put(type, 0);
            productionPerCycle.put(type, 0);
            consumptionPerCycle.put(type, 0);
        }
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id.value());
        tag.putString("name", name);
        tag.putString("primaryTrait", primaryTrait.name());
        tag.putString("secondaryTrait", secondaryTrait.name());
        tag.putString("techTier", techTier.name());
        tag.putString("status", status.name());
        tag.putLongArray("territory", territory.stream().mapToLong(Long::longValue).toArray());
        tag.put("capital", NbtUtil.writePos(capital));
        tag.putInt("population", population);
        tag.putInt("maxPopulation", maxPopulation);
        tag.put("stockpile", NbtUtil.writeResourceMap(stockpile));
        tag.put("productionPerCycle", NbtUtil.writeResourceMap(productionPerCycle));
        tag.put("consumptionPerCycle", NbtUtil.writeResourceMap(consumptionPerCycle));
        tag.putInt("militaryStrength", militaryStrength);
        tag.putInt("garrisonCount", garrisonCount);
        tag.putInt("techProgress", techProgress);
        tag.putLong("expansionCooldownUntil", expansionCooldownUntil);
        tag.put("namedNPCs", NbtUtil.writeUuidList(namedNPCs));
        ListTag settlementTags = new ListTag();
        for (Settlement settlement : settlements) settlementTags.add(settlement.serializeNBT());
        tag.put("settlements", settlementTags);
        ListTag routeTags = new ListTag();
        for (TradeRouteId routeId : activeTradeRoutes) {
            CompoundTag routeTag = new CompoundTag();
            routeTag.putUUID("id", routeId.value());
            routeTags.add(routeTag);
        }
        tag.put("activeTradeRoutes", routeTags);
        return tag;
    }

    static Civilization deserializeNBT(CompoundTag tag) {
        Civilization civ = new Civilization();
        civ.id = new CivId(tag.getUUID("id"));
        civ.name = tag.getString("name");
        civ.primaryTrait = CultureTrait.valueOf(tag.getString("primaryTrait"));
        civ.secondaryTrait = CultureTrait.valueOf(tag.getString("secondaryTrait"));
        civ.techTier = TechTier.valueOf(tag.getString("techTier"));
        civ.status = CivStatus.valueOf(tag.getString("status"));
        civ.territory.clear();
        for (long chunk : tag.getLongArray("territory")) civ.territory.add(chunk);
        civ.capital = NbtUtil.readPos(tag.getCompound("capital"));
        civ.population = tag.getInt("population");
        civ.maxPopulation = tag.getInt("maxPopulation");
        civ.stockpile = NbtUtil.readResourceMap(tag.getCompound("stockpile"));
        civ.productionPerCycle = NbtUtil.readResourceMap(tag.getCompound("productionPerCycle"));
        civ.consumptionPerCycle = NbtUtil.readResourceMap(tag.getCompound("consumptionPerCycle"));
        civ.militaryStrength = tag.getInt("militaryStrength");
        civ.garrisonCount = tag.getInt("garrisonCount");
        civ.techProgress = tag.getInt("techProgress");
        civ.expansionCooldownUntil = tag.getLong("expansionCooldownUntil");
        civ.namedNPCs = NbtUtil.readUuidList(tag.getList("namedNPCs", Tag.TAG_COMPOUND));
        civ.settlements.clear();
        ListTag settlements = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < settlements.size(); i++) civ.settlements.add(Settlement.deserializeNBT(settlements.getCompound(i)));
        civ.activeTradeRoutes.clear();
        ListTag routes = tag.getList("activeTradeRoutes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routes.size(); i++) civ.activeTradeRoutes.add(new TradeRouteId(routes.getCompound(i).getUUID("id")));
        return civ;
    }
}

final class Settlement {
    UUID id = UUID.randomUUID();
    CivId owner = CivId.random();
    String name = "Unnamed";
    BlockPos center = BlockPos.ZERO;
    SettlementType type = SettlementType.VILLAGE;
    int populationLocal = 20;
    TechTier tier = TechTier.STONE;
    List<BlockPos> structures = new ArrayList<>();
    boolean generatedInWorld = false;
    Map<ResourceType, Integer> localMarket = new EnumMap<>(ResourceType.class);

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner", owner.value());
        tag.putString("name", name);
        tag.put("center", NbtUtil.writePos(center));
        tag.putString("type", type.name());
        tag.putInt("populationLocal", populationLocal);
        tag.putString("tier", tier.name());
        ListTag structureTags = new ListTag();
        for (BlockPos pos : structures) structureTags.add(NbtUtil.writePos(pos));
        tag.put("structures", structureTags);
        tag.putBoolean("generatedInWorld", generatedInWorld);
        tag.put("localMarket", NbtUtil.writeResourceMap(localMarket));
        return tag;
    }

    static Settlement deserializeNBT(CompoundTag tag) {
        Settlement settlement = new Settlement();
        settlement.id = tag.getUUID("id");
        settlement.owner = new CivId(tag.getUUID("owner"));
        settlement.name = tag.getString("name");
        settlement.center = NbtUtil.readPos(tag.getCompound("center"));
        settlement.type = SettlementType.valueOf(tag.getString("type"));
        settlement.populationLocal = tag.getInt("populationLocal");
        settlement.tier = TechTier.valueOf(tag.getString("tier"));
        settlement.structures.clear();
        ListTag structures = tag.getList("structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structures.size(); i++) settlement.structures.add(NbtUtil.readPos(structures.getCompound(i)));
        settlement.generatedInWorld = tag.getBoolean("generatedInWorld");
        settlement.localMarket = NbtUtil.readResourceMap(tag.getCompound("localMarket"));
        return settlement;
    }
}

final class ChronicaNPCData {
    UUID id = UUID.randomUUID();
    CivId civilization = CivId.random();
    UUID homeSettlementId = new UUID(0L, 0L);
    NPCRole role = NPCRole.CITIZEN;
    String name = "Unnamed";
    int age = 0;
    boolean alive = true;
    BlockPos lastKnownPos = BlockPos.ZERO;
    Map<UUID, List<MemoryEntry>> playerMemory = new HashMap<>();
    Map<UUID, Integer> npcRelations = new HashMap<>();
    UUID activeQuestId;

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("civilization", civilization.value());
        tag.putUUID("homeSettlementId", homeSettlementId);
        tag.putString("role", role.name());
        tag.putString("name", name);
        tag.putInt("age", age);
        tag.putBoolean("alive", alive);
        tag.put("lastKnownPos", NbtUtil.writePos(lastKnownPos));
        if (activeQuestId != null) tag.putUUID("activeQuestId", activeQuestId);
        ListTag memoryPlayers = new ListTag();
        for (Map.Entry<UUID, List<MemoryEntry>> entry : playerMemory.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("player", entry.getKey());
            ListTag memories = new ListTag();
            for (MemoryEntry memory : entry.getValue()) memories.add(memory.serializeNBT());
            playerTag.put("memories", memories);
            memoryPlayers.add(playerTag);
        }
        tag.put("playerMemory", memoryPlayers);
        CompoundTag relations = new CompoundTag();
        for (Map.Entry<UUID, Integer> relation : npcRelations.entrySet()) relations.putInt(relation.getKey().toString(), relation.getValue());
        tag.put("npcRelations", relations);
        return tag;
    }
}

record MemoryEntry(long worldTime, MemoryType type, int reputationDelta, String contextNote) {
    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("worldTime", worldTime);
        tag.putString("type", type.name());
        tag.putInt("reputationDelta", reputationDelta);
        tag.putString("contextNote", contextNote);
        return tag;
    }

    static MemoryEntry deserializeNBT(CompoundTag tag) {
        return new MemoryEntry(tag.getLong("worldTime"), MemoryType.valueOf(tag.getString("type")), tag.getInt("reputationDelta"), tag.getString("contextNote"));
    }
}

final class DiplomacyMatrix {
    private final Map<String, DiplomaticRelation> relations = new HashMap<>();

    DiplomaticRelation getRelation(CivId a, CivId b) {
        if (a.equals(b)) return DiplomaticRelation.neutral(0L);
        return relations.computeIfAbsent(key(a, b), ignored -> DiplomaticRelation.neutral(0L));
    }

    void setRelation(CivId a, CivId b, DiplomaticRelation rel) {
        if (!a.equals(b)) relations.put(key(a, b), rel);
    }

    Map<String, DiplomaticRelation> raw() {
        return relations;
    }

    private static String key(CivId a, CivId b) {
        List<UUID> ids = new ArrayList<>(List.of(a.value(), b.value()));
        ids.sort(Comparator.comparing(UUID::toString));
        return ids.get(0) + ":" + ids.get(1);
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, DiplomaticRelation> entry : relations.entrySet()) {
            CompoundTag rel = entry.getValue().serializeNBT();
            rel.putString("key", entry.getKey());
            list.add(rel);
        }
        tag.put("relations", list);
        return tag;
    }

    static DiplomacyMatrix deserializeNBT(CompoundTag tag) {
        DiplomacyMatrix matrix = new DiplomacyMatrix();
        ListTag list = tag.getList("relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag rel = list.getCompound(i);
            matrix.relations.put(rel.getString("key"), DiplomaticRelation.deserializeNBT(rel));
        }
        return matrix;
    }
}

final class DiplomaticRelation {
    RelationType type = RelationType.NEUTRAL;
    int trustScore = 0;
    long lastEventTime = 0;
    List<DiplomaticEvent> history = new ArrayList<>();

    static DiplomaticRelation neutral(long time) {
        DiplomaticRelation rel = new DiplomaticRelation();
        rel.lastEventTime = time;
        return rel;
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putInt("trustScore", trustScore);
        tag.putLong("lastEventTime", lastEventTime);
        ListTag events = new ListTag();
        for (DiplomaticEvent event : history) events.add(event.serializeNBT());
        tag.put("history", events);
        return tag;
    }

    static DiplomaticRelation deserializeNBT(CompoundTag tag) {
        DiplomaticRelation rel = new DiplomaticRelation();
        rel.type = RelationType.valueOf(tag.getString("type"));
        rel.trustScore = tag.getInt("trustScore");
        rel.lastEventTime = tag.getLong("lastEventTime");
        ListTag events = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < events.size(); i++) rel.history.add(DiplomaticEvent.deserializeNBT(events.getCompound(i)));
        return rel;
    }
}

record DiplomaticEvent(long worldTime, RelationType type, String summary) {
    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("worldTime", worldTime);
        tag.putString("type", type.name());
        tag.putString("summary", summary);
        return tag;
    }

    static DiplomaticEvent deserializeNBT(CompoundTag tag) {
        return new DiplomaticEvent(tag.getLong("worldTime"), RelationType.valueOf(tag.getString("type")), tag.getString("summary"));
    }
}

final class TradeRoute {
    TradeRouteId id = TradeRouteId.random();
    CivId sourceOwner = CivId.random();
    CivId destinationOwner = CivId.random();
    UUID sourceSettlementId = new UUID(0L, 0L);
    UUID destinationSettlementId = new UUID(0L, 0L);
    List<BlockPos> waypoints = new ArrayList<>();
    Map<ResourceType, Integer> cargo = new EnumMap<>(ResourceType.class);
    TradeRouteStatus status = TradeRouteStatus.ACTIVE;
    int cycleCount = 0;
    long nextDepartureTime = 0;
    UUID activeCaravanEntityId;

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id.value());
        tag.putUUID("sourceOwner", sourceOwner.value());
        tag.putUUID("destinationOwner", destinationOwner.value());
        tag.putUUID("sourceSettlementId", sourceSettlementId);
        tag.putUUID("destinationSettlementId", destinationSettlementId);
        ListTag waypointTags = new ListTag();
        for (BlockPos waypoint : waypoints) waypointTags.add(NbtUtil.writePos(waypoint));
        tag.put("waypoints", waypointTags);
        tag.put("cargo", NbtUtil.writeResourceMap(cargo));
        tag.putString("status", status.name());
        tag.putInt("cycleCount", cycleCount);
        tag.putLong("nextDepartureTime", nextDepartureTime);
        if (activeCaravanEntityId != null) tag.putUUID("activeCaravanEntityId", activeCaravanEntityId);
        return tag;
    }

    static TradeRoute deserializeNBT(CompoundTag tag) {
        TradeRoute route = new TradeRoute();
        route.id = new TradeRouteId(tag.getUUID("id"));
        route.sourceOwner = new CivId(tag.getUUID("sourceOwner"));
        route.destinationOwner = new CivId(tag.getUUID("destinationOwner"));
        route.sourceSettlementId = tag.getUUID("sourceSettlementId");
        route.destinationSettlementId = tag.getUUID("destinationSettlementId");
        route.waypoints.clear();
        ListTag waypoints = tag.getList("waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < waypoints.size(); i++) route.waypoints.add(NbtUtil.readPos(waypoints.getCompound(i)));
        route.cargo = NbtUtil.readResourceMap(tag.getCompound("cargo"));
        route.status = TradeRouteStatus.valueOf(tag.getString("status"));
        route.cycleCount = tag.getInt("cycleCount");
        route.nextDepartureTime = tag.getLong("nextDepartureTime");
        if (tag.hasUUID("activeCaravanEntityId")) route.activeCaravanEntityId = tag.getUUID("activeCaravanEntityId");
        return route;
    }
}

sealed interface HistoryEvent permits HistoryEvent.CivFounded, HistoryEvent.CivCollapsed, HistoryEvent.WarDeclared, HistoryEvent.PeaceTreaty, HistoryEvent.TradeRouteEstablished, HistoryEvent.SettlementRazed, HistoryEvent.TechAdvanced, HistoryEvent.PlayerIntervention, HistoryEvent.NaturalDisaster {
    UUID id();
    long worldTime();

    default String generateLoreText(NameGenerator nameGen) {
        return switch (this) {
            case CivFounded e -> "In the first records, " + e.founderName + " founded a people at " + e.location.toShortString() + ".";
            case CivCollapsed e -> "The banners of " + e.civId.value() + " fell silent after " + e.reason + ".";
            case WarDeclared e -> "War was declared: " + e.aggressor.value() + " marched against " + e.defender.value() + " because " + e.causeSummary + ".";
            case PeaceTreaty e -> "Peace returned between " + e.first.value() + " and " + e.second.value() + ": " + e.terms + ".";
            case TradeRouteEstablished e -> "A trade road opened between " + e.source.value() + " and " + e.destination.value() + ".";
            case SettlementRazed e -> "The settlement at " + e.location.toShortString() + " was razed. " + e.summary;
            case TechAdvanced e -> "The artisans of " + e.civId.value() + " entered the age of " + e.newTier.name().toLowerCase() + ".";
            case PlayerIntervention e -> "A traveler changed the balance of history: " + e.summary;
            case NaturalDisaster e -> "A disaster struck near " + e.location.toShortString() + ": " + e.summary;
        };
    }

    default CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id());
        tag.putLong("worldTime", worldTime());
        switch (this) {
            case CivFounded e -> { tag.putString("type", "civ_founded"); tag.putUUID("civId", e.civId.value()); tag.put("location", NbtUtil.writePos(e.location)); tag.putString("founderName", e.founderName); }
            case CivCollapsed e -> { tag.putString("type", "civ_collapsed"); tag.putUUID("civId", e.civId.value()); tag.putString("reason", e.reason); }
            case WarDeclared e -> { tag.putString("type", "war_declared"); tag.putUUID("aggressor", e.aggressor.value()); tag.putUUID("defender", e.defender.value()); tag.putString("causeSummary", e.causeSummary); }
            case PeaceTreaty e -> { tag.putString("type", "peace_treaty"); tag.putUUID("first", e.first.value()); tag.putUUID("second", e.second.value()); tag.putString("terms", e.terms); }
            case TradeRouteEstablished e -> { tag.putString("type", "trade_route_established"); tag.putUUID("route", e.route.value()); tag.putUUID("source", e.source.value()); tag.putUUID("destination", e.destination.value()); }
            case SettlementRazed e -> { tag.putString("type", "settlement_razed"); tag.putUUID("civId", e.civId.value()); tag.put("location", NbtUtil.writePos(e.location)); tag.putString("summary", e.summary); }
            case TechAdvanced e -> { tag.putString("type", "tech_advanced"); tag.putUUID("civId", e.civId.value()); tag.putString("newTier", e.newTier.name()); }
            case PlayerIntervention e -> { tag.putString("type", "player_intervention"); tag.putUUID("playerId", e.playerId); tag.putString("summary", e.summary); }
            case NaturalDisaster e -> { tag.putString("type", "natural_disaster"); tag.put("location", NbtUtil.writePos(e.location)); tag.putString("summary", e.summary); }
        }
        return tag;
    }

    static HistoryEvent deserializeNBT(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        long time = tag.getLong("worldTime");
        return switch (tag.getString("type")) {
            case "civ_founded" -> new CivFounded(id, time, new CivId(tag.getUUID("civId")), NbtUtil.readPos(tag.getCompound("location")), tag.getString("founderName"));
            case "civ_collapsed" -> new CivCollapsed(id, time, new CivId(tag.getUUID("civId")), tag.getString("reason"));
            case "war_declared" -> new WarDeclared(id, time, new CivId(tag.getUUID("aggressor")), new CivId(tag.getUUID("defender")), tag.getString("causeSummary"));
            case "peace_treaty" -> new PeaceTreaty(id, time, new CivId(tag.getUUID("first")), new CivId(tag.getUUID("second")), tag.getString("terms"));
            case "trade_route_established" -> new TradeRouteEstablished(id, time, new TradeRouteId(tag.getUUID("route")), new CivId(tag.getUUID("source")), new CivId(tag.getUUID("destination")));
            case "settlement_razed" -> new SettlementRazed(id, time, new CivId(tag.getUUID("civId")), NbtUtil.readPos(tag.getCompound("location")), tag.getString("summary"));
            case "tech_advanced" -> new TechAdvanced(id, time, new CivId(tag.getUUID("civId")), TechTier.valueOf(tag.getString("newTier")));
            case "player_intervention" -> new PlayerIntervention(id, time, tag.getUUID("playerId"), tag.getString("summary"));
            case "natural_disaster" -> new NaturalDisaster(id, time, NbtUtil.readPos(tag.getCompound("location")), tag.getString("summary"));
            default -> new NaturalDisaster(id, time, BlockPos.ZERO, "Unknown corrupted history event");
        };
    }

    record CivFounded(UUID id, long worldTime, CivId civId, BlockPos location, String founderName) implements HistoryEvent {}
    record CivCollapsed(UUID id, long worldTime, CivId civId, String reason) implements HistoryEvent {}
    record WarDeclared(UUID id, long worldTime, CivId aggressor, CivId defender, String causeSummary) implements HistoryEvent {}
    record PeaceTreaty(UUID id, long worldTime, CivId first, CivId second, String terms) implements HistoryEvent {}
    record TradeRouteEstablished(UUID id, long worldTime, TradeRouteId route, CivId source, CivId destination) implements HistoryEvent {}
    record SettlementRazed(UUID id, long worldTime, CivId civId, BlockPos location, String summary) implements HistoryEvent {}
    record TechAdvanced(UUID id, long worldTime, CivId civId, TechTier newTier) implements HistoryEvent {}
    record PlayerIntervention(UUID id, long worldTime, UUID playerId, String summary) implements HistoryEvent {}
    record NaturalDisaster(UUID id, long worldTime, BlockPos location, String summary) implements HistoryEvent {}
}

final class ChronicaQuest {
    UUID id = UUID.randomUUID();
    QuestType type = QuestType.DELIVERY;
    UUID sourceNpcId;
    UUID targetNpcId;
    CivId sourceCiv = CivId.random();
    CivId targetCiv;
    BlockPos targetLocation = BlockPos.ZERO;
    Map<ResourceType, Integer> rewardGoods = new EnumMap<>(ResourceType.class);
    int rewardReputation = 0;
    QuestStatus status = QuestStatus.AVAILABLE;
    long generatedAtTime = 0;
    long expiresAtTime = 0;
    UUID assignedPlayerId;
    QuestConditionSnapshot generationContext = QuestConditionSnapshot.empty();
    String summary = "";

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        if (sourceNpcId != null) tag.putUUID("sourceNpcId", sourceNpcId);
        if (targetNpcId != null) tag.putUUID("targetNpcId", targetNpcId);
        tag.putUUID("sourceCiv", sourceCiv.value());
        if (targetCiv != null) tag.putUUID("targetCiv", targetCiv.value());
        tag.put("targetLocation", NbtUtil.writePos(targetLocation));
        tag.put("rewardGoods", NbtUtil.writeResourceMap(rewardGoods));
        tag.putInt("rewardReputation", rewardReputation);
        tag.putString("status", status.name());
        tag.putLong("generatedAtTime", generatedAtTime);
        tag.putLong("expiresAtTime", expiresAtTime);
        if (assignedPlayerId != null) tag.putUUID("assignedPlayerId", assignedPlayerId);
        tag.put("generationContext", generationContext.serializeNBT());
        tag.putString("summary", summary);
        return tag;
    }

    static ChronicaQuest deserializeNBT(CompoundTag tag) {
        ChronicaQuest quest = new ChronicaQuest();
        quest.id = tag.getUUID("id");
        quest.type = QuestType.valueOf(tag.getString("type"));
        if (tag.hasUUID("sourceNpcId")) quest.sourceNpcId = tag.getUUID("sourceNpcId");
        if (tag.hasUUID("targetNpcId")) quest.targetNpcId = tag.getUUID("targetNpcId");
        quest.sourceCiv = new CivId(tag.getUUID("sourceCiv"));
        if (tag.hasUUID("targetCiv")) quest.targetCiv = new CivId(tag.getUUID("targetCiv"));
        quest.targetLocation = NbtUtil.readPos(tag.getCompound("targetLocation"));
        quest.rewardGoods = NbtUtil.readResourceMap(tag.getCompound("rewardGoods"));
        quest.rewardReputation = tag.getInt("rewardReputation");
        quest.status = QuestStatus.valueOf(tag.getString("status"));
        quest.generatedAtTime = tag.getLong("generatedAtTime");
        quest.expiresAtTime = tag.getLong("expiresAtTime");
        if (tag.hasUUID("assignedPlayerId")) quest.assignedPlayerId = tag.getUUID("assignedPlayerId");
        quest.generationContext = QuestConditionSnapshot.deserializeNBT(tag.getCompound("generationContext"));
        quest.summary = tag.getString("summary");
        return quest;
    }
}

record QuestConditionSnapshot(Map<ResourceType, Integer> sourceCivStockpile, RelationType civRelationAtGeneration, boolean targetNpcAliveAtGeneration) {
    static QuestConditionSnapshot empty() {
        return new QuestConditionSnapshot(new EnumMap<>(ResourceType.class), RelationType.NEUTRAL, true);
    }

    boolean deviatesFrom(Civilization civ, RelationType currentRelation, boolean targetNpcAlive, int threshold) {
        if (civRelationAtGeneration != currentRelation) return true;
        if (targetNpcAliveAtGeneration && !targetNpcAlive) return true;
        int changed = 0;
        for (ResourceType type : ResourceType.values()) {
            int oldValue = sourceCivStockpile.getOrDefault(type, 0);
            int current = civ.stockpile.getOrDefault(type, 0);
            if (Math.abs(oldValue - current) > threshold) changed++;
        }
        return changed >= 3;
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("sourceCivStockpile", NbtUtil.writeResourceMap(sourceCivStockpile));
        tag.putString("civRelationAtGeneration", civRelationAtGeneration.name());
        tag.putBoolean("targetNpcAliveAtGeneration", targetNpcAliveAtGeneration);
        return tag;
    }

    static QuestConditionSnapshot deserializeNBT(CompoundTag tag) {
        return new QuestConditionSnapshot(
                NbtUtil.readResourceMap(tag.getCompound("sourceCivStockpile")),
                RelationType.valueOf(tag.getString("civRelationAtGeneration")),
                tag.getBoolean("targetNpcAliveAtGeneration")
        );
    }
}

final class QuestPool {
    final Map<UUID, ChronicaQuest> quests = new HashMap<>();
    final Map<String, Long> cooldowns = new HashMap<>();

    boolean hasSimilar(QuestType type, CivId source, CivId target) {
        for (ChronicaQuest quest : quests.values()) {
            if (quest.status == QuestStatus.COMPLETED || quest.status == QuestStatus.FAILED || quest.status == QuestStatus.EXPIRED) continue;
            if (quest.type == type && quest.sourceCiv.equals(source) && java.util.Objects.equals(quest.targetCiv, target)) return true;
        }
        return false;
    }

    boolean isOnCooldown(CivId civ, QuestType type, long now) {
        return cooldowns.getOrDefault(civ.value() + ":" + type.name(), 0L) > now;
    }

    void setCooldown(CivId civ, QuestType type, long until) {
        cooldowns.put(civ.value() + ":" + type.name(), until);
    }

    long activeCount() {
        return quests.values().stream().filter(q -> q.status == QuestStatus.AVAILABLE || q.status == QuestStatus.ACTIVE).count();
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ChronicaQuest quest : quests.values()) list.add(quest.serializeNBT());
        tag.put("quests", list);
        CompoundTag cooldownTag = new CompoundTag();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) cooldownTag.putLong(entry.getKey(), entry.getValue());
        tag.put("cooldowns", cooldownTag);
        return tag;
    }

    static QuestPool deserializeNBT(CompoundTag tag) {
        QuestPool pool = new QuestPool();
        ListTag list = tag.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ChronicaQuest quest = ChronicaQuest.deserializeNBT(list.getCompound(i));
            pool.quests.put(quest.id, quest);
        }
        CompoundTag cooldownTag = tag.getCompound("cooldowns");
        for (String key : cooldownTag.getAllKeys()) pool.cooldowns.put(key, cooldownTag.getLong(key));
        return pool;
    }
}

final class PlayerWorldState {
    UUID playerId = new UUID(0L, 0L);
    Map<CivId, Integer> reputation = new HashMap<>();
    List<UUID> activeQuests = new ArrayList<>();
    List<UUID> completedQuests = new ArrayList<>();
    Map<UUID, String> knownNPCs = new HashMap<>();
    boolean hasChronicaBook = false;

    int reputation(CivId civ) {
        return reputation.getOrDefault(civ, 0);
    }

    void addReputation(CivId civ, int delta) {
        reputation.put(civ, Math.max(-100, Math.min(100, reputation(civ) + delta)));
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("playerId", playerId);
        tag.put("reputation", NbtUtil.writeCivRepMap(reputation));
        tag.put("activeQuests", NbtUtil.writeUuidList(activeQuests));
        tag.put("completedQuests", NbtUtil.writeUuidList(completedQuests));
        CompoundTag known = new CompoundTag();
        for (Map.Entry<UUID, String> entry : knownNPCs.entrySet()) known.putString(entry.getKey().toString(), entry.getValue());
        tag.put("knownNPCs", known);
        tag.putBoolean("hasChronicaBook", hasChronicaBook);
        return tag;
    }

    static PlayerWorldState deserializeNBT(CompoundTag tag) {
        PlayerWorldState state = new PlayerWorldState();
        state.playerId = tag.getUUID("playerId");
        state.reputation = NbtUtil.readCivRepMap(tag.getCompound("reputation"));
        state.activeQuests = NbtUtil.readUuidList(tag.getList("activeQuests", Tag.TAG_COMPOUND));
        state.completedQuests = NbtUtil.readUuidList(tag.getList("completedQuests", Tag.TAG_COMPOUND));
        CompoundTag known = tag.getCompound("knownNPCs");
        for (String key : known.getAllKeys()) state.knownNPCs.put(UUID.fromString(key), known.getString(key));
        state.hasChronicaBook = tag.getBoolean("hasChronicaBook");
        return state;
    }
}

final class TerritoryMap {
    private final Map<Long, CivId> owners = new HashMap<>();
    private final ArrayDeque<Long> dirtyChunks = new ArrayDeque<>();

    Optional<CivId> ownerOf(ChunkPos pos) {
        return Optional.ofNullable(owners.get(pos.toLong()));
    }

    Optional<CivId> ownerOf(long chunkLong) {
        return Optional.ofNullable(owners.get(chunkLong));
    }

    boolean isClaimed(long chunkLong) {
        return owners.containsKey(chunkLong);
    }

    void claim(CivId civ, long chunkLong) {
        owners.put(chunkLong, civ);
        dirtyChunks.add(chunkLong);
    }

    void unclaim(long chunkLong) {
        owners.remove(chunkLong);
        dirtyChunks.add(chunkLong);
    }

    Map<Long, CivId> raw() {
        return owners;
    }

    CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<Long, CivId> entry : owners.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putLong("chunk", entry.getKey());
            item.putUUID("civ", entry.getValue().value());
            list.add(item);
        }
        tag.put("owners", list);
        return tag;
    }

    static TerritoryMap deserializeNBT(CompoundTag tag) {
        TerritoryMap map = new TerritoryMap();
        ListTag list = tag.getList("owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            map.owners.put(item.getLong("chunk"), new CivId(item.getUUID("civ")));
        }
        return map;
    }
}
