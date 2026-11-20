package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * L'aller-retour des deux paquets du téléphone : l'état (serveur vers client) ressort égal pour chaque valeur de chaque
 * énumération ; un geste (client vers serveur) dont un texte dépasse {@link PhoneActionPacket#MAX_WIRE_TEXT} ne se lit
 * pas.
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
                                n % 2 == 0, n % 3 == 0, "Joueur_" + (n % 100), n % 6, n % 61, last, reason);
                        assertEquals(packet, allerRetour(packet));
                    }
                }
            }
        }
        // Les bornes : session et batterie à zéro, heure négative, textes vides.
        PhoneStatePacket bornes = new PhoneStatePacket(0, PhoneStatePacket.Kind.CLOSE, PhoneStatePacket.Lock.SETUP, 0,
                -1L, -43_200, "", false, false, "", 0, 0, PhoneStatePacket.Result.NONE,
                PhoneStatePacket.CloseReason.NONE);
        assertEquals(bornes, allerRetour(bornes));

        for (PhoneActionPacket.Action action : PhoneActionPacket.Action.values()) {
            PhoneActionPacket geste = new PhoneActionPacket(7, action, "4821", "0000", action.ordinal() % 2 == 0);
            assertEquals(geste, allerRetour(geste));
        }
        PhoneActionPacket seize = new PhoneActionPacket(Integer.MAX_VALUE, PhoneActionPacket.Action.WALLPAPER,
                "a".repeat(PhoneActionPacket.MAX_WIRE_TEXT), "", false);
        assertEquals(seize, allerRetour(seize));
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
