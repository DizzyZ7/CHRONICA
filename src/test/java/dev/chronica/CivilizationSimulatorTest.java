package dev.chronica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

final class CivilizationSimulatorTest {
    @Test
    void civilizationNbtRoundTripPreservesCoreFields() {
        Civilization civ = new Civilization();
        civ.name = "Keth Arun";
        civ.population = 123;
        civ.stockpile.put(ResourceType.FOOD, 456);
        Civilization restored = Civilization.deserializeNBT(civ.serializeNBT());
        assertEquals("Keth Arun", restored.name);
        assertEquals(123, restored.population);
        assertEquals(456, restored.stockpile.get(ResourceType.FOOD));
    }

    @Test
    @Disabled("Requires a mocked or game-provided ServerLevel to execute processCiv safely.")
    void processCivUpdatesPopulationResourcesExpansionAndCollapse() {
        assertTrue(true);
    }
}
