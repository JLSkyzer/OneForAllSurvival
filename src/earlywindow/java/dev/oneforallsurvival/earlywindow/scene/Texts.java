package dev.oneforallsurvival.earlywindow.scene;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Textes affichés par la scène (messages d'état, repères de la barre, devise), lus dans les fichiers de langue du
 * jar de service ({@code lostisland/lang/<code>.json}) : ce jar n'a pas les traductions de Minecraft (spec,
 * contraintes du projet ; ruling 32).
 *
 * <p>Choix de la langue : celle du jeu ({@code lang:} de {@code options.txt}) si nous avons son fichier, sinon
 * {@code en_us}, sinon les textes français intégrés ({@link #BUILT_IN}). Une clé absente d'un fichier se lit dans
 * le suivant de cette chaîne. Logique pure, hors {@link #install}.
 */
public final class Texts {
    public static final String STATUS_FORGE_START = "lostisland.loading.status.forge_start";
    public static final String DETAIL_FORGE_START = "lostisland.loading.detail.forge_start";
    public static final String STATUS_MOD_DISCOVERY = "lostisland.loading.status.mod_discovery";
    public static final String DETAIL_MOD_DISCOVERY = "lostisland.loading.detail.mod_discovery";
    public static final String STATUS_MOD_LOADING = "lostisland.loading.status.mod_loading";
    public static final String DETAIL_MODS = "lostisland.loading.detail.mods";
    public static final String STATUS_REGISTRIES = "lostisland.loading.status.registries";
    public static final String DETAIL_REGISTRIES = "lostisland.loading.detail.registries";
    public static final String STATUS_RESOURCES = "lostisland.loading.status.resources";
    public static final String DETAIL_RESOURCES = "lostisland.loading.detail.resources";
    public static final String STATUS_DONE = "lostisland.loading.status.done";
    public static final String UI_START = "lostisland.loading.ui.start";
    public static final String UI_END = "lostisland.loading.ui.end";
    public static final String UI_MOTTO = "lostisland.loading.ui.motto";

    public static final String FALLBACK = "en_us";
    public static final String DIRECTORY = "lostisland/lang/";

    /** Textes français de la spec (table validée par Killian), dernier repli si aucun fichier ne se lit. */
    public static final Map<String, String> BUILT_IN = builtIn();

    // Code de langue de Minecraft (« fr_fr », « en_us », « zh_cn ») : rien d'autre n'entre dans un chemin.
    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}_[a-z0-9]{2,4}");

    private static volatile Texts current = new Texts("fr_fr (intégré)", List.of(BUILT_IN));

    private final String origin;
    private final List<Map<String, String>> chain;

    private Texts(String origin, List<Map<String, String>> chain) {
        this.origin = origin;
        this.chain = chain;
    }

    private static Map<String, String> builtIn() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(STATUS_FORGE_START, "Les moteurs toussent…");
        m.put(DETAIL_FORGE_START, "démarrage");
        m.put(STATUS_MOD_DISCOVERY, "Inventaire de la soute…");
        m.put(DETAIL_MOD_DISCOVERY, "recherche des mods");
        m.put(STATUS_MOD_LOADING, "Tri des caisses de l'épave…");
        m.put(DETAIL_MODS, "mods");
        m.put(STATUS_REGISTRIES, "Le camp s'organise…");
        m.put(DETAIL_REGISTRIES, "registres");
        m.put(STATUS_RESOURCES, "Exploration du littoral…");
        m.put(DETAIL_RESOURCES, "textures");
        m.put(STATUS_DONE, "Bienvenue sur Lost Island.");
        m.put(UI_START, "LOST ISLAND RP");
        m.put(UI_END, "ÎLE INCONNUE");
        m.put(UI_MOTTO, "Là où tout recommence.");
        return Map.copyOf(m);
    }

    /** Textes en usage (français intégré tant que {@link #install} n'a pas été appelé). */
    public static Texts current() {
        return current;
    }

    /** Remplace les textes en usage ; appelé une fois, à la création de la scène. */
    public static void install(Texts texts) {
        current = texts;
    }

    /** Texte d'une clé : langue choisie, puis {@code en_us}, puis français intégré ; la clé elle-même sinon. */
    public String get(String key) {
        for (Map<String, String> m : chain) {
            String v = m.get(key);
            if (v != null) {
                return v;
            }
        }
        return key;
    }

    /** D'où viennent les textes (« fr_fr », « en_us (repli de de_de) », « fr_fr (intégré) »), pour le journal. */
    public String origin() {
        return origin;
    }

    /**
     * Valeur de {@code lang:} dans {@code options.txt} du dossier du jeu, en minuscules ; {@code null} si le
     * fichier ou la ligne manque, ou s'il est illisible.
     */
    public static String gameLanguage(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        try {
            Path options = gameDir.resolve("options.txt");
            if (!Files.isRegularFile(options)) {
                return null;
            }
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("lang:")) {
                    String code = line.substring("lang:".length()).trim().toLowerCase(Locale.ROOT);
                    return code.isEmpty() ? null : code;
                }
            }
        } catch (IOException | RuntimeException e) {
            // options.txt illisible : langue inconnue.
        }
        return null;
    }

    /**
     * Ordre de lecture des fichiers : la langue du jeu (si c'est un code valide), puis {@code en_us}, sans doublon.
     * Le français intégré vient toujours après.
     */
    public static List<String> candidates(String gameLanguage) {
        List<String> out = new ArrayList<>();
        if (gameLanguage != null && CODE.matcher(gameLanguage).matches()) {
            out.add(gameLanguage);
        }
        if (!out.contains(FALLBACK)) {
            out.add(FALLBACK);
        }
        return out;
    }

    /**
     * Charge les textes pour la langue du jeu. {@code opener} ouvre un chemin du jar de service
     * ({@code lostisland/lang/fr_fr.json}) ou renvoie {@code null}. Un fichier absent ou illisible est sauté.
     */
    public static Texts load(String gameLanguage, Function<String, InputStream> opener, Consumer<String> log) {
        List<Map<String, String>> chain = new ArrayList<>();
        String origin = null;
        for (String code : candidates(gameLanguage)) {
            Map<String, String> m = read(DIRECTORY + code + ".json", opener, log);
            if (m == null) {
                continue;
            }
            chain.add(m);
            if (origin == null) {
                origin = code.equals(gameLanguage) || gameLanguage == null ? code
                        : code + " (repli de " + gameLanguage + ")";
            }
        }
        chain.add(BUILT_IN);
        return new Texts(origin == null ? "fr_fr (intégré)" : origin, List.copyOf(chain));
    }

    /** Fichier de langue à plat ({@code {"clé": "texte"}}) ; {@code null} s'il manque ou ne se lit pas. */
    static Map<String, String> read(String path, Function<String, InputStream> opener, Consumer<String> log) {
        try (InputStream in = opener.apply(path)) {
            if (in == null) {
                return null;
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            log.accept("langue : " + path + " illisible (" + e + ")");
            return null;
        }
    }

    /** Lit un fichier de langue ; seules les valeurs texte sont gardées. */
    public static Map<String, String> parse(Reader json) {
        JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                m.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return m;
    }
}
