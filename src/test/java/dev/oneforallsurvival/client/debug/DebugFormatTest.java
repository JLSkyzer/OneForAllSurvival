package dev.oneforallsurvival.client.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.client.debug.DebugFormat.Row;
import dev.oneforallsurvival.client.debug.DebugFormat.Segment;
import dev.oneforallsurvival.client.debug.DebugFormat.Tone;
import dev.oneforallsurvival.client.debug.DebugLines.Line;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DebugFormatTest {

    private static Row ligne(String key, String text) {
        return DebugFormat.row(new Line(key, text), k -> k.substring(DebugFormat.PREFIX.length()), (k, id) -> null);
    }

    @Test
    void lesCoordonneesSontColoreesParAxe() {
        Row row = ligne("xyz", "XYZ: 1284.512 / 72.00000 / -903.209");
        assertEquals("X 1284.5 · Y 72.0 · Z -903.2", row.text());
        assertEquals(Tone.X, row.segments().get(0).tone());
        assertEquals(Tone.Y, row.segments().get(2).tone());
        assertEquals(Tone.Z, row.segments().get(4).tone());
    }

    @Test
    void lesLignesConnuesPortentLeurLibelleTraduit() {
        assertEquals("label.block 12 64 -6", ligne("block", "Block: 12 64 -6 [12 0 10]").text());
        assertEquals("label.chunk 0 4 0", ligne("chunk", "Chunk: 0 4 0 [0 0 in r.0.0.mca]").text());
        assertEquals("label.facing direction.north (-1.2° / 3.4°)", ligne("facing", "Facing: north (Towards negative Z) (-1.2 / 3.4)").text());
        assertEquals("label.light 15 (label.sky 15, label.block_light 0)", ligne("light.client", "Client Light: 15 (15 sky, 0 block)").text());
        assertEquals("label.difficulty 2.25", ligne("difficulty", "Local Difficulty: 2.25 // 0.00 (Day 3)").text());
        assertEquals("label.target_block 12, 63, -6", ligne("target.block.pos", "§nTargeted Block: 12, 63, -6").text());
    }

    @Test
    void uneLigneInattendueResteTelleQuelle() {
        assertEquals("XYZ quelque chose d'autre", ligne("xyz", "XYZ quelque chose d'autre").text());
        assertEquals("Create: 42 RPM", ligne("mod.create", "Create: 42 RPM").text());
        assertEquals("§aPression 3", ligne("mod.pression", "§aPression 3").text());
    }

    @Test
    void laCiblePorteSonNomSiOnLeConnait() {
        Row row = DebugFormat.row(new Line("target.block", "minecraft:stone"), k -> k, (k, id) -> "Roche");
        assertEquals(List.of(new Segment("Roche", Tone.VALUE), new Segment(" minecraft:stone", Tone.LABEL)), row.segments());
        assertEquals("minecraft:stone", ligne("target.block", "minecraft:stone").text());
    }

    @Test
    void fpsEtMemoireSontLus() {
        assertEquals(118, DebugFormat.fps("118 fps T: 120 vsync fancy-clouds B: 2"));
        assertEquals(-1, DebugFormat.fps("pas de chiffre"));
        DebugFormat.Memory memory = DebugFormat.memory("Mem:  5% 123/4096MB");
        assertEquals(new DebugFormat.Memory(5, 123, 4096), memory);
        assertNull(DebugFormat.memory("Mem: inconnu"));
    }

    /** Mesure d'essai : 1 par caractère visible, comme Font.width ignore les codes. */
    private static int largeur(String text) {
        return DebugFormat.plain(text).length();
    }

    @Test
    void laCoupureNePerdRien() {
        String texte = "CPU: 16x AMD Ryzen 9 7950X 16-Core Processor avec un nom interminable";
        List<String> lignes = DebugFormat.wrap(texte, 20, DebugFormatTest::largeur);
        assertTrue(lignes.size() > 1);
        lignes.forEach(l -> assertTrue(largeur(l) <= 20, l));
        assertEquals(texte.replace(" ", ""), String.join("", lignes).replace(" ", ""));
    }

    @Test
    void unMotTropLongEstCoupeSansBoucler() {
        List<String> lignes = DebugFormat.wrap("a".repeat(45), 10, DebugFormatTest::largeur);
        assertEquals(5, lignes.size());
        assertEquals(45, String.join("", lignes).length());
    }

    @Test
    void laCouleurContinueSurLaLigneSuivante() {
        List<String> lignes = DebugFormat.wrap("§cabcdefghij klmnopqrst", 10, DebugFormatTest::largeur);
        assertEquals(List.of("§cabcdefghij", "§cklmnopqrst"), lignes);
    }

    @Test
    void unCodeSeulNeFaitPasUneLigne() {
        List<String> lignes = DebugFormat.wrap("§a motmotmotmot", 10, DebugFormatTest::largeur);
        lignes.forEach(l -> assertFalse(DebugFormat.plain(l).isBlank(), lignes.toString()));
        assertEquals(" motmotmotmot".replace(" ", ""), lignes.stream().map(DebugFormat::plain).collect(Collectors.joining()).replace(" ", ""));
    }

    private static final DebugFormat.Labels SUFFIXES = k -> k.substring(DebugFormat.PREFIX.length());

    @Test
    void leJourEtLHeureSuiventLesTicks() {
        assertEquals("label.day 0, 06:00", DebugFormat.dayTimeRow(0, SUFFIXES).text());
        assertEquals("label.day 0, 12:00", DebugFormat.dayTimeRow(6000, SUFFIXES).text());
        assertEquals("label.day 0, 00:00", DebugFormat.dayTimeRow(18000, SUFFIXES).text());
        assertEquals("label.day 0, 05:59", DebugFormat.dayTimeRow(23999, SUFFIXES).text());
        assertEquals("label.day 14, 06:12", DebugFormat.dayTimeRow(14 * 24000 + 200, SUFFIXES).text());
    }

    @Test
    void lOragePrimeSurLaPluie() {
        assertEquals("label.weather value.clear", DebugFormat.weatherRow(false, false, SUFFIXES).text());
        assertEquals("label.weather value.rain", DebugFormat.weatherRow(true, false, SUFFIXES).text());
        assertEquals("label.weather value.thunder", DebugFormat.weatherRow(true, true, SUFFIXES).text());
    }

    @Test
    void laDistanceSeMesureEtSAffiche() {
        DebugFormat.Point yeux = new DebugFormat.Point(0.5, 64.5, 0.5);
        assertEquals(5.0, DebugFormat.distance(yeux, new DebugFormat.Point(3.5, 68.5, 0.5)), 1e-9);
        assertEquals("label.distance 3.2 unit.blocks", DebugFormat.distanceRow(3.24, SUFFIXES).text());
    }

    @Test
    void leCentreDuBlocVientDeLaLigneDeCible() {
        assertEquals(new DebugFormat.Point(12.5, 63.5, -5.5), DebugFormat.blockCenter("§nTargeted Block: 12, 63, -6"));
        assertNull(DebugFormat.blockCenter("Targeted Block: ailleurs"));
    }

    @Test
    void laCibleMontreSesPremieresProprietes() {
        List<Line> cible = List.of(new Line("target.block.pos", "§nTargeted Block: 1, 2, 3"), new Line("target.block", "minecraft:oak_stairs"));
        List<Line> detail = List.of(new Line("version", "Minecraft 1.20.1"),
                new Line("target.block.prop.facing", "facing: north"),
                new Line("target.block.prop.half", "half: bottom"),
                new Line("target.block.prop.shape", "shape: straight"),
                new Line("target.block.prop.waterlogged", "waterlogged: §cfalse"),
                new Line("target.block.tag0", "#minecraft:stairs"));
        List<String> lignes = DebugFormat.targetRows(cible, detail, SUFFIXES, (k, id) -> null, DebugFormat.distanceRow(2, SUFFIXES))
                .stream().map(Row::text).collect(Collectors.toList());
        assertEquals(List.of("label.target_block 1, 2, 3", "minecraft:oak_stairs", "facing north", "half bottom", "shape straight",
                "label.distance 2.0 unit.blocks"), lignes);
    }

    @Test
    void laValeurDUneProprieteColoreeEstLue() {
        List<Line> cible = List.of(new Line("target.block", "minecraft:grass_block"));
        List<Line> detail = List.of(new Line("target.block.prop.snowy", "snowy: §cfalse"));
        assertEquals("snowy false", DebugFormat.targetRows(cible, detail, SUFFIXES, (k, id) -> null, null).get(1).text());
    }

    @Test
    void lEntiteMontreSonIdentifiantEtSaDistance() {
        List<Line> cible = List.of(new Line("target.entity.header", "§nTargeted Entity"), new Line("target.entity", "minecraft:cow"));
        List<String> lignes = DebugFormat.targetRows(cible, List.of(), SUFFIXES, (k, id) -> "Vache", DebugFormat.distanceRow(1.5, SUFFIXES))
                .stream().map(Row::text).collect(Collectors.toList());
        assertEquals(List.of("label.target_entity", "Vache minecraft:cow", "label.distance 1.5 unit.blocks"), lignes);
    }

    @Test
    void sansLigneDeCiblePasDeCarte() {
        assertTrue(DebugFormat.targetRows(List.of(), List.of(new Line("target.block.prop.a", "a: b")), SUFFIXES, (k, id) -> null,
                DebugFormat.distanceRow(1, SUFFIXES)).isEmpty());
    }

    @Test
    void laCoupureEnVueNormaleIgnoreLesCodes() {
        assertEquals("§cabcde", DebugFormat.head("§cabcdefghij", 5, DebugFormatTest::largeur));
        // Un code entier ne prend aucune largeur : il peut finir la tête, jamais y être coupé en deux.
        assertEquals("ab§c", DebugFormat.head("ab§cdef", 2, DebugFormatTest::largeur));
    }

    @Test
    void uneLigneQuiTientNEstPasTouchee() {
        assertEquals(List.of("court"), DebugFormat.wrap("court", 10, DebugFormatTest::largeur));
    }
}
