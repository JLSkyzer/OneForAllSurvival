package dev.oneforallsurvival.economy.quest;

import java.time.LocalDate;
import javax.annotation.Nullable;

/**
 * La quête qu'un client a vue : sa liste, sa place, son modèle et la clé de sa série (jour, lundi, ou jour d'origine
 * d'une récompense en attente). Une référence dont le modèle ou la clé ne correspond plus (série renouvelée entre
 * l'affichage et le clic) ne désigne rien : le serveur la refuse.
 *
 * <ul>
 *   <li>{@code PENDING} : {@code origin} dit de quelle liste (journalière, hebdomadaire, événement) vient la
 *       récompense ; deux attentes de même modèle et de même clé de série, l'une journalière, l'autre hebdomadaire
 *       (le lundi), ne se confondent pas.</li>
 *   <li>{@code EVENT} : {@code templateId} est l'identifiant de l'événement et {@code eventKey} sa date de début
 *       (ms) : un événement relancé sous le même identifiant est un autre événement, un clic périmé sur l'ancien ne
 *       réclame pas le nouveau. {@code seriesKey} en porte le jour (UTC), à titre d'information.</li>
 * </ul>
 */
public record QuestRef(Kind kind, int index, String templateId, @Nullable LocalDate seriesKey, @Nullable Kind origin,
        long eventKey) {

    public enum Kind { DAILY, WEEKLY, PENDING, EVENT }

    /** Une référence de série (journalière ou hebdomadaire), sans origine ni clé d'événement. */
    public QuestRef(Kind kind, int index, String templateId, @Nullable LocalDate seriesKey) {
        this(kind, index, templateId, seriesKey, null, 0L);
    }

    /** La référence d'une quête d'événement : son identifiant et sa date de début. */
    public static QuestRef event(String eventId, long startMillis) {
        return new QuestRef(Kind.EVENT, 0, eventId, QuestBook.eventDay(startMillis), null, startMillis);
    }
}
