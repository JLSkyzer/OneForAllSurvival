package dev.oneforallsurvival.salvage;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Les débris d'avion et ce qu'on en tire : ferraille, fil, noyaux d'instruments et cellules de batterie. */
public final class SalvageRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final List<RegistryObject<? extends Item>> DEBRIS_ITEMS = new ArrayList<>();

    public static final RegistryObject<Block> DEBRIS_FUSELAGE = debris("debris_fuselage", MapColor.METAL);
    public static final RegistryObject<Block> DEBRIS_WIRING = debris("debris_wiring", MapColor.COLOR_ORANGE);
    public static final RegistryObject<Block> DEBRIS_INSTRUMENTS = debris("debris_instruments", MapColor.COLOR_GRAY);
    public static final RegistryObject<Block> DEBRIS_BATTERY = debris("debris_battery", MapColor.COLOR_BLACK);
    public static final RegistryObject<Block> DEBRIS_CABIN = debris("debris_cabin", MapColor.COLOR_LIGHT_GRAY);

    public static final RegistryObject<Item> SCRAP_METAL = item("scrap_metal");
    public static final RegistryObject<Item> AVIATION_WIRE = item("aviation_wire");
    public static final RegistryObject<Item> INSTRUMENT_CORE = item("instrument_core");
    public static final RegistryObject<Item> BATTERY_CELL = item("battery_cell");
    // Versions à terminer : leurs recettes arrivent avec les visuels finaux.
    public static final RegistryObject<Item> INCOMPLETE_INSTRUMENT_CORE = item("incomplete_instrument_core");
    public static final RegistryObject<Item> INCOMPLETE_BATTERY_CELL = item("incomplete_battery_cell");

    private SalvageRegistry() {}

    private static RegistryObject<Block> debris(String name, MapColor color) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new SalvageBlock(color));
        DEBRIS_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }

    private static RegistryObject<Item> item(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        List<RegistryObject<? extends Item>> items = new ArrayList<>(DEBRIS_ITEMS);
        items.addAll(List.of(SCRAP_METAL, AVIATION_WIRE, INSTRUMENT_CORE, BATTERY_CELL,
                INCOMPLETE_INSTRUMENT_CORE, INCOMPLETE_BATTERY_CELL));
        return items;
    }
}
