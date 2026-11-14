package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Line;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class TreasuryLedgerTest {

    private static final UUID MAIRE = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    private static Line ligne(long t, Kind kind, long montant, String motif) {
        return new Line(t, MAIRE, "Maire", kind, montant, motif);
    }

    @Test
    void uneLigneSeRelitALIdentique() {
        TreasuryLedger registre = new TreasuryLedger();
        Line avecAuteur = ligne(7L, Kind.SPEND, 250L, "pont du nord");
        Line sansAuteur = new Line(8L, null, "", Kind.TAX, 3L, "taxe");
        registre.add(avecAuteur, 100);
        registre.add(sansAuteur, 100);
        TreasuryLedger relu = TreasuryLedger.load(registre.save());
        assertEquals(2, relu.size());
        assertEquals(sansAuteur, relu.page(0).get(0));
        assertNull(relu.page(0).get(0).actor());
        assertEquals(avecAuteur, relu.page(0).get(1));
    }

    @Test
    void lesPlusAnciennesPartentAuPlafond() {
        TreasuryLedger registre = new TreasuryLedger();
        for (int i = 1; i <= 105; i++) {
            registre.add(ligne(i, Kind.DEPOSIT, i, "n" + i), 100);
        }
        assertEquals(100, registre.size());
        List<Line> derniere = registre.page(registre.pageCount() - 1);
        assertEquals(6L, derniere.get(derniere.size() - 1).epochMillis());
        assertEquals(105L, registre.page(0).get(0).epochMillis());
    }

    @Test
    void laPageUnEstLaPlusRecente() {
        TreasuryLedger registre = new TreasuryLedger();
        assertEquals(1, registre.pageCount());
        assertTrue(registre.page(0).isEmpty());
        for (int i = 1; i <= 25; i++) {
            registre.add(ligne(i, Kind.INJECTION, i, "m"), 100);
        }
        assertEquals(3, registre.pageCount());
        assertEquals(10, registre.page(0).size());
        assertEquals(25L, registre.page(0).get(0).epochMillis());
        assertEquals(16L, registre.page(0).get(9).epochMillis());
        assertEquals(15L, registre.page(1).get(0).epochMillis());
        assertEquals(5, registre.page(2).size());
        assertTrue(registre.page(3).isEmpty());
        assertTrue(registre.page(-1).isEmpty());
    }

    @Test
    void unMotifTropLongEstCoupe() {
        Line longue = ligne(1L, Kind.SPEND, 1L, "x".repeat(500));
        assertEquals(EconomyLedger.MAX_REASON, longue.reason().length());
        assertEquals(200, longue.reason().length());
        TreasuryLedger registre = new TreasuryLedger();
        registre.add(longue, 100);
        assertEquals(200, TreasuryLedger.load(registre.save()).page(0).get(0).reason().length());
    }

    @Test
    void uneLigneAbimeeEstIgnoree() {
        TreasuryLedger registre = new TreasuryLedger();
        registre.add(ligne(1L, Kind.PLACED, 0L, "pose"), 100);
        ListTag liste = registre.save();
        CompoundTag inconnue = new CompoundTag();
        inconnue.putString("kind", "INCONNU");
        liste.add(inconnue);
        assertEquals(1, TreasuryLedger.load(liste).size());
    }
}
