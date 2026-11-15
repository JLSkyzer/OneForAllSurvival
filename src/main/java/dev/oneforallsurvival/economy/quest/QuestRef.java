package dev.oneforallsurvival.economy.quest;

import java.time.LocalDate;
import javax.annotation.Nullable;

/**
 * La quête qu'un client a vue : sa liste, sa place, son modèle et la clé de sa série (jour, lundi, ou jour d'origine
 * d'une récompense en attente). Une référence dont le modèle ou la clé ne correspond plus (série renouvelée entre
 * l'affichage et le clic) ne désigne rien : le serveur la refuse. Pour une quête d'événement, {@code templateId} est
 * l'identifiant de l'événement et la clé n'est pas lue.
 */
public record QuestRef(Kind kind, int index, String templateId, @Nullable LocalDate seriesKey) {

    public enum Kind { DAILY, WEEKLY, PENDING, EVENT }
}
