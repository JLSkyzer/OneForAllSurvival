package dev.oneforallsurvival.client.tab;

import dev.oneforallsurvival.client.debug.DebugTheme;
import dev.oneforallsurvival.role.RoleTeams;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Règles de la liste des joueurs (touche TAB) qui ne dépendent pas du dessin : métier lu dans le préfixe d'équipe, rang
 * de tri, couleur affichée, ping, jour et heure, troncature. Logique pure (spécification
 * {@code docs/superpowers/specs/2026-11-14-liste-joueurs-design.md}, sections 2, 3, 5 et 6).
 */
public final class TabRules {

    /** Rang d'un métier créé par les admins. */
    public static final int RANK_CUSTOM = 4;
    public static final int RANK_CITIZEN = 5;
    /** Joueur sans métier (pas d'équipe, ou équipe hors du mod). */
    public static final int RANK_NONE = 6;
    /** Largeur maximale d'un libellé de métier, en pixels GUI. */
    public static final int JOB_MAX_W = 96;
    public static final String ELLIPSIS = "…";

    /** Ping inconnu, barres éteintes et texte « … » à la couleur des étiquettes. */
    public static final int PING_UNKNOWN = DebugTheme.LABEL;

    private static final List<String> BASE_ORDER = List.of("medecin", "ingenieur", "policier", "agriculteur");

    private TabRules() {}

    /** Un morceau du préfixe d'équipe, avec la couleur RGB de son style ({@code null} : pas de couleur). */
    public record Segment(String text, Integer rgb) {}

    /**
     * Métier lu dans l'équipe : libellé ({@code null} sans métier), couleur RGB du jeu, rang de tri, et si le joueur
     * est le maire.
     */
    public record Job(String label, int rgb, int rank, boolean mayor) {
        public static final Job NONE = new Job(null, 0xFFFFFF, RANK_NONE, false);

        public boolean present() {
            return label != null;
        }
    }

    /** Rang de tri d'après le nom d'équipe : stable même si un libellé de base est renommé. */
    public static int rank(String teamName) {
        if (teamName == null || !teamName.startsWith(RoleTeams.PREFIX)) {
            return RANK_NONE;
        }
        String id = teamName.substring(RoleTeams.PREFIX.length());
        int base = BASE_ORDER.indexOf(id);
        if (base >= 0) {
            return base;
        }
        return "citoyen".equals(id) ? RANK_CITIZEN : RANK_CUSTOM;
    }

    /** Libellé d'un segment : espaces de fin, {@code [} initial et {@code ]} final retirés. */
    static String cleanLabel(String text) {
        String s = text.stripTrailing();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Métier d'un joueur d'après le nom de son équipe et les segments non vides de son préfixe. Équipe {@code ofas_<id>} :
     * le premier libellé ; équipe du maire : le second (le premier est « Maire ») ; autre équipe : pas de métier.
     */
    public static Job job(String teamName, List<Segment> segments) {
        if (teamName == null) {
            return Job.NONE;
        }
        boolean mayor = RoleTeams.MAYOR_TEAM.equals(teamName);
        if (!mayor && !teamName.startsWith(RoleTeams.PREFIX)) {
            return Job.NONE;
        }
        List<Segment> labels = segments.stream().filter(s -> !cleanLabel(s.text()).isBlank()).toList();
        int index = mayor ? 1 : 0;
        if (labels.size() <= index) {
            return mayor ? new Job(null, 0xFFFFFF, RANK_NONE, true) : Job.NONE;
        }
        Segment s = labels.get(index);
        int rgb = s.rgb() == null ? 0xFFFFFF : s.rgb() & 0xFFFFFF;
        // Le maire sort de la liste : son rang ne sert pas au tri.
        return new Job(cleanLabel(s.text()), rgb, mayor ? RANK_NONE : rank(teamName), mayor);
    }

    /** Couleur affichée (ARGB opaque) : une couleur trop sombre sur le verre est mêlée à 40 % de blanc. */
    public static int displayColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        if (luminance < 0.30) {
            r = lighten(r);
            g = lighten(g);
            b = lighten(b);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int c) {
        return (int) Math.round(c + (255 - c) * 0.40);
    }

    /** Jour du monde, qui change à minuit (le tick 0 est 06:00). */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime + 6000, 24000) + 1;
    }

    /** Heure du monde, {@code HH:MM}. */
    public static String clock(long dayTime) {
        long d = Math.floorMod(dayTime, 24000L);
        long hour = (d / 1000 + 6) % 24;
        long minute = (d % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    /** Nombre de barres allumées : seuils de {@code PlayerTabOverlay.renderPingIcon}, 0 pour un ping inconnu (≤ 0). */
    public static int pingBars(int latency) {
        if (latency <= 0) {
            return 0;
        }
        if (latency < 150) {
            return 5;
        }
        if (latency < 300) {
            return 4;
        }
        if (latency < 600) {
            return 3;
        }
        if (latency < 1000) {
            return 2;
        }
        return 1;
    }

    public static int pingColor(int latency) {
        if (latency <= 0) {
            return PING_UNKNOWN;
        }
        if (latency < 150) {
            return DebugTheme.GOOD;
        }
        return latency < 600 ? DebugTheme.AMBER : DebugTheme.BAD;
    }

    /** Texte du ping, sans l'unité : {@code …} inconnu, {@code 999+} à partir d'une seconde. */
    public static String pingText(int latency) {
        if (latency <= 0) {
            return ELLIPSIS;
        }
        return latency >= 1000 ? "999+" : Integer.toString(latency);
    }

    /**
     * Le libellé s'il tient dans {@code max} pixels ; sinon des caractères retirés à la fin jusqu'à ce que le reste,
     * sans espaces de fin et suivi de « … », tienne.
     */
    public static String truncate(String label, int max, ToIntFunction<String> width) {
        if (label == null || width.applyAsInt(label) <= max) {
            return label;
        }
        String s = label;
        while (!s.isEmpty()) {
            s = s.substring(0, s.length() - 1);
            String candidate = s.stripTrailing() + ELLIPSIS;
            if (width.applyAsInt(candidate) <= max) {
                return candidate;
            }
        }
        return ELLIPSIS;
    }

    /** Texte qui suit le nombre de survivants : singulier pour 0 et 1. */
    public static String survivorsText(int survivors) {
        return survivors > 1 ? "survivants sur l'île" : "survivant sur l'île";
    }

    /** Dernière case d'une liste tronquée. */
    public static String othersText(int hidden) {
        return hidden == 1 ? "et 1 autre" : "et " + hidden + " autres";
    }

    /** Ordre de la liste : non-spectateurs d'abord, rang du métier, libellé (rang 4 seulement), pseudo ; sans casse. */
    public static <T> Comparator<T> order(java.util.function.Function<T, Boolean> spectator,
            java.util.function.Function<T, Job> job, java.util.function.Function<T, String> name) {
        return Comparator.<T, Boolean>comparing(spectator)
                .thenComparingInt(t -> job.apply(t).rank())
                .thenComparing(t -> {
                    Job j = job.apply(t);
                    return j.rank() == RANK_CUSTOM && j.label() != null ? j.label() : "";
                }, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(name, String.CASE_INSENSITIVE_ORDER);
    }
}
