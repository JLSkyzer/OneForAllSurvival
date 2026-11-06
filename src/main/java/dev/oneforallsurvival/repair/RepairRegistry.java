package dev.oneforallsurvival.repair;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.machine.FaultType;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Les objets du métier de réparation : testeur, poste à souder et pièces de rechange. */
public final class RepairRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

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

    private RepairRegistry() {}

    private static RegistryObject<Item> part(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
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
        return List.of(TESTER, WELDER, FUSE, COIL, BEARING, CLEANING_KIT,
                TREATED_FUSE, TREATED_COIL, TREATED_BEARING, TREATED_CLEANING_KIT);
    }
}
