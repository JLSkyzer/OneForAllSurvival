package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchConfig;
import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.BenchRecipe;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlockEntity;
import dev.oneforallsurvival.bench.EngineerBenchMenu;
import dev.oneforallsurvival.bench.OrderResult;
import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.WorkshopEntry;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le bloc de l'établi, les commandes et le réseau, avec de vrais joueurs sur des connexions embarquées.
 * Chaque essai a son lot et installe son propre catalogue, qu'il rend avant de finir : les ateliers sont
 * ceux du monde, nettoyés à la fin. Les rappels d'un même tick n'ont pas d'ordre, donc chaque contrôle a son
 * propre tick et les durées se mesurent entre deux ticks distincts.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class BenchOrdersGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Vec3 JOUEUR = new Vec3(2.5, 1.0, 2.5);

    /** 2 fer + 1 cuivre donnent 3 lingots d'or, en une seconde. */
    private static final ResourceLocation R_FER = new ResourceLocation("oneforallsurvival", "test_cmd_fer");
    /** Seau de lait + sucre donnent du pain, et le seau revient. */
    private static final ResourceLocation R_LAIT = new ResourceLocation("oneforallsurvival", "test_cmd_lait");
    /** Une pioche (usée, nommée) + un bâton. */
    private static final ResourceLocation R_PIOCHE = new ResourceLocation("oneforallsurvival", "test_cmd_pioche");
    /** Dix minutes : ne finit jamais pendant un essai. */
    private static final ResourceLocation R_LONGUE = new ResourceLocation("oneforallsurvival", "test_cmd_longue");

    private BenchOrdersGameTests() {}

    private static BenchRecipe recette(ResourceLocation id, ItemStack resultat, int secondes, Ingredient... ingredients) {
        return new BenchRecipe(id, resultat, List.of(ingredients), "crafting", secondes);
    }

    private static List<BenchRecipe> recettes() {
        return List.of(
                recette(R_FER, new ItemStack(Items.GOLD_INGOT, 3), 1, Ingredient.of(Items.IRON_INGOT),
                        Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.COPPER_INGOT)),
                recette(R_LAIT, new ItemStack(Items.BREAD), 1, Ingredient.of(Items.MILK_BUCKET),
                        Ingredient.of(Items.SUGAR)),
                recette(R_PIOCHE, new ItemStack(Items.DIAMOND), 1, Ingredient.of(Items.IRON_PICKAXE),
                        Ingredient.of(Items.STICK)),
                recette(R_LONGUE, new ItemStack(Items.EMERALD), 600, Ingredient.of(Items.IRON_INGOT),
                        Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.COPPER_INGOT)));
    }

    /** Installe le catalogue d'essai et rend l'ancien, à remettre en fin d'essai. */
    private static BenchCatalog installer() {
        return BenchCatalog.install(BenchCatalog.of(recettes()));
    }

    private static MinecraftServer serveur(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom, boolean ingenieur) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
        RoleBook book = RoleSavedData.get(serveur(helper)).book();
        book.assign(j.uuid(), ingenieur ? RoleBook.ENGINEER_ROLE_ID : RoleBook.DEFAULT_ROLE_ID);
        return j;
    }

    private static PlayerWorkshop atelier(GameTestHelper helper, UUID joueur) {
        return WorkshopSavedData.get(serveur(helper)).of(joueur);
    }

    private static void viderFile(PlayerWorkshop atelier) {
        for (WorkshopEntry e : new ArrayList<>(atelier.queue())) {
            atelier.cancel(e.id());
        }
    }

    private static void garnir(ServerPlayer p, int index, ItemStack pile) {
        p.getInventory().items.set(index, pile);
    }

    private static void viderInventaire(ServerPlayer p) {
        p.getInventory().clearContent();
    }

    private static int compter(ServerPlayer p, Item item) {
        return p.getInventory().countItem(item);
    }

    private static int compterStock(PlayerWorkshop atelier, Item item) {
        int n = 0;
        for (ItemStack s : atelier.stock()) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void quitter(JoueurDeTest j, PlayerWorkshop atelier) {
        if (atelier != null) {
            viderFile(atelier);
        }
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    // --- Commande ----------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_cmd_citoyen")
    public static void unCitoyenEstRefuse(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "CitoyenEssai", false);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.NOT_ENGINEER, "résultat attendu NOT_ENGINEER, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 5 && compter(j.joueur(), Items.COPPER_INGOT) == 1,
                    "l'inventaire a changé");
            helper.assertTrue(atelier.queue().isEmpty(), "la file devait rester vide");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_paie")
    public static void unIngenieurCommandeEtPaie(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurPaie", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            BlockPos banc = helper.absolutePos(POS);
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 1, banc);
            helper.assertTrue(r == OrderResult.OK, "résultat attendu OK, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 3, "3 fers attendus, vu "
                    + compter(j.joueur(), Items.IRON_INGOT));
            helper.assertTrue(compter(j.joueur(), Items.COPPER_INGOT) == 0, "le cuivre devait être payé");
            helper.assertTrue(atelier.queue().size() == 1, "une entrée attendue, vu " + atelier.queue().size());
            WorkshopEntry e = atelier.queue().get(0);
            helper.assertTrue(e.recipeId().equals(R_FER) && e.result().is(Items.GOLD_INGOT) && e.result().getCount() == 3,
                    "identité ou résultat de l'entrée altérés");
            helper.assertTrue(e.totalTicks() == 20, "une seconde, soit 20 ticks, attendue, vu " + e.totalTicks());
            helper.assertTrue(e.benchPos().equals(banc) && e.benchLevel().equals(helper.getLevel().dimension()),
                    "établi de l'entrée altéré");
            int fer = 0;
            int cuivre = 0;
            for (ItemStack p : e.paid()) {
                fer += p.is(Items.IRON_INGOT) ? p.getCount() : 0;
                cuivre += p.is(Items.COPPER_INGOT) ? p.getCount() : 0;
            }
            helper.assertTrue(fer == 2 && cuivre == 1, "payé attendu 2 fers et 1 cuivre, vu " + fer + "/" + cuivre);
            helper.assertTrue(e.remainders().isEmpty(), "aucun reste attendu");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_manque")
    public static void ingredientsManquantsNeRienPrelever(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurManque", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            // Pas de cuivre : le fer, lui, est présent et ne doit pas être touché.
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.MISSING_INGREDIENTS, "MISSING_INGREDIENTS attendu, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 5, "le fer a été prélevé");
            helper.assertTrue(atelier.queue().isEmpty(), "la file devait rester vide");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_quantite")
    public static void quantiteInvalide(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurQuantite", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 64));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 64));
            for (int n : new int[] {0, -1, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                OrderResult r = BenchOrders.order(j.joueur(), R_FER, n, helper.absolutePos(POS));
                helper.assertTrue(r == OrderResult.BAD_COUNT, "BAD_COUNT attendu pour " + n + ", vu " + r);
            }
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 64 && atelier.queue().isEmpty(),
                    "rien ne devait bouger");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_inconnue")
    public static void uneRecetteInconnueEstRefusee(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurInconnue", true);
            atelier = atelier(helper, j.uuid());
            OrderResult r = BenchOrders.order(j.joueur(), new ResourceLocation("oneforallsurvival", "n_existe_pas"), 1,
                    helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.UNKNOWN_RECIPE, "UNKNOWN_RECIPE attendu, vu " + r);
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_trois")
    public static void troisUnitesPartiellementPayables(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurTrois", true);
            atelier = atelier(helper, j.uuid());
            // Deux unités se paient (4 fers, 2 cuivres), pas trois (6 fers).
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 3));
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 3, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.MISSING_INGREDIENTS, "MISSING_INGREDIENTS attendu, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 5 && compter(j.joueur(), Items.COPPER_INGOT) == 3,
                    "rien ne devait être prélevé : fer " + compter(j.joueur(), Items.IRON_INGOT) + ", cuivre "
                            + compter(j.joueur(), Items.COPPER_INGOT));
            helper.assertTrue(atelier.queue().isEmpty(), "la file devait rester vide");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_piles")
    public static void plusieursUnitesPayeesSurPlusieursPiles(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurPiles", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 4));
            garnir(j.joueur(), 1, new ItemStack(Items.IRON_INGOT, 2));
            garnir(j.joueur(), 2, new ItemStack(Items.COPPER_INGOT, 3));
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 3, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.OK, "OK attendu, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 0 && compter(j.joueur(), Items.COPPER_INGOT) == 0,
                    "tout devait être payé");
            helper.assertTrue(atelier.queue().size() == 3, "trois entrées attendues, vu " + atelier.queue().size());
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_tropLoin")
    public static void tropLoin(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurLoin", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            BlockPos loin = j.joueur().blockPosition().offset(20, 0, 0);
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 1, loin);
            helper.assertTrue(r == OrderResult.TOO_FAR, "TOO_FAR attendu, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 5 && atelier.queue().isEmpty(),
                    "rien ne devait bouger");
            // Et tout près, la même commande passe : le refus venait bien de la distance.
            r = BenchOrders.order(j.joueur(), R_FER, 1, j.joueur().blockPosition().offset(3, 0, 0));
            helper.assertTrue(r == OrderResult.OK, "OK attendu à 3 blocs, vu " + r);
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_fileMax")
    public static void laFilePleineRefuseSansRienPrelever(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurFileMax", true);
            atelier = atelier(helper, j.uuid());
            int max = BenchConfig.maxQueue();
            helper.assertTrue(max == 64, "ce test suppose maxQueue = 64, vu " + max);
            // 64 unités : 128 fers et 64 cuivres, puis une 65e doit être refusée.
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 64));
            garnir(j.joueur(), 1, new ItemStack(Items.IRON_INGOT, 64));
            garnir(j.joueur(), 2, new ItemStack(Items.IRON_INGOT, 64));
            garnir(j.joueur(), 3, new ItemStack(Items.COPPER_INGOT, 64));
            garnir(j.joueur(), 4, new ItemStack(Items.COPPER_INGOT, 64));
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 64, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.OK, "64 unités : OK attendu, vu " + r);
            helper.assertTrue(atelier.queue().size() == 64, "64 entrées attendues, vu " + atelier.queue().size());
            r = BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS));
            helper.assertTrue(r == OrderResult.QUEUE_FULL, "QUEUE_FULL attendu, vu " + r);
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 64 && compter(j.joueur(), Items.COPPER_INGOT) == 64,
                    "la commande refusée a prélevé : fer " + compter(j.joueur(), Items.IRON_INGOT));
            helper.assertTrue(atelier.queue().size() == 64, "la file devait rester à 64");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_exact")
    public static void lesPilesPayeesEtLesRestesSontExacts(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurExact", true);
            atelier = atelier(helper, j.uuid());
            ItemStack usee = new ItemStack(Items.IRON_PICKAXE);
            usee.setDamageValue(37);
            usee.setHoverName(net.minecraft.network.chat.Component.literal("Vieille pioche"));
            garnir(j.joueur(), 0, usee.copy());
            garnir(j.joueur(), 1, new ItemStack(Items.STICK, 2));
            garnir(j.joueur(), 2, new ItemStack(Items.MILK_BUCKET));
            garnir(j.joueur(), 3, new ItemStack(Items.SUGAR, 2));
            BlockPos banc = helper.absolutePos(POS);
            helper.assertTrue(BenchOrders.order(j.joueur(), R_PIOCHE, 1, banc) == OrderResult.OK, "pioche : OK attendu");
            helper.assertTrue(BenchOrders.order(j.joueur(), R_LAIT, 1, banc) == OrderResult.OK, "lait : OK attendu");
            WorkshopEntry piochee = atelier.queue().get(0);
            ItemStack payee = piochee.paid().get(0);
            helper.assertTrue(ItemStack.matches(payee, usee) && payee.getDamageValue() == 37,
                    "la pioche payée n'est pas celle de l'inventaire (usure, nom)");
            WorkshopEntry lait = atelier.queue().get(1);
            helper.assertTrue(lait.remainders().size() == 1 && lait.remainders().get(0).is(Items.BUCKET),
                    "le seau vide devait être le reste de la recette de lait");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    // --- Annulation --------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_annul_plein")
    public static void annulationInventairePlein(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurPlein", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            // L'inventaire se remplit entièrement de piles pleines : rien ne peut plus y entrer.
            for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                garnir(j.joueur(), i, new ItemStack(Items.DIRT, 64));
            }
            helper.assertTrue(!BenchOrders.cancel(j.joueur(), UUID.randomUUID()), "un identifiant inconnu devait échouer");
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'annulation devait réussir");
            helper.assertTrue(atelier.queue().isEmpty(), "la file devait être vide");
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 0, "le fer ne pouvait pas entrer dans l'inventaire");
            helper.assertTrue(compterStock(atelier, Items.IRON_INGOT) == 2 && compterStock(atelier, Items.COPPER_INGOT) == 1,
                    "tout le surplus devait être au stock : fer " + compterStock(atelier, Items.IRON_INGOT)
                            + ", cuivre " + compterStock(atelier, Items.COPPER_INGOT));
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_annul_partiel")
    public static void annulationAvecUneSeuleCaseLibre(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurPartiel", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                garnir(j.joueur(), i, new ItemStack(Items.DIRT, 64));
            }
            garnir(j.joueur(), 5, ItemStack.EMPTY);
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'annulation devait réussir");
            // Les deux fers ont rempli la case libre, le cuivre n'avait plus de place : stock.
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 2, "2 fers attendus dans l'inventaire, vu "
                    + compter(j.joueur(), Items.IRON_INGOT));
            helper.assertTrue(compter(j.joueur(), Items.COPPER_INGOT) == 0 && compterStock(atelier, Items.COPPER_INGOT) == 1,
                    "le cuivre devait aller au stock");
            helper.assertTrue(compterStock(atelier, Items.IRON_INGOT) == 0, "le fer ne devait pas aller au stock");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_annul_citoyen")
    public static void annulerExigeLeRoleEtRendLesPilesExactes(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurRetire", true);
            atelier = atelier(helper, j.uuid());
            ItemStack usee = new ItemStack(Items.IRON_PICKAXE);
            usee.setDamageValue(37);
            garnir(j.joueur(), 0, usee.copy());
            garnir(j.joueur(), 1, new ItemStack(Items.STICK));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_PIOCHE, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            // Le rôle est retiré : les crafts continuent, mais plus d'annulation.
            RoleSavedData.get(serveur(helper)).book().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            helper.assertTrue(!BenchOrders.cancel(j.joueur(), id), "un citoyen ne devait pas pouvoir annuler");
            helper.assertTrue(atelier.queue().size() == 1, "l'entrée devait rester en file");
            RoleSavedData.get(serveur(helper)).book().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'ingénieur devait pouvoir annuler");
            int idx = -1;
            for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                if (j.joueur().getInventory().items.get(i).is(Items.IRON_PICKAXE)) {
                    idx = i;
                }
            }
            helper.assertTrue(idx >= 0 && ItemStack.matches(j.joueur().getInventory().items.get(idx), usee)
                    && j.joueur().getInventory().items.get(idx).getDamageValue() == 37,
                    "la pioche rendue devait être celle d'origine, usure comprise");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    // --- File et déconnexion -----------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_deconnexion", timeoutTicks = 200)
    public static void fileEtDeconnexion(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = joueur(helper, "IngenieurDeconnecte", true);
        UUID uuid = j.uuid();
        PlayerWorkshop atelier = atelier(helper, uuid);
        try {
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 2));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            j.quitter();
        } catch (RuntimeException e) {
            quitter(j, atelier);
            BenchCatalog.install(avant);
            throw e;
        }
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(atelier.queue().size() == 1, "la file devait avancer sans le joueur");
            } catch (RuntimeException e) {
                viderFile(atelier);
                BenchCatalog.install(avant);
                throw e;
            }
        });
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(atelier.queue().isEmpty(), "la fabrication devait être finie");
                helper.assertTrue(compterStock(atelier, Items.GOLD_INGOT) == 3,
                        "le stock devait contenir 3 lingots d'or, vu " + compterStock(atelier, Items.GOLD_INGOT));
                helper.succeed();
            } finally {
                viderFile(atelier);
                BenchCatalog.install(avant);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_annul_deco")
    public static void unJoueurDeconnecteNePeutPasAnnuler(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = joueur(helper, "IngenieurPartiAnnuler", true);
        PlayerWorkshop atelier = atelier(helper, j.uuid());
        try {
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 2));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_LONGUE, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            ServerPlayer p = j.joueur();
            j.quitter();
            helper.assertTrue(!BenchOrders.cancel(p, id), "un joueur déconnecté ne devait pas pouvoir annuler");
            helper.assertTrue(atelier.queue().size() == 1, "l'entrée devait rester en file");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_cmd_reload", timeoutTicks = 200)
    public static void recetteDisparueAuReload(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = joueur(helper, "IngenieurReload", true);
        PlayerWorkshop atelier = atelier(helper, j.uuid());
        UUID aAnnuler;
        try {
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 4));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 2));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 2, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            aAnnuler = atelier.queue().get(1).id();
            // Le rechargement retire la recette du catalogue.
            BenchCatalog.install(BenchCatalog.of(List.of()));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.UNKNOWN_RECIPE,
                    "la recette retirée devait être inconnue");
        } catch (RuntimeException e) {
            quitter(j, atelier);
            BenchCatalog.install(avant);
            throw e;
        }
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(atelier.queue().size() == 1, "la première entrée devait être finie, file : "
                        + atelier.queue().size());
                helper.assertTrue(compterStock(atelier, Items.GOLD_INGOT) == 3, "3 lingots d'or attendus au stock");
                helper.assertTrue(BenchOrders.cancel(j.joueur(), aAnnuler), "l'entrée restante devait rester annulable");
                helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 2 && compter(j.joueur(), Items.COPPER_INGOT) == 1,
                        "les piles payées devaient revenir");
                helper.succeed();
            } finally {
                quitter(j, atelier);
                BenchCatalog.install(avant);
            }
        });
    }

    // --- Remboursements particuliers, menu, rechargement --------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_annul_mort")
    public static void unJoueurMortRecoitSesPilesAuStock(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurMort", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 2));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_LONGUE, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            j.joueur().setHealth(0.0f);
            helper.assertTrue(!j.joueur().isAlive(), "le joueur devait être mort");
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'annulation devait réussir");
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 0 && compter(j.joueur(), Items.COPPER_INGOT) == 0,
                    "rien ne doit être rendu à l'inventaire d'un mort");
            helper.assertTrue(compterStock(atelier, Items.IRON_INGOT) == 2 && compterStock(atelier, Items.COPPER_INGOT) == 1,
                    "les piles payées devaient aller au stock : fer " + compterStock(atelier, Items.IRON_INGOT)
                            + ", cuivre " + compterStock(atelier, Items.COPPER_INGOT));
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_annul_creatif_plein")
    public static void unCreatifAvecUnInventairePleinNePerdRien(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurCreatifPlein", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            j.joueur().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            helper.assertTrue(j.joueur().getAbilities().instabuild, "le joueur devait être en créatif");
            for (int i = 0; i < j.joueur().getInventory().items.size(); i++) {
                garnir(j.joueur(), i, new ItemStack(Items.DIRT, 64));
            }
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'annulation devait réussir");
            // Inventory.add avale la pile d'un joueur créatif quand rien n'entre : elle doit aller au stock.
            helper.assertTrue(compterStock(atelier, Items.IRON_INGOT) == 2 && compterStock(atelier, Items.COPPER_INGOT) == 1,
                    "tout le surplus devait être au stock : fer " + compterStock(atelier, Items.IRON_INGOT)
                            + ", cuivre " + compterStock(atelier, Items.COPPER_INGOT));
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_annul_creatif_place")
    public static void unCreatifAvecDeLaPlaceRecupereSesPiles(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            j = joueur(helper, "IngenieurCreatifPlace", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            helper.assertTrue(BenchOrders.order(j.joueur(), R_FER, 1, helper.absolutePos(POS)) == OrderResult.OK,
                    "la commande devait passer");
            UUID id = atelier.queue().get(0).id();
            j.joueur().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            helper.assertTrue(BenchOrders.cancel(j.joueur(), id), "l'annulation devait réussir");
            helper.assertTrue(compter(j.joueur(), Items.IRON_INGOT) == 5 && compter(j.joueur(), Items.COPPER_INGOT) == 1,
                    "avec de la place, le rendu doit rester dans l'inventaire");
            helper.assertTrue(atelier.stock().isEmpty(), "le stock devait rester vide : " + atelier.stock());
            helper.assertTrue(j.joueur().getAbilities().instabuild, "le mode créatif devait être rétabli");
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_menu_loin")
    public static void unMenuLointainNeChargePasDeChunk(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "IngenieurLoin", true);
            BlockPos loin = new BlockPos(1_000_000, 64, 1_000_000);
            net.minecraft.server.level.ServerLevel monde = helper.getLevel();
            helper.assertTrue(!monde.hasChunk(loin.getX() >> 4, loin.getZ() >> 4), "le chunk lointain est déjà chargé");
            EngineerBenchMenu menu = new EngineerBenchMenu(99, j.joueur().getInventory(), loin);
            helper.assertTrue(!menu.stillValid(j.joueur()), "un établi à un million de blocs ne peut pas être valide");
            helper.assertTrue(!monde.hasChunk(loin.getX() >> 4, loin.getZ() >> 4),
                    "la vérification de portée a chargé un chunk lointain");
            helper.succeed();
        } finally {
            quitter(j, null);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_connexion")
    public static void lEntreeEnJeuEnvoieLeCatalogue(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "IngenieurConnexion", true);
            var fragments = j.listesALaConnexion();
            helper.assertTrue(!fragments.isEmpty(), "la vraie connexion devait envoyer le catalogue");
            for (var f : fragments) {
                helper.assertTrue(f.generation() == BenchCatalog.generation(), "génération du paquet inattendue");
            }
            helper.succeed();
        } finally {
            quitter(j, null);
            BenchCatalog.install(avant);
        }
    }

    /** Un vrai /reload : le joueur connecté reçoit de nouveau le catalogue, de la génération qui vient d'être installée. */
    @GameTest(template = "test/plancher", batch = "etabli_reload_envoi", timeoutTicks = 400)
    public static void apresUnReloadLeJoueurConnecteRecoitLeNouveauCatalogue(GameTestHelper helper) {
        MinecraftServer server = serveur(helper);
        JoueurDeTest j = joueur(helper, "IngenieurRechargeEnvoi", true);
        dev.oneforallsurvival.bench.BenchNetwork.sendCatalogIfNeeded(j.joueur());
        j.listesRecues();
        int generationAvant = BenchCatalog.generation();
        List<dev.oneforallsurvival.bench.RecipeListPacket> recus = new ArrayList<>();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        helper.runAfterDelay(380, () -> quitter(j, null));
        helper.succeedWhen(() -> {
            recus.addAll(j.listesRecues());
            helper.assertTrue(BenchCatalog.generation() > generationAvant, "le catalogue n'a pas été reconstruit");
            helper.assertTrue(!recus.isEmpty(), "aucun catalogue reçu après le reload");
            int taille = 0;
            for (var f : recus) {
                helper.assertTrue(f.generation() == BenchCatalog.generation(),
                        "génération reçue " + f.generation() + ", courante " + BenchCatalog.generation());
                taille += f.recipes().size();
            }
            helper.assertTrue(taille == BenchCatalog.current().recipes().size(),
                    "le catalogue reçu est incomplet : " + taille + " sur " + BenchCatalog.current().recipes().size());
            quitter(j, null);
        });
    }

    // --- Bloc, énergie, vitesse --------------------------------------------------------------

    private static EngineerBenchBlockEntity poserBanc(GameTestHelper helper) {
        helper.setBlock(POS, BenchRegistry.ENGINEER_BENCH.get());
        EngineerBenchBlockEntity be = (EngineerBenchBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de l'établi");
        return be;
    }

    private static WorkshopEntry entreeLongue(GameTestHelper helper, BlockPos banc, boolean alimente) {
        return new WorkshopEntry(UUID.randomUUID(), R_LONGUE, new ItemStack(Items.EMERALD), List.of(), List.of(),
                12000, 0.0, helper.getLevel().dimension(), banc, alimente);
    }

    @GameTest(template = "test/plancher", batch = "etabli_energie")
    public static void lEtabliConsommeSeulementQuandIlTravaille(GameTestHelper helper) {
        EngineerBenchBlockEntity be = poserBanc(helper);
        for (int i = 0; i < 10; i++) {
            be.energy().receiveEnergy(100, false);
        }
        helper.assertTrue(be.energy().getEnergyStored() == 1000, "1000 FE attendus, vu " + be.energy().getEnergyStored());
        PlayerWorkshop atelier = atelier(helper, UUID.randomUUID());
        WorkshopEntry entree = entreeLongue(helper, helper.absolutePos(POS), false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "sans file : STOPPED attendu, vu " + be.state());
            helper.assertTrue(be.energy().getEnergyStored() == 1000, "sans file, rien ne devait être consommé");
            atelier.add(entree);
        });
        int[] avant = new int[1];
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING,
                    "avec courant et file : RUNNING attendu, vu " + be.state());
            avant[0] = be.energy().getEnergyStored();
        });
        helper.runAfterDelay(13, () -> helper.assertTrue(avant[0] - be.energy().getEnergyStored() == 20 * 5,
                "consommation attendue 20 FE par tick, soit 100 en 5 ticks, vue "
                        + (avant[0] - be.energy().getEnergyStored())));
        helper.runAfterDelay(15, () -> {
            atelier.cancel(entree.id());
        });
        helper.runAfterDelay(20, () -> {
            int stocke = be.energy().getEnergyStored();
            helper.assertTrue(be.state() == MachineState.STOPPED, "file vidée : STOPPED attendu, vu " + be.state());
            helper.assertTrue(stocke > 0 && stocke < 1000, "réserve inattendue : " + stocke);
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "toujours STOPPED attendu");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_seuleLaTete")
    public static void seuleLaTeteFaitTravaillerLEtabli(GameTestHelper helper) {
        WorkshopSavedData donnees = new WorkshopSavedData();
        PlayerWorkshop atelier = donnees.of(UUID.randomUUID());
        BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = helper.absolutePos(new BlockPos(3, 1, 1));
        WorkshopEntry ea = entreeLongue(helper, a, false);
        WorkshopEntry eb = entreeLongue(helper, b, false);
        atelier.add(ea);
        atelier.add(eb);
        helper.assertTrue(donnees.hasHeadAt(helper.getLevel().dimension(), a), "A porte la tête de file");
        helper.assertTrue(!donnees.hasHeadAt(helper.getLevel().dimension(), b), "B n'est que deuxième : pas de travail");
        atelier.cancel(ea.id());
        helper.assertTrue(!donnees.hasHeadAt(helper.getLevel().dimension(), a), "A n'a plus de tête");
        helper.assertTrue(donnees.hasHeadAt(helper.getLevel().dimension(), b), "B devient la tête");
        // Rechargé, le décompte se reconstruit.
        WorkshopSavedData relu = WorkshopSavedData.load(donnees.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(relu.hasHeadAt(helper.getLevel().dimension(), b) && !relu.hasHeadAt(helper.getLevel().dimension(), a),
                "le décompte devait être reconstruit au chargement");
        // La fin d'une fabrication passe la tête à la suivante.
        PlayerWorkshop courte = donnees.of(UUID.randomUUID());
        BlockPos c = helper.absolutePos(new BlockPos(5, 1, 1));
        courte.add(new WorkshopEntry(UUID.randomUUID(), R_FER, new ItemStack(Items.STICK), List.of(), List.of(), 2, 0.0,
                helper.getLevel().dimension(), c, false));
        helper.assertTrue(donnees.hasHeadAt(helper.getLevel().dimension(), c), "C porte une tête");
        courte.advance(5);
        helper.assertTrue(!donnees.hasHeadAt(helper.getLevel().dimension(), c), "C a fini");
        helper.succeed();
    }

    /** Avance de la tête sur 10 ticks, entre les ticks {@code debut} et {@code debut + 10}. */
    private static void mesurer(GameTestHelper helper, WorkshopEntry entree, int debut, double attendu,
            Runnable apres) {
        double[] depart = new double[1];
        helper.runAfterDelay(debut, () -> depart[0] = entree.doneTicks());
        helper.runAfterDelay(debut + 10, () -> {
            double delta = entree.doneTicks() - depart[0];
            helper.assertTrue(delta == attendu, "avance sur 10 ticks attendue " + attendu + ", vue " + delta);
            apres.run();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_vitesse_courant")
    public static void laVitesseAlimentee(GameTestHelper helper) {
        EngineerBenchBlockEntity be = poserBanc(helper);
        for (int i = 0; i < 10; i++) {
            be.energy().receiveEnergy(100, false);
        }
        PlayerWorkshop atelier = atelier(helper, UUID.randomUUID());
        WorkshopEntry entree = entreeLongue(helper, helper.absolutePos(POS), false);
        atelier.add(entree);
        mesurer(helper, entree, 5, 4.0 * 10, () -> {
            helper.assertTrue(entree.lastKnownPowered(), "le dernier état connu devait être alimenté");
            atelier.cancel(entree.id());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_vitesse_main")
    public static void laVitesseSansCourantEstCelleDeLaMain(GameTestHelper helper) {
        EngineerBenchBlockEntity be = poserBanc(helper);
        PlayerWorkshop atelier = atelier(helper, UUID.randomUUID());
        // Dernier état « alimenté » à tort : le bloc, chargé et sans courant, doit le corriger.
        WorkshopEntry entree = entreeLongue(helper, helper.absolutePos(POS), true);
        atelier.add(entree);
        mesurer(helper, entree, 5, 10.0, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(!entree.lastKnownPowered(), "le dernier état connu devait passer à non alimenté");
            atelier.cancel(entree.id());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_vitesse_casse")
    public static void blocCasseVitesseALaMainFileIntacte(GameTestHelper helper) {
        EngineerBenchBlockEntity be = poserBanc(helper);
        for (int i = 0; i < 10; i++) {
            be.energy().receiveEnergy(100, false);
        }
        PlayerWorkshop atelier = atelier(helper, UUID.randomUUID());
        WorkshopEntry entree = entreeLongue(helper, helper.absolutePos(POS), true);
        atelier.add(entree);
        helper.runAfterDelay(3, () -> helper.setBlock(POS, Blocks.AIR));
        mesurer(helper, entree, 6, 10.0, () -> {
            helper.assertTrue(atelier.queue().size() == 1 && atelier.queue().get(0) == entree, "la file devait être intacte");
            atelier.cancel(entree.id());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_vitesse_decharge")
    public static void etatInconnuApresDechargementDernierEtatSansCharger(GameTestHelper helper) {
        BlockPos loin = new BlockPos(20000, 80, 20000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(loin), "le chunk d'essai devait être déchargé");
        PlayerWorkshop alimente = atelier(helper, UUID.randomUUID());
        PlayerWorkshop manuel = atelier(helper, UUID.randomUUID());
        WorkshopEntry ea = entreeLongue(helper, loin, true);
        WorkshopEntry em = entreeLongue(helper, loin.offset(1, 0, 0), false);
        alimente.add(ea);
        manuel.add(em);
        double[] depart = new double[2];
        helper.runAfterDelay(3, () -> {
            depart[0] = ea.doneTicks();
            depart[1] = em.doneTicks();
        });
        helper.runAfterDelay(13, () -> {
            try {
                helper.assertTrue(ea.doneTicks() - depart[0] == 40.0, "alimenté : 40 ticks attendus, vu "
                        + (ea.doneTicks() - depart[0]));
                helper.assertTrue(em.doneTicks() - depart[1] == 10.0, "à la main : 10 ticks attendus, vu "
                        + (em.doneTicks() - depart[1]));
                helper.assertTrue(!helper.getLevel().hasChunkAt(loin), "le ticker a chargé le chunk");
                helper.assertTrue(ea.lastKnownPowered() && !em.lastKnownPowered(), "le dernier état ne devait pas changer");
                helper.succeed();
            } finally {
                alimente.cancel(ea.id());
                manuel.cancel(em.id());
            }
        });
    }

    // --- Menu et ouverture -------------------------------------------------------------------

    private static BlockHitResult toucher(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
    }

    @GameTest(template = "test/plancher", batch = "etabli_ouverture")
    public static void ouvrirExigeLeRoleEtEnvoieLeCatalogue(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest citoyen = null;
        JoueurDeTest ingenieur = null;
        try {
            poserBanc(helper);
            BlockPos banc = helper.absolutePos(POS);
            BlockState etat = helper.getLevel().getBlockState(banc);
            citoyen = joueur(helper, "CitoyenOuvre", false);
            // Le catalogue part à la connexion, à tout joueur (JEI en a besoin sans établi ouvert).
            helper.assertTrue(!connecter(citoyen).isEmpty(), "le catalogue devait partir à la connexion");
            etat.use(helper.getLevel(), citoyen.joueur(), InteractionHand.MAIN_HAND, toucher(banc));
            helper.assertTrue(citoyen.joueur().containerMenu == citoyen.joueur().inventoryMenu,
                    "un citoyen ne devait pas ouvrir l'établi");
            helper.assertTrue(citoyen.listesRecues().isEmpty() && citoyen.filesRecues().isEmpty(),
                    "rien de plus ne devait être envoyé à un citoyen refusé");

            ingenieur = joueur(helper, "IngenieurOuvre", true);
            helper.assertTrue(!connecter(ingenieur).isEmpty(), "le catalogue devait partir à la connexion");
            etat.use(helper.getLevel(), ingenieur.joueur(), InteractionHand.MAIN_HAND, toucher(banc));
            helper.assertTrue(ingenieur.joueur().containerMenu instanceof EngineerBenchMenu m && m.pos().equals(banc),
                    "le menu de l'établi devait s'ouvrir");
            helper.assertTrue(ingenieur.listesRecues().isEmpty(), "le catalogue ne devait pas être renvoyé à l'ouverture");
            helper.assertTrue(!ingenieur.filesRecues().isEmpty(), "la file devait être envoyée à l'ouverture");
            // Deuxième ouverture : même génération, pas de nouveau catalogue.
            ingenieur.joueur().closeContainer();
            etat.use(helper.getLevel(), ingenieur.joueur(), InteractionHand.MAIN_HAND, toucher(banc));
            helper.assertTrue(ingenieur.listesRecues().isEmpty(), "le catalogue ne devait pas être renvoyé");
            helper.succeed();
        } finally {
            quitter(citoyen, null);
            quitter(ingenieur, atelier(helper, ingenieur == null ? UUID.randomUUID() : ingenieur.uuid()));
            BenchCatalog.install(avant);
        }
    }

    /**
     * Rejoue la synchronisation des données du joueur, comme Forge la publie à sa connexion : le gestionnaire du
     * mod envoie le catalogue. On oublie ce que le joueur a reçu et ce que le serveur sait lui avoir envoyé, on
     * publie l'événement, et on rend les fragments reçus.
     */
    private static java.util.List<dev.oneforallsurvival.bench.RecipeListPacket> connecter(JoueurDeTest j) {
        j.listesRecues();
        dev.oneforallsurvival.bench.BenchNetwork.onLogout(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(j.joueur()));
        dev.oneforallsurvival.bench.BenchNetwork.onDatapackSync(new net.minecraftforge.event.OnDatapackSyncEvent(
                j.joueur().server.getPlayerList(), j.joueur()));
        return j.listesRecues();
    }

    /**
     * Le client vanille n'applique un paquet de case d'identifiant 0 qu'aux cases de la barre d'action tant qu'un
     * autre menu est ouvert ; le menu de l'établi n'a aucune case. Une commande payée avec des objets rangés hors de
     * la barre d'action doit donc produire un paquet que le client applique (identifiant -2, ou celui du menu),
     * sinon son inventaire affiché garde l'ancien contenu. Le client est modélisé par les règles de
     * {@code ClientPacketListener.handleContainerSetSlot}.
     */
    @GameTest(template = "test/plancher", batch = "etabli_sync_inventaire")
    public static void unAchatPendantLEcranMetLInventaireDuClientAJour(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        PlayerWorkshop atelier = null;
        try {
            poserBanc(helper);
            BlockPos banc = helper.absolutePos(POS);
            j = joueur(helper, "IngenieurSync", true);
            atelier = atelier(helper, j.uuid());
            garnir(j.joueur(), 20, new ItemStack(Items.IRON_INGOT, 5));
            garnir(j.joueur(), 21, new ItemStack(Items.COPPER_INGOT, 1));
            helper.getLevel().getBlockState(banc).use(helper.getLevel(), j.joueur(), InteractionHand.MAIN_HAND, toucher(banc));
            helper.assertTrue(j.joueur().containerMenu instanceof EngineerBenchMenu, "le menu devait s'ouvrir");
            j.joueur().containerMenu.broadcastChanges();
            ItemStack[] client = new ItemStack[36];
            java.util.function.Consumer<java.util.List<net.minecraft.network.protocol.Packet<?>>> appliquer = paquets -> {
                for (var p : paquets) {
                    if (p instanceof net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket s) {
                        if (s.getContainerId() == -2) {
                            client[s.getSlot()] = s.getItem().copy();
                        } else if (s.getContainerId() == 0 && s.getSlot() >= 36 && s.getSlot() <= 44) {
                            client[s.getSlot() - 36] = s.getItem().copy();
                        }
                        // Autre identifiant : la case appartiendrait au menu ouvert, qui n'en a pas.
                    }
                }
            };
            // L'état du client à l'ouverture : ce que le serveur lui a envoyé jusque-là.
            for (int i = 0; i < 36; i++) {
                client[i] = j.joueur().getInventory().items.get(i).copy();
            }
            j.menusRecus();
            OrderResult r = BenchOrders.order(j.joueur(), R_FER, 1, banc);
            helper.assertTrue(r == OrderResult.OK, "résultat attendu OK, vu " + r);
            j.joueur().containerMenu.broadcastChanges();
            appliquer.accept(j.menusRecus());
            for (int i = 0; i < 36; i++) {
                ItemStack serveur = j.joueur().getInventory().items.get(i);
                helper.assertTrue(ItemStack.matches(serveur, client[i]), "case " + i + " : le serveur a " + serveur
                        + ", le client verrait " + client[i]);
            }
            helper.succeed();
        } finally {
            quitter(j, atelier);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_menu_valide")
    public static void leMenuSeFermeSansRoleSansBlocOuTropLoin(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            poserBanc(helper);
            BlockPos banc = helper.absolutePos(POS);
            j = joueur(helper, "IngenieurMenu", true);
            helper.getLevel().getBlockState(banc).use(helper.getLevel(), j.joueur(), InteractionHand.MAIN_HAND, toucher(banc));
            EngineerBenchMenu menu = (EngineerBenchMenu) j.joueur().containerMenu;
            helper.assertTrue(menu.stillValid(j.joueur()), "le menu devait être valide");
            RoleSavedData.get(serveur(helper)).book().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            helper.assertTrue(!menu.stillValid(j.joueur()), "sans le rôle, le menu devait se fermer");
            RoleSavedData.get(serveur(helper)).book().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            helper.assertTrue(menu.stillValid(j.joueur()), "le rôle rendu, le menu redevient valide");
            j.joueur().moveTo(j.joueur().getX() + 30, j.joueur().getY(), j.joueur().getZ());
            helper.assertTrue(!menu.stillValid(j.joueur()), "trop loin, le menu devait se fermer");
            j.joueur().moveTo(j.joueur().getX() - 30, j.joueur().getY(), j.joueur().getZ());
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(!menu.stillValid(j.joueur()), "sans le bloc, le menu devait se fermer");
            helper.succeed();
        } finally {
            quitter(j, null);
        }
    }

    // --- Paquets -----------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "etabli_paquets", timeoutTicks = 100)
    public static void lesPaquetsDuClientSontRevalides(GameTestHelper helper) {
        BenchCatalog avant = installer();
        poserBanc(helper);
        BlockPos banc = helper.absolutePos(POS);
        JoueurDeTest ouvert = joueur(helper, "IngenieurPaquetOuvert", true);
        JoueurDeTest ferme = joueur(helper, "IngenieurPaquetFerme", true);
        PlayerWorkshop atelierOuvert = atelier(helper, ouvert.uuid());
        PlayerWorkshop atelierFerme = atelier(helper, ferme.uuid());
        UUID[] entree = new UUID[1];
        try {
            for (JoueurDeTest j : List.of(ouvert, ferme)) {
                garnir(j.joueur(), 0, new ItemStack(Items.IRON_INGOT, 5));
                garnir(j.joueur(), 1, new ItemStack(Items.COPPER_INGOT, 1));
            }
            helper.getLevel().getBlockState(banc).use(helper.getLevel(), ouvert.joueur(), InteractionHand.MAIN_HAND,
                    toucher(banc));
            ouvert.listesRecues();
            ouvert.filesRecues();
            ouvert.envoyer(new dev.oneforallsurvival.bench.OrderPacket(R_FER, 1, banc));
            ferme.envoyer(new dev.oneforallsurvival.bench.OrderPacket(R_FER, 1, banc));
        } catch (RuntimeException e) {
            quitter(ouvert, atelierOuvert);
            quitter(ferme, atelierFerme);
            BenchCatalog.install(avant);
            throw e;
        }
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(atelierOuvert.queue().size() == 1, "le joueur au menu ouvert devait avoir sa commande");
                helper.assertTrue(compter(ouvert.joueur(), Items.IRON_INGOT) == 3, "le joueur au menu ouvert devait payer");
                helper.assertTrue(atelierFerme.queue().isEmpty() && compter(ferme.joueur(), Items.IRON_INGOT) == 5,
                        "sans menu ouvert, la commande devait être ignorée");
                helper.assertTrue(!ouvert.filesRecues().isEmpty(), "la file devait être renvoyée après la commande");
                entree[0] = atelierOuvert.queue().get(0).id();
                ferme.envoyer(new dev.oneforallsurvival.bench.CancelPacket(entree[0]));
                ouvert.envoyer(new dev.oneforallsurvival.bench.OrderPacket(R_FER, 99, banc));
            } catch (RuntimeException e) {
                quitter(ouvert, atelierOuvert);
                quitter(ferme, atelierFerme);
                BenchCatalog.install(avant);
                throw e;
            }
        });
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(atelierOuvert.queue().size() == 1, "l'annulation d'un autre joueur ne devait rien faire"
                        + " et la quantité invalide ne devait rien ajouter");
                ouvert.envoyer(new dev.oneforallsurvival.bench.CancelPacket(entree[0]));
            } catch (RuntimeException e) {
                quitter(ouvert, atelierOuvert);
                quitter(ferme, atelierFerme);
                BenchCatalog.install(avant);
                throw e;
            }
        });
        helper.runAfterDelay(12, () -> {
            try {
                helper.assertTrue(atelierOuvert.queue().isEmpty(), "l'annulation par le propriétaire devait passer");
                helper.assertTrue(compter(ouvert.joueur(), Items.IRON_INGOT) == 5, "les fers devaient revenir");
                helper.succeed();
            } finally {
                quitter(ouvert, atelierOuvert);
                quitter(ferme, atelierFerme);
                BenchCatalog.install(avant);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue_envoi")
    public static void leCatalogueEstEnvoyeUneFoisParGeneration(GameTestHelper helper) {
        BenchCatalog avant = installer();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "IngenieurCatalogue", true);
            dev.oneforallsurvival.bench.client.BenchClientState.reset();
            // Le catalogue part à la synchronisation des données (OnDatapackSyncEvent).
            var fragments = connecter(j);
            helper.assertTrue(!fragments.isEmpty(), "le catalogue devait être envoyé à la connexion");
            for (var f : fragments) {
                helper.assertTrue(f.generation() == BenchCatalog.generation(), "génération du paquet inattendue");
                dev.oneforallsurvival.bench.client.BenchClientState.acceptFragment(f);
            }
            var client = dev.oneforallsurvival.bench.client.BenchClientState.catalog();
            helper.assertTrue(client.size() == recettes().size(), "le client devait avoir " + recettes().size()
                    + " recettes, vu " + client.size());
            BenchRecipe serveurFer = BenchCatalog.current().byId(R_FER).orElseThrow();
            BenchRecipe clientFer = client.stream().filter(r -> r.id().equals(R_FER)).findFirst().orElseThrow();
            helper.assertTrue(clientFer != serveurFer, "le client doit avoir sa propre copie, pas celle du serveur");
            helper.assertTrue(clientFer.result().is(Items.GOLD_INGOT) && clientFer.result().getCount() == 3
                    && clientFer.seconds() == 1 && clientFer.ingredients().size() == 3
                    && clientFer.ingredients().get(2).test(new ItemStack(Items.COPPER_INGOT))
                    && !clientFer.ingredients().get(2).test(new ItemStack(Items.IRON_INGOT)),
                    "la copie du client est altérée");
            dev.oneforallsurvival.bench.BenchNetwork.sendCatalogIfNeeded(j.joueur());
            helper.assertTrue(j.listesRecues().isEmpty(), "le catalogue ne devait pas être renvoyé à la même génération");
            BenchCatalog.install(BenchCatalog.of(recettes().subList(0, 2)));
            dev.oneforallsurvival.bench.BenchNetwork.sendCatalogIfNeeded(j.joueur());
            fragments = j.listesRecues();
            helper.assertTrue(!fragments.isEmpty(), "une nouvelle génération devait renvoyer le catalogue");
            for (var f : fragments) {
                dev.oneforallsurvival.bench.client.BenchClientState.acceptFragment(f);
            }
            helper.assertTrue(dev.oneforallsurvival.bench.client.BenchClientState.catalog().size() == 2,
                    "le client devait avoir le nouveau catalogue de 2 recettes");
            helper.succeed();
        } finally {
            dev.oneforallsurvival.bench.client.BenchClientState.reset();
            quitter(j, null);
            BenchCatalog.install(avant);
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue_fragments")
    public static void unGrosCatalogueEstFractionneSansRienPerdre(GameTestHelper helper) {
        List<BenchRecipe> beaucoup = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            beaucoup.add(recette(new ResourceLocation("oneforallsurvival", "gros_" + i), new ItemStack(Items.STICK, 1 + i % 5),
                    i % 600, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.COPPER_INGOT),
                    Ingredient.of(Items.GOLD_INGOT), Ingredient.of(Items.DIAMOND), Ingredient.of(Items.EMERALD)));
        }
        int limite = 16 * 1024;
        var fragments = dev.oneforallsurvival.bench.RecipeListPacket.fragments(7, beaucoup, limite);
        helper.assertTrue(fragments.size() > 1, "2000 recettes sous une limite de 16 Kio devaient donner plusieurs fragments");
        dev.oneforallsurvival.bench.client.BenchClientState.reset();
        try {
            int total = 0;
            for (int i = 0; i < fragments.size(); i++) {
                var f = fragments.get(i);
                helper.assertTrue(f.part() == i && f.parts() == fragments.size() && f.generation() == 7,
                        "numérotation du fragment " + i + " inattendue");
                net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                f.encode(buf);
                helper.assertTrue(buf.readableBytes() <= limite + 64, "le fragment " + i + " pèse " + buf.readableBytes()
                        + " octets, au-delà de la limite");
                var relu = dev.oneforallsurvival.bench.RecipeListPacket.decode(buf);
                helper.assertTrue(relu.recipes().size() == f.recipes().size(), "le fragment relu a perdu des recettes");
                total += relu.recipes().size();
                if (i < fragments.size() - 1) {
                    dev.oneforallsurvival.bench.client.BenchClientState.acceptFragment(relu);
                    helper.assertTrue(dev.oneforallsurvival.bench.client.BenchClientState.catalog().isEmpty(),
                            "le catalogue ne doit paraître qu'une fois tous les fragments reçus");
                } else {
                    dev.oneforallsurvival.bench.client.BenchClientState.acceptFragment(relu);
                }
                buf.release();
            }
            helper.assertTrue(total == 2000, "2000 recettes attendues au total, vu " + total);
            var client = dev.oneforallsurvival.bench.client.BenchClientState.catalog();
            helper.assertTrue(client.size() == 2000 && client.get(1999).id().getPath().equals("gros_1999")
                    && client.get(0).id().getPath().equals("gros_0"), "ordre ou contenu altérés");
            helper.succeed();
        } finally {
            dev.oneforallsurvival.bench.client.BenchClientState.reset();
        }
    }

    @GameTest(template = "test/plancher", batch = "etabli_catalogue_pack")
    public static void avecLePack_tailleDuPaquetDuCatalogue(GameTestHelper helper) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            org.apache.logging.log4j.LogManager.getLogger("oneforallsurvival-bench")
                    .info("[etabli] avecLePack_tailleDuPaquetDuCatalogue : IGNORE, pack absent");
            helper.succeed();
            return;
        }
        var recettes = BenchCatalog.current().recipes();
        var fragments = dev.oneforallsurvival.bench.RecipeListPacket.fragments(BenchCatalog.generation(), recettes);
        long total = 0;
        long plusGros = 0;
        for (var f : fragments) {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            f.encode(buf);
            total += buf.readableBytes();
            plusGros = Math.max(plusGros, buf.readableBytes());
            buf.release();
        }
        org.apache.logging.log4j.LogManager.getLogger("oneforallsurvival-bench").info(
                "[etabli] paquet du catalogue : {} recettes, {} fragments, {} octets au total, plus gros fragment {} octets",
                recettes.size(), fragments.size(), total, plusGros);
        helper.assertTrue(!recettes.isEmpty(), "le catalogue du pack ne devait pas être vide");
        helper.assertTrue(plusGros < 1_000_000, "un fragment dépasse la limite d'un paquet personnalisé : " + plusGros);
        helper.succeed();
    }
}
