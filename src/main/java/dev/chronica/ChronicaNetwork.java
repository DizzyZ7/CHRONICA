package dev.chronica;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ChronicaNetwork {
    private ChronicaNetwork() {}

    public static final String NETWORK_VERSION = "1";

    public record ChronicaMapDataPacket(String encodedTerritories) implements CustomPacketPayload {
        public static final Type<ChronicaMapDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChronicaMod.MOD_ID, "map_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChronicaMapDataPacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                ChronicaMapDataPacket::encodedTerritories,
                ChronicaMapDataPacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReputationUpdatePacket(String encodedReputation) implements CustomPacketPayload {
        public static final Type<ReputationUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChronicaMod.MOD_ID, "reputation_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReputationUpdatePacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                ReputationUpdatePacket::encodedReputation,
                ReputationUpdatePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record QuestUpdatePacket(String encodedQuests) implements CustomPacketPayload {
        public static final Type<QuestUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ChronicaMod.MOD_ID, "quest_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, QuestUpdatePacket> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                QuestUpdatePacket::encodedQuests,
                QuestUpdatePacket::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final class ModBusEvents {
        @SubscribeEvent
        public void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
            registrar.playToClient(ChronicaMapDataPacket.TYPE, ChronicaMapDataPacket.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(ReputationUpdatePacket.TYPE, ReputationUpdatePacket.STREAM_CODEC, (payload, context) -> {});
            registrar.playToClient(QuestUpdatePacket.TYPE, QuestUpdatePacket.STREAM_CODEC, (payload, context) -> {});
        }
    }
}
