package dev.oneforallsurvival.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.grid.WeatherForecast.Trend;
import org.junit.jupiter.api.Test;

/**
 * La prévision de la station météo, sans Minecraft. Les compteurs suivent la règle vanilla de
 * {@code ServerLevel.advanceWeatherCycle} : un compteur à {@code t > 0} bascule son drapeau dans {@code t} ticks ;
 * pendant un ciel forcé clair, pluie et orage sont coupés et leurs compteurs tenus à 1.
 */
class WeatherForecastTest {

    private static final int H = 6000;

    private static Trend trend(boolean raining, int rainTime, boolean thundering, int thunderTime, int clearTime) {
        return WeatherForecast.trend(raining, rainTime, thundering, thunderTime, clearTime, H);
    }

    // --- Ciel clair forcé (/weather clear) ---

    @Test
    void cielClairForceAuDelaDeLHorizonStable() {
        // /weather clear 20000 : setWeatherParameters(20000, 0, false, false), puis compteurs tenus à 1.
        assertEquals(Trend.STABLE, trend(false, 1, false, 1, 20000));
        // Même avec des drapeaux ou des compteurs qui annonceraient la pluie : le ciel forcé l'emporte.
        assertEquals(Trend.STABLE, trend(true, 50, true, 50, 20000));
    }

    @Test
    void finDuCielClairDansLHorizonOrage() {
        // Vanilla : au tick qui suit la fin du ciel forcé, les deux compteurs (tenus à 1) basculent ensemble,
        // pluie et orage commencent. /weather clear 600 annonce donc un orage à 601 ticks.
        assertEquals(Trend.STORM_SOON, trend(false, 1, false, 1, 600));
    }

    @Test
    void bornesDuCielClair() {
        // Orage au tick clearTime + 1 : dans l'horizon tant que clearTime + 1 <= H.
        assertEquals(Trend.STORM_SOON, trend(false, 1, false, 1, H - 1));
        assertEquals(Trend.STABLE, trend(false, 1, false, 1, H));
    }

    // --- Pluie ---

    @Test
    void pluieDansLHorizon() {
        assertEquals(Trend.RAIN_SOON, trend(false, 3000, false, 100_000, 0));
    }

    @Test
    void pluieAuDelaDeLHorizonStable() {
        assertEquals(Trend.STABLE, trend(false, 9000, false, 100_000, 0));
    }

    @Test
    void pluieEnCours() {
        assertEquals(Trend.RAIN_SOON, trend(true, 8000, false, 100_000, 0));
        // Compteur à 0 sous la pluie : vanilla tire une durée d'au moins 12 000 ticks au tick suivant.
        assertEquals(Trend.RAIN_SOON, trend(true, 0, false, 0, 0));
    }

    @Test
    void sansPluieCompteurAZeroStable() {
        // Compteur à 0 sans pluie : vanilla tire un délai d'au moins 12 000 ticks, rien dans l'horizon.
        assertEquals(Trend.STABLE, trend(false, 0, false, 0, 0));
    }

    @Test
    void bornesDeLaPluie() {
        assertEquals(Trend.RAIN_SOON, trend(false, H, false, 100_000, 0));
        assertEquals(Trend.STABLE, trend(false, H + 1, false, 100_000, 0));
    }

    // --- Orage ---

    @Test
    void orageEnCours() {
        // /weather thunder 600 : setWeatherParameters(0, 600, true, true).
        assertEquals(Trend.STORM_SOON, trend(true, 600, true, 600, 0));
    }

    @Test
    void orageSansPluieNeCompteQuAvecLaPluie() {
        // L'orage vanilla n'a d'effet que sous la pluie.
        assertEquals(Trend.STABLE, trend(false, 100_000, true, 4000, 0));
        // Mais si la pluie arrive pendant l'orage, l'orage éclate avec elle.
        assertEquals(Trend.STORM_SOON, trend(false, 2000, true, 4000, 0));
        // Pluie qui arrive après la fin de l'orage : pluie seulement.
        assertEquals(Trend.RAIN_SOON, trend(false, 4000, true, 4000, 0));
    }

    @Test
    void oragePrevuPendantUnePluie() {
        assertEquals(Trend.STORM_SOON, trend(true, 5000, false, 3000, 0));
    }

    @Test
    void oragePrevuApresLaFinDeLaPluiePluieSeulement() {
        // /weather rain 600 : setWeatherParameters(0, 600, true, false). La pluie s'arrête au tick 600, l'orage
        // commence au même tick : ils ne se croisent pas.
        assertEquals(Trend.RAIN_SOON, trend(true, 600, false, 600, 0));
        assertEquals(Trend.RAIN_SOON, trend(true, 600, false, 2000, 0));
        // Un tick de recouvrement suffit.
        assertEquals(Trend.STORM_SOON, trend(true, 600, false, 599, 0));
    }

    @Test
    void orageEtPluiePrevusEnsemble() {
        assertEquals(Trend.STORM_SOON, trend(false, 3000, false, 3500, 0));
        assertEquals(Trend.RAIN_SOON, trend(false, 3000, false, 7000, 0));
    }

    @Test
    void bornesDeLOrage() {
        assertEquals(Trend.STORM_SOON, trend(true, 0, false, H, 0));
        assertEquals(Trend.RAIN_SOON, trend(true, 0, false, H + 1, 0));
        assertEquals(Trend.STORM_SOON, trend(false, H, false, H, 0));
        assertEquals(Trend.STABLE, trend(false, H + 1, false, H, 0));
    }

    // --- Horizon propre à chaque station ---

    @Test
    void lHorizonTireResteDansSesBornes() {
        assertEquals(4800, WeatherForecast.drawHorizon(H, 0.2, 0.0));
        assertEquals(6000, WeatherForecast.drawHorizon(H, 0.2, 0.5));
        int haut = WeatherForecast.drawHorizon(H, 0.2, Math.nextDown(1.0));
        assertTrue(haut <= 7200 && haut >= 7199, "borne haute : " + haut);
        assertEquals(H, WeatherForecast.drawHorizon(H, 0.0, 0.73));
        assertTrue(WeatherForecast.drawHorizon(1, 0.9, 0.0) >= 1, "un horizon reste d'au moins un tick");
    }
}
