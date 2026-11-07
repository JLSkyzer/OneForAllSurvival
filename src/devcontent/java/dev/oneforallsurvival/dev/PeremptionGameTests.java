package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.DecayState;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FoodTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** La péremption : tags, états selon la date, durée lente, déchet. Les dates sont posées à la main. */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PeremptionGameTests {

    private PeremptionGameTests() {}

    private static ItemStack essai(GameTestHelper helper, String name) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("oneforallsurvival", name));
        helper.assertTrue(item != null && item != Items.AIR, name + " est introuvable");
        return new ItemStack(item);
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void unBoeufEstPerissableMaisPasLaPierre(GameTestHelper helper) {
        helper.assertTrue(FoodStamp.isPerishable(new ItemStack(Items.BEEF)), "le boeuf cru devait périmer");
        helper.assertTrue(FoodStamp.isPerishable(new ItemStack(Items.COOKED_BEEF)), "le boeuf cuit devait périmer");
        helper.assertTrue(FoodStamp.isPerishable(new ItemStack(Items.COD)), "le poisson devait périmer");
        helper.assertTrue(FoodStamp.isPerishable(new ItemStack(Items.RABBIT)), "le lapin cru devait périmer");
        helper.assertTrue(FoodStamp.isPerishable(new ItemStack(Items.COOKED_RABBIT)), "le lapin cuit devait périmer");
        helper.assertTrue(!FoodStamp.isPerishable(new ItemStack(Items.STONE)), "la pierre ne doit pas périmer");
        helper.assertTrue(!FoodStamp.isPerishable(ItemStack.EMPTY), "une pile vide ne périme pas");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void unPlatDeFarmersDelightEstPerissable(GameTestHelper helper) {
        ItemStack plat = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmersdelight", "beef_stew")));
        helper.assertTrue(!plat.isEmpty(), "farmersdelight:beef_stew est introuvable");
        helper.assertTrue(FoodStamp.isPerishable(plat), "le ragoût de boeuf devait périmer");
        ItemStack bacon = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmersdelight", "bacon")));
        helper.assertTrue(FoodStamp.isPerishable(bacon), "le bacon cru devait périmer");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void nonPerishableLEmporte(GameTestHelper helper) {
        // L'aliment d'essai est dans le tag périssable ET dans non_perishable : ce dernier gagne.
        ItemStack pile = essai(helper, "test_non_perishable_food");
        helper.assertTrue(pile.is(FoodTags.PERISHABLE), "l'aliment d'essai devait être dans le tag périssable");
        helper.assertTrue(pile.is(FoodTags.NON_PERISHABLE), "il devait aussi être dans non_perishable");
        helper.assertTrue(!FoodStamp.isPerishable(pile), "non_perishable doit l'emporter");
        helper.assertTrue(!FoodStamp.stampIfNeeded(pile, 40000), "un non périssable ne reçoit pas de date");
        helper.assertTrue(!FoodStamp.isPerishable(new ItemStack(FoodRegistry.SPOILED_FOOD.get())),
                "le déchet ne doit pas périmer");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void lesTagsDesViandesEtPoissonsResolvent(GameTestHelper helper) {
        String[][] ids = {
            {"minecraft", "cooked_beef"}, {"minecraft", "chicken"}, {"minecraft", "cooked_chicken"},
            {"minecraft", "porkchop"}, {"minecraft", "cooked_porkchop"}, {"minecraft", "mutton"},
            {"minecraft", "cooked_mutton"}, {"farmersdelight", "bacon"}, {"farmersdelight", "cooked_bacon"},
            {"minecraft", "cod"}, {"minecraft", "cooked_cod"}, {"minecraft", "salmon"},
            {"minecraft", "cooked_salmon"}, {"minecraft", "rabbit"}, {"minecraft", "cooked_rabbit"},
        };
        for (String[] id : ids) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id[0], id[1]));
            helper.assertTrue(item != null && item != Items.AIR, id[0] + ":" + id[1] + " est introuvable");
            helper.assertTrue(FoodStamp.isPerishable(new ItemStack(item)), id[0] + ":" + id[1] + " devait périmer");
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void lEtatSuitLaDate(GameTestHelper helper) {
        long fresh = FoodConfig.freshTicks();
        long stale = FoodConfig.staleTicks();
        long now = 10 * (fresh + stale);
        ItemStack pile = new ItemStack(Items.BEEF);
        FoodStamp.setStamp(pile, now);
        helper.assertTrue(FoodStamp.isStamped(pile), "la date devait être lue");
        helper.assertTrue(FoodStamp.state(pile, now) == DecayState.FRESH, "frais attendu à la date même");
        helper.assertTrue(FoodStamp.state(pile, now + fresh - 1) == DecayState.FRESH, "frais attendu juste avant fresh");
        helper.assertTrue(FoodStamp.state(pile, now + fresh) == DecayState.STALE, "rassis attendu à fresh");
        helper.assertTrue(FoodStamp.state(pile, now + fresh + stale - 1) == DecayState.STALE, "rassis attendu avant l'avarie");
        helper.assertTrue(FoodStamp.state(pile, now + fresh + stale) == DecayState.SPOILED, "avarié attendu");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void leLentDureHuitFoisPlus(GameTestHelper helper) {
        helper.assertTrue(FoodConfig.slowFactor() == 8.0, "facteur lent 8 attendu par défaut");
        ItemStack lent = essai(helper, "test_slow_food");
        helper.assertTrue(lent.is(FoodTags.SLOW_PERISHABLE), "l'aliment d'essai devait être dans slow_perishable");
        helper.assertTrue(FoodStamp.isPerishable(lent), "un aliment lent périme quand même");
        ItemStack normal = new ItemStack(Items.COOKED_BEEF);
        long fresh = FoodConfig.freshTicks();
        long stale = FoodConfig.staleTicks();
        FoodStamp.setStamp(lent, 0);
        FoodStamp.setStamp(normal, 0);
        helper.assertTrue(FoodStamp.state(normal, fresh) == DecayState.STALE, "l'aliment normal devait être rassis à fresh");
        helper.assertTrue(FoodStamp.state(lent, fresh) == DecayState.FRESH, "l'aliment lent devait rester frais à fresh");
        helper.assertTrue(FoodStamp.state(lent, 8 * fresh - 1) == DecayState.FRESH, "frais jusqu'à 8 fois fresh");
        helper.assertTrue(FoodStamp.state(lent, 8 * fresh) == DecayState.STALE, "rassis à 8 fois fresh");
        helper.assertTrue(FoodStamp.state(lent, 8 * (fresh + stale) - 1) == DecayState.STALE, "rassis jusqu'à 8 fois la somme");
        helper.assertTrue(FoodStamp.state(lent, 8 * (fresh + stale)) == DecayState.SPOILED, "avarié à 8 fois la somme");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void sansDateCEstFrais(GameTestHelper helper) {
        ItemStack pile = new ItemStack(Items.BEEF);
        helper.assertTrue(!FoodStamp.isStamped(pile), "une pile neuve n'a pas de date");
        helper.assertTrue(FoodStamp.state(pile, Long.MAX_VALUE) == DecayState.FRESH, "sans date, c'est frais");
        helper.assertTrue(FoodStamp.stampIfNeeded(pile, 40000), "la pile devait être datée");
        helper.assertTrue(FoodStamp.stamp(pile) == 36000, "date arrondie au quart d'heure attendue, vue " + FoodStamp.stamp(pile));
        helper.assertTrue(!FoodStamp.stampIfNeeded(pile, 90000), "une pile déjà datée ne se redate pas");
        helper.assertTrue(FoodStamp.stamp(pile) == 36000, "la date ne devait pas bouger");
        ItemStack pierre = new ItemStack(Items.STONE);
        helper.assertTrue(!FoodStamp.stampIfNeeded(pierre, 40000) && !pierre.hasTag(), "la pierre ne reçoit pas de date");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void leDechetSeComposte(GameTestHelper helper) {
        float chance = ComposterBlock.COMPOSTABLES.getFloat(FoodRegistry.SPOILED_FOOD.get());
        helper.assertTrue(chance == 0.5F, "compostable à 0.5 attendu, vu " + chance);
        helper.assertTrue(!new ItemStack(FoodRegistry.SPOILED_FOOD.get()).getItem().isEdible(), "le déchet ne se mange pas");
        helper.succeed();
    }
}
