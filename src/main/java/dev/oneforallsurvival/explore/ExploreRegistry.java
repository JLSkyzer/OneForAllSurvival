package dev.oneforallsurvival.explore;

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
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que l'exploration enregistre : la cache enfouie (les objets du détecteur, de la lampe, etc. s'y ajoutent). */
public final class ExploreRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);

    public static final RegistryObject<Block> CACHE = BLOCKS.register("buried_cache",
            () -> new CacheBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> CACHE_ITEM = ITEMS.register("buried_cache",
            () -> new BlockItem(CACHE.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_DETECTOR = ITEMS.register("metal_detector",
            () -> new DetectorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HEADLAMP = ITEMS.register("headlamp",
            () -> new HeadlampItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<CacheBlockEntity>> CACHE_BE =
            BLOCK_ENTITIES.register("buried_cache", () -> BlockEntityType.Builder
                    .of(CacheBlockEntity::new, CACHE.get()).build(null));

    public static final RegistryObject<Block> RECEIVER = BLOCKS.register("signal_receiver",
            () -> new ReceiverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> RECEIVER_ITEM = ITEMS.register("signal_receiver",
            () -> new BlockItem(RECEIVER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ReceiverBlockEntity>> RECEIVER_BE =
            BLOCK_ENTITIES.register("signal_receiver", () -> BlockEntityType.Builder
                    .of(ReceiverBlockEntity::new, RECEIVER.get()).build(null));
    public static final RegistryObject<MenuType<ReceiverMenu>> RECEIVER_MENU =
            MENUS.register("signal_receiver", () -> IForgeMenuType.create(ReceiverMenu::fromNetwork));

    /** Dureté -1 : ni la main ni un outil ne l'entament en survie ; la résistance maximale le garde des explosions. */
    public static final RegistryObject<Block> MECHANISM = BLOCKS.register("ancient_mechanism",
            () -> new MechanismBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK)
                    .noLootTable()));
    public static final RegistryObject<Item> MECHANISM_ITEM = ITEMS.register("ancient_mechanism",
            () -> new BlockItem(MECHANISM.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<MechanismBlockEntity>> MECHANISM_BE =
            BLOCK_ENTITIES.register("ancient_mechanism", () -> BlockEntityType.Builder
                    .of(MechanismBlockEntity::new, MECHANISM.get()).build(null));

    private ExploreRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(CACHE_ITEM, METAL_DETECTOR, HEADLAMP, RECEIVER_ITEM, MECHANISM_ITEM);
    }
}
