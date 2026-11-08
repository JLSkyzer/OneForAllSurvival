package dev.oneforallsurvival.earlywindow.scene;

/**
 * Compile et lie les programmes GLSL de la scène. Tous les shaders passent par ici : un test peut ainsi
 * injecter un chargeur qui refuse un shader, comme le ferait une carte graphique (tâche 4).
 */
public interface ShaderLoader {
    /**
     * Programme lié à partir de {@code lostisland/shaders/<vertex>.vert} et {@code <fragment>.frag}.
     * L'attribut {@code aPos} est à l'emplacement 0, la sortie {@code fragColor} sur la couleur 0.
     * Appelé sur le thread GL.
     *
     * @return identifiant du programme GL
     * @throws ShaderException si une source manque, ne compile pas ou ne se lie pas
     */
    int load(String vertex, String fragment);

    /** Échec de compilation ou d'édition des liens ; le message porte le journal du pilote. */
    final class ShaderException extends RuntimeException {
        public ShaderException(String message) {
            super(message);
        }
    }
}
