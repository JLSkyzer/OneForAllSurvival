package dev.oneforallsurvival.client.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.regex.Pattern;

/**
 * Construit un appel de fonction JavaScript à partir d'arguments JSON. Gson échappe {@code <},
 * {@code >}, {@code &}, {@code =} et {@code '} (jamais {@code disableHtmlEscaping}) ; U+2028 et U+2029
 * sont de plus échappés ici, quoi que fasse la version de Gson.
 */
public final class WebScript {

    private static final Pattern FUNCTION = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]*");
    private static final Gson GSON = new Gson();

    private WebScript() {}

    public static String call(String function, JsonElement... args) {
        if (function == null || !FUNCTION.matcher(function).matches()) {
            throw new IllegalArgumentException("nom de fonction refusé : " + function);
        }
        StringBuilder sb = new StringBuilder(function).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(GSON.toJson(args[i]));
        }
        return sb.append(')').toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
