package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Blocs d'essai, enregistrés en développement seulement (jamais dans le jar livré). */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DevBlocks {

    private static final ResourceLocation TEST_MACHINE_ID = new ResourceLocation("oneforallsurvival", "test_machine");
    private static final ResourceLocation TEST_SOURCE_ID = new ResourceLocation("oneforallsurvival", "test_source");
    private static final ResourceLocation TEST_INLINE_ID =new ResourceLocation("oneforallsurvival", "test_inline");

    // Créés pendant l'événement d'enregistrement : un Block ne se construit plus une fois les registres gelés.
    private static Block testMachine;
    private static BlockEntityType<TestMachineBlockEntity> testMachineType;
    private static Block testInline;
    private static BlockEntityType<TestInlineBlockEntity> testInlineType;
    private static Block testSource;
    private static BlockEntityType<TestSourceBlockEntity> testSourceType;

    private DevBlocks() {}

    public static Block testMachine() {
        return testMachine;
    }

    public static BlockEntityType<TestMachineBlockEntity> testMachineType() {
        return testMachineType;
    }

    public static Block testInline() {
        return testInline;
    }

    public static BlockEntityType<TestInlineBlockEntity> testInlineType() {
        return testInlineType;
    }

    public static Block testSource() {
        return testSource;
    }

    public static BlockEntityType<TestSourceBlockEntity> testSourceType() {
        return testSourceType;
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            testMachine = new TestMachineBlock(BlockBehaviour.Properties.of().strength(1.0F));
            helper.register(TEST_MACHINE_ID, testMachine);
            testInline = new TestInlineBlock(BlockBehaviour.Properties.of().strength(1.0F));
            helper.register(TEST_INLINE_ID, testInline);
            testSource = new TestSourceBlock(BlockBehaviour.Properties.of().strength(1.0F));
            helper.register(TEST_SOURCE_ID, testSource);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            testMachineType = BlockEntityType.Builder.of(TestMachineBlockEntity::new, testMachine).build(null);
            helper.register(TEST_MACHINE_ID, testMachineType);
            testInlineType = BlockEntityType.Builder.of(TestInlineBlockEntity::new, testInline).build(null);
            helper.register(TEST_INLINE_ID, testInlineType);
            testSourceType = BlockEntityType.Builder.of(TestSourceBlockEntity::new, testSource).build(null);
            helper.register(TEST_SOURCE_ID, testSourceType);
        });
        event.register(Registries.ITEM, helper -> {
            helper.register(TEST_MACHINE_ID, new BlockItem(testMachine, new Item.Properties()));
            helper.register(TEST_INLINE_ID, new BlockItem(testInline, new Item.Properties()));
            helper.register(TEST_SOURCE_ID, new BlockItem(testSource, new Item.Properties()));
            // Résultats d'essai des recettes de l'établi (surcharges, exclusions, chaîne de fabrication).
            helper.register(new ResourceLocation("oneforallsurvival", "test_hidden"), new Item(new Item.Properties()));
            helper.register(new ResourceLocation("oneforallsurvival", "test_chain"), new Item(new Item.Properties()));
            // Pièce d'essai du bain galvanique et sa version traitée (recette de devcontent).
            helper.register(new ResourceLocation("oneforallsurvival", "test_part"), new Item(new Item.Properties()));
            helper.register(new ResourceLocation("oneforallsurvival", "test_part_done"), new Item(new Item.Properties()));
            // Aliments d'essai de la péremption : un non périssable (aussi dans le tag périssable) et un lent.
            helper.register(new ResourceLocation("oneforallsurvival", "test_non_perishable_food"), new Item(new Item.Properties()));
            helper.register(new ResourceLocation("oneforallsurvival", "test_slow_food"), new Item(new Item.Properties()));
        });
    }

    /** Le bloc de la machine d'essai : un clic droit affiche état, énergie et progression. */
    private static final class TestMachineBlock extends ElectricMachineBlock {

        TestMachineBlock(Properties properties) {
            super(properties, () -> testMachineType);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TestMachineBlockEntity(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TestMachineBlockEntity be) {
                player.sendSystemMessage(Component.literal("état " + be.state()
                        + ", " + be.energy().getEnergyStored() + "/" + be.energy().getMaxEnergyStored() + " FE"
                        + ", progression " + be.progress()));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Le bloc source d'essai : un accumulateur d'un autre mod, dont on peut tirer du courant. */
    private static final class TestSourceBlock extends net.minecraft.world.level.block.BaseEntityBlock {

        TestSourceBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TestSourceBlockEntity(pos, state);
        }

        @Override
        public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return net.minecraft.world.level.block.RenderShape.MODEL;
        }
    }

    /** Le bloc en ligne d'essai : un clic droit affiche le total transmis. */
    private static final class TestInlineBlock extends InlineEnergyBlock {

        TestInlineBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TestInlineBlockEntity(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TestInlineBlockEntity be) {
                player.sendSystemMessage(Component.literal("total transmis " + be.totalTransferred() + " FE"));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
