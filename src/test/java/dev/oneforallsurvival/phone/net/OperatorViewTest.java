package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.oneforallsurvival.machine.MachineState;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * La vue de l'opérateur (lot T2, tâche 9) sur le réseau : une vue pleine aux bornes (32 antennes, 32 cartes, 32
 * demandes, 32 opérateurs au relais, textes de 32 caractères) ressort égale ; au-delà d'une borne, elle ne se lit pas.
 * Le geste ({@link OperatorActionPacket}) fait l'aller-retour pour chaque action.
 */
class OperatorViewTest {

    private static final String TEXTE_32 = "abcdefghijklmnopqrstuvwxyzABCDEF";

    private static byte[] octets(OperatorView view) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            view.encode(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static OperatorView relire(byte[] octets) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(octets));
        try {
            OperatorView out = OperatorView.decode(buf);
            assertEquals(0, buf.readableBytes(), "octets laissés après la lecture");
            return out;
        } finally {
            buf.release();
        }
    }

    /** Une vue pleine : chaque liste à sa borne, chaque texte à 32 caractères, chaque état d'énumération présent. */
    static OperatorView pleine(int cartes, String nom) {
        List<OperatorView.AntennaLine> antennes = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            MachineState etat = MachineState.values()[i % MachineState.values().length];
            antennes.add(new OperatorView.AntennaLine(new UUID(1, i), TEXTE_32, new BlockPos(-30_000_000, -64 + i,
                    30_000_000 - i), etat, new AntennaLoad.Breakdown(6, 6 * i, 9 * (i % 3)), 1_000_000L * i,
                    Long.MAX_VALUE - i, i == 0, i % 2 == 0));
        }
        List<OperatorView.CardLine> lignes = new ArrayList<>();
        for (int i = 0; i < cartes; i++) {
            SimCard.State etat = SimCard.State.values()[i % SimCard.State.values().length];
            lignes.add(new OperatorView.CardLine(new UUID(2, i), "06" + String.format("%08d", i),
                    i % 4 == 0 ? "" : TEXTE_32.substring(0, 16), etat));
        }
        List<OperatorView.RequestLine> demandes = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            demandes.add(new OperatorView.RequestLine(new UUID(3, i), TEXTE_32, 1_789_000_000_000L + i));
        }
        List<PhoneNetwork.RelayState> relais = new ArrayList<>();
        for (int i = 0; i < OperatorView.MAX_LIST; i++) {
            relais.add(new PhoneNetwork.RelayState(String.format("%012x", i), TEXTE_32, i, i / 2));
        }
        return new OperatorView(OperatorView.Source.BLOCK, new BlockPos(12, 70, -40), 7, "0123456789ab", nom,
                new OperatorView.Rights(true, true, true, false), 25L, 1_000L, 20L, 123_456_789L, antennes, 3, 4,
                3 * OperatorView.MAX_LIST + cartes, lignes, demandes, relais);
    }

    @Test
    void allerRetour() {
        OperatorView pleine = pleine(OperatorView.MAX_LIST, TEXTE_32);
        assertEquals(pleine, relire(octets(pleine)));
        // Les deux sources, les droits un à un, le tarif du lendemain absent, des listes vides.
        for (OperatorView.Source source : OperatorView.Source.values()) {
            for (int droits = 0; droits < 16; droits++) {
                OperatorView v = new OperatorView(source, BlockPos.ZERO, droits, "", "",
                        new OperatorView.Rights((droits & 1) != 0, (droits & 2) != 0, (droits & 4) != 0,
                                (droits & 8) != 0), 0L, null, 0L, 0L, List.of(), 0, 0, 0, List.of(), List.of(),
                        List.of());
                OperatorView relue = relire(octets(v));
                assertEquals(v, relue);
                assertNull(relue.nextTariff(), "le tarif du lendemain absent reste absent");
            }
        }
    }

    @Test
    void uneVueTropLongueNeSeLitPas() {
        // Écrite à la main (encode borne déjà) : 33 cartes.
        OperatorView trop = pleine(OperatorView.MAX_LIST + 1, "Lagon");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            trop.encodeUnbounded(buf);
            assertThrows(DecoderException.class, () -> OperatorView.decode(buf));
        } finally {
            buf.release();
        }
        // encode refuse aussi d'écrire une vue au-delà des bornes : jamais un paquet que le client ne lirait pas.
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(IllegalArgumentException.class, () -> trop.encode(out));
        } finally {
            out.release();
        }
    }

    @Test
    void unNomDe33CaracteresNeSeLitPas() {
        OperatorView trop = pleine(1, TEXTE_32 + "G");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            trop.encodeUnbounded(buf);
            assertThrows(DecoderException.class, () -> OperatorView.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void unGesteFaitLAllerRetour() {
        for (OperatorActionPacket.Action action : OperatorActionPacket.Action.values()) {
            OperatorActionPacket p = new OperatorActionPacket(41, action, new UUID(5, action.ordinal()).toString(),
                    action.ordinal() * 1000L);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                p.encode(buf);
                OperatorActionPacket relu = OperatorActionPacket.decode(buf);
                assertEquals(p, relu);
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
        // Un argument plus long qu'un identifiant ne se lit pas.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeVarInt(0);
            buf.writeUtf("x".repeat(OperatorActionPacket.MAX_ARG + 1));
            buf.writeLong(0L);
            assertThrows(DecoderException.class, () -> OperatorActionPacket.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void uneVueNePorteAucunJeton() {
        // Une carte n'y figure que par son identifiant : la ligne de carte n'a pas de champ « jeton ».
        for (var composant : OperatorView.CardLine.class.getRecordComponents()) {
            assertFalse(composant.getName().toLowerCase().contains("token"), "champ " + composant.getName());
            assertFalse(composant.getName().toLowerCase().contains("phone"), "champ " + composant.getName());
        }
    }
}
