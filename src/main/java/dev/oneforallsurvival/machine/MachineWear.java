package dev.oneforallsurvival.machine;

/** Calculs purs de l'usure des machines, séparés du block entity pour être testés sans Minecraft. */
public final class MachineWear {

    /** Sous ce seuil (en %), le rendement baisse. */
    public static final double REDUCED_BELOW = 50;
    /** Sous ce seuil (en %), la machine fume et crache des étincelles. */
    public static final double SMOKE_BELOW = 20;
    /** État d'une machine neuve ou réparée. */
    public static final double FULL = 100;

    private MachineWear() {}

    /**
     * Perte d'état (en points sur 100) pour un tick de travail payé. Le milieu côtier la multiplie,
     * sauf si la machine est protégée.
     */
    public static double wearPerTick(double lifetimeTicks, boolean coastal, boolean isProtected,
            double coastalMultiplier) {
        double base = FULL / lifetimeTicks;
        return coastal && !isProtected ? base * coastalMultiplier : base;
    }

    /** Rendement : plein jusqu'à {@link #REDUCED_BELOW} inclus, réduit en dessous. */
    public static double efficiency(double condition, double reducedEfficiency) {
        return condition >= REDUCED_BELOW ? 1.0 : reducedEfficiency;
    }
}
