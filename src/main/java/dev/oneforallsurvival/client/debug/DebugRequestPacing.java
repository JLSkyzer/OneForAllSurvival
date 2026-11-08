package dev.oneforallsurvival.client.debug;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Quand le client doit redemander les données Lost Island : F3 ouvert, hors mode réduit, au plus quatre fois par
 * seconde, et seulement si la cible ou la seconde a changé. Logique pure.
 */
public final class DebugRequestPacing {

    /** 250 ms : quatre requêtes par seconde au plus. */
    public static final long MIN_INTERVAL_MS = 250;
    private static final long SECOND_MS = 1000;

    private boolean sentOnce;
    private long lastSentAt;
    @Nullable
    private BlockPos lastTarget;

    /** Vrai s'il faut envoyer maintenant ; l'envoi est alors compté. */
    public boolean due(boolean debugOpen, boolean reducedInfo, long nowMs, @Nullable BlockPos target) {
        if (!debugOpen || reducedInfo) {
            return false;
        }
        if (sentOnce) {
            boolean sameSecond = Math.floorDiv(nowMs, SECOND_MS) == Math.floorDiv(lastSentAt, SECOND_MS);
            if (nowMs - lastSentAt < MIN_INTERVAL_MS || (sameSecond && Objects.equals(target, lastTarget))) {
                return false;
            }
        }
        sentOnce = true;
        lastSentAt = nowMs;
        lastTarget = target == null ? null : target.immutable();
        return true;
    }
}
