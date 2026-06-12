package dev.chronica;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class TradeRouteSimulator extends ChronicaSubSimulator {
    public TradeRouteSimulator(ServerLevel level, ChronicaWorldData data) {
        super(level, data);
    }

    @Override
    protected void doTick(long budgetNanos) {
        long start = System.nanoTime();
        List<TradeRoute> routes = new ArrayList<>(data.tradeRoutes.values());
        while (processingCursor < routes.size()) {
            if (System.nanoTime() - start > budgetNanos) break;
            processRoute(routes.get(processingCursor++));
        }
        if (processingCursor >= routes.size()) processingCursor = 0;
    }

    private void processRoute(TradeRoute route) {
        if (route.status != TradeRouteStatus.ACTIVE) return;
        if (level.getGameTime() < route.nextDepartureTime) return;
        Civilization source = data.civilizations.get(route.sourceOwner);
        Civilization dest = data.civilizations.get(route.destinationOwner);
        if (source == null || dest == null) {
            route.status = TradeRouteStatus.DESTROYED;
            return;
        }
        EconomySimulator.executeGoodsTransfer(source, dest, route.cargo);
        route.cycleCount++;
        route.nextDepartureTime = level.getGameTime() + 24_000L;
        if (route.cycleCount == 1) {
            data.addHistory(new HistoryEvent.TradeRouteEstablished(UUID.randomUUID(), level.getGameTime(), route.id, route.sourceOwner, route.destinationOwner));
        }
        data.setDirty();
    }
}
