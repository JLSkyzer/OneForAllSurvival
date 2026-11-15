package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.economy.EconomyRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * L'entité du tableau de la mairie, portée par sa seule partie maîtresse (le bas du milieu) : le rendu de tout le
 * panneau de 3 x 2 part d'elle. Aucune donnée à ce lot : les comptes viennent du serveur ({@code TownFinancesCache}).
 *
 * <p>Point d'extension du lot du maire : les annonces et le registre des décisions s'écriront ici ({@link #notices()}),
 * et le rendu les range sous les comptes, dans la place qui reste.
 */
public final class TownBoardBlockEntity extends BlockEntity {

    public TownBoardBlockEntity(BlockPos pos, BlockState state) {
        super(EconomyRegistry.TOWN_BOARD_BE.get(), pos, state);
    }

    /** Les lignes du maire à afficher sous les comptes (annonces, décisions) : aucune à ce lot. */
    public List<Component> notices() {
        return List.of();
    }

    /**
     * Chargé (y compris un ancien tableau d'un bloc, relu comme une maîtresse seule) : le bloc vérifie au tick suivant
     * que ses six parties sont là ({@link TownBoardBlock#tick}).
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && getBlockState().getBlock() instanceof TownBoardBlock block) {
            server.scheduleTick(worldPosition, block, 1);
        }
    }

    /** Le texte couvre les 3 x 2 blocs du panneau, quelle que soit l'orientation. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
    }
}
