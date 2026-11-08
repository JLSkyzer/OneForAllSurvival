package dev.oneforallsurvival.client.debug;

/**
 * Cycle des vues du F3 : 0 est la vue normale, 1 à n les pages de la vue détaillée. F3 + Tab fait défiler
 * normale, page 1, ..., page n, normale. Logique pure.
 */
public final class DebugViews {

    private DebugViews() {}

    /** La vue suivante, {@code pages} étant le nombre de pages de la vue détaillée. */
    public static int next(int view, int pages) {
        if (pages <= 0 || view >= pages) {
            return 0;
        }
        return Math.max(view, 0) + 1;
    }

    /** Garde une vue valide quand le nombre de pages diminue (fenêtre agrandie, cible perdue). */
    public static int clamp(int view, int pages) {
        return Math.max(0, Math.min(view, Math.max(pages, 0)));
    }
}
