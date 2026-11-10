package dev.oneforallsurvival.town;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Qui est sous la pomme de douche : règle géométrique pure. */
public final class ShowerRules {

    /** Les pieds se tiennent de 1 à 3 blocs sous la pomme. */
    public static final double MIN_BELOW = 1.0;
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
