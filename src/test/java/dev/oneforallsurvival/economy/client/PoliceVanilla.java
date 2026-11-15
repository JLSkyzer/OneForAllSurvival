package dev.oneforallsurvival.economy.client;

import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * Les avances de la police par défaut de Minecraft 1.20.1, relevées dans {@code ascii.png} et {@code accented.png} du
 * client (largeur de l'œil la plus à droite + 1, l'espace à 4) : 6 px pour tout caractère absent de la table. Couvre
 * l'ASCII et les lettres accentuées du français ; c'est ce que mesure {@code Font.width} pour un texte sans style.
 */
final class PoliceVanilla {

    private static final Map<Character, Integer> TABLE = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry('"', 4), Map.entry('\'', 2), Map.entry('(', 4),
            Map.entry(')', 4), Map.entry('*', 4), Map.entry(',', 2), Map.entry('.', 2), Map.entry(':', 2),
            Map.entry(';', 2), Map.entry('<', 5), Map.entry('>', 5), Map.entry('@', 7), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('`', 3), Map.entry('f', 5), Map.entry('i', 2),
            Map.entry('k', 5), Map.entry('l', 3), Map.entry('t', 4), Map.entry('{', 4), Map.entry('|', 2),
            Map.entry('}', 4), Map.entry('~', 7), Map.entry('î', 4), Map.entry('ï', 4), Map.entry('œ', 10),
            Map.entry('Î', 4), Map.entry('Ï', 4), Map.entry('Œ', 10), Map.entry('…', 8));

    private PoliceVanilla() {}

    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += TABLE.getOrDefault(text.charAt(i), 6);
        }
        return w;
    }

    static int width(Component text) {
        return width(text.getString());
    }
}
