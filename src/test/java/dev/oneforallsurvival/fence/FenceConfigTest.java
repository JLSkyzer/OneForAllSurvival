package dev.oneforallsurvival.fence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

/** Les plages de la configuration : aucune valeur permise ne doit rendre la clôture gratuite ni mortelle sans le dire. */
class FenceConfigTest {

    private static ForgeConfigSpec.ValueSpec spec(String key) {
        return FenceConfig.SPEC.getSpec().get(key);
    }

    @Test
    void safeHealthZeroEstRefuse() {
        // À 0, le choc « non mortel » laisserait 0 point de vie : il tuerait.
        assertFalse(spec("safeHealth").test(0.0));
        assertTrue(spec("safeHealth").test(0.5));
        assertTrue(spec("safeHealth").test(4.0));
    }

    @Test
    void baseEnergyZeroEstRefuse() {
        // À 0 (avec le reste à 0), le coût serait nul et la clôture tournerait sans courant.
        assertFalse(spec("baseEnergy").test(0.0));
        assertTrue(spec("baseEnergy").test(1.0));
        assertTrue(spec("baseEnergy").test(2.0));
    }

    @Test
    void leCoutMinimalResteStrictementPositif() {
        int cout = FenceRules.energyPerTick(1, 0, false, FenceConfig.MIN_BASE_ENERGY, 0.0, 0.0, 1.0, 40);
        assertTrue(cout >= 1, "coût " + cout);
    }

    @Test
    void lePlancherMinimalLaisseDeLaVie() {
        float degats = FenceRules.damageFor(4.5f, 10f, (float) FenceConfig.MIN_SAFE_HEALTH, false);
        assertTrue(4.5f - degats >= 0.5f, "dégâts " + degats);
    }
}
