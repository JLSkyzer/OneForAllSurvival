package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyGuard;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallCashMenu;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import dev.oneforallsurvival.economy.stall.StallLedger;
import dev.oneforallsurvival.economy.stall.StallLedger.Kind;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.stall.StallStockHandler;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * L'étal de marché, première partie (lot E2, tâche 3) : pose et propriétaire, ouverture de la gestion, stock qui
 * refuse l'argent, automatismes (la trémie garnit, ne vide rien), caisse (dépôt, retrait, droits, registre global de
 * l'équipe), casse (seulement vide, seulement par les ayants droit, créatif compris), explosion, piston,
 * rechargement et registre propre à l'étal.
 *
 * <p>Les gestes passent par ce que ferait le client : clic droit réel sur le bloc, {@code clicked} sur le menu,
 * {@link StallCashPacket#apply}. Chaque test range ce qu'il a ouvert (joueurs, droits d'équipe, blocs) en fin de
 * test, même en cas d'échec.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class EtalStockGameTests {

    private static final String LOT_A = "etal_a";
    private static final String LOT_B = "etal_b";
    private static final String LOT_C = "etal_c";
    private static final String LOT_PACK = "etal_pack";

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(1, 0, 3);
    private static final BlockPos C = new BlockPos(1, 0, 6);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    private EtalStockGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Une aire close : sol, pourtour, plafond, lumière, vidée de tout ce qui n'est pas un joueur. */
    static void boite(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean bord = x == 0 || x == 7 || z == 0 || z == 7;
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), bord ? Blocks.STONE : Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.GLOWSTONE);
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        AABB zone = new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone, e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    /** Ce qu'un test a ouvert : tout est rendu à la fermeture, même si le test a échoué. */
    static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<com.mojang.authlib.GameProfile> equipe = new ArrayList<>();
        private boolean ferme;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /** Un joueur de l'équipe : opérateur de niveau 4 (écrit à la main, voir la leçon du 2026-11-12), retiré à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = joueur(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        @Override
        public void close() {
            if (ferme) {
                return;
            }
            ferme = true;
            for (com.mojang.authlib.GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            boite(helper);
        }
    }

    /** Pose un étal comme le ferait le joueur : l'objet en main, un clic sur le sol sous {@code pos}. */
    static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        ItemStack pile = new ItemStack(EconomyRegistry.MARKET_STALL_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    static void poserEtVerifier(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        InteractionResult r = poser(helper, j, pos);
        helper.assertTrue(r.consumesAction() && helper.getBlockState(pos).is(EconomyRegistry.MARKET_STALL.get()),
                "l'étal devait se poser : " + r + ", vu " + helper.getBlockState(pos));
        j.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    static MarketStallBlockEntity entite(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getBlockEntity(pos) instanceof MarketStallBlockEntity, "pas d'entité d'étal en " + pos);
        return (MarketStallBlockEntity) helper.getBlockEntity(pos);
    }

    /** Un clic droit réel, par le jeu : la garde de l'argent, puis le bloc. */
    static void clicDroit(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    private static boolean gestion(JoueurDeTest j) {
        return j.joueur().containerMenu instanceof StallOwnerMenu;
    }

    private static boolean caisse(JoueurDeTest j) {
        return j.joueur().containerMenu instanceof StallCashMenu;
    }

    static StallOwnerMenu menuGestion(GameTestHelper helper, JoueurDeTest j) {
        helper.assertTrue(gestion(j), "la gestion devait être ouverte, vu " + j.joueur().containerMenu);
        return (StallOwnerMenu) j.joueur().containerMenu;
    }

    /** Ouvre la gestion par un clic droit, puis la caisse par le bouton du menu (comme l'écran). */
    private static StallCashMenu ouvrirCaisse(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        clicDroit(helper, j, pos);
        StallOwnerMenu menu = menuGestion(helper, j);
        helper.assertTrue(menu.clickMenuButton(j.joueur(), StallOwnerMenu.BUTTON_CASH), "le bouton Caisse devait répondre");
        helper.assertTrue(caisse(j), "la caisse devait s'ouvrir, vu " + j.joueur().containerMenu);
        return (StallCashMenu) j.joueur().containerMenu;
    }

    private static long porte(JoueurDeTest j, Coin coin) {
        return PlayerCoins.held(j.joueur()).getOrDefault(coin, 0L);
    }

    static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    static void donner(JoueurDeTest j, Coin coin, int nombre) {
        int reste = nombre;
        while (reste > 0) {
            int taille = Math.min(reste, 64);
            j.joueur().getInventory().add(new ItemStack(coin.item(), taille));
            reste -= taille;
        }
    }

    private static Result agir(JoueurDeTest j, StallCashPacket paquet) {
        return StallCashPacket.apply(j.joueur(), paquet);
    }

    static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(new BlockPos(0, -1, 0)), helper.absolutePos(new BlockPos(8, 5, 8))));
    }

    static int compter(StallStockHandler stock, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            if (stock.getStackInSlot(i).is(item)) {
                total += stock.getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    private static int argentDansLe(StallStockHandler stock) {
        int total = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            if (MoneyStorageRules.isMoney(stock.getStackInSlot(i))) {
                total += stock.getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    private static ItemStack piece(int n) {
        return new ItemStack(Coin.GOLD.item(), n);
    }

    private static ItemStack boursePleine() {
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        Purse.add(bourse, Coin.COPPER, 10);
        return bourse;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : pose, droits d'ouverture, stock, automatismes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lePoseurEnEstProprietaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_poseur");
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            helper.assertTrue(j.uuid().equals(be.owner()), "le poseur devait être le propriétaire : " + be.owner());
            helper.assertTrue("etal_poseur".equals(be.ownerName()), "nom du propriétaire : " + be.ownerName());
            helper.assertTrue(be.stock().getSlots() == 27 && be.stock().isEmpty(), "27 cases vides au départ");
            helper.assertTrue(be.cash().isEmpty() && be.cashValue() == 0L, "caisse vide au départ");
            helper.assertTrue(be.ledger().size() == 0 && be.version() == 0, "registre vide, version 0");
            helper.assertTrue(helper.getBlockState(A).getValue(MarketStallBlock.FACING).getAxis().isHorizontal(),
                    "bloc orienté");

            // Sans joueur (distributeur) et par un faux joueur (automate) : refusé.
            ItemStack pile = new ItemStack(EconomyRegistry.MARKET_STALL_ITEM.get());
            InteractionResult distributeur = ((BlockItem) pile.getItem()).place(
                    new DirectionalPlaceContext(helper.getLevel(), helper.absolutePos(B), Direction.DOWN, pile, Direction.UP));
            helper.assertTrue(!distributeur.consumesAction() && helper.getBlockState(B).isAir(),
                    "sans joueur, rien ne devait se poser : " + distributeur);
            FakePlayer faux = FakePlayerFactory.getMinecraft(helper.getLevel());
            BlockPos sol = helper.absolutePos(B.below());
            InteractionResult automate = pile.useOn(new UseOnContext(helper.getLevel(), faux, InteractionHand.MAIN_HAND,
                    pile, new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
            helper.assertTrue(!automate.consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas : " + automate);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unJoueurPoseAutantDEtalsQuIlVeut(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_pose_trois");
            poserEtVerifier(helper, j, A);
            poserEtVerifier(helper, j, B);
            poserEtVerifier(helper, j, C);
            for (BlockPos pos : List.of(A, B, C)) {
                helper.assertTrue(j.uuid().equals(entite(helper, pos).owner()), "propriétaire de " + pos);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unAutreNOuvrePasLaGestion(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_ouv_proprio");
            JoueurDeTest autre = essai.joueur("etal_ouv_autre");
            poserEtVerifier(helper, proprio, A);
            helper.assertTrue(!autre.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            autre.messagesRecus();
            clicDroit(helper, autre, A);
            helper.assertTrue(!gestion(autre) && !caisse(autre), "un autre joueur ne doit pas ouvrir la gestion");
            helper.assertTrue(autre.joueur().containerMenu instanceof dev.oneforallsurvival.economy.stall.StallShopMenu,
                    "il ouvre la vitrine, vu " + autre.joueur().containerMenu);
            autre.joueur().closeContainer();
            helper.assertTrue(!MarketStallBlock.openManage(autre.joueur(), helper.absolutePos(A)),
                    "ni par l'ouverture directe");
            helper.assertTrue(!MarketStallBlock.openCash(autre.joueur(), helper.absolutePos(A)), "ni la caisse");
            helper.assertTrue(!MarketStallBlock.mayManage(autre.joueur(), proprio.uuid()), "mayManage : pas lui");

            // Le propriétaire, lui, ouvre, même avec une pièce en main (le bloc est dans money_use_allowed).
            helper.assertTrue(helper.getBlockState(A).is(MoneyGuard.MONEY_USE_ALLOWED), "bloc absent de money_use_allowed");
            helper.assertTrue(new ItemStack(EconomyRegistry.MARKET_STALL_ITEM.get()).is(BenchCatalog.TABLE_ONLY),
                    "objet absent de table_only");
            proprio.joueur().setItemInHand(InteractionHand.MAIN_HAND, piece(2));
            clicDroit(helper, proprio, A);
            helper.assertTrue(gestion(proprio), "le propriétaire devait ouvrir, pièce en main");
            // La gestion n'est PAS un rangement permis pour l'argent ; la caisse l'est.
            helper.assertTrue(!MoneyStorageRules.isAllowedMenu(proprio.joueur().containerMenu),
                    "la gestion ne doit pas être permise à l'argent");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEquipeOuvreLaGestion(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_eq_proprio");
            JoueurDeTest chef = essai.equipe("etal_eq_chef");
            poserEtVerifier(helper, proprio, A);
            entite(helper, A).stock().setStackInSlot(3, new ItemStack(Items.BREAD, 4));
            helper.assertTrue(chef.joueur().hasPermissions(2), "le chef doit être de l'équipe");
            clicDroit(helper, chef, A);
            StallOwnerMenu menu = menuGestion(helper, chef);
            helper.assertTrue(menu.getSlot(StallOwnerMenu.STOCK_START + 3).getItem().is(Items.BREAD),
                    "l'équipe voit le stock du propriétaire");
            helper.assertTrue(MarketStallBlock.mayManage(chef.joueur(), proprio.uuid()), "mayManage : l'équipe");
            helper.assertTrue(MarketStallBlock.mayManage(proprio.joueur(), proprio.uuid()), "mayManage : le propriétaire");
            helper.assertTrue(!MarketStallBlock.mayManage(proprio.joueur(), null), "pas de propriétaire : pas de gestion");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leStockRefuseLArgent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_refuse");
            ServerPlayer p = j.joueur();
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            StallStockHandler stock = be.stock();
            clicDroit(helper, j, A);
            StallOwnerMenu menu = menuGestion(helper, j);
            int case0 = StallOwnerMenu.STOCK_START;

            // Clic avec la pièce, puis avec la bourse pleine, sur une case du stock.
            for (ItemStack argent : List.of(piece(5), boursePleine())) {
                menu.setCarried(argent.copy());
                menu.clicked(case0, 0, ClickType.PICKUP, p);
                menu.clicked(case0, 1, ClickType.PICKUP, p);
                helper.assertTrue(stock.isEmpty(), "le clic a posé de l'argent : " + stock.getStackInSlot(0));
                menu.setCarried(ItemStack.EMPTY);
            }
            // Maj-clic depuis l'inventaire.
            p.getInventory().items.set(0, piece(5));
            p.getInventory().items.set(1, boursePleine());
            menu.clicked(StallOwnerMenu.slotOfInventory(0), 0, ClickType.QUICK_MOVE, p);
            menu.clicked(StallOwnerMenu.slotOfInventory(1), 0, ClickType.QUICK_MOVE, p);
            helper.assertTrue(stock.isEmpty(), "le maj-clic a posé de l'argent");
            helper.assertTrue(p.getInventory().items.get(0).getCount() == 5 && Purse.total(p.getInventory().items.get(1)) == 10,
                    "l'argent n'a pas bougé de l'inventaire");
            // Touche numérique (échange avec la barre d'action) et glisser.
            menu.clicked(case0, 0, ClickType.SWAP, p);
            menu.clicked(case0, 1, ClickType.SWAP, p);
            helper.assertTrue(stock.isEmpty(), "la touche numérique a posé de l'argent");
            menu.setCarried(piece(8));
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, p);
            menu.clicked(case0, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
            menu.clicked(case0 + 1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
            menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, p);
            helper.assertTrue(stock.isEmpty(), "le glissé a posé de l'argent");
            menu.setCarried(ItemStack.EMPTY);

            // Par le stockage lui-même, et par sa vue exposée.
            for (ItemStack argent : List.of(piece(5), boursePleine(), new ItemStack(Coin.COPPER.item(), 64))) {
                helper.assertTrue(!stock.isItemValid(0, argent), "isItemValid doit refuser " + argent);
                helper.assertTrue(stock.insertItem(0, argent.copy(), false).getCount() == argent.getCount(),
                        "insertItem a pris de l'argent");
                helper.assertTrue(stock.insertOnly().insertItem(0, argent.copy(), false).getCount() == argent.getCount(),
                        "la vue exposée a pris de l'argent");
                helper.assertTrue(!stock.insertOnly().isItemValid(0, argent), "la vue accepte de l'argent");
            }
            helper.assertTrue(stock.isEmpty(), "le stock doit être resté vide");

            // Témoin : le même geste avec du pain passe, par le clic comme par le maj-clic.
            menu.setCarried(new ItemStack(Items.BREAD, 3));
            menu.clicked(case0, 0, ClickType.PICKUP, p);
            helper.assertTrue(stock.getStackInSlot(0).is(Items.BREAD) && stock.getStackInSlot(0).getCount() == 3,
                    "témoin : le pain devait se poser, vu " + stock.getStackInSlot(0));
            p.getInventory().items.set(2, new ItemStack(Items.BREAD, 5));
            menu.clicked(StallOwnerMenu.slotOfInventory(2), 0, ClickType.QUICK_MOVE, p);
            helper.assertTrue(compter(stock, Items.BREAD) == 8, "témoin : le maj-clic du pain, vu " + compter(stock, Items.BREAD));
            helper.assertTrue(argentDansLe(stock) == 0, "aucune pièce dans le stock");
            helper.succeed();
        }
    }

    /** Une trémie au-dessus de l'étal le garnit. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 160)
    public static void uneTremieGarnitLeStock(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            BlockPos etal = new BlockPos(3, 0, 3);
            BlockPos entonnoir = new BlockPos(3, 1, 3);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(entonnoir, Blocks.HOPPER);
            HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(entonnoir);
            hopper.setItem(0, new ItemStack(Items.DIRT, 3));
            hopper.setItem(1, new ItemStack(Items.BREAD, 2));
            MarketStallBlockEntity be = entite(helper, etal);
            helper.runAfterDelay(80, () -> {
                try {
                    helper.assertTrue(compter(be.stock(), Items.DIRT) == 3 && compter(be.stock(), Items.BREAD) == 2,
                            "la trémie devait garnir le stock : terre " + compter(be.stock(), Items.DIRT) + ", pain "
                                    + compter(be.stock(), Items.BREAD));
                    helper.assertTrue(hopper.isEmpty(), "la trémie devait être vidée");
                    helper.succeed();
                } finally {
                    essai.close();
                }
            });
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
    }

    /** Une trémie sous l'étal ne lui prend rien (ni objets, ni pièces de la caisse) ; sous un coffre, elle prend. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 160)
    public static void uneTremieSousLEtalNeVideRien(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            BlockPos etal = new BlockPos(3, 1, 3);
            BlockPos dessous = new BlockPos(3, 0, 3);
            BlockPos coffre = new BlockPos(5, 1, 3);
            BlockPos temoin = new BlockPos(5, 0, 3);
            helper.setBlock(dessous, Blocks.HOPPER);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(temoin, Blocks.HOPPER);
            helper.setBlock(coffre, Blocks.CHEST);
            MarketStallBlockEntity be = entite(helper, etal);
            be.stock().setStackInSlot(0, new ItemStack(Items.DIRT, 5));
            be.stock().setStackInSlot(4, new ItemStack(Items.BREAD, 2));
            be.addCash(Coin.COPPER, 7);
            be.addCash(Coin.GOLD, 2);
            ((Container) helper.getBlockEntity(coffre)).setItem(0, new ItemStack(Items.DIRT, 5));
            HopperBlockEntity sous = (HopperBlockEntity) helper.getBlockEntity(dessous);
            HopperBlockEntity sousCoffre = (HopperBlockEntity) helper.getBlockEntity(temoin);
            helper.runAfterDelay(100, () -> {
                try {
                    helper.assertTrue(!sousCoffre.isEmpty(), "témoin : la trémie sous le coffre devait prendre de la terre");
                    helper.assertTrue(sous.isEmpty(), "la trémie sous l'étal a pris : " + sous.getItem(0) + " " + sous.getItem(1));
                    helper.assertTrue(compter(be.stock(), Items.DIRT) == 5 && compter(be.stock(), Items.BREAD) == 2,
                            "le stock devait être intact");
                    helper.assertTrue(be.cash().get(Coin.COPPER) == 7L && be.cash().get(Coin.GOLD) == 2L, "la caisse devait être intacte");
                    helper.succeed();
                } finally {
                    essai.close();
                }
            });
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
    }

    /** Ni une trémie ni un dropper ne poussent de pièce dans l'étal ; les témoins, eux, passent. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unEntonnoirNePoussePasDePieceDansLEtal(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            BlockPos etal = new BlockPos(3, 0, 3);
            BlockPos entonnoir = new BlockPos(3, 1, 3);
            BlockPos etalDropper = new BlockPos(6, 0, 3);
            BlockPos dropper = new BlockPos(6, 1, 3);
            BlockPos etalTemoin = new BlockPos(6, 0, 5);
            BlockPos dropperTemoin = new BlockPos(6, 1, 5);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(etalDropper, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(etalTemoin, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(entonnoir, Blocks.HOPPER);
            BlockState bas = Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.DOWN);
            helper.setBlock(dropper, bas);
            helper.setBlock(dropperTemoin, bas);
            HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(entonnoir);
            // Posées sans case : le refus est celui de la voie automatique, pas d'une case.
            hopper.setItem(0, piece(5));
            hopper.setItem(1, new ItemStack(Items.DIRT, 3));
            hopper.setItem(2, boursePleine());
            ((DispenserBlockEntity) helper.getBlockEntity(dropper)).setItem(0, piece(5));
            ((DispenserBlockEntity) helper.getBlockEntity(dropperTemoin)).setItem(0, new ItemStack(Items.DIRT, 1));
            helper.setBlock(new BlockPos(6, 2, 3), Blocks.REDSTONE_BLOCK);
            helper.setBlock(new BlockPos(6, 2, 5), Blocks.REDSTONE_BLOCK);
            MarketStallBlockEntity be = entite(helper, etal);
            MarketStallBlockEntity beDropper = entite(helper, etalDropper);
            MarketStallBlockEntity beTemoin = entite(helper, etalTemoin);
            helper.runAfterDelay(100, () -> {
                try {
                    helper.assertTrue(compter(be.stock(), Items.DIRT) == 3, "témoin trémie : la terre devait passer, vu "
                            + compter(be.stock(), Items.DIRT));
                    helper.assertTrue(compter(beTemoin.stock(), Items.DIRT) == 1, "témoin dropper : la terre devait passer");
                    helper.assertTrue(argentDansLe(be.stock()) == 0 && argentDansLe(beDropper.stock()) == 0,
                            "une pièce est entrée dans l'étal");
                    helper.assertTrue(hopper.getItem(0).getCount() == 5 && hopper.getItem(0).is(Coin.GOLD.item())
                            && Purse.total(hopper.getItem(2)) == 10, "la trémie devait garder ses pièces et sa bourse");
                    ItemStack gardee = ((DispenserBlockEntity) helper.getBlockEntity(dropper)).getItem(0);
                    helper.assertTrue(gardee.getCount() == 5 && gardee.is(Coin.GOLD.item()),
                            "le dropper devait garder ses pièces : " + gardee);
                    helper.assertTrue(beDropper.stock().isEmpty(), "le stock de l'étal du dropper est resté vide");
                    helper.succeed();
                } finally {
                    essai.close();
                }
            });
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : caisse, casse, explosion et piston, rechargement, registre
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laCaisseDeposeEtRetireAValeurEgale(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_caisse");
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            donner(j, Coin.GOLD, 3);
            donner(j, Coin.SILVER, 70);
            donner(j, Coin.COPPER, 5);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.COPPER, 20);
            j.joueur().getInventory().add(bourse);
            long valeur = valeurPortee(j);
            helper.assertTrue(valeur == 3 * 100 + 70 * 10 + 5 + 20, "valeur de départ " + valeur);
            long circulation = registre.inCirculation();
            long transferts = registre.totalTransferred(MoneyTransfer.STAFF_WITHDRAW);
            BlockPos abs = helper.absolutePos(A);

            StallCashMenu menu = ouvrirCaisse(helper, j, A);
            helper.assertTrue(menu.usesPurses(), "la caisse prend aussi les bourses portées");
            helper.assertTrue(MoneyStorageRules.isAllowedMenu(menu), "la caisse est un rangement permis");
            helper.assertTrue(agir(j, StallCashPacket.depositAll(abs)) == Result.DONE, "dépôt");
            helper.assertTrue(valeurPortee(j) == 0 && be.cashValue() == valeur,
                    "tout devait passer à la caisse : porté " + valeurPortee(j) + ", caisse " + be.cashValue());
            helper.assertTrue(agir(j, StallCashPacket.depositAll(abs)) == Result.NOTHING, "rien à déposer");
            helper.assertTrue(agir(j, StallCashPacket.withdraw(abs, Coin.SILVER, 64)) == Result.DONE, "retrait de 64");
            helper.assertTrue(agir(j, StallCashPacket.withdraw(abs, Coin.GOLD, 1)) == Result.DONE, "retrait de 1");
            helper.assertTrue(agir(j, StallCashPacket.withdraw(abs, Coin.GOLD, 64)) == Result.INSUFFICIENT, "trop demander");
            helper.assertTrue(valeurPortee(j) + be.cashValue() == valeur, "valeur égale : " + valeurPortee(j) + " + "
                    + be.cashValue() + " pour " + valeur);
            helper.assertTrue(agir(j, StallCashPacket.groupAll(abs)) == Result.DONE, "regroupement");
            helper.assertTrue(valeurPortee(j) + be.cashValue() == valeur, "le regroupement garde la valeur");
            // Ni créé ni détruit, et le propriétaire n'est pas « l'équipe » : aucun transfert.
            helper.assertTrue(registre.inCirculation() == circulation, "le registre a bougé");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.STAFF_WITHDRAW) == transferts,
                    "un retrait du propriétaire n'est pas un transfert de l'équipe");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unAutreNeTouchePasALaCaisse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_cai_proprio");
            JoueurDeTest autre = essai.joueur("etal_cai_autre");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            be.addCash(Coin.COPPER, 5);
            donner(autre, Coin.GOLD, 2);
            BlockPos abs = helper.absolutePos(A);
            helper.assertTrue(agir(autre, StallCashPacket.withdraw(abs, Coin.COPPER, 1)) == Result.NOT_ALLOWED, "retrait par un autre");
            helper.assertTrue(agir(autre, StallCashPacket.depositAll(abs)) == Result.NOT_ALLOWED, "dépôt par un autre");
            helper.assertTrue(agir(autre, StallCashPacket.groupAll(abs)) == Result.NOT_ALLOWED, "regroupement par un autre");
            helper.assertTrue(be.cashValue() == 5 && porte(autre, Coin.COPPER) == 0 && porte(autre, Coin.GOLD) == 2,
                    "rien ne devait bouger");
            // Le propriétaire, lui, doit avoir l'écran ouvert : sans lui, même à portée, c'est refusé.
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(abs, Coin.COPPER, 1)) == Result.NOT_OPEN, "écran fermé");
            // Un bloc qui n'est pas un étal.
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(helper.absolutePos(B), Coin.COPPER, 1)) == Result.NO_SAFE,
                    "pas d'étal ici");
            // Demandes hors des boutons : refusées sans effet.
            ouvrirCaisse(helper, proprio, A);
            for (int montant : new int[] {0, -1, 2, 63, 65, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(proprio, new StallCashPacket(abs, new CoinRequest(CoinRequest.WITHDRAW, 0, montant, false)))
                        == Result.BAD_REQUEST, "montant " + montant);
            }
            helper.assertTrue(agir(proprio, new StallCashPacket(abs, null)) == Result.BAD_REQUEST, "demande absente");
            // Trop loin : refusé, et le chunk d'un point lointain n'est jamais chargé par le paquet.
            BlockPos horsMonde = new BlockPos(1_000_000, 64, 1_000_000);
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(horsMonde, Coin.COPPER, 1)) == Result.TOO_FAR, "chunk non chargé");
            helper.assertTrue(!helper.getLevel().isLoaded(horsMonde), "le paquet ne charge pas le chunk");
            proprio.joueur().moveTo(helper.absoluteVec(JOUEUR).add(40, 0, 0));
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(abs, Coin.COPPER, 1)) == Result.TOO_FAR, "trop loin");
            proprio.joueur().moveTo(helper.absoluteVec(JOUEUR));
            helper.assertTrue(be.cashValue() == 5, "la caisse est intacte");
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(abs, Coin.COPPER, 1)) == Result.DONE, "à portée, c'est permis");
            // Parti : rien ne se passe.
            ServerPlayer parti = proprio.joueur();
            proprio.quitter();
            helper.assertTrue(StallCashPacket.apply(parti, StallCashPacket.withdraw(abs, Coin.COPPER, 1)) == Result.GONE,
                    "retrait après le départ");
            helper.assertTrue(be.cashValue() == 4, "la caisse ne bouge plus");
            helper.succeed();
        }
    }

    private static void avecModes(GameTestHelper helper, JoueurDeTest j, Runnable action) {
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            j.joueur().gameMode.changeGameModeForPlayer(mode);
            action.run();
        }
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void pleinNeSeCassePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_plein_proprio");
            JoueurDeTest chef = essai.equipe("etal_plein_chef");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), abs) < 0, "dureté réservée (-1)");

            for (String plein : new String[] {"stock", "caisse", "les deux"}) {
                be.stock().setStackInSlot(0, ItemStack.EMPTY);
                be.removeCash(Coin.COPPER, be.cash().getOrDefault(Coin.COPPER, 0L));
                if (!plein.equals("caisse")) {
                    be.stock().setStackInSlot(0, new ItemStack(Items.BREAD, 1));
                }
                if (!plein.equals("stock")) {
                    be.addCash(Coin.COPPER, 1);
                }
                helper.assertTrue(!MarketStallBlock.isEmpty(be), "plein par " + plein);
                for (JoueurDeTest j : List.of(proprio, chef)) {
                    avecModes(helper, j, () -> {
                        String qui = j.joueur().getGameProfile().getName() + "/" + j.joueur().gameMode.getGameModeForPlayer()
                                + "/" + plein;
                        j.messagesRecus();
                        helper.assertTrue(etat.getDestroyProgress(j.joueur(), helper.getLevel(), abs) == 0.0F,
                                "pas de progression : " + qui);
                        helper.assertTrue(!j.joueur().gameMode.destroyBlock(abs)
                                && helper.getBlockState(A).is(EconomyRegistry.MARKET_STALL.get()),
                                "plein, il ne devait pas se casser : " + qui);
                        helper.assertTrue(j.messagesRecus().contains(MarketStallBlock.MESSAGE + "not_empty"),
                                "message « videz d'abord » attendu : " + qui);
                    });
                }
            }
            helper.assertTrue(objets(helper).isEmpty(), "rien ne devait tomber");
            helper.assertTrue(be.stock().getStackInSlot(0).is(Items.BREAD) && be.cashValue() == 1, "contenu intact");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void videSeCasseParLeProprietaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_vide_proprio");
            JoueurDeTest chef = essai.equipe("etal_vide_chef");
            poserEtVerifier(helper, proprio, A);
            poserEtVerifier(helper, proprio, B);
            BlockPos abs = helper.absolutePos(A);
            MarketStallBlockEntity be = entite(helper, A);
            // Plein puis vidé : le propriétaire le casse.
            be.stock().setStackInSlot(0, new ItemStack(Items.BREAD, 3));
            be.addCash(Coin.SILVER, 2);
            helper.assertTrue(!proprio.joueur().gameMode.destroyBlock(abs), "plein, non");
            be.stock().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(!proprio.joueur().gameMode.destroyBlock(abs), "stock vide mais caisse pleine, non");
            helper.assertTrue(be.removeCash(Coin.SILVER, 2), "vidage de la caisse");
            helper.assertTrue(MarketStallBlock.isEmpty(be), "vide");
            helper.assertTrue(helper.getBlockState(A).getDestroyProgress(proprio.joueur(), helper.getLevel(), abs) > 0.0F,
                    "progression, vide");
            helper.assertTrue(proprio.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(), "vide, il se casse");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1 && lache.get(0).getItem().is(EconomyRegistry.MARKET_STALL_ITEM.get()),
                    "seul le bloc tombe : " + lache);
            lache.forEach(Entity::discard);
            // L'équipe casse un étal vide aussi (elle vide d'abord un étal plein).
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(helper.absolutePos(B)) && helper.getBlockState(B).isAir(),
                    "l'équipe casse un étal vide");
            helper.assertTrue(objets(helper).size() == 1, "seul le bloc tombe, côté équipe");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unAutreNeLeCassePasMemeVide(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_autre_proprio");
            JoueurDeTest autre = essai.joueur("etal_autre_autre");
            poserEtVerifier(helper, proprio, A);
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(MarketStallBlock.isEmpty(entite(helper, A)), "l'étal est vide");
            avecModes(helper, autre, () -> {
                autre.messagesRecus();
                helper.assertTrue(etat.getDestroyProgress(autre.joueur(), helper.getLevel(), abs) == 0.0F,
                        "pas de progression pour un autre");
                helper.assertTrue(!autre.joueur().gameMode.destroyBlock(abs)
                        && helper.getBlockState(A).is(EconomyRegistry.MARKET_STALL.get()),
                        "un autre ne le casse pas, même vide, " + autre.joueur().gameMode.getGameModeForPlayer());
                helper.assertTrue(autre.messagesRecus().contains(MarketStallBlock.MESSAGE + "not_yours"), "message attendu");
            });
            helper.assertTrue(objets(helper).isEmpty(), "rien ne devait tomber");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 100)
    public static void explosionEtPistonSansEffet(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            BlockPos etal = new BlockPos(3, 0, 3);
            BlockPos temoin = new BlockPos(2, 0, 3);
            BlockPos centre = new BlockPos(2, 0, 4);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(temoin, Blocks.DIRT); // prouve que les rayons atteignent le côté de l'étal
            entite(helper, etal).stock().setStackInSlot(0, new ItemStack(Items.BREAD, 4));
            BlockState etat = helper.getBlockState(etal);
            helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE),
                    "le Wither et le dragon ne le cassent pas (tags)");
            helper.assertTrue(etat.getPistonPushReaction() == PushReaction.BLOCK, "le piston est bloqué");
            helper.assertTrue(etat.is(BlockTags.MINEABLE_WITH_AXE), "se mine à la hache");
            Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
            helper.getLevel().explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
            helper.assertTrue(helper.getBlockState(etal).is(EconomyRegistry.MARKET_STALL.get())
                    && helper.getBlockEntity(etal) instanceof MarketStallBlockEntity be
                    && be.stock().getStackInSlot(0).getCount() == 4, "l'étal devait résister, contenu compris");

            // Le piston : un piston témoin pousse la terre, celui qui fait face à l'étal ne le pousse pas.
            BlockPos piston = new BlockPos(1, 0, 6);
            BlockPos etalPiston = new BlockPos(2, 0, 6);
            BlockPos pistonTemoin = new BlockPos(1, 0, 5);
            BlockPos terre = new BlockPos(2, 0, 5);
            helper.setBlock(etalPiston, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(terre, Blocks.DIRT);
            helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
            helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
            helper.setBlock(new BlockPos(1, 1, 6), Blocks.REDSTONE_BLOCK);
            helper.setBlock(new BlockPos(1, 1, 5), Blocks.REDSTONE_BLOCK);
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 5)).is(Blocks.DIRT),
                            "le piston témoin devait pousser la terre"))
                    .thenExecute(() -> {
                        try {
                            helper.assertTrue(helper.getBlockState(etalPiston).is(EconomyRegistry.MARKET_STALL.get()),
                                    "l'étal ne devait pas bouger");
                            helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                                    "le piston bloqué ne devait pas s'étendre");
                        } finally {
                            essai.close();
                        }
                    })
                    .thenSucceed();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void contenuEtCaisseSurviventAuRechargement(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_recharge");
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            ItemStack nomme = new ItemStack(Items.DIAMOND_SWORD);
            nomme.setHoverName(net.minecraft.network.chat.Component.literal("Lame de démonstration"));
            nomme.getOrCreateTag().putInt("Damage", 7);
            be.stock().setStackInSlot(0, nomme.copy());
            be.stock().setStackInSlot(26, new ItemStack(Items.BREAD, 33));
            be.addCash(Coin.GOLD, 3);
            be.addCash(Coin.RUBY, 5_000_000_000L);
            be.ledger().add(new StallLedger.Line(42L, j.uuid(), "etal_recharge", Kind.CASH_IN, "", 0, 123L));
            be.bumpVersion();
            BlockPos abs = helper.absolutePos(A);

            // 1) Une copie neuve relit le NBT du bloc.
            CompoundTag tag = be.saveWithoutMetadata();
            MarketStallBlockEntity copie = new MarketStallBlockEntity(abs, helper.getBlockState(A));
            copie.load(tag);
            verifierRelu(helper, copie, be, nomme, j.uuid());

            // 2) Le chemin réel d'une sauvegarde de chunk : écrit comme le serveur l'écrit, relu comme il le relit.
            LevelChunk chunk = helper.getLevel().getChunkAt(abs);
            CompoundTag chunkTag = ChunkSerializer.write(helper.getLevel(), chunk);
            ListTag entites = chunkTag.getList("block_entities", 10);
            CompoundTag trouve = null;
            for (int i = 0; i < entites.size(); i++) {
                CompoundTag candidate = entites.getCompound(i);
                if (candidate.getInt("x") == abs.getX() && candidate.getInt("y") == abs.getY()
                        && candidate.getInt("z") == abs.getZ()) {
                    trouve = candidate;
                }
            }
            helper.assertTrue(trouve != null, "l'étal n'est pas dans le chunk écrit");
            BlockEntity relu = BlockEntity.loadStatic(abs, helper.getBlockState(A), trouve);
            helper.assertTrue(relu instanceof MarketStallBlockEntity, "l'entité relue du chunk : " + relu);
            verifierRelu(helper, (MarketStallBlockEntity) relu, be, nomme, j.uuid());

            // 3) Un fichier abîmé ne fait ni planter ni gagner de pièces.
            CompoundTag abime = be.saveWithoutMetadata();
            CompoundTag caisse = abime.getCompound("Cash");
            caisse.putLong(Coin.COPPER.key(), -50L);
            caisse.putLong(Coin.SILVER.key(), Long.MAX_VALUE);
            abime.put("Cash", caisse);
            abime.put("Stock", new CompoundTag());
            MarketStallBlockEntity bancal = new MarketStallBlockEntity(abs, helper.getBlockState(A));
            bancal.load(abime);
            helper.assertTrue(bancal.cash().getOrDefault(Coin.COPPER, 0L) == 0L, "une caisse négative vaut zéro");
            helper.assertTrue(bancal.cash().getOrDefault(Coin.SILVER, 0L) <= dev.oneforallsurvival.economy.CoinStore.MAX_PER_COIN,
                    "au plus le plafond : " + bancal.cash());
            helper.assertTrue(bancal.stock().getSlots() == StallStockHandler.SIZE, "toujours 27 cases");
            helper.succeed();
        }
    }

    private static void verifierRelu(GameTestHelper helper, MarketStallBlockEntity relu, MarketStallBlockEntity origine,
            ItemStack nomme, UUID proprio) {
        helper.assertTrue(proprio.equals(relu.owner()) && "etal_recharge".equals(relu.ownerName()), "propriétaire relu");
        helper.assertTrue(ItemStack.matches(relu.stock().getStackInSlot(0), nomme), "objet nommé relu : " + relu.stock().getStackInSlot(0));
        helper.assertTrue(relu.stock().getStackInSlot(26).getCount() == 33 && relu.stock().getStackInSlot(26).is(Items.BREAD),
                "dernière case relue");
        helper.assertTrue(relu.cash().equals(origine.cash()), "caisse relue : " + relu.cash());
        helper.assertTrue(relu.cashValue() == origine.cashValue(), "valeur relue");
        helper.assertTrue(relu.ledger().size() == 1 && relu.ledger().page(0).get(0).amount() == 123L, "registre relu");
        helper.assertTrue(relu.version() == origine.version() && origine.version() == 1, "version relue : " + relu.version());
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leRegistreNoteRetraitsEtDepots(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_reg_proprio");
            JoueurDeTest chef = essai.equipe("etal_reg_chef");
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            BlockPos abs = helper.absolutePos(A);
            ServerPlayer p = proprio.joueur();

            // Le propriétaire pose 10 terres, puis les reprend (maj-clic).
            clicDroit(helper, proprio, A);
            StallOwnerMenu menu = menuGestion(helper, proprio);
            menu.setCarried(new ItemStack(Items.DIRT, 10));
            menu.clicked(StallOwnerMenu.STOCK_START, 0, ClickType.PICKUP, p);
            helper.assertTrue(compter(be.stock(), Items.DIRT) == 10, "10 terres posées");
            menu.clicked(StallOwnerMenu.STOCK_START, 0, ClickType.QUICK_MOVE, p);
            helper.assertTrue(be.stock().isEmpty(), "10 terres reprises");
            // Il dépose 3 or, puis en retire 1.
            donner(proprio, Coin.GOLD, 3);
            helper.assertTrue(menu.clickMenuButton(p, StallOwnerMenu.BUTTON_CASH) && caisse(proprio), "caisse");
            helper.assertTrue(agir(proprio, StallCashPacket.depositAll(abs)) == Result.DONE, "dépôt");
            helper.assertTrue(agir(proprio, StallCashPacket.withdraw(abs, Coin.GOLD, 1)) == Result.DONE, "retrait");
            // Rien qui ne change pas la valeur n'est noté : regrouper, casser.
            helper.assertTrue(agir(proprio, StallCashPacket.groupAll(abs)) == Result.NOTHING, "rien à regrouper");

            // L'équipe agit sur l'étal d'un autre : tout est une ligne STAFF, signée, et un retrait est un transfert.
            be.stock().setStackInSlot(0, new ItemStack(Items.DIRT, 4));
            clicDroit(helper, chef, A);
            StallOwnerMenu menuChef = menuGestion(helper, chef);
            menuChef.clicked(StallOwnerMenu.STOCK_START, 0, ClickType.QUICK_MOVE, chef.joueur());
            menuChef.setCarried(new ItemStack(Items.DIRT, 2));
            menuChef.clicked(StallOwnerMenu.STOCK_START + 1, 0, ClickType.PICKUP, chef.joueur());
            helper.assertTrue(menuChef.clickMenuButton(chef.joueur(), StallOwnerMenu.BUTTON_CASH) && caisse(chef), "caisse de l'équipe");
            long transferts = global.totalTransferred(MoneyTransfer.STAFF_WITHDRAW);
            long circulation = global.inCirculation();
            helper.assertTrue(agir(chef, StallCashPacket.withdraw(abs, Coin.GOLD, 1)) == Result.DONE, "retrait de l'équipe");
            helper.assertTrue(global.totalTransferred(MoneyTransfer.STAFF_WITHDRAW) - transferts == 100L,
                    "le retrait de l'équipe est un transfert de 100");
            helper.assertTrue(global.inCirculation() == circulation, "aucune pièce créée ni détruite");

            List<StallLedger.Line> lignes = be.ledger().page(0);
            helper.assertTrue(lignes.size() == 7, "7 lignes attendues, vu " + lignes.size() + " : " + lignes);
            verifierLigne(helper, lignes.get(0), Kind.STAFF, chef.uuid(), "", 0, -100L);
            verifierLigne(helper, lignes.get(1), Kind.STAFF, chef.uuid(), "minecraft:dirt", 2, 0L);
            verifierLigne(helper, lignes.get(2), Kind.STAFF, chef.uuid(), "minecraft:dirt", -4, 0L);
            verifierLigne(helper, lignes.get(3), Kind.CASH_OUT, proprio.uuid(), "", 0, 100L);
            verifierLigne(helper, lignes.get(4), Kind.CASH_IN, proprio.uuid(), "", 0, 300L);
            verifierLigne(helper, lignes.get(5), Kind.GOODS_OUT, proprio.uuid(), "minecraft:dirt", 10, 0L);
            verifierLigne(helper, lignes.get(6), Kind.GOODS_IN, proprio.uuid(), "minecraft:dirt", 10, 0L);
            helper.assertTrue("etal_reg_chef".equals(lignes.get(0).actorName()) && lignes.get(0).epochMillis() > 0L,
                    "nom et date de la ligne");
            helper.succeed();
        }
    }

    private static void verifierLigne(GameTestHelper helper, StallLedger.Line ligne, Kind genre, UUID acteur, String objet,
            int nombre, long montant) {
        helper.assertTrue(ligne.kind() == genre && ligne.actor().equals(acteur) && ligne.itemId().equals(objet)
                && ligne.count() == nombre && ligne.amount() == montant,
                "ligne attendue " + genre + " " + objet + " x" + nombre + " " + montant + ", vue " + ligne);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : la vue exposée, les menus, le client
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laCapaciteExposeeNeRendRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            BlockPos etal = new BlockPos(3, 0, 3);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            MarketStallBlockEntity be = entite(helper, etal);
            be.stock().setStackInSlot(0, new ItemStack(Items.DIRT, 64));
            be.stock().setStackInSlot(5, new ItemStack(Items.BREAD, 7));
            be.addCash(Coin.GOLD, 4);
            List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
            faces.add(null);
            for (Direction face : faces) {
                IItemHandler vue = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
                helper.assertTrue(vue != null, "pas de capacité d'objets sur la face " + face);
                helper.assertTrue(vue != be.stock(), "la capacité est la vue, pas le stock lui-même (" + face + ")");
                helper.assertTrue(vue.getSlots() == 27, "27 cases vues");
                for (int i = 0; i < vue.getSlots(); i++) {
                    helper.assertTrue(vue.extractItem(i, 64, true).isEmpty() && vue.extractItem(i, 64, false).isEmpty(),
                            "extraction réussie, case " + i + " face " + face);
                }
                helper.assertTrue(vue.getStackInSlot(0).getCount() == 64, "la vue montre le contenu");
                vue.getStackInSlot(0).shrink(60);
                helper.assertTrue(be.stock().getStackInSlot(0).getCount() == 64, "lire la vue ne donne pas de quoi modifier le stock");
                helper.assertTrue(!vue.isItemValid(0, piece(1)) && vue.isItemValid(0, new ItemStack(Items.DIRT)), "validité");
            }
            helper.assertTrue(compter(be.stock(), Items.DIRT) == 64 && compter(be.stock(), Items.BREAD) == 7 && be.cashValue() == 400,
                    "rien n'a bougé");
            // Insérer passe : la vue sert bien à garnir.
            IItemHandler vue = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(AssertionError::new);
            helper.assertTrue(vue.insertItem(1, new ItemStack(Items.APPLE, 2), false).isEmpty() && compter(be.stock(), Items.APPLE) == 2,
                    "témoin : la vue reçoit");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laCaisseNEstExposeeNullePart(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            BlockPos etal = new BlockPos(3, 0, 3);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            MarketStallBlockEntity be = entite(helper, etal);
            be.addCash(Coin.RUBY, 2);
            helper.assertTrue(!(helper.getBlockEntity(etal) instanceof Container), "l'étal n'est pas un Container vanilla");
            helper.assertTrue(!(helper.getBlockEntity(etal) instanceof net.minecraft.world.WorldlyContainer), "ni un WorldlyContainer");
            for (Direction face : Direction.values()) {
                helper.assertTrue(!be.getCapability(ForgeCapabilities.ENERGY, face).isPresent()
                        && !be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).isPresent(), "aucune autre capacité");
                IItemHandler vue = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElseThrow(AssertionError::new);
                for (int i = 0; i < vue.getSlots(); i++) {
                    helper.assertTrue(!MoneyStorageRules.isMoney(vue.getStackInSlot(i)), "aucune pièce visible dans la vue");
                }
            }
            helper.assertTrue(be.cashValue() == CoinMath.valueOf(Coin.RUBY, 2), "la caisse est intacte");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void lesMenusPortentLes36CasesEtLeClientSuit(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_client");
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            be.stock().setStackInSlot(2, new ItemStack(Items.BREAD, 9));
            donner(j, Coin.GOLD, 3);
            clicDroit(helper, j, A);
            StallOwnerMenu serveur = menuGestion(helper, j);
            helper.assertTrue(serveur.slots.size() == StallOwnerMenu.SLOT_COUNT && StallOwnerMenu.SLOT_COUNT == 27 + 36,
                    "27 cases de stock et les 36 du joueur : " + serveur.slots.size());
            FriendlyByteBuf tampon = new FriendlyByteBuf(Unpooled.buffer());
            tampon.writeBlockPos(helper.absolutePos(A));
            StallOwnerMenu client = StallOwnerMenu.fromNetwork(serveur.containerId, new Inventory(j.joueur()), tampon);
            j.synchroniser(client);
            helper.assertTrue(client.getSlot(StallOwnerMenu.STOCK_START + 2).getItem().is(Items.BREAD)
                    && client.getSlot(StallOwnerMenu.STOCK_START + 2).getItem().getCount() == 9, "le client voit le stock");
            helper.assertTrue(client.pos().equals(helper.absolutePos(A)), "le client connaît la position");

            // La caisse : mêmes 36 cases, et l'inventaire du client suit le dépôt sans fermer l'écran.
            helper.assertTrue(serveur.clickMenuButton(j.joueur(), StallOwnerMenu.BUTTON_CASH) && caisse(j), "caisse");
            StallCashMenu caisse = (StallCashMenu) j.joueur().containerMenu;
            helper.assertTrue(caisse.slots.size() == 36, "la caisse porte les 36 cases du joueur : " + caisse.slots.size());
            tampon = new FriendlyByteBuf(Unpooled.buffer());
            tampon.writeBlockPos(helper.absolutePos(A));
            StallCashMenu clientCaisse = StallCashMenu.fromNetwork(caisse.containerId, new Inventory(j.joueur()), tampon);
            j.synchroniser(clientCaisse);
            helper.assertTrue(clientCaisse.carried(Coin.GOLD) == 3, "le client voit l'or porté");
            j.envoyer(new StallCashPacket(helper.absolutePos(A), CoinRequest.depositAll()));
            j.synchroniser(clientCaisse);
            helper.assertTrue(clientCaisse.carried(Coin.GOLD) == 0 && clientCaisse.count(Coin.GOLD) == 3 && clientCaisse.total() == 300,
                    "après le dépôt : porté " + clientCaisse.carried(Coin.GOLD) + ", caisse " + clientCaisse.count(Coin.GOLD));
            // Retour au stock par le bouton de la caisse.
            helper.assertTrue(caisse.clickMenuButton(j.joueur(), StallCashMenu.BUTTON_STOCK) && gestion(j), "retour au stock");
            // La gestion se ferme si l'étal disparaît ou si le joueur s'éloigne.
            StallOwnerMenu menu = menuGestion(helper, j);
            helper.assertTrue(menu.stillValid(j.joueur()), "valide à portée");
            j.joueur().moveTo(helper.absoluteVec(JOUEUR).add(40, 0, 0));
            helper.assertTrue(!menu.stillValid(j.joueur()), "plus valide trop loin");
            j.joueur().moveTo(helper.absoluteVec(JOUEUR));
            helper.setBlock(A, Blocks.AIR);
            helper.assertTrue(!menu.stillValid(j.joueur()), "plus valide sans l'étal");
            helper.succeed();
        }
    }

    /** Remplacer une pile par une autre de même identifiant mais de contenu différent s'écrit au registre. */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void echangerDeuxPilesDeMemeIdentifiantSEcrit(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("etal_nbt_proprio");
            JoueurDeTest chef = essai.equipe("etal_nbt_chef");
            poserEtVerifier(helper, proprio, A);
            MarketStallBlockEntity be = entite(helper, A);
            ItemStack lameA = new ItemStack(Items.DIAMOND_SWORD);
            lameA.setHoverName(net.minecraft.network.chat.Component.literal("Lame A"));
            ItemStack lameB = new ItemStack(Items.DIAMOND_SWORD);
            lameB.setHoverName(net.minecraft.network.chat.Component.literal("Lame B"));
            be.stock().setStackInSlot(0, lameA.copy());
            for (JoueurDeTest j : List.of(proprio, chef)) {
                clicDroit(helper, j, A);
                StallOwnerMenu menu = menuGestion(helper, j);
                int avant = be.ledger().size();
                menu.setCarried(be.stock().getStackInSlot(0).is(Items.DIAMOND_SWORD)
                        && be.stock().getStackInSlot(0).getHoverName().getString().equals("Lame A") ? lameB.copy() : lameA.copy());
                menu.clicked(StallOwnerMenu.STOCK_START, 0, ClickType.PICKUP, j.joueur());
                helper.assertTrue(be.ledger().size() - avant == 2, "deux lignes attendues (sortie et entrée), vu "
                        + (be.ledger().size() - avant) + " : " + be.ledger().page(0));
                List<StallLedger.Line> lignes = be.ledger().page(0).subList(0, 2);
                boolean proprietaire = j == proprio;
                helper.assertTrue(lignes.stream().allMatch(l -> l.itemId().equals("minecraft:diamond_sword")
                        && l.actor().equals(j.uuid())), "objet et acteur : " + lignes);
                if (proprietaire) {
                    helper.assertTrue(lignes.stream().anyMatch(l -> l.kind() == Kind.GOODS_OUT && l.count() == 1)
                            && lignes.stream().anyMatch(l -> l.kind() == Kind.GOODS_IN && l.count() == 1), "sortie et entrée : " + lignes);
                } else {
                    helper.assertTrue(lignes.stream().allMatch(l -> l.kind() == Kind.STAFF)
                            && lignes.stream().anyMatch(l -> l.count() == -1) && lignes.stream().anyMatch(l -> l.count() == 1),
                            "STAFF signé : " + lignes);
                }
                j.joueur().closeContainer();
                j.joueur().getInventory().clearContent();
            }
            helper.succeed();
        }
    }

    /** Un stock relu ne garde pas d'argent : la valeur passe à la caisse, rien n'est détruit ni dupliqué. */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void unStockRelaVecDeLArgentLeRangeALaCaisse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("etal_relu_argent");
            poserEtVerifier(helper, j, A);
            MarketStallBlockEntity be = entite(helper, A);
            BlockPos abs = helper.absolutePos(A);
            be.addCash(Coin.SILVER, 2);
            be.stock().setStackInSlot(0, piece(5));
            be.stock().setStackInSlot(1, boursePleine());
            be.stock().setStackInSlot(2, new ItemStack(Items.BREAD, 3));
            long valeur = CoinMath.valueOf(Coin.SILVER, 2) + CoinMath.valueOf(Coin.GOLD, 5) + CoinMath.valueOf(Coin.COPPER, 10);
            MarketStallBlockEntity relu = new MarketStallBlockEntity(abs, helper.getBlockState(A));
            relu.load(be.saveWithoutMetadata());
            helper.assertTrue(argentDansLe(relu.stock()) == 0 && relu.stock().getStackInSlot(0).isEmpty(), "plus de pièce dans le stock");
            helper.assertTrue(Purse.total(relu.stock().getStackInSlot(1)) == 0L && !MoneyStorageRules.isMoney(relu.stock().getStackInSlot(1)),
                    "la bourse reste, vide");
            helper.assertTrue(relu.stock().getStackInSlot(2).getCount() == 3, "le pain est resté");
            helper.assertTrue(relu.cashValue() == valeur && relu.cashCount(Coin.GOLD) == 5 && relu.cashCount(Coin.COPPER) == 10,
                    "valeur conservée : " + relu.cashValue() + " pour " + valeur);
            helper.succeed();
        }
    }

    /**
     * La forme du bloc est exactement l'union des boîtes de son modèle (celles de {@code market_stall.json}, tournées
     * comme son état d'orientation), pour les quatre orientations ; ce n'est pas le cube plein, le bloc ne couvre
     * donc pas l'œil ({@code noOcclusion}) : un bloc plein posé contre lui garde sa face tournée vers lui.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laFormeSuitLeModele(GameTestHelper helper) throws java.io.IOException {
        com.google.gson.JsonObject modele;
        try (var in = EtalStockGameTests.class.getResourceAsStream("/assets/oneforallsurvival/models/block/market_stall.json")) {
            helper.assertTrue(in != null, "modèle de bloc absent");
            modele = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        com.google.gson.JsonArray elements = modele.getAsJsonArray("elements");
        helper.assertTrue(elements.size() >= 6, "comptoir, montants et auvent attendus : " + elements.size() + " éléments");
        net.minecraft.world.phys.shapes.VoxelShape cube = net.minecraft.world.phys.shapes.Shapes.block();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // Le blockstate tourne le modèle (tourné vers le nord) de 0, 90, 180, 270 degrés.
            int quarts = switch (facing) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                default -> 3;
            };
            net.minecraft.world.phys.shapes.VoxelShape attendue = net.minecraft.world.phys.shapes.Shapes.empty();
            for (com.google.gson.JsonElement e : elements) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                helper.assertTrue(!o.has("rotation"), "un élément tourné n'a pas de boîte alignée");
                double[] de = {o.getAsJsonArray("from").get(0).getAsDouble(), o.getAsJsonArray("from").get(1).getAsDouble(),
                        o.getAsJsonArray("from").get(2).getAsDouble()};
                double[] a = {o.getAsJsonArray("to").get(0).getAsDouble(), o.getAsJsonArray("to").get(1).getAsDouble(),
                        o.getAsJsonArray("to").get(2).getAsDouble()};
                helper.assertTrue(de[0] >= 0 && de[1] >= 0 && de[2] >= 0 && a[0] <= 16 && a[1] <= 16 && a[2] <= 16,
                        "un élément sort du cube : " + o);
                double x0 = de[0];
                double z0 = de[2];
                double x1 = a[0];
                double z1 = a[2];
                for (int i = 0; i < quarts; i++) {
                    // Un quart de tour dans le sens des aiguilles d'une montre vu d'en haut : (x, z) -> (16 - z, x).
                    double nx0 = 16 - z1;
                    double nx1 = 16 - z0;
                    double nz0 = x0;
                    double nz1 = x1;
                    x0 = nx0;
                    x1 = nx1;
                    z0 = nz0;
                    z1 = nz1;
                }
                attendue = net.minecraft.world.phys.shapes.Shapes.or(attendue,
                        Block.box(x0, de[1], z0, x1, a[1], z1));
            }
            BlockState etat = EconomyRegistry.MARKET_STALL.get().defaultBlockState().setValue(MarketStallBlock.FACING, facing);
            net.minecraft.world.phys.shapes.VoxelShape vue = etat.getShape(helper.getLevel(), helper.absolutePos(A));
            helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(attendue, vue,
                    net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME), facing + " : la forme n'est pas l'union des boîtes du modèle");
            helper.assertTrue(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(cube, vue,
                    net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST), facing + " : la forme ne doit pas être le cube plein");
            helper.assertTrue(!etat.canOcclude() && !etat.isSolidRender(helper.getLevel(), helper.absolutePos(A)),
                    facing + " : le bloc doit être sans occlusion (noOcclusion)");
        }
        // Un bloc plein à côté garde sa face tournée vers l'étal, le voisin ne la cache pas.
        helper.setBlock(A, EconomyRegistry.MARKET_STALL.get());
        helper.setBlock(A.west(), Blocks.STONE);
        BlockState pierre = helper.getBlockState(A.west());
        helper.assertTrue(Block.shouldRenderFace(pierre, helper.getLevel(), helper.absolutePos(A.west()), Direction.EAST,
                helper.absolutePos(A)), "la pierre voisine doit garder sa face tournée vers l'étal");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Avec le pack : un entonnoir de Create
    // ---------------------------------------------------------------------------------------------

    /**
     * Un entonnoir de Create posé contre l'étal, en extraction : il ne vide rien. Le témoin (le même entonnoir contre
     * un coffre) lâche la terre au sol, ce qui prouve que l'entonnoir fonctionne dans ce montage.
     */
    @GameTest(template = "test/plancher", batch = LOT_PACK, timeoutTicks = 300)
    public static void unEntonnoirDeCreateNeVidePasLEtal(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        Essai essai = new Essai(helper);
        try {
            Block funnel = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "andesite_funnel"));
            helper.assertTrue(funnel != null && funnel != Blocks.AIR, "create:andesite_funnel est introuvable");
            BlockPos etal = new BlockPos(3, 0, 3);
            BlockPos coffre = new BlockPos(5, 0, 3);
            helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
            helper.setBlock(coffre, Blocks.CHEST);
            helper.setBlock(new BlockPos(3, 0, 2), entonnoirVers(funnel, Direction.NORTH));
            helper.setBlock(new BlockPos(5, 0, 2), entonnoirVers(funnel, Direction.NORTH));
            MarketStallBlockEntity be = entite(helper, etal);
            be.stock().setStackInSlot(0, new ItemStack(Items.DIRT, 5));
            be.stock().setStackInSlot(1, new ItemStack(Items.BREAD, 3));
            be.addCash(Coin.COPPER, 7);
            ((Container) helper.getBlockEntity(coffre)).setItem(0, new ItemStack(Items.DIRT, 5));
            helper.runAfterDelay(120, () -> {
                try {
                    int auSolEtal = 0;
                    int auSolCoffre = 0;
                    for (ItemEntity e : objets(helper)) {
                        Vec3 rel = e.position().subtract(helper.absoluteVec(Vec3.ZERO));
                        if (rel.x < 4.0D) {
                            auSolEtal += e.getItem().getCount();
                        } else {
                            auSolCoffre += e.getItem().getCount();
                        }
                    }
                    helper.assertTrue(auSolCoffre > 0 || !((Container) helper.getBlockEntity(coffre)).getItem(0).is(Items.DIRT)
                            || ((Container) helper.getBlockEntity(coffre)).getItem(0).getCount() < 5,
                            "témoin : l'entonnoir devait vider le coffre (il ne fonctionne pas dans ce montage)");
                    helper.assertTrue(auSolEtal == 0, "des objets de l'étal sont tombés au sol : " + auSolEtal);
                    helper.assertTrue(compter(be.stock(), Items.DIRT) == 5 && compter(be.stock(), Items.BREAD) == 3,
                            "le stock devait être intact");
                    helper.assertTrue(be.cash().get(Coin.COPPER) == 7L, "la caisse devait être intacte");
                    helper.succeed();
                } finally {
                    essai.close();
                }
            });
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
    }

    /** L'entonnoir de Create contre le bloc au sud de lui, tourné vers {@code face}, en extraction. */
    private static BlockState entonnoirVers(Block funnel, Direction face) {
        BlockState etat = funnel.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> propriete : etat.getProperties()) {
            if (propriete.getName().equals("facing") && propriete.getValueClass() == Direction.class) {
                etat = withDirection(etat, propriete, face);
            } else if (propriete instanceof BooleanProperty booleenne && propriete.getName().equals("extracting")) {
                etat = etat.setValue(booleenne, true);
            }
        }
        return etat;
    }

    @SuppressWarnings("unchecked")
    private static BlockState withDirection(BlockState etat, net.minecraft.world.level.block.state.properties.Property<?> propriete,
            Direction face) {
        return etat.setValue((net.minecraft.world.level.block.state.properties.Property<Direction>) propriete, face);
    }
}
