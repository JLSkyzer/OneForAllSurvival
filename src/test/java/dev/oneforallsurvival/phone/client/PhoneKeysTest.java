package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.phone.client.PhoneKeys.Route;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/**
 * Retours d'essai du 27/11, sections 1 et 2 : le jeu traduit lui-même les touches du téléphone (rangée du haut sans Maj,
 * clavier AZERTY compris, et pavé numérique ; Retour arrière ; Entrée des deux blocs), laisse un champ de texte faire
 * sa saisie, et ne s'attribue jamais les touches « parler » et « chuchoter » du voice chat.
 */
class PhoneKeysTest {

    @Test
    void laRangeeDuHautDonneLeChiffreAvecOuSansMaj() {
        for (int d = 0; d <= 9; d++) {
            assertEquals(String.valueOf(d), PhoneKeys.key(GLFW.GLFW_KEY_0 + d, 0), "rangée du haut " + d);
            // Sur un clavier AZERTY, la touche « 1 » sans Maj tape « & » : GLFW la nomme pourtant GLFW_KEY_1.
            assertEquals(String.valueOf(d), PhoneKeys.key(GLFW.GLFW_KEY_0 + d, GLFW.GLFW_MOD_SHIFT), "avec Maj " + d);
        }
    }

    @Test
    void lePaveNumeriqueDonneLeChiffre() {
        for (int d = 0; d <= 9; d++) {
            assertEquals(String.valueOf(d), PhoneKeys.key(GLFW.GLFW_KEY_KP_0 + d, 0), "pavé " + d);
            assertEquals(String.valueOf(d), PhoneKeys.key(GLFW.GLFW_KEY_KP_0 + d, GLFW.GLFW_MOD_NUM_LOCK),
                    "pavé verrouillé " + d);
        }
    }

    @Test
    void retourArriereEtLesDeuxEntrees() {
        assertEquals(PhoneKeys.BACK, PhoneKeys.key(GLFW.GLFW_KEY_BACKSPACE, 0));
        assertEquals(PhoneKeys.ENTER, PhoneKeys.key(GLFW.GLFW_KEY_ENTER, 0));
        assertEquals(PhoneKeys.ENTER, PhoneKeys.key(GLFW.GLFW_KEY_KP_ENTER, 0));
    }

    @Test
    void unAccordOuAltGrNeDonneRien() {
        // AltGr arrive comme Ctrl + Alt sous Windows : AltGr + 0 tape « @ » sur un clavier AZERTY.
        assertNull(PhoneKeys.key(GLFW.GLFW_KEY_0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT));
        assertNull(PhoneKeys.key(GLFW.GLFW_KEY_5, GLFW.GLFW_MOD_CONTROL));
        assertNull(PhoneKeys.key(GLFW.GLFW_KEY_KP_5, GLFW.GLFW_MOD_ALT));
        assertNull(PhoneKeys.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SUPER));
    }

    @Test
    void lesAutresTouchesNeDonnentRien() {
        for (int key : new int[] {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_DELETE,
                GLFW.GLFW_KEY_KP_DECIMAL, GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_ESCAPE,
                GLFW.GLFW_KEY_UNKNOWN, GLFW.GLFW_KEY_0 - 1, GLFW.GLFW_KEY_9 + 1, GLFW.GLFW_KEY_KP_0 - 1,
                GLFW.GLFW_KEY_KP_9 + 1}) {
            assertNull(PhoneKeys.key(key, 0), "touche " + key);
        }
    }

    @Test
    void horsDUnChampLaToucheVaALaPageTraduite() {
        PhoneKeys.Decision d = PhoneKeys.press(GLFW.GLFW_KEY_KP_7, 0, false, false);
        assertEquals(Route.PAGE, d.route());
        assertEquals("7", d.key());
        assertEquals(Route.PAGE, PhoneKeys.press(GLFW.GLFW_KEY_BACKSPACE, 0, false, false).route());
        assertEquals(Route.PAGE, PhoneKeys.press(GLFW.GLFW_KEY_KP_ENTER, 0, false, false).route());
    }

    @Test
    void dansUnChampLaSaisieResteCelleDuChamp() {
        assertEquals(Route.BROWSER, PhoneKeys.press(GLFW.GLFW_KEY_3, 0, false, true).route());
        assertEquals(Route.BROWSER, PhoneKeys.press(GLFW.GLFW_KEY_KP_3, 0, false, true).route());
        assertEquals(Route.BROWSER, PhoneKeys.press(GLFW.GLFW_KEY_BACKSPACE, 0, false, true).route());
        // Entrée valide la feuille du contact, depuis un champ aussi (comme aujourd'hui).
        PhoneKeys.Decision entree = PhoneKeys.press(GLFW.GLFW_KEY_KP_ENTER, 0, false, true);
        assertEquals(Route.PAGE, entree.route());
        assertEquals(PhoneKeys.ENTER, entree.key());
    }

    @Test
    void uneToucheSansSensVaAuNavigateurTelleQuelle() {
        PhoneKeys.Decision d = PhoneKeys.press(GLFW.GLFW_KEY_A, 0, false, false);
        assertEquals(Route.BROWSER, d.route());
        assertNull(d.key());
    }

    @Test
    void laToucheDuVoiceChatResteAuJeuQuelleQuElleSoit() {
        for (int key : new int[] {GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_ENTER}) {
            assertEquals(Route.VOICE, PhoneKeys.press(key, 0, true, false).route(), "touche " + key);
            assertEquals(Route.VOICE, PhoneKeys.press(key, 0, true, true).route(), "touche " + key + " dans un champ");
        }
    }

    @Test
    void lesDeuxTouchesDuVoiceChatSontParlerEtChuchoter() {
        assertEquals(Set.of("key.push_to_talk", "key.whisper"), PhoneKeys.VOICE_MAPPINGS);
        assertTrue(PhoneKeys.isVoiceMapping("key.push_to_talk"));
        assertTrue(PhoneKeys.isVoiceMapping("key.whisper"));
        // Muet et menus du voice chat : un menu remplacerait le téléphone.
        assertFalse(PhoneKeys.isVoiceMapping("key.mute_microphone"));
        assertFalse(PhoneKeys.isVoiceMapping("key.voice_chat"));
        assertFalse(PhoneKeys.isVoiceMapping(null));
    }

    @Test
    void uneToucheMaintenueSeDitRepetee() {
        PhoneKeys.Held held = new PhoneKeys.Held();
        assertFalse(held.press(GLFW.GLFW_KEY_ENTER), "premier appui");
        assertTrue(held.press(GLFW.GLFW_KEY_ENTER), "répétition");
        assertFalse(held.press(GLFW.GLFW_KEY_KP_ENTER), "l'autre Entrée");
        held.release(GLFW.GLFW_KEY_ENTER);
        assertFalse(held.press(GLFW.GLFW_KEY_ENTER), "après relâchement");
        held.clear();
        assertFalse(held.press(GLFW.GLFW_KEY_KP_ENTER), "après oubli");
    }
}
