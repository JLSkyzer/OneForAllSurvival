package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinDropEntity;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.MoneySource;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneySink;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Purse;
import dev.oneforallsurvival.economy.PurseActionPacket;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.PurseItem;
import dev.oneforallsurvival.economy.PurseMenu;
import dev.oneforallsurvival.bench.BenchCatalog;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La bourse : ce qu'elle ramasse, ce qu'elle paie, son écran, son refus là où une pièce est refusée, et la bourse
 * pleine jetée au sol (entité du mod, comptée au registre). Les paquets de l'écran sont rejoués côté serveur, sans
 * le réseau, comme pour le coffre-fort.
 *
 * <p>Chaque test vide son aire et rend ses joueurs ; le registre est global, il ne se lit que par différence, dans
 * un test synchrone ou dans son lot.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class BourseGameTests {

    private static final String LOT_A = "bourse_a";
    private static final String LOT_B = "bourse_b";
    private static final String LOT_C = "bourse_c";
    private static final String LOT_D = "bourse_d";
    private static final String LOT_E = "bourse_e";
    private static final String LOT_G = "bourse_depart";
    /** Seul dans son lot : il détruit de l'argent, et le registre est global. */
    private static final String LOT_F = "bourse_registre";

    private BourseGameTests() {}

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

    private static JoueurDeTest joueur(GameTestHelper helper, String nom) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(4.5, 0, 4.5)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    /** Une bourse qui contient ces pièces : {@code bourse(Coin.GOLD, 3, Coin.SILVER, 5)}. */
    private static ItemStack bourse(Object... pieces) {
        ItemStack bourse = new ItemStack(EconomyRegistry.PURSE.get());
        for (int i = 0; i < pieces.length; i += 2) {
            Purse.add(bourse, (Coin) pieces[i], ((Number) pieces[i + 1]).longValue());
        }
        return bourse;
    }

    /** La bourse est la case {@code indice} de l'inventaire du joueur ; la même pile. */
    private static ItemStack porter(JoueurDeTest j, int indice, ItemStack bourse) {
        j.joueur().getInventory().setItem(indice, bourse);
        return bourse;
    }

    private static void donnerEnVrac(JoueurDeTest j, Coin coin, int nombre) {
        int reste = nombre;
        while (reste > 0) {
            int taille = Math.min(reste, 64);
            j.joueur().getInventory().add(new ItemStack(coin.item(), taille));
            reste -= taille;
        }
    }

    private static long enVrac(JoueurDeTest j, Coin coin) {
        return PlayerCoins.heldLoose(j.joueur()).getOrDefault(coin, 0L);
    }

    private static long valeurPortee(JoueurDeTest j) {
        return CoinMath.valueOf(PlayerCoins.held(j.joueur()));
    }

    private static void remplirDeTerre(JoueurDeTest j) {
        for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
            if (j.joueur().getInventory().items.get(i).isEmpty()) {
                j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
        }
    }

    private static List<CoinDropEntity> auSol(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(CoinDropEntity.class, zone(helper));
    }

    private static List<CoinDropEntity> boursesAuSol(GameTestHelper helper) {
        return auSol(helper).stream().filter(CoinDropEntity::isPurse).toList();
    }

    private static long valeurAuSol(GameTestHelper helper) {
        return auSol(helper).stream().mapToLong(CoinDropEntity::value).sum();
    }

    private static EconomyLedger registre(GameTestHelper helper) {
        return EconomyLedger.get(helper.getLevel().getServer());
    }

    /** Clic droit en main : ouvre l'écran de la bourse tenue en main principale. */
    private static void ouvrirEcran(JoueurDeTest j) {
        ServerPlayer p = j.joueur();
        p.getItemInHand(InteractionHand.MAIN_HAND).use(p.serverLevel(), p, InteractionHand.MAIN_HAND);
    }

    private static Result agir(JoueurDeTest j, PurseActionPacket paquet) {
        return PurseActionPacket.apply(j.joueur(), paquet);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : ramassage, paiement
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void elleRamasseLesPieces(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_ramasse");
        try {
            ItemStack premiere = porter(j, 3, bourse());
            ItemStack seconde = porter(j, 1, bourse());
            ServerPlayer p = j.joueur();
            Vec3 pos = p.position();

            // Le ramassage va dans la première bourse de l'inventaire (dans l'ordre des cases : la case 1), pas ailleurs.
            CoinDropEntity or = CoinDropEntity.at(helper.getLevel(), pos.x, pos.y, pos.z, Coin.GOLD, 3);
            or.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(or);
            or.playerTouch(p);
            helper.assertTrue(or.isRemoved(), "les pièces devaient être ramassées");
            helper.assertTrue(Purse.count(seconde, Coin.GOLD) == 3 && Purse.count(premiere, Coin.GOLD) == 0,
                    "l'or va à la première bourse : " + Purse.contents(seconde) + " / " + Purse.contents(premiere));
            helper.assertTrue(Purse.firstPurse(p).orElseThrow() == seconde, "la première bourse est la case 1");
            helper.assertTrue(enVrac(j, Coin.GOLD) == 0, "rien dans l'inventaire : " + PlayerCoins.heldLoose(p));

            // Quand la première est pleine pour cette coupure, le reste passe à la suivante, puis à l'inventaire.
            Purse.add(seconde, Coin.SILVER, Purse.MAX_PER_COIN - 1);
            Purse.add(premiere, Coin.SILVER, Purse.MAX_PER_COIN - 2);
            CoinDropEntity argent = CoinDropEntity.at(helper.getLevel(), pos.x, pos.y, pos.z, Coin.SILVER, 10);
            argent.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(argent);
            argent.playerTouch(p);
            helper.assertTrue(Purse.count(seconde, Coin.SILVER) == Purse.MAX_PER_COIN
                            && Purse.count(premiere, Coin.SILVER) == Purse.MAX_PER_COIN
                            && enVrac(j, Coin.SILVER) == 7,
                    "1 puis 2 dans les bourses, 7 dans l'inventaire : " + Purse.count(seconde, Coin.SILVER) + " "
                            + Purse.count(premiere, Coin.SILVER) + " " + enVrac(j, Coin.SILVER));

            // Sans bourse, les pièces vont dans l'inventaire comme avant.
            p.getInventory().setItem(3, ItemStack.EMPTY);
            p.getInventory().setItem(1, ItemStack.EMPTY);
            CoinDropEntity cuivre = CoinDropEntity.at(helper.getLevel(), pos.x, pos.y, pos.z, Coin.COPPER, 4);
            cuivre.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(cuivre);
            cuivre.playerTouch(p);
            helper.assertTrue(enVrac(j, Coin.COPPER) == 4, "sans bourse : dans l'inventaire");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void payerRendLaMonnaieExacte(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_paie");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.GOLD, 1));
            EconomyLedger registre = registre(helper);
            long circulation = registre.inCirculation();
            int lignes = registre.entryCount();

            long paye = Purse.pay(j.joueur(), 30);
            helper.assertTrue(paye == 30, "le montant payé est renvoyé : " + paye);
            EnumMap<Coin, Long> attendu = new EnumMap<>(Coin.class);
            attendu.put(Coin.SILVER, 7L);
            helper.assertTrue(Purse.contents(bourse).equals(attendu),
                    "1 or pour 30 cuivres : 7 argent rendus dans la bourse, vu " + Purse.contents(bourse));
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).isEmpty(), "la monnaie va dans la bourse, pas en vrac");
            helper.assertTrue(valeurPortee(j) == 70, "il reste 70 cuivres : " + valeurPortee(j));
            // Payer ne crée ni ne détruit : la somme part chez l'appelant, qui l'inscrira.
            helper.assertTrue(registre.inCirculation() == circulation && registre.entryCount() == lignes,
                    "le paiement n'écrit rien au registre");

            // Le tout : la bourse vide, sans monnaie.
            helper.assertTrue(Purse.pay(j.joueur(), 70) == 70 && Purse.total(bourse) == 0, "70 payés, bourse vide");
            helper.assertTrue(bourse.getTag() == null, "une bourse vide n'a plus de NBT");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void payerPlusQueLeContenuNeTouchePasLaBourse(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_trop");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.COPPER, 5, Coin.GOLD, 1));
            EnumMap<Coin, Long> avant = Purse.contents(bourse);
            donnerEnVrac(j, Coin.SILVER, 2);
            long valeur = valeurPortee(j);

            helper.assertTrue(Purse.pay(j.joueur(), valeur + 1) == 0, "pas assez : rien n'est payé");
            helper.assertTrue(Purse.contents(bourse).equals(avant) && enVrac(j, Coin.SILVER) == 2,
                    "rien ne bouge : " + Purse.contents(bourse) + " " + PlayerCoins.heldLoose(j.joueur()));
            helper.assertTrue(Purse.pay(j.joueur(), 0) == 0 && Purse.pay(j.joueur(), -10) == 0, "un prix nul ou négatif ne paie rien");
            helper.assertTrue(Purse.contents(bourse).equals(avant), "toujours rien");
            helper.assertTrue(auSol(helper).isEmpty(), "rien ne tombe au sol");
            helper.assertTrue(valeurPortee(j) == valeur, "valeur portée inchangée");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void payerPrendDAbordDansLesBourses(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_ordre");
        try {
            // Les bourses suffisent : les pièces en vrac ne bougent pas.
            ItemStack bourse = porter(j, 0, bourse(Coin.SILVER, 3));
            donnerEnVrac(j, Coin.COPPER, 100);
            helper.assertTrue(Purse.pay(j.joueur(), 10) == 10, "paiement par la bourse");
            helper.assertTrue(Purse.count(bourse, Coin.SILVER) == 2 && enVrac(j, Coin.COPPER) == 100,
                    "la bourse paie seule : " + Purse.contents(bourse) + " " + PlayerCoins.heldLoose(j.joueur()));

            // Elles ne suffisent pas (20 + 5 < 100) : on complète avec les pièces en vrac, bourses d'abord.
            ItemStack petite = porter(j, 20, bourse(Coin.COPPER, 5));
            helper.assertTrue(Purse.pay(j.joueur(), 100) == 100, "paiement mixte");
            helper.assertTrue(Purse.total(bourse) == 0 && Purse.total(petite) == 0 && enVrac(j, Coin.COPPER) == 25,
                    "bourses vidées, 75 cuivres pris en vrac : " + Purse.contents(bourse) + " " + Purse.contents(petite)
                            + " " + PlayerCoins.heldLoose(j.joueur()));
            helper.assertTrue(valeurPortee(j) == 25, "125 - 100 : " + valeurPortee(j));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void payerMixteVideLesBoursesAvantLeVrac(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_mixte");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.COPPER, 5));
            donnerEnVrac(j, Coin.COPPER, 5);
            helper.assertTrue(Purse.pay(j.joueur(), 8) == 8, "8 sur 10");
            helper.assertTrue(Purse.total(bourse) == 0 && enVrac(j, Coin.COPPER) == 2,
                    "la bourse est vidée d'abord : " + Purse.contents(bourse) + " " + PlayerCoins.heldLoose(j.joueur()));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansBourseLaMonnaieVaALInventaireOuAuSol(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_sans");
        try {
            donnerEnVrac(j, Coin.GOLD, 1);
            helper.assertTrue(Purse.pay(j.joueur(), 30) == 30, "paiement sans bourse");
            helper.assertTrue(enVrac(j, Coin.SILVER) == 7 && enVrac(j, Coin.GOLD) == 0,
                    "7 argent rendus dans l'inventaire : " + PlayerCoins.heldLoose(j.joueur()));

            // Inventaire plein : la monnaie tombe aux pieds, rien n'est perdu ni créé.
            j.joueur().getInventory().clearContent();
            j.joueur().getInventory().items.set(0, new ItemStack(Coin.GOLD.item(), 2));
            for (int i = 1; i < j.joueur().getInventory().items.size(); i++) {
                j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            helper.assertTrue(Purse.pay(j.joueur(), 30) == 30, "paiement, inventaire plein");
            helper.assertTrue(valeurPortee(j) == 100 && valeurAuSol(helper) == 70,
                    "70 cuivres de monnaie au sol : porté " + valeurPortee(j) + ", sol " + valeurAuSol(helper));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unTakeDeLEquipePrendDansLaBourse(GameTestHelper helper) throws Exception {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_take");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.GOLD, 2));
            donnerEnVrac(j, Coin.COPPER, 3);
            MinecraftServer serveur = helper.getLevel().getServer();
            EconomyLedger registre = registre(helper);
            long detruit = registre.totalDestroyed(MoneySink.STAFF);
            serveur.getCommands().getDispatcher().execute("eco take bo_take 50 essai",
                    serveur.createCommandSourceStack().withPermission(4));
            helper.assertTrue(registre.totalDestroyed(MoneySink.STAFF) - detruit == 50, "50 cuivres détruits au registre");
            helper.assertTrue(Purse.total(bourse) + CoinMath.valueOf(PlayerCoins.heldLoose(j.joueur())) == 200 + 3 - 50,
                    "le joueur a perdu 50 : " + valeurPortee(j));
            helper.assertTrue(Purse.total(bourse) == 150, "la bourse a payé seule, monnaie rendue dedans : " + Purse.contents(bourse));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : l'écran
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void retirerParCoupure(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_retire");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.SILVER, 25, Coin.GOLD, 2));
            j.joueur().getInventory().selected = 0;
            ouvrirEcran(j);
            helper.assertTrue(j.joueur().containerMenu instanceof PurseMenu menu && menu.slot() == 0,
                    "le clic droit devait ouvrir l'écran de la bourse : " + j.joueur().containerMenu);
            helper.assertTrue(MoneyStorageRules.isAllowedMenu(j.joueur().containerMenu), "l'écran est un rangement permis");

            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.SILVER, 10)) == Result.DONE, "retrait de 10 argent");
            helper.assertTrue(Purse.count(bourse, Coin.SILVER) == 15 && enVrac(j, Coin.SILVER) == 10,
                    "15 dans la bourse, 10 dans l'inventaire : " + Purse.contents(bourse));
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.GOLD, 1)) == Result.DONE, "retrait d'1 or");
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.GOLD, 10)) == Result.INSUFFICIENT, "10 or sur 1");
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.RUBY, 1)) == Result.INSUFFICIENT, "aucun rubis");
            helper.assertTrue(valeurPortee(j) == 250 + 200, "rien créé, rien perdu : " + valeurPortee(j));
            // Des montants, coupures, actions hors des boutons : refusés sans effet.
            for (int montant : new int[] {0, -1, 2, 5, 63, 65, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new PurseActionPacket(0, new CoinRequest(CoinRequest.WITHDRAW, 0, montant, false))) == Result.BAD_REQUEST,
                        "montant " + montant);
            }
            for (int coupure : new int[] {-1, 5, 99, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new PurseActionPacket(0, new CoinRequest(CoinRequest.WITHDRAW, coupure, 1, false))) == Result.BAD_REQUEST,
                        "coupure " + coupure);
            }
            for (int action : new int[] {-1, 3, 99, Integer.MIN_VALUE}) {
                helper.assertTrue(agir(j, new PurseActionPacket(0, new CoinRequest(action, 0, 1, false))) == Result.BAD_REQUEST, "action " + action);
            }
            // Une autre case que celle de l'écran : refusé.
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(5, Coin.SILVER, 1)) == Result.NOT_OPEN, "mauvaise case");
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(-3, Coin.SILVER, 1)) == Result.NOT_OPEN, "case négative");
            helper.assertTrue(valeurPortee(j) == 450, "toujours 450");
            // Écran fermé : plus rien ne passe.
            j.joueur().closeContainer();
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.SILVER, 1)) == Result.NOT_OPEN, "écran fermé");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deposerEtRegrouper(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_depot");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.COPPER, 5));
            ItemStack autre = porter(j, 1, bourse(Coin.GOLD, 4));
            donnerEnVrac(j, Coin.COPPER, 70);
            donnerEnVrac(j, Coin.SILVER, 3);
            long valeur = valeurPortee(j);
            ouvrirEcran(j);

            helper.assertTrue(agir(j, PurseActionPacket.depositAll(0)) == Result.DONE, "dépôt");
            helper.assertTrue(PlayerCoins.heldLoose(j.joueur()).isEmpty(), "tout est passé dans la bourse");
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) == 75 && Purse.count(bourse, Coin.SILVER) == 3
                            && Purse.count(autre, Coin.GOLD) == 4, "bourse " + Purse.contents(bourse) + ", l'autre " + Purse.contents(autre));
            helper.assertTrue(valeurPortee(j) == valeur, "valeur égale");
            helper.assertTrue(agir(j, PurseActionPacket.depositAll(0)) == Result.NOTHING, "plus rien à déposer");

            // Regrouper à valeur égale, au rapport vivant : 75 cuivres = 7 argent + 5, avec les 3 déjà là : 10 argent = 1 or.
            helper.assertTrue(agir(j, PurseActionPacket.groupAll(0)) == Result.DONE, "regroupement");
            EnumMap<Coin, Long> attendu = new EnumMap<>(Coin.class);
            attendu.put(Coin.COPPER, 5L);
            attendu.put(Coin.GOLD, 1L);
            helper.assertTrue(Purse.contents(bourse).equals(attendu), "75 cuivres + 3 argent = 1 or + 5 cuivres : " + Purse.contents(bourse));
            helper.assertTrue(valeurPortee(j) == valeur, "le regroupement garde la valeur");
            helper.assertTrue(agir(j, PurseActionPacket.groupAll(0)) == Result.NOTHING, "rien à regrouper");

            // Le plafond de la bourse : le dépôt ne perd rien.
            Purse.add(bourse, Coin.GOLD, Purse.MAX_PER_COIN - 1 - 1);
            donnerEnVrac(j, Coin.GOLD, 5);
            helper.assertTrue(agir(j, PurseActionPacket.depositAll(0)) == Result.DONE, "dépôt jusqu'au plafond");
            helper.assertTrue(Purse.count(bourse, Coin.GOLD) == Purse.MAX_PER_COIN && enVrac(j, Coin.GOLD) == 4,
                    "une seule pièce d'or a la place : en vrac " + enVrac(j, Coin.GOLD));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lEcranRecoitLeContenuMemeGrand(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_ecran");
        try {
            long[] nombres = {12, 70_000, 5_000_000_000L, 123_456_789_012L, Purse.MAX_PER_COIN};
            ItemStack bourse = porter(j, 0, bourse());
            for (Coin coin : Coin.values()) {
                Purse.add(bourse, coin, nombres[coin.ordinal()]);
            }
            ouvrirEcran(j);
            AbstractContainerMenu serveur = j.joueur().containerMenu;
            FriendlyByteBuf tampon = new FriendlyByteBuf(Unpooled.buffer());
            tampon.writeVarInt(0);
            PurseMenu client = PurseMenu.fromNetwork(serveur.containerId, new Inventory(j.joueur()), tampon);
            j.synchroniser(client);
            for (Coin coin : Coin.values()) {
                helper.assertTrue(client.count(coin) == nombres[coin.ordinal()],
                        "client " + coin + " : " + client.count(coin) + " au lieu de " + nombres[coin.ordinal()]);
            }
            helper.assertTrue(client.total() == Purse.total(bourse), "total du client");
            helper.assertTrue(client.slot() == 0, "la case");
            Purse.remove(bourse, Coin.COPPER, 2);
            serveur.broadcastChanges();
            j.synchroniser(client);
            helper.assertTrue(client.count(Coin.COPPER) == 10, "le client suit : " + client.count(Coin.COPPER));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    /**
     * Pendant l'écran, la case de la bourse ouverte est verrouillée : ni prise au curseur, ni remplacée, ni échangée
     * par une touche numérique ; les autres cases de l'inventaire restent libres.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laCaseDeLaBourseOuverteEstVerrouillee(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_verrou");
        try {
            ItemStack bourse = porter(j, 2, bourse(Coin.GOLD, 4));
            j.joueur().getInventory().selected = 2;
            j.joueur().getInventory().setItem(3, new ItemStack(Items.DIRT, 5));
            ouvrirEcran(j);
            helper.assertTrue(j.joueur().containerMenu instanceof PurseMenu menu && menu.slot() == 2, "écran ouvert");
            AbstractContainerMenu menu = j.joueur().containerMenu;
            helper.assertTrue(menu.slots.size() == 36, "les 36 cases de l'inventaire : " + menu.slots.size());
            // Dans le menu, la barre d'action est en 27 à 35 : la bourse (case 2 de l'inventaire) est en 29.
            int caseBourse = 27 + 2;
            helper.assertTrue(menu.getSlot(caseBourse).getItem() == bourse, "la case 29 est la bourse");
            menu.clicked(caseBourse, 0, ClickType.PICKUP, j.joueur());
            helper.assertTrue(menu.getCarried().isEmpty() && j.joueur().getInventory().getItem(2) == bourse,
                    "la bourse ne se prend pas : curseur " + menu.getCarried());
            menu.clicked(caseBourse, 0, ClickType.QUICK_MOVE, j.joueur());
            menu.clicked(caseBourse, 0, ClickType.THROW, j.joueur());
            helper.assertTrue(j.joueur().getInventory().getItem(2) == bourse, "ni maj-clic ni jet");
            // Touche numérique « 3 » (case 2) au-dessus d'une autre case : la bourse ne s'échange pas.
            menu.clicked(0, 2, ClickType.SWAP, j.joueur());
            helper.assertTrue(j.joueur().getInventory().getItem(2) == bourse, "pas d'échange par la touche numérique");
            // Une autre case reste libre : la terre se prend et se repose.
            menu.clicked(27 + 3, 0, ClickType.PICKUP, j.joueur());
            helper.assertTrue(menu.getCarried().is(Items.DIRT), "les autres cases restent libres");
            menu.clicked(caseBourse, 0, ClickType.PICKUP, j.joueur());
            helper.assertTrue(menu.getCarried().is(Items.DIRT) && j.joueur().getInventory().getItem(2) == bourse,
                    "rien ne se pose sur la case de la bourse");
            menu.clicked(27 + 3, 0, ClickType.PICKUP, j.joueur());
            helper.assertTrue(menu.stillValid(j.joueur()) && Purse.total(bourse) == 400, "l'écran reste valable");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lEcranSeFermeSiLaBourseQuitteSaCase(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_ferme");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.GOLD, 2));
            ouvrirEcran(j);
            helper.assertTrue(j.joueur().containerMenu instanceof PurseMenu, "écran ouvert");
            // La bourse est jetée (ou volée) pendant que l'écran est ouvert : plus rien ne s'y fait.
            j.joueur().getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.GOLD, 1)) == Result.NOT_OPEN,
                    "bourse partie : plus d'action");
            helper.assertTrue(!j.joueur().containerMenu.stillValid(j.joueur()), "l'écran n'est plus valable");
            // Une autre pile dans la case ne se laisse pas prendre pour la bourse.
            j.joueur().getInventory().setItem(0, new ItemStack(Items.DIRT, 3));
            helper.assertTrue(agir(j, PurseActionPacket.depositAll(0)) == Result.NOT_OPEN, "autre objet dans la case");
            helper.assertTrue(Purse.total(bourse) == 200, "la bourse n'a pas bougé");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneBourseNeSEmpileNiNeSeRange(GameTestHelper helper) {
        ItemStack bourse = bourse(Coin.GOLD, 1);
        helper.assertTrue(bourse.getMaxStackSize() == 1, "stacksTo(1)");
        helper.assertTrue(!bourse.getItem().canFitInsideContainerItems(), "pas dans une sacoche ni une boîte");
        helper.assertTrue(new ItemStack(EconomyRegistry.PURSE.get()).is(BenchCatalog.TABLE_ONLY), "dans table_only");
        // Le contenu survit à une sauvegarde de la pile.
        CompoundTag nbt = bourse(Coin.GOLD, 3, Coin.RUBY, 1_000_000_000_000L).save(new CompoundTag());
        ItemStack relue = ItemStack.of(nbt);
        helper.assertTrue(Purse.count(relue, Coin.GOLD) == 3 && Purse.count(relue, Coin.RUBY) == 1_000_000_000_000L,
                "le NBT redonne le contenu : " + Purse.contents(relue));
        // Un NBT hors bornes est ramené dans les bornes (jamais négatif, jamais au-dessus du plafond).
        ItemStack truquee = new ItemStack(EconomyRegistry.PURSE.get());
        CompoundTag coins = new CompoundTag();
        coins.putLong("gold", -5);
        coins.putLong("ruby", Long.MAX_VALUE);
        truquee.getOrCreateTag().put(Purse.TAG, coins);
        helper.assertTrue(Purse.count(truquee, Coin.GOLD) == 0 && Purse.count(truquee, Coin.RUBY) == Purse.MAX_PER_COIN,
                "NBT hors bornes : " + Purse.contents(truquee));
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : là où une pièce est refusée, la bourse pleine l'est aussi
    // ---------------------------------------------------------------------------------------------

    private static AbstractContainerMenu ouvrirBloc(GameTestHelper helper, JoueurDeTest j, net.minecraft.world.level.block.Block bloc) {
        BlockPos pos = new BlockPos(3, 0, 3);
        helper.setBlock(pos, bloc);
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be instanceof MenuProvider, bloc + " n'a pas d'écran : " + be);
        j.joueur().openMenu((MenuProvider) be);
        helper.assertTrue(j.joueur().containerMenu != j.joueur().inventoryMenu, "l'écran ne s'est pas ouvert");
        return j.joueur().containerMenu;
    }

    private static Slot premiereCaseDuRangement(AbstractContainerMenu menu, ServerPlayer joueur) {
        for (Slot slot : menu.slots) {
            if (slot.container != joueur.getInventory()) {
                return slot;
            }
        }
        throw new AssertionError("pas de case de rangement");
    }

    /** Pose l'objet sur le curseur et clique la première case du rangement ; rend ce que la case contient ensuite. */
    private static ItemStack cliquerDansLeRangement(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        Slot cible = premiereCaseDuRangement(menu, j.joueur());
        cible.set(ItemStack.EMPTY);
        menu.setCarried(objet.copy());
        menu.clicked(cible.index, 0, ClickType.PICKUP, j.joueur());
        menu.setCarried(ItemStack.EMPTY);
        return cible.getItem().copy();
    }

    /** Maj-clic depuis la case 9 de l'inventaire. */
    private static boolean majCliquerDepuisLInventaire(AbstractContainerMenu menu, JoueurDeTest j, ItemStack objet) {
        Slot cible = premiereCaseDuRangement(menu, j.joueur());
        cible.set(ItemStack.EMPTY);
        j.joueur().getInventory().setItem(9, objet.copy());
        for (Slot slot : menu.slots) {
            if (slot.container == j.joueur().getInventory() && slot.getContainerSlot() == 9) {
                menu.clicked(slot.index, 0, ClickType.QUICK_MOVE, j.joueur());
                break;
            }
        }
        boolean entree = !cible.getItem().isEmpty();
        for (Slot slot : menu.slots) {
            if (slot.container != j.joueur().getInventory() && !slot.getItem().isEmpty()) {
                entree = true;
            }
        }
        return entree;
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBourseVidePasse(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_vide");
        try {
            ItemStack vide = bourse();
            helper.assertTrue(!MoneyStorageRules.isMoney(vide), "une bourse vide n'est pas de l'argent");
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            ItemStack dedans = cliquerDansLeRangement(menu, j, vide);
            helper.assertTrue(dedans.getItem() instanceof PurseItem, "la bourse vide devait entrer dans le coffre : " + dedans);
            // Le témoin du même geste avec une pièce : refusée, ce qui prouve que le geste porte.
            helper.assertTrue(cliquerDansLeRangement(menu, j, new ItemStack(Coin.GOLD.item())).isEmpty(), "témoin : la pièce est refusée");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBoursePleineRefuseeDansUnCoffre(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_coffre");
        try {
            ItemStack pleine = bourse(Coin.COPPER, 1);
            helper.assertTrue(MoneyStorageRules.isMoney(pleine), "une bourse qui contient une pièce est de l'argent");
            AbstractContainerMenu menu = ouvrirBloc(helper, j, Blocks.CHEST);
            helper.assertTrue(cliquerDansLeRangement(menu, j, pleine).isEmpty(), "clic : la bourse pleine est entrée");
            helper.assertTrue(!majCliquerDepuisLInventaire(menu, j, pleine), "maj-clic : la bourse pleine est entrée");
            helper.assertTrue(j.joueur().getInventory().getItem(9).getItem() instanceof PurseItem, "elle reste dans l'inventaire");
            // Touche numérique.
            Slot cible = premiereCaseDuRangement(menu, j.joueur());
            j.joueur().getInventory().setItem(0, pleine.copy());
            menu.clicked(cible.index, 0, ClickType.SWAP, j.joueur());
            helper.assertTrue(cible.getItem().isEmpty(), "touche numérique : la bourse pleine est entrée");
            helper.assertTrue(j.joueur().getInventory().getItem(0).getItem() instanceof PurseItem, "elle n'a pas changé de main");
            // Témoin : de la terre entre par le même geste.
            helper.assertTrue(!cliquerDansLeRangement(menu, j, new ItemStack(Items.DIRT, 8)).isEmpty(), "témoin : la terre entre");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBoursePleineRefusee(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_refus");
        try {
            ItemStack pleine = bourse(Coin.SILVER, 2);
            // Un tonneau et une boîte de Shulker, par le clic.
            for (net.minecraft.world.level.block.Block bloc : new net.minecraft.world.level.block.Block[] {Blocks.BARREL, Blocks.SHULKER_BOX}) {
                AbstractContainerMenu menu = ouvrirBloc(helper, j, bloc);
                helper.assertTrue(cliquerDansLeRangement(menu, j, pleine).isEmpty(), bloc + " : bourse pleine acceptée");
                helper.assertTrue(!majCliquerDepuisLInventaire(menu, j, pleine), bloc + " : maj-clic accepté");
                helper.assertTrue(!cliquerDansLeRangement(menu, j, new ItemStack(Items.DIRT, 8)).isEmpty(),
                        bloc + " : le témoin n'est pas entré, le geste ne prouve rien");
                j.joueur().closeContainer();
            }
            // Un entonnoir qui pousse : refusé.
            HopperBlockEntity entonnoir = new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState());
            ItemStack reste = HopperBlockEntity.addItem(null, entonnoir, pleine.copy(), null);
            helper.assertTrue(!reste.isEmpty() && entonnoir.isEmpty(), "l'entonnoir a reçu la bourse pleine : " + entonnoir.getItem(0));
            // Le témoin : la terre entre dans le même entonnoir.
            helper.assertTrue(HopperBlockEntity.addItem(null, entonnoir, new ItemStack(Items.DIRT, 2), null).isEmpty(), "témoin entonnoir");
            // Dans une autre bourse : elle n'a pas de case, donc pas de chemin ; elle ne se range dans aucun conteneur d'objets.
            helper.assertTrue(!pleine.getItem().canFitInsideContainerItems(), "pas dans un sac");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBoursePleineNEntrePasDansUneGrilleDeFabrication(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_grille");
        try {
            ItemStack pleine = bourse(Coin.GOLD, 1);
            ItemStack vide = bourse();
            ServerPlayer p = j.joueur();
            // La table de fabrication et la grille 2x2 du joueur : la pièce entre (le change), la bourse pleine non.
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv,
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 3)))),
                    net.minecraft.network.chat.Component.literal("table")));
            for (AbstractContainerMenu menu : new AbstractContainerMenu[] {p.containerMenu, p.inventoryMenu}) {
                Slot grille = menu.getSlot(1);
                helper.assertTrue(grille.mayPlace(new ItemStack(Coin.GOLD.item())), "la pièce entre dans la grille (change)");
                helper.assertTrue(grille.mayPlace(vide), "la bourse vide entre dans la grille");
                helper.assertTrue(!grille.mayPlace(pleine), "la bourse pleine ne doit pas entrer dans la grille");
                // Le geste réel : elle reste sur le curseur.
                menu.setCarried(pleine.copy());
                menu.clicked(1, 0, ClickType.PICKUP, p);
                helper.assertTrue(grille.getItem().isEmpty(), "clic : la bourse pleine est dans la grille");
                menu.setCarried(ItemStack.EMPTY);
                p.getInventory().setItem(9, pleine.copy());
                for (Slot slot : menu.slots) {
                    if (slot.container == p.getInventory() && slot.getContainerSlot() == 9) {
                        menu.clicked(slot.index, 0, ClickType.QUICK_MOVE, p);
                    }
                }
                helper.assertTrue(grille.getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),
                        "maj-clic : la bourse pleine est dans la grille");
                helper.assertTrue(Purse.purses(p).size() == 1, "elle est restée dans l'inventaire");
                p.getInventory().clearContent();
            }
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void uneBourseNeSeLitQueSiElleContientUnePiece(GameTestHelper helper) {
        ItemStack b = bourse(Coin.GOLD, 1);
        helper.assertTrue(MoneyStorageRules.isMoney(b), "pleine : argent");
        Purse.remove(b, Coin.GOLD, 1);
        helper.assertTrue(!MoneyStorageRules.isMoney(b), "revidée : plus d'argent");
        helper.assertTrue(!MoneyStorageRules.isMoney(new ItemStack(Items.LEATHER)), "témoin : du cuir");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot D : la bourse pleine jetée au sol
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_D, timeoutTicks = 120)
    public static void uneBoursePleineJeteeNEstPasRamasseeParUnEntonnoir(GameTestHelper helper) {
        boite(helper);
        BlockPos entonnoirPurse = new BlockPos(2, 0, 3);
        BlockPos entonnoirTemoin = new BlockPos(5, 0, 3);
        helper.setBlock(entonnoirPurse, Blocks.HOPPER);
        helper.setBlock(entonnoirTemoin, Blocks.HOPPER);
        // Posée comme un objet ordinaire : le jeu la remplace par l'entité du mod.
        Vec3 p = helper.absoluteVec(new Vec3(2.5, 1.2, 3.5));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), p.x, p.y, p.z, bourse(Coin.GOLD, 4)));
        helper.spawnItem(Items.DIRT, 5.5F, 1.2F, 3.5F);
        helper.runAfterDelay(60, () -> {
            HopperBlockEntity sans = (HopperBlockEntity) helper.getBlockEntity(entonnoirPurse);
            HopperBlockEntity avec = (HopperBlockEntity) helper.getBlockEntity(entonnoirTemoin);
            boolean temoin = false;
            for (int i = 0; i < avec.getContainerSize(); i++) {
                temoin |= avec.getItem(i).is(Items.DIRT);
            }
            helper.assertTrue(temoin, "l'entonnoir témoin n'a rien pris : le test ne prouve rien");
            helper.assertTrue(sans.isEmpty(), "l'entonnoir a pris la bourse : " + sans.getItem(0));
            List<CoinDropEntity> bourses = boursesAuSol(helper);
            helper.assertTrue(bourses.size() == 1 && bourses.get(0).value() == 400 && !bourses.get(0).isRemoved(),
                    "la bourse pleine est restée au sol, entité du mod : " + bourses.size());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper),
                    o -> o.getItem().getItem() instanceof PurseItem).isEmpty(), "aucun objet de bourse ne traîne");
            boite(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_D, timeoutTicks = 120)
    public static void viderPuisJeterNeDupliquePas(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_jette");
        porter(j, 0, bourse(Coin.GOLD, 3));
        j.joueur().getInventory().selected = 0;
        EconomyLedger registre = registre(helper);
        long circulation = registre.inCirculation();
        ouvrirEcran(j);
        helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.GOLD, 1)) == Result.DONE, "retrait d'1 or");
        helper.assertTrue(valeurPortee(j) == 300, "300 cuivres portés");
        // Jeter la bourse (2 or dedans) : une seule entité, et la case est vide.
        ItemStack lancee = j.joueur().getInventory().removeItem(0, 1);
        helper.assertTrue(Purse.total(lancee) == 200, "la pile jetée porte les 200 cuivres : " + Purse.total(lancee));
        j.joueur().drop(lancee, false, true);
        helper.assertTrue(j.joueur().getInventory().getItem(0).isEmpty(), "la case est vide");
        helper.runAfterDelay(5, () -> {
            try {
                List<CoinDropEntity> bourses = boursesAuSol(helper);
                helper.assertTrue(bourses.size() == 1 && bourses.get(0).value() == 200,
                        "une seule bourse au sol, 200 cuivres : " + bourses.size());
                helper.assertTrue(valeurPortee(j) == 100 && valeurAuSol(helper) == 200, "portés 100, sol 200");
                helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.GOLD, 1)) == Result.NOT_OPEN,
                        "l'écran s'est fermé avec la bourse, aucun retrait");
                // Ramassée par son lanceur (après le délai), elle revient pleine, et l'entité disparaît.
                CoinDropEntity auSol = bourses.get(0);
                auSol.setPickUpDelay(0);
                auSol.playerTouch(j.joueur());
                helper.assertTrue(auSol.isRemoved(), "ramassée");
                helper.assertTrue(Purse.firstPurse(j.joueur()).map(Purse::total).orElse(-1L) == 200, "la bourse est de retour, 200 dedans");
                helper.assertTrue(boursesAuSol(helper).isEmpty(), "plus de bourse au sol");
                helper.assertTrue(valeurPortee(j) == 300, "300 cuivres, ni plus ni moins : " + valeurPortee(j));
                helper.assertTrue(registre.inCirculation() == circulation, "le registre n'a rien vu naître ni disparaître");
                // Tout retirer puis jeter : la bourse vide reste un objet ordinaire, rien de plus au sol.
                ItemStack retour = Purse.firstPurse(j.joueur()).orElseThrow();
                int indice = -1;
                for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                    if (j.joueur().getInventory().items.get(i) == retour) {
                        indice = i;
                    }
                }
                helper.assertTrue(indice >= 0, "la bourse est dans l'inventaire");
                Purse.remove(retour, Coin.GOLD, 2);
                PlayerCoins.receiveLoose(j.joueur(), Coin.GOLD, 2);
                j.joueur().drop(j.joueur().getInventory().removeItem(indice, 1), false, true);
                helper.runAfterDelay(5, () -> {
                    try {
                        helper.assertTrue(boursesAuSol(helper).isEmpty(), "une bourse vide n'est pas une entité du mod");
                        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper),
                                o -> o.getItem().getItem() instanceof PurseItem).isEmpty(), "c'est un objet ordinaire");
                        helper.assertTrue(valeurPortee(j) == 300 && valeurAuSol(helper) == 0, "la valeur n'a pas bougé");
                        helper.succeed();
                    } finally {
                        quitter(j);
                        boite(helper);
                    }
                });
            } catch (RuntimeException e) {
                quitter(j);
                boite(helper);
                throw e;
            }
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_F, timeoutTicks = 100)
    public static void uneBourseDansLaLaveOuQuiDisparaitEstComptee(GameTestHelper helper) {
        boite(helper);
        EconomyLedger registre = registre(helper);
        long feuAvant = registre.totalDestroyed(MoneySink.FIRE);
        long solAvant = registre.totalDestroyed(MoneySink.DESPAWN);
        long videAvant = registre.totalDestroyed(MoneySink.VOID);
        // Trois bourses posées comme des objets, remplacées par le jeu.
        Vec3 a = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
        Vec3 b = helper.absoluteVec(new Vec3(5.5, 1.0, 2.5));
        Vec3 c = helper.absoluteVec(new Vec3(3.5, 1.0, 5.5));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), a.x, a.y, a.z, bourse(Coin.GOLD, 2)));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), b.x, b.y, b.z, bourse(Coin.EMERALD, 1)));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), c.x, c.y, c.z, bourse(Coin.SILVER, 3)));
        helper.runAfterDelay(4, () -> {
            List<CoinDropEntity> bourses = boursesAuSol(helper);
            helper.assertTrue(bourses.size() == 3, "trois bourses au sol : " + bourses.size());
            for (CoinDropEntity e : bourses) {
                if (e.value() == 200) {
                    // Le feu : la lave comme les flammes.
                    e.hurt(helper.getLevel().damageSources().lava(), 4.0F);
                } else if (e.value() == 1000) {
                    // Cinq minutes au sol.
                    e.setAge(CoinDropEntity.LIFETIME - 2);
                } else {
                    // Sous le monde.
                    e.setNoGravity(true);
                    e.setDeltaMovement(Vec3.ZERO);
                    e.setPos(e.getX(), -140.0D, e.getZ());
                }
            }
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(boursesAuSol(helper).isEmpty(), "toutes les bourses ont disparu");
                helper.assertTrue(registre.totalDestroyed(MoneySink.FIRE) - feuAvant == 200,
                        "feu : " + (registre.totalDestroyed(MoneySink.FIRE) - feuAvant));
                helper.assertTrue(registre.totalDestroyed(MoneySink.DESPAWN) - solAvant == 1000,
                        "disparue au sol : " + (registre.totalDestroyed(MoneySink.DESPAWN) - solAvant));
                helper.assertTrue(registre.totalDestroyed(MoneySink.VOID) - videAvant == 30,
                        "vide : " + (registre.totalDestroyed(MoneySink.VOID) - videAvant));
                helper.succeed();
            });
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_D)
    public static void uneBourseAuSolSeRelitDeSonNbtEtSeVole(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest voleur = joueur(helper, "bo_voleur");
        try {
            ItemStack bourse = bourse(Coin.GOLD, 2, Coin.COPPER, 7);
            ItemEntity objet = new ItemEntity(helper.getLevel(), 0, 0, 0, bourse);
            CoinDropEntity sol = (CoinDropEntity) EconomyRegistry.PURSE.get().createEntity(helper.getLevel(), objet, bourse);
            helper.assertTrue(sol != null && sol.isPurse() && sol.value() == 207, "l'entité porte la bourse");
            CompoundTag nbt = sol.saveWithoutId(new CompoundTag());
            CoinDropEntity lue = new CoinDropEntity(EconomyRegistry.coinDropType(), helper.getLevel());
            lue.load(nbt);
            helper.assertTrue(lue.isPurse() && lue.value() == 207 && Purse.count(lue.purse(), Coin.COPPER) == 7,
                    "relue : " + lue.value());
            // N'importe qui la ramasse (sans lanceur) : elle va dans une case libre, jamais dans une autre bourse.
            lue.setPos(voleur.joueur().position());
            lue.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(lue);
            lue.playerTouch(voleur.joueur());
            helper.assertTrue(lue.isRemoved() && valeurPortee(voleur) == 207, "le voleur a la bourse : " + valeurPortee(voleur));
            helper.assertTrue(Purse.purses(voleur.joueur()).size() == 1, "une seule bourse");
            // Inventaire plein : elle reste au sol, entière.
            ItemStack autre = bourse(Coin.RUBY, 1);
            CoinDropEntity deux = (CoinDropEntity) EconomyRegistry.PURSE.get().createEntity(helper.getLevel(),
                    new ItemEntity(helper.getLevel(), 0, 0, 0, autre), autre);
            remplirDeTerre(voleur);
            deux.setPos(voleur.joueur().position());
            deux.setPickUpDelay(0);
            helper.getLevel().addFreshEntity(deux);
            deux.playerTouch(voleur.joueur());
            helper.assertTrue(!deux.isRemoved() && deux.value() == 10000, "inventaire plein : elle reste au sol");
            helper.succeed();
        } finally {
            quitter(voleur);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_D, timeoutTicks = 100)
    public static void uneBourseAuSolNeFusionnePasAvecUnePileDePieces(GameTestHelper helper) {
        boite(helper);
        Vec3 p = helper.absoluteVec(new Vec3(3.5, 0.1, 3.5));
        CoinDropEntity pile = CoinDropEntity.at(helper.getLevel(), p.x, p.y, p.z, Coin.COPPER, 3);
        pile.setPickUpDelay(0);
        pile.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(pile);
        // La bourse au sol garde la coupure par défaut (cuivre) : la même que la pile. Seule l'exclusion des
        // bourses dans la fusion peut empêcher qu'elle soit absorbée.
        ItemStack bourse = bourse(Coin.GOLD, 2);
        CoinDropEntity sol = (CoinDropEntity) EconomyRegistry.PURSE.get().createEntity(helper.getLevel(),
                new ItemEntity(helper.getLevel(), p.x, p.y, p.z, bourse), bourse);
        sol.setPos(p.x, p.y, p.z);
        sol.setPickUpDelay(0);
        sol.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(sol);
        helper.assertTrue(sol.coin() == pile.coin(), "témoin : la bourse et la pile ont la même coupure, " + sol.coin());
        // Témoin positif : deux piles de cuivre au même endroit fusionnent dans la même fenêtre.
        Vec3 q = helper.absoluteVec(new Vec3(5.5, 0.1, 5.5));
        CoinDropEntity un = CoinDropEntity.at(helper.getLevel(), q.x, q.y, q.z, Coin.COPPER, 3);
        CoinDropEntity deux = CoinDropEntity.at(helper.getLevel(), q.x, q.y, q.z, Coin.COPPER, 2);
        un.setPickUpDelay(0);
        deux.setPickUpDelay(0);
        un.setDeltaMovement(Vec3.ZERO);
        deux.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(un);
        helper.getLevel().addFreshEntity(deux);
        // Au-delà de l'intervalle de fusion (20 ticks), plusieurs fois.
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(un.isRemoved() != deux.isRemoved() && (un.isRemoved() ? deux : un).count() == 5,
                    "témoin : les deux piles de cuivre devaient fusionner : " + un.count() + " / " + deux.count());
            helper.assertTrue(!pile.isRemoved() && pile.count() == 3 && !pile.isPurse(),
                    "la pile de pièces a fusionné : " + pile.count() + " removed=" + pile.isRemoved());
            helper.assertTrue(!sol.isRemoved() && sol.isPurse() && sol.value() == 200 && Purse.count(sol.purse(), Coin.GOLD) == 2,
                    "la bourse a fusionné : " + sol.value() + " removed=" + sol.isRemoved());
            helper.assertTrue(valeurAuSol(helper) == 3 + 200 + 5, "208 cuivres au sol : " + valeurAuSol(helper));
            boite(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_G)
    public static void ecoGiveVaDansLaBourse(GameTestHelper helper) throws Exception {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_give");
        try {
            ItemStack bourse = porter(j, 4, bourse());
            MinecraftServer serveur = helper.getLevel().getServer();
            EconomyLedger registre = registre(helper);
            long cree = registre.totalCreated(MoneySource.STAFF);
            serveur.getCommands().getDispatcher().execute("eco give bo_give 250 essai",
                    serveur.createCommandSourceStack().withPermission(4));
            helper.assertTrue(registre.totalCreated(MoneySource.STAFF) - cree == 250, "250 cuivres créés au registre");
            helper.assertTrue(Purse.total(bourse) == 250 && PlayerCoins.heldLoose(j.joueur()).isEmpty(),
                    "tout dans la bourse : " + Purse.contents(bourse) + ", en vrac " + PlayerCoins.heldLoose(j.joueur()));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_G)
    public static void laSommeDeDepartVaDansLaBourse(GameTestHelper helper) {
        boite(helper);
        String nom = "bo_depart_" + Long.toHexString(System.nanoTime() & 0xFFFFFFFFL);
        long somme = EconomyConfig.startingMoney();
        helper.assertTrue(somme > 0, "témoin : la somme de départ est configurée à zéro");
        ItemStack bourse = bourse();
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(new Vec3(4.5, 0, 4.5)), false,
                p -> p.getInventory().setItem(2, bourse));
        try {
            ItemStack dedans = j.joueur().getInventory().getItem(2);
            helper.assertTrue(Purse.isPurse(dedans), "la bourse posée avant l'entrée en jeu est là : " + dedans);
            helper.assertTrue(Purse.total(dedans) == somme && PlayerCoins.heldLoose(j.joueur()).isEmpty(),
                    "la somme de départ est dans la bourse : " + Purse.contents(dedans) + ", en vrac "
                            + PlayerCoins.heldLoose(j.joueur()));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot E : atomicité
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_E, timeoutTicks = 100)
    public static void deuxClicsRapidesNeDupliquentPas(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_deux_clics");
        ItemStack bourse = porter(j, 0, bourse(Coin.COPPER, 100));
        j.joueur().getInventory().selected = 0;
        ouvrirEcran(j);
        helper.assertTrue(j.joueur().containerMenu instanceof PurseMenu, "écran ouvert");
        // Deux retraits de 64 envoyés avant que le serveur ne traite le premier : la bourse n'en a que pour un.
        j.envoyer(PurseActionPacket.withdraw(0, Coin.COPPER, 64));
        j.envoyer(PurseActionPacket.withdraw(0, Coin.COPPER, 64));
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(enVrac(j, Coin.COPPER) == 64 && Purse.count(bourse, Coin.COPPER) == 36,
                        "un seul retrait de 64 : porté " + enVrac(j, Coin.COPPER) + ", bourse " + Purse.count(bourse, Coin.COPPER));
                helper.assertTrue(j.messagesRecus().contains(PurseActionPacket.MESSAGE + "insufficient"),
                        "le second clic est refusé avec un message");
                // Deux dépôts de suite : le second ne trouve plus rien.
                j.envoyer(PurseActionPacket.depositAll(0));
                j.envoyer(PurseActionPacket.depositAll(0));
            } catch (RuntimeException e) {
                quitter(j);
                boite(helper);
                throw e;
            }
        });
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(enVrac(j, Coin.COPPER) == 0 && Purse.count(bourse, Coin.COPPER) == 100,
                        "100 dans la bourse, rien de plus : " + Purse.count(bourse, Coin.COPPER));
                helper.assertTrue(j.messagesRecus().contains(PurseActionPacket.MESSAGE + "nothing"), "le second dépôt dit rien");
                // Départ juste après l'envoi : le total (bourse + ce que le parti emporte) ne change pas.
                j.envoyer(PurseActionPacket.withdraw(0, Coin.COPPER, 10));
                j.quitter();
            } catch (RuntimeException e) {
                quitter(j);
                boite(helper);
                throw e;
            }
        });
        helper.runAfterDelay(15, () -> {
            try {
                helper.assertTrue(Purse.count(bourse, Coin.COPPER) + enVrac(j, Coin.COPPER) == 100,
                        "ni perdu ni créé : bourse " + Purse.count(bourse, Coin.COPPER) + ", porté " + enVrac(j, Coin.COPPER));
                helper.succeed();
            } finally {
                quitter(j);
                boite(helper);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_E)
    public static void unJoueurPartiNeDeclencheRien(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_parti");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.GOLD, 10));
            donnerEnVrac(j, Coin.COPPER, 20);
            j.joueur().getInventory().selected = 0;
            ouvrirEcran(j);
            ServerPlayer joueur = j.joueur();
            j.quitter();
            helper.assertTrue(PurseActionPacket.apply(joueur, PurseActionPacket.withdraw(0, Coin.GOLD, 1)) == Result.GONE,
                    "retrait après le départ");
            helper.assertTrue(PurseActionPacket.apply(joueur, PurseActionPacket.depositAll(0)) == Result.GONE, "dépôt après le départ");
            helper.assertTrue(PurseActionPacket.apply(joueur, PurseActionPacket.groupAll(0)) == Result.GONE, "regroupement après le départ");
            helper.assertTrue(Purse.count(bourse, Coin.GOLD) == 10 && Purse.count(bourse, Coin.COPPER) == 0,
                    "la bourse est intacte : " + Purse.contents(bourse));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_E)
    public static void inventairePleinRienNeSePerd(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_plein");
        try {
            ItemStack bourse = porter(j, 0, bourse(Coin.COPPER, 100));
            j.joueur().getInventory().selected = 0;
            ouvrirEcran(j);
            remplirDeTerre(j);
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.COPPER, 64)) == Result.FULL, "inventaire plein");
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) == 100 && enVrac(j, Coin.COPPER) == 0,
                    "rien ne sort : " + Purse.count(bourse, Coin.COPPER));
            helper.assertTrue(auSol(helper).isEmpty(), "rien ne tombe au sol");

            // Une case se libère à moitié : seul ce qui entre quitte la bourse.
            j.joueur().getInventory().items.set(1, new ItemStack(Coin.COPPER.item(), 60));
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.COPPER, 10)) == Result.DONE, "entrée partielle");
            helper.assertTrue(enVrac(j, Coin.COPPER) == 64 && Purse.count(bourse, Coin.COPPER) == 96,
                    "porté " + enVrac(j, Coin.COPPER) + ", bourse " + Purse.count(bourse, Coin.COPPER));
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.COPPER, 1)) == Result.FULL, "plus de place");
            helper.assertTrue(valeurPortee(j) == 160, "160 cuivres au total : " + valeurPortee(j));

            // Un retrait n'entre jamais dans la bourse elle-même : en créatif, plein, rien ne disparaît non plus.
            j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(j.joueur().getAbilities().instabuild, "créatif");
            helper.assertTrue(agir(j, PurseActionPacket.withdraw(0, Coin.COPPER, 1)) == Result.FULL, "créatif, plein");
            helper.assertTrue(Purse.count(bourse, Coin.COPPER) == 96, "l'argent non pris reste dans la bourse");
            // Le ramassage en créatif va dans la bourse, sans mensonge d'Inventory.add.
            long recu = PlayerCoins.receive(j.joueur(), Coin.GOLD, 5);
            helper.assertTrue(recu == 5 && Purse.count(bourse, Coin.GOLD) == 5, "créatif : 5 or dans la bourse, vu " + recu);
            // Sans bourse, inventaire plein, créatif : rien n'entre, rien n'est annoncé.
            j.joueur().getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(PlayerCoins.receive(j.joueur(), Coin.GOLD, 5) == 0, "rien n'entre, rien n'est annoncé");
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_E)
    public static void lePaiementEnCreatifPleinNeCreeRien(GameTestHelper helper) {
        boite(helper);
        JoueurDeTest j = joueur(helper, "bo_crea_paie");
        try {
            j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            ItemStack bourse = porter(j, 0, bourse(Coin.GOLD, 1));
            for (int i = 1; i < j.joueur().getInventory().items.size(); i++) {
                j.joueur().getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
            }
            // La monnaie va dans la bourse (qui vient d'être vidée de l'or) : rien n'est perdu.
            helper.assertTrue(Purse.pay(j.joueur(), 30) == 30, "paiement");
            helper.assertTrue(Purse.total(bourse) == 70 && auSol(helper).isEmpty(), "70 restent dans la bourse : " + Purse.contents(bourse));
            helper.succeed();
        } finally {
            quitter(j);
            boite(helper);
        }
    }
}
