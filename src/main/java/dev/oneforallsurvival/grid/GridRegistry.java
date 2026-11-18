package dev.oneforallsurvival.grid;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerStationBlockEntity;
import dev.oneforallsurvival.grid.supplier.PowerStationItem;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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

    public static final RegistryObject<Block> SURGE_ARRESTER = BLOCKS.register("surge_arrester",
            () -> new SurgeArresterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> SURGE_ARRESTER_ITEM = ITEMS.register("surge_arrester",
            () -> new BlockItem(SURGE_ARRESTER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SurgeArresterBlockEntity>> SURGE_ARRESTER_BE =
            BLOCK_ENTITIES.register("surge_arrester", () -> BlockEntityType.Builder
                    .of(SurgeArresterBlockEntity::new, SURGE_ARRESTER.get()).build(null));

    public static final RegistryObject<Block> WEATHER_STATION = BLOCKS.register("weather_station",
            () -> new WeatherStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> WEATHER_STATION_ITEM = ITEMS.register("weather_station",
            () -> new BlockItem(WEATHER_STATION.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<WeatherStationBlockEntity>> WEATHER_STATION_BE =
            BLOCK_ENTITIES.register("weather_station", () -> BlockEntityType.Builder
                    .of(WeatherStationBlockEntity::new, WEATHER_STATION.get()).build(null));

    /**
     * Le poste de fournisseur (lot E6) : protégé comme le coffre-fort (dureté -1, résistance maximale, piston bloqué ;
     * la casse est rendue aux ayants droit par {@link PowerStationBlock#getDestroyProgress}). Cube plein.
     */
    public static final RegistryObject<Block> POWER_STATION = BLOCKS.register("power_station",
            () -> new PowerStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> POWER_STATION_ITEM = ITEMS.register("power_station",
            () -> new PowerStationItem(POWER_STATION.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<PowerStationBlockEntity>> POWER_STATION_BE =
            BLOCK_ENTITIES.register("power_station", () -> BlockEntityType.Builder
                    .of(PowerStationBlockEntity::new, POWER_STATION.get()).build(null));
    public static final RegistryObject<MenuType<PowerTillMenu>> POWER_TILL_MENU =
            MENUS.register("power_till", () -> IForgeMenuType.create(PowerTillMenu::fromNetwork));

    private GridRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(GridRegistry::onCommonSetup);
    }

    /** La caisse d'un poste est un rangement permis pour l'argent (des deux côtés, comme le coffre-fort). */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> MoneyStorageRules.register(POWER_TILL_MENU.get()));
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(LOAD_SHEDDER_ITEM, METER_ITEM, BREAKER_ITEM, SEAL, SURGE_ARRESTER_ITEM, WEATHER_STATION_ITEM,
                POWER_STATION_ITEM);
    }
}
