package dev.chronica;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ChronicaNPCEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_CIV_NAME = SynchedEntityData.defineId(ChronicaNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ROLE = SynchedEntityData.defineId(ChronicaNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_NPC_NAME = SynchedEntityData.defineId(ChronicaNPCEntity.class, EntityDataSerializers.STRING);

    private UUID chronicaNpcId = UUID.randomUUID();
    private CivId civId = new CivId(new UUID(0L, 0L));
    private UUID settlementId = new UUID(0L, 0L);
    private NPCRole role = NPCRole.CITIZEN;

    public ChronicaNPCEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CIV_NAME, "Unknown Civ");
        builder.define(DATA_ROLE, NPCRole.CITIZEN.name());
        builder.define(DATA_NPC_NAME, "Unnamed");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.addGoal(5, new ChronicaWanderInSettlementGoal(this, 0.7));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(level() instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        ChronicaWorldData data = ChronicaWorldData.getOrCreate(serverLevel);
        ChronicaNPCData npc = data.namedNpcs.computeIfAbsent(chronicaNpcId, id -> createDataSnapshot(serverLevel));
        npc.lastKnownPos = blockPosition();
        npc.playerMemory.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>())
                .add(new MemoryEntry(serverLevel.getGameTime(), MemoryType.IGNORED, 0, "Player opened dialogue"));
        trimMemory(npc.playerMemory.get(player.getUUID()), 50);

        Civilization civ = data.civilizations.get(npc.civilization);
        PlayerWorldState state = data.playerState(player.getUUID());
        int rep = state.reputation(npc.civilization);
        String civName = civ == null ? "Unknown Civ" : civ.name;
        player.displayClientMessage(Component.literal("[CHRONICA] " + npc.name + " — " + npc.role.name() + " of " + civName + ". Reputation: " + rep), false);

        if (rep < -50) {
            player.displayClientMessage(Component.literal("This settlement considers you hostile."), false);
        } else {
            data.questPool.quests.values().stream()
                    .filter(q -> q.status == QuestStatus.AVAILABLE)
                    .filter(q -> q.sourceNpcId != null && q.sourceNpcId.equals(npc.id))
                    .findFirst()
                    .ifPresent(q -> player.displayClientMessage(Component.literal("Available quest: " + q.summary), false));
        }
        data.setDirty();
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putUUID("chronicaNpcId", chronicaNpcId);
        tag.putUUID("civId", civId.value());
        tag.putUUID("settlementId", settlementId);
        tag.putString("role", role.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("chronicaNpcId")) chronicaNpcId = tag.getUUID("chronicaNpcId");
        if (tag.hasUUID("civId")) civId = new CivId(tag.getUUID("civId"));
        if (tag.hasUUID("settlementId")) settlementId = tag.getUUID("settlementId");
        if (tag.contains("role")) role = NPCRole.valueOf(tag.getString("role"));
        entityData.set(DATA_ROLE, role.name());
    }

    public void bindToChronica(UUID npcId, CivId civId, UUID settlementId, NPCRole role, String npcName, String civName) {
        this.chronicaNpcId = npcId;
        this.civId = civId;
        this.settlementId = settlementId;
        this.role = role;
        entityData.set(DATA_NPC_NAME, npcName);
        entityData.set(DATA_CIV_NAME, civName);
        entityData.set(DATA_ROLE, role.name());
        setCustomName(Component.literal(npcName));
        setCustomNameVisible(true);
    }

    private ChronicaNPCData createDataSnapshot(ServerLevel serverLevel) {
        ChronicaNPCData npc = new ChronicaNPCData();
        npc.id = chronicaNpcId;
        npc.civilization = civId;
        npc.homeSettlementId = settlementId;
        npc.role = role;
        npc.name = entityData.get(DATA_NPC_NAME);
        npc.age = (int) (serverLevel.getDayTime() / 24_000L);
        npc.alive = true;
        npc.lastKnownPos = blockPosition();
        return npc;
    }

    private static void trimMemory(List<MemoryEntry> entries, int max) {
        while (entries.size() > max) entries.remove(0);
    }

    static final class ChronicaWanderInSettlementGoal extends RandomStrollGoal {
        private final ChronicaNPCEntity npc;

        ChronicaWanderInSettlementGoal(ChronicaNPCEntity npc, double speedModifier) {
            super(npc, speedModifier, 80);
            this.npc = npc;
        }

        @Override
        public boolean canUse() {
            BlockPos home = npc.getRestrictCenter();
            if (home != null && npc.blockPosition().distSqr(home) > 48 * 48) return false;
            return super.canUse();
        }
    }

    @Nullable
    public UUID chronicaNpcId() {
        return chronicaNpcId;
    }
}
