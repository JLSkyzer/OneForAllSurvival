package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchStockMenu;
import dev.oneforallsurvival.bench.EngineerBenchMenu;
import dev.oneforallsurvival.bench.OpenBenchPacket;
import dev.oneforallsurvival.bench.OpenStockPacket;
import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le menu de stock : prendre, défiler, et surtout ne jamais dupliquer ni perdre un objet, quel que soit le
 * type de clic. Chaque essai a son joueur (donc son stock), en survie, et compte avant et après tous les
 * endroits où un objet peut se trouver : stock, inventaire, curseur et objets jetés au sol.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class StockMenuGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Vec3 JOUEUR = new Vec3(2.5, 1.0, 2.5);
    private static final int HORS_FENETRE = -999;

    private StockMenuGameTests() {}

    /** Un ingénieur en survie près de l'établi, son stock rempli, et le menu de stock ouvert. */
    private static final class Essai {
        final GameTestHelper helper;
        final JoueurDeTest j;
        final ServerPlayer joueur;
        final PlayerWorkshop atelier;
        BenchStockMenu menu;

        Essai(GameTestHelper helper, String nom, boolean ingenieur) {
            this.helper = helper;
            helper.setBlock(POS, dev.oneforallsurvival.bench.BenchRegistry.ENGINEER_BENCH.get());
            this.j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            this.joueur = j.joueur();
            joueur.setGameMode(GameType.SURVIVAL);
            // Un joueur qui entre peut recevoir des objets de départ : l'essai compte sur un inventaire vide.
            joueur.getInventory().clearContent();
            RoleSavedData.get(helper.getLevel().getServer()).book().assign(j.uuid(),
                    ingenieur ? RoleBook.ENGINEER_ROLE_ID : RoleBook.DEFAULT_ROLE_ID);
            this.atelier = WorkshopSavedData.get(helper.getLevel().getServer()).of(j.uuid());
        }

        BlockPos banc() {
            return helper.absolutePos(POS);
        }

        Essai piles(int n) {
            for (int i = 0; i < n; i++) {
                atelier.addToStock(pile(i));
            }
            return this;
        }

        Essai ouvrir() {
            BenchStockMenu.open(joueur, banc());
            menu = (BenchStockMenu) joueur.containerMenu;
            return this;
        }

        void cliquer(int slot, int bouton, ClickType type) {
            menu.clicked(slot, bouton, type, joueur);
        }

        /** Tout ce qui existe du matériau d'essai : stock, inventaire, curseur, sol. */
        long total() {
            long n = 0;
            for (ItemStack s : atelier.stock()) {
                n += s.getCount();
            }
            Inventory inv = joueur.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                n += inv.getItem(i).getCount();
            }
            n += joueur.containerMenu.getCarried().getCount();
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(banc()).inflate(16))) {
                n += e.getItem().getCount();
            }
            return n;
        }

        int dansInventaire() {
            int n = 0;
            Inventory inv = joueur.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                n += inv.getItem(i).getCount();
            }
            return n;
        }

        void fermer() {
            // Du dernier au premier : retirer en tête décalerait tout le reste, et le stock peut être énorme.
            for (int i = atelier.stock().size() - 1; i >= 0; i--) {
                atelier.takeFromStock(i, 64);
            }
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(banc()).inflate(16))) {
                e.discard();
            }
            if (j.connecte()) {
                j.quitter();
            }
        }
    }

    /** Une pile reconnaissable : du pavé, 1 à 64 objets, marquée de son rang. */
    private static ItemStack pile(int rang) {
        ItemStack pile = new ItemStack(Items.COBBLESTONE, 1 + rang % 64);
        pile.getOrCreateTag().putInt("n", rang);
        return pile;
    }

    private static int rang(ItemStack pile) {
        return pile.isEmpty() || !pile.hasTag() ? -1 : pile.getTag().getInt("n");
    }

    private static String vu(Essai e, String quoi) {
        return quoi + " (stock " + e.atelier.stock().size() + " piles, " + e.total() + " objets)";
    }

    // --- Prendre ---------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_prendre")
    public static void prendreRetireDuStock(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockPrend", true).piles(20).ouvrir();
        try {
            long avant = e.total();
            ItemStack premiere = e.atelier.stock().get(0).copy();
            e.menu.quickMoveStack(e.joueur, 0);
            helper.assertTrue(e.dansInventaire() == premiere.getCount(),
                    vu(e, "l'inventaire devait recevoir " + premiere.getCount() + " objets, vu " + e.dansInventaire()));
            helper.assertTrue(e.atelier.stock().size() == 19, vu(e, "le stock devait perdre une pile"));
            helper.assertTrue(rang(e.atelier.stock().get(0)) == 1, "la suite devait remonter sans trou");
            helper.assertTrue(e.total() == avant, vu(e, "total changé : avant " + avant));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_plein")
    public static void inventairePleinRienNeDisparait(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockPlein", true);
        try {
            e.atelier.addToStock(new ItemStack(Items.COBBLESTONE, 64));
            e.atelier.addToStock(new ItemStack(Items.DIAMOND, 2));
            e.ouvrir();
            // Inventaire presque plein : une seule place de 3 objets de pavé, le reste de pierre.
            Inventory inv = e.joueur.getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
            inv.items.set(5, new ItemStack(Items.COBBLESTONE, 61));
            long avant = e.total();
            e.menu.quickMoveStack(e.joueur, 0);
            helper.assertTrue(e.total() == avant, vu(e, "des objets ont disparu ou sont apparus, avant " + avant));
            helper.assertTrue(inv.items.get(5).getCount() == 64, "3 objets devaient tenir, vu " + inv.items.get(5).getCount());
            helper.assertTrue(e.atelier.stock().size() == 2 && e.atelier.stock().get(0).getCount() == 61,
                    "le surplus (61) devait rester en tête du stock, vu " + e.atelier.stock().get(0).getCount());
            // Tout à fait plein : rien ne bouge.
            avant = e.total();
            e.menu.quickMoveStack(e.joueur, 0);
            e.menu.quickMoveStack(e.joueur, 1);
            helper.assertTrue(e.total() == avant && e.atelier.stock().size() == 2
                    && e.atelier.stock().get(0).getCount() == 61 && e.atelier.stock().get(1).getCount() == 2,
                    vu(e, "rien ne devait bouger"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_depot")
    public static void deposerEstRefuse(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockDepot", true).piles(12).ouvrir();
        try {
            for (int i = 0; i < BenchStockMenu.WINDOW; i++) {
                Slot s = e.menu.slots.get(i);
                helper.assertTrue(!s.mayPlace(new ItemStack(Items.COBBLESTONE)), "la case " + i + " accepte un dépôt");
                helper.assertTrue(!s.mayPlace(pile(0)), "la case " + i + " accepte un dépôt de même pile");
                helper.assertTrue(s.mayPickup(e.joueur), "la case " + i + " devait se laisser prendre");
            }
            e.joueur.getInventory().items.set(0, new ItemStack(Items.COBBLESTONE, 10));
            e.joueur.getInventory().items.set(1, new ItemStack(Items.DIAMOND, 3));
            long avant = e.total();
            // Maj-clic depuis l'inventaire : ne dépose rien.
            e.menu.quickMoveStack(e.joueur, BenchStockMenu.WINDOW);
            helper.assertTrue(e.atelier.stock().size() == 12 && e.total() == avant, vu(e, "le maj-clic a déposé"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    // --- Défilement -----------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_defile")
    public static void leDecalageMontreLaSuite(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockDecale", true).piles(200).ouvrir();
        try {
            helper.assertTrue(rang(e.menu.slots.get(0).getItem()) == 0, "au départ, la première case montre la pile 1");
            helper.assertTrue(e.menu.clickMenuButton(e.joueur, BenchStockMenu.buttonForOffset(10)), "décalage refusé");
            helper.assertTrue(e.menu.offsetLines() == 10, "décalage 10 attendu, vu " + e.menu.offsetLines());
            helper.assertTrue(rang(e.menu.slots.get(0).getItem()) == 90,
                    "10 lignes plus bas, la première case montre la 91e pile, vu rang " + rang(e.menu.slots.get(0).getItem()));
            helper.assertTrue(rang(e.menu.slots.get(53).getItem()) == 143, "la dernière case montre la 144e pile");
            // Les boutons 0 et 1 font un pas.
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.BUTTON_DOWN);
            helper.assertTrue(e.menu.offsetLines() == 11, "un pas vers le bas attendu");
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.BUTTON_UP);
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.BUTTON_UP);
            helper.assertTrue(e.menu.offsetLines() == 9, "deux pas vers le haut attendus");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    /** Cent piles font 12 lignes : on ne descend pas au-delà de la dernière fenêtre pleine (6 lignes). */
    @GameTest(template = "test/plancher", batch = "stock_cent")
    public static void centPilesPlafonnentLeDecalage(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockCent", true).piles(100).ouvrir();
        try {
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.buttonForOffset(10));
            helper.assertTrue(e.menu.offsetLines() == 6, "12 lignes - 6 visibles : décalage 6 attendu, vu " + e.menu.offsetLines());
            helper.assertTrue(rang(e.menu.slots.get(0).getItem()) == 54, "la première case montre la 55e pile");
            helper.assertTrue(rang(e.menu.slots.get(45).getItem()) == 99 && e.menu.slots.get(46).getItem().isEmpty(),
                    "la 100e pile ferme la fenêtre, le reste est vide");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_bornes")
    public static void decalageHorsLimites(GameTestHelper helper) {
        Essai vide = new Essai(helper, "StockBornesVide", true).ouvrir();
        Essai plein = null;
        try {
            for (int id : new int[] {BenchStockMenu.BUTTON_UP, BenchStockMenu.BUTTON_DOWN, 2, 3, 1_000_000,
                    Integer.MAX_VALUE}) {
                vide.menu.clickMenuButton(vide.joueur, id);
                helper.assertTrue(vide.menu.offsetLines() == 0, "un stock vide ne défile pas, id " + id);
            }
            vide.fermer();
            plein = new Essai(helper, "StockBornesPlein", true).piles(60).ouvrir();
            Essai p = plein;
            // 60 piles : 7 lignes, donc au plus 1 de décalage.
            p.menu.clickMenuButton(p.joueur, BenchStockMenu.BUTTON_UP);
            helper.assertTrue(p.menu.offsetLines() == 0, "pas de décalage négatif");
            for (int i = 0; i < 20; i++) {
                p.menu.clickMenuButton(p.joueur, BenchStockMenu.BUTTON_DOWN);
            }
            helper.assertTrue(p.menu.offsetLines() == 1, "plafond de 1 attendu, vu " + p.menu.offsetLines());
            p.menu.clickMenuButton(p.joueur, Integer.MAX_VALUE);
            helper.assertTrue(p.menu.offsetLines() == 1, "un décalage énorme est borné");
            // Un identifiant négatif n'est pas une commande.
            helper.assertTrue(!p.menu.clickMenuButton(p.joueur, -1), "un identifiant négatif devait être refusé");
            helper.assertTrue(!p.menu.clickMenuButton(p.joueur, Integer.MIN_VALUE), "un identifiant négatif devait être refusé");
            helper.assertTrue(p.menu.offsetLines() == 1, "le décalage ne devait pas bouger");
            // Le stock se vide sous la fenêtre : le décalage se resserre sans exception.
            p.menu.clickMenuButton(p.joueur, BenchStockMenu.buttonForOffset(1));
            for (int i = 59; i >= 10; i--) {
                p.atelier.takeFromStock(i, 64);
            }
            p.menu.broadcastChanges();
            helper.assertTrue(p.menu.offsetLines() == 0, "10 piles tiennent dans une fenêtre, vu " + p.menu.offsetLines());
            helper.assertTrue(rang(p.menu.slots.get(0).getItem()) == 0, "la première case montre la première pile");
            helper.succeed();
        } finally {
            vide.fermer();
            if (plein != null) {
                plein.fermer();
            }
        }
    }

    // --- Validité -------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_valide")
    public static void unNonIngenieurEstEjecte(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockValide", true).piles(5).ouvrir();
        try {
            helper.assertTrue(e.menu.stillValid(e.joueur), "le menu devait être valide pour un ingénieur");
            RoleSavedData.get(helper.getLevel().getServer()).book().assign(e.j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            helper.assertTrue(!e.menu.stillValid(e.joueur), "sans le rôle, le menu devait se fermer");
            helper.assertTrue(!e.menu.clickMenuButton(e.joueur, BenchStockMenu.BUTTON_DOWN),
                    "sans le rôle, le défilement devait être refusé");
            RoleSavedData.get(helper.getLevel().getServer()).book().assign(e.j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            helper.assertTrue(e.menu.stillValid(e.joueur), "le rôle rendu, le menu redevient valide");
            e.joueur.moveTo(e.joueur.getX() + 30, e.joueur.getY(), e.joueur.getZ());
            helper.assertTrue(!e.menu.stillValid(e.joueur), "trop loin de l'établi, le menu devait se fermer");
            e.joueur.moveTo(e.joueur.getX() - 30, e.joueur.getY(), e.joueur.getZ());
            helper.setBlock(POS, net.minecraft.world.level.block.Blocks.AIR);
            helper.assertTrue(!e.menu.stillValid(e.joueur), "sans le bloc, le menu devait se fermer");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    // --- Un essai par type de clic : le total ne change jamais ----------------------------------

    @GameTest(template = "test/plancher", batch = "stock_clic_ramasser")
    public static void clicRamasserEtPoserDehors(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockPickup", true).piles(20).ouvrir();
        try {
            long avant = e.total();
            int pile0 = e.atelier.stock().get(0).getCount();
            e.cliquer(0, 0, ClickType.PICKUP);
            helper.assertTrue(e.menu.getCarried().getCount() == pile0 && rang(e.menu.getCarried()) == 0,
                    "le curseur devait tenir la première pile");
            helper.assertTrue(e.atelier.stock().size() == 19 && rang(e.atelier.stock().get(0)) == 1,
                    "la première pile devait quitter le stock, la suite remonter");
            helper.assertTrue(e.total() == avant, vu(e, "total changé après ramassage"));
            // Reposer sur une case de stock, pleine ou vide : refusé.
            e.cliquer(3, 0, ClickType.PICKUP);
            e.cliquer(3, 1, ClickType.PICKUP);
            e.cliquer(40, 0, ClickType.PICKUP);
            e.cliquer(40, 1, ClickType.PICKUP);
            helper.assertTrue(e.atelier.stock().size() == 19 && e.total() == avant, vu(e, "un dépôt est passé"));
            // Clic droit sur une autre pile avec le curseur plein : rien.
            helper.assertTrue(rang(e.menu.getCarried()) == 0, "le curseur ne devait pas changer");
            // Le jeter dehors : le total compte le sol.
            e.cliquer(HORS_FENETRE, 0, ClickType.PICKUP);
            helper.assertTrue(e.menu.getCarried().isEmpty(), "le curseur devait être vidé");
            helper.assertTrue(e.total() == avant, vu(e, "total changé après le jet"));
            // Clic droit à vide : la moitié, arrondie en haut.
            e.cliquer(1, 1, ClickType.PICKUP);
            int demi = e.menu.getCarried().getCount();
            helper.assertTrue(demi > 0 && e.total() == avant, vu(e, "le clic droit a changé le total"));
            // Le curseur, remis dans le stock par le clic sur l'inventaire (non refusé), puis fermeture.
            e.cliquer(60, 0, ClickType.PICKUP);
            helper.assertTrue(e.total() == avant, vu(e, "total changé après dépôt en inventaire"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_majuscule")
    public static void clicMajusculeNeBougeQuUnePile(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockShift", true).piles(20).ouvrir();
        try {
            long avant = e.total();
            ItemStack deuxieme = e.atelier.stock().get(1).copy();
            e.cliquer(0, 0, ClickType.QUICK_MOVE);
            helper.assertTrue(e.atelier.stock().size() == 19, vu(e, "une pile exactement devait partir"));
            helper.assertTrue(rang(e.atelier.stock().get(0)) == rang(deuxieme),
                    "la deuxième pile devait avoir remonté et rester en place");
            helper.assertTrue(e.total() == avant, vu(e, "total changé"));
            // Maj-clic droit, et sur une case vide : même résultat.
            e.cliquer(0, 1, ClickType.QUICK_MOVE);
            e.cliquer(50, 0, ClickType.QUICK_MOVE);
            helper.assertTrue(e.atelier.stock().size() == 18 && e.total() == avant, vu(e, "total changé"));
            // Depuis l'inventaire vers le stock : refusé.
            e.cliquer(54 + 27, 0, ClickType.QUICK_MOVE);
            e.cliquer(54, 0, ClickType.QUICK_MOVE);
            helper.assertTrue(e.atelier.stock().size() == 18 && e.total() == avant, vu(e, "un dépôt est passé"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_echange")
    public static void clicTouchedEchangeNeDeposeRien(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockSwap", true).piles(20).ouvrir();
        try {
            long avant = e.total();
            // Raccourci vers une case de barre vide : c'est une prise.
            e.cliquer(0, 0, ClickType.SWAP);
            helper.assertTrue(rang(e.joueur.getInventory().getItem(0)) == 0, "la barre devait recevoir la pile");
            helper.assertTrue(e.atelier.stock().size() == 19 && rang(e.atelier.stock().get(0)) == 1,
                    "la pile devait quitter le stock");
            helper.assertTrue(e.total() == avant, vu(e, "total changé après la prise par raccourci"));
            // Avec un objet en main de barre : l'échange déposerait, il est refusé.
            e.joueur.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 5));
            long avant2 = e.total();
            ItemStack visee = e.atelier.stock().get(0).copy();
            e.cliquer(0, 1, ClickType.SWAP);
            helper.assertTrue(e.joueur.getInventory().getItem(1).getCount() == 5
                    && e.joueur.getInventory().getItem(1).getTag() == null, "l'objet de la barre devait rester");
            helper.assertTrue(rang(e.atelier.stock().get(0)) == rang(visee) && e.atelier.stock().get(0).getCount() == visee.getCount(),
                    "la pile visée devait rester");
            // Objet de barre, case de stock vide : déposer est refusé aussi.
            e.cliquer(45, 1, ClickType.SWAP);
            helper.assertTrue(e.joueur.getInventory().getItem(1).getCount() == 5 && e.atelier.stock().size() == 19,
                    "le dépôt dans une case vide est passé");
            // Main secondaire.
            e.joueur.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 7)); // 7 objets de plus en jeu : ils comptent dans le total attendu
            avant2 += 7;
            e.cliquer(0, 40, ClickType.SWAP);
            e.cliquer(45, 40, ClickType.SWAP);
            helper.assertTrue(e.joueur.getInventory().offhand.get(0).getCount() == 7 && e.atelier.stock().size() == 19,
                    "l'échange avec la main secondaire a déposé");
            helper.assertTrue(e.total() == avant2, vu(e, "total changé après les échanges refusés"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_jeter")
    public static void clicJeterRetireUneFoisDuStock(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockThrow", true).piles(20).ouvrir();
        try {
            long avant = e.total();
            int taille = e.atelier.stock().get(0).getCount();
            e.cliquer(0, 0, ClickType.THROW); // un seul objet
            helper.assertTrue(e.total() == avant, vu(e, "total changé après le jet d'un objet"));
            helper.assertTrue(taille == 1 ? e.atelier.stock().size() == 19
                    : e.atelier.stock().get(0).getCount() == taille - 1, "un seul objet devait partir");
            e.cliquer(2, 1, ClickType.THROW); // toute la pile
            helper.assertTrue(e.total() == avant, vu(e, "total changé après le jet d'une pile"));
            e.cliquer(45, 0, ClickType.THROW); // case vide
            e.cliquer(60, 1, ClickType.THROW); // inventaire vide
            helper.assertTrue(e.total() == avant, vu(e, "total changé après les jets à vide"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_cloner")
    public static void clicClonerNeCloneRienEnSurvie(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockClone", true).piles(20).ouvrir();
        try {
            helper.assertTrue(!e.joueur.getAbilities().instabuild, "l'essai exige la survie");
            long avant = e.total();
            e.cliquer(0, 2, ClickType.CLONE);
            helper.assertTrue(e.menu.getCarried().isEmpty() && e.total() == avant, vu(e, "le clic milieu a cloné"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_glisser")
    public static void clicGlisserNeDeposeRien(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockDrag", true).piles(20).ouvrir();
        try {
            // Un curseur de pavé qu'on glisse sur le stock, puis à cheval sur le stock et l'inventaire.
            for (int mode = 0; mode <= 1; mode++) {
                e.menu.setCarried(new ItemStack(Items.COBBLESTONE, 12));
                long avant = e.total();
                int[] tailles = new int[20];
                for (int i = 0; i < 20; i++) {
                    tailles[i] = e.atelier.stock().get(i).getCount();
                }
                e.cliquer(HORS_FENETRE, AbstractContainerMenu.getQuickcraftMask(0, mode), ClickType.QUICK_CRAFT);
                for (int slot : new int[] {0, 1, 2, 30, 55, 56, 57}) {
                    e.cliquer(slot, AbstractContainerMenu.getQuickcraftMask(1, mode), ClickType.QUICK_CRAFT);
                }
                e.cliquer(HORS_FENETRE, AbstractContainerMenu.getQuickcraftMask(2, mode), ClickType.QUICK_CRAFT);
                helper.assertTrue(e.atelier.stock().size() == 20, vu(e, "le glissé a changé le nombre de piles"));
                for (int i = 0; i < 20; i++) {
                    helper.assertTrue(e.atelier.stock().get(i).getCount() == tailles[i], "la pile " + i + " a grossi");
                }
                helper.assertTrue(e.total() == avant + 0, vu(e, "total changé par le glissé (mode " + mode + ")"));
                // Les cases d'inventaire ont bien reçu leur part : le glissé fonctionne, il évite le stock seulement.
                helper.assertTrue(e.dansInventaire() > 0, "le glissé n'a rien posé dans l'inventaire (mode " + mode + ")");
                e.menu.setCarried(ItemStack.EMPTY);
                e.joueur.getInventory().clearContent();
                e.menu.setCarried(ItemStack.EMPTY);
            }
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    @GameTest(template = "test/plancher", batch = "stock_clic_double")
    public static void clicDoubleRamasseSansDupliquer(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockDouble", true).ouvrir();
        try {
            // Deux piles sans marque, qui peuvent rejoindre un curseur sans marque.
            e.atelier.addToStock(new ItemStack(Items.COBBLESTONE, 64));
            e.atelier.addToStock(new ItemStack(Items.COBBLESTONE, 40));
            e.atelier.addToStock(new ItemStack(Items.DIAMOND, 3));
            e.menu.setCarried(new ItemStack(Items.COBBLESTONE, 30));
            long avant = e.total();
            int diamantsAvant = e.atelier.stock().stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            // Double-clic sur une case vide : ramasse tout ce qui est identique, stock compris.
            e.cliquer(45, 0, ClickType.PICKUP_ALL);
            helper.assertTrue(e.menu.getCarried().getCount() == 64, "le curseur devait se remplir à 64, vu "
                    + e.menu.getCarried().getCount());
            helper.assertTrue(e.total() == avant, vu(e, "total changé par le double-clic"));
            int diamants = e.atelier.stock().stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(diamants == diamantsAvant, "les diamants ne devaient pas bouger");
            // Un second double-clic, curseur plein : rien de plus.
            e.cliquer(46, 0, ClickType.PICKUP_ALL);
            helper.assertTrue(e.total() == avant && e.menu.getCarried().getCount() == 64, vu(e, "total changé"));
            // Curseur d'un autre objet : le stock reste.
            e.menu.setCarried(new ItemStack(Items.IRON_INGOT, 4));
            long avant2 = e.total();
            e.cliquer(47, 0, ClickType.PICKUP_ALL);
            helper.assertTrue(e.total() == avant2 && e.menu.getCarried().getCount() == 4, vu(e, "total changé"));
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    // --- Données ---------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_donnees")
    public static void prendreMarqueLesDonneesEtRendLaPile(GameTestHelper helper) {
        WorkshopSavedData donnees = new WorkshopSavedData();
        PlayerWorkshop atelier = donnees.of(UUID.randomUUID());
        atelier.addToStock(pile(0));
        atelier.addToStock(pile(1));
        atelier.addToStock(new ItemStack(Items.DIAMOND, 10));
        donnees.setDirty(false);
        // Une part de pile : le reste demeure, la part est détachée.
        ItemStack part = atelier.takeFromStock(2, 4);
        helper.assertTrue(part.is(Items.DIAMOND) && part.getCount() == 4, "4 diamants attendus");
        helper.assertTrue(atelier.stock().get(2).getCount() == 6, "6 diamants devaient rester");
        helper.assertTrue(donnees.isDirty(), "prendre devait marquer les données");
        // La pile entière : retirée, la suite remonte.
        donnees.setDirty(false);
        ItemStack tout = atelier.takeFromStock(0, 64);
        helper.assertTrue(rang(tout) == 0 && tout.getCount() == 1, "la pile 0 entière attendue");
        helper.assertTrue(atelier.stock().size() == 2 && rang(atelier.stock().get(0)) == 1, "la suite devait remonter");
        helper.assertTrue(donnees.isDirty(), "retirer devait marquer les données");
        // Hors limites : rien, sans exception, sans marquer.
        donnees.setDirty(false);
        helper.assertTrue(atelier.takeFromStock(-1, 1).isEmpty() && atelier.takeFromStock(2, 1).isEmpty()
                && atelier.takeFromStock(0, 0).isEmpty() && atelier.takeFromStock(0, -3).isEmpty(), "hors limites");
        helper.assertTrue(!donnees.isDirty() && atelier.stock().size() == 2, "rien ne devait changer");
        // La pile rendue est détachée du stock : la modifier ne touche pas le stock.
        ItemStack detachee = atelier.takeFromStock(0, 64);
        detachee.shrink(1);
        helper.assertTrue(atelier.stock().size() == 1, "la pile détachée ne devait plus être au stock");
        helper.succeed();
    }

    // --- Synchronisation vers le client --------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_sync")
    public static void laSynchronisationSuitLesPrisesEtLeDefilement(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockSync", true).piles(100).ouvrir();
        try {
            BenchStockMenu client = BenchStockMenu.client(e.menu.containerId, new Inventory(e.joueur), e.banc());
            e.j.synchroniser(client);
            helper.assertTrue(client.totalStacks() == 100, "le client devait voir 100 piles, vu " + client.totalStacks());
            controle(helper, e, client, "à l'ouverture");
            // Une prise : la suite remonte aussi chez le client.
            e.cliquer(0, 0, ClickType.QUICK_MOVE);
            e.menu.broadcastChanges();
            e.j.synchroniser(client);
            helper.assertTrue(client.totalStacks() == 99, "99 piles attendues après la prise, vu " + client.totalStacks());
            controle(helper, e, client, "après une prise");
            // Un défilement.
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.buttonForOffset(3));
            e.menu.broadcastChanges();
            e.j.synchroniser(client);
            controle(helper, e, client, "après un défilement");
            // Un craft qui se termine pendant que la fenêtre est ouverte.
            e.atelier.addToStock(pile(1000));
            e.menu.broadcastChanges();
            e.j.synchroniser(client);
            helper.assertTrue(client.totalStacks() == 100, "100 piles attendues après l'arrivée d'une pile");
            controle(helper, e, client, "après l'arrivée d'une pile");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    /**
     * Le chemin du vrai client : un paquet de clic traité par le serveur (mises à jour suspendues pendant le
     * clic, puis diffusées), et le client qui reçoit cases et compteur.
     */
    @GameTest(template = "test/plancher", batch = "stock_sync_paquet")
    public static void unClicEnvoyeParPaquetMetLeClientAJour(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockPaquet", true).piles(100).ouvrir();
        try {
            BenchStockMenu client = BenchStockMenu.client(e.menu.containerId, new Inventory(e.joueur), e.banc());
            e.j.synchroniser(client);
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.buttonForOffset(2));
            e.menu.broadcastChanges();
            e.j.synchroniser(client);
            long avant = e.total();
            e.joueur.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(
                    e.menu.containerId, e.menu.getStateId(), 0, 0, ClickType.QUICK_MOVE, ItemStack.EMPTY,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap()));
            helper.assertTrue(e.atelier.stock().size() == 99, "le serveur devait avoir retiré une pile, vu "
                    + e.atelier.stock().size());
            helper.assertTrue(e.total() == avant, vu(e, "total changé"));
            e.j.synchroniser(client);
            helper.assertTrue(client.totalStacks() == 99, "le client devait voir 99 piles, vu " + client.totalStacks());
            controle(helper, e, client, "après le clic par paquet");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    private static void controle(GameTestHelper helper, Essai e, BenchStockMenu client, String quand) {
        for (int i = 0; i < BenchStockMenu.WINDOW; i++) {
            ItemStack a = e.menu.slots.get(i).getItem();
            ItemStack b = client.slots.get(i).getItem();
            helper.assertTrue(ItemStack.matches(a, b), "case " + i + " différente chez le client " + quand + " : serveur "
                    + a + " " + a.getTag() + ", client " + b + " " + b.getTag());
        }
    }

    /** Au-delà de 32767 piles : une donnée de menu se réduit à un entier court, deux moitiés la portent. */
    @GameTest(template = "test/plancher", batch = "stock_enorme", timeoutTicks = 400)
    public static void unStockAuDela32767PilesArriveAuClient(GameTestHelper helper) {
        Essai e = new Essai(helper, "StockEnorme", true);
        try {
            int piles = 33_000;
            for (int i = 0; i < piles; i++) {
                e.atelier.addToStock(new ItemStack(Items.COBBLESTONE, 64));
            }
            e.ouvrir();
            BenchStockMenu client = BenchStockMenu.client(e.menu.containerId, new Inventory(e.joueur), e.banc());
            e.j.synchroniser(client);
            helper.assertTrue(client.totalStacks() == piles, "le client devait voir " + piles + " piles, vu " + client.totalStacks());
            helper.assertTrue(client.totalLines() == 3667, "3667 lignes attendues, vu " + client.totalLines());
            // Défilement jusqu'à la fin : la dernière fenêtre est pleine à moitié ou presque.
            int dernier = client.totalLines() - 6;
            helper.assertTrue(e.menu.clickMenuButton(e.joueur, BenchStockMenu.buttonForOffset(dernier)), "décalage refusé");
            helper.assertTrue(e.menu.offsetLines() == dernier, "décalage final attendu " + dernier + ", vu " + e.menu.offsetLines());
            helper.assertTrue(!e.menu.slots.get(0).getItem().isEmpty() && e.menu.slots.get(53).getItem().isEmpty()
                    == (dernier * 9 + 53 >= piles), "la dernière fenêtre ne montre pas la fin du stock");
            // Un cran de plus ne passe pas.
            e.menu.clickMenuButton(e.joueur, BenchStockMenu.BUTTON_DOWN);
            helper.assertTrue(e.menu.offsetLines() == dernier, "borné au dernier décalage");
            helper.succeed();
        } finally {
            e.fermer();
        }
    }

    // --- Réseau ----------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "stock_reseau", timeoutTicks = 100)
    public static void lesPaquetsOuvrirEtRetourSontRevalides(GameTestHelper helper) {
        Essai ouvert = new Essai(helper, "StockReseauOuvert", true);
        Essai ferme = new Essai(helper, "StockReseauFerme", true);
        Essai citoyen = new Essai(helper, "StockReseauCitoyen", false);
        Essai mauvais = new Essai(helper, "StockReseauMauvais", true);
        try {
            BlockPos banc = ouvert.banc();
            // Ouvert : ouvre le menu de l'établi par le clic droit du bloc ; ferme : n'a rien ouvert.
            for (Essai e : List.of(ouvert, mauvais)) {
                helper.getLevel().getBlockState(banc).use(helper.getLevel(), e.joueur,
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(banc), net.minecraft.core.Direction.UP,
                                banc, false));
                helper.assertTrue(e.joueur.containerMenu instanceof EngineerBenchMenu, "l'établi devait s'ouvrir");
            }
            ouvert.j.envoyer(new OpenStockPacket(banc));
            ferme.j.envoyer(new OpenStockPacket(banc));
            citoyen.j.envoyer(new OpenStockPacket(banc));
            mauvais.j.envoyer(new OpenStockPacket(banc.above(5)));
        } catch (RuntimeException ex) {
            for (Essai e : List.of(ouvert, ferme, citoyen, mauvais)) {
                e.fermer();
            }
            throw ex;
        }
        helper.runAfterDelay(4, () -> {
            try {
                BlockPos banc = ouvert.banc();
                helper.assertTrue(ouvert.joueur.containerMenu instanceof BenchStockMenu m && m.pos().equals(banc),
                        "le stock devait s'ouvrir depuis l'établi");
                helper.assertTrue(ferme.joueur.containerMenu == ferme.joueur.inventoryMenu,
                        "sans établi ouvert, la demande devait être ignorée");
                helper.assertTrue(citoyen.joueur.containerMenu == citoyen.joueur.inventoryMenu,
                        "un citoyen n'ouvre pas le stock");
                helper.assertTrue(mauvais.joueur.containerMenu instanceof EngineerBenchMenu,
                        "un établi qui n'est pas le sien devait être ignoré");
                // Le retour : depuis le stock seulement.
                ouvert.j.envoyer(new OpenBenchPacket(banc));
                mauvais.j.envoyer(new OpenBenchPacket(banc));
                ferme.j.envoyer(new OpenBenchPacket(banc));
                helper.runAfterDelay(4, () -> {
                    try {
                        helper.assertTrue(ouvert.joueur.containerMenu instanceof EngineerBenchMenu m && m.pos().equals(banc),
                                "le retour devait rouvrir l'établi à la même place");
                        helper.assertTrue(mauvais.joueur.containerMenu instanceof EngineerBenchMenu,
                                "le retour depuis l'établi ne devait rien rouvrir d'autre");
                        helper.assertTrue(ferme.joueur.containerMenu == ferme.joueur.inventoryMenu,
                                "sans stock ouvert, le retour devait être ignoré");
                        helper.succeed();
                    } finally {
                        for (Essai e : List.of(ouvert, ferme, citoyen, mauvais)) {
                            e.fermer();
                        }
                    }
                });
            } catch (RuntimeException ex) {
                for (Essai e : List.of(ouvert, ferme, citoyen, mauvais)) {
                    e.fermer();
                }
                throw ex;
            }
        });
    }
}
