package dev.oneforallsurvival.client.tab;

import java.util.List;

/**
 * Mise en page de la liste des joueurs, en pixels GUI (spécification, section 7) : choix des colonnes, troncature,
 * largeur et hauteur du panneau. Logique pure ; les largeurs de texte viennent de {@link Measure}.
 */
public final class TabLayout {

    public static final int MARGIN_TOP = 6;
    public static final int MARGIN_BOTTOM = 6;
    public static final int MARGIN_SIDE = 8;
    public static final int PAD = 8;
    public static final int HEADER_H = 22;
    public static final int TEXT_Y = 7;
    public static final int MAYOR_GAP = 5;
    public static final int MAYOR_H = 24;
    public static final int COLS_GAP = 6;
    public static final int COLHEAD_H = 10;
    public static final int ROW_H = 11;
    public static final int COL_GAP = 12;
    public static final int MAX_ROWS = 20;
    public static final int TARGET_ROWS = 10;
    public static final int FOOT_GAP = 4;
    public static final int FOOT_H = 15;
    public static final int SPLIT = 16;
    /** Place gardée pour le ping du maire : la largeur de « 999+ ms ». */
    public static final int MAYOR_PING_W = 40;
    public static final int BARS_W = 10;

    public static final String TITLE = "LOST ISLAND";
    public static final String TITLE_RP = "RP";
    public static final String SUBTITLE = "· liste des joueurs";
    public static final String MAYOR_WORD = "MAIRE";
    public static final String MAYOR_OF = "de l'île";
    public static final String DAY = "Jour";
    public static final String HOUR = "Heure";
    public static final String FLIGHT = "Vol 417 · île inconnue";
    public static final String COL_NAME = "Survivant";
    public static final String COL_JOB = "Métier";
    public static final String COL_PING = "ms";

    private TabLayout() {}

    /** Largeurs de texte de la police du jeu ; en gras, 1 px de plus par caractère. */
    public interface Measure {
        int width(String text);

        int boldWidth(String text);
    }

    /** Une ligne de la liste : pseudo et libellé de métier tel qu'affiché (déjà tronqué, {@code null} sans métier). */
    public record Row(String name, String job) {}

    /** Le maire du bandeau : pseudo et libellé de son métier ({@code null} sans métier). */
    public record Mayor(String name, String job) {}

    public enum Mode { FULL, COMPACT }

    /**
     * Résultat. Ordonnées relatives au haut du panneau, abscisses de colonne relatives à son bord gauche.
     *
     * @param shown joueurs dessinés (les premiers de la liste) ; {@code hidden} > 0 : la dernière case dit « et N autres »
     * @param mayorJob libellé du métier du maire à dessiner (tronqué par le garde-fou des très petites largeurs)
     */
    public record Result(int x, int y, int width, int height, int innerWidth, boolean shortForms, Mode mode, int cols,
            int rows, int colWidth, int nameW, int jobW, int dot, int jobX, int msRight, int bars, int shown, int hidden,
            int mayorY, int colsY, int footY, String mayorJob) {

        public boolean hasMayor() {
            return mayorY >= 0;
        }
    }

    /** Valeurs fixes d'une colonne pour un mode donné. */
    private record Columns(Mode mode, int colW, int dot, int jobX, int msRight, int bars) {}

