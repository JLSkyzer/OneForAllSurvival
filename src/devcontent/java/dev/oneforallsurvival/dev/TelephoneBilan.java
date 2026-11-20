package dev.oneforallsurvival.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Les contrôles du bilan de {@link TelephoneShots}, sans Minecraft : seuils de performance du plan et étapes attendues
 * de chaque mode. Un seuil manqué est un défaut à écrire, jamais à arrondir. {@link #main} rejoue un bilan de
 * performance à partir de chiffres relevés au journal (contrôle des mutations sans relancer le jeu).
 */
final class TelephoneBilan {

    /** Images par seconde pendant l'ouverture : au moins 80 % de celles d'avant. */
    static final double SEUIL_PENDANT = 0.80;
    /** Images par seconde après la fermeture : au moins 90 % de celles d'avant. */
    static final double SEUIL_APRES = 0.90;
    /** Tas après GC, après la fermeture : au plus 64 Mo de plus qu'avant l'ouverture. */
    static final double TAS_MAX_MO = 64;
    /** Tas après GC sur cinq cycles d'ouverture : au plus 20 Mo de plus par ouverture. */
    static final double PAR_OUVERTURE_MAX_MO = 20;

    private TelephoneBilan() {}

    /**
     * Les seuils manqués (vide si tout tient). Les tas sont en Mo, après GC ; {@code cycles} donne le tas après chacun
     * des cinq cycles, la croissance par ouverture se compte depuis {@code tasApres}, en moyenne et pour chaque
     * ouverture.
     */
    static List<String> perf(double avant, double pendant, double apres, double tasAvant, double tasApres,
            double[] cycles) {
        List<String> manques = new ArrayList<>();
        if (!(avant > 0)) {
            manques.add("seuil-mesure : aucune image comptée avant l'ouverture");
            return manques;
        }
        if (pendant < SEUIL_PENDANT * avant) {
            manques.add(String.format(Locale.ROOT, "seuil-pendant : %.1f img/s pendant, %.1f %% d'avant (%.1f), "
                    + "au moins %.0f %% attendu", pendant, 100 * pendant / avant, avant, 100 * SEUIL_PENDANT));
        }
        if (apres < SEUIL_APRES * avant) {
            manques.add(String.format(Locale.ROOT, "seuil-apres : %.1f img/s après, %.1f %% d'avant (%.1f), "
                    + "au moins %.0f %% attendu", apres, 100 * apres / avant, avant, 100 * SEUIL_APRES));
        }
        if (tasApres - tasAvant > TAS_MAX_MO) {
            manques.add(String.format(Locale.ROOT, "seuil-tas : %+.1f Mo après GC (%.1f puis %.1f), au plus +%.0f Mo",
                    tasApres - tasAvant, tasAvant, tasApres, TAS_MAX_MO));
        }
        if (cycles == null || cycles.length != 5) {
            manques.add("seuil-cycles : " + (cycles == null ? 0 : cycles.length) + " cycle(s) relevé(s), 5 attendus");
        } else {
            double parOuverture = (cycles[4] - tasApres) / 5;
            if (parOuverture > PAR_OUVERTURE_MAX_MO) {
                manques.add(String.format(Locale.ROOT, "seuil-cycles : %+.1f Mo par ouverture sur cinq (%.1f puis "
                        + "%.1f), au plus +%.0f Mo", parOuverture, tasApres, cycles[4], PAR_OUVERTURE_MAX_MO));
            }
            // Chaque ouverture aussi : un saut à une seule ouverture se perdrait dans la moyenne.
            for (int i = 0; i < 5; i++) {
                double avantCycle = i == 0 ? tasApres : cycles[i - 1];
                double pas = cycles[i] - avantCycle;
                if (pas > PAR_OUVERTURE_MAX_MO) {
                    manques.add(String.format(Locale.ROOT, "seuil-ouverture : %+.1f Mo à l'ouverture %d (%.1f puis "
                            + "%.1f), au plus +%.0f Mo", pas, i + 1, avantCycle, cycles[i], PAR_OUVERTURE_MAX_MO));
                }
            }
        }
        return manques;
    }

    /** Les étapes qu'un mode doit avoir réussies, quel que soit le détail de sa composition. */
    static List<String> attendues(String mode, boolean serveur, boolean webAttendu) {
        return switch (mode) {
            case "perf" -> List.of("perf-avant", "perf-tas-avant", "perf-pendant", "perf-apres", "perf-tas-apres",
                    "perf-cycle-5-tas", "perf-bilan");
            case "webdisplays" -> List.of("wd-pad-ouvert", "wd-pad-en-main-gauche", "wd-telephone-deverrouille",
                    "wd-reglages", "wd-pad-encore", "wd-bilan-pont");
            default -> {
                List<String> l = new ArrayList<>(List.of("ouverture-1", "confirmation-au-clavier", "reglages",
                        "mode-avion", "modification-du-code", "ouverture-2", "faux-5-reponse", "blocage",
                        "telephone-hors-main", "reset-equipe-ferme", "ouverture-apres-reset", "bilan-avis-final"));
                if (webAttendu) {
                    l.add("adresse-non-permise");
                }
                if (!serveur) {
                    l.addAll(List.of("batterie-baisse", "brancher", "batterie-remontee", "fin-du-blocage",
                            "effacement-ferme", "reouverture-choix-du-code"));
                    if (webAttendu) {
                        l.addAll(List.of("repli-load-error", "repli-no-ready"));
                    }
                }
                yield l;
            }
        };
    }

    /** Les étapes attendues absentes des étapes réussies. */
    static List<String> manquantes(List<String> attendues, Set<String> reussies) {
        return attendues.stream().filter(e -> !reussies.contains(e)).toList();
    }

    /**
     * Relecture : {@code perf <avant> <pendant> <apres> <tas_avant> <tas_apres> <c1> <c2> <c3> <c4> <c5>} (img/s, Mo).
     * Écrit le verdict ; code de sortie 1 si un seuil manque.
     */
    public static void main(String[] args) {
        if (args.length != 11 || !"perf".equals(args[0])) {
            System.out.println("usage : perf avant pendant apres tas_avant tas_apres c1 c2 c3 c4 c5");
            System.exit(2);
        }
        double[] v = new double[10];
        for (int i = 0; i < 10; i++) {
            v[i] = Double.parseDouble(args[i + 1]);
        }
        List<String> manques = perf(v[0], v[1], v[2], v[3], v[4], new double[] {v[5], v[6], v[7], v[8], v[9]});
        System.out.println(manques.isEmpty() ? "perf : tous les seuils tiennent" : "perf : " + manques);
        System.exit(manques.isEmpty() ? 0 : 1);
    }
}
