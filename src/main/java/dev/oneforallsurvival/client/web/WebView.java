package dev.oneforallsurvival.client.web;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Une page affichée par le navigateur du jeu. Aucun type de MCEF ici : l'implémentation vit dans
 * {@code client/web/mcef/} et n'est chargée qu'une fois MCEF présent et prêt ({@link WebViews}).
 *
 * <p>Coordonnées et tailles en pixels réels de la fenêtre, relatifs au coin haut gauche de la vue.
 */
public interface WebView {

    void resize(int pixelWidth, int pixelHeight);

    /** Agrandissement de la page (1 = un pixel CSS par pixel réel). */
    void setScale(double scale);

    /** Dessine la dernière image de la page dans le rectangle donné, en coordonnées d'interface. */
    void draw(GuiGraphics g, int x, int y, int width, int height);

    void mouseMove(int px, int py);

    void mousePress(int px, int py, int button);

    void mouseRelease(int px, int py, int button);

    void mouseWheel(int px, int py, double amount);

    void keyPress(int key, int scan, int modifiers);

    void keyRelease(int key, int scan, int modifiers);

    void charTyped(char c, int modifiers);

    void focus(boolean focused);

    /** Exécute un script dans la page ; le construire par {@link WebScript}, jamais par concaténation. */
    void run(String script);

    /** Vrai dès que la page a peint une première image. */
    boolean painted();

    void close();
}
