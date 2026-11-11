package dev.oneforallsurvival.explore;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que l'exploration enregistre : la cache enfouie (les objets du détecteur, de la lampe, etc. s'y ajoutent). */
public final class ExploreRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);

    public static final RegistryObject<Block> CACHE = BLOCKS.register("buried_cache",
            () -> new CacheBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Item> CACHE_ITEM = ITEMS.register("buried_cache",
            () -> new BlockItem(CACHE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<CacheBlockEntity>> CACHE_BE =
            BLOCK_ENTITIES.register("buried_cache", () -> BlockEntityType.Builder
                    .of(CacheBlockEntity::new, CACHE.get()).build(null));

    private ExploreRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(CACHE_ITEM);
    }
}
