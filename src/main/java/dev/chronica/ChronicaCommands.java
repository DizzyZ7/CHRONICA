package dev.chronica;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ChronicaCommands {
    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chronica")
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("civs").executes(ctx -> civs(ctx.getSource())))
                .then(Commands.literal("civ").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> civ(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("reputation").executes(ctx -> reputation(ctx.getSource())))
                .then(Commands.literal("history").executes(ctx -> history(ctx.getSource(), null))
                        .then(Commands.argument("civ", StringArgumentType.greedyString()).executes(ctx -> history(ctx.getSource(), StringArgumentType.getString(ctx, "civ")))))
                .then(Commands.literal("quests").executes(ctx -> quests(ctx.getSource())))
                .then(Commands.literal("map").executes(ctx -> map(ctx.getSource())))
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2)).executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("simulate").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("years", IntegerArgumentType.integer(1, 10000)).executes(ctx -> simulate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "years")))))
                .then(Commands.literal("debug").requires(src -> src.hasPermission(2)).then(Commands.literal("territory").executes(ctx -> debugTerritory(ctx.getSource()))))
                .then(Commands.literal("spawn_caravan").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civA", StringArgumentType.word()).then(Commands.argument("civB", StringArgumentType.word()).executes(ctx -> spawnCaravan(ctx.getSource(), StringArgumentType.getString(ctx, "civA"), StringArgumentType.getString(ctx, "civB"))))))
                .then(Commands.literal("force_war").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("civA", StringArgumentType.word()).then(Commands.argument("civB", StringArgumentType.word()).executes(ctx -> forceWar(ctx.getSource(), StringArgumentType.getString(ctx, "civA"), StringArgumentType.getString(ctx, "civB"))))))
        );
    }

    private static ChronicaWorldData data(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
        if (level == null) throw new IllegalStateException("Overworld is not loaded");
        return ChronicaWorldData.getOrCreate(level);
    }

    private static int info(CommandSourceStack source) {
        ChronicaWorldData data = data(source);
        source.sendSuccess(() -> Component.literal("CHRONICA: civs=" + data.civilizations.size() + ", history=" + data.historyLog.size() + ", age=" + data.chronologicalAge), false);
        return 1;
    }

    private static int civs(CommandSourceStack source) {
        ChronicaWorldData data = data(source);
        for (Civilization civ : data.civilizations.values()) {
            source.sendSuccess(() -> Component.literal(civ.name + " | " + civ.status + " | " + civ.techTier + " | pop " + civ.population), false);
        }
        return data.civilizations.size();
    }

    private static int civ(CommandSourceStack source, String name) {
        Civilization civ = data(source).civilizations.values().stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (civ == null) {
            source.sendFailure(Component.literal("Civilization not found: " + name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(civ.name + "\nTrait: " + civ.primaryTrait + "/" + civ.secondaryTrait + "\nStatus: " + civ.status + "\nTier: " + civ.techTier + "\nPopulation: " + civ.population + "/" + civ.maxPopulation + "\nChunks: " + civ.territory.size()), false);
        return 1;
    }

    private static int reputation(CommandSourceStack source) {
        try {
            ChronicaWorldData data = data(source);
            PlayerWorldState state = data.playerState(source.getPlayerOrException().getUUID());
            for (Civilization civ : data.civilizations.values()) {
                source.sendSuccess(() -> Component.literal(civ.name + ": " + state.reputation(civ.id)), false);
            }
            return state.reputation.size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }
    }

    private static int history(CommandSourceStack source, String civName) {
        ChronicaWorldData data = data(source);
        int shown = 0;
        for (int i = Math.max(0, data.historyLog.size() - 10); i < data.historyLog.size(); i++) {
            HistoryEvent event = data.historyLog.get(i);
            if (civName == null || event.generateLoreText(NameGenerator.create(data.worldSeed)).toLowerCase().contains(civName.toLowerCase())) {
                source.sendSuccess(() -> Component.literal(event.worldTime() + ": " + event.generateLoreText(NameGenerator.create(data.worldSeed))), false);
                shown++;
            }
        }
        return shown;
    }

    private static int quests(CommandSourceStack source) {
        ChronicaWorldData data = data(source);
        int count = 0;
        for (ChronicaQuest quest : data.questPool.quests.values()) {
            if (quest.status == QuestStatus.AVAILABLE || quest.status == QuestStatus.ACTIVE) {
                source.sendSuccess(() -> Component.literal(quest.type + " | " + quest.status + " | " + quest.summary), false);
                count++;
            }
        }
        return count;
    }

    private static int map(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Territory overlay packet is planned; current claimed chunks: " + data(source).territoryMap.raw().size()), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("CHRONICA config reload hook executed. NeoForge handles config file reload events separately."), true);
        return 1;
    }

    private static int simulate(CommandSourceStack source, int years) {
        ServerLevel level = source.getServer().getLevel(Level.OVERWORLD);
        ChronicaWorldData data = data(source);
        CivilizationSimulator civSim = new CivilizationSimulator(level, data);
        DiplomacySimulator dipSim = new DiplomacySimulator(level, data);
        EconomySimulator ecoSim = new EconomySimulator(level, data);
        for (int i = 0; i < years; i++) {
            for (Civilization civ : data.civilizations.values()) civSim.processCiv(civ);
            ecoSim.tick(5_000_000L);
            dipSim.tick(5_000_000L);
            data.chronologicalAge++;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Fast-forwarded CHRONICA by " + years + " years."), true);
        return years;
    }

    private static int debugTerritory(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Claimed territory chunks: " + data(source).territoryMap.raw().size()), false);
        return 1;
    }

    private static int spawnCaravan(CommandSourceStack source, String civA, String civB) {
        source.sendSuccess(() -> Component.literal("Caravan entity spawning is reserved for v0.5. Current route simulation is abstract."), true);
        return 1;
    }

    private static int forceWar(CommandSourceStack source, String civA, String civB) {
        ChronicaWorldData data = data(source);
        Civilization a = data.civilizations.values().stream().filter(c -> c.name.equalsIgnoreCase(civA)).findFirst().orElse(null);
        Civilization b = data.civilizations.values().stream().filter(c -> c.name.equalsIgnoreCase(civB)).findFirst().orElse(null);
        if (a == null || b == null) {
            source.sendFailure(Component.literal("Both civilizations must exist."));
            return 0;
        }
        DiplomaticRelation rel = data.diplomacyMatrix.getRelation(a.id, b.id);
        rel.type = RelationType.WAR;
        rel.trustScore = -100;
        rel.lastEventTime = source.getLevel().getGameTime();
        data.addHistory(new HistoryEvent.WarDeclared(java.util.UUID.randomUUID(), source.getLevel().getGameTime(), a.id, b.id, "operator command"));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Forced war: " + a.name + " vs " + b.name), true);
        return 1;
    }
}
