package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.InlineEnergyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le bloc de passage du courant en ligne. Le bloc d'essai regarde vers le nord : sa sortie touche
 * la machine d'essai posée au nord, son entrée est la face sud. Les contrôles sont synchrones, dans
 * le tick où l'on pose les blocs : aucune machine n'a encore consommé quoi que ce soit.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class InlineGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos INLINE = new BlockPos(1, 1, 2);
    private static final Direction ENTREE = Direction.SOUTH;

    private InlineGameTests() {}

    private static TestInlineBlockEntity poserEnLigne(GameTestHelper helper) {
        helper.setBlock(INLINE, DevBlocks.testInline().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.NORTH));
        TestInlineBlockEntity be = (TestInlineBlockEntity) helper.getBlockEntity(INLINE);
        helper.assertTrue(be != null, "pas de block entity du bloc en ligne");
        return be;
    }

    private static TestMachineBlockEntity poserMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, DevBlocks.testMachine());
        TestMachineBlockEntity be = (TestMachineBlockEntity) helper.getBlockEntity(MACHINE);
        helper.assertTrue(be != null, "pas de block entity de la machine d'essai");
        return be;
    }

    private static IEnergyStorage entree(GameTestHelper helper, TestInlineBlockEntity be) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, ENTREE).resolve().orElse(null);
        helper.assertTrue(cap != null, "pas de capability sur la face d'entrée");
        return cap;
    }

    @GameTest(template = "test/plancher", batch = "inline_traverse")
    public static void leCourantTraverseLeBloc(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper);
        int recu = entree(helper, be).receiveEnergy(100, false);
        helper.assertTrue(recu == 100, "retour : " + recu);
        helper.assertTrue(machine.energy().getEnergyStored() == 100,
                "énergie de la machine : " + machine.energy().getEnergyStored());
        helper.assertTrue(be.totalTransferred() == 100, "total : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_sans_voisin")
    public static void sansVoisinLeCourantEstRefuse(GameTestHelper helper) {
        TestInlineBlockEntity be = poserEnLigne(helper);
        int recu = entree(helper, be).receiveEnergy(100, false);
        helper.assertTrue(recu == 0, "retour : " + recu);
        helper.assertTrue(be.totalTransferred() == 0, "total : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_pleine")
    public static void faceAUneMachinePleineRienNeSePerd(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper);
        for (int i = 0; i < 9; i++) {
            machine.energy().receiveEnergy(100, false);
        }
        machine.energy().receiveEnergy(50, false); // 950 / 1000 : la place pour 50 FE seulement
        int avant = machine.energy().getEnergyStored();
        int recu = entree(helper, be).receiveEnergy(100, false);
        int gagne = machine.energy().getEnergyStored() - avant;
        helper.assertTrue(avant == 950, "énergie avant : " + avant);
        helper.assertTrue(recu == 50, "retour : " + recu);
        helper.assertTrue(gagne == recu, "la machine a gagné " + gagne + " pour un retour de " + recu);
        helper.assertTrue(be.totalTransferred() == recu, "total : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_simulation")
    public static void laSimulationNeTransmetRien(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper);
        int recu = entree(helper, be).receiveEnergy(100, true);
        helper.assertTrue(recu == 100, "retour simulé : " + recu);
        helper.assertTrue(machine.energy().getEnergyStored() == 0,
                "énergie de la machine : " + machine.energy().getEnergyStored());
        helper.assertTrue(be.totalTransferred() == 0, "total : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_ferme")
    public static void fermeRienNePasse(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper);
        be.fermer(true);
        int recu = entree(helper, be).receiveEnergy(100, false);
        helper.assertTrue(recu == 0, "retour : " + recu);
        helper.assertTrue(machine.energy().getEnergyStored() == 0,
                "énergie de la machine : " + machine.energy().getEnergyStored());
        helper.assertTrue(be.totalTransferred() == 0, "total : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_sauvegarde")
    public static void leTotalSurvitAuRechargement(GameTestHelper helper) {
        poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper);
        entree(helper, be).receiveEnergy(70, false);
        helper.assertTrue(be.totalTransferred() == 70, "le total est nul, le test ne prouve rien");
        CompoundTag tag = be.saveWithoutMetadata();
        TestInlineBlockEntity copie = new TestInlineBlockEntity(INLINE, be.getBlockState());
        copie.load(tag);
        helper.assertTrue(copie.totalTransferred() == be.totalTransferred(),
                "total rechargé : " + copie.totalTransferred() + " au lieu de " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "inline_plafond")
    public static void lePlafondParTickEstRespecte(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        TestInlineBlockEntity be = poserEnLigne(helper); // plafond de 100 FE par tick
        IEnergyStorage cap = entree(helper, be);
        int premier = cap.receiveEnergy(80, false);
        int simule = cap.receiveEnergy(80, true);
        int second = cap.receiveEnergy(80, false);
        helper.assertTrue(premier == 80, "premier appel : " + premier);
        helper.assertTrue(simule == 20, "simulation du second appel : " + simule);
        helper.assertTrue(second == 20, "second appel : " + second);
        helper.assertTrue(machine.energy().getEnergyStored() == 100,
                "énergie de la machine : " + machine.energy().getEnergyStored());
        helper.assertTrue(be.totalTransferred() == 100, "total : " + be.totalTransferred());
        helper.succeed();
    }
}
