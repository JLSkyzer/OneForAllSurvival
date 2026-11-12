package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.PlayerCoins;
import java.io.File;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le registre de l'économie et les pièces au sol : créations et destructions comptées, somme de départ, commandes
 * de l'équipe, ramassage, fusion, sauvegarde.
 *
 * <p>Le registre est unique pour le serveur : un test qui lit un total (ou compare créé et détruit) a son lot à lui,
 * sinon un voisin qui crée ou détruit des pièces au même moment fausserait la lecture. Chaque test lit des écarts
 * (valeur après moins valeur avant), pas des valeurs absolues, puisque les joueurs d'essai des autres lots ont reçu
 * leur somme de départ avant lui.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RegistreGameTests {

    private static final String LOT_SOL = "registre_sol";

    private RegistreGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static EconomyLedger registre(GameTestHelper helper) {
        return EconomyLedger.get(helper.getLevel().getServer());
    }

    /** La zone du test, en coordonnées du monde, plancher compris. */
    private static AABB zone(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        return new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
    }

    private static List<CoinDropEntity> pieces(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(CoinDropEntity.class, zone(helper));
    }

    private static long valeurAuSol(GameTestHelper helper) {
        long total = 0;
        for (CoinDropEntity piece : pieces(helper)) {
            total += piece.value();
        }
        return total;
    }

    private static int auSol(GameTestHelper helper, Coin coin) {
        int total = 0;
        for (CoinDropEntity piece : pieces(helper)) {
            if (piece.coin() == coin) {
                total += piece.count();
            }
        }
        return total;
    }

    /** Les objets au sol qui sont des pièces : il ne doit pas y en avoir. */
    private static int objetsDePiece(GameTestHelper helper) {
        int total = 0;
        for (ItemEntity objet : helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper))) {
            if (Coin.fromItem(objet.getItem().getItem()).isPresent()) {
                total += objet.getItem().getCount();
            }
        }
        return total;
    }

    /**
     * Retire les entités autres que les joueurs et vide la zone de jeu (le gabarit n'est pas vide au-dessus du
     * plancher : une pièce posée en l'air s'y arrêterait) ; à appeler avant de lire un compteur.
     */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                for (int y = 0; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone(helper), e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    /** Une aire close (pourtour, plafond et lumière) pour qu'un joueur d'essai y reste seul. */
    private static void boite(GameTestHelper helper) {
        degager(helper);
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean bord = x == 0 || x == 7 || z == 0 || z == 7;
                for (int y = 0; y < 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), bord ? Blocks.STONE : Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.GLOWSTONE);
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(3.5, 0, 3.5)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    /** Un joueur dont le tic complet tourne à chaque tick (ramassage, physique) : à créer au début du test. */
    private static JoueurDeTest joueurActif(GameTestHelper helper, String nom) {
        JoueurDeTest j = joueur(helper, nom);
        helper.onEachTick(() -> {
            if (j.connecte()) {
                j.joueur().doTick();
            }
        });
        return j;
    }

    private static CoinDropEntity poser(GameTestHelper helper, Coin coin, int nombre, double x, double y, double z) {
        Vec3 p = helper.absoluteVec(new Vec3(x, y, z));
        CoinDropEntity piece = CoinDropEntity.at(helper.getLevel(), p.x, p.y, p.z, coin, nombre);
        piece.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(piece);
        return piece;
    }

    private static CommandSourceStack source(GameTestHelper helper, int niveau) {
        return helper.getLevel().getServer().createCommandSourceStack().withPermission(niveau).withSuppressedOutput();
    }

    /** Lance une commande ; -1 si elle est refusée (permission, syntaxe), sinon le résultat de la commande. */
    private static int lancer(GameTestHelper helper, CommandSourceStack source, String commande) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(commande, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    private static long porte(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static long porte(JoueurDeTest j, Coin coin) {
        return PlayerCoins.held(j.joueur()).getOrDefault(coin, 0L);
    }

    private static void donner(JoueurDeTest j, Coin coin, int nombre) {
        j.joueur().getInventory().add(new ItemStack(coin.item(), nombre));
    }

    private static void remplirDeTerre(ServerPlayer joueur) {
        Inventory inventaire = joueur.getInventory();
        for (int i = 0; i < inventaire.items.size(); i++) {
            inventaire.items.set(i, new ItemStack(Items.DIRT, 64));
        }
    }

    private static long detruitTotal(EconomyLedger registre) {
        long total = 0;
        for (MoneySink sink : MoneySink.values()) {
            total += registre.totalDestroyed(sink);
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------
    // Créations, retraits et somme de départ
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "registre_injection")
    public static void uneInjectionEstCompteeEtNotee(GameTestHelper helper) {
        JoueurDeTest j = joueur(helper, "eco_injection");
        EconomyLedger registre = registre(helper);
        long avant = registre.totalCreated(MoneySource.STAFF);
        long t0 = System.currentTimeMillis();
        CommandSourceStack equipe = j.joueur().createCommandSourceStack().withPermission(2).withSuppressedOutput();
        int resultat = lancer(helper, equipe, "eco give eco_injection 235 prime de bienvenue");
        long t1 = System.currentTimeMillis();
        helper.assertTrue(resultat == 1, "la commande a rendu " + resultat);
        helper.assertTrue(registre.totalCreated(MoneySource.STAFF) - avant == 235,
                "créé STAFF : " + (registre.totalCreated(MoneySource.STAFF) - avant));
        EconomyLedger.Entry ligne = registre.entries(0).get(0);
        helper.assertTrue(ligne.amount() == 235 && ligne.kind().equals("created.staff"), "ligne : " + ligne);
        helper.assertTrue(j.uuid().equals(ligne.author()), "auteur : " + ligne.author());
        helper.assertTrue(ligne.reason().contains("eco_injection") && ligne.reason().contains("prime de bienvenue"),
                "motif : " + ligne.reason());
        helper.assertTrue(ligne.epochMillis() >= t0 && ligne.epochMillis() <= t1, "heure réelle : " + ligne.epochMillis());
        helper.assertTrue(porte(j, Coin.GOLD) == 2 && porte(j, Coin.SILVER) == 3 && porte(j, Coin.COPPER) == 5,
                "pièces reçues : " + PlayerCoins.held(j.joueur()));
        // Sans le niveau 2, rien ne se passe.
        helper.assertTrue(lancer(helper, source(helper, 1), "eco give eco_injection 1000 interdit") == -1,
                "le niveau 1 a pu créer des pièces");
        helper.assertTrue(registre.totalCreated(MoneySource.STAFF) - avant == 235 && porte(j) == 235,
                "le niveau 1 a changé quelque chose");
        j.quitter();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "registre_retrait")
    public static void unRetraitNestJamaisPartiel(GameTestHelper helper) {
        JoueurDeTest j = joueur(helper, "eco_retrait");
        donner(j, Coin.GOLD, 1);
        donner(j, Coin.COPPER, 3);
        EconomyLedger registre = registre(helper);
        long detruitAvant = registre.totalDestroyed(MoneySink.STAFF);
        int lignesAvant = registre.entryCount();
        CommandSourceStack equipe = source(helper, 2);

        helper.assertTrue(lancer(helper, equipe, "eco take eco_retrait 150 trop") == 0, "150 sur 103 devait échouer");
        helper.assertTrue(porte(j, Coin.GOLD) == 1 && porte(j, Coin.COPPER) == 3 && porte(j) == 103,
                "le retrait refusé a pris : " + PlayerCoins.held(j.joueur()));
        helper.assertTrue(registre.totalDestroyed(MoneySink.STAFF) == detruitAvant && registre.entryCount() == lignesAvant,
                "le retrait refusé est dans le registre");

        // 25 sur 103 : 3 cuivre + 1 or pris, 78 rendus (7 argent, 8 cuivre).
        helper.assertTrue(lancer(helper, equipe, "eco take eco_retrait 25 amende") == 1, "25 sur 103 devait passer");
        helper.assertTrue(porte(j) == 78 && porte(j, Coin.GOLD) == 0 && porte(j, Coin.SILVER) == 7
                && porte(j, Coin.COPPER) == 8, "monnaie rendue : " + PlayerCoins.held(j.joueur()));
        helper.assertTrue(registre.totalDestroyed(MoneySink.STAFF) - detruitAvant == 25,
                "détruit STAFF : " + (registre.totalDestroyed(MoneySink.STAFF) - detruitAvant));
        EconomyLedger.Entry ligne = registre.entries(0).get(0);
        helper.assertTrue(ligne.amount() == 25 && ligne.kind().equals("destroyed.staff")
                && ligne.reason().contains("amende"), "ligne : " + ligne);

        helper.assertTrue(lancer(helper, equipe, "eco take eco_retrait 79 trop") == 0, "79 sur 78 devait échouer");
        helper.assertTrue(porte(j) == 78, "le second refus a pris : " + porte(j));
        helper.assertTrue(lancer(helper, equipe, "eco take eco_retrait 78 tout") == 1, "78 sur 78 devait passer");
        helper.assertTrue(porte(j) == 0 && PlayerCoins.held(j.joueur()).isEmpty(), "il reste des pièces");
        helper.assertTrue(registre.totalDestroyed(MoneySink.STAFF) - detruitAvant == 103, "détruit total attendu 103");
        j.quitter();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "registre_depart", timeoutTicks = 200)
    public static void laSommeDeDepartUneSeuleFois(GameTestHelper helper) {
        boite(helper);
        EconomyLedger registre = registre(helper);
        long avant = registre.totalCreated(MoneySource.STARTING);
        Vec3 pos = helper.absoluteVec(new Vec3(3.5, 0, 3.5));
        long somme = EconomyConfig.startingMoney();

        // Première connexion : la somme est donnée, comptée et notée.
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "eco_depart", pos, false, p -> {});
        helper.assertTrue(registre.totalCreated(MoneySource.STARTING) - avant == somme,
                "créé STARTING : " + (registre.totalCreated(MoneySource.STARTING) - avant));
        helper.assertTrue(porte(j) == somme, "inventaire : " + PlayerCoins.held(j.joueur()));
        helper.assertTrue(registre.hasReceivedStarting(j.uuid()), "l'identifiant n'est pas noté");
        EconomyLedger.Entry ligne = registre.entries(0).get(0);
        helper.assertTrue(ligne.kind().equals("created.starting") && ligne.amount() == somme
                && j.uuid().equals(ligne.author()), "ligne : " + ligne);
        j.quitter();

        // Reconnexion : rien de plus, et l'inventaire sauvé est intact.
        j = JoueurDeTest.rejoindre(helper.getLevel(), "eco_depart", pos, false, p -> {});
        helper.assertTrue(registre.totalCreated(MoneySource.STARTING) - avant == somme, "la reconnexion a recréé");
        helper.assertTrue(porte(j) == somme, "après reconnexion : " + PlayerCoins.held(j.joueur()));

        // Mort : l'inventaire tombe, puis une nouvelle connexion ne donne rien.
        j.joueur().die(helper.getLevel().damageSources().generic());
        j.joueur().setHealth(j.joueur().getMaxHealth());
        helper.assertTrue(porte(j) == 0, "l'inventaire n'est pas tombé à la mort : " + PlayerCoins.held(j.joueur()));
        j.quitter();
        JoueurDeTest dernier = JoueurDeTest.rejoindre(helper.getLevel(), "eco_depart", pos, false, p -> {});
        helper.assertTrue(registre.totalCreated(MoneySource.STARTING) - avant == somme,
                "la connexion après la mort a recréé : " + (registre.totalCreated(MoneySource.STARTING) - avant));
        helper.assertTrue(porte(dernier) == 0, "pièces après la mort : " + PlayerCoins.held(dernier.joueur()));
        dernier.quitter();
        // Les pièces tombées à la mort arrivent au tick suivant : on les retire avant de finir.
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(auSol(helper, Coin.GOLD) > 0, "les pièces de la mort ne sont pas tombées en pièces au sol");
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_plein", timeoutTicks = 200)
    public static void inventairePleinLeResteTombe(GameTestHelper helper) {
        boite(helper);
        EconomyLedger registre = registre(helper);
        long avant = registre.totalCreated(MoneySource.STARTING);
        long detruitAvant = detruitTotal(registre);
        long somme = EconomyConfig.startingMoney();
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "eco_plein",
                helper.absoluteVec(new Vec3(3.5, 0, 3.5)), false, p -> remplirDeTerre(p));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        helper.onEachTick(() -> {
            if (j.connecte()) {
                j.joueur().doTick();
            }
        });
        helper.assertTrue(registre.totalCreated(MoneySource.STARTING) - avant == somme,
                "la somme est comptée même si elle tombe : " + (registre.totalCreated(MoneySource.STARTING) - avant));
        helper.assertTrue(porte(j) == 0, "rien ne devait rentrer : " + PlayerCoins.held(j.joueur()));
        helper.assertTrue(valeurAuSol(helper) == somme, "au sol : " + valeurAuSol(helper) + " au lieu de " + somme);
        helper.assertTrue(registre.hasReceivedStarting(j.uuid()), "l'identifiant n'est pas noté");
        // Une case se libère : le joueur ramasse le reste après le délai de ramassage.
        j.joueur().getInventory().items.set(0, ItemStack.EMPTY);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(porte(j) == somme, "ramassé : " + PlayerCoins.held(j.joueur()));
            helper.assertTrue(pieces(helper).isEmpty(), "il reste des pièces au sol : " + valeurAuSol(helper));
            helper.assertTrue(detruitTotal(registre) == detruitAvant, "un ramassage a été compté comme une destruction");
            j.quitter();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Pièces détruites : lave, vide, disparition
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "registre_lave", timeoutTicks = 100)
    public static void unePieceDansLaLaveEstDetruite(GameTestHelper helper) {
        degager(helper);
        // La lave est murée : elle ne coule pas.
        BlockPos lave = new BlockPos(3, 0, 3);
        helper.setBlock(lave, Blocks.LAVA);
        for (BlockPos mur : new BlockPos[] {lave.west(), lave.east(), lave.north(), lave.south()}) {
            helper.setBlock(mur, Blocks.STONE);
        }
        EconomyLedger registre = registre(helper);
        long circulationAvant = registre.inCirculation();
        long feuAvant = registre.totalDestroyed(MoneySink.FIRE);
        // 300 cuivres « créés » : 200 tombent dans la lave, 100 restent sur le sol.
        registre.created(MoneySource.STAFF, 300, null, "essai lave");
        poser(helper, Coin.GOLD, 2, 3.5, 2.0, 3.5);
        poser(helper, Coin.GOLD, 1, 6.5, 0.1, 6.5);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(auSol(helper, Coin.GOLD) == 1, "or restant au sol : " + auSol(helper, Coin.GOLD)
                    + " " + pieces(helper).stream().map(p -> p.getY() + " lave=" + p.isInLava() + " feu="
                    + p.getRemainingFireTicks() + " bloc=" + helper.getLevel().getBlockState(p.blockPosition())).toList());
            helper.assertTrue(registre.totalDestroyed(MoneySink.FIRE) - feuAvant == 200,
                    "détruit FIRE : " + (registre.totalDestroyed(MoneySink.FIRE) - feuAvant));
            helper.assertTrue(registre.inCirculation() - circulationAvant == valeurAuSol(helper),
                    "créé moins détruit : " + (registre.inCirculation() - circulationAvant)
                            + ", au sol : " + valeurAuSol(helper));
            helper.assertTrue(registre.entries(0).get(0).kind().equals("destroyed.fire"), "ligne : " + registre.entries(0).get(0));
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_vide", timeoutTicks = 100)
    public static void unePieceDansLeVideEstDetruite(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long videAvant = registre.totalDestroyed(MoneySink.VOID);
        long autresAvant = detruitTotal(registre) - videAvant;
        Vec3 p = helper.absoluteVec(new Vec3(3.5, 1, 3.5));
        CoinDropEntity piece = CoinDropEntity.at(helper.getLevel(), p.x, -140.0D, p.z, Coin.SILVER, 3);
        piece.setNoGravity(true);
        piece.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(piece);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(piece.isRemoved(), "la pièce est encore là, y = " + piece.getY());
            helper.assertTrue(registre.totalDestroyed(MoneySink.VOID) - videAvant == 30,
                    "détruit VOID : " + (registre.totalDestroyed(MoneySink.VOID) - videAvant));
            helper.assertTrue(detruitTotal(registre) - registre.totalDestroyed(MoneySink.VOID) == autresAvant,
                    "une autre cause a compté");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_sol_perdu", timeoutTicks = 100)
    public static void unePieceQuiDisparaitAuSolEstDetruite(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long avant = registre.totalDestroyed(MoneySink.DESPAWN);
        long autresAvant = detruitTotal(registre) - avant;
        CoinDropEntity vieille = poser(helper, Coin.EMERALD, 2, 2.5, 0.1, 2.5);
        vieille.setAge(CoinDropEntity.LIFETIME - 2);
        CoinDropEntity neuve = poser(helper, Coin.RUBY, 1, 6.5, 0.1, 6.5);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(vieille.isRemoved(), "la pièce de 5 minutes est encore là, âge " + vieille.getAge());
            helper.assertTrue(!neuve.isRemoved(), "la pièce neuve a disparu");
            helper.assertTrue(registre.totalDestroyed(MoneySink.DESPAWN) - avant == 2000,
                    "détruit DESPAWN : " + (registre.totalDestroyed(MoneySink.DESPAWN) - avant));
            helper.assertTrue(detruitTotal(registre) - registre.totalDestroyed(MoneySink.DESPAWN) == autresAvant,
                    "une autre cause a compté");
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_dechargement", timeoutTicks = 100)
    public static void unDechargementNEstPasUneDestruction(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long avant = detruitTotal(registre);
        // Déchargement de chunk et changement de dimension : l'entité est sauvée ou renaît ailleurs.
        CoinDropEntity chunk = poser(helper, Coin.GOLD, 2, 2.5, 0.1, 2.5);
        chunk.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        CoinDropEntity dimension = poser(helper, Coin.GOLD, 3, 3.5, 0.1, 3.5);
        dimension.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        CoinDropEntity avecJoueur = poser(helper, Coin.GOLD, 1, 3.5, 0.1, 5.5);
        avecJoueur.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        helper.assertTrue(detruitTotal(registre) == avant, "un déchargement a été compté : " + (detruitTotal(registre) - avant));
        // La même pièce retirée pour de bon (commande /kill, autre mod) est comptée à la cause STAFF.
        CoinDropEntity tuee = poser(helper, Coin.GOLD, 4, 4.5, 0.1, 4.5);
        tuee.kill();
        helper.assertTrue(detruitTotal(registre) - avant == 400, "détruit : " + (detruitTotal(registre) - avant));
        EconomyLedger.Entry ligne = registre.entries(0).get(0);
        helper.assertTrue(ligne.kind().equals("destroyed.staff") && ligne.amount() == 400, "ligne : " + ligne);
        degager(helper);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Le registre lui-même : plafond et sauvegarde
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "registre_plafond", timeoutTicks = 200)
    public static void leRegistreEstPlafonneMaisPasLesTotaux(GameTestHelper helper) {
        EconomyLedger registre = registre(helper);
        int plafond = EconomyConfig.ledgerMaxLines();
        int ecritures = plafond + 25;
        long avant = registre.totalCreated(MoneySource.STAFF);
        for (int i = 1; i <= ecritures; i++) {
            registre.created(MoneySource.STAFF, 1, null, "n=" + i);
        }
        helper.assertTrue(registre.entryCount() == plafond, "lignes : " + registre.entryCount() + " pour un plafond de " + plafond);
        helper.assertTrue(registre.totalCreated(MoneySource.STAFF) - avant == ecritures,
                "total : " + (registre.totalCreated(MoneySource.STAFF) - avant) + " au lieu de " + ecritures);
        helper.assertTrue(registre.entries(0).get(0).reason().equals("n=" + ecritures), "plus récente : " + registre.entries(0).get(0));
        List<EconomyLedger.Entry> derniere = registre.entries(registre.pageCount() - 1);
        helper.assertTrue(derniere.get(derniere.size() - 1).reason().equals("n=" + (ecritures - plafond + 1)),
                "plus ancienne gardée : " + derniere.get(derniere.size() - 1));
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "registre_rechargement", timeoutTicks = 100)
    public static void leRegistreSurvitAuRechargement(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        EconomyLedger registre = registre(helper);
        UUID auteur = UUID.fromString("00000000-0000-0000-0000-0000000e1002");
        registre.created(MoneySource.STAFF, 42, auteur, "avant le rechargement");
        registre.destroyed(MoneySink.FIRE, 7, null, "feu");
        registre.markStartingGiven(auteur);
        // Écrit sur le disque, puis relit avec un stockage neuf : c'est ce que fait un redémarrage.
        serveur.overworld().getDataStorage().save();
        File dossier = serveur.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
        helper.assertTrue(new File(dossier, EconomyLedger.KEY + ".dat").isFile(), "le fichier du registre n'est pas écrit");
        EconomyLedger relu = new DimensionDataStorage(dossier, serveur.getFixerUpper())
                .get(EconomyLedger::load, EconomyLedger.KEY);
        helper.assertTrue(relu != null && relu != registre, "registre non relu du disque");
        for (MoneySource source : MoneySource.values()) {
            helper.assertTrue(relu.totalCreated(source) == registre.totalCreated(source), "créé " + source);
        }
        for (MoneySink sink : MoneySink.values()) {
            helper.assertTrue(relu.totalDestroyed(sink) == registre.totalDestroyed(sink), "détruit " + sink);
        }
        helper.assertTrue(relu.entryCount() == registre.entryCount(), "lignes : " + relu.entryCount());
        helper.assertTrue(relu.entries(0).equals(registre.entries(0)), "première page différente");
        helper.assertTrue(relu.hasReceivedStarting(auteur), "la somme de départ servie est oubliée");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Tout ce qui tombe au sol devient une pièce du mod
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "registre_chemins", timeoutTicks = 100)
    public static void unePieceLacheeEstUneEntiteDuMod(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "eco_lache");
        Level level = helper.getLevel();
        Vec3 coin = helper.absoluteVec(new Vec3(5.5, 0.5, 5.5));
        // Touche Q (inventaire fermé) ou curseur lâché hors de l'écran.
        j.joueur().drop(new ItemStack(Coin.GOLD.item(), 3), false, true);
        // Bloc cassé qui lâche une pièce.
        Block.popResource(level, helper.absolutePos(new BlockPos(5, 0, 5)), new ItemStack(Coin.SILVER.item(), 2));
        // Contenu d'un coffre qui se vide.
        Containers.dropItemStack(level, coin.x, coin.y, coin.z, new ItemStack(Coin.COPPER.item(), 4));
        // Mort : l'inventaire tombe.
        j.joueur().getInventory().add(new ItemStack(Coin.EMERALD.item(), 1));
        j.joueur().getInventory().dropAll();
        // Un objet de pièce ajouté au monde par n'importe quel autre moyen.
        level.addFreshEntity(new ItemEntity(level, coin.x, coin.y, coin.z, new ItemStack(Coin.RUBY.item(), 1)));
        EconomyLedger registre = registre(helper);
        long detruitAvant = detruitTotal(registre);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(objetsDePiece(helper) == 0, "des pièces sont restées des objets : " + objetsDePiece(helper));
            helper.assertTrue(auSol(helper, Coin.GOLD) == 3, "or : " + auSol(helper, Coin.GOLD));
            helper.assertTrue(auSol(helper, Coin.SILVER) == 2, "argent : " + auSol(helper, Coin.SILVER));
            helper.assertTrue(auSol(helper, Coin.COPPER) == 4, "cuivre : " + auSol(helper, Coin.COPPER));
            helper.assertTrue(auSol(helper, Coin.EMERALD) == 1, "émeraude : " + auSol(helper, Coin.EMERALD));
            helper.assertTrue(auSol(helper, Coin.RUBY) == 1, "rubis : " + auSol(helper, Coin.RUBY));
            // Le jeu donne un délai à ce qu'un joueur lâche, à un butin de bloc, à la mort ; pas à un coffre qui se vide
            // (cuivre) ni à un objet ajouté tel quel (rubis) : la pièce reprend ce que l'objet avait.
            for (CoinDropEntity piece : pieces(helper)) {
                boolean attendu = piece.coin() != Coin.COPPER && piece.coin() != Coin.RUBY;
                helper.assertTrue(piece.hasPickUpDelay() == attendu, "délai de ramassage de " + piece.coin() + " : "
                        + piece.hasPickUpDelay());
            }
            CoinDropEntity lachee = pieces(helper).stream().filter(p -> p.coin() == Coin.GOLD).findFirst().orElseThrow();
            helper.assertTrue(j.uuid().equals(lachee.thrower()), "le lanceur est perdu : " + lachee.thrower());
            helper.assertTrue(detruitTotal(registre) == detruitAvant,
                    "le remplacement d'un objet a compté une destruction : " + (detruitTotal(registre) - detruitAvant));
            // Rien dans le monde ne vient de sortir de nulle part : le registre n'a rien vu naître, et c'est normal ici.
            degager(helper);
            j.quitter();
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_explosion", timeoutTicks = 100)
    public static void uneExplosionNeDetruitPasLesPieces(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long avant = detruitTotal(registre);
        CoinDropEntity piece = poser(helper, Coin.GOLD, 5, 3.5, 0.1, 3.5);
        ItemEntity terre = helper.spawnItem(Items.DIRT, 3.5F, 0.3F, 3.5F);
        Vec3 p = helper.absoluteVec(new Vec3(3.5, 0.5, 3.5));
        helper.getLevel().explode(null, p.x, p.y, p.z, 4.0F, Level.ExplosionInteraction.NONE);
        helper.runAfterDelay(5, () -> {
            // Le témoin prouve que l'explosion a bien frappé les entités à cet endroit.
            helper.assertTrue(terre.isRemoved(), "l'objet témoin a survécu à l'explosion : elle n'a rien prouvé");
            helper.assertTrue(!piece.isRemoved() && piece.count() == 5, "la pièce a été touchée par l'explosion");
            helper.assertTrue(detruitTotal(registre) == avant, "l'explosion a compté une destruction");
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_give", timeoutTicks = 200)
    public static void giveNeFabriquePasDePiece(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueurActif(helper, "eco_give");
        CommandSourceStack equipe = source(helper, 2);
        // /give lâche un objet factice pour l'animation de ramassage : il ne doit pas devenir une pièce.
        helper.assertTrue(lancer(helper, equipe, "give eco_give oneforallsurvival:gold_coin 5") == 1, "/give a échoué");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(porte(j, Coin.GOLD) == 5, "or reçu : " + porte(j, Coin.GOLD));
            helper.assertTrue(pieces(helper).isEmpty(), "le /give a fait naître une pièce au sol : " + valeurAuSol(helper));
            helper.assertTrue(objetsDePiece(helper) == 0, "un objet de pièce traîne");
            // Inventaire plein : tout tombe, rien de plus.
            remplirDeTerre(j.joueur());
            helper.assertTrue(lancer(helper, equipe, "give eco_give oneforallsurvival:copper_coin 7") == 1,
                    "/give plein a échoué");
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(auSol(helper, Coin.COPPER) == 7, "cuivre au sol : " + auSol(helper, Coin.COPPER));
            helper.assertTrue(porte(j, Coin.COPPER) == 0, "du cuivre est aussi rentré : " + porte(j, Coin.COPPER));
            j.joueur().getInventory().items.set(1, ItemStack.EMPTY);
            // Le joueur va sur les pièces : lâchées à bout de bras, elles sont retombées hors de sa portée.
            CoinDropEntity cuivre = pieces(helper).get(0);
            j.joueur().moveTo(cuivre.getX(), cuivre.getY(), cuivre.getZ());
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(porte(j, Coin.COPPER) == 7, "cuivre ramassé : " + porte(j, Coin.COPPER));
            helper.assertTrue(pieces(helper).isEmpty(), "des pièces traînent encore");
            j.quitter();
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_SOL, timeoutTicks = 100)
    public static void seulUnJoueurRamasseUnePiece(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueurActif(helper, "eco_ramasse");
        FakePlayer faux = FakePlayerFactory.getMinecraft(helper.getLevel());
        CoinDropEntity libre = poser(helper, Coin.SILVER, 3, 3.5, 0.1, 3.5);
        libre.setPickUpDelay(0);
        CoinDropEntity reservee = poser(helper, Coin.GOLD, 1, 3.5, 0.1, 3.5);
        reservee.setPickUpDelay(0);
        reservee.setTarget(UUID.fromString("00000000-0000-0000-0000-0000000e1003"));
        // Un faux joueur (déployeur de Create, etc.) a beau la toucher, elle reste.
        libre.playerTouch(faux);
        helper.assertTrue(!libre.isRemoved() && libre.count() == 3, "un faux joueur a ramassé la pièce");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(libre.isRemoved(), "le joueur n'a pas ramassé la pièce libre");
            helper.assertTrue(porte(j, Coin.SILVER) == 3, "argent ramassé : " + porte(j, Coin.SILVER));
            helper.assertTrue(!reservee.isRemoved() && porte(j, Coin.GOLD) == 0,
                    "le joueur a ramassé une pièce réservée à un autre");
            degager(helper);
            j.quitter();
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_fusion", timeoutTicks = 100)
    public static void deuxPiecesDeMemeCoupureSeFondent(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long avant = detruitTotal(registre);
        poser(helper, Coin.GOLD, 3, 3.5, 0.1, 3.5);
        poser(helper, Coin.GOLD, 4, 3.6, 0.1, 3.6);
        poser(helper, Coin.SILVER, 5, 3.55, 0.1, 3.55);
        poser(helper, Coin.GOLD, 2, 6.5, 0.1, 6.5);
        helper.runAfterDelay(45, () -> {
            List<CoinDropEntity> or = pieces(helper).stream().filter(p -> p.coin() == Coin.GOLD).toList();
            helper.assertTrue(or.size() == 2, "piles d'or : " + or.size());
            helper.assertTrue(or.stream().anyMatch(p -> p.count() == 7) && or.stream().anyMatch(p -> p.count() == 2),
                    "contenu des piles d'or : " + or.stream().map(CoinDropEntity::count).toList());
            helper.assertTrue(auSol(helper, Coin.SILVER) == 5 && pieces(helper).size() == 3,
                    "l'argent ne devait pas se fondre dans l'or");
            helper.assertTrue(valeurAuSol(helper) == 900 + 50, "valeur au sol : " + valeurAuSol(helper));
            helper.assertTrue(detruitTotal(registre) == avant, "une fusion a compté une destruction");
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "registre_entonnoir", timeoutTicks = 100)
    public static void unEntonnoirNeRamassePasUnePiece(GameTestHelper helper) {
        degager(helper);
        EconomyLedger registre = registre(helper);
        long avant = detruitTotal(registre);
        BlockPos entonnoirPiece = new BlockPos(2, 0, 3);
        BlockPos entonnoirTemoin = new BlockPos(5, 0, 3);
        helper.setBlock(entonnoirPiece, Blocks.HOPPER);
        helper.setBlock(entonnoirTemoin, Blocks.HOPPER);
        CoinDropEntity piece = poser(helper, Coin.GOLD, 4, 2.5, 1.2, 3.5);
        piece.setPickUpDelay(0);
        // Le témoin : le même entonnoir prend bien un objet ordinaire.
        helper.spawnItem(Items.DIRT, 5.5F, 1.2F, 3.5F);
        helper.runAfterDelay(50, () -> {
            HopperBlockEntity sans = (HopperBlockEntity) helper.getBlockEntity(entonnoirPiece);
            HopperBlockEntity avec = (HopperBlockEntity) helper.getBlockEntity(entonnoirTemoin);
            boolean temoinPris = false;
            for (int i = 0; i < avec.getContainerSize(); i++) {
                temoinPris |= avec.getItem(i).is(Items.DIRT);
            }
            helper.assertTrue(temoinPris, "l'entonnoir témoin n'a rien pris : le test ne prouve rien");
            helper.assertTrue(sans.isEmpty(), "l'entonnoir a pris la pièce : " + sans.getItem(0));
            helper.assertTrue(!piece.isRemoved() && piece.count() == 4, "la pièce a disparu du sol");
            helper.assertTrue(detruitTotal(registre) == avant, "une destruction a été comptée");
            degager(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_SOL, timeoutTicks = 100)
    public static void uneEntiteSeRelitDeSonNbt(GameTestHelper helper) {
        degager(helper);
        CoinDropEntity piece = poser(helper, Coin.EMERALD, 12, 3.5, 0.1, 3.5);
        piece.setAge(777);
        piece.setPickUpDelay(33);
        CompoundTag tag = new CompoundTag();
        piece.saveWithoutId(tag);
        CoinDropEntity lue = new CoinDropEntity(EconomyRegistry.coinDropType(), helper.getLevel());
        lue.load(tag);
        helper.assertTrue(lue.coin() == Coin.EMERALD && lue.count() == 12, "relue : " + lue.coin() + " x" + lue.count());
        helper.assertTrue(lue.getAge() == 777 && lue.hasPickUpDelay(), "âge ou délai perdus");
        helper.assertTrue(lue.value() == 12_000, "valeur : " + lue.value());
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation("oneforallsurvival", "coin_drop")),
                "le type d'entité n'est pas enregistré");
        helper.assertTrue(piece.getAddEntityPacket() != null, "pas de paquet d'apparition pour les clients");
        degager(helper);
        helper.succeed();
    }
}
