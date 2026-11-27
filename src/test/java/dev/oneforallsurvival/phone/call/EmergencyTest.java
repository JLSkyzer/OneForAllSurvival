package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.Emergency.Cooldown;
import dev.oneforallsurvival.phone.call.Emergency.Dispatch;
import dev.oneforallsurvival.phone.call.Emergency.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Les règles pures des urgences : services, délai, premier répondant, journal borné. */
class EmergencyTest {

    private static final UUID APPELANT = new UUID(0, 1);
    private static final UUID S1 = new UUID(0, 2);
    private static final UUID S2 = new UUID(0, 3);

    @Test
    void lesDeuxNumerosEtRienDAutre() {
        assertEquals(Optional.of(Service.POLICE), Service.of("17"));
        assertEquals(Optional.of(Service.MEDICAL), Service.of(" 1 5 "));
        assertEquals("policier", Service.POLICE.role());
        assertEquals("medecin", Service.MEDICAL.role());
        for (String autre : new String[] {"18", "112", "", "017", "１７", "١٧", "0612345678"}) {
            assertTrue(Service.of(autre).isEmpty(), autre);
        }
        assertTrue(Service.of(null).isEmpty());
    }

    @Test
    void leDelaiRefuseJusquASaBorne() {
        Cooldown c = new Cooldown();
        assertTrue(c.allow(APPELANT, 100, 1200));
        assertFalse(c.allow(APPELANT, 100 + 1199, 1200), "une tic avant la borne : refusé");
        assertTrue(c.allow(S1, 150, 1200), "un autre joueur n'est pas freiné");
        assertTrue(c.allow(APPELANT, 100 + 1200, 1200), "à la borne : accepté");
        assertFalse(c.allow(APPELANT, 100 + 1300, 1200), "le délai repart du dernier appel accepté");
    }

    @Test
    void unRefusNeRallongePasLeDelai() {
        Cooldown c = new Cooldown();
        c.allow(APPELANT, 0, 1200);
        assertFalse(c.allow(APPELANT, 600, 1200));
        assertTrue(c.allow(APPELANT, 1200, 1200));
    }

    @Test
    void uneHorlogeQuiReculeRemetLeDelaiAZero() {
        Cooldown c = new Cooldown();
        c.allow(APPELANT, 5000, 1200);
        assertTrue(c.allow(APPELANT, 10, 1200), "serveur relancé : tics repartis de zéro");
        c.purge(10 + 1200, 1200);
        assertEquals(0, c.tracked(), "purge des entrées sorties du délai");
    }

    @Test
    void lePremierQuiRepondPrendLAppel() {
        Dispatch d = new Dispatch(7, APPELANT, Service.POLICE, 40, List.of(S1, S2, APPELANT));
        assertEquals(2, d.ringing().size(), "l'appelant ne se sonne pas lui-même");
        assertTrue(d.ringsFor(S1) && d.ringsFor(S2));
        assertTrue(d.take(S2), "le premier prend");
        assertFalse(d.take(S1), "le second ne prend pas aussi");
        assertFalse(d.take(S2), "ne reprend pas deux fois");
        assertEquals(Optional.of(S2), d.taker());
        assertFalse(d.ringsFor(S1), "plus rien ne sonne une fois pris");
    }

    @Test
    void unRefusOuUnInconnuNePrendPas() {
        Dispatch d = new Dispatch(7, APPELANT, Service.MEDICAL, 0, List.of(S1));
        assertFalse(d.take(S2), "ne sonnait pas chez lui");
        assertTrue(d.drop(S1));
        assertFalse(d.take(S1), "a refusé : ne prend plus");
        assertTrue(d.taker().isEmpty());
        assertFalse(d.expired(599, 600));
        assertTrue(d.expired(600, 600), "sonnerie expirée à sa borne");
    }

    @Test
    void leJournalEstBorneEtSeLitDuPlusRecent() {
        EmergencyLog log = new EmergencyLog();
        long premier = log.add(1, APPELANT, "Bob", "17", "minecraft:overworld", 1, 2, 3);
        for (int i = 2; i <= EmergencyLog.MAX + 5; i++) {
            log.add(i, APPELANT, "Bob", i % 2 == 0 ? "17" : "15", "minecraft:overworld", i, 64, -i);
        }
        assertEquals(EmergencyLog.MAX, log.size(), "borné à " + EmergencyLog.MAX);
        assertFalse(log.answered(premier, "Alice"), "le plus ancien est oublié");
        List<EmergencyLog.Entry> vingt = log.latest(EmergencyLog.SHOWN);
        assertEquals(EmergencyLog.SHOWN, vingt.size());
        assertEquals(EmergencyLog.MAX + 5, vingt.get(0).atMillis(), "le plus récent d'abord");
        long dernier = vingt.get(0).seq();
        assertTrue(log.answered(dernier, "Alice"));
        assertEquals("Alice", log.latest(1).get(0).answeredBy());
        assertEquals("", log.latest(2).get(1).answeredBy());
    }

    @Test
    void leJournalRelitAuPlusSaBorneEtNeReprendPasUnNumero() {
        EmergencyLog source = new EmergencyLog();
        for (int i = 0; i < EmergencyLog.MAX; i++) {
            source.add(i, APPELANT, "B".repeat(100), "17", "d", 0, 0, 0);
        }
        List<EmergencyLog.Entry> trop = new java.util.ArrayList<>(source.all());
        trop.addAll(source.all().subList(0, 10));
        EmergencyLog relu = new EmergencyLog();
        relu.restore(trop, 0);
        assertEquals(EmergencyLog.MAX, relu.size());
        assertEquals(64, relu.latest(1).get(0).playerName().length(), "nom borné");
        long suivant = relu.add(9, APPELANT, "Bob", "15", "d", 0, 0, 0);
        assertTrue(relu.all().stream().filter(e -> e.seq() == suivant).count() == 1, "numéro neuf");
    }
}
