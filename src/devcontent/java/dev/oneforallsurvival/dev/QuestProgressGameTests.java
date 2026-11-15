package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.loot.MobLoot;
import dev.oneforallsurvival.economy.loot.MobLootLimits;
import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import dev.oneforallsurvival.economy.quest.Quest;
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import dev.oneforallsurvival.economy.quest.QuestGenerator.Series;
import dev.oneforallsurvival.economy.quest.QuestJobs;
import dev.oneforallsurvival.economy.quest.QuestProgress;
import dev.oneforallsurvival.economy.quest.QuestRef;
import dev.oneforallsurvival.economy.quest.QuestService;
import dev.oneforallsurvival.economy.quest.QuestTarget;
import dev.oneforallsurvival.economy.quest.QuestTemplate;
import dev.oneforallsurvival.economy.quest.QuestTemplates;
import dev.oneforallsurvival.food.CanteenPotBlockEntity;
import dev.oneforallsurvival.food.CanteenPotMenu;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.DryerMenu;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.repair.RepairActions;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.repair.WelderEnergy;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;

/**
 * La progression des quêtes par les vrais gestes d'un joueur d'essai en survie, et ses garde-fous : bloc posé puis
 * cassé, bloc recyclé, générateur de pierre, piston, mauvais outil, mob sans marque, faux joueur, créatif, quête finie.
 * Horloge de l'économie fixe et réserve d'essai (une quête par catégorie, quantités de 2 à 4), posées par lot ; chaque
 * essai écrit lui-même le carnet de son joueur (journalières du jour de l'horloge fixe : aucun renouvellement).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class QuestProgressGameTests {

    private static final String LOT_BLOCS = "quetesGestesBlocs";
    private static final String LOT_VIVANTS = "quetesGestesVivants";
    private static final String LOT_CUISINE = "quetesGestesCuisine";
    /** Mercredi 18 novembre 2026, midi (UTC) : loin de toute heure de renouvellement. */
    private static final long MAINTENANT = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli();
    /** Le joueur se tient dans un coin, loin des blocs de l'essai. */
    private static final BlockPos COIN = new BlockPos(6, 1, 6);

    private QuestProgressGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Réserve, horloge, carnet
    // ---------------------------------------------------------------------------------------------

    private static QuestTemplate modele(String id, QuestCategory categorie, String cible, int min, int max) {
        return new QuestTemplate(id, categorie, cible == null ? null : QuestTarget.parse(cible).orElseThrow(), min,
                max, 10L, 1.0D, Map.of(), OptionalInt.empty(), null);
    }

    /** Une quête par catégorie comptée, quantités de 2 à 4. */
    private static List<QuestTemplate> reserve() {
        return List.of(
                modele("essai_casse", QuestCategory.BREAK, "#minecraft:base_stone_overworld", 2, 4),
                modele("essai_pose", QuestCategory.PLACE, "minecraft:stone", 2, 4),
                modele("essai_combat", QuestCategory.KILL, "minecraft:zombie", 2, 4),
                modele("essai_recolte", QuestCategory.HARVEST, "minecraft:wheat", 2, 4),
                modele("essai_peche", QuestCategory.FISH, "#minecraft:fishes", 2, 4),
                modele("essai_cuisine", QuestCategory.COOK, null, 2, 4),
                modele("essai_conserve", QuestCategory.PRESERVE, null, 2, 4),
                modele("essai_elevage", QuestCategory.BREED, null, 2, 4),
                modele("essai_metier", QuestCategory.JOB, QuestJobs.REPAIR_MACHINE.toString(), 2, 4));
    }

    private static void avant() {
        EconomyClock.setClock(() -> MAINTENANT);
        QuestTemplates.setForTest(reserve());
    }

    private static void apres() {
        EconomyClock.reset();
        QuestTemplates.setForTest(null);
    }

    @BeforeBatch(batch = LOT_BLOCS)
    public static void avantBlocs(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_BLOCS)
    public static void apresBlocs(ServerLevel level) {
        apres();
    }

    @BeforeBatch(batch = LOT_VIVANTS)
    public static void avantVivants(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_VIVANTS)
    public static void apresVivants(ServerLevel level) {
        apres();
    }

    @BeforeBatch(batch = LOT_CUISINE)
    public static void avantCuisine(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_CUISINE)
    public static void apresCuisine(ServerLevel level) {
        apres();
    }

    /** Une quête neuve du modèle de la réserve de cette catégorie, au but donné (de 2 à 4). */
    private static Quest quete(QuestCategory categorie, int but) {
        QuestTemplate t = reserve().stream().filter(m -> m.category() == categorie).findFirst().orElseThrow();
        return quete(t.id(), categorie, t.target() == null ? null : t.target().text(), but);
    }

    private static Quest quete(String id, QuestCategory categorie, String cible, int but) {
        return new Quest(id, categorie, cible == null ? null : QuestTarget.parse(cible).orElseThrow(), but, 10L, 0,
                false, null);
    }

    /** Un carnet neuf avec ces journalières (le playerdata d'un passage précédent peut porter une progression). */
    private static void carnet(ServerPlayer joueur, Quest... journalieres) {
        QuestBook book = new QuestBook();
        book.setSeries(Series.DAILY, QuestService.today(MAINTENANT), List.of(journalieres));
        book.setSeries(Series.WEEKLY, QuestService.week(MAINTENANT), List.of());
        book.saveTo(joueur);
    }

    private static int progres(ServerPlayer joueur, int index) {
        return QuestBook.of(joueur).daily().get(index).progress();
    }

    // ---------------------------------------------------------------------------------------------
    // Joueur, gestes
    // ---------------------------------------------------------------------------------------------

    /** Un joueur d'essai en survie, dans le coin de l'essai ; son butin de mobs va dans des comptes détachés. */
    private static JoueurDeTest entrer(GameTestHelper helper, String nom) {
        JoueurDeTest simule = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(Vec3.atBottomCenterOf(COIN)));
        ServerPlayer joueur = simule.joueur();
        Vec3 coin = helper.absoluteVec(Vec3.atBottomCenterOf(COIN));
        joueur.teleportTo(coin.x, coin.y, coin.z);
        joueur.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        MobLoot.setBooksForTest(simule.uuid(), EconomyLedger.load(new CompoundTag()), new MobLootLimits());
        return simule;
    }

    private static void sortir(GameTestHelper helper, JoueurDeTest simule) {
        MobLoot.clearBooksForTest(simule.uuid());
        QuestProgress.setTimeShiftForTest(simule.uuid(), 0L);
        if (simule.connecte()) {
            simule.quitter();
        }
        ranger(helper);
    }

    /** L'espace de cet essai seulement (les essais d'un lot tournent côte à côte). */
    private static AABB zone(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 5, 8)));
    }

    private static void ranger(GameTestHelper helper) {
        Stream.of(EntityType.ITEM, EntityType.EXPERIENCE_ORB, EntityType.ZOMBIE, EntityType.COW)
                .forEach(type -> helper.getLevel().getEntities(type, zone(helper), e -> true).forEach(Entity::discard));
    }

    /** Pose un bloc de l'objet tenu, d'un clic sur le dessus du bloc d'en dessous, par le vrai chemin de Forge. */
    private static void poser(GameTestHelper helper, ServerPlayer joueur, BlockPos pos, Item objet) {
        joueur.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(objet, 64));
        // Sous le gabarit « plancher », la case du dessous peut être de l'air (vu : la pierre se posait dessous).
        if (helper.getBlockState(pos.below()).canBeReplaced()) {
            helper.setBlock(pos.below(), Blocks.STONE);
        }
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult clic = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP,
                abs.below(), false);
        ItemStack pile = joueur.getMainHandItem();
        InteractionResult r = pile.useOn(new UseOnContext(helper.getLevel(), joueur, InteractionHand.MAIN_HAND, pile, clic));
        helper.assertTrue(!helper.getBlockState(pos).isAir(), "pose de " + objet + " refusée : " + r);
    }

    /** Casse le bloc comme un joueur qui finit de le miner, outil en main. */
    private static void casser(GameTestHelper helper, ServerPlayer joueur, BlockPos pos, ItemStack outil) {
        joueur.setItemInHand(InteractionHand.MAIN_HAND, outil);
        joueur.gameMode.destroyBlock(helper.absolutePos(pos));
        helper.assertBlockPresent(Blocks.AIR, pos);
    }

    private static ItemStack pioche() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot des blocs
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void casserDeLaPierreNaturelleAvance(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteCasse");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.BREAK, 3));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.DEEPSLATE);
            casser(helper, joueur, new BlockPos(2, 1, 2), pioche());
            casser(helper, joueur, new BlockPos(3, 1, 2), pioche());
            helper.assertTrue(progres(joueur, 0) == 2, "casse : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void unBlocPosePuisCasseNAvanceRien(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QuetePoseCasse");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.BREAK, 3), quete(QuestCategory.PLACE, 3));
            BlockPos pos = new BlockPos(2, 1, 2);
            poser(helper, joueur, pos, Items.STONE);
            helper.assertBlockPresent(Blocks.STONE, pos);
            helper.assertTrue(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(pos)), "pierre posée non marquée");
            helper.assertTrue(progres(joueur, 1) == 1, "pose après la pose : " + progres(joueur, 1));
            casser(helper, joueur, pos, pioche());
            helper.assertTrue(progres(joueur, 0) == 0, "casse : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 0, "pose après la casse : " + progres(joueur, 1));
            helper.assertTrue(!PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(pos)), "marque restée");
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void unSeulBlocRecycleNeFaitPas64Poses(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteRecycle");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.PLACE, 4), quete(QuestCategory.BREAK, 4));
            BlockPos pos = new BlockPos(2, 1, 2);
            for (int i = 0; i < 10; i++) {
                poser(helper, joueur, pos, Items.STONE);
                helper.assertBlockPresent(Blocks.STONE, pos);
                casser(helper, joueur, pos, pioche());
            }
            helper.assertTrue(progres(joueur, 0) == 0, "pose : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 0, "casse : " + progres(joueur, 1));
            helper.assertTrue(QuestProgress.recentPlacementsForTest(simule.uuid()) == 0,
                    "poses retenues : " + QuestProgress.recentPlacementsForTest(simule.uuid()));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void poserEtGarderAvance(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteGarde");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.PLACE, 4), quete(QuestCategory.BREAK, 4));
            poser(helper, joueur, new BlockPos(2, 1, 2), Items.STONE);
            poser(helper, joueur, new BlockPos(3, 1, 2), Items.STONE);
            // 60 s d'horloge de jeu plus un tick : la casse ne retire plus la pose.
            QuestProgress.setTimeShiftForTest(simule.uuid(), EconomyConfig.questPlaceUndoSeconds() * 20L + 1L);
            casser(helper, joueur, new BlockPos(2, 1, 2), pioche());
            helper.assertTrue(progres(joueur, 0) == 2, "pose : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 0, "casse d'un bloc posé : " + progres(joueur, 1));
            // Juste sous la fenêtre : retirée.
            QuestProgress.setTimeShiftForTest(simule.uuid(), EconomyConfig.questPlaceUndoSeconds() * 20L - 1L);
            casser(helper, joueur, new BlockPos(3, 1, 2), pioche());
            helper.assertTrue(progres(joueur, 0) == 1, "pose sous la fenêtre : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** Lave qui coule rejointe par de l'eau : la pierre formée est marquée et sa casse n'avance rien. */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void laPierreDUnGenerateurNeComptePas(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteGenerateur");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete("essai_casse_tout", QuestCategory.BREAK, null, 4));
            BlockPos lave = new BlockPos(2, 1, 2);
            BlockPos eau = new BlockPos(3, 1, 2);
            helper.setBlock(lave, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2));
            helper.setBlock(eau, Blocks.WATER);
            helper.assertBlockPresent(Blocks.COBBLESTONE, lave);
            helper.setBlock(eau, Blocks.AIR);
            helper.assertTrue(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(lave)),
                    "pierre du générateur non marquée");
            casser(helper, joueur, lave, pioche());
            helper.assertTrue(progres(joueur, 0) == 0, "casse de la pierre du générateur : " + progres(joueur, 0));
            // Témoin : la même pierre, née du gabarit, compte.
            helper.setBlock(new BlockPos(2, 1, 5), Blocks.COBBLESTONE);
            casser(helper, joueur, new BlockPos(2, 1, 5), pioche());
            helper.assertTrue(progres(joueur, 0) == 1, "témoin : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_BLOCS, timeoutTicks = 100)
    public static void unBlocPousseParUnPistonNeComptePas(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QuetePiston");
        ServerPlayer joueur = simule.joueur();
        carnet(joueur, quete(QuestCategory.BREAK, 4));
        BlockPos piston = new BlockPos(1, 1, 1);
        BlockPos pierre = new BlockPos(2, 1, 1);
        BlockPos arrivee = new BlockPos(3, 1, 1);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pierre, Blocks.STONE);
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.STONE, arrivee))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(arrivee)),
                                "pierre poussée non marquée");
                        casser(helper, joueur, arrivee, pioche());
                        helper.assertTrue(progres(joueur, 0) == 0, "casse de la pierre poussée : " + progres(joueur, 0));
                        helper.setBlock(new BlockPos(2, 1, 5), Blocks.STONE);
                        casser(helper, joueur, new BlockPos(2, 1, 5), pioche());
                        helper.assertTrue(progres(joueur, 0) == 1, "témoin : " + progres(joueur, 0));
                    } finally {
                        helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
                        sortir(helper, simule);
                    }
                })
                .thenSucceed();
    }

    /** La marque est écrite avec le chunk (aller-retour par {@code ChunkSerializer}) ; 4 097 marques : la première oubliée. */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void laMarqueSurvitAuChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        LevelChunk chunk = level.getChunkAt(abs);
        int min = level.getMinBuildHeight();
        int tasse = PlacedBlocks.pack(abs, min);
        helper.assertTrue(PlacedBlocks.unpack(tasse, chunk.getPos(), min).equals(abs), "pack/unpack");
        try {
            chunk.setUnsaved(false);
            PlacedBlocks.mark(level, abs);
            helper.assertTrue(chunk.isUnsaved(), "le chunk n'est pas à sauver");
            CompoundTag ecrit = ChunkSerializer.write(level, chunk);
            Tag marques = ecrit.getCompound("ForgeCaps").get(PlacedBlocks.KEY.toString());
            helper.assertTrue(marques instanceof IntArrayTag, "pas de marques dans ForgeCaps : " + marques);
            PlacedBlocks relu = new PlacedBlocks();
            relu.deserializeNBT((IntArrayTag) marques);
            helper.assertTrue(relu.contains(tasse), "marque perdue à la relecture");
        } finally {
            PlacedBlocks.unmark(level, abs);
        }
        helper.assertTrue(EconomyConfig.questPlacedMarksPerChunk() == 4096,
                "borne : " + EconomyConfig.questPlacedMarksPerChunk());
        PlacedBlocks borne = new PlacedBlocks();
        for (int i = 0; i < 4097; i++) {
            borne.add(i, EconomyConfig.questPlacedMarksPerChunk());
        }
        helper.assertTrue(borne.size() == 4096 && !borne.contains(0) && borne.contains(1) && borne.contains(4096),
                "borne : " + borne.size() + ", première " + borne.contains(0));
        PlacedBlocks rond = new PlacedBlocks();
        rond.deserializeNBT(borne.serializeNBT());
        helper.assertTrue(rond.size() == 4096 && !rond.contains(0) && rond.contains(4096), "aller-retour de la borne");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_BLOCS)
    public static void sansLeBonOutilRienNAvance(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteOutil");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete("essai_fer", QuestCategory.BREAK, "#forge:ores/iron", 3));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_ORE);
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.IRON_ORE);
            casser(helper, joueur, new BlockPos(2, 1, 2), ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 0, "minerai cassé à la main : " + progres(joueur, 0));
            casser(helper, joueur, new BlockPos(3, 1, 2), new ItemStack(Items.STONE_PICKAXE));
            helper.assertTrue(progres(joueur, 0) == 1, "minerai cassé à la pioche : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot des vivants (et des garde-fous qui portent sur le joueur)
    // ---------------------------------------------------------------------------------------------

    private static void tuer(Entity mob, ServerPlayer joueur) {
        mob.invulnerableTime = 0;
        mob.hurt(mob.damageSources().playerAttack(joueur), 1000f);
    }

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void unZombieSauvageAvanceUnZombieDeGenerateurNon(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteZombie");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.KILL, 3), quete("essai_monstres", QuestCategory.KILL, null, 3));
            Zombie sauvage = ButinGameTests.creer(helper, EntityType.ZOMBIE, new BlockPos(2, 1, 2), true);
            tuer(sauvage, joueur);
            helper.assertTrue(!sauvage.isAlive(), "le zombie sauvage vit");
            Zombie generateur = ButinGameTests.creer(helper, EntityType.ZOMBIE, new BlockPos(3, 1, 2), false);
            tuer(generateur, joueur);
            helper.assertTrue(!generateur.isAlive(), "le zombie sans marque vit");
            helper.assertTrue(progres(joueur, 0) == 1, "zombies : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 1, "monstres : " + progres(joueur, 1));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /**
     * Un faux joueur (déployeur, ferme d'un mod) qui casse de la pierre naturelle, pose de la pierre et tue un zombie
     * sauvage n'avance rien, même avec le carnet d'un vrai joueur ; un joueur absent de la liste des joueurs non plus.
     * Ce qu'il pose est marqué.
     */
    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void unFauxJoueurNAvanceRien(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        ServerLevel level = helper.getLevel();
        FakePlayer faux = FakePlayerFactory.get(level, new GameProfile(
                UUID.nameUUIDFromBytes("QueteFauxJoueur".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "QueteFauxJoueur"));
        try {
            carnet(faux, quete(QuestCategory.BREAK, 3), quete(QuestCategory.PLACE, 3), quete(QuestCategory.KILL, 3));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            casser(helper, faux, new BlockPos(2, 1, 2), pioche());
            poser(helper, faux, new BlockPos(3, 1, 2), Items.STONE);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 2));
            helper.assertTrue(PlacedBlocks.isPlaced(level, helper.absolutePos(new BlockPos(3, 1, 2))),
                    "pose du faux joueur non marquée");
            Zombie zombie = ButinGameTests.creer(helper, EntityType.ZOMBIE, new BlockPos(4, 1, 4), true);
            tuer(zombie, faux);
            helper.assertTrue(!zombie.isAlive(), "le zombie vit");
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(progres(faux, i) == 0, "faux joueur, quête " + i + " : " + progres(faux, i));
            }
            // Un ServerPlayer qui n'est pas dans la liste des joueurs du serveur.
            ServerPlayer absent = new ServerPlayer(level.getServer(), level, new GameProfile(
                    UUID.nameUUIDFromBytes("QueteAbsent".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "QueteAbsent"));
            carnet(absent, quete(QuestCategory.BREAK, 3));
            helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
            MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, helper.absolutePos(new BlockPos(2, 1, 4)),
                    Blocks.STONE.defaultBlockState(), absent));
            helper.assertTrue(progres(absent, 0) == 0, "joueur absent de la liste : " + progres(absent, 0));
            helper.assertTrue(!QuestProgress.eligible(faux) && !QuestProgress.eligible(absent), "éligibles");
        } finally {
            ranger(helper);
        }
        helper.succeed();
    }

    /** Pose sans prévenir les voisins ni recalculer leur forme. */
    private static final int SANS_FORME = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void uneRecolteMureAvanceUnePousseNon(GameTestHelper helper) {
        // Le blé veut de la lumière (canSurvive) : posé normalement, il disparaissait dès qu'un voisin changeait de
        // forme (vu : « avant air »). Il est donc posé sans mise à jour de forme des voisins.
        JoueurDeTest simule = entrer(helper, "QueteRecolte");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.HARVEST, 4));
            BlockPos mur = new BlockPos(2, 1, 2);
            // Écartés : la casse de l'un ferait recalculer la forme du voisin.
            BlockPos pousse = new BlockPos(4, 1, 2);
            BlockPos seme = new BlockPos(2, 1, 4);
            for (BlockPos p : List.of(mur, pousse, seme)) {
                helper.setBlock(p.below(), Blocks.FARMLAND);
            }
            CropBlock ble = (CropBlock) Blocks.WHEAT;
            helper.getLevel().setBlock(helper.absolutePos(mur), ble.getStateForAge(ble.getMaxAge()), SANS_FORME);
            helper.getLevel().setBlock(helper.absolutePos(pousse), ble.getStateForAge(0), SANS_FORME);
            helper.assertBlockPresent(Blocks.WHEAT, mur);
            casser(helper, joueur, mur, ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 1, "blé mûr : " + progres(joueur, 0));
            helper.assertBlockPresent(Blocks.WHEAT, pousse);
            casser(helper, joueur, pousse, ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 1, "pousse : " + progres(joueur, 0));
            // Semé (marqué « posé », comme tout semis d'un joueur), puis mûr : la récolte compte quand même. La marque
            // est posée directement : semer par useOn exige la lumière du ciel, que le monde d'essai n'a pas ici.
            helper.getLevel().setBlock(helper.absolutePos(seme), ble.getStateForAge(ble.getMaxAge()), SANS_FORME);
            PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(seme));
            casser(helper, joueur, seme, ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 2, "blé semé puis mûr : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void peche(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QuetePeche");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.FISH, 3), quete("essai_peche_tout", QuestCategory.FISH, null, 4));
            FishingHook bouchon = new FishingHook(joueur, helper.getLevel(), 0, 0);
            MinecraftForge.EVENT_BUS.post(new ItemFishedEvent(
                    List.of(new ItemStack(Items.COD), new ItemStack(Items.LEATHER_BOOTS)), 1, bouchon));
            bouchon.discard();
            helper.assertTrue(progres(joueur, 0) == 1, "poissons : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 2, "tout objet pêché : " + progres(joueur, 1));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void elevage(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteElevage");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.BREED, 2));
            Cow a = ButinGameTests.creer(helper, EntityType.COW, new BlockPos(2, 1, 2), false);
            Cow b = ButinGameTests.creer(helper, EntityType.COW, new BlockPos(3, 1, 2), false);
            for (Cow vache : List.of(a, b)) {
                vache.setPersistenceRequired();
                vache.setAge(0);
                joueur.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
                vache.mobInteract(joueur, InteractionHand.MAIN_HAND);
                helper.assertTrue(vache.isInLove(), "vache non nourrie");
            }
            a.spawnChildFromBreeding(helper.getLevel(), b);
            long petits = helper.getLevel().getEntities(EntityType.COW, zone(helper), Cow::isBaby).size();
            helper.assertTrue(petits == 1, "petits : " + petits);
            helper.assertTrue(progres(joueur, 0) == 1, "élevage : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void leCreatifNAvanceRien(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteCreatif");
        try {
            ServerPlayer joueur = simule.joueur();
            joueur.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            carnet(joueur, quete(QuestCategory.BREAK, 3), quete(QuestCategory.PLACE, 3), quete(QuestCategory.KILL, 3));
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
            casser(helper, joueur, new BlockPos(2, 1, 2), pioche());
            poser(helper, joueur, new BlockPos(3, 1, 2), Items.STONE);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 2));
            Zombie zombie = ButinGameTests.creer(helper, EntityType.ZOMBIE, new BlockPos(4, 1, 4), true);
            tuer(zombie, joueur);
            helper.assertTrue(!zombie.isAlive(), "le zombie vit");
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(progres(joueur, i) == 0, "créatif, quête " + i + " : " + progres(joueur, i));
            }
        } finally {
            simule.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** Une quête finie s'arrête à son but ; réclamée, plus rien ne la touche. */
    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void uneQueteFinieNAvancePlus(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteFinie");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.BREAK, 2));
            for (int x = 1; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
            }
            for (int x = 1; x <= 3; x++) {
                casser(helper, joueur, new BlockPos(x, 1, 2), pioche());
            }
            helper.assertTrue(progres(joueur, 0) == 2, "finie : " + progres(joueur, 0));
            QuestBook book = QuestBook.of(joueur);
            helper.assertTrue(book.markClaimed(book.ref(QuestRef.Kind.DAILY, 0)), "réclamation refusée");
            book.saveTo(joueur);
            casser(helper, joueur, new BlockPos(4, 1, 2), pioche());
            Quest apres = QuestBook.of(joueur).daily().get(0);
            helper.assertTrue(apres.claimed() && apres.progress() == 2, "réclamée : " + apres);
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VIVANTS)
    public static void reparerUneMachineAvanceLaQueteDeMetier(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteMecano");
        try {
            ServerPlayer joueur = simule.joueur();
            RoleSavedData.get(helper.getLevel().getServer()).book().assign(simule.uuid(), RoleBook.ENGINEER_ROLE_ID);
            carnet(joueur, quete(QuestCategory.JOB, 2));
            helper.setBlock(new BlockPos(1, 1, 1), DevBlocks.testMachine());
            TestMachineBlockEntity machine = (TestMachineBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            machine.breakDown(FaultType.FUSE);
            ItemStack poste = new ItemStack(RepairRegistry.WELDER.get());
            WelderEnergy.store(poste, 1000);
            // Une réparation refusée (mauvaise pièce) n'avance rien.
            RepairActions.Result rate = RepairActions.repair(joueur, machine, poste,
                    new ItemStack(RepairRegistry.partItem(FaultType.BEARING, false)));
            helper.assertTrue(rate != RepairActions.Result.REPAIRED && progres(joueur, 0) == 0, "refus : " + rate);
            RepairActions.Result r = RepairActions.repair(joueur, machine, poste,
                    new ItemStack(RepairRegistry.partItem(FaultType.FUSE, false)));
            helper.assertTrue(r == RepairActions.Result.REPAIRED, "réparation : " + r);
            helper.assertTrue(progres(joueur, 0) == 1, "métier : " + progres(joueur, 0));
        } finally {
            RoleSavedData.get(helper.getLevel().getServer()).book().assign(simule.uuid(), RoleBook.DEFAULT_ROLE_ID);
            sortir(helper, simule);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot de la cuisine
    // ---------------------------------------------------------------------------------------------

    /**
     * Un four : un lingot n'est pas un aliment ; trois steaks au clic majuscule, un au clic. Un fumoir : deux côtelettes
     * au clic majuscule, en conserve. Comptes exacts : ni double compte, ni pile d'origine comptée à la place du pris.
     */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void cuisineAuFourEtConserveAuFumoir(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteFour");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.COOK, 4), quete(QuestCategory.PRESERVE, 4));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
            FurnaceBlockEntity four = (FurnaceBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            FurnaceMenu menuFour = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
            four.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            menuFour.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(four.getItem(2).isEmpty() && progres(joueur, 0) == 0, "lingots : " + progres(joueur, 0));
            four.setItem(2, new ItemStack(Items.COOKED_BEEF, 3));
            menuFour.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(four.getItem(2).isEmpty(), "steaks restés : " + four.getItem(2));
            helper.assertTrue(progres(joueur, 0) == 3, "cuisine au clic majuscule : " + progres(joueur, 0));
            four.setItem(2, new ItemStack(Items.COOKED_BEEF, 1));
            menuFour.clicked(2, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menuFour.getCarried().is(Items.COOKED_BEEF), "steak non pris");
            menuFour.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 4, "cuisine au clic : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 0, "conserve après le four : " + progres(joueur, 1));

            helper.setBlock(new BlockPos(3, 1, 1), Blocks.SMOKER);
            SmokerBlockEntity fumoir = (SmokerBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
            SmokerMenu menuFumoir = new SmokerMenu(2, joueur.getInventory(), fumoir, new SimpleContainerData(4));
            fumoir.setItem(2, new ItemStack(Items.COOKED_PORKCHOP, 2));
            menuFumoir.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 1) == 2, "conserve au fumoir : " + progres(joueur, 1));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void sortieDuSechoirEtDeLaMarmite(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteSechoir");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.PRESERVE, 4), quete(QuestCategory.COOK, 4));
            BlockPos posSechoir = new BlockPos(1, 1, 1);
            helper.setBlock(posSechoir, FoodRegistry.DRYER.get());
            DryerBlockEntity sechoir = (DryerBlockEntity) helper.getBlockEntity(posSechoir);
            DryerMenu menuSechoir = new DryerMenu(1, joueur.getInventory(), sechoir, helper.absolutePos(posSechoir));
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DRIED_KELP, 2));
            menuSechoir.clicked(DryerBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 2, "séchoir au clic majuscule : " + progres(joueur, 0));
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DRIED_KELP, 1));
            menuSechoir.clicked(DryerBlockEntity.SLOT_OUTPUT, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menuSechoir.getCarried().is(Items.DRIED_KELP), "séchage non pris");
            menuSechoir.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 3, "séchoir au clic : " + progres(joueur, 0));

            BlockPos posMarmite = new BlockPos(3, 1, 1);
            helper.setBlock(posMarmite, FoodRegistry.CANTEEN_POT.get());
            CanteenPotBlockEntity marmite = (CanteenPotBlockEntity) helper.getBlockEntity(posMarmite);
            CanteenPotMenu menuMarmite = new CanteenPotMenu(2, joueur.getInventory(), marmite,
                    helper.absolutePos(posMarmite));
            marmite.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, new ItemStack(Items.BAKED_POTATO, 2));
            menuMarmite.clicked(CanteenPotBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 1) == 2, "marmite au clic majuscule : " + progres(joueur, 1));
            marmite.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, new ItemStack(Items.BAKED_POTATO, 1));
            menuMarmite.clicked(CanteenPotBlockEntity.SLOT_OUTPUT, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menuMarmite.getCarried().is(Items.BAKED_POTATO), "plat non pris");
            menuMarmite.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 1) == 3, "marmite au clic : " + progres(joueur, 1));
            helper.assertTrue(progres(joueur, 0) == 3, "conserve après la marmite : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** Le vrai Cooking Pot de Farmer's Delight : deux plats au clic majuscule, un au clic, comptés par le tag des plats. */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void unPlatDuCookingPotDeFarmersDelight(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteCookingPot");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete("essai_plats", QuestCategory.COOK, "#farmersdelight:meals", 4));
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, ModBlocks.COOKING_POT.get());
            CookingPotBlockEntity pot = (CookingPotBlockEntity) helper.getBlockEntity(pos);
            CookingPotMenu menu = new CookingPotMenu(1, joueur.getInventory(), pot, new SimpleContainerData(2));
            pot.getInventory().setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, new ItemStack(ModItems.BEEF_STEW.get(), 2));
            menu.clicked(CookingPotMenu.INDEX_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(pot.getInventory().getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT).isEmpty(),
                    "plats restés");
            helper.assertTrue(progres(joueur, 0) == 2, "Cooking Pot au clic majuscule : " + progres(joueur, 0));
            pot.getInventory().setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, new ItemStack(ModItems.BEEF_STEW.get(), 1));
            menu.clicked(CookingPotMenu.INDEX_OUTPUT, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menu.getCarried().is(ModItems.BEEF_STEW.get()), "plat non pris");
            menu.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 3, "Cooking Pot au clic : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }
}
