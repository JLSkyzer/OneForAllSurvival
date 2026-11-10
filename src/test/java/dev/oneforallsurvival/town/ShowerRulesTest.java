package dev.oneforallsurvival.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ShowerRulesTest {

    private static final Vec3 TETE = new Vec3(0.5, 10.0, 0.5);

    /** Un joueur de 0,6 x 1,8 dont les pieds sont en {@code y}, centré en ({@code x}, 0,5). */
    private static AABB joueur(double x, double y) {
        return new AABB(x - 0.3, y, 0.2, x + 0.3, y + 1.8, 0.8);
    }

    /** Un joueur décalé de ({@code dx}, {@code dz}) par rapport à l'axe de la pomme. */
    private static AABB joueurDecale(double dx, double dz, double y) {
        double x = TETE.x + dx;
        double z = TETE.z + dz;
        return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    @Test
    void sousLaPommeAUnBloc() {
        assertTrue(ShowerRules.isUnder(TETE, joueur(0.5, 9.0)));
    }

    @Test
    void aTroisBlocs() {
        assertTrue(ShowerRules.isUnder(TETE, joueur(0.5, 7.0)));
    }

    @Test
    void pasAQuatre() {
        assertFalse(ShowerRules.isUnder(TETE, joueur(0.5, 6.0)));
    }

    @Test
    void pasAColeDeLAxe() {
        assertTrue(ShowerRules.isUnder(TETE, joueur(1.0, 8.0)));
        assertFalse(ShowerRules.isUnder(TETE, joueur(1.2, 8.0)));
    }

    /** Le seuil est un disque, pas un carré : 0,4 de chaque côté (0,57 en diagonale) passe, 0,5 (0,71) non. */
    @Test
    void enDiagonaleLeSeuilEstUnDisque() {
        assertTrue(ShowerRules.isUnder(TETE, joueurDecale(0.4, 0.4, 8.0)));
        assertFalse(ShowerRules.isUnder(TETE, joueurDecale(0.5, 0.5, 8.0)));
        assertFalse(ShowerRules.isUnder(TETE, joueurDecale(-0.5, 0.5, 8.0)));
    }

    @Test
    void pasAuDessus() {
        assertFalse(ShowerRules.isUnder(TETE, joueur(0.5, 9.5)));
        assertFalse(ShowerRules.isUnder(TETE, joueur(0.5, 11.0)));
    }
}
