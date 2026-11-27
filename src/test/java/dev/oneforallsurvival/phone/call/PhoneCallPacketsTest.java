package dev.oneforallsurvival.phone.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Book;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Les deux paquets d'appel (lot T3, tâche 4a) : aller-retour, bornes à la construction (jamais un envoi que la lecture
 * refuse) et à la lecture (un paquet trafiqué ne se lit pas), {@code toString} sans numéro ni nom.
 */
class PhoneCallPacketsTest {

    private static final String NUMERO = "0612345678";

    private static PhoneCallActionPacket allerRetour(PhoneCallActionPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            PhoneCallActionPacket out = PhoneCallActionPacket.decode(buf);
            assertEquals(0, buf.readableBytes(), "octets laissés après la lecture");
            return out;
        } finally {
            buf.release();
        }
    }

    private static PhoneCallStatePacket allerRetour(PhoneCallStatePacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            PhoneCallStatePacket out = PhoneCallStatePacket.decode(buf);
            assertEquals(0, buf.readableBytes(), "octets laissés après la lecture");
            return out;
        } finally {
            buf.release();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Geste
    // ---------------------------------------------------------------------------------------------

    @Test
    void chaqueGesteFaitLAllerRetour() {
        for (Action action : Action.values()) {
            PhoneCallActionPacket geste = new PhoneCallActionPacket(7, action, NUMERO, "Maman");
            assertEquals(geste, allerRetour(geste), action.name());
        }
        PhoneCallActionPacket borne = new PhoneCallActionPacket(Integer.MAX_VALUE, Action.SAVE_CONTACT,
                "é".repeat(PhoneCallActionPacket.MAX_TEXT), "😀".repeat(PhoneCallActionPacket.MAX_TEXT / 2));
        assertEquals(borne, allerRetour(borne));
        PhoneCallActionPacket nuls = new PhoneCallActionPacket(1, Action.OPEN_PHONE_APP, null, null);
        assertEquals("", nuls.first());
        assertEquals("", nuls.second());
        assertEquals(32, PhoneCallActionPacket.MAX_TEXT);
    }

    /** Un texte trop long est coupé à la construction, sans jamais couper un caractère en deux. */
    @Test
    void unGesteTropLongEstCoupeALaConstruction() {
        PhoneCallActionPacket long_ = new PhoneCallActionPacket(1, Action.DIAL, "1".repeat(500), "x".repeat(33));
        assertEquals(PhoneCallActionPacket.MAX_TEXT, long_.first().length());
        assertEquals(PhoneCallActionPacket.MAX_TEXT, long_.second().length());
        assertEquals(long_, allerRetour(long_));
        // 31 caractères puis un emoji (deux unités) : l'emoji tomberait à cheval sur la borne, il part entier.
        PhoneCallActionPacket emoji = new PhoneCallActionPacket(1, Action.SAVE_CONTACT, NUMERO, "a".repeat(31) + "😀");
        assertEquals("a".repeat(31), emoji.second());
        assertEquals(emoji, allerRetour(emoji));
    }

    @Test
    void unGesteTrafiqueNeSeLitPas() {
        FriendlyByteBuf trop = new FriendlyByteBuf(Unpooled.buffer());
        try {
            trop.writeVarInt(1);
            trop.writeByte(Action.DIAL.ordinal());
            trop.writeUtf("1".repeat(PhoneCallActionPacket.MAX_TEXT + 1));
            trop.writeUtf("");
            assertThrows(DecoderException.class, () -> PhoneCallActionPacket.decode(trop), "33 caractères");
        } finally {
            trop.release();
        }
        FriendlyByteBuf inconnu = new FriendlyByteBuf(Unpooled.buffer());
        try {
            inconnu.writeVarInt(1);
            inconnu.writeByte(Action.values().length);
            inconnu.writeUtf("");
            inconnu.writeUtf("");
            assertThrows(DecoderException.class, () -> PhoneCallActionPacket.decode(inconnu), "geste inconnu");
        } finally {
            inconnu.release();
        }
    }

    /** Ni le numéro composé ni le nom d'un contact ne s'écrivent par le {@code toString} (journal, assertion). */
    @Test
    void leToStringDUnGesteMasqueNumerosEtNoms() {
        for (Action action : Action.values()) {
            String texte = new PhoneCallActionPacket(7, action, NUMERO, "Maman").toString();
            assertFalse(texte.contains("12345678") || texte.contains("Maman"), texte);
            assertTrue(texte.contains(action.name()) && texte.contains("7"), texte);
        }
        assertTrue(new PhoneCallActionPacket(7, Action.MUTE, "on", "").toString().contains("on"),
                "le silence n'est pas secret");
    }

    // ---------------------------------------------------------------------------------------------
    // État
    // ---------------------------------------------------------------------------------------------

    private static Book plein() {
        List<Contact> contacts = new ArrayList<>();
        for (int i = 0; i < PhoneCallStatePacket.MAX_CONTACTS; i++) {
            contacts.add(new Contact(String.format("06%08d", i), "é".repeat(PhoneCallStatePacket.MAX_NAME - 3) + i % 10));
        }
        List<Recent> recents = new ArrayList<>();
        Recent.Kind[] genres = Recent.Kind.values();
        for (int i = 0; i < PhoneCallStatePacket.MAX_RECENTS; i++) {
            recents.add(new Recent(i % 7 == 0 ? "17" : String.format("07%08d", i), genres[i % genres.length],
                    1_789_000_000_000L + i, i * 3));
        }
        return new Book(contacts, recents);
    }

    @Test
    void chaqueEtatFaitLAllerRetour() {
        for (Phase phase : Phase.values()) {
            for (boolean carnet : new boolean[] {false, true}) {
                PhoneCallStatePacket etat = new PhoneCallStatePacket(phase, 42, NUMERO, "Maman",
                        phase == Phase.ACTIVE ? 1_789_000_000_000L : -1L, phase.ordinal() % 2 == 0,
                        phase.ordinal() % 3 == 0, !carnet,
                        Component.translatable("message.oneforallsurvival.call.busy"),
                        carnet ? plein() : null);
                PhoneCallStatePacket relu = allerRetour(etat);
                assertEquals(etat, relu, phase + " carnet " + carnet);
                assertEquals(carnet, relu.book() != null);
            }
        }
        PhoneCallStatePacket vide = new PhoneCallStatePacket(Phase.NONE, 0, null, null, -1L, false, false, true, null, null);
        assertEquals("", vide.number());
        assertEquals("", vide.name());
        assertEquals(Component.empty(), vide.message());
        assertEquals(vide, allerRetour(vide));
        assertNull(allerRetour(vide).book());
        PhoneCallStatePacket carnetVide = new PhoneCallStatePacket(Phase.NONE, 0, "", "", -1L, false, false, true,
                null, new Book(null, null));
        assertEquals(carnetVide, allerRetour(carnetVide));
        assertTrue(allerRetour(carnetVide).book().contacts().isEmpty());
    }

    /** Construit au-delà des bornes (101 contacts, 51 récents, nom de 40, numéro de 40), il est ramené dedans. */
    @Test
    void unEtatConstruitAuDelaDesBornesSeLitToujours() {
        List<Contact> contacts = new ArrayList<>(plein().contacts());
        contacts.add(new Contact("0699999999", "Trop"));
        contacts.set(0, new Contact("0600000000", "n".repeat(40)));
        List<Recent> recents = new ArrayList<>(plein().recents());
        recents.add(new Recent("0799999999", Recent.Kind.MISSED, 1L, 0));
        PhoneCallStatePacket etat = new PhoneCallStatePacket(Phase.INCOMING, 3, "9".repeat(40), "m".repeat(40), -1L,
                false, false, false, null, new Book(contacts, recents));
        assertEquals(PhoneCallStatePacket.MAX_CONTACTS, etat.book().contacts().size());
        assertEquals(PhoneCallStatePacket.MAX_RECENTS, etat.book().recents().size());
        assertEquals(PhoneCallStatePacket.MAX_NAME, etat.book().contacts().get(0).name().length());
        assertEquals(PhoneCallStatePacket.MAX_TEXT, etat.number().length());
        assertEquals(PhoneCallStatePacket.MAX_NAME, etat.name().length());
        assertEquals(etat, allerRetour(etat));
    }

    /** L'en-tête d'un état écrit à la main, carnet présent, avant ses listes. */
    private static FriendlyByteBuf entete(String numero, String nom) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(Phase.ACTIVE.ordinal());
        buf.writeVarInt(5);
        buf.writeUtf(numero);
        buf.writeUtf(nom);
        buf.writeLong(-1L);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(true); // sur ce téléphone
        buf.writeUtf(""); // aucun détail d'urgence
        buf.writeBoolean(false); // aucun message
        return buf;
    }

    /** Le détail d'une urgence (appelant et position) fait l'aller-retour, borné, et reste masqué. */
    @Test
    void leDetailDUneUrgenceEstBorneEtMasque() {
        PhoneCallStatePacket urgence = new PhoneCallStatePacket(Phase.INCOMING, 1_000_000_000, "17", "Bob", -1L, false,
                false, true, null, null, "Bob · 12 64 -30 · overworld");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            urgence.encode(buf);
            PhoneCallStatePacket lu = PhoneCallStatePacket.decode(buf);
            assertEquals(urgence, lu);
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
        assertFalse(urgence.toString().contains("-30"), urgence.toString());
        PhoneCallStatePacket long_ = new PhoneCallStatePacket(Phase.INCOMING, 3, "15", "", -1L, false, false, true,
                null, null, "x".repeat(500));
        assertEquals(PhoneCallStatePacket.MAX_DETAIL, long_.detail().length());
        FriendlyByteBuf trop = entete(NUMERO, "Maman");
        trop.clear();
        trop.writeByte(Phase.ACTIVE.ordinal());
        trop.writeVarInt(5);
        trop.writeUtf(NUMERO);
        trop.writeUtf("Maman");
        trop.writeLong(-1L);
        trop.writeBoolean(false);
        trop.writeBoolean(false);
        trop.writeBoolean(true);
        trop.writeUtf("d".repeat(PhoneCallStatePacket.MAX_DETAIL + 1));
        refuse(trop, "détail trop long");
    }

    private static FriendlyByteBuf trafique(int contacts, String nomContact, int recents, int genre) {
        FriendlyByteBuf buf = entete(NUMERO, "Maman");
        buf.writeBoolean(true);
        buf.writeVarInt(contacts);
        for (int i = 0; i < contacts; i++) {
            buf.writeUtf(NUMERO);
            buf.writeUtf(nomContact);
        }
        buf.writeVarInt(recents);
        for (int i = 0; i < recents; i++) {
            buf.writeUtf(NUMERO);
            buf.writeByte(genre);
            buf.writeLong(1L);
            buf.writeVarInt(1);
        }
        return buf;
    }

    private static void refuse(FriendlyByteBuf buf, String quoi) {
        try {
            assertThrows(DecoderException.class, () -> PhoneCallStatePacket.decode(buf), quoi);
        } finally {
            buf.release();
        }
    }

    @Test
    void unEtatTrafiqueNeSeLitPas() {
        FriendlyByteBuf temoin = trafique(100, "n".repeat(32), 50, 0);
        try {
            PhoneCallStatePacket lu = PhoneCallStatePacket.decode(temoin);
            assertEquals(100, lu.book().contacts().size());
            assertEquals(50, lu.book().recents().size());
            assertEquals(0, temoin.readableBytes());
        } finally {
            temoin.release();
        }
        refuse(trafique(101, "n", 0, 0), "101 contacts");
        refuse(trafique(1, "n".repeat(33), 0, 0), "nom de 33");
        refuse(trafique(0, "n", 51, 0), "51 récents");
        refuse(trafique(0, "n", 1, Recent.Kind.values().length), "genre de récent inconnu");
        refuse(trafique(-1, "n", 0, 0), "nombre de contacts négatif");
        refuse(entete("1".repeat(33), "Maman"), "numéro de 33");
        refuse(entete(NUMERO, "n".repeat(33)), "nom de 33");
        FriendlyByteBuf phase = new FriendlyByteBuf(Unpooled.buffer());
        phase.writeByte(Phase.values().length);
        refuse(phase, "phase inconnue");
    }

    /** Ni le numéro du correspondant, ni son nom, ni rien du carnet dans le {@code toString}. */
    @Test
    void leToStringDUnEtatMasqueNumerosEtNoms() {
        Book book = new Book(List.of(new Contact("0798765432", "Tonton")),
                List.of(new Recent("0711223344", Recent.Kind.INCOMING, 5L, 9)));
        String texte = new PhoneCallStatePacket(Phase.ACTIVE, 12, NUMERO, "Maman", 5L, true, false, true, null, book)
                .toString();
        for (String secret : new String[] {"12345678", "Maman", "98765432", "Tonton", "11223344"}) {
            assertFalse(texte.contains(secret), secret + " dans " + texte);
        }
        assertTrue(texte.contains("ACTIVE") && texte.contains("12"), texte);
    }
}
