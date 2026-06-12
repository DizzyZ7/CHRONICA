package dev.chronica;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

final class QuestEngineTest {
    @Test
    void questPoolDetectsSimilarActiveQuest() {
        QuestPool pool = new QuestPool();
        CivId a = CivId.random();
        CivId b = CivId.random();
        ChronicaQuest quest = new ChronicaQuest();
        quest.type = QuestType.ESCORT;
        quest.sourceCiv = a;
        quest.targetCiv = b;
        quest.status = QuestStatus.AVAILABLE;
        pool.quests.put(quest.id, quest);
        assertTrue(pool.hasSimilar(QuestType.ESCORT, a, b));
        assertFalse(pool.hasSimilar(QuestType.DIPLOMACY, a, b));
    }

    @Test
    @Disabled("Requires ServerLevel-backed QuestEngine for full generateQuestsFromWorldState execution.")
    void generateQuestsFromWorldStateCreatesEscortAndDiplomacyQuests() {
    }
}
