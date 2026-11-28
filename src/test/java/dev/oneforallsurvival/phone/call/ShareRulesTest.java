package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * « Partager mon numéro » (retours d'essai du 27/11) : qui est dans la liste (8 blocs, en vue, ni spectateur ni
 * invisible, une carte), et la liste sur le réseau (bornée, aller-retour exact).
 */
class ShareRulesTest {

    @Test
    void huitBlocsCompris() {
        assertTrue(ShareRules.listed(64.0, true, false, false, true), "8 blocs pile");
        assertFalse(ShareRules.listed(64.0001, true, false, false, true), "au-delà de 8 blocs");
        assertTrue(ShareRules.listed(0.0, true, false, false, true));
    }

    @Test
    void chaqueConditionCompte() {
        assertFalse(ShareRules.listed(4.0, false, false, false, true), "un mur entre");
        assertFalse(ShareRules.listed(4.0, true, true, false, true), "spectateur");
        assertFalse(ShareRules.listed(4.0, true, false, true, true), "invisible");
        assertFalse(ShareRules.listed(4.0, true, false, false, false), "sans téléphone à carte");
    }

    @Test
    void sonTelephoneEstASonNomOuSansProprietaire() {
        UUID joueur = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID autre = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        assertTrue(ShareRules.own(joueur, joueur), "enregistré à son nom");
        assertTrue(ShareRules.own(null, joueur), "sans propriétaire");
        assertFalse(ShareRules.own(autre, joueur), "celui d'un autre");
    }

    private static PhoneSharePacket allerRetour(PhoneSharePacket p) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            p.encode(buf);
            PhoneSharePacket out = PhoneSharePacket.decode(buf);
            assertEquals(0, buf.readableBytes());
            return out;
        } finally {
            buf.release();
        }
    }

    @Test
    void laListeFaitLAllerRetour() {
        List<PhoneSharePacket.Row> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(new PhoneSharePacket.Row(i * 1000, "Joueur_" + i));
        }
        PhoneSharePacket p = new PhoneSharePacket(rows, Component.translatable("message.oneforallsurvival.call.share.sent",
                "Joueur_0"));
        assertEquals(ShareRules.MAX_ROWS, p.rows().size(), "bornée à la construction");
        PhoneSharePacket back = allerRetour(p);
        assertEquals(p.rows(), back.rows());
        assertEquals(p.message(), back.message());
        PhoneSharePacket vide = allerRetour(new PhoneSharePacket(null, null));
        assertTrue(vide.rows().isEmpty());
        assertEquals(Component.empty(), vide.message());
        String long32 = "x".repeat(40);
        assertEquals(PhoneSharePacket.MAX_NAME, new PhoneSharePacket.Row(1, long32).name().length());
    }

    @Test
    void uneListeTropLongueNeSeLitPas() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(ShareRules.MAX_ROWS + 1);
            assertThrows(DecoderException.class, () -> PhoneSharePacket.decode(buf));
        } finally {
            buf.release();
        }
    }
}
