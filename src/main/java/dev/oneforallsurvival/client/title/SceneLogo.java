package dev.oneforallsurvival.client.title;

import dev.oneforallsurvival.earlywindow.EarlyScene;

/**
 * Position du logo de la scène, lue dans son manifeste. Classe à part pour que l'écran titre ne nomme jamais
 * {@link EarlyScene} (jar de service) : elle n'est exécutée qu'avec une scène présente, et la référence à la scène
 * n'est pas gardée au-delà de l'appel.
 */
final class SceneLogo {
    private SceneLogo() {}

    /** @param scene la scène rendue par {@link TitleScene#acquire()} (une {@link EarlyScene}) */
    static TitleLayout.Rect rect(Object scene, int guiW, int guiH, int guiScale) {
        return TitleLayout.logoRect(guiW, guiH, guiScale, ((EarlyScene) scene).manifest());
    }
}
