package dev.oneforallsurvival.explore;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Un message reçu par un récepteur : l'heure du jeu, la fréquence et un texte brut. Le constructeur nettoie le texte :
 * pas de code de mise en forme ({@code §}), pas de caractère de contrôle, 256 caractères au plus. Une commande ne peut
 * donc rien injecter dans l'écran.
 */
public record SignalMessage(long gameTime, int frequency, String text) {

    public static final int MAX_LENGTH = 256;
    private static final char SECTION_SIGN = '§';

    public SignalMessage {
        text = clean(text);
    }

    /** Le texte sans {@code §}, sans caractère de contrôle (remplacé par une espace), coupé à {@link #MAX_LENGTH}. */
    public static String clean(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_LENGTH));
        for (int i = 0; i < raw.length() && out.length() < MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c == SECTION_SIGN) {
                continue;
            }
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(gameTime);
        buf.writeVarInt(frequency);
        buf.writeUtf(text, MAX_LENGTH);
    }

    public static SignalMessage decode(FriendlyByteBuf buf) {
        return new SignalMessage(buf.readLong(), buf.readVarInt(), buf.readUtf(MAX_LENGTH));
    }
}
