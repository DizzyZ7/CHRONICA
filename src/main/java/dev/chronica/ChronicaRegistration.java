package dev.chronica;

import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ChronicaRegistration {
    private ChronicaRegistration() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, ChronicaMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ChronicaMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ChronicaMod.MOD_ID);

    public static final DeferredHolder<Block, Block> EPITAPH_STONE = registerBlockWithItem("epitaph_stone",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.4f, 6.0f).sound(SoundType.STONE)));
    public static final DeferredHolder<Block, Block> TERRITORY_MARKER = registerBlockWithItem("territory_marker",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.6f, 3.0f).sound(SoundType.STONE)));
    public static final DeferredHolder<Block, Block> CARAVAN_WAYPOINT = registerBlockWithItem("caravan_waypoint",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.8f, 3.0f).sound(SoundType.STONE)));
    public static final DeferredHolder<Block, Block> CHRONICLE_PEDESTAL = registerBlockWithItem("chronicle_pedestal",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 4.0f).sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, Block> CIV_BANNER = registerBlockWithItem("civ_banner",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0f, 1.0f).sound(SoundType.WOOL)));

    public static final DeferredHolder<Item, Item> CHRONICLE_BOOK = ITEMS.register("chronicle_book", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> TRADE_MANIFEST = ITEMS.register("trade_manifest", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> DIPLOMATIC_SEAL = ITEMS.register("diplomatic_seal", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> ARTIFACT_SHARD = ITEMS.register("artifact_shard", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> TERRITORIAL_MAP = ITEMS.register("territorial_map", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> REPUTATION_MEDALLION = ITEMS.register("reputation_medallion", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<EntityType<?>, EntityType<ChronicaNPCEntity>> CIV_NPC = ENTITIES.register("civ_npc", () ->
            EntityType.Builder.of(ChronicaNPCEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(ResourceLocation.fromNamespaceAndPath(ChronicaMod.MOD_ID, "civ_npc").toString())
    );

    private static DeferredHolder<Block, Block> registerBlockWithItem(String name, Supplier<Block> blockSupplier) {
        DeferredHolder<Block, Block> block = BLOCKS.register(name, blockSupplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static final class ModBusEvents {
        @SubscribeEvent
        public void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(CIV_NPC.get(), ChronicaNPCEntity.createAttributes().build());
        }
    }
}
