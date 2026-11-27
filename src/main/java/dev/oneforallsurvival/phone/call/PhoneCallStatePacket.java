package dev.oneforallsurvival.phone.call;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client (lot T3, tâche 4a) : l'appel d'un porteur, vu de son bout, envoyé écran fermé compris
 * ({@link CallUpdates}). Ni jeton, ni UUID de carte, ni joueur : le numéro du correspondant et le nom que lui donne le
 * carnet de <b>ce</b> porteur (vide s'il n'y est pas).
 *
 * @param phase {@link Phase#NONE} hors appel (et à la fin d'un appel), {@link Phase#OUTGOING} pour l'appelant pendant
 *     la sonnerie, {@link Phase#INCOMING} pour l'appelé pendant la sonnerie, {@link Phase#ACTIVE} une fois décroché
 * @param callId le numéro de l'appel (celui qui vient de finir pour un {@code NONE} de fin), 0 sans appel
 * @param number le numéro du correspondant, en chiffres sans espaces ; vide sans appel
 * @param name le nom du correspondant dans le carnet de ce porteur ; vide sinon
 * @param answeredMillis l'instant du décroché ({@code EconomyClock.now()}), -1 avant
 * @param muted le micro de ce porteur est coupé
 * @param speaker le haut-parleur de ce porteur est mis
 * @param here l'appel est porté par le téléphone de la session ouverte de ce porteur (revue de la tâche 4a, I3) : faux
 *     écran fermé ou ouvert sur un autre téléphone, où la page ne montre pas l'appel ; vrai pour un {@link Phase#NONE}
 *     qui répond à la session ouverte (ouverture, geste)
 * @param message la fin ou l'échec à montrer (composant traduit chez le client) ; {@link Component#empty()} sinon
 * @param book le carnet de la carte (contacts et récents), seulement quand l'application Téléphone le demande ou qu'il
 *     change ; nul sinon
 * @param detail sous le titre d'un appel d'urgence, chez le secours seulement : l'appelant et sa position (feature
 *     95) ; vide sinon
 */
public record PhoneCallStatePacket(Phase phase, int callId, String number, String name, long answeredMillis,
        boolean muted, boolean speaker, boolean here, Component message, @Nullable Book book, String detail) {

    /** Borne du détail d'un appel d'urgence (nom et position). */
    public static final int MAX_DETAIL = 96;

    /** Borne d'un numéro sur le réseau. */
    public static final int MAX_TEXT = 32;
    public static final int MAX_NAME = ContactRules.MAX_NAME;
    public static final int MAX_CONTACTS = ContactRules.MAX_CONTACTS;
    public static final int MAX_RECENTS = ContactRules.MAX_RECENTS;

    public enum Phase { NONE, OUTGOING, INCOMING, ACTIVE }

    public PhoneCallStatePacket {
        if (phase == null) {
            throw new IllegalArgumentException("phase");
        }
        number = PhoneCallActionPacket.bound(number, MAX_TEXT);
        name = PhoneCallActionPacket.bound(name, MAX_NAME);
        message = message == null ? Component.empty() : message;
        detail = PhoneCallActionPacket.bound(detail, MAX_DETAIL);
    }

    /** Sans détail : tout appel qui n'est pas une urgence vue du secours. */
    public PhoneCallStatePacket(Phase phase, int callId, String number, String name, long answeredMillis,
            boolean muted, boolean speaker, boolean here, Component message, @Nullable Book book) {
        this(phase, callId, number, name, answeredMillis, muted, speaker, here, message, book, "");
    }

    /**
     * Le carnet d'une carte tel que la page le montre, borné dès la construction (jamais un envoi que la lecture
     * refuse) : {@value ContactRules#MAX_CONTACTS} contacts, {@value ContactRules#MAX_RECENTS} récents, noms de
     * {@value ContactRules#MAX_NAME} au plus.
     */
    public record Book(List<Contact> contacts, List<Recent> recents) {

        public Book {
            List<Contact> c = new ArrayList<>();
            if (contacts != null) {
                for (Contact contact : contacts) {
                    if (c.size() == MAX_CONTACTS) {
                        break;
                    }
                    c.add(new Contact(PhoneCallActionPacket.bound(contact.number(), MAX_TEXT),
                            PhoneCallActionPacket.bound(contact.name(), MAX_NAME)));
                }
            }
            List<Recent> r = new ArrayList<>();
            if (recents != null) {
                for (Recent recent : recents) {
                    if (r.size() == MAX_RECENTS) {
                        break;
                    }
                    r.add(new Recent(PhoneCallActionPacket.bound(recent.number(), MAX_TEXT), recent.kind(),
                            recent.atMillis(), recent.seconds()));
                }
            }
            contacts = List.copyOf(c);
            recents = List.copyOf(r);
        }

        @Override
        public String toString() {
            return "Book[contacts=" + contacts.size() + ", recents=" + recents.size() + "]";
        }
    }

    /** Sans numéro ni nom, ni rien du carnet que ses tailles. */
    @Override
    public String toString() {
        return "PhoneCallStatePacket[phase=" + phase + ", callId=" + callId + ", number=***, name=***, answeredMillis="
                + answeredMillis + ", muted=" + muted + ", speaker=" + speaker + ", here=" + here + ", message="
                + (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        ? t.getKey() : "…")
                + ", book=" + book + ", detail=" + (detail.isEmpty() ? "" : "***") + "]";
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase.ordinal());
        buf.writeVarInt(callId);
        buf.writeUtf(number, MAX_TEXT);
        buf.writeUtf(name, MAX_NAME);
        buf.writeLong(answeredMillis);
        buf.writeBoolean(muted);
        buf.writeBoolean(speaker);
        buf.writeBoolean(here);
        buf.writeUtf(detail, MAX_DETAIL);
        boolean hasMessage = !message.equals(Component.empty());
        buf.writeBoolean(hasMessage);
        if (hasMessage) {
            buf.writeComponent(message);
        }
        buf.writeBoolean(book != null);
        if (book != null) {
            buf.writeVarInt(book.contacts().size());
            for (Contact c : book.contacts()) {
                buf.writeUtf(c.number(), MAX_TEXT);
                buf.writeUtf(c.name(), MAX_NAME);
            }
            buf.writeVarInt(book.recents().size());
            for (Recent r : book.recents()) {
                buf.writeUtf(r.number(), MAX_TEXT);
                buf.writeByte(r.kind().ordinal());
                buf.writeLong(r.atMillis());
                buf.writeVarInt(r.seconds());
            }
        }
    }

    public static PhoneCallStatePacket decode(FriendlyByteBuf buf) {
        Phase phase = pick(Phase.values(), buf.readUnsignedByte(), "phase");
        int callId = buf.readVarInt();
        String number = buf.readUtf(MAX_TEXT);
        String name = buf.readUtf(MAX_NAME);
        long answeredMillis = buf.readLong();
        boolean muted = buf.readBoolean();
        boolean speaker = buf.readBoolean();
        boolean here = buf.readBoolean();
        String detail = buf.readUtf(MAX_DETAIL);
        Component message = buf.readBoolean() ? buf.readComponent() : Component.empty();
        Book book = null;
        if (buf.readBoolean()) {
            int contacts = size(buf.readVarInt(), MAX_CONTACTS, "contacts");
            List<Contact> c = new ArrayList<>(contacts);
            for (int i = 0; i < contacts; i++) {
                c.add(new Contact(buf.readUtf(MAX_TEXT), buf.readUtf(MAX_NAME)));
            }
            int recents = size(buf.readVarInt(), MAX_RECENTS, "récents");
            List<Recent> r = new ArrayList<>(recents);
            for (int i = 0; i < recents; i++) {
                String n = buf.readUtf(MAX_TEXT);
                Recent.Kind kind = pick(Recent.Kind.values(), buf.readUnsignedByte(), "genre de récent");
                r.add(new Recent(n, kind, buf.readLong(), buf.readVarInt()));
            }
            book = new Book(c, r);
        }
        return new PhoneCallStatePacket(phase, callId, number, name, answeredMillis, muted, speaker, here, message,
                book, detail);
    }

    private static int size(int n, int max, String what) {
        if (n < 0 || n > max) {
            throw new DecoderException(what + " : " + n + " (au plus " + max + ")");
        }
        return n;
    }

    private static <E> E pick(E[] values, int ordinal, String what) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException(what + " inconnu : " + ordinal);
        }
        return values[ordinal];
    }

    /** Côté client, sur son fil principal : le dernier état d'appel, et l'écran du téléphone s'il est ouvert. */
    public static void handle(PhoneCallStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.phone.client.PhoneClient.acceptCall(packet));
    }
}
