package dev.oneforallsurvival.dev;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.loot.MobCoinTable;
import dev.oneforallsurvival.economy.loot.MobCoinTables;
import dev.oneforallsurvival.economy.loot.MobLoot;
import dev.oneforallsurvival.economy.loot.MobLootLimits;
import dev.oneforallsurvival.economy.loot.MobOrigin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Le butin des mobs à la mort ({@link MobLoot}), par le vrai chemin du jeu : un mob tué par {@code hurt} déclenche
 * {@code LivingDropsEvent}, les pièces ajoutées deviennent des {@link CoinDropEntity} au tick suivant. Sauf mention,
 * la table est « certaine » (chances à 1, nombres fixes, {@link #tableCertaine}) et le butin du joueur d'essai
 * s'écrit dans des comptes détachés ({@link MobLoot#setBooksForTest}). Lots de 8 au plus.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ButinGameTests {

    private static final int PLAFOND = 4;
    private static final BlockPos ICI = new BlockPos(2, 1, 2);
    private static final BlockPos LA = new BlockPos(5, 1, 5);
    /** Loin des mobs : le joueur ne ramasse pas les pièces. */
    private static final Vec3 LOIN = new Vec3(6.5, 1, 1.5);

    private static final String LOT_A = "butin_a";
    private static final String LOT_B = "butin_b";
    private static final String LOT_REGLE = "butin_regle";
    private static final String LOT_ECO = "butin_eco";
    private static final String LOT_HORLOGE = "butin_horloge";
    private static final String LOT_DEPART = "butin_depart";
    private static final String LOT_RELOAD = "butin_reload";
    private static final String LOT_DRAGON = "butin_dragon";
    private static final String LOT_PLAFOND = "butin_plafond";
    private static final String LOT_AVERTI = "butin_averti";

    /** Courant 3 cuivres, dangereux 1 argent, boss 5 or, animal 1 cuivre, pillard 40 cuivres : tout à coup sûr. */
    static MobCoinTable tableCertaine() {
        return MobCoinTable.parse(JsonParser.parseString("""
                {"tiers": {
                  "common": {"rolls": [{"coin": "copper", "chance": 1, "min": 3, "max": 3}]},
                  "dangerous": {"rolls": [{"coin": "silver", "chance": 1, "min": 1, "max": 1}]},
                  "boss": {"any_origin": true, "rolls": [{"coin": "gold", "chance": 1, "min": 5, "max": 5}]},
                  "wild_animal": {"rolls": [{"coin": "copper", "chance": 1, "min": 1, "max": 1}]},
                  "gros": {"rolls": [{"coin": "copper", "chance": 1, "min": 40, "max": 40}]}},
                 "rules": [{"match": "#forge:bosses", "tier": "boss"}, {"match": "minecraft:elder_guardian", "tier": "boss"},
                           {"match": "minecraft:pillager", "tier": "gros"}],
                 "category_defaults": {"monster": "common", "creature": "wild_animal"},
                 "charged_creeper": "dangerous"}
                """).getAsJsonObject());
    }

    private ButinGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Lots : table certaine, rendue après
    // ---------------------------------------------------------------------------------------------

    @BeforeBatch(batch = LOT_A)
    public static void avantA(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        MobCoinTables.setForTest(null);
    }

    @BeforeBatch(batch = LOT_B)
    public static void avantB(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        MobCoinTables.setForTest(null);
    }

    private static boolean butinAvant = true;

    @BeforeBatch(batch = LOT_REGLE)
    public static void avantRegle(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
        butinAvant = level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false, level.getServer());
    }

    @AfterBatch(batch = LOT_REGLE)
    public static void apresRegle(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(butinAvant, level.getServer());
        MobCoinTables.setForTest(null);
    }

    @BeforeBatch(batch = LOT_ECO)
    public static void avantEco(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_ECO)
    public static void apresEco(ServerLevel level) {
        MobCoinTables.setForTest(null);
    }

    @BeforeBatch(batch = LOT_HORLOGE)
    public static void avantHorloge(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_HORLOGE)
    public static void apresHorloge(ServerLevel level) {
        EconomyClock.reset();
        MobCoinTables.setForTest(null);
    }

    @BeforeBatch(batch = LOT_DRAGON)
    public static void avantDragon(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_DRAGON)
    public static void apresDragon(ServerLevel level) {
        MobCoinTables.setForTest(null);
    }

    @BeforeBatch(batch = LOT_PLAFOND)
    public static void avantPlafond(ServerLevel level) {
        MobCoinTables.setForTest(tableCertaine());
    }

    @AfterBatch(batch = LOT_PLAFOND)
    public static void apresPlafond(ServerLevel level) {
        EconomyClock.reset();
        MobCoinTables.setForTest(null);
    }

    /** Un essai qui a échoué en route laisse son datapack : on le retire et on recharge, pour les lots suivants. */
    private static void retirerDatapack(ServerLevel level, String nom) {
        Path pack = level.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(nom);
        if (Files.exists(pack)) {
            supprimer(pack);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "reload");
        }
    }

    @AfterBatch(batch = LOT_RELOAD)
    public static void apresReload(ServerLevel level) {
        retirerDatapack(level, "butin_invalide");
    }

    @AfterBatch(batch = LOT_AVERTI)
    public static void apresAverti(ServerLevel level) {
        retirerDatapack(level, "butin_inconnu");
    }

    @BeforeBatch(batch = LOT_DEPART)
    public static void avantDepart(ServerLevel level) {
        MobCoinTables.setForTest(null);
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Dégage l'espace d'essai et le couvre (leçons du 2026-11-09 et du 2026-11-11). */
    static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 1; y < PLAFOND; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, PLAFOND, z), Blocks.STONE);
            }
        }
    }

    /** Un mob ajouté au monde sans événement d'apparition, marqué sauvage si demandé. */
    static <T extends Mob> T creer(GameTestHelper helper, EntityType<T> type, BlockPos pos, boolean sauvage) {
        T mob = type.create(helper.getLevel());
        helper.assertTrue(mob != null, "création de " + type);
        Vec3 v = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        mob.moveTo(v.x, v.y, v.z, 0f, 0f);
        if (sauvage) {
            MobOrigin.markWild(mob);
        }
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    /** Les comptes détachés d'un joueur d'essai. */
    record Comptes(EconomyLedger registre, MobLootLimits limites) {}

    static Comptes ouvrir(JoueurDeTest joueur) {
        Comptes comptes = new Comptes(EconomyLedger.load(new CompoundTag()), new MobLootLimits());
        MobLoot.setBooksForTest(joueur.uuid(), comptes.registre(), comptes.limites());
        return comptes;
    }

    static void fermer(JoueurDeTest joueur) {
        MobLoot.clearBooksForTest(joueur.uuid());
        if (joueur.joueur().getServer().getPlayerList().getPlayer(joueur.uuid()) == joueur.joueur()) {
            joueur.quitter();
        }
    }

    static void frapper(LivingEntity mob, JoueurDeTest joueur, float degats) {
        mob.invulnerableTime = 0;
        mob.hurt(mob.damageSources().playerAttack(joueur.joueur()), degats);
    }

    /** Les pièces au sol de l'espace d'essai. */
    static List<CoinDropEntity> pieces(GameTestHelper helper) {
        return helper.getLevel().getEntities(EconomyRegistry.coinDropType(), zone(helper), e -> true);
    }

    /**
     * L'espace de cet essai seulement : les essais d'un lot tournent côte à côte, et un rayon autour du centre
     * compterait (ou rangerait) les pièces et les mobs du voisin.
     */
    static AABB zone(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, PLAFOND + 1, 8)));
    }

    static long valeur(GameTestHelper helper) {
        return pieces(helper).stream().mapToLong(CoinDropEntity::value).sum();
    }

    /** Retire ce que les morts ont laissé ; les pièces au sol sont réglées par leur retrait. */
    static void ranger(GameTestHelper helper) {
        Stream.of(EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.ZOMBIE, EntityType.COW, EntityType.WOLF,
                        EntityType.WITHER, EntityType.PILLAGER, EntityType.CREEPER, EntityType.CHICKEN, EntityType.ARROW,
                        EntityType.ELDER_GUARDIAN, EntityType.ENDER_DRAGON)
                .forEach(type -> helper.getLevel().getEntities(type, zone(helper), e -> true).forEach(Entity::discard));
        pieces(helper).forEach(Entity::discard);
    }

    /**
     * Le cas commun : prépare, tue, attend le tick du remplacement des objets par des pièces au sol, vérifie que leur
     * valeur et le registre détaché valent {@code attendu}, range.
     */
    private static void essai(GameTestHelper helper, String nom, long attendu, Consumer<JoueurDeTest> tuer) {
        degager(helper);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        try {
            tuer.accept(joueur);
        } catch (RuntimeException e) {
            fermer(joueur);
            ranger(helper);
            throw e;
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long sol = valeur(helper);
                    long registre = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
                    Long duJoueur = comptes.registre().lootByPlayer().getOrDefault(joueur.uuid(), 0L);
                    fermer(joueur);
                    ranger(helper);
                    helper.assertTrue(sol == attendu, "pièces au sol : " + sol + " cuivres, attendu " + attendu);
                    helper.assertTrue(registre == attendu, "registre : " + registre + " cuivres, attendu " + attendu);
                    helper.assertTrue(duJoueur == attendu, "total du joueur : " + duJoueur + ", attendu " + attendu);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot a : qui a tué, d'où vient le mob
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unZombieSauvageTueParUnJoueurDonne(GameTestHelper helper) {
        essai(helper, "ButinZombie", 3, joueur -> frapper(creer(helper, EntityType.ZOMBIE, ICI, true), joueur, 1000f));
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unZombieSansMarqueNeDonneRien(GameTestHelper helper) {
        essai(helper, "ButinSansMarque", 0, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, false);
            frapper(zombie, joueur, 1000f);
            helper.assertTrue(!zombie.isAlive(), "le zombie sans marque n'est pas mort");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unZombieDeGenerateurNeDonneRien(GameTestHelper helper) {
        essai(helper, "ButinGenerateur", 0, joueur -> {
            Zombie zombie = EntityType.ZOMBIE.spawn(helper.getLevel(), helper.absolutePos(ICI), MobSpawnType.SPAWNER);
            helper.assertTrue(zombie != null, "le zombie du générateur n'est pas apparu");
            helper.assertTrue(!MobOrigin.isWild(zombie), "le zombie du générateur est marqué");
            if (zombie.getVehicle() != null) {
                zombie.getVehicle().discard();
            }
            frapper(zombie, joueur, 1000f);
            helper.assertTrue(!zombie.isAlive(), "le zombie du générateur n'est pas mort");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unWitherSansMarqueDonneSonButin(GameTestHelper helper) {
        essai(helper, "ButinWither", 500,
                joueur -> frapper(creer(helper, EntityType.WITHER, ICI, false), joueur, 10_000f));
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unFauxJoueurNeDonneRien(GameTestHelper helper) {
        essai(helper, "ButinFaux", 0, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            FakePlayer faux = FakePlayerFactory.getMinecraft(helper.getLevel());
            zombie.hurt(zombie.damageSources().playerAttack(faux), 1000f);
            helper.assertTrue(!zombie.isAlive(), "le faux joueur devait tuer le zombie");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unVraiCoupPuisUnFauxJoueurNeDonneRien(GameTestHelper helper) {
        essai(helper, "ButinVraiFaux", 0, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            frapper(zombie, joueur, 2f);
            helper.assertTrue(zombie.isAlive(), "le premier coup ne devait pas tuer");
            zombie.invulnerableTime = 0;
            zombie.hurt(zombie.damageSources().playerAttack(FakePlayerFactory.getMinecraft(helper.getLevel())), 1000f);
            helper.assertTrue(!zombie.isAlive(), "le faux joueur devait achever le zombie");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void laLaveSansJoueurNeDonneRien(GameTestHelper helper) {
        essai(helper, "ButinLave", 0, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            zombie.hurt(zombie.damageSources().lava(), 1000f);
            helper.assertTrue(!zombie.isAlive(), "la lave devait tuer le zombie");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, setupTicks = 20)
    public static void unVraiCoupPuisLaChuteDonne(GameTestHelper helper) {
        essai(helper, "ButinChute", 3, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            frapper(zombie, joueur, 2f);
            zombie.invulnerableTime = 0;
            zombie.hurt(zombie.damageSources().fall(), 1000f);
            helper.assertTrue(!zombie.isAlive(), "la chute devait achever le zombie");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot b : flèche, animaux, annulation, journal, raids, creeper chargé
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void uneFlecheDuJoueurDonne(GameTestHelper helper) {
        essai(helper, "ButinFleche", 3, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            Arrow fleche = new Arrow(helper.getLevel(), joueur.joueur());
            zombie.hurt(zombie.damageSources().arrow(fleche, joueur.joueur()), 1000f);
            fleche.discard();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unVeauNeDonneRienUneVacheSauvageDonne(GameTestHelper helper) {
        essai(helper, "ButinVeau", 1, joueur -> {
            Cow veau = creer(helper, EntityType.COW, ICI, true);
            veau.setBaby(true);
            helper.assertTrue(veau.isBaby(), "le veau n'est pas petit");
            frapper(veau, joueur, 1000f);
            helper.assertTrue(!veau.isAlive(), "le veau n'est pas mort");
            frapper(creer(helper, EntityType.COW, LA, true), joueur, 1000f);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unLoupApprivoiseNeDonneRien(GameTestHelper helper) {
        // Le témoin : un loup sauvage non apprivoisé donne 1 cuivre ; l'apprivoisé, rien.
        essai(helper, "ButinLoup", 1, joueur -> {
            Wolf chien = creer(helper, EntityType.WOLF, ICI, true);
            chien.tame(joueur.joueur());
            helper.assertTrue(chien.isTame(), "le loup n'est pas apprivoisé");
            frapper(chien, joueur, 1000f);
            helper.assertTrue(!chien.isAlive(), "le loup apprivoisé n'est pas mort");
            frapper(creer(helper, EntityType.WOLF, LA, true), joueur, 1000f);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void desObjetsAnnulesNeComptentPas(GameTestHelper helper) {
        List<Entity> visees = new ArrayList<>();
        Consumer<LivingDropsEvent> annuleur = event -> {
            if (visees.contains(event.getEntity())) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingDropsEvent.class, annuleur);
        try {
            // Le témoin, un zombie sauvage dont les objets ne sont pas annulés, paie ses 3 cuivres : seul le visé ne donne rien.
            essai(helper, "ButinAnnule", 3, joueur -> {
                Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
                visees.add(zombie);
                frapper(zombie, joueur, 1000f);
                helper.assertTrue(!zombie.isAlive(), "le zombie visé n'est pas mort");
                frapper(creer(helper, EntityType.ZOMBIE, LA, true), joueur, 1000f);
            });
        } finally {
            MinecraftForge.EVENT_BUS.unregister(annuleur);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unGrosButinEstAuJournal(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinJournal", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        try {
            frapper(creer(helper, EntityType.ZOMBIE, ICI, true), joueur, 1000f);
            helper.assertTrue(comptes.registre().totalCreated(MoneySource.MOB_LOOT) == 3, "le zombie n'a pas payé");
            helper.assertTrue(comptes.registre().entryCount() == 0, "un butin de 3 cuivres ne va pas au journal");
            frapper(creer(helper, EntityType.WITHER, LA, false), joueur, 10_000f);
            helper.assertTrue(comptes.registre().entryCount() == 1,
                    "lignes du journal : " + comptes.registre().entryCount());
            EconomyLedger.Entry ligne = comptes.registre().entries(0).get(0);
            helper.assertTrue(ligne.kind().equals("created.mob_loot") && ligne.amount() == 500
                    && joueur.uuid().equals(ligne.author()) && ligne.reason().equals("butin : minecraft:wither"),
                    "ligne du journal : " + ligne);
            helper.assertTrue(comptes.registre().lootKills() == 2, "morts payantes : " + comptes.registre().lootKills());
            helper.assertTrue(comptes.registre().lootByTier().equals(java.util.Map.of("common", 3L, "boss", 500L)),
                    "par palier : " + comptes.registre().lootByTier());
        } finally {
            fermer(joueur);
            ranger(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unRaidPlafonneSonButin(GameTestHelper helper) {
        degager(helper);
        ServerLevel level = helper.getLevel();
        JoueurDeTest joueur = JoueurDeTest.rejoindre(level, "ButinRaid", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        List<Long> gains = new ArrayList<>();
        try {
            BlockPos centre = helper.absolutePos(new BlockPos(4, 1, 4));
            Raid premier = new Raid(910_001, level, centre);
            Raid second = new Raid(910_002, level, centre);
            for (int i = 0; i < 4; i++) {
                gains.add(tuerPillard(helper, comptes, joueur, enRaid(helper, premier, i)));
            }
            // Un pillard sauvage recruté par le raid en cours, sans apparition : le même plafond le tient.
            Pillager recrue = creer(helper, EntityType.PILLAGER, LA, true);
            recrue.setCurrentRaid(premier);
            gains.add(tuerPillard(helper, comptes, joueur, recrue));
            // Un second raid repart à zéro.
            gains.add(tuerPillard(helper, comptes, joueur, enRaid(helper, second, 0)));
        } catch (RuntimeException e) {
            fermer(joueur);
            ranger(helper);
            throw e;
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long sol = valeur(helper);
                    fermer(joueur);
                    ranger(helper);
                    helper.assertTrue(gains.equals(List.of(40L, 40L, 20L, 0L, 0L, 40L)), "gains par mort : " + gains);
                    helper.assertTrue(sol == 140, "pièces au sol : " + sol);
                    helper.assertTrue(comptes.limites().raidRemaining(
                            level.dimension().location() + "#910001", 100) == 0, "le premier raid devait être plein");
                    helper.assertTrue(comptes.limites().raidRemaining(
                            level.dimension().location() + "#910002", 100) == 60, "le second raid devait avoir payé 40");
                })
                .thenSucceed();
    }

    /** Un pillard qui rejoint le raid comme le fait une vague (apparition en EVENT). */
    private static Pillager enRaid(GameTestHelper helper, Raid raid, int rang) {
        Pillager pillard = EntityType.PILLAGER.create(helper.getLevel());
        helper.assertTrue(pillard != null, "création du pillard");
        raid.joinRaid(1, pillard, helper.absolutePos(new BlockPos(1 + rang, 0, 5)), false);
        helper.assertTrue(pillard.getCurrentRaid() == raid, "le pillard n'a pas rejoint le raid");
        helper.assertTrue(MobOrigin.isWild(pillard), "le pillard du raid devrait être sauvage");
        return pillard;
    }

    private static long tuerPillard(GameTestHelper helper, Comptes comptes, JoueurDeTest joueur, Pillager pillard) {
        long avant = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
        frapper(pillard, joueur, 1000f);
        helper.assertTrue(!pillard.isAlive(), "le pillard n'est pas mort");
        return comptes.registre().totalCreated(MoneySource.MOB_LOOT) - avant;
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unCreeperChargeEstDangereux(GameTestHelper helper) {
        // Chargé : palier dangereux (1 argent = 10) ; ordinaire : courant (3 cuivres).
        essai(helper, "ButinCreeper", 13, joueur -> {
            Creeper charge = creer(helper, EntityType.CREEPER, ICI, true);
            CompoundTag tag = charge.saveWithoutId(new CompoundTag());
            tag.putBoolean("powered", true);
            charge.load(tag);
            helper.assertTrue(charge.isPowered(), "le creeper n'est pas chargé");
            frapper(charge, joueur, 1000f);
            frapper(creer(helper, EntityType.CREEPER, LA, true), joueur, 1000f);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_B, setupTicks = 20)
    public static void unJoueurAbsentDeLaListeNeDonneRien(GameTestHelper helper) {
        // Un faux joueur d'un mod qui ne dérive pas de FakePlayer : un ServerPlayer jamais entré dans la liste des joueurs.
        // Le témoin, le vrai joueur de l'essai, touche ses 3 cuivres.
        essai(helper, "ButinFantome", 3, joueur -> {
            ServerPlayer fantome = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "ButinFantomeFaux"));
            helper.assertTrue(!(fantome instanceof FakePlayer)
                    && helper.getLevel().getServer().getPlayerList().getPlayer(fantome.getUUID()) == null,
                    "le fantôme devrait être un ServerPlayer hors de la liste");
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            zombie.hurt(zombie.damageSources().playerAttack(fantome), 1000f);
            helper.assertTrue(!zombie.isAlive(), "le fantôme devait tuer le zombie");
            frapper(creer(helper, EntityType.ZOMBIE, LA, true), joueur, 1000f);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lots à eux
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_REGLE, setupTicks = 20)
    public static void doMobLootFauxNeDonneRien(GameTestHelper helper) {
        helper.assertTrue(!helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT),
                "doMobLoot devrait être faux dans ce lot");
        // Le témoin : la règle rendue le temps d'un second zombie, qui paie ses 3 cuivres ; le premier, rien.
        essai(helper, "ButinRegle", 3, joueur -> {
            Zombie zombie = creer(helper, EntityType.ZOMBIE, ICI, true);
            frapper(zombie, joueur, 1000f);
            helper.assertTrue(!zombie.isAlive(), "le zombie n'est pas mort");
            GameRules.BooleanValue regle = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
            regle.set(true, helper.getLevel().getServer());
            try {
                frapper(creer(helper, EntityType.ZOMBIE, LA, true), joueur, 1000f);
            } finally {
                regle.set(false, helper.getLevel().getServer());
            }
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, setupTicks = 20)
    public static void unBossUneFoisParJourEtParJoueur(GameTestHelper helper) {
        degager(helper);
        long midi = ZonedDateTime.of(2030, 1, 15, 12, 0, 0, 0,
                dev.oneforallsurvival.economy.EconomyCalendar.configuredZone()).toInstant().toEpochMilli();
        EconomyClock.setClock(() -> midi);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinBossJour", helper.absoluteVec(LOIN));
        JoueurDeTest autre = JoueurDeTest.rejoindre(helper.getLevel(), "ButinBossAutre",
                helper.absoluteVec(new Vec3(6.5, 1, 6.5)));
        Comptes comptes = ouvrir(joueur);
        Comptes comptesAutre = ouvrir(autre);
        try {
            frapper(creer(helper, EntityType.WITHER, ICI, false), joueur, 10_000f);
            long premier = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            frapper(creer(helper, EntityType.WITHER, ICI, false), joueur, 10_000f);
            long second = comptes.registre().totalCreated(MoneySource.MOB_LOOT) - premier;
            // Un autre joueur, le même jour, touche le sien.
            frapper(creer(helper, EntityType.WITHER, LA, false), autre, 10_000f);
            long autreJoueur = comptesAutre.registre().totalCreated(MoneySource.MOB_LOOT);
            EconomyClock.setClock(() -> midi + 86_400_000L);
            frapper(creer(helper, EntityType.WITHER, ICI, false), joueur, 10_000f);
            long lendemain = comptes.registre().totalCreated(MoneySource.MOB_LOOT) - premier - second;
            helper.assertTrue(premier == 500, "premier Wither : " + premier);
            helper.assertTrue(second == 0, "second Wither le même jour : " + second);
            helper.assertTrue(autreJoueur == 500, "Wither d'un autre joueur le même jour : " + autreJoueur);
            helper.assertTrue(lendemain == 500, "Wither du lendemain : " + lendemain);
        } finally {
            EconomyClock.reset();
            fermer(joueur);
            fermer(autre);
            ranger(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, setupTicks = 20)
    public static void unBossSauvagePaieAChaqueFois(GameTestHelper helper) {
        // Décision de Killian : le délai d'un jour ne vise que les boss sans marque sauvage (Wither construit, dragon ranimé) ;
        // les trois gardiens anciens d'un monument, nés avec lui, paient chacun.
        degager(helper);
        long midi = ZonedDateTime.of(2030, 2, 15, 12, 0, 0, 0,
                dev.oneforallsurvival.economy.EconomyCalendar.configuredZone()).toInstant().toEpochMilli();
        EconomyClock.setClock(() -> midi);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinGardiens", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        try {
            for (int i = 0; i < 3; i++) {
                ElderGuardian gardien = creer(helper, EntityType.ELDER_GUARDIAN, ICI, true);
                frapper(gardien, joueur, 10_000f);
                helper.assertTrue(!gardien.isAlive(), "le gardien " + (i + 1) + " n'est pas mort");
            }
            long gardiens = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            for (int i = 0; i < 2; i++) {
                frapper(creer(helper, EntityType.WITHER, LA, false), joueur, 10_000f);
            }
            long withers = comptes.registre().totalCreated(MoneySource.MOB_LOOT) - gardiens;
            helper.assertTrue(gardiens == 1500, "trois gardiens anciens sauvages le même jour : " + gardiens + ", attendu 1500");
            helper.assertTrue(withers == 500, "deux Withers construits le même jour : " + withers + ", attendu 500");
        } finally {
            EconomyClock.reset();
            fermer(joueur);
            ranger(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_DRAGON, setupTicks = 20)
    public static void leDragonNeDonneQuUneFois(GameTestHelper helper) {
        // Le dragon meurt en deux temps : le coup fatal appelle die (LivingDropsEvent), puis il passe en phase DYING, vie
        // remise à 1, et tickDeath (200 ticks, sans die) le retire. Il est retiré ici avant son premier tick : ailleurs que
        // dans l'End, il casserait les blocs voisins (checkWalls).
        degager(helper);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinDragon", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        int[] evenements = {0};
        List<Entity> vise = new ArrayList<>();
        Consumer<LivingDropsEvent> compteur = event -> {
            if (vise.contains(event.getEntity())) {
                evenements[0]++;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDropsEvent.class, compteur);
        try {
            EnderDragon dragon = creer(helper, EntityType.ENDER_DRAGON, new BlockPos(4, 1, 4), false);
            vise.add(dragon);
            // Comme le dragon d'un combat de l'End (EndDragonFight.createNewDragon) : en vol, pas posé.
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            frapper(dragon, joueur, 10_000f);
            helper.assertTrue(dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING,
                    "le dragon n'est pas en phase de mort : " + dragon.getPhaseManager().getCurrentPhase().getPhase());
            // Des coups pendant la phase de mort ne le tuent pas une seconde fois.
            for (int i = 0; i < 3; i++) {
                frapper(dragon, joueur, 10_000f);
            }
            dragon.discard();
        } catch (RuntimeException e) {
            MinecraftForge.EVENT_BUS.unregister(compteur);
            fermer(joueur);
            ranger(helper);
            throw e;
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(compteur);
                    long sol = valeur(helper);
                    long registre = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
                    long morts = comptes.registre().lootKills();
                    fermer(joueur);
                    ranger(helper);
                    helper.assertTrue(evenements[0] == 1, "LivingDropsEvent du dragon : " + evenements[0] + ", attendu 1");
                    helper.assertTrue(morts == 1, "morts payantes : " + morts);
                    helper.assertTrue(sol == 500 && registre == 500, "pièces au sol " + sol + ", registre " + registre + ", attendu 500");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_DEPART, setupTicks = 20, timeoutTicks = 200)
    public static void centMortsComptesJustes(GameTestHelper helper) {
        degager(helper);
        helper.assertTrue(MobCoinTables.current().tier("common") != null, "la table de départ n'est pas chargée");
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinCent", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        try {
            for (int i = 0; i < 200; i++) {
                Zombie zombie = creer(helper, EntityType.ZOMBIE, new BlockPos(1 + i % 5, 1, 1 + (i / 5) % 5), true);
                zombie.getRandom().setSeed(7_000L + i);
                frapper(zombie, joueur, 1000f);
            }
        } catch (RuntimeException e) {
            fermer(joueur);
            ranger(helper);
            throw e;
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long sol = valeur(helper);
                    long registre = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
                    long morts = comptes.registre().lootKills();
                    fermer(joueur);
                    ranger(helper);
                    helper.assertTrue(registre > 0 && morts > 0 && morts < 200,
                            "un tiers environ des morts paie : " + morts + " morts payantes, " + registre + " cuivres");
                    helper.assertTrue(sol == registre, "pièces au sol " + sol + " contre registre " + registre);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ECO, setupTicks = 20)
    public static void ecoLootMontreLesTotaux(GameTestHelper helper) {
        degager(helper);
        MinecraftServer server = helper.getLevel().getServer();
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinEco", helper.absoluteVec(LOIN));
        try {
            // Le registre du serveur, cette fois.
            EconomyLedger registre = EconomyLedger.get(server);
            long avant = registre.totalCreated(MoneySource.MOB_LOOT);
            frapper(creer(helper, EntityType.ZOMBIE, ICI, true), joueur, 1000f);
            long total = registre.totalCreated(MoneySource.MOB_LOOT);
            helper.assertTrue(total == avant + 3, "registre du serveur : " + avant + " puis " + total);
            List<Component> sortie = new ArrayList<>();
            CommandSourceStack equipe = server.createCommandSourceStack().withSource(new Ecoute(sortie))
                    .withPermission(2);
            int r = server.getCommands().performPrefixedCommand(equipe, "eco loot");
            helper.assertTrue(r == 1 && sortie.size() == 1, "réponse de /eco loot : " + r + ", " + sortie);
            String texte = sortie.get(0).getString();
            helper.assertTrue(texte.contains(CoinMath.format(total).getString()), "total absent : " + texte);
            helper.assertTrue(texte.contains("ButinEco"), "nom du joueur absent : " + texte);
            helper.assertTrue(texte.contains("common"), "palier absent : " + texte);
            // Sans le niveau 2, la commande n'existe pas.
            List<Component> refus = new ArrayList<>();
            int r0 = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSource(new Ecoute(refus)).withPermission(0), "eco loot");
            helper.assertTrue(r0 == 0, "/eco loot accepté sans le niveau 2");
        } finally {
            joueur.quitter();
            ranger(helper);
        }
        helper.succeed();
    }

    /** Une source de commande qui garde ce qu'on lui dit. */
    private record Ecoute(List<Component> messages) implements CommandSource {
        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    /** Garde les messages du journal d'un logger, le temps d'un essai. */
    private static final class Journal extends AbstractAppender {
        final List<String> lignes = new CopyOnWriteArrayList<>();

        Journal() {
            super("butin-essai", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lignes.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
        }

        boolean vu(String niveau, String texte) {
            return lignes.stream().anyMatch(l -> l.startsWith(niveau) && l.contains(texte));
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_RELOAD, timeoutTicks = 1200)
    public static void unFichierInvalideGardeLaTablePrecedente(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("butin_invalide");
        Path fichier = pack.resolve("data/oneforallsurvival/economy/mob_coins.json");
        // Un passage précédent interrompu peut avoir laissé son datapack : il démarrerait ce passage avec une table invalide.
        if (Files.exists(pack)) {
            supprimer(pack);
        }
        Files.createDirectories(fichier.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\": {\"pack_format\": 15, \"description\": \"essai du butin\"}}", StandardCharsets.UTF_8);
        // Bien formé, mais une coupure inconnue : le chargeur le transmet, la lecture le refuse.
        Files.writeString(fichier, "{\"tiers\": {\"common\": {\"rolls\": [{\"coin\": \"plomb\", \"chance\": 1, "
                + "\"min\": 1, \"max\": 1}]}}}", StandardCharsets.UTF_8);
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(MobCoinTables.class.getName());
        Journal journal = new Journal();
        journal.start();
        logger.addAppender(journal);
        MobCoinTable avant = MobCoinTables.current();
        Runnable rendre = () -> {
            logger.removeAppender(journal);
            journal.stop();
        };
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(journal.vu("ERROR", "mob_coins.json invalide"),
                        "aucune erreur du butin au journal : " + journal.lignes))
                .thenExecute(() -> {
                    boolean garde = MobCoinTables.current() == avant;
                    boolean nomme = journal.vu("ERROR", "plomb");
                    boolean actif = server.getPackRepository().getSelectedIds().contains("file/butin_invalide");
                    supprimer(pack);
                    journal.lignes.clear();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
                    if (!garde || !nomme || !actif) {
                        rendre.run();
                    }
                    helper.assertTrue(actif, "le datapack d'essai n'a pas été activé");
                    helper.assertTrue(garde, "la table a changé malgré le fichier invalide");
                    helper.assertTrue(nomme, "l'erreur ne dit pas pourquoi : " + journal.lignes);
                })
                .thenWaitUntil(() -> helper.assertTrue(journal.vu("INFO", "Butin des mobs : "),
                        "la table livrée n'est pas revenue : " + journal.lignes))
                .thenExecute(() -> {
                    rendre.run();
                    helper.assertTrue(MobCoinTables.current() != avant && MobCoinTables.current().tier("boss") != null,
                            "la table livrée n'est pas en service");
                    helper.assertTrue(!server.getPackRepository().getSelectedIds().contains("file/butin_invalide"),
                            "le datapack d'essai est resté actif");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_AVERTI, timeoutTicks = 1200)
    public static void uneRegleSansEffetEstSignaleeAuJournal(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("butin_inconnu");
        Path fichier = pack.resolve("data/oneforallsurvival/economy/mob_coins.json");
        if (Files.exists(pack)) {
            supprimer(pack);
        }
        Files.createDirectories(fichier.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\": {\"pack_format\": 15, \"description\": \"essai du butin\"}}", StandardCharsets.UTF_8);
        // Valide, mais deux règles qui ne visent rien : une faute de frappe d'identifiant et une de tag.
        Files.writeString(fichier, "{\"tiers\": {\"common\": {\"rolls\": [{\"coin\": \"copper\", \"chance\": 1, "
                + "\"min\": 1, \"max\": 1}]}}, \"rules\": [{\"match\": \"minecraft:endermen\", \"tier\": \"none\"}, "
                + "{\"match\": \"#minecraft:raidres\", \"tier\": \"none\"}, "
                + "{\"match\": \"minecraft:zombie\", \"tier\": \"common\"}, "
                + "{\"match\": \"#minecraft:raiders\", \"tier\": \"common\"}]}", StandardCharsets.UTF_8);
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(MobCoinTables.class.getName());
        Journal journal = new Journal();
        journal.start();
        logger.addAppender(journal);
        MobCoinTable avant = MobCoinTables.current();
        Runnable rendre = () -> {
            logger.removeAppender(journal);
            journal.stop();
        };
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(journal.vu("WARN", "minecraft:endermen"),
                        "aucun avertissement pour l'identifiant inconnu : " + journal.lignes))
                .thenExecute(() -> {
                    boolean tag = journal.vu("WARN", "minecraft:raidres");
                    boolean connus = journal.lignes.stream().noneMatch(l -> l.startsWith("WARN")
                            && (l.contains("minecraft:zombie") || l.contains("minecraft:raiders")));
                    boolean acceptee = MobCoinTables.current() != avant && MobCoinTables.current().tier("common") != null;
                    supprimer(pack);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
                    if (!tag || !connus || !acceptee) {
                        rendre.run();
                    }
                    helper.assertTrue(tag, "aucun avertissement pour le tag inconnu : " + journal.lignes);
                    helper.assertTrue(connus, "une règle valide est signalée : " + journal.lignes);
                    helper.assertTrue(acceptee, "la table n'a pas été acceptée malgré les règles sans effet");
                })
                .thenWaitUntil(() -> helper.assertTrue(MobCoinTables.current().tier("boss") != null
                        && !server.getPackRepository().getSelectedIds().contains("file/butin_inconnu"),
                        "la table livrée n'est pas revenue"))
                .thenExecute(rendre)
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Plafond horaire par joueur (200 cuivres sur 60 minutes glissantes, boss exemptés)
    // ---------------------------------------------------------------------------------------------

    private static void tuerPillards(GameTestHelper helper, JoueurDeTest joueur, int nombre) {
        for (int i = 0; i < nombre; i++) {
            Pillager pillard = creer(helper, EntityType.PILLAGER, ICI, true);
            frapper(pillard, joueur, 10_000f);
            helper.assertTrue(!pillard.isAlive(), "le pillard " + (i + 1) + " n'est pas mort");
        }
    }

    private static void tuerZombie(GameTestHelper helper, JoueurDeTest joueur) {
        Zombie zombie = creer(helper, EntityType.ZOMBIE, LA, true);
        frapper(zombie, joueur, 10_000f);
        helper.assertTrue(!zombie.isAlive(), "le zombie n'est pas mort");
    }

    @GameTest(template = "test/plancher", batch = LOT_PLAFOND, setupTicks = 20)
    public static void lePlafondHoraireTronqueEtSeRouvreApresUneHeure(GameTestHelper helper) {
        degager(helper);
        long t0 = ZonedDateTime.of(2030, 3, 15, 12, 0, 0, 0,
                dev.oneforallsurvival.economy.EconomyCalendar.configuredZone()).toInstant().toEpochMilli();
        EconomyClock.setClock(() -> t0);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinPlafond", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        long[] vu = new long[6];
        try {
            // Sous le plafond : 4 pillards (160) et 3 zombies (9) paient tout.
            tuerPillards(helper, joueur, 4);
            for (int i = 0; i < 3; i++) {
                tuerZombie(helper, joueur);
            }
            vu[0] = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            // Un pillard de 40 pour 31 restants : tronqué à 31, pile 200.
            tuerPillards(helper, joueur, 1);
            vu[1] = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            // Au plafond : rien.
            tuerZombie(helper, joueur);
            vu[2] = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            // 59 minutes après : rien encore ; 60 minutes après : le premier gain est sorti de la fenêtre.
            EconomyClock.setClock(() -> t0 + 59 * 60_000L);
            tuerZombie(helper, joueur);
            vu[3] = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            EconomyClock.setClock(() -> t0 + 60 * 60_000L);
            tuerZombie(helper, joueur);
            vu[4] = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            vu[5] = comptes.limites().hourlyEarned(joueur.uuid(), t0 + 60 * 60_000L);
        } catch (RuntimeException e) {
            EconomyClock.reset();
            fermer(joueur);
            ranger(helper);
            throw e;
        }
        EconomyClock.reset();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    long sol = valeur(helper);
                    fermer(joueur);
                    ranger(helper);
                    helper.assertTrue(vu[0] == 169, "sous le plafond : " + vu[0] + ", attendu 169");
                    helper.assertTrue(vu[1] == 200, "butin tronqué : " + vu[1] + ", attendu 200");
                    helper.assertTrue(vu[2] == 200, "au plafond : " + vu[2] + ", attendu 200");
                    helper.assertTrue(vu[3] == 200, "59 minutes plus tard : " + vu[3] + ", attendu 200");
                    helper.assertTrue(vu[4] == 203, "60 minutes plus tard : " + vu[4] + ", attendu 203");
                    helper.assertTrue(vu[5] == 3, "gains de l'heure glissante : " + vu[5] + ", attendu 3");
                    helper.assertTrue(sol == vu[4], "pièces au sol : " + sol + ", registre " + vu[4]);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_PLAFOND, setupTicks = 20)
    public static void lesBossNeSontPasLimitesParLePlafondHoraire(GameTestHelper helper) {
        degager(helper);
        long t0 = ZonedDateTime.of(2030, 4, 15, 12, 0, 0, 0,
                dev.oneforallsurvival.economy.EconomyCalendar.configuredZone()).toInstant().toEpochMilli();
        EconomyClock.setClock(() -> t0);
        JoueurDeTest joueur = JoueurDeTest.rejoindre(helper.getLevel(), "ButinPlafondBoss", helper.absoluteVec(LOIN));
        Comptes comptes = ouvrir(joueur);
        long avant;
        long apres;
        try {
            tuerPillards(helper, joueur, 5);
            avant = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
            frapper(creer(helper, EntityType.WITHER, LA, false), joueur, 10_000f);
            apres = comptes.registre().totalCreated(MoneySource.MOB_LOOT);
        } finally {
            EconomyClock.reset();
            fermer(joueur);
            ranger(helper);
        }
        helper.assertTrue(avant == 200, "plafond atteint : " + avant + ", attendu 200");
        helper.assertTrue(apres - avant == 500, "Wither au plafond : " + (apres - avant) + ", attendu 500");
        helper.succeed();
    }

    private static void supprimer(Path dossier) {
        try (Stream<Path> tout = Files.walk(dossier)) {
            for (Path p : tout.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("datapack d'essai non supprimé : " + dossier, e);
        }
    }
}
