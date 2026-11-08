package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que la gestion du réseau enregistre : blocs, objets, block entities et menus. */
public final class GridRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);

    public static final RegistryObject<Block> LOAD_SHEDDER = BLOCKS.register("load_shedder",
            () -> new LoadShedderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> LOAD_SHEDDER_ITEM = ITEMS.register("load_shedder",
            () -> new BlockItem(LOAD_SHEDDER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<LoadShedderBlockEntity>> LOAD_SHEDDER_BE =
            BLOCK_ENTITIES.register("load_shedder", () -> BlockEntityType.Builder
                    .of(LoadShedderBlockEntity::new, LOAD_SHEDDER.get()).build(null));
    public static final RegistryObject<MenuType<LoadShedderMenu>> LOAD_SHEDDER_MENU =
            MENUS.register("load_shedder", () -> IForgeMenuType.create(LoadShedderMenu::fromNetwork));

    public static final RegistryObject<Block> METER = BLOCKS.register("meter",
            () -> new MeterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> METER_ITEM = ITEMS.register("meter",
            () -> new BlockItem(METER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<MeterBlockEntity>> METER_BE =
            BLOCK_ENTITIES.register("meter", () -> BlockEntityType.Builder
                    .of(MeterBlockEntity::new, METER.get()).build(null));
    public static final RegistryObject<Item> METER_READING = ITEMS.register("meter_reading",
            () -> new MeterReadingItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> BREAKER = BLOCKS.register("breaker",
            () -> new BreakerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> BREAKER_ITEM = ITEMS.register("breaker",
            () -> new BlockItem(BREAKER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<BreakerBlockEntity>> BREAKER_BE =
            BLOCK_ENTITIES.register("breaker", () -> BlockEntityType.Builder
                    .of(BreakerBlockEntity::new, BREAKER.get()).build(null));
    public static final RegistryObject<Item> SEAL = ITEMS.register("seal",
            () -> new SealItem(new Item.Properties().stacksTo(16)));

    private GridRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(LOAD_SHEDDER_ITEM, METER_ITEM, BREAKER_ITEM, SEAL);
    }
}
