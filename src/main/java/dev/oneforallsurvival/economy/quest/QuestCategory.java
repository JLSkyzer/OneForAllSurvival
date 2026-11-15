package dev.oneforallsurvival.economy.quest;

import java.util.Locale;
import java.util.Optional;

/** Les gestes qu'une quête peut demander ; la clé est celle du fichier de données. */
public enum QuestCategory {
    BREAK(TargetKind.BLOCK, true),
    PLACE(TargetKind.BLOCK, true),
    KILL(TargetKind.ENTITY, true),
    HARVEST(TargetKind.BLOCK, true),
    FISH(TargetKind.ITEM, true),
    COOK(TargetKind.ITEM, true),
    PRESERVE(TargetKind.ITEM, true),
    BREED(TargetKind.ENTITY, true),
    JOB(TargetKind.ACTION, true),
    /** Livraison au grenier communal : aucun moyen de la compter aujourd'hui, donc refusée au chargement. */
    DELIVER(TargetKind.ITEM, false);

    /** Ce que désigne la cible d'une quête : le registre où la chercher. */
    public enum TargetKind { BLOCK, ENTITY, ITEM, ACTION }

    private final TargetKind targetKind;
    private final boolean counted;

    QuestCategory(TargetKind targetKind, boolean counted) {
        this.targetKind = targetKind;
        this.counted = counted;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<QuestCategory> byKey(String key) {
        for (QuestCategory category : values()) {
            if (category.key().equals(key)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    /** Faux quand rien ne sait encore compter cette catégorie. */
    public boolean counted() {
        return counted;
    }
}
