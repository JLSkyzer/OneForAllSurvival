package dev.oneforallsurvival.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final UUID TROISIEME = UUID.fromString("00000000-0000-0000-0000-000000000003");

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
    void unCompteurNeufALaPlaceDUnCompteurDeposeEstSignale() {
        List<Event> events = List.of(new Event(EventKind.REMOVED, 4, COMPTEUR, false));
        Reading r = MeterLedger.read("Dupont", AUTRE, 40, 5, "09/11/2026 10:00", null, events);
        assertEquals(40, r.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED, Anomaly.METER_CHANGED), r.anomalies());
    }

    @Test
    void deuxCompteursAuMemeNomSontFacturesChacunSansAnomalie() {
        MeterLedger ledger = new MeterLedger();
        ledger.inscribe("", "Dupont", COMPTEUR, 0, 1, "a");
        ledger.inscribe("", "Dupont", AUTRE, 0, 1, "a");
        assertEquals(100, ledger.take("Dupont", COMPTEUR, 100, 2, "b").consumption());
        assertEquals(40, ledger.take("Dupont", AUTRE, 40, 2, "b").consumption());
        Reading r1 = ledger.take("Dupont", COMPTEUR, 130, 3, "c");
        Reading r2 = ledger.take("Dupont", AUTRE, 90, 3, "c");
        Reading r3 = ledger.take("Dupont", COMPTEUR, 135, 4, "d");
        assertEquals(30, r1.consumption());
        assertEquals(50, r2.consumption());
        assertEquals(5, r3.consumption());
        for (Reading r : List.of(r1, r2, r3)) {
            assertTrue(r.anomalies().isEmpty(), "aucune anomalie attendue : " + r.anomalies());
        }
    }

    @Test
    void deuxCompteursAuMemeNomUnIndexEnBaisseNeVautQuePourLeSien() {
        MeterLedger ledger = new MeterLedger();
        ledger.take("Dupont", COMPTEUR, 100, 1, "a");
        ledger.take("Dupont", AUTRE, 500, 1, "a");
        Reading r = ledger.take("Dupont", COMPTEUR, 120, 2, "b");
        assertEquals(20, r.consumption());
        assertTrue(r.anomalies().isEmpty(), "anomalies " + r.anomalies());
        Reading baisse = ledger.take("Dupont", AUTRE, 450, 2, "b");
        assertEquals(0, baisse.consumption());
        assertEquals(List.of(Anomaly.INDEX_DECREASED), baisse.anomalies());
    }

    @Test
    void unCompteurQuiAComptéAvantLInscriptionNeFactureQueDepuisLInscription() {
        MeterLedger ledger = new MeterLedger();
        ledger.inscribe("", "Dupont", COMPTEUR, 700, 1, "a");
        assertEquals(new Previous(COMPTEUR, 700, 1, "a", true), ledger.previous("Dupont", COMPTEUR));
        Reading r = ledger.take("Dupont", COMPTEUR, 760, 2, "b");
        assertEquals(760, r.index());
        assertEquals(60, r.consumption());
        assertTrue(r.anomalies().isEmpty(), "anomalies " + r.anomalies());
    }

    @Test
    void renommerDonneUnReleveDeClotureALAncienEtFactureLeNouveauDepuisLeChangement() {
        MeterLedger ledger = new MeterLedger();
        ledger.inscribe("", "Dupont", COMPTEUR, 0, 1, "a");
        ledger.take("Dupont", COMPTEUR, 100, 2, "b");
        ledger.inscribe("", "Dupont", AUTRE, 0, 2, "b");
        // 50 FE passés sous Dupont, non relevés, puis le compteur passe à Leroy à l'index 150.
        Reading cloture = ledger.inscribe("Dupont", "Leroy", COMPTEUR, 150, 3, "c");

        assertNotNull(cloture, "un changement d'abonné donne un relevé de clôture");
        assertTrue(cloture.closing());
        assertEquals("Dupont", cloture.subscriber());
        assertEquals(150, cloture.index());
        assertEquals(50, cloture.consumption());
        assertEquals(3, cloture.day());
        assertEquals("c", cloture.realDate());
        assertTrue(cloture.anomalies().isEmpty(), "anomalies " + cloture.anomalies());
        assertNull(ledger.previous("Dupont", COMPTEUR), "le compteur a quitté le compte de Dupont");
        assertTrue(ledger.events("Dupont").isEmpty(), "rien n'attend au compte de Dupont");

        Reading leroy = ledger.take("Leroy", COMPTEUR, 180, 4, "d");
        assertEquals(30, leroy.consumption(), "Leroy ne paie que depuis le changement");
        assertTrue(leroy.anomalies().isEmpty(), "anomalies " + leroy.anomalies());
        assertFalse(leroy.closing());

        // L'autre compteur de Dupont n'hérite de rien.
        Reading dupont = ledger.take("Dupont", AUTRE, 10, 4, "d");
        assertEquals(10, dupont.consumption());
        assertTrue(dupont.anomalies().isEmpty(), "anomalies " + dupont.anomalies());

        // Chaque FE compté par le compteur est facturé une fois, à un seul abonné : 100 + 50 + 30 = 180.
        assertEquals(180, 100 + cloture.consumption() + leroy.consumption());
    }

    @Test
    void laPremiereInscriptionEtLeMemeNomNeDonnentPasDeCloture() {
        MeterLedger ledger = new MeterLedger();
        assertNull(ledger.inscribe("", "Dupont", COMPTEUR, 0, 1, "a"));
        ledger.take("Dupont", COMPTEUR, 100, 2, "b");
        assertNull(ledger.inscribe("Dupont", "  DUPONT ", COMPTEUR, 150, 3, "c"));
        assertEquals(new Previous(COMPTEUR, 100, 2, "b"), ledger.previous("dupont", COMPTEUR));
        assertEquals(50, ledger.take("Dupont", COMPTEUR, 150, 4, "d").consumption());
    }

    @Test
    void renommerPuisRevenirNeFactureRienDeuxFois() {
        MeterLedger ledger = new MeterLedger();
        ledger.inscribe("", "Dupont", COMPTEUR, 0, 1, "a");
        Reading c1 = ledger.inscribe("Dupont", "Leroy", COMPTEUR, 40, 2, "b");
        Reading c2 = ledger.inscribe("Leroy", "Dupont", COMPTEUR, 70, 3, "c");
        Reading dupont = ledger.take("Dupont", COMPTEUR, 100, 4, "d");
        assertEquals(40, c1.consumption());
        assertEquals("Leroy", c2.subscriber());
        assertEquals(30, c2.consumption());
        assertEquals(30, dupont.consumption(), "Dupont repart de 70");
        assertTrue(dupont.anomalies().isEmpty(), "anomalies " + dupont.anomalies());
        assertEquals(100, c1.consumption() + c2.consumption() + dupont.consumption());
        assertTrue(ledger.events("Leroy").isEmpty());
        assertNull(ledger.previous("Leroy", COMPTEUR));
    }

    @Test
    void laClotureSignaleLaDeposeDeCeCompteur() {
        MeterLedger ledger = new MeterLedger();
        ledger.inscribe("", "Dupont", COMPTEUR, 0, 1, "a");
        ledger.record("Dupont", EventKind.REMOVED, 2, COMPTEUR);
        Reading cloture = ledger.inscribe("Dupont", "Leroy", COMPTEUR, 60, 3, "c");
        assertEquals(60, cloture.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED), cloture.anomalies());
        assertTrue(ledger.events("Dupont").isEmpty());
    }

    @Test
    void unCompteurDeposeEstSignaleAuReleveSuivantDeNimporteQuelCompteurUneFois() {
        MeterLedger ledger = new MeterLedger();
        ledger.take("Dupont", COMPTEUR, 100, 1, "a");
        ledger.take("Dupont", AUTRE, 10, 1, "a");
        ledger.record("Dupont", EventKind.REMOVED, 2, COMPTEUR);
        // Le compteur restant le signale à son relevé suivant…
        Reading autre = ledger.take("Dupont", AUTRE, 20, 2, "b");
        assertEquals(10, autre.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED), autre.anomalies());
        // … une seule fois.
        Reading ensuite = ledger.take("Dupont", AUTRE, 25, 3, "c");
        assertTrue(ensuite.anomalies().isEmpty(), "anomalies " + ensuite.anomalies());
        // Un compteur neuf posé ensuite dit qu'il a remplacé le compteur déposé, sans redire la dépose.
        Reading neuf = ledger.take("Dupont", TROISIEME, 5, 4, "d");
        assertEquals(List.of(Anomaly.METER_CHANGED), neuf.anomalies());
        assertTrue(ledger.events("Dupont").isEmpty());
    }

    @Test
    void unCompteurDeposePuisReposeNeSignalePasDeChangement() {
        MeterLedger ledger = new MeterLedger();
        ledger.take("Dupont", COMPTEUR, 100, 1, "a");
        ledger.take("Dupont", AUTRE, 10, 1, "a");
        ledger.record("Dupont", EventKind.REMOVED, 2, COMPTEUR);
        // L'autre compteur signale la dépose une fois…
        Reading autre = ledger.take("Dupont", AUTRE, 20, 2, "b");
        assertEquals(List.of(Anomaly.METER_REMOVED), autre.anomalies());
        // … et le compteur reposé reprend son index, sans « compteur différent » ni dépose redite.
        Reading r = ledger.take("Dupont", COMPTEUR, 130, 3, "c");
        assertEquals(30, r.consumption());
        assertTrue(r.anomalies().isEmpty(), "anomalies " + r.anomalies());
        assertTrue(ledger.events("Dupont").isEmpty());
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
        assertEquals(new Previous(COMPTEUR, 130, 4, "b"), ledger.previous("Dupont", COMPTEUR));

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
        assertNull(ledger.previous("martin", COMPTEUR));
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
        data.inscribe("", "Dupont", AUTRE, 25, 2, "b");
        data.inscribe("", "Moreau", COMPTEUR, 7, 2, "b");
        data.inscribe("Moreau", "Leroy", COMPTEUR, 9, 3, "c");
        data.take("Martin", AUTRE, 5, 3, "c");
        data.take("Martin", TROISIEME, 5, 3, "c");
        data.record("Martin", EventKind.REMOVED, 4, AUTRE);
        data.take("Martin", TROISIEME, 6, 4, "d"); // la dépose est signalée, gardée pour un remplacement

        SubscriberSavedData relu = SubscriberSavedData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(new Previous(COMPTEUR, 100, 1, "a"), relu.previous("dupont", COMPTEUR));
        assertEquals(new Previous(AUTRE, 25, 2, "b", true), relu.previous("dupont", AUTRE));
        assertEquals(List.of(new Event(EventKind.SEAL_BROKEN, 2)), relu.events("Dupont"));
        assertEquals(new Previous(COMPTEUR, 9, 3, "c", true), relu.previous("Leroy", COMPTEUR));
        assertEquals(List.of(new Event(EventKind.REMOVED, 3)), relu.events("leroy"));
        assertNull(relu.previous("Moreau", COMPTEUR));
        assertTrue(relu.events("moreau").isEmpty());
        assertEquals(List.of(new Event(EventKind.REMOVED, 4, AUTRE, true)), relu.events("martin"));
    }

    @Test
    void unEvenementDeLaVersionPrecedenteDuLotSeRelitSansGener() {
        // 6b643cb notait « renamed » avec un reste ; ce format de développement est ignoré à la relecture.
        net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
        e.putString("kind", "renamed");
        e.putLong("day", 3);
        e.putUUID("serial", COMPTEUR);
        e.putLong("carried", 50);
        net.minecraft.nbt.ListTag events = new net.minecraft.nbt.ListTag();
        events.add(e);
        net.minecraft.nbt.CompoundTag account = new net.minecraft.nbt.CompoundTag();
        account.putString("name", "dupont");
        account.put("events", events);
        net.minecraft.nbt.ListTag accounts = new net.minecraft.nbt.ListTag();
        accounts.add(account);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("accounts", accounts);

        SubscriberSavedData relu = SubscriberSavedData.load(tag);
        assertTrue(relu.events("Dupont").isEmpty());
        Reading r = relu.take("Dupont", AUTRE, 10, 4, "d");
        assertEquals(10, r.consumption());
        assertTrue(r.anomalies().isEmpty());
    }

    @Test
    void uneAncienneSauvegardeAUnSeulReleveSeRelit() {
        net.minecraft.nbt.CompoundTag p = new net.minecraft.nbt.CompoundTag();
        p.putUUID("serial", COMPTEUR);
        p.putLong("index", 100);
        p.putLong("day", 1);
        p.putString("real_date", "a");
        net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
        e.putString("kind", "removed");
        e.putLong("day", 2);
        net.minecraft.nbt.ListTag events = new net.minecraft.nbt.ListTag();
        events.add(e);
        net.minecraft.nbt.CompoundTag account = new net.minecraft.nbt.CompoundTag();
        account.putString("name", "dupont");
        account.put("previous", p);
        account.put("events", events);
        net.minecraft.nbt.ListTag accounts = new net.minecraft.nbt.ListTag();
        accounts.add(account);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("accounts", accounts);

        SubscriberSavedData relu = SubscriberSavedData.load(tag);
        assertEquals(new Previous(COMPTEUR, 100, 1, "a"), relu.previous("Dupont", COMPTEUR));
        assertEquals(List.of(new Event(EventKind.REMOVED, 2)), relu.events("Dupont"));
        Reading r = relu.take("Dupont", COMPTEUR, 130, 3, "b");
        assertEquals(30, r.consumption());
        assertEquals(List.of(Anomaly.METER_REMOVED), r.anomalies());
    }
}
