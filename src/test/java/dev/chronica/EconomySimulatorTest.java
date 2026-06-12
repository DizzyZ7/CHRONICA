package dev.chronica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EconomySimulatorTest {
    @Test
    void priceRisesWhenDemandExceedsSupply() {
        int scarce = EconomySimulator.price(ResourceType.IRON, 100, 10);
        int abundant = EconomySimulator.price(ResourceType.IRON, 10, 100);
        assertTrue(scarce > abundant);
    }

    @Test
    void transferMovesAbstractGoodsBetweenStockpiles() {
        Civilization source = new Civilization();
        Civilization dest = new Civilization();
        source.stockpile.put(ResourceType.FOOD, 50);
        dest.stockpile.put(ResourceType.FOOD, 0);
        EconomySimulator.executeGoodsTransfer(source, dest, java.util.Map.of(ResourceType.FOOD, 12));
        assertEquals(38, source.stockpile.get(ResourceType.FOOD));
        assertEquals(12, dest.stockpile.get(ResourceType.FOOD));
    }
}
