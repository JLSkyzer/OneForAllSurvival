package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.EconomyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'entité du tableau de la mairie : aucune donnée, le rendu lit les comptes publics reçus du serveur
 * ({@code TownFinancesCache}). Point d'extension du lot du maire : les annonces et le registre des décisions
 * s'afficheront ici.
 */
public final class TownBoardBlockEntity extends BlockEntity {

    public TownBoardBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.TOWN_BOARD_BE.get(), pos, state);
    }
}
