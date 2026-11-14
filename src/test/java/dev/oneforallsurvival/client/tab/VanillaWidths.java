package dev.oneforallsurvival.client.tab;

import java.util.Map;

/**
 * Avances de la police par défaut de Minecraft 1.20.1 ({@code ascii.png} et caractères accentués), pour les seuls
 * caractères dont les essais ont besoin : 6 px pour la plupart, sauf ceux de la table. {@link TabLayoutTest} vérifie
 * d'abord que cette table redonne les largeurs de référence de la spécification.
 */
final class VanillaWidths implements TabLayout.Measure {

    static final VanillaWidths INSTANCE = new VanillaWidths();

    private static final Map<Character, Integer> NARROW = Map.ofEntries(
            Map.entry('i', 2), Map.entry('l', 3), Map.entry('t', 4), Map.entry('I', 4), Map.entry('f', 5),
            Map.entry('k', 5), Map.entry(' ', 4), Map.entry('.', 2), Map.entry(',', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('!', 2), Map.entry('\'', 2), Map.entry('|', 2), Map.entry('·', 2),
            Map.entry('…', 8), Map.entry('î', 4));

    @Override
    public int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += NARROW.getOrDefault(text.charAt(i), 6);
        }
        return w;
    }

    @Override
    public int boldWidth(String text) {
        return width(text) + text.length();
    }
}
