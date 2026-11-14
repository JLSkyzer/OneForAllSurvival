package dev.oneforallsurvival.economy.payroll;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.EconomyConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * Les taux de salaire, en cuivres par heure, par clé : l'identifiant d'un rôle ({@code policier}, {@code medecin},
 * tout rôle créé) ou {@link #MAYOR_KEY} pour le statut de maire. La configuration est la seule source : la commande de
 * l'équipe l'écrit ({@link #set}), et une lecture la relit. Fil du serveur.
 */
public final class SalaryRates {

    /** La clé réservée du salaire du maire (aucun rôle ne peut porter cet identifiant). */
    public static final String MAYOR_KEY = "mayor";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,32}");

    /** La liste de la configuration lue en dernier (par identité) et sa lecture ; relue quand la liste change. */
    private static List<? extends String> parsedFrom;
    private static Map<String, Long> parsed = Map.of();

    private SalaryRates() {}

    /** Le taux de cette clé, en cuivres par heure ; 0 si elle n'en a pas. */
    public static long rateFor(String key) {
        if (MAYOR_KEY.equals(key)) {
            return EconomyConfig.mayorSalaryPerHour();
        }
        return roles().getOrDefault(key, 0L);
    }

    /** Tous les taux réglés, rôles dans l'ordre de la configuration puis le maire. */
    public static Map<String, Long> all() {
        Map<String, Long> out = new LinkedHashMap<>(roles());
        out.put(MAYOR_KEY, EconomyConfig.mayorSalaryPerHour());
        return Collections.unmodifiableMap(out);
    }

    /** Les entrées des rôles telles qu'écrites dans la configuration. */
    public static List<String> configured() {
        return List.copyOf(EconomyConfig.salaryPerHour());
    }

    /**
     * Règle le taux d'une clé et écrit la configuration. Pour un rôle, 0 retire son entrée (un rôle sans entrée n'est
     * pas payé) ; les entrées illisibles sont gardées telles quelles.
     *
     * @throws IllegalArgumentException si la clé n'est pas un identifiant ou si le taux sort de 0 à 1 000 000
     */
    public static void set(String key, long rate) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("clé de salaire invalide : " + key);
        }
        if (rate < 0L || rate > EconomyConfig.MAX_SALARY_PER_HOUR) {
            throw new IllegalArgumentException("taux hors limites : " + rate);
        }
        if (MAYOR_KEY.equals(key)) {
            EconomyConfig.setMayorSalaryPerHour(rate);
            return;
        }
        List<String> entries = new ArrayList<>();
        boolean placed = false;
        for (String entry : EconomyConfig.salaryPerHour()) {
            if (key.equals(keyOf(entry))) {
                if (!placed && rate > 0L) {
                    entries.add(key + "=" + rate);
                }
                placed = true;
            } else {
                entries.add(entry);
            }
        }
        if (!placed && rate > 0L) {
            entries.add(key + "=" + rate);
        }
        EconomyConfig.setSalaryPerHour(entries);
    }

    private static synchronized Map<String, Long> roles() {
        List<? extends String> current = EconomyConfig.salaryPerHour();
        if (current != parsedFrom) {
            parsed = parse(current);
            parsedFrom = current;
        }
        return parsed;
    }

    /** La clé d'une entrée {@code "cle=taux"}, ou nul si elle n'en a pas. */
    private static String keyOf(String entry) {
        int eq = entry.indexOf('=');
        return eq < 0 ? null : entry.substring(0, eq).strip();
    }

    /** Lit les entrées ; une entrée illisible (clé, taux, doublon) est ignorée avec un avertissement. */
    static Map<String, Long> parse(List<? extends String> entries) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String entry : entries) {
            String key = keyOf(entry);
            Long rate = null;
            if (key != null) {
                try {
                    rate = Long.parseLong(entry.substring(entry.indexOf('=') + 1).strip());
                } catch (NumberFormatException e) {
                    rate = null;
                }
            }
            if (key == null || !KEY.matcher(key).matches() || MAYOR_KEY.equals(key) || rate == null || rate < 0L
                    || rate > EconomyConfig.MAX_SALARY_PER_HOUR) {
                LOGGER.warn("Economie : taux de salaire illisible ignore : '{}' (attendu \"role=taux\", taux de 0 a {} ;"
                        + " le maire se regle par mayorSalaryPerHour)", entry, EconomyConfig.MAX_SALARY_PER_HOUR);
                continue;
            }
            if (out.put(key, rate) != null) {
                LOGGER.warn("Economie : taux de salaire de '{}' donne deux fois, le dernier ({}) est garde", key, rate);
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
