package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.loot.MobOrigin;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La marque d'origine des mobs ({@link MobOrigin}). Le monde d'essai n'a aucune apparition naturelle : les origines
 * passent par {@code EntityType.spawn(level, pos, type)}, qui publie le même événement que le jeu, et le générateur
 * par un vrai bloc de générateur. Deux lots de 8 au plus.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class OrigineMobGameTests {

    /** Hauteur du plafond posé par {@link #degager} (leçons du 2026-11-09 et du 2026-11-11). */
    private static final int PLAFOND = 4;
    private static final BlockPos ICI = new BlockPos(2, 1, 2);
    private static final BlockPos LA = new BlockPos(5, 1, 5);

    /** Les joueurs d'essai d'un test à séquence : ceux qu'un délai dépassé laisserait en jeu sortent après le lot. */
    private static final List<JoueurDeTest> A_FAIRE_SORTIR = new ArrayList<>();

    private OrigineMobGameTests() {}

    /** Fait sortir le joueur s'il est encore là ; sans effet sinon. */
    private static void sortir(JoueurDeTest joueur) {
        A_FAIRE_SORTIR.remove(joueur);
        if (joueur.joueur().getServer().getPlayerList().getPlayer(joueur.uuid()) == joueur.joueur()) {
            joueur.quitter();
        }
    }

    @AfterBatch(batch = "origine_a")
    public static void apresLotA(ServerLevel level) {
        for (JoueurDeTest joueur : List.copyOf(A_FAIRE_SORTIR)) {
            sortir(joueur);
        }
    }

    /** Dégage l'espace d'essai et le couvre : rien ne tombe sur les vivants gardés plus d'une seconde. */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 1; y < PLAFOND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, PLAFOND, z), Blocks.STONE);
            }
        }
    }

    private static <T extends Mob> T apparaitre(GameTestHelper helper, EntityType<T> type, BlockPos pos,
            MobSpawnType origine) {
        T mob = type.spawn(helper.getLevel(), helper.absolutePos(pos), origine);
        helper.assertTrue(mob != null, type + " n'est pas apparu en " + origine);
        return mob;
    }

    /** Un mob ajouté au monde sans événement d'apparition, comme le fait le jeu pour un petit ou un slime divisé. */
    private static <T extends Mob> T creer(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
        T mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "création de " + type);
        Vec3 v = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        mob.moveTo(v.x, v.y, v.z, 0f, 0f);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void sauvage(GameTestHelper helper, Entity e, boolean attendu, String quoi) {
        helper.assertTrue(MobOrigin.isWild(e) == attendu,
                quoi + (attendu ? " devrait être sauvage" : " ne devrait pas être sauvage") + " (" + e + ")");
    }

    /** Les entités du type dans l'espace d'essai (8 x 8, plancher au plafond). */
    private static <T extends Entity> List<T> tous(GameTestHelper helper, EntityType<T> type) {
        return helper.getEntities(type, new BlockPos(4, 2, 4), 8.0);
    }

    private static List<Cow> petitsVaches(GameTestHelper helper) {
        return tous(helper, EntityType.COW).stream().filter(Cow::isBaby).toList();
    }

    private static void toutRetirer(GameTestHelper helper) {
        for (EntityType<?> type : List.of(EntityType.COW, EntityType.ZOMBIE, EntityType.DROWNED,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.SLIME, EntityType.TADPOLE, EntityType.FROG,
                EntityType.CHICKEN, EntityType.EXPERIENCE_ORB)) {
            tous(helper, type).forEach(Entity::discard);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot a
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20)
    public static void naturelEtGenerationSontSauvages(GameTestHelper helper) {
        degager(helper);
        for (MobSpawnType origine : MobSpawnType.values()) {
            Cow vache = apparaitre(helper, EntityType.COW, ICI, origine);
            boolean attendu = origine == MobSpawnType.NATURAL || origine == MobSpawnType.CHUNK_GENERATION
                    || origine == MobSpawnType.STRUCTURE || origine == MobSpawnType.PATROL
                    || origine == MobSpawnType.EVENT || origine == MobSpawnType.TRIGGERED;
            sauvage(helper, vache, attendu, "une vache née en " + origine);
            vache.discard();
        }
        toutRetirer(helper);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20)
    public static void oeufCommandeDistributeurNeMarquentPas(GameTestHelper helper) {
        degager(helper);
        List<MobSpawnType> origines = List.of(MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND, MobSpawnType.DISPENSER,
                MobSpawnType.MOB_SUMMONED, MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY, MobSpawnType.SPAWNER,
                MobSpawnType.BUCKET, MobSpawnType.BREEDING, MobSpawnType.CONVERSION);
        for (MobSpawnType origine : origines) {
            Zombie zombie = apparaitre(helper, EntityType.ZOMBIE, ICI, origine);
            sauvage(helper, zombie, false, "un zombie né en " + origine);
            zombie.discard();
            // Une marque déjà là (NBT d'un œuf ou d'un générateur) est effacée par la même apparition.
            Zombie marque = EntityType.ZOMBIE.create(helper.getLevel());
            Vec3 v = helper.absoluteVec(Vec3.atBottomCenterOf(ICI));
            marque.moveTo(v.x, v.y, v.z, 0f, 0f);
            MobOrigin.markWild(marque);
            ServerLevel level = helper.getLevel();
            ForgeEventFactory.onFinalizeSpawn(marque, level, level.getCurrentDifficultyAt(marque.blockPosition()),
                    origine, null, null);
            sauvage(helper, marque, false, "un zombie marqué d'avance puis né en " + origine);
        }
        toutRetirer(helper);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20, timeoutTicks = 200)
    public static void unVraiGenerateurNeMarquePas(GameTestHelper helper) {
        degager(helper);
        helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
                "difficulté paisible : le générateur ne ferait rien naître");
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.SPAWNER);
        SpawnerBlockEntity be = (SpawnerBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity du générateur");
        // Le zombie à faire naître porte déjà la marque dans son NBT : l'apparition du générateur doit l'effacer.
        CompoundTag zombie = new CompoundTag();
        zombie.putString("id", "minecraft:zombie");
        CompoundTag forge = new CompoundTag();
        forge.putBoolean(MobOrigin.KEY, true);
        zombie.put("ForgeData", forge);
        CompoundTag limite = new CompoundTag();
        limite.putInt("min_inclusive", 0);
        limite.putInt("max_inclusive", 15);
        CompoundTag regles = new CompoundTag();
        regles.put("block_light_limit", limite.copy());
        regles.put("sky_light_limit", limite.copy());
        CompoundTag donnees = new CompoundTag();
        donnees.put("entity", zombie);
        donnees.put("custom_spawn_rules", regles);
        CompoundTag tag = new CompoundTag();
        tag.putShort("Delay", (short) 0);
        tag.putShort("MinSpawnDelay", (short) 200);
        tag.putShort("MaxSpawnDelay", (short) 400);
        tag.putShort("SpawnCount", (short) 1);
        tag.putShort("MaxNearbyEntities", (short) 6);
        tag.putShort("RequiredPlayerRange", (short) 16);
        tag.putShort("SpawnRange", (short) 1);
        tag.put("SpawnData", donnees);
        be.getSpawner().load(helper.getLevel(), helper.absolutePos(pos), tag);
        be.setChanged();
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "OrigineGen",
                helper.absoluteVec(new Vec3(6.5, 1, 6.5)));
        A_FAIRE_SORTIR.add(joueur);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!tous(helper, EntityType.ZOMBIE).isEmpty(),
                        "aucun zombie du générateur"))
                .thenExecute(() -> {
                    List<Zombie> nes = tous(helper, EntityType.ZOMBIE);
                    try {
                        helper.setBlock(pos, Blocks.AIR);
                        for (Zombie z : nes) {
                            sauvage(helper, z, false, "un zombie du générateur");
                        }
                    } finally {
                        sortir(joueur);
                        toutRetirer(helper);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20)
    public static void unOeufSurUnMobDonneUnPetitSansMarque(GameTestHelper helper) {
        degager(helper);
        Cow mere = apparaitre(helper, EntityType.COW, ICI, MobSpawnType.NATURAL);
        sauvage(helper, mere, true, "la vache sauvage");
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "OrigineOeuf",
                helper.absoluteVec(new Vec3(3.5, 1, 2.5)));
        try {
            joueur.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COW_SPAWN_EGG));
            mere.interact(joueur.joueur(), InteractionHand.MAIN_HAND);
            List<Cow> petits = petitsVaches(helper);
            helper.assertTrue(petits.size() == 1, "un petit attendu, vu " + petits.size());
            sauvage(helper, petits.get(0), false, "le petit né de l'œuf");
            sauvage(helper, mere, true, "la vache après l'œuf");
        } finally {
            joueur.quitter();
            toutRetirer(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20)
    public static void unPiglinNeDansUnPortailNEstPasSauvage(GameTestHelper helper) {
        degager(helper);
        // Un portail d'un bloc dans son cadre : rien ne le met à jour avant l'apparition, il tient le temps du test.
        BlockPos portail = new BlockPos(2, 2, 2);
        helper.setBlock(portail.below(), Blocks.OBSIDIAN);
        helper.setBlock(portail.above(), Blocks.OBSIDIAN);
        helper.setBlock(portail.west(), Blocks.OBSIDIAN);
        helper.setBlock(portail.east(), Blocks.OBSIDIAN);
        helper.setBlock(portail, Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, portail);
        Mob dansLePortail = apparaitre(helper, EntityType.ZOMBIFIED_PIGLIN, portail, MobSpawnType.STRUCTURE);
        Mob ailleurs = apparaitre(helper, EntityType.ZOMBIFIED_PIGLIN, LA, MobSpawnType.STRUCTURE);
        boolean portailSauvage = MobOrigin.isWild(dansLePortail);
        boolean ailleursSauvage = MobOrigin.isWild(ailleurs);
        dansLePortail.discard();
        ailleurs.discard();
        for (BlockPos p : List.of(portail, portail.below(), portail.above(), portail.west(), portail.east())) {
            helper.setBlock(p, Blocks.AIR);
        }
        helper.assertTrue(!portailSauvage, "le piglin né dans le portail ne devrait pas être sauvage");
        helper.assertTrue(ailleursSauvage, "le piglin né d'une structure hors portail devrait être sauvage");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_a", setupTicks = 20)
    public static void laMarqueSurvitALaSauvegarde(GameTestHelper helper) {
        degager(helper);
        Cow marquee = apparaitre(helper, EntityType.COW, ICI, MobSpawnType.NATURAL);
        Cow sans = apparaitre(helper, EntityType.COW, LA, MobSpawnType.SPAWN_EGG);
        CompoundTag tagMarquee = marquee.saveWithoutId(new CompoundTag());
        CompoundTag tagSans = sans.saveWithoutId(new CompoundTag());
        toutRetirer(helper);
        Cow neuve = EntityType.COW.create(helper.getLevel());
        neuve.load(tagMarquee);
        sauvage(helper, neuve, true, "la vache marquée rechargée");
        Cow neuveSans = EntityType.COW.create(helper.getLevel());
        neuveSans.load(tagSans);
        sauvage(helper, neuveSans, false, "la vache sans marque rechargée");
        // Un mob d'avant le lot : aucune donnée de Forge.
        CompoundTag ancien = tagSans.copy();
        ancien.remove("ForgeData");
        Cow vieille = EntityType.COW.create(helper.getLevel());
        vieille.load(ancien);
        sauvage(helper, vieille, false, "une vache d'avant le lot");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot b
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "origine_b", setupTicks = 20)
    public static void lePetitDeDeuxSauvagesNEstPasSauvage(GameTestHelper helper) {
        degager(helper);
        Cow a = apparaitre(helper, EntityType.COW, ICI, MobSpawnType.NATURAL);
        Cow b = apparaitre(helper, EntityType.COW, ICI.east(), MobSpawnType.NATURAL);
        a.setInLove(null);
        b.setInLove(null);
        a.spawnChildFromBreeding(helper.getLevel(), b);
        List<Cow> petits = petitsVaches(helper);
        helper.assertTrue(petits.size() == 1, "un petit attendu, vu " + petits.size());
        sauvage(helper, petits.get(0), false, "le petit de deux vaches sauvages");
        sauvage(helper, a, true, "le premier parent");
        sauvage(helper, b, true, "le second parent");
        // Un mod qui recopierait les données des parents sur le petit : l'événement de naissance l'efface.
        Cow copie = EntityType.COW.create(helper.getLevel());
        copie.setBaby(true);
        MobOrigin.markWild(copie);
        MinecraftForge.EVENT_BUS.post(new BabyEntitySpawnEvent(a, b, copie));
        sauvage(helper, copie, false, "un petit à qui l'on a recopié la marque");
        toutRetirer(helper);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_b", setupTicks = 20)
    public static void lePetitDuPetitNonPlus(GameTestHelper helper) {
        degager(helper);
        Cow a = apparaitre(helper, EntityType.COW, ICI, MobSpawnType.NATURAL);
        Cow b = apparaitre(helper, EntityType.COW, ICI.east(), MobSpawnType.NATURAL);
        a.setInLove(null);
        b.setInLove(null);
        a.spawnChildFromBreeding(helper.getLevel(), b);
        Cow petit = petitsVaches(helper).get(0);
        petit.setAge(0);
        Cow c = apparaitre(helper, EntityType.COW, LA, MobSpawnType.NATURAL);
        petit.setInLove(null);
        c.setInLove(null);
        petit.spawnChildFromBreeding(helper.getLevel(), c);
        List<Cow> petitsEnfants = petitsVaches(helper);
        helper.assertTrue(petitsEnfants.size() == 1, "un petit-enfant attendu, vu " + petitsEnfants.size());
        sauvage(helper, petitsEnfants.get(0), false, "le petit du petit");
        sauvage(helper, petit, false, "le petit devenu grand");
        toutRetirer(helper);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "origine_b", setupTicks = 20, timeoutTicks = 100)
    public static void unZombieNoyeGardeSaMarque(GameTestHelper helper) {
        degager(helper);
        helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
                "difficulté paisible : le zombie disparaîtrait");
        // Le vrai chemin du jeu : un zombie déjà en conversion sous l'eau (sauvegarde), qui finit au tick suivant.
        Zombie sauvageZ = apparaitre(helper, EntityType.ZOMBIE, ICI, MobSpawnType.NATURAL);
        Zombie sansZ = apparaitre(helper, EntityType.ZOMBIE, LA, MobSpawnType.SPAWN_EGG);
        for (Zombie z : List.of(sauvageZ, sansZ)) {
            z.setBaby(false);
            // Un joueur d'essai resté connecté ailleurs (à plus de 128 blocs) ferait retirer le zombie par checkDespawn
            // (leçon du 2026-11-15) ; le noyé hérite de la persistance à la conversion.
            z.setPersistenceRequired();
            CompoundTag tag = z.saveWithoutId(new CompoundTag());
            tag.putInt("DrownedConversionTime", 1);
            z.load(tag);
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tous(helper, EntityType.DROWNED).size() == 2,
                        "deux noyés attendus, vus " + tous(helper, EntityType.DROWNED).size()))
                .thenExecute(() -> {
                    List<Drowned> noyes = tous(helper, EntityType.DROWNED);
                    Drowned deSauvage = noyes.stream()
                            .min((x, y) -> Double.compare(x.distanceToSqr(helper.absoluteVec(Vec3.atBottomCenterOf(ICI))),
                                    y.distanceToSqr(helper.absoluteVec(Vec3.atBottomCenterOf(ICI)))))
                            .orElseThrow();
                    Drowned deSans = noyes.stream().filter(d -> d != deSauvage).findFirst().orElseThrow();
                    boolean s1 = MobOrigin.isWild(deSauvage);
                    boolean s2 = MobOrigin.isWild(deSans);
                    // La marque absente se recopie aussi : un noyé marqué par erreur la perd.
                    Zombie source = EntityType.ZOMBIE.create(helper.getLevel());
                    Drowned cible = EntityType.DROWNED.create(helper.getLevel());
                    MobOrigin.markWild(cible);
                    ForgeEventFactory.onLivingConvert(source, cible);
                    boolean s3 = MobOrigin.isWild(cible);
                    toutRetirer(helper);
                    helper.assertTrue(s1, "le noyé d'un zombie sauvage devrait être sauvage");
                    helper.assertTrue(!s2, "le noyé d'un zombie d'œuf ne devrait pas être sauvage");
                    helper.assertTrue(!s3, "l'absence de marque devrait se recopier");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "origine_b", setupTicks = 20, timeoutTicks = 100)
    public static void unTetardDeFraiDevientUneGrenouilleSansMarque(GameTestHelper helper) {
        degager(helper);
        // Un têtard sorti du frai : créé sans événement d'apparition, à un tick de devenir grenouille.
        Tadpole tetard = creer(helper, EntityType.TADPOLE, ICI);
        CompoundTag tag = tetard.saveWithoutId(new CompoundTag());
        tag.putInt("Age", Tadpole.ticksToBeFrog - 1);
        tetard.load(tag);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!tous(helper, EntityType.FROG).isEmpty(),
                        "le têtard n'est pas devenu grenouille"))
                .thenExecute(() -> {
                    List<Frog> grenouilles = tous(helper, EntityType.FROG);
                    boolean s = grenouilles.stream().anyMatch(MobOrigin::isWild);
                    toutRetirer(helper);
                    helper.assertTrue(!s, "la grenouille d'un têtard de frai ne devrait pas être sauvage");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = "origine_b", setupTicks = 20, timeoutTicks = 100)
    public static void unSlimeDiviseNeDonnePasDePetitsSauvages(GameTestHelper helper) {
        degager(helper);
        helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
                "difficulté paisible : le slime disparaîtrait");
        Slime grand = apparaitre(helper, EntityType.SLIME, new BlockPos(3, 1, 3), MobSpawnType.NATURAL);
        grand.setSize(2, true);
        sauvage(helper, grand, true, "le grand slime");
        grand.kill();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(grand.isRemoved()
                        && !tous(helper, EntityType.SLIME).isEmpty(), "le slime ne s'est pas divisé"))
                .thenExecute(() -> {
                    List<Slime> petits = tous(helper, EntityType.SLIME);
                    boolean s = petits.stream().anyMatch(MobOrigin::isWild);
                    int n = petits.size();
                    toutRetirer(helper);
                    helper.assertTrue(n >= 2, "au moins deux petits attendus, vus " + n);
                    helper.assertTrue(!s, "un petit d'un slime divisé ne devrait pas être sauvage");
                })
                .thenSucceed();
    }
}
