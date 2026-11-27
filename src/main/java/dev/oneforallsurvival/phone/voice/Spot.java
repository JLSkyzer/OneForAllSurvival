package dev.oneforallsurvival.phone.voice;

import java.util.Objects;

/** Où se tient un joueur : sa dimension (nom de la clé, {@code minecraft:overworld}) et sa position. */
public record Spot(String dimension, double x, double y, double z) {

    public Spot {
        Objects.requireNonNull(dimension, "dimension");
    }

    /** Est-il à {@code blocks} blocs de {@code other} au plus (borne comprise), dans la même dimension ? */
    public boolean within(Spot other, double blocks) {
        if (!dimension.equals(other.dimension)) {
            return false;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz <= blocks * blocks;
    }
}
