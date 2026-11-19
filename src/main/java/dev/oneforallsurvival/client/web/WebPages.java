package dev.oneforallsurvival.client.web;

import java.util.Locale;

/**
 * Adresses des pages locales du mod (schéma {@code mod://oneforallsurvival/}). Seules ces pages et
 * {@code about:blank} peuvent être chargées par le navigateur.
 */
public final class WebPages {

    public static final String BASE = "mod://oneforallsurvival/";

    private static final String BLANK = "about:blank";
    private static final String RESOURCE_ROOT = "/assets/oneforallsurvival/html/";

    private WebPages() {}

    /** Adresse d'une page du mod ; refuse tout chemin vide, en majuscules, remontant, absolu ou qui contient un blanc. */
    public static String url(String path) {
        if (path == null
                || path.isEmpty()
                || hasBlankOrControl(path)
                || !path.equals(path.toLowerCase(Locale.ROOT))
                || path.contains("..")
                || path.indexOf('\\') >= 0
                || path.indexOf(':') >= 0
                || path.startsWith("/")) {
            throw new IllegalArgumentException("chemin de page refusé : " + path);
        }
        return BASE + path;
    }

    /**
     * Vrai pour {@code about:blank} et les pages du mod ; les remontées, antiobliques, pourcentages, espaces et caractères
     * de contrôle (jusqu'à U+0020) sont refusés.
     */
    public static boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        if (url.equals(BLANK)) {
            return true;
        }
        return url.startsWith(BASE)
                && !hasBlankOrControl(url)
                && !url.contains("..")
                && url.indexOf('\\') < 0
                && url.indexOf('%') < 0;
    }

    private static boolean hasBlankOrControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) <= 0x20) {
                return true;
            }
        }
        return false;
    }

    /** Chemin de la page dans les ressources du mod. */
    public static String resourcePath(String path) {
        return RESOURCE_ROOT + path;
    }
}
