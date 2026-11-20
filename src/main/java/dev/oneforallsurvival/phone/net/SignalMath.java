package dev.oneforallsurvival.phone.net;

import net.minecraft.core.BlockPos;

/** Le signal du téléphone : les barres d'après la distance à l'antenne. Règle pure, la portée arrive en paramètre. */
public final class SignalMath {

    private SignalMath() {}

    /**
     * Barres de signal (0 à 4) à {@code distance} blocs d'une antenne de portée {@code range}. La portée est coupée en
     * quatre quarts égaux : un bloc exactement à une limite (un quart, la moitié, les trois quarts) tombe dans la
     * tranche du dessous ; la portée elle-même garde une barre, au-delà de la portée plus rien. Sans portée
     * ({@code range <= 0}) ou avec une distance négative, infinie négative ou inconnue (NaN) : 0.
     */
    public static int bars(double distance, int range) {
        if (range <= 0 || !(distance >= 0)) {
            return 0;
        }
        if (distance < range * 0.25) {
            return 4;
        }
        if (distance < range * 0.5) {
            return 3;
        }
        if (distance < range * 0.75) {
            return 2;
        }
        return distance <= range ? 1 : 0;
    }

    /** Distance entre les centres de deux blocs, en trois dimensions. */
    public static double distance(BlockPos a, BlockPos b) {
        double dx = (double) a.getX() - b.getX();
        double dy = (double) a.getY() - b.getY();
        double dz = (double) a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
