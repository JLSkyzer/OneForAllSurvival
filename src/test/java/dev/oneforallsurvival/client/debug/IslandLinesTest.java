package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.client.debug.DebugFormat.Labels;
import dev.oneforallsurvival.client.debug.DebugFormat.Row;
import dev.oneforallsurvival.client.debug.DebugLines.Line;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.debug.IslandDebugInfo.Breaker;
import dev.oneforallsurvival.debug.IslandDebugInfo.Machine;
import dev.oneforallsurvival.debug.IslandDebugInfo.Meter;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IslandLinesTest {

    private static final Labels SUFFIXES = k -> k.startsWith(DebugFormat.PREFIX) ? k.substring(DebugFormat.PREFIX.length()) : k;

    private static List<String> lignes(IslandLines.Input in) {
        return IslandLines.rows(false, in, SUFFIXES).stream().map(Row::text).collect(Collectors.toList());
    }

    private static IslandDebugInfo avec(Machine machine, Meter meter, Breaker breaker) {
        return new IslandDebugInfo(true, machine, meter, breaker);
    }

    @Test
    void enModeReduitLePanneauDisparait() {
        IslandDebugInfo info = avec(new Machine(MachineState.RUNNING, true, 40f, null), null, null);
        assertTrue(IslandLines.rows(true, new IslandLines.Input(info, true, MachineState.RUNNING, "Séchoir", 0), SUFFIXES).isEmpty());
    }

    @Test
    void sansReponseLaZoneEstEnAttente() {
        assertEquals(List.of("label.zone …"), lignes(new IslandLines.Input(null, false, null, null, 0)));
    }

    @Test
    void unCitoyenVoitLEtatMaisNiUsureNiPanne() {
        IslandDebugInfo info = avec(new Machine(MachineState.FAULT, true, null, null), null, null);
        assertEquals(List.of("label.zone value.salt_water", "label.machine Séchoir · state.fault"),
                lignes(new IslandLines.Input(info, true, MachineState.FAULT, "Séchoir", 0)));
    }

    @Test
    void unIngenieurVoitUsureEtPanne() {
        IslandDebugInfo info = avec(new Machine(MachineState.FAULT, true, 37.6f, FaultType.BEARING), null, null);
        List<String> lignes = lignes(new IslandLines.Input(info, true, MachineState.FAULT, "Séchoir", 0));
        assertTrue(lignes.contains("label.wear 38 %"), lignes.toString());
        assertTrue(lignes.contains("label.failure fault.oneforallsurvival.bearing"), lignes.toString());
    }

    @Test
    void uneReponseSurUneAutreCibleNeSAffichePas() {
        IslandDebugInfo info = avec(new Machine(MachineState.RUNNING, true, 12f, null), null, null);
        List<String> lignes = lignes(new IslandLines.Input(info, false, MachineState.STOPPED, "Séchoir", 0));
        assertEquals(List.of("label.zone value.salt_water", "label.machine Séchoir · state.stopped"), lignes);
    }

    @Test
    void leCompteurMontreLIndexSeulementSIlEstLa() {
        IslandDebugInfo citoyen = avec(null, new Meter("", 60, null), null);
        assertEquals(List.of("label.zone value.salt_water", "label.subscriber value.nobody", "label.flow 42 FE/t"),
                lignes(new IslandLines.Input(citoyen, true, null, null, 41.6)));
        IslandDebugInfo ingenieur = avec(null, new Meter("Mme Essai", 60, 1234L), null);
        List<String> lignes = lignes(new IslandLines.Input(ingenieur, true, null, null, 60));
        assertTrue(lignes.contains("label.subscriber Mme Essai"), lignes.toString());
        assertTrue(lignes.contains("label.index 1234 FE"), lignes.toString());
    }

    @Test
    void leDisjoncteurDitSonEtat() {
        List<String> ferme = lignes(new IslandLines.Input(avec(null, null, new Breaker(true, false)), true, null, null, 0));
        assertTrue(ferme.contains("label.breaker value.closed"), ferme.toString());
        List<String> scelle = lignes(new IslandLines.Input(avec(null, null, new Breaker(false, true)), true, null, null, 0));
        assertTrue(scelle.contains("label.breaker value.open · value.sealed"), scelle.toString());
    }

    /** Toutes les clés que demandent le formatage et le panneau existent dans les deux langues. */
    @Test
    void toutesLesClesSontTraduites() throws IOException {
        Set<String> demandees = new HashSet<>();
        Labels labels = k -> {
            demandees.add(k);
            return k;
        };
        for (MachineState state : MachineState.values()) {
            IslandLines.rows(false, new IslandLines.Input(new IslandDebugInfo(true, null, null, null), false, state, "x", 0), labels);
        }
        IslandLines.rows(false, new IslandLines.Input(new IslandDebugInfo(false, new Machine(MachineState.FAULT, false, 1f, FaultType.FUSE),
                new Meter("", 1, 1L), new Breaker(true, true)), true, MachineState.FAULT, "x", 1), labels);
        IslandLines.rows(false, new IslandLines.Input(avec(null, new Meter("a", 1, null), new Breaker(false, false)), true, null, null, 1), labels);
        String[][] vanilla = {{"block", "Block: 1 2 3"}, {"chunk", "Chunk: 1 2 3"}, {"chunkrel", "Chunk-relative: 1 2 3"},
            {"dimension", "minecraft:overworld FC: 0"}, {"biome", "Biome: minecraft:plains"},
            {"light.client", "Client Light: 15 (15 sky, 0 block)"}, {"light.server", "Server Light: (15 sky, 0 block)"},
            {"difficulty", "Local Difficulty: 2.25 // 0.00 (Day 3)"}, {"target.block.pos", "Targeted Block: 1, 2, 3"},
            {"target.fluid.pos", "Targeted Fluid: 1, 2, 3"}, {"target.entity.header", "Targeted Entity"},
            {"facing", "Facing: north (x) (1 / 2)"}, {"facing", "Facing: south (x) (1 / 2)"},
            {"facing", "Facing: east (x) (1 / 2)"}, {"facing", "Facing: west (x) (1 / 2)"},
            {"facing", "Facing: up (x) (1 / 2)"}, {"facing", "Facing: down (x) (1 / 2)"}};
        for (String[] ligne : vanilla) {
            DebugFormat.row(new Line(ligne[0], ligne[1]), labels, (k, id) -> null);
        }
        DebugFormat.dayTimeRow(0, labels);
        DebugFormat.weatherRow(false, false, labels);
        DebugFormat.weatherRow(true, false, labels);
        DebugFormat.weatherRow(true, true, labels);
        DebugFormat.distanceRow(1, labels);
        for (String suffixe : List.of("panel.position", "panel.world", "panel.island", "panel.mods", "panel.detail",
                "panel.performance", "panel.target", "hint.normal", "hint.page", "label.memory", "label.server", "label.ping",
                "unit.megabytes", "graph.fps", "graph.tps", "graph.stats")) {
            demandees.add(DebugFormat.key(suffixe));
        }
        demandees.add("key.oneforallsurvival.f3_detail");
        demandees.add("key.categories.oneforallsurvival");
        for (String langue : List.of("fr_fr", "en_us")) {
            Path fichier = Path.of("src/main/resources/assets/oneforallsurvival/lang/" + langue + ".json");
            try (InputStreamReader lecteur = new InputStreamReader(Files.newInputStream(fichier), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(lecteur).getAsJsonObject();
                for (String cle : demandees) {
                    assertTrue(json.has(cle), langue + " : clé absente " + cle);
                }
            }
        }
    }
}
