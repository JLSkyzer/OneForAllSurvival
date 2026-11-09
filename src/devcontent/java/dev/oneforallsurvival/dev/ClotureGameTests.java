package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.fence.ElectrifierBlockEntity;
import dev.oneforallsurvival.fence.FenceRegistry;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La clôture électrique. Les vivants sont des mobs sans IA, ramenés à leur place à chaque tick : un repoussement ne
 * doit pas leur faire perdre le contact, que le test veut répété. L'électrificateur est en (1, 1, 1) et la ligne part
 * vers l'est ; les vivants se tiennent juste au sud de la ligne.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ClotureGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    /** Hauteur du plafond posé par {@link #degager}. */
    private static final int PLAFOND = 4;
    /** Contre la clôture : à 0,025 bloc de la barre de la ligne, sous la marge de contact. */
    private static final Vec3 CONTRE_LA_LIGNE = new Vec3(3.5, 1, 1.95);

    private ClotureGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /**
     * Dégage l'espace d'essai et le couvre. Le gabarit ne pose que son plancher : du gravier du monde tombe d'en haut
     * dans le gabarit vidé, une quinzaine de ticks après le début, et s'empile sur les vivants (dégât « inWall »). Le
     * plafond le retient au-dessus des têtes ; un gravier lâché à chaque essai au-dessus de la colonne des vivants le
     * prouve (sans plafond, le zombie et la vache y perdaient des points de vie une fois sur dix).
     */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 1; y < PLAFOND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, PLAFOND, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 12, 1), Blocks.GRAVEL);
    }

    /** Pose {@code bloc} sur {@code longueur} cases à l'est de {@code depart}. */
    private static void ligne(GameTestHelper helper, BlockPos depart, int longueur, Block bloc) {
        for (int i = 0; i < longueur; i++) {
            helper.setBlock(depart.east(i), bloc);
        }
    }

    /** Un électrificateur en {@link #POS} et {@code longueur} clôtures électriques à sa droite. */
    private static ElectrifierBlockEntity poser(GameTestHelper helper, int longueur) {
        degager(helper);
        helper.setBlock(POS, FenceRegistry.ELECTRIFIER.get());
        ligne(helper, POS.east(), longueur, FenceRegistry.ELECTRIC_FENCE.get());
        return electrificateur(helper, POS);
    }

    private static ElectrifierBlockEntity electrificateur(GameTestHelper helper, BlockPos pos) {
        ElectrifierBlockEntity be = (ElectrifierBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity de l'électrificateur en " + pos);
        return be;
    }

    private static void alimenter(ElectrifierBlockEntity be) {
        for (int i = 0; i < ElectrifierBlockEntity.CAPACITY / ElectrifierBlockEntity.MAX_RECEIVE; i++) {
            be.energy().receiveEnergy(ElectrifierBlockEntity.MAX_RECEIVE, false);
        }
    }

    /** Garde la réserve pleine : un test long ne doit pas dépendre de la réserve de départ. */
    private static void maintenirLeCourant(GameTestHelper helper, ElectrifierBlockEntity be) {
        helper.onEachTick(() -> alimenter(be));
    }

    /** Un mobile sans IA, ramené à {@code position} (relative) à chaque tick. */
    private static <T extends Mob> T immobile(GameTestHelper helper, EntityType<T> type, Vec3 position) {
        T mob = helper.spawn(type, position);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        Vec3 absolue = helper.absoluteVec(position);
        helper.onEachTick(() -> {
            mob.setPos(absolue);
            mob.setDeltaMovement(Vec3.ZERO);
        });
        return mob;
    }

    /** Un zombie casqué : le soleil ne le brûle pas, seule la clôture le blesse. */
    private static Zombie zombie(GameTestHelper helper, Vec3 position) {
        Zombie zombie = immobile(helper, EntityType.ZOMBIE, position);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        return zombie;
    }

    /** Pour un message d'échec : la dernière source de dégâts subie et le feu éventuel. */
    private static String cause(LivingEntity entity) {
        DamageSource source = entity.getLastDamageSource();
        BlockPos oeil = BlockPos.containing(entity.getEyePosition());
        return (source == null ? "aucun dégât" : source.getMsgId()) + ", feu " + entity.getRemainingFireTicks()
                + ", pos " + entity.position() + ", oeil " + oeil + " = " + entity.level().getBlockState(oeil)
                + ", au-dessus " + entity.level().getBlockState(oeil.above());
    }

    private static Vec3 fil(GameTestHelper helper, BlockPos relative) {
        return Vec3.atCenterOf(helper.absolutePos(relative));
    }

    // ---------------------------------------------------------------------------------------------
    // Le choc
    // ---------------------------------------------------------------------------------------------

    /** Un monstre au contact est blessé par le courant ; un choc direct le blesse de deux points et le repousse. */
    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void unZombieEstBlesseEtRepousse(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Zombie auContact = zombie(helper, CONTRE_LA_LIGNE);
        Zombie loin = zombie(helper, new Vec3(7.5, 1, 4.5));
        alimenter(be);
        // Un choc direct : deux points, et une poussée qui l'éloigne de la clôture.
        be.shock(loin, fil(helper, new BlockPos(3, 1, 1)));
        Vec3 poussee = loin.getDeltaMovement();
        helper.assertTrue(loin.getHealth() == loin.getMaxHealth() - 2f,
                "le choc direct devait retirer 2 points : " + loin.getHealth());
        helper.assertTrue(poussee.x > 0 && poussee.z > 0,
                "le zombie devait être repoussé à l'opposé de la clôture : " + poussee);
        helper.assertTrue(loin.hurtMarked, "la poussée devait être marquée pour le client");
        maintenirLeCourant(helper, be);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.assertTrue(auContact.getHealth() < auContact.getMaxHealth(),
                    "le zombie contre la clôture devait être blessé : " + auContact.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void sansCourantRienNArrive(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Zombie zombie = zombie(helper, CONTRE_LA_LIGNE);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                    "sans courant le zombie ne devait rien subir : " + zombie.getHealth() + " (" + cause(zombie) + ")");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void enPanneRienNArrive(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Zombie zombie = zombie(helper, CONTRE_LA_LIGNE);
        alimenter(be);
        be.breakDown(FaultType.values()[0]);
        maintenirLeCourant(helper, be);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                    "en panne le zombie ne devait rien subir : " + zombie.getHealth() + " (" + cause(zombie) + ")");
            helper.succeed();
        });
    }

    /** Une vache est blessée jusqu'à 4 points de vie, pas au-delà : les enclos ne se déciment pas. */
    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void unAnimalNeMeurtPas(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Cow vache = immobile(helper, EntityType.COW, CONTRE_LA_LIGNE);
        maintenirLeCourant(helper, be);
        helper.onEachTick(() -> helper.assertTrue(vache.getHealth() >= 4f,
                "la vache ne devait jamais passer sous 4 points : " + vache.getHealth() + " (" + cause(vache) + ")"));
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(vache.getHealth() == 4f,
                    "après plusieurs contacts la vache devait être à 4 points : " + vache.getHealth() + " (" + cause(vache) + ")");
            helper.succeed();
        });
    }

    /** Sous le plancher de 4 points, le joueur ne perd plus rien ; juste au-dessus, il descend au plancher. */
    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void unJoueurFaibleNeMeurtPas(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Player joueur = helper.makeMockPlayer(); // hors du monde : le choc se donne directement
        joueur.moveTo(helper.absoluteVec(CONTRE_LA_LIGNE));
        Vec3 fil = fil(helper, new BlockPos(3, 1, 1));
        joueur.setHealth(4.5f);
        be.shock(joueur, fil);
        helper.assertTrue(joueur.getHealth() == 4f, "4,5 points devaient descendre à 4 : " + joueur.getHealth());
        be.shock(joueur, fil);
        helper.assertTrue(joueur.getHealth() == 4f, "à 4 points le choc ne devait rien retirer : " + joueur.getHealth());
        joueur.setHealth(1f);
        be.shock(joueur, fil);
        helper.assertTrue(joueur.getHealth() == 1f, "à 1 point le choc ne devait rien retirer : " + joueur.getHealth());
        helper.assertTrue(joueur.isAlive(), "le joueur devait rester en vie");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void unJoueurEstBlesse(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        Player joueur = helper.makeMockPlayer();
        joueur.moveTo(helper.absoluteVec(CONTRE_LA_LIGNE));
        float avant = joueur.getHealth();
        be.shock(joueur, fil(helper, new BlockPos(3, 1, 1)));
        helper.assertTrue(joueur.getHealth() == avant - 1f,
                "le joueur devait perdre 1 point : " + avant + " puis " + joueur.getHealth());
        helper.assertTrue(joueur.getDeltaMovement().z > 0, "le joueur devait être repoussé : " + joueur.getDeltaMovement());
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // La consommation
    // ---------------------------------------------------------------------------------------------

    /** Un bloc de végétation contre la ligne ajoute un point par tick, une fois la ligne relue. */
    @GameTest(template = "test/plancher", batch = "cloture_a", setupTicks = 20, timeoutTicks = 300)
    public static void laVegetationCouteDavantage(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        helper.assertTrue(be.energyPerTick() == 3, "4 clôtures devaient coûter 3 : " + be.energyPerTick());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        helper.assertTrue(be.energyPerTick() == 3, "la ligne n'est relue que toutes les 2 secondes : " + be.energyPerTick());
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(be.energyPerTick() == 4, "une feuille devait coûter 1 de plus : " + be.energyPerTick());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 300)
    public static void aucuneClotureAucuneConsommation(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 0);
        alimenter(be);
        int energie = be.energy().getEnergyStored();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.energyPerTick() == 0, "aucune clôture : 0 attendu, vu " + be.energyPerTick());
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
            helper.assertTrue(be.energy().getEnergyStored() == energie,
                    "aucune consommation attendue : " + energie + " puis " + be.energy().getEnergyStored());
            helper.succeed();
        });
    }

    /** Une clôture en bois contre l'électrificateur n'est pas sur la ligne : on peut la toucher. */
    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 300)
    public static void uneClotureOrdinaireNEstPasElectrifiee(GameTestHelper helper) {
        degager(helper);
        helper.setBlock(POS, FenceRegistry.ELECTRIFIER.get());
        ligne(helper, POS.east(), 4, Blocks.OAK_FENCE);
        ElectrifierBlockEntity be = electrificateur(helper, POS);
        Zombie zombie = zombie(helper, CONTRE_LA_LIGNE);
        alimenter(be);
        maintenirLeCourant(helper, be);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.line().isEmpty(), "la ligne devait être vide : " + be.line());
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                    "une clôture ordinaire ne devait rien infliger : " + zombie.getHealth() + " (" + cause(zombie) + ")");
            helper.succeed();
        });
    }

    /** Un bloc retiré coupe tout ce qui le suit, une fois la ligne relue (au plus 40 ticks). */
    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 300)
    public static void unBlocRetireCoupeLaSuite(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 5); // x = 2 à 6
        alimenter(be);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(be.line().size() == 5, "5 clôtures attendues : " + be.line().size());
            helper.setBlock(new BlockPos(4, 1, 1), Blocks.AIR);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.line().size() == 2, "2 clôtures attendues derrière le trou : " + be.line().size());
            helper.assertTrue(be.line().contains(helper.absolutePos(new BlockPos(3, 1, 1))),
                    "la clôture voisine devait rester sur la ligne");
            helper.succeed();
        });
    }

    /** Deux électrificateurs sur la même ligne : un seul choc par période. */
    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 300)
    public static void deuxElectrificateursNeDoublentPasLeChoc(GameTestHelper helper) {
        ElectrifierBlockEntity a = poser(helper, 4); // clôtures en x = 2 à 5
        BlockPos secondPos = new BlockPos(6, 1, 1);
        helper.setBlock(secondPos, FenceRegistry.ELECTRIFIER.get());
        ElectrifierBlockEntity b = electrificateur(helper, secondPos);
        Zombie zombie = zombie(helper, CONTRE_LA_LIGNE);
        alimenter(a);
        alimenter(b);
        maintenirLeCourant(helper, a);
        maintenirLeCourant(helper, b);
        float maximum = zombie.getMaxHealth();
        helper.runAfterDelay(16, () -> helper.assertTrue(zombie.getHealth() == maximum - 2f,
                "un seul choc attendu après 16 ticks : " + zombie.getHealth()));
        helper.runAfterDelay(28, () -> {
            helper.assertTrue(zombie.getHealth() == maximum - 4f,
                    "deux chocs attendus après 28 ticks : " + zombie.getHealth());
            helper.succeed();
        });
    }

    /** La clôture électrique se raccorde aux clôtures qui ne sont pas en bois, pas à celles en bois. */
    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 100)
    public static void laClotureSeRelieAuxClotures(GameTestHelper helper) {
        degager(helper);
        BlockPos centre = new BlockPos(2, 1, 1);
        helper.setBlock(centre, FenceRegistry.ELECTRIC_FENCE.get());
        helper.setBlock(centre.east(), Blocks.NETHER_BRICK_FENCE);
        helper.setBlock(centre.west(), Blocks.OAK_FENCE);
        helper.assertTrue(helper.getBlockState(centre).getValue(FenceBlock.EAST),
                "la clôture électrique devait se relier à la clôture en brique du Nether");
        helper.assertTrue(!helper.getBlockState(centre).getValue(FenceBlock.WEST),
                "la clôture électrique ne devait pas se relier à la clôture en bois");
        helper.assertTrue(!helper.getBlockState(centre.west()).getValue(FenceBlock.EAST),
                "la clôture en bois ne devait pas se relier à la clôture électrique");
        helper.succeed();
    }

    /** L'énergie se relit de la sauvegarde et la machine reprend sa consommation. */
    @GameTest(template = "test/plancher", batch = "cloture_b", setupTicks = 20, timeoutTicks = 300)
    public static void etatSurvitAuRechargement(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        alimenter(be);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la sauvegarde, vu " + be.state());
            int energie = be.energy().getEnergyStored();
            CompoundTag tag = be.saveWithoutMetadata();
            be.drainEnergy(energie);
            helper.assertTrue(be.energy().getEnergyStored() == 0, "la réserve devait être vidée avant le rechargement");
            be.load(tag);
            helper.assertTrue(be.energy().getEnergyStored() == energie,
                    "l'énergie devait revenir : " + energie + " puis " + be.energy().getEnergyStored());
            helper.runAfterDelay(6, () -> {
                helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu après le rechargement, vu " + be.state());
                helper.assertTrue(be.energy().getEnergyStored() < energie,
                        "la machine devait continuer de consommer : " + energie + " puis " + be.energy().getEnergyStored());
                helper.succeed();
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // L'orage (météo rendue après le lot)
    // ---------------------------------------------------------------------------------------------

    private static boolean pluieAvant;
    private static boolean orageAvant;
    private static boolean meteoGardee;

    /** Pendant un orage, la ligne coûte le double. La météo d'avant est rendue par l'{@link AfterBatch}. */
    @GameTest(template = "test/plancher", batch = "cloture_orage", setupTicks = 20, timeoutTicks = 300)
    public static void lOrageCouteDavantage(GameTestHelper helper) {
        ElectrifierBlockEntity be = poser(helper, 4);
        ServerLevel level = helper.getLevel();
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        helper.assertTrue(be.energyPerTick() == 3, "par beau temps 4 clôtures devaient coûter 3 : " + be.energyPerTick());
        pluieAvant = data.isRaining();
        orageAvant = data.isThundering();
        meteoGardee = true;
        data.setRaining(true);
        data.setThundering(true);
        level.setRainLevel(1f);
        level.setThunderLevel(1f);
        helper.assertTrue(level.isThundering(), "le monde devait être sous l'orage");
        helper.assertTrue(be.energyPerTick() == 6, "sous l'orage la ligne devait coûter 6 : " + be.energyPerTick());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.energyPerTick() == 6, "l'orage devait durer : " + be.energyPerTick());
            helper.succeed();
        });
    }

    @AfterBatch(batch = "cloture_orage")
    public static void apresOrage(ServerLevel level) {
        if (!meteoGardee) {
            return;
        }
        meteoGardee = false;
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        data.setRaining(pluieAvant);
        data.setThundering(orageAvant);
        level.setRainLevel(pluieAvant ? 1f : 0f);
        level.setThunderLevel(orageAvant ? 1f : 0f);
    }
}
