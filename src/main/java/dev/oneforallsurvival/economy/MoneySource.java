package dev.oneforallsurvival.economy;

import java.util.Locale;

/** D'où viennent les pièces créées. Chaque source a son total dans le registre. */
public enum MoneySource {
    /** Butin des créatures (lot E4). */
    MOB_LOOT,
    /** Récompenses de quêtes (lot E5). */
    QUEST,
    /** Somme de départ, donnée à la première connexion d'un joueur. */
    STARTING,
    /** Création par l'équipe ({@code /eco give}). */
    STAFF;

    /** Genre de la ligne du registre pour une création : {@code created.starting}. */
    public String kind() {
        return "created." + name().toLowerCase(Locale.ROOT);
    }

    /** Clé de langue du nom de la source. */
    public String langKey() {
        return "economy.oneforallsurvival.kind." + kind();
    }
}
