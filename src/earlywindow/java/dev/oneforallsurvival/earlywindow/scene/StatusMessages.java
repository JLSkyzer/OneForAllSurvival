package dev.oneforallsurvival.earlywindow.scene;

import dev.oneforallsurvival.earlywindow.scene.ProgressModel.Phase;
import java.util.regex.Pattern;

/**
 * Messages d'état thématiques Lost Island, avec l'étape réelle en détail. Textes lus dans {@link Texts#current()}
 * (fichiers de langue du jar de service, français intégré par défaut).
 */
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
        return of(Texts.current(), phase, rawForgeLabel, current, steps);
    }

    /** Comme {@link #of(Phase, String, int, int)}, avec des textes donnés. */
    public static Status of(Texts t, Phase phase, String rawForgeLabel, int current, int steps) {
        return switch (phase) {
            case FORGE_START -> new Status(t.get(Texts.STATUS_FORGE_START), t.get(Texts.DETAIL_FORGE_START));
            case MOD_DISCOVERY -> new Status(t.get(Texts.STATUS_MOD_DISCOVERY), t.get(Texts.DETAIL_MOD_DISCOVERY));
            case MOD_LOADING -> new Status(t.get(Texts.STATUS_MOD_LOADING),
                    detailMods(t.get(Texts.DETAIL_MODS), rawForgeLabel, current, steps));
            case REGISTRIES -> new Status(t.get(Texts.STATUS_REGISTRIES), t.get(Texts.DETAIL_REGISTRIES));
            case RESOURCES -> new Status(t.get(Texts.STATUS_RESOURCES), t.get(Texts.DETAIL_RESOURCES));
            case DONE -> new Status(t.get(Texts.STATUS_DONE), "");
        };
    }

    private static String detailMods(String mods, String label, int current, int steps) {
        // Barre sans étapes (« EARLY », « Loading mods ») : pas de compte « 0/0 ».
        String compte = steps > 0 ? mods + " " + current + "/" + steps : mods;
        String nom = label == null ? "" : label.trim();
        return NOM_DE_MOD.matcher(nom).matches() ? compte + " : " + nom : compte;
    }
}
