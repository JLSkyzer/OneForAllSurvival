package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.light.LightConfig;
import dev.oneforallsurvival.light.LightRegistry;
import dev.oneforallsurvival.light.LighthouseBlock;
import dev.oneforallsurvival.light.LighthouseBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le phare. Le monde d'essai est plat et bas : le phare est posé à une hauteur absolue au-dessus du niveau de la mer
 * ({@link MachineConfig#seaLevel()} plus la marge de {@link LightConfig}), dans le ciel libre au-dessus du gabarit, et
 * chaque test le retire à la fin. Tout GameTest qui exige que la machine ait tourné porte {@code setupTicks = 20}.
 *
 * <p>Le phare ne marche que la nuit et l'heure est globale (les tests d'un lot tournent ensemble, les lots l'un après
 * l'autre) : le lot {@value #LOT} (les huit tests qui supposent un phare allumé) est mis à la nuit par son
 * {@link BeforeBatch}, le test de jour et celui de l'aube ont chacun le leur ; chaque {@link AfterBatch} rend l'heure
 * d'avant. {@code skyDarken} (qui fait la nuit pour {@code Level.isNight}) se recalcule au tick qui suit le changement
 * d'heure : les tests ont {@code setupTicks = 20} et l'aube attend l'état.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PhareGameTests {

    private static final String LOT = "phare";
    private static final String LOT_JOUR = "phare_jour";
    private static final String LOT_AUBE = "phare_aube";
    private static final long NUIT = 18_000L;
    private static final long JOUR = 6_000L;

    /** L'heure d'avant le lot en cours, rendue par l'{@link AfterBatch} ; {@code null} si rien n'a été réglé. */
    private static Long heureAvant;

    private PhareGameTests() {}

    private static void regler(ServerLevel level, long heure) {
        if (heureAvant == null) {
            heureAvant = level.getDayTime();
        }
        level.setDayTime(heure);
    }

    private static void rendre(ServerLevel level) {
        if (heureAvant != null) {
            level.setDayTime(heureAvant);
            heureAvant = null;
        }
    }

    @BeforeBatch(batch = LOT)
    public static void nuitPourLeLot(ServerLevel level) {
        regler(level, NUIT);
    }

    @AfterBatch(batch = LOT)
    public static void apresLeLot(ServerLevel level) {
        rendre(level);
    }

    @BeforeBatch(batch = LOT_JOUR)
    public static void jourPourLeLotDeJour(ServerLevel level) {
        regler(level, JOUR);
    }

    @AfterBatch(batch = LOT_JOUR)
    public static void apresLeLotDeJour(ServerLevel level) {
        rendre(level);
    }

    @BeforeBatch(batch = LOT_AUBE)
    public static void nuitPourLAube(ServerLevel level) {
        regler(level, NUIT);
    }

    @AfterBatch(batch = LOT_AUBE)
    public static void apresLAube(ServerLevel level) {
        rendre(level);
    }

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
        helper.assertTrue(helper.getLevel().isNight(), "le lot devait être de nuit (skyDarken "
                + helper.getLevel().getSkyDarken() + ")");
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        // Le bloc ne s'allume qu'après LIT_HOLD_TICKS ticks de marche : 40 laisse de la marge.
        helper.runAfterDelay(40, () -> {
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

    /**
     * Un phare posé sous un toit déjà en place ne consomme rien. Le toit est posé et la lumière du ciel attendue AVANT le
     * phare : {@code Level.canSeeSky} lit la lumière du ciel, que le moteur de lumière met à jour un tick après le
     * {@code setBlock} (relevé tick par tick au 2026-11-12 : {@code canSeeSky} vrai au premier tick du phare, faux dès
     * le suivant, d'où 32 FE pris). Poser les deux dans le même tick testait la course du moteur de lumière, pas le
     * phare ; le cas du toit qui arrive sur un phare en marche est {@link #sousUnToitApresCoup}.
     */
    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void sousUnToitIlNeConsommeRien(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        helper.setBlock(pos.above(), Blocks.STONE);
        LighthouseBlockEntity[] be = new LighthouseBlockEntity[1];
        int[] avant = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!helper.getLevel().canSeeSky(helper.absolutePos(pos.above())),
                        "la lumière du ciel devait se mettre à jour sous le toit"))
                .thenExecute(() -> {
                    be[0] = poser(helper, pos);
                    alimenter(be[0]);
                    avant[0] = be[0].energy().getEnergyStored();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(be[0].state() == MachineState.STOPPED, "STOPPED attendu, vu " + be[0].state());
                        helper.assertTrue(!allume(helper, pos), "le phare ne devait pas être allumé");
                        helper.assertTrue(be[0].energy().getEnergyStored() == avant[0],
                                "rien ne devait être consommé : " + avant[0] + " puis " + be[0].energy().getEnergyStored());
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantIlSEteint(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare devait d'abord s'allumer"))
                .thenExecute(() -> be.drainEnergy(1_000_000))
                // Sans courant il s'éteint après LIT_HOLD_TICKS ticks (hystérésis), pas tout de suite.
                .thenWaitUntil(() -> helper.assertTrue(!allume(helper, pos), "le phare sans courant devait s'éteindre"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
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
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare devait d'abord s'allumer"))
                .thenExecute(() -> be.breakDown(FaultType.values()[0]))
                .thenWaitUntil(() -> helper.assertTrue(!allume(helper, pos), "le phare en panne devait s'éteindre"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }

    /**
     * Le toit posé ou retiré après coup : le phare suit. « Voir le ciel » est la lumière du ciel au-dessus du phare
     * ({@code Level.canSeeSky}), que le moteur de lumière met à jour de façon asynchrone : relevé au 2026-11-11, elle
     * passe de 15 à 0 deux ticks après la pose du toit. Le test a échoué deux fois de suite à « STOPPED attendu, vu
     * RUNNING » avec un délai fixe de cinq ticks ; qu'elle dépasse cinq ticks sous la charge de la suite complète est
     * une hypothèse non mesurée. Le test attend donc les états (jusqu'à {@code timeoutTicks}) au lieu d'un délai fixe ; il exige les mêmes états finals.
     */
    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 400)
    public static void sousUnToitApresCoup(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare devait d'abord s'allumer"))
                .thenExecute(() -> {
                    try {
                        helper.setBlock(pos.above(), Blocks.STONE);
                    } catch (RuntimeException e) {
                        nettoyer(helper, pos);
                        throw e;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state()))
                .thenExecute(() -> {
                    try {
                        // Pas d'hystérésis ici : la tour n'est plus libre, le phare s'éteint tout de suite.
                        helper.assertTrue(!allume(helper, pos), "le phare couvert devait s'éteindre");
                        helper.setBlock(pos.above(), Blocks.AIR);
                    } catch (RuntimeException e) {
                        nettoyer(helper, pos);
                        throw e;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state()))
                // Le rallumage attend LIT_HOLD_TICKS ticks de marche : état attendu, délai borné par timeoutTicks.
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare découvert devait se rallumer"))
                .thenExecute(() -> nettoyer(helper, pos))
                .thenSucceed();
    }

    /**
     * Une source plus faible que le besoin (20 FE par tick pour 32) fait alterner RUNNING et NO_POWER : le bloc ne doit
     * pas clignoter. Le phare s'allume d'abord sur sa réserve pleine, qu'on vide, puis le test injecte 20 FE à chaque
     * tick pendant 100 ticks et compte les changements de {@code lit} (au plus un) et les états vus (la preuve que la
     * source alternait bien). L'injection et le relevé sont posés d'emblée (pas d'{@code onEachTick} dans un rappel).
     */
    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 400)
    public static void sousAlimenteIlNeClignotePas(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        boolean[] mesure = {false};
        boolean[] dernier = {false};
        int[] changements = {0};
        boolean[] vus = new boolean[MachineState.values().length];
        helper.onEachTick(() -> {
            if (!mesure[0]) {
                return;
            }
            be.energy().receiveEnergy(20, false);
            boolean maintenant = allume(helper, pos);
            if (maintenant != dernier[0]) {
                changements[0]++;
                dernier[0] = maintenant;
            }
            vus[be.state().ordinal()] = true;
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare devait d'abord s'allumer"))
                .thenExecute(() -> {
                    be.drainEnergy(1_000_000);
                    dernier[0] = allume(helper, pos);
                    mesure[0] = true;
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(20 < LightConfig.lighthouseEnergy(),
                                "le montage suppose une source plus faible que le besoin");
                        helper.assertTrue(vus[MachineState.RUNNING.ordinal()] && vus[MachineState.NO_POWER.ordinal()],
                                "la source devait faire alterner RUNNING et NO_POWER");
                        helper.assertTrue(changements[0] <= 1,
                                "lit ne devait pas changer plus d'une fois sur 100 ticks, vu " + changements[0]);
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void tropBas(GameTestHelper helper) {
        // Un bloc sous le seuil, en plein ciel : seule la hauteur l'empêche de marcher.
        int absolu = MachineConfig.seaLevel() + LightConfig.lighthouseMinAboveSea() - 1;
        BlockPos pos = new BlockPos(1, absolu - helper.absolutePos(BlockPos.ZERO).getY(), 1);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
                helper.assertTrue(!allume(helper, pos), "le phare trop bas ne devait pas être allumé");
                helper.assertTrue(be.energy().getEnergyStored() == avant,
                        "rien ne devait être consommé : " + avant + " puis " + be.energy().getEnergyStored());
            } finally {
                nettoyer(helper, pos);
            }
            helper.succeed();
        });
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

    /** De jour, la tour est libre et la source alimente : le phare ne veut pas travailler, ne consomme et ne s'use pas. */
    @GameTest(template = "test/plancher", batch = LOT_JOUR, setupTicks = 20, timeoutTicks = 200)
    public static void deJourIlResteEteint(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        double usure = be.condition();
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(helper.getLevel().isDay(), "le lot devait être de jour (skyDarken "
                        + helper.getLevel().getSkyDarken() + ")");
                helper.assertTrue(LighthouseBlock.isFreeTower(helper.getLevel(), helper.absolutePos(pos)),
                        "la tour devait être libre");
                helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
                helper.assertTrue(!allume(helper, pos), "le phare ne devait pas être allumé de jour");
                helper.assertTrue(be.energy().getEnergyStored() == avant,
                        "rien ne devait être consommé : " + avant + " puis " + be.energy().getEnergyStored());
                helper.assertTrue(be.condition() == usure, "il ne devait pas s'user : " + usure + " puis " + be.condition());
            } finally {
                nettoyer(helper, pos);
            }
            helper.succeed();
        });
    }

    /**
     * Allumé de nuit, il s'éteint à l'aube : tout de suite, sans l'hystérésis de {@value LighthouseBlockEntity#LIT_HOLD_TICKS}
     * ticks (le jour qui se lève est un refus de travailler, comme un toit). Cinq ticks après le changement d'heure
     * (un pour que {@code skyDarken} se recalcule, un pour le phare) il doit être éteint.
     */
    @GameTest(template = "test/plancher", batch = LOT_AUBE, setupTicks = 20, timeoutTicks = 300)
    public static void alAubeIlSEteintToutDeSuite(GameTestHelper helper) {
        BlockPos pos = haut(helper);
        LighthouseBlockEntity be = poser(helper, pos);
        alimenter(be);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(allume(helper, pos), "le phare devait d'abord s'allumer de nuit"))
                .thenExecute(() -> helper.getLevel().setDayTime(JOUR))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(helper.getLevel().isDay(), "il devait faire jour (skyDarken "
                                + helper.getLevel().getSkyDarken() + ")");
                        helper.assertTrue(!allume(helper, pos), "le phare devait être éteint 5 ticks après l'aube");
                        helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
                    } finally {
                        nettoyer(helper, pos);
                    }
                })
                .thenSucceed();
    }
}
