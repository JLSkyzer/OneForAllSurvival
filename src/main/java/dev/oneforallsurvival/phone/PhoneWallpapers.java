package dev.oneforallsurvival.phone;

import java.util.List;

/** Les fonds d'écran du téléphone : leurs identifiants, validés par le serveur. */
public final class PhoneWallpapers {

    public static final String DEFAULT = "aube";
    public static final List<String> IDS = List.of("aube", "lagon", "jungle", "nuit");

    private PhoneWallpapers() {}

    public static boolean isValid(String id) {
        return id != null && IDS.contains(id);
    }
}
