package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Relecture de la tâche 7 (C1) : un bouton retiré par la reconstruction de l'écran de repli ne garde pas le focus,
 * sinon Espace ou Entrée l'actionne sous l'écran d'appel.
 */
class PhoneFallbackFocusTest {

    @Test
    void unBoutonRetireNeGardePasLeFocus() {
        Object changerLeCode = new Object();
        Object raccrocher = new Object();
        assertNull(PhoneFallbackFocus.kept(changerLeCode, List.of(raccrocher)), "bouton retiré par la reconstruction");
        assertNull(PhoneFallbackFocus.kept(changerLeCode, List.of()), "écran vidé");
    }

    @Test
    void unEnfantPresentGardeLeFocus() {
        Object champ = new Object();
        assertSame(champ, PhoneFallbackFocus.kept(champ, List.of(new Object(), champ)));
        assertNull(PhoneFallbackFocus.kept(null, List.of(champ)));
    }
}
