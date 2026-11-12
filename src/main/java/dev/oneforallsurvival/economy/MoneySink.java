package dev.oneforallsurvival.economy;

import java.util.Locale;

/** Pourquoi des pièces ont disparu pour de bon. Chaque cause a son total dans le registre. */
public enum MoneySink {
    /** Feu ou lave. */
    FIRE,
    /** Sous le monde. */
    VOID,
    /** Restée 5 minutes au sol. */
    DESPAWN,
    /** Retrait par l'équipe ({@code /eco take}), ou pièce au sol retirée par une commande ou un autre mod. */
    STAFF;

    /** Genre de la ligne du registre pour une destruction : {@code destroyed.fire}. */
    public String kind() {
        return "destroyed." + name().toLowerCase(Locale.ROOT);
    }

    /** Clé de langue du nom de la cause. */
    public String langKey() {
        return "economy.oneforallsurvival.kind." + kind();
    }
}
