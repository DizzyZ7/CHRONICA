package dev.chronica;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class DiplomacySimulator extends ChronicaSubSimulator {
    public DiplomacySimulator(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long start = System.nanoTime();
        List<Civilization> civs = new ArrayList<>(data.civilizations.values());
        outer:
        for (int i = 0; i < civs.size(); i++) {
            for (int j = i + 1; j < civs.size(); j++) {
                if (System.nanoTime() - start > budgetNanos) break outer;
                evaluateDecision(civs.get(i), civs.get(j), level.getGameTime());
            }
        }
    }

    public DiplomaticDecision evaluateDecision(Civilization a, Civilization b, long now) {
        if (a.status == CivStatus.COLLAPSED || b.status == CivStatus.COLLAPSED) return DiplomaticDecision.NONE;
        DiplomaticRelation relation = data.diplomacyMatrix.getRelation(a.id, b.id);
        Random random = new Random(data.worldSeed ^ a.id.value().getMostSignificantBits() ^ b.id.value().getLeastSignificantBits() ^ now / 200L);
        double variance = 0.8 + random.nextDouble() * 0.4;

        if (relation.type != RelationType.WAR && relation.trustScore < -70 && a.militaryStrength > b.militaryStrength * 1.2 * variance && ChronicaCommonConfig.CONFIG.enableWar.get()) {
            relation.type = RelationType.WAR;
            relation.lastEventTime = now;
            relation.history.add(new DiplomaticEvent(now, RelationType.WAR, a.name + " declared war on " + b.name));
            data.addHistory(new HistoryEvent.WarDeclared(UUID.randomUUID(), now, a.id, b.id, "rivalry and weak trust"));
            data.setDirty();
            return DiplomaticDecision.DECLARE_WAR;
        }

        if (relation.type == RelationType.WAR && a.militaryStrength < b.militaryStrength * 0.5 * variance) {
            relation.type = RelationType.NEUTRAL;
            relation.trustScore = Math.min(10, relation.trustScore + 25);
            relation.lastEventTime = now;
            relation.history.add(new DiplomaticEvent(now, RelationType.NEUTRAL, a.name + " requested peace with " + b.name));
            data.addHistory(new HistoryEvent.PeaceTreaty(UUID.randomUUID(), now, a.id, b.id, "exhaustion after war"));
            data.setDirty();
            return DiplomaticDecision.PROPOSE_PEACE;
        }

        if (relation.type == RelationType.NEUTRAL && hasComplementaryResources(a, b) && relation.trustScore > -20) {
            relation.type = RelationType.TRADE_PACT;
            relation.trustScore = Math.min(100, relation.trustScore + 10);
            relation.lastEventTime = now;
            relation.history.add(new DiplomaticEvent(now, RelationType.TRADE_PACT, a.name + " opened trade with " + b.name));
            data.setDirty();
            return DiplomaticDecision.PROPOSE_TRADE;
        }

        if (relation.type == RelationType.TRADE_PACT && relation.trustScore > 45 && commonEnemy(a, b)) {
            relation.type = RelationType.ALLIANCE;
            relation.trustScore = Math.min(100, relation.trustScore + 15);
            relation.lastEventTime = now;
            relation.history.add(new DiplomaticEvent(now, RelationType.ALLIANCE, a.name + " allied with " + b.name));
            data.setDirty();
            return DiplomaticDecision.PROPOSE_ALLIANCE;
        }

        passiveTrustDrift(relation, now);
        return DiplomaticDecision.NONE;
    }

    private boolean hasComplementaryResources(Civilization a, Civilization b) {
        for (ResourceType resource : ResourceType.values()) {
            if (EconomySimulator.demand(a, resource) > 15 && EconomySimulator.supply(b, resource) > 15) return true;
            if (EconomySimulator.demand(b, resource) > 15 && EconomySimulator.supply(a, resource) > 15) return true;
        }
        return false;
    }

    private boolean commonEnemy(Civilization a, Civilization b) {
        for (Civilization other : data.civilizations.values()) {
            if (other.id.equals(a.id) || other.id.equals(b.id)) continue;
            if (data.diplomacyMatrix.getRelation(a.id, other.id).type == RelationType.WAR
                    && data.diplomacyMatrix.getRelation(b.id, other.id).type == RelationType.WAR) {
                return true;
            }
        }
        return false;
    }

    private static void passiveTrustDrift(DiplomaticRelation relation, long now) {
        if (now - relation.lastEventTime < 24_000L) return;
        if (relation.trustScore > 0) relation.trustScore--;
        if (relation.trustScore < 0) relation.trustScore++;
        relation.lastEventTime = now;
    }
}

enum DiplomaticDecision {
    NONE, DECLARE_WAR, PROPOSE_TRADE, PROPOSE_PEACE, PROPOSE_ALLIANCE
}
