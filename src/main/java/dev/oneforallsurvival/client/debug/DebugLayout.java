package dev.oneforallsurvival.client.debug;

import dev.oneforallsurvival.client.title.TitleLayout.Rect;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mise en page des panneaux du F3.
 *
 * <p>Logique pure (aucune classe de Minecraft). Vue normale ({@link #place}) : colonne de gauche Position,
 * Monde, Lost Island, Mods ; colonne de droite Performances, Cible ; marges et écart de 4 px. Règle « Repli
 * des panneaux » : quand l'écran est trop petit, Position, Performances et Cible restent, un panneau non
 * essentiel qui ne tient pas est retiré. Vue détaillée ({@link #placeDetail}) : rien n'est jamais retiré,
 * ce qui ne tient pas passe sur des pages suivantes.
 */
public final class DebugLayout {

    /** Marge à chaque bord de l'écran, en pixels d'interface. */
    public static final int MARGIN = 4;
    /** Écart entre deux panneaux, en pixels d'interface. */
    public static final int GAP = 4;
    /** Dégagement d'une carte au centre de l'écran : le repère 3D du F3 a des bras de 10 px autour du viseur. */
    public static final int CROSSHAIR_CLEARANCE = 12;
    /** La barre d'objets s'étend de cette distance de part et d'autre du centre de l'écran. */
    private static final int HOTBAR_HALF_W = 91;
    /** Fond de la pastille (6 px) et écart de 2 px avec la barre d'objets. */
    private static final int HINT_CHROME = 8;

    private DebugLayout() {}

    /** Largeur maximale d'une carte de la vue normale : la colonne de droite s'arrête avant le viseur. */
    public static int normalMaxWidth(int guiW, int cap) {
        return Math.min(cap, guiW / 2 - MARGIN - CROSSHAIR_CLEARANCE);
    }

    /** Vrai si la pastille de vue, de texte large de {@code textW}, tient à droite de la barre d'objets. */
    public static boolean hintFitsBesideHotbar(int guiW, int textW) {
        return textW <= guiW - MARGIN - (guiW / 2 + HOTBAR_HALF_W) - HINT_CHROME;
    }

    /**
     * Emplacements de mise en page. Distinct de {@link DebugLines.Panel} car Lost Island n'y figure pas
     * (ses données viennent d'un paquet) ; l'affichage associe chaque {@code Panel} à son emplacement :
     * POSITION, WORLD, PERFORMANCE, TARGET, MODS portent le même nom ; ISLAND est le panneau Lost Island ;
     * DETAIL reçoit {@code Panel.DETAIL_ONLY} (vue détaillée uniquement, jamais placé par {@link #place}).
     */
    public enum Slot {
        POSITION(true, false),
        WORLD(false, false),
        ISLAND(false, false),
        MODS(false, false),
        DETAIL(false, false),
        PERFORMANCE(true, true),
        TARGET(true, true);

        private final boolean essential;
        private final boolean right;

        Slot(boolean essential, boolean right) {
            this.essential = essential;
            this.right = right;
        }

        /** Vrai si le panneau reste dans la vue normale quand l'écran est trop petit. */
        public boolean essential() {
            return essential;
        }

        /** Vrai si le panneau est dans la colonne de droite de la vue normale. */
        public boolean right() {
            return right;
        }
    }

    /** Dimensions d'un panneau en pixels d'interface. */
    public record Size(int w, int h) {}

    /**
     * Place les panneaux de la vue normale. Un panneau absent de {@code sizes} (ou de taille nulle) n'est
     * pas placé ; un panneau retiré par le repli est absent du résultat (la vue détaillée les montre tous).
     * {@link Slot#DETAIL} n'est jamais placé ici. Les rectangles ne se chevauchent jamais et restent dans
     * l'écran : un panneau trop large ou trop haut est rogné aux marges, et un panneau qui n'a plus aucune
     * place est absent.
     */
    public static Map<Slot, Rect> place(int guiW, int guiH, Map<Slot, Size> sizes) {
        List<Slot> left = new ArrayList<>();
        List<Slot> right = new ArrayList<>();
        for (Slot s : Slot.values()) {
            Size size = sizes.get(s);
            if (size == null || size.w() <= 0 || size.h() <= 0 || s == Slot.DETAIL) {
                continue;
            }
            (s.right() ? right : left).add(s);
        }

        // Les colonnes se gêneraient horizontalement : on ne garde que les panneaux essentiels à gauche.
        if (tooWide(left, right, sizes, guiW)) {
            left.removeIf(s -> !s.essential());
        }
        // Toujours trop étroit : une seule colonne, de haut en bas, sans risque de chevauchement.
        if (tooWide(left, right, sizes, guiW)) {
            List<Slot> single = new ArrayList<>(left);
            single.addAll(right);
            return stack(single, sizes, guiW, guiH, false);
        }

        Map<Slot, Rect> out = new EnumMap<>(Slot.class);
        out.putAll(stack(left, sizes, guiW, guiH, false));
        out.putAll(stack(right, sizes, guiW, guiH, true));
        return out;
    }

    /**
     * Un morceau de panneau posé sur une page de la vue détaillée : les lignes {@code firstLine} à
     * {@code firstLine + lineCount - 1} (un panneau entier : {@code firstLine = 0}, {@code lineCount} = toutes).
     */
    public record Placement(Slot slot, Rect rect, int firstLine, int lineCount) {}

    /**
     * Vue détaillée : rien n'est jamais retiré. Les panneaux (dans l'ordre de {@link Slot}) coulent en autant
     * de colonnes que la largeur en permet, de haut en bas ; quand l'écran est plein, la suite passe à la page
     * suivante (F3 + Tab fait défiler : vue normale, page 1, page 2, ..., vue normale). Un panneau plus haut
     * que l'écran est coupé par lignes sur plusieurs colonnes ou pages, chaque morceau gardant son cadre.
     *
     * <p>La hauteur d'un panneau est {@code chrome + lignes * lineHeight} (cadre et titre dans
     * {@code chrome}) ; le nombre de lignes se déduit de {@code Size.h()}. Chaque slot présent apparaît sur
     * au moins une page et ses morceaux couvrent chacune de ses lignes une fois.
     *
     * @param lineHeight hauteur d'une ligne (au moins 1)
     * @param chrome     hauteur fixe d'un panneau ou d'un morceau, en dehors des lignes
     * @return les pages ; liste vide s'il n'y a aucun panneau
     */
    public static List<List<Placement>> placeDetail(
            int guiW, int guiH, Map<Slot, Size> sizes, int lineHeight, int chrome) {
        int lh = Math.max(1, lineHeight);
        int usableH = Math.max(1, guiH - 2 * MARGIN);
        int usableW = Math.max(1, guiW - 2 * MARGIN);
        int colW = 1;
        for (Slot s : Slot.values()) {
            Size size = sizes.get(s);
            if (size != null && size.w() > 0 && size.h() > 0) {
                colW = Math.max(colW, size.w());
            }
        }
        colW = Math.min(colW, usableW);
        int cols = Math.max(1, (usableW + GAP) / (colW + GAP));

        List<List<Placement>> pages = new ArrayList<>();
        List<Placement> page = null;
        int col = 0;
        int y = 0;
        for (Slot s : Slot.values()) {
            Size size = sizes.get(s);
            if (size == null || size.w() <= 0 || size.h() <= 0) {
                continue;
            }
            int w = Math.min(size.w(), colW);
            int total = Math.max(1, (size.h() - chrome + lh - 1) / lh);
            int done = 0;
            while (done < total) {
                if (page == null) {
                    page = new ArrayList<>();
                    pages.add(page);
                }
                int remaining = total - done;
                int fullH = chrome + remaining * lh;
                int avail = usableH - y;
                int take;
                if (fullH <= avail) {
                    take = remaining;
                } else if (y > 0 && fullH <= usableH) {
                    take = 0; // tient entier dans une colonne neuve : on change de colonne
                } else {
                    take = Math.min(remaining, (avail - chrome) / lh);
                    if (take < 1 && y == 0) {
                        take = 1; // écran minuscule : au moins une ligne, rognée par l'écran
                    }
                }
                if (take < 1) {
                    col++;
                    y = 0;
                    if (col >= cols) {
                        col = 0;
                        page = new ArrayList<>();
                        pages.add(page);
                    }
                    continue;
                }
                int h = Math.max(1, Math.min(chrome + take * lh, usableH - y));
                page.add(new Placement(s, new Rect(MARGIN + col * (colW + GAP), MARGIN + y, w, h), done, take));
                y += h + GAP;
                done += take;
            }
        }
        return pages;
    }

    private static boolean tooWide(List<Slot> left, List<Slot> right, Map<Slot, Size> sizes, int guiW) {
        return widest(left, sizes) + widest(right, sizes) + 2 * MARGIN + GAP > guiW;
    }

    private static int widest(List<Slot> slots, Map<Slot, Size> sizes) {
        int w = 0;
        for (Slot s : slots) {
            w = Math.max(w, sizes.get(s).w());
        }
        return w;
    }

    /**
     * Empile une colonne de haut en bas. Un panneau non essentiel qui ne tient pas, ou qui suit un panneau
     * retiré, est retiré (l'ordre est conservé : on ne remonte pas un petit panneau devant un grand).
     *
     * @param rightAligned colle les panneaux au bord droit
     */
    private static Map<Slot, Rect> stack(
            List<Slot> slots, Map<Slot, Size> sizes, int guiW, int guiH, boolean rightAligned) {
        Map<Slot, Rect> out = new EnumMap<>(Slot.class);
        int y = MARGIN;
        boolean dropping = false;
        for (Slot s : slots) {
            Size size = sizes.get(s);
            boolean fits = y + size.h() <= guiH - MARGIN;
            if (!s.essential() && (dropping || !fits)) {
                dropping = true;
                continue;
            }
            // Rognage aux marges de l'écran (un essentiel plus grand que l'écran reste visible en partie).
            int w = Math.min(size.w(), guiW - 2 * MARGIN);
            int h = Math.min(size.h(), guiH - MARGIN - y);
            if (w <= 0 || h <= 0) {
                continue;
            }
            int x = rightAligned ? guiW - MARGIN - w : MARGIN;
            out.put(s, new Rect(x, y, w, h));
            y += h + GAP;
        }
        return out;
    }
}
