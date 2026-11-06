package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.FaultType;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
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

/** Le métier de réparation : testeur, poste à souder, pièces de rechange et bain galvanique (bloc, recette, menu). */
public final class RepairRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, OneForAllSurvival.MODID);

    public static final RegistryObject<Item> TESTER = ITEMS.register("tester",
            () -> new TesterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WELDER = ITEMS.register("welder",
            () -> new WelderItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FUSE = part("fuse");
    public static final RegistryObject<Item> COIL = part("coil");
    public static final RegistryObject<Item> BEARING = part("bearing");
    public static final RegistryObject<Item> CLEANING_KIT = part("cleaning_kit");
    public static final RegistryObject<Item> TREATED_FUSE = part("treated_fuse");
    public static final RegistryObject<Item> TREATED_COIL = part("treated_coil");
    public static final RegistryObject<Item> TREATED_BEARING = part("treated_bearing");
    public static final RegistryObject<Item> TREATED_CLEANING_KIT = part("treated_cleaning_kit");

    public static final RegistryObject<Block> GALVANIC_BATH = BLOCKS.register("galvanic_bath",
            () -> new GalvanicBathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> GALVANIC_BATH_ITEM = ITEMS.register("galvanic_bath",
            () -> new BlockItem(GALVANIC_BATH.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<GalvanicBathBlockEntity>> GALVANIC_BATH_BE =
            BLOCK_ENTITIES.register("galvanic_bath", () -> BlockEntityType.Builder
                    .of(GalvanicBathBlockEntity::new, GALVANIC_BATH.get()).build(null));
    public static final RegistryObject<MenuType<GalvanicBathMenu>> GALVANIC_BATH_MENU =
            MENUS.register("galvanic_bath", () -> IForgeMenuType.create(GalvanicBathMenu::fromNetwork));
    public static final RegistryObject<RecipeType<GalvanicRecipe>> GALVANIC_TYPE =
            RECIPE_TYPES.register("galvanic_bath", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return OneForAllSurvival.MODID + ":galvanic_bath";
                }
            });
    public static final RegistryObject<RecipeSerializer<GalvanicRecipe>> GALVANIC_SERIALIZER =
            RECIPE_SERIALIZERS.register("galvanic_bath", GalvanicRecipe.Serializer::new);

    private RepairRegistry() {}

    private static RegistryObject<Item> part(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }

    /** L'objet de cette pièce de rechange, normale ou traitée. */
    public static Item partItem(FaultType type, boolean treated) {
        return switch (type) {
            case FUSE -> (treated ? TREATED_FUSE : FUSE).get();
            case WINDING -> (treated ? TREATED_COIL : COIL).get();
            case BEARING -> (treated ? TREATED_BEARING : BEARING).get();
            case FOULING -> (treated ? TREATED_CLEANING_KIT : CLEANING_KIT).get();
        };
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(GALVANIC_BATH_ITEM, TESTER, WELDER, FUSE, COIL, BEARING, CLEANING_KIT,
                TREATED_FUSE, TREATED_COIL, TREATED_BEARING, TREATED_CLEANING_KIT);
    }
}
