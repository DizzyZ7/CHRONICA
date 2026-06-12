package dev.chronica;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

final class DiplomacySimulatorTest {
    @Test
    void neutralRelationDefaultsToNeutral() {
        DiplomacyMatrix matrix = new DiplomacyMatrix();
        CivId a = CivId.random();
        CivId b = CivId.random();
        assertEquals(RelationType.NEUTRAL, matrix.getRelation(a, b).type);
    }

    @Test
    @Disabled("Requires ServerLevel-backed DiplomacySimulator for full evaluateDecision execution.")
    void evaluateDecisionCanDeclareWarTradePeaceOrAlliance() {
    }
}
