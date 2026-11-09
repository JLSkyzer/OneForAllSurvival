package dev.oneforallsurvival.fence;

import dev.oneforallsurvival.OneForAllSurvival;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Ce que la clôture électrique enregistre : blocs, objets, block entity. Le type de dégâts vient des données. */
public final class FenceRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);

    /** Le type de dégâts du choc ({@code data/oneforallsurvival/damage_type/electric_fence.json}). */
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(OneForAllSurvival.MODID, "electric_fence"));

    /** Les blocs qui fuient le courant de la ligne : feuilles, herbes, fleurs, pousses, lianes. */
    public static final TagKey<Block> VEGETATION =
            TagKey.create(Registries.BLOCK, new ResourceLocation(OneForAllSurvival.MODID, "fence_vegetation"));

    public static final RegistryObject<Block> ELECTRIC_FENCE = BLOCKS.register("electric_fence",
            () -> new ElectricFenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> ELECTRIC_FENCE_ITEM = ITEMS.register("electric_fence",
            () -> new BlockItem(ELECTRIC_FENCE.get(), new Item.Properties()));

    public static final RegistryObject<Block> ELECTRIFIER = BLOCKS.register("electrifier",
            () -> new ElectrifierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> ELECTRIFIER_ITEM = ITEMS.register("electrifier",
            () -> new BlockItem(ELECTRIFIER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ElectrifierBlockEntity>> ELECTRIFIER_BE =
            BLOCK_ENTITIES.register("electrifier", () -> BlockEntityType.Builder
                    .of(ElectrifierBlockEntity::new, ELECTRIFIER.get()).build(null));

    private FenceRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(ELECTRIFIER_ITEM, ELECTRIC_FENCE_ITEM);
    }
}
