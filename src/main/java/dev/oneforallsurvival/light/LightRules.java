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
}
