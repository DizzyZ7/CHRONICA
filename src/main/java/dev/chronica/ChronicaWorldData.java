package dev.chronica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChronicaWorldData extends SavedData {
    public static final String DATA_NAME = "chronica_world";
    private static final int MAX_HISTORY = 10_000;

    public final Map<CivId, Civilization> civilizations = new HashMap<>();
    public final List<HistoryEvent> historyLog = new ArrayList<>();
    public DiplomacyMatrix diplomacyMatrix = new DiplomacyMatrix();
    public final Map<TradeRouteId, TradeRoute> tradeRoutes = new HashMap<>();
    public QuestPool questPool = new QuestPool();
    public final Map<UUID, PlayerWorldState> playerStates = new HashMap<>();
    public TerritoryMap territoryMap = new TerritoryMap();
    public final Map<UUID, ChronicaNPCData> namedNpcs = new HashMap<>();
    public long worldSeed = 0L;
    public long chronologicalAge = 0L;
    public boolean initialized = false;

    public static ChronicaWorldData create() {
        return new ChronicaWorldData();
    }

    public static ChronicaWorldData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ChronicaWorldData::create, ChronicaWorldData::load), DATA_NAME);
    }

    public static ChronicaWorldData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        ChronicaWorldData data = new ChronicaWorldData();
        data.worldSeed = tag.getLong("worldSeed");
        data.chronologicalAge = tag.getLong("chronologicalAge");
        data.initialized = tag.getBoolean("initialized");

        ListTag civTags = tag.getList("civilizations", Tag.TAG_COMPOUND);
        for (int i = 0; i < civTags.size(); i++) {
            Civilization civ = Civilization.deserializeNBT(civTags.getCompound(i));
            data.civilizations.put(civ.id, civ);
        }

        ListTag historyTags = tag.getList("historyLog", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyTags.size(); i++) {
            data.historyLog.add(HistoryEvent.deserializeNBT(historyTags.getCompound(i)));
        }

        data.diplomacyMatrix = DiplomacyMatrix.deserializeNBT(tag.getCompound("diplomacyMatrix"));

        ListTag routeTags = tag.getList("tradeRoutes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTags.size(); i++) {
            TradeRoute route = TradeRoute.deserializeNBT(routeTags.getCompound(i));
            data.tradeRoutes.put(route.id, route);
        }

        data.questPool = QuestPool.deserializeNBT(tag.getCompound("questPool"));

        ListTag playerTags = tag.getList("playerStates", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerTags.size(); i++) {
            PlayerWorldState state = PlayerWorldState.deserializeNBT(playerTags.getCompound(i));
            data.playerStates.put(state.playerId, state);
        }

        data.territoryMap = TerritoryMap.deserializeNBT(tag.getCompound("territoryMap"));

        ListTag npcTags = tag.getList("namedNpcs", Tag.TAG_COMPOUND);
        for (int i = 0; i < npcTags.size(); i++) {
            ChronicaNPCData npc = readNpcData(npcTags.getCompound(i));
            data.namedNpcs.put(npc.id, npc);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("worldSeed", worldSeed);
        tag.putLong("chronologicalAge", chronologicalAge);
        tag.putBoolean("initialized", initialized);

        ListTag civTags = new ListTag();
        for (Civilization civ : civilizations.values()) civTags.add(civ.serializeNBT());
        tag.put("civilizations", civTags);

        ListTag historyTags = new ListTag();
        int start = Math.max(0, historyLog.size() - MAX_HISTORY);
        for (int i = start; i < historyLog.size(); i++) historyTags.add(historyLog.get(i).serializeNBT());
        tag.put("historyLog", historyTags);

        tag.put("diplomacyMatrix", diplomacyMatrix.serializeNBT());

        ListTag routeTags = new ListTag();
        for (TradeRoute route : tradeRoutes.values()) routeTags.add(route.serializeNBT());
        tag.put("tradeRoutes", routeTags);

        tag.put("questPool", questPool.serializeNBT());

        ListTag playerTags = new ListTag();
        for (PlayerWorldState state : playerStates.values()) playerTags.add(state.serializeNBT());
        tag.put("playerStates", playerTags);

        tag.put("territoryMap", territoryMap.serializeNBT());

        ListTag npcTags = new ListTag();
        for (ChronicaNPCData npc : namedNpcs.values()) npcTags.add(npc.serializeNBT());
        tag.put("namedNpcs", npcTags);
        return tag;
    }

    public void addHistory(HistoryEvent event) {
        historyLog.add(event);
        while (historyLog.size() > MAX_HISTORY) historyLog.remove(0);
        setDirty();
    }

    public PlayerWorldState playerState(UUID playerId) {
        PlayerWorldState state = playerStates.computeIfAbsent(playerId, id -> {
            PlayerWorldState created = new PlayerWorldState();
            created.playerId = id;
            return created;
        });
        setDirty();
        return state;
    }

    private static ChronicaNPCData readNpcData(CompoundTag tag) {
        ChronicaNPCData npc = new ChronicaNPCData();
        npc.id = tag.getUUID("id");
        npc.civilization = new CivId(tag.getUUID("civilization"));
        npc.homeSettlementId = tag.getUUID("homeSettlementId");
        npc.role = NPCRole.valueOf(tag.getString("role"));
        npc.name = tag.getString("name");
        npc.age = tag.getInt("age");
        npc.alive = tag.getBoolean("alive");
        npc.lastKnownPos = NbtUtil.readPos(tag.getCompound("lastKnownPos"));
        if (tag.hasUUID("activeQuestId")) npc.activeQuestId = tag.getUUID("activeQuestId");

        ListTag memoryPlayers = tag.getList("playerMemory", Tag.TAG_COMPOUND);
        for (int i = 0; i < memoryPlayers.size(); i++) {
            CompoundTag playerTag = memoryPlayers.getCompound(i);
            UUID player = playerTag.getUUID("player");
            List<MemoryEntry> memories = new ArrayList<>();
            ListTag memoryTags = playerTag.getList("memories", Tag.TAG_COMPOUND);
            for (int j = 0; j < memoryTags.size(); j++) memories.add(MemoryEntry.deserializeNBT(memoryTags.getCompound(j)));
            npc.playerMemory.put(player, memories);
        }

        CompoundTag relations = tag.getCompound("npcRelations");
        for (String key : relations.getAllKeys()) npc.npcRelations.put(UUID.fromString(key), relations.getInt(key));
        return npc;
    }
}
