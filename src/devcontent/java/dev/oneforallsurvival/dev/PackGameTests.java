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
     * Les identifiants de toutes les recettes, de tous les types (table, four, tailleur de pierre, assemblage de
     * Create...), dont le résultat est cet objet. La règle du propriétaire vaut pour toutes les voies de fabrication,
     * pas seulement la table : une machine ne se fabrique qu'à l'établi.
     */
    private static List<ResourceLocation> recettesDonnant(GameTestHelper helper, Item item) {
        var access = helper.getLevel().registryAccess();
        return helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(r -> r.getResultItem(access).is(item)).map(Recipe::getId).toList();
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
        for (Materiel m : List.of(
                new Materiel("fuse", 4, 2, false, false),
                new Materiel("coil", 5, 1, false, false),
                new Materiel("bearing", 6, 1, true, false),
                new Materiel("cleaning_kit", 3, 1, true, false),
                new Materiel("tester", 4, 1, true, false),
                new Materiel("welder", 5, 1, true, false),
                new Materiel("galvanic_bath", 6, 1, true, true),
                new Materiel("fridge", 6, 1, true, false),
                new Materiel("desalinator", 6, 1, true, false),
                new Materiel("dryer", 6, 1, true, false))) {
            Item item = objet("oneforallsurvival", m.nom());
            // Aucune recette d'aucun type : l'établi (le catalogue) est la seule voie.
            List<ResourceLocation> autres = recettesDonnant(helper, item);
            helper.assertTrue(autres.isEmpty(), m.nom() + " a encore des recettes hors établi : " + autres);
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
     * Le sel et les rations ne se fabriquent qu'aux machines : le dessalinisateur donne le sel, le séchoir et
     * le sel donnent les rations. Ni la table de craft ni l'établi n'en portent une recette, une fois le pack chargé.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void selEtRationsNeSeFabriquentQuAuxMachines(GameTestHelper helper) {
        var etabli = BenchCatalog.current().recipes();
        for (String nom : new String[] {"salt", "salted_meat", "salted_fish", "salted_fruit", "dried_meat",
                "dried_fish", "dried_fruit", "potable_water_bottle", "spoiled_food"}) {
            Item item = objet("oneforallsurvival", nom);
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR, "objet absent : " + nom);
            // Seul le séchoir en fait, et seulement des six rations (recettes oneforallsurvival:drying) : le sel,
            // la bouteille d'eau potable et le déchet n'ont droit à aucune recette.
            boolean ration = nom.startsWith("dried_") || nom.startsWith("salted_");
            List<ResourceLocation> autres = recettesDonnant(helper, item).stream()
                    .filter(id -> !(ration && helper.getLevel().getRecipeManager().byKey(id).orElseThrow().getType()
                            .equals(FoodRegistry.DRYING_TYPE.get())))
                    .toList();
            helper.assertTrue(autres.isEmpty(), nom + " se fabrique par d'autres recettes que le séchoir : " + autres);
            helper.assertTrue(etabli.stream().noneMatch(r -> r.result().is(item)),
                    "l'établi propose " + nom);
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

    /**
     * Le courant d'un vrai réseau de Create Crafts &amp; Additions traverse le tableau de délestage, au motif de la
     * preuve des réseaux du lot 0 ({@link ReseauxGameTests}) : la créative, un connecteur, un fil de cuivre tiré à la
     * bobine, un connecteur contre l'arrière du tableau, et la machine d'essai devant lui. Critère : la machine tourne
     * et a travaillé 20 ticks, et le tableau affiche sa sortie avant alimentée, avec des FE.
     */
    @GameTest(template = "test/plancher", batch = "pack_delesteur", timeoutTicks = 300, setupTicks = 20)
    public static void leCourantDeCraftsAdditionsTraverseLeDelesteur(GameTestHelper helper) {
        if (!ReseauxDev.Reseau.CREATE_ADDITION.disponible()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(0, 2, 1));
        BlockPos tableau = source.east(5);
        BlockPos machine = source.east(6);
        java.util.List<String> journal = new java.util.ArrayList<>();
        // Sortie avant à l'est, vers la machine ; entrée à l'ouest, du côté du connecteur.
        level.setBlock(machine, DevBlocks.testMachine().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(tableau, dev.oneforallsurvival.grid.GridRegistry.LOAD_SHEDDER.get().defaultBlockState()
                .setValue(dev.oneforallsurvival.grid.LoadShedderBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(source, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("createaddition", "creative_energy"))
                .defaultBlockState(), Block.UPDATE_ALL);
        ReseauxDev.cliquer(level, "createaddition:connector", source, Direction.EAST, Direction.WEST, 1, journal);
        ReseauxDev.cliquer(level, "createaddition:connector", tableau, Direction.WEST, Direction.EAST, 1, journal);
        ReseauxDev.Scene scene = new ReseauxDev.Scene(ReseauxDev.Reseau.CREATE_ADDITION, true, source, machine, tableau,
                source.east(1), source.east(4), journal);
        // Le fil vient 5 ticks plus tard, comme dans la preuve du lot 0 : les connecteurs ont eu leur premier tick.
        helper.runAfterDelay(5, () -> ReseauxDev.cabler(level, scene));
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockEntity(machine) instanceof TestMachineBlockEntity, "machine d'essai absente");
            helper.assertTrue(level.getBlockEntity(tableau) instanceof dev.oneforallsurvival.grid.LoadShedderBlockEntity,
                    "tableau absent");
            TestMachineBlockEntity m = (TestMachineBlockEntity) level.getBlockEntity(machine);
            var t = (dev.oneforallsurvival.grid.LoadShedderBlockEntity) level.getBlockEntity(tableau);
            int devant = dev.oneforallsurvival.grid.LoadShedderBlockEntity.OUTPUT_FRONT;
            helper.assertTrue(m.state() == dev.oneforallsurvival.machine.MachineState.RUNNING && m.progress() >= 20,
                    "machine : " + m.state() + ", progression " + m.progress() + ", journal " + journal);
            // Alimentée, ou partielle si le fil apporte moins que la machine ne prend : jamais coupée.
            var etat = t.status(devant);
            helper.assertTrue((etat == dev.oneforallsurvival.grid.OutputStatus.FED
                    || etat == dev.oneforallsurvival.grid.OutputStatus.PARTIAL) && t.lastDelivered(devant) > 0,
                    "sortie avant : " + etat + ", " + t.lastDelivered(devant) + " FE");
            org.apache.logging.log4j.LogManager.getLogger("oneforallsurvival-reseaux").info(
                    "[reseau] delesteur | RESULTAT OK au tick {} : progression {}, sortie avant {} a {} FE, journal {}",
                    helper.getTick(), m.progress(), etat, t.lastDelivered(devant), journal);
        });
    }
}
