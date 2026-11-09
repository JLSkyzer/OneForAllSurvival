package dev.oneforallsurvival.fence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * La ligne d'une clôture électrique : les clôtures contiguës par une face (en hauteur aussi) à partir de
 * l'électrificateur. Un parcours en largeur garde les plus proches quand la ligne dépasse le maximum.
 */
public final class FenceLine {

    private FenceLine() {}

    /**
     * Les blocs que {@code isFence} accepte, atteints de proche en proche depuis {@code start} par les six faces
     * (dans l'ordre de {@link Direction#values()}), au plus {@code max}. {@code start} n'est jamais compté, et chaque
     * bloc l'est une fois. Le prédicat doit répondre faux pour une position qu'il ne peut pas lire sans charger.
     */
    public static List<BlockPos> scan(BlockPos start, Predicate<BlockPos> isFence, int max) {
        List<BlockPos> line = new ArrayList<>();
        if (max <= 0) {
            return line;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos origin = start.immutable();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next) && isFence.test(next)) {
                    line.add(next);
                    if (line.size() >= max) {
                        return line;
                    }
                    queue.add(next);
                }
            }
        }
        return line;
    }
}
