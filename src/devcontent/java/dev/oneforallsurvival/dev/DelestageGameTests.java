package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.GridConfig;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LoadShedderBlock;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.LoadShedderMenu;
import dev.oneforallsurvival.grid.OutputStatus;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.MachineReport;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le tableau de délestage. Il regarde vers le nord : entrée au sud (arrière), sorties devant (nord), à gauche (est,
 * vue de devant), à droite (ouest) et dessus. Les sorties sont des machines d'essai : 1 000 FE de réserve, 100 FE
 * au plus par appel. Les essais qui lisent l'affichage poussent dans un tick et lisent au suivant : l'état et les FE
 * d'une sortie sont ceux du dernier tick achevé.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DelestageGameTests {

    private static final BlockPos TABLEAU = new BlockPos(2, 1, 2);
    private static final BlockPos DEVANT = TABLEAU.north();
    private static final BlockPos GAUCHE = TABLEAU.east();
    private static final BlockPos DROITE = TABLEAU.west();
    private static final BlockPos DESSUS = TABLEAU.above();
    private static final Direction ARRIERE = Direction.SOUTH;

    private static final int F = LoadShedderBlockEntity.OUTPUT_FRONT;
    private static final int G = LoadShedderBlockEntity.OUTPUT_LEFT;
    private static final int D = LoadShedderBlockEntity.OUTPUT_RIGHT;
    private static final int H = LoadShedderBlockEntity.OUTPUT_TOP;

    private DelestageGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static LoadShedderBlockEntity poser(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, GridRegistry.LOAD_SHEDDER.get().defaultBlockState().setValue(LoadShedderBlock.FACING, facing));
        verifier(helper.getBlockEntity(pos) instanceof LoadShedderBlockEntity, "pas de tableau de délestage en " + pos);
        return (LoadShedderBlockEntity) helper.getBlockEntity(pos);
    }

    private static LoadShedderBlockEntity poserTableau(GameTestHelper helper) {
        return poser(helper, TABLEAU, Direction.NORTH);
    }

    private static TestMachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        // Une machine déjà là ne serait pas remplacée par la même : on vide la case d'abord.
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, DevBlocks.testMachine());
        verifier(helper.getBlockEntity(pos) instanceof TestMachineBlockEntity, "pas de machine d'essai en " + pos);
        return (TestMachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static TestMachineBlockEntity[] quatreMachines(GameTestHelper helper) {
        return new TestMachineBlockEntity[] {machine(helper, DEVANT), machine(helper, GAUCHE), machine(helper, DROITE),
                machine(helper, DESSUS)};
    }

    private static IEnergyStorage entree(LoadShedderBlockEntity be, Direction cote) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, cote).resolve().orElse(null);
        verifier(cap != null, "pas d'énergie sur la face " + cote);
        return cap;
    }

    private static int stocke(TestMachineBlockEntity m) {
        return m.energy().getEnergyStored();
    }

    private static String stocks(TestMachineBlockEntity... machines) {
        return Arrays.toString(Arrays.stream(machines).mapToInt(DelestageGameTests::stocke).toArray());
    }

    /** Pousse {@code fois} fois {@code paquet} FE dans le même tick ; renvoie le total accepté. */
    private static int pousser(IEnergyStorage entree, int paquet, int fois) {
        int total = 0;
        for (int i = 0; i < fois; i++) {
            total += entree.receiveEnergy(paquet, false);
        }
        return total;
    }

    /** Recharge le tableau avec un état d'usure donné (l'usure n'a pas d'accès public en écriture). */
    private static void abimer(LoadShedderBlockEntity be, double etat) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putDouble("condition", etat);
        be.load(tag);
    }

    private static void etats(LoadShedderBlockEntity be, OutputStatus devant, OutputStatus gauche,
            OutputStatus droite, OutputStatus dessus) {
        OutputStatus[] attendus = {devant, gauche, droite, dessus};
        OutputStatus[] vus = {be.status(F), be.status(G), be.status(D), be.status(H)};
        verifier(Arrays.equals(attendus, vus), "états attendus " + Arrays.toString(attendus) + ", vus "
                + Arrays.toString(vus));
    }

    private static void livres(LoadShedderBlockEntity be, int devant, int gauche, int droite, int dessus) {
        int[] attendus = {devant, gauche, droite, dessus};
        int[] vus = {be.lastDelivered(F), be.lastDelivered(G), be.lastDelivered(D), be.lastDelivered(H)};
        verifier(Arrays.equals(attendus, vus), "FE affichés attendus " + Arrays.toString(attendus) + ", vus "
                + Arrays.toString(vus));
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "delestage_ordre")
    public static void lesSortiesSontServiesDansLOrdre(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        TestMachineBlockEntity[] m = quatreMachines(helper);
        for (Direction cote : Direction.values()) {
            boolean expose = be.getCapability(ForgeCapabilities.ENERGY, cote).isPresent();
            verifier(expose == (cote == ARRIERE), "énergie sur la face " + cote + " : " + expose);
        }
        verifier(!be.getCapability(ForgeCapabilities.ENERGY, null).isPresent(), "énergie sans face exposée");
        verifier(Arrays.equals(be.priorities(), new int[] {F, G, D, H}),
                "ordre d'usine attendu devant, gauche, droite, dessus ; vu " + Arrays.toString(be.priorities()));
        int recu = entree(be, ARRIERE).receiveEnergy(250, false);
        verifier(recu == 250, "250 FE devaient passer, vu " + recu);
        verifier(stocke(m[0]) == 100 && stocke(m[1]) == 100 && stocke(m[2]) == 50 && stocke(m[3]) == 0,
                "attendu [100, 100, 50, 0], vu " + stocks(m));
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "delestage_manque", setupTicks = 20)
    public static void quandIlManqueLesDernieresSontCoupees(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        machine(helper, DEVANT);
        machine(helper, GAUCHE);
        machine(helper, DROITE);
        int recu = entree(be, ARRIERE).receiveEnergy(150, false);
        verifier(recu == 150, "150 FE devaient passer, vu " + recu);
        helper.runAfterDelay(1, () -> {
            etats(be, OutputStatus.FED, OutputStatus.PARTIAL, OutputStatus.CUT, OutputStatus.NONE);
            livres(be, 100, 50, 0, 0);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "delestage_rang")
    public static void changerUnRangEchangeLesDeux(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        TestMachineBlockEntity[] m = quatreMachines(helper);
        be.setPriority(H, 0);
        verifier(Arrays.equals(be.priorities(), new int[] {H, G, D, F}),
                "dessus au premier rang, devant au dernier ; vu " + Arrays.toString(be.priorities()));
        verifier(be.rank(H) == 0 && be.rank(F) == 3, "rangs : dessus " + be.rank(H) + ", devant " + be.rank(F));
        // Par l'écran : le bouton « monter » de la sortie gauche, comme le client l'enverrait.
        Player joueur = helper.makeMockPlayer();
        LoadShedderMenu menu = new LoadShedderMenu(0, joueur.getInventory(), be, helper.absolutePos(TABLEAU));
        verifier(menu.rank(G) == 1, "le menu lit le rang de la gauche : " + menu.rank(G));
        verifier(menu.clickMenuButton(joueur, LoadShedderMenu.buttonId(G, true)), "le bouton devait agir");
        verifier(Arrays.equals(be.priorities(), new int[] {G, H, D, F}),
                "gauche et dessus échangés ; vu " + Arrays.toString(be.priorities()));
        verifier(!menu.clickMenuButton(joueur, LoadShedderMenu.buttonId(G, true)),
                "le premier rang ne monte pas plus haut");
        verifier(!menu.clickMenuButton(joueur, LoadShedderMenu.buttonId(F, false)),
                "le dernier rang ne descend pas plus bas");
        verifier(!menu.clickMenuButton(joueur, 99), "un bouton inconnu ne fait rien");
        int recu = entree(be, ARRIERE).receiveEnergy(150, false);
        verifier(recu == 150, "150 FE devaient passer, vu " + recu);
        verifier(stocke(m[1]) == 100 && stocke(m[3]) == 50 && stocke(m[2]) == 0 && stocke(m[0]) == 0,
                "attendu gauche 100, dessus 50, le reste à 0 ; vu " + stocks(m));
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "delestage_poussees", setupTicks = 20)
    public static void plusieursPoussesRespectentLaPriorite(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        TestMachineBlockEntity[] m = quatreMachines(helper);
        abimer(be, 40);
        int plafond = (int) Math.floor(GridConfig.shedderMaxPerTick() * be.efficiency());
        verifier(plafond < 4000, "plafond " + plafond + " : les quatre machines ne le dépassent pas, rien n'est prouvé");
        IEnergyStorage entree = entree(be, ARRIERE);
        int total = pousser(entree, 100, 15);
        verifier(total == 1500 && stocke(m[0]) == 1000 && stocke(m[1]) == 500 && stocke(m[2]) == 0
                && stocke(m[3]) == 0, "après 15 poussées : " + total + " FE, " + stocks(m));
        total += pousser(entree, 100, 30);
        verifier(total == plafond, "le plafond du tick est " + plafond + ", passé " + total);
        verifier(stocke(m[0]) == 1000 && stocke(m[1]) == 1000 && stocke(m[2]) == 1000
                && stocke(m[3]) == plafond - 3000, "attendu les trois premières pleines, vu " + stocks(m));
        int simule = entree.receiveEnergy(100, true);
        verifier(simule == 0, "plafond atteint, la simulation devait renvoyer 0, vu " + simule);
        helper.runAfterDelay(1, () -> {
            livres(be, 1000, 1000, 1000, plafond - 3000);
            etats(be, OutputStatus.FED, OutputStatus.FED, OutputStatus.FED, OutputStatus.PARTIAL);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "delestage_simulation", setupTicks = 20)
    public static void uneSimulationNeChangeRien(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        TestMachineBlockEntity devant = machine(helper, DEVANT);
        TestMachineBlockEntity gauche = machine(helper, GAUCHE);
        machine(helper, DROITE);
        IEnergyStorage entree = entree(be, ARRIERE);
        int recu = entree.receiveEnergy(150, false);
        int simule = entree.receiveEnergy(150, true);
        verifier(recu == 150 && simule == 150, "reçu " + recu + ", simulé " + simule);
        verifier(stocke(devant) == 100 && stocke(gauche) == 50, "la simulation a livré : " + stocks(devant, gauche));
        helper.runAfterDelay(1, () -> {
            // Exactement l'affichage d'une seule poussée réelle.
            livres(be, 100, 50, 0, 0);
            etats(be, OutputStatus.FED, OutputStatus.PARTIAL, OutputStatus.CUT, OutputStatus.NONE);
            helper.succeed();
        });
    }

    /**
     * Quatre tableaux en carré, chacun branché par sa sortie gauche sur l'arrière du suivant : le courant qui part à
     * gauche revient sur l'entrée du premier. Ni récursion sans fin, ni énergie créée.
     */
    @GameTest(template = "test/plancher", batch = "delestage_boucle")
    public static void uneBoucleNeCreeRien(GameTestHelper helper) {
        BlockPos p1 = new BlockPos(1, 1, 1);
        LoadShedderBlockEntity t1 = poser(helper, p1, Direction.NORTH);
        poser(helper, new BlockPos(2, 1, 1), Direction.EAST);
        poser(helper, new BlockPos(2, 1, 2), Direction.SOUTH);
        poser(helper, new BlockPos(1, 1, 2), Direction.WEST);
        t1.setPriority(G, 0);
        IEnergyStorage entree = entree(t1, Direction.SOUTH);
        int seul = entree.receiveEnergy(100, false);
        int seulSimule = entree.receiveEnergy(100, true);
        verifier(seul == 0 && seulSimule == 0, "la boucle seule a accepté " + seul + " FE (simulé " + seulSimule + ")");
        TestMachineBlockEntity m = machine(helper, p1.north());
        int simule = entree.receiveEnergy(100, true);
        int recu = entree.receiveEnergy(100, false);
        verifier(simule == 100 && recu == 100, "attendu 100 FE vers la machine, simulé " + simule + ", reçu " + recu);
        verifier(stocke(m) == 100, "la machine a reçu " + stocke(m) + " FE pour 100 acceptés");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "delestage_panne", setupTicks = 20)
    public static void enPanneToutEstCoupe(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        TestMachineBlockEntity devant = machine(helper, DEVANT);
        TestMachineBlockEntity gauche = machine(helper, GAUCHE);
        be.breakDown(FaultType.FUSE);
        IEnergyStorage entree = entree(be, ARRIERE);
        int simule = entree.receiveEnergy(100, true);
        int recu = entree.receiveEnergy(100, false);
        verifier(simule == 0 && recu == 0, "en panne rien ne passe : simulé " + simule + ", reçu " + recu);
        verifier(stocke(devant) == 0 && stocke(gauche) == 0, "les sorties ont reçu " + stocks(devant, gauche));
        helper.runAfterDelay(1, () -> {
            verifier(be.state() == MachineState.FAULT, "état " + be.state());
            etats(be, OutputStatus.CUT, OutputStatus.CUT, OutputStatus.NONE, OutputStatus.NONE);
            livres(be, 0, 0, 0, 0);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "delestage_use", setupTicks = 20)
    public static void useIlLaissePasserMoins(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        quatreMachines(helper);
        int neuf = pousser(entree(be, ARRIERE), 100, 50);
        verifier(neuf == 4000, "neuf, les quatre machines devaient se remplir : " + neuf);
        helper.runAfterDelay(1, () -> {
            quatreMachines(helper);
            abimer(be, 25);
            int plafond = (int) Math.floor(GridConfig.shedderMaxPerTick() * be.efficiency());
            int use = pousser(entree(be, ARRIERE), 100, 50);
            verifier(use == plafond && use < neuf, "usé : " + use + " FE passés, plafond " + plafond + ", neuf " + neuf);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "delestage_usure", setupTicks = 20)
    public static void ilNeSUseQueQuandLeCourantPasse(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        machine(helper, DEVANT);
        IEnergyStorage entree = entree(be, ARRIERE);
        double[] apres = new double[1];
        helper.runAfterDelay(5, () -> {
            verifier(be.condition() == 100, "sans courant il s'est usé : " + be.condition());
            verifier(be.state() == MachineState.STOPPED, "sans courant, état " + be.state());
            entree.receiveEnergy(100, false);
        });
        helper.runAfterDelay(6, () -> entree.receiveEnergy(100, false));
        helper.runAfterDelay(7, () -> {
            verifier(be.state() == MachineState.RUNNING, "le courant passe, état " + be.state());
            entree.receiveEnergy(100, false);
        });
        helper.runAfterDelay(11, () -> {
            verifier(be.condition() < 100, "le courant est passé sans usure : " + be.condition());
            verifier(be.state() == MachineState.STOPPED, "plus de courant, état " + be.state());
            apres[0] = be.condition();
        });
        helper.runAfterDelay(15, () -> {
            verifier(be.condition() == apres[0], "sans courant l'usure a avancé : " + apres[0] + " puis " + be.condition());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "delestage_sauvegarde")
    public static void lesRangsSurviventAuRechargement(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        be.setPriority(D, 0);
        int[] attendus = {D, G, F, H};
        verifier(Arrays.equals(be.priorities(), attendus), "avant : " + Arrays.toString(be.priorities()));
        CompoundTag tag = be.saveWithoutMetadata();
        BlockState etat = be.getBlockState();
        LoadShedderBlockEntity copie = new LoadShedderBlockEntity(be.getBlockPos(), etat);
        copie.load(tag);
        verifier(Arrays.equals(copie.priorities(), attendus), "rechargé : " + Arrays.toString(copie.priorities()));
        // Une sauvegarde abîmée (pas une permutation) retombe sur l'ordre d'usine.
        tag.putIntArray("priorities", new int[] {0, 0, 1, 2});
        LoadShedderBlockEntity abimee = new LoadShedderBlockEntity(be.getBlockPos(), etat);
        abimee.load(tag);
        verifier(Arrays.equals(abimee.priorities(), new int[] {F, G, D, H}),
                "sauvegarde abîmée : " + Arrays.toString(abimee.priorities()));
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "delestage_casse", setupTicks = 20)
    public static void casseIlGardeSonUsure(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        abimer(be, 37.5);
        FaultType prochaine = be.nextFault();
        helper.getLevel().destroyBlock(helper.absolutePos(TABLEAU), true);
        verifier(helper.getBlockState(TABLEAU).isAir(), "le tableau devait être cassé");
        ItemStack pile = null;
        List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, TABLEAU, 3.0);
        for (ItemEntity e : objets) {
            if (e.getItem().is(GridRegistry.LOAD_SHEDDER_ITEM.get())) {
                pile = e.getItem().copy();
                e.discard();
            }
        }
        verifier(pile != null, "aucun tableau lâché, vu " + objets);
        Player joueur = helper.makeMockPlayer();
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos cible = helper.absolutePos(TABLEAU);
        InteractionResult pose = pile.useOn(new UseOnContext(joueur, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cible), Direction.UP, cible, false)));
        verifier(pose.consumesAction(), "la pose devait réussir, vu " + pose);
        verifier(helper.getBlockEntity(TABLEAU) instanceof LoadShedderBlockEntity, "pas de tableau après la pose");
        LoadShedderBlockEntity repose = (LoadShedderBlockEntity) helper.getBlockEntity(TABLEAU);
        verifier(repose.condition() == 37.5, "état attendu 37.5, vu " + repose.condition());
        verifier(repose.nextFault() == prochaine, "prochaine panne " + prochaine + " attendue, vue " + repose.nextFault());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "delestage_testeur")
    public static void leTesteurLitLeTableau(GameTestHelper helper) {
        LoadShedderBlockEntity be = poserTableau(helper);
        abimer(be, 37.5);
        Player joueur = helper.makeMockPlayer();
        ItemStack testeur = new ItemStack(RepairRegistry.TESTER.get());
        joueur.setItemInHand(InteractionHand.MAIN_HAND, testeur);
        BlockPos abs = helper.absolutePos(TABLEAU);
        InteractionResult r = testeur.getItem().onItemUseFirst(testeur, new UseOnContext(helper.getLevel(), joueur,
                InteractionHand.MAIN_HAND, testeur, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        verifier(r.consumesAction(), "le testeur devait agir avant le bloc, vu " + r);
        verifier(joueur.containerMenu == joueur.inventoryMenu, "l'écran du tableau ne devait pas s'ouvrir");
        List<Component> lignes = MachineReport.lines(be);
        verifier(!lignes.isEmpty() && lignes.get(0).getContents() instanceof TranslatableContents etat
                && etat.getKey().equals("message.oneforallsurvival.report.condition")
                && etat.getArgs().length == 1 && etat.getArgs()[0].equals(38L),
                "première ligne du rapport : " + (lignes.isEmpty() ? "aucune" : lignes.get(0)));
        helper.succeed();
    }
}
