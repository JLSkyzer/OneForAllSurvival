package dev.oneforallsurvival.economy;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Ce que l'écran du coffre-fort ou de la bourse demande au serveur : le codec d'action commun aux deux paquets
 * ({@link SafeActionPacket}, {@link PurseActionPacket}). Le serveur ne croit rien : {@link #coinIfValid} refuse
 * toute action, coupure, montant ou drapeau qui ne correspond pas à un bouton de l'écran.
 *
 * <p>« Maj = autant que possible » est un drapeau ({@code all}) : le serveur calcule lui-même combien de change il
 * peut faire, jamais à partir d'un nombre envoyé par le client.
 */
public record CoinRequest(int action, int coin, int amount, boolean all) {

    /** Déposer toutes les pièces portées (le coffre-fort prend aussi celles des bourses). */
    public static final int DEPOSIT_ALL = 0;
    /** Retirer {@code amount} pièces de la coupure {@code coin}, toutes ou aucune. */
    public static final int WITHDRAW = 1;
    /** Regrouper tout le contenu (10 pour 1, à valeur égale). */
    public static final int GROUP_ALL = 2;
    /** Déposer jusqu'à {@code amount} pièces de la coupure {@code coin}, selon ce qui est porté et la place. */
    public static final int DEPOSIT = 3;
    /** Casser une pièce de la coupure {@code coin} en 10 de celle du dessous ({@code all} : autant que possible). */
    public static final int BREAK = 4;
    /** Regrouper 10 pièces de la coupure sous {@code coin} en 1 pièce {@code coin} ({@code all} : autant que possible). */
    public static final int GROUP_PAIR = 5;

    /** Les montants des boutons de l'écran : tout autre est refusé. */
    private static final int[] AMOUNTS = {1, 10, 64};

    public static CoinRequest depositAll() {
        return new CoinRequest(DEPOSIT_ALL, 0, 0, false);
    }

    public static CoinRequest groupAll() {
        return new CoinRequest(GROUP_ALL, 0, 0, false);
    }

    public static CoinRequest deposit(Coin coin, int amount) {
        return new CoinRequest(DEPOSIT, coin.ordinal(), amount, false);
    }

    public static CoinRequest withdraw(Coin coin, int amount) {
        return new CoinRequest(WITHDRAW, coin.ordinal(), amount, false);
    }

    /** {@code upper} : la pièce cassée (l'or pour « 1 or → 10 argent »). */
    public static CoinRequest breakCoin(Coin upper, boolean all) {
        return new CoinRequest(BREAK, upper.ordinal(), 0, all);
    }

    /** {@code upper} : la pièce obtenue (l'or pour « 10 argent → 1 or »). */
    public static CoinRequest groupPair(Coin upper, boolean all) {
        return new CoinRequest(GROUP_PAIR, upper.ordinal(), 0, all);
    }

    /** Les montants permis, ceux des boutons, dans l'ordre de l'écran. */
    public static int[] amounts() {
        return AMOUNTS.clone();
    }

    public static boolean isAmount(int amount) {
        for (int allowed : AMOUNTS) {
            if (allowed == amount) {
                return true;
            }
        }
        return false;
    }

    /**
     * La demande correspond-elle à un bouton de l'écran ? Renvoie la coupure visée (le cuivre pour une action
     * sans coupure), ou vide si quoi que ce soit est hors des bornes.
     */
    public Optional<Coin> coinIfValid() {
        Coin[] coins = Coin.values();
        switch (action) {
            case DEPOSIT_ALL, GROUP_ALL -> {
                return coin == 0 && amount == 0 && !all ? Optional.of(Coin.COPPER) : Optional.empty();
            }
            case WITHDRAW, DEPOSIT -> {
                return coin >= 0 && coin < coins.length && isAmount(amount) && !all
                        ? Optional.of(coins[coin]) : Optional.empty();
            }
            case BREAK, GROUP_PAIR -> {
                // La paire est nommée par sa pièce du dessus : le cuivre n'en a pas en dessous.
                return coin >= 1 && coin < coins.length && amount == 0 ? Optional.of(coins[coin]) : Optional.empty();
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(coin);
        buf.writeVarInt(amount);
        buf.writeBoolean(all);
    }

    public static CoinRequest decode(FriendlyByteBuf buf) {
        return new CoinRequest(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
