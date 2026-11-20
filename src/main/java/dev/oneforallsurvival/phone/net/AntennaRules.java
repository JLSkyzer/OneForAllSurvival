package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.repair.MaintenanceToolItem;
import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Qui a la main sur une antenne et sur son opérateur (lot T2). « L'équipe » est le niveau 2.
 *
 * <ul>
 *   <li>Régler et déplacer une antenne ({@link #mayManage}) : le fondateur de son opérateur, le maire, l'équipe, et
 *       l'ingénieur qui tient l'outil de maintenance (le rôle <b>et</b> l'outil, jamais l'outil seul).
 *   <li>Fonder un opérateur au nom du poseur d'une antenne neuve ({@link #mayFoundFor}) : le poseur lui-même, le maire et
 *       l'équipe ; l'ingénieur, même outillé, ne fonde pas au nom d'un autre.
 *   <li>Les cartes ({@link #mayManageCards}) : le fondateur seul. La caisse ({@link #mayTakeTill}) : le fondateur et
 *       l'équipe. Le tarif ({@link #maySetTariff}) : le fondateur, le maire et l'équipe.
 * </ul>
 *
 * <p>Jamais un faux joueur (déployeur, automate). Côté client les rôles sont inconnus : ces règles laissent faire
 * (progression de casse, pose prédite) et le serveur tranche, comme {@code PowerRules}.
 */
public final class AntennaRules {

    private AntennaRules() {}

    /** Une des deux mains de ce joueur porte-t-elle un outil de maintenance (tag {@code maintenance_tools}) ? */
    public static boolean hasMaintenanceTool(Player player) {
        return MaintenanceToolItem.isHeldBy(player);
    }

    private static boolean staffOrMayor(ServerPlayer player) {
        return player.hasPermissions(2) || RoleGate.isMayor(player);
    }

    private static boolean isEngineer(ServerPlayer player) {
        return RoleGate.isEngineer(RoleSavedData.get(player.server).book(), player.getUUID());
    }

    /**
     * A-t-il la main sur ce qui appartient à {@code founder} (une antenne encore sans opérateur appartient à qui l'a
     * posée) ? {@code founder} nul : le maire, l'équipe et l'ingénieur outillé seulement.
     */
    public static boolean mayManage(Player player, @Nullable UUID founder) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (staffOrMayor(server) || (founder != null && founder.equals(server.getUUID()))) {
            return true;
        }
        return isEngineer(server) && hasMaintenanceTool(server);
    }

    /**
     * Peut-il fonder l'opérateur d'une antenne neuve posée par {@code placer} ? Au nom du poseur : le poseur lui-même, le
     * maire et l'équipe. Sans poseur noté ({@code /setblock}), l'acteur fonde à son nom : mêmes droits que
     * {@link #mayManage} sur une antenne sans propriétaire.
     */
    public static boolean mayFoundFor(Player player, @Nullable UUID placer) {
        if (placer == null) {
            return mayManage(player, null);
        }
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staffOrMayor(server) || placer.equals(server.getUUID());
    }

    /** Émettre, suspendre ou retirer les cartes de cet opérateur : le fondateur seul. */
    public static boolean mayManageCards(Player player, PhoneOperator operator) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return operator.founder().equals(server.getUUID());
    }

    /** Retirer de la caisse de cet opérateur : le fondateur et l'équipe (le maire et l'ingénieur, non). */
    public static boolean mayTakeTill(Player player, PhoneOperator operator) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return server.hasPermissions(2) || operator.founder().equals(server.getUUID());
    }

    /** Régler le tarif de cet opérateur : le fondateur, le maire et l'équipe. */
    public static boolean maySetTariff(Player player, PhoneOperator operator) {
        if (player instanceof FakePlayer) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        return staffOrMayor(server) || operator.founder().equals(server.getUUID());
    }
}
