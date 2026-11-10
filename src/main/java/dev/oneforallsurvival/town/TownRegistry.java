package dev.oneforallsurvival.town;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que la vie de la ville enregistre : l'eau chaude et son chauffe-eau (puis, plus tard, douche, cinéma, phare). */
public final class TownRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, OneForAllSurvival.MODID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, OneForAllSurvival.MODID);

    /** L'eau chaude : un fluide sans seau ni bloc, qui ne circule que par les tuyaux et les réservoirs. */
    public static final RegistryObject<FluidType> HOT_WATER_TYPE = FLUID_TYPES.register("hot_water",
            () -> new HotWaterType(FluidType.Properties.create()
                    .descriptionId("fluid_type." + OneForAllSurvival.MODID + ".hot_water")
                    .temperature(340)));
    public static final RegistryObject<Fluid> HOT_WATER = FLUIDS.register("hot_water",
            () -> new ForgeFlowingFluid.Source(hotWaterProperties()));
    public static final RegistryObject<Fluid> HOT_WATER_FLOWING = FLUIDS.register("hot_water_flowing",
            () -> new ForgeFlowingFluid.Flowing(hotWaterProperties()));

    public static final RegistryObject<Block> WATER_HEATER = BLOCKS.register("water_heater",
            () -> new WaterHeaterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> WATER_HEATER_ITEM = ITEMS.register("water_heater",
            () -> new BlockItem(WATER_HEATER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<WaterHeaterBlockEntity>> WATER_HEATER_BE =
            BLOCK_ENTITIES.register("water_heater", () -> BlockEntityType.Builder
                    .of(WaterHeaterBlockEntity::new, WATER_HEATER.get()).build(null));
    public static final RegistryObject<MenuType<WaterHeaterMenu>> WATER_HEATER_MENU =
            MENUS.register("water_heater", () -> IForgeMenuType.create(WaterHeaterMenu::fromNetwork));

    /** Le Bien-être : l'effet d'une douche chaude. Il ne soigne jamais. */
    public static final RegistryObject<MobEffect> WELL_BEING = EFFECTS.register("well_being", WellBeingEffect::new);

    public static final RegistryObject<Block> SHOWER_HEAD = BLOCKS.register("shower_head",
            () -> new ShowerHeadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Item> SHOWER_HEAD_ITEM = ITEMS.register("shower_head",
            () -> new BlockItem(SHOWER_HEAD.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ShowerHeadBlockEntity>> SHOWER_HEAD_BE =
            BLOCK_ENTITIES.register("shower_head", () -> BlockEntityType.Builder
                    .of(ShowerHeadBlockEntity::new, SHOWER_HEAD.get()).build(null));

    private TownRegistry() {}

    private static ForgeFlowingFluid.Properties hotWaterProperties() {
        return new ForgeFlowingFluid.Properties(HOT_WATER_TYPE, HOT_WATER, HOT_WATER_FLOWING);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        EFFECTS.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(WATER_HEATER_ITEM, SHOWER_HEAD_ITEM);
    }
}
