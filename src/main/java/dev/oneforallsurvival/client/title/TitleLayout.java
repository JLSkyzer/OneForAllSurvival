package dev.oneforallsurvival.client.title;

import dev.oneforallsurvival.earlywindow.scene.SceneManifest;
import dev.oneforallsurvival.earlywindow.scene.SceneProjection;
import java.util.ArrayList;
import java.util.List;

/**
 * Disposition de l'écran titre : logique pure, sans classe Minecraft.
 *
 * <p>Toutes les coordonnées sont en pixels d'interface mis à l'échelle (ceux de {@code guiScaledWidth} et
 * {@code guiScaledHeight}), y vers le bas.
 */
public final class TitleLayout {
    /** Largeur d'un bouton principal, réduite jusqu'à {@link #MAIN_W_MIN} si l'écran est étroit. */
    public static final int MAIN_W = 160;
    public static final int MAIN_W_MIN = 120;
    public static final int MAIN_H = 20;
    public static final int MAIN_GAP = 4;
    public static final int MAIN_COUNT = 4;
    /** Écart entre le bas du logo et le premier bouton. */
    public static final int LOGO_GAP = 12;
    /** Marge à gauche et en bas de la rangée du bas, et écart entre ses lignes. */
    public static final int ROW_MARGIN = 6;
    public static final int ROW_GAP = 4;
    /** La rangée du bas passe à la ligne au-dessus si elle dépasse cette part de la largeur. */
    public static final double ROW_MAX_SHARE = 0.45;
    /** Écart minimal entre le dernier bouton principal et la rangée du bas. */
    private static final int ABOVE_ROW_GAP = 6;

    private TitleLayout() {}

    /** Rectangle en pixels d'interface. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean intersects(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }
    }

    /**
     * Position du logo de la scène en pixels d'interface : lettres de {@code SceneManifest.logo()}, posées à
     * ({@link SceneProjection#LOGO_X}, {@link SceneProjection#LOGO_Y}) de l'image de référence, projetées avec le
     * recadrage centré du rendu de la scène ({@link SceneProjection}), puis ramenées à l'échelle d'interface.
     *
     * <p>C'est la position au repos de l'aube tenue ({@code StoryClock.HOLD}, récit figé) : à l'écran, le logo
     * respire de quelques pixels (temps réel) mais il ne suit pas la dérive de la caméra, qui n'agit que sur les
     * calques du décor. La lueur autour des lettres (marge {@code glowPad}) n'est pas comptée.
     *
     * @param guiW     largeur d'interface ({@code guiScaledWidth})
     * @param guiH     hauteur d'interface ({@code guiScaledHeight})
     * @param guiScale facteur d'échelle d'interface (pixels de la fenêtre par pixel d'interface)
     */
    public static Rect logoRect(int guiW, int guiH, int guiScale, SceneManifest manifest) {
        int[] fb = SceneProjection.toWindow(guiW * guiScale, guiH * guiScale, manifest.outW(), manifest.outH(),
                SceneProjection.LOGO_X, SceneProjection.LOGO_Y, manifest.logo().width(), manifest.logo().height());
        int x = Math.floorDiv(fb[0], guiScale);
        int y = Math.floorDiv(fb[1], guiScale);
        int right = Math.floorDiv(fb[0] + fb[2] + guiScale - 1, guiScale);
        int bottom = Math.floorDiv(fb[1] + fb[3] + guiScale - 1, guiScale);
        return new Rect(x, y, right - x, bottom - y);
    }

    /**
     * Position de repli du logo quand il n'y a pas de scène (jar de service absent, échec) : aucun logo n'est
     * dessiné, ce rectangle vide sert seulement d'ancre à la colonne de boutons, centrée verticalement, à la même
     * marge gauche relative que le logo de la scène (72 px sur 1920).
     */
    public static Rect fallbackLogoRect(int screenW, int screenH) {
        int x = Math.max(2 * ROW_MARGIN, screenW * 3 / 80);
        int total = MAIN_COUNT * MAIN_H + (MAIN_COUNT - 1) * MAIN_GAP;
        int y = Math.max(0, (screenH - total) / 2 - LOGO_GAP);
        return new Rect(x, y, 0, 0);
    }

    /**
     * Les quatre boutons principaux, l'un sous l'autre, alignés à gauche sur le logo et commençant
     * {@link #LOGO_GAP} px sous lui. Si la rangée du bas ({@code bottom}, vide si absente) gênerait, la colonne
     * remonte jusqu'à la laisser libre, quitte à recouvrir le logo sur les très petits écrans.
     */
    public static List<Rect> mainButtons(int screenW, int screenH, Rect logo, List<Rect> bottom) {
        int w = Math.max(MAIN_W_MIN, Math.min(MAIN_W, screenW - logo.x() - ROW_MARGIN));
        int x = Math.max(0, Math.min(logo.x(), screenW - w));
        int total = MAIN_COUNT * MAIN_H + (MAIN_COUNT - 1) * MAIN_GAP;
        int y = logo.bottom() + LOGO_GAP;
        if (!bottom.isEmpty()) {
            int rowTop = Integer.MAX_VALUE;
            for (Rect r : bottom) {
                rowTop = Math.min(rowTop, r.y());
            }
            y = Math.min(y, rowTop - ABOVE_ROW_GAP - total);
        }
        y = Math.max(0, y);
        List<Rect> out = new ArrayList<>(MAIN_COUNT);
        for (int i = 0; i < MAIN_COUNT; i++) {
            out.add(new Rect(x, y + i * (MAIN_H + MAIN_GAP), w, MAIN_H));
        }
        return out;
    }

