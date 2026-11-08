package dev.oneforallsurvival.earlywindow.scene;

/**
 * Ce que la scène doit montrer à une image donnée.
 *
 * <p>Deux horloges, à ne pas confondre :
 * <ul>
 *   <li>{@code storyTime} : temps du récit ({@link StoryClock}), figé à {@link StoryClock#HOLD} une fois l'aube
 *       atteinte. Il pilote l'ambiance (via {@link StoryClock#storyProgress}), l'avancée du zoom, l'ouverture
 *       depuis le noir et tout ce qui est scénarisé ;</li>
 *   <li>{@code realTime} : secondes réelles depuis l'ouverture de la fenêtre, jamais figées. Il anime ce qui doit
 *       rester vivant après le récit : mer, défilement des nuages et des bancs, dérive de la caméra,
 *       scintillement.</li>
 * </ul>
 * {@code progress} est la vraie progression du chargement (0 à 1) : elle ne sert qu'à la barre et au texte
 * (tâche 6), jamais à l'ambiance.
 */
public record SceneState(float storyTime, float progress, StatusMessages.Status status, float uiAlpha,
                         double realTime) {
}
