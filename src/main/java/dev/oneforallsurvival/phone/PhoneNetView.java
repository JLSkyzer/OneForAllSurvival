package dev.oneforallsurvival.phone;

import dev.oneforallsurvival.phone.net.PhoneSims;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Ce que l'écran du téléphone montre du réseau (lot T2), calculé par le serveur ({@code PhoneService}) et envoyé avec
 * chaque état ({@link PhoneStatePacket#net}). Le jeton d'une carte n'y est jamais : une carte n'y figure que par son
 * numéro formaté, le nom de son opérateur et ce qu'elle vaut.
 *
 * <p>Hors {@link PhoneStatePacket.Lock#UNLOCKED}, seuls {@link #sim}, {@link #operator} et {@link #bars} restent
 * ({@link #locked()}) : ni numéro, ni titulaire, ni cartes de l'inventaire, ni opérateurs demandables, ni appli
 * « Antenne ».
 *
 * @param sim ce que vaut la carte logée
 * @param operator le nom de l'opérateur de la ligne, vide sans ligne
 * @param bars les barres de signal, 0 à 4 (0 en mode avion)
 * @param number le numéro formaté ({@code 06 12 34 56 78}), vide sans ligne ou verrouillé
 * @param holder le titulaire de la carte, vide pour une carte sans nom, sans ligne ou verrouillé
 * @param founderApp l'icône de l'appli « Antenne » se montre-t-elle ?
 * @param choices les cartes de l'inventaire de celui qui regarde, case par case ({@value #MAX_CHOICES} au plus)
 * @param requestable les opérateurs à qui demander une carte, avec leurs barres ({@value #MAX_REQUESTABLE} au plus)
 */
public record PhoneNetView(PhoneSims.Validity sim, String operator, int bars, String number, String holder,
        boolean founderApp, List<PhoneSims.SimChoice> choices, List<OperatorChoice> requestable) {

    /** Un opérateur à qui demander une carte : son identifiant (12 caractères hexadécimaux), son nom, ses barres ici. */
    public record OperatorChoice(String id, String name, int bars) {

        public OperatorChoice {
            id = clip(id);
            name = clip(name);
        }
    }

    /** Borne de chaque texte sur le réseau ; un texte plus long est coupé à l'écriture de la vue. */
    public static final int MAX_TEXT = 32;
    /** Les cases d'un inventaire de joueur : 36, l'armure (4) et la seconde main. */
    public static final int MAX_CHOICES = 41;
    public static final int MAX_REQUESTABLE = 16;
    public static final int MAX_BARS = 4;

    /** Aucune carte, aucun signal, rien à proposer. */
    public static final PhoneNetView NONE = new PhoneNetView(PhoneSims.Validity.NONE, "", 0, "", "", false, List.of(),
            List.of());

    public PhoneNetView {
        sim = sim == null ? PhoneSims.Validity.NONE : sim;
        operator = clip(operator);
        number = clip(number);
        holder = clip(holder);
        choices = choices == null ? List.of() : List.copyOf(choices);
        requestable = requestable == null ? List.of() : List.copyOf(requestable);
    }

    /** Un texte jamais nul et jamais plus long que {@link #MAX_TEXT} (un NBT forgé ne fait pas lever l'envoi). */
    static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    /** Ce qu'un écran verrouillé montre : la carte, l'opérateur et les barres, rien d'autre. */
    public PhoneNetView locked() {
        return new PhoneNetView(sim, operator, bars, "", "", false, List.of(), List.of());
    }

    // ---------------------------------------------------------------------------------------------
    // Réseau
    // ---------------------------------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(sim.ordinal());
        buf.writeUtf(operator, MAX_TEXT);
        buf.writeVarInt(bars);
        buf.writeUtf(number, MAX_TEXT);
        buf.writeUtf(holder, MAX_TEXT);
        buf.writeBoolean(founderApp);
        buf.writeVarInt(choices.size());
        for (PhoneSims.SimChoice c : choices) {
            buf.writeVarInt(c.slot());
            buf.writeUtf(clip(c.number()), MAX_TEXT);
            buf.writeUtf(clip(c.operator()), MAX_TEXT);
            buf.writeByte(c.validity().ordinal());
        }
        buf.writeVarInt(requestable.size());
        for (OperatorChoice o : requestable) {
            buf.writeUtf(o.id(), MAX_TEXT);
            buf.writeUtf(o.name(), MAX_TEXT);
            buf.writeVarInt(o.bars());
        }
    }

    /** Relit une vue ; lève si une liste dépasse sa borne, si un texte est trop long ou si une valeur est hors plage. */
    public static PhoneNetView decode(FriendlyByteBuf buf) {
        PhoneSims.Validity sim = PhoneStatePacket.pick(PhoneSims.Validity.values(), buf.readUnsignedByte(), "sim");
        String operator = buf.readUtf(MAX_TEXT);
        int bars = bars(buf.readVarInt());
        String number = buf.readUtf(MAX_TEXT);
        String holder = buf.readUtf(MAX_TEXT);
        boolean founderApp = buf.readBoolean();
        int n = count(buf.readVarInt(), MAX_CHOICES, "cartes");
        List<PhoneSims.SimChoice> choices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slot = buf.readVarInt();
            if (slot < 0 || slot >= MAX_CHOICES) {
                throw new DecoderException("case hors de l'inventaire : " + slot);
            }
            String num = buf.readUtf(MAX_TEXT);
            String op = buf.readUtf(MAX_TEXT);
            PhoneSims.Validity v = PhoneStatePacket.pick(PhoneSims.Validity.values(), buf.readUnsignedByte(), "carte");
            choices.add(new PhoneSims.SimChoice(slot, num, op, v));
        }
        int m = count(buf.readVarInt(), MAX_REQUESTABLE, "opérateurs");
        List<OperatorChoice> requestable = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            String id = buf.readUtf(MAX_TEXT);
            String name = buf.readUtf(MAX_TEXT);
            requestable.add(new OperatorChoice(id, name, bars(buf.readVarInt())));
        }
        return new PhoneNetView(sim, operator, bars, number, holder, founderApp, choices, requestable);
    }

    private static int bars(int value) {
        if (value < 0 || value > MAX_BARS) {
            throw new DecoderException("barres hors plage : " + value);
        }
        return value;
    }

    private static int count(int value, int max, String what) {
        if (value < 0 || value > max) {
            throw new DecoderException("trop de " + what + " : " + value);
        }
        return value;
    }
}
