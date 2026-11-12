package dev.oneforallsurvival.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * La somme de départ : donnée une seule fois par joueur, à sa toute première connexion. Le registre retient les
 * identifiants servis, donc ni une reconnexion, ni la mort, ni un changement de pseudo (l'identifiant reste le
 * même) ne la redonnent.
 */
public final class StartingMoney {

    private StartingMoney() {}

    /** Branché sur la sortie de connexion de Forge. */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grantIfNew(player);
        }
    }

    /**
     * Donne la somme de départ à ce joueur si son identifiant est inconnu du registre. L'identifiant est noté
     * <b>avant</b> de donner : si la suite échouait, le joueur ne la recevrait pas deux fois. Ce qui ne rentre pas
     * dans l'inventaire tombe au sol à ses pieds. Un montant de départ nul note quand même le joueur.
     *
     * @return vrai si le joueur était nouveau
     */
    public static boolean grantIfNew(ServerPlayer player) {
        EconomyLedger ledger = EconomyLedger.get(player.server);
        if (!ledger.markStartingGiven(player.getUUID())) {
            return false;
        }
        long amount = EconomyConfig.startingMoney();
        if (amount > 0L) {
            PlayerCoins.giveOrDrop(player, CoinMath.decompose(amount));
            ledger.created(MoneySource.STARTING, amount, player.getUUID(), "somme de depart");
        }
        return true;
    }
}
