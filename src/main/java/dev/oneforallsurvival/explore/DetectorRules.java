package dev.oneforallsurvival.explore;

/** Les règles du détecteur de métaux, sans Minecraft : cadence et hauteur du bip selon la distance à la cache. */
public final class DetectorRules {

    /** Intervalle entre deux bips au bord de la portée, en ticks. */
    public static final int SLOWEST = 40;
    /** Intervalle entre deux bips au contact, en ticks. */
    public static final int FASTEST = 2;
    public static final float PITCH_FAR = 0.5F;
    public static final float PITCH_NEAR = 2.0F;

    private DetectorRules() {}

    /** Ticks entre deux bips : -1 au-delà de la portée, sinon de 2 (contact) à 40 (bord), linéairement. */
    public static int beepInterval(double distance, double range) {
        if (distance > range) {
            return -1;
        }
        long interval = Math.round(FASTEST + (SLOWEST - FASTEST) * distance / range);
        return (int) Math.max(FASTEST, Math.min(SLOWEST, interval));
    }

    /** Hauteur du bip : 0,5 au bord de la portée, 2,0 au contact. */
    public static float pitch(double distance, double range) {
        double ratio = Math.max(0.0, Math.min(1.0, distance / range));
        return (float) (PITCH_NEAR - (PITCH_NEAR - PITCH_FAR) * ratio);
    }
}
