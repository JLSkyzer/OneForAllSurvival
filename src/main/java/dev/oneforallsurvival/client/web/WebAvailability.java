package dev.oneforallsurvival.client.web;

import dev.oneforallsurvival.client.web.mcef.McefProbe;
import net.minecraftforge.fml.ModList;

/**
 * MCEF est-il là et prêt ? La classe de {@code client/web/mcef/} n'est touchée qu'après {@code isLoaded("mcef")}.
 * Propriété de développement {@code oneforallsurvival.web.forceFallback=true} : le navigateur est dit absent.
 */
public final class WebAvailability {

    private static final String FORCE_FALLBACK = "oneforallsurvival.web.forceFallback";

    private WebAvailability() {}

    public static boolean ready() {
        return failure() == null;
    }

    /** {@code ABSENT}, {@code NOT_INITIALIZED}, ou {@code null} si le navigateur est prêt. */
    public static WebFailure failure() {
        if (Boolean.getBoolean(FORCE_FALLBACK) || !ModList.get().isLoaded("mcef")) {
            return WebFailure.ABSENT;
        }
        return McefProbe.initialized() ? null : WebFailure.NOT_INITIALIZED;
    }
}
