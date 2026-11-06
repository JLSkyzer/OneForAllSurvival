package dev.oneforallsurvival.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchConfig;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchTimes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preuve que les recettes des mods d'ingénierie quittent le RecipeManager pour le catalogue de
 * l'établi. Les essais « avecLePack_ » n'ont de sens qu'avec `-PwithPack` : sans lui ils s'écartent
 * en le journalisant.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class BenchCatalogGameTests {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-bench");

    private static final ResourceLocation RECETTE_ESSAI = new ResourceLocation("oneforallsurvival", "test_bench_shaped");
    private static final ResourceLocation TABLE = new ResourceLocation("minecraft", "crafting_table");
    private static final ResourceLocation MACHINE_ESSAI = new ResourceLocation("oneforallsurvival", "test_machine");
    private static final ResourceLocation RECETTE_ALT = new ResourceLocation("oneforallsurvival", "test_bench_alt");
    private static final ResourceLocation RECETTE_INLINE = new ResourceLocation("oneforallsurvival", "test_bench_inline");
    private static final ResourceLocation RECETTE_CACHEE = new ResourceLocation("oneforallsurvival", "test_bench_hidden");
    private static final ResourceLocation RECETTE_CHAINE = new ResourceLocation("oneforallsurvival", "test_bench_chain");
    private static final ResourceLocation ETABLI = new ResourceLocation("oneforallsurvival", "engineer_bench");

    private BenchCatalogGameTests() {}

    private static boolean packAbsent(GameTestHelper helper, String nom) {
        if (ModList.get().isLoaded("create") && ModList.get().isLoaded("createaddition")) {
            return false;
        }
        LOG.info("[etabli] {} : IGNORE, pack absent", nom);
        helper.succeed();
        return true;
    }

    private static boolean gere(Recipe<?> recette, RegistryAccess acces) {
        return BenchCatalog.isManaged(recette, acces, BenchConfig.managedMods());
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void laRecetteDEssaiQuitteLeManagerEtEntreAuCatalogue(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(manager.byKey(RECETTE_ESSAI).isEmpty(), "la recette d'essai est restée dans le manager");
        Optional<BenchRecipe> recette = BenchCatalog.current().byId(RECETTE_ESSAI);
        helper.assertTrue(recette.isPresent(), "la recette d'essai manque au catalogue");
        BenchRecipe r = recette.get();
        helper.assertTrue(r.result().is(BuiltInRegistries.ITEM.get(MACHINE_ESSAI)) && r.result().getCount() == 1,
                "résultat inattendu : " + r.result());
        // 8 lingots de fer et 1 de cuivre : les cases vides de la grille ne comptent pas.
        helper.assertTrue(r.ingredients().size() == 9, "nombre d'ingrédients : " + r.ingredients().size());
        long fer = r.ingredients().stream().filter(i -> i.test(new ItemStack(Items.IRON_INGOT))).count();
        long cuivre = r.ingredients().stream().filter(i -> i.test(new ItemStack(Items.COPPER_INGOT))).count();
        helper.assertTrue(fer == 8 && cuivre == 1, "fer " + fer + ", cuivre " + cuivre);
        helper.assertTrue("crafting".equals(r.category()), "catégorie : " + r.category());
        helper.assertTrue(r.seconds() == 120, "durée imposée par la surcharge : " + r.seconds());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneRecetteVanillaResteSurLaTable(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(manager.byKey(TABLE).isPresent(), "minecraft:crafting_table a quitté le manager");
        helper.assertTrue(BenchCatalog.current().byId(TABLE).isEmpty(), "minecraft:crafting_table est au catalogue");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void lEtabliRestePrenableALaMain(GameTestHelper helper) {
        helper.assertTrue(BenchConfig.tableAllowedItems().contains(ETABLI),
                "tableAllowedItems ne contient pas l'établi : " + BenchConfig.tableAllowedItems());
        // L'établi n'existe pas encore : la même règle s'éprouve sur l'objet d'essai, géré par défaut.
        BenchRecipe entree = BenchCatalog.current().byId(RECETTE_ESSAI).orElseThrow();
        Recipe<?> factice = new ShapelessRecipe(RECETTE_ESSAI, "", CraftingBookCategory.MISC, entree.result(),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)));
        RegistryAccess acces = helper.getLevel().registryAccess();
        helper.assertTrue(BenchCatalog.isManaged(factice, acces, BenchConfig.managedMods(), Set.of()),
                "l'objet d'essai devrait être géré");
        helper.assertTrue(!BenchCatalog.isManaged(factice, acces, BenchConfig.managedMods(), Set.of(MACHINE_ESSAI)),
                "un objet autorisé sur la table ne doit pas être géré");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneRecetteSpecialeResteSurLaTable(GameTestHelper helper) {
        // armor_dye est dynamique : son résultat est vide, elle ne peut pas se payer d'avance.
        ResourceLocation id = new ResourceLocation("minecraft", "armor_dye");
        Recipe<?> teinture = helper.getLevel().getServer().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(teinture != null, "armor_dye a quitté le manager");
        helper.assertTrue(!gere(teinture, helper.getLevel().registryAccess()), "armor_dye ne doit pas être gérée");
        helper.assertTrue(BenchCatalog.current().byId(id).isEmpty(), "armor_dye est au catalogue");
        helper.succeed();
    }

    // --- Temps et surcharges ----------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneSurchargeFixeLaDuree(GameTestHelper helper) {
        BenchRecipe r = BenchCatalog.current().byId(RECETTE_ESSAI).orElseThrow();
        helper.assertTrue(r.seconds() == 120, "durée : " + r.seconds());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneSurchargeVautPourToutesLesRecettesDuResultat(GameTestHelper helper) {
        BenchRecipe alt = BenchCatalog.current().byId(RECETTE_ALT).orElse(null);
        helper.assertTrue(alt != null, "la deuxième recette du même résultat manque");
        helper.assertTrue(alt.seconds() == 120, "durée de la deuxième recette : " + alt.seconds());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneSurchargeCacheLaRecetteMaisLaRetireDeLaTable(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(RECETTE_CACHEE).isEmpty(),
                "la recette cachée est restée sur la table");
        helper.assertTrue(BenchCatalog.current().byId(RECETTE_CACHEE).isEmpty(), "la recette cachée est visible");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneSurchargeRemplaceLesIngredients(GameTestHelper helper) {
        BenchRecipe r = BenchCatalog.current().byId(RECETTE_INLINE).orElseThrow();
        helper.assertTrue(r.ingredients().size() == 1 && r.ingredients().get(0).test(new ItemStack(Items.GOLD_INGOT)),
                "ingrédients : " + r.ingredients().size());
        // 4 de base + 1 pour un lingot d'or, objet commun sans recette au catalogue.
        helper.assertTrue(r.seconds() == 5, "durée : " + r.seconds());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void laComplexiteEtLaRareteSeTransmettent(GameTestHelper helper) {
        BenchRecipe r = BenchCatalog.current().byId(RECETTE_CHAINE).orElseThrow();
        // 4 + (1 + moitié de 120) + (1 + 5 pour la pomme d'or, rare) ; la surcharge à l'objet inconnu est ignorée.
        helper.assertTrue(r.ingredients().size() == 2, "ingrédients : " + r.ingredients().size());
        helper.assertTrue(r.seconds() == 71, "durée : " + r.seconds());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void unFichierInvalideOuUneSurchargeInconnueNArreteRien(GameTestHelper helper) {
        // Trois fichiers fautifs sont livrés (sans résultat, objet inconnu, résultat inconnu) : le
        // catalogue s'est construit quand même, et les autres surcharges ont joué.
        helper.assertTrue(BenchCatalog.current().byId(RECETTE_ESSAI).isPresent(), "catalogue vide");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void leCatalogueEstEnLectureSeule(GameTestHelper helper) {
        boolean refuse = false;
        try {
            BenchCatalog.current().recipes().clear();
        } catch (UnsupportedOperationException e) {
            refuse = true;
        }
        helper.assertTrue(refuse, "recipes() est modifiable");
        helper.assertTrue(BenchCatalog.current().byId(RECETTE_ESSAI).isPresent(), "le catalogue a été vidé");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void unObjetExcluQuitteLaTableSansEntrerAuCatalogue(GameTestHelper helper) {
        RegistryAccess acces = helper.getLevel().registryAccess();
        ItemStack resultat = new ItemStack(BuiltInRegistries.ITEM.get(MACHINE_ESSAI));
        ResourceLocation id = new ResourceLocation("oneforallsurvival", "exclusion_essai");
        Set<String> mods = Set.of("oneforallsurvival");
        BenchTimes.Params params = new BenchTimes.Params(4, 1, 600);
        for (boolean exclu : new boolean[] {true, false}) {
            RecipeManager manager = new RecipeManager();
            manager.replaceRecipes(List.of(new ShapelessRecipe(id, "", CraftingBookCategory.MISC, resultat,
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.COPPER_INGOT)))));
            BenchCatalog catalogue = BenchCatalog.extract(manager, acces, mods, Set.of(),
                    exclu ? Set.of(MACHINE_ESSAI) : Set.of(), Map.of(), params);
            helper.assertTrue(manager.byKey(id).isEmpty(), "la recette est restée sur la table (exclu=" + exclu + ")");
            helper.assertTrue(catalogue.byId(id).isPresent() == !exclu,
                    "visibilité au catalogue (exclu=" + exclu + ")");
            helper.assertTrue(exclu || catalogue.byId(id).get().seconds() == 6, "durée sans exclusion");
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue")
    public static void uneRecetteSpecialeAuResultatGereReste(GameTestHelper helper) {
        // Sous-classe de ShapedRecipe : classe non standard, résultat d'un mod géré. Elle reste sur la
        // table, est relevée comme spéciale, et extract la journalise en WARN (visible dans le journal).
        ResourceLocation id = new ResourceLocation("oneforallsurvival", "speciale_essai");
        ItemStack resultat = new ItemStack(BuiltInRegistries.ITEM.get(MACHINE_ESSAI));
        Recipe<?> speciale = new ShapedRecipe(id, "", CraftingBookCategory.MISC, 1, 1,
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT)), resultat) {};
        RecipeManager manager = new RecipeManager();
        manager.replaceRecipes(List.of(speciale));
        BenchCatalog catalogue = BenchCatalog.extract(manager, helper.getLevel().registryAccess(),
                Set.of("oneforallsurvival"), Set.of(), Set.of(), Map.of(), new BenchTimes.Params(4, 1, 600));
        helper.assertTrue(manager.byKey(id).isPresent(), "la recette spéciale a quitté la table");
        helper.assertTrue(catalogue.byId(id).isEmpty(), "la recette spéciale est au catalogue");
        helper.assertTrue(catalogue.specials().size() == 1 && catalogue.specials().get(0).id().equals(id),
                "recettes spéciales : " + catalogue.specials());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_reload", timeoutTicks = 400)
    public static void leReloadReconstruitLeCatalogue(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BenchCatalog avant = BenchCatalog.current();
        int tailleAvant = avant.recipes().size();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        helper.succeedWhen(() -> {
            BenchCatalog apres = BenchCatalog.current();
            helper.assertTrue(apres != avant, "le catalogue n'a pas été reconstruit");
            helper.assertTrue(server.getRecipeManager().byKey(RECETTE_ESSAI).isEmpty(),
                    "la recette d'essai est revenue dans le manager");
            helper.assertTrue(apres.byId(RECETTE_ESSAI).isPresent(), "la recette d'essai manque après le reload");
            helper.assertTrue(apres.recipes().size() == tailleAvant,
                    "taille du catalogue : " + tailleAvant + " puis " + apres.recipes().size());
            List<ResourceLocation> ids = new ArrayList<>();
            apres.recipes().forEach(r -> ids.add(r.id()));
            helper.assertTrue(ids.stream().distinct().count() == ids.size(), "doublons dans le catalogue");
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_pack")
    public static void avecLePack_aucuneRecetteDeTableDeCreate(GameTestHelper helper) {
        if (packAbsent(helper, "aucuneRecetteDeTableDeCreate")) {
            return;
        }
        RegistryAccess acces = helper.getLevel().registryAccess();
        List<String> restes = new ArrayList<>();
        for (Recipe<?> r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            RecipeType<?> type = r.getType();
            boolean concerne = type == RecipeType.CRAFTING || type == RecipeType.STONECUTTING
                    || type == RecipeType.SMITHING;
            if (concerne && gere(r, acces)) {
                restes.add(r.getId().toString());
            }
        }
        helper.assertTrue(restes.isEmpty(), restes.size() + " recettes gérées restent au manager : "
                + restes.subList(0, Math.min(10, restes.size())));
        int taille = BenchCatalog.current().recipes().size();
        LOG.info("[etabli] catalogue : {} recettes", taille);
        helper.assertTrue(taille > 1000, "catalogue trop petit : " + taille);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_pack")
    public static void avecLePack_lesFoursEtMachinesRestent(GameTestHelper helper) {
        if (packAbsent(helper, "lesFoursEtMachinesRestent")) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Set<String> mods = BenchConfig.managedMods();
        RegistryAccess acces = helper.getLevel().registryAccess();
        long milling = manager.getRecipes().stream()
                .filter(r -> BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()).toString().equals("create:milling"))
                .count();
        long fours = manager.getAllRecipesFor(RecipeType.SMELTING).stream()
                .filter(r -> mods.contains(BuiltInRegistries.ITEM.getKey(r.getResultItem(acces).getItem()).getNamespace()))
                .count();
        LOG.info("[etabli] {} recettes create:milling, {} recettes de four vers un mod géré restent", milling, fours);
        helper.assertTrue(milling > 0, "plus aucune recette create:milling dans le manager");
        helper.assertTrue(fours > 0, "plus aucune recette de four vers un mod géré");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_pack")
    public static void avecLePack_lesRecettesSpecialesRestentSurLaTable(GameTestHelper helper) {
        if (packAbsent(helper, "lesRecettesSpecialesRestentSurLaTable")) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        BenchCatalog catalogue = BenchCatalog.current();
        helper.assertTrue(!catalogue.specials().isEmpty(), "aucune recette spéciale relevée (key_copy attendue)");
        boolean keyCopy = false;
        for (BenchCatalog.Special s : catalogue.specials()) {
            LOG.info("[etabli] SPECIALE {} | {} | {}", s.id(), s.recipeClass(), s.reason());
            keyCopy |= s.id().getPath().equals("key_copy");
            helper.assertTrue(manager.byKey(s.id()).isPresent(), "la recette spéciale " + s.id() + " a quitté la table");
            helper.assertTrue(catalogue.byId(s.id()).isEmpty(), "la recette spéciale " + s.id() + " est au catalogue");
        }
        helper.assertTrue(keyCopy, "key_copy n'est pas dans les recettes spéciales");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_pack")
    public static void avecLePack_repartitionDesTemps(GameTestHelper helper) {
        if (packAbsent(helper, "repartitionDesTemps")) {
            return;
        }
        List<BenchRecipe> toutes = new ArrayList<>(BenchCatalog.current().recipes());
        int[] temps = toutes.stream().mapToInt(BenchRecipe::seconds).sorted().toArray();
        int n = temps.length;
        long plafond = Arrays.stream(temps).filter(t -> t >= 600).count();
        LOG.info("[etabli] TEMPS n={} min={} mediane={} max={} auPlafond={}", n, temps[0], temps[n / 2],
                temps[n - 1], plafond);
        toutes.stream()
                .sorted(Comparator.comparingInt(BenchRecipe::seconds).reversed().thenComparing(r -> r.id().toString()))
                .limit(5)
                .forEach(r -> LOG.info("[etabli] TEMPS-TOP {} s | {} | {}", r.seconds(), r.id(),
                        BuiltInRegistries.ITEM.getKey(r.result().getItem())));
        helper.assertTrue(n > 1000 && temps[0] >= 1, "temps incohérents : " + n + " recettes, min " + temps[0]);
        helper.succeed();
    }
}
