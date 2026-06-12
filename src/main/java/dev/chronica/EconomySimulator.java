package dev.chronica;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;

public final class EconomySimulator extends ChronicaSubSimulator {
    private static final int SAFETY_BUFFER = 3;

    public EconomySimulator(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long start = System.nanoTime();
        List<TradeRoute> routes = new ArrayList<>(data.tradeRoutes.values());
        while (processingCursor < routes.size()) {
            if (System.nanoTime() - start > budgetNanos) break;
            TradeRoute route = routes.get(processingCursor++);
            if (route.status == TradeRouteStatus.ACTIVE) processRoute(route);
        }
        if (processingCursor >= routes.size()) processingCursor = 0;
    }

    public void processRoute(TradeRoute route) {
        Civilization source = data.civilizations.get(route.sourceOwner);
        Civilization dest = data.civilizations.get(route.destinationOwner);
        if (source == null || dest == null || source.status == CivStatus.COLLAPSED || dest.status == CivStatus.COLLAPSED) {
            route.status = TradeRouteStatus.DESTROYED;
            data.setDirty();
            return;
        }

        route.cargo.clear();
        for (ResourceType resource : ResourceType.values()) {
            int supply = supply(source, resource);
            int demand = demand(dest, resource);
            if (supply > 0 && demand > 0) {
                int cargo = Math.min(Math.min(supply, demand), 32);
                route.cargo.put(resource, cargo);
                updateMarketPrice(dest, resource, price(resource, demand, supply));
            }
        }
        data.setDirty();
    }

    public static int demand(Civilization civ, ResourceType resource) {
        int consumption = civ.consumptionPerCycle.getOrDefault(resource, 0);
        int stock = civ.stockpile.getOrDefault(resource, 0);
        return Math.max(0, consumption * SAFETY_BUFFER - stock);
    }

    public static int supply(Civilization civ, ResourceType resource) {
        int consumption = civ.consumptionPerCycle.getOrDefault(resource, 0);
        int stock = civ.stockpile.getOrDefault(resource, 0);
        return Math.max(0, stock - consumption * SAFETY_BUFFER);
    }

    public static int price(ResourceType resource, int demand, int supply) {
        double scarcity = (double) Math.max(1, demand) / Math.max(1, supply);
        double clamped = Math.max(0.35, Math.min(4.0, scarcity));
        return Math.max(1, (int) Math.round(resource.baseValue() * clamped));
    }

    private static void updateMarketPrice(Civilization civ, ResourceType resource, int price) {
        for (Settlement settlement : civ.settlements) {
            if (settlement.localMarket == null) settlement.localMarket = new EnumMap<>(ResourceType.class);
            settlement.localMarket.put(resource, price);
        }
    }

    public static void executeGoodsTransfer(Civilization source, Civilization dest, Map<ResourceType, Integer> cargo) {
        for (Map.Entry<ResourceType, Integer> entry : cargo.entrySet()) {
            ResourceType resource = entry.getKey();
            int amount = Math.max(0, entry.getValue());
            source.stockpile.put(resource, Math.max(0, source.stockpile.getOrDefault(resource, 0) - amount));
            dest.stockpile.put(resource, dest.stockpile.getOrDefault(resource, 0) + amount);
        }
    }
}
