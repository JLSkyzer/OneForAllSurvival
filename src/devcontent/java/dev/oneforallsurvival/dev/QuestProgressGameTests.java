package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.loot.MobLoot;
import dev.oneforallsurvival.economy.loot.MobLootLimits;
import dev.oneforallsurvival.economy.quest.CookingCredit;
import dev.oneforallsurvival.economy.quest.PlacedBlocks;
import dev.oneforallsurvival.economy.quest.Quest;
import dev.oneforallsurvival.economy.quest.QuestBook;
import dev.oneforallsurvival.economy.quest.QuestCategory;
import dev.oneforallsurvival.economy.quest.QuestEvent;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
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
    private static final String LOT_BLOCS2 = "quetesGestesBlocs2";
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

    @BeforeBatch(batch = LOT_BLOCS2)
    public static void avantBlocs2(ServerLevel level) {
        avant();
    }

    @AfterBatch(batch = LOT_BLOCS2)
    public static void apresBlocs2(ServerLevel level) {
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

    /** La marque est écrite avec le chunk (aller-retour par {@code ChunkSerializer}) ; 4 097 marques : chunk saturé. */
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
        // Rien n'est oublié : la 4 097e sature le chunk, et toute case y compte alors pour « posé ».
        helper.assertTrue(borne.size() == 4096 && borne.contains(0) && !borne.contains(4096) && borne.saturated(),
                "borne : " + borne.size() + ", première " + borne.contains(0) + ", saturé " + borne.saturated());
        helper.assertTrue(borne.covers(0) && borne.covers(4096) && borne.covers(123_456), "chunk saturé non couvert");
        PlacedBlocks rond = new PlacedBlocks();
        rond.deserializeNBT(borne.serializeNBT());
        helper.assertTrue(rond.size() == 4096 && rond.contains(0) && rond.saturated(), "aller-retour de la borne");
        rond.remove(0);
        helper.assertTrue(rond.covers(0), "une marque retirée d'un chunk saturé ne le désature pas");
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
    // Second lot des blocs (tour 1 de la relecture)
    // ---------------------------------------------------------------------------------------------

    /**
     * Du sable posé par le joueur au-dessus d'un vide tombe : la pose comptée est retirée dès qu'il tombe, sa case
     * d'atterrissage est marquée et sa casse à la pelle n'avance rien (ferme d'un seul bloc de sable sinon). Témoin : un
     * sable du gabarit compte.
     */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS2, timeoutTicks = 100)
    public static void unBlocQuiTombeNeComptePas(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteSable");
        ServerPlayer joueur = simule.joueur();
        carnet(joueur, quete("essai_sable", QuestCategory.BREAK, "#minecraft:sand", 4),
                quete("essai_pose_sable", QuestCategory.PLACE, "minecraft:sand", 4),
                quete("essai_pose_libre", QuestCategory.PLACE, null, 4));
        BlockPos sol = new BlockPos(2, 0, 2);
        BlockPos appui = new BlockPos(2, 2, 2);
        BlockPos pose = new BlockPos(2, 3, 2);
        BlockPos arrivee = new BlockPos(2, 1, 2);
        helper.setBlock(sol, Blocks.STONE);
        helper.setBlock(appui, Blocks.STONE);
        poser(helper, joueur, pose, Items.SAND);
        helper.assertBlockPresent(Blocks.SAND, pose);
        helper.assertTrue(progres(joueur, 1) == 1, "pose du sable : " + progres(joueur, 1));
        helper.assertTrue(progres(joueur, 2) == 1, "pose sans cible : " + progres(joueur, 2));
        helper.setBlock(appui, Blocks.AIR);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.SAND, arrivee))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(arrivee)),
                                "case d'atterrissage non marquée");
                        // La pose comptée ne tient pas : le bloc est parti de lui-même.
                        helper.assertTrue(progres(joueur, 1) == 0, "pose après la chute : " + progres(joueur, 1));
                        // Une quête de pose sans cible (possible pour un événement) ne se remplit pas non plus.
                        helper.assertTrue(progres(joueur, 2) == 0, "pose sans cible après la chute : " + progres(joueur, 2));
                        casser(helper, joueur, arrivee, new ItemStack(Items.IRON_SHOVEL));
                        helper.assertTrue(progres(joueur, 0) == 0, "casse du sable tombé : " + progres(joueur, 0));
                        helper.setBlock(new BlockPos(4, 1, 2), Blocks.SAND);
                        casser(helper, joueur, new BlockPos(4, 1, 2), new ItemStack(Items.IRON_SHOVEL));
                        helper.assertTrue(progres(joueur, 0) == 1, "témoin : " + progres(joueur, 0));
                    } finally {
                        sortir(helper, simule);
                    }
                })
                .thenSucceed();
    }

    private static QuestEvent evenementPose(long debut) {
        return new QuestEvent("fete", QuestCategory.PLACE, null, 4, 30L, debut, debut + 100_000_000L, "",
                new UUID(1L, 1L));
    }

    /** Un carnet neuf qui ne porte que cet événement (aucune série). */
    private static void carnetEvenement(ServerPlayer joueur, QuestEvent evenement) {
        QuestBook book = new QuestBook();
        book.setSeries(Series.DAILY, QuestService.today(MAINTENANT), List.of());
        book.setSeries(Series.WEEKLY, QuestService.week(MAINTENANT), List.of());
        book.refresh(QuestService.today(MAINTENANT), QuestService.week(MAINTENANT), s -> List.of(), 8,
                List.of(evenement));
        book.saveTo(joueur);
    }

    private static int progresEvenement(ServerPlayer joueur) {
        return QuestBook.of(joueur).events().get("fete").progress();
    }

    /** Une quête d'événement de pose (sans cible) : poser puis casser dans la minute n'avance rien. */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS2)
    public static void uneQueteDEvenementDePoseSAnnuleSiOnCasse(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteEvPose");
        try {
            ServerPlayer joueur = simule.joueur();
            carnetEvenement(joueur, evenementPose(1000L));
            BlockPos pos = new BlockPos(2, 1, 2);
            poser(helper, joueur, pos, Items.STONE);
            helper.assertTrue(progresEvenement(joueur) == 1, "pose : " + progresEvenement(joueur));
            casser(helper, joueur, pos, pioche());
            helper.assertTrue(progresEvenement(joueur) == 0, "pose après la casse : " + progresEvenement(joueur));
            for (int i = 0; i < 6; i++) {
                poser(helper, joueur, pos, Items.STONE);
                casser(helper, joueur, pos, pioche());
            }
            helper.assertTrue(progresEvenement(joueur) == 0, "bloc recyclé : " + progresEvenement(joueur));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** L'événement est relancé entre la pose et la casse : la quête neuve n'est pas retouchée. */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS2)
    public static void unEvenementRelanceNEstPasRetouchePourUneCasse(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteEvRelance");
        try {
            ServerPlayer joueur = simule.joueur();
            carnetEvenement(joueur, evenementPose(1000L));
            BlockPos premier = new BlockPos(2, 1, 2);
            poser(helper, joueur, premier, Items.STONE);
            helper.assertTrue(progresEvenement(joueur) == 1, "pose : " + progresEvenement(joueur));
            // Relancé sous le même identifiant (autre début) : la quête neuve repart de zéro.
            QuestBook book = QuestBook.of(joueur);
            book.refresh(QuestService.today(MAINTENANT), QuestService.week(MAINTENANT), s -> List.of(), 8,
                    List.of(evenementPose(2000L)));
            book.saveTo(joueur);
            helper.assertTrue(progresEvenement(joueur) == 0, "la neuve a hérité : " + progresEvenement(joueur));
            poser(helper, joueur, new BlockPos(4, 1, 2), Items.STONE);
            helper.assertTrue(progresEvenement(joueur) == 1, "pose de la neuve : " + progresEvenement(joueur));
            casser(helper, joueur, premier, pioche());
            helper.assertTrue(progresEvenement(joueur) == 1, "la casse a retouché la neuve : " + progresEvenement(joueur));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /**
     * Un chunk qui atteint la borne des marques est saturé : la pierre du gabarit, jamais posée, n'y compte plus. Les
     * marques du chunk sont rendues à leur état d'avant dans le même appel (les essais voisins n'en voient rien).
     */
    @GameTest(template = "test/plancher", batch = LOT_BLOCS2)
    public static void unChunkSatureNeCompteAucuneCasse(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteSature");
        ServerLevel level = helper.getLevel();
        BlockPos pierre = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(pierre);
        PlacedBlocks marques = level.getChunkAt(abs).getCapability(PlacedBlocks.CAPABILITY).resolve().orElseThrow();
        IntArrayTag avant = marques.serializeNBT();
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.BREAK, 4));
            helper.setBlock(pierre, Blocks.STONE);
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
            casser(helper, joueur, new BlockPos(3, 1, 2), pioche());
            helper.assertTrue(progres(joueur, 0) == 1, "témoin avant saturation : " + progres(joueur, 0));
            net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(abs);
            int n = 0;
            for (int y = level.getMinBuildHeight(); n <= EconomyConfig.questPlacedMarksPerChunk(); y++) {
                for (int x = 0; x < 16 && n <= EconomyConfig.questPlacedMarksPerChunk(); x++) {
                    for (int z = 0; z < 16 && n <= EconomyConfig.questPlacedMarksPerChunk(); z++) {
                        BlockPos p = new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z);
                        if (!p.equals(abs)) {
                            PlacedBlocks.mark(level, p);
                            n++;
                        }
                    }
                }
            }
            helper.assertTrue(PlacedBlocks.isPlaced(level, abs), "chunk non saturé après " + n + " marques");
            casser(helper, joueur, pierre, pioche());
            helper.assertTrue(progres(joueur, 0) == 1, "casse dans un chunk saturé : " + progres(joueur, 0));
        } finally {
            marques.deserializeNBT(avant);
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

    /** Le joueur pose la pile tenue au curseur dans cette case, d'un clic. */
    private static void deposer(GameTestHelper helper, AbstractContainerMenu menu, ServerPlayer joueur, int index,
            ItemStack pile) {
        menu.setCarried(pile);
        menu.clicked(index, 0, ClickType.PICKUP, joueur);
        helper.assertTrue(menu.getCarried().isEmpty(), "dépôt refusé en case " + index + " : reste " + menu.getCarried());
    }

    /** Le joueur glisse la pile tenue sur ces cases (bouton gauche : répartie). */
    private static void glisser(GameTestHelper helper, AbstractContainerMenu menu, ServerPlayer joueur, ItemStack pile,
            int... cases) {
        menu.setCarried(pile);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, joueur);
        for (int c : cases) {
            menu.clicked(c, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, joueur);
        }
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, joueur);
        helper.assertTrue(menu.getCarried().isEmpty(), "glisser refusé : reste " + menu.getCarried());
    }

    /** Le joueur fait passer d'un clic majuscule la pile de sa première case de barre d'action dans l'écran. */
    private static void depuisLaBarre(GameTestHelper helper, AbstractContainerMenu menu, ServerPlayer joueur,
            ItemStack pile) {
        joueur.getInventory().setItem(0, pile);
        for (Slot s : menu.slots) {
            if (s.container == joueur.getInventory() && s.getContainerSlot() == 0) {
                menu.clicked(s.index, 0, ClickType.QUICK_MOVE, joueur);
                helper.assertTrue(joueur.getInventory().getItem(0).isEmpty(),
                        "clic majuscule refusé : reste " + joueur.getInventory().getItem(0));
                return;
            }
        }
        throw new IllegalStateException("case 0 de la barre absente de l'écran");
    }

    /** La cuisson elle-même, sans attendre le combustible : l'entrée du four devient autant d'objets cuits en sortie. */
    private static void cuire(FurnaceBlockEntity four, Item cuit) {
        int n = four.getItem(0).getCount() + four.getItem(2).getCount();
        four.setItem(0, ItemStack.EMPTY);
        four.setItem(2, new ItemStack(cuit, n));
    }

    private static FurnaceBlockEntity four(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.FURNACE);
        return (FurnaceBlockEntity) helper.getBlockEntity(pos);
    }

    /** Une trémie posée sur le four, chargée : elle pousse dans l'entrée, 1 objet tous les 8 tics. */
    private static void tremie(GameTestHelper helper, BlockPos four, ItemStack charge) {
        helper.setBlock(four.above(), Blocks.HOPPER);
        ((HopperBlockEntity) helper.getBlockEntity(four.above())).setItem(0, charge);
    }

    /**
     * Un four : des lingots (pas un aliment) consomment le crédit de leur minerai sans rien compter ; trois steaks au
     * clic majuscule, un au clic, chacun mis cru par le joueur. Un fumoir : deux côtelettes, en conserve. Comptes exacts.
     */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void cuisineAuFourEtConserveAuFumoir(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteFour");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.COOK, 4), quete(QuestCategory.PRESERVE, 4));
            FurnaceBlockEntity four = four(helper, new BlockPos(1, 1, 1));
            FurnaceMenu menuFour = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
            deposer(helper, menuFour, joueur, 0, new ItemStack(Items.RAW_IRON, 2));
            cuire(four, Items.IRON_INGOT);
            menuFour.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(four.getItem(2).isEmpty() && progres(joueur, 0) == 0, "lingots : " + progres(joueur, 0));
            helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 0,
                    "crédit après les lingots : " + CookingCredit.credit(four, simule.uuid()));
            depuisLaBarre(helper, menuFour, joueur, new ItemStack(Items.BEEF, 4));
            helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 4,
                    "crédit des steaks crus : " + CookingCredit.credit(four, simule.uuid()));
            four.setItem(0, new ItemStack(Items.BEEF, 1));
            four.setItem(2, new ItemStack(Items.COOKED_BEEF, 3));
            menuFour.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(four.getItem(2).isEmpty(), "steaks restés : " + four.getItem(2));
            helper.assertTrue(progres(joueur, 0) == 3, "cuisine au clic majuscule : " + progres(joueur, 0));
            cuire(four, Items.COOKED_BEEF);
            menuFour.clicked(2, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menuFour.getCarried().is(Items.COOKED_BEEF), "steak non pris");
            menuFour.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 4, "cuisine au clic : " + progres(joueur, 0));
            helper.assertTrue(progres(joueur, 1) == 0, "conserve après le four : " + progres(joueur, 1));

            helper.setBlock(new BlockPos(3, 1, 1), Blocks.SMOKER);
            SmokerBlockEntity fumoir = (SmokerBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 1));
            SmokerMenu menuFumoir = new SmokerMenu(2, joueur.getInventory(), fumoir, new SimpleContainerData(4));
            deposer(helper, menuFumoir, joueur, 0, new ItemStack(Items.PORKCHOP, 2));
            fumoir.setItem(0, ItemStack.EMPTY);
            fumoir.setItem(2, new ItemStack(Items.COOKED_PORKCHOP, 2));
            menuFumoir.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 1) == 2, "conserve au fumoir : " + progres(joueur, 1));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** Un four nourri par une trémie puis vidé par le joueur ne compte rien : le joueur n'a rien mis. */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE, timeoutTicks = 200)
    public static void unFourNourriParUneTremieNeCompteRien(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteTremie");
        ServerPlayer joueur = simule.joueur();
        carnet(joueur, quete(QuestCategory.COOK, 16));
        BlockPos pos = new BlockPos(1, 1, 1);
        FurnaceBlockEntity four = four(helper, pos);
        FurnaceMenu menu = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
        tremie(helper, pos, new ItemStack(Items.BEEF, 8));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(four.getItem(0).getCount() == 8, "entrée " + four.getItem(0)))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 0,
                                "crédit donné par la trémie : " + CookingCredit.credit(four, simule.uuid()));
                        cuire(four, Items.COOKED_BEEF);
                        menu.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
                        helper.assertTrue(four.getItem(2).isEmpty(), "steaks restés : " + four.getItem(2));
                        helper.assertTrue(progres(joueur, 0) == 0, "ferme à trémie comptée : " + progres(joueur, 0));
                    } finally {
                        sortir(helper, simule);
                    }
                })
                .thenSucceed();
    }

    /**
     * Huit crus mis par le joueur (glissés à la souris), huit cuits retirés : huit. Puis le livre de recettes (un geste
     * de l'écran aussi) remet quatre crus : quatre de plus ; et ce que le joueur reprend cru ne compte plus.
     */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void huitCrusMisParLeJoueurFontHuitCuits(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteHuit");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.COOK, 16));
            FurnaceBlockEntity four = four(helper, new BlockPos(1, 1, 1));
            FurnaceMenu menu = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
            glisser(helper, menu, joueur, new ItemStack(Items.BEEF, 8), 0);
            helper.assertTrue(four.getItem(0).getCount() == 8 && CookingCredit.credit(four, simule.uuid()) == 8,
                    "entrée " + four.getItem(0) + ", crédit " + CookingCredit.credit(four, simule.uuid()));
            cuire(four, Items.COOKED_BEEF);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 8, "huit cuits : " + progres(joueur, 0));
            helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 0,
                    "crédit après : " + CookingCredit.credit(four, simule.uuid()));

            Recipe<?> recette = helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation("minecraft", "cooked_beef")).orElseThrow();
            joueur.awardRecipes(List.of(recette));
            joueur.getInventory().setItem(0, new ItemStack(Items.BEEF, 4));
            menu.handlePlacement(true, recette, joueur);
            helper.assertTrue(four.getItem(0).getCount() == 4 && CookingCredit.credit(four, simule.uuid()) == 4,
                    "livre de recettes : entrée " + four.getItem(0) + ", crédit " + CookingCredit.credit(four, simule.uuid()));
            // Le joueur reprend deux crus d'un clic droit (la moitié) : ils ne cuiront plus pour lui.
            menu.clicked(0, 1, ClickType.PICKUP, joueur);
            menu.setCarried(ItemStack.EMPTY);
            helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 2,
                    "crédit après reprise : " + CookingCredit.credit(four, simule.uuid()));
            cuire(four, Items.COOKED_BEEF);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 10, "après le livre de recettes : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }

    /** Huit crus de la trémie et quatre du joueur (clic majuscule) : douze cuits retirés, quatre comptés. */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE, timeoutTicks = 200)
    public static void tremieEtJoueurMelangesNeComptentQueLaPartDuJoueur(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteMelange");
        ServerPlayer joueur = simule.joueur();
        carnet(joueur, quete(QuestCategory.COOK, 16));
        BlockPos pos = new BlockPos(1, 1, 1);
        FurnaceBlockEntity four = four(helper, pos);
        FurnaceMenu menu = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
        tremie(helper, pos, new ItemStack(Items.BEEF, 8));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(four.getItem(0).getCount() == 8, "entrée " + four.getItem(0)))
                .thenExecute(() -> {
                    try {
                        depuisLaBarre(helper, menu, joueur, new ItemStack(Items.BEEF, 4));
                        helper.assertTrue(four.getItem(0).getCount() == 12, "entrée " + four.getItem(0));
                        helper.assertTrue(CookingCredit.credit(four, simule.uuid()) == 4,
                                "crédit : " + CookingCredit.credit(four, simule.uuid()));
                        cuire(four, Items.COOKED_BEEF);
                        menu.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
                        helper.assertTrue(four.getItem(2).isEmpty(), "steaks restés : " + four.getItem(2));
                        helper.assertTrue(progres(joueur, 0) == 4, "mélange : " + progres(joueur, 0));
                    } finally {
                        sortir(helper, simule);
                    }
                })
                .thenSucceed();
    }

    /** Le crédit est écrit avec le chunk (aller-retour par {@code ChunkSerializer}) et sert après la relecture. */
    @GameTest(template = "test/plancher", batch = LOT_CUISINE)
    public static void leCreditDeCuisineSurvitAuChunk(GameTestHelper helper) {
        ButinGameTests.degager(helper);
        JoueurDeTest simule = entrer(helper, "QueteCredit");
        try {
            ServerPlayer joueur = simule.joueur();
            carnet(joueur, quete(QuestCategory.COOK, 16));
            BlockPos pos = new BlockPos(1, 1, 1);
            FurnaceBlockEntity four = four(helper, pos);
            FurnaceMenu menu = new FurnaceMenu(1, joueur.getInventory(), four, new SimpleContainerData(4));
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);
            LevelChunk chunk = level.getChunkAt(abs);
            chunk.setUnsaved(false);
            deposer(helper, menu, joueur, 0, new ItemStack(Items.BEEF, 4));
            helper.assertTrue(chunk.isUnsaved(), "le chunk n'est pas à sauver après le dépôt");
            CompoundTag ecrit = ChunkSerializer.write(level, chunk);
            CompoundTag tagDuFour = null;
            for (Tag t : ecrit.getList("block_entities", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                if (BlockEntity.getPosFromTag(c).equals(abs)) {
                    tagDuFour = c;
                }
            }
            helper.assertTrue(tagDuFour != null, "four absent du chunk écrit");
            BlockEntity relu = BlockEntity.loadStatic(abs, four.getBlockState(), tagDuFour);
            helper.assertTrue(relu instanceof FurnaceBlockEntity, "four relu : " + relu);
            helper.assertTrue(CookingCredit.credit(relu, simule.uuid()) == 4,
                    "crédit relu : " + CookingCredit.credit(relu, simule.uuid()));
            // Le four relu remplace celui du monde (comme au rechargement du chunk) : sa sortie compte encore 4.
            level.setBlockEntity(relu);
            FurnaceBlockEntity neuf = (FurnaceBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(neuf == relu, "four relu non posé");
            FurnaceMenu menuNeuf = new FurnaceMenu(2, joueur.getInventory(), neuf, new SimpleContainerData(4));
            cuire(neuf, Items.COOKED_BEEF);
            menuNeuf.clicked(2, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 4, "après la relecture : " + progres(joueur, 0));
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
            // Sans courant, le séchoir ne sèche rien : la sortie est posée à la main, après le dépôt du joueur.
            deposer(helper, menuSechoir, joueur, DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.COD, 3));
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DRIED_KELP, 2));
            menuSechoir.clicked(DryerBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 2, "séchoir au clic majuscule : " + progres(joueur, 0));
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DRIED_KELP, 1));
            menuSechoir.clicked(DryerBlockEntity.SLOT_OUTPUT, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(menuSechoir.getCarried().is(Items.DRIED_KELP), "séchage non pris");
            menuSechoir.setCarried(ItemStack.EMPTY);
            helper.assertTrue(progres(joueur, 0) == 3, "séchoir au clic : " + progres(joueur, 0));
            // Rien de plus mis par le joueur : une sortie de plus ne compte pas.
            sechoir.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(Items.DRIED_KELP, 1));
            menuSechoir.clicked(DryerBlockEntity.SLOT_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 3, "séchoir sans dépôt : " + progres(joueur, 0));

            BlockPos posMarmite = new BlockPos(3, 1, 1);
            helper.setBlock(posMarmite, FoodRegistry.CANTEEN_POT.get());
            CanteenPotBlockEntity marmite = (CanteenPotBlockEntity) helper.getBlockEntity(posMarmite);
            CanteenPotMenu menuMarmite = new CanteenPotMenu(2, joueur.getInventory(), marmite,
                    helper.absolutePos(posMarmite));
            // Deux ingrédients et un récipient : un crédit de 3.
            deposer(helper, menuMarmite, joueur, 0, new ItemStack(Items.BROWN_MUSHROOM, 2));
            deposer(helper, menuMarmite, joueur, CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.BOWL, 1));
            helper.assertTrue(CookingCredit.credit(marmite, simule.uuid()) == 3,
                    "crédit de la marmite : " + CookingCredit.credit(marmite, simule.uuid()));
            marmite.inventory().setStackInSlot(0, ItemStack.EMPTY);
            marmite.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, ItemStack.EMPTY);
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

    /**
     * Le vrai Cooking Pot de Farmer's Delight : un bœuf, une carotte et un bol mis par le joueur (crédit 3) ; deux plats
     * au clic majuscule, un au clic, comptés par le tag des plats ; un quatrième, sans dépôt, ne compte pas.
     */
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
            deposer(helper, menu, joueur, 0, new ItemStack(Items.BEEF, 1));
            depuisLaBarre(helper, menu, joueur, new ItemStack(Items.CARROT, 1));
            deposer(helper, menu, joueur, CookingPotMenu.INDEX_CONTAINER, new ItemStack(Items.BOWL, 1));
            helper.assertTrue(CookingCredit.credit(pot, simule.uuid()) == 3,
                    "crédit du Cooking Pot : " + CookingCredit.credit(pot, simule.uuid()));
            for (int i = 0; i <= CookingPotMenu.INDEX_CONTAINER; i++) {
                if (i != CookingPotMenu.INDEX_MEAL) {
                    menu.getSlot(i).set(ItemStack.EMPTY);
                }
            }
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
            pot.getInventory().setStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT, new ItemStack(ModItems.BEEF_STEW.get(), 1));
            menu.clicked(CookingPotMenu.INDEX_OUTPUT, 0, ClickType.QUICK_MOVE, joueur);
            helper.assertTrue(progres(joueur, 0) == 3, "Cooking Pot sans dépôt : " + progres(joueur, 0));
        } finally {
            sortir(helper, simule);
        }
        helper.succeed();
    }
}