    public static Result compute(List<Row> rows, Mayor mayor, int survivors, String day, String clock, int guiWidth,
            int guiHeight, Measure m) {
        int n = rows.size();
        boolean hasMayor = mayor != null;
        int fixedH = HEADER_H + 1 + (hasMayor ? MAYOR_GAP + MAYOR_H : 0) + (n > 0 ? COLS_GAP + COLHEAD_H : 0)
                + FOOT_GAP + FOOT_H;
        int maxRows = clamp((guiHeight - MARGIN_TOP - MARGIN_BOTTOM - fixedH) / ROW_H, 1, MAX_ROWS);
        int availW = guiWidth - 2 * MARGIN_SIDE - 2 * PAD;

        // Écart à la spécification : la colonne des pseudos est au moins aussi large que son en-tête « Survivant »,
        // sinon « Métier » le chevauche quand tous les pseudos sont courts (vu en jeu avec « Dev » seul, 18 px).
        int nameW = n > 0 ? m.width(COL_NAME) : 0;
        int jobW = 0;
        for (Row r : rows) {
            nameW = Math.max(nameW, m.width(r.name()));
            if (r.job() != null) {
                jobW = Math.max(jobW, m.width(r.job()));
            }
        }
        Columns full = full(nameW, jobW);
        Columns compact = compact(nameW);

        Columns chosen = null;
        int cols = 0;
        int rowCount = 0;
        int shown = n;
        int hidden = 0;
        if (n > 0) {
            int[][] tries = {{0, ceilDiv(n, Math.min(TARGET_ROWS, maxRows))}, {0, ceilDiv(n, maxRows)}, {1, ceilDiv(n, maxRows)}};
            for (int[] t : tries) {
                Columns c = t[0] == 0 ? full : compact;
                if (columnsWidth(t[1], c.colW()) <= availW) {
                    chosen = c;
                    cols = t[1];
                    rowCount = ceilDiv(n, cols);
                    break;
                }
            }
            if (chosen == null) {
                chosen = compact;
                cols = Math.max(1, (availW + COL_GAP) / (compact.colW() + COL_GAP));
                rowCount = maxRows;
                shown = cols * rowCount - 1;
                hidden = n - shown;
            }
        }
        int colsW = n > 0 ? columnsWidth(cols, chosen.colW()) : 0;

        String survivorsNumber = Integer.toString(survivors);
        int rightBlock = m.boldWidth(survivorsNumber) + 4 + m.width(TabRules.survivorsText(survivors));
        int headerShort = m.boldWidth(TITLE) + 4 + m.boldWidth(TITLE_RP) + SPLIT + rightBlock;
        int header = headerShort + 6 + m.width(SUBTITLE);
        int footShort = m.width(DAY) + 4 + m.width(day) + 8 + m.width(HOUR) + 4 + m.width(clock);
        int foot = footShort + SPLIT + m.width(FLIGHT);

        String mayorJob = hasMayor ? mayor.job() : null;
        int mayorW = 0;
        if (hasMayor) {
            int fixed = mayorFixedWidth(mayor.name(), mayorJob != null, m);
            mayorW = fixed + (mayorJob == null ? 0 : m.width(mayorJob));
            if (mayorW > availW && mayorJob != null) {
                mayorJob = TabRules.truncate(mayorJob, Math.max(0, availW - fixed), m::width);
                mayorW = fixed + m.width(mayorJob);
            }
        }

        boolean shortForms = false;
        int iw = Math.max(Math.max(colsW, header), Math.max(foot, mayorW));
        if (iw > availW) {
            shortForms = true;
            iw = Math.max(Math.max(colsW, headerShort), Math.max(footShort, mayorW));
        }

        int y = HEADER_H + 1;
        int mayorY = -1;
        if (hasMayor) {
            mayorY = y + MAYOR_GAP;
            y = mayorY + MAYOR_H;
        }
        int colsY = -1;
        if (n > 0) {
            colsY = y + COLS_GAP;
            y = colsY + COLHEAD_H + rowCount * ROW_H;
        }
        int footY = y + FOOT_GAP;
        int height = footY + FOOT_H;
        int width = iw + 2 * PAD;
        Columns c = chosen == null ? full : chosen;
        return new Result((guiWidth - width) / 2, MARGIN_TOP, width, height, iw, shortForms, c.mode(), cols, rowCount,
                c.colW(), nameW, jobW, c.dot(), c.jobX(), c.msRight(), c.bars(), shown, hidden, mayorY, colsY, footY,
                mayorJob);
    }

    /** Abscisse de la pastille du maire, relative au bandeau. */
    public static int mayorDot(String mayorName, Measure m) {
        return 28 + Math.max(m.boldWidth(mayorName), mayorTitleWidth(m)) + 8;
    }

    /** « MAIRE de l'île » : 71 px avec la police du jeu. */
    static int mayorTitleWidth(Measure m) {
        return m.boldWidth(MAYOR_WORD) + 4 + m.width(MAYOR_OF);
    }

    /** Largeur du bandeau du maire sans le libellé de son métier. */
    private static int mayorFixedWidth(String name, boolean job, Measure m) {
        int w = mayorDot(name, m);
        if (job) {
            w += 6;
        }
        return w + 12 + MAYOR_PING_W + 4 + BARS_W + 4;
    }

    private static Columns full(int nameW, int jobW) {
        int dot = 13 + nameW + 8;
        int jobX = dot + 6;
        int msRight = jobX + jobW + 6 + 24;
        int bars = msRight + 4;
        return new Columns(Mode.FULL, bars + BARS_W + 2, dot, jobX, msRight, bars);
    }

    private static Columns compact(int nameW) {
        int bars = 15 + nameW + 6;
        return new Columns(Mode.COMPACT, bars + 12, -1, -1, -1, bars);
    }

    private static int columnsWidth(int cols, int colW) {
        return cols * colW + (cols - 1) * COL_GAP;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
