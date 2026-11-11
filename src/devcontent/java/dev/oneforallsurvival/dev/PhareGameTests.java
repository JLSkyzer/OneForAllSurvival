package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.light.LightConfig;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.light.LighthouseBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le phare. Le monde d'essai est plat et bas : le phare est posé à une hauteur absolue au-dessus du niveau de la mer
 * ({@link MachineConfig#seaLevel()} plus la marge de {@link LightConfig}), dans le ciel libre au-dessus du gabarit, et
 * chaque test le retire à la fin. Tout GameTest qui exige que la machine ait tourné porte {@code setupTicks = 20}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PhareGameTests {

    private static final String LOT = "phare";

    private PhareGameTests() {}

    /** La position (relative au gabarit) du phare : assez haut pour être en tour libre. */
    private static BlockPos haut(GameTestHelper helper) {
        int absolu = MachineConfig.seaLevel() + LightConfig.lighthouseMinAboveSea() + 4;
        int origine = helper.absolutePos(BlockPos.ZERO).getY();
        return new BlockPos(1, absolu - origine, 1);
    }

    private static LighthouseBlockEntity poser(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, LightRegistry.LIGHTHOUSE.get());
        LighthouseBlockEntity be = (LighthouseBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity du phare");
        return be;
    }

    private static void alimenter(LighthouseBlockEntity be) {
        for (int i = 0; i < 100; i++) {
            be.energy().receiveEnergy(LighthouseBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static boolean allume(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(LighthouseBlock.LIT);
    }

    private static void nettoyer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.above(), Blocks.AIR);
        helper.setBlock(pos, Blocks.AIR);
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void libreIlSAllume(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
                helper.assertTrue(allume(helper, pos), "le phare devait être allumé");
                BlockState etat = helper.getBlockState(pos);
                helper.assertTrue(etat.getLightEmission() == LighthouseBlock.LIGHT,
                        "lumière " + LighthouseBlock.LIGHT + " attendue, vue " + etat.getLightEmission());
                int consomme = avant - be.energy().getEnergyStored();
                helper.assertTrue(consomme > 0 && consomme % LightConfig.lighthouseEnergy() == 0,
                        "des multiples de " + LightConfig.lighthouseEnergy() + " FE attendus, consommé " + consomme);
            } finally {
                nettoyer(helper, pos);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void sousUnToitIlNeConsommeRien(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        helper.setBlock(pos.above(), Blocks.STONE);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
                helper.assertTrue(!allume(helper, pos), "le phare ne devait pas être allumé");
                helper.assertTrue(be.energy().getEnergyStored() == avant,
                        "rien ne devait être consommé : " + avant + " puis " + be.energy().getEnergyStored());
            } finally {
                nettoyer(helper, pos);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantIlSEteint(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(allume(helper, pos), "le phare devait d'abord être allumé");
                    be.drainEnergy(1_000_000);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
                        helper.assertTrue(!allume(helper, pos), "le phare sans courant devait s'éteindre");
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void enPanneIlSEteint(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(allume(helper, pos), "le phare devait d'abord être allumé");
                    be.breakDown(FaultType.values()[0]);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
                        helper.assertTrue(!allume(helper, pos), "le phare en panne devait s'éteindre");
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void etatSurvitAuRechargement(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.runAfterDelay(30, () -> {
            try {
                CompoundTag tag = be.saveWithoutMetadata();
                BlockState etat = helper.getBlockState(pos);
                LighthouseBlockEntity copie = new LighthouseBlockEntity(helper.absolutePos(pos), etat);
                copie.load(tag);
                helper.assertTrue(etat.getValue(LighthouseBlock.LIT), "le bloc allumé devait garder sa propriété");
                helper.assertTrue(copie.state() == MachineState.RUNNING,
                        "RUNNING attendu après relecture, vu " + copie.state());
                helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
                helper.assertTrue(copie.condition() == be.condition(), "usure perdue");
            } finally {
                nettoyer(helper, pos);
            }
            helper.succeed();
        });
    }
}
