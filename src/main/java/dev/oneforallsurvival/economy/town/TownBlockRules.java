package dev.oneforallsurvival.economy.town;

import dev.oneforallsurvival.phone.net.AntennaRules;
import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Qui peut quoi sur les blocs de la ville (trésor, tableau de la mairie). « L'équipe » est le niveau 2.
 *
 * <p>Côté client les rôles sont inconnus : ces règles laissent faire (progression de casse, pose prédite) et le serveur
 * tranche, comme {@code SafeBlock.mayBreak}. Seule {@link #creativeNeedsCrouch} se lit des deux côtés.
 *
 * <p>Un faux joueur n'a aucun droit : celui du déployeur de Create rend l'UUID du joueur qui a posé le déployeur
 * ({@code DeployerFakePlayer.getUUID}, lu dans le jar de Create 6.0.8), donc le rôle de maire ou d'ingénieur de ce joueur.
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
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server);
    }

    /** Déplacer (casser) un bloc de la ville : l'équipe, le maire, ou un ingénieur ({@link #engineerMayMove}). */
    public static boolean mayMove(Player player) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server) || engineerMayMove(server);
    }

    /**
     * L'ingénieur peut-il déplacer un bloc de la ville ? Le rôle d'ingénieur <b>et</b> l'outil de maintenance dans une des
     * deux mains (lot T2) ; l'outil seul ne donne rien, un faux joueur jamais.
     */
    public static boolean engineerMayMove(Player player) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return engineer(server) && AntennaRules.hasMaintenanceTool(server);
    }

    /**
     * Poser le trésor : l'équipe ; le maire et l'ingénieur (outil de maintenance en main, {@link #engineerMayMove})
     * seulement s'il n'existe aucun trésor vivant (repose après un déplacement). Même permise, la pose est refusée tant
     * qu'un trésor vivant existe (un seul trésor).
     */
    public static boolean mayPlaceTreasury(Player player, boolean liveTreasuryExists) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (staff(server)) {
            return true;
        }
        return !liveTreasuryExists && (mayor(server) || engineerMayMove(server));
    }

    /** Poser un tableau de la mairie : le maire ou l'équipe. */
    public static boolean mayPlaceBoard(Player player) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staff(server) || mayor(server);
    }

    /** En créatif, casser un bloc de la ville exige d'être accroupi, pour tout le monde (contre l'accident). */
    public static boolean creativeNeedsCrouch(Player player) {
        return player.isCreative() && !player.isShiftKeyDown();
    }

    /**
     * Pourquoi ce joueur ne peut pas déplacer (casser) un bloc de la ville : {@code "crouch"} (créatif sans être
     * accroupi) ou {@code "break_denied"} (pas le droit), ou nul si la casse est permise. Les clés de langue des blocs
     * portent ces deux suffixes.
     */
    @Nullable
    public static String moveRefusal(Player player) {
        if (creativeNeedsCrouch(player)) {
            return "crouch";
        }
        return mayMove(player) ? null : "break_denied";
    }

    /**
     * La progression de casse d'un bloc de la ville (dureté -1) : nulle pour qui ne peut pas le déplacer, sinon celle
     * d'un bloc de dureté 5 sans outil exigé.
     */
    public static float moveProgress(Player player, BlockState state, BlockPos pos) {
        if (moveRefusal(player) != null) {
            return 0.0F;
        }
        return player.getDigSpeed(state, pos) / 5.0F / 30.0F;
    }
}
