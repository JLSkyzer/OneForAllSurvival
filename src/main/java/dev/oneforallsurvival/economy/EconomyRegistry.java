package dev.oneforallsurvival.economy;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que l'économie enregistre : les cinq pièces, la pièce au sol, le coffre-fort personnel et la bourse. */
public final class EconomyRegistry {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OneForAllSurvival.MODID);

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, OneForAllSurvival.MODID);

    /** La pièce au sol : taille et suivi comme un objet au sol (0,25 bloc, vue à 6 chunks, mise à jour tous les 20 ticks). */
    public static final RegistryObject<EntityType<CoinDropEntity>> COIN_DROP = ENTITIES.register("coin_drop",
            () -> EntityType.Builder.<CoinDropEntity>of(CoinDropEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(20)
                    .build(OneForAllSurvival.MODID + ":coin_drop"));

    /**
     * Le coffre-fort personnel. Dureté -1 et résistance maximale (aucune foreuse, explosion, Wither ni dragon ne
     * l'entame, et le piston ne le pousse pas) ; seuls ceux qui y ont droit le cassent, voir
     * {@link SafeBlock#getDestroyProgress}. Son butin est le seul bloc : le contenu ne tombe jamais.
     */
    public static final RegistryObject<Block> SAFE = BLOCKS.register("safe",
            () -> new SafeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> SAFE_ITEM = ITEMS.register("safe",
            () -> new BlockItem(SAFE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SafeBlockEntity>> SAFE_BE =
            BLOCK_ENTITIES.register("safe", () -> BlockEntityType.Builder.of(SafeBlockEntity::new, SAFE.get()).build(null));
    public static final RegistryObject<MenuType<SafeMenu>> SAFE_MENU =
            MENUS.register("safe", () -> IForgeMenuType.create(SafeMenu::fromNetwork));

    /** La bourse : ne s'empile pas, garde les pièces dans son NBT ({@link Purse}). */
    public static final RegistryObject<Item> PURSE = ITEMS.register("purse", () -> new PurseItem(new Item.Properties()));
    public static final RegistryObject<MenuType<PurseMenu>> PURSE_MENU =
            MENUS.register("purse", () -> IForgeMenuType.create(PurseMenu::fromNetwork));

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
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(EconomyRegistry::onCommonSetup);
    }

    /**
     * Le coffre-fort et l'écran de la bourse sont des rangements permis pour l'argent, et une bourse qui contient au
     * moins une pièce est de l'argent. Déclaré sur les deux côtés (l'évènement tourne aussi sur le client) : le
     * client ne doit pas prévoir un refus que le serveur ne ferait pas.
     */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Les champs privés que la pièce au sol relit d'un objet remplacé : absents, le chargement échoue ici.
        CoinDropEntity.checkFields();
        event.enqueueWork(() -> {
            MoneyStorageRules.register(SAFE_MENU.get());
            MoneyStorageRules.register(PURSE_MENU.get());
            MoneyStorageRules.registerMoneyPredicate(stack -> Purse.isPurse(stack) && Purse.total(stack) > 0L);
        });
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        List<RegistryObject<? extends Item>> items = new ArrayList<>(COINS.values());
        items.add(SAFE_ITEM);
        items.add(PURSE);
        return items;
    }
}
