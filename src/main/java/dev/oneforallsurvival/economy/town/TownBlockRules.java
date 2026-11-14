package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Qui peut quoi sur les blocs de la ville (trésor, tableau de la mairie). « L'équipe » est le niveau 2.
 *
 * <p>Côté client les rôles sont inconnus : ces règles laissent faire (progression de casse, pose prédite) et le serveur
 * tranche, comme {@code SafeBlock.mayBreak}. Seule {@link #creativeNeedsCrouch} se lit des deux côtés.
 */
public final class TownBlockRules {

    private TownBlockRules() {}

    private static boolean staff(Player player) {
        return player.hasPermissions(2);
    }

    private static boolean mayor(ServerPlayer player) {
        return RoleGate.isMayor(player);
    }

    private static boolean engineer(ServerPlayer player) {
        return RoleGate.isEngineer(RoleSavedData.get(player.server).book(), player.getUUID());
    }

    /**
     * Ouvrir le trésor : le maire et l'équipe. Point d'extension : le crochetage du policier (feature 67) s'ajoutera
     * ici, comme pour le coffre-fort et l'étal.
     */
    public static boolean mayOpenTreasury(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server);
    }

    /** Déplacer (casser) un bloc de la ville : l'équipe, le maire, ou un ingénieur ({@link #engineerMayMove}). */
    public static boolean mayMove(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server) || engineerMayMove(server);
    }

    /**
     * L'ingénieur peut-il déplacer un bloc de la ville ? À ce lot, le rôle suffit. Point d'extension : le lot du
     * téléphone y exigera l'outil de maintenance en main.
     */
    public static boolean engineerMayMove(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return engineer(server);
    }

    /**
     * Poser le trésor : l'équipe ; le maire et l'ingénieur seulement s'il n'existe aucun trésor vivant (repose après
     * un déplacement). Même permise, la pose est refusée tant qu'un trésor vivant existe (un seul trésor).
     */
    public static boolean mayPlaceTreasury(Player player, boolean liveTreasuryExists) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (staff(server)) {
            return true;
        }
        return !liveTreasuryExists && (mayor(server) || engineer(server));
    }

    /** Poser un tableau de la mairie : le maire ou l'équipe. */
    public static boolean mayPlaceBoard(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server);
    }

    /** En créatif, casser un bloc de la ville exige d'être accroupi, pour tout le monde (contre l'accident). */
    public static boolean creativeNeedsCrouch(Player player) {
        return player.isCreative() && !player.isShiftKeyDown();
    }
}
