package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.repair.MachineReport;
import dev.oneforallsurvival.repair.RepairActions;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La trousse de diagnostic : testeur, poste à souder et pièces, avec un vrai joueur non opérateur.
 * Chaque test a son lot, donc s'exécute seul, et fait quitter son joueur à la fin.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ReparationGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);

    private ReparationGameTests() {}

    private static TestMachineBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, DevBlocks.testMachine());
        TestMachineBlockEntity be = (TestMachineBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la machine d'essai");
        return be;
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom, boolean ingenieur) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        RoleSavedData.get(helper.getLevel().getServer()).book().assign(j.uuid(),
                ingenieur ? RoleBook.ENGINEER_ROLE_ID : RoleBook.DEFAULT_ROLE_ID);
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être opérateur");
        return j;
    }

    private static ItemStack poste(int fe) {
        ItemStack pile = new ItemStack(RepairRegistry.WELDER.get());
        WelderEnergy.store(pile, fe);
        return pile;
    }

    private static ItemStack piece(FaultType type, boolean traitee) {
        return new ItemStack(RepairRegistry.partItem(type, traitee));
    }

    /** Aplatit un texte traduisible : clé et arguments, pour les chercher sans fichier de langue. */
    private static String aplatir(Component c) {
        StringBuilder sb = new StringBuilder();
        if (c.getContents() instanceof TranslatableContents t) {
            sb.append(t.getKey()).append(' ');
            for (Object arg : t.getArgs()) {
                sb.append(arg instanceof Component ac ? aplatir(ac) : String.valueOf(arg)).append(' ');
            }
        } else {
            sb.append(c.getString()).append(' ');
        }
        for (Component s : c.getSiblings()) {
            sb.append(aplatir(s));
        }
        return sb.toString();
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_bonne_piece")
    public static void laBonnePieceReparePuisEstConsommee(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Mecano", true);
            ItemStack poste = poste(1000);
            ItemStack fusible = piece(FaultType.FUSE, false);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, fusible);
            helper.assertTrue(r == RepairActions.Result.REPAIRED, "REPAIRED attendu, vu " + r);
            helper.assertTrue(WelderEnergy.stored(poste) == 1000 - MachineConfig.repairEnergy(),
                    "400 FE retirés attendus, reste " + WelderEnergy.stored(poste));
            helper.assertTrue(be.condition() == 100.0, "état 100 attendu, vu " + be.condition());
            helper.assertTrue(be.fault() == null, "plus de panne attendue, vue " + be.fault());
            helper.assertTrue(fusible.isEmpty(), "la pièce devait être consommée");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_mauvaise_piece")
    public static void laMauvaisePieceEstRefuseeSansRienConsommer(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Mecano", true);
            ItemStack poste = poste(1000);
            ItemStack roulement = piece(FaultType.BEARING, false);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, roulement);
            helper.assertTrue(r == RepairActions.Result.WRONG_PART, "WRONG_PART attendu, vu " + r);
            helper.assertTrue(roulement.getCount() == 1, "la pièce devait rester");
            helper.assertTrue(WelderEnergy.stored(poste) == 1000, "aucun FE ne devait partir");
            helper.assertTrue(be.fault() == FaultType.FUSE && be.condition() == 0.0, "la machine devait rester en panne");
            RepairActions.Result vide = RepairActions.repair(j.joueur(), be, poste, ItemStack.EMPTY);
            helper.assertTrue(vide == RepairActions.Result.NO_PART, "NO_PART attendu, vu " + vide);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_sans_batterie")
    public static void sansBatterieRienNestConsomme(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Mecano", true);
            ItemStack poste = poste(MachineConfig.repairEnergy() - 1);
            ItemStack fusible = piece(FaultType.FUSE, false);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, fusible);
            helper.assertTrue(r == RepairActions.Result.NO_ENERGY, "NO_ENERGY attendu, vu " + r);
            helper.assertTrue(fusible.getCount() == 1, "la pièce devait rester");
            helper.assertTrue(WelderEnergy.stored(poste) == MachineConfig.repairEnergy() - 1, "la batterie devait rester");
            helper.assertTrue(be.fault() == FaultType.FUSE, "la panne devait rester");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_citoyen")
    public static void unCitoyenNePeutPasReparer(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Citoyen", false);
            ItemStack poste = poste(1000);
            ItemStack fusible = piece(FaultType.FUSE, false);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, fusible);
            helper.assertTrue(r == RepairActions.Result.NOT_ENGINEER, "NOT_ENGINEER attendu, vu " + r);
            helper.assertTrue(fusible.getCount() == 1 && WelderEnergy.stored(poste) == 1000, "rien ne devait être consommé");
            helper.assertTrue(be.fault() == FaultType.FUSE, "la panne devait rester");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_lecture")
    public static void unCitoyenPeutLireLEtat(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.BEARING);
        StringBuilder texte = new StringBuilder();
        List<Component> lignes = MachineReport.lines(be);
        for (Component c : lignes) {
            texte.append(aplatir(c)).append('\n');
        }
        String t = texte.toString();
        helper.assertTrue(t.contains("fault.oneforallsurvival.bearing"), "la panne devait figurer :\n" + t);
        helper.assertTrue(t.contains(RepairRegistry.BEARING.get().getDescriptionId()),
                "la pièce à prévoir devait figurer :\n" + t);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "reparation_traitee")
    public static void laPieceTraiteeProtegeEtLaNormaleRetire(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Mecano", true);
            ItemStack poste = poste(1000);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, piece(FaultType.FUSE, true));
            helper.assertTrue(r == RepairActions.Result.REPAIRED, "REPAIRED attendu, vu " + r);
            helper.assertTrue(be.isProtected(), "la machine devait être protégée");
            be.breakDown(FaultType.FUSE);
            r = RepairActions.repair(j.joueur(), be, poste, piece(FaultType.FUSE, false));
            helper.assertTrue(r == RepairActions.Result.REPAIRED, "REPAIRED attendu au 2e cycle, vu " + r);
            helper.assertTrue(!be.isProtected(), "la pièce normale devait retirer la protection");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_machine_neuve")
    public static void laPieceTraiteeSePoseSurUneMachineNeuve(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        FaultType attendue = be.nextFault();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Mecano", true);
            ItemStack poste = poste(1000);
            ItemStack normale = piece(attendue, false);
            RepairActions.Result r = RepairActions.repair(j.joueur(), be, poste, normale);
            helper.assertTrue(r == RepairActions.Result.NOTHING_TO_DO, "NOTHING_TO_DO attendu, vu " + r);
            helper.assertTrue(normale.getCount() == 1 && WelderEnergy.stored(poste) == 1000,
                    "rien ne devait être consommé");
            ItemStack traitee = piece(attendue, true);
            r = RepairActions.repair(j.joueur(), be, poste, traitee);
            helper.assertTrue(r == RepairActions.Result.REPAIRED, "REPAIRED attendu, vu " + r);
            helper.assertTrue(be.isProtected() && traitee.isEmpty(), "protégée et pièce consommée attendues");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_testeur")
    public static void leTesteurNOuvrePasLEtabli(GameTestHelper helper) {
        helper.setBlock(POS, BenchRegistry.ENGINEER_BENCH.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Citoyen", false);
            ServerPlayer p = j.joueur();
            ItemStack testeur = new ItemStack(RepairRegistry.TESTER.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, testeur);
            BlockPos abs = helper.absolutePos(POS);
            UseOnContext ctx = new UseOnContext(helper.getLevel(), p, InteractionHand.MAIN_HAND, testeur,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            InteractionResult r = testeur.getItem().onItemUseFirst(testeur, ctx);
            helper.assertTrue(r.consumesAction(), "le testeur devait agir avant le bloc, vu " + r);
            helper.assertTrue(p.containerMenu == p.inventoryMenu, "l'établi ne devait pas s'ouvrir");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "reparation_recharge")
    public static void lePosteSeRechargeSurUneMachine(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.breakDown(FaultType.FUSE); // en panne la machine ne consomme pas : sa réserve reste celle qu'on lui met
        for (int i = 0; i < 10; i++) {
            be.energy().receiveEnergy(100, false);
        }
        helper.assertTrue(be.energy().getEnergyStored() == 1000, "la machine devait avoir 1000 FE");
        ItemStack poste = poste(0);
        int pris = RepairActions.charge(poste, be, null);
        helper.assertTrue(pris == 1000, "1000 FE attendus, pris " + pris);
        helper.assertTrue(WelderEnergy.stored(poste) == 1000, "poste à 1000 FE attendu, vu " + WelderEnergy.stored(poste));
        helper.assertTrue(be.energy().getEnergyStored() == 0, "la machine devait être à 0, vue " + be.energy().getEnergyStored());
        IEnergyStorage cap = poste.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        helper.assertTrue(cap != null, "le poste devait exposer la capability d'énergie");
        helper.assertTrue(cap.receiveEnergy(5000, false) == MachineConfig.welderCapacity() - 1000,
                "la réception devait être plafonnée à la capacité");
        helper.assertTrue(cap.getEnergyStored() == MachineConfig.welderCapacity(), "poste plein attendu");
        helper.assertTrue(cap.extractEnergy(100, false) == 0 && !cap.canExtract(), "rien ne doit sortir du poste");
        helper.succeed();
    }
}
