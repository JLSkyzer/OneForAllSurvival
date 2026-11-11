package dev.oneforallsurvival.bench;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import dev.oneforallsurvival.town.TownRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/** Ce que l'établi d'ingénieur enregistre : bloc, objet, block entity, menu et onglet créatif. */
public final class BenchRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OneForAllSurvival.MODID);

    public static final RegistryObject<Block> ENGINEER_BENCH = BLOCKS.register("engineer_bench",
            () -> new EngineerBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5F).sound(SoundType.WOOD).noOcclusion()
                    // Seule la lampe allumée éclaire : une machine au travail se voit de nuit.
                    .lightLevel(state -> state.getValue(ElectricMachineBlock.STATE) == MachineState.RUNNING ? 10 : 0)));

    public static final RegistryObject<Item> ENGINEER_BENCH_ITEM = ITEMS.register("engineer_bench",
            () -> new BlockItem(ENGINEER_BENCH.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<EngineerBenchBlockEntity>> ENGINEER_BENCH_BE =
            BLOCK_ENTITIES.register("engineer_bench", () -> BlockEntityType.Builder
                    .of(EngineerBenchBlockEntity::new, ENGINEER_BENCH.get()).build(null));

    public static final RegistryObject<MenuType<EngineerBenchMenu>> ENGINEER_BENCH_MENU =
            MENUS.register("engineer_bench", () -> IForgeMenuType.create(EngineerBenchMenu::fromNetwork));

    public static final RegistryObject<MenuType<BenchStockMenu>> BENCH_STOCK_MENU =
            MENUS.register("bench_stock", () -> IForgeMenuType.create(BenchStockMenu::fromNetwork));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + OneForAllSurvival.MODID))
                    .icon(() -> new ItemStack(ENGINEER_BENCH_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ENGINEER_BENCH_ITEM.get());
                        RepairRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        SalvageRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        FoodRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        GridRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        FenceRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        TownRegistry.tabItems().forEach(item -> output.accept(item.get()));
                        LightRegistry.tabItems().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private BenchRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
