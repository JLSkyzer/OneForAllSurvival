package dev.oneforallsurvival.earlywindow.scene.effects;

import dev.oneforallsurvival.earlywindow.scene.Ambiance;
import dev.oneforallsurvival.earlywindow.scene.StoryClock;

/** Fonctions pures partagées par les effets, portées de compose.py. Sans GL. */
public final class EffectMath {
    private EffectMath() {
    }

    /**
     * {@code s["fire"]} de {@code state(t)} : les feux prennent à l'impact, puis faiblissent quand le jour se
     * lève. Suit le récit.
     */
    public static double fireLevel(float storyTime) {
        double p = StoryClock.storyProgress(storyTime);
        return Ambiance.sstep(StoryClock.IMPACT, StoryClock.IMPACT + 0.25, storyTime)
                * (1 - 0.8 * Ambiance.sstep(0.55, 1.0, p));
    }

    /** {@code flicker} de {@code render(t)} : vacillement des feux, sur le temps réel. */
    public static double flicker(double realTime) {
        return 0.82 + 0.1 * Math.sin(realTime * 13.1) + 0.08 * Math.sin(realTime * 31.7);
    }

    /** {@code hash01(a, b)} de compose.py : bruit déterministe dans [0, 1). */
    public static double hash01(double a, double b) {
        return frac(Math.sin(a * 12.9898 + b * 78.233) * 43758.5453);
    }

    /** Partie fractionnaire positive, comme {@code x % 1} de Python (aussi pour x négatif). */
    public static double frac(double x) {
        return x - Math.floor(x);
    }

    /** Reste positif, comme {@code a % m} de Python pour m > 0. */
    public static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }
}
