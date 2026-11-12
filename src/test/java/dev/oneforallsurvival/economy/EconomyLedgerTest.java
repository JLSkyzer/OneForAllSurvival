package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EconomyLedgerTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    @Test
    void lesTotauxSontParSourceEtParCause() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.created(MoneySource.STARTING, 500, AUTHOR, "depart", 100, 1L);
        ledger.created(MoneySource.STAFF, 30, null, "geste", 100, 2L);
        ledger.destroyed(MoneySink.FIRE, 200, null, "lave", 100, 3L);
        assertEquals(500, ledger.totalCreated(MoneySource.STARTING));
        assertEquals(30, ledger.totalCreated(MoneySource.STAFF));
        assertEquals(0, ledger.totalCreated(MoneySource.QUEST));
        assertEquals(200, ledger.totalDestroyed(MoneySink.FIRE));
        assertEquals(0, ledger.totalDestroyed(MoneySink.VOID));
        assertEquals(330, ledger.inCirculation());
    }

    @Test
    void unMontantNulOuNegatifNEstPasNote() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.created(MoneySource.STAFF, 0, null, "rien", 100, 1L);
        ledger.destroyed(MoneySink.STAFF, -5, null, "rien", 100, 1L);
        assertEquals(0, ledger.entryCount());
        assertEquals(0, ledger.inCirculation());
    }

    @Test
    void lePlafondEffaceLesLignesPasLesTotaux() {
        EconomyLedger ledger = new EconomyLedger();
        for (int i = 1; i <= 25; i++) {
            ledger.created(MoneySource.STAFF, i, null, "n=" + i, 10, i);
        }
        assertEquals(10, ledger.entryCount());
        assertEquals(325, ledger.totalCreated(MoneySource.STAFF));
        // La plus récente d'abord ; les quinze plus anciennes sont parties.
        assertEquals("n=25", ledger.entries(0).get(0).reason());
        assertEquals("n=16", ledger.entries(0).get(9).reason());
        assertTrue(ledger.entries(1).isEmpty());
    }

    @Test
    void lesPagesSontDixLignesDuPlusRecentAuPlusAncien() {
        EconomyLedger ledger = new EconomyLedger();
        for (int i = 1; i <= 23; i++) {
            ledger.created(MoneySource.STAFF, 1, null, "n=" + i, 100, i);
        }
        assertEquals(3, ledger.pageCount());
        assertEquals(10, ledger.entries(0).size());
        assertEquals(10, ledger.entries(1).size());
        assertEquals(3, ledger.entries(2).size());
        assertEquals("n=23", ledger.entries(0).get(0).reason());
        assertEquals("n=13", ledger.entries(1).get(0).reason());
        assertEquals("n=3", ledger.entries(2).get(0).reason());
        assertTrue(ledger.entries(-1).isEmpty());
        assertEquals(1, new EconomyLedger().pageCount());
    }

    @Test
    void leMotifTropLongEstCoupe() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.created(MoneySource.STAFF, 1, null, "x".repeat(500), 100, 1L);
        assertEquals(EconomyLedger.MAX_REASON, ledger.entries(0).get(0).reason().length());
    }

    @Test
    void leJoueurServiNEstSertQuUneFois() {
        EconomyLedger ledger = new EconomyLedger();
        assertFalse(ledger.hasReceivedStarting(AUTHOR));
        assertTrue(ledger.markStartingGiven(AUTHOR));
        assertFalse(ledger.markStartingGiven(AUTHOR));
        assertTrue(ledger.hasReceivedStarting(AUTHOR));
    }

    @Test
    void laSauvegardeRedonneTout() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.created(MoneySource.STARTING, 500, AUTHOR, "depart", 100, 1_700_000_000_000L);
        ledger.destroyed(MoneySink.DESPAWN, 7, null, "sol", 100, 1_700_000_000_001L);
        ledger.markStartingGiven(AUTHOR);
        CompoundTag tag = ledger.save(new CompoundTag());
        EconomyLedger back = EconomyLedger.load(tag);
        for (MoneySource source : MoneySource.values()) {
            assertEquals(ledger.totalCreated(source), back.totalCreated(source));
        }
        for (MoneySink sink : MoneySink.values()) {
            assertEquals(ledger.totalDestroyed(sink), back.totalDestroyed(sink));
        }
        assertEquals(ledger.entries(0), back.entries(0));
        assertEquals(AUTHOR, back.entries(0).get(1).author());
        assertNull(back.entries(0).get(0).author());
        assertTrue(back.hasReceivedStarting(AUTHOR));
    }
}
