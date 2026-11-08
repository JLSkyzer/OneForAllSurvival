package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridConfig;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.LightningEvents;
import dev.oneforallsurvival.grid.LoadShedderBlockEntity;
import dev.oneforallsurvival.grid.SurgeArresterBlockEntity;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.repair.RepairActions;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La foudre et le parafoudre. Les règles sont testées par {@link LightningEvents#strike} avec un tirage truqué, sans
 * toucher à la config : le résultat ne dépend pas du hasard. De vrais {@link LightningBolt}, ajoutés au monde puis
 * retirés aussitôt, prouvent le chemin de l'événement (éclair visuel, chunks déchargés) ; leurs assertions acceptent
 * les deux issues du tirage. Les rayons (16 et 24 blocs) débordent de la structure d'essai : chaque test a son propre
 * lot et retire en fin de test tout ce qu'il a posé, pour qu'aucun parafoudre ne reste protéger les tests suivants.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class FoudreGameTests {

    /** Point d'impact de tous les éclairs, en coordonnées de la structure. */
    private static final BlockPos ECLAIR = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);
    private static final double EPS = 1e-6;

    private FoudreGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static void etatAttendu(ElectricMachineBlockEntity be, double attendu, String quoi) {
        verifier(Math.abs(be.condition() - attendu) < EPS, quoi + " : état " + attendu + " attendu, vu " + be.condition());
    }

    /** Ce qu'un test pose, pour tout retirer à la fin. */
    private static final class Pose {
        private final GameTestHelper helper;
        private final List<BlockPos> poses = new ArrayList<>();

        Pose(GameTestHelper helper) {
            this.helper = helper;
        }

        BlockEntity bloc(BlockPos decalage, BlockState etat) {
            BlockPos rel = ECLAIR.offset(decalage);
            helper.setBlock(rel, etat);
            poses.add(rel);
            BlockEntity be = helper.getBlockEntity(rel);
            verifier(be != null, "pas de block entity en " + rel);
            return be;
        }

        TestMachineBlockEntity machine(int dx, int dy, int dz) {
            return (TestMachineBlockEntity) bloc(new BlockPos(dx, dy, dz), DevBlocks.testMachine().defaultBlockState());
        }

        SurgeArresterBlockEntity parafoudre(int dx, int dy, int dz) {
            return (SurgeArresterBlockEntity) bloc(new BlockPos(dx, dy, dz),
                    GridRegistry.SURGE_ARRESTER.get().defaultBlockState());
        }

        void toutRetirer() {
            for (BlockPos rel : poses) {
                helper.setBlock(rel, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Un tirage truqué : {@code nextDouble()} rend toujours la même valeur. */
    private static RandomSource tirage(double valeur) {
        return new SingleThreadedRandomSource(0L) {
            @Override
            public double nextDouble() {
                return valeur;
            }
        };
    }

    /** Sous tout risque de fusible non nul : le fusible grille. */
    private static final double FUSIBLE = 0.0;
    /** Au-dessus du risque par défaut (0,25) : pas de fusible, seulement les points perdus. */
    private static final double SANS_FUSIBLE = 0.999;

    /** Un éclair au point d'impact, appliqué directement avec un tirage connu : le résultat ne dépend pas du hasard. */
    private static void frapper(GameTestHelper helper, double tirage) {
        LightningEvents.strike(helper.getLevel(), Vec3.atBottomCenterOf(helper.absolutePos(ECLAIR)), tirage(tirage));
    }

    /**
     * Un vrai éclair au point, ajouté au monde puis retiré aussitôt : il passe par {@code EntityJoinLevelEvent}, avec
     * le tirage du monde. Retiré tout de suite, il ne met pas le feu et ne frappe aucune entité.
     */
    private static void vraiEclair(ServerLevel level, BlockPos absolu, boolean visuel) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        verifier(bolt != null, "impossible de créer l'éclair");
        bolt.moveTo(Vec3.atBottomCenterOf(absolu));
        bolt.setVisualOnly(visuel);
        level.addFreshEntity(bolt);
        bolt.discard();
    }

    /** Un vrai éclair a frappé cette machine neuve, quel que soit le tirage : fusible grillé, ou 25 points perdus. */
    private static void frappeeParUnVraiEclair(ElectricMachineBlockEntity be, String quoi) {
        boolean fusible = be.fault() == FaultType.FUSE && be.condition() == 0;
        boolean abimee = be.fault() == null && Math.abs(be.condition() - 75) < EPS;
        verifier(fusible || abimee, quoi + " : fusible grillé ou état 75 attendu, vu " + be.condition() + " / " + be.fault());
    }

    /** Fait tourner {@code corps}, puis retire ce qui a été posé. */
    private static void poser(GameTestHelper helper, java.util.function.Consumer<Pose> corps) {
        Pose pose = new Pose(helper);
        try {
            corps.accept(pose);
        } finally {
            pose.toutRetirer();
        }
        helper.succeed();
    }

    private static JoueurDeTest ingenieur(GameTestHelper helper) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "Mecano", helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        RoleSavedData.get(helper.getLevel().getServer()).book().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    // ---------------------------------------------------------------------------------------------
    // Dégâts de la foudre
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "foudre_proche")
    public static void laFoudreAbimeLesMachinesProches(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity machine = pose.machine(3, 0, 0);
            LoadShedderBlockEntity delesteur = (LoadShedderBlockEntity) pose.bloc(new BlockPos(0, 0, 3),
                    GridRegistry.LOAD_SHEDDER.get().defaultBlockState());
            TestMachineBlockEntity usee = pose.machine(5, 0, 5);
            usee.etat(20);
            FaultType annoncee = usee.nextFault();
            TestMachineBlockEntity enPanne = pose.machine(0, 0, 6);
            enPanne.etat(50);
            enPanne.panne(true); // fusible, état à 0 : la foudre ne s'acharne pas sur une machine déjà en panne
            FaultType panneAvant = enPanne.fault();
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(machine, 75, "la machine à 3 blocs");
            verifier(machine.fault() == null, "sans risque de fusible, pas de panne attendue, vue " + machine.fault());
            etatAttendu(delesteur, 75, "le tableau de délestage");
            etatAttendu(usee, 0, "la machine usée à 20");
            verifier(usee.fault() == annoncee, "la panne annoncée " + annoncee + " attendue, vue " + usee.fault());
            verifier(enPanne.fault() == panneAvant, "la panne en cours ne devait pas changer");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_portee")
    public static void auDelaDe16BlocsRien(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity a16 = pose.machine(16, 0, 0);
            TestMachineBlockEntity a17 = pose.machine(17, 0, 0);
            TestMachineBlockEntity enHaut = pose.machine(0, 17, 0);
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(a16, 75, "la machine à 16 blocs");
            etatAttendu(a17, 100, "la machine à 17 blocs");
            etatAttendu(enHaut, 100, "la machine 17 blocs plus haut");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_fusible")
    public static void elleProvoqueUnePanneDeFusible(GameTestHelper helper) {
        poser(helper, pose -> {
            List<TestMachineBlockEntity> machines = List.of(pose.machine(2, 0, 0), pose.machine(0, 0, 2),
                    pose.machine(2, 0, 2));
            frapper(helper, FUSIBLE);
            for (TestMachineBlockEntity m : machines) {
                verifier(m.fault() == FaultType.FUSE, "panne de fusible attendue, vue " + m.fault());
                etatAttendu(m, 0, "une machine au fusible grillé");
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_visuelle")
    public static void unEclairVisuelNeFaitRien(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity machine = pose.machine(2, 0, 0);
            BlockPos impact = helper.absolutePos(ECLAIR);
            vraiEclair(helper.getLevel(), impact, true);
            etatAttendu(machine, 100, "après un éclair visuel");
            vraiEclair(helper.getLevel(), impact, false);
            frappeeParUnVraiEclair(machine, "après un vrai éclair (témoin)");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Parafoudre
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "foudre_parafoudre_24")
    public static void leParafoudreProtegeA24Blocs(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity protegee = pose.machine(0, 0, 0);
            TestMachineBlockEntity temoin = pose.machine(0, 0, 16); // à 28,8 blocs du parafoudre
            SurgeArresterBlockEntity parafoudre = pose.parafoudre(24, 0, 0);
            verifier(parafoudre.isActive(), "un parafoudre neuf devait être actif");
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(protegee, 100, "la machine à 24 blocs du parafoudre");
            etatAttendu(temoin, 75, "la machine hors de portée du parafoudre");
            etatAttendu(parafoudre, 90, "le parafoudre qui a encaissé");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_parafoudre_25")
    public static void auDelaDe24IlNeProtegePas(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity machine = pose.machine(0, 0, 0);
            SurgeArresterBlockEntity parafoudre = pose.parafoudre(25, 0, 0);
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(machine, 75, "la machine à 25 blocs du parafoudre");
            etatAttendu(parafoudre, 100, "le parafoudre qui n'a rien protégé");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_parafoudre_une_fois")
    public static void ilSUseUneFoisParEclair(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity m1 = pose.machine(0, 0, 0);
            TestMachineBlockEntity m2 = pose.machine(0, 0, 2);
            TestMachineBlockEntity m3 = pose.machine(2, 0, 0);
            SurgeArresterBlockEntity p1 = pose.parafoudre(5, 0, 0);
            SurgeArresterBlockEntity p2 = pose.parafoudre(0, 0, 5);
            frapper(helper, SANS_FUSIBLE);
            for (TestMachineBlockEntity m : List.of(m1, m2, m3)) {
                etatAttendu(m, 100, "une machine protégée");
            }
            etatAttendu(p1, 90, "le premier parafoudre, trois machines protégées par un seul éclair");
            etatAttendu(p2, 90, "le second parafoudre, trois machines protégées par un seul éclair");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_parafoudre_use")
    public static void useAZeroIlNeProtegePlus(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity machine = pose.machine(0, 0, 0);
            SurgeArresterBlockEntity parafoudre = pose.parafoudre(3, 0, 0);
            for (int i = 1; i <= 10; i++) {
                frapper(helper, SANS_FUSIBLE);
                etatAttendu(machine, 100, "la machine protégée au coup " + i);
                etatAttendu(parafoudre, 100 - 10 * i, "le parafoudre au coup " + i);
            }
            verifier(!parafoudre.isActive() && parafoudre.fault() != null, "à 0, le parafoudre devait être en panne");
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(machine, 75, "la machine après le 11e coup, parafoudre hors d'usage");
            verifier(parafoudre.condition() == 0, "le parafoudre en panne ne bouge plus");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_parafoudre_repare")
    public static void repareIlProtegeDeNouveau(GameTestHelper helper) {
        poser(helper, pose -> {
            TestMachineBlockEntity machine = pose.machine(0, 0, 0);
            SurgeArresterBlockEntity parafoudre = pose.parafoudre(3, 0, 0);
            for (int i = 0; i < 10; i++) {
                frapper(helper, SANS_FUSIBLE);
            }
            verifier(!parafoudre.isActive(), "le parafoudre devait être hors d'usage");
            JoueurDeTest j = null;
            try {
                j = ingenieur(helper);
                ItemStack poste = new ItemStack(RepairRegistry.WELDER.get());
                WelderEnergy.store(poste, 1000);
                ItemStack piece = new ItemStack(RepairRegistry.partItem(parafoudre.expectedPart(), false));
                RepairActions.Result r = RepairActions.repair(j.joueur(), parafoudre, poste, piece);
                verifier(r == RepairActions.Result.REPAIRED, "REPAIRED attendu, vu " + r);
            } finally {
                if (j != null && j.connecte()) {
                    j.quitter();
                }
            }
            verifier(parafoudre.isActive(), "réparé, le parafoudre devait être actif");
            etatAttendu(parafoudre, 100, "le parafoudre réparé");
            frapper(helper, SANS_FUSIBLE);
            etatAttendu(machine, 100, "la machine de nouveau protégée");
            etatAttendu(parafoudre, 90, "le parafoudre réparé qui encaisse");
        });
    }

    @GameTest(template = "test/plancher", batch = "foudre_compteur_disjoncteur")
    public static void leCompteurEtLeDisjoncteurNeCraignentRien(GameTestHelper helper) {
        poser(helper, pose -> {
            BlockEntity compteur = pose.bloc(new BlockPos(2, 0, 0), GridRegistry.METER.get().defaultBlockState()
                    .setValue(InlineEnergyBlock.FACING, Direction.NORTH));
            BlockEntity disjoncteur = pose.bloc(new BlockPos(0, 0, 2), GridRegistry.BREAKER.get().defaultBlockState()
                    .setValue(InlineEnergyBlock.FACING, Direction.NORTH).setValue(BreakerBlock.CLOSED, true));
            TestMachineBlockEntity temoin = pose.machine(2, 0, 2);
            CompoundTag compteurAvant = compteur.saveWithoutMetadata();
            CompoundTag disjoncteurAvant = disjoncteur.saveWithoutMetadata();
            BlockState etatCompteur = compteur.getBlockState();
            BlockState etatDisjoncteur = disjoncteur.getBlockState();
            frapper(helper, FUSIBLE);
            verifier(temoin.fault() == FaultType.FUSE, "la machine témoin devait être frappée");
            verifier(compteur.saveWithoutMetadata().equals(compteurAvant), "le compteur ne devait pas changer");
            verifier(disjoncteur.saveWithoutMetadata().equals(disjoncteurAvant), "le disjoncteur ne devait pas changer");
            verifier(helper.getBlockState(ECLAIR.offset(2, 0, 0)) == etatCompteur, "le bloc du compteur ne devait pas changer");
            verifier(helper.getBlockState(ECLAIR.offset(0, 0, 2)) == etatDisjoncteur,
                    "le bloc du disjoncteur ne devait pas changer");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Chunks déchargés
    // ---------------------------------------------------------------------------------------------

    /** Les chunks à portée de recherche de ce point (foudre et parafoudre) qui ne sont pas chargés. */
    private static Set<ChunkPos> dechargesAutour(ServerLevel level, BlockPos centre) {
        int portee = GridConfig.lightningRadius() + GridConfig.arresterRadius();
        Set<ChunkPos> decharges = new HashSet<>();
        for (int cx = (centre.getX() - portee) >> 4; cx <= (centre.getX() + portee) >> 4; cx++) {
            for (int cz = (centre.getZ() - portee) >> 4; cz <= (centre.getZ() + portee) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    decharges.add(new ChunkPos(cx, cz));
                }
            }
        }
        return decharges;
    }

    private static void verifierToujoursDecharges(ServerLevel level, Set<ChunkPos> decharges) {
        for (ChunkPos c : decharges) {
            verifier(level.getChunkSource().getChunkNow(c.x, c.z) == null, "la foudre a chargé le chunk " + c);
        }
    }

    @GameTest(template = "test/plancher", batch = "foudre_chunks")
    public static void laFoudreNeChargeAucunChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origine = helper.absolutePos(ECLAIR);
        ChunkPos depart = new ChunkPos(origine);
        // Le premier chunk déchargé vers l'est, sur la rangée de la structure.
        Integer dechargeX = null;
        for (int dx = 1; dx <= 512 && dechargeX == null; dx++) {
            if (level.getChunkSource().getChunkNow(depart.x + dx, depart.z) == null) {
                dechargeX = depart.x + dx;
            }
        }
        verifier(dechargeX != null, "aucun chunk déchargé trouvé à l'est de la structure");
        // Un éclair dans le dernier chunk chargé, à 4 blocs du bord : son rayon couvre le chunk déchargé.
        BlockPos frappe = new BlockPos(dechargeX * 16 - 4, origine.getY(), origine.getZ());
        BlockPos machinePos = frappe.west();
        try {
            level.setBlockAndUpdate(machinePos, DevBlocks.testMachine().defaultBlockState());
            verifier(level.getBlockEntity(machinePos) instanceof TestMachineBlockEntity, "pas de machine en " + machinePos);
            TestMachineBlockEntity machine = (TestMachineBlockEntity) level.getBlockEntity(machinePos);
            Set<ChunkPos> decharges = dechargesAutour(level, frappe);
            verifier(decharges.contains(new ChunkPos(dechargeX, depart.z)), "le chunk visé devait être déchargé");
            vraiEclair(level, frappe, false);
            frappeeParUnVraiEclair(machine, "la machine du chunk chargé");
            verifierToujoursDecharges(level, decharges);

            // Très loin de tout : aucune zone chargée, la recherche ne trouve rien et ne charge rien.
            BlockPos loin = frappe.offset(200_000, 0, 200_000);
            Set<ChunkPos> toutDecharge = dechargesAutour(level, loin);
            verifier(!toutDecharge.isEmpty(), "la zone lointaine devait être déchargée");
            LightningEvents.strike(level, Vec3.atBottomCenterOf(loin), tirage(SANS_FUSIBLE));
            verifierToujoursDecharges(level, toutDecharge);
        } finally {
            if (level.getChunkSource().getChunkNow(machinePos.getX() >> 4, machinePos.getZ() >> 4) != null) {
                level.setBlockAndUpdate(machinePos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }
}
