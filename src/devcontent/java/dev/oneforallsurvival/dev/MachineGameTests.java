package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La machine électrique de base, éprouvée par la machine d'essai (10 FE par tick, capacité 1000,
 * 100 FE reçus au plus par appel). Les rappels de {@code runAfterDelay} d'un même tick s'exécutent
 * dans un ordre arbitraire : chaque contrôle a son propre tick, et l'égalité exacte n'est exigée
 * que là où elle est déterminée.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MachineGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private MachineGameTests() {}

    private static TestMachineBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, DevBlocks.testMachine());
        TestMachineBlockEntity be = (TestMachineBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la machine d'essai");
        return be;
    }

    private static void exiger(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    @GameTest(template = "test/plancher", batch = "machine_pause")
    public static void sansCourantLaMachineEstEnPauseSansPerdreSaProgression(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.energy().receiveEnergy(100, false);
        // 100 FE à 10 FE/t : dix ticks de travail. Au dixième, la machine tourne encore (ou vient de finir).
        helper.runAfterDelay(10, () -> {
            exiger(helper, be.progress() >= 9 && be.progress() <= 10, "progression au tick 10 : " + be.progress());
            exiger(helper, be.state() == MachineState.RUNNING, "état au tick 10 : " + be.state());
        });
        // Réserve épuisée : le compte est exact et ne bouge plus.
        helper.runAfterDelay(15, () -> {
            exiger(helper, be.progress() == 10, "progression au tick 15 : " + be.progress());
            exiger(helper, be.state() == MachineState.NO_POWER, "état au tick 15 : " + be.state());
        });
        helper.runAfterDelay(35, () -> {
            exiger(helper, be.progress() == 10, "progression au tick 35 : " + be.progress());
            exiger(helper, be.state() == MachineState.NO_POWER, "état au tick 35 : " + be.state());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "machine_reprise")
    public static void leCourantLaFaitRepartir(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.energy().receiveEnergy(100, false);
        helper.runAfterDelay(15, () -> {
            exiger(helper, be.state() == MachineState.NO_POWER, "état avant recharge : " + be.state());
            exiger(helper, be.progress() == 10, "progression avant recharge : " + be.progress());
            be.energy().receiveEnergy(100, false);
        });
        helper.runAfterDelay(20, () -> {
            exiger(helper, be.state() == MachineState.RUNNING, "état après recharge : " + be.state());
            exiger(helper, be.progress() > 10, "progression après recharge : " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "machine_sauvegarde")
    public static void laProgressionEtLaReserveSurviventAuRechargement(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.energy().receiveEnergy(100, false);
        helper.runAfterDelay(5, () -> {
            // Lecture et sauvegarde dans le même tick : les deux instances comparées sont figées.
            CompoundTag tag = be.saveWithoutMetadata();
            TestMachineBlockEntity copie = new TestMachineBlockEntity(POS, be.getBlockState());
            copie.load(tag);
            exiger(helper, be.energy().getEnergyStored() > 0, "la réserve est vide, le test ne prouve rien");
            exiger(helper, copie.energy().getEnergyStored() == be.energy().getEnergyStored(),
                    "énergie rechargée : " + copie.energy().getEnergyStored() + " au lieu de " + be.energy().getEnergyStored());
            exiger(helper, be.progress() > 0, "la progression est nulle, le test ne prouve rien");
            exiger(helper, copie.progress() == be.progress(),
                    "progression rechargée : " + copie.progress() + " au lieu de " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "machine_capability")
    public static void laCapabilityRefuseDExtraire(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.panne(true); // la machine ne consomme rien pendant la vérification
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, null).resolve().orElse(null);
        exiger(helper, cap != null, "pas de capability d'énergie");
        cap.receiveEnergy(100, false);
        exiger(helper, cap.extractEnergy(10, false) == 0, "l'extérieur a pu extraire de l'énergie");
        exiger(helper, cap.getEnergyStored() == 100, "énergie après tentative d'extraction : " + cap.getEnergyStored());
        for (int i = 0; i < 20; i++) {
            cap.receiveEnergy(100, false);
        }
        exiger(helper, cap.getEnergyStored() == cap.getMaxEnergyStored(),
                "énergie " + cap.getEnergyStored() + " pour une capacité de " + cap.getMaxEnergyStored());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "machine_panne")
    public static void enPanneLaMachineNeConsommeRien(GameTestHelper helper) {
        TestMachineBlockEntity be = poser(helper);
        be.energy().receiveEnergy(100, false);
        be.panne(true);
        helper.runAfterDelay(3, () -> {
            exiger(helper, be.state() == MachineState.FAULT, "état en panne : " + be.state());
            exiger(helper, be.energy().getEnergyStored() == 100, "énergie à 3 ticks : " + be.energy().getEnergyStored());
        });
        helper.runAfterDelay(8, () -> {
            exiger(helper, be.state() == MachineState.FAULT, "état en panne : " + be.state());
            exiger(helper, be.energy().getEnergyStored() == 100, "énergie à 8 ticks : " + be.energy().getEnergyStored());
            exiger(helper, be.progress() == 0, "progression en panne : " + be.progress());
            helper.succeed();
        });
    }
}
