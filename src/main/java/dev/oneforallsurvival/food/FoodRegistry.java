package dev.oneforallsurvival.food;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Les objets de la péremption : le déchet, ce qu'un aliment avarié devient, et l'armoire frigorifique. */
public final class FoodRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);

    /** Non comestible, compostable. */
    public static final RegistryObject<Item> SPOILED_FOOD = ITEMS.register("spoiled_food",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Block> FRIDGE = BLOCKS.register("fridge",
            () -> new FridgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> FRIDGE_ITEM = ITEMS.register("fridge",
            () -> new BlockItem(FRIDGE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<FridgeBlockEntity>> FRIDGE_BE =
            BLOCK_ENTITIES.register("fridge", () -> BlockEntityType.Builder
                    .of(FridgeBlockEntity::new, FRIDGE.get()).build(null));
    public static final RegistryObject<MenuType<FridgeMenu>> FRIDGE_MENU =
            MENUS.register("fridge", () -> IForgeMenuType.create(FridgeMenu::fromNetwork));

    private FoodRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // La table de compostage est partagée : on n'y touche que sur le fil principal.
        event.enqueueWork(() -> ComposterBlock.COMPOSTABLES.put(SPOILED_FOOD.get(), 0.5F));
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(FRIDGE_ITEM, SPOILED_FOOD);
    }
}
