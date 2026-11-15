package dev.oneforallsurvival.economy.quest.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.Entry;
import dev.oneforallsurvival.economy.quest.QuestSyncPacket.State;
import java.time.LocalDate;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** Quand le toast « Quête terminée » paraît : une quête qui passe de « en cours » à « finie » entre deux états reçus. */
class QuestClientCacheTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 18);

    private static Entry quete(String id, int progres, State etat) {
        return new Entry(new QuestRef(QuestRef.Kind.DAILY, 0, id, JOUR), Component.literal(id), null, progres, 10, 20L,
                etat == State.COMPLETE ? 20L : 0L, etat);
    }

    private static QuestSyncPacket etat(Entry... quetes) {
        return new QuestSyncPacket(0L, 1_000L, 1, 1L, 2L, 0, List.of(quetes));
    }

    @Test
    void uneQueteQuiFinitDonneUnToast() {
        List<Entry> finies = QuestClientCache.newlyCompleted(etat(quete("a", 9, State.IN_PROGRESS)),
                etat(quete("a", 10, State.COMPLETE)));
        assertEquals(1, finies.size());
        assertEquals("a", finies.get(0).title().getString());
    }

    @Test
    void jamaisALaPremiereReception() {
        assertTrue(QuestClientCache.newlyCompleted(null, etat(quete("a", 10, State.COMPLETE))).isEmpty());
    }

    @Test
    void niPourUneQueteDejaFinieReclameeOuNeuve() {
        assertTrue(QuestClientCache.newlyCompleted(etat(quete("a", 10, State.COMPLETE)),
                etat(quete("a", 10, State.COMPLETE))).isEmpty(), "déjà finie");
        assertTrue(QuestClientCache.newlyCompleted(etat(quete("a", 10, State.COMPLETE)),
                etat(quete("a", 10, State.CLAIMED))).isEmpty(), "réclamée");
        // Relance ou renouvellement : une autre référence à la même place, déjà finie à la première vue.
        assertTrue(QuestClientCache.newlyCompleted(etat(quete("a", 3, State.IN_PROGRESS)),
                etat(quete("b", 10, State.COMPLETE))).isEmpty(), "quête neuve");
        assertTrue(QuestClientCache.newlyCompleted(etat(quete("a", 3, State.IN_PROGRESS)),
                etat(quete("a", 4, State.IN_PROGRESS))).isEmpty(), "toujours en cours");
    }
}
