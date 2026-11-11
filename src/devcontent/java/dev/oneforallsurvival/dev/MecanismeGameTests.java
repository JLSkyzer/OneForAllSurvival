package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.MechanismBlock;
import dev.oneforallsurvival.explore.MechanismBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le mécanisme ancien : actif après la durée alimentée au seuil (lu sur une lampe de redstone voisine), remis à zéro par
 * une coupure, verrouillé jusqu'à {@code /mechanism reset}, incassable en survie, insensible à l'explosion et au piston,
 * réglages et état sauvegardés. Une source de courant verse à chaque tick par {@code onEachTick} posé au début du test.
 * Les lots : A (progression et sauvegarde), B (réglages, commandes, créatif), C (explosion et piston, seuls car ils
 * touchent leur voisinage).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MecanismeGameTests {

    private static final String LOT_A = "mecanisme";
    private static final String LOT_B = "mecanisme2";
    private static final String LOT_C = "mecanisme3";
    private static final BlockPos MECA = new BlockPos(2, 1, 2);
    private static final BlockPos LAMPE = new BlockPos(3, 1, 2);
    private static final BlockPos JOUEUR = new BlockPos(2, 1, 4);

    private MecanismeGameTests() {}

    private static void vider(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, Blocks.AIR);
        }
    }

    /** Pose la lampe puis le mécanisme réglé à {@code seuil} FE par tick pendant {@code ticks} ticks. */
    private static MechanismBlockEntity poser(GameTestHelper helper, int seuil, int ticks) {
        vider(helper, MECA, LAMPE);
        helper.setBlock(LAMPE, Blocks.REDSTONE_LAMP);
        helper.setBlock(MECA, ExploreRegistry.MECHANISM.get());
        helper.assertTrue(helper.getBlockEntity(MECA) instanceof MechanismBlockEntity, "un MechanismBlockEntity était attendu");
        MechanismBlockEntity be = (MechanismBlockEntity) helper.getBlockEntity(MECA);
        be.setSettings(seuil, ticks);
        return be;
    }

    /** Une source qui verse {@code parTick} FE au mécanisme à chaque tick tant que {@code marche[0]} est vrai. */
    private static void source(GameTestHelper helper, MechanismBlockEntity be, boolean[] marche, int parTick) {
        helper.onEachTick(() -> {
            if (marche[0]) {
                be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).ifPresent(e -> e.receiveEnergy(parTick, false));
            }
        });
    }

    private static boolean lampeAllumee(GameTestHelper helper) {
        return helper.getBlockState(LAMPE).getValue(RedstoneLampBlock.LIT);
    }

    private static boolean actifEtat(GameTestHelper helper) {
        return helper.getBlockState(MECA).getValue(MechanismBlock.ACTIVE);
    }

    private static int commande(GameTestHelper helper, String ligne) throws CommandSyntaxException {
        var serveur = helper.getLevel().getServer();
        CommandSourceStack source = serveur.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2)
                .withSuppressedOutput();
        return serveur.getCommands().getDispatcher().execute(ligne, source);
    }

    private static String coords(GameTestHelper helper) {
        BlockPos p = helper.absolutePos(MECA);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void assezLongtempsIlSActive(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 20);
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.assertTrue(!lampeAllumee(helper), "la lampe devait être éteinte au départ");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() > 0 && !be.active(),
                        "la progression devait avancer avant la fin : " + be.progress()))
                .thenWaitUntil(() -> helper.assertTrue(be.active(), "actif attendu, progression " + be.progress()))
                .thenExecute(() -> {
                    helper.assertTrue(actifEtat(helper), "la propriété active devait suivre");
                    helper.assertTrue(helper.getBlockState(MECA).getValue(MechanismBlock.STAGE) == 3, "étage 3 attendu");
                    helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(MECA), Direction.EAST) == 15,
                            "signal 15 attendu sur le bloc");
                    helper.assertTrue(lampeAllumee(helper), "la lampe voisine devait être allumée par le signal");
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void uneCoupureRemetAZero(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 60);
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 10, "progression " + be.progress()))
                .thenExecute(() -> marche[0] = false)
                .thenWaitUntil(() -> helper.assertTrue(be.progress() == 0, "la coupure devait remettre à zéro : " + be.progress()))
                .thenExecute(() -> {
                    helper.assertTrue(!be.active(), "pas actif après une coupure");
                    helper.assertTrue(!lampeAllumee(helper), "la lampe devait rester éteinte");
                    helper.assertTrue(helper.getBlockState(MECA).getValue(MechanismBlock.STAGE) == 0, "étage 0 attendu");
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unSeuilTropBasNAvancePas(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 20);
        boolean[] marche = {true};
        source(helper, be, marche, 9);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(be.progress() == 0 && !be.active(),
                            "sous le seuil rien n'avance : " + be.progress() + " actif=" + be.active());
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void actifIlLeReste(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 10);
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.active(), "actif attendu"))
                .thenExecute(() -> marche[0] = false)
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(be.active() && actifEtat(helper), "il devait rester actif sans courant");
                    helper.assertTrue(lampeAllumee(helper), "la lampe devait rester allumée");
                    helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(MECA), Direction.UP) == 15,
                            "signal 15 toujours attendu");
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void resetLEteint(GameTestHelper helper) throws CommandSyntaxException {
        MechanismBlockEntity be = poser(helper, 10, 10);
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.active() && lampeAllumee(helper), "actif et lampe allumée attendus"))
                .thenExecute(() -> {
                    marche[0] = false;
                    try {
                        int r = commande(helper, "mechanism reset " + coords(helper));
                        helper.assertTrue(r == 1, "la commande devait réussir, résultat " + r);
                    } catch (CommandSyntaxException e) {
                        helper.fail("commande refusée : " + e.getMessage());
                    }
                    helper.assertTrue(!be.active() && be.progress() == 0, "remis à zéro attendu");
                    helper.assertTrue(!actifEtat(helper), "la propriété active devait retomber");
                    helper.assertTrue(helper.getLevel().getSignal(helper.absolutePos(MECA), Direction.EAST) == 0,
                            "plus de signal attendu");
                })
                .thenWaitUntil(() -> helper.assertTrue(!lampeAllumee(helper), "la lampe devait finir par s'éteindre"))
                .thenExecute(() -> vider(helper, MECA, LAMPE))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void incassableEnSurvie(GameTestHelper helper) {
        poser(helper, 10, 20);
        JoueurDeTest j = null;
        try {
            j = JoueurDeTest.rejoindre(helper.getLevel(), "Fouilleur_meca_survie", helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(MECA);
            helper.assertTrue(helper.getBlockState(MECA).getDestroySpeed(helper.getLevel(), abs) < 0, "dureté -1 attendue");
            helper.assertTrue(helper.getBlockState(MECA).getDestroyProgress(j.joueur(), helper.getLevel(), abs) == 0.0F,
                    "aucune progression de casse en survie");
            j.joueur().gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, 320, 0);
            helper.assertTrue(helper.getBlockState(MECA).is(ExploreRegistry.MECHANISM.get()), "le mécanisme devait tenir");
            helper.assertTrue(helper.getBlockEntity(MECA) instanceof MechanismBlockEntity, "son bloc d'entité devait tenir");
            vider(helper, MECA, LAMPE);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void reglageEtEtatSurviventAuRechargement(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 37, 90);
        boolean[] marche = {true};
        source(helper, be, marche, 37);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 5 && !be.active(), "progression " + be.progress()))
                .thenExecute(() -> {
                    marche[0] = false;
                    // En cours : seuil, durée et progression sont sauvegardés, active reste faux.
                    CompoundTag tag = be.saveWithoutMetadata();
                    MechanismBlockEntity copie = new MechanismBlockEntity(helper.absolutePos(MECA), helper.getBlockState(MECA));
                    copie.load(tag);
                    helper.assertTrue(copie.threshold() == 37 && copie.durationTicks() == 90 && !copie.active()
                            && copie.progress() == be.progress(), "réglage et progression attendus après rechargement");
                    // Actif : il le reste.
                    MechanismBlockEntity actif = new MechanismBlockEntity(helper.absolutePos(MECA), helper.getBlockState(MECA));
                    CompoundTag tagActif = tag.copy();
                    tagActif.putInt("Progress", 90);
                    tagActif.putBoolean("Active", true);
                    actif.load(tagActif);
                    helper.assertTrue(actif.active() && actif.progress() == 90, "actif attendu après rechargement");
                    // Sans donnée : valeurs par défaut.
                    MechanismBlockEntity neuf = new MechanismBlockEntity(helper.absolutePos(MECA), helper.getBlockState(MECA));
                    neuf.load(new CompoundTag());
                    helper.assertTrue(neuf.threshold() == 100 && neuf.durationTicks() == 200 && !neuf.active()
                            && neuf.progress() == 0, "100 FE / 200 ticks par défaut");
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    // ---- lot B

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void accepteAuPlusDeuxFoisLeSeuilParTick(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 20);
        IEnergyStorage e = be.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(
                () -> new IllegalStateException("capability d'énergie attendue"));
        helper.assertTrue(e.canReceive() && !e.canExtract(), "reçoit seulement");
        helper.assertTrue(e.receiveEnergy(1000, true) == 20, "au plus 2 x seuil par tick, simulé : " + e.receiveEnergy(1000, true));
        helper.assertTrue(e.receiveEnergy(15, false) == 15, "15 acceptés");
        helper.assertTrue(e.receiveEnergy(1000, false) == 5, "5 restent jusqu'à 20");
        helper.assertTrue(e.receiveEnergy(1, false) == 0, "plafond atteint pour ce tick");
        helper.assertTrue(e.getEnergyStored() == 0, "rien n'est stocké");
        helper.assertTrue(e.extractEnergy(10, false) == 0, "rien ne se retire");
        vider(helper, MECA, LAMPE);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void lEnergieNEstJamaisGardeD(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 60);
        // Un versement un tick sur deux : la fenêtre « depuis le dernier tick » n'en compte jamais deux, ni de gardé.
        boolean[] marche = {true};
        int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (marche[0] && tick[0] % 2 == 0) {
                be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).ifPresent(e -> e.receiveEnergy(10, false));
            }
        });
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(be.progress() <= 1, "un tick sur deux ne s'accumule pas : " + be.progress());
                    helper.assertTrue(!be.active(), "pas actif");
                    marche[0] = false;
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    /** Un rechargement (sauvegarde puis chargement du bloc posé) ne coûte rien : le premier tick ne juge pas. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void rechargeNePerdPasLaProgression(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 60);
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 5 && !be.active(), "progression " + be.progress()))
                .thenExecute(() -> {
                    marche[0] = false;
                    int p = be.progress();
                    BlockPos abs = helper.absolutePos(MECA);
                    be.load(be.saveWithoutMetadata()); // le bloc posé lui-même, pas une copie
                    // Les ticks sont appelés ici, dans le même tick de jeu : le monde n'en intercale aucun.
                    be.serverTick(helper.getLevel(), abs, helper.getBlockState(MECA));
                    helper.assertTrue(be.progress() == p, "le tick de grâce ne devait pas juger : " + p + " puis " + be.progress());
                    be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).ifPresent(e -> e.receiveEnergy(10, false));
                    be.serverTick(helper.getLevel(), abs, helper.getBlockState(MECA));
                    helper.assertTrue(be.progress() == p + 1, "il devait reprendre de " + p + ", vu " + be.progress());
                    // Une vraie coupure, hors rechargement, remet toujours à zéro.
                    be.serverTick(helper.getLevel(), abs, helper.getBlockState(MECA));
                    helper.assertTrue(be.progress() == 0, "une coupure devait remettre à zéro, vu " + be.progress());
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void immuniseContreLesBoss(GameTestHelper helper) {
        var etat = ExploreRegistry.MECHANISM.get().defaultBlockState();
        helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE), "le mécanisme doit résister au wither");
        helper.assertTrue(etat.is(BlockTags.DRAGON_IMMUNE), "le mécanisme doit résister au dragon");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void uneRafaleDeDeuxFoisLeSeuilNeSeReporteJamais(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 60);
        int[] tick = {0};
        int[] maximum = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 3) {
                int v = be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).map(e -> e.receiveEnergy(1000, false)).orElse(-1);
                helper.assertTrue(v == 20, "la rafale devait être plafonnée à 2 x seuil, vu " + v);
            }
            maximum[0] = Math.max(maximum[0], be.progress());
        });
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(maximum[0] <= 1, "la rafale ne vaut qu'un tick, progression max " + maximum[0]);
                    helper.assertTrue(be.progress() == 0, "rien ne doit se reporter : progression " + be.progress());
                    int v = be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).map(e -> e.receiveEnergy(1000, true)).orElse(-1);
                    helper.assertTrue(v == 20, "la fenêtre suivante repart de zéro, vu " + v);
                    vider(helper, MECA, LAMPE);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void laPoudreSurUnBlocVoisinSAllume(GameTestHelper helper) {
        MechanismBlockEntity be = poser(helper, 10, 10);
        BlockPos pierre = LAMPE;
        BlockPos poudre = LAMPE.above();
        vider(helper, poudre);
        helper.setBlock(pierre, Blocks.STONE);
        helper.setBlock(poudre, Blocks.REDSTONE_WIRE);
        helper.assertTrue(helper.getBlockState(poudre).getValue(RedStoneWireBlock.POWER) == 0, "poudre éteinte au départ");
        boolean[] marche = {true};
        source(helper, be, marche, 10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.active(), "actif attendu"))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(poudre).getValue(RedStoneWireBlock.POWER) > 0,
                        "la poudre sur le bloc voisin devait s'allumer, vu " + helper.getBlockState(poudre)))
                .thenExecute(() -> vider(helper, MECA, pierre, poudre))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lesCommandesReglentEtDisent(GameTestHelper helper) throws CommandSyntaxException {
        MechanismBlockEntity be = poser(helper, 10, 20);
        int r = commande(helper, "mechanism set " + coords(helper) + " 55 4");
        helper.assertTrue(r == 1, "set devait réussir, résultat " + r);
        helper.assertTrue(be.threshold() == 55 && be.durationTicks() == 80,
                "réglage attendu 55 / 80 ticks, vu " + be.threshold() + " / " + be.durationTicks());
        helper.assertTrue(commande(helper, "mechanism info " + coords(helper)) == 1, "info devait réussir");
        helper.assertTrue(commande(helper, "mechanism reset " + coords(helper)) == 1, "reset devait réussir");
        BlockPos ailleurs = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.AIR);
        helper.assertTrue(commande(helper, "mechanism info " + ailleurs.getX() + " " + ailleurs.getY() + " " + ailleurs.getZ()) == 0,
                "info sur autre chose qu'un mécanisme devait échouer");
        // niveau de permission : sans 2, la commande n'existe pas pour la source
        CommandSourceStack simple = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                .withPermission(1);
        boolean refuse = false;
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("mechanism info " + coords(helper), simple);
        } catch (CommandSyntaxException e) {
            refuse = true;
        }
        helper.assertTrue(refuse, "la commande exige le niveau 2");
        vider(helper, MECA, LAMPE);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void cassableEnCreatifSansButin(GameTestHelper helper) {
        poser(helper, 10, 20);
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(MECA)).inflate(4.0))) {
            e.discard();
        }
        JoueurDeTest j = null;
        try {
            j = JoueurDeTest.rejoindre(helper.getLevel(), "Fouilleur_meca_creatif", helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            j.joueur().gameMode.handleBlockBreakAction(helper.absolutePos(MECA),
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, 320, 0);
            helper.assertTrue(helper.getBlockState(MECA).isAir(), "il devait se casser en créatif");
            var objets = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(MECA)).inflate(4.0));
            helper.assertTrue(objets.isEmpty(), "aucun objet attendu : " + objets);
            vider(helper, LAMPE);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    // ---- lot C

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void ilResisteALExplosion(GameTestHelper helper) {
        BlockPos temoin = new BlockPos(1, 1, 3);
        BlockPos centreCase = new BlockPos(2, 1, 3);
        BlockPos meca = new BlockPos(3, 1, 3);
        vider(helper, temoin, centreCase, meca);
        helper.setBlock(meca, ExploreRegistry.MECHANISM.get());
        helper.setBlock(temoin, Blocks.DIRT); // témoin symétrique : prouve que les rayons atteignent le côté du mécanisme
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(centreCase));
        helper.getLevel().explode(null, centre.x, centre.y, centre.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(temoin).isAir(),
                "le témoin (terre) devait sauter : l'explosion a eu lieu, vu " + helper.getBlockState(temoin));
        helper.assertTrue(helper.getBlockState(meca).is(ExploreRegistry.MECHANISM.get()), "le mécanisme devait résister");
        helper.assertTrue(helper.getBlockEntity(meca) instanceof MechanismBlockEntity, "son bloc d'entité devait tenir");
        vider(helper, meca);
        helper.succeed();
    }

    /** Centrée dans la case du mécanisme, l'explosion ne casse rien autour : chaque rayon s'éteint dès son premier pas. */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneExplosionDansLeMecanismeNeCasseRien(GameTestHelper helper) {
        BlockPos meca = new BlockPos(2, 1, 3);
        BlockPos g = new BlockPos(1, 1, 3);
        BlockPos d = new BlockPos(3, 1, 3);
        vider(helper, meca, g, d);
        helper.setBlock(meca, ExploreRegistry.MECHANISM.get());
        helper.setBlock(g, Blocks.DIRT);
        helper.setBlock(d, Blocks.DIRT);
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(meca));
        helper.getLevel().explode(null, centre.x, centre.y, centre.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(meca).is(ExploreRegistry.MECHANISM.get()), "le mécanisme devait résister");
        helper.assertTrue(helper.getBlockState(g).is(Blocks.DIRT) && helper.getBlockState(d).is(Blocks.DIRT),
                "les voisins devaient rester : vu " + helper.getBlockState(g) + " et " + helper.getBlockState(d));
        vider(helper, meca, g, d);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void lePistonNeLePoussePas(GameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 1, 2);
        BlockPos temoinPiston = new BlockPos(1, 1, 4);
        BlockPos temoin = new BlockPos(2, 1, 4);
        vider(helper, MECA, LAMPE, piston, temoinPiston, temoin, new BlockPos(3, 1, 4), new BlockPos(1, 2, 2),
                new BlockPos(1, 2, 4));
        helper.setBlock(MECA, ExploreRegistry.MECHANISM.get());
        helper.setBlock(temoin, Blocks.DIRT);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(temoinPiston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                // Le piston témoin pousse la terre : le piston, lui, est réel et alimenté.
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 4)).is(Blocks.DIRT),
                        "le piston témoin devait pousser la terre"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(MECA).is(ExploreRegistry.MECHANISM.get()),
                            "le mécanisme ne devait pas bouger");
                    helper.assertTrue(helper.getBlockEntity(MECA) instanceof MechanismBlockEntity, "son bloc d'entité devait rester");
                    helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                            "le piston bloqué ne devait pas s'étendre");
                    vider(helper, MECA, piston, temoinPiston, new BlockPos(2, 1, 4), new BlockPos(3, 1, 4),
                            new BlockPos(1, 2, 2), new BlockPos(1, 2, 4));
                })
                .thenSucceed();
    }
}
