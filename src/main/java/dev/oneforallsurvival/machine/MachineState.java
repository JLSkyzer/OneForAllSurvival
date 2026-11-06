package dev.oneforallsurvival.machine;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** L'état visible d'une machine électrique, porté par son BlockState. */
public enum MachineState implements StringRepresentable {
    STOPPED,
    RUNNING,
    NO_POWER,
    FAULT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
