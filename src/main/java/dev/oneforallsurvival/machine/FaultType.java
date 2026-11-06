package dev.oneforallsurvival.machine;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Les quatre pièces dont l'usure peut faire tomber une machine en panne. */
public enum FaultType {
    FUSE,
    WINDING,
    BEARING,
    FOULING;

    /** Clé de langue du nom de la panne. */
    public String translationKey() {
        return "fault.oneforallsurvival." + name().toLowerCase(Locale.ROOT);
    }

    /** La panne portant ce nom (casse indifférente), ou {@code null} si le nom est inconnu. */
    @Nullable
    public static FaultType byName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (FaultType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
