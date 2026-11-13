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
import dev.oneforallsurvival.economy.trade.TradeActionPacket;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import dev.oneforallsurvival.economy.trade.TradeService;
import dev.oneforallsurvival.economy.trade.TradeService.RequestResult;
import dev.oneforallsurvival.economy.trade.TradeSession;
import dev.oneforallsurvival.economy.trade.TradeSession.ConfirmResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

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
            for (UUID uuid : comptes) {
                held.takePending(uuid);
                held.closeEscrow(uuid);
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
            HeldGoods held = HeldGoods.get(essai.serveur());
            helper.assertTrue(held.closeEscrow(a.uuid()).isEmpty() && held.closeEscrow(b.uuid()).isEmpty(),
                    "plus aucune garde");
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
            HeldGoods held = HeldGoods.get(essai.serveur());
            helper.assertTrue(held.closeEscrow(a.uuid()).isEmpty() && held.closeEscrow(b.uuid()).isEmpty(), "plus de garde");
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

            a.joueur().setHealth(0.0F);
            a.joueur().die(a.joueur().damageSources().genericKill());
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "la mort annule l'échange");
            helper.assertTrue(fermee(b), "la fenêtre de l'autre se ferme");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
            // Rendus avant les lâchers : les pains suivent le sort de l'inventaire du mort, une seule fois.
            AABB autour = a.joueur().getBoundingBox().inflate(4.0D);
            int total = painsAuSol(helper, autour) + compte(a.joueur(), Items.BREAD);
            helper.assertTrue(total == 5, "cinq pains au sol ou dans l'inventaire du mort : " + total);
            helper.assertTrue(HeldGoods.get(essai.serveur()).closeEscrow(a.uuid()).isEmpty(), "plus de garde");
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

            a.quitter();
            helper.assertTrue(TradeService.sessionOf(a.uuid()) == null && TradeService.sessionOf(b.uuid()) == null,
                    "la déconnexion annule l'échange");
            helper.assertTrue(fermee(b), "la fenêtre de l'autre se ferme");
            helper.assertTrue(compte(b.joueur(), Items.DIAMOND) == 1, "B récupère son diamant");
            helper.assertTrue(painsAuSol(helper, autour) == 0, "rien au sol");
            helper.assertTrue(HeldGoods.get(essai.serveur()).closeEscrow(a.uuid()).isEmpty(), "plus de garde");
            helper.assertTrue(rienEnAttente(essai.serveur(), a, b), "rien en attente : tout est rentré avant la sauvegarde");

            // Le playerdata relu du disque : les cinq pains y sont, une seule fois.
            Path fichier = essai.serveur().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(a.uuid() + ".dat");
            int pains = 0;
            try {
                CompoundTag donnees = NbtIo.readCompressed(fichier.toFile());
                ListTag inventaire = donnees.getList("Inventory", Tag.TAG_COMPOUND);
                String pain = ForgeRegistries.ITEMS.getKey(Items.BREAD).toString();
                for (Tag brut : inventaire) {
                    CompoundTag pile = (CompoundTag) brut;
                    if (pain.equals(pile.getString("id"))) {
                        pains += pile.getByte("Count");
                    }
                }
            } catch (java.io.IOException e) {
                throw new AssertionError("playerdata illisible : " + fichier + " : " + e.getMessage());
            }
            helper.assertTrue(pains == 5, "le playerdata doit contenir les cinq pains : " + pains);
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
}
