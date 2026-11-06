package dev.oneforallsurvival.bench.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.oneforallsurvival.bench.BenchRecipe;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Le filtre du catalogue tourne sans Minecraft : les recettes n'ont ni résultat ni ingrédients ici. */
class BenchClientStateTest {

    private static final BenchRecipe FER = recette("create:iron_sheet", "create");
    private static final BenchRecipe ETABLI = recette("oneforallsurvival:engineer_bench", "oneforallsurvival");
    private static final BenchRecipe ENGRENAGE = recette("create:cogwheel", "create");
    private static final BenchRecipe TORCHE = recette("minecraft:torch", "minecraft");
    private static final List<BenchRecipe> TOUT = List.of(FER, ETABLI, ENGRENAGE, TORCHE);

    private static final Map<BenchRecipe, String> NOMS = Map.of(
            FER, "Tôle de fer",
            ETABLI, "Établi d'ingénieur",
            ENGRENAGE, "Engrenage",
            TORCHE, "Torche");

    private static BenchRecipe recette(String id, String mod) {
        return new BenchRecipe(new ResourceLocation(id), null, List.of(), "crafting", 1);
    }

    @BeforeEach
    void installer() {
        BenchClientState.useDescriber(new BenchClientState.Describer() {
            @Override
            public String name(BenchRecipe recipe) {
                return NOMS.get(recipe);
            }

            @Override
            public String mod(BenchRecipe recipe) {
                return recipe.id().getNamespace();
            }
        });
    }

    @AfterEach
    void retirer() {
        BenchClientState.useDescriber(null);
    }

    @Test
    void requeteVideEtCategorieVideRendentToutDansLOrdre() {
        assertEquals(TOUT, BenchClientState.filter(TOUT, "", ""));
        assertEquals(TOUT, BenchClientState.filter(TOUT, null, null));
        assertEquals(TOUT, BenchClientState.filter(TOUT, "", "   "));
    }

    @Test
    void laCategorieRetientLesRecettesDeCeMod() {
        assertEquals(List.of(FER, ENGRENAGE), BenchClientState.filter(TOUT, "create", ""));
        assertEquals(List.of(TORCHE), BenchClientState.filter(TOUT, "minecraft", ""));
        assertTrue(BenchClientState.filter(TOUT, "inconnu", "").isEmpty());
    }

    @Test
    void laRechercheIgnoreLaCasse() {
        assertEquals(List.of(ENGRENAGE), BenchClientState.filter(TOUT, "", "ENGRENAGE"));
        assertEquals(List.of(TORCHE), BenchClientState.filter(TOUT, "", "tOrChE"));
    }

    @Test
    void laRechercheIgnoreLesAccentsDesDeuxCotes() {
        assertEquals(List.of(ETABLI), BenchClientState.filter(TOUT, "", "etabli"));
        assertEquals(List.of(ETABLI), BenchClientState.filter(TOUT, "", "ÉTABLI"));
        assertEquals(List.of(ETABLI), BenchClientState.filter(TOUT, "", "ingenieur"));
        assertEquals(List.of(FER), BenchClientState.filter(TOUT, "", "tole"));
        assertEquals(List.of(FER), BenchClientState.filter(TOUT, "", "tôle de fer"));
    }

    @Test
    void laRechercheLitAussiLIdentifiant() {
        assertEquals(List.of(ENGRENAGE), BenchClientState.filter(TOUT, "", "cogwheel"));
        assertEquals(List.of(FER, ENGRENAGE), BenchClientState.filter(TOUT, "", "create:"));
        assertEquals(List.of(ETABLI), BenchClientState.filter(TOUT, "", "engineer_bench"));
    }

    @Test
    void plusieursMotsSeCumulentDansUnOrdreQuelconque() {
        assertEquals(List.of(FER), BenchClientState.filter(TOUT, "", "fer tole"));
        assertTrue(BenchClientState.filter(TOUT, "", "fer torche").isEmpty());
    }

    @Test
    void categorieEtRechercheSeCumulent() {
        assertEquals(List.of(ENGRENAGE), BenchClientState.filter(TOUT, "create", "engr"));
        assertTrue(BenchClientState.filter(TOUT, "minecraft", "engr").isEmpty());
    }

    @Test
    void normaliserRetireAccentsCasseEtEspacesEnTrop() {
        assertEquals("etabli d'ingenieur", BenchClientState.normalize("  ÉTABLI   d'Ingénieur "));
    }

    @Test
    void lePlaceholderDeTraductionNEstPasAffiche() {
        assertEquals("Câble", BenchClientState.cleanName("[NEEDS_TRANSLATION] Câble"));
        assertEquals("Câble", BenchClientState.cleanName("[NEEDS_TRANSLATION]Câble"));
        assertEquals("Câble", BenchClientState.cleanName("Câble"));
        assertEquals("", BenchClientState.cleanName(null));
    }
}
