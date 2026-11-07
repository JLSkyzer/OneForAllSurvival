package dev.oneforallsurvival.food;

import java.math.BigInteger;

/** Les règles chiffrées de la péremption, sans aucune dépendance à Minecraft : testées en JUnit. */
public final class FoodDecay {

    /** Le quart d'heure : deux aliments datés dans le même quart d'heure ont la même date et s'empilent. */
    public static final long BUCKET_TICKS = 18000;

    private FoodDecay() {}

    /** Arrondit un temps de jeu au quart d'heure inférieur. */
    public static long roundDown(long tick) {
        return Math.floorDiv(tick, BUCKET_TICKS) * BUCKET_TICKS;
    }

    /**
     * L'état d'un aliment daté de {@code stamp} à l'instant {@code now} : frais pendant {@code freshTicks},
     * puis rassis pendant {@code staleTicks}, puis avarié. Une date dans le futur est fraîche.
     */
    public static DecayState stateAt(long stamp, long now, long freshTicks, long staleTicks) {
        if (stamp >= now) {
            return DecayState.FRESH;
        }
        long age;
        try {
            age = Math.subtractExact(now, stamp);
        } catch (ArithmeticException e) {
            age = Long.MAX_VALUE;
        }
        if (age < freshTicks) {
            return DecayState.FRESH;
        }
        // age - freshTicks ne peut pas déborder : age >= freshTicks >= 0.
        return age - freshTicks < staleTicks ? DecayState.STALE : DecayState.SPOILED;
    }

    /**
     * La date de deux piles fusionnées : moyenne pondérée par les quantités, arrondie au quart d'heure
     * inférieur. L'ordre des deux piles est sans effet.
     */
    public static long weightedStamp(long stampA, int countA, long stampB, int countB) {
        long totalCount = (long) countA + countB;
        if (totalCount <= 0) {
            return roundDown(Math.min(stampA, stampB));
        }
        BigInteger sum = BigInteger.valueOf(stampA).multiply(BigInteger.valueOf(countA))
                .add(BigInteger.valueOf(stampB).multiply(BigInteger.valueOf(countB)));
        // floorDiv par BigInteger : la moyenne de deux longs tient toujours dans un long.
        BigInteger[] qr = sum.divideAndRemainder(BigInteger.valueOf(totalCount));
        BigInteger average = qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
        return roundDown(average.longValueExact());
    }

    /**
     * La part de vie restante d'un aliment daté de {@code stamp} à l'instant {@code now}, de 1 (à la pose, ou date dans le
     * futur) à 0 (avarié, et au-delà), répartie linéairement sur {@code freshTicks + staleTicks}. Vaut 0 exactement quand
     * {@link #stateAt} rend {@link DecayState#SPOILED}.
     */
    public static double remainingFraction(long stamp, long now, long freshTicks, long staleTicks) {
        if (stamp >= now) {
            return 1.0;
        }
        long total;
        try {
            total = Math.addExact(freshTicks, staleTicks);
        } catch (ArithmeticException e) {
            total = Long.MAX_VALUE;
        }
        long age;
        try {
            age = Math.subtractExact(now, stamp);
        } catch (ArithmeticException e) {
            age = Long.MAX_VALUE;
        }
        if (age >= total) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, 1.0 - (double) age / (double) total));
    }

    /**
     * Le nombre de traits pleins d'une barre de {@code cells} traits : arrondi vers le haut, donc au moins un trait tant
     * que la vie restante n'est pas nulle.
     */
    public static int filledCells(double fraction, int cells) {
        if (!(fraction > 0.0)) {
            return 0;
        }
        return (int) Math.min(cells, Math.ceil(fraction * cells));
    }
}
