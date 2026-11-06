package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.Withdrawal;
import dev.oneforallsurvival.bench.WorkshopEntry;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La file et le stock par joueur. Chaque essai travaille sur sa propre {@link WorkshopSavedData}
 * autonome : rien n'est écrit dans les données partagées du monde, et le ticker est remplacé par des
 * appels directs à {@code advance}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class WorkshopGameTests {

    private static final ResourceLocation RECETTE = new ResourceLocation("oneforallsurvival", "test_bench_shaped");

    private WorkshopGameTests() {}

    private static WorkshopEntry entree(UUID id, ItemStack result, int totalTicks, List<ItemStack> paid,
            List<ItemStack> restes) {
        return new WorkshopEntry(id, RECETTE, result, paid, restes, totalTicks, 0.0, Level.OVERWORLD,
                new BlockPos(1, 2, 3), false);
    }

    private static WorkshopEntry simple(int totalTicks) {
        return entree(UUID.randomUUID(), new ItemStack(Items.STICK, 2), totalTicks,
                List.of(new ItemStack(Items.OAK_PLANKS)), List.of());
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void lePlanEstAtomique(GameTestHelper helper) {
        List<ItemStack> inventaire = List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.STICK, 5));
        List<Ingredient> besoin = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.DIAMOND));
        helper.assertTrue(Withdrawal.plan(besoin, inventaire).isEmpty(), "le plan devait échouer");
        helper.assertTrue(inventaire.get(0).getCount() == 2 && inventaire.get(1).getCount() == 5,
                "l'inventaire a été modifié");
        // Le même besoin sans l'ingrédient absent réussit : l'échec venait bien du diamant.
        Optional<int[]> plan = Withdrawal.plan(besoin.subList(0, 2), inventaire);
        helper.assertTrue(plan.isPresent() && plan.get()[0] == 0 && plan.get()[1] == 0, "plan attendu : 0, 0");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void unePileNeSertPasDeuxFoisAuDelaDeSonNombre(GameTestHelper helper) {
        List<ItemStack> inventaire = List.of(new ItemStack(Items.IRON_INGOT, 1));
        List<Ingredient> besoin = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT));
        helper.assertTrue(Withdrawal.plan(besoin, inventaire).isEmpty(), "une pile de 1 a servi deux fois");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void lesTagsSontAcceptes(GameTestHelper helper) {
        Ingredient lingot = Ingredient.of(Tags.Items.INGOTS);
        List<ItemStack> inventaire = List.of(new ItemStack(Items.STICK), new ItemStack(Items.GOLD_INGOT));
        Optional<int[]> plan = Withdrawal.plan(List.of(lingot), inventaire);
        helper.assertTrue(plan.isPresent() && plan.get()[0] == 1, "le lingot d'or devait servir le tag");
        inventaire = List.of(new ItemStack(Items.COPPER_INGOT));
        plan = Withdrawal.plan(List.of(lingot), inventaire);
        helper.assertTrue(plan.isPresent() && plan.get()[0] == 0, "le lingot de cuivre devait servir le tag");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void lesIngredientsSeRecoupent(GameTestHelper helper) {
        // Le tag est listé en premier : un premier ajustement glouton lui donnerait le fer, et le
        // lingot de fer précis resterait sans rien.
        List<ItemStack> inventaire = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT));
        List<Ingredient> besoin = List.of(Ingredient.of(Tags.Items.INGOTS), Ingredient.of(Items.IRON_INGOT));
        Optional<int[]> plan = Withdrawal.plan(besoin, inventaire);
        helper.assertTrue(plan.isPresent(), "le plan devait réussir en réaffectant le tag à l'or");
        helper.assertTrue(plan.get()[0] == 1 && plan.get()[1] == 0, "affectation inattendue");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void seuleLaTeteAvance(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        atelier.add(simple(100));
        atelier.add(simple(100));
        atelier.advance(10);
        helper.assertTrue(atelier.queue().get(0).doneTicks() == 10.0, "la tête devait être à 10");
        helper.assertTrue(atelier.queue().get(1).doneTicks() == 0.0, "la deuxième devait rester à 0");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void laFinVersLeStock(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        WorkshopEntry e = entree(UUID.randomUUID(), new ItemStack(Items.BREAD), 10,
                List.of(new ItemStack(Items.MILK_BUCKET)), List.of(new ItemStack(Items.BUCKET)));
        atelier.add(e);
        atelier.advance(9);
        helper.assertTrue(atelier.queue().size() == 1 && atelier.stock().isEmpty(), "trop tôt");
        atelier.advance(5);
        helper.assertTrue(atelier.queue().isEmpty(), "l'entrée devait être retirée");
        helper.assertTrue(atelier.stock().size() == 2, "le stock devait avoir 2 piles");
        helper.assertTrue(atelier.stock().get(0).is(Items.BREAD), "le résultat est absent");
        helper.assertTrue(atelier.stock().get(1).is(Items.BUCKET), "le seau rendu est absent");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void laFinPasseALEntreeSuivante(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        atelier.add(simple(5));
        atelier.add(simple(100));
        atelier.advance(8);
        helper.assertTrue(atelier.queue().size() == 1, "la première devait avoir fini");
        helper.assertTrue(atelier.queue().get(0).doneTicks() == 0.0, "le surplus n'est pas reporté");
        atelier.advance(1);
        helper.assertTrue(atelier.queue().get(0).doneTicks() == 1.0, "la suivante devait avancer");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void annulerEnAttenteOuEnCoursRendLesPiles(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        ItemStack usee = new ItemStack(Items.IRON_PICKAXE);
        usee.setDamageValue(37);
        usee.setHoverName(net.minecraft.network.chat.Component.literal("Vieille pioche"));
        UUID enCours = UUID.randomUUID();
        UUID enAttente = UUID.randomUUID();
        atelier.add(entree(enCours, new ItemStack(Items.STICK), 100, List.of(usee), List.of()));
        atelier.add(entree(enAttente, new ItemStack(Items.STICK), 100, List.of(usee), List.of()));
        atelier.advance(10);

        Optional<WorkshopEntry> rendue = atelier.cancel(enCours);
        helper.assertTrue(rendue.isPresent(), "l'entrée en cours devait s'annuler");
        ItemStack p = rendue.get().paid().get(0);
        helper.assertTrue(ItemStack.matches(p, usee) && p.getDamageValue() == 37, "pile payée altérée");
        helper.assertTrue(atelier.queue().size() == 1 && atelier.queue().get(0).id().equals(enAttente),
                "la file devait ne garder que l'entrée en attente");
        helper.assertTrue(atelier.cancel(enAttente).isPresent(), "l'entrée en attente devait s'annuler");
        helper.assertTrue(atelier.cancel(UUID.randomUUID()).isEmpty(), "un UUID inconnu devait rendre vide");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void fileEtStockSurviventALaSauvegarde(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        UUID joueur = UUID.randomUUID();
        PlayerWorkshop atelier = data.of(joueur);
        ItemStack nommee = new ItemStack(Items.IRON_PICKAXE);
        nommee.setDamageValue(12);
        nommee.setHoverName(net.minecraft.network.chat.Component.literal("Marquée"));
        UUID id = UUID.randomUUID();
        atelier.add(new WorkshopEntry(id, RECETTE, new ItemStack(Items.STICK, 3), List.of(nommee),
                List.of(new ItemStack(Items.BUCKET)), 200, 0.0, Level.NETHER, new BlockPos(-5, 70, 9), true));
        atelier.add(simple(50));
        atelier.advance(42.5);
        atelier.addToStock(nommee);

        WorkshopSavedData relu = WorkshopSavedData.load(data.save(new CompoundTag()));
        PlayerWorkshop r = relu.of(joueur);
        helper.assertTrue(r.queue().size() == 2, "deux entrées attendues");
        WorkshopEntry t = r.queue().get(0);
        helper.assertTrue(t.id().equals(id) && t.recipeId().equals(RECETTE), "identité altérée");
        helper.assertTrue(t.doneTicks() == 42.5 && t.totalTicks() == 200, "progression altérée");
        helper.assertTrue(t.benchLevel().equals(Level.NETHER) && t.benchPos().equals(new BlockPos(-5, 70, 9))
                && t.lastKnownPowered(), "position de l'établi altérée");
        helper.assertTrue(ItemStack.matches(t.paid().get(0), nommee) && t.paid().get(0).getDamageValue() == 12,
                "pile payée (NBT, usure) altérée");
        helper.assertTrue(t.remainders().size() == 1 && t.remainders().get(0).is(Items.BUCKET), "reste altéré");
        helper.assertTrue(t.result().is(Items.STICK) && t.result().getCount() == 3, "résultat altéré");
        helper.assertTrue(r.stock().size() == 1 && ItemStack.matches(r.stock().get(0), nommee), "stock altéré");
        helper.assertTrue(relu.activeWorkshops().size() == 1, "l'atelier rechargé devait être actif");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void unStockTresLong(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        UUID joueur = UUID.randomUUID();
        PlayerWorkshop atelier = data.of(joueur);
        for (int i = 0; i < 2000; i++) {
            ItemStack s = new ItemStack(Items.STICK);
            s.getOrCreateTag().putInt("n", i);
            atelier.addToStock(s);
        }
        helper.assertTrue(atelier.stock().size() == 2000, "2000 piles différentes attendues, vu "
                + atelier.stock().size());
        WorkshopSavedData relu = WorkshopSavedData.load(data.save(new CompoundTag()));
        List<ItemStack> stock = relu.of(joueur).stock();
        helper.assertTrue(stock.size() == 2000, "le stock rechargé a " + stock.size() + " piles");
        helper.assertTrue(stock.get(1999).getTag().getInt("n") == 1999, "ordre ou NBT altéré");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void deuxJoueursIndependants(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop a = data.of(UUID.randomUUID());
        PlayerWorkshop b = data.of(UUID.randomUUID());
        a.add(simple(10));
        b.add(simple(100));
        a.advance(10);
        b.advance(3);
        helper.assertTrue(a.queue().isEmpty() && a.stock().size() == 1, "A devait avoir fini");
        helper.assertTrue(b.queue().size() == 1 && b.queue().get(0).doneTicks() == 3.0 && b.stock().isEmpty(),
                "B devait avoir avancé seul");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void leStockFusionneSansPerdreNiMelanger(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        atelier.addToStock(new ItemStack(Items.STONE, 60));
        atelier.addToStock(new ItemStack(Items.STONE, 10));
        helper.assertTrue(atelier.stock().size() == 2 && atelier.stock().get(0).getCount() == 64
                && atelier.stock().get(1).getCount() == 6, "fusion jusqu'à 64 attendue : 64 puis 6");
        atelier.addToStock(ItemStack.EMPTY);
        helper.assertTrue(atelier.stock().size() == 2, "une pile vide ne doit rien ajouter");
        ItemStack marquee = new ItemStack(Items.STONE, 1);
        marquee.getOrCreateTag().putString("marque", "x");
        atelier.addToStock(marquee);
        helper.assertTrue(atelier.stock().size() == 3, "un NBT différent ne doit pas fusionner");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void uneEntreeCorrompueEstIgnoree(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        UUID joueur = UUID.randomUUID();
        data.of(joueur).add(simple(50));
        data.of(joueur).addToStock(new ItemStack(Items.STONE, 4));
        CompoundTag tag = data.save(new CompoundTag());

        CompoundTag atelier = tag.getList("workshops", 10).getCompound(0);
        ListTag file = atelier.getList("queue", 10);
        file.add(new CompoundTag()); // sans identifiant
        CompoundTag inconnu = file.getCompound(0).copy();
        inconnu.putUUID("id", UUID.randomUUID());
        inconnu.getCompound("result").putString("id", "oneforallsurvival:n_existe_pas");
        file.add(inconnu);
        atelier.getList("stock", 10).add(new CompoundTag());
        ListTag ateliers = tag.getList("workshops", 10);
        ateliers.add(new CompoundTag()); // atelier sans joueur
        tag.put("workshops", ateliers);

        WorkshopSavedData relu = WorkshopSavedData.load(tag);
        PlayerWorkshop r = relu.of(joueur);
        helper.assertTrue(r.queue().size() == 1, "seule l'entrée saine devait rester, vu " + r.queue().size());
        // L'entrée au résultat inconnu n'est pas relancée, mais son bois payé n'est pas détruit : il va au stock.
        helper.assertTrue(r.stock().size() == 2 && compterStock(r, Items.STONE) == 4 && compterStock(r, Items.OAK_PLANKS) == 1,
                "le stock sain devait rester, plus le bois payé de l'entrée inconnue : " + r.stock());
        helper.succeed();
    }

    private static int compterStock(PlayerWorkshop atelier, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : atelier.stock()) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void unResultatInconnuRendLesPilesPayeesAuStock(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        UUID joueur = UUID.randomUUID();
        data.of(joueur).add(entree(UUID.randomUUID(), new ItemStack(Items.STICK, 2), 50,
                List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.COPPER_INGOT, 3),
                        new ItemStack(Items.OAK_PLANKS)),
                List.of(new ItemStack(Items.BUCKET))));
        CompoundTag tag = data.save(new CompoundTag());
        CompoundTag entree = tag.getList("workshops", 10).getCompound(0).getList("queue", 10).getCompound(0);
        // Un mod retiré : le résultat n'existe plus, et une des piles payées non plus.
        entree.getCompound("result").putString("id", "oneforallsurvival:n_existe_pas");
        entree.getList("paid", 10).getCompound(1).putString("id", "oneforallsurvival:aussi_disparu");

        PlayerWorkshop r = WorkshopSavedData.load(tag).of(joueur);
        helper.assertTrue(r.queue().isEmpty(), "l'entrée au résultat inconnu ne devait pas être relancée");
        helper.assertTrue(compterStock(r, Items.IRON_INGOT) == 2 && compterStock(r, Items.OAK_PLANKS) == 1,
                "les piles payées lisibles devaient aller au stock : " + r.stock());
        helper.assertTrue(compterStock(r, Items.COPPER_INGOT) == 0 && r.stock().size() == 2,
                "la pile illisible ne devait pas apparaître : " + r.stock());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void uneSeulePilePayeeIllisibleNeFaitPasPerdreLEntree(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        UUID joueur = UUID.randomUUID();
        data.of(joueur).add(entree(UUID.randomUUID(), new ItemStack(Items.STICK, 2), 50,
                List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.COPPER_INGOT, 3)), List.of()));
        CompoundTag tag = data.save(new CompoundTag());
        CompoundTag entree = tag.getList("workshops", 10).getCompound(0).getList("queue", 10).getCompound(0);
        entree.getList("paid", 10).getCompound(0).putString("id", "oneforallsurvival:aussi_disparu");

        PlayerWorkshop r = WorkshopSavedData.load(tag).of(joueur);
        helper.assertTrue(r.queue().size() == 1, "l'entrée au résultat connu devait rester, vu " + r.queue().size());
        List<ItemStack> payees = r.queue().get(0).paid();
        helper.assertTrue(payees.size() == 1 && payees.get(0).is(Items.COPPER_INGOT) && payees.get(0).getCount() == 3,
                "la pile lisible devait rester payée : " + payees);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_file")
    public static void laProgressionNeSalitQuUneFoisParSeconde(GameTestHelper helper) {
        WorkshopSavedData data = new WorkshopSavedData();
        PlayerWorkshop atelier = data.of(UUID.randomUUID());
        atelier.add(simple(1000));
        helper.assertTrue(data.isDirty(), "l'ajout devait marquer la sauvegarde");
        data.setDirty(false);
        for (int i = 0; i < 19; i++) {
            atelier.advance(1);
        }
        helper.assertTrue(!data.isDirty(), "19 ticks ne doivent pas salir la sauvegarde");
        atelier.advance(1);
        helper.assertTrue(data.isDirty(), "le 20e tick devait salir la sauvegarde");
        helper.succeed();
    }
}
