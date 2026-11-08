package dev.oneforallsurvival.grid;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * La prévision de la station météo, en logique pure. Elle rejoue la règle vanilla de
 * {@code ServerLevel.advanceWeatherCycle} (1.20.1) sur les {@code horizon} ticks à venir :
 *
 * <ul>
 *   <li>tant que {@code clearTime > 0}, pluie et orage sont coupés et leurs compteurs tenus à 1 : les deux basculent
 *       ensemble au tick {@code clearTime + 1}, pluie et orage commencent ;</li>
 *   <li>sinon un compteur {@code t > 0} bascule son drapeau au tick {@code t} ; un compteur à 0 tire une nouvelle durée
 *       au tick suivant, sans bascule.</li>
 * </ul>
 *
 * <p>Après sa prochaine bascule, chaque drapeau est supposé garder sa valeur jusqu'au bout de l'horizon : vanilla tire
 * alors ses durées au hasard, au moins 12 000 ticks (3 600 pour la durée d'un orage, dont seul le début compte ici).
 * L'orage vanilla n'a d'effet que sous la pluie : l'orage est annoncé si, dans l'horizon, l'intervalle de pluie croise
 * l'intervalle d'orage. Les bornes sont incluses : une pluie qui commence exactement à {@code horizon} est annoncée.
 */
public final class WeatherForecast {

    /** Ce que la station annonce, et ce que sa face avant affiche. */
    public enum Trend implements StringRepresentable {
        /** Rien dans l'horizon : soleil. */
        STABLE,
        /** Pluie en cours ou qui commence dans l'horizon : nuage. */
        RAIN_SOON,
        /** Orage en cours ou qui éclate dans l'horizon : éclair, et signal de redstone. */
        STORM_SOON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private WeatherForecast() {}

    public static Trend trend(boolean raining, int rainTime, boolean thundering, int thunderTime, int clearTime,
            int horizon) {
        int h = Math.max(0, horizon);
        if (clearTime > 0) {
            // Ciel forcé : rien avant clearTime + 1, puis pluie et orage ensemble.
            return clearTime < h ? Trend.STORM_SOON : Trend.STABLE;
        }
        long[] rain = interval(raining, rainTime, h);
        if (rain == null) {
            return Trend.STABLE;
        }
        long[] thunder = interval(thundering, thunderTime, h);
        if (thunder != null && Math.max(rain[0], thunder[0]) <= Math.min(rain[1], thunder[1])) {
            return Trend.STORM_SOON;
        }
        return Trend.RAIN_SOON;
    }

    /**
     * Les ticks {@code [début, fin]} de {@code [0, horizon]} où le drapeau est vrai, ou {@code null} s'il ne l'est
     * jamais : vrai jusqu'à sa bascule s'il l'est maintenant, vrai dès sa bascule sinon.
     */
    private static long[] interval(boolean now, int timer, int horizon) {
        boolean toggles = timer > 0 && timer <= horizon;
        if (now) {
            return new long[] {0, toggles ? timer - 1L : horizon};
        }
        return toggles ? new long[] {timer, horizon} : null;
    }

    /**
     * L'horizon propre d'une station : {@code horizon × (1 − jitter + 2 × jitter × u)}, arrondi, au moins 1 tick.
     * {@code u} est un tirage uniforme dans {@code [0, 1)}.
     */
    public static int drawHorizon(int horizon, double jitter, double u) {
        double factor = 1.0 - jitter + 2.0 * jitter * u;
        return (int) Math.max(1, Math.round(horizon * factor));
    }
}