    /**
     * Place qu'occupe à l'écran un aperçu 3D du joueur de Quick-Skin 4.1.0 ({@code PlayerWidget}), dont le rectangle
     * de widget ne dit rien : modèle de hauteur {@code 2 x échelle} posé sur ses pieds, centré en x, large de 0,6 fois
     * sa hauteur (règle de {@code PlayerWidget.isMouseOutsideModelArea} et de {@code TitleSplashClearance.fits}, lues
     * au javap).
     */
    public static Rect modelFootprint(int centerX, int feetY, float scale) {
        int h = (int) (scale * 2f);
        int w = (int) (h * 0.6f);
        return new Rect(centerX - w / 2, feetY - h, w, h);
    }

    /** Écart minimal entre un panneau d'un autre mod et ce qui l'entoure, et marge au bord de l'écran. */
    public static final int PANEL_GAP = 4;

    /**
     * Place d'un panneau d'un autre mod (l'aperçu de Quick-Skin et ses boutons), déplacé d'un bloc : même taille que
     * {@code panel}, du côté droit (centre dans la moitié droite), le plus à droite possible puis le plus près du
     * milieu en hauteur, à {@link #PANEL_GAP} px au moins des {@code obstacles} (logo, nos boutons, rangée du bas,
     * version, mention de Mojang) et des bords. Vide s'il n'y a pas de place sans chevauchement : l'appelant le laisse
     * alors où son mod l'a mis.
     */
    public static java.util.Optional<Rect> panel(int screenW, int screenH, Rect panel, List<Rect> obstacles) {
        int w = panel.w();
        int h = panel.h();
        int minX = Math.max(PANEL_GAP, screenW / 2 - w / 2);
        int maxX = screenW - PANEL_GAP - w;
        int maxY = screenH - PANEL_GAP - h;
        if (maxX < minX || maxY < PANEL_GAP) {
            return java.util.Optional.empty();
        }
        int centerY = Math.max(PANEL_GAP, Math.min(maxY, (screenH - h) / 2));
        for (int x = maxX; x >= minX; x--) {
            for (int d = 0; centerY - d >= PANEL_GAP || centerY + d <= maxY; d++) {
                int[] ys = d == 0 ? new int[] {centerY} : new int[] {centerY - d, centerY + d};
                for (int y : ys) {
                    if (y >= PANEL_GAP && y <= maxY && clear(new Rect(x, y, w, h), obstacles)) {
                        return java.util.Optional.of(new Rect(x, y, w, h));
                    }
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean clear(Rect r, List<Rect> obstacles) {
        Rect grown = new Rect(r.x() - PANEL_GAP, r.y() - PANEL_GAP, r.w() + 2 * PANEL_GAP, r.h() + 2 * PANEL_GAP);
        for (Rect o : obstacles) {
            if (o.w() > 0 && o.h() > 0 && grown.intersects(o)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rangée du bas : widgets de tailles données ({@code {largeur, hauteur}}), de gauche à droite à partir de
     * x = {@link #ROW_MARGIN}, à {@link #ROW_MARGIN} px du bas, écart {@link #MAIN_GAP}. Un widget qui ferait
     * dépasser {@link #ROW_MAX_SHARE} de la largeur de l'écran passe sur une ligne au-dessus ; les lignes se
     * lisent de haut en bas dans l'ordre des widgets, la dernière touchant le bas. Rects dans l'ordre d'entrée.
     */
    public static List<Rect> bottomRow(int screenW, int screenH, List<int[]> sizes) {
        return bottomRow(screenW, screenH, sizes, (int) (screenW * ROW_MAX_SHARE));
    }

    /**
     * Comme {@link #bottomRow(int, int, List)}, mais une ligne ne dépasse pas {@code maxRight} (bord droit permis,
     * ramené à au plus {@link #ROW_MAX_SHARE} de la largeur) : laisse la place à la mention de Mojang en bas à droite
     * sur les écrans étroits. Un widget seul plus large que la limite garde sa ligne.
     */
    public static List<Rect> bottomRow(int screenW, int screenH, List<int[]> sizes, int maxRight) {
        int limit = Math.min(maxRight, (int) (screenW * ROW_MAX_SHARE));
        List<List<Integer>> lines = new ArrayList<>();
        List<Integer> line = new ArrayList<>();
        int used = ROW_MARGIN;
        for (int i = 0; i < sizes.size(); i++) {
            int w = sizes.get(i)[0];
            int next = line.isEmpty() ? used + w : used + MAIN_GAP + w;
            if (!line.isEmpty() && next > limit) {
                lines.add(line);
                line = new ArrayList<>();
                next = ROW_MARGIN + w;
            }
            line.add(i);
            used = next;
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        Rect[] out = new Rect[sizes.size()];
        int lineBottom = screenH - ROW_MARGIN;
        for (int l = lines.size() - 1; l >= 0; l--) {
            int height = 0;
            for (int i : lines.get(l)) {
                height = Math.max(height, sizes.get(i)[1]);
            }
            int x = ROW_MARGIN;
            for (int i : lines.get(l)) {
                int[] s = sizes.get(i);
                out[i] = new Rect(x, lineBottom - s[1], s[0], s[1]); // alignés sur le bas de la ligne
                x += s[0] + MAIN_GAP;
            }
            lineBottom -= height + ROW_GAP;
        }
        return List.of(out);
    }
}
