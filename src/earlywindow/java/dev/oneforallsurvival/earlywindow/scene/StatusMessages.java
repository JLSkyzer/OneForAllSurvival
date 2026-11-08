package dev.oneforallsurvival.earlywindow.scene;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import java.util.regex.Pattern;

/** Messages d'état thématiques Lost Island, avec l'étape réelle en détail. Logique pure. */
public final class StatusMessages {
    public record Status(String theme, String detail) {
        /**
         * Ligne affichée sous la barre : le thème, puis l'étape réelle entre parenthèses (table de la spec).
         * Sans détail (« Bienvenue sur Lost Island. »), le thème seul.
         */
        public String line() {
            String t = theme == null ? "" : theme;
            String d = detail == null ? "" : detail.trim();
            return d.isEmpty() ? t : t + " (" + d + ")";
        }
    }

    // Un libellé de Forge n'est un nom de mod que s'il est un identifiant seul (« create »).
    // Les libellés relevés (« Loading mods », « Mod Gather working ») n'en portent pas.
    private static final Pattern NOM_DE_MOD = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private StatusMessages() { }

    /**
     * Phase déduite du nom de la barre de Forge, puis, à défaut, d'un message ; {@code null} si
     * rien ne la fait reconnaître (l'appelant garde alors la phase courante).
     * Noms relevés en tâche 0 : « EARLY » (libellé « Loading mods »), « Mod Gather »,
     * « State transition LOAD_REGISTRIES running » ; messages « Forge loading … »,
     * « REGISTERING … ».
     */
    public static Phase phaseOf(String meterName, String message) {
        if (meterName != null) {
            if (meterName.startsWith("State transition")) {
                return Phase.REGISTRIES;
            }
            // Dans le relevé, la barre EARLY n'apparaît qu'au début du chargement des mods.
            if (meterName.equals("EARLY") || meterName.startsWith("Mod Gather")) {
                return Phase.MOD_LOADING;
            }
        }
        if (message != null) {
            if (message.startsWith("REGISTERING")) {
                return Phase.REGISTRIES;
            }
            if (message.startsWith("Forge loading") || message.startsWith("Forge version")) {
                return Phase.FORGE_START;
            }
        }
        return null;
    }

    /** Message de la phase ; {@code rawForgeLabel} sert au nom du mod en cours de chargement. */
    public static Status of(Phase phase, String rawForgeLabel, int current, int steps) {
        return switch (phase) {
            case FORGE_START -> new Status("Les moteurs toussent…", "démarrage");
            case MOD_DISCOVERY -> new Status("Inventaire de la soute…", "recherche des mods");
            case MOD_LOADING ->
                    new Status("Tri des caisses de l'épave…", detailMods(rawForgeLabel, current, steps));
            case REGISTRIES -> new Status("Le camp s'organise…", "registres");
            case RESOURCES -> new Status("Exploration du littoral…", "textures");
            case DONE -> new Status("Bienvenue sur Lost Island.", "");
        };
    }

    private static String detailMods(String label, int current, int steps) {
        String compte = "mods " + current + "/" + steps;
        String nom = label == null ? "" : label.trim();
        return NOM_DE_MOD.matcher(nom).matches() ? compte + " : " + nom : compte;
    }
}
