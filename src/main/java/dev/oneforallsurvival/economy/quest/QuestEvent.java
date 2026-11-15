package dev.oneforallsurvival.economy.quest;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Une quête d'événement lancée par l'équipe : commune à tous les joueurs, progression propre à chacun (dans son
 * {@link QuestBook}). {@code startMillis} est l'identité de l'événement avec son {@code id} : un événement relancé
 * sous le même identifiant a une autre date de début. {@code title} est un texte libre (peut être vide).
 */
public record QuestEvent(String id, QuestCategory category, @Nullable QuestTarget target, int goal, long reward,
        long startMillis, long endMillis, String title, UUID author) {

    /** Encore en cours à cet instant : seule la fin compte (une horloge reculée ne retire pas un événement). */
    public boolean runningAt(long now) {
        return now < endMillis;
    }

    /** La quête qu'un joueur reçoit à la première vue de l'événement ; son titre est le texte libre de l'équipe. */
    public Quest toQuest() {
        return new Quest(id, category, target, goal, reward, 0, false, title.isBlank() ? null : title);
    }
}
