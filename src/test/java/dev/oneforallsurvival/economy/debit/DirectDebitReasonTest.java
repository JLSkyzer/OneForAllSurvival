package dev.oneforallsurvival.economy.debit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.economy.debit.DebitPlan.Source;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Les motifs qu'un prélèvement écrit au registre de l'économie ({@code /eco ledger}) : lisibles par une équipe
 * francophone, sans la référence interne du créancier (« electricity:2/… ») ni la source en anglais (« (held) »), mais
 * avec le numéro court de la facture (celui de {@code /invoice}) et son motif, de quoi la retrouver.
 */
class DirectDebitReasonTest {

    private static final UUID ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");

    /** Une facture d'électricité telle que les émet le fournisseur : motif « nom du fournisseur et jour ». */
    private static Invoice facture() {
        String ref = "electricity:2/abcdef123456/" + UUID.fromString("00000000-0000-4000-8000-000000000002")
                + "/5/20000/2300/46/10/30/20000/Lagon";
        return new Invoice(ID, UUID.randomUUID(), "Lagon", UUID.randomUUID(), "Pierre", 76L, "Lagon 27/11", 0L,
                Status.DUE, 0L, ref);
    }

    @Test
    void unRefusDitLaFactureSansSaReferenceInterne() {
        String motif = DirectDebit.refusedReason(facture());
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélèvement refusé", motif);
    }

    @Test
    void chaqueSourceSeDitEnFrancais() {
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélevée sur les pièces portées",
                DirectDebit.paidReason(facture(), Source.HELD));
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélevée sur les pièces en attente",
                DirectDebit.paidReason(facture(), Source.PENDING));
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélevée au coffre-fort",
                DirectDebit.paidReason(facture(), Source.SAFE));
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélevée sur plusieurs réserves",
                DirectDebit.paidReason(facture(), Source.MIXED));
        assertEquals("facture publique 1a2b3c4d (Lagon 27/11) de Pierre : payée avec l'objet facture",
                DirectDebit.paidByItemReason(facture()));
    }

    @Test
    void aucunMotifNePorteLaReferenceNiUnMotAnglais() {
        Invoice f = facture();
        for (String motif : new String[] {DirectDebit.refusedReason(f), DirectDebit.paidByItemReason(f),
                DirectDebit.paidReason(f, Source.HELD), DirectDebit.paidReason(f, Source.PENDING),
                DirectDebit.paidReason(f, Source.SAFE), DirectDebit.paidReason(f, Source.MIXED)}) {
            assertFalse(motif.contains("electricity") || motif.contains("abcdef123456"), "référence interne : " + motif);
            for (Source source : Source.values()) {
                assertFalse(motif.contains("(" + source.name().toLowerCase(java.util.Locale.ROOT) + ")"),
                        "source en anglais : " + motif);
            }
            assertTrue(motif.contains(f.shortId()), "numéro court de la facture : " + motif);
        }
    }
}
