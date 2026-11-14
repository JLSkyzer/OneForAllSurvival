package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import dev.oneforallsurvival.economy.trade.TradeService;
import dev.oneforallsurvival.economy.trade.TradeService.RequestResult;
import dev.oneforallsurvival.economy.trade.TradeSession;
import dev.oneforallsurvival.economy.trade.TradeSession.ConfirmResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'échange de main à main (lot E2, tâche 2) : demande, fenêtre à deux côtés, confirmations sur la version vue,
 * cases de l'autre intouchables, départ (fermeture, éloignement, mort, déconnexion) qui rend tout à son
 * propriétaire, inventaire plein, pièces des bourses, bourse pleine et reçu. Les gestes passent par
 * {@link TradeService}, par le paquet du client ({@link JoueurDeTest#envoyer}) et par {@code clicked} sur le menu,
 * comme le ferait le client.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class EchangeGameTests {

    private static final String LOT = "echange";
    private static final String LOT_B = "echange_b";
    private static final String LOT_C = "echange_c";
    private static final BlockPos COFFRE = new BlockPos(1, 0, 1);

    private static final Vec3 POS_A = new Vec3(2.5, 0, 4.5);
    private static final Vec3 POS_B = new Vec3(5.5, 0, 4.5);
    private static final Vec3 POS_C = new Vec3(4.5, 0, 2.5);

    private EchangeGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Une aire close : sol, pourtour, plafond, vidée de tout ce qui n'est pas un joueur. */
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

    /** Ce qu'un test a ouvert : tout est rendu à la fermeture, même si le test a échoué. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> comptes = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        /** Un joueur de survie neuf (nom propre à l'essai, 16 caractères au plus), inventaire vide. */
        JoueurDeTest joueur(String court, Vec3 position) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
            comptes.add(uuid);
            EconomyLedger.get(serveur()).markStartingGiven(uuid);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /**
         * Le même joueur revient : son playerdata est relu du disque (inventaire non vidé), comme à une connexion
         * après un redémarrage.
         */
        JoueurDeTest revenir(JoueurDeTest parti, Vec3 position) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), parti.joueur().getGameProfile().getName(),
                    helper.absoluteVec(position), false, p -> {});
            joueurs.add(j);
            return j;
        }

        @Override
        public void close() {
            for (JoueurDeTest j : joueurs) {
                TradeSession session = TradeService.sessionOf(j.uuid());
                if (session != null) {
                    TradeService.cancel(session, TradeService.CancelReason.SERVER_STOP);
                }
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            boite(helper);
            HeldGoods held = HeldGoods.get(serveur());
            SafeAccounts coffres = SafeAccounts.get(serveur());
            for (UUID uuid : comptes) {
                held.takePending(uuid);
                for (Coin coin : Coin.values()) {
                    coffres.withdraw(uuid, coin, coffres.count(uuid, coin));
                }
                coffres.forgetSafePos(uuid);
            }
        }
    }

    /** Ouvre un échange entre {@code a} et {@code b} : demande, acceptation, fenêtres ouvertes. */
    private static TradeSession ouvrir(GameTestHelper helper, JoueurDeTest a, JoueurDeTest b) {
        RequestResult demande = TradeService.request(a.joueur(), b.joueur());
        helper.assertTrue(demande == RequestResult.SENT, "demande : " + demande);
        RequestResult accord = TradeService.accept(b.joueur(), a.joueur());
        helper.assertTrue(accord == RequestResult.ACCEPTED, "acceptation : " + accord);
        TradeSession session = TradeService.sessionOf(a.uuid());
        helper.assertTrue(session != null && session == TradeService.sessionOf(b.uuid()), "une session pour les deux");
        helper.assertTrue(menu(a) != null && menu(b) != null, "les deux fenêtres devaient s'ouvrir");
        return session;
    }

    private static TradeMenu menu(JoueurDeTest j) {
        return j.joueur().containerMenu instanceof TradeMenu m ? m : null;
    }

    private static boolean fermee(JoueurDeTest j) {
        return j.joueur().containerMenu == j.joueur().inventoryMenu;
    }

    /** Maj-clic sur la case {@code inv} de l'inventaire : la pile passe dans sa garde. */
    private static void poser(JoueurDeTest j, int inv) {
        menu(j).clicked(TradeMenu.slotOfInventory(inv), 0, ClickType.QUICK_MOVE, j.joueur());
    }

    private static int compte(ServerPlayer joueur, Item objet) {
        return joueur.getInventory().countItem(objet);
    }

    private static int compte(Container garde, Item objet) {
        return garde.countItem(objet);
    }

    private static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static long creeEtDetruit(EconomyLedger registre) {
        long total = 0L;
        for (MoneySource source : MoneySource.values()) {
            total += registre.totalCreated(source) * 31L;
        }
        for (MoneySink sink : MoneySink.values()) {
            total += registre.totalDestroyed(sink) * 17L;
        }
        return total;
    }

    private static List<ItemStack> copie(Container garde) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < garde.getContainerSize(); i++) {
            out.add(garde.getItem(i).copy());
        }
        return out;
    }

    private static boolean pareil(List<ItemStack> avant, Container garde) {
        for (int i = 0; i < garde.getContainerSize(); i++) {
            if (!ItemStack.matches(avant.get(i), garde.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean rienEnAttente(MinecraftServer serveur, JoueurDeTest... joueurs) {
        for (JoueurDeTest j : joueurs) {
            if (HeldGoods.get(serveur).hasPending(j.uuid())) {
                return false;
            }
        }
        return true;
    }

    private static int compte(List<ItemStack> piles, Item objet) {
        int total = 0;
        for (ItemStack pile : piles) {
            if (pile.is(objet)) {
                total += pile.getCount();
            }
        }
        return total;
    }

    private static int painsAuSol(GameTestHelper helper, AABB autour) {
        int total = 0;
        for (ItemEntity objet : helper.getLevel().getEntitiesOfClass(ItemEntity.class, autour)) {
            if (objet.getItem().is(Items.BREAD)) {
                total += objet.getItem().getCount();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------
    // Demandes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void demandeTropLoinRefusee(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_loinA", POS_A);
            JoueurDeTest b = essai.joueur("ec_loinB", POS_A.add(0, 0, 20));
            helper.assertTrue(TradeService.request(a.joueur(), a.joueur()) == RequestResult.SELF, "pas à soi-même");
            RequestResult r = TradeService.request(a.joueur(), b.joueur());
            helper.assertTrue(r == RequestResult.TOO_FAR, "à 20 blocs : " + r);
            helper.assertTrue(TradeService.accept(b.joueur(), a.joueur()) == RequestResult.NO_REQUEST,
                    "une demande refusée n'est pas gardée");
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "aucun échange");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void demandeExpireApres30s(GameTestHelper helper) {
        long[] maintenant = {5_000_000L};
        TradeService.setClock(() -> maintenant[0]);
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_expA", POS_A);
            JoueurDeTest b = essai.joueur("ec_expB", POS_B);
            helper.assertTrue(TradeService.request(a.joueur(), b.joueur()) == RequestResult.SENT, "demande");
            maintenant[0] += 31_000L;
            RequestResult r = TradeService.accept(b.joueur(), a.joueur());
            helper.assertTrue(r == RequestResult.EXPIRED, "après 31 s : " + r);
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null, "aucun échange après expiration");
            helper.assertTrue(TradeService.accept(b.joueur(), a.joueur()) == RequestResult.NO_REQUEST,
                    "une demande expirée est oubliée");

            helper.assertTrue(TradeService.request(a.joueur(), b.joueur()) == RequestResult.SENT, "nouvelle demande");
            maintenant[0] += 29_000L;
            r = TradeService.accept(b.joueur(), a.joueur());
            helper.assertTrue(r == RequestResult.ACCEPTED, "après 29 s : " + r);
            helper.assertTrue(TradeService.sessionOf(a.uuid()) != null, "l'échange est ouvert");
            helper.succeed();
        } finally {
            TradeService.resetClock();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void pasDeuxEchangesALaFois(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_deuxA", POS_A);
            JoueurDeTest b = essai.joueur("ec_deuxB", POS_B);
            JoueurDeTest c = essai.joueur("ec_deuxC", POS_C);
            TradeSession session = ouvrir(helper, a, b);
            helper.assertTrue(TradeService.request(c.joueur(), a.joueur()) == RequestResult.BUSY, "A est occupé");
            helper.assertTrue(TradeService.request(a.joueur(), c.joueur()) == RequestResult.BUSY, "A ne peut pas proposer");
            helper.assertTrue(TradeService.request(c.joueur(), b.joueur()) == RequestResult.BUSY, "B est occupé");
            helper.assertTrue(TradeService.request(b.joueur(), a.joueur()) == RequestResult.BUSY, "déjà ensemble");
            helper.assertTrue(TradeService.sessionOf(c.uuid()) == null && menu(c) == null, "C n'a rien d'ouvert");
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == session, "la première session reste");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Confirmations
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void unEchangeNALieuQuApresLesDeuxConfirmations(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_okA", POS_A);
            JoueurDeTest b = essai.joueur("ec_okB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            long echangeAvant = registre.totalTransferred(MoneyTransfer.TRADE);
            long creeAvant = creeEtDetruit(registre);
            long circulation = registre.inCirculation();

            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            helper.assertTrue(compte(session.offer(a.uuid()), Items.BREAD) == 5, "cinq pains en garde");
            helper.assertTrue(compte(session.offer(b.uuid()), Coin.GOLD.item()) == 2, "deux or en garde");
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 0 && valeurPortee(b) == 0L,
                    "ce qui est posé quitte l'inventaire");

            ConfirmResult une = session.confirm(a.joueur(), session.version());
            helper.assertTrue(une == ConfirmResult.WAITING, "une confirmation : " + une);
            helper.assertTrue(session.confirmed(a.uuid()) && !session.confirmed(b.uuid()), "A seul a confirmé");
            helper.assertTrue(valeurPortee(a) == 0L && compte(b.joueur(), Items.BREAD) == 0
                    && compte(session.offer(a.uuid()), Items.BREAD) == 5, "rien n'a bougé après une confirmation");

            b.envoyer(TradeActionPacket.confirm(session.version()));
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "l'échange est fini");
            helper.assertTrue(valeurPortee(a) == 200L, "A reçoit 2 or : " + valeurPortee(a));
            helper.assertTrue(compte(b.joueur(), Items.BREAD) == 5, "B reçoit 5 pains : " + compte(b.joueur(), Items.BREAD));
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 0 && valeurPortee(b) == 0L, "rien n'est doublé");
            helper.assertTrue(fermee(a) && fermee(b), "les deux fenêtres se ferment");
            helper.assertTrue(SauvegardeDEssai.gardeEnMemoire(a.joueur()).isEmpty()
                    && SauvegardeDEssai.gardeEnMemoire(b.joueur()).isEmpty(), "plus aucune garde");
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.TRADE) - echangeAvant == 200L,
                    "échange noté : " + (registre.totalTransferred(MoneyTransfer.TRADE) - echangeAvant));
            helper.assertTrue(creeEtDetruit(registre) == creeAvant && registre.inCirculation() == circulation,
                    "aucune pièce créée ni détruite");
            EconomyLedger.Entry ligne = registre.entries(0).get(0);
            helper.assertTrue(ligne.kind().equals(MoneyTransfer.TRADE.kind()) && ligne.amount() == 200L
                    && b.uuid().equals(ligne.author()) && ligne.reason().contains("->"),
                    "ligne du registre : " + ligne);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void toucherAuContenuAnnuleLesConfirmations(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_touA", POS_A);
            JoueurDeTest b = essai.joueur("ec_touB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            b.joueur().getInventory().items.set(1, new ItemStack(Coin.GOLD.item(), 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            int v0 = session.version();
            helper.assertTrue(session.confirm(a.joueur(), v0) == ConfirmResult.WAITING, "A confirme");

            // L'autre pose un objet : les deux confirmations tombent, la version avance.
            poser(b, 0);
            helper.assertTrue(session.version() > v0, "la version avance : " + session.version());
            helper.assertTrue(!session.confirmed(a.uuid()), "la confirmation de A tombe quand B pose");

            // Un clic qui ne change rien ne fait rien tomber.
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A reconfirme");
            int v1 = session.version();
            menu(a).clicked(5, 0, ClickType.PICKUP, a.joueur());
            helper.assertTrue(session.version() == v1 && session.confirmed(a.uuid()), "un clic sur une case vide ne change rien");

            // Le reçu coché par l'autre fait tomber les confirmations.
            b.envoyer(TradeActionPacket.toggleReceipt(session.version()));
            helper.assertTrue(session.receipt(), "le reçu est coché");
            helper.assertTrue(session.version() > v1 && !session.confirmed(a.uuid()), "le reçu fait tomber la confirmation");

            // Des pièces ajoutées par le bouton aussi.
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A reconfirme");
            int v2 = session.version();
            b.envoyer(TradeActionPacket.addCoins(Coin.GOLD, 1));
            helper.assertTrue(compte(session.offer(b.uuid()), Coin.GOLD.item()) == 1, "l'or est posé");
            helper.assertTrue(session.version() > v2 && !session.confirmed(a.uuid()), "les pièces font tomber la confirmation");

            // Et A qui reprend son pain.
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A reconfirme");
            int v3 = session.version();
            menu(a).clicked(0, 0, ClickType.PICKUP, a.joueur());
            helper.assertTrue(session.version() > v3 && !session.confirmed(a.uuid()), "reprendre fait tomber la confirmation");
            menu(a).clicked(TradeMenu.slotOfInventory(0), 0, ClickType.PICKUP, a.joueur());
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 5, "le pain est revenu dans l'inventaire");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneConfirmationSurUneVersionPerimeeEstRefusee(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_perA", POS_A);
            JoueurDeTest b = essai.joueur("ec_perB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            b.joueur().getInventory().items.set(1, new ItemStack(Items.DIRT, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            int vue = session.version();
            // B change au dernier moment, puis confirme le nouvel état.
            poser(b, 1);
            int nouvelle = session.version();
            helper.assertTrue(nouvelle != vue, "la version a changé");
            helper.assertTrue(session.confirm(b.joueur(), nouvelle) == ConfirmResult.WAITING, "B confirme");

            ConfirmResult r = session.confirm(a.joueur(), vue);
            helper.assertTrue(r == ConfirmResult.STALE, "la version vue par A est périmée : " + r);
            a.envoyer(TradeActionPacket.confirm(vue));
            helper.assertTrue(!session.confirmed(a.uuid()), "une confirmation périmée ne compte pas");
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == session, "l'échange continue");
            helper.assertTrue(compte(a.joueur(), Items.DIAMOND) == 0 && compte(a.joueur(), Items.DIRT) == 0
                    && compte(b.joueur(), Items.BREAD) == 0, "rien n'a bougé");
            helper.assertTrue(compte(session.offer(a.uuid()), Items.BREAD) == 5
                    && compte(session.offer(b.uuid()), Items.DIAMOND) == 1
                    && compte(session.offer(b.uuid()), Items.DIRT) == 1, "les gardes sont intactes");

            // Sur la bonne version, l'échange a lieu.
            helper.assertTrue(session.confirm(a.joueur(), nouvelle) == ConfirmResult.DONE, "la bonne version passe");
            helper.assertTrue(compte(a.joueur(), Items.DIAMOND) == 1 && compte(a.joueur(), Items.DIRT) == 1
                    && compte(b.joueur(), Items.BREAD) == 5, "échange fait sur l'état final");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void lesCasesDeLAutreNeSePrennentPas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_casA", POS_A);
            JoueurDeTest b = essai.joueur("ec_casB", POS_B);
            ServerPlayer pa = a.joueur();
            pa.getInventory().items.set(1, new ItemStack(Items.STICK, 1));
            pa.getInventory().offhand.set(0, new ItemStack(Items.APPLE, 1));
            pa.getInventory().items.set(9, new ItemStack(Items.BREAD, 3));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            TradeSession session = ouvrir(helper, a, b);
            poser(b, 0);
            Container autre = session.offer(b.uuid());
            List<ItemStack> avant = copie(autre);
            int version = session.version();
            TradeMenu m = menu(a);
            int premiere = TradeMenu.OTHER_START;

            m.clicked(premiere, 0, ClickType.PICKUP, pa);
            m.clicked(premiere, 1, ClickType.PICKUP, pa);
            helper.assertTrue(m.getCarried().isEmpty(), "le clic ne prend rien chez l'autre : " + m.getCarried());
            m.clicked(premiere, 0, ClickType.QUICK_MOVE, pa);
            m.clicked(premiere, 0, ClickType.THROW, pa);
            m.clicked(premiere, 1, ClickType.THROW, pa);
            m.clicked(premiere, 1, ClickType.SWAP, pa);
            m.clicked(premiere + 1, 1, ClickType.SWAP, pa);
            m.clicked(premiere, 40, ClickType.SWAP, pa);
            m.clicked(premiere + 2, 40, ClickType.SWAP, pa);
            helper.assertTrue(pa.getInventory().items.get(1).is(Items.STICK), "le bâton reste dans la barre");
            helper.assertTrue(pa.getInventory().offhand.get(0).is(Items.APPLE), "la pomme reste en main gauche");

            // Double-clic « tout ramasser » avec un pain au curseur : le pain de l'autre ne vient pas.
            m.clicked(TradeMenu.slotOfInventory(9), 0, ClickType.PICKUP, pa);
            helper.assertTrue(m.getCarried().is(Items.BREAD) && m.getCarried().getCount() == 3, "trois pains au curseur");
            m.clicked(TradeMenu.slotOfInventory(9), 0, ClickType.PICKUP_ALL, pa);
            m.clicked(premiere, 0, ClickType.PICKUP_ALL, pa);
            helper.assertTrue(m.getCarried().getCount() == 3, "tout ramasser ne prend rien chez l'autre : " + m.getCarried());

            // Glisser le pain sur les cases de l'autre.
            m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, pa);
            m.clicked(premiere + 1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, pa);
            m.clicked(premiere + 2, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, pa);
            m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, pa);
            // Et la même chose clic droit (un par case).
            m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 1), ClickType.QUICK_CRAFT, pa);
            m.clicked(premiere + 3, AbstractContainerMenu.getQuickcraftMask(1, 1), ClickType.QUICK_CRAFT, pa);
            m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 1), ClickType.QUICK_CRAFT, pa);
            helper.assertTrue(m.getCarried().getCount() == 3, "glisser ne pose rien chez l'autre : " + m.getCarried());
            m.clicked(TradeMenu.slotOfInventory(9), 0, ClickType.PICKUP, pa);

            helper.assertTrue(pareil(avant, autre), "la garde de l'autre est intacte");
            helper.assertTrue(compte(autre, Items.BREAD) == 5 && compte(autre, Items.STICK) == 0
                    && compte(autre, Items.APPLE) == 0, "rien n'entre chez l'autre");
            helper.assertTrue(compte(pa, Items.BREAD) == 3, "A garde ses trois pains : " + compte(pa, Items.BREAD));
            helper.assertTrue(session.version() == version, "la version n'a pas bougé");
            helper.assertTrue(painsAuSol(helper, zone(helper)) == 0, "rien n'est jeté au sol");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Départs
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void fermerRendToutASonProprietaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_ferA", POS_A);
            JoueurDeTest b = essai.joueur("ec_ferB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            a.joueur().getInventory().items.set(1, new ItemStack(Coin.GOLD.item(), 1));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(a, 1);
            poser(b, 0);
            // A tient au curseur un objet de sa garde au moment de fermer.
            a.joueur().getInventory().items.set(2, new ItemStack(Items.DIRT, 4));
            menu(a).clicked(TradeMenu.slotOfInventory(2), 0, ClickType.PICKUP, a.joueur());
            a.messagesRecus();
            b.messagesRecus();

            a.joueur().closeContainer();
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "l'échange est annulé");
            helper.assertTrue(fermee(b), "la fenêtre de l'autre se ferme");
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 5 && valeurPortee(a) == 100L
                    && compte(a.joueur(), Items.DIRT) == 4, "A récupère tout");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
            helper.assertTrue(compte(a.joueur(), Items.DIAMOND) == 0 && compte(b.joueur(), Items.BREAD) == 0,
                    "rien ne passe de l'un à l'autre");
            helper.assertTrue(SauvegardeDEssai.gardeEnMemoire(a.joueur()).isEmpty()
                    && SauvegardeDEssai.gardeEnMemoire(b.joueur()).isEmpty(), "plus de garde");
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente");
            helper.assertTrue(painsAuSol(helper, zone(helper)) == 0, "rien au sol");
            helper.assertTrue(b.messagesRecus().stream().anyMatch(k -> k.startsWith("message.oneforallsurvival.trade.cancelled")),
                    "l'autre est prévenu");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void eloignementAnnuleEtRend(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            JoueurDeTest a = essai.joueur("ec_eloA", POS_A);
            JoueurDeTest b = essai.joueur("ec_eloB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            Vec3 loin = helper.absoluteVec(POS_A.add(0, 0, 24));
            a.joueur().teleportTo(loin.x, loin.y, loin.z);
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                            "l'éloignement annule l'échange");
                    helper.assertTrue(fermee(a) && fermee(b), "les deux fenêtres se ferment");
                    helper.assertTrue(compte(a.joueur(), Items.BREAD) == 5, "A récupère ses pains");
                    helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
                    helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente");
                    helper.succeed();
                } finally {
                    essai.close();
                }
            });
        } catch (RuntimeException | Error e) {
            essai.close();
            throw e;
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void mortAnnuleEtRend(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_morA", POS_A);
            JoueurDeTest b = essai.joueur("ec_morB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);

            AABB autour = a.joueur().getBoundingBox().inflate(4.0D);
            a.joueur().setHealth(0.0F);
            a.joueur().die(a.joueur().damageSources().genericKill());
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "la mort annule l'échange");
            helper.assertTrue(fermee(b), "la fenêtre de l'autre se ferme");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
            // Le mort ne reçoit rien : ni au sol, ni dans l'inventaire qui serait perdu ; ses pains attendent.
            HeldGoods held = HeldGoods.get(essai.serveur());
            helper.assertTrue(painsAuSol(helper, autour) == 0 && compte(a.joueur(), Items.BREAD) == 0,
                    "rien au sol ni dans l'inventaire du mort : sol " + painsAuSol(helper, autour));
            helper.assertTrue(SauvegardeDEssai.gardeEnMemoire(a.joueur()).isEmpty(), "plus de garde");
            helper.assertTrue(compte(held.pendingFor(a.uuid()).items(), Items.BREAD) == 5, "les cinq pains attendent");
            helper.assertTrue(dev.oneforallsurvival.economy.CommerceDelivery.deliverPending(a.joueur())
                    && compte(a.joueur(), Items.BREAD) == 0, "une livraison au mort n'a pas lieu");

            // Réapparu, il les reçoit, une seule fois.
            ServerPlayer reapparu = essai.serveur().getPlayerList().respawn(a.joueur(), false);
            helper.assertTrue(compte(reapparu, Items.BREAD) == 5, "les pains arrivent à la réapparition : "
                    + compte(reapparu, Items.BREAD));
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deconnexionRendToutAvantLaSauvegarde(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_decA", POS_A);
            JoueurDeTest b = essai.joueur("ec_decB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            AABB autour = a.joueur().getBoundingBox().inflate(4.0D);
            SauvegardeDEssai.automatique(essai.serveur());
            helper.assertTrue(compte(SauvegardeDEssai.gardeDansLePlayerdata(essai.serveur(), a.uuid()), Items.BREAD) == 5,
                    "la garde de A est dans son playerdata");

            a.quitter();
            List<ItemStack> surLeDisque = SauvegardeDEssai.gardeDansLePlayerdata(essai.serveur(), a.uuid());
            helper.assertTrue(surLeDisque.isEmpty(),
                    "à la déconnexion, le playerdata de A ne porte plus de garde : " + surLeDisque);
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "la déconnexion annule l'échange");
            helper.assertTrue(fermee(b), "la fenêtre de l'autre se ferme");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
            helper.assertTrue(painsAuSol(helper, autour) == 0, "rien au sol");
            helper.assertTrue(SauvegardeDEssai.gardeEnMemoire(b.joueur()).isEmpty(), "plus de garde chez B");
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente : tout est rentré avant la sauvegarde");

            // Le playerdata relu du disque : les cinq pains y sont, une seule fois.
            int pains = SauvegardeDEssai.objetsDansLePlayerdata(essai.serveur(), a.uuid(), Items.BREAD);
            helper.assertTrue(pains == 5, "le playerdata doit contenir les cinq pains : " + pains);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unEchangeConcluNeRevientPasDuDisque(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_dskA", POS_A);
            JoueurDeTest b = essai.joueur("ec_dskB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            // Une sauvegarde automatique tombe pendant l'échange : chaque garde est dans le playerdata de son propriétaire.
            MinecraftServer serveur = essai.serveur();
            SauvegardeDEssai.automatique(serveur);
            helper.assertTrue(compte(SauvegardeDEssai.gardeDansLePlayerdata(serveur, a.uuid()), Items.BREAD) == 5
                    && compte(SauvegardeDEssai.gardeDansLePlayerdata(serveur, b.uuid()), Coin.GOLD.item()) == 2,
                    "les deux gardes sont dans les playerdata");
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A confirme");
            helper.assertTrue(session.confirm(b.joueur(), session.version()) == ConfirmResult.DONE, "B confirme");

            // A part avec ses 2 or : son playerdata relu du disque n'a plus de garde, il porte les 2 or et plus les pains.
            a.quitter();
            helper.assertTrue(SauvegardeDEssai.gardeDansLePlayerdata(serveur, a.uuid()).isEmpty()
                    && SauvegardeDEssai.objetsDansLePlayerdata(serveur, a.uuid(), Coin.GOLD.item()) == 2
                    && SauvegardeDEssai.objetsDansLePlayerdata(serveur, a.uuid(), Items.BREAD) == 0,
                    "le playerdata de A : l'échange conclu, sans garde");
            helper.assertTrue(compte(b.joueur(), Items.BREAD) == 5, "B a ses pains");
            // B, resté, s'écrit à la sauvegarde suivante : l'échange conclu, sans garde. (Entre les deux, un arrêt
            // brutal rendrait à B sa garde : la limite de Minecraft pour un don, écrite dans la vérification.)
            SauvegardeDEssai.automatique(serveur);
            helper.assertTrue(SauvegardeDEssai.gardeDansLePlayerdata(serveur, b.uuid()).isEmpty()
                    && SauvegardeDEssai.objetsDansLePlayerdata(serveur, b.uuid(), Items.BREAD) == 5
                    && SauvegardeDEssai.objetsDansLePlayerdata(serveur, b.uuid(), Coin.GOLD.item()) == 0,
                    "le playerdata de B à la sauvegarde suivante : l'échange conclu, sans garde");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Place, pièces, bourses, reçu
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void inventairePleinRefuseSansRienPerdre(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_plnA", POS_A);
            JoueurDeTest b = essai.joueur("ec_plnB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            for (int i = 1; i < 36; i++) {
                a.joueur().getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            b.joueur().getInventory().items.set(1, new ItemStack(Items.IRON_SWORD, 1));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            poser(b, 1);
            a.messagesRecus();
            b.messagesRecus();
            // A n'a plus qu'une case libre : le diamant et l'épée n'y tiennent pas ensemble.
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A confirme");
            ConfirmResult r = session.confirm(b.joueur(), session.version());
            helper.assertTrue(r == ConfirmResult.NO_ROOM, "inventaire plein : " + r);
            helper.assertTrue(!session.confirmed(a.uuid()) && !session.confirmed(b.uuid()), "les confirmations tombent");
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == session, "l'échange reste ouvert");
            helper.assertTrue(compte(session.offer(a.uuid()), Items.BREAD) == 5
                    && compte(session.offer(b.uuid()), Items.DIAMOND) == 1
                    && compte(session.offer(b.uuid()), Items.IRON_SWORD) == 1, "les gardes sont intactes");
            helper.assertTrue(compte(a.joueur(), Items.DIAMOND) == 0 && compte(b.joueur(), Items.BREAD) == 0, "rien n'a bougé");
            helper.assertTrue(a.messagesRecus().contains("message.oneforallsurvival.trade.no_room"),
                    "le message dit qui manque de place");

            a.joueur().closeContainer();
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null, "fermé");
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 5, "A récupère ses pains");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1 && compte(b.joueur(), Items.IRON_SWORD) == 1,
                    "B récupère tout");
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lesPiecesDeLaBourseSAjoutent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_bouA", POS_A);
            JoueurDeTest b = essai.joueur("ec_bouB", POS_B);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 3L);
            a.joueur().getInventory().items.set(0, bourse);
            TradeSession session = ouvrir(helper, a, b);
            for (int i = 0; i < 3; i++) {
                a.envoyer(TradeActionPacket.addCoins(Coin.GOLD, 1));
            }
            ItemStack laBourse = a.joueur().getInventory().items.get(0);
            helper.assertTrue(Purse.count(laBourse, Coin.GOLD) == 0L, "la bourse est vide : " + Purse.contents(laBourse));
            helper.assertTrue(compte(session.offer(a.uuid()), Coin.GOLD.item()) == 3, "trois or en garde");
            a.envoyer(TradeActionPacket.addCoins(Coin.GOLD, 1));
            a.envoyer(TradeActionPacket.addCoins(Coin.SILVER, 10));
            helper.assertTrue(compte(session.offer(a.uuid()), Coin.GOLD.item()) == 3
                    && compte(session.offer(a.uuid()), Coin.SILVER.item()) == 0, "sans pièce, rien ne s'ajoute");
            helper.assertTrue(valeurPortee(a) == 0L, "A ne porte plus rien");

            a.envoyer(TradeActionPacket.cancel());
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null, "annulé");
            laBourse = a.joueur().getInventory().items.get(0);
            helper.assertTrue(Purse.count(laBourse, Coin.GOLD) == 3L, "les 3 or reviennent dans la bourse : "
                    + Purse.contents(laBourse));
            helper.assertTrue(compte(a.joueur(), Coin.GOLD.item()) == 0, "aucun or en vrac");
            helper.assertTrue(fermee(a) && fermee(b), "fenêtres fermées");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneBoursePleineSEchange(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_bpA", POS_A);
            JoueurDeTest b = essai.joueur("ec_bpB", POS_B);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 5L);
            a.joueur().getInventory().items.set(0, bourse);
            b.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 2));
            EconomyLedger registre = EconomyLedger.get(essai.serveur());
            long echangeAvant = registre.totalTransferred(MoneyTransfer.TRADE);
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            helper.assertTrue(Purse.isPurse(session.offer(a.uuid()).getItem(0)), "la bourse pleine se pose");
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A confirme");
            helper.assertTrue(session.confirm(b.joueur(), session.version()) == ConfirmResult.DONE, "B confirme");
            helper.assertTrue(Purse.firstPurse(b.joueur()).map(Purse::total).orElse(0L) == 500L, "B reçoit la bourse de 5 or");
            helper.assertTrue(Purse.firstPurse(a.joueur()).isEmpty(), "A n'a plus de bourse");
            helper.assertTrue(compte(a.joueur(), Items.BREAD) == 2, "A reçoit les pains");
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.TRADE) - echangeAvant == 500L,
                    "la bourse compte au registre : " + (registre.totalTransferred(MoneyTransfer.TRADE) - echangeAvant));
            EconomyLedger.Entry ligne = registre.entries(0).get(0);
            helper.assertTrue(a.uuid().equals(ligne.author()) && ligne.amount() == 500L, "auteur : celui qui donne : " + ligne);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leRecuListeCeQuiAEteEchange(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest a = essai.joueur("ec_recA", POS_A);
            JoueurDeTest b = essai.joueur("ec_recB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            b.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            b.envoyer(TradeActionPacket.toggleReceipt(session.version()));
            helper.assertTrue(session.receipt(), "reçu coché");
            long avant = System.currentTimeMillis();
            helper.assertTrue(session.confirm(a.joueur(), session.version()) == ConfirmResult.WAITING, "A confirme");
            helper.assertTrue(session.confirm(b.joueur(), session.version()) == ConfirmResult.DONE, "B confirme");
            long apres = System.currentTimeMillis();

            Item recu = EconomyRegistry.RECEIPT.get();
            helper.assertTrue(compte(a.joueur(), recu) == 1 && compte(b.joueur(), recu) == 1, "un reçu chacun");
            ItemStack ra = trouver(a.joueur(), recu);
            ItemStack rb = trouver(b.joueur(), recu);
            helper.assertTrue(Receipt.other(ra).equals(b.joueur().getGameProfile().getName()), "autre partie : " + Receipt.other(ra));
            helper.assertTrue(Receipt.other(rb).equals(a.joueur().getGameProfile().getName()), "autre partie : " + Receipt.other(rb));
            helper.assertTrue(Receipt.when(ra) >= avant && Receipt.when(ra) <= apres, "date : " + Receipt.when(ra));
            helper.assertTrue(ligne(Receipt.given(ra), Items.BREAD, 5) && ligne(Receipt.received(ra), Coin.GOLD.item(), 2),
                    "A a donné 5 pains et reçu 2 or : " + Receipt.given(ra) + " / " + Receipt.received(ra));
            helper.assertTrue(ligne(Receipt.given(rb), Coin.GOLD.item(), 2) && ligne(Receipt.received(rb), Items.BREAD, 5),
                    "B a donné 2 or et reçu 5 pains : " + Receipt.given(rb) + " / " + Receipt.received(rb));
            helper.assertTrue(Receipt.given(ra).size() == 1 && Receipt.received(ra).size() == 1, "une ligne de chaque côté");
            helper.assertTrue(Receipt.tooltip(ra).size() >= 5, "l'infobulle liste genre, date, autre partie et lignes");
            helper.assertTrue(!dev.oneforallsurvival.economy.MoneyStorageRules.isMoney(ra), "un reçu n'est pas de l'argent");
            helper.succeed();
        }
    }

    private static ItemStack trouver(ServerPlayer joueur, Item objet) {
        for (ItemStack pile : joueur.getInventory().items) {
            if (pile.is(objet)) {
                return pile;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean ligne(List<Receipt.Line> lignes, Item objet, int nombre) {
        for (Receipt.Line l : lignes) {
            if (l.item() == objet && l.count() == nombre) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : écriture cohérente à la déconnexion, pièces rendues au coffre-fort
    // ---------------------------------------------------------------------------------------------

    /**
     * A verse 2 or dans un échange avec B, puis C, sans rapport, se déconnecte. Le serveur s'arrête brutalement : ce
     * que le disque rend à A (son playerdata, plus sa garde devenue attente) ne contient ses 2 or qu'une fois.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laDeconnexionDUnTiersNeDoubleRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_triA", POS_A);
            JoueurDeTest b = essai.joueur("ec_triB", POS_B);
            JoueurDeTest c = essai.joueur("ec_triC", POS_C);
            a.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            // Une sauvegarde automatique : A porte ses 2 or, B son diamant, aucune garde.
            SauvegardeDEssai.automatique(serveur);
            TradeSession session = ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            helper.assertTrue(compte(session.offer(a.uuid()), Coin.GOLD.item()) == 2
                    && compte(session.offer(b.uuid()), Items.DIAMOND) == 1, "les 2 or et le diamant sont en garde");

            c.quitter();

            // Arrêt brutal : ce qu'un redémarrage relirait (inventaire, garde du playerdata, attentes).
            HeldGoods relu = SauvegardeDEssai.attenteApresRedemarrage(serveur);
            int orPorte = SauvegardeDEssai.objetsDansLePlayerdata(serveur, a.uuid(), Coin.GOLD.item());
            int orGarde = compte(SauvegardeDEssai.gardeDansLePlayerdata(serveur, a.uuid()), Coin.GOLD.item());
            long orAttente = relu.pendingFor(a.uuid()).coins().getOrDefault(Coin.GOLD, 0L);
            helper.assertTrue(orPorte + orGarde + orAttente == 2L, "A retrouverait " + orPorte + " or sur lui, " + orGarde
                    + " en garde et " + orAttente + " en attente : ses 2 or ne doivent être qu'à un seul endroit");
            int diamantPorte = SauvegardeDEssai.objetsDansLePlayerdata(serveur, b.uuid(), Items.DIAMOND);
            int diamantGarde = compte(SauvegardeDEssai.gardeDansLePlayerdata(serveur, b.uuid()), Items.DIAMOND);
            int diamantAttente = compte(relu.pendingFor(b.uuid()).items(), Items.DIAMOND);
            helper.assertTrue(diamantPorte + diamantGarde + diamantAttente == 1, "B retrouverait " + diamantPorte
                    + " diamant sur lui, " + diamantGarde + " en garde et " + diamantAttente + " en attente : un seul attendu");
            helper.succeed();
        }
    }

    /** Remplit tout l'inventaire de pierre, sauf les cases données. */
    private static void remplirSauf(ServerPlayer joueur, int... libres) {
        java.util.Set<Integer> garder = new java.util.HashSet<>();
        for (int i : libres) {
            garder.add(i);
        }
        for (int i = 0; i < 36; i++) {
            if (!garder.contains(i)) {
                joueur.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
        }
    }

    /**
     * A pose 3 or et un pain dans un échange, remplit son inventaire, puis ferme : les pièces qui ne tiennent plus vont
     * dans son coffre-fort, le pain seul attend. L'attente n'est pas une cachette pour l'argent.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void lesPiecesRenduesVontAuCoffreFortAvantLAttente(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_cfA", POS_A);
            JoueurDeTest b = essai.joueur("ec_cfB", POS_B);
            SafeAccounts coffres = SafeAccounts.get(serveur);
            coffres.setSafePos(a.uuid(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COFFRE)));
            a.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 3));
            a.joueur().getInventory().items.set(1, new ItemStack(Items.BREAD, 2));
            ouvrir(helper, a, b);
            poser(a, 0);
            poser(a, 1);
            remplirSauf(a.joueur());
            long coffreAvant = coffres.total(a.uuid());

            a.joueur().closeContainer();
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null, "fermé");
            HeldGoods.Pending attente = HeldGoods.get(serveur).pendingFor(a.uuid());
            helper.assertTrue(coffres.count(a.uuid(), Coin.GOLD) == 3L && coffres.total(a.uuid()) - coffreAvant == 300L,
                    "les 3 or devaient aller au coffre-fort : " + coffres.count(a.uuid(), Coin.GOLD));
            helper.assertTrue(attente.coins().isEmpty(), "aucune pièce en attente : " + attente.coins());
            helper.assertTrue(compte(attente.items(), Items.BREAD) == 2, "le pain attend : " + attente.items());
            helper.succeed();
        }
    }

    /** Même départ sans coffre-fort : les pièces n'ont nulle part où aller, elles attendent avec le pain. */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void sansCoffreFortLesPiecesRenduesAttendent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_scfA", POS_A);
            JoueurDeTest b = essai.joueur("ec_scfB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 3));
            a.joueur().getInventory().items.set(1, new ItemStack(Items.BREAD, 2));
            ouvrir(helper, a, b);
            poser(a, 0);
            poser(a, 1);
            remplirSauf(a.joueur());

            a.joueur().closeContainer();
            HeldGoods.Pending attente = HeldGoods.get(serveur).pendingFor(a.uuid());
            helper.assertTrue(attente.coins().getOrDefault(Coin.GOLD, 0L) == 3L, "les 3 or attendent : " + attente.coins());
            helper.assertTrue(compte(attente.items(), Items.BREAD) == 2, "le pain attend : " + attente.items());
            helper.assertTrue(SafeAccounts.get(serveur).total(a.uuid()) == 0L, "pas de coffre-fort, rien dedans");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Seconde passe : la garde vit avec son propriétaire, aucune écriture forcée, bourses au coffre-fort
    // ---------------------------------------------------------------------------------------------

    /**
     * A pose cinq pains dans un échange ; une sauvegarde de A les écrit dans son playerdata, sous sa garde, avec son
     * inventaire. Le serveur s'arrête brutalement à cet instant (le fichier relu tel quel) : à la connexion suivante,
     * les pains lui reviennent, une seule fois.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laGardeVitDansLePlayerdataEtRevientUneFois(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_pdA", POS_A);
            JoueurDeTest b = essai.joueur("ec_pdB", POS_B);
            a.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 5));
            ouvrir(helper, a, b);
            poser(a, 0);
            SauvegardeDEssai.automatique(serveur);
            int porte = SauvegardeDEssai.objetsDansLePlayerdata(serveur, a.uuid(), Items.BREAD);
            int enGarde = compte(SauvegardeDEssai.gardeDansLePlayerdata(serveur, a.uuid()), Items.BREAD);
            helper.assertTrue(porte == 0 && enGarde == 5,
                    "le playerdata de A porte la garde : inventaire " + porte + ", garde " + enGarde);
            helper.assertTrue(!SauvegardeDEssai.attenteApresRedemarrage(serveur).hasPending(a.uuid()),
                    "la garde n'est pas dans le fichier des attentes");

            // L'arrêt brutal : le playerdata tel qu'il est sur le disque à cet instant.
            java.nio.file.Path fichier = SauvegardeDEssai.fichierDuJoueur(serveur, a.uuid());
            byte[] instant = lire(fichier);
            a.quitter();
            ecrire(fichier, instant);

            JoueurDeTest revenu = essai.revenir(a, POS_A);
            helper.assertTrue(compte(revenu.joueur(), Items.BREAD) == 5,
                    "la garde revient à la connexion : " + compte(revenu.joueur(), Items.BREAD));
            helper.assertTrue(SauvegardeDEssai.gardeEnMemoire(revenu.joueur()).isEmpty(), "la garde est vidée");
            helper.assertTrue(rienEnAttente(serveur, revenu), "rien en attente : tout est rentré");
            revenu.quitter();
            JoueurDeTest encore = essai.revenir(a, POS_A);
            helper.assertTrue(compte(encore.joueur(), Items.BREAD) == 5,
                    "une seule fois : " + compte(encore.joueur(), Items.BREAD) + " pains à la connexion suivante");
            helper.succeed();
        }
    }

    /**
     * Pendant un échange entre A et B, C se déconnecte : son départ n'écrit que son propre playerdata. Ni celui de A ni
     * celui de B, ni aucune donnée de l'économie (même changée depuis) ne sont réécrits sur le disque : tout ce qui
     * n'est pas C reste au même instant, celui de la dernière sauvegarde.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laDeconnexionDUnTiersNEcritQueSonPlayerdata(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_ecrA", POS_A);
            JoueurDeTest b = essai.joueur("ec_ecrB", POS_B);
            JoueurDeTest c = essai.joueur("ec_ecrC", POS_C);
            a.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            b.joueur().getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));
            c.joueur().getInventory().items.set(0, new ItemStack(Items.BREAD, 3));
            SauvegardeDEssai.automatique(serveur);
            ouvrir(helper, a, b);
            poser(a, 0);
            poser(b, 0);
            c.joueur().getInventory().items.set(1, new ItemStack(Items.APPLE, 1));
            // Toutes les données de l'économie ont changé depuis la sauvegarde.
            dev.oneforallsurvival.economy.invoice.InvoiceRegistry.get(serveur).setDirty();
            EconomyLedger.get(serveur).setDirty();
            HeldGoods.get(serveur).setDirty();
            SafeAccounts.get(serveur).setDirty();
            dev.oneforallsurvival.economy.TreasuryAccount.get(serveur).setDirty();
            java.util.Map<String, java.nio.file.Path> fichiers = new java.util.LinkedHashMap<>();
            fichiers.put("playerdata de A", SauvegardeDEssai.fichierDuJoueur(serveur, a.uuid()));
            fichiers.put("playerdata de B", SauvegardeDEssai.fichierDuJoueur(serveur, b.uuid()));
            for (String cle : List.of(HeldGoods.KEY, dev.oneforallsurvival.economy.invoice.InvoiceRegistry.KEY,
                    EconomyLedger.KEY, SafeAccounts.KEY, dev.oneforallsurvival.economy.TreasuryAccount.KEY)) {
                fichiers.put(cle, SauvegardeDEssai.fichierDeDonnees(serveur, cle));
            }
            java.util.Map<String, SauvegardeDEssai.Instantane> avant = new java.util.LinkedHashMap<>();
            fichiers.forEach((nom, fichier) -> avant.put(nom, SauvegardeDEssai.Instantane.de(fichier)));

            c.quitter();

            List<String> reecrits = new ArrayList<>();
            fichiers.forEach((nom, fichier) -> {
                if (!avant.get(nom).pareil(SauvegardeDEssai.Instantane.de(fichier))) {
                    reecrits.add(nom);
                }
            });
            helper.assertTrue(reecrits.isEmpty(), "la déconnexion de C a réécrit : " + reecrits);
            helper.assertTrue(SauvegardeDEssai.objetsDansLePlayerdata(serveur, c.uuid(), Items.APPLE) == 1,
                    "le playerdata de C est écrit à son départ");
            helper.succeed();
        }
    }

    /**
     * A, qui a un coffre-fort presque plein en or, pose une bourse de 3 or et 5 cuivres et deux pains, remplit son
     * inventaire, puis ferme : la bourse ne rentre pas. Son contenu va au coffre-fort dans la limite du plafond (1 or,
     * 5 cuivres) ; la bourse, avec ce qui n'a pas tenu (2 or), attend avec les pains.
     */
    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBoursePleineRendueSeVideAuCoffreFort(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            JoueurDeTest a = essai.joueur("ec_bcfA", POS_A);
            JoueurDeTest b = essai.joueur("ec_bcfB", POS_B);
            SafeAccounts coffres = SafeAccounts.get(serveur);
            coffres.setSafePos(a.uuid(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COFFRE)));
            coffres.deposit(a.uuid(), Coin.GOLD, SafeAccounts.MAX_PER_COIN - 1L);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, 3L);
            Purse.add(bourse, Coin.COPPER, 5L);
            a.joueur().getInventory().items.set(0, bourse);
            a.joueur().getInventory().items.set(1, new ItemStack(Items.BREAD, 2));
            ouvrir(helper, a, b);
            poser(a, 0);
            poser(a, 1);
            remplirSauf(a.joueur());

            a.joueur().closeContainer();
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null, "fermé");
            helper.assertTrue(coffres.count(a.uuid(), Coin.GOLD) == SafeAccounts.MAX_PER_COIN
                    && coffres.count(a.uuid(), Coin.COPPER) == 5L, "1 or et 5 cuivres au coffre-fort : or "
                    + coffres.count(a.uuid(), Coin.GOLD) + ", cuivre " + coffres.count(a.uuid(), Coin.COPPER));
            HeldGoods.Pending attente = HeldGoods.get(serveur).pendingFor(a.uuid());
            List<ItemStack> bourses = attente.items().stream().filter(Purse::isPurse).toList();
            helper.assertTrue(bourses.size() == 1 && Purse.count(bourses.get(0), Coin.GOLD) == 2L
                    && Purse.count(bourses.get(0), Coin.COPPER) == 0L,
                    "la bourse attend avec les 2 or qui n'ont pas tenu : " + bourses.stream().map(Purse::contents).toList());
            helper.assertTrue(compte(attente.items(), Items.BREAD) == 2 && attente.coins().isEmpty(),
                    "les pains attendent, aucune pièce en vrac : " + attente.items() + " / " + attente.coins());
            helper.succeed();
        }
    }

    private static byte[] lire(java.nio.file.Path fichier) {
        try {
            return java.nio.file.Files.readAllBytes(fichier);
        } catch (java.io.IOException e) {
            throw new AssertionError("illisible : " + fichier + " : " + e.getMessage());
        }
    }

    private static void ecrire(java.nio.file.Path fichier, byte[] octets) {
        try {
            java.nio.file.Files.write(fichier, octets);
        } catch (java.io.IOException e) {
            throw new AssertionError("écriture impossible : " + fichier + " : " + e.getMessage());
        }
    }
}
