package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.repair.GalvanicRecipe;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Garde-fous du pack de développement. Ces essais ne prouvent quelque chose que lancés avec le
 * pack complet (`-PwithPack`) : sans lui, aucun mod tiers n'est chargé et ils passent trivialement.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PackGameTests {

    private PackGameTests() {}

    /**
     * Le mixin d'ESL (embarqué dans New Age) s'applique à tout `getCapability(ENERGY, ...)`. Il
     * appelle des méthodes de BlockEntity sous leurs noms de développement : si l'ESL chargé est
     * resté en noms SRG, la requête plante en NoSuchMethodError, même sur un coffre vanilla.
     */
    @GameTest(template = "test/plancher", batch = "pack_esl")
    public static void leMixinEslSAppliqueSansPlanter(GameTestHelper helper) {
        ModList mods = ModList.get();
        // Sans le pack (pas de New Age, donc pas d'ESL), il n'y a rien à prouver : on passe.
        if (!mods.isLoaded("createaddition")) {
            helper.succeed();
            return;
        }
        // Avec le pack, ESL doit être chargé : sinon un coffre sans plantage ne prouverait rien.
        helper.assertTrue(mods.isLoaded("esl"), "le pack est chargé mais pas ESL : le mixin n'est pas testé");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity pour le coffre");
        // Un coffre n'expose pas d'énergie : une réponse vide est la bonne issue, un plantage non.
        boolean present = be.getCapability(ForgeCapabilities.ENERGY, null).isPresent();
        helper.assertTrue(!present, "un coffre ne doit pas exposer d'énergie");
        helper.succeed();
    }

    /**
     * Les quatre recettes du bain galvanique livrées avec le mod se chargent, chacune vers sa pièce
     * traitée. Avec Create, l'étiquette du zinc n'est pas vide : sans lui, le bain n'a rien à traiter
     * (ce que le pack, lui, doit prouver).
     */
    @GameTest(template = "test/plancher", batch = "pack_bain")
    public static void lesQuatreRecettesDuBainSontChargees(GameTestHelper helper) {
        boolean create = ModList.get().isLoaded("create");
        for (String nom : new String[] {"fuse", "coil", "bearing", "cleaning_kit"}) {
            ResourceLocation id = new ResourceLocation("oneforallsurvival", "galvanic/" + nom);
            var recette = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recette instanceof GalvanicRecipe, "recette absente ou d'un autre type : " + id);
            GalvanicRecipe bain = (GalvanicRecipe) recette;
            helper.assertTrue(ForgeRegistries.ITEMS.getKey(bain.result().getItem())
                    .equals(new ResourceLocation("oneforallsurvival", "treated_" + nom)),
                    id + " devait donner treated_" + nom + ", vu " + bain.result());
            helper.assertTrue(bain.acceptsPiece(new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("oneforallsurvival", nom)))), id + " devait accepter la pièce " + nom);
            // Forge renvoie une barrière pour une étiquette vide : un vrai zinc n'en est pas une.
            helper.assertTrue(!create || !GalvanicRecipe.realStacks(bain.zinc()).isEmpty(),
                    "Create est chargé mais l'étiquette du zinc est vide : " + id);
            helper.assertTrue(!create || bain.acceptsZinc(new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("create", "zinc_ingot")))), "le lingot de zinc de Create devait convenir : " + id);
        }
        helper.succeed();
    }

    private static Item objet(String mod, String nom) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(mod, nom));
    }

    private static boolean porte(Item item, String etiquette) {
        return new ItemStack(item).is(ItemTags.create(new ResourceLocation("oneforallsurvival", etiquette)));
    }

    /**
     * Un objet du matériel du métier : son nombre d'ingrédients, le nombre fabriqué et les mods qu'il lui faut.
     * La plaque de zinc vient de Crafts &amp; Additions, le reste de Create ; fusible et bobinage restent vanilla.
     */
    private record Materiel(String nom, int ingredients, int resultat, boolean create, boolean additions) {}

    /**
     * Toute recette de table au résultat du mod part à l'établi : le catalogue la porte, avec de vrais objets
     * dans chaque ingrédient (ni étiquette vide, ni identifiant mal écrit), et la table ne la voit plus. Une
     * recette dont le mod manque n'existe nulle part : ni au catalogue, ni sur la table.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void leMaterielDuMetierEstALEtabliSeulement(GameTestHelper helper) {
        boolean create = ModList.get().isLoaded("create");
        boolean additions = ModList.get().isLoaded("createaddition");
        var access = helper.getLevel().registryAccess();
        var table = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        for (Materiel m : List.of(
                new Materiel("fuse", 4, 2, false, false),
                new Materiel("coil", 5, 1, false, false),
                new Materiel("bearing", 6, 1, true, false),
                new Materiel("cleaning_kit", 3, 1, true, false),
                new Materiel("tester", 4, 1, true, false),
                new Materiel("welder", 5, 1, true, false),
                new Materiel("galvanic_bath", 6, 1, true, true))) {
            Item item = objet("oneforallsurvival", m.nom());
            helper.assertTrue(table.stream().noneMatch(r -> r.getResultItem(access).is(item)),
                    "une recette de table donne encore " + m.nom());
            ResourceLocation id = new ResourceLocation("oneforallsurvival", m.nom());
            BenchRecipe recette = BenchCatalog.current().byId(id).orElse(null);
            if ((m.create() && !create) || (m.additions() && !additions)) {
                helper.assertTrue(recette == null, m.nom() + " ne devrait pas exister sans son mod");
                continue;
            }
            helper.assertTrue(recette != null, m.nom() + " absent du catalogue de l'établi");
            helper.assertTrue(recette.result().is(item) && recette.result().getCount() == m.resultat(),
                    id + " donne " + recette.result());
            helper.assertTrue(recette.ingredients().size() == m.ingredients(),
                    id + " : " + m.ingredients() + " ingrédients attendus, vu " + recette.ingredients().size());
            for (Ingredient ingredient : recette.ingredients()) {
                // Forge renvoie une barrière pour une étiquette vide.
                helper.assertTrue(!GalvanicRecipe.realStacks(ingredient).isEmpty(),
                        id + " a un ingrédient sans objet : " + ingredient.toJson());
            }
        }
        // Les étiquettes s'élargissent aux objets du pack.
        helper.assertTrue(porte(objet("oneforallsurvival", "aviation_wire"), "wire"),
                "le fil d'aviation doit porter l'étiquette wire");
        if (additions) {
            helper.assertTrue(porte(objet("createaddition", "copper_wire"), "wire"),
                    "le fil de cuivre doit porter l'étiquette wire");
        }
        String ampoules = "light_bulb_addon_create_crafts_and__addions";
        if (ModList.get().isLoaded(ampoules)) {
            helper.assertTrue(porte(objet(ampoules, "fuse_16a"), "parts/fuse"), "le fusible 16 A doit servir de fusible");
        }
        helper.succeed();
    }

    /**
     * Les pièces rares se refabriquent par assemblage séquencé. L'objet de transition est un objet simple du mod,
     * pas un {@code SequencedAssemblyItem} : on déroule toute la séquence par la méthode privée de Create (le mod
     * ne dépend pas de Create à la compilation), trois boucles de trois étapes, jusqu'à la pièce finie.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void lesPiecesRaresSeRefabriquent(GameTestHelper helper) throws ReflectiveOperationException {
        boolean create = ModList.get().isLoaded("create");
        boolean additions = ModList.get().isLoaded("createaddition");
        for (String nom : new String[] {"instrument_core", "battery_cell"}) {
            ResourceLocation id = new ResourceLocation("oneforallsurvival", "sequenced/" + nom);
            Recipe<?> recette = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            if (!create || (!additions && nom.equals("battery_cell"))) {
                helper.assertTrue(recette == null, id + " ne devrait pas exister sans son mod");
                continue;
            }
            helper.assertTrue(recette != null, "recette absente : " + id);
            helper.assertTrue(new ResourceLocation("create", "sequenced_assembly")
                    .equals(ForgeRegistries.RECIPE_TYPES.getKey(recette.getType())), id + " : mauvais type");
            Item fini = objet("oneforallsurvival", nom);
            Item transition = objet("oneforallsurvival", "incomplete_" + nom);
            helper.assertTrue(recette.getResultItem(helper.getLevel().registryAccess()).is(fini), id + " : mauvais résultat");
            Ingredient base = (Ingredient) recette.getClass().getMethod("getIngredient").invoke(recette);
            helper.assertTrue(!GalvanicRecipe.realStacks(base).isEmpty(), id + " : la base n'a aucun objet");
            Method avancer = recette.getClass().getDeclaredMethod("advance", ItemStack.class);
            avancer.setAccessible(true);
            ItemStack pile = base.getItems()[0].copy();
            for (int etape = 1; etape < 9; etape++) {
                pile = (ItemStack) avancer.invoke(recette, pile);
                helper.assertTrue(pile.is(transition), id + " étape " + etape + " : vu " + pile);
            }
            pile = (ItemStack) avancer.invoke(recette, pile);
            helper.assertTrue(pile.is(fini), id + " : la neuvième étape doit finir la pièce, vu " + pile);
        }
        helper.succeed();
    }

    /**
     * Un tuyau de Create posé contre le dessalinisateur le voit : sa propre logique de réseau (appelée par
     * réflexion, le mod ne dépend pas de Create à la compilation) reconnaît la capability de fluides sur la face
     * du tuyau, puis le raccord du tuyau avec la machine. Ce n'est PAS une pompe qui tourne : le montage d'une pompe
     * demande une force cinétique que les GameTests ne fournissent pas. Le transfert lui-même passe par la
     * capability, que {@code DessalinisateurGameTests} éprouve face par face.
     */
    @GameTest(template = "test/plancher", batch = "pack_tuyau")
    public static void leTuyauCreateVoitLeFluide(GameTestHelper helper) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        BlockPos machine = new BlockPos(1, 1, 1);
        BlockPos tuyau = machine.east();
        helper.setBlock(machine, FoodRegistry.DESALINATOR.get());
        Block pipe = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "fluid_pipe"));
        helper.assertTrue(pipe != null && pipe != Blocks.AIR, "le tuyau de Create (create:fluid_pipe) est introuvable");
        helper.setBlock(tuyau, pipe);
        var level = helper.getLevel();
        BlockPos machineAbs = helper.absolutePos(machine);
        BlockPos tuyauAbs = helper.absolutePos(tuyau);
        Class<?> propagateur = Class.forName("com.simibubi.create.content.fluids.FluidPropagator");
        Method aCapability = propagateur.getMethod("hasFluidCapability", BlockGetter.class, BlockPos.class, Direction.class);
        helper.assertTrue((boolean) aCapability.invoke(null, level, machineAbs, Direction.EAST),
                "Create ne voit pas de capability de fluides sur la face est du dessalinisateur");
        Class<?> bloc = Class.forName("com.simibubi.create.content.fluids.pipes.FluidPipeBlock");
        Method raccord = bloc.getMethod("canConnectTo", BlockAndTintGetter.class, BlockPos.class, BlockState.class,
                Direction.class);
        helper.assertTrue((boolean) raccord.invoke(null, level, tuyauAbs, level.getBlockState(tuyauAbs), Direction.WEST),
                "le tuyau de Create ne se raccorde pas au dessalinisateur");
        // Et ce que Create y verserait entre bien : l'eau seulement, par la face du tuyau.
        BlockEntity be = helper.getBlockEntity(machine);
        helper.assertTrue(be != null, "pas de block entity du dessalinisateur");
        var cuve = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElse(null);
        helper.assertTrue(cuve != null, "pas de capability de fluides sur la face est");
        helper.assertTrue(cuve.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "1000 mB d'eau devaient entrer");
        helper.assertTrue(cuve.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "la lave ne doit pas entrer");
        helper.succeed();
    }
}
