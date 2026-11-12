package dev.oneforallsurvival.economy;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que l'économie enregistre : les cinq pièces et la pièce au sol. */
public final class EconomyRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OneForAllSurvival.MODID);

    /** La pièce au sol : taille et suivi comme un objet au sol (0,25 bloc, vue à 6 chunks, mise à jour tous les 20 ticks). */
    public static final RegistryObject<EntityType<CoinDropEntity>> COIN_DROP = ENTITIES.register("coin_drop",
            () -> EntityType.Builder.<CoinDropEntity>of(CoinDropEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(20)
                    .build(OneForAllSurvival.MODID + ":coin_drop"));

    private static final Map<Coin, RegistryObject<Item>> COINS = new EnumMap<>(Coin.class);

    static {
        for (Coin coin : Coin.values()) {
            COINS.put(coin, ITEMS.register(coin.key() + "_coin", () -> new CoinItem(coin, new Item.Properties())));
        }
    }

    private EconomyRegistry() {}

    public static Item coinItem(Coin coin) {
        return COINS.get(coin).get();
    }

    public static EntityType<CoinDropEntity> coinDropType() {
        return COIN_DROP.get();
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return new ArrayList<>(COINS.values());
    }
}
