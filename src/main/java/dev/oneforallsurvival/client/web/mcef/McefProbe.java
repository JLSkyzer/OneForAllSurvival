package dev.oneforallsurvival.client.web.mcef;

import com.cinemamod.mcef.MCEF;

/** Seul accès de {@code WebAvailability} à MCEF ; appelé seulement une fois {@code isLoaded("mcef")} vérifié. */
public final class McefProbe {

    private McefProbe() {}

    public static boolean initialized() {
        try {
            return MCEF.isInitialized();
        } catch (LinkageError e) {
            return false;
        }
    }
}
