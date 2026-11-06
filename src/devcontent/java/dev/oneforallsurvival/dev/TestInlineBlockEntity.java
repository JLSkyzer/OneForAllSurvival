package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Bloc en ligne d'essai : plafond de 100 FE par tick, qu'un test peut fermer. */
public class TestInlineBlockEntity extends InlineEnergyBlockEntity {

    private boolean closed;

    public TestInlineBlockEntity(BlockPos pos, BlockState state) {
        super(DevBlocks.testInlineType(), pos, state, 100);
    }

    @Override
    protected boolean isOpen() {
        return !closed;
    }

    /** Expose la fermeture aux GameTests, qui vivent dans un autre paquet. */
    public void fermer(boolean closed) {
        this.closed = closed;
    }
}
