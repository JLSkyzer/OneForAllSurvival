package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaItem;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Ce que le téléphone enregistre : l'objet et la prise murale qui le recharge (bloc, objet, block entity), dont les
 * recettes sont des recettes de table (voir {@code table_only}) ; l'antenne d'un opérateur (lot T2), fabriquée à
 * l'établi de l'ingénieur.
 */
public final class PhoneRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);

    public static final RegistryObject<Item> PHONE = ITEMS.register("phone",
            () -> new PhoneItem(new Item.Properties().stacksTo(1)));

    /** Plaque de 2 px contre le mur : {@code noOcclusion()}, et la forme exacte est dans le bloc. */
    public static final RegistryObject<Block> WALL_SOCKET = BLOCKS.register("wall_socket",
            () -> new WallSocketBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(1.0F).sound(SoundType.STONE).noOcclusion()));
    public static final RegistryObject<Item> WALL_SOCKET_ITEM = ITEMS.register("wall_socket",
            () -> new BlockItem(WALL_SOCKET.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<WallSocketBlockEntity>> WALL_SOCKET_BE =
            BLOCK_ENTITIES.register("wall_socket", () -> BlockEntityType.Builder
                    .of(WallSocketBlockEntity::new, WALL_SOCKET.get()).build(null));

    /**
     * L'antenne : mât et semelle hors du cube, {@code noOcclusion()} et forme exacte dans le bloc. Protégée comme le poste
     * de fournisseur (dureté -1, résistance maximale, piston bloqué ; tags du Wither, du dragon et
     * {@code create:non_movable}) : la casse n'est rendue qu'aux ayants droit par {@link AntennaBlock#getDestroyProgress}.
     */
    public static final RegistryObject<Block> ANTENNA = BLOCKS.register("antenna",
            () -> new AntennaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion()));
    public static final RegistryObject<Item> ANTENNA_ITEM = ITEMS.register("antenna",
            () -> new AntennaItem(ANTENNA.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<AntennaBlockEntity>> ANTENNA_BE =
            BLOCK_ENTITIES.register("antenna", () -> BlockEntityType.Builder
                    .of(AntennaBlockEntity::new, ANTENNA.get()).build(null));

    private PhoneRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(PHONE, WALL_SOCKET_ITEM, ANTENNA_ITEM);
    }
}
