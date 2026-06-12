package dev.chronica;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class ChronicaServerEvents {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ChronicaNPCEntity npc) || !(entity.level() instanceof ServerLevel level)) return;
        ChronicaWorldData data = ChronicaWorldData.getOrCreate(level);
        if (npc.chronicaNpcId() != null) {
            ChronicaNPCData npcData = data.namedNpcs.get(npc.chronicaNpcId());
            if (npcData != null) {
                npcData.alive = false;
                npcData.lastKnownPos = npc.blockPosition();
                data.setDirty();
            }
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ChronicaWorldData data = ChronicaWorldData.getOrCreate(level);
        data.territoryMap.ownerOf(event.getChunk().getPos()).ifPresent(civId -> {
            if (ChronicaCommonConfig.CONFIG.logSimulationTicks.get()) {
                ChronicaMod.LOGGER.debug("Loaded CHRONICA territory chunk {} owned by {}", event.getChunk().getPos(), civId.value());
            }
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getState().is(ChronicaRegistration.EPITAPH_STONE.get()) && event.getPlayer() instanceof Player player) {
            ChronicaWorldData data = ChronicaWorldData.getOrCreate(level);
            Civilization civ = data.territoryMap.ownerOf(new net.minecraft.world.level.ChunkPos(event.getPos())).map(data.civilizations::get).orElse(null);
            if (civ != null) {
                data.playerState(player.getUUID()).addReputation(civ.id, -5);
                data.addHistory(new HistoryEvent.PlayerIntervention(java.util.UUID.randomUUID(), level.getGameTime(), player.getUUID(), "broke an epitaph stone in " + civ.name));
            }
        }
    }

    public static final class ChronicaEvents {
        public static class TerritoryExpandedEvent extends Event {
            public final CivId civilization;
            public final net.minecraft.world.level.ChunkPos chunk;
            public TerritoryExpandedEvent(CivId civilization, net.minecraft.world.level.ChunkPos chunk) { this.civilization = civilization; this.chunk = chunk; }
        }
        public static class WarDeclaredEvent extends Event {
            public final CivId aggressor;
            public final CivId defender;
            public final net.minecraft.core.BlockPos conflictZoneCenter;
            public WarDeclaredEvent(CivId aggressor, CivId defender, net.minecraft.core.BlockPos conflictZoneCenter) { this.aggressor = aggressor; this.defender = defender; this.conflictZoneCenter = conflictZoneCenter; }
        }
        public static class PlayerReputationChangedEvent extends Event {
            public final java.util.UUID playerId;
            public final CivId civilization;
            public final int oldValue;
            public final int newValue;
            public final String reason;
            public PlayerReputationChangedEvent(java.util.UUID playerId, CivId civilization, int oldValue, int newValue, String reason) { this.playerId = playerId; this.civilization = civilization; this.oldValue = oldValue; this.newValue = newValue; this.reason = reason; }
        }
        public static class CaravanSpawnedEvent extends Event {
            public final TradeRouteId route;
            public CaravanSpawnedEvent(TradeRouteId route) { this.route = route; }
        }
        public static class CaravanDestroyedEvent extends Event {
            public final TradeRouteId route;
            public final DestructionCause cause;
            public CaravanDestroyedEvent(TradeRouteId route, DestructionCause cause) { this.route = route; this.cause = cause; }
        }
        public static class QuestStateChangedEvent extends Event {
            public final java.util.UUID questId;
            public final QuestStatus oldStatus;
            public final QuestStatus newStatus;
            public QuestStateChangedEvent(java.util.UUID questId, QuestStatus oldStatus, QuestStatus newStatus) { this.questId = questId; this.oldStatus = oldStatus; this.newStatus = newStatus; }
        }
    }

    static void fireQuestStateChanged(java.util.UUID questId, QuestStatus oldStatus, QuestStatus newStatus) {
        NeoForge.EVENT_BUS.post(new ChronicaEvents.QuestStateChangedEvent(questId, oldStatus, newStatus));
    }
}
