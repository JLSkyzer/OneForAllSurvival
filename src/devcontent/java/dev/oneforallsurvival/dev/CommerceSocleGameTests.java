package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CommerceDelivery;
import dev.oneforallsurvival.economy.CommerceDelivery.Destination;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.InventoryFit;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.TreasuryAccount;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le socle du commerce (lot E2, tâche 1) : livraison en attente, retour d'objets, paiement d'un créancier,
 * garde qui survit à un arrêt brutal, trésor et tableau de bord. Chaque test a ses joueurs d'essai, aux noms
 * propres à l'essai (le monde d'essai garde leurs données d'un passage à l'autre), et rend ce qu'il a mis dans les
 * comptes globaux (attentes, coffres-forts) en fin de test.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CommerceSocleGameTests {

    private static final String LOT = "commerce_socle";
    private static final String LOT_B = "commerce_socle_b";

    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);
    private static final BlockPos COFFRE = new BlockPos(1, 0, 1);

    private CommerceSocleGameTests() {}

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

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        /** Un nom propre à cet essai (16 caractères au plus) : jamais de données gardées d'un passage précédent. */
        String nom(String court) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            comptes.add(identifiant(nom));
            return nom;
        }

        /** Son identifiant est noté « somme de départ déjà reçue » : la connexion ne lui donne rien. */
        UUID absent(String nom) {
            UUID uuid = identifiant(nom);
            EconomyLedger.get(serveur()).markStartingGiven(uuid);
            return uuid;
        }

        /** Un joueur de survie qui se connecte sans rien perdre de ce que la connexion lui a donné. */
        JoueurDeTest connecter(String nom, java.util.function.Consumer<ServerPlayer> avantEntree) {
            absent(nom);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR), false,
                    avantEntree);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /** Un joueur de survie à l'inventaire vide (la somme de départ, si elle a été donnée, est retirée). */
        JoueurDeTest vide(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        @Override
        public void close() {
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
                held.closeEscrow(uuid);
                for (Coin coin : Coin.values()) {
                    coffres.withdraw(uuid, coin, coffres.count(uuid, coin));
                }
                coffres.forgetSafePos(uuid);
            }
        }
    }

    private static UUID identifiant(String nom) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
    }

    private static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static int pains(ServerPlayer joueur) {
        int total = 0;
        for (ItemStack pile : joueur.getInventory().items) {
            if (pile.is(Items.BREAD)) {
                total += pile.getCount();
            }
        }
        return total;
    }

    private static int pains(List<ItemStack> piles) {
        int total = 0;
        for (ItemStack pile : piles) {
            if (pile.is(Items.BREAD)) {
                total += pile.getCount();
            }
        }
        return total;
    }

    /** Remplit les 36 cases principales de terre ou de pierre, sauf celles qu'on laisse vides. */
    private static void remplir(ServerPlayer joueur, int libres) {
        for (int i = 0; i < 36 - libres; i++) {
            joueur.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Livraison en attente
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneLivraisonEnAttenteArriveALaConnexion(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            String nom = essai.nom("cs_arrive");
            UUID uuid = essai.absent(nom);
            HeldGoods held = HeldGoods.get(essai.serveur());
            held.addPending(uuid, List.of(new ItemStack(Items.BREAD)), Map.of(Coin.GOLD, 3L));
            helper.assertTrue(held.hasPending(uuid), "la livraison devait attendre");
            helper.assertTrue(held.pendingMass() >= 300L, "la masse en attente doit compter les 3 or : " + held.pendingMass());

            JoueurDeTest j = essai.connecter(nom, p -> {});
            helper.assertTrue(pains(j.joueur()) == 1, "le pain devait arriver : " + pains(j.joueur()));
            helper.assertTrue(valeurPortee(j) == 300L, "les 3 or devaient arriver : " + valeurPortee(j));
            helper.assertTrue(!held.hasPending(uuid), "plus rien ne devait attendre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneLivraisonQuiNeTientPasResteEnAttente(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            String nom = essai.nom("cs_plein");
            UUID uuid = essai.absent(nom);
            HeldGoods held = HeldGoods.get(essai.serveur());
            held.addPending(uuid, List.of(new ItemStack(Items.BREAD)), Map.of(Coin.GOLD, 3L));

            // Trente-cinq cases de pierre et une bourse : plus aucune case libre pour le pain.
            JoueurDeTest j = essai.connecter(nom, p -> {
                remplir(p, 1);
                p.getInventory().items.set(35, new ItemStack(EconomyRegistry.PURSE.get()));
            });
            helper.assertTrue(pains(j.joueur()) == 0, "le pain ne devait pas entrer");
            helper.assertTrue(held.hasPending(uuid), "le pain devait rester en attente");
            HeldGoods.Pending reste = held.pendingFor(uuid);
            helper.assertTrue(reste.items().size() == 1 && reste.items().get(0).is(Items.BREAD)
                    && reste.items().get(0).getCount() == 1, "il devait rester un pain : " + reste.items());
            helper.assertTrue(reste.coins().isEmpty(), "les pièces devaient être entrées : " + reste.coins());
            ItemStack bourse = Purse.firstPurse(j.joueur()).orElseThrow();
            helper.assertTrue(Purse.count(bourse, Coin.GOLD) == 3L, "les 3 or devaient être dans la bourse");

            // Une case se libère : la livraison suivante passe et plus rien n'attend.
            j.joueur().getInventory().items.set(0, ItemStack.EMPTY);
            boolean encore = CommerceDelivery.deliverPending(j.joueur());
            helper.assertTrue(!encore && pains(j.joueur()) == 1 && !held.hasPending(uuid),
                    "le pain devait arriver une fois la place faite");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Payer un créancier
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void payToConnecteAvecPlace(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            String nom = essai.nom("cs_pay_ok");
            JoueurDeTest j = essai.vide(nom);
            long avant = valeurPortee(j);
            Destination d = CommerceDelivery.payTo(essai.serveur(), j.uuid(), 1_234L);
            helper.assertTrue(d == Destination.INVENTORY, "destination : " + d);
            helper.assertTrue(valeurPortee(j) - avant == 1_234L, "reçu : " + (valeurPortee(j) - avant));
            helper.assertTrue(!HeldGoods.get(essai.serveur()).hasPending(j.uuid()), "rien ne devait attendre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void payToSansPlaceAvecCoffreFort(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            String nom = essai.nom("cs_pay_cf");
            JoueurDeTest j = essai.vide(nom);
            SafeAccounts coffres = SafeAccounts.get(essai.serveur());
            coffres.setSafePos(j.uuid(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COFFRE)));
            remplir(j.joueur(), 0);
            long coffreAvant = coffres.total(j.uuid());
            Destination d = CommerceDelivery.payTo(essai.serveur(), j.uuid(), 1_234L);
            helper.assertTrue(d == Destination.SAFE, "destination : " + d);
            helper.assertTrue(coffres.total(j.uuid()) - coffreAvant == 1_234L,
                    "le coffre-fort devait recevoir la somme : " + (coffres.total(j.uuid()) - coffreAvant));
            helper.assertTrue(valeurPortee(j) == 0L, "rien ne devait entrer dans l'inventaire plein");
            helper.assertTrue(!HeldGoods.get(essai.serveur()).hasPending(j.uuid()), "rien ne devait attendre");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void payToHorsLigneSansCoffreFort(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            String nom = essai.nom("cs_pay_hl");
            UUID uuid = essai.absent(nom);
            HeldGoods held = HeldGoods.get(essai.serveur());
            Destination d = CommerceDelivery.payTo(essai.serveur(), uuid, 1_234L);
            helper.assertTrue(d == Destination.PENDING, "destination : " + d);
            helper.assertTrue(held.hasPending(uuid), "la somme devait attendre");
            helper.assertTrue(CoinMath.valueOf(held.pendingFor(uuid).coins()) == 1_234L,
                    "la somme en attente : " + held.pendingFor(uuid).coins());

            JoueurDeTest j = essai.connecter(nom, p -> {});
            helper.assertTrue(valeurPortee(j) == 1_234L, "la somme devait arriver à la connexion : " + valeurPortee(j));
            helper.assertTrue(!held.hasPending(uuid), "plus rien ne devait attendre");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Garde, trésor, tableau de bord, place
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneGardeSurvitAUnArretBrutal(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            UUID uuid = identifiant(essai.nom("cs_garde"));
            HeldGoods held = HeldGoods.get(essai.serveur());
            SimpleContainer garde = held.openEscrow(uuid, 12);
            helper.assertTrue(held.openEscrow(uuid, 12) == garde, "une seule garde par joueur");
            garde.addItem(new ItemStack(Items.BREAD, 5));

            // Le serveur s'arrête là : la sauvegarde est tout ce qui reste, relue sur une copie.
            CompoundTag disque = held.save(new CompoundTag());
            HeldGoods relu = HeldGoods.load(disque);
            helper.assertTrue(relu.hasPending(uuid), "la garde devait devenir une attente");
            HeldGoods.Pending attente = relu.pendingFor(uuid);
            helper.assertTrue(attente.items().size() == 1 && pains(attente.items()) == 5,
                    "cinq pains attendus, une seule fois : " + attente.items());
            // Un second arrêt ne double rien.
            HeldGoods encore = HeldGoods.load(relu.save(new CompoundTag()));
            helper.assertTrue(pains(encore.pendingFor(uuid).items()) == 5,
                    "toujours cinq pains : " + encore.pendingFor(uuid).items());

            // Une garde fermée rend son contenu et s'oublie.
            List<ItemStack> rendu = held.closeEscrow(uuid);
            helper.assertTrue(pains(rendu) == 5, "la garde fermée devait rendre cinq pains : " + rendu);
            helper.assertTrue(held.closeEscrow(uuid).isEmpty(), "une garde fermée ne rend rien deux fois");
            helper.assertTrue(!HeldGoods.load(held.save(new CompoundTag())).hasPending(uuid),
                    "une garde fermée ne revient pas à la relecture");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leTresorGardeSonSoldeAuRechargement(GameTestHelper helper) {
        TreasuryAccount compte = TreasuryAccount.load(new CompoundTag());
        helper.assertTrue(compte.total() == 0L, "un trésor neuf est vide");
        compte.deposit(Map.of(Coin.GOLD, 3L, Coin.COPPER, 5L));
        compte.deposit(Map.of(Coin.GOLD, 1L));
        helper.assertTrue(compte.total() == 405L, "total : " + compte.total());
        TreasuryAccount relu = TreasuryAccount.load(compte.save(new CompoundTag()));
        helper.assertTrue(relu.balance().equals(compte.balance()), "solde relu : " + relu.balance());
        helper.assertTrue(relu.total() == 405L && relu.balance().get(Coin.GOLD) == 4L, "total relu : " + relu.total());
        boolean refuse = false;
        try {
            relu.deposit(Map.of(Coin.GOLD, -1L));
        } catch (IllegalArgumentException e) {
            refuse = true;
        }
        helper.assertTrue(refuse && relu.total() == 405L, "un dépôt négatif doit être refusé sans rien changer");
        // Le compte du serveur existe et se lit.
        helper.assertTrue(TreasuryAccount.get(helper.getLevel().getServer()).total() >= 0L, "le trésor du serveur");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void ecoStatsMontreTransfertsTresorEtAttente(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MinecraftServer serveur = essai.serveur();
            EconomyLedger registre = EconomyLedger.get(serveur);
            HeldGoods held = HeldGoods.get(serveur);
            UUID uuid = essai.absent(essai.nom("cs_stats"));
            long circulation = registre.inCirculation();
            registre.transferred(MoneyTransfer.TRADE, 200L, uuid, "essai du tableau de bord");
            helper.assertTrue(registre.inCirculation() == circulation, "un transfert ne change pas la circulation");
            TreasuryAccount.get(serveur).deposit(Map.of(Coin.SILVER, 3L));
            held.addPending(uuid, List.of(), Map.of(Coin.GOLD, 4L));

            List<Component> sortie = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    sortie.add(message);
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            CommandSourceStack source = serveur.createCommandSourceStack().withSource(capture).withPermission(2);
            try {
                int r = serveur.getCommands().getDispatcher().execute("eco stats", source);
                helper.assertTrue(r == 1, "eco stats : " + r);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new AssertionError("eco stats a échoué : " + e.getMessage());
            }
            helper.assertTrue(sortie.size() == 1, "une réponse attendue : " + sortie.size());
            String texte = sortie.get(0).getString();

            for (MoneyTransfer genre : MoneyTransfer.values()) {
                String ligne = Component.translatable("command.oneforallsurvival.eco.stats.line",
                        Component.translatable(genre.langKey()), CoinMath.format(registre.totalTransferred(genre)))
                        .getString();
                helper.assertTrue(texte.contains(ligne), "ligne de transfert absente : " + ligne + " dans " + texte);
            }
            helper.assertTrue(registre.totalTransferred(MoneyTransfer.TRADE) >= 200L, "total d'échange noté");
            String tresor = Component.translatable("command.oneforallsurvival.eco.stats.treasury",
                    CoinMath.format(TreasuryAccount.get(serveur).total())).getString();
            helper.assertTrue(texte.contains(tresor), "ligne du trésor absente : " + tresor + " dans " + texte);
            helper.assertTrue(TreasuryAccount.get(serveur).total() >= 30L, "le trésor doit compter les 3 argent");
            String attente = Component.translatable("command.oneforallsurvival.eco.stats.pending",
                    CoinMath.format(held.pendingMass())).getString();
            helper.assertTrue(texte.contains(attente), "ligne d'attente absente : " + attente + " dans " + texte);
            helper.assertTrue(held.pendingMass() >= 400L, "la masse en attente doit compter les 4 or");
            String circ = Component.translatable("command.oneforallsurvival.eco.stats.circulation",
                    CoinMath.format(registre.inCirculation())).getString();
            helper.assertTrue(registre.inCirculation() < 0L || texte.contains(circ),
                    "la circulation reste créé moins détruit : " + circ + " dans " + texte);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laPlaceSeCalculeCommeInventoryAdd(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.vide(essai.nom("cs_place"));
            ServerPlayer p = j.joueur();
            remplir(p, 1);
            // Une case libre : 64 pierres entrent (elle reçoit la pile entière), 65 non.
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Items.DIRT, 64))), "64 terres, une case libre");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 1))),
                    "65 terres ne tiennent pas dans une case");
            // Deux pains, de même sorte, se rejoignent dans la case libre ; deux épées non.
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Items.BREAD), new ItemStack(Items.BREAD))),
                    "deux pains dans une case");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_SWORD))),
                    "deux épées ne tiennent pas dans une case");
            // Une pile entamée se complète : 63 pierres + 1 reste dans la case entamée.
            p.getInventory().items.set(0, new ItemStack(Items.STONE, 63));
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Items.STONE, 65))),
                    "1 pierre dans la pile entamée, 64 dans la case libre");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Items.STONE, 66))),
                    "66 pierres : une de trop");
            // Les pièces : sans bourse elles prennent une case ; avec une bourse, elles y entrent.
            remplir(p, 0);
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 3))),
                    "sans bourse, des pièces ne tiennent pas dans un inventaire plein");
            p.getInventory().items.set(35, new ItemStack(EconomyRegistry.PURSE.get()));
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 3))),
                    "avec une bourse, les pièces y entrent");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Items.BREAD))),
                    "un pain ne tient pas : la bourse occupe la dernière case");
            // La simulation ne touche à rien.
            helper.assertTrue(p.getInventory().items.get(35).getItem() == EconomyRegistry.PURSE.get(),
                    "l'inventaire ne devait pas bouger");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneBoursePleineLaissePasserLesPiecesDansLesCases(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest j = essai.vide(essai.nom("cs_bpleine"));
            ServerPlayer p = j.joueur();
            remplir(p, 0);
            ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
            Purse.add(bourse, Coin.GOLD, Purse.MAX_PER_COIN - 2L);
            p.getInventory().items.set(35, bourse);
            // Deux or entrent encore dans la bourse ; le troisième n'a ni bourse ni case.
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 2))),
                    "deux or tiennent dans la bourse presque pleine");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 3))),
                    "bourse pleine et inventaire plein : le troisième or ne tient pas");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 2),
                    new ItemStack(Coin.GOLD.item(), 1))), "la place de la bourse se compte une seule fois");
            // L'argent n'est pas plein : il entre dans la bourse.
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Coin.SILVER.item(), 64))),
                    "l'argent entre dans la bourse");
            // Une case libre : le surplus d'or y prend place.
            p.getInventory().items.set(0, ItemStack.EMPTY);
            helper.assertTrue(InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 3))),
                    "le surplus d'or prend la case libre");
            helper.assertTrue(!InventoryFit.fits(p, List.of(new ItemStack(Coin.GOLD.item(), 3), new ItemStack(Items.BREAD))),
                    "le surplus d'or occupe la case : plus de place pour un pain");
            helper.assertTrue(Purse.count(p.getInventory().items.get(35), Coin.GOLD) == Purse.MAX_PER_COIN - 2L,
                    "la simulation ne touche pas à la bourse");
            helper.succeed();
        }
    }
}
