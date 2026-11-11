package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.explore.CacheIndex;
import dev.oneforallsurvival.explore.DetectorItem;
import dev.oneforallsurvival.explore.DetectorItem.Tick;
import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le détecteur de métaux : bip cadencé tenu en main près d'une cache, silence sans charge, sans cache ou dans le sac,
 * purge d'une entrée d'index sans bloc, recharge sur une source. Chaque test est synchrone (aucun délai) et retire sa
 * cache avant de rendre la main : les caches d'un test ne peuvent pas gêner un autre. Le GameTest du poste à souder
 * (ReparationGameTests) tourne dans la même suite, sans changement, pour prouver que sa batterie n'a pas bougé.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DetecteurGameTests {

    private static final String LOT = "detecteur";
    private static final BlockPos CACHE = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);
    /** À 2 blocs (2,06 en distance réelle) de la cache : un bip toutes les 5 ticks pour une portée de 24. */
    private static final int INTERVALLE = 5;

    private DetecteurGameTests() {}

    private static JoueurDeTest joueur(GameTestHelper helper) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "Prospecteur",
                helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        // Le nom est celui d'un joueur d'essai déjà passé : le serveur le replace à sa dernière position sauvegardée.
        j.joueur().moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        j.joueur().getInventory().clearContent();
        j.sonsRecus();
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    private static ItemStack detecteur(int fe) {
        ItemStack pile = new ItemStack(ExploreRegistry.METAL_DETECTOR.get());
        DetectorItem.store(pile, fe);
        return pile;
    }

    private static Tick tick(GameTestHelper helper, ServerPlayer p, ItemStack pile) {
        return DetectorItem.tick(helper.getLevel(), p, pile);
    }

    private static int bips(GameTestHelper helper, ServerPlayer p, ItemStack pile, int ticks) {
        int n = 0;
        for (int i = 0; i < ticks; i++) {
            if (tick(helper, p, pile) == Tick.BEEPED) {
                n++;
            }
        }
        return n;
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void tenuPresDUneCacheIlBipe(GameTestHelper helper) {
        helper.setBlock(CACHE, ExploreRegistry.CACHE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            int bips = bips(helper, p, pile, 40);
            helper.assertTrue(bips == 40 / INTERVALLE, "8 bips attendus en 40 ticks à 2 blocs, vus " + bips);
            List<Float> sons = j.sonsRecus();
            helper.assertTrue(sons.size() == bips, "le porteur devait entendre chaque bip, entendus " + sons.size());
            helper.assertTrue(sons.get(0) > 1.8F, "à 2 blocs le bip devait être aigu, hauteur " + sons.get(0));
            int depense = 1000 - DetectorItem.stored(pile);
            helper.assertTrue(depense == 40 * ExploreConfig.detectorEnergy(),
                    "2 FE par tick attendus (80), dépensé " + depense);
            helper.setBlock(CACHE, Blocks.AIR);
            helper.succeed();
        } finally {
            quitter(j);
            helper.setBlock(CACHE, Blocks.AIR);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void sansCacheIlSeTait(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            helper.assertTrue(!CacheIndex.get(helper.getLevel()).contains(helper.absolutePos(CACHE)),
                    "la case ne devait pas avoir de cache");
            helper.assertTrue(bips(helper, p, pile, 60) == 0, "aucun bip sans cache à portée");
            helper.assertTrue(j.sonsRecus().isEmpty(), "aucun son sans cache à portée");
            if (CacheIndex.get(helper.getLevel()).size() == 0) {
                helper.assertTrue(DetectorItem.stored(pile) == 1000, "sans cache dans la dimension rien ne se dépense");
            }
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void deChargeIlSeTaitEtNeDepenseRien(GameTestHelper helper) {
        helper.setBlock(CACHE, ExploreRegistry.CACHE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            for (int fe : new int[] {0, ExploreConfig.detectorEnergy() - 1}) {
                ItemStack pile = detecteur(fe);
                p.setItemInHand(InteractionHand.MAIN_HAND, pile);
                for (int i = 0; i < 40; i++) {
                    helper.assertTrue(tick(helper, p, pile) == Tick.IDLE, "un détecteur sans de quoi payer un tick ne fait rien");
                }
                helper.assertTrue(DetectorItem.stored(pile) == fe, "rien ne devait être dépensé, reste " + DetectorItem.stored(pile));
            }
            helper.assertTrue(j.sonsRecus().isEmpty(), "aucun son sans charge");
            helper.setBlock(CACHE, Blocks.AIR);
            helper.succeed();
        } finally {
            quitter(j);
            helper.setBlock(CACHE, Blocks.AIR);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneCacheCasseeNeSonnePlus(GameTestHelper helper) {
        helper.setBlock(CACHE, ExploreRegistry.CACHE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            helper.assertTrue(bips(helper, p, pile, 20) > 0, "la cache en place devait faire bipper");
            j.sonsRecus();
            p.gameMode.destroyBlock(helper.absolutePos(CACHE));
            helper.assertTrue(helper.getBlockState(CACHE).is(Blocks.AIR), "la cache devait être cassée");
            helper.assertTrue(!CacheIndex.get(helper.getLevel()).contains(helper.absolutePos(CACHE)),
                    "la cache cassée devait sortir de l'index");
            helper.assertTrue(bips(helper, p, pile, 40) == 0, "une cache cassée ne devait plus sonner");
            helper.assertTrue(j.sonsRecus().isEmpty(), "aucun son après la casse");
            helper.succeed();
        } finally {
            quitter(j);
            helper.setBlock(CACHE, Blocks.AIR);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneEntreePerimeeEstPurgee(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(CACHE);
        CacheIndex index = CacheIndex.get(helper.getLevel());
        // Un test d'un autre lot a pu laisser un bloc dans la case (le gabarit ne vide que son plancher).
        helper.setBlock(CACHE, Blocks.AIR);
        index.add(abs);
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            helper.assertTrue(tick(helper, p, pile) != Tick.BEEPED, "une entrée sans bloc ne doit pas bipper");
            helper.assertTrue(!index.contains(abs), "l'entrée périmée devait être retirée de l'index");
            helper.assertTrue(bips(helper, p, pile, 40) == 0, "aucun bip pour une entrée périmée");
            helper.assertTrue(j.sonsRecus().isEmpty(), "aucun son pour une entrée périmée");
            helper.succeed();
        } finally {
            index.remove(abs);
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void dansLeSacIlNeDepenseRien(GameTestHelper helper) {
        helper.setBlock(CACHE, ExploreRegistry.CACHE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.getInventory().setItem(5, pile);
            for (int i = 0; i < 40; i++) {
                pile.getItem().inventoryTick(pile, helper.getLevel(), p, 5, false);
            }
            helper.assertTrue(DetectorItem.stored(pile) == 1000, "dans le sac rien ne se dépense, reste " + DetectorItem.stored(pile));
            helper.assertTrue(j.sonsRecus().isEmpty(), "dans le sac il se tait");
            // Passé en main, le même objet agit par le chemin de jeu (inventoryTick).
            p.getInventory().setItem(5, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            pile.getItem().inventoryTick(pile, helper.getLevel(), p, 0, true);
            helper.assertTrue(DetectorItem.stored(pile) == 1000 - ExploreConfig.detectorEnergy(),
                    "en main un tick coûte 2 FE, reste " + DetectorItem.stored(pile));
            helper.setBlock(CACHE, Blocks.AIR);
            helper.succeed();
        } finally {
            quitter(j);
            helper.setBlock(CACHE, Blocks.AIR);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void ilSeRechargeSurUneSource(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(3, 1, 1);
        helper.setBlock(sourcePos, DevBlocks.testSource());
        TestSourceBlockEntity source = (TestSourceBlockEntity) helper.getBlockEntity(sourcePos);
        helper.assertTrue(source != null && source.stocke() == 3000, "la source devait avoir 3000 FE");
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(100);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            BlockPos abs = helper.absolutePos(sourcePos);
            UseOnContext ctx = new UseOnContext(helper.getLevel(), p, InteractionHand.MAIN_HAND, pile,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            p.setShiftKeyDown(false);
            InteractionResult r = pile.getItem().onItemUseFirst(pile, ctx);
            helper.assertTrue(r.consumesAction(), "le clic droit sur une source devait agir, vu " + r);
            helper.assertTrue(DetectorItem.stored(pile) == 3100, "3100 FE attendus, vus " + DetectorItem.stored(pile));
            helper.assertTrue(source.stocke() == 0, "la source devait être vidée, reste " + source.stocke());
            helper.setBlock(sourcePos, Blocks.STONE);
            helper.assertTrue(pile.getItem().onItemUseFirst(pile, ctx) == InteractionResult.PASS,
                    "un bloc sans énergie devait rendre PASS");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void lePasDeMainEnMainGardeLeRythme(GameTestHelper helper) {
        helper.setBlock(CACHE, ExploreRegistry.CACHE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper);
            ServerPlayer p = j.joueur();
            ItemStack pile = detecteur(1000);
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            int bips = bips(helper, p, pile, 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.OFF_HAND, pile);
            for (int i = 0; i < 7; i++) {
                pile.getItem().inventoryTick(pile, helper.getLevel(), p, 0, false);
            }
            helper.assertTrue(DetectorItem.stored(pile) == 1000 - 10 * ExploreConfig.detectorEnergy(),
                    "10 ticks sur deux mains : 20 FE attendus, reste " + DetectorItem.stored(pile));
            helper.assertTrue(bips == 0, "aucun bip avant le cinquième tick");
            helper.assertTrue(j.sonsRecus().size() == 2, "10 ticks sur deux mains : 2 bips attendus");
            helper.setBlock(CACHE, Blocks.AIR);
            helper.succeed();
        } finally {
            quitter(j);
            helper.setBlock(CACHE, Blocks.AIR);
        }
    }
}
