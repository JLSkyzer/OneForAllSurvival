package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.net.PhoneSims;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * L'aller-retour des deux paquets du téléphone : l'état (serveur vers client) ressort égal pour chaque valeur de chaque
 * énumération, vue réseau pleine comprise (lot T2) ; un geste (client vers serveur) dont un texte dépasse
 * {@link PhoneActionPacket#MAX_WIRE_TEXT} ne se lit pas, ni une vue réseau au-delà de ses bornes.
 */
class PhoneStatePacketTest {

    private static PhoneStatePacket allerRetour(PhoneStatePacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            PhoneStatePacket out = PhoneStatePacket.decode(buf);
            assertEquals(0, buf.readableBytes(), "octets laissés après la lecture");
            return out;
        } finally {
            buf.release();
        }
    }

    private static PhoneActionPacket allerRetour(PhoneActionPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            PhoneActionPacket out = PhoneActionPacket.decode(buf);
            assertEquals(0, buf.readableBytes(), "octets laissés après la lecture");
            return out;
        } finally {
            buf.release();
        }
    }

    @Test
    void allerRetour() {
        int n = 0;
        for (PhoneStatePacket.Kind kind : PhoneStatePacket.Kind.values()) {
            for (PhoneStatePacket.Lock lock : PhoneStatePacket.Lock.values()) {
                for (PhoneStatePacket.Result last : PhoneStatePacket.Result.values()) {
                    for (PhoneStatePacket.CloseReason reason : PhoneStatePacket.CloseReason.values()) {
                        n++;
                        PhoneStatePacket packet = new PhoneStatePacket(n, kind, lock, n % 101,
                                1_789_000_000_000L + n, (n % 2 == 0 ? 1 : -1) * 3600 * (n % 14), "nuit",
                                n % 2 == 0, n % 3 == 0, "Joueur_" + (n % 100), n % 6, n % 61, last, reason,
                                n % 2 == 0 ? pleine() : PhoneNetView.NONE);
                        assertEquals(packet, allerRetour(packet));
                    }
                }
            }
        }
        // Les bornes : session et batterie à zéro, heure négative, textes vides.
        PhoneStatePacket bornes = new PhoneStatePacket(0, PhoneStatePacket.Kind.CLOSE, PhoneStatePacket.Lock.SETUP, 0,
                -1L, -43_200, "", false, false, "", 0, 0, PhoneStatePacket.Result.NONE,
                PhoneStatePacket.CloseReason.NONE, PhoneNetView.NONE);
        assertEquals(bornes, allerRetour(bornes));
        // Une vue réseau pleine aux bornes : 41 cartes, 16 opérateurs, chaque état de carte, textes de 32 caractères.
        PhoneStatePacket plein = new PhoneStatePacket(1, PhoneStatePacket.Kind.UPDATE, PhoneStatePacket.Lock.UNLOCKED,
                100, 0L, 0, "aube", false, true, "Alex", 5, 0, PhoneStatePacket.Result.SIM_INSERTED,
                PhoneStatePacket.CloseReason.NONE, pleine());
        PhoneStatePacket relu = allerRetour(plein);
        assertEquals(plein, relu);
        assertEquals(PhoneNetView.MAX_CHOICES, relu.net().choices().size());
        assertEquals(PhoneNetView.MAX_REQUESTABLE, relu.net().requestable().size());
        assertEquals(PhoneNetView.NONE, allerRetour(new PhoneStatePacket(1, PhoneStatePacket.Kind.OPEN,
                PhoneStatePacket.Lock.SETUP, 1, 0L, 0, "", false, false, "", 5, 0, PhoneStatePacket.Result.NONE,
                PhoneStatePacket.CloseReason.NONE, null)).net(), "une vue nulle part comme NONE");

        for (PhoneActionPacket.Action action : PhoneActionPacket.Action.values()) {
            PhoneActionPacket geste = new PhoneActionPacket(7, action, "4821", "0000", action.ordinal() % 2 == 0);
            assertEquals(geste, allerRetour(geste));
        }
        PhoneActionPacket seize = new PhoneActionPacket(Integer.MAX_VALUE, PhoneActionPacket.Action.WALLPAPER,
                "a".repeat(PhoneActionPacket.MAX_WIRE_TEXT), "", false);
        assertEquals(seize, allerRetour(seize));
    }

    /** Une vue réseau pleine : 41 cartes (chaque case, chaque état), 16 opérateurs, textes à la borne de 32. */
    private static PhoneNetView pleine() {
        List<PhoneSims.SimChoice> cartes = new ArrayList<>();
        PhoneSims.Validity[] etats = PhoneSims.Validity.values();
        for (int slot = 0; slot < PhoneNetView.MAX_CHOICES; slot++) {
            cartes.add(new PhoneSims.SimChoice(slot, String.format("06 %02d 34 56 78", slot), "Opérateur " + slot,
                    etats[slot % etats.length]));
        }
        List<PhoneNetView.OperatorChoice> ops = new ArrayList<>();
        for (int i = 0; i < PhoneNetView.MAX_REQUESTABLE; i++) {
            ops.add(new PhoneNetView.OperatorChoice(String.format("%012x", 0xabc000L + i), "Lagon " + i, i % 5));
        }
        return new PhoneNetView(PhoneSims.Validity.SUSPENDED, "é".repeat(PhoneNetView.MAX_TEXT), 4, "06 12 34 56 78",
                "Joueur_Seize_16c", true, cartes, ops);
    }

    private static FriendlyByteBuf ecrire(PhoneNetView vue) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        vue.encode(buf);
        return buf;
    }

    /** 42 cartes, 17 opérateurs ou 5 barres, écrits comme par un serveur trafiqué : la vue ne se lit pas. */
    @Test
    void uneVueTropLongueNeSeLitPas() {
        List<PhoneSims.SimChoice> cartes = new ArrayList<>(pleine().choices());
        cartes.add(new PhoneSims.SimChoice(40, "06 00 00 00 00", "Trop", PhoneSims.Validity.ACTIVE));
        PhoneNetView trop = new PhoneNetView(PhoneSims.Validity.ACTIVE, "Lagon", 2, "", "", false, cartes, List.of());
        FriendlyByteBuf buf = ecrire(trop);
        try {
            assertThrows(DecoderException.class, () -> PhoneNetView.decode(buf), "42 cartes");
        } finally {
            buf.release();
        }
        List<PhoneNetView.OperatorChoice> ops = new ArrayList<>(pleine().requestable());
        ops.add(new PhoneNetView.OperatorChoice("0123456789ab", "Trop", 1));
        FriendlyByteBuf buf2 = ecrire(new PhoneNetView(PhoneSims.Validity.NONE, "", 0, "", "", false, List.of(), ops));
        try {
            assertThrows(DecoderException.class, () -> PhoneNetView.decode(buf2), "17 opérateurs");
        } finally {
            buf2.release();
        }
        FriendlyByteBuf buf3 = ecrire(new PhoneNetView(PhoneSims.Validity.ACTIVE, "Lagon", 5, "", "", false, List.of(),
                List.of()));
        try {
            assertThrows(DecoderException.class, () -> PhoneNetView.decode(buf3), "5 barres");
        } finally {
            buf3.release();
        }
        // Un texte de 33 caractères écrit à la main : refusé à la lecture.
        FriendlyByteBuf buf4 = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf4.writeByte(PhoneSims.Validity.ACTIVE.ordinal());
            buf4.writeUtf("x".repeat(PhoneNetView.MAX_TEXT + 1));
            assertThrows(DecoderException.class, () -> PhoneNetView.decode(buf4), "opérateur de 33 caractères");
        } finally {
            buf4.release();
        }
        // Côté serveur, un texte trop long (NBT forgé) est coupé, jamais une levée à l'envoi.
        assertEquals(PhoneNetView.MAX_TEXT, new PhoneNetView(PhoneSims.Validity.ACTIVE, "y".repeat(500), 1, "", "",
                false, List.of(), List.of()).operator().length());
    }

    /**
     * Hors {@code UNLOCKED}, la vue ne garde que la carte, l'opérateur et les barres : ni numéro, ni titulaire, ni
     * cartes, ni opérateurs demandables, ni appli. Les octets envoyés ne contiennent ni le numéro ni le titulaire.
     */
    @Test
    void uneVueVerrouilleeNePorteNiNumeroNiTitulaire() {
        PhoneNetView vue = pleine().locked();
        assertEquals(PhoneSims.Validity.SUSPENDED, vue.sim());
        assertEquals(pleine().operator(), vue.operator());
        assertEquals(4, vue.bars());
        assertEquals("", vue.number());
        assertEquals("", vue.holder());
        assertFalse(vue.founderApp());
        assertTrue(vue.choices().isEmpty() && vue.requestable().isEmpty(), vue.toString());
        PhoneStatePacket etat = new PhoneStatePacket(2, PhoneStatePacket.Kind.UPDATE, PhoneStatePacket.Lock.LOCKED, 50,
                0L, 0, "nuit", false, false, "", 5, 0, PhoneStatePacket.Result.NONE,
                PhoneStatePacket.CloseReason.NONE, vue);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            etat.encode(buf);
            byte[] octets = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), octets);
            String brut = new String(octets, StandardCharsets.ISO_8859_1);
            for (String secret : new String[] {"06 12 34 56 78", "Joueur_Seize_16c", "06 05 34 56 78", "Lagon 3"}) {
                assertFalse(brut.contains(secret), "« " + secret + " » est parti avec une vue verrouillée");
            }
            assertEquals(etat, PhoneStatePacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void actionTropLongueNeSeLitPas() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // Écrit à la main, comme un client trafiqué : 17 caractères dans le premier champ.
            buf.writeVarInt(1);
            buf.writeByte(PhoneActionPacket.Action.UNLOCK.ordinal());
            buf.writeUtf("1".repeat(PhoneActionPacket.MAX_WIRE_TEXT + 1));
            buf.writeUtf("");
            buf.writeBoolean(false);
            assertThrows(DecoderException.class, () -> PhoneActionPacket.decode(buf));
        } finally {
            buf.release();
        }
        assertEquals(16, PhoneActionPacket.MAX_WIRE_TEXT);
    }

    @Test
    void gesteInconnuNeSeLitPas() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeByte(PhoneActionPacket.Action.values().length);
            buf.writeUtf("");
            buf.writeUtf("");
            buf.writeBoolean(false);
            assertThrows(DecoderException.class, () -> PhoneActionPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    /** Un geste qui porte un code ne l'écrit jamais par son {@code toString} (journal, message d'assertion). */
    @Test
    void leToStringDUnGesteMasqueLeCode() {
        for (PhoneActionPacket.Action a : new PhoneActionPacket.Action[] {PhoneActionPacket.Action.UNLOCK,
                PhoneActionPacket.Action.SETUP, PhoneActionPacket.Action.ERASE, PhoneActionPacket.Action.CHANGE_CODE}) {
            String texte = new PhoneActionPacket(7, a, "4821", "1357", false).toString();
            assertFalse(texte.contains("4821") || texte.contains("1357"), texte);
            assertTrue(texte.contains("****") && texte.contains(a.name()) && texte.contains("7"), texte);
        }
        // Le fond d'écran n'est pas secret : il reste lisible.
        assertTrue(new PhoneActionPacket(7, PhoneActionPacket.Action.WALLPAPER, "nuit", "", false).toString()
                .contains("nuit"));
    }
}
