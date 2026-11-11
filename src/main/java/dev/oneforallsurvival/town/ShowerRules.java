package dev.oneforallsurvival.town;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Qui est sous la pomme de douche : règle géométrique pure. La référence {@code head} est le <b>dessous</b> du bloc de la
 * pomme (son y entier, pas son centre) : les pieds se tiennent de 2 à 3 blocs sous elle, c'est-à-dire que la pomme est
 * posée 2 ou 3 blocs au-dessus du sol où se tient le joueur (sa tête juste dessous, ou un bloc d'air entre les deux).
 */
public final class ShowerRules {

    /** Les pieds se tiennent de 2 à 3 blocs sous le dessous de la pomme, bornes incluses. */
    public static final double MIN_BELOW = 2.0;
    public static final double MAX_BELOW = 3.0;
    /** Le centre du joueur reste à 0,6 bloc au plus de l'axe vertical de la pomme. */
    public static final double MAX_OFFSET = 0.6;

    private ShowerRules() {}

    public static boolean isUnder(Vec3 head, AABB player) {
        double below = head.y - player.minY;
        if (below < MIN_BELOW || below > MAX_BELOW) {
            return false;
        }
        double dx = player.getCenter().x - head.x;
        double dz = player.getCenter().z - head.z;
        return dx * dx + dz * dz <= MAX_OFFSET * MAX_OFFSET;
    }
}
