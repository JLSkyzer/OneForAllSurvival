package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.IncubatorBlockEntity;
import dev.oneforallsurvival.food.IncubatorMenu;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * La couveuse, éprouvée avec la vraie configuration (vingt minutes d'incubation, une minute de coupure). Pour ne pas
 * attendre, un test qui doit voir une maturité ou une coupure sauvegarde la machine, écrit le compteur voulu dans la
 * sauvegarde et la recharge : c'est le chemin même du rechargement d'un monde.
 *
 * <p>Les tests sont répartis en deux lots (a et b) : un seul lot de seize tests décalait les gabarits suivants du
 * lot par défaut jusqu'à faire échouer {@code IslandDebugGameTests.uneCibleDansUnChunkNonChargeEstIgnoree}, dont le
 * résultat dépend de l'endroit où son gabarit est posé.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CouveuseGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    /** Les clés de {@code IncubatorBlockEntity} ; la relecture après chargement dit si elles sont justes. */
    private static final String CLE_PROGRESSION = "progress";
    private static final String CLE_COUPURE = "outage";

    private CouveuseGameTests() {}

    private static IncubatorBlockEntity poser(GameTestHelper helper) {
        // Au-dessus du gabarit, c'est le terrain du monde d'essai (du gravier, parfois) : on dégage la colonne de l'animal.
        for (int haut = 1; haut <= 4; haut++) {
            helper.setBlock(POS.above(haut), Blocks.AIR);
        }
        helper.setBlock(POS, FoodRegistry.INCUBATOR.get());
        IncubatorBlockEntity be = (IncubatorBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la couveuse");
        return be;
    }

    private static void alimenter(IncubatorBlockEntity be) {
        for (int i = 0; i < 60; i++) {
            be.energy().receiveEnergy(IncubatorBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static void poserOeufs(IncubatorBlockEntity be, Item... oeufs) {
        for (int i = 0; i < oeufs.length; i++) {
            be.inventory().setStackInSlot(i, new ItemStack(oeufs[i]));
        }
    }

    /** Écrit les progressions voulues dans la sauvegarde puis recharge la machine. */
    private static void fixerProgressions(IncubatorBlockEntity be, int... progressions) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putIntArray(CLE_PROGRESSION, progressions);
        be.load(tag);
        for (int i = 0; i < progressions.length; i++) {
            if (be.progress(i) != progressions[i]) {
                throw new GameTestAssertException("la clé de progression de la sauvegarde est fausse : " + be.progress(i));
            }
        }
    }

    /** Amène chacun des {@code n} premiers œufs à un tick de son éclosion. */
    private static void presqueMurs(IncubatorBlockEntity be, int n) {
        int[] progressions = new int[IncubatorBlockEntity.SLOTS];
        for (int i = 0; i < n; i++) {
            progressions[i] = FoodConfig.incubatorHatchTicks() - 1;
        }
        fixerProgressions(be, progressions);
    }

    private static void fixerCoupure(IncubatorBlockEntity be, int ticks) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt(CLE_COUPURE, ticks);
        be.load(tag);
        if (be.outageTicks() != ticks) {
            throw new GameTestAssertException("la clé de coupure de la sauvegarde est fausse : " + be.outageTicks());
        }
    }

    private static int oeufsPresents(IncubatorBlockEntity be) {
        int n = 0;
        for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
            if (!be.inventory().getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static <T extends AgeableMob> List<T> animaux(GameTestHelper helper, EntityType<T> type) {
        return helper.getEntities(type, POS, 4.0);
    }

    /** Fin d'un test qui a fait naître des animaux : on les retire, ils ne doivent rien laisser au monde. */
    private static void reussirEtNettoyer(GameTestHelper helper) {
        for (EntityType<? extends AgeableMob> type : List.of(EntityType.CHICKEN, EntityType.TURTLE)) {
            animaux(helper, type).forEach(animal -> animal.discard());
        }
        helper.succeed();
    }

    private static int totalAuSol(List<ItemEntity> objets, Item item) {
        int n = 0;
        for (ItemEntity objet : objets) {
            if (objet.getItem().is(item)) {
                n += objet.getItem().getCount();
            }
        }
        return n;
    }

    private static IItemHandler handler(IncubatorBlockEntity be, Direction face) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).resolve().orElseThrow();
    }

    // ---------------------------------------------------------------------------------------------
    // L'éclosion
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void unOeufDePouleDonneUnPoussin(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG);
        alimenter(be);
        presqueMurs(be, 1);
        helper.runAfterDelay(6, () -> {
            var poussins = animaux(helper, EntityType.CHICKEN);
            helper.assertTrue(poussins.size() == 1, "un poussin attendu, vus " + poussins.size());
            helper.assertTrue(poussins.get(0).isBaby(), "l'animal devait être un petit");
            helper.assertTrue(be.inventory().getStackInSlot(0).isEmpty(), "l'œuf devait être consommé");
            helper.assertTrue(be.progress(0) == 0, "la case vidée devait repartir de zéro, vue " + be.progress(0));
            reussirEtNettoyer(helper);
        });
    }

    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void uneTortueAussi(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.TURTLE_EGG);
        alimenter(be);
        presqueMurs(be, 1);
        helper.runAfterDelay(6, () -> {
            var tortues = animaux(helper, EntityType.TURTLE);
            helper.assertTrue(tortues.size() == 1, "une tortue attendue, vues " + tortues.size());
            helper.assertTrue(tortues.get(0).isBaby(), "la tortue devait être un bébé");
            helper.assertTrue(be.inventory().getStackInSlot(0).isEmpty(), "l'œuf devait être consommé");
            reussirEtNettoyer(helper);
        });
    }

    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void quatreOeufsEclosent(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG, Items.EGG, Items.TURTLE_EGG);
        alimenter(be);
        presqueMurs(be, 4);
        helper.runAfterDelay(6, () -> {
            var poules = animaux(helper, EntityType.CHICKEN);
            var tortues = animaux(helper, EntityType.TURTLE);
            helper.assertTrue(poules.size() == 2 && tortues.size() == 2,
                    "2 poussins et 2 tortues attendus, vus " + poules.size() + " et " + tortues.size());
            helper.assertTrue(poules.stream().allMatch(AgeableMob::isBaby) && tortues.stream().allMatch(AgeableMob::isBaby),
                    "tous devaient être des petits");
            helper.assertTrue(oeufsPresents(be) == 0, "plus aucun œuf attendu, vus " + oeufsPresents(be));
            reussirEtNettoyer(helper);
        });
    }

    /** Sans rechargement : chaque œuf avance d'un tick de travail par tick, et la couveuse paie son courant. */
    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void chaqueOeufAvanceEtLeCourantSePaie(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.assertTrue(be.progress(0) > 0 && be.progress(0) == be.progress(1),
                    "les deux œufs devaient avancer ensemble, vus " + be.progress(0) + " et " + be.progress(1));
            helper.assertTrue(be.progress(2) == 0 && be.progress(3) == 0, "les cases vides ne comptent pas");
            helper.assertTrue(avant - be.energy().getEnergyStored() >= be.progress(0) * FoodConfig.incubatorEnergy(),
                    "le courant de chaque tick devait être payé");
            helper.succeed();
        });
    }

    /** Une case vidée repart de zéro, même si on y repose un œuf de la même espèce. */
    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void uneCaseVideeRepartDeZero(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.EGG);
        alimenter(be);
        fixerProgressions(be, 500, 500);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(be.progress(0) > 500 && be.progress(1) > 500, "les œufs devaient avancer");
            be.inventory().setStackInSlot(0, ItemStack.EMPTY);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(be.progress(0) == 0, "la case vidée devait repartir de zéro, vue " + be.progress(0));
            helper.assertTrue(be.progress(1) > 500, "l'autre œuf devait garder sa progression, vue " + be.progress(1));
            be.inventory().setStackInSlot(0, new ItemStack(Items.EGG));
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(be.progress(0) > 0 && be.progress(0) < 10,
                    "le nouvel œuf devait partir de zéro, vu " + be.progress(0));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Ce qui entre, ce qui reste
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "couveuse_a")
    public static void unObjetQuelconqueEstRefuse(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        helper.assertTrue(!be.inventory().isItemValid(0, new ItemStack(Items.STONE)), "la pierre ne doit pas être valide");
        helper.assertTrue(be.inventory().isItemValid(0, new ItemStack(Items.EGG)), "l'œuf de poule doit être valide");
        helper.assertTrue(be.inventory().isItemValid(0, new ItemStack(Items.TURTLE_EGG)), "l'œuf de tortue doit être valide");
        helper.assertTrue(!be.inventory().isItemValid(0, new ItemStack(Items.SPIDER_EYE)), "un œil d'araignée n'est pas un œuf");
        IItemHandler dessus = handler(be, Direction.UP);
        helper.assertTrue(dessus.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "la pierre ne doit pas entrer");
        // Un œuf par case : sur une pile de cinq, quatre restent dans la main.
        ItemStack reste = dessus.insertItem(0, new ItemStack(Items.EGG, 5), false);
        helper.assertTrue(reste.getCount() == 4, "un seul œuf devait entrer par case, restes " + reste.getCount());
        helper.assertTrue(dessus.extractItem(0, 1, false).isEmpty(), "le dessus ne doit rien extraire");
        helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1, "l'œuf devait rester en place");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "couveuse_a")
    public static void seulLeDessusInsereEtAucuneFaceNExtrait(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG);
        for (Direction cote : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            helper.assertTrue(be.getCapability(ForgeCapabilities.ITEM_HANDLER, cote).resolve().isEmpty(),
                    "la face " + cote + " ne devait rien exposer");
        }
        helper.assertTrue(handler(be, Direction.UP).extractItem(0, 1, false).isEmpty(), "le dessus ne doit rien extraire");
        helper.assertTrue(!be.inventory().getStackInSlot(0).isEmpty(), "l'œuf devait rester");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "couveuse_a", setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantLaProgressionReste(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.progress(0) == 0, "rien ne devait avancer sans courant, vu " + be.progress(0));
        });
        int cout = FoodConfig.incubatorEnergy();
        // Cinq ticks de courant, puis plus rien.
        helper.runAfterDelay(5, () -> be.energy().receiveEnergy(cout * 5, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.progress(0) == 5, "cinq ticks de travail attendus, vus " + be.progress(0));
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.progress(0) == 5, "la progression devait attendre le courant, vue " + be.progress(0));
            helper.assertTrue(!be.inventory().getStackInSlot(0).isEmpty(), "l'œuf devait rester");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // La coupure
    // ---------------------------------------------------------------------------------------------

    /** 59 secondes sans courant, puis le courant revient : rien n'est perdu et le compteur repart de zéro. */
    @GameTest(template = "test/plancher", batch = "couveuse_b", setupTicks = 20, timeoutTicks = 200)
    public static void uneCoupureCourteNePerdRien(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG, Items.EGG, Items.TURTLE_EGG);
        fixerCoupure(be, 59 * 20);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.outageTicks() > 59 * 20, "le compteur devait avancer, vu " + be.outageTicks());
            helper.assertTrue(oeufsPresents(be) == 4, "les quatre œufs devaient être là, vus " + oeufsPresents(be));
            alimenter(be);
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.assertTrue(be.outageTicks() == 0, "le compteur devait revenir à zéro, vu " + be.outageTicks());
            helper.assertTrue(oeufsPresents(be) == 4, "les quatre œufs devaient être là, vus " + oeufsPresents(be));
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "couveuse_b", setupTicks = 20, timeoutTicks = 200)
    public static void uneCoupureDePlusDUneMinutePerdLaCouvee(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG, Items.EGG, Items.TURTLE_EGG);
        fixerProgressions(be, 100, 200, 300, 400);
        // À deux ticks du seuil : il n'est pas encore dépassé.
        fixerCoupure(be, FoodConfig.incubatorOutageTicks() - 2);
        helper.runAfterDelay(1, () -> helper.assertTrue(oeufsPresents(be) == 4, "la couvée ne devait pas encore être perdue"));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(oeufsPresents(be) == 0, "la couvée devait être perdue, œufs restants : " + oeufsPresents(be));
            for (int i = 0; i < IncubatorBlockEntity.SLOTS; i++) {
                helper.assertTrue(be.progress(i) == 0, "la progression de la case " + i + " devait être effacée");
            }
            helper.assertTrue(be.outageTicks() == 0, "le compteur devait être remis à zéro, vu " + be.outageTicks());
            helper.assertTrue(animaux(helper, EntityType.CHICKEN).isEmpty() && animaux(helper, EntityType.TURTLE).isEmpty(),
                    "aucun animal ne devait naître d'une couvée perdue");
            List<ItemEntity> auSol = helper.getEntities(EntityType.ITEM, POS, 1.5);
            helper.assertTrue(totalAuSol(auSol, Items.EGG) == 0 && totalAuSol(auSol, Items.TURTLE_EGG) == 0,
                    "les œufs ne devaient pas être rendus");
            helper.succeed();
        });
    }

    /** En panne, la couvée reste même sans courant, et le compteur de coupure ne bouge pas. */
    @GameTest(template = "test/plancher", batch = "couveuse_b", setupTicks = 20, timeoutTicks = 300)
    public static void enPanneLaCouveeResteMemeSansCourant(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG, Items.EGG, Items.TURTLE_EGG);
        int seuil = FoodConfig.incubatorOutageTicks();
        fixerCoupure(be, seuil - 5);
        be.breakDown(FaultType.values()[0]);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(be.outageTicks() == seuil - 5, "le compteur devait rester figé, vu " + be.outageTicks());
            helper.assertTrue(oeufsPresents(be) == 4, "la couvée devait rester, œufs : " + oeufsPresents(be));
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "couveuse_b", setupTicks = 20, timeoutTicks = 200)
    public static void laCoupureSurvitAuRechargement(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG);
        fixerCoupure(be, 400);
        helper.runAfterDelay(8, () -> {
            int vu = be.outageTicks();
            helper.assertTrue(vu > 400, "le compteur devait avancer, vu " + vu);
            CompoundTag tag = be.saveWithoutMetadata();
            IncubatorBlockEntity copie = new IncubatorBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.outageTicks() == vu, "compteur relu " + copie.outageTicks() + ", attendu " + vu);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "couveuse_b")
    public static void laProgressionSurvitAuRechargement(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, new ItemStack(Items.EGG));
        be.inventory().setStackInSlot(1, new ItemStack(Items.TURTLE_EGG));
        be.inventory().setStackInSlot(3, new ItemStack(Items.EGG));
        fixerProgressions(be, 100, 2000, 0, 50);
        CompoundTag tag = be.saveWithoutMetadata();
        IncubatorBlockEntity copie = new IncubatorBlockEntity(be.getBlockPos(), be.getBlockState());
        copie.load(tag);
        int[] attendu = {100, 2000, 0, 50};
        for (int i = 0; i < attendu.length; i++) {
            helper.assertTrue(copie.progress(i) == attendu[i],
                    "progression de la case " + i + " relue " + copie.progress(i) + ", attendue " + attendu[i]);
            helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                    "case " + i + " différente après rechargement");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Sans place
    // ---------------------------------------------------------------------------------------------

    /** Machine couverte et murée : l'œuf mûr attend. Dès qu'une place se libère, l'animal sort et l'œuf ne part qu'une fois. */
    @GameTest(template = "test/plancher", batch = "couveuse_b", setupTicks = 20, timeoutTicks = 300)
    public static void sansPlaceLOeufAttend(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        BlockPos dessus = POS.above();
        for (BlockPos voisin : new BlockPos[] {dessus, POS.north(), POS.south(), POS.east(), POS.west()}) {
            helper.setBlock(voisin, Blocks.STONE);
        }
        poserOeufs(be, Items.EGG);
        alimenter(be);
        presqueMurs(be, 1);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(animaux(helper, EntityType.CHICKEN).isEmpty(), "aucun poussin ne pouvait sortir");
            helper.assertTrue(!be.inventory().getStackInSlot(0).isEmpty(), "l'œuf mûr devait attendre dans sa case");
            helper.assertTrue(be.progress(0) >= FoodConfig.incubatorHatchTicks(), "l'œuf devait rester mûr, vu " + be.progress(0));
            helper.setBlock(dessus, Blocks.AIR);
        });
        // Le poussin n'est pas retenu : il se promène et peut sortir du rayon de quatre blocs en quelques secondes. On suit
        // donc chaque poussin vu près de la couveuse, au lieu de recompter ceux qui sont encore autour.
        List<Chicken> vus = new ArrayList<>();
        helper.onEachTick(() -> animaux(helper, EntityType.CHICKEN).stream().filter(c -> !vus.contains(c)).forEach(vus::add));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(vus.size() == 1, "un poussin attendu une fois la place libérée, vus " + vus.size());
            helper.assertTrue(be.inventory().getStackInSlot(0).isEmpty(), "l'œuf devait être parti");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(vus.size() == 1, "l'œuf ne devait éclore qu'une fois, vus " + vus.size());
            helper.assertTrue(!vus.get(0).isRemoved(), "le poussin devait être encore là");
            reussirEtNettoyer(helper);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Le bloc et son menu
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "couveuse_b")
    public static void casseElleRendSesOeufs(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        poserOeufs(be, Items.EGG, Items.TURTLE_EGG, Items.EGG);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(totalAuSol(objets, Items.EGG) == 2, "2 œufs de poule attendus au sol");
            helper.assertTrue(totalAuSol(objets, Items.TURTLE_EGG) == 1, "1 œuf de tortue attendu au sol");
            helper.succeed();
        });
    }

    /** Le clic majuscule range les œufs dans leurs cases (un par case), refuse le reste, et marque la machine modifiée. */
    @GameTest(template = "test/plancher", batch = "couveuse_b")
    public static void leClicMajusculeFonctionne(GameTestHelper helper) {
        IncubatorBlockEntity be = poser(helper);
        Player joueur = helper.makeMockPlayer();
        IncubatorMenu menu = new IncubatorMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        joueur.getInventory().setItem(0, new ItemStack(Items.EGG, 3)); // case de menu 31 : barre d'action
        joueur.getInventory().setItem(1, new ItemStack(Items.STONE, 3));
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
        helper.runAfterDelay(4, () -> {
            chunk.setUnsaved(false);
            helper.assertTrue(!menu.quickMoveStack(joueur, IncubatorBlockEntity.SLOTS + 27).isEmpty(),
                    "les œufs devaient passer dans la couveuse");
            helper.assertTrue(oeufsPresents(be) == 3, "3 cases garnies attendues, vues " + oeufsPresents(be));
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1, "un seul œuf par case");
            helper.assertTrue(chunk.isUnsaved(), "le clic majuscule devait marquer la couveuse modifiée");
            helper.assertTrue(menu.quickMoveStack(joueur, IncubatorBlockEntity.SLOTS + 28).isEmpty(), "la pierre ne devait pas passer");
            helper.assertTrue(joueur.getInventory().getItem(1).getCount() == 3, "le joueur devait garder sa pierre");
            helper.assertTrue(!menu.getSlot(3).mayPlace(new ItemStack(Items.STONE)), "la case refuse la pierre");
            helper.assertTrue(!menu.quickMoveStack(joueur, 0).isEmpty(), "l'œuf devait pouvoir ressortir");
            helper.assertTrue(be.inventory().getStackInSlot(0).isEmpty(), "la case devait être vidée");
            helper.succeed();
        });
    }
}
