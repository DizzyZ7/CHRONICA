package dev.chronica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class QuestEngine extends ChronicaSubSimulator {
    public QuestEngine(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long start = System.nanoTime();
        expireInvalidQuests();
        if (System.nanoTime() - start > budgetNanos) return;
        generateQuestsFromWorldState(budgetNanos - (System.nanoTime() - start));
    }

    public List<ChronicaQuest> generateQuestsFromWorldState(long budgetNanos) {
        long start = System.nanoTime();
        long now = level.getGameTime();
        List<ChronicaQuest> generated = new ArrayList<>();
        List<Civilization> civs = new ArrayList<>(data.civilizations.values());
        civs.sort(Comparator.comparing(c -> c.name));

        while (processingCursor < civs.size()) {
            if (System.nanoTime() - start > budgetNanos) break;
            if (data.questPool.activeCount() >= ChronicaCommonConfig.CONFIG.globalActiveQuestCap.get()) break;

            Civilization civ = civs.get(processingCursor++);
            if (civ.status == CivStatus.COLLAPSED) continue;

            findResourceDeficitQuest(civ, now).ifPresent(quest -> {
                data.questPool.quests.put(quest.id, quest);
                data.questPool.setCooldown(civ.id, quest.type, now + ChronicaCommonConfig.CONFIG.perCivTypeCooldownTicks.get());
                generated.add(quest);
            });

            if (ChronicaCommonConfig.CONFIG.enableDiplomacyQuests.get()) {
                findDiplomacyQuest(civ, now).ifPresent(quest -> {
                    data.questPool.quests.put(quest.id, quest);
                    data.questPool.setCooldown(civ.id, quest.type, now + ChronicaCommonConfig.CONFIG.perCivTypeCooldownTicks.get());
                    generated.add(quest);
                });
            }
        }
        if (processingCursor >= civs.size()) processingCursor = 0;
        if (!generated.isEmpty()) data.setDirty();
        return generated;
    }

    private Optional<ChronicaQuest> findResourceDeficitQuest(Civilization civ, long now) {
        if (data.questPool.isOnCooldown(civ.id, QuestType.ESCORT, now)) return Optional.empty();
        ResourceType deficit = strongestDeficit(civ).orElse(null);
        if (deficit == null) return Optional.empty();

        TradeRoute route = data.tradeRoutes.values().stream()
                .filter(r -> r.destinationOwner.equals(civ.id) || r.sourceOwner.equals(civ.id))
                .filter(r -> r.status == TradeRouteStatus.BLOCKED || r.cargo.getOrDefault(deficit, 0) > 0)
                .findFirst()
                .orElse(null);
        if (route == null) return Optional.empty();
        CivId target = route.sourceOwner.equals(civ.id) ? route.destinationOwner : route.sourceOwner;
        if (data.questPool.hasSimilar(QuestType.ESCORT, civ.id, target)) return Optional.empty();

        ChronicaQuest quest = baseQuest(QuestType.ESCORT, civ, target, now);
        quest.targetLocation = settlementCenter(target).orElse(civ.capital);
        quest.rewardGoods.put(deficit, Math.max(2, Math.min(12, Math.abs(civ.stockpile.getOrDefault(deficit, 0)) / 5)));
        quest.rewardReputation = 15;
        quest.summary = "Escort a caravan carrying " + deficit.name().toLowerCase() + " for " + civ.name + ".";
        return Optional.of(quest);
    }

    private Optional<ChronicaQuest> findDiplomacyQuest(Civilization civ, long now) {
        if (data.questPool.isOnCooldown(civ.id, QuestType.DIPLOMACY, now)) return Optional.empty();
        for (Civilization other : data.civilizations.values()) {
            if (other.id.equals(civ.id) || other.status == CivStatus.COLLAPSED) continue;
            DiplomaticRelation relation = data.diplomacyMatrix.getRelation(civ.id, other.id);
            if (relation.type != RelationType.WAR && relation.trustScore > -70) continue;
            if (data.questPool.hasSimilar(QuestType.DIPLOMACY, civ.id, other.id)) continue;
            boolean hasTrustedPlayer = data.playerStates.values().stream()
                    .anyMatch(p -> p.reputation(civ.id) >= 50 && p.reputation(other.id) >= 30);
            if (!hasTrustedPlayer) continue;

            ChronicaQuest quest = baseQuest(QuestType.DIPLOMACY, civ, other.id, now);
            quest.targetLocation = settlementCenter(other.id).orElse(other.capital);
            quest.rewardGoods.put(ResourceType.GOLD, 8);
            quest.rewardReputation = 30;
            quest.summary = "Carry a peace proposal from " + civ.name + " to " + other.name + ".";
            quest.generationContext = new QuestConditionSnapshot(new EnumMap<>(civ.stockpile), relation.type, true);
            return Optional.of(quest);
        }
        return Optional.empty();
    }

    private ChronicaQuest baseQuest(QuestType type, Civilization civ, CivId target, long now) {
        ChronicaQuest quest = new ChronicaQuest();
        quest.id = UUID.randomUUID();
        quest.type = type;
        quest.sourceCiv = civ.id;
        quest.targetCiv = target;
        quest.generatedAtTime = now;
        quest.expiresAtTime = now + ChronicaCommonConfig.CONFIG.questExpiryCycles.get() * ChronicaCommonConfig.CONFIG.questTickInterval.get();
        quest.sourceNpcId = chooseSourceNpc(civ, type).orElse(null);
        quest.generationContext = new QuestConditionSnapshot(new EnumMap<>(civ.stockpile), target == null ? RelationType.NEUTRAL : data.diplomacyMatrix.getRelation(civ.id, target).type, true);
        return quest;
    }

    private Optional<UUID> chooseSourceNpc(Civilization civ, QuestType type) {
        NPCRole wanted = switch (type) {
            case ESCORT, DELIVERY -> NPCRole.MERCHANT;
            case DIPLOMACY -> NPCRole.DIPLOMAT;
            case RESCUE, SCOUTING -> NPCRole.ELDER;
            case RETRIEVAL -> NPCRole.ELDER;
        };
        return civ.namedNPCs.stream()
                .map(data.namedNpcs::get)
                .filter(npc -> npc != null && npc.alive)
                .filter(npc -> npc.role == wanted || npc.role == NPCRole.ELDER)
                .map(npc -> npc.id)
                .findFirst();
    }

    private Optional<ResourceType> strongestDeficit(Civilization civ) {
        return civ.stockpile.entrySet().stream()
                .filter(e -> e.getValue() < -10)
                .max(Comparator.comparingInt(e -> Math.abs(e.getValue()) * e.getKey().baseValue()))
                .map(Map.Entry::getKey);
    }

    private Optional<BlockPos> settlementCenter(CivId civId) {
        Civilization civ = data.civilizations.get(civId);
        if (civ == null || civ.settlements.isEmpty()) return Optional.empty();
        return Optional.of(civ.settlements.getFirst().center);
    }

    private void expireInvalidQuests() {
        long now = level.getGameTime();
        for (ChronicaQuest quest : data.questPool.quests.values()) {
            if (quest.status != QuestStatus.AVAILABLE && quest.status != QuestStatus.ACTIVE) continue;
            Civilization source = data.civilizations.get(quest.sourceCiv);
            if (source == null || source.status == CivStatus.COLLAPSED || now > quest.expiresAtTime) {
                quest.status = QuestStatus.EXPIRED;
                continue;
            }
            RelationType relation = quest.targetCiv == null ? RelationType.NEUTRAL : data.diplomacyMatrix.getRelation(quest.sourceCiv, quest.targetCiv).type;
            if (quest.generationContext.deviatesFrom(source, relation, true, 250)) quest.status = QuestStatus.EXPIRED;
        }
    }
}
