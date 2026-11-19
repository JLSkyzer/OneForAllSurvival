package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que le téléphone enregistre : l'objet (la recette est une recette de table, voir {@code table_only}). */
public final class PhoneRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    public static final RegistryObject<Item> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    private PhoneRegistry() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(PHONE);
    }
}
