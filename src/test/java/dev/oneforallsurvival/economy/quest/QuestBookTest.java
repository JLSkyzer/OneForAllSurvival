package dev.oneforallsurvival.economy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class QuestBookTest {

    /** Mercredi 18 novembre 2026, et le lundi de sa semaine. */
    private static final LocalDate MERCREDI = LocalDate.of(2026, 11, 18);
    private static final LocalDate LUNDI = LocalDate.of(2026, 11, 16);

    /** Tireur factice : numérote ses quêtes (q1, q2…), 3 journalières et 5 hebdomadaires. */
    private static final class Tireur implements Function<Series, List<Quest>> {
        int next = 1;
        int calls;

        @Override
        public List<Quest> apply(Series series) {
            calls++;
            List<Quest> out = new ArrayList<>();
            for (int i = 0; i < (series == Series.DAILY ? 3 : 5); i++) {
                out.add(quest("q" + next++));
            }
            return out;
        }

        Function<Set<String>, Optional<Quest>> relance() {
            return excluded -> {
                String id = "r" + next++;
                assertFalse(excluded.contains(id));
                return Optional.of(quest(id));
            };
        }
    }

    private static Quest quest(String id) {
        return new Quest(id, QuestCategory.BREAK, new QuestTarget(new ResourceLocation("minecraft", "stone"), false),
                10, 20L, 0, false, "quest.oneforallsurvival.template.break_stone");
    }

    private static QuestBook neuf(Tireur tireur) {
        QuestBook book = new QuestBook();
        book.refresh(MERCREDI, LUNDI, tireur, 8);
        return book;
    }

    private static void finir(QuestBook book, Series series, int index) {
        List<Quest> list = series == Series.DAILY ? book.daily() : book.weekly();
        book.set(series, index, list.get(index).withProgress(list.get(index).goal()));
    }

    @Test
    void premiereConnexionDonneTroisEtCinq() {
        Tireur tireur = new Tireur();
        QuestBook book = new QuestBook();
        assertTrue(book.refresh(MERCREDI, LUNDI, tireur, 8));
        assertEquals(3, book.daily().size());
        assertEquals(5, book.weekly().size());
        assertEquals(MERCREDI, book.dailyKey());
        assertEquals(LUNDI, book.weeklyKey());
    }

    @Test
    void memeJourRienNeChange() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        CompoundTag before = book.save();
        assertFalse(book.refresh(MERCREDI, LUNDI, tireur, 8));
        assertEquals(before, book.save());
        assertEquals(2, tireur.calls);
    }

    @Test
    void lendemainNouvellesJournalieresMemeHebdomadaires() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        List<Quest> daily = book.daily();
        List<Quest> weekly = book.weekly();
        assertTrue(book.refresh(MERCREDI.plusDays(1), LUNDI, tireur, 8));
        assertNotEquals(daily, book.daily());
        assertEquals(MERCREDI.plusDays(1), book.dailyKey());
        assertEquals(weekly, book.weekly());
        assertEquals(LUNDI, book.weeklyKey());
    }

    @Test
    void lundiNouvellesHebdomadaires() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        List<Quest> weekly = book.weekly();
        LocalDate lundiSuivant = LUNDI.plusWeeks(1);
        assertTrue(book.refresh(lundiSuivant, lundiSuivant, tireur, 8));
        assertNotEquals(weekly, book.weekly());
        assertEquals(lundiSuivant, book.weeklyKey());
        assertEquals(lundiSuivant, book.dailyKey());
    }

    @Test
    void horlogeReculeeToutEstGarde() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        assertTrue(book.reroll(Series.DAILY, 0, MERCREDI, tireur.relance()).isPresent());
        book.recordPaid(LUNDI, 300L);
        CompoundTag before = book.save();
        // L'horloge recule d'un jour, puis d'une semaine : rien ne change, rien ne revient.
        assertFalse(book.refresh(MERCREDI.minusDays(1), LUNDI, tireur, 8));
        assertFalse(book.refresh(MERCREDI.minusDays(7), LUNDI.minusWeeks(1), tireur, 8));
        assertEquals(before, book.save());
        assertFalse(book.canReroll(MERCREDI.minusDays(1), 1), "relance rendue par une horloge reculée");
        assertEquals(700L, book.capRemaining(LUNDI.minusWeeks(1), 1_000L), "plafond remis par une horloge reculée");
        // Une relance prise pendant le recul compte avec celle du jour gardé.
        assertTrue(book.canReroll(MERCREDI.minusDays(1), 2));
        assertTrue(book.reroll(Series.DAILY, 1, MERCREDI.minusDays(1), tireur.relance()).isPresent());
        assertFalse(book.canReroll(MERCREDI, 2));
        book.recordPaid(LUNDI.minusWeeks(1), 100L);
        assertEquals(600L, book.capRemaining(LUNDI, 1_000L));
        // L'heure rattrapée, puis dépassée : tout reprend.
        assertFalse(book.refresh(MERCREDI, LUNDI, tireur, 8));
        assertTrue(book.canReroll(MERCREDI.plusDays(1), 1));
    }

    @Test
    void dixJoursSautesUnSeulRenouvellement() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        int calls = tireur.calls;
        LocalDate plusDix = MERCREDI.plusDays(10);
        assertTrue(book.refresh(plusDix, plusDix.with(java.time.DayOfWeek.MONDAY), tireur, 8));
        // Une série journalière et une hebdomadaire, pas dix.
        assertEquals(calls + 2, tireur.calls);
        assertEquals(plusDix, book.dailyKey());
        assertEquals(3, book.daily().size());
        assertFalse(book.refresh(plusDix, plusDix.with(java.time.DayOfWeek.MONDAY), tireur, 8));
    }

    @Test
    void uneFinieNonReclameePasseEnAttente() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        finir(book, Series.DAILY, 1);
        Quest finie = book.daily().get(1);
        // Une finie déjà réclamée ne passe pas en attente.
        finir(book, Series.DAILY, 2);
        book.set(Series.DAILY, 2, book.daily().get(2).claimedNow());
        book.refresh(MERCREDI.plusDays(1), LUNDI, tireur, 8);
        assertEquals(1, book.pending().size());
        assertEquals(finie, book.pending().get(0).quest());
        assertEquals(MERCREDI, book.pending().get(0).seriesKey());
        assertEquals(0, book.forgotten());
        // Elle se réclame par sa référence ; une seconde fois, elle n'existe plus.
        QuestRef ref = new QuestRef(QuestRef.Kind.PENDING, 0, finie.templateId(), MERCREDI);
        assertEquals(Optional.of(finie), book.find(ref));
        assertTrue(book.markClaimed(ref));
        assertTrue(book.pending().isEmpty());
        assertFalse(book.markClaimed(ref));
    }

    @Test
    void laPlusAncienneAttenteEstOubliee() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        List<String> finies = new ArrayList<>();
        LocalDate day = MERCREDI;
        // 9 jours, une journalière finie chaque jour.
        for (int i = 0; i < 9; i++) {
            finir(book, Series.DAILY, 0);
            finies.add(book.daily().get(0).templateId());
            day = day.plusDays(1);
            book.refresh(day, LUNDI, tireur, 8);
        }
        assertEquals(8, book.pending().size());
        assertEquals(1, book.forgotten());
        assertEquals(finies.subList(1, 9), book.pending().stream().map(p -> p.quest().templateId()).toList());
    }

    @Test
    void uneNonFinieExpireSansRien() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        book.set(Series.DAILY, 0, book.daily().get(0).withProgress(9));
        book.refresh(MERCREDI.plusDays(1), LUNDI, tireur, 8);
        assertTrue(book.pending().isEmpty());
    }

    @Test
    void uneRelanceParJour() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        assertTrue(book.canReroll(MERCREDI, 1));
        Quest avant = book.daily().get(0);
        Optional<Quest> nouvelle = book.reroll(Series.DAILY, 0, MERCREDI, tireur.relance());
        assertTrue(nouvelle.isPresent());
        assertEquals(nouvelle.get(), book.daily().get(0));
        assertNotEquals(avant.templateId(), book.daily().get(0).templateId());
        assertFalse(book.canReroll(MERCREDI, 1));
        // Le lendemain, la relance est rendue.
        assertTrue(book.canReroll(MERCREDI.plusDays(1), 1));
        assertFalse(book.canReroll(MERCREDI, 0));
    }

    @Test
    void onNeRelancePasUneFinie() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        finir(book, Series.WEEKLY, 3);
        Quest finie = book.weekly().get(3);
        assertTrue(book.reroll(Series.WEEKLY, 3, MERCREDI, tireur.relance()).isEmpty());
        assertEquals(finie, book.weekly().get(3));
        assertTrue(book.canReroll(MERCREDI, 1), "une relance refusée n'est pas comptée");
        assertTrue(book.reroll(Series.WEEKLY, 9, MERCREDI, tireur.relance()).isEmpty());
    }

    @Test
    void laRelanceExclutLaSerie() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        Set<String> seen = new java.util.HashSet<>();
        book.reroll(Series.DAILY, 1, MERCREDI, excluded -> {
            seen.addAll(excluded);
            return Optional.of(quest("autre"));
        });
        assertEquals(Set.of("q1", "q2", "q3"), seen);
    }

    @Test
    void lePlafondSeRemetLeLundi() {
        QuestBook book = new QuestBook();
        assertEquals(1_000L, book.capRemaining(LUNDI, 1_000L));
        book.recordPaid(LUNDI, 980L);
        assertEquals(20L, book.capRemaining(LUNDI, 1_000L));
        book.recordPaid(LUNDI, 20L);
        assertEquals(0L, book.capRemaining(LUNDI, 1_000L));
        assertEquals(1_000L, book.capRemaining(LUNDI.plusWeeks(1), 1_000L));
        assertEquals(Long.MAX_VALUE, book.capRemaining(LUNDI, 0L));
        book.recordPaid(LUNDI.plusWeeks(1), 50L);
        assertEquals(950L, book.capRemaining(LUNDI.plusWeeks(1), 1_000L));
    }

    @Test
    void uneReferencePerimeeNeTrouveRien() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        Quest q = book.daily().get(0);
        assertTrue(book.find(new QuestRef(QuestRef.Kind.DAILY, 0, q.templateId(), MERCREDI)).isPresent());
        assertTrue(book.find(new QuestRef(QuestRef.Kind.DAILY, 0, q.templateId(), MERCREDI.minusDays(1))).isEmpty());
        assertTrue(book.find(new QuestRef(QuestRef.Kind.DAILY, 0, "autre", MERCREDI)).isEmpty());
        assertTrue(book.find(new QuestRef(QuestRef.Kind.DAILY, 3, q.templateId(), MERCREDI)).isEmpty());
        assertTrue(book.find(new QuestRef(QuestRef.Kind.WEEKLY, -1, q.templateId(), LUNDI)).isEmpty());
    }

    @Test
    void sauvegardeEtRelectureIdentiques() {
        Tireur tireur = new Tireur();
        QuestBook book = neuf(tireur);
        finir(book, Series.DAILY, 0);
        book.set(Series.WEEKLY, 2, book.weekly().get(2).withProgress(4));
        book.reroll(Series.DAILY, 1, MERCREDI, tireur.relance());
        book.recordPaid(LUNDI, 120L);
        book.refresh(MERCREDI.plusDays(1), LUNDI, tireur, 8);
        book.events().put("fete", quest("fete").withProgress(3));
        QuestBook back = QuestBook.load(book.save());
        assertEquals(book.save(), back.save());
        assertEquals(book.daily(), back.daily());
        assertEquals(book.weekly(), back.weekly());
        assertEquals(book.pending(), back.pending());
        assertEquals(book.events(), back.events());
        assertEquals(book.dailyKey(), back.dailyKey());
        assertEquals(book.weeklyKey(), back.weeklyKey());
        assertEquals(book.rerollDay(), back.rerollDay());
        assertEquals(book.rerollsUsed(), back.rerollsUsed());
        assertEquals(book.capWeek(), back.capWeek());
        assertEquals(book.capPaid(), back.capPaid());
        assertEquals(1, back.pending().size());
        assertEquals(4, back.weekly().get(2).progress());
        // Un composé vide se relit en carnet vierge.
        QuestBook vide = QuestBook.load(new CompoundTag());
        assertTrue(vide.daily().isEmpty());
        assertEquals(null, vide.dailyKey());
    }
}
