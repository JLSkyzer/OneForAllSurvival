package dev.oneforallsurvival.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EconomyLedgerTransferTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    @Test
    void unTransfertNeChangePasLaCirculation() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.created(MoneySource.STAFF, 500, null, "depart", 100, 1L);
        ledger.transferred(MoneyTransfer.TRADE, 200, AUTHOR, "echange", 100, 2L);
        assertEquals(500, ledger.inCirculation());
        assertEquals(200, ledger.totalTransferred(MoneyTransfer.TRADE));
        assertEquals(0, ledger.totalTransferred(MoneyTransfer.INVOICE));
    }

    @Test
    void unTransfertEstUneLigneDuRegistre() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.transferred(MoneyTransfer.TRADE, 200, AUTHOR, "echange avec Bob", 100, 7L);
        assertEquals(1, ledger.entryCount());
        EconomyLedger.Entry line = ledger.entries(0).get(0);
        assertEquals("transfer.trade", line.kind());
        assertEquals(AUTHOR, line.author());
        assertEquals("echange avec Bob", line.reason());
        assertEquals(200, line.amount());
        assertEquals(7L, line.epochMillis());
        assertEquals("economy.oneforallsurvival.kind.transfer.trade", MoneyTransfer.TRADE.langKey());
        assertEquals("transfer.stall_sale", MoneyTransfer.STALL_SALE.kind());
    }

    @Test
    void lesTotauxDeTransfertSurviventALaSauvegarde() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.transferred(MoneyTransfer.TRADE, 200, AUTHOR, "a", 100, 1L);
        ledger.transferred(MoneyTransfer.SALES_TAX, 15, null, "b", 100, 2L);
        ledger.transferred(MoneyTransfer.TRADE, 5, null, "c", 100, 3L);
        EconomyLedger back = EconomyLedger.load(ledger.save(new CompoundTag()));
        assertEquals(205, back.totalTransferred(MoneyTransfer.TRADE));
        assertEquals(15, back.totalTransferred(MoneyTransfer.SALES_TAX));
        assertEquals(0, back.totalTransferred(MoneyTransfer.STALL_SALE));
        assertEquals(3, back.entryCount());
        assertEquals(0, back.inCirculation());
        assertTrue(back.entries(0).get(0).kind().startsWith("transfer."));
    }

    @Test
    void unTransfertNulOuNegatifNEstPasNote() {
        EconomyLedger ledger = new EconomyLedger();
        ledger.transferred(MoneyTransfer.TRADE, 0, null, "rien", 100, 1L);
        ledger.transferred(MoneyTransfer.INVOICE, -5, null, "rien", 100, 1L);
        assertEquals(0, ledger.entryCount());
        assertEquals(0, ledger.totalTransferred(MoneyTransfer.TRADE));
        assertEquals(0, ledger.totalTransferred(MoneyTransfer.INVOICE));
    }
}
