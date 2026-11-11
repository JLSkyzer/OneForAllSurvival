package dev.oneforallsurvival.light;

import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le phare : une machine électrique qui ne marche qu'au sommet d'une tour libre. Sa propriété {@code lit} suit l'état
 * {@code RUNNING} (le serveur la pose) et allume le bloc au niveau {@link #LIGHT}. Le modèle n'est pas un cube : forme
 * exacte et {@code noOcclusion()}.
 */
public final class LighthouseBlock extends ElectricMachineBlock {

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final int LIGHT = 15;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 6, 14),
            Block.box(4, 6, 4, 12, 16, 12));

    public LighthouseBlock(Properties properties) {
        super(properties, () -> LightRegistry.LIGHTHOUSE_BE.get());
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LighthouseBlockEntity(pos, state);
    }

    /** Vrai si un phare posé en {@code pos} serait en tour libre : il voit le ciel et domine assez la mer. */
    public static boolean isFreeTower(Level level, BlockPos pos) {
        return LightRules.isFreeTower(level.canSeeSky(pos.above()), pos.getY(), MachineConfig.seaLevel(),
                LightConfig.lighthouseMinAboveSea());
    }

    /** Un joueur qui pose le phare hors d'une tour libre apprend pourquoi il ne s'allumera pas. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player && !isFreeTower(level, pos)) {
            player.displayClientMessage(Component.translatable("message.oneforallsurvival.lighthouse.needs_sky",
                    LightConfig.lighthouseMinAboveSea()), true);
        }
    }
}
