package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.coast.CoastDetector;
import dev.oneforallsurvival.debug.IslandDebugInfo;
import dev.oneforallsurvival.debug.IslandDebugLimiter;
import dev.oneforallsurvival.debug.IslandDebugRequest;
import dev.oneforallsurvival.debug.IslandDebugResponse;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.machine.MachineConfig;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les données Lost Island du F3, de la requête aux octets de la réponse : de vrais joueurs non opérateurs sur une
 * connexion embarquée. Chaque test a ses propres joueurs : le débit se compte par joueur.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class IslandDebugGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos COMPTEUR = new BlockPos(1, 1, 2);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 4);
    /** La hauteur des yeux d'un joueur debout. */
    private static final double YEUX = 1.62;

    private IslandDebugGameTests() {}

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static TestMachineBlockEntity poserMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, DevBlocks.testMachine());
        verifier(helper.getBlockEntity(MACHINE) instanceof TestMachineBlockEntity, "pas de machine d'essai");
        return (TestMachineBlockEntity) helper.getBlockEntity(MACHINE);
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom, boolean ingenieur) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        RoleSavedData.get(helper.getLevel().getServer()).book().assign(j.uuid(),
                ingenieur ? RoleBook.ENGINEER_ROLE_ID : RoleBook.DEFAULT_ROLE_ID);
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        verifier(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être opérateur");
        return j;
    }

    private static void quitter(JoueurDeTest... joueurs) {
        for (JoueurDeTest j : joueurs) {
            if (j != null && j.connecte()) {
                j.quitter();
            }
        }
    }

    /** Une requête, et l'unique réponse qu'elle doit produire. */
    private static IslandDebugResponse demander(JoueurDeTest j, BlockPos cible) {
        j.envoyer(new IslandDebugRequest(cible));
        List<IslandDebugResponse> recues = j.reponsesDebugRecues();
        verifier(recues.size() == 1, "une réponse attendue, reçu " + recues.size());
        return recues.get(0);
    }

    /** Place les yeux du joueur à {@code distance} blocs du centre de {@code cible}, au sud, à la même hauteur. */
    private static void placerAuSud(JoueurDeTest j, BlockPos cible, double distance) {
        Vec3 centre = Vec3.atCenterOf(cible);
        j.joueur().moveTo(centre.x, centre.y - YEUX, centre.z + distance);
    }

    @GameTest(template = "test/plancher")
    public static void unIngenieurRecoitLUsure(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        BlockPos cible = helper.absolutePos(MACHINE);
        JoueurDeTest ingenieur = joueur(helper, "f3_inge", true);
        try {
            machine.takeDamage(25);
            IslandDebugResponse usee = demander(ingenieur, cible);
            verifier(cible.equals(usee.target()), "la réponse doit nommer sa cible : " + usee.target());
            IslandDebugInfo.Machine vue = usee.info().machine();
            verifier(vue != null, "la machine visée manque");
            verifier(vue.wearPercent() != null && Math.abs(vue.wearPercent() - 25f) < 0.01f,
                    "usure attendue 25 : " + vue.wearPercent());
            verifier(vue.failure() == null, "pas de panne attendue : " + vue.failure());

            machine.breakDown(FaultType.BEARING);
            IslandDebugInfo.Machine panne = demander(ingenieur, cible).info().machine();
            verifier(panne != null && panne.failure() == FaultType.BEARING, "la panne manque : " + panne);
            verifier(panne.wearPercent() != null && Math.abs(panne.wearPercent() - 100f) < 0.01f,
                    "hors d'usage attendu : " + panne.wearPercent());
        } finally {
            quitter(ingenieur);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher")
    public static void unCitoyenNeRecoitPasLUsure(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        machine.takeDamage(25);
        machine.breakDown(FaultType.WINDING);
        BlockPos cible = helper.absolutePos(MACHINE);
        // L'état de la machine se recalcule à son tick.
        helper.runAfterDelay(2, () -> {
            JoueurDeTest citoyen = joueur(helper, "f3_citoyen", false);
            try {
                IslandDebugInfo.Machine vue = demander(citoyen, cible).info().machine();
                verifier(vue != null, "la machine visée manque");
                verifier(vue.state() == MachineState.FAULT, "l'état visible doit rester donné : " + vue.state());
                verifier(vue.wearPercent() == null, "un citoyen a reçu l'usure : " + vue.wearPercent());
                verifier(vue.failure() == null, "un citoyen a reçu la panne : " + vue.failure());
            } finally {
                quitter(citoyen);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher")
    public static void unCitoyenLitLAbonneEtLeDebitMaisPasLIndex(GameTestHelper helper) {
        poserMachine(helper);
        helper.setBlock(COMPTEUR, GridRegistry.METER.get().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.NORTH));
        MeterBlockEntity compteur = (MeterBlockEntity) helper.getBlockEntity(COMPTEUR);
        compteur.setSubscriber("Mme Essai");
        IEnergyStorage entree = compteur.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).resolve().orElse(null);
        verifier(entree != null && entree.receiveEnergy(60, false) == 60, "le courant devait passer");
        BlockPos cible = helper.absolutePos(COMPTEUR);
        JoueurDeTest ingenieur = joueur(helper, "f3_inge_cpt", true);
        JoueurDeTest citoyen = joueur(helper, "f3_citoyen_cpt", false);
        try {
            IslandDebugInfo.Meter lu = demander(ingenieur, cible).info().meter();
            verifier(lu != null && "Mme Essai".equals(lu.subscriber()) && lu.fePerTick() == 60
                    && Long.valueOf(60).equals(lu.index()), "lecture de l'ingénieur : " + lu);
            IslandDebugInfo.Meter vu = demander(citoyen, cible).info().meter();
            verifier(vu != null && "Mme Essai".equals(vu.subscriber()) && vu.fePerTick() == 60,
                    "un citoyen devait voir l'abonné et le débit : " + vu);
            verifier(vu.index() == null, "un citoyen a reçu l'index : " + vu.index());
        } catch (RuntimeException e) {
            quitter(ingenieur, citoyen);
            throw e;
        }
        // Plus rien ne passe aux ticks suivants : le débit retombe, l'index reste.
        helper.runAfterDelay(2, () -> {
            try {
                IslandDebugInfo.Meter apres = demander(ingenieur, cible).info().meter();
                verifier(apres != null && apres.fePerTick() == 0 && Long.valueOf(60).equals(apres.index()),
                        "sans courant, débit nul et index gardé : " + apres);
            } finally {
                quitter(ingenieur, citoyen);
            }
            helper.succeed();
        });
    }

    /** Lot à part : la règle de jeu et le détecteur côtier sont globaux au serveur. */
    @GameTest(template = "test/plancher", batch = "f3_reduit")
    public static void sousReducedDebugInfoLaReponseEstVide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameRules.BooleanValue regle = level.getGameRules().getRule(GameRules.RULE_REDUCEDDEBUGINFO);
        poserMachine(helper);
        BlockPos cible = helper.absolutePos(MACHINE);
        JoueurDeTest ingenieur = joueur(helper, "f3_reduit", true);
        Map<BlockPos, BlockState> avant = CoteGameTests.poserEau(level,
                new BlockPos(cible.getX() + 3, MachineConfig.seaLevel(), cible.getZ()), true);
        try {
            CoastDetector.clearCache();
            IslandDebugResponse temoin = demander(ingenieur, cible);
            verifier(temoin.info().coastZone() && temoin.info().machine() != null, "témoin sans la règle : " + temoin);

            regle.set(true, level.getServer());
            IslandDebugResponse reduite = demander(ingenieur, cible);
            verifier(reduite.target() == null, "la règle active, pas de cible : " + reduite.target());
            verifier(IslandDebugInfo.EMPTY.equals(reduite.info()), "la règle active, réponse vide : " + reduite.info());
        } finally {
            regle.set(false, level.getServer());
            CoteGameTests.rendre(level, avant);
            CoastDetector.clearCache();
            quitter(ingenieur);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher")
    public static void leDisjoncteurEstVisibleDeTous(GameTestHelper helper) {
        helper.setBlock(COMPTEUR, GridRegistry.BREAKER.get().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.NORTH)
                .setValue(BreakerBlock.CLOSED, false).setValue(BreakerBlock.SEALED, true));
        BlockPos cible = helper.absolutePos(COMPTEUR);
        JoueurDeTest citoyen = joueur(helper, "f3_citoyen_dj", false);
        try {
            IslandDebugInfo.Breaker vu = demander(citoyen, cible).info().breaker();
            verifier(vu != null && !vu.closed() && vu.sealed(), "disjoncteur : " + vu);
        } finally {
            quitter(citoyen);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher")
    public static void uneCibleHorsDePorteeEstIgnoree(GameTestHelper helper) {
        poserMachine(helper);
        BlockPos cible = helper.absolutePos(MACHINE);
        JoueurDeTest ingenieur = joueur(helper, "f3_portee", true);
        try {
            double limite = ingenieur.joueur().getBlockReach() + 1;
            placerAuSud(ingenieur, cible, limite - 0.1);
            IslandDebugResponse proche = demander(ingenieur, cible);
            verifier(cible.equals(proche.target()) && proche.info().machine() != null,
                    "juste à portée, la cible devait être retenue : " + proche);

            placerAuSud(ingenieur, cible, limite + 0.1);
            IslandDebugResponse loin = demander(ingenieur, cible);
            verifier(loin.target() == null, "hors de portée, la cible devait être ignorée : " + loin.target());
            verifier(loin.info().machine() == null, "hors de portée, rien sur la machine : " + loin.info().machine());
        } finally {
            quitter(ingenieur);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher")
    public static void uneCibleDansUnChunkNonChargeEstIgnoree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 depart = helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR));
        BlockPos cible = helper.absolutePos(MACHINE).offset(1_000_000, 0, 0);
        verifier(level.getChunkSource().getChunkNow(cible.getX() >> 4, cible.getZ() >> 4) == null,
                "le chunk lointain devait être non chargé");
        JoueurDeTest ingenieur = joueur(helper, "f3_chunk", true);
        try {
            placerAuSud(ingenieur, cible, 2);
            IslandDebugResponse reponse = demander(ingenieur, cible);
            verifier(reponse.target() == null, "chunk non chargé, la cible devait être ignorée : " + reponse.target());
            verifier(level.getChunkSource().getChunkNow(cible.getX() >> 4, cible.getZ() >> 4) == null,
                    "la requête a chargé le chunk");
        } finally {
            // Le joueur ne doit pas quitter là-bas : sa sortie toucherait le chunk lointain.
            ingenieur.joueur().moveTo(depart.x, depart.y, depart.z);
            quitter(ingenieur);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher")
    public static void lesRequetesEnRafaleSontLimitees(GameTestHelper helper) {
        BlockPos cible = helper.absolutePos(MACHINE);
        JoueurDeTest citoyen = joueur(helper, "f3_rafale", false);
        for (int i = 0; i < 10; i++) {
            citoyen.envoyer(new IslandDebugRequest(cible));
        }
        int passees = citoyen.reponsesDebugRecues().size();
        if (passees != IslandDebugLimiter.MAX_PER_WINDOW) {
            quitter(citoyen);
            throw new GameTestAssertException("en rafale, " + IslandDebugLimiter.MAX_PER_WINDOW + " réponses attendues : "
                    + passees);
        }
        helper.runAfterDelay(IslandDebugLimiter.WINDOW_TICKS + 1, () -> {
            try {
                demander(citoyen, cible);
            } finally {
                quitter(citoyen);
            }
            helper.succeed();
        });
    }

    /** Lot à part : le détecteur côtier est global au serveur. */
    @GameTest(template = "test/plancher", batch = "f3_cote")
    public static void laZoneCotiereEstDonnee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        poserMachine(helper);
        BlockPos cible = helper.absolutePos(MACHINE);
        JoueurDeTest citoyen = joueur(helper, "f3_cote", false);
        Map<BlockPos, BlockState> avant = null;
        try {
            CoastDetector.clearCache();
            IslandDebugResponse sec = demander(citoyen, cible);
            verifier(!sec.info().coastZone(), "sans eau, le joueur ne devait pas être en zone côtière");
            verifier(sec.info().machine() != null && !sec.info().machine().coastal(), "sans eau, machine côtière");

            avant = CoteGameTests.poserEau(level, new BlockPos(cible.getX() + 3, MachineConfig.seaLevel(), cible.getZ()),
                    true);
            CoastDetector.clearCache();
            IslandDebugResponse mouille = demander(citoyen, cible);
            verifier(mouille.info().coastZone(), "près de l'eau salée, le joueur devait être en zone côtière");
            verifier(mouille.info().machine() != null && mouille.info().machine().coastal(),
                    "près de l'eau salée, la machine devait être côtière");

            IslandDebugResponse sansCible = demander(citoyen, null);
            verifier(sansCible.target() == null && sansCible.info().machine() == null, "sans cible, rien sur la cible");
            verifier(sansCible.info().coastZone(), "sans cible, la zone reste donnée");
        } finally {
            if (avant != null) {
                CoteGameTests.rendre(level, avant);
            }
            CoastDetector.clearCache();
            quitter(citoyen);
        }
        helper.succeed();
    }
}
