package dev.chronica;

import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class ChronicaHistoryText {
    private ChronicaHistoryText() {
    }

    static Component commandLine(ChronicaWorldData data, HistoryEvent event) {
        String type = event.getClass().getSimpleName();

        return switch (type) {
            case "CivFounded" -> Component.translatable(
                    "chronica.history.civ_founded",
                    event.worldTime(),
                    civName(data, read(event, "civId")),
                    founderName(read(event, "founderName")),
                    position(read(event, "capital"))
            );
            case "TechAdvanced" -> Component.translatable(
                    "chronica.history.tech_advanced",
                    event.worldTime(),
                    civName(data, read(event, "civId")),
                    techTier(read(event, "newTier"))
            );
            case "WarDeclared" -> Component.translatable(
                    "chronica.history.war_declared",
                    event.worldTime(),
                    civName(data, read(event, "attacker")),
                    civName(data, read(event, "defender")),
                    reason(read(event, "reason"))
            );
            case "CivCollapsed" -> Component.translatable(
                    "chronica.history.civ_collapsed",
                    event.worldTime(),
                    civName(data, read(event, "civId")),
                    reason(read(event, "reason"))
            );
            case "TradeRouteEstablished" -> Component.translatable(
                    "chronica.history.trade_route_established",
                    event.worldTime(),
                    civName(data, firstExisting(data, read(event, "from"), read(event, "fromCiv"), read(event, "origin"))),
                    civName(data, firstExisting(data, read(event, "to"), read(event, "toCiv"), read(event, "target")))
            );
            case "TerritoryExpanded" -> Component.translatable(
                    "chronica.history.territory_expanded",
                    event.worldTime(),
                    civName(data, read(event, "civId"))
            );
            case "SettlementDestroyed" -> Component.translatable(
                    "chronica.history.settlement_destroyed",
                    event.worldTime(),
                    civName(data, read(event, "civId")),
                    reason(read(event, "reason"))
            );
            case "PlayerIntervention" -> Component.translatable(
                    "chronica.history.player_intervention",
                    event.worldTime(),
                    civName(data, read(event, "civId"))
            );
            default -> Component.translatable(
                    "chronica.history.unknown",
                    event.worldTime(),
                    humanType(type),
                    legacyWithNames(data, event)
            );
        };
    }

    static String searchText(ChronicaWorldData data, HistoryEvent event) {
        return legacyWithNames(data, event) + " " + event.getClass().getSimpleName();
    }

    private static Object firstExisting(ChronicaWorldData data, Object... values) {
        for (Object value : values) {
            if (value instanceof CivId id && data.civilizations.containsKey(id)) {
                return id;
            }
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object read(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object civName(ChronicaWorldData data, Object value) {
        if (value instanceof CivId id) {
            Civilization civ = data.civilizations.get(id);
            if (civ != null && civ.name != null && !civ.name.isBlank()) {
                return civ.name;
            }
            return id.value().toString().substring(0, 8);
        }

        return value == null ? Component.translatable("chronica.history.unknown_civ") : String.valueOf(value);
    }

    private static Object techTier(Object value) {
        if (value instanceof TechTier tier) {
            return Component.translatable("chronica.tech_tier." + key(tier));
        }

        return value == null ? Component.translatable("chronica.history.unknown_tech") : String.valueOf(value);
    }

    private static Object founderName(Object value) {
        return value == null ? Component.translatable("chronica.history.unknown_founder") : String.valueOf(value);
    }

    private static Object position(Object value) {
        if (value instanceof BlockPos pos) {
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }

        return Component.translatable("chronica.history.unknown_place");
    }

    private static Object reason(Object value) {
        if (value == null) {
            return Component.translatable("chronica.reason.unknown");
        }

        String raw = String.valueOf(value).trim();
        String key = raw.toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");

        return switch (key) {
            case "hunger_and_depopulation" -> Component.translatable("chronica.reason.hunger_and_depopulation");
            case "rivalry_and_weak_trust" -> Component.translatable("chronica.reason.rivalry_and_weak_trust");
            case "loss_of_the_capital" -> Component.translatable("chronica.reason.loss_of_the_capital");
            case "operator_command" -> Component.translatable("chronica.reason.operator_command");
            default -> raw;
        };
    }

    private static String legacyWithNames(ChronicaWorldData data, HistoryEvent event) {
        String text = event.generateLoreText(NameGenerator.create(data.worldSeed));

        for (Civilization civ : data.civilizations.values()) {
            if (civ.id != null && civ.name != null) {
                text = text.replace(civ.id.value().toString(), civ.name);
            }
        }

        return text;
    }

    private static String humanType(String type) {
        return type.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
