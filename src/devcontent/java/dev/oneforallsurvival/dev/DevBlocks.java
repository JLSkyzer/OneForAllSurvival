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
    private static final ResourceLocation TEST_INLINE_ID = new ResourceLocation("oneforallsurvival", "test_inline");

    // Créés pendant l'événement d'enregistrement : un Block ne se construit plus une fois les registres gelés.
    private static Block testMachine;
    private static BlockEntityType<TestMachineBlockEntity> testMachineType;
    private static Block testInline;
    private static BlockEntityType<TestInlineBlockEntity> testInlineType;

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

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            testMachine = new TestMachineBlock(BlockBehaviour.Properties.of().strength(1.0F));
            helper.register(TEST_MACHINE_ID, testMachine);
            testInline = new TestInlineBlock(BlockBehaviour.Properties.of().strength(1.0F));
            helper.register(TEST_INLINE_ID, testInline);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            testMachineType = BlockEntityType.Builder.of(TestMachineBlockEntity::new, testMachine).build(null);
            helper.register(TEST_MACHINE_ID, testMachineType);
            testInlineType = BlockEntityType.Builder.of(TestInlineBlockEntity::new, testInline).build(null);
            helper.register(TEST_INLINE_ID, testInlineType);
        });
        event.register(Registries.ITEM, helper -> {
            helper.register(TEST_MACHINE_ID, new BlockItem(testMachine, new Item.Properties()));
            helper.register(TEST_INLINE_ID, new BlockItem(testInline, new Item.Properties()));
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
