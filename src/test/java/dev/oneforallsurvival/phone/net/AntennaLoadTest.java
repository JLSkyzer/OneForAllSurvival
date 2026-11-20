package dev.oneforallsurvival.phone.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AntennaLoadTest {

    @Test
    void veilleCartesEtTrafic() {
        AntennaLoad.Breakdown b = AntennaLoad.of(40, 6, 10, 1, 9, 1, 24);
        assertEquals(new AntennaLoad.Breakdown(40, 60, 33), b);
        assertEquals(133, b.total());
    }

    @Test
    void sansCarteNiTraficLaVeilleSeule() {
        AntennaLoad.Breakdown b = AntennaLoad.of(40, 6, 0, 0, 9, 0, 24);
        assertEquals(new AntennaLoad.Breakdown(40, 0, 0), b);
        assertEquals(40, b.total());
    }

    @Test
    void lAppelEtLeVisioSeComptentChacunSonTarif() {
        assertEquals(new AntennaLoad.Breakdown(40, 0, 27), AntennaLoad.of(40, 6, 0, 3, 9, 0, 24));
        assertEquals(new AntennaLoad.Breakdown(40, 0, 48), AntennaLoad.of(40, 6, 0, 0, 9, 2, 24));
    }

    @Test
    void laSommeSeSatureSansDebordement() {
        AntennaLoad.Breakdown b = AntennaLoad.of(40, 6, Integer.MAX_VALUE, 0, 9, 0, 24);
        assertEquals(Integer.MAX_VALUE, b.clients());
        assertEquals(Integer.MAX_VALUE, b.total());
    }

    @Test
    void chaquePosteSeSatureAvantLaSomme() {
        AntennaLoad.Breakdown b = AntennaLoad.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, b.standby());
        assertEquals(Integer.MAX_VALUE, b.clients());
        assertEquals(Integer.MAX_VALUE, b.traffic());
        assertEquals(Integer.MAX_VALUE, b.total());
    }

    @Test
    void uneValeurNegativeNeFaitJamaisBaisserLaConsommation() {
        AntennaLoad.Breakdown b = AntennaLoad.of(-5, -6, -10, -1, -9, -1, -24);
        assertEquals(new AntennaLoad.Breakdown(0, 0, 0), b);
        assertEquals(0, b.total());
    }
}
