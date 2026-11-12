package dev.oneforallsurvival.dev;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SlotOwner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * Les pièces ne se rangent qu'aux endroits permis : refus dans un coffre, un tonneau, une boîte de Shulker, le
 * coffre de l'Ender (clic, maj-clic, touche numérique, glissé), dans une sacoche, un cadre, un porte-armures, un
 * allay, un entonnoir ; l'inventaire et la grille de change restent permis. Chaque refus est accompagné d'un témoin :
 * le même geste avec de la terre passe, ce qui prouve que le geste est bien fait.
 *
 * <p>Les essais de la bourse (vide qui passe, pleine refusée) sont ceux de la tâche 5, qui crée la bourse.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RangementGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LOT_A = "rangement_a";
    private static final String LOT_B = "rangement_b";
    private static final String LOT_C = "rangement_c";
    private static final String LOT_ECRAN = "rangement_ecran";
    private static final String LOT_PACK = "rangement_pack";
    private static final String LOT_TAPIS = "rangement_depot";

    private static final BlockPos RANGEMENT = new BlockPos(3, 0, 3);

    private RangementGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static ResourceLocation id(String path) {
        return new ResourceLocation("oneforallsurvival", path);
    }

    /** Vide la zone de jeu (le gabarit n'est pas vide au-dessus du plancher) et retire les entités sauf les joueurs. */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 0; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        AABB zone = new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone, e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(3.5, 0, 5.5)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    private static ItemStack piece(int nombre) {
        return new ItemStack(Coin.GOLD.item(), nombre);
    }

    /** Ouvre l'écran d'un fournisseur pour le joueur et renvoie cet écran. */
    private static AbstractContainerMenu ouvrir(GameTestHelper helper, JoueurDeTest j, MenuProvider fournisseur) {
        j.joueur().openMenu(fournisseur);
        helper.assertTrue(j.joueur().containerMenu != j.joueur().inventoryMenu,
                "l'écran de " + fournisseur.getDisplayName().getString() + " ne s'est pas ouvert");
        return j.joueur().containerMenu;
    }

    private static AbstractContainerMenu ouvrirBloc(GameTestHelper helper, JoueurDeTest j, Block bloc) {
        helper.setBlock(RANGEMENT, bloc);
        BlockEntity be = helper.getBlockEntity(RANGEMENT);
        helper.assertTrue(be instanceof MenuProvider, bloc + " n'a pas d'écran : " + be);
        return ouvrir(helper, j, (MenuProvider) be);
    }

    /** Les cases de l'écran qui ne sont pas à l'inventaire du joueur. */
    private static List<Slot> cases(AbstractContainerMenu menu, ServerPlayer joueur) {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container != joueur.getInventory()) {
                out.add(slot);
            }
        }
        return out;
    }

    /** La case de l'écran qui montre la case {@code indice} de l'inventaire du joueur. */
    private static Slot caseInventaire(AbstractContainerMenu menu, ServerPlayer joueur, int indice) {
        for (Slot slot : menu.slots) {
            if (slot.container == joueur.getInventory() && slot.getContainerSlot() == indice) {
                return slot;
            }
        }
        throw new AssertionError("pas de case d'inventaire " + indice + " dans " + menu.getClass().getName());
    }

    /** Le nombre d'objets de cet item dans les cases de l'écran qui ne sont pas à l'inventaire du joueur. */
    private static int dedans(AbstractContainerMenu menu, ServerPlayer joueur, ItemStack modele) {
        int total = 0;
        for (Slot slot : cases(menu, joueur)) {
            if (slot.getItem().is(modele.getItem())) {
                total += slot.getItem().getCount();
            }
        }
        return total;
    }

    private static int argentDedans(AbstractContainerMenu menu, ServerPlayer joueur) {
        int total = 0;
        for (Slot slot : cases(menu, joueur)) {
            if (MoneyStorageRules.isMoney(slot.getItem())) {
                total += slot.getItem().getCount();
            }
        }
        return total;
    }

    /** Clic : le curseur tient l'objet et on clique sur la première case du rangement. */
    private static void cliquer(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        menu.setCarried(objet.copy());
        menu.clicked(cases(menu, j.joueur()).get(0).index, 0, ClickType.PICKUP, j.joueur());
        menu.setCarried(ItemStack.EMPTY);
    }

    /** Maj-clic : l'objet est dans l'inventaire du joueur, à la case 9. */
    private static void majCliquer(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        j.joueur().getInventory().setItem(9, objet.copy());
        menu.clicked(caseInventaire(menu, j.joueur(), 9).index, 0, ClickType.QUICK_MOVE, j.joueur());
    }

    /** Touche numérique : l'objet est à la case 0 de la barre, on la presse sur la première case du rangement. */
    private static void toucherNumerique(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        j.joueur().getInventory().setItem(0, objet.copy());
        menu.clicked(cases(menu, j.joueur()).get(0).index, 0, ClickType.SWAP, j.joueur());
    }

    /** Glissé : le curseur tient l'objet, on le répartit sur la première case du rangement et sur une case d'inventaire. */
    private static void glisser(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        menu.setCarried(objet.copy());
        int rangement = cases(menu, j.joueur()).get(0).index;
        int inventaire = caseInventaire(menu, j.joueur(), 10).index;
        ServerPlayer p = j.joueur();
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(rangement, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(inventaire, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, p);
        menu.setCarried(ItemStack.EMPTY);
    }

    private static void viderRangement(AbstractContainerMenu menu, ServerPlayer joueur) {
        for (Slot slot : cases(menu, joueur)) {
            slot.set(ItemStack.EMPTY);
        }
    }

    /** Les quatre gestes, un rangement après l'autre : la pièce n'entre jamais, la terre entre au moins une fois. */
    private static void refuseTout(GameTestHelper helper, JoueurDeTest j, AbstractContainerMenu menu, String nom) {
        ServerPlayer p = j.joueur();
        ItemStack terre = new ItemStack(Items.DIRT, 8);
        cliquer(menu, j, piece(8));
        helper.assertTrue(argentDedans(menu, p) == 0, nom + " : clic, argent accepté");
        cliquer(menu, j, terre);
        helper.assertTrue(dedans(menu, p, terre) > 0, nom + " : le témoin du clic n'est pas entré, le geste ne prouve rien");
        viderRangement(menu, p);

        majCliquer(menu, j, piece(8));
        helper.assertTrue(argentDedans(menu, p) == 0, nom + " : maj-clic, argent accepté");
        helper.assertTrue(p.getInventory().getItem(9).is(Coin.GOLD.item()), nom + " : la pièce a quitté l'inventaire");
        majCliquer(menu, j, terre);
        helper.assertTrue(dedans(menu, p, terre) > 0, nom + " : le témoin du maj-clic n'est pas entré");
        viderRangement(menu, p);
        p.getInventory().clearContent();

        toucherNumerique(menu, j, piece(8));
        helper.assertTrue(argentDedans(menu, p) == 0, nom + " : touche numérique, argent accepté");
        helper.assertTrue(p.getInventory().getItem(0).is(Coin.GOLD.item()), nom + " : la pièce a changé de main");
        toucherNumerique(menu, j, terre);
        helper.assertTrue(dedans(menu, p, terre) > 0, nom + " : le témoin de la touche numérique n'est pas entré");
        viderRangement(menu, p);
        p.getInventory().clearContent();

        glisser(menu, j, piece(8));
        helper.assertTrue(argentDedans(menu, p) == 0, nom + " : glissé, argent accepté");
        glisser(menu, j, terre);
        helper.assertTrue(dedans(menu, p, terre) > 0, nom + " : le témoin du glissé n'est pas entré");
        viderRangement(menu, p);
    }

    // ---------------------------------------------------------------------------------------------
    // Cases : lot A
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void refuseeDansUnCoffre(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur1");
        try {
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            cliquer(menu, j, piece(8));
            helper.assertTrue(argentDedans(menu, j.joueur()) == 0, "coffre : une pièce est entrée au clic");
            cliquer(menu, j, new ItemStack(Items.DIRT, 8));
            helper.assertTrue(dedans(menu, j.joueur(), new ItemStack(Items.DIRT)) == 8, "coffre : le témoin n'est pas entré");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void refuseeDansUnTonneau(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur2");
        try {
            refuseTout(helper, j, ouvrirBloc(helper, j, Blocks.BARREL), "tonneau");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void refuseeDansUneBoiteDeShulker(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur3");
        try {
            refuseTout(helper, j, ouvrirBloc(helper, j, Blocks.SHULKER_BOX), "boîte de Shulker");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void refuseeDansLeCoffreDeLEnder(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur4");
        try {
            ServerPlayer p = j.joueur();
            AbstractContainerMenu menu = ouvrir(helper, j, new SimpleMenuProvider(
                    (n, inv, joueur) -> ChestMenu.threeRows(n, inv, joueur.getEnderChestInventory()),
                    Component.literal("ender")));
            refuseTout(helper, j, menu, "coffre de l'Ender");
            helper.assertTrue(p.getEnderChestInventory().isEmpty(), "le coffre de l'Ender n'est pas vide");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void majClicRefuse(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur5");
        try {
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            ServerPlayer p = j.joueur();
            majCliquer(menu, j, piece(64));
            helper.assertTrue(argentDedans(menu, p) == 0, "maj-clic : argent accepté");
            helper.assertTrue(p.getInventory().getItem(9).getCount() == 64, "maj-clic : la pile a bougé");
            // Une pile de pièces d'une autre coupure et un lot de plusieurs cases.
            for (int i = 9; i < 20; i++) {
                p.getInventory().setItem(i, new ItemStack(Coin.RUBY.item(), 3));
                menu.clicked(caseInventaire(menu, p, i).index, 0, ClickType.QUICK_MOVE, p);
            }
            helper.assertTrue(argentDedans(menu, p) == 0, "maj-clic en série : argent accepté");
            majCliquer(menu, j, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(dedans(menu, p, new ItemStack(Items.DIRT)) == 64, "le témoin du maj-clic n'est pas entré");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void toucheNumeriqueRefusee(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur6");
        try {
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            ServerPlayer p = j.joueur();
            for (int touche = 0; touche < 9; touche++) {
                p.getInventory().setItem(touche, piece(5));
                menu.clicked(cases(menu, p).get(touche).index, touche, ClickType.SWAP, p);
            }
            helper.assertTrue(argentDedans(menu, p) == 0, "touche numérique : argent accepté");
            // Le geste du clavier pour la main gauche (touche F) passe par la même route : la case 40 du joueur.
            p.getInventory().offhand.set(0, piece(5));
            menu.clicked(cases(menu, p).get(0).index, 40, ClickType.SWAP, p);
            helper.assertTrue(argentDedans(menu, p) == 0, "touche F : argent accepté");
            toucherNumerique(menu, j, new ItemStack(Items.DIRT, 8));
            helper.assertTrue(dedans(menu, p, new ItemStack(Items.DIRT)) == 8, "le témoin de la touche n'est pas entré");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void glisseRefuse(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur7");
        try {
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            glisser(menu, j, piece(8));
            helper.assertTrue(argentDedans(menu, j.joueur()) == 0, "glissé : argent accepté");
            glisser(menu, j, new ItemStack(Items.DIRT, 8));
            helper.assertTrue(dedans(menu, j.joueur(), new ItemStack(Items.DIRT)) > 0, "le témoin du glissé n'est pas entré");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void accepteeDansLInventaire(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur8");
        try {
            ServerPlayer p = j.joueur();
            // Dans un coffre ouvert, les cases de l'inventaire du joueur prennent les pièces : maj-clic entre cases,
            // clic, touche numérique.
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            p.getInventory().setItem(0, piece(8));
            menu.clicked(caseInventaire(menu, p, 0).index, 0, ClickType.QUICK_MOVE, p);
            helper.assertTrue(PlayerCoins.held(p).getOrDefault(Coin.GOLD, 0L) == 8L, "le maj-clic a perdu des pièces");
            helper.assertTrue(argentDedans(menu, p) == 0, "les pièces ont fui dans le coffre");
            menu.setCarried(piece(3));
            menu.clicked(caseInventaire(menu, p, 20).index, 0, ClickType.PICKUP, p);
            helper.assertTrue(p.getInventory().getItem(20).is(Coin.GOLD.item()), "le clic ne pose pas dans l'inventaire");
            p.closeContainer();
            // Et dans l'inventaire seul, grille de change comprise.
            p.inventoryMenu.getSlot(1).set(new ItemStack(Coin.SILVER.item()));
            helper.assertTrue(p.inventoryMenu.getSlot(1).getItem().is(Coin.SILVER.item()), "la grille 2x2 est lisible");
            p.inventoryMenu.setCarried(piece(1));
            p.inventoryMenu.clicked(2, 0, ClickType.PICKUP, p);
            helper.assertTrue(p.inventoryMenu.getSlot(2).getItem().is(Coin.GOLD.item()), "la grille 2x2 refuse une pièce");
            p.inventoryMenu.setCarried(ItemStack.EMPTY);
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : entités, entonnoir, grille 2x2, sac, balayage
    // ---------------------------------------------------------------------------------------------

    private static InteractionResult toucher(ServerPlayer p, Entity cible) {
        // Le chemin du serveur : Forge d'abord (point précis), puis l'interaction de l'entité.
        InteractionResult r = ForgeHooks.onInteractEntityAt(p, cible, new Vec3(0.0, 1.0, 0.0), InteractionHand.MAIN_HAND);
        if (r == null) {
            r = cible.interactAt(p, new Vec3(0.0, 1.0, 0.0), InteractionHand.MAIN_HAND);
        }
        if (!r.consumesAction()) {
            r = p.interactOn(cible, InteractionHand.MAIN_HAND);
        }
        return r;
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void cadreEtPorteArmureRefuses(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur9");
        try {
            ServerPlayer p = j.joueur();
            Vec3 ici = helper.absoluteVec(new Vec3(3.5, 0.0, 1.5));
            ItemFrame cadre = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 1)), Direction.SOUTH);
            helper.getLevel().addFreshEntity(cadre);
            ArmorStand porte = new ArmorStand(helper.getLevel(), ici.x + 2, ici.y, ici.z);
            porte.setShowArms(true);
            helper.getLevel().addFreshEntity(porte);
            Allay allay = EntityType.ALLAY.create(helper.getLevel());
            allay.moveTo(ici.x - 2, ici.y, ici.z);
            helper.getLevel().addFreshEntity(allay);

            p.setItemInHand(InteractionHand.MAIN_HAND, piece(4));
            toucher(p, cadre);
            toucher(p, porte);
            toucher(p, allay);
            helper.assertTrue(cadre.getItem().isEmpty(), "le cadre a pris : " + cadre.getItem());
            helper.assertTrue(porte.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "le porte-armures a pris");
            helper.assertTrue(allay.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "l'allay a pris");
            helper.assertTrue(p.getMainHandItem().getCount() == 4, "la pile du joueur a bougé");

            // Les témoins : le même geste avec de la terre est reçu par le cadre, le porte-armures et l'allay.
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
            toucher(p, cadre);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
            toucher(p, porte);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
            toucher(p, allay);
            helper.assertTrue(cadre.getItem().is(Items.DIRT), "témoin : le cadre n'a pas pris la terre");
            helper.assertTrue(porte.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIRT), "témoin : le porte-armures");
            helper.assertTrue(allay.getItemInHand(InteractionHand.MAIN_HAND).is(Items.DIRT), "témoin : l'allay");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 120)
    public static void unEntonnoirNePoussePasDePiece(GameTestHelper helper) {
        degager(helper);
        BlockPos entonnoir = new BlockPos(3, 1, 3);
        BlockPos coffre = new BlockPos(3, 0, 3);
        helper.setBlock(coffre, Blocks.CHEST);
        helper.setBlock(entonnoir, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(entonnoir);
        // Posées sans case : le refus est celui de l'entonnoir, pas celui d'une case.
        hopper.setItem(0, piece(5));
        hopper.setItem(1, new ItemStack(Items.DIRT, 3));
        helper.runAfterDelay(60, () -> {
            net.minecraft.world.Container c = (net.minecraft.world.Container) helper.getBlockEntity(coffre);
            int terre = 0;
            int argent = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                terre += c.getItem(i).is(Items.DIRT) ? c.getItem(i).getCount() : 0;
                argent += MoneyStorageRules.isMoney(c.getItem(i)) ? c.getItem(i).getCount() : 0;
            }
            helper.assertTrue(terre == 3, "témoin : la terre devait passer, " + terre);
            helper.assertTrue(argent == 0, "l'entonnoir a poussé des pièces : " + argent);
            helper.assertTrue(hopper.getItem(0).getCount() == 5 && hopper.getItem(0).is(Coin.GOLD.item()),
                    "les pièces ont quitté l'entonnoir : " + hopper.getItem(0));
            helper.succeed();
        });
    }

    /** Une recette sans forme qui fabrique 3 pièces de rubis à partir d'un bloc de terre : ni change, ni légitime. */
    private static Recipe<?> recetteHostile() {
        return new ShapelessRecipe(new ResourceLocation("autre_mod", "rubis_gratuits"), "", CraftingBookCategory.MISC,
                new ItemStack(Coin.RUBY.item(), 3), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT)));
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void majClicDansLaGrille2x2SansRecetteLegitimeNeDonneRien(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur10");
        RecipeManager vivant = helper.getLevel().getServer().getRecipeManager();
        List<Recipe<?>> avant = new ArrayList<>(vivant.getRecipes());
        try {
            List<Recipe<?>> avec = new ArrayList<>(avant);
            avec.add(recetteHostile());
            vivant.replaceRecipes(avec);
            ServerPlayer p = j.joueur();
            p.getInventory().clearContent();
            p.inventoryMenu.getSlot(1).set(new ItemStack(Items.DIRT, 2));
            // Le témoin : la recette hostile est bien active, le résultat est des pièces de rubis.
            helper.assertTrue(p.inventoryMenu.getSlot(0).getItem().is(Coin.RUBY.item()),
                    "la recette hostile n'est pas active : " + p.inventoryMenu.getSlot(0).getItem());
            p.inventoryMenu.clicked(0, 0, ClickType.QUICK_MOVE, p);
            helper.assertTrue(PlayerCoins.held(p).isEmpty(), "des pièces sont sorties de la grille : " + PlayerCoins.held(p));
            helper.assertTrue(p.inventoryMenu.getSlot(1).getItem().getCount() == 2,
                    "les ingrédients ont été pris : " + p.inventoryMenu.getSlot(1).getItem());
            helper.succeed();
        } finally {
            vivant.replaceRecipes(avant);
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leChangeResteLegitimeDansLaGrille2x2(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur11");
        try {
            ServerPlayer p = j.joueur();
            p.getInventory().clearContent();
            p.inventoryMenu.getSlot(1).set(new ItemStack(Coin.SILVER.item(), 2));
            p.inventoryMenu.clicked(0, 0, ClickType.QUICK_MOVE, p);
            long cuivre = PlayerCoins.held(p).getOrDefault(Coin.COPPER, 0L);
            helper.assertTrue(cuivre == 20L, "deux argents donnent 20 cuivres au maj-clic, vus " + cuivre);
            helper.assertTrue(p.inventoryMenu.getSlot(1).getItem().isEmpty(), "la grille devait être consommée");
            helper.assertTrue(CoinMath.valueOf(PlayerCoins.held(p)) == 20L, "la valeur n'est pas conservée");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneSacocheNePrendPasUnePiece(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur12");
        try {
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(9, new ItemStack(Items.BUNDLE));
            Slot sacoche = caseInventaire(p.inventoryMenu, p, 9);
            // Clic droit d'un objet du curseur sur la sacoche : il y entre.
            p.inventoryMenu.setCarried(piece(8));
            p.inventoryMenu.clicked(sacoche.index, 1, ClickType.PICKUP, p);
            helper.assertTrue(p.inventoryMenu.getCarried().getCount() == 8, "le curseur a perdu des pièces");
            helper.assertTrue(!p.getInventory().getItem(9).hasTag(), "la sacoche a pris une pièce : " + p.getInventory().getItem(9).getTag());
            // Clic droit de la sacoche sur la pièce d'une case : elle l'avale.
            p.inventoryMenu.setCarried(ItemStack.EMPTY);
            p.getInventory().setItem(10, piece(8));
            p.inventoryMenu.setCarried(p.getInventory().getItem(9).copy());
            p.getInventory().setItem(9, ItemStack.EMPTY);
            p.inventoryMenu.clicked(caseInventaire(p.inventoryMenu, p, 10).index, 1, ClickType.PICKUP, p);
            helper.assertTrue(p.getInventory().getItem(10).getCount() == 8, "la sacoche a avalé les pièces");
            helper.assertTrue(!p.inventoryMenu.getCarried().hasTag(), "la sacoche a un contenu");
            // Le témoin : la terre entre dans la sacoche.
            p.getInventory().setItem(10, new ItemStack(Items.DIRT, 8));
            p.inventoryMenu.clicked(caseInventaire(p.inventoryMenu, p, 10).index, 1, ClickType.PICKUP, p);
            helper.assertTrue(p.getInventory().getItem(10).getCount() < 8 || p.inventoryMenu.getCarried().hasTag(),
                    "témoin : la terre n'est pas entrée dans la sacoche");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unBalayageRendLesPiecesDUneCaseInterdite(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur13");
        try {
            ServerPlayer p = j.joueur();
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            // Un mod qui surcharge mayPlace sans appeler l'original : la pièce entre quand même, par la case.
            menu.getSlot(0).set(piece(7));
            helper.assertTrue(argentDedans(menu, p) == 7, "la pièce n'a pas été posée de force");
            p.closeContainer();
            helper.assertTrue(PlayerCoins.held(p).getOrDefault(Coin.GOLD, 0L) == 7L,
                    "le balayage n'a pas rendu les pièces : " + PlayerCoins.held(p));
            helper.assertTrue(((net.minecraft.world.Container) helper.getBlockEntity(RANGEMENT)).isEmpty(), "le coffre garde des pièces");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unBlocSansEcranRefuseLaPiece(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur14");
        try {
            ServerPlayer p = j.joueur();
            // Un feu de camp porteur d'un stockage d'objets d'essai (RangementDevCaps) : un dépôt, sans écran.
            helper.setBlock(RANGEMENT, Blocks.CAMPFIRE);
            BlockPos abs = helper.absolutePos(RANGEMENT);
            helper.assertTrue(helper.getBlockEntity(RANGEMENT)
                    .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                    .isPresent(), "le stockage d'essai manque : le test ne prouve rien");
            BlockHitResult coup = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            p.setItemInHand(InteractionHand.MAIN_HAND, piece(3));
            PlayerInteractEvent.RightClickBlock avecPiece = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, abs, coup);
            helper.assertTrue(avecPiece.getUseBlock() == Event.Result.DENY && avecPiece.getUseItem() == Event.Result.DENY,
                    "le clic droit avec une pièce n'est pas refusé : " + avecPiece.getUseBlock() + "/" + avecPiece.getUseItem());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 3));
            PlayerInteractEvent.RightClickBlock avecTerre = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, abs, coup);
            helper.assertTrue(avecTerre.getUseBlock() != Event.Result.DENY, "témoin : la terre est refusée aussi");
            // Un coffre ouvre un écran : le clic droit n'est pas touché, ses cases font le tri.
            helper.setBlock(RANGEMENT, Blocks.CHEST);
            p.setItemInHand(InteractionHand.MAIN_HAND, piece(3));
            PlayerInteractEvent.RightClickBlock surCoffre = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, abs, coup);
            helper.assertTrue(surCoffre.getUseBlock() != Event.Result.DENY, "le coffre est refusé : il ne pourrait plus s'ouvrir");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lesMixinsSontApplies(GameTestHelper helper) {
        helper.assertTrue(SlotOwner.class.isAssignableFrom(Slot.class), "SlotMixin n'est pas appliqué à Slot");
        helper.assertTrue(SlotOwner.class.isAssignableFrom(net.minecraftforge.items.SlotItemHandler.class),
                "SlotItemHandler n'hérite pas de Slot ?");
        ChestMenu menu = ChestMenu.oneRow(1, joueurMenu(helper).getInventory());
        helper.assertTrue(((SlotOwner) menu.getSlot(0)).oneforall$menu() == menu, "AbstractContainerMenuMixin n'est pas appliqué");
        helper.succeed();
    }

    private static net.minecraft.world.entity.player.Player joueurMenu(GameTestHelper helper) {
        return helper.makeMockPlayer();
    }

    // ---------------------------------------------------------------------------------------------
    // Écran permis par déclaration
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_ECRAN)
    public static void unEcranDeclarePermetLesPieces(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur15");
        try {
            ServerPlayer p = j.joueur();
            helper.setBlock(RANGEMENT, Blocks.HOPPER);
            MenuProvider entonnoir = (MenuProvider) helper.getBlockEntity(RANGEMENT);
            AbstractContainerMenu menu = ouvrir(helper, j, entonnoir);
            helper.assertTrue(menu.getType() == MenuType.HOPPER, "type inattendu " + menu.getType());
            cliquer(menu, j, piece(2));
            helper.assertTrue(argentDedans(menu, p) == 0, "avant la déclaration : argent accepté");
            p.closeContainer();

            MoneyStorageRules.register(MenuType.HOPPER);
            try {
                menu = ouvrir(helper, j, entonnoir);
                cliquer(menu, j, piece(2));
                helper.assertTrue(argentDedans(menu, p) == 2, "écran déclaré : la pièce devait entrer");
                viderRangement(menu, p);
            } finally {
                MoneyStorageRules.unregister(MenuType.HOPPER);
            }
            cliquer(menu, j, piece(2));
            helper.assertTrue(argentDedans(menu, p) == 0, "après le retrait de la déclaration : argent accepté");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : machines du mod, case fantôme, aspiration
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void lesMachinesDuModSOuvrentPieceEnMain(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur17");
        try {
            ServerPlayer p = j.joueur();
            helper.assertTrue(BuiltInRegistries.BLOCK.getTag(dev.oneforallsurvival.economy.MoneyGuard.MONEY_USE_ALLOWED).isPresent(),
                    "l'étiquette money_use_allowed n'existe pas");
            BlockPos abs = helper.absolutePos(RANGEMENT);
            BlockHitResult coup = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            for (String nom : new String[] {"canteen_pot", "desalinator", "dryer", "fridge", "incubator", "galvanic_bath"}) {
                Block bloc = BuiltInRegistries.BLOCK.get(id(nom));
                helper.assertTrue(bloc != Blocks.AIR, nom + " n'existe pas");
                helper.setBlock(RANGEMENT, bloc);
                helper.assertTrue(helper.getLevel().getBlockState(abs).getMenuProvider(helper.getLevel(), abs) != null,
                        nom + " : getMenuProvider est nul");
                p.setItemInHand(InteractionHand.MAIN_HAND, piece(3));
                PlayerInteractEvent.RightClickBlock evt = ForgeHooks.onRightClickBlock(p, InteractionHand.MAIN_HAND, abs, coup);
                helper.assertTrue(evt.getUseBlock() != Event.Result.DENY, nom + " : le clic droit avec une pièce est refusé");
                p.gameMode.useItemOn(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, coup);
                helper.assertTrue(p.containerMenu != p.inventoryMenu, nom + " : l'écran ne s'est pas ouvert, pièce en main");
                p.closeContainer();
                p.getInventory().clearContent();
                helper.setBlock(RANGEMENT, Blocks.AIR);
            }
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneCaseFantomeNeCreePasDArgent(GameTestHelper helper) {
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur18");
        try {
            ServerPlayer p = j.joueur();
            // Une case de filtre : elle accepte tout, ne prend rien au joueur, sur un conteneur quelconque.
            AbstractContainerMenu fantome = new AbstractContainerMenu(null, 77) {
                {
                    addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 0, 0) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return true;
                        }
                    });
                }

                @Override
                public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player joueur, int index) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(net.minecraft.world.entity.player.Player joueur) {
                    return true;
                }
            };
            fantome.getSlot(0).set(piece(7));
            int rendues = dev.oneforallsurvival.economy.MoneyGuard.sweep(p, fantome);
            helper.assertTrue(rendues == 0, "le balayage a rendu une case fantôme : " + rendues);
            helper.assertTrue(PlayerCoins.held(p).isEmpty(), "de l'argent a été créé : " + PlayerCoins.held(p));
            // Témoin : le même balayage rend bien la pièce d'un vrai coffre.
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            menu.getSlot(0).set(piece(7));
            helper.assertTrue(dev.oneforallsurvival.economy.MoneyGuard.sweep(p, menu) == 1, "témoin : le coffre n'est pas balayé");
            helper.assertTrue(PlayerCoins.held(p).getOrDefault(Coin.GOLD, 0L) == 7L, "témoin : pièces non rendues");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 120)
    public static void unEntonnoirNAspirePasUnePiece(GameTestHelper helper) {
        degager(helper);
        BlockPos coffre = new BlockPos(3, 1, 3);
        BlockPos entonnoir = new BlockPos(3, 0, 3);
        helper.setBlock(entonnoir, Blocks.HOPPER);
        helper.setBlock(coffre, Blocks.CHEST);
        net.minecraft.world.Container c = (net.minecraft.world.Container) helper.getBlockEntity(coffre);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(entonnoir);
        // Posées sans case : le refus est celui de l'entonnoir.
        c.setItem(0, piece(5));
        c.setItem(1, new ItemStack(Items.DIRT, 3));
        helper.runAfterDelay(60, () -> {
            int terre = 0;
            int argent = 0;
            for (int i = 0; i < hopper.getContainerSize(); i++) {
                terre += hopper.getItem(i).is(Items.DIRT) ? hopper.getItem(i).getCount() : 0;
                argent += MoneyStorageRules.isMoney(hopper.getItem(i)) ? hopper.getItem(i).getCount() : 0;
            }
            helper.assertTrue(terre == 3, "témoin : la terre devait être aspirée, " + terre);
            helper.assertTrue(argent == 0, "l'entonnoir a aspiré des pièces : " + argent);
            helper.assertTrue(c.getItem(0).getCount() == 5 && c.getItem(0).is(Coin.GOLD.item()),
                    "les pièces ont quitté le coffre : " + c.getItem(0));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Avec le pack : les stockages des mods installés
    // ---------------------------------------------------------------------------------------------

    private static boolean sansPack(GameTestHelper helper, String nom) {
        if (ModList.get().isLoaded("create")) {
            return false;
        }
        LOGGER.info("[rangement] {} : IGNORE, pack absent", nom);
        helper.succeed();
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Block> blocsDe(BlockEntityType<?> type) {
        java.util.Set<Block> blocs = ObfuscationReflectionHelper.getPrivateValue(BlockEntityType.class, type, "validBlocks");
        return blocs == null ? List.of() : new ArrayList<>(blocs);
    }

    @GameTest(template = "test/plancher", batch = LOT_PACK, timeoutTicks = 1200)
    public static void lesStockagesDesModsInstallesRefusent(GameTestHelper helper) {
        if (sansPack(helper, "lesStockagesDesModsInstallesRefusent")) {
            return;
        }
        degager(helper);
        JoueurDeTest j = joueur(helper, "Rangeur16");
        List<String> essayes = new ArrayList<>();
        List<String> ecartes = new ArrayList<>();
        List<String> acceptes = new ArrayList<>();
        List<String> fantomes = new ArrayList<>();
        try {
            ServerPlayer p = j.joueur();
            BlockPos abs = helper.absolutePos(RANGEMENT);
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                ResourceLocation cle = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                if (cle == null || cle.getNamespace().equals("minecraft") || cle.getNamespace().equals("oneforallsurvival")) {
                    continue;
                }
                List<Block> blocs = blocsDe(type);
                if (blocs.isEmpty()) {
                    ecartes.add(cle + " (aucun bloc)");
                    continue;
                }
                BlockState etat = blocs.get(0).defaultBlockState();
                try {
                    helper.setBlock(RANGEMENT, etat);
                    BlockEntity be = helper.getLevel().getBlockEntity(abs);
                    if (be == null) {
                        ecartes.add(cle + " (le bloc " + BuiltInRegistries.BLOCK.getKey(blocs.get(0)) + " n'a pas d'entité posé seul)");
                        continue;
                    }
                    if (!(be instanceof MenuProvider fournisseur)) {
                        ecartes.add(cle + " (pas un MenuProvider)");
                        continue;
                    }
                    p.getInventory().clearContent();
                    p.openMenu(fournisseur);
                    AbstractContainerMenu menu = p.containerMenu;
                    if (menu == p.inventoryMenu) {
                        ecartes.add(cle + " (le menu ne s'ouvre pas pour le joueur d'essai)");
                        continue;
                    }
                    essayes.add(cle + " -> " + menu.getClass().getName() + " (" + menu.slots.size() + " cases)");
                    verifier(p, menu, cle.toString(), acceptes, fantomes);
                } catch (Throwable e) {
                    ecartes.add(cle + " (exception " + e + ")");
                } finally {
                    try {
                        p.closeContainer();
                        p.getInventory().clearContent();
                        helper.setBlock(RANGEMENT, Blocks.AIR);
                    } catch (Throwable ignore) {
                        // Le nettoyage d'un bloc d'un autre mod ne doit pas masquer le résultat.
                    }
                }
            }
            LOGGER.info("[rangement] stockages de mods essayes ({}) : {}", essayes.size(), essayes);
            LOGGER.info("[rangement] stockages de mods ecartes ({}) : {}", ecartes.size(), ecartes);
            LOGGER.info("[rangement] cases fantomes (le joueur garde ses pieces) ({}) : {}", fantomes.size(), fantomes);
            helper.assertTrue(acceptes.isEmpty(), "des écrans de mods ont accepté une pièce : " + acceptes);
            helper.assertTrue(!essayes.isEmpty(), "aucun écran de mod n'a été essayé : le test ne prouve rien");
            helper.succeed();
        } finally {
            quitter(j);
            degager(helper);
        }
    }

    /** Les pièces que le joueur tient : inventaire et curseur. */
    private static int tenu(ServerPlayer p, AbstractContainerMenu menu) {
        int total = MoneyStorageRules.isMoney(menu.getCarried()) ? menu.getCarried().getCount() : 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack pile = p.getInventory().getItem(i);
            total += MoneyStorageRules.isMoney(pile) ? pile.getCount() : 0;
        }
        return total;
    }

    /**
     * Les trois gestes (clic, touche numérique, maj-clic) sur chaque case du rangement. Une case qui montre de
     * l'argent après le geste est un stockage qui l'a accepté si le joueur en a perdu ({@code acceptes}) ; si le
     * joueur tient toujours tout, c'est une case fantôme (filtre), que l'on note à part ({@code fantomes}).
     */
    private static void verifier(ServerPlayer p, AbstractContainerMenu menu, String nom, List<String> acceptes,
            List<String> fantomes) {
        for (Slot slot : cases(menu, p)) {
            for (int geste = 0; geste < 2; geste++) {
                int avant = 0;
                try {
                    p.getInventory().clearContent();
                    if (geste == 0) {
                        menu.setCarried(piece(8));
                        avant = tenu(p, menu);
                        menu.clicked(slot.index, 0, ClickType.PICKUP, p);
                    } else {
                        p.getInventory().setItem(0, piece(8));
                        avant = tenu(p, menu);
                        menu.clicked(slot.index, 0, ClickType.SWAP, p);
                    }
                } catch (Throwable e) {
                    LOGGER.info("[rangement] {} case {} geste {} : exception {}", nom, slot.index, geste, e.toString());
                }
                String qui = nom + " case " + slot.index + " (" + slot.getClass().getName() + ") "
                        + (geste == 0 ? "clic" : "touche");
                if (MoneyStorageRules.isMoney(slot.getItem())) {
                    (tenu(p, menu) < avant ? acceptes : fantomes).add(qui);
                    slot.set(ItemStack.EMPTY);
                }
                menu.setCarried(ItemStack.EMPTY);
            }
        }
        for (Slot propre : new ArrayList<>(menu.slots)) {
            if (propre.container != p.getInventory()) {
                continue;
            }
            int avant = 0;
            try {
                p.getInventory().clearContent();
                propre.set(piece(8));
                avant = tenu(p, menu);
                menu.clicked(propre.index, 0, ClickType.QUICK_MOVE, p);
            } catch (Throwable e) {
                LOGGER.info("[rangement] {} maj-clic case {} : exception {}", nom, propre.index, e.toString());
            }
            for (Slot slot : cases(menu, p)) {
                if (MoneyStorageRules.isMoney(slot.getItem())) {
                    (tenu(p, menu) < avant ? acceptes : fantomes)
                            .add(nom + " case " + slot.index + " (" + slot.getClass().getName() + ") maj-clic");
                    slot.set(ItemStack.EMPTY);
                }
            }
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_TAPIS, timeoutTicks = 200)
    public static void unDepotDeCreateNeRamassePasUnePiece(GameTestHelper helper) {
        if (sansPack(helper, "unDepotDeCreateNeRamassePasUnePiece")) {
            return;
        }
        degager(helper);
        Block tapis = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "belt"));
        Block depot = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "depot"));
        helper.assertTrue(tapis != null && depot != null && tapis != Blocks.AIR && depot != Blocks.AIR, "Create sans tapis ni dépôt");
        // Un dépôt et un entonnoir de Create ramassent les objets posés dessus : le témoin est un objet de terre.
        BlockPos sur = new BlockPos(3, 0, 3);
        helper.setBlock(sur, depot);
        Vec3 haut = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
        ItemEntity terre = new ItemEntity(helper.getLevel(), haut.x, haut.y, haut.z, new ItemStack(Items.DIRT, 4));
        terre.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(terre);
        dev.oneforallsurvival.economy.CoinDropEntity piece = dev.oneforallsurvival.economy.CoinDropEntity.at(
                helper.getLevel(), haut.x, haut.y + 0.2, haut.z, Coin.GOLD, 4);
        piece.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(piece);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(terre.isRemoved(), "témoin : le dépôt de Create n'a pas pris l'objet, le test ne prouve rien");
            helper.assertTrue(!piece.isRemoved() && piece.count() == 4, "le dépôt de Create a pris la pièce");
            degager(helper);
            helper.succeed();
        });
    }

    /** Pose un état de Create par les noms de ses propriétés (aucune dépendance de compilation). */
    private static BlockState etatCreate(Block bloc, String... nomsEtValeurs) {
        BlockState etat = bloc.defaultBlockState();
        for (int i = 0; i < nomsEtValeurs.length; i += 2) {
            net.minecraft.world.level.block.state.properties.Property<?> propriete =
                    bloc.getStateDefinition().getProperty(nomsEtValeurs[i]);
            helperAssert(propriete != null, "propriété absente : " + nomsEtValeurs[i]);
            etat = withValeur(etat, propriete, nomsEtValeurs[i + 1]);
        }
        return etat;
    }

    private static void helperAssert(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Comparable<T>> BlockState withValeur(BlockState etat,
            net.minecraft.world.level.block.state.properties.Property<T> propriete, String valeur) {
        T v = propriete.getValue(valeur).orElseThrow(() -> new AssertionError("valeur inconnue : " + valeur));
        return etat.setValue(propriete, v);
    }

    @GameTest(template = "test/plancher", batch = LOT_TAPIS + "_tapis", timeoutTicks = 200)
    public static void unTapisDeCreateNeRamassePasUnePiece(GameTestHelper helper) {
        if (sansPack(helper, "unTapisDeCreateNeRamassePasUnePiece")) {
            return;
        }
        degager(helper);
        Block tapis = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "belt"));
        helper.assertTrue(tapis != null && tapis != Blocks.AIR, "Create sans tapis");
        for (int x = 2; x <= 4; x++) {
            String part = x == 2 ? "start" : (x == 4 ? "end" : "middle");
            helper.setBlock(new BlockPos(x, 0, 3), etatCreate(tapis, "part", part, "slope", "horizontal", "facing", "east"));
        }
        try {
            Class<?> be = Class.forName("com.simibubi.create.content.kinetics.belt.BeltBlock");
            be.getMethod("initBelt", net.minecraft.world.level.Level.class, BlockPos.class)
                    .invoke(null, helper.getLevel(), helper.absolutePos(new BlockPos(2, 0, 3)));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("initBelt : " + e);
        }
        Vec3 haut = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
        ItemEntity terre = new ItemEntity(helper.getLevel(), haut.x, haut.y, haut.z, new ItemStack(Items.DIRT, 4));
        terre.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(terre);
        dev.oneforallsurvival.economy.CoinDropEntity piece = dev.oneforallsurvival.economy.CoinDropEntity.at(
                helper.getLevel(), haut.x, haut.y + 0.2, haut.z, Coin.GOLD, 4);
        piece.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(piece);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(terre.isRemoved(), "témoin : le tapis n'a pas pris l'objet, le test ne prouve rien");
            helper.assertTrue(!piece.isRemoved() && piece.count() == 4, "le tapis de Create a pris la pièce");
            degager(helper);
            helper.succeed();
        });
    }
}
