package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.EconomyRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'entité du bloc du trésor. Le contenu n'est pas ici, il vit dans {@code TreasuryAccount} (avec l'emplacement noté
 * et le registre). Elle ne retient, le temps d'une casse, que le joueur qui casse : la ligne « Trésor retiré » du
 * registre porte son nom. Rien n'est sauvegardé.
 */
public final class TreasuryBlockEntity extends BlockEntity {

    @Nullable
    private Player breaker;
    /** Transitoire : le bloc cassé était-il le trésor noté ? Sinon (bloc de {@code /setblock}), il ne lâche rien. */
    private boolean notedWhenBroken;

    public TreasuryBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.TREASURY_BE.get(), pos, state);
    }

    /** Transitoire : le joueur qui casse le bloc, posé juste avant le retrait. */
    void setBreaker(@Nullable Player player) {
        this.breaker = player;
    }

    @Nullable
    Player breaker() {
        return breaker;
    }

    void setNotedWhenBroken(boolean noted) {
        this.notedWhenBroken = noted;
    }

    boolean notedWhenBroken() {
        return notedWhenBroken;
    }
}
