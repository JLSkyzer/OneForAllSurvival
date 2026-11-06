package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.dev.ReseauxDev.Reseau;
import dev.oneforallsurvival.dev.ReseauxDev.Scene;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Preuve que le courant des trois réseaux tiers arrive dans la machine d'essai, en direct et à
 * travers le bloc en ligne. Les scènes sont montées par le code réel des mods (voir
 * {@link ReseauxDev}). Sans le pack complet (`-PwithPack`), chaque essai s'écarte proprement.
 *
 * <p>Critère : après attente, la machine est à l'état RUNNING et a travaillé au moins 20 ticks, soit
 * au moins 200 FE reçus (10 FE par tick de travail). Les lignes « [reseau] » du journal donnent
 * l'état, la réserve et la progression, tick par tick, pour le rapport.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ReseauxGameTests {

    private static final Logger LOG = LogManager.getLogger("oneforallsurvival-reseaux");

    /** Le dessus du plancher du gabarit : sa couche est en y = 1, on pose en y = 2. */
    private static final BlockPos ORIGINE = new BlockPos(0, 2, 1);

    private static final int TRAVAIL_MINIMUM = 20;

    private ReseauxGameTests() {}

    private static void essayer(GameTestHelper helper, String nom, Reseau reseau, boolean enLigne) {
        if (!reseau.disponible()) {
            LOG.info("[reseau] {} : IGNORE, pack absent", nom);
            helper.succeed();
            return;
        }
        Scene scene = ReseauxDev.construire(helper.getLevel(), helper.absolutePos(ORIGINE), reseau, enLigne);
        for (String ligne : scene.journal()) {
            LOG.info("[reseau] {} | {}", nom, ligne);
        }
        int journalAvant = scene.journal().size();
        TestMachineBlockEntity machine = (TestMachineBlockEntity) helper.getLevel().getBlockEntity(scene.machine());
        helper.assertTrue(machine != null, "pas de machine d'essai en " + scene.machine());
        // Le câblage vient 5 ticks plus tard : les connecteurs ont eu leur premier tick, comme en jeu.
        helper.runAfterDelay(5, () -> {
            ReseauxDev.cabler(helper.getLevel(), scene);
            for (String ligne : scene.journal().subList(journalAvant, scene.journal().size())) {
                LOG.info("[reseau] {} | {}", nom, ligne);
            }
        });
        long[] dernierLog = {-1};
        helper.succeedWhen(() -> {
            long t = helper.getTick();
            if (t % 20 == 0 && t != dernierLog[0]) {
                dernierLog[0] = t;
                LOG.info("[reseau] {} | tick {} : {} {}/{} FE, progression {}", nom, t, machine.state(),
                        machine.energy().getEnergyStored(), machine.energy().getMaxEnergyStored(), machine.progress());
            }
            helper.assertTrue(machine.state() == MachineState.RUNNING && machine.progress() >= TRAVAIL_MINIMUM,
                    nom + " : état " + machine.state() + ", " + machine.energy().getEnergyStored() + " FE, progression "
                            + machine.progress());
            LOG.info("[reseau] {} | RESULTAT OK au tick {} : {} FE reçus au moins, progression {}", nom, t,
                    machine.progress() * 10, machine.progress());
        });
    }

    /**
     * Contrôle négatif : la même scène sans tirer le fil ne doit rien livrer. Il prouve que les
     * essais positifs ne passent pas par un autre chemin que le câblage.
     */
    private static void sansFil(GameTestHelper helper, String nom, Reseau reseau) {
        if (!reseau.disponible()) {
            LOG.info("[reseau] {} : IGNORE, pack absent", nom);
            helper.succeed();
            return;
        }
        Scene scene = ReseauxDev.construire(helper.getLevel(), helper.absolutePos(ORIGINE), reseau, false);
        TestMachineBlockEntity machine = (TestMachineBlockEntity) helper.getLevel().getBlockEntity(scene.machine());
        helper.assertTrue(machine != null, "pas de machine d'essai en " + scene.machine());
        helper.runAfterDelay(100, () -> {
            LOG.info("[reseau] {} | RESULTAT apres 100 ticks sans fil : {} {} FE, progression {}", nom,
                    machine.state(), machine.energy().getEnergyStored(), machine.progress());
            helper.assertTrue(machine.progress() == 0 && machine.energy().getEnergyStored() == 0,
                    nom + " : sans fil, la machine a pourtant reçu du courant (progression " + machine.progress()
                            + ", " + machine.energy().getEnergyStored() + " FE)");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "reseau_ca_sans_fil", timeoutTicks = 200)
    public static void createAdditionSansFilNeLivreRien(GameTestHelper helper) {
        sansFil(helper, "createaddition_sans_fil", Reseau.CREATE_ADDITION);
    }

    @GameTest(template = "test/plancher", batch = "reseau_na_sans_fil", timeoutTicks = 200)
    public static void newAgeSansFilNeLivreRien(GameTestHelper helper) {
        sansFil(helper, "newage_sans_fil", Reseau.NEW_AGE);
    }

    @GameTest(template = "test/plancher", batch = "reseau_ae_sans_fil", timeoutTicks = 200)
    public static void advancedElectricitySansFilNeLivreRien(GameTestHelper helper) {
        sansFil(helper, "advancedelectricity_sans_fil", Reseau.ADVANCED_ELECTRICITY);
    }

    @GameTest(template = "test/plancher", batch = "reseau_temoin_direct", timeoutTicks = 200)
    public static void temoinDirect(GameTestHelper helper) {
        essayer(helper, "temoin_direct", Reseau.TEMOIN, false);
    }

    @GameTest(template = "test/plancher", batch = "reseau_temoin_ligne", timeoutTicks = 200)
    public static void temoinEnLigne(GameTestHelper helper) {
        essayer(helper, "temoin_en_ligne", Reseau.TEMOIN, true);
    }

    @GameTest(template = "test/plancher", batch = "reseau_ca_direct", timeoutTicks = 300)
    public static void createAdditionDirect(GameTestHelper helper) {
        essayer(helper, "createaddition_direct", Reseau.CREATE_ADDITION, false);
    }

    @GameTest(template = "test/plancher", batch = "reseau_ca_ligne", timeoutTicks = 300)
    public static void createAdditionEnLigne(GameTestHelper helper) {
        essayer(helper, "createaddition_en_ligne", Reseau.CREATE_ADDITION, true);
    }

    @GameTest(template = "test/plancher", batch = "reseau_na_direct", timeoutTicks = 300)
    public static void newAgeDirect(GameTestHelper helper) {
        essayer(helper, "newage_direct", Reseau.NEW_AGE, false);
    }

    @GameTest(template = "test/plancher", batch = "reseau_na_ligne", timeoutTicks = 300)
    public static void newAgeEnLigne(GameTestHelper helper) {
        essayer(helper, "newage_en_ligne", Reseau.NEW_AGE, true);
    }

    @GameTest(template = "test/plancher", batch = "reseau_ae_direct", timeoutTicks = 300)
    public static void advancedElectricityDirect(GameTestHelper helper) {
        essayer(helper, "advancedelectricity_direct", Reseau.ADVANCED_ELECTRICITY, false);
    }

    @GameTest(template = "test/plancher", batch = "reseau_ae_ligne", timeoutTicks = 300)
    public static void advancedElectricityEnLigne(GameTestHelper helper) {
        essayer(helper, "advancedelectricity_en_ligne", Reseau.ADVANCED_ELECTRICITY, true);
    }

    /**
     * Reproduit ce que le propriétaire a fait en jeu : poser d'abord la créative, la laisser tourner,
     * puis poser la machine contre l'une de ses six faces avec l'objet (clic droit d'un joueur). Une
     * face après l'autre, chacune sur une machine neuve. Le résultat de chaque face est journalisé.
     */
    @GameTest(template = "test/plancher", batch = "reseau_temoin_faces", timeoutTicks = 600)
    public static void temoinCreativeDAbordPuisMachineSurChaqueFace(GameTestHelper helper) {
        if (!Reseau.TEMOIN.disponible()) {
            LOG.info("[reseau] temoin_faces : IGNORE, pack absent");
            helper.succeed();
            return;
        }
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(3, 4, 3));
        net.minecraft.world.level.block.Block creative = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(new net.minecraft.resources.ResourceLocation("createaddition:creative_energy"));
        level.setBlock(centre, creative.defaultBlockState(), 3);
        net.minecraft.core.Direction[] faces = net.minecraft.core.Direction.values();
        int[] echecs = {0};
        for (int i = 0; i < faces.length; i++) {
            net.minecraft.core.Direction face = faces[i];
            int debut = 10 + i * 40;
            BlockPos cible = centre.relative(face);
            helper.runAfterDelay(debut, () -> {
                // Un joueur clique la face de la créative avec la machine en main.
                ReseauxDev.cliquer(level, "oneforallsurvival:test_machine", centre, face, face.getOpposite(), 1,
                        new java.util.ArrayList<>());
            });
            helper.runAfterDelay(debut + 30, () -> {
                var be = level.getBlockEntity(cible);
                if (be instanceof TestMachineBlockEntity m) {
                    boolean ok = m.progress() >= 10;
                    LOG.info("[reseau] temoin_face_{} | machine {} {} {}/{} FE, progression {} -> {}", face, cible.toShortString(),
                            m.state(), m.energy().getEnergyStored(), m.energy().getMaxEnergyStored(), m.progress(),
                            ok ? "OK" : "SANS COURANT");
                    if (!ok) {
                        echecs[0]++;
                    }
                } else {
                    LOG.info("[reseau] temoin_face_{} | pas de machine posée en {}", face, cible.toShortString());
                    echecs[0]++;
                }
                level.setBlock(cible, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            });
        }
        helper.runAfterDelay(10 + faces.length * 40 + 5, () -> {
            helper.assertTrue(echecs[0] == 0, echecs[0] + " face(s) sur 6 sans courant");
            helper.succeed();
        });
    }

    /**
     * Diagnostic, pas une preuve de livraison : la créative de Create Crafts & Additions garde un
     * LazyOptional par face, rafraîchi seulement à son premier tick et quand un voisin change. Si la
     * capability de la machine est invalidée (comme au déchargement d'un chunk) puis recréée sans que
     * la créative soit prévenue, elle ne pousse plus rien. L'essai journalise les trois temps :
     * avant, après invalidation, après une mise à jour de voisin. Il ne tranche rien sur l'observation
     * du propriétaire ; il montre seulement le seul mécanisme de la classe qui peut donner 0 FE.
     */
    @GameTest(template = "test/plancher", batch = "reseau_temoin_cache", timeoutTicks = 300, required = false)
    public static void temoinCacheDeLaCreativePerimeApresInvalidation(GameTestHelper helper) {
        if (!Reseau.TEMOIN.disponible()) {
            helper.succeed();
            return;
        }
        Scene scene = ReseauxDev.construire(helper.getLevel(), helper.absolutePos(ORIGINE), Reseau.TEMOIN, false);
        TestMachineBlockEntity machine = (TestMachineBlockEntity) helper.getLevel().getBlockEntity(scene.machine());
        int[] marque = new int[1];
        helper.runAfterDelay(40, () -> {
            LOG.info("[reseau] temoin_cache | avant : {} {} FE, progression {}", machine.state(),
                    machine.energy().getEnergyStored(), machine.progress());
            machine.invalidateCaps();
            machine.reviveCaps();
            marque[0] = machine.progress();
            // Vide la réserve : sinon la machine continuerait de tourner sur son stock.
            machine.energy().extractEnergy(0, false);
            machine.load(machine.saveWithoutMetadata().copy());
        });
        helper.runAfterDelay(120, () -> {
            LOG.info("[reseau] temoin_cache | apres invalidation + recreation, sans prevenir la creative : {} {} FE, progression {} (marque {})",
                    machine.state(), machine.energy().getEnergyStored(), machine.progress(), marque[0]);
            helper.getLevel().updateNeighborsAt(scene.machine(), machine.getBlockState().getBlock());
        });
        helper.runAfterDelay(200, () -> {
            LOG.info("[reseau] temoin_cache | apres mise a jour de voisin : {} {} FE, progression {}",
                    machine.state(), machine.energy().getEnergyStored(), machine.progress());
            helper.succeed();
        });
    }
}
