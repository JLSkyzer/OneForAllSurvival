package dev.oneforallsurvival.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncubatorEggsTest {

    @Test
    void lisLesEntreesValides() {
        assertEquals(Map.of("minecraft:egg", "minecraft:chicken", "minecraft:turtle_egg", "minecraft:turtle"),
                IncubatorEggs.parse(List.of("minecraft:egg=minecraft:chicken", " minecraft:turtle_egg = minecraft:turtle ")));
    }

    @Test
    void ignoreUneEntreeMalFormee() {
        assertEquals(Map.of("minecraft:egg", "minecraft:chicken"),
                IncubatorEggs.parse(List.of("minecraft:egg=minecraft:chicken", "n'importe quoi", "=minecraft:cow",
                        "minecraft:egg=", "Majuscule=minecraft:cow", "minecraft:egg=minecraft:cow=trop")));
    }

    @Test
    void listeVideDonneRien() {
        assertTrue(IncubatorEggs.parse(List.of()).isEmpty());
    }
}
