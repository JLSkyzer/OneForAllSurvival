package dev.oneforallsurvival.grid.supplier;

import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Qui a la main sur un fournisseur : son poste, et (lot E6, tâche 3) ses compteurs et disjoncteurs jumelés. Les ayants
 * droit sont le fondateur, l'ingénieur, le maire et l'équipe (niveau 2) ; jamais le client en tant que tel, jamais un
 * faux joueur (déployeur, automate).
 *
 * <p>Côté client les rôles sont inconnus : ces règles laissent faire (progression de casse) et le serveur tranche, comme
 * {@code SafeBlock.mayBreak}.
 */
public final class PowerRules {

    private PowerRules() {}

    /** A-t-il la main sur ce fournisseur ? */
    public static boolean mayManage(Player player, PowerSupplier supplier) {
        return mayManage(player, supplier.founder());
    }

    /**
     * A-t-il la main sur ce qui appartient à {@code founder} (un poste encore sans fournisseur appartient à celui qui l'a
     * posé) ? {@code founder} nul : seuls l'ingénieur, le maire et l'équipe.
     */
    public static boolean mayManage(Player player, @Nullable UUID founder) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (server.hasPermissions(2) || RoleGate.isMayor(server)) {
            return true;
        }
        if (founder != null && founder.equals(server.getUUID())) {
            return true;
        }
        return RoleGate.isEngineer(RoleSavedData.get(server.server).book(), server.getUUID());
    }

    /**
     * Peut-il retirer de la caisse (et y faire le change) ? Le fondateur et l'équipe seulement : l'ingénieur et le
     * maire ont la main sur le poste, pas sur l'argent du fournisseur (le maire qui supprime un fournisseur verse la
     * caisse au fondateur, il ne la prend pas).
     */
    public static boolean mayTakeTill(Player player, PowerSupplier supplier) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return server.hasPermissions(2) || supplier.founder().equals(server.getUUID());
    }

    /** Ce joueur est-il le débiteur d'un compteur jumelé à ce fournisseur (quel que soit l'état du raccordement) ? */
    public static boolean isClientOf(Player player, PowerSupplier supplier, PowerSuppliers registry) {
        for (PowerClient client : registry.clients(supplier.id())) {
            if (client.player().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }
}
