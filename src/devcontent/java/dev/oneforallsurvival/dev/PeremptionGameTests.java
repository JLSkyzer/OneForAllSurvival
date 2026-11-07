package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.DecayState;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodEvents;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FoodTags;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.GalvanicBathMenu;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemStackedOnOtherEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
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

    // ------------------------------------------------------------------ vie des aliments

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);

    private static JoueurDeTest joueur(GameTestHelper helper, String nom) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    private static ItemStack date(Item item, int nombre, long date) {
        ItemStack pile = new ItemStack(item, nombre);
        FoodStamp.setStamp(pile, date);
        return pile;
    }

    /** Une date qui fait de l'aliment un aliment rassis maintenant (la partie fraîche est écoulée). */
    private static long rassis() {
        return -FoodConfig.freshTicks() - 1000;
    }

    /** Une date qui fait de l'aliment un aliment avarié maintenant. */
    private static long avarie() {
        return -(FoodConfig.freshTicks() + FoodConfig.staleTicks()) - 1000;
    }

    private static int total(ServerPlayer joueur, Item... objets) {
        int somme = 0;
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            ItemStack pile = joueur.getInventory().getItem(i);
            for (Item objet : objets) {
                if (pile.is(objet)) {
                    somme += pile.getCount();
                }
            }
        }
        return somme;
    }

    private static SlotAccess portee(ItemStack[] main) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return main[0];
            }

            @Override
            public boolean set(ItemStack pile) {
                main[0] = pile;
                return true;
            }
        };
    }

    @GameTest(template = "test/plancher", batch = "peremption_inventaire")
    public static void lInventaireDateSesAliments(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Gardemanger");
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
            p.getInventory().setItem(1, new ItemStack(Items.COOKED_COD, 2));
            p.getInventory().setItem(2, new ItemStack(Items.STONE, 5));
            p.getInventory().setItem(3, date(Items.COOKED_BEEF, 2, 36000));
            // Les joueurs d'essai ne sont pas animés par le niveau : on publie l'événement de tick nous-mêmes.
            // Entre deux secondes, l'abonnement ne fait rien.
            p.tickCount = 21;
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
            helper.assertTrue(!p.getInventory().getItem(0).hasTag(), "pas de passe entre deux secondes");
            p.tickCount = 20;
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, p));
            helper.assertTrue(!p.getInventory().getItem(0).hasTag(), "pas de passe au début du tick");
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
            helper.assertTrue(FoodStamp.isStamped(p.getInventory().getItem(0)), "le boeuf devait être daté");
            helper.assertTrue(FoodStamp.isStamped(p.getInventory().getItem(1)), "le poisson devait être daté");
            helper.assertTrue(FoodStamp.stamp(p.getInventory().getItem(0)) % FoodDecay.BUCKET_TICKS == 0,
                    "la date devait être arrondie au quart d'heure");
            helper.assertTrue(!p.getInventory().getItem(2).hasTag(), "la pierre ne devait pas être touchée");
            helper.assertTrue(FoodStamp.stamp(p.getInventory().getItem(3)) == 36000, "une date existante ne se refait pas");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void unObjetAuSolEstDate(GameTestHelper helper) {
        ItemEntity boeuf = helper.spawnItem(Items.BEEF, 1.5F, 2.0F, 1.5F);
        ItemEntity pierre = helper.spawnItem(Items.STONE, 2.5F, 2.0F, 1.5F);
        helper.assertTrue(FoodStamp.isStamped(boeuf.getItem()), "l'objet au sol devait être daté dès son apparition");
        helper.assertTrue(!pierre.getItem().hasTag(), "la pierre au sol ne devait pas être touchée");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void lAvarieDevientUnDechetAvecLeMemeNombre(GameTestHelper helper) {
        long now = 40000;
        ItemStack vieux = date(Items.BEEF, 5, avarie());
        ItemStack dechet = FoodEvents.observe(vieux, now);
        helper.assertTrue(dechet.is(FoodRegistry.SPOILED_FOOD.get()) && dechet.getCount() == 5,
                "5 déchets attendus, vu " + dechet);
        ItemStack frais = date(Items.BEEF, 3, FoodDecay.roundDown(now));
        helper.assertTrue(FoodEvents.observe(frais, now) == frais, "un aliment frais reste la même pile");
        ItemStack sansDate = new ItemStack(Items.COOKED_BEEF, 2);
        helper.assertTrue(FoodEvents.observe(sansDate, now) == sansDate && FoodStamp.isStamped(sansDate),
                "un aliment sans date est daté, sur place");
        ItemStack pierre = new ItemStack(Items.STONE, 4);
        helper.assertTrue(FoodEvents.observe(pierre, now) == pierre && !pierre.hasTag(), "la pierre reste intacte");
        ItemStack deja = new ItemStack(FoodRegistry.SPOILED_FOOD.get(), 2);
        helper.assertTrue(FoodEvents.observe(deja, now) == deja && !deja.hasTag(), "le déchet ne périme pas");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_balayage")
    public static void lAvarieDansLInventaireDevientUnDechet(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Oublieux");
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(4, date(Items.BEEF, 5, avarie()));
            p.getInventory().setItem(5, date(Items.COOKED_BEEF, 2, rassis()));
            FoodEvents.scan(p);
            ItemStack dechet = p.getInventory().getItem(4);
            helper.assertTrue(dechet.is(FoodRegistry.SPOILED_FOOD.get()) && dechet.getCount() == 5,
                    "5 déchets attendus à la place du boeuf, vu " + dechet);
            helper.assertTrue(p.getInventory().getItem(5).is(Items.COOKED_BEEF), "le rassis reste un aliment");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void laFusionFaitLaMoyenne(GameTestHelper helper) {
        ItemStack dans = date(Items.BEEF, 3, 0);
        ItemStack depuis = date(Items.BEEF, 1, 72000);
        int deplace = FoodEvents.merge(dans, depuis, 64);
        helper.assertTrue(deplace == 1, "1 objet déplacé attendu, vu " + deplace);
        helper.assertTrue(dans.getCount() == 4, "4 objets attendus, vu " + dans.getCount());
        helper.assertTrue(FoodStamp.stamp(dans) == 18000, "date 18000 attendue, vue " + FoodStamp.stamp(dans));
        helper.assertTrue(depuis.isEmpty(), "la pile source devait être vide");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_clic")
    public static void laFusionNePerdNiNeCreeRien(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Fusionneur");
            ServerPlayer p = j.joueur();
            // Clic gauche : 60 + 10 donnent 64 + 6, et la pile portée est mise à jour.
            p.getInventory().setItem(9, date(Items.BEEF, 60, 36000));
            p.containerMenu.setCarried(date(Items.BEEF, 10, 90000));
            p.containerMenu.clicked(9, 0, ClickType.PICKUP, p);
            ItemStack fond = p.getInventory().getItem(9);
            ItemStack main = p.containerMenu.getCarried();
            helper.assertTrue(fond.getCount() == 64, "64 attendus dans la case, vu " + fond.getCount());
            helper.assertTrue(main.getCount() == 6, "6 attendus en main, vu " + main.getCount());
            helper.assertTrue(FoodStamp.stamp(fond) == FoodDecay.weightedStamp(36000, 60, 90000, 4),
                    "date moyenne attendue dans la case, vue " + FoodStamp.stamp(fond));
            helper.assertTrue(FoodStamp.stamp(main) == 90000, "le reste en main garde sa date");
            // Pile pleine : rien ne se fond, le comportement vanilla échange, sans perte.
            p.containerMenu.clicked(9, 0, ClickType.PICKUP, p);
            helper.assertTrue(p.getInventory().getItem(9).getCount() + p.containerMenu.getCarried().getCount() == 70,
                    "l'échange sur pile pleine ne devait rien perdre");
            p.containerMenu.setCarried(ItemStack.EMPTY);
            p.getInventory().setItem(9, ItemStack.EMPTY);
            // Clic droit : un seul objet se déplace.
            p.getInventory().setItem(10, date(Items.BEEF, 20, 0));
            p.containerMenu.setCarried(date(Items.BEEF, 5, 72000));
            p.containerMenu.clicked(10, 1, ClickType.PICKUP, p);
            helper.assertTrue(p.getInventory().getItem(10).getCount() == 21,
                    "21 attendus, vu " + p.getInventory().getItem(10).getCount());
            helper.assertTrue(p.containerMenu.getCarried().getCount() == 4,
                    "4 attendus en main, vu " + p.containerMenu.getCarried().getCount());
            helper.assertTrue(FoodStamp.stamp(p.getInventory().getItem(10)) == FoodDecay.weightedStamp(0, 20, 72000, 1),
                    "date moyenne attendue après le clic droit");
            // Un seul objet portée : la main se vide.
            p.containerMenu.setCarried(date(Items.BEEF, 1, 72000));
            p.containerMenu.clicked(10, 1, ClickType.PICKUP, p);
            helper.assertTrue(p.containerMenu.getCarried().isEmpty() && p.getInventory().getItem(10).getCount() == 22,
                    "la main devait se vider, 22 dans la case");
            // Des objets différents : l'événement n'est pas annulé.
            ItemStack[] main2 = {date(Items.COD, 2, 72000)};
            Slot case10 = p.containerMenu.slots.get(10);
            boolean annule = MinecraftForge.EVENT_BUS.post(new ItemStackedOnOtherEvent(
                    main2[0], case10.getItem(), case10, ClickAction.PRIMARY, p, portee(main2)));
            helper.assertTrue(!annule, "deux objets différents ne devaient pas être fondus");
            helper.assertTrue(main2[0].getCount() == 2 && case10.getItem().getCount() == 22, "rien ne devait bouger");
            // Même objet à la même date : rien à moyenner, l'événement n'est pas annulé non plus.
            ItemStack[] main3 = {date(Items.BEEF, 2, FoodStamp.stamp(case10.getItem()))};
            boolean annule2 = MinecraftForge.EVENT_BUS.post(new ItemStackedOnOtherEvent(
                    main3[0], case10.getItem(), case10, ClickAction.PRIMARY, p, portee(main3)));
            helper.assertTrue(!annule2, "deux piles à la même date relèvent du comportement vanilla");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_coffre")
    public static void laFermetureDUnCoffreRegroupe(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity coffre = (ChestBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(coffre != null, "pas de coffre");
        coffre.setItem(0, date(Items.BEEF, 30, 0));
        coffre.setItem(5, date(Items.BEEF, 20, 72000));
        coffre.setItem(7, date(Items.BEEF, 10, 36000));
        coffre.setItem(9, new ItemStack(Items.COD, 4));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Rangeur");
            ServerPlayer p = j.joueur();
            helper.assertTrue(p.openMenu(coffre).isPresent(), "le coffre devait s'ouvrir");
            p.closeContainer();
            helper.assertTrue(coffre.getItem(0).getCount() == 60, "60 attendus dans la première case, vu " + coffre.getItem(0));
            helper.assertTrue(coffre.getItem(5).isEmpty() && coffre.getItem(7).isEmpty(), "les autres piles devaient être vidées");
            helper.assertTrue(FoodStamp.stamp(coffre.getItem(0)) == 18000,
                    "date 18000 attendue, vue " + FoodStamp.stamp(coffre.getItem(0)));
            helper.assertTrue(coffre.getItem(9).getCount() == 4, "le poisson ne devait pas bouger");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Un four a un vrai conteneur de bloc : ses cases ont des rôles, on n'y regroupe jamais rien. */
    @GameTest(template = "test/plancher", batch = "peremption_four")
    public static void laFermetureNeToucheJamaisUneAutreMachine(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.FURNACE);
        FurnaceBlockEntity four = (FurnaceBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(four != null, "pas de four");
        four.setItem(0, date(Items.BEEF, 3, 0));
        four.setItem(2, date(Items.BEEF, 1, 72000));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Curieux");
            ServerPlayer p = j.joueur();
            helper.assertTrue(p.openMenu(four).isPresent(), "le four devait s'ouvrir");
            helper.assertTrue(p.containerMenu instanceof FurnaceMenu, "un menu de four attendu");
            p.closeContainer();
            helper.assertTrue(four.getItem(0).getCount() == 3 && FoodStamp.stamp(four.getItem(0)) == 0,
                    "l'entrée du four devait rester intacte, vu " + four.getItem(0));
            helper.assertTrue(four.getItem(2).getCount() == 1 && FoodStamp.stamp(four.getItem(2)) == 72000,
                    "la sortie du four devait rester intacte, vu " + four.getItem(2));
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Un grand coffre est un {@code CompoundContainer} derrière un {@code ChestMenu} : il est regroupé. */
    @GameTest(template = "test/plancher", batch = "peremption_grand_coffre")
    public static void laFermetureRegroupeUnGrandCoffre(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        helper.setBlock(POS.east(), Blocks.CHEST);
        ChestBlockEntity gauche = (ChestBlockEntity) helper.getBlockEntity(POS);
        ChestBlockEntity droite = (ChestBlockEntity) helper.getBlockEntity(POS.east());
        helper.assertTrue(gauche != null && droite != null, "pas de coffres");
        CompoundContainer grand = new CompoundContainer(gauche, droite);
        grand.setItem(0, date(Items.BEEF, 30, 0));
        grand.setItem(27 + 3, date(Items.BEEF, 20, 72000));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Rangeuse");
            ServerPlayer p = j.joueur();
            ChestMenu menu = ChestMenu.sixRows(91, p.getInventory(), grand);
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(p, menu));
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Close(p, menu));
            helper.assertTrue(grand.getItem(0).getCount() == 50, "50 attendus dans la première case, vu " + grand.getItem(0));
            helper.assertTrue(grand.getItem(30).isEmpty(), "la seconde pile devait être vidée");
            helper.assertTrue(FoodStamp.stamp(grand.getItem(0)) == FoodDecay.weightedStamp(0, 30, 72000, 20),
                    "date moyenne attendue, vue " + FoodStamp.stamp(grand.getItem(0)));
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** L'inventaire du joueur, vu depuis un menu de coffre, n'est pas regroupé par la fermeture du coffre. */
    @GameTest(template = "test/plancher", batch = "peremption_inventaire_coffre")
    public static void laFermetureDUnCoffreNeRegroupePasLInventaire(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity coffre = (ChestBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(coffre != null, "pas de coffre");
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Porteur");
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(9, date(Items.BEEF, 3, 0));
            p.getInventory().setItem(10, date(Items.BEEF, 1, 72000));
            helper.assertTrue(p.openMenu(coffre).isPresent(), "le coffre devait s'ouvrir");
            p.closeContainer();
            helper.assertTrue(p.getInventory().getItem(9).getCount() == 3 && p.getInventory().getItem(10).getCount() == 1,
                    "l'inventaire n'est regroupé que par la passe du joueur");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_ouverture")
    public static void unAlimentNonDateEstDateALOuvertureDUnCoffre(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity coffre = (ChestBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(coffre != null, "pas de coffre");
        coffre.setItem(4, new ItemStack(Items.BEEF, 2));
        coffre.setItem(5, new ItemStack(Items.STONE, 2));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Ouvreur");
            helper.assertTrue(j.joueur().openMenu(coffre).isPresent(), "le coffre devait s'ouvrir");
            helper.assertTrue(FoodStamp.isStamped(coffre.getItem(4)), "le boeuf du coffre devait être daté à l'ouverture");
            helper.assertTrue(!coffre.getItem(5).hasTag(), "la pierre ne devait pas être touchée");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_ouverture_avarie")
    public static void unAvarieDansUnCoffreDevientUnDechetALOuverture(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity coffre = (ChestBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(coffre != null, "pas de coffre");
        coffre.setItem(4, date(Items.BEEF, 6, avarie()));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Fouineur");
            helper.assertTrue(j.joueur().openMenu(coffre).isPresent(), "le coffre devait s'ouvrir");
            ItemStack pile = coffre.getItem(4);
            helper.assertTrue(pile.is(FoodRegistry.SPOILED_FOOD.get()) && pile.getCount() == 6,
                    "6 déchets attendus, vu " + pile);
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Une case de mod (ici celles du bain) peut refuser un objet : on la date, on n'y pose jamais de déchet. */
    @GameTest(template = "test/plancher", batch = "peremption_bain")
    public static void uneCaseDeModRecoitLaDateMaisJamaisLeDechet(GameTestHelper helper) {
        helper.setBlock(POS, RepairRegistry.GALVANIC_BATH.get());
        GalvanicBathBlockEntity bain = (GalvanicBathBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(bain != null, "pas de bain");
        bain.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, 3));
        bain.inventory().setStackInSlot(1, date(Items.BEEF, 2, avarie()));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Curieuse");
            ServerPlayer p = j.joueur();
            GalvanicBathMenu menu = new GalvanicBathMenu(77, p.getInventory(), bain, helper.absolutePos(POS));
            MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(p, menu));
            helper.assertTrue(FoodStamp.isStamped(bain.inventory().getStackInSlot(0)), "la case du bain devait être datée");
            ItemStack avarie = bain.inventory().getStackInSlot(1);
            helper.assertTrue(avarie.is(Items.BEEF) && avarie.getCount() == 2,
                    "une case de mod ne reçoit jamais de déchet, vu " + avarie);
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption")
    public static void unAlimentDateResteUnIngredient(GameTestHelper helper) {
        ItemStack boeuf = date(Items.BEEF, 1, 36000);
        Ingredient etiquette = Ingredient.of(TagKey.create(Registries.ITEM, new ResourceLocation("forge", "raw_beef")));
        helper.assertTrue(etiquette.test(boeuf), "le boeuf daté devait rester un ingrédient du tag forge:raw_beef");
        // Le four.
        SimpleContainer four = new SimpleContainer(boeuf);
        Optional<SmeltingRecipe> cuisson = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, four, helper.getLevel());
        helper.assertTrue(cuisson.isPresent(), "la recette de four du boeuf daté est introuvable");
        ItemStack sortie = cuisson.get().assemble(four, helper.getLevel().registryAccess());
        helper.assertTrue(sortie.is(Items.COOKED_BEEF) && !sortie.hasTag(), "la sortie devait être un steak sans date, vu " + sortie);
        // La table de craft (ragoût de lapin, recette sans forme).
        CraftingContainer table = new TransientCraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player joueur, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player joueur) {
                return true;
            }
        }, 3, 3);
        Item[] ingredients = {Items.COOKED_RABBIT, Items.BAKED_POTATO, Items.CARROT, Items.BROWN_MUSHROOM, Items.BOWL};
        for (int i = 0; i < ingredients.length; i++) {
            table.setItem(i, date(ingredients[i], 1, 36000));
        }
        Optional<CraftingRecipe> ragout = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, table, helper.getLevel());
        helper.assertTrue(ragout.isPresent(), "la recette du ragoût de lapin avec ingrédients datés est introuvable");
        ItemStack plat = ragout.get().assemble(table, helper.getLevel().registryAccess());
        helper.assertTrue(plat.is(Items.RABBIT_STEW) && !plat.hasTag(), "le ragoût devait sortir sans date, vu " + plat);
        // Le livre de recettes compte les objets sans regarder leurs données.
        StackedContents comptes = new StackedContents();
        comptes.accountSimpleStack(boeuf);
        helper.assertTrue(comptes.canCraft(cuisson.get(), null), "le livre de recettes devait compter le boeuf daté");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_avarie_main")
    public static void onNeMangePasUnAvarie(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Imprudent");
            ServerPlayer p = j.joueur();
            p.getFoodData().setFoodLevel(4);
            p.setItemInHand(InteractionHand.MAIN_HAND, date(Items.COOKED_BEEF, 3, avarie()));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(!p.isUsingItem(), "un aliment avarié ne devait pas se manger");
            ItemStack main = p.getMainHandItem();
            helper.assertTrue(main.is(FoodRegistry.SPOILED_FOOD.get()) && main.getCount() == 3,
                    "3 déchets attendus en main, vu " + main);
            helper.assertTrue(p.getFoodData().getFoodLevel() == 4, "la faim ne devait pas bouger");
            // Dans l'autre main aussi.
            p.setItemInHand(InteractionHand.OFF_HAND, date(Items.COOKED_BEEF, 2, avarie()));
            p.startUsingItem(InteractionHand.OFF_HAND);
            helper.assertTrue(!p.isUsingItem() && p.getOffhandItem().is(FoodRegistry.SPOILED_FOOD.get())
                    && p.getOffhandItem().getCount() == 2, "2 déchets attendus dans la main gauche");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Fait passer le temps d'un repas : le niveau n'anime pas les joueurs d'essai, on les anime à la main. */
    private static void manger(ServerPlayer joueur, int ticks) {
        for (int i = 0; i < ticks; i++) {
            joueur.doTick();
        }
    }

    @GameTest(template = "test/plancher", batch = "peremption_rassis")
    public static void leRassisNourritDeMoitie(GameTestHelper helper) {
        int nutrition = Items.COOKED_BEEF.getFoodProperties().getNutrition();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Convive");
            ServerPlayer p = j.joueur();
            p.getFoodData().setFoodLevel(4);
            p.getFoodData().setSaturation(0.0F);
            p.setItemInHand(InteractionHand.MAIN_HAND, date(Items.COOKED_BEEF, 2, rassis()));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(p.isUsingItem(), "un aliment rassis se mange");
            manger(p, 40);
            helper.assertTrue(!p.isUsingItem(), "le repas devait être fini");
            helper.assertTrue(p.getMainHandItem().getCount() == 1, "un steak devait avoir été mangé");
            int attendu = 4 + nutrition / 2;
            helper.assertTrue(p.getFoodData().getFoodLevel() == attendu,
                    "faim " + attendu + " attendue (moitié de " + nutrition + "), vue " + p.getFoodData().getFoodLevel());
            helper.assertTrue(p.getFoodData().getSaturationLevel() <= p.getFoodData().getFoodLevel(),
                    "la saturation ne dépasse pas la faim");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_frais")
    public static void leFraisNourritEntierement(GameTestHelper helper) {
        int nutrition = Items.COOKED_BEEF.getFoodProperties().getNutrition();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Gourmand");
            ServerPlayer p = j.joueur();
            p.getFoodData().setFoodLevel(4);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 2));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(p.isUsingItem(), "un aliment frais se mange");
            manger(p, 40);
            helper.assertTrue(p.getFoodData().getFoodLevel() == 4 + nutrition,
                    "faim " + (4 + nutrition) + " attendue, vue " + p.getFoodData().getFoodLevel());
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Un repas dont l'aliment s'avarie en route : l'état noté au Start décide, rassis ou frais. */
    @GameTest(template = "test/plancher", batch = "peremption_pleine_bouche")
    public static void unAvarieEnPleineBoucheNePlantePas(GameTestHelper helper) {
        int nutrition = Items.COOKED_BEEF.getFoodProperties().getNutrition();
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Lent");
            ServerPlayer p = j.joueur();
            long maintenant = helper.getLevel().getGameTime();
            long[] datesAuDepart = {rassis(), FoodDecay.roundDown(maintenant)};
            int[] faimAttendue = {4 + nutrition / 2, 4 + nutrition};
            String[] noms = {"rassis au Start", "frais au Start"};
            for (int cas = 0; cas < 2; cas++) {
                p.getFoodData().setFoodLevel(4);
                p.setItemInHand(InteractionHand.MAIN_HAND, date(Items.COOKED_BEEF, 2, datesAuDepart[cas]));
                p.startUsingItem(InteractionHand.MAIN_HAND);
                helper.assertTrue(p.isUsingItem(), noms[cas] + " : le repas devait commencer");
                manger(p, 15);
                // Le temps passe : l'aliment tenu en bouche s'avarie avant la fin du repas.
                FoodStamp.setStamp(p.getMainHandItem(), avarie());
                helper.assertTrue(p.isUsingItem(), noms[cas] + " : le repas devait être en cours");
                FoodEvents.scan(p);
                helper.assertTrue(p.getMainHandItem().is(Items.COOKED_BEEF) && p.getMainHandItem().getCount() == 2,
                        noms[cas] + " : le repas en cours ne devait pas être remplacé, vu " + p.getMainHandItem());
                manger(p, 25);
                helper.assertTrue(!p.isUsingItem(), noms[cas] + " : le repas devait être fini");
                helper.assertTrue(p.getFoodData().getFoodLevel() == faimAttendue[cas],
                        noms[cas] + " : faim " + faimAttendue[cas] + " attendue, vue " + p.getFoodData().getFoodLevel());
                helper.assertTrue(p.getMainHandItem().getCount() == 1, noms[cas] + " : un seul steak devait avoir été mangé");
                FoodEvents.scan(p);
                ItemStack reste = p.getMainHandItem();
                helper.assertTrue(reste.is(FoodRegistry.SPOILED_FOOD.get()) && reste.getCount() == 1,
                        noms[cas] + " : le reste devait devenir 1 déchet, vu " + reste);
                helper.assertTrue(total(p, Items.COOKED_BEEF, FoodRegistry.SPOILED_FOOD.get()) == 1,
                        noms[cas] + " : aucun objet ne devait apparaître : 2 de départ, 1 mangé, 1 restant");
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "peremption_sans_faim")
    public static void mangerSansFaimNeRendRienEnNegatif(GameTestHelper helper) {
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Repu");
            ServerPlayer p = j.joueur();
            ItemStack repas = date(Items.COOKED_BEEF, 1, rassis());
            // Repas rassis à pleine faim : rien n'a été donné, rien n'est repris.
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5.0F);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(p, repas, 32));
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p, repas, 0, repas));
            helper.assertTrue(p.getFoodData().getFoodLevel() == 20, "faim 20 attendue, vue " + p.getFoodData().getFoodLevel());
            helper.assertTrue(p.getFoodData().getSaturationLevel() == 5.0F,
                    "saturation 5 attendue, vue " + p.getFoodData().getSaturationLevel());
            // La faim a baissé pendant le repas : rien à reprendre, jamais de valeur négative.
            p.getFoodData().setFoodLevel(10);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(p, repas, 32));
            p.getFoodData().setFoodLevel(3);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p, repas, 0, repas));
            helper.assertTrue(p.getFoodData().getFoodLevel() == 3, "faim 3 attendue, vue " + p.getFoodData().getFoodLevel());
            // Un seul point donné : la moitié (0) est reprise, la faim ne passe pas sous le point de départ.
            p.getFoodData().setFoodLevel(0);
            p.getFoodData().setSaturation(0.0F);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Start(p, repas, 32));
            p.getFoodData().setFoodLevel(1);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p, repas, 0, repas));
            helper.assertTrue(p.getFoodData().getFoodLevel() == 1, "faim 1 attendue, vue " + p.getFoodData().getFoodLevel());
            // Un Finish sans Start n'a aucun effet.
            p.getFoodData().setFoodLevel(7);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(p, repas, 0, repas));
            helper.assertTrue(p.getFoodData().getFoodLevel() == 7, "un Finish sans Start ne change rien");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }
}
