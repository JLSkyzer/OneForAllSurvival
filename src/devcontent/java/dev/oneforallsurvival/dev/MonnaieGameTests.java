package dev.oneforallsurvival.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchTimes;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRecipeGuard;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Les pièces de l'économie : objets, change, garde des recettes, établi, infobulle. */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MonnaieGameTests {

    private static final String LOT = "monnaie_pieces";

    private MonnaieGameTests() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation("oneforallsurvival", path);
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void lesCinqPiecesExistent(GameTestHelper helper) {
        long[] valeurs = {1, 10, 100, 1000, 10000};
        String[] noms = {"copper", "silver", "gold", "emerald", "ruby"};
        for (Coin coin : Coin.values()) {
            ResourceLocation id = id(noms[coin.ordinal()] + "_coin");
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "objet manquant : " + id);
            helper.assertTrue(coin.item() == BuiltInRegistries.ITEM.get(id), "Coin.item() != " + id);
            helper.assertTrue(Coin.fromItem(coin.item()).orElse(null) == coin, "fromItem : " + id);
            helper.assertTrue(coin.value() == valeurs[coin.ordinal()], "valeur de " + coin + " : " + coin.value());
            helper.assertTrue(CoinMath.valueOf(new ItemStack(coin.item(), 3)) == 3 * valeurs[coin.ordinal()],
                    "valeur d'une pile de " + coin);
            helper.assertTrue(new ItemStack(coin.item()).is(BenchCatalog.TABLE_ONLY), id + " n'est pas dans table_only");
        }
        helper.assertTrue(Coin.fromItem(Items.GOLD_INGOT).isEmpty(), "un lingot d'or n'est pas une pièce");
        helper.assertTrue(CoinMath.valueOf(new ItemStack(Items.GOLD_INGOT, 5)) == 0, "un lingot d'or vaut 0");
        helper.assertTrue(CoinMath.valueOf(ItemStack.EMPTY) == 0, "une pile vide vaut 0");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leChangeDescendALaTable(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(CoinRecipeGuard.CHANGE_RECIPES.size() == 4, "quatre recettes de change attendues");
        int deCuivreAuRubis = 0;
        for (ResourceLocation id : CoinRecipeGuard.CHANGE_RECIPES) {
            Recipe<?> recette = manager.byKey(id).orElse(null);
            helper.assertTrue(recette instanceof ShapelessRecipe, "recette de change absente ou non sans forme : " + id);
            ItemStack resultat = recette.getResultItem(helper.getLevel().registryAccess());
            Coin sortie = Coin.fromItem(resultat.getItem()).orElse(null);
            helper.assertTrue(sortie != null, "le résultat de " + id + " n'est pas une pièce");
            long entree = 0;
            Coin pieceEntree = null;
            for (Ingredient ingredient : recette.getIngredients()) {
                ItemStack[] choix = ingredient.getItems();
                helper.assertTrue(choix.length == 1, "ingrédient ambigu dans " + id);
                Coin coin = Coin.fromItem(choix[0].getItem()).orElse(null);
                helper.assertTrue(coin != null && (pieceEntree == null || pieceEntree == coin),
                        "ingrédient de " + id + " : " + choix[0]);
                pieceEntree = coin;
                entree += coin.value();
            }
            long sortieValeur = CoinMath.valueOf(resultat);
            helper.assertTrue(entree == sortieValeur, id + " : entrée " + entree + ", sortie " + sortieValeur);
            helper.assertTrue(pieceEntree.ordinal() - sortie.ordinal() == 1,
                    id + " ne descend pas d'une coupure : " + pieceEntree + " -> " + sortie);
            helper.assertTrue(recette.getIngredients().size() == 1 && resultat.getCount() == 10, id + " : 1 pour 10");
            deCuivreAuRubis++;
        }
        helper.assertTrue(deCuivreAuRubis == 4, "recettes vérifiées : " + deCuivreAuRubis);
        // Aucune recette de la table ne regroupe : rien ne produit une pièce hors du change vers le bas.
        for (Recipe<?> r : manager.getRecipes()) {
            ItemStack out = r.getResultItem(helper.getLevel().registryAccess());
            if (!out.isEmpty() && Coin.fromItem(out.getItem()).isPresent()) {
                helper.assertTrue(CoinRecipeGuard.CHANGE_RECIPES.contains(r.getId()), "recette de pièce inattendue : " + r.getId());
            }
        }
        helper.succeed();
    }

    private static Recipe<?> fabrique(ResourceLocation id, Coin coin) {
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, new ItemStack(coin.item(), 1),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)));
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneRecetteQuiFabriqueUnePieceEstRetiree(GameTestHelper helper) {
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        RegistryAccess acces = helper.getLevel().registryAccess();
        ResourceLocation changeId = id("coin_change/silver_to_copper");
        Recipe<?> change = vivant.byKey(changeId).orElseThrow();
        ResourceLocation intruse = new ResourceLocation("autre_mod", "pieces_gratuites");
        ResourceLocation faux = id("coin_change/gratuit");
        ResourceLocation cuisson = new ResourceLocation("autre_mod", "fondre_une_piece");
        ResourceLocation lingot = new ResourceLocation("autre_mod", "lingot_normal");
        Recipe<?> fondre = new SmeltingRecipe(cuisson, "", CookingBookCategory.MISC, Ingredient.of(Items.GOLD_INGOT),
                new ItemStack(Coin.GOLD.item()), 0.1F, 200);
        Recipe<?> ordinaire = new ShapelessRecipe(lingot, "", CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_NUGGET)));

        // Un manager à part : le vrai ne doit pas bouger.
        RecipeManager essai = new RecipeManager();
        essai.replaceRecipes(List.of(change, fabrique(intruse, Coin.RUBY), fabrique(faux, Coin.COPPER), fondre, ordinaire));
        List<ResourceLocation> retirees = CoinRecipeGuard.filter(essai, acces);

        helper.assertTrue(Set.copyOf(retirees).equals(Set.of(intruse, faux, cuisson)), "retirées : " + retirees);
        helper.assertTrue(essai.byKey(changeId).isPresent(), "la recette de change a été retirée");
        helper.assertTrue(essai.byKey(lingot).isPresent(), "une recette sans pièce a été retirée");
        helper.assertTrue(essai.byKey(intruse).isEmpty() && essai.byKey(faux).isEmpty() && essai.byKey(cuisson).isEmpty(),
                "une recette de pièce est restée");
        helper.assertTrue(CoinRecipeGuard.filter(essai, acces).isEmpty(), "un second passage retire encore quelque chose");
        helper.assertTrue(vivant.byKey(changeId).isPresent(), "le vrai manager a perdu le change");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leChangeResteSurLaTable(GameTestHelper helper) {
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        RegistryAccess acces = helper.getLevel().registryAccess();
        // « oneforallsurvival » est un mod géré et la liste des objets de table est vide : seul le tag retient les pièces.
        Set<String> mods = Set.of("oneforallsurvival");
        for (ResourceLocation id : CoinRecipeGuard.CHANGE_RECIPES) {
            helper.assertTrue(BenchCatalog.current().byId(id).isEmpty(), "le change " + id + " est au catalogue");
            helper.assertTrue(vivant.byKey(id).isPresent(), "le change " + id + " a quitté la table");
            Recipe<?> recette = vivant.byKey(id).orElseThrow();
            helper.assertTrue(!BenchCatalog.isManaged(recette, acces, mods, Set.of()), id + " serait pris par l'établi");
        }
        RecipeManager essai = new RecipeManager();
        List<Recipe<?>> lesHuit = new ArrayList<>();
        for (ResourceLocation id : CoinRecipeGuard.CHANGE_RECIPES) {
            lesHuit.add(vivant.byKey(id).orElseThrow());
        }
        essai.replaceRecipes(lesHuit);
        BenchCatalog catalogue = BenchCatalog.extract(essai, acces, mods, Set.of(), Set.of(), Map.of(),
                new BenchTimes.Params(4, 1, 600));
        helper.assertTrue(catalogue.recipes().isEmpty() && essai.getRecipes().size() == 4,
                "extraction : " + catalogue.recipes().size() + " au catalogue, " + essai.getRecipes().size() + " sur la table");
        helper.assertTrue(BenchCatalog.isTableOnly(id("gold_coin")), "gold_coin n'est pas table_only");
        helper.assertTrue(!BenchCatalog.isTableOnly(id("engineer_bench")), "l'établi ne doit pas être table_only");
        helper.succeed();
    }

    private static String arg(Component ligne) {
        return ligne.getContents() instanceof TranslatableContents t && t.getArgs().length > 0
                ? ((Component) t.getArgs()[0]).getString() : "";
    }

    private static String cle(Component ligne) {
        return ligne.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneInfobulleDonneLaValeur(GameTestHelper helper) {
        List<Component> seule = new ItemStack(Coin.GOLD.item()).getTooltipLines(null, TooltipFlag.NORMAL);
        helper.assertTrue(seule.stream().anyMatch(l -> cle(l).equals("tooltip.oneforallsurvival.coin.value")
                && arg(l).startsWith("1 ")), "pas de ligne de valeur pour une pièce d'or : " + seule);
        helper.assertTrue(seule.stream().noneMatch(l -> cle(l).equals("tooltip.oneforallsurvival.coin.total")),
                "total affiché pour une pièce seule");
        List<Component> pile = new ItemStack(Coin.GOLD.item(), 7).getTooltipLines(null, TooltipFlag.NORMAL);
        // 7 or = 700 cuivres = 7 or ; la valeur d'une pièce reste « 1 or ».
        helper.assertTrue(pile.stream().anyMatch(l -> cle(l).equals("tooltip.oneforallsurvival.coin.total")
                && arg(l).startsWith("7 ")), "pas de total pour une pile de 7 : " + pile);
        helper.succeed();
    }

    private static Recipe<?> changeDe(ResourceLocation id, Coin entree, Coin sortie, int nombre) {
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, new ItemStack(sortie.item(), nombre),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(entree.item())));
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneRecetteDeChangeAltereeEstRetiree(GameTestHelper helper) {
        RegistryAccess acces = helper.getLevel().registryAccess();
        ResourceLocation or = id("coin_change/gold_to_silver");
        ResourceLocation argent = id("coin_change/silver_to_copper");
        ResourceLocation rubis = id("coin_change/ruby_to_emerald");
        ResourceLocation emeraude = id("coin_change/emerald_to_gold");
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        RecipeManager essai = new RecipeManager();
        essai.replaceRecipes(List.of(
                changeDe(or, Coin.GOLD, Coin.SILVER, 100),          // donne 100 au lieu de 10
                changeDe(argent, Coin.GOLD, Coin.COPPER, 10),       // mauvaise pièce en entrée
                changeDe(rubis, Coin.RUBY, Coin.GOLD, 10),          // saute une coupure
                vivant.byKey(emeraude).orElseThrow()));             // intacte
        List<ResourceLocation> retirees = CoinRecipeGuard.filter(essai, acces);
        helper.assertTrue(Set.copyOf(retirees).equals(Set.of(or, argent, rubis)), "retirées : " + retirees);
        helper.assertTrue(essai.byKey(emeraude).isPresent(), "la recette de change intacte a été retirée");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leFiletRefuseUneRecetteDynamiqueQuiFabriqueUnePiece(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player joueur = helper.makeMockPlayer();
        net.minecraft.world.level.Level monde = helper.getLevel();
        RecipeManager vivant = monde.getServer().getRecipeManager();
        net.minecraft.world.inventory.CraftingMenu table = new net.minecraft.world.inventory.CraftingMenu(1,
                joueur.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        table.getSlot(1).set(new ItemStack(Coin.SILVER.item()));
        helper.assertTrue(CoinRecipeGuard.craftAllowed(table, monde, vivant), "le change d'argent est permis");

        // Une recette hostile, ajoutée en mémoire, donne aussi une pièce à partir d'une pièce d'argent.
        RecipeManager hostile = new RecipeManager();
        hostile.replaceRecipes(List.of(vivant.byKey(id("coin_change/silver_to_copper")).orElseThrow(),
                changeDe(new ResourceLocation("autre_mod", "gratuit"), Coin.SILVER, Coin.RUBY, 5)));
        helper.assertTrue(!CoinRecipeGuard.craftAllowed(table, monde, hostile), "la recette hostile est permise");

        table.getSlot(1).set(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(!CoinRecipeGuard.craftAllowed(table, monde, vivant), "aucune recette de change sur un lingot");
        net.minecraft.world.inventory.AnvilMenu enclume = new net.minecraft.world.inventory.AnvilMenu(2,
                joueur.getInventory());
        helper.assertTrue(!CoinRecipeGuard.craftAllowed(enclume, monde, vivant), "un autre écran est permis");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void unRapportDeConfigAutreQueDixRetireLeChange(GameTestHelper helper) {
        RegistryAccess acces = helper.getLevel().registryAccess();
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        List<Recipe<?>> quatre = new ArrayList<>();
        for (ResourceLocation id : CoinRecipeGuard.CHANGE_RECIPES) {
            quatre.add(vivant.byKey(id).orElseThrow());
        }
        // Valeurs 1 / 5 / 20 / 100 / 500 : nulle part dix pièces du dessous ne valent la pièce du dessus.
        long[] cinq = {1, 5, 20, 100, 500};
        RecipeManager autre = new RecipeManager();
        autre.replaceRecipes(quatre);
        List<ResourceLocation> retirees = CoinRecipeGuard.filter(autre, acces, c -> cinq[c.ordinal()]);
        helper.assertTrue(Set.copyOf(retirees).equals(CoinRecipeGuard.CHANGE_RECIPES) && autre.getRecipes().isEmpty(),
                "avec un rapport de 5, retirées : " + retirees);
        // 1 / 5 / 50 : seul le couple cuivre-argent est faux (10 x 1 != 5) ; argent-or l'est (10 x 5 == 50).
        long[] mixte = {1, 5, 50, 500, 5000};
        RecipeManager partiel = new RecipeManager();
        partiel.replaceRecipes(quatre);
        List<ResourceLocation> seule = CoinRecipeGuard.filter(partiel, acces, c -> mixte[c.ordinal()]);
        helper.assertTrue(seule.equals(List.of(id("coin_change/silver_to_copper"))), "rapport 1/5/50, retirées : " + seule);
        RecipeManager dix = new RecipeManager();
        dix.replaceRecipes(quatre);
        helper.assertTrue(CoinRecipeGuard.filter(dix, acces, c -> (long) Math.pow(10, c.ordinal())).isEmpty()
                && dix.getRecipes().size() == 4, "avec un rapport de 10, une recette de change a été retirée");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leFiletRefuseUnChangeSiLeRapportVivantEstFaux(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player joueur = helper.makeMockPlayer();
        net.minecraft.world.level.Level monde = helper.getLevel();
        RecipeManager vivant = monde.getServer().getRecipeManager();
        net.minecraft.world.inventory.CraftingMenu table = new net.minecraft.world.inventory.CraftingMenu(1,
                joueur.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        table.getSlot(1).set(new ItemStack(Coin.SILVER.item()));
        long[] cinq = {1, 5, 50, 500, 5000};
        helper.assertTrue(!CoinRecipeGuard.craftAllowed(table, monde, vivant, c -> cinq[c.ordinal()]),
                "argent -> cuivre permis alors que 10 x 1 != 5");
        helper.assertTrue(CoinRecipeGuard.craftAllowed(table, monde, vivant, c -> (long) Math.pow(10, c.ordinal())),
                "argent -> cuivre refusé à valeur égale");
        table.getSlot(1).set(new ItemStack(Coin.GOLD.item()));
        helper.assertTrue(CoinRecipeGuard.craftAllowed(table, monde, vivant, c -> cinq[c.ordinal()]),
                "or -> argent refusé alors que 10 x 5 == 50");
        helper.succeed();
    }

    /**
     * Une recette qui prend une pièce en ingrédient (hors les quatre changes) est signalée, pas retirée. Le jeu livré
     * (sans le pack ou avec) n'en a aucune.
     */
    @GameTest(template = "test/plancher", batch = "monnaie_consommees")
    public static void uneRecetteQuiConsommeUnePieceEstSignalee(GameTestHelper helper) {
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        RegistryAccess acces = helper.getLevel().registryAccess();
        helper.assertTrue(CoinRecipeGuard.coinConsumers(vivant.getRecipes()).isEmpty(),
                "recettes du jeu qui consomment une pièce : " + CoinRecipeGuard.coinConsumers(vivant.getRecipes()));
        ResourceLocation mange = new ResourceLocation("autre_mod", "baton_dore");
        ResourceLocation lingot = new ResourceLocation("autre_mod", "lingot_normal");
        Recipe<?> consomme = new ShapelessRecipe(mange, "", CraftingBookCategory.MISC, new ItemStack(Items.STICK),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK), Ingredient.of(Coin.GOLD.item())));
        Recipe<?> ordinaire = new ShapelessRecipe(lingot, "", CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_NUGGET)));
        Recipe<?> change = vivant.byKey(id("coin_change/gold_to_silver")).orElseThrow();
        helper.assertTrue(CoinRecipeGuard.coinConsumers(List.of(consomme, ordinaire, change)).equals(List.of(mange)),
                "signalées : " + CoinRecipeGuard.coinConsumers(List.of(consomme, ordinaire, change)));
        RecipeManager essai = new RecipeManager();
        essai.replaceRecipes(List.of(consomme, ordinaire, change));
        helper.assertTrue(CoinRecipeGuard.filter(essai, acces).isEmpty() && essai.byKey(mange).isPresent(),
                "la recette qui consomme une pièce est gardée (seulement signalée)");
        helper.succeed();
    }
}
