package dev.oneforallsurvival.phone;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur vers client : ce que l'écran du téléphone montre, pour la session {@link #session}. {@link Kind#OPEN} ouvre
 * l'écran, {@link Kind#UPDATE} met à jour celui de la même session (après chaque geste), {@link Kind#CLOSE} le ferme
 * avec sa raison.
 *
 * <p>Le code n'y est jamais. Hors {@link Lock#UNLOCKED}, la vue ne dit rien du propriétaire ni des réglages :
 * {@code ownerName} vide, {@code owner}, {@code airplane} et {@code faceId} faux ; seul le fond d'écran reste visible, comme sur
 * l'écran verrouillé d'un vrai téléphone. La vue réseau ({@link #net}, lot T2) n'y garde que la carte, l'opérateur et
 * les barres ({@link PhoneNetView#locked()}) ; l'état de la carte y est grossier ({@code ACTIVE} s'y lit « présente »,
 * jamais « active » : une carte suspendue ou désactivée s'y montre aussi ainsi). Le jeton d'une carte n'y est jamais.
 *
 * @param battery la charge en pour cent (0 à 100, arrondie vers le bas)
 * @param serverMillis l'heure de l'économie ({@code EconomyClock.now()}) à l'envoi
 * @param zoneOffsetSeconds le décalage du fuseau de l'économie à cet instant, en secondes
 * @param owner celui qui regarde est le propriétaire (vue déverrouillée seulement)
 * @param attemptsLeft les essais de code avant blocage (0 pendant un blocage)
 * @param lockoutSeconds les secondes de blocage restantes, arrondies vers le haut (0 sans blocage)
 * @param last l'issue du dernier geste, {@link Result#NONE} à l'ouverture ou pour un geste sans message
 * @param net ce que le téléphone montre du réseau (lot T2) ; nul se lit {@link PhoneNetView#NONE}
 * @param faceId Face ID est actif (retours d'essai du 27/11) : vue déverrouillée du propriétaire seulement, faux sinon
 */
public record PhoneStatePacket(int session, Kind kind, Lock lock, int battery, long serverMillis,
        int zoneOffsetSeconds, String wallpaper, boolean airplane, boolean owner, String ownerName, int attemptsLeft,
        int lockoutSeconds, Result last, CloseReason reason, PhoneNetView net, boolean faceId) {

    /** Borne des textes sur le réseau (un nom de joueur fait 16 caractères au plus, un fond bien moins). */
    public static final int MAX_TEXT = 32;

    public enum Kind { OPEN, UPDATE, CLOSE }

    public enum Lock { SETUP, LOCKED, UNLOCKED }

    public enum Result {
        NONE, WRONG_CODE, LOCKED_OUT, INVALID_CODE, CODE_CHANGED, NOT_OWNER, ERASED,
        // Lot T2 : la carte SIM et le réseau.
        SIM_INSERTED, SIM_REMOVED, SIM_UNKNOWN, INVENTORY_FULL, SIM_REQUESTED, ALREADY_REQUESTED, NO_NETWORK,
        // La file de demandes de l'opérateur est pleine (simRequestsMax).
        REQUESTS_FULL,
        // Retours d'essai du 27/11, Face ID : déverrouillé par le visage à l'ouverture ; visage couvert à l'ouverture
        // (le pavé s'affiche) ; interrupteur posé (code reconnu) ou coupé.
        FACE_UNLOCKED, FACE_NOT_RECOGNIZED, FACE_ID_ON, FACE_ID_OFF
    }

    public enum CloseReason { NONE, CLOSED, BATTERY_EMPTY, PHONE_GONE, ERASED }

    public PhoneStatePacket {
        wallpaper = wallpaper == null ? "" : wallpaper;
        ownerName = ownerName == null ? "" : ownerName;
        net = net == null ? PhoneNetView.NONE : net;
    }

    /** Sans Face ID (faux) : la forme d'avant les retours d'essai du 27/11, gardée pour les essais. */
    public PhoneStatePacket(int session, Kind kind, Lock lock, int battery, long serverMillis, int zoneOffsetSeconds,
            String wallpaper, boolean airplane, boolean owner, String ownerName, int attemptsLeft, int lockoutSeconds,
            Result last, CloseReason reason, PhoneNetView net) {
        this(session, kind, lock, battery, serverMillis, zoneOffsetSeconds, wallpaper, airplane, owner, ownerName,
                attemptsLeft, lockoutSeconds, last, reason, net, false);
    }

    /** Le même état, avec l'issue {@code result}. */
    public PhoneStatePacket withLast(Result result) {
        return new PhoneStatePacket(session, kind, lock, battery, serverMillis, zoneOffsetSeconds, wallpaper, airplane,
                owner, ownerName, attemptsLeft, lockoutSeconds, result, reason, net, faceId);
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(session);
        buf.writeByte(kind.ordinal());
        buf.writeByte(lock.ordinal());
        buf.writeVarInt(battery);
        buf.writeLong(serverMillis);
        buf.writeInt(zoneOffsetSeconds);
        buf.writeUtf(wallpaper, MAX_TEXT);
        buf.writeBoolean(airplane);
        buf.writeBoolean(faceId);
        buf.writeBoolean(owner);
        buf.writeUtf(ownerName, MAX_TEXT);
        buf.writeVarInt(attemptsLeft);
        buf.writeVarInt(lockoutSeconds);
        buf.writeByte(last.ordinal());
        buf.writeByte(reason.ordinal());
        net.encode(buf);
    }

    public static PhoneStatePacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        Kind kind = pick(Kind.values(), buf.readUnsignedByte(), "kind");
        Lock lock = pick(Lock.values(), buf.readUnsignedByte(), "lock");
        int battery = buf.readVarInt();
        long serverMillis = buf.readLong();
        int zoneOffsetSeconds = buf.readInt();
        String wallpaper = buf.readUtf(MAX_TEXT);
        boolean airplane = buf.readBoolean();
        boolean faceId = buf.readBoolean();
        boolean owner = buf.readBoolean();
        String ownerName = buf.readUtf(MAX_TEXT);
        int attemptsLeft = buf.readVarInt();
        int lockoutSeconds = buf.readVarInt();
        Result last = pick(Result.values(), buf.readUnsignedByte(), "last");
        CloseReason reason = pick(CloseReason.values(), buf.readUnsignedByte(), "reason");
        return new PhoneStatePacket(session, kind, lock, battery, serverMillis, zoneOffsetSeconds, wallpaper, airplane,
                owner, ownerName, attemptsLeft, lockoutSeconds, last, reason, PhoneNetView.decode(buf), faceId);
    }

    static <E> E pick(E[] values, int ordinal, String what) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException(what + " inconnu : " + ordinal);
        }
        return values[ordinal];
    }

    /** Côté client, sur son fil principal : l'écran du téléphone ({@code phone/client}, jamais chargé par un serveur). */
    public static void handle(PhoneStatePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.oneforallsurvival.phone.client.PhoneClient.accept(packet));
    }
}
