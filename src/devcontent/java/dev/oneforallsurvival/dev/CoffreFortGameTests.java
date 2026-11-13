package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyGuard;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeActionPacket;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.SafeBlock;
import dev.oneforallsurvival.economy.SafeBlockEntity;
import dev.oneforallsurvival.economy.SafeMenu;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le coffre-fort personnel : pose (un par joueur), droits d'ouverture, dépôt, retrait, regroupement, casse,
 * persistance et paquet. L'écran lui-même n'est pas joué ici : le test porte sur ce que le serveur fait d'un paquet
 * (bornes, distance, droits, atomicité) et sur ce qu'il envoie au client.
 *
 * <p>Chaque test a son nom de joueur d'essai et ses comptes, rangés en fin de test par {@link Essai}. Les lots
 * tournent l'un après l'autre : le registre et la masse des coffres-forts, qui sont globaux, ne se lisent que dans un
 * test synchrone (sans tic entre la lecture avant et la lecture après), et la masse seule dans son lot.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CoffreFortGameTests {

    private static final String LOT_A = "coffre_fort_a";
    private static final String LOT_B = "coffre_fort_b";
    private static final String LOT_C = "coffre_fort_c";
    private static final String LOT_MASSE = "coffre_fort_masse";
    private static final String LOT_BOURSE = "coffre_fort_bourse";
    private static final String LOT_ECRAN = "coffre_fort_ecran";

    /** Où le coffre-fort se pose (case d'air ; le sol est un bloc plus bas), loin du joueur. */
    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(1, 0, 6);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    private CoffreFortGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Une aire close : sol, pourtour, plafond, lumière, vidée de tout ce qui n'est pas un joueur. */
    private static void boite(GameTestHelper helper) {
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
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> comptes = new ArrayList<>();
        private final List<com.mojang.authlib.GameProfile> equipe = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        SafeAccounts comptes() {
            return SafeAccounts.get(serveur());
        }

        /** Un joueur de survie, au milieu de l'aire. */
        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            comptes.add(j.uuid());
            return j;
        }

        /** Un joueur de l'équipe (niveau 2) : opérateur, jusqu'à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = joueur(nom);
            // Le niveau 4 est écrit à la main : le serveur d'essai donne 0 aux opérateurs qu'il crée par op().
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        UUID compteLibre() {
            UUID uuid = UUID.randomUUID();
            comptes.add(uuid);
            return uuid;
        }

        @Override
        public void close() {
            for (com.mojang.authlib.GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            // Les blocs d'abord (leur retrait efface l'emplacement noté), puis les comptes.
            boite(helper);
            SafeAccounts accounts = comptes();
            for (UUID uuid : comptes) {
                for (Coin coin : Coin.values()) {
                    accounts.withdraw(uuid, coin, accounts.count(uuid, coin));
                }
                accounts.forgetSafePos(uuid);
            }
        }
    }

    /** Pose un coffre-fort comme le ferait le joueur : l'objet en main, un clic sur le sol sous {@code pos}. */
    private static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        ItemStack pile = new ItemStack(EconomyRegistry.SAFE_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    /** Un coffre-fort posé par {@code j}, vérifié. */
    private static void poserEtVerifier(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        InteractionResult r = poser(helper, j, pos);
        helper.assertTrue(r.consumesAction() && helper.getBlockState(pos).is(EconomyRegistry.SAFE.get()),
                "le coffre-fort devait se poser : " + r + ", vu " + helper.getBlockState(pos));
    }

    private static SafeBlockEntity entite(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getBlockEntity(pos) instanceof SafeBlockEntity, "pas de bloc d'entité en " + pos);
        return (SafeBlockEntity) helper.getBlockEntity(pos);
    }

    /** Un clic droit réel, par le jeu : la garde de l'argent, puis le bloc. */
    private static void clicDroit(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    private static boolean ouvert(JoueurDeTest j) {
        return j.joueur().containerMenu instanceof SafeMenu;
    }

    private static void fermer(JoueurDeTest j) {
        j.joueur().closeContainer();
    }

    private static long porte(JoueurDeTest j, Coin coin) {
        return PlayerCoins.held(j.joueur()).getOrDefault(coin, 0L);
    }

    private static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static void donner(JoueurDeTest j, Coin coin, int nombre) {
        int reste = nombre;
        while (reste > 0) {
            int taille = Math.min(reste, 64);
            j.joueur().getInventory().add(new ItemStack(coin.item(), taille));
            reste -= taille;
        }
    }

    private static void remplirDeTerre(JoueurDeTest j) {
        for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
            j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static Result agir(JoueurDeTest j, SafeActionPacket paquet) {
        return SafeActionPacket.apply(j.joueur(), paquet);
    }

    private static long creeMoinsDetruit(EconomyLedger registre) {
        return registre.inCirculation();
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(new BlockPos(0, -1, 0)), helper.absolutePos(new BlockPos(8, 5, 8))));
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : pose, droits, dépôt, retrait, casse
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unSeulParJoueur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_un_seul");
            SafeAccounts comptes = essai.comptes();
            ServerLevel level = helper.getLevel();

            poserEtVerifier(helper, j, A);
            helper.assertTrue(j.uuid().equals(entite(helper, A).owner()), "le propriétaire n'est pas noté");
            helper.assertTrue(comptes.safePos(j.uuid()).equals(java.util.Optional.of(GlobalPos.of(level.dimension(),
                    helper.absolutePos(A)))), "l'emplacement n'est pas noté : " + comptes.safePos(j.uuid()));

            // Un second est refusé, avec un message.
            j.messagesRecus();
            InteractionResult second = poser(helper, j, B);
            helper.assertTrue(!second.consumesAction() && helper.getBlockState(B).isAir(),
                    "le second ne devait pas se poser : " + second);
            helper.assertTrue(j.messagesRecus().contains(SafeBlock.MESSAGE + "has_one"), "message de refus attendu");

            // Sans joueur (distributeur) et par un faux joueur (automate) : refusé.
            ItemStack pile = new ItemStack(EconomyRegistry.SAFE_ITEM.get());
            InteractionResult distributeur = ((BlockItem) pile.getItem())
                    .place(new DirectionalPlaceContext(level, helper.absolutePos(B), Direction.DOWN, pile, Direction.UP));
            helper.assertTrue(!distributeur.consumesAction() && helper.getBlockState(B).isAir(),
                    "sans joueur, rien ne devait se poser : " + distributeur);
            FakePlayer faux = FakePlayerFactory.getMinecraft(level);
            BlockPos sol = helper.absolutePos(B.below());
            InteractionResult automate = pile.useOn(new UseOnContext(level, faux, InteractionHand.MAIN_HAND, pile,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
            helper.assertTrue(!automate.consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas : " + automate);

            // Le premier retiré (par n'importe quel moyen) libère la place, et le compte reste.
            comptes.deposit(j.uuid(), Coin.GOLD, 3);
            helper.setBlock(A, Blocks.AIR);
            helper.assertTrue(comptes.safePos(j.uuid()).isEmpty(), "l'emplacement devait être effacé");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 3, "le compte devait rester");
            poserEtVerifier(helper, j, B);
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 3, "le compte suit le joueur, pas le bloc");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unAutreNeLOuvrePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("cf_ouv_proprio");
            JoueurDeTest autre = essai.joueur("cf_ouv_autre");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, proprio, A);
            comptes.deposit(proprio.uuid(), Coin.COPPER, 5);

            helper.assertTrue(!autre.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            autre.messagesRecus();
            clicDroit(helper, autre, A);
            helper.assertTrue(!ouvert(autre), "un autre joueur ne doit pas ouvrir");
            helper.assertTrue(autre.messagesRecus().contains(SafeBlock.MESSAGE + "not_yours"), "message de refus attendu");
            // Même en ayant le paquet : ni droit, ni écran.
            helper.assertTrue(agir(autre, SafeActionPacket.withdraw(helper.absolutePos(A), Coin.COPPER, 1)) == Result.NOT_ALLOWED,
                    "retrait par un autre");
            helper.assertTrue(agir(autre, SafeActionPacket.depositAll(helper.absolutePos(A))) == Result.NOT_ALLOWED,
                    "dépôt par un autre");
            helper.assertTrue(agir(autre, SafeActionPacket.groupAll(helper.absolutePos(A))) == Result.NOT_ALLOWED,
                    "regroupement par un autre");
            helper.assertTrue(comptes.count(proprio.uuid(), Coin.COPPER) == 5 && porte(autre, Coin.COPPER) == 0,
                    "rien ne devait bouger");

            // Le propriétaire, lui, ouvre, même avec une pièce en main (le bloc est dans money_use_allowed).
            helper.assertTrue(helper.getBlockState(A).is(MoneyGuard.MONEY_USE_ALLOWED), "bloc absent de money_use_allowed");
            helper.assertTrue(new ItemStack(EconomyRegistry.SAFE_ITEM.get()).is(BenchCatalog.TABLE_ONLY),
                    "objet absent de table_only");
            proprio.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Coin.GOLD.item(), 2));
            clicDroit(helper, proprio, A);
            helper.assertTrue(ouvert(proprio), "le propriétaire devait ouvrir, pièce en main");
            helper.assertTrue(MoneyStorageRules.isAllowedMenu(proprio.joueur().containerMenu),
                    "l'écran du coffre-fort est un rangement permis");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEquipeLOuvre(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("cf_equipe_proprio");
            JoueurDeTest chef = essai.equipe("cf_equipe_chef");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, proprio, A);
            comptes.deposit(proprio.uuid(), Coin.SILVER, 7);

            helper.assertTrue(chef.joueur().hasPermissions(2), "le chef doit être de l'équipe");
            clicDroit(helper, chef, A);
            helper.assertTrue(chef.joueur().containerMenu instanceof SafeMenu menu
                    && proprio.uuid().equals(menu.owner()), "l'équipe devait ouvrir le coffre-fort du joueur");
            // Elle agit sur le compte du propriétaire.
            helper.assertTrue(agir(chef, SafeActionPacket.withdraw(helper.absolutePos(A), Coin.SILVER, 1)) == Result.DONE,
                    "retrait par l'équipe");
            helper.assertTrue(comptes.count(proprio.uuid(), Coin.SILVER) == 6 && porte(chef, Coin.SILVER) == 1,
                    "le retrait vient du compte du propriétaire");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void deposerPuisRetirerAValeurEgale(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_depot");
            SafeAccounts comptes = essai.comptes();
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            poserEtVerifier(helper, j, A);
            donner(j, Coin.GOLD, 3);
            donner(j, Coin.SILVER, 70);
            donner(j, Coin.COPPER, 5);
            long valeur = valeurPortee(j);
            helper.assertTrue(valeur == 3 * 100 + 70 * 10 + 5, "valeur de départ " + valeur);
            long circulation = creeMoinsDetruit(registre);
            long masse = comptes.totalMass();
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "l'écran devait s'ouvrir");

            helper.assertTrue(agir(j, SafeActionPacket.depositAll(abs)) == Result.DONE, "dépôt");
            helper.assertTrue(valeurPortee(j) == 0 && comptes.total(j.uuid()) == valeur,
                    "tout devait passer au compte : porté " + valeurPortee(j) + ", compte " + comptes.total(j.uuid()));
            helper.assertTrue(comptes.totalMass() - masse == valeur, "la masse suit le compte");
            helper.assertTrue(agir(j, SafeActionPacket.depositAll(abs)) == Result.NOTHING, "rien à déposer");

            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.SILVER, 64)) == Result.DONE, "retrait de 64");
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.GOLD, 1)) == Result.DONE, "retrait de 1");
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 1)) == Result.DONE, "retrait de 1 cuivre");
            helper.assertTrue(porte(j, Coin.SILVER) == 64 && porte(j, Coin.GOLD) == 1 && porte(j, Coin.COPPER) == 1,
                    "portés : " + PlayerCoins.held(j.joueur()));
            helper.assertTrue(valeurPortee(j) + comptes.total(j.uuid()) == valeur, "valeur égale : "
                    + valeurPortee(j) + " + " + comptes.total(j.uuid()) + " pour " + valeur);
            // Ni créé ni détruit : le registre n'a pas bougé.
            helper.assertTrue(creeMoinsDetruit(registre) == circulation, "le registre a bougé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void retirerPlusQueLeSoldeNeDonneRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_solde");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 5);
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, j, A);

            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 10)) == Result.INSUFFICIENT,
                    "10 sur 5 devait être refusé");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 5 && porte(j, Coin.COPPER) == 0, "rien ne bouge");
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.RUBY, 1)) == Result.INSUFFICIENT, "aucun rubis");
            // Des montants ou coupures hors des boutons : refusés sans effet.
            for (int montant : new int[] {0, -1, 2, 5, 63, 65, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new SafeActionPacket(abs, new CoinRequest(CoinRequest.WITHDRAW, 0, montant, false))) == Result.BAD_REQUEST,
                        "montant " + montant);
            }
            for (int coupure : new int[] {-1, 5, 99, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new SafeActionPacket(abs, new CoinRequest(CoinRequest.WITHDRAW, coupure, 1, false))) == Result.BAD_REQUEST,
                        "coupure " + coupure);
            }
            for (int action : new int[] {-1, 6, 99, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new SafeActionPacket(abs, new CoinRequest(action, 0, 1, false))) == Result.BAD_REQUEST, "action " + action);
            }
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 5 && porte(j, Coin.COPPER) == 0, "toujours rien");
            // Le retrait exact passe, et le compte ne descend pas sous zéro.
            helper.assertTrue(!comptes.withdraw(j.uuid(), Coin.COPPER, 6) && !comptes.withdraw(j.uuid(), Coin.COPPER, -1)
                    && !comptes.withdraw(j.uuid(), Coin.COPPER, 0), "retrait de la couche basse");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 5, "5 cuivres toujours");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void inventairePleinRienNeSePerd(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_plein");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 100);
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, j, A);
            remplirDeTerre(j);

            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 64)) == Result.FULL, "inventaire plein");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 100 && porte(j, Coin.COPPER) == 0,
                    "rien ne devait sortir : compte " + comptes.count(j.uuid(), Coin.COPPER));
            helper.assertTrue(objets(helper).stream().noneMatch(o -> Coin.fromItem(o.getItem().getItem()).isPresent()),
                    "rien ne tombe au sol");

            // Une case se libère à moitié : seul ce qui entre est retiré du compte.
            j.joueur().getInventory().items.set(0, new ItemStack(Coin.COPPER.item(), 60));
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 10)) == Result.DONE, "entrée partielle");
            helper.assertTrue(porte(j, Coin.COPPER) == 64 && comptes.count(j.uuid(), Coin.COPPER) == 96,
                    "porté " + porte(j, Coin.COPPER) + ", compte " + comptes.count(j.uuid(), Coin.COPPER));
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 1)) == Result.FULL, "plus de place");
            helper.assertTrue(porte(j, Coin.COPPER) + comptes.count(j.uuid(), Coin.COPPER) == 160, "160 cuivres au total");

            // Le dépôt ne perd rien non plus quand le compte est plein.
            comptes.deposit(j.uuid(), Coin.GOLD, SafeAccounts.MAX_PER_COIN - 1);
            j.joueur().getInventory().items.set(1, new ItemStack(Coin.GOLD.item(), 5));
            helper.assertTrue(agir(j, SafeActionPacket.depositAll(abs)) == Result.DONE, "dépôt jusqu'au plafond");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == SafeAccounts.MAX_PER_COIN && porte(j, Coin.GOLD) == 4,
                    "une seule pièce d'or a la place : porté " + porte(j, Coin.GOLD));
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leProprietaireNeLeCassePasPlein(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_casse_proprio");
            JoueurDeTest autre = essai.joueur("cf_casse_autre");
            JoueurDeTest createur = essai.joueur("cf_casse_creatif");
            createur.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 5);
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), abs) < 0, "dureté réservée (-1)");

            // Plein : ni progression ni bris, et le propriétaire est prévenu.
            j.messagesRecus();
            helper.assertTrue(etat.getDestroyProgress(j.joueur(), helper.getLevel(), abs) == 0.0F, "pas de progression, plein");
            helper.assertTrue(!j.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).is(EconomyRegistry.SAFE.get()),
                    "plein, il ne devait pas se casser");
            helper.assertTrue(j.messagesRecus().contains(SafeBlock.MESSAGE + "not_empty"), "message attendu");
            // Un autre joueur, même en créatif : rien.
            helper.assertTrue(etat.getDestroyProgress(autre.joueur(), helper.getLevel(), abs) == 0.0F, "autre : pas de progression");
            helper.assertTrue(!autre.joueur().gameMode.destroyBlock(abs), "un autre ne le casse pas");
            helper.assertTrue(!createur.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).is(EconomyRegistry.SAFE.get()),
                    "un autre en créatif non plus (filet du bris)");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 5, "le compte est intact");

            // Vide : le propriétaire le casse, il ne lâche que son bloc, et la place est libérée.
            helper.assertTrue(comptes.withdraw(j.uuid(), Coin.COPPER, 5), "vidage");
            helper.assertTrue(etat.getDestroyProgress(j.joueur(), helper.getLevel(), abs) > 0.0F, "progression, vide");
            helper.assertTrue(j.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(), "vide, il se casse");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1 && lache.get(0).getItem().is(EconomyRegistry.SAFE_ITEM.get()),
                    "seul le bloc tombe : " + lache);
            helper.assertTrue(comptes.safePos(j.uuid()).isEmpty(), "l'emplacement devait être effacé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEquipeLeCassePleinEtLeCompteReste(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("cf_ecasse_proprio");
            JoueurDeTest chef = essai.equipe("cf_ecasse_chef");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, proprio, A);
            comptes.deposit(proprio.uuid(), Coin.GOLD, 3);
            comptes.deposit(proprio.uuid(), Coin.COPPER, 5);
            long valeur = comptes.total(proprio.uuid());
            BlockPos abs = helper.absolutePos(A);

            helper.assertTrue(helper.getBlockState(A).getDestroyProgress(chef.joueur(), helper.getLevel(), abs) > 0.0F,
                    "l'équipe a une progression");
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                    "l'équipe devait le casser plein");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1 && lache.get(0).getItem().is(EconomyRegistry.SAFE_ITEM.get()),
                    "seul le bloc tombe, pas le contenu : " + lache);
            helper.assertTrue(comptes.total(proprio.uuid()) == valeur, "le compte devait rester : " + comptes.total(proprio.uuid()));
            helper.assertTrue(comptes.safePos(proprio.uuid()).isEmpty(), "l'emplacement devait être effacé");

            // Le contenu réapparaît dans son prochain coffre-fort.
            poserEtVerifier(helper, proprio, B);
            clicDroit(helper, proprio, B);
            helper.assertTrue(proprio.joueur().containerMenu instanceof SafeMenu menu && menu.total() == valeur,
                    "le nouveau coffre-fort montre le même compte");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : persistance, regroupement, vérifications du paquet, ce que voit le client
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leCompteSurvitAuRechargementEtZoneDechargee(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_persiste");
            SafeAccounts comptes = essai.comptes();
            MinecraftServer serveur = essai.serveur();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.GOLD, 3);
            comptes.deposit(j.uuid(), Coin.RUBY, 5_000_000_000L);
            GlobalPos pos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(A));

            // Écrit sur le disque, puis relu par un stockage neuf : c'est ce que fait un redémarrage.
            serveur.overworld().getDataStorage().save();
            File dossier = serveur.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
            helper.assertTrue(new File(dossier, SafeAccounts.KEY + ".dat").isFile(), "le fichier des comptes n'est pas écrit");
            SafeAccounts relu = new DimensionDataStorage(dossier, serveur.getFixerUpper())
                    .get(SafeAccounts::load, SafeAccounts.KEY);
            helper.assertTrue(relu != null && relu != comptes, "comptes non relus du disque");
            helper.assertTrue(relu.balance(j.uuid()).equals(comptes.balance(j.uuid())), "contenu relu : " + relu.balance(j.uuid()));
            helper.assertTrue(relu.totalMass() == comptes.totalMass(), "masse relue");
            helper.assertTrue(relu.safePos(j.uuid()).equals(java.util.Optional.of(pos)), "emplacement relu");
            // Un fichier abîmé ne fait ni planter ni gagner de pièces.
            CompoundTag abime = comptes.save(new CompoundTag());
            abime.getList("accounts", 10).add(new CompoundTag());
            helper.assertTrue(SafeAccounts.load(abime).totalMass() == comptes.totalMass(), "ligne sans propriétaire ignorée");

            // Zone déchargée : le compte se lit sans le chunk, et le coffre-fort noté compte encore.
            UUID loin = essai.compteLibre();
            BlockPos horsMonde = new BlockPos(1_000_000, 64, 1_000_000);
            helper.assertTrue(!helper.getLevel().isLoaded(horsMonde), "la cible doit être hors des chunks chargés");
            comptes.deposit(loin, Coin.SILVER, 12);
            comptes.setSafePos(loin, GlobalPos.of(helper.getLevel().dimension(), horsMonde));
            helper.assertTrue(comptes.total(loin) == 120, "le compte se lit sans son chunk");
            helper.assertTrue(SafeBlock.hasLiveSafe(serveur, loin), "un coffre-fort en zone déchargée compte encore");
            helper.assertTrue(!helper.getLevel().isLoaded(horsMonde), "la vérification ne charge pas le chunk");
            // Une note vers un bloc qui n'existe plus (chunk chargé) est périmée : elle ne bloque pas le joueur,
            // la vérification ne change rien, et la prochaine pose la remplace.
            comptes.setSafePos(loin, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(B)));
            helper.assertTrue(!SafeBlock.hasLiveSafe(serveur, loin) && comptes.safePos(loin).isPresent(),
                    "note périmée ignorée sans effet de bord");
            helper.assertTrue(comptes.total(loin) == 120, "le compte reste");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void regrouperDixPourUnAValeurEgale(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_regroupe");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 25);
            comptes.deposit(j.uuid(), Coin.SILVER, 13);
            comptes.deposit(j.uuid(), Coin.GOLD, 7);
            long valeur = comptes.total(j.uuid());
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, j, A);

            helper.assertTrue(agir(j, SafeActionPacket.groupAll(abs)) == Result.DONE, "regroupement");
            EnumMap<Coin, Long> apres = comptes.balance(j.uuid());
            helper.assertTrue(apres.get(Coin.COPPER) == 5 && apres.get(Coin.SILVER) == 5 && apres.get(Coin.GOLD) == 8
                    && apres.get(Coin.EMERALD) == 0, "après : " + apres);
            helper.assertTrue(comptes.total(j.uuid()) == valeur, "valeur égale");
            helper.assertTrue(agir(j, SafeActionPacket.groupAll(abs)) == Result.NOTHING, "rien de plus à regrouper");

            // Le rapport vivant : si un rapport n'est pas 10, la paire est laissée telle quelle.
            UUID autre = essai.compteLibre();
            comptes.deposit(autre, Coin.COPPER, 25);
            comptes.deposit(autre, Coin.SILVER, 13);
            long avant = comptes.total(autre);
            java.util.function.ToLongFunction<Coin> exotique = c -> new long[] {1, 20, 200, 2000, 20000}[c.ordinal()];
            helper.assertTrue(comptes.group(autre, exotique), "l'argent monte en or, le cuivre non");
            EnumMap<Coin, Long> exo = comptes.balance(autre);
            helper.assertTrue(exo.get(Coin.COPPER) == 25 && exo.get(Coin.SILVER) == 3 && exo.get(Coin.GOLD) == 1, "exotique : " + exo);
            java.util.function.ToLongFunction<Coin> aucun = c -> new long[] {1, 20, 300, 4000, 50000}[c.ordinal()];
            helper.assertTrue(!comptes.group(autre, aucun), "aucun rapport de 10 : rien ne bouge");
            helper.assertTrue(comptes.balance(autre).equals(exo), "le compte est resté tel quel");
            helper.assertTrue(avant > 0, "garde-fou du test");
            // Le plafond : un regroupement qui ferait dépasser le plafond du rubis laisse le reste en émeraude.
            UUID plein = essai.compteLibre();
            comptes.deposit(plein, Coin.RUBY, SafeAccounts.MAX_PER_COIN);
            comptes.deposit(plein, Coin.EMERALD, 10);
            helper.assertTrue(!comptes.group(plein) && comptes.count(plein, Coin.RUBY) == SafeAccounts.MAX_PER_COIN
                    && comptes.count(plein, Coin.EMERALD) == 10, "plafond respecté : " + comptes.balance(plein));
            helper.assertTrue(comptes.room(plein, Coin.RUBY) == 0, "place nulle, jamais négative");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lePaquetEstVerifieSurLeServeur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_verifie");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 10);
            BlockPos abs = helper.absolutePos(A);
            SafeActionPacket retrait = SafeActionPacket.withdraw(abs, Coin.COPPER, 1);

            // Écran pas ouvert : refusé, même à portée et propriétaire.
            helper.assertTrue(agir(j, retrait) == Result.NOT_OPEN, "sans écran ouvert");
            clicDroit(helper, j, A);
            helper.assertTrue(agir(j, retrait) == Result.DONE, "écran ouvert, à portée");
            // Pas de coffre-fort à cette position.
            BlockPos vide = helper.absolutePos(B);
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(vide, Coin.COPPER, 1)) == Result.NO_SAFE, "pas de coffre-fort");
            // Trop loin, et un chunk non chargé (jamais chargé par le paquet).
            BlockPos horsMonde = new BlockPos(1_000_000, 64, 1_000_000);
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(horsMonde, Coin.COPPER, 1)) == Result.TOO_FAR, "chunk non chargé");
            helper.assertTrue(!helper.getLevel().isLoaded(horsMonde), "le paquet ne charge pas le chunk");
            j.joueur().moveTo(helper.absoluteVec(JOUEUR).add(40, 0, 0));
            helper.assertTrue(agir(j, retrait) == Result.TOO_FAR, "trop loin");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 9 && porte(j, Coin.COPPER) == 1, "un seul retrait a passé");
            // Revenu à portée : à nouveau possible (l'écran est resté ouvert, rien ne l'a fermé).
            j.joueur().moveTo(helper.absoluteVec(JOUEUR));
            Result retour = agir(j, retrait);
            helper.assertTrue(retour == Result.DONE, "retour à portée : " + retour);
            // Le bloc disparaît : plus de coffre-fort, plus d'écran valide.
            helper.setBlock(A, Blocks.AIR);
            helper.assertTrue(agir(j, retrait) == Result.NO_SAFE, "bloc disparu");
            long total = comptes.count(j.uuid(), Coin.COPPER) + porte(j, Coin.COPPER);
            helper.assertTrue(total == 10, "aucune pièce perdue ni créée : " + total);
            helper.succeed();
        }
    }

    /**
     * Pendant que l'écran est ouvert, l'inventaire du client suit les dépôts : le menu porte les cases de
     * l'inventaire, synchronisées comme pour tout écran (avant, il fallait fermer l'écran).
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lInventaireDuClientSuitPendantLEcran(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_client_inv");
            poserEtVerifier(helper, j, A);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            donner(j, Coin.GOLD, 3);
            donner(j, Coin.SILVER, 20);
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");
            AbstractContainerMenu serveur = j.joueur().containerMenu;
            helper.assertTrue(serveur.slots.size() == 36, "les 36 cases de l'inventaire : " + serveur.slots.size());
            FriendlyByteBuf tampon = new FriendlyByteBuf(Unpooled.buffer());
            tampon.writeBlockPos(helper.absolutePos(A));
            // Le client : un inventaire à part, rempli seulement par les paquets réellement envoyés.
            SafeMenu client = SafeMenu.fromNetwork(serveur.containerId, new Inventory(j.joueur()), tampon);
            j.synchroniser(client);
            helper.assertTrue(client.carried(Coin.GOLD) == 3 && client.carried(Coin.SILVER) == 20,
                    "à l'ouverture le client voit l'inventaire : or " + client.carried(Coin.GOLD));
            // Le dépôt passe par le vrai paquet (réseau compris) ; le client est à jour sans fermer l'écran.
            j.envoyer(SafeActionPacket.depositAll(helper.absolutePos(A)));
            j.synchroniser(client);
            helper.assertTrue(client.carried(Coin.GOLD) == 0 && client.carried(Coin.SILVER) == 0,
                    "après le dépôt, le client voit un inventaire vide : or " + client.carried(Coin.GOLD) + ", argent "
                            + client.carried(Coin.SILVER));
            helper.assertTrue(client.count(Coin.GOLD) == 3 && client.count(Coin.SILVER) == 20 && client.total() == 500,
                    "le compte du client : " + client.total());
            helper.assertTrue(ouvert(j), "l'écran est resté ouvert");
            // Le maj-clic sur une case ne fait rien (rien ne passe par le curseur vers le compte).
            helper.assertTrue(serveur.quickMoveStack(j.joueur(), 0).isEmpty(), "maj-clic");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lEcranRecoitLeCompteMemeGrand(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_ecran");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            long[] nombres = {12, 70_000, 5_000_000_000L, 123_456_789_012L, SafeAccounts.MAX_PER_COIN};
            for (Coin coin : Coin.values()) {
                comptes.deposit(j.uuid(), coin, nombres[coin.ordinal()]);
            }
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");
            // Ce que le client reconstruit à partir des paquets réellement envoyés.
            AbstractContainerMenu serveur = j.joueur().containerMenu;
            FriendlyByteBuf tampon = new FriendlyByteBuf(Unpooled.buffer());
            tampon.writeBlockPos(helper.absolutePos(A));
            SafeMenu client = SafeMenu.fromNetwork(serveur.containerId, new Inventory(j.joueur()), tampon);
            j.synchroniser(client);
            for (Coin coin : Coin.values()) {
                helper.assertTrue(client.count(coin) == nombres[coin.ordinal()],
                        "client " + coin + " : " + client.count(coin) + " au lieu de " + nombres[coin.ordinal()]);
            }
            helper.assertTrue(client.total() == comptes.total(j.uuid()), "total du client");
            // Un changement ultérieur se diffuse aussi.
            comptes.withdraw(j.uuid(), Coin.COPPER, 2);
            serveur.broadcastChanges();
            j.synchroniser(client);
            helper.assertTrue(client.count(Coin.COPPER) == 10, "le client suit : " + client.count(Coin.COPPER));
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void enCreatifPleinRienNeDisparait(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest proprio = essai.joueur("cf_crea_proprio");
            JoueurDeTest chef = essai.equipe("cf_crea_chef");
            chef.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, proprio, A);
            comptes.deposit(proprio.uuid(), Coin.COPPER, 100);
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, chef, A);
            helper.assertTrue(ouvert(chef), "l'équipe en créatif ouvre");
            remplirDeTerre(chef);
            helper.assertTrue(chef.joueur().getAbilities().instabuild, "le chef est en créatif");
            helper.assertTrue(agir(chef, SafeActionPacket.withdraw(abs, Coin.COPPER, 64)) == Result.FULL, "inventaire plein");
            helper.assertTrue(comptes.count(proprio.uuid(), Coin.COPPER) == 100 && porte(chef, Coin.COPPER) == 0,
                    "l'argent non pris reste au compte : " + comptes.count(proprio.uuid(), Coin.COPPER));
            // Place pour 4 seulement : 4 sortent, 96 restent.
            chef.joueur().getInventory().items.set(0, new ItemStack(Coin.COPPER.item(), 60));
            helper.assertTrue(agir(chef, SafeActionPacket.withdraw(abs, Coin.COPPER, 10)) == Result.DONE, "entrée partielle");
            helper.assertTrue(porte(chef, Coin.COPPER) == 64 && comptes.count(proprio.uuid(), Coin.COPPER) == 96,
                    "porté " + porte(chef, Coin.COPPER) + ", compte " + comptes.count(proprio.uuid(), Coin.COPPER));
            // L'entrée commune de PlayerCoins : rien n'entre, rien n'est annoncé.
            long recu = PlayerCoins.receive(chef.joueur(), Coin.GOLD, 5);
            helper.assertTrue(recu == 0 && porte(chef, Coin.GOLD) == 0, "rien n'entre, rien n'est annoncé : " + recu);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unJoueurPartiNeDeclencheRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_parti");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.GOLD, 10);
            donner(j, Coin.COPPER, 20);
            clicDroit(helper, j, A);
            ServerPlayer joueur = j.joueur();
            j.quitter();
            // Le paquet arrive après le départ : rien ne se passe, ni dans le compte, ni dans l'inventaire du parti.
            helper.assertTrue(SafeActionPacket.apply(joueur, SafeActionPacket.withdraw(helper.absolutePos(A), Coin.GOLD, 1)) == Result.GONE,
                    "retrait après le départ");
            helper.assertTrue(SafeActionPacket.apply(joueur, SafeActionPacket.depositAll(helper.absolutePos(A))) == Result.GONE,
                    "dépôt après le départ");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 10 && comptes.count(j.uuid(), Coin.COPPER) == 0,
                    "le compte est intact");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : par le canal, avec des ticks (deux clics rapides, explosion, piston)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void deuxClicsRapidesNeDupliquentPas(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            JoueurDeTest j = essai.joueur("cf_deux_clics");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            comptes.deposit(j.uuid(), Coin.COPPER, 100);
            BlockPos abs = helper.absolutePos(A);
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");
            // Deux retraits de 64 envoyés avant que le serveur ne traite le premier : le compte n'en a que pour un.
            j.envoyer(SafeActionPacket.withdraw(abs, Coin.COPPER, 64));
            j.envoyer(SafeActionPacket.withdraw(abs, Coin.COPPER, 64));
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertTrue(porte(j, Coin.COPPER) == 64 && comptes.count(j.uuid(), Coin.COPPER) == 36,
                            "un seul retrait de 64 : porté " + porte(j, Coin.COPPER) + ", compte "
                                    + comptes.count(j.uuid(), Coin.COPPER));
                    helper.assertTrue(j.messagesRecus().contains(SafeBlock.MESSAGE + "insufficient"),
                            "le second clic est refusé avec un message");
                    // Deux dépôts de suite : le second ne trouve plus rien.
                    j.envoyer(SafeActionPacket.depositAll(abs));
                    j.envoyer(SafeActionPacket.depositAll(abs));
                } catch (RuntimeException e) {
                    essai.close();
                    throw e;
                }
            });
            helper.runAfterDelay(10, () -> {
                try {
                    helper.assertTrue(porte(j, Coin.COPPER) == 0 && comptes.count(j.uuid(), Coin.COPPER) == 100,
                            "100 au compte, rien de plus : " + comptes.count(j.uuid(), Coin.COPPER));
                    helper.assertTrue(j.messagesRecus().contains(SafeBlock.MESSAGE + "nothing"), "le second dépôt dit rien");
                    // Départ juste après l'envoi : le serveur de test traite le paquet sur place, mais le total
                    // (compte + ce que le parti emporte) ne change pas, quel que soit le moment du traitement.
                    j.envoyer(SafeActionPacket.withdraw(abs, Coin.COPPER, 10));
                    j.quitter();
                } catch (RuntimeException e) {
                    essai.close();
                    throw e;
                }
            });
            helper.runAfterDelay(15, () -> {
                try {
                    helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) + porte(j, Coin.COPPER) == 100,
                            "ni perdu ni créé : compte " + comptes.count(j.uuid(), Coin.COPPER) + ", porté "
                                    + porte(j, Coin.COPPER));
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
    // Le coffre-fort et la bourse
    // ---------------------------------------------------------------------------------------------

    private static long valeurTotale(JoueurDeTest j, SafeAccounts comptes) {
        return valeurPortee(j) + comptes.total(j.uuid());
    }

    @GameTest(template = "test/plancher", batch = LOT_BOURSE)
    public static void deposerToutPrendAussiLaBourseEtLeRetraitLuiRevient(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_bourse_cycle");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            BlockPos abs = helper.absolutePos(A);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.COPPER, 30);
            j.joueur().getInventory().setItem(0, bourse);
            donner(j, Coin.GOLD, 3);
            donner(j, Coin.COPPER, 5);
            long valeur = valeurPortee(j);
            helper.assertTrue(valeur == 30 + 300 + 5, "valeur de départ " + valeur);
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "l'écran devait s'ouvrir");

            // Tout part, bourse comprise ; la valeur est conservée.
            helper.assertTrue(agir(j, SafeActionPacket.depositAll(abs)) == Result.DONE, "dépôt");
            helper.assertTrue(valeurPortee(j) == 0 && Purse.total(bourse) == 0, "plus rien sur le joueur : " + PlayerCoins.held(j.joueur()));
            helper.assertTrue(comptes.total(j.uuid()) == valeur, "le compte a tout : " + comptes.total(j.uuid()));

            // Le retrait va d'abord en vrac dans l'inventaire (décision du 2026-11-13) : le joueur voit ce qu'il retire.
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 10)) == Result.DONE, "retrait");
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) == 0
                    && PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.COPPER, 0L) == 10,
                    "en vrac : " + PlayerCoins.heldLoose(j.joueur()) + ", bourse " + Purse.contents(bourse));
            helper.assertTrue(valeurTotale(j, comptes) == valeur, "valeur conservée");

            // L'inventaire n'a plus de place que sur le tas de 10 cuivres : le reste entre dans la bourse.
            for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                if (j.joueur().getInventory().items.get(i).isEmpty()) {
                    j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
                }
            }
            comptes.deposit(j.uuid(), Coin.COPPER, 64);
            long avant = valeurTotale(j, comptes);
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 64)) == Result.DONE, "retrait, vrac presque plein");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.COPPER, 0L) == 64
                    && Purse.count(bourse, Coin.COPPER) == 10,
                    "54 en vrac puis 10 dans la bourse : " + PlayerCoins.heldLoose(j.joueur()) + ", bourse " + Purse.contents(bourse));
            helper.assertTrue(valeurTotale(j, comptes) == avant, "valeur conservée au plafond");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_BOURSE)
    public static void retraitBourseAuPlafondEtInventairePleinRemetAuCoffre(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_bourse_plein");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            BlockPos abs = helper.absolutePos(A);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.COPPER, Purse.MAX_PER_COIN - 4);
            j.joueur().getInventory().setItem(0, bourse);
            comptes.deposit(j.uuid(), Coin.COPPER, 20);
            // Inventaire plein de terre, sauf un tas de 61 cuivres (place pour 3).
            for (int i = 1; i < j.joueur().getInventory().items.size(); i++) {
                j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            j.joueur().getInventory().items.set(1, new ItemStack(Coin.COPPER.item(), 61));
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");
            long avant = Purse.count(bourse, Coin.COPPER) + 61 + 20;

            // 10 demandés : 4 dans la bourse, 3 en vrac, 3 retournent au compte.
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 10)) == Result.DONE, "retrait");
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) == Purse.MAX_PER_COIN, "bourse pleine");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.COPPER, 0L) == 64, "tas complet");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 13, "3 remis au compte : " + comptes.count(j.uuid(), Coin.COPPER));
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) + PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.COPPER, 0L)
                    + comptes.count(j.uuid(), Coin.COPPER) == avant, "ni perdu ni créé");
            // Plus aucune place : FULL, rien ne bouge, rien au sol.
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.COPPER, 1)) == Result.FULL, "plus de place");
            helper.assertTrue(comptes.count(j.uuid(), Coin.COPPER) == 13, "le compte est intact");
            helper.assertTrue(objets(helper).stream().noneMatch(o -> Coin.fromItem(o.getItem().getItem()).isPresent()),
                    "rien ne tombe au sol");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(dev.oneforallsurvival.economy.CoinDropEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(0, -1, 0)), helper.absolutePos(new BlockPos(8, 5, 8)))).isEmpty(),
                    "aucune pièce au sol (entité du mod)");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void explosionEtTagsSansEffet(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            BlockPos coffre = new BlockPos(3, 0, 3);
            BlockPos temoin = new BlockPos(2, 0, 3);
            BlockPos centre = new BlockPos(2, 0, 4);
            helper.setBlock(coffre, EconomyRegistry.SAFE.get());
            helper.setBlock(temoin, Blocks.DIRT); // prouve que les rayons atteignent le côté du coffre-fort
            BlockState etat = helper.getBlockState(coffre);
            helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE),
                    "le Wither et le dragon ne le cassent pas (tags)");
            helper.assertTrue(etat.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK,
                    "le piston est bloqué");
            Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
            helper.getLevel().explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
            helper.assertTrue(helper.getBlockState(coffre).is(EconomyRegistry.SAFE.get())
                    && helper.getBlockEntity(coffre) instanceof SafeBlockEntity, "le coffre-fort devait résister");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void lePistonNeLePoussePas(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos piston = new BlockPos(1, 0, 2);
        BlockPos coffre = new BlockPos(2, 0, 2);
        BlockPos pistonTemoin = new BlockPos(1, 0, 4);
        BlockPos terre = new BlockPos(2, 0, 4);
        helper.setBlock(coffre, EconomyRegistry.SAFE.get());
        helper.setBlock(terre, Blocks.DIRT);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                // Le piston témoin pousse la terre : les pistons sont réels et alimentés.
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 4)).is(Blocks.DIRT),
                        "le piston témoin devait pousser la terre"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(helper.getBlockState(coffre).is(EconomyRegistry.SAFE.get()),
                                "le coffre-fort ne devait pas bouger");
                        helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                                "le piston bloqué ne devait pas s'étendre");
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot à part : la masse exacte des coffres-forts (valeur globale, lue sans voisin)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_MASSE)
    public static void laMasseSuitLesComptes(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            SafeAccounts comptes = essai.comptes();
            long avant = comptes.totalMass();
            UUID a = essai.compteLibre();
            UUID b = essai.compteLibre();
            comptes.deposit(a, Coin.GOLD, 3);
            comptes.deposit(a, Coin.COPPER, 5);
            comptes.deposit(b, Coin.RUBY, 2);
            helper.assertTrue(comptes.totalMass() - avant == 305 + 20_000, "masse : " + (comptes.totalMass() - avant));
            comptes.withdraw(b, Coin.RUBY, 1);
            helper.assertTrue(comptes.totalMass() - avant == 305 + 10_000, "après retrait : " + (comptes.totalMass() - avant));
            // Le tableau de bord l'affiche à la place de « pas encore en service ».
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            helper.assertTrue(registre.totalCreated(MoneySource.STAFF) >= 0 && registre.totalDestroyed(MoneySink.STAFF) >= 0,
                    "registre lisible");
            net.minecraft.commands.CommandSourceStack source = essai.serveur().createCommandSourceStack().withPermission(2)
                    .withSuppressedOutput();
            try {
                int r = essai.serveur().getCommands().getDispatcher().execute("eco stats", source);
                helper.assertTrue(r == 1, "eco stats : " + r);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new AssertionError("eco stats a échoué : " + e.getMessage());
            }
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Écran de la passe finale : déposer N (clic gauche), retirer N (clic droit), panneau « Change »
    // ---------------------------------------------------------------------------------------------

    /** Valeur portée (bourses comprises) plus le compte : elle ne bouge jamais d'une action de l'écran. */
    private static long tout(JoueurDeTest j, SafeAccounts comptes) {
        return valeurPortee(j) + comptes.total(j.uuid());
    }

    @GameTest(template = "test/plancher", batch = LOT_ECRAN)
    public static void deposerNPrendLeVracPuisLesBourses(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_depot_n");
            SafeAccounts comptes = essai.comptes();
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            poserEtVerifier(helper, j, A);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos abs = helper.absolutePos(A);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 20);
            j.joueur().getInventory().setItem(8, bourse);
            donner(j, Coin.GOLD, 5);
            long valeur = tout(j, comptes);
            long circulation = creeMoinsDetruit(registre);
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");

            // 10 demandés : les 5 en vrac d'abord, puis 5 de la bourse.
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.GOLD, 10)) == Result.DONE, "dépôt de 10");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.GOLD, 0L) == 0
                    && Purse.count(bourse, Coin.GOLD) == 15 && comptes.count(j.uuid(), Coin.GOLD) == 10,
                    "vrac " + PlayerCoins.heldLoose(j.joueur()) + ", bourse " + Purse.contents(bourse));
            // 64 demandés, 15 portés : jusqu'à N, selon ce qui est là.
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.GOLD, 64)) == Result.DONE, "dépôt de 64");
            helper.assertTrue(Purse.count(bourse, Coin.GOLD) == 0 && comptes.count(j.uuid(), Coin.GOLD) == 25, "tout est au compte");
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.GOLD, 1)) == Result.NOTHING, "plus rien à déposer");
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.RUBY, 1)) == Result.NOTHING, "aucun rubis porté");

            // Retirer 64 sur 25 : refusé (tout ou rien) ; retirer 10 : en vrac.
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.GOLD, 64)) == Result.INSUFFICIENT, "64 sur 25");
            helper.assertTrue(agir(j, SafeActionPacket.withdraw(abs, Coin.GOLD, 10)) == Result.DONE, "retrait de 10");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.GOLD, 0L) == 10, "10 en vrac");

            // Compte au plafond pour l'argent : le dépôt est refusé, rien ne quitte le joueur.
            comptes.deposit(j.uuid(), Coin.SILVER, SafeAccounts.MAX_PER_COIN);
            donner(j, Coin.SILVER, 3);
            long avantPlein = tout(j, comptes);
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.SILVER, 1)) == Result.STORE_FULL, "compte plein");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.SILVER, 0L) == 3 && tout(j, comptes) == avantPlein,
                    "rien ne bouge au plafond");
            comptes.withdraw(j.uuid(), Coin.SILVER, SafeAccounts.MAX_PER_COIN);
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.SILVER, 10)) == Result.DONE, "3 sur 10");

            // En créatif aussi, le compte ne gagne que ce que le joueur a perdu.
            j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            long avantCreatif = tout(j, comptes);
            helper.assertTrue(agir(j, SafeActionPacket.deposit(abs, Coin.GOLD, 1)) == Result.DONE, "dépôt en créatif");
            helper.assertTrue(tout(j, comptes) == avantCreatif && PlayerCoins.heldLoose(j.joueur()).getOrDefault(Coin.GOLD, 0L) == 9,
                    "créatif : " + PlayerCoins.heldLoose(j.joueur()));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

            helper.assertTrue(tout(j, comptes) == valeur + 3 * 10, "valeur conservée (plus les 3 argent donnés)");
            helper.assertTrue(creeMoinsDetruit(registre) == circulation, "le registre n'a pas bougé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_ECRAN)
    public static void leChangeCasseEtRegroupeAValeurEgale(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.joueur("cf_change");
            SafeAccounts comptes = essai.comptes();
            poserEtVerifier(helper, j, A);
            BlockPos abs = helper.absolutePos(A);
            comptes.deposit(j.uuid(), Coin.GOLD, 3);
            comptes.deposit(j.uuid(), Coin.SILVER, 25);
            comptes.deposit(j.uuid(), Coin.RUBY, 1);
            long valeur = comptes.total(j.uuid());
            clicDroit(helper, j, A);
            helper.assertTrue(ouvert(j), "écran ouvert");

            // Clic simple : une seule conversion.
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, false))) == Result.DONE, "1 or -> 10 argent");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 2 && comptes.count(j.uuid(), Coin.SILVER) == 35, comptes.balance(j.uuid()).toString());
            // Maj + clic : autant que possible, calculé par le serveur.
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, true))) == Result.DONE, "tout l'or");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 0 && comptes.count(j.uuid(), Coin.SILVER) == 55, comptes.balance(j.uuid()).toString());
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, false))) == Result.INSUFFICIENT, "plus d'or");
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.groupPair(Coin.GOLD, false))) == Result.DONE, "10 argent -> 1 or");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 1 && comptes.count(j.uuid(), Coin.SILVER) == 45, comptes.balance(j.uuid()).toString());
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.groupPair(Coin.GOLD, true))) == Result.DONE, "tout l'argent");
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 5 && comptes.count(j.uuid(), Coin.SILVER) == 5, comptes.balance(j.uuid()).toString());
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.groupPair(Coin.GOLD, false))) == Result.INSUFFICIENT, "5 argent");
            // Le rubis se casse aussi.
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.RUBY, false))) == Result.DONE, "1 rubis -> 10 émeraudes");
            helper.assertTrue(comptes.count(j.uuid(), Coin.EMERALD) == 10 && comptes.count(j.uuid(), Coin.RUBY) == 0, comptes.balance(j.uuid()).toString());
            helper.assertTrue(comptes.total(j.uuid()) == valeur, "valeur conservée : " + comptes.total(j.uuid()) + " pour " + valeur);

            // Plus de place sous le plafond : refusé, rien ne bouge.
            comptes.deposit(j.uuid(), Coin.SILVER, SafeAccounts.MAX_PER_COIN - 5 - 5);
            long avant = comptes.total(j.uuid());
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, false))) == Result.STORE_FULL, "argent au plafond");
            helper.assertTrue(agir(j, new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, true))) == Result.STORE_FULL, "argent au plafond, maj");
            helper.assertTrue(comptes.total(j.uuid()) == avant && comptes.count(j.uuid(), Coin.GOLD) == 5, "rien n'a bougé");

            // Demandes hors des boutons : refusées.
            for (CoinRequest faux : new CoinRequest[] {CoinRequest.breakCoin(Coin.COPPER, false), CoinRequest.groupPair(Coin.COPPER, true),
                    new CoinRequest(CoinRequest.BREAK, 2, 10, false), new CoinRequest(CoinRequest.GROUP_PAIR, 9, 0, true),
                    new CoinRequest(CoinRequest.DEPOSIT, 2, 5, false), new CoinRequest(CoinRequest.WITHDRAW, 2, 1, true)}) {
                helper.assertTrue(agir(j, new SafeActionPacket(abs, faux)) == Result.BAD_REQUEST, "refusé : " + faux);
            }
            // Deux paquets « tout casser » d'affilée : le second ne trouve plus rien, rien n'est créé.
            comptes.withdraw(j.uuid(), Coin.SILVER, comptes.count(j.uuid(), Coin.SILVER));
            long avantDouble = comptes.total(j.uuid());
            j.envoyer(new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, true)));
            j.envoyer(new SafeActionPacket(abs, CoinRequest.breakCoin(Coin.GOLD, true)));
            helper.assertTrue(comptes.count(j.uuid(), Coin.GOLD) == 0 && comptes.count(j.uuid(), Coin.SILVER) == 50
                    && comptes.total(j.uuid()) == avantDouble, "deux clics : " + comptes.balance(j.uuid()));
            // Un autre joueur sans droit ne change rien.
            JoueurDeTest autre = essai.joueur("cf_change_autre");
            helper.assertTrue(agir(autre, new SafeActionPacket(abs, CoinRequest.groupPair(Coin.GOLD, true))) == Result.NOT_ALLOWED, "sans droit");
            helper.assertTrue(comptes.count(j.uuid(), Coin.SILVER) == 50, "le compte n'a pas bougé");
            helper.succeed();
        }
    }
}
