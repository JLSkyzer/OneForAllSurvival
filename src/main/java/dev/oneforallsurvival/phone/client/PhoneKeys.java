package dev.oneforallsurvival.phone.client;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Les touches du téléphone (retours d'essai du 27/11), règle pure appliquée par {@link PhoneWebScreen} et par
 * {@link PhoneFallbackScreen}.
 *
 * <p>Le jeu traduit lui-même la touche au lieu de compter sur la traduction du navigateur : MCEF 2.1.6 donne à Chromium
 * le code de touche de GLFW tel quel (lu par javap dans {@code MCEFBrowser.sendKeyPress}), que la page ne reconnaît pas
 * pour le pavé numérique (GLFW 320 à 329, Windows 96 à 105) ; et l'écran de repli ne lisait que le caractère tapé, que
 * la rangée du haut d'un clavier AZERTY sans Maj ne donne pas (« & », « é »…). Les codes de touche de GLFW sont ceux
 * de la position sur un clavier américain, quelle que soit la disposition : la rangée du haut est toujours
 * {@code GLFW_KEY_0} à {@code GLFW_KEY_9}.
 *
 * <p>Les touches « parler » et « chuchoter » de Simple Voice Chat ({@link #VOICE_MAPPINGS}) restent au jeu : le voice
 * chat les lit dans l'événement clavier de Forge, que le jeu ne publie pas quand l'écran dit avoir pris la touche.
 */
public final class PhoneKeys {

    /** Retour arrière. */
    public static final String BACK = "back";
    /** Entrée, celle du clavier comme celle du pavé. */
    public static final String ENTER = "enter";
    /** Les liaisons du voice chat que l'écran du téléphone ne prend jamais. Muet et menus n'y sont pas. */
    public static final Set<String> VOICE_MAPPINGS = Set.of("key.push_to_talk", "key.whisper");

    /** Ctrl, Alt (AltGr sous Windows arrive en Ctrl + Alt) ou la touche système : un raccourci, pas un chiffre. */
    private static final int CHORD = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;

    /** Où va une touche enfoncée, téléphone ouvert. */
    public enum Route {
        /** Au jeu : l'écran ne la prend pas, l'événement de Forge part (voice chat). */
        VOICE,
        /** À la page, traduite ({@link Decision#key()}). */
        PAGE,
        /** Au navigateur telle quelle (saisie d'un champ, touches sans sens pour le téléphone). */
        BROWSER
    }

    /** Une décision : la route, et la touche traduite pour {@link Route#PAGE} (nulle sinon). */
    public record Decision(Route route, @Nullable String key) {}

    private static final Decision VOICE = new Decision(Route.VOICE, null);
    private static final Decision BROWSER = new Decision(Route.BROWSER, null);

    private PhoneKeys() {}

    /**
     * La touche du téléphone : un chiffre {@code "0"} à {@code "9"} (rangée du haut, avec ou sans Maj, ou pavé
     * numérique), {@link #BACK}, {@link #ENTER} ; {@code null} pour toute autre touche et pour un raccourci (Ctrl, Alt,
     * AltGr, touche système).
     */
    @Nullable
    public static String key(int glfwKey, int modifiers) {
        if ((modifiers & CHORD) != 0) {
            return null;
        }
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) {
            return String.valueOf(glfwKey - GLFW.GLFW_KEY_0);
        }
        if (glfwKey >= GLFW.GLFW_KEY_KP_0 && glfwKey <= GLFW.GLFW_KEY_KP_9) {
            return String.valueOf(glfwKey - GLFW.GLFW_KEY_KP_0);
        }
        if (glfwKey == GLFW.GLFW_KEY_BACKSPACE) {
            return BACK;
        }
        if (glfwKey == GLFW.GLFW_KEY_ENTER || glfwKey == GLFW.GLFW_KEY_KP_ENTER) {
            return ENTER;
        }
        return null;
    }

    /** Vrai pour le nom d'une liaison du voice chat que le téléphone laisse au jeu. */
    public static boolean isVoiceMapping(@Nullable String name) {
        return name != null && VOICE_MAPPINGS.contains(name);
    }

    /**
     * Où va une touche enfoncée dans la page du téléphone. Une touche du voice chat ({@code voice}) reste au jeu, quelle
     * qu'elle soit. Un champ de texte qui a la main ({@code textFocus}) garde sa saisie : seule Entrée y est traduite
     * (elle valide la feuille). Sinon, chiffres, Retour arrière et Entrée vont à la page traduits, et jamais aussi au
     * navigateur : un chiffre ne compte qu'une fois.
     */
    public static Decision press(int glfwKey, int modifiers, boolean voice, boolean textFocus) {
        if (voice) {
            return VOICE;
        }
        String k = key(glfwKey, modifiers);
        if (k == null || (textFocus && !ENTER.equals(k))) {
            return BROWSER;
        }
        return new Decision(Route.PAGE, k);
    }

    /**
     * Les touches enfoncées, pour reconnaître une touche maintenue : le jeu rappelle {@code keyPressed} à chaque
     * répétition sans le dire, et une Entrée maintenue ne doit ni valider ni appeler une seconde fois.
     */
    public static final class Held {
        private final Set<Integer> down = new HashSet<>();

        /** Note l'appui ; vrai si la touche était déjà enfoncée (répétition). */
        public boolean press(int glfwKey) {
            return !down.add(glfwKey);
        }

        public void release(int glfwKey) {
            down.remove(glfwKey);
        }

        public void clear() {
            down.clear();
        }
    }
}
