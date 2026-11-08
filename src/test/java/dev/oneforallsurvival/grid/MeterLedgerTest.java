package dev.oneforallsurvival.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.grid.MeterLedger.Anomaly;
import dev.oneforallsurvival.grid.MeterLedger.Event;
import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.grid.MeterLedger.Previous;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Le relevé d'un compteur et le registre des abonnés, sans Minecraft. */
class MeterLedgerTest {

    private static final UUID COMPTEUR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void premierReleveLaConsommationEstLIndex() {
        Reading r = MeterLedger.read("Dupont", COMPTEUR, 500, 3, "08/11/2026 14:05", null, List.of());
        assertEquals("Dupont", r.subscriber());
        assertEquals(500, r.index());
        assertEquals(500, r.consumption());
        assertEquals(3, r.day());
        assertEquals("08/11/2026 14:05", r.realDate());
        assertTrue(r.anomalies().isEmpty());
    }

    @Test
    void entreDeuxRelevesLaConsommationEstLaDifference() {
        Previous avant = new Previous(COMPTEUR, 500, 3, "08/11/2026 14:05");
        Reading r = MeterLedger.read("Dupont", COMPTEUR, 800, 5, "09/11/2026 10:00", avant, List.of());
        assertEquals(800, r.index());
        assertEquals(300, r.consumption());
        assertTrue(r.anomalies().isEmpty());
    }

    @Test
    void unCompteurChangeEstSignaleEtSonIndexFaitLaConsommation() {
        Previous avant = new Previous(COMPTEUR, 500, 3, "08/11/2026 14:05");
        Reading r = MeterLedger.read("Dupont", AUTRE, 40, 5, "09/11/2026 10:00", avant, List.of());
        assertEquals(40, r.consumption());
        assertEquals(List.of(Anomaly.METER_CHANGED), r.anomalies());
    }

    @Test
    void unIndexEnBaisseEstSignaleSansConsommationNegative() {
        Previous avant = new Previous(COMPTEUR, 500, 3, "08/11/2026 14:05");
        Reading r = MeterLedger.read("Dupont", COMPTEUR, 200, 5, "09/11/2026 10:00", avant, List.of());
        assertEquals(200, r.index());
        assertEquals(0, r.consumption());
        assertEquals(List.of(Anomaly.INDEX_DECREASED), r.anomalies());
    }

    @Test
    void lesEvenementsDonnentLeursAnomaliesUneFoisChacune() {
        List<Event> events = List.of(new Event(EventKind.SEAL_BROKEN, 2), new Event(EventKind.REMOVED, 2),
                new Event(EventKind.REMOVED, 4));
        Reading r = MeterLedger.read("Dupont", COMPTEUR, 10, 5, "d", null, events);
        assertEquals(List.of(Anomaly.METER_REMOVED, Anomaly.SEAL_BROKEN), r.anomalies());
    }

    @Test
    void lesEvenementsSontReprisAuReleveSuivantPuisVides() {
        MeterLedger ledger = new MeterLedger();
        ledger.take("Dupont", COMPTEUR, 100, 1, "a");
        ledger.record("Dupont", EventKind.REMOVED, 2);
        ledger.record("Dupont", EventKind.SEAL_BROKEN, 3);
        assertEquals(2, ledger.events("Dupont").size());

        Reading r = ledger.take("Dupont", COMPTEUR, 130, 4, "b");
        assertEquals(30, r.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED, Anomaly.SEAL_BROKEN), r.anomalies());
        assertTrue(ledger.events("Dupont").isEmpty());
        assertEquals(new Previous(COMPTEUR, 130, 4, "b"), ledger.previous("Dupont"));

        Reading suivant = ledger.take("Dupont", COMPTEUR, 150, 5, "c");
        assertEquals(20, suivant.consumption());
        assertTrue(suivant.anomalies().isEmpty());
    }

    @Test
    void unEvenementAvantLePremierReleveEstReprisAussi() {
        MeterLedger ledger = new MeterLedger();
        ledger.record("Dupont", EventKind.REMOVED, 1);
        Reading r = ledger.take("Dupont", COMPTEUR, 70, 2, "a");
        assertEquals(70, r.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED), r.anomalies());
    }

    @Test
    void leNomEstNormaliseSansChangerCeQueLeReleveAffiche() {
        assertEquals("jean-luc martin", MeterLedger.normalize("  Jean-Luc MARTIN "));
        MeterLedger ledger = new MeterLedger();
        ledger.take("  DuPont ", COMPTEUR, 100, 1, "a");
        ledger.record("DUPONT", EventKind.SEAL_BROKEN, 2);
        Reading r = ledger.take("Dupont", COMPTEUR, 150, 3, "b");
        assertEquals("Dupont", r.subscriber());
        assertEquals(50, r.consumption());
        assertEquals(List.of(Anomaly.SEAL_BROKEN), r.anomalies());
        assertNull(ledger.previous("martin"));
        assertTrue(ledger.events("martin").isEmpty());
    }

    @Test
    void unNomValideTientEnSeizeLettresChiffresEspacesOuTirets() {
        assertTrue(MeterLedger.isValidName("Dupont"));
        assertTrue(MeterLedger.isValidName("Jean-Luc 2"));
        assertTrue(MeterLedger.isValidName("Hélène"));
        assertTrue(MeterLedger.isValidName("abcdefghijklmnop"));
        assertTrue(MeterLedger.isValidName("  Dupont  "), "les espaces autour sont retirés");
        assertFalse(MeterLedger.isValidName("abcdefghijklmnopq"), "17 caractères");
        assertFalse(MeterLedger.isValidName(""));
        assertFalse(MeterLedger.isValidName("   "));
        assertFalse(MeterLedger.isValidName("Bob!"));
        assertFalse(MeterLedger.isValidName("a_b"));
        assertFalse(MeterLedger.isValidName("§cRouge"));
        assertFalse(MeterLedger.isValidName(null));
    }

    @Test
    void leJourCommenceAUn() {
        assertEquals(1, MeterLedger.gameDay(0));
        assertEquals(1, MeterLedger.gameDay(23_999));
        assertEquals(2, MeterLedger.gameDay(24_000));
        assertEquals(11, MeterLedger.gameDay(240_500));
    }

    @Test
    void laDateReelleSuitLeFuseauDonne() {
        Instant instant = Instant.parse("2026-11-08T13:05:42Z");
        assertEquals("08/11/2026 13:05", MeterLedger.formatRealDate(instant, ZoneId.of("UTC")));
        assertEquals("08/11/2026 14:05", MeterLedger.formatRealDate(instant, ZoneId.of("Europe/Paris")));
    }

    @Test
    void leRegistreSeRelitDepuisSaSauvegarde() {
        SubscriberSavedData data = new SubscriberSavedData();
        data.take("Dupont", COMPTEUR, 100, 1, "a");
        data.record("Dupont", EventKind.SEAL_BROKEN, 2);
        data.record("Leroy", EventKind.REMOVED, 3);

        SubscriberSavedData relu = SubscriberSavedData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(new Previous(COMPTEUR, 100, 1, "a"), relu.previous("dupont"));
        assertEquals(List.of(new Event(EventKind.SEAL_BROKEN, 2)), relu.events("Dupont"));
        assertNull(relu.previous("Leroy"));
        assertEquals(List.of(new Event(EventKind.REMOVED, 3)), relu.events("leroy"));
    }
}
