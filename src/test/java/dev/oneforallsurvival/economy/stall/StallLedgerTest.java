package dev.oneforallsurvival.economy.stall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallLedger.Line;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class StallLedgerTest {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-00000000beef");

    private static Line line(long t, Kind kind, String item, int count, long amount) {
        return new Line(t, ACTOR, "Alice", kind, item, count, amount);
    }

    @Test
    void lesPlusAnciennesLignesTombentAuPlafond() {
        StallLedger ledger = new StallLedger();
        for (int i = 0; i < 5; i++) {
            ledger.add(line(i, Kind.CASH_IN, "", 0, 100 + i), 3);
        }
        assertEquals(3, ledger.size());
        List<Line> page = ledger.page(0);
        assertEquals(List.of(4L, 3L, 2L), page.stream().map(Line::epochMillis).toList());
    }

    @Test
    void laPageZeroEstLaPlusRecenteEtLesSuivantesContinuent() {
        StallLedger ledger = new StallLedger();
        int total = StallLedger.PAGE_SIZE + 5;
        for (int i = 0; i < total; i++) {
            ledger.add(line(i, Kind.GOODS_IN, "minecraft:dirt", 1, 0), 1000);
        }
        assertEquals(2, ledger.pages());
        assertEquals(StallLedger.PAGE_SIZE, ledger.page(0).size());
        assertEquals(total - 1L, ledger.page(0).get(0).epochMillis());
        assertEquals(5, ledger.page(1).size());
        assertEquals(0L, ledger.page(1).get(4).epochMillis());
        assertTrue(ledger.page(2).isEmpty());
        assertTrue(ledger.page(-1).isEmpty());
        assertTrue(ledger.page(Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void unRegistreVideAUnePage() {
        assertEquals(1, new StallLedger().pages());
        assertTrue(new StallLedger().page(0).isEmpty());
    }

    @Test
    void leRegistreSurvitALaSauvegardeAvecSesQuantitesSignees() {
        StallLedger ledger = new StallLedger();
        ledger.add(line(1, Kind.STAFF, "minecraft:dirt", -12, 0), 100);
        ledger.add(line(2, Kind.STAFF, "", 0, -340), 100);
        ledger.add(line(3, Kind.SALE, "minecraft:bread", 3, 45), 100);
        CompoundTag holder = new CompoundTag();
        holder.put("ledger", ledger.save());
        StallLedger relu = new StallLedger();
        relu.load(holder.getList("ledger", 10));
        assertEquals(ledger.page(0), relu.page(0));
        assertEquals(-12, relu.page(0).get(2).count());
        assertEquals(-340L, relu.page(0).get(1).amount());
    }

    @Test
    void uneLigneAbimeeEstIgnoreeSansRienCasser() {
        StallLedger ledger = new StallLedger();
        ledger.add(line(1, Kind.SALE, "minecraft:bread", 1, 5), 100);
        ListTag list = ledger.save();
        list.add(new CompoundTag());
        CompoundTag mauvaisGenre = line(2, Kind.SALE, "x", 1, 1).save();
        mauvaisGenre.putString("kind", "N_IMPORTE_QUOI");
        list.add(mauvaisGenre);
        StallLedger relu = new StallLedger();
        relu.load(list);
        assertEquals(1, relu.size());
    }

    @Test
    void lesTextesTropLongsSontCoupes() {
        Line line = new Line(1, ACTOR, "x".repeat(500), Kind.SALE, "y".repeat(500), 1, 1);
        assertTrue(line.actorName().length() <= 32);
        assertTrue(line.itemId().length() <= 128);
    }
}
