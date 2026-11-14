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

    private static final List<String> LOT7 = List.of("metal_detector", "headlamp", "signal_receiver");

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
                new Materiel("dryer", 6, 1, true, false),
                // Lot 4 : la gestion du réseau. Le scellé, un consommable, reste à la portée d'une île sans Create.
                new Materiel("load_shedder", 7, 1, true, false),
                new Materiel("meter", 6, 1, true, false),
                new Materiel("breaker", 5, 1, true, false),
                new Materiel("seal", 3, 4, false, false),
                new Materiel("surge_arrester", 6, 1, true, false),
                new Materiel("weather_station", 6, 1, true, false),
                // Lot 5 : vivres et protection. La clôture demande le fil barbelé de Crafts & Additions et en donne huit.
                new Materiel("incubator", 7, 1, true, false),
                new Materiel("hot_plate", 5, 1, true, false),
                new Materiel("canteen_pot", 6, 1, true, false),
                new Materiel("electrifier", 6, 1, true, false),
                new Materiel("electric_fence", 5, 8, true, true),
                // Lot 6 : vie de la ville. Le chauffe-eau et le phare demandent des pièces du lot 2 en plus de Create.
                new Materiel("water_heater", 7, 1, true, false),
                new Materiel("shower_head", 3, 1, true, false),
                new Materiel("lighthouse", 7, 1, true, false),
                // Lot 7 : l'exploration. Le détecteur, la lampe frontale et le récepteur se fabriquent à l'établi.
                new Materiel("metal_detector", 6, 1, true, false),
                new Materiel("headlamp", 6, 1, true, false),
                new Materiel("signal_receiver", 7, 1, true, false))) {
            Item item = objet("oneforallsurvival", m.nom());
            // Aucune recette d'aucun type : l'établi (le catalogue) est la seule voie.
            List<ResourceLocation> autres = recettesDonnant(helper, item);
            helper.assertTrue(autres.isEmpty(), m.nom() + " a encore des recettes hors établi : " + autres);
            ResourceLocation id = new ResourceLocation("oneforallsurvival", m.nom());
            BenchRecipe recette = BenchCatalog.current().byId(id).orElse(null);
            if (LOT7.contains(m.nom())) {
                // Le matériel du lot 7 n'a qu'une seule recette au catalogue, la sienne, sous quelque identifiant que ce soit.
                long auCatalogue = BenchCatalog.current().recipes().stream().filter(r -> r.result().is(item)).count();
                helper.assertTrue(auCatalogue == ((m.create() && !create) ? 0 : 1),
                        m.nom() + " : " + auCatalogue + " recettes au catalogue de l'établi");
            }
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
        // Lot 6 : le chauffe-eau demande du fil (étiquette wire), le phare du verre ; le phare n'a pas besoin de fil.
        if (create) {
            var catalogue = BenchCatalog.current();
            helper.assertTrue(accepte(catalogue, "water_heater", new ItemStack(objet("oneforallsurvival", "aviation_wire"))),
                    "le chauffe-eau n'a aucun ingrédient qui accepte l'étiquette wire");
            helper.assertTrue(accepte(catalogue, "lighthouse", new ItemStack(net.minecraft.world.item.Items.GLASS)),
                    "le phare n'a aucun ingrédient qui accepte le verre");
        }
        // Lot 7 : chaque ingrédient clé est exigé, et en bon nombre (échanger un ingrédient doit faire échouer).
        if (create) {
            var catalogue = BenchCatalog.current();
            ItemStack bobine = new ItemStack(objet("oneforallsurvival", "coil"));
            ItemStack fusible = new ItemStack(objet("oneforallsurvival", "fuse"));
            ItemStack fil = new ItemStack(objet("oneforallsurvival", "aviation_wire"));
            ItemStack plaqueFer = new ItemStack(objet("create", "iron_sheet"));
            ItemStack plaqueLaiton = new ItemStack(objet("create", "brass_sheet"));
            for (String nom : LOT7) {
                helper.assertTrue(nombreAcceptant(catalogue, nom, fusible) >= 1, nom + " : le fusible n'est pas exigé");
                helper.assertTrue(nombreAcceptant(catalogue, nom, fil) >= 1, nom + " : l'étiquette wire n'est pas exigée");
                helper.assertTrue(nombreAcceptant(catalogue, nom, plaqueFer) >= 1,
                        nom + " : la plaque de fer n'est pas exigée");
            }
            helper.assertTrue(nombreAcceptant(catalogue, "signal_receiver", plaqueLaiton) >= 1,
                    "le récepteur n'exige pas de plaque de laiton");
            helper.assertTrue(nombreAcceptant(catalogue, "metal_detector", bobine) == 2,
                    "le détecteur demande deux bobines");
            helper.assertTrue(nombreAcceptant(catalogue, "headlamp", bobine) == 1, "la lampe demande une bobine");
            helper.assertTrue(nombreAcceptant(catalogue, "signal_receiver", bobine) == 2,
                    "le récepteur demande deux bobines");
            helper.assertTrue(accepte(catalogue, "metal_detector", new ItemStack(objet("create", "fluid_pipe"))),
                    "le détecteur n'a aucun ingrédient qui accepte le tuyau de Create");
            helper.assertTrue(accepte(catalogue, "headlamp", new ItemStack(net.minecraft.world.item.Items.LEATHER)),
                    "la lampe n'a aucun ingrédient qui accepte le cuir");
            helper.assertTrue(accepte(catalogue, "headlamp", new ItemStack(net.minecraft.world.item.Items.GLASS)),
                    "la lampe n'a aucun ingrédient qui accepte le verre");
            helper.assertTrue(accepte(catalogue, "signal_receiver", new ItemStack(net.minecraft.world.item.Items.IRON_BARS)),
                    "le récepteur n'a aucun ingrédient qui accepte les barreaux de fer");
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
     * Le relevé de compteur ne naît que du testeur de l'ingénieur posé sur un compteur : aucune recette d'aucun type,
     * ni à la table ni à l'établi, une fois le pack chargé.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void leReleveNeSeFabriquePas(GameTestHelper helper) {
        Item releve = objet("oneforallsurvival", "meter_reading");
        helper.assertTrue(releve != null && releve != net.minecraft.world.item.Items.AIR, "objet absent : meter_reading");
        List<ResourceLocation> recettes = recettesDonnant(helper, releve);
        helper.assertTrue(recettes.isEmpty(), "le relevé a des recettes : " + recettes);
        helper.assertTrue(BenchCatalog.current().recipes().stream().noneMatch(r -> r.result().is(releve)),
                "l'établi propose le relevé");
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

    /** Combien d'ingrédients de la recette de l'établi de ce nom acceptent cet objet. */
    private static long nombreAcceptant(BenchCatalog catalogue, String nom, ItemStack pile) {
        BenchRecipe recette = catalogue.byId(new ResourceLocation("oneforallsurvival", nom)).orElse(null);
        return recette == null ? 0 : recette.ingredients().stream().filter(ingredient -> ingredient.test(pile)).count();
    }

    /** Vrai si un ingrédient de la recette de l'établi de ce nom accepte cet objet. */
    private static boolean accepte(BenchCatalog catalogue, String nom, ItemStack pile) {
        BenchRecipe recette = catalogue.byId(new ResourceLocation("oneforallsurvival", nom)).orElse(null);
        return recette != null && recette.ingredients().stream().anyMatch(ingredient -> ingredient.test(pile));
    }

    /**
     * Les cinq blocs du lot 5 se cassent à la pioche et rendent leur objet : un butin et l'étiquette de pioche pour
     * chacun. Sans eux la machine ne tomberait pas, ou resterait indestructible avec un pic. Aucun mod tiers requis.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void lesCinqBlocsDuLot5TombentALaPioche(GameTestHelper helper) {
        tombentALaPioche(helper, "incubator", "hot_plate", "canteen_pot", "electrifier", "electric_fence");
        helper.succeed();
    }

    /** Les trois blocs du lot 6 (chauffe-eau, pomme de douche, phare) : même garde-fou que le lot 5. */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void lesTroisBlocsDuLot6TombentALaPioche(GameTestHelper helper) {
        tombentALaPioche(helper, "water_heater", "shower_head", "lighthouse");
        helper.succeed();
    }

    /** Chaque bloc nommé porte l'étiquette mineable/pickaxe et, cassé à la pioche de fer, rend un exemplaire de lui-même. */
    private static void tombentALaPioche(GameTestHelper helper, String... noms) {
        var level = helper.getLevel();
        BlockPos absolue = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack pioche = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        var lootContext = new net.minecraft.world.level.storage.loot.LootParams.Builder(level);
        for (String nom : noms) {
            Block bloc = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("oneforallsurvival", nom));
            helper.assertTrue(bloc != null && bloc != Blocks.AIR, "bloc absent : " + nom);
            BlockState etat = bloc.defaultBlockState();
            helper.assertTrue(etat.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),
                    nom + " ne porte pas l'étiquette mineable/pickaxe");
            var drops = etat.getDrops(lootContext
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                            net.minecraft.world.phys.Vec3.atCenterOf(absolue))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, pioche));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(objet("oneforallsurvival", nom))
                    && drops.get(0).getCount() == 1, nom + " devait rendre un exemplaire de lui-même, vu " + drops);
        }
    }

    /**
     * Lot 7 : la cache enfouie et le mécanisme ancien ne s'obtiennent que par l'équipe. Aucune recette d'aucun type
     * ne les donne, ni à la table ni à l'établi ; seul le matériel (détecteur, lampe, récepteur) se fabrique.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void cacheEtMecanismeSontSansRecette(GameTestHelper helper) {
        for (String nom : List.of("buried_cache", "ancient_mechanism")) {
            Item item = objet("oneforallsurvival", nom);
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR, "objet absent : " + nom);
            List<ResourceLocation> recettes = recettesDonnant(helper, item);
            helper.assertTrue(recettes.isEmpty(), nom + " ne devrait avoir aucune recette, vu " + recettes);
            helper.assertTrue(BenchCatalog.current().byId(new ResourceLocation("oneforallsurvival", nom)).isEmpty(),
                    nom + " ne devrait pas figurer au catalogue de l'établi");
            // Quel que soit l'identifiant : aucune recette de l'établi ne les donne.
            List<ResourceLocation> aLEtabli = BenchCatalog.current().recipes().stream()
                    .filter(r -> r.result().is(item)).map(BenchRecipe::id).toList();
            helper.assertTrue(aLEtabli.isEmpty(), nom + " ne devrait avoir aucune recette d'établi, vu " + aLEtabli);
        }
        helper.succeed();
    }

    /**
     * Lot 7 : le récepteur tombe à la pioche et rend son objet ; la cache se casse à la hache (étiquette mineable/axe,
     * butin tiré de sa table) ; le mécanisme ne se mine pas et ne rend rien.
     */
    @GameTest(template = "test/plancher", batch = "pack_materiel")
    public static void lesBlocsDuLot7SeCassentCommeVoulu(GameTestHelper helper) {
        tombentALaPioche(helper, "signal_receiver");
        Block cache = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("oneforallsurvival", "buried_cache"));
        helper.assertTrue(cache.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),
                "la cache ne porte pas l'étiquette mineable/axe");
        Block mecanisme = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("oneforallsurvival", "ancient_mechanism"));
        BlockState etat = mecanisme.defaultBlockState();
        helper.assertTrue(!etat.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                && !etat.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE), "le mécanisme ne devrait pas se miner");
        helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))) < 0,
                "le mécanisme devrait être incassable (dureté -1)");
        helper.succeed();
    }

    /**
     * Lot E1 : les pièces, la bourse et le coffre-fort ne se fabriquent jamais à l'établi, quelle que soit sa
     * configuration ni les mods chargés. Seules la bourse et le coffre-fort ont une recette de table (les pièces
     * n'ont que le change) : elles sont sans condition de mod, d'ingrédients vanilla, et la bourse comme les pièces
     * ne sont l'ingrédient d'aucune recette (le livre de recettes remplit la grille sans passer par
     * {@code Slot.mayPlace}).
     */
    @GameTest(template = "test/plancher", batch = "pack_monnaie")
    public static void laBourseEtLeCoffreSeFabriquentSurLaTableSeulement(GameTestHelper helper) throws java.io.IOException {
        var manager = helper.getLevel().getRecipeManager();
        var access = helper.getLevel().registryAccess();
        List<Item> argent = List.of(objet("oneforallsurvival", "copper_coin"), objet("oneforallsurvival", "silver_coin"),
                objet("oneforallsurvival", "gold_coin"), objet("oneforallsurvival", "emerald_coin"),
                objet("oneforallsurvival", "ruby_coin"), objet("oneforallsurvival", "purse"),
                objet("oneforallsurvival", "safe"));
        for (Item item : argent) {
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR, "objet absent : " + item);
            List<ResourceLocation> aLEtabli = BenchCatalog.current().recipes().stream()
                    .filter(r -> r.result().is(item)).map(BenchRecipe::id).toList();
            helper.assertTrue(aLEtabli.isEmpty(), item + " ne devrait avoir aucune recette d'établi, vu " + aLEtabli);
            helper.assertTrue(BenchCatalog.isTableOnly(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)),
                    item + " devrait porter l'étiquette table_only");
        }
        for (String nom : List.of("purse", "safe")) {
            ResourceLocation id = new ResourceLocation("oneforallsurvival", nom);
            Item item = objet("oneforallsurvival", nom);
            // Une seule recette, de table, formée, qui donne un exemplaire.
            List<ResourceLocation> recettes = recettesDonnant(helper, item);
            helper.assertTrue(recettes.equals(List.of(id)), nom + " : recettes vues " + recettes);
            Recipe<?> recette = manager.byKey(id).orElseThrow();
            helper.assertTrue(recette.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING
                    && recette instanceof net.minecraft.world.item.crafting.ShapedRecipe,
                    nom + " devrait être une recette de table formée");
            helper.assertTrue(recette.getResultItem(access).is(item) && recette.getResultItem(access).getCount() == 1,
                    nom + " devrait donner un exemplaire");
            // Aucune condition : le fichier de données ne porte pas de « conditions ».
            try (var in = PackGameTests.class.getResourceAsStream("/data/oneforallsurvival/recipes/" + nom + ".json")) {
                helper.assertTrue(in != null, "fichier de recette absent : " + nom);
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                helper.assertTrue(!json.contains("conditions") && !json.contains("mod_loaded"),
                        nom + " ne devrait porter aucune condition de mod");
            }
            // Ingrédients vanilla, jamais de pièce ni de bourse.
            for (Ingredient ingredient : recette.getIngredients()) {
                // Une recette formée porte aussi les cases vides de sa grille : seuls les vrais ingrédients comptent.
                for (ItemStack pile : ingredient.getItems()) {
                    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pile.getItem())
                            .getNamespace().equals("minecraft"), nom + " : ingrédient non vanilla " + pile);
                }
            }
        }
        // Le coffre-fort se mine à la pioche (plus vite qu'à la main) et rend son objet ; qui le casse reste réglé par le bloc.
        tombentALaPioche(helper, "safe");
        // La bourse et les pièces ne sont l'ingrédient d'aucune recette, d'aucun type.
        for (Recipe<?> recette : manager.getRecipes()) {
            for (Ingredient ingredient : recette.getIngredients()) {
                for (ItemStack pile : ingredient.getItems()) {
                    helper.assertTrue(!pile.is(objet("oneforallsurvival", "purse")) && !argent.subList(0, 5).contains(pile.getItem())
                            || recette.getId().getPath().startsWith("coin_change/"),
                            "la monnaie est un ingrédient de " + recette.getId());
                }
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot E2 : l'étal et le carnet de factures
    // ---------------------------------------------------------------------------------------------

    /** Une grille de table 3 x 3 garnie de ces objets (de gauche à droite, de haut en bas ; {@code null} = vide). */
    private static net.minecraft.world.inventory.CraftingContainer grille(Item... cases) {
        net.minecraft.world.inventory.AbstractContainerMenu menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player joueur, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player joueur) {
                return true;
            }
        };
        net.minecraft.world.inventory.TransientCraftingContainer grille =
                new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < cases.length; i++) {
            grille.setItem(i, cases[i] == null ? ItemStack.EMPTY : new ItemStack(cases[i]));
        }
        return grille;
    }

    /** La recette de table que la grille déclenche, ou rien. */
    private static java.util.Optional<net.minecraft.world.item.crafting.CraftingRecipe> fabrique(GameTestHelper helper,
            net.minecraft.world.inventory.CraftingContainer grille) {
        return helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grille,
                helper.getLevel());
    }

    private static boolean donne(java.util.Optional<net.minecraft.world.item.crafting.CraftingRecipe> trouvee, ResourceLocation id) {
        return trouvee.map(r -> r.getId().equals(id)).orElse(false);
    }

    private static String fichierDeRecette(GameTestHelper helper, String nom) throws java.io.IOException {
        try (var in = PackGameTests.class.getResourceAsStream("/data/oneforallsurvival/recipes/" + nom + ".json")) {
            helper.assertTrue(in != null, "fichier de recette absent : " + nom);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * L'étal : trois laines, deux bâtons, trois planches (forme {@code WWW / S S / PPP}), n'importe quelle laine et
     * n'importe quelles planches, sans condition de mod, un exemplaire. La forme est exigée.
     */
    @GameTest(template = "test/plancher", batch = "pack_commerce")
    public static void lEtalSeFabriqueALaTable(GameTestHelper helper) throws java.io.IOException {
        Item etal = objet("oneforallsurvival", "market_stall");
        ResourceLocation id = new ResourceLocation("oneforallsurvival", "market_stall");
        helper.assertTrue(etal != null && etal != net.minecraft.world.item.Items.AIR, "étal absent");
        helper.assertTrue(recettesDonnant(helper, etal).equals(List.of(id)), "recettes vues : " + recettesDonnant(helper, etal));
        Recipe<?> recette = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        helper.assertTrue(recette instanceof net.minecraft.world.item.crafting.ShapedRecipe forme
                && forme.getWidth() == 3 && forme.getHeight() == 3, "recette formée 3 x 3 attendue");
        helper.assertTrue(recette.getResultItem(helper.getLevel().registryAccess()).is(etal)
                && recette.getResultItem(helper.getLevel().registryAccess()).getCount() == 1, "un exemplaire attendu");
        String json = fichierDeRecette(helper, "market_stall");
        helper.assertTrue(!json.contains("conditions") && !json.contains("mod_loaded"), "aucune condition de mod");
        helper.assertTrue(json.contains("minecraft:wool") && json.contains("minecraft:stick") && json.contains("minecraft:planks"),
                "laine, bâton, planches attendus");

        Item s = net.minecraft.world.item.Items.STICK;
        // Des laines et des planches de toutes sortes, la forme exacte : la recette se déclenche.
        for (Item[] pieces : new Item[][] {
                {net.minecraft.world.item.Items.WHITE_WOOL, net.minecraft.world.item.Items.OAK_PLANKS},
                {net.minecraft.world.item.Items.RED_WOOL, net.minecraft.world.item.Items.CRIMSON_PLANKS},
                {net.minecraft.world.item.Items.BLACK_WOOL, net.minecraft.world.item.Items.BAMBOO_PLANKS}}) {
            Item w = pieces[0];
            Item p = pieces[1];
            var trouvee = fabrique(helper, grille(w, w, w, s, null, s, p, p, p));
            helper.assertTrue(donne(trouvee, id), "la forme exacte devait donner l'étal : " + trouvee);
        }
        // Des laines et des planches mêlées : toujours l'étal (l'étiquette, pas une couleur).
        helper.assertTrue(donne(fabrique(helper, grille(net.minecraft.world.item.Items.RED_WOOL, net.minecraft.world.item.Items.WHITE_WOOL,
                net.minecraft.world.item.Items.BLUE_WOOL, s, null, s, net.minecraft.world.item.Items.OAK_PLANKS,
                net.minecraft.world.item.Items.SPRUCE_PLANKS, net.minecraft.world.item.Items.BIRCH_PLANKS)), id),
                "des laines et des planches mêlées devaient convenir");
        // Hors forme, rien : la forme est exigée.
        Item w = net.minecraft.world.item.Items.WHITE_WOOL;
        Item p = net.minecraft.world.item.Items.OAK_PLANKS;
        helper.assertTrue(!donne(fabrique(helper, grille(w, w, w, s, s, null, p, p, p)), id), "un bâton déplacé ne devait pas donner l'étal");
        helper.assertTrue(!donne(fabrique(helper, grille(p, p, p, s, null, s, w, w, w)), id), "la forme renversée ne devait pas donner l'étal");
        helper.succeed();
    }

    /**
     * Le carnet de factures : sans forme, un livre, une poche d'encre et du papier, sans condition de mod, un
     * exemplaire. Le livre, l'encre et la plume sont la recette du livre et de la plume vanilla ({@code writable_book}) :
     * elle reste intacte, et le carnet ne la recouvre pas (l'un des deux serait sinon infabricable).
     */
    @GameTest(template = "test/plancher", batch = "pack_commerce")
    public static void leCarnetSeFabriqueALaTable(GameTestHelper helper) throws java.io.IOException {
        Item carnet = objet("oneforallsurvival", "invoice_book");
        ResourceLocation id = new ResourceLocation("oneforallsurvival", "invoice_book");
        helper.assertTrue(carnet != null && carnet != net.minecraft.world.item.Items.AIR, "carnet absent");
        helper.assertTrue(recettesDonnant(helper, carnet).equals(List.of(id)), "recettes vues : " + recettesDonnant(helper, carnet));
        Recipe<?> recette = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
        helper.assertTrue(recette instanceof net.minecraft.world.item.crafting.ShapelessRecipe,
                "une recette sans forme attendue : " + recette.getClass());
        helper.assertTrue(recette.getResultItem(helper.getLevel().registryAccess()).is(carnet)
                && recette.getResultItem(helper.getLevel().registryAccess()).getCount() == 1, "un exemplaire attendu");
        String json = fichierDeRecette(helper, "invoice_book");
        helper.assertTrue(!json.contains("conditions") && !json.contains("mod_loaded"), "aucune condition de mod");
        for (Ingredient ingredient : recette.getIngredients()) {
            for (ItemStack pile : ingredient.getItems()) {
                helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(pile.getItem()).getNamespace()
                        .equals("minecraft"), "ingrédient non vanilla " + pile);
            }
        }
        Item livre = net.minecraft.world.item.Items.BOOK;
        Item encre = net.minecraft.world.item.Items.INK_SAC;
        Item papier = net.minecraft.world.item.Items.PAPER;
        // Dans n'importe quel ordre, dans n'importe quelles cases.
        var a = fabrique(helper, grille(livre, encre, papier, papier));
        var b = fabrique(helper, grille(null, papier, null, encre, null, papier, null, livre, null));
        helper.assertTrue(donne(a, id), "livre, encre, papiers (en haut) : " + a);
        helper.assertTrue(donne(b, id), "livre, encre, papiers (en vrac) : " + b);
        // Il manque un ingrédient : rien.
        helper.assertTrue(!donne(fabrique(helper, grille(livre, encre, papier)), id), "un papier de moins ne devait pas donner le carnet");
        // Le livre, l'encre et la plume : toujours la recette vanilla, pas le carnet.
        var plume = fabrique(helper, grille(livre, encre, net.minecraft.world.item.Items.FEATHER));
        helper.assertTrue(plume.isPresent() && plume.get().getResultItem(helper.getLevel().registryAccess())
                .is(net.minecraft.world.item.Items.WRITABLE_BOOK), "livre, encre, plume devait rester le livre et la plume : " + plume);
        helper.succeed();
    }

    /**
     * Ni l'étal, ni le carnet, ni la facture, ni le reçu ne se fabriquent à l'établi : aucune recette de l'établi ne
     * les donne ; l'étal et le carnet portent l'étiquette {@code table_only} (l'établi ne les prend jamais), la
     * facture et le reçu n'ont aucune recette, de quelque type que ce soit.
     */
    @GameTest(template = "test/plancher", batch = "pack_commerce")
    public static void niLEtalNiLeCarnetNiLaFactureALEtabli(GameTestHelper helper) {
        for (String nom : List.of("market_stall", "invoice_book", "invoice", "receipt")) {
            Item item = objet("oneforallsurvival", nom);
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR, "objet absent : " + nom);
            List<ResourceLocation> aLEtabli = BenchCatalog.current().recipes().stream()
                    .filter(r -> r.result().is(item)).map(BenchRecipe::id).toList();
            helper.assertTrue(aLEtabli.isEmpty(), nom + " ne devrait avoir aucune recette d'établi, vu " + aLEtabli);
        }
        for (String nom : List.of("market_stall", "invoice_book")) {
            helper.assertTrue(BenchCatalog.isTableOnly(new ResourceLocation("oneforallsurvival", nom)), nom + " : étiquette table_only");
        }
        for (String nom : List.of("invoice", "receipt")) {
            List<ResourceLocation> recettes = recettesDonnant(helper, objet("oneforallsurvival", nom));
            helper.assertTrue(recettes.isEmpty(), nom + " ne devrait avoir aucune recette, vu " + recettes);
        }
        helper.succeed();
    }

    /** Un reçu d'échange à {@code donne} et {@code recu} piles : les clés de l'infobulle, avec l'argument des lignes « et N autres ». */
    private static List<String> infobulle(GameTestHelper helper, int donne, int recu) {
        var joueur = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        List<ItemStack> a = new java.util.ArrayList<>();
        List<ItemStack> b = new java.util.ArrayList<>();
        for (int i = 0; i < donne; i++) {
            a.add(new ItemStack(net.minecraft.world.item.Items.BREAD, i + 1));
        }
        for (int i = 0; i < recu; i++) {
            b.add(new ItemStack(net.minecraft.world.item.Items.APPLE, i + 1));
        }
        ItemStack recuPile = dev.oneforallsurvival.economy.Receipt.forTrade(joueur, joueur, a, b, 1L);
        List<String> out = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.Component c : dev.oneforallsurvival.economy.Receipt.tooltip(recuPile)) {
            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
                String cle = t.getKey().substring(t.getKey().lastIndexOf('.') + 1);
                out.add(t.getKey().endsWith(".more") ? "more:" + t.getArgs()[0] : cle);
            }
        }
        return out;
    }

    /** Chaque côté du reçu d'échange compte ses propres lignes cachées. */
    @GameTest(template = "test/plancher", batch = "pack_commerce")
    public static void chaqueCoteDuRecuCompteSesLignesCachees(GameTestHelper helper) {
        List<String> plein = infobulle(helper, 12, 12);
        int d = plein.indexOf("given");
        int r = plein.indexOf("received");
        helper.assertTrue(d >= 0 && r > d, "en-têtes : " + plein);
        helper.assertTrue(plein.subList(d, r).equals(List.of("given", "line", "line", "more:10")), "donné 12 : " + plein.subList(d, r));
        helper.assertTrue(plein.subList(r, plein.size()).equals(List.of("received", "line", "line", "more:10")),
                "reçu 12 : " + plein.subList(r, plein.size()));
        List<String> mixte = infobulle(helper, 1, 12);
        int d2 = mixte.indexOf("given");
        int r2 = mixte.indexOf("received");
        helper.assertTrue(mixte.subList(d2, r2).equals(List.of("given", "line")), "donné 1 : " + mixte.subList(d2, r2));
        helper.assertTrue(mixte.subList(r2, mixte.size()).equals(List.of("received", "line", "line", "more:10")),
                "reçu 12 (1 + 12) : " + mixte.subList(r2, mixte.size()));
        helper.succeed();
    }
}
