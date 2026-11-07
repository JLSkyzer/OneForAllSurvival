package dev.oneforallsurvival.food;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Les objets de la péremption : pour l'instant le déchet, ce qu'un aliment avarié devient. */
public final class FoodRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    /** Non comestible, compostable. */
    public static final RegistryObject<Item> SPOILED_FOOD = ITEMS.register("spoiled_food",
            () -> new Item(new Item.Properties()));

    private FoodRegistry() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // La table de compostage est partagée : on n'y touche que sur le fil principal.
        event.enqueueWork(() -> ComposterBlock.COMPOSTABLES.put(SPOILED_FOOD.get(), 0.5F));
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(SPOILED_FOOD);
    }
}
