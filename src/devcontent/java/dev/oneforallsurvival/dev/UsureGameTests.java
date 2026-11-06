package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlockEntity;
import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.WorkshopEntry;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.machine.RepairOutcome;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'usure et les pannes du socle des machines, éprouvées par la machine d'essai (10 FE par tick).
 * Les rappels de {@code runAfterDelay} d'un même tick s'exécutent dans un ordre arbitraire :
 * chaque contrôle a son propre tick, et les bornes laissent un tick d'écart là où il en faut un.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class UsureGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private UsureGameTests() {}

    private static TestMachineBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, DevBlocks.testMachine());
        TestMachineBlockEntity be = (TestMachineBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la machine d'essai");
        return be;
    }

    private static void remplir(TestMachineBlockEntity be) {
        for (int i = 0; i < 10; i++) {
            be.energy().receiveEnergy(100, false);
        }
    }

    @GameTest(template = "test/plancher", batch = "usure_marche")
    public static void lEtatNeBaisseQuEnMarche(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.condition() == 100.0, "sans courant l'état devait rester à 100, vu " + be.condition());
            remplir(be);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.condition() < 100.0, "avec courant l'état devait baisser, vu " + be.condition());
            helper.assertTrue(be.condition() > 99.0, "l'usure d'un tick est minime, vu " + be.condition());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "usure_rendement")
    public static void sousCinquanteLeRendementBaisse(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.etat(40);
        helper.assertTrue(be.efficiency() == MachineConfig.reducedEfficiency(), "rendement à 40 % : " + be.efficiency());
        remplir(be);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.progress() >= 28 && be.progress() <= 32,
                    "progression sur 40 ticks à 0,75 : entre 28 et 32 attendu, vu " + be.progress());
            int consomme = 1000 - be.energy().getEnergyStored();
            helper.assertTrue(consomme >= 390 && consomme <= 410,
                    "l'énergie se paie à chaque tick : environ 400 FE attendus, vus " + consomme);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "usure_panne")
    public static void aZeroLaMachineTombeEnPanne(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        FaultType annoncee = be.nextFault();
        be.etat(0.0001);
        remplir(be);
        int[] reserve = new int[1];
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(be.fault() == annoncee, "panne attendue " + annoncee + ", vue " + be.fault());
            helper.assertTrue(be.condition() == 0.0, "l'état devait être à 0, vu " + be.condition());
            reserve[0] = be.energy().getEnergyStored();
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(be.energy().getEnergyStored() == reserve[0],
                    "en panne la consommation devait s'arrêter : " + reserve[0] + " puis " + be.energy().getEnergyStored());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "usure_flammes", timeoutTicks = 400)
    public static void lesFlammesNePosentAucunBlocEtSEteignent(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.allumer();
        helper.assertTrue(be.flammes() == MachineConfig.flameTicks(), "flammes allumées : " + be.flammes());
        helper.runAfterDelay(10, () -> helper.assertTrue(be.flammes() > 0, "les flammes devaient durer"));
        helper.runAfterDelay(MachineConfig.flameTicks() + 5, () -> {
            helper.assertTrue(be.flammes() == 0, "les flammes devaient être éteintes, vu " + be.flammes());
            helper.assertTrue(helper.getBlockEntity(POS) == be, "la machine devait être toujours là");
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos p = POS.offset(x, y, z);
                        helper.assertTrue(!helper.getBlockState(p).is(Blocks.FIRE)
                                && !helper.getBlockState(p).is(Blocks.SOUL_FIRE), "un bloc de feu en " + p);
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "usure_sauvegarde")
    public static void lEtatSurvitAuRechargement(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        helper.assertTrue(be.repair(be.nextFault(), true) == RepairOutcome.REPAIRED, "la réparation traitée devait réussir");
        be.breakDown(FaultType.WINDING);
        be.etat(37);
        helper.runAfterDelay(2, () -> {
            CompoundTag tag = be.saveWithoutMetadata();
            TestMachineBlockEntity copie = new TestMachineBlockEntity(POS, be.getBlockState());
            copie.load(tag);
            helper.assertTrue(be.isProtected() && be.fault() == FaultType.WINDING && be.condition() == 37.0,
                    "l'original devait porter l'état à prouver");
            helper.assertTrue(copie.condition() == 37.0, "état rechargé : " + copie.condition());
            helper.assertTrue(copie.fault() == FaultType.WINDING, "panne rechargée : " + copie.fault());
            helper.assertTrue(copie.nextFault() == be.nextFault(),
                    "prochaine panne rechargée : " + copie.nextFault() + " au lieu de " + be.nextFault());
            helper.assertTrue(copie.isProtected(), "la protection devait survivre");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "usure_reparation")
    public static void laReparationVerifieLaPiece(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        FaultType annoncee = be.nextFault();
        FaultType autre = annoncee == FaultType.FUSE ? FaultType.BEARING : FaultType.FUSE;
        helper.assertTrue(be.repair(annoncee, false) == RepairOutcome.NOTHING_TO_DO, "neuve et non traitée : rien à faire");
        helper.assertTrue(be.repair(autre, true) == RepairOutcome.WRONG_PART, "mauvaise pièce attendue");
        helper.assertTrue(be.repair(annoncee, true) == RepairOutcome.REPAIRED, "traiter une machine neuve devait réussir");
        helper.assertTrue(be.isProtected(), "la machine devait être protégée");
        helper.assertTrue(be.repair(be.nextFault(), true) == RepairOutcome.NOTHING_TO_DO, "déjà protégée : rien à faire");
        be.breakDown(FaultType.BEARING);
        helper.assertTrue(be.repair(FaultType.FUSE, false) == RepairOutcome.WRONG_PART, "il fallait un roulement");
        helper.assertTrue(be.repair(FaultType.BEARING, false) == RepairOutcome.REPAIRED, "le roulement devait réparer");
        helper.assertTrue(be.fault() == null && be.condition() == 100.0 && !be.isProtected(),
                "réparée sans traitement : à neuf, sans panne, non protégée");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "usure_etabli_panne")
    public static void lEtablienPanneContinueALaMain(GameTestHelper helper) {
        helper.setBlock(POS, BenchRegistry.ENGINEER_BENCH.get());
        EngineerBenchBlockEntity banc = (EngineerBenchBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(banc != null, "pas de block entity de l'établi");
        for (int i = 0; i < 10; i++) {
            banc.energy().receiveEnergy(100, false);
        }
        banc.breakDown(FaultType.FUSE);
        PlayerWorkshop atelier = WorkshopSavedData.get(helper.getLevel().getServer()).of(UUID.randomUUID());
        ItemStack fer = new ItemStack(Items.IRON_INGOT, 3);
        WorkshopEntry entree = new WorkshopEntry(UUID.randomUUID(), new ResourceLocation("oneforallsurvival", "test_cmd_longue"),
                new ItemStack(Items.EMERALD), List.of(fer.copy()), List.of(), 12000, 0.0, helper.getLevel().dimension(),
                helper.absolutePos(POS), true);
        atelier.add(entree);
        double[] depart = new double[1];
        helper.runAfterDelay(5, () -> depart[0] = entree.doneTicks());
        helper.runAfterDelay(15, () -> {
            double delta = entree.doneTicks() - depart[0];
            helper.assertTrue(banc.state() == MachineState.FAULT, "FAULT attendu, vu " + banc.state());
            helper.assertTrue(delta == 10.0, "à la main : 10 attendus en 10 ticks, vus " + delta);
            helper.assertTrue(banc.energy().getEnergyStored() == 1000, "l'établi en panne ne consomme rien");
            var rendue = atelier.cancel(entree.id());
            helper.assertTrue(rendue.isPresent(), "l'entrée devait s'annuler");
            helper.assertTrue(ItemStack.matches(rendue.get().paid().get(0), fer), "l'ingrédient payé devait être intact");
            helper.succeed();
        });
    }
}
