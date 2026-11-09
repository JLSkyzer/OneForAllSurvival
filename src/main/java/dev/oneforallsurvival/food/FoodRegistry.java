package dev.oneforallsurvival.food;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Les objets de la péremption (le déchet, ce qu'un aliment avarié devient, l'armoire frigorifique), ceux du
 * dessalinisateur (le sel, l'eau potable, sa bouteille, la machine) et ceux du séchoir (la machine, sa recette, les rations) la couveuse et la plaque électrique.
 */
public final class FoodRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, OneForAllSurvival.MODID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, OneForAllSurvival.MODID);

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

    /** Étiquette {@code forge:salt} : le sel du mod en fait partie, d'autres mods peuvent l'y rejoindre. */
    public static final RegistryObject<Item> SALT = ITEMS.register("salt",
            () -> new Item(new Item.Properties()));
    /** Ingrédient seulement (pas de soif dans le jeu) : une fois vidée, elle rend sa bouteille de verre. */
    public static final RegistryObject<Item> POTABLE_WATER_BOTTLE = ITEMS.register("potable_water_bottle",
            () -> new Item(new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE)));

    /** L'eau potable : un fluide sans seau ni bloc, qui ne circule que par les tuyaux et les réservoirs. */
    public static final RegistryObject<FluidType> POTABLE_WATER_TYPE = FLUID_TYPES.register("potable_water",
            () -> new PotableWaterType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + OneForAllSurvival.MODID + ".potable_water")));
    public static final RegistryObject<Fluid> POTABLE_WATER = FLUIDS.register("potable_water",
            () -> new ForgeFlowingFluid.Source(potableWaterProperties()));
    public static final RegistryObject<Fluid> POTABLE_WATER_FLOWING = FLUIDS.register("potable_water_flowing",
            () -> new ForgeFlowingFluid.Flowing(potableWaterProperties()));

    public static final RegistryObject<Block> DESALINATOR = BLOCKS.register("desalinator",
            () -> new DesalinatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> DESALINATOR_ITEM = ITEMS.register("desalinator",
            () -> new BlockItem(DESALINATOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<DesalinatorBlockEntity>> DESALINATOR_BE =
            BLOCK_ENTITIES.register("desalinator", () -> BlockEntityType.Builder
                    .of(DesalinatorBlockEntity::new, DESALINATOR.get()).build(null));
    public static final RegistryObject<MenuType<DesalinatorMenu>> DESALINATOR_MENU =
            MENUS.register("desalinator", () -> IForgeMenuType.create(DesalinatorMenu::fromNetwork));

    public static final RegistryObject<Block> DRYER = BLOCKS.register("dryer",
            () -> new DryerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> DRYER_ITEM = ITEMS.register("dryer",
            () -> new BlockItem(DRYER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<DryerBlockEntity>> DRYER_BE =
            BLOCK_ENTITIES.register("dryer", () -> BlockEntityType.Builder
                    .of(DryerBlockEntity::new, DRYER.get()).build(null));
    public static final RegistryObject<MenuType<DryerMenu>> DRYER_MENU =
            MENUS.register("dryer", () -> IForgeMenuType.create(DryerMenu::fromNetwork));
    public static final RegistryObject<Block> INCUBATOR = BLOCKS.register("incubator",
            () -> new IncubatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> INCUBATOR_ITEM = ITEMS.register("incubator",
            () -> new BlockItem(INCUBATOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<IncubatorBlockEntity>> INCUBATOR_BE =
            BLOCK_ENTITIES.register("incubator", () -> BlockEntityType.Builder
                    .of(IncubatorBlockEntity::new, INCUBATOR.get()).build(null));
    public static final RegistryObject<MenuType<IncubatorMenu>> INCUBATOR_MENU =
            MENUS.register("incubator", () -> IForgeMenuType.create(IncubatorMenu::fromNetwork));
    public static final RegistryObject<Block> HOT_PLATE = BLOCKS.register("hot_plate",
            () -> new HotPlateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> HOT_PLATE_ITEM = ITEMS.register("hot_plate",
            () -> new BlockItem(HOT_PLATE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<HotPlateBlockEntity>> HOT_PLATE_BE =
            BLOCK_ENTITIES.register("hot_plate", () -> BlockEntityType.Builder
                    .of(HotPlateBlockEntity::new, HOT_PLATE.get()).build(null));
    public static final RegistryObject<RecipeType<DryingRecipe>> DRYING_TYPE =
            RECIPE_TYPES.register("drying", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return OneForAllSurvival.MODID + ":drying";
                }
            });
    public static final RegistryObject<RecipeSerializer<DryingRecipe>> DRYING_SERIALIZER =
            RECIPE_SERIALIZERS.register("drying", DryingRecipe.Serializer::new);

    /** Les rations du séchoir : séchées (périment lentement, tag slow_perishable) et salées (ne périment jamais). */
    public static final RegistryObject<Item> DRIED_MEAT = ration("dried_meat", 4, true);
    public static final RegistryObject<Item> DRIED_FISH = ration("dried_fish", 3, false);
    public static final RegistryObject<Item> DRIED_FRUIT = ration("dried_fruit", 2, false);
    public static final RegistryObject<Item> SALTED_MEAT = ration("salted_meat", 4, true);
    public static final RegistryObject<Item> SALTED_FISH = ration("salted_fish", 3, false);
    public static final RegistryObject<Item> SALTED_FRUIT = ration("salted_fruit", 2, false);

    private FoodRegistry() {}

    private static RegistryObject<Item> ration(String name, int nutrition, boolean meat) {
        FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationMod(0.4F);
        if (meat) {
            food.meat();
        }
        return ITEMS.register(name, () -> new Item(new Item.Properties().food(food.build())));
    }

    private static ForgeFlowingFluid.Properties potableWaterProperties() {
        return new ForgeFlowingFluid.Properties(POTABLE_WATER_TYPE, POTABLE_WATER, POTABLE_WATER_FLOWING);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // La table de compostage est partagée : on n'y touche que sur le fil principal.
        event.enqueueWork(() -> ComposterBlock.COMPOSTABLES.put(SPOILED_FOOD.get(), 0.5F));
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(FRIDGE_ITEM, DESALINATOR_ITEM, DRYER_ITEM, INCUBATOR_ITEM, HOT_PLATE_ITEM, SALT, POTABLE_WATER_BOTTLE,
                DRIED_MEAT, DRIED_FISH, DRIED_FRUIT, SALTED_MEAT, SALTED_FISH, SALTED_FRUIT, SPOILED_FOOD);
    }
}
