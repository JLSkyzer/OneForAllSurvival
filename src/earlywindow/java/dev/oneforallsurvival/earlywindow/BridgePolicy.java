package dev.oneforallsurvival.earlywindow;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Faut-il un pont ({@link GapBridge}) pendant le trou du passage des mods aux ressources ? Logique pure, sans GL :
 * le réglage, le nombre de cœurs, la version et le nom du rendu OpenGL, la plateforme.
 *
 * <p>Réglage {@code bridgeThread} : {@code auto} (défaut : actif sauf sur une petite machine), {@code false} (coupé,
 * l'image se fige au passage comme avant), {@code force} (actif même sur une petite machine). Il se lit dans la
 * propriété système {@value #PROPERTY}, sinon dans {@code config/lostisland-earlywindow.properties}, créé avec la
 * valeur par défaut s'il manque.
 */
public final class BridgePolicy {
    public static final String PROPERTY = "lostisland.bridgeThread";
    public static final String FILE_NAME = "lostisland-earlywindow.properties";
    static final String KEY = "bridgeThread";
    static final int MIN_CORES = 3;

    /** Valeurs du réglage. */
    public enum Setting {
        AUTO, OFF, FORCE;

        /** {@code null} si la valeur n'est pas reconnue. */
        static Setting parse(String value) {
            if (value == null) {
                return null;
            }
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "auto":
                case "true":
                case "on":
                    return AUTO;
                case "false":
                case "off":
                    return OFF;
                case "force":
                    return FORCE;
                default:
                    return null;
            }
        }
    }

    /** Décision et sa raison, lisible au journal. */
    public record Decision(boolean enabled, String reason) {
    }

    private static final String[] SOFTWARE_RENDERERS = {"gdi generic", "llvmpipe", "softpipe", "swiftshader",
            "microsoft basic render"};

    private BridgePolicy() {
    }

    /**
     * @param cores      {@code Runtime.availableProcessors()}
     * @param glVersion  version du contexte, « majeur.mineur »
     * @param renderer   {@code GL_RENDERER}
     * @param windows    vrai sous Windows : le pont présente ses images par WGL, rien d'autre n'est écrit
     */
    public static Decision decide(Setting setting, int cores, String glVersion, String renderer, boolean windows) {
        if (setting == Setting.OFF) {
            return new Decision(false, "coupé par le réglage (bridgeThread=false)");
        }
        if (!windows) {
            return new Decision(false, "plateforme autre que Windows (présentation par WGL seulement)");
        }
        if (setting == Setting.FORCE) {
            return new Decision(true, "forcé par le réglage (bridgeThread=force), " + cores + " cœurs, GL " + glVersion
                    + ", " + renderer);
        }
        if (cores < MIN_CORES) {
            return new Decision(false, cores + " cœurs logiques (≤ 2)");
        }
        if (!atLeast32(glVersion)) {
            return new Decision(false, "GL " + glVersion + " (< 3.2)");
        }
        if (isSoftware(renderer)) {
            return new Decision(false, "rendu logiciel (" + renderer + ")");
        }
        return new Decision(true, cores + " cœurs, GL " + glVersion + ", " + renderer);
    }

    static boolean atLeast32(String glVersion) {
        if (glVersion == null) {
            return false;
        }
        String[] parts = glVersion.trim().split("\\.");
        try {
            int major = Integer.parseInt(parts[0].trim());
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim().split("\\D", 2)[0]) : 0;
            return major > 3 || (major == 3 && minor >= 2);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isSoftware(String renderer) {
        if (renderer == null) {
            return true; // inconnu : prudence
        }
        String r = renderer.toLowerCase(Locale.ROOT);
        for (String s : SOFTWARE_RENDERERS) {
            if (r.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Réglage effectif : la propriété système si elle est posée et reconnue, sinon le fichier (créé avec
     * {@code auto} s'il manque), sinon {@code auto}. Une valeur non reconnue est journalisée et vaut {@code auto}.
     */
    public static Setting resolve(String property, Path configDir, Consumer<String> log) {
        if (property != null) {
            Setting s = Setting.parse(property);
            if (s != null) {
                return s;
            }
            log.accept("pont : -D" + PROPERTY + "=" + property + " non reconnu (auto, false ou force), auto retenu");
        }
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file, defaultFile(), StandardCharsets.UTF_8);
                log.accept("pont : réglage créé avec la valeur par défaut : " + file);
                return Setting.AUTO;
            }
            Properties p = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(in);
            }
            String value = p.getProperty(KEY);
            Setting s = Setting.parse(value);
            if (s == null) {
                log.accept("pont : " + KEY + "=" + value + " non reconnu dans " + file + ", auto retenu");
                return Setting.AUTO;
            }
            return s;
        } catch (IOException | RuntimeException e) {
            log.accept("pont : réglage illisible (" + e + "), auto retenu");
            return Setting.AUTO;
        }
    }

    static String defaultFile() {
        return String.join("\n",
                "# Écran de chargement Lost Island : fil de dessin allégé pendant le passage des mods aux ressources.",
                "# auto  : actif, sauf sur une petite machine (2 cœurs logiques ou moins, OpenGL < 3.2, rendu logiciel)",
                "# false : coupé ; l'image se fige alors environ 1,6 s au passage, comme avec l'écran de Forge",
                "# force : actif même sur une petite machine",
                "# La propriété système -D" + PROPERTY + "=... l'emporte sur ce fichier.",
                KEY + "=auto",
                "");
    }
}
