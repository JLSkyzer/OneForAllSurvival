package dev.oneforallsurvival.client.title;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Tri des widgets de l'écran titre : logique pure, sans classe Minecraft (testée par {@code TitleWidgetsTest}).
 *
 * <p>Un widget est reconnu par la clé de traduction de son libellé (celle de {@code TranslatableContents}), ou
 * {@code null} si son libellé n'est pas traduit (texte littéral, libellé vide comme le bouton de Create). Les clés
 * sont celles que crée {@code TitleScreen.init} de Minecraft 1.20.1 avec les ajouts de Forge 47.4.28 (lues dans le
 * code désassemblé du jar du jeu).
 */
public final class TitleWidgets {
    /** Pseudo-clé du bouton de la mention de Mojang (libellé littéral {@code TitleScreen.COPYRIGHT_TEXT}). */
    public static final String COPYRIGHT_KEY = "oneforallsurvival:title.copyright";
    public static final String MODS_KEY = "fml.menu.mods";
    public static final String LANGUAGE_KEY = "narrator.button.language";
    public static final String ACCESSIBILITY_KEY = "narrator.button.accessibility";

    /** Boutons principaux de Minecraft retirés : nos quatre boutons les remplacent, Multijoueur et Realms n'ont pas d'équivalent. */
    private static final Set<String> REMOVED = Set.of("menu.singleplayer", "menu.multiplayer", "menu.online",
            "menu.options", "menu.quit", "menu.playdemo", "menu.resetdemo");
    /** Petits boutons de Minecraft et de Forge gardés (rangée du bas), et la mention de Mojang (en bas à droite). */
    private static final Set<String> KEPT = Set.of(MODS_KEY, LANGUAGE_KEY, ACCESSIBILITY_KEY, COPYRIGHT_KEY);

    private TitleWidgets() {}

    /** Origine d'un widget de l'écran titre. */
    public enum Kind {
        /** Créé par {@code LostIslandTitleScreen}. */
        OURS,
        /** Créé par Minecraft ou Forge et gardé. */
        VANILLA_KEPT,
        /** Créé par Minecraft et retiré par l'écran du mod. */
        VANILLA_REMOVED,
        /** Tout le reste : ajouté par un autre mod (rangé dans la rangée du bas, jamais caché). */
        FOREIGN
    }

    /**
     * @param translationKey clé de traduction du libellé, {@link #COPYRIGHT_KEY} pour la mention de Mojang,
     *                       {@code null} si le libellé n'est pas traduit
     * @param ours           le widget a été créé par l'écran du mod (prime sur la clé)
     */
    public static Kind classify(String translationKey, boolean ours) {
        if (ours) {
            return Kind.OURS;
        }
        if (translationKey == null) {
            return Kind.FOREIGN;
        }
        if (REMOVED.contains(translationKey)) {
            return Kind.VANILLA_REMOVED;
        }
        if (KEPT.contains(translationKey)) {
            return Kind.VANILLA_KEPT;
        }
        return Kind.FOREIGN;
    }

    /**
     * Rang dans la rangée du bas : liste des mods, langue, accessibilité, puis tout le reste dans l'ordre
     * d'ajout (tri stable).
     */
    public static int rowRank(String translationKey, Kind kind) {
        if (kind == Kind.VANILLA_KEPT && translationKey != null) {
            switch (translationKey) {
                case MODS_KEY:
                    return 0;
                case LANGUAGE_KEY:
                    return 1;
                case ACCESSIBILITY_KEY:
                    return 2;
                default:
                    break;
            }
        }
        return 3;
    }

    /**
     * Ordre de la rangée du bas pour des widgets (non nôtres) donnés par leurs clés, dans l'ordre d'ajout : indices
     * triés par {@link #rowRank}, tri stable (l'ordre d'ajout départage).
     */
    public static List<Integer> rowOrder(List<String> keys) {
        List<Integer> order = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> rowRank(keys.get(i), classify(keys.get(i), false))));
        return order;
    }
}
