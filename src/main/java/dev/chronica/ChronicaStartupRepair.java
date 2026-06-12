package dev.chronica;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ChronicaMod.MOD_ID)
public final class ChronicaStartupRepair {
    private ChronicaStartupRepair() {}

    @SubscribeEvent
    public static void repairNamedNpcIndex(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;
        ChronicaWorldData data = ChronicaWorldData.getOrCreate(level);
        if (!data.initialized) return;
        boolean changed = false;
        NameGenerator names = NameGenerator.create(data.worldSeed);
        for (Civilization civ : data.civilizations.values()) {
            for (UUID npcId : civ.namedNPCs) {
                if (data.namedNpcs.containsKey(npcId)) continue;
                ChronicaNPCData npc = new ChronicaNPCData();
                npc.id = npcId;
                npc.civilization = civ.id;
                npc.homeSettlementId = civ.settlements.isEmpty() ? new UUID(0L, 0L) : civ.settlements.getFirst().id;
                npc.role = NPCRole.CITIZEN;
                npc.name = names.generateNPCName(civ.id, npc.role, npcId.getLeastSignificantBits());
                npc.lastKnownPos = civ.capital;
                data.namedNpcs.put(npcId, npc);
                changed = true;
            }
        }
        if (changed) data.setDirty();
    }
}
