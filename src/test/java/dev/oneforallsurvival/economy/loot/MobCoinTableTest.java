package dev.oneforallsurvival.economy.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.oneforallsurvival.economy.Coin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MobCoinTableTest {

    private static final Path FILE =
            Path.of("src/main/resources/data/oneforallsurvival/economy/mob_coins.json");
    private static MobCoinTable shipped;

    @BeforeAll
    static void lire() throws IOException {
        shipped = MobCoinTable.parse(JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                .getAsJsonObject());
    }

    private static ResourceLocation id(String s) {
        return new ResourceLocation(s);
    }

    private static MobCoinTable.Tier tier(String type, MobCategory category, boolean charged, String... tags) {
        Set<ResourceLocation> set = new java.util.HashSet<>();
        for (String t : tags) {
            set.add(id(t));
        }
        return shipped.tierFor(id(type), set, category, charged);
    }

    @Test
    void leFichierLivreSeLit() {
        assertEquals(4, shipped.tierCount());
        assertNotNull(shipped.tier("common"));
        assertNotNull(shipped.tier("dangerous"));
        assertNotNull(shipped.tier("boss"));
        assertNotNull(shipped.tier("wild_animal"));
        assertNull(shipped.tier("none"));
        assertTrue(shipped.ruleCount() > 0);
    }

    @Test
    void zombieCourantVacheSauvageVillageoisRien() {
        assertEquals("common", tier("minecraft:zombie", MobCategory.MONSTER, false).id());
        assertEquals("wild_animal", tier("minecraft:cow", MobCategory.CREATURE, false).id());
        assertNull(tier("minecraft:villager", MobCategory.MISC, false));
    }

    @Test
    void witherParSonTagGardienAncienParSonIdentifiant() {
        MobCoinTable.Tier wither = tier("minecraft:wither", MobCategory.MONSTER, false, "forge:bosses");
        assertEquals("boss", wither.id());
        assertTrue(wither.anyOrigin());
        assertEquals("boss", tier("minecraft:elder_guardian", MobCategory.MONSTER, false).id());
        assertFalse(shipped.tier("common").anyOrigin());
    }

    @Test
    void creeperChargeDangereux() {
        assertEquals("dangerous", tier("minecraft:creeper", MobCategory.MONSTER, true).id());
        assertEquals("common", tier("minecraft:creeper", MobCategory.MONSTER, false).id());
        assertEquals("dangerous", tier("minecraft:witch", MobCategory.MONSTER, false).id());
    }

    @Test
    void laPremiereRegleGagne() {
        JsonObject json = JsonParser.parseString("""
                {"tiers": {"boss": {"any_origin": true, "rolls": [{"coin": "gold", "chance": 1, "min": 1, "max": 1}]}},
                 "rules": [{"match": "minecraft:wither", "tier": "none"}, {"match": "#forge:bosses", "tier": "boss"}],
                 "category_defaults": {"monster": "boss"}}
                """).getAsJsonObject();
        MobCoinTable table = MobCoinTable.parse(json);
        Set<ResourceLocation> tags = Set.of(id("forge:bosses"));
        assertNull(table.tierFor(id("minecraft:wither"), tags, MobCategory.MONSTER, false));
        assertEquals("boss", table.tierFor(id("minecraft:dragon"), tags, MobCategory.MONSTER, false).id());
    }

    @Test
    void unMobModdeSuitSaCategorie() {
        assertEquals("common", tier("autremod:bete", MobCategory.MONSTER, false).id());
    }

    @Test
    void tiragesDeDepartSurDixMille() {
        RandomSource random = RandomSource.create(20261115L);
        int payes = 0;
        for (int i = 0; i < 10_000; i++) {
            EnumMap<Coin, Long> out = MobCoinTable.roll(shipped.tier("common"), random);
            if (!out.isEmpty()) {
                payes++;
                assertEquals(1, out.size());
                long cuivres = out.get(Coin.COPPER);
                assertTrue(cuivres >= 1 && cuivres <= 3, "cuivres : " + cuivres);
            }
        }
        assertTrue(payes >= 3_000 && payes <= 3_670, "courants payes : " + payes);

        int emeraudes = 0;
        int rubis = 0;
        for (int i = 0; i < 10_000; i++) {
            EnumMap<Coin, Long> out = MobCoinTable.roll(shipped.tier("boss"), random);
            assertEquals(5L, out.get(Coin.GOLD));
            emeraudes += out.containsKey(Coin.EMERALD) ? 1 : 0;
            rubis += out.containsKey(Coin.RUBY) ? 1 : 0;
        }
        assertTrue(emeraudes >= 800 && emeraudes <= 1_200, "emeraudes : " + emeraudes);
        assertTrue(rubis >= 1, "rubis : " + rubis);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"tiers\": []} | tiers",
            "{\"tiers\": {\"a\": {\"rolls\": [{\"coin\": \"platine\", \"chance\": 0.5, \"min\": 1, \"max\": 2}]}}} | coin",
            "{\"tiers\": {\"a\": {\"rolls\": [{\"coin\": \"gold\", \"chance\": 1.5, \"min\": 1, \"max\": 2}]}}} | chance",
            "{\"tiers\": {\"a\": {\"rolls\": [{\"coin\": \"gold\", \"chance\": 0.5, \"min\": 3, \"max\": 2}]}}} | max",
            "{\"tiers\": {\"a\": {\"rolls\": [{\"coin\": \"gold\", \"chance\": 0.5, \"min\": 0, \"max\": 2}]}}} | min",
            "{\"tiers\": {\"a\": {\"rolls\": []}}, \"rules\": [{\"match\": \"minecraft:cow\", \"tier\": \"inconnu\"}]} | rules[0].tier",
            "{\"tiers\": {\"a\": {\"rolls\": []}}, \"charged_creeper\": \"inconnu\"} | charged_creeper"
    })
    void unFichierInvalideEstRefuse(String text, String champ) {
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> MobCoinTable.parse(JsonParser.parseString(text).getAsJsonObject()));
        assertTrue(e.getMessage().contains(champ), "message : " + e.getMessage());
    }

    @Test
    void unJsonMalFormeEstRefuse() {
        assertThrows(JsonParseException.class, () -> JsonParser.parseString("{\"tiers\": {"));
    }
}
