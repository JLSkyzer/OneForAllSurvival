package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.town.TreasuryActionPacket;
import dev.oneforallsurvival.economy.town.TreasuryBlock;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.economy.town.TreasuryLedger.Kind;
import dev.oneforallsurvival.economy.town.TreasuryMenu;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le bloc du trésor (lot E3, tâche 2) : pose unique, ouverture réservée, dépense motivée, dépôt noté, change non noté,
 * casse et repose, créatif, bloc hors emplacement, explosion, piston, paquet.
 *
 * <p>Ces tests touchent l'emplacement et le solde du trésor du serveur : leurs lots sont à eux. {@code @BeforeBatch}
 * range l'emplacement noté (un trésor posé ailleurs dans le monde d'essai ne gêne pas), {@code @AfterBatch} le rend.
 * Chaque test est synchrone (sauf le piston, qui ne pose rien de noté) et rend en fin de test ce qu'il a changé :
 * opérateurs, rôles, maire, solde, blocs. Les lignes ajoutées au registre du trésor restent.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TresorBlocGameTests {

    private static final String LOT_A = "tresor_bloc_a";
    private static final String LOT_B = "tresor_bloc_b";
    private static final String LOT_C = "tresor_bloc_c";

    private static final String MSG = TreasuryBlock.MESSAGE;

    /** Où le trésor se pose (case d'air ; le sol est un bloc plus bas), loin du joueur. */
    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(1, 0, 6);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    /** L'emplacement noté avant le lot, rendu après. */
    @Nullable
    private static GlobalPos emplacementAvant;

    private TresorBlocGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Avant et après les lots
    // ---------------------------------------------------------------------------------------------

    private static void ranger(ServerLevel level) {
        TreasuryAccount tresor = TreasuryAccount.get(level.getServer());
        emplacementAvant = tresor.position().orElse(null);
        tresor.position().ifPresent(tresor::clearPosition);
    }

    private static void rendre(ServerLevel level) {
        TreasuryAccount tresor = TreasuryAccount.get(level.getServer());
        tresor.position().ifPresent(tresor::clearPosition);
        if (emplacementAvant != null) {
            tresor.setPosition(emplacementAvant);
            emplacementAvant = null;
        }
    }

    @BeforeBatch(batch = LOT_A)
    public static void avantA(ServerLevel level) {
        ranger(level);
    }

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        rendre(level);
    }

    @BeforeBatch(batch = LOT_B)
    public static void avantB(ServerLevel level) {
        ranger(level);
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        rendre(level);
    }

    @BeforeBatch(batch = LOT_C)
    public static void avantC(ServerLevel level) {
        ranger(level);
    }

    @AfterBatch(batch = LOT_C)
    public static void apresC(ServerLevel level) {
        rendre(level);
    }

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
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone(helper), e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    private static AABB zone(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        return new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
    }

    /** Ce qu'un test a changé : tout est rendu à la fermeture, même si le test a échoué. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final Map<UUID, String> rolesAvant = new LinkedHashMap<>();
        private boolean maireTouche;
        @Nullable
        private UUID maireAvant;
        @Nullable
        private EnumMap<Coin, Long> soldeAvant;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        TreasuryAccount tresor() {
            return TreasuryAccount.get(serveur());
        }

        RoleBook roles() {
            return RoleSavedData.get(serveur()).book();
        }

        /** Le solde du trésor sera rendu tel quel en fin de test. */
        TreasuryAccount garderTresor() {
            if (soldeAvant == null) {
                soldeAvant = tresor().balance();
            }
            return tresor();
        }

        /** Un citoyen de survie, au milieu de l'aire. */
        JoueurDeTest citoyen(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            role(j, RoleBook.DEFAULT_ROLE_ID);
            return j;
        }

        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = citoyen(nom);
            role(j, RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        /** Le maire du serveur, jusqu'à la fermeture. */
        JoueurDeTest maire(String nom) {
            JoueurDeTest j = citoyen(nom);
            if (!maireTouche) {
                maireTouche = true;
                maireAvant = roles().mayor().orElse(null);
            }
            roles().appointMayor(j.uuid());
            return j;
        }

        /** Un joueur de l'équipe (niveau 4 écrit à la main, voir les leçons), jusqu'à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = citoyen(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        private void role(JoueurDeTest j, String id) {
            rolesAvant.putIfAbsent(j.uuid(), roles().roleOf(j.uuid()).id());
            roles().assign(j.uuid(), id);
        }

        @Override
        public void close() {
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            rolesAvant.forEach((uuid, id) -> roles().assign(uuid, id));
            if (maireTouche) {
                if (maireAvant != null) {
                    roles().appointMayor(maireAvant);
                } else {
                    roles().removeMayor();
                }
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            // Les blocs d'abord (leur retrait efface l'emplacement noté), puis le solde.
            boite(helper);
            TreasuryAccount tresor = tresor();
            tresor.position().ifPresent(tresor::clearPosition);
            if (soldeAvant != null) {
                for (Coin coin : Coin.values()) {
                    long diff = soldeAvant.get(coin) - tresor.count(coin);
                    if (diff < 0L) {
                        tresor.remove(coin, -diff);
                    } else if (diff > 0L) {
                        tresor.add(coin, diff);
                    }
                }
            }
        }
    }

    /** Pose un trésor comme le ferait le joueur : la pile en main, un clic sur le sol sous {@code pos}. */
    private static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, ItemStack pile, BlockPos pos) {
        ServerPlayer p = j.joueur();
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    private static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        return poser(helper, j, new ItemStack(EconomyRegistry.TREASURY_ITEM.get()), pos);
    }

    private static void poserEtVerifier(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        InteractionResult r = poser(helper, j, pos);
        helper.assertTrue(r.consumesAction() && helper.getBlockState(pos).is(EconomyRegistry.TREASURY.get()),
                "le trésor devait se poser : " + r + ", vu " + helper.getBlockState(pos));
    }

    private static GlobalPos global(GameTestHelper helper, BlockPos pos) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    /** Un clic droit réel, par le jeu : la garde de l'argent, puis le bloc. */
    private static void clicDroit(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    private static boolean ouvert(JoueurDeTest j) {
        return j.joueur().containerMenu instanceof TreasuryMenu;
    }

    private static long porte(JoueurDeTest j, Coin coin) {
        return PlayerCoins.held(j.joueur()).getOrDefault(coin, 0L);
    }

    private static Result agir(JoueurDeTest j, BlockPos abs, CoinRequest demande, String motif) {
        return TreasuryActionPacket.apply(j.joueur(), new TreasuryActionPacket(abs, demande, motif));
    }

    @Nullable
    private static TreasuryLedger.Line derniere(TreasuryAccount tresor) {
        List<TreasuryLedger.Line> page = tresor.ledger().page(0);
        return page.isEmpty() ? null : page.get(0);
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper));
    }

    /** Le seul objet au sol est un trésor (aucune pièce). */
    private static void seulLeTresorTombe(GameTestHelper helper) {
        List<ItemEntity> lache = objets(helper);
        helper.assertTrue(lache.size() == 1 && lache.get(0).getItem().is(EconomyRegistry.TREASURY_ITEM.get())
                && lache.get(0).getItem().getCount() == 1, "seul le bloc tombe : " + lache);
    }

    private static void ramasserLesObjets(GameTestHelper helper) {
        objets(helper).forEach(Entity::discard);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : pose, ouverture, dépense, dépôt, change
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEquipePoseLeTresor(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_pose_chef");
            TreasuryAccount tresor = essai.tresor();
            int lignes = tresor.ledger().size();
            helper.assertTrue(!TreasuryBlock.hasLiveTreasury(essai.serveur()), "aucun trésor vivant au départ");
            poserEtVerifier(helper, chef, A);
            helper.assertTrue(tresor.position().equals(Optional.of(global(helper, A))),
                    "l'emplacement n'est pas noté : " + tresor.position());
            helper.assertTrue(TreasuryBlock.hasLiveTreasury(essai.serveur()), "le trésor est vivant");
            helper.assertTrue(tresor.ledger().size() == lignes + 1, "une ligne de plus");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne != null && ligne.kind() == Kind.PLACED && chef.uuid().equals(ligne.actor())
                    && ligne.reason().equals(helper.absolutePos(A).toShortString()), "ligne PLACED : " + ligne);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCitoyenNePosePasLeTresor(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest citoyen = essai.citoyen("tb_pose_citoyen");
            ServerLevel level = helper.getLevel();
            citoyen.messagesRecus();
            InteractionResult r = poser(helper, citoyen, A);
            helper.assertTrue(!r.consumesAction() && helper.getBlockState(A).isAir(), "un citoyen ne pose pas : " + r);
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "place_denied"), "message de refus attendu");
            helper.assertTrue(citoyen.joueur().getMainHandItem().is(EconomyRegistry.TREASURY_ITEM.get()),
                    "l'objet reste en main");
            helper.assertTrue(essai.tresor().position().isEmpty(), "rien n'est noté");

            // Sans joueur (distributeur) et par un faux joueur (automate) : refusé.
            ItemStack pile = new ItemStack(EconomyRegistry.TREASURY_ITEM.get());
            InteractionResult distributeur = ((BlockItem) pile.getItem())
                    .place(new DirectionalPlaceContext(level, helper.absolutePos(B), Direction.DOWN, pile, Direction.UP));
            helper.assertTrue(!distributeur.consumesAction() && helper.getBlockState(B).isAir(),
                    "sans joueur, rien ne se pose : " + distributeur);
            FakePlayer faux = FakePlayerFactory.getMinecraft(level);
            BlockPos sol = helper.absolutePos(B.below());
            InteractionResult automate = pile.useOn(new UseOnContext(level, faux, InteractionHand.MAIN_HAND, pile,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
            helper.assertTrue(!automate.consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas : " + automate);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unSecondTresorEstRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_second_chef");
            JoueurDeTest maire = essai.maire("tb_second_maire");
            poserEtVerifier(helper, chef, A);
            chef.messagesRecus();
            ItemStack pile = new ItemStack(EconomyRegistry.TREASURY_ITEM.get());
            InteractionResult r = poser(helper, chef, pile, B);
            helper.assertTrue(!r.consumesAction() && helper.getBlockState(B).isAir(), "le second ne se pose pas : " + r);
            helper.assertTrue(chef.messagesRecus().contains(MSG + "exists"), "message « existe déjà » attendu");
            helper.assertTrue(pile.getCount() == 1 && chef.joueur().getMainHandItem() == pile, "l'objet reste en main");
            maire.messagesRecus();
            InteractionResult m = poser(helper, maire, B);
            helper.assertTrue(!m.consumesAction() && helper.getBlockState(B).isAir(), "le maire non plus : " + m);
            helper.assertTrue(maire.messagesRecus().contains(MSG + "exists"), "message « existe déjà » au maire");
            helper.assertTrue(essai.tresor().position().equals(Optional.of(global(helper, A))), "le premier reste noté");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void seulsLeMaireEtLEquipeOuvrent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_ouv_chef");
            JoueurDeTest maire = essai.maire("tb_ouv_maire");
            JoueurDeTest citoyen = essai.citoyen("tb_ouv_citoyen");
            JoueurDeTest inge = essai.ingenieur("tb_ouv_inge");
            poserEtVerifier(helper, chef, A);
            for (JoueurDeTest refuse : List.of(citoyen, inge)) {
                refuse.messagesRecus();
                clicDroit(helper, refuse, A);
                helper.assertTrue(!ouvert(refuse), refuse.joueur().getGameProfile().getName() + " ne doit pas ouvrir");
                helper.assertTrue(refuse.messagesRecus().contains(MSG + "open_denied"), "message de refus attendu");
            }
            // Le maire ouvre, même une pièce en main.
            maire.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Coin.GOLD.item(), 1));
            clicDroit(helper, maire, A);
            helper.assertTrue(ouvert(maire), "le maire ouvre");
            helper.assertTrue(MoneyStorageRules.isAllowedMenu(maire.joueur().containerMenu), "rangement permis");
            clicDroit(helper, chef, A);
            helper.assertTrue(ouvert(chef), "l'équipe ouvre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unRetraitSansMotifEstRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_sans_motif");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 2L);
            poserEtVerifier(helper, chef, A);
            clicDroit(helper, chef, A);
            helper.assertTrue(ouvert(chef), "l'équipe ouvre");
            Map<Coin, Long> avant = tresor.balance();
            int lignes = tresor.ledger().size();
            long depenses = EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.TREASURY_SPEND);
            BlockPos abs = helper.absolutePos(A);
            String tropLong = "x".repeat(EconomyConfig.treasuryReasonMax() + 1);
            for (String motif : List.of("", "   ", "ligne\nautre", tropLong)) {
                Result r = agir(chef, abs, CoinRequest.withdraw(Coin.GOLD, 1), motif);
                helper.assertTrue(r == Result.BAD_REQUEST, "motif « " + motif.length() + " car. » : " + r);
            }
            helper.assertTrue(tresor.balance().equals(avant) && porte(chef, Coin.GOLD) == 0L, "rien n'a bougé");
            helper.assertTrue(tresor.ledger().size() == lignes, "rien n'est noté au registre du trésor");
            helper.assertTrue(EconomyLedger.get(essai.serveur()).totalTransferred(MoneyTransfer.TREASURY_SPEND) == depenses,
                    "rien n'est noté au registre global");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unRetraitMotiveEstUneDepensePublique(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_motif_chef");
            JoueurDeTest maire = essai.maire("tb_motif_maire");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 2L);
            poserEtVerifier(helper, chef, A);
            clicDroit(helper, maire, A);
            helper.assertTrue(ouvert(maire), "le maire ouvre");
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long circulation = global.inCirculation();
            long depenses = global.totalTransferred(MoneyTransfer.TREASURY_SPEND);
            long or = tresor.count(Coin.GOLD);
            Result r = agir(maire, helper.absolutePos(A), CoinRequest.withdraw(Coin.GOLD, 1), "Achat de planches");
            helper.assertTrue(r == Result.DONE, "retrait motivé : " + r);
            helper.assertTrue(porte(maire, Coin.GOLD) == 1L && tresor.count(Coin.GOLD) == or - 1L, "1 or est sorti");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne != null && ligne.kind() == Kind.SPEND && ligne.amount() == 100L
                    && "Achat de planches".equals(ligne.reason()) && maire.uuid().equals(ligne.actor()),
                    "ligne SPEND de 100 avec le motif : " + ligne);
            helper.assertTrue(global.totalTransferred(MoneyTransfer.TREASURY_SPEND) - depenses == 100L,
                    "transfert TREASURY_SPEND de 100");
            EconomyLedger.Entry entree = global.entries(0).get(0);
            helper.assertTrue(entree.reason().equals("tb_motif_maire : Achat de planches")
                    && maire.uuid().equals(entree.author()), "motif au registre global : " + entree);
            helper.assertTrue(global.inCirculation() == circulation, "aucune pièce créée ni détruite");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unDepotEstNote(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_depot_chef");
            TreasuryAccount tresor = essai.garderTresor();
            poserEtVerifier(helper, chef, A);
            clicDroit(helper, chef, A);
            chef.joueur().getInventory().add(new ItemStack(Coin.SILVER.item(), 3));
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            long depots = global.totalTransferred(MoneyTransfer.TREASURY_DEPOSIT);
            long circulation = global.inCirculation();
            long total = tresor.total();
            tresor.takeFundsArrived();
            Result r = agir(chef, helper.absolutePos(A), CoinRequest.deposit(Coin.SILVER, 10), "");
            helper.assertTrue(r == Result.DONE, "dépôt : " + r);
            helper.assertTrue(tresor.total() - total == 30L && porte(chef, Coin.SILVER) == 0L, "30 déposés");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne != null && ligne.kind() == Kind.DEPOSIT && ligne.amount() == 30L
                    && chef.uuid().equals(ligne.actor()), "ligne DEPOSIT de 30 : " + ligne);
            helper.assertTrue(global.totalTransferred(MoneyTransfer.TREASURY_DEPOSIT) - depots == 30L,
                    "transfert TREASURY_DEPOSIT de 30");
            helper.assertTrue(global.inCirculation() == circulation, "aucune pièce créée ni détruite");
            helper.assertTrue(tresor.takeFundsArrived(), "l'arrivée de fonds est signalée (paie de la file)");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leChangeNeNoteRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_change_chef");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 1L);
            poserEtVerifier(helper, chef, A);
            clicDroit(helper, chef, A);
            EconomyLedger global = EconomyLedger.get(essai.serveur());
            int lignes = tresor.ledger().size();
            int entrees = global.entryCount();
            long total = tresor.total();
            BlockPos abs = helper.absolutePos(A);
            helper.assertTrue(agir(chef, abs, CoinRequest.breakCoin(Coin.GOLD, false), "") == Result.DONE, "casser 1 or");
            helper.assertTrue(agir(chef, abs, CoinRequest.groupPair(Coin.GOLD, false), "") == Result.DONE, "regrouper");
            helper.assertTrue(agir(chef, abs, CoinRequest.breakCoin(Coin.GOLD, true), "") == Result.DONE, "casser tout");
            helper.assertTrue(agir(chef, abs, CoinRequest.groupAll(), "") == Result.DONE, "regrouper tout");
            helper.assertTrue(tresor.total() == total, "la valeur ne change pas");
            helper.assertTrue(tresor.ledger().size() == lignes, "rien au registre du trésor");
            helper.assertTrue(global.entryCount() == entrees, "rien au registre global");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : casse, repose, créatif, sans emplacement, bloc hors emplacement
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lIngenieurLeMaireEtLEquipeLeCassent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_casse_chef");
            JoueurDeTest maire = essai.maire("tb_casse_maire");
            JoueurDeTest inge = essai.ingenieur("tb_casse_inge");
            // Lot T2 : l'ingénieur ne déplace un bloc de la ville qu'avec l'outil de maintenance en main.
            inge.joueur().setItemInHand(InteractionHand.OFF_HAND,
                    new ItemStack(dev.oneforallsurvival.repair.RepairRegistry.MAINTENANCE_TOOL.get()));
            BlockPos abs = helper.absolutePos(A);
            for (JoueurDeTest casseur : List.of(inge, maire, chef)) {
                String nom = casseur.joueur().getGameProfile().getName();
                poserEtVerifier(helper, chef, A);
                BlockState etat = helper.getBlockState(A);
                helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), abs) < 0, "dureté réservée (-1)");
                helper.assertTrue(etat.getDestroyProgress(casseur.joueur(), helper.getLevel(), abs) > 0.0F,
                        nom + " a une progression");
                helper.assertTrue(casseur.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                        nom + " le casse");
                seulLeTresorTombe(helper);
                ramasserLesObjets(helper);
                helper.assertTrue(essai.tresor().position().isEmpty(), "emplacement effacé après " + nom);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unCitoyenNeLeCassePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_nocasse_chef");
            JoueurDeTest citoyen = essai.citoyen("tb_nocasse_citoyen");
            JoueurDeTest createur = essai.citoyen("tb_nocasse_creatif");
            createur.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            createur.joueur().setShiftKeyDown(true);
            poserEtVerifier(helper, chef, A);
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroyProgress(citoyen.joueur(), helper.getLevel(), abs) == 0.0F,
                    "pas de progression pour un citoyen");
            citoyen.messagesRecus();
            helper.assertTrue(!citoyen.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TREASURY.get()), "un citoyen ne le casse pas");
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "break_denied"), "message de refus attendu");
            helper.assertTrue(!createur.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TREASURY.get()), "un citoyen en créatif non plus");
            helper.assertTrue(essai.tresor().position().equals(Optional.of(global(helper, A))), "toujours noté");
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void casseLeContenuResteEtLaPositionSEfface(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_contenu_chef");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 3L);
            poserEtVerifier(helper, chef, A);
            long total = tresor.total();
            BlockPos abs = helper.absolutePos(A);
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(), "cassé");
            seulLeTresorTombe(helper);
            helper.assertTrue(tresor.total() == total, "le solde ne bouge pas : " + tresor.total());
            helper.assertTrue(tresor.position().isEmpty(), "l'emplacement est effacé");
            helper.assertTrue(!TreasuryBlock.hasLiveTreasury(essai.serveur()), "plus de trésor vivant");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne != null && ligne.kind() == Kind.REMOVED && chef.uuid().equals(ligne.actor())
                    && ligne.reason().equals(abs.toShortString()), "ligne REMOVED : " + ligne);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void reposeIlRedevientLeTresor(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_repose_chef");
            JoueurDeTest maire = essai.maire("tb_repose_maire");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.SILVER, 7L);
            poserEtVerifier(helper, chef, A);
            long total = tresor.total();
            int lignes = tresor.ledger().size();
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(helper.absolutePos(A)), "cassé par l'équipe");
            ItemStack objet = objets(helper).get(0).getItem().copy();
            ramasserLesObjets(helper);
            InteractionResult r = poser(helper, maire, objet, B);
            helper.assertTrue(r.consumesAction() && helper.getBlockState(B).is(EconomyRegistry.TREASURY.get()),
                    "le maire repose le trésor : " + r);
            helper.assertTrue(tresor.position().equals(Optional.of(global(helper, B))), "nouvel emplacement noté");
            helper.assertTrue(tresor.total() == total, "même solde");
            helper.assertTrue(tresor.ledger().size() == lignes + 2, "même registre, deux lignes de plus");
            List<TreasuryLedger.Line> page = tresor.ledger().page(0);
            helper.assertTrue(page.get(0).kind() == Kind.PLACED && maire.uuid().equals(page.get(0).actor())
                    && page.get(1).kind() == Kind.REMOVED, "REMOVED puis PLACED : " + page);
            clicDroit(helper, maire, B);
            helper.assertTrue(maire.joueur().containerMenu instanceof TreasuryMenu menu && menu.total() == total,
                    "l'écran montre le même solde");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unObjetRamassePasUnCitoyenNeSePosePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_ramasse_chef");
            JoueurDeTest citoyen = essai.citoyen("tb_ramasse_citoyen");
            JoueurDeTest maire = essai.maire("tb_ramasse_maire");
            poserEtVerifier(helper, chef, A);
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(helper.absolutePos(A)), "cassé par l'équipe");
            ItemStack objet = objets(helper).get(0).getItem().copy();
            ramasserLesObjets(helper);
            citoyen.messagesRecus();
            InteractionResult r = poser(helper, citoyen, objet, B);
            helper.assertTrue(!r.consumesAction() && helper.getBlockState(B).isAir(), "un citoyen ne le repose pas");
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "place_denied"), "message de refus attendu");
            helper.assertTrue(objet.getCount() == 1, "l'objet lui reste");
            // Pendant que l'objet traîne, l'équipe pose un trésor neuf : le seul.
            poserEtVerifier(helper, chef, B);
            // Le maire, avec l'objet ramassé, n'en fait pas un second.
            maire.messagesRecus();
            InteractionResult m = poser(helper, maire, objet, A);
            helper.assertTrue(!m.consumesAction() && helper.getBlockState(A).isAir(), "pas de second trésor");
            helper.assertTrue(maire.messagesRecus().contains(MSG + "exists"), "message « existe déjà »");
            helper.assertTrue(essai.tresor().position().equals(Optional.of(global(helper, B))), "B reste le trésor");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void enCreatifIlFautSAccroupir(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_creatif_chef");
            chef.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.EMERALD, 1L);
            poserEtVerifier(helper, chef, A);
            long total = tresor.total();
            BlockPos abs = helper.absolutePos(A);
            chef.joueur().setShiftKeyDown(false);
            chef.messagesRecus();
            helper.assertTrue(!chef.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TREASURY.get()), "non accroupi : refusé");
            helper.assertTrue(chef.messagesRecus().contains(MSG + "crouch"), "message « accroupissez-vous »");
            chef.joueur().setShiftKeyDown(true);
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                    "accroupi : cassé");
            helper.assertTrue(objets(helper).isEmpty(), "en créatif, aucun objet ne tombe");
            helper.assertTrue(tresor.position().isEmpty() && tresor.total() == total, "emplacement effacé, solde intact");
            // Un trésor neuf de l'onglet reprend le trésor unique.
            chef.joueur().setShiftKeyDown(false);
            poserEtVerifier(helper, chef, B);
            helper.assertTrue(tresor.position().equals(Optional.of(global(helper, B))), "le neuf est noté");
            clicDroit(helper, chef, B);
            helper.assertTrue(chef.joueur().containerMenu instanceof TreasuryMenu menu && menu.total() == total,
                    "le neuf montre le même solde");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void sansEmplacementLaTaxeArriveQuandMeme(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest client = essai.citoyen("tb_taxe_client");
            TreasuryAccount tresor = essai.garderTresor();
            helper.assertTrue(tresor.position().isEmpty() && !TreasuryBlock.hasLiveTreasury(essai.serveur()),
                    "aucun trésor posé");
            long total = tresor.total();
            // Le chemin de la taxe de l'étal (StallTrade.buy) : un crédit TAX, sans rien demander au bloc.
            tresor.credit(CoinMath.decompose(5L), Kind.TAX, client.uuid(), "tb_taxe_client", "");
            helper.assertTrue(tresor.total() - total == 5L, "la taxe arrive sans bloc posé");
            TreasuryLedger.Line ligne = derniere(tresor);
            helper.assertTrue(ligne != null && ligne.kind() == Kind.TAX && ligne.amount() == 5L, "ligne TAX : " + ligne);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unBlocHorsEmplacementNOuvreRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_hors_chef");
            helper.setBlock(A, EconomyRegistry.TREASURY.get());
            helper.assertTrue(essai.tresor().position().isEmpty(), "un bloc posé par setBlock n'est pas noté");
            helper.assertTrue(!TreasuryBlock.hasLiveTreasury(essai.serveur()), "et ne compte pas comme trésor vivant");
            chef.messagesRecus();
            clicDroit(helper, chef, A);
            helper.assertTrue(!ouvert(chef), "un bloc hors emplacement n'ouvre rien");
            helper.assertTrue(chef.messagesRecus().contains(MSG + "not_the_treasury"), "message attendu");
            // Le vrai trésor peut se poser ailleurs ; le bloc de setBlock reste muet.
            poserEtVerifier(helper, chef, B);
            clicDroit(helper, chef, A);
            helper.assertTrue(!ouvert(chef), "toujours rien en A");
            clicDroit(helper, chef, B);
            helper.assertTrue(ouvert(chef), "B s'ouvre");
            helper.assertTrue(agir(chef, helper.absolutePos(A), CoinRequest.groupAll(), "") == Result.NO_SAFE,
                    "le paquet vers A est refusé");
            // Casser le bloc muet n'efface pas l'emplacement du vrai, et (revue finale, M6) ne lâche aucun objet.
            ramasserLesObjets(helper);
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(helper.absolutePos(A)) && helper.getBlockState(A).isAir(),
                    "le bloc muet se casse");
            helper.assertTrue(objets(helper).isEmpty(), "un bloc hors emplacement ne lâche rien : " + objets(helper));
            helper.assertTrue(essai.tresor().position().equals(Optional.of(global(helper, B))), "B reste noté");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : explosion, piston, paquet
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void explosionEtPistonSansEffet(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos tresor = new BlockPos(3, 0, 3);
        BlockPos temoin = new BlockPos(2, 0, 3);
        BlockPos centre = new BlockPos(2, 0, 4);
        helper.setBlock(tresor, EconomyRegistry.TREASURY.get());
        helper.setBlock(temoin, Blocks.DIRT);
        BlockState etat = helper.getBlockState(tresor);
        helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE), "tags Wither et dragon");
        helper.assertTrue(etat.getPistonPushReaction() == PushReaction.BLOCK, "le piston est bloqué");
        Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
        helper.getLevel().explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
        helper.assertTrue(helper.getBlockState(tresor).is(EconomyRegistry.TREASURY.get()), "le trésor résiste");
        // Le piston : un trésor devant, et un témoin qui pousse de la terre.
        boite(helper);
        BlockPos piston = new BlockPos(1, 0, 2);
        BlockPos bloc = new BlockPos(2, 0, 2);
        BlockPos pistonTemoin = new BlockPos(1, 0, 4);
        helper.setBlock(bloc, EconomyRegistry.TREASURY.get());
        helper.setBlock(new BlockPos(2, 0, 4), Blocks.DIRT);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 4)).is(Blocks.DIRT),
                        "le piston témoin devait pousser la terre"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(helper.getBlockState(bloc).is(EconomyRegistry.TREASURY.get()),
                                "le trésor ne devait pas bouger");
                        helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                                "le piston bloqué ne devait pas s'étendre");
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void unAutrePaquetQueLeMenuOuvertEstRefuse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_paquet_chef");
            JoueurDeTest citoyen = essai.citoyen("tb_paquet_citoyen");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.COPPER, 10L);
            poserEtVerifier(helper, chef, A);
            BlockPos abs = helper.absolutePos(A);
            CoinRequest retrait = CoinRequest.withdraw(Coin.COPPER, 1);
            helper.assertTrue(agir(chef, abs, retrait, "essai") == Result.NOT_OPEN, "sans écran ouvert");
            clicDroit(helper, chef, A);
            helper.assertTrue(agir(chef, abs, retrait, "essai") == Result.DONE, "écran ouvert, à portée");
            BlockPos horsMonde = new BlockPos(1_000_000, 64, 1_000_000);
            helper.assertTrue(agir(chef, horsMonde, retrait, "essai") == Result.TOO_FAR, "chunk non chargé");
            helper.assertTrue(!helper.getLevel().isLoaded(horsMonde), "le paquet ne charge pas le chunk");
            chef.joueur().moveTo(helper.absoluteVec(JOUEUR).add(40, 0, 0));
            helper.assertTrue(agir(chef, abs, retrait, "essai") == Result.TOO_FAR, "trop loin");
            chef.joueur().moveTo(helper.absoluteVec(JOUEUR));
            helper.assertTrue(agir(citoyen, abs, retrait, "essai") == Result.NOT_ALLOWED, "un citoyen n'a pas le droit");
            helper.assertTrue(agir(chef, abs, new CoinRequest(CoinRequest.WITHDRAW, 0, 7, false), "essai")
                    == Result.BAD_REQUEST, "un montant hors des boutons");
            helper.assertTrue(agir(chef, helper.absolutePos(B), retrait, "essai") == Result.NO_SAFE, "pas de trésor en B");
            // Revue finale, M1 : le droit d'abord, comme à l'ouverture ; un citoyen n'apprend pas où est le trésor.
            helper.assertTrue(agir(citoyen, helper.absolutePos(B), retrait, "essai") == Result.NOT_ALLOWED,
                    "un citoyen sans droit, devant un bloc qui n'est pas le trésor : NOT_ALLOWED");
            helper.assertTrue(tresor.count(Coin.COPPER) == 9L && porte(chef, Coin.COPPER) == 1L, "un seul retrait a passé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void unMaireDestitueEcranOuvertNeRetireRien(GameTestHelper helper) {
        // Revue finale, M5 : le droit se relit à chaque paquet ; l'écran ouvert ne vaut pas droit.
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tb_destitue_chef");
            JoueurDeTest maire = essai.maire("tb_destitue_maire");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 2L);
            poserEtVerifier(helper, chef, A);
            clicDroit(helper, maire, A);
            helper.assertTrue(ouvert(maire), "le maire ouvre");
            BlockPos abs = helper.absolutePos(A);
            CoinRequest retrait = CoinRequest.withdraw(Coin.GOLD, 1);
            helper.assertTrue(agir(maire, abs, retrait, "avant") == Result.DONE, "maire : retrait permis");
            Map<Coin, Long> avant = tresor.balance();
            int lignes = tresor.ledger().size();
            essai.roles().removeMayor();
            TreasuryMenu menu = (TreasuryMenu) maire.joueur().containerMenu;
            helper.assertTrue(!menu.stillValid(maire.joueur()), "l'écran ne tient plus : il se ferme au tick suivant");
            Result r = agir(maire, abs, retrait, "apres");
            helper.assertTrue(r == Result.NOT_ALLOWED, "destitué, écran encore ouvert : refusé, vu " + r);
            helper.assertTrue(tresor.balance().equals(avant) && porte(maire, Coin.GOLD) == 1L
                    && tresor.ledger().size() == lignes, "rien n'a bougé ni n'est noté");
            helper.succeed();
        }
    }
}
