package dev.oneforallsurvival.light;

/** Où le phare peut marcher : règle pure, sans monde. */
public final class LightRules {

    private LightRules() {}

    /**
     * Une tour libre voit le ciel au-dessus d'elle et se tient à {@code minAboveSea} blocs au moins au-dessus du niveau
     * de la mer (exactement à cette hauteur, elle compte).
     */
    public static boolean isFreeTower(boolean seesSky, int y, int seaLevel, int minAboveSea) {
        return seesSky && y - seaLevel >= minAboveSea;
    }

    /**
     * Le phare veut travailler quand la tour est libre et qu'il fait nuit. « Nuit » est le jugement du jeu, côté
     * serveur ({@code Level.isNight()} de Minecraft 1.20.1, lu dans les classes de la version 47.4.28) : vrai quand la
     * dimension n'a pas d'heure fixe et que {@code skyDarken} vaut 4 ou plus. {@code skyDarken} est recalculé à chaque
     * tick du monde avant l'avance de l'heure, d'après l'heure du jour, la pluie et l'orage : un changement d'heure se
     * voit donc au tick suivant. Sous la pluie seule (facteur 11/16), le plein jour reste du jour ({@code skyDarken} 3) ; sous un
     * orage plein (pluie et orage à 1), il fait nuit même à midi ({@code skyDarken} 5 : calcul à la main d'après le
     * code, jamais relevé en jeu ni en GameTest), ce qui allume le phare, souhaitable pour un phare. Dans une dimension à heure fixe (Nether, End), {@code isNight()} est
     * toujours faux : le phare ne s'y allume jamais.
     */
    public static boolean shouldShine(boolean freeTower, boolean night) {
        return freeTower && night;
    }
}
