package dev.oneforallsurvival.light;

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

/** Ce que l'éclairage de la ville enregistre : le phare. */
public final class LightRegistry {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OneForAllSurvival.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OneForAllSurvival.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OneForAllSurvival.MODID);

    public static final RegistryObject<Block> LIGHTHOUSE = BLOCKS.register("lighthouse",
            () -> new LighthouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(LighthouseBlock.LIT) ? LighthouseBlock.LIGHT : 0)));
    public static final RegistryObject<Item> LIGHTHOUSE_ITEM = ITEMS.register("lighthouse",
            () -> new BlockItem(LIGHTHOUSE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<LighthouseBlockEntity>> LIGHTHOUSE_BE =
            BLOCK_ENTITIES.register("lighthouse", () -> BlockEntityType.Builder
                    .of(LighthouseBlockEntity::new, LIGHTHOUSE.get()).build(null));

    private LightRegistry() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Les objets que l'onglet créatif affiche, dans l'ordre. */
    public static List<RegistryObject<? extends Item>> tabItems() {
        return List.of(LIGHTHOUSE_ITEM);
    }
}
