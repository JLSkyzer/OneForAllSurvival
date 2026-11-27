package dev.oneforallsurvival.phone.call;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Un appel inscrit aux récents d'une carte SIM ({@link PhoneDirectory}).
 *
 * @param number le numéro du correspondant, en chiffres sans espaces (carte, ou numéro court d'urgence)
 * @param kind appel passé, reçu ou manqué
 * @param atMillis instant de l'appel ({@code EconomyClock.now()}) ; jamais négatif
 * @param seconds durée en secondes ; jamais négative
 */
public record Recent(String number, Recent.Kind kind, long atMillis, int seconds) {

    /** Le sens d'un appel, vu de la carte qui le garde. */
    public enum Kind { OUTGOING, INCOMING, MISSED }

    /** Chiffres au plus d'un numéro gardé : ceux d'un numéro de carte. */
    private static final int MAX_DIGITS = 10;

    public Recent {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(kind, "kind");
        atMillis = Math.max(0L, atMillis);
        seconds = Math.max(0, seconds);
    }

    /**
     * Le numéro se garde-t-il ? De un à {@value #MAX_DIGITS} chiffres ASCII : un numéro de carte, ou un numéro court
     * (17, 15). Les récents n'inscrivent ni ne relisent aucun autre texte.
     */
    boolean hasStorableNumber() {
        return !number.isEmpty() && number.length() <= MAX_DIGITS
                && number.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("number", number);
        tag.putString("kind", kind.name());
        tag.putLong("at", atMillis);
        tag.putInt("seconds", seconds);
        return tag;
    }

    /**
     * Relit un appel ; nul si la ligne est illisible : genre inconnu, date ou durée absente ou d'un autre type, ou
     * numéro qui ne se garde pas ({@link #hasStorableNumber}).
     */
    @Nullable
    static Recent load(CompoundTag tag) {
        if (!tag.contains("at", Tag.TAG_ANY_NUMERIC) || !tag.contains("seconds", Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Recent recent = new Recent(tag.getString("number"), kind, tag.getLong("at"), tag.getInt("seconds"));
        return recent.hasStorableNumber() ? recent : null;
    }
}
