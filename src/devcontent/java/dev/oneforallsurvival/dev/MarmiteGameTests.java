package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.CanteenPotBlockEntity;
import dev.oneforallsurvival.food.CanteenPotMenu;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import vectorwing.farmersdelight.common.registry.ModItems;

/**
 * La marmite de cantine, éprouvée avec les vraies recettes du Cooking Pot de Farmer's Delight : le ragoût de bœuf
 * (trois ingrédients, un bol, 200 ticks) et le chocolat chaud (un seau de lait, qui laisse un seau vide). Pour ne pas
 * attendre dix secondes, un test qui doit voir la fin d'une cuisson sauvegarde la machine, avance la progression à un
 * tick de la fin dans la sauvegarde et la recharge : c'est le chemin même du rechargement d'un monde.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MarmiteGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    /** Index, dans le menu, de la première case de la barre d'action du joueur (8 cases de marmite, 27 de sac). */
    private static final int HOTBAR_0 = CanteenPotBlockEntity.SLOTS + 27;

    private MarmiteGameTests() {}

    private static CanteenPotBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, FoodRegistry.CANTEEN_POT.get());
        CanteenPotBlockEntity be = (CanteenPotBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la marmite");
        return be;
    }

    private static void alimenter(CanteenPotBlockEntity be) {
        for (int i = 0; i < 40; i++) {
            be.energy().receiveEnergy(CanteenPotBlockEntity.MAX_RECEIVE, false);
        }
    }

    /** Les trois ingrédients du ragoût de bœuf, {@code jeux} fois, et {@code bols} bols dans la case du récipient. */
    private static void chargerRagout(CanteenPotBlockEntity be, int jeux, int bols) {
        be.inventory().setStackInSlot(0, new ItemStack(Items.BEEF, jeux));
        be.inventory().setStackInSlot(1, new ItemStack(Items.CARROT, jeux));
        be.inventory().setStackInSlot(2, new ItemStack(Items.POTATO, jeux));
        be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER,
                bols == 0 ? ItemStack.EMPTY : new ItemStack(Items.BOWL, bols));
    }

    /** Fixe la progression dans la sauvegarde, puis recharge. La durée est lue avant : la lire fixe la recette suivie. */
    private static void fixerProgression(CanteenPotBlockEntity be, int progression) {
        be.data().get(CanteenPotBlockEntity.DATA_TOTAL);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt("progress", progression); // la clé de CanteenPotBlockEntity.TAG_PROGRESS
        be.load(tag);
        if (be.progress() != progression) {
            throw new GameTestAssertException("la clé de progression de la sauvegarde est fausse : " + be.progress());
        }
    }

    private static void presqueFini(CanteenPotBlockEntity be) {
        fixerProgression(be, be.data().get(CanteenPotBlockEntity.DATA_TOTAL) - 1);
    }

    private static ItemStack sortie(CanteenPotBlockEntity be) {
        return be.inventory().getStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT);
    }

    private static ItemStack case_(CanteenPotBlockEntity be, int slot) {
        return be.inventory().getStackInSlot(slot);
    }

    private static void attendre(GameTestHelper helper, int ticks, Runnable controle) {
        helper.runAfterDelay(ticks, controle);
    }

    private static Item ragout() {
        return ModItems.BEEF_STEW.get();
    }

    private static int total(List<ItemEntity> objets, Item item) {
        return objets.stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static IItemHandler handler(CanteenPotBlockEntity be, Direction side) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(
                () -> new GameTestAssertException("pas de capability objets au côté " + side));
    }

    // ---------------------------------------------------------------------------------------------
    // La cuisson, l'attente, la progression
    // ---------------------------------------------------------------------------------------------

    /** Un seul jeu d'ingrédients et trois bols payés donnent trois ragoûts. */
    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void leRagoutDonneTroisPortions(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        helper.assertTrue(FoodConfig.canteenPotPortions() == 3, "trois portions par défaut attendues");
        chargerRagout(be, 2, 5);
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            helper.assertTrue(sortie(be).is(ragout()) && sortie(be).getCount() == 3,
                    "trois ragoûts attendus en sortie, vu " + sortie(be));
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(case_(be, slot).getCount() == 1,
                        "un seul jeu d'ingrédients devait être payé, case " + slot + " : " + case_(be, slot));
            }
            helper.assertTrue(case_(be, CanteenPotBlockEntity.SLOT_CONTAINER).is(Items.BOWL)
                            && case_(be, CanteenPotBlockEntity.SLOT_CONTAINER).getCount() == 2,
                    "trois bols payés sur cinq attendus, vus " + case_(be, CanteenPotBlockEntity.SLOT_CONTAINER));
            helper.assertTrue(be.progress() == 0, "la progression devait repartir de zéro, vue " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void sansBolsElleAttend(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 1, 0);
        alimenter(be);
        int energie = be.energy().getEnergyStored();
        attendre(helper, 8, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu faute de bols, vu " + be.state());
            helper.assertTrue(be.progress() == 0, "rien ne devait avancer faute de bols, vu " + be.progress());
            helper.assertTrue(be.containersNeeded() == 3, "trois bols exigés, vus " + be.containersNeeded());
            helper.assertTrue(be.data().get(CanteenPotBlockEntity.DATA_CONTAINERS_MISSING) == 3,
                    "trois bols manquants attendus, vus " + be.data().get(CanteenPotBlockEntity.DATA_CONTAINERS_MISSING));
            helper.assertTrue(be.energy().getEnergyStored() == energie, "aucun courant ne devait être consommé");
            be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.BOWL, 3));
        });
        attendre(helper, 14, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avec trois bols, vu " + be.state());
            helper.assertTrue(be.progress() > 0, "la cuisson devait avancer, vue " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void avecDeuxBolsSurTroisElleAttend(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 1, 2);
        alimenter(be);
        attendre(helper, 2, () -> fixerProgression(be, 50));
        attendre(helper, 12, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu avec deux bols, vu " + be.state());
            helper.assertTrue(be.progress() == 50, "la progression devait être gardée, vue " + be.progress());
            helper.assertTrue(be.data().get(CanteenPotBlockEntity.DATA_CONTAINERS_MISSING) == 1,
                    "un bol manquant attendu, vu " + be.data().get(CanteenPotBlockEntity.DATA_CONTAINERS_MISSING));
            helper.assertTrue(sortie(be).isEmpty() && case_(be, 0).getCount() == 1, "rien ne devait être payé");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void sortieTropPleineElleAttend(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 1, 3);
        alimenter(be);
        ItemStack presquePleine = new ItemStack(ragout(), ragout().getMaxStackSize(new ItemStack(ragout())) - 1);
        be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, presquePleine.copy());
        attendre(helper, 2, () -> fixerProgression(be, 50));
        attendre(helper, 12, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu sortie pleine, vu " + be.state());
            helper.assertTrue(be.progress() == 50, "la progression devait être gardée, vue " + be.progress());
            helper.assertTrue(sortie(be).getCount() == presquePleine.getCount(), "la sortie ne devait pas changer");
            helper.assertTrue(case_(be, 0).getCount() == 1, "les ingrédients devaient rester");
            // Une place faite, la cuisson reprend là où elle en était.
            be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
        });
        attendre(helper, 18, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING && be.progress() > 50,
                    "la cuisson devait reprendre au-delà de 50, vue " + be.progress() + " " + be.state());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void leCourantCoupeGardeLaProgression(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 1, 3);
        attendre(helper, 3, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.progress() == 0, "rien ne devait avancer sans courant, vu " + be.progress());
        });
        int cout = FoodConfig.canteenPotEnergy();
        // Cinq ticks de courant, puis plus rien.
        attendre(helper, 5, () -> be.energy().receiveEnergy(cout * 5, false));
        attendre(helper, 20, () -> {
            helper.assertTrue(be.progress() == 5, "cinq ticks de travail attendus, vus " + be.progress());
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
        });
        attendre(helper, 40, () -> {
            helper.assertTrue(be.progress() == 5, "la progression devait attendre le courant, vue " + be.progress());
            helper.assertTrue(case_(be, 0).getCount() == 1 && sortie(be).isEmpty(), "les ingrédients devaient rester, sans sortie");
            helper.succeed();
        });
    }

    /** Une autre recette ne profite pas de la cuisson de la première. */
    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void laRecetteChangeeRepartDeZero(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 1, 3);
        alimenter(be);
        attendre(helper, 2, () -> fixerProgression(be, 100));
        attendre(helper, 5, () -> {
            helper.assertTrue(be.progress() > 100, "le ragoût devait avoir avancé au-delà de 100, vu " + be.progress());
            // Le chocolat chaud prend la place : seau de lait, sucre, deux fèves, et ses bouteilles.
            be.inventory().setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
            be.inventory().setStackInSlot(1, new ItemStack(Items.SUGAR));
            be.inventory().setStackInSlot(2, new ItemStack(Items.COCOA_BEANS));
            be.inventory().setStackInSlot(3, new ItemStack(Items.COCOA_BEANS));
            be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.GLASS_BOTTLE, 3));
        });
        attendre(helper, 9, () -> {
            helper.assertTrue(be.progress() > 0 && be.progress() < 10,
                    "le chocolat chaud devait repartir de zéro, progression vue " + be.progress());
            helper.succeed();
        });
    }

    /** Poser d'autres ingrédients à la souris, ou retirer la pile et la remettre à l'identique, ne coûte pas la progression. */
    @GameTest(template = "test/plancher", batch = "marmite", setupTicks = 20, timeoutTicks = 200)
    public static void leClicNeRemetPasLaProgressionAZero(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 2, 3);
        alimenter(be);
        Player joueur = helper.makeMockPlayer();
        CanteenPotMenu menu = new CanteenPotMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        int[] avant = new int[1];
        attendre(helper, 8, () -> {
            avant[0] = be.progress();
            helper.assertTrue(avant[0] > 0, "la cuisson devait avoir avancé, vu " + avant[0]);
            menu.setCarried(new ItemStack(Items.BEEF, 1));
            menu.clicked(0, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(case_(be, 0).getCount() == 3, "le bœuf devait être posé dans la case, vu " + case_(be, 0));
            // Prendre la pile entière puis la reposer : la case est vide un instant, dans le même tick.
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(1, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(case_(be, 1).isEmpty() && menu.getCarried().is(Items.CARROT), "la pile devait être en main");
            menu.clicked(1, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(case_(be, 1).getCount() == 2, "la pile devait être reposée, vue " + case_(be, 1));
        });
        attendre(helper, 11, () -> {
            helper.assertTrue(be.progress() > avant[0], "la progression devait continuer (avant " + avant[0] + "), vue " + be.progress());
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Ce qui entre, ce qui reste, ce qui survit
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void unIngredientDateEstAccepte(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        IItemHandler dessus = handler(be, Direction.UP);
        ItemStack date = new ItemStack(Items.BEEF, 1);
        FoodStamp.setStamp(date, FoodStamp.now(helper.getLevel()));
        helper.assertTrue(FoodStamp.isStamped(date), "le bœuf d'essai devait être daté");
        helper.assertTrue(dessus.insertItem(0, date, false).isEmpty(), "le bœuf daté mais frais doit entrer");
        helper.assertTrue(dessus.insertItem(1, new ItemStack(Items.STONE), false).getCount() == 1, "la pierre ne doit pas entrer");
        helper.assertTrue(dessus.getSlots() == CanteenPotBlockEntity.INPUTS, "le dessus ne voit que les six cases d'ingrédients");
        // Le dessus ne reçoit pas de bol, les côtés ne reçoivent que lui, le dessous ne reçoit rien.
        helper.assertTrue(dessus.insertItem(1, new ItemStack(Items.BOWL), false).getCount() == 1, "le dessus refuse le bol");
        IItemHandler cote = handler(be, Direction.NORTH);
        helper.assertTrue(cote.insertItem(0, new ItemStack(Items.BEEF), false).getCount() == 1, "le côté refuse la viande");
        helper.assertTrue(cote.insertItem(0, new ItemStack(Items.BOWL), false).isEmpty(), "le côté accepte le bol");
        helper.assertTrue(handler(be, Direction.DOWN).insertItem(0, new ItemStack(Items.BOWL), false).getCount() == 1,
                "le dessous ne reçoit rien");
        be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, new ItemStack(ragout(), 2));
        helper.assertTrue(handler(be, Direction.DOWN).extractItem(0, 5, false).getCount() == 2, "le dessous extrait la sortie");
        helper.assertTrue(handler(be, Direction.UP).extractItem(0, 5, false).isEmpty(), "le dessus n'extrait rien");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void unIngredientAvarieEstRefuse(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        IItemHandler dessus = handler(be, Direction.UP);
        long now = FoodStamp.now(helper.getLevel());
        ItemStack avarie = new ItemStack(Items.BEEF, 1);
        FoodStamp.setStamp(avarie, now - FoodConfig.freshTicks() - FoodConfig.staleTicks() - 1);
        helper.assertTrue(FoodStamp.state(avarie, now) == dev.oneforallsurvival.food.DecayState.SPOILED, "bœuf d'essai non avarié");
        helper.assertTrue(dessus.insertItem(0, avarie.copy(), false).getCount() == 1, "l'aliment avarié ne doit pas entrer");
        helper.assertTrue(dessus.insertItem(0, new ItemStack(FoodRegistry.SPOILED_FOOD.get()), false).getCount() == 1,
                "le déchet ne doit pas entrer");
        // Celui qui s'avarie en route n'est pas cuit : la machine attend, sans toucher à la progression.
        chargerRagout(be, 1, 3);
        be.inventory().setStackInSlot(0, avarie);
        alimenter(be);
        attendre(helper, 8, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu avec un ingrédient avarié, vu " + be.state());
            helper.assertTrue(be.progress() == 0 && sortie(be).isEmpty(), "rien ne devait cuire");
            helper.succeed();
        });
    }

    /** Une recette au seau de lait : le seau vide ne disparaît pas, il tombe au-dessus de la marmite. */
    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void leResteDUnIngredientEstRendu(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, new ItemStack(Items.MILK_BUCKET));
        be.inventory().setStackInSlot(1, new ItemStack(Items.SUGAR, 2));
        be.inventory().setStackInSlot(2, new ItemStack(Items.COCOA_BEANS, 2));
        be.inventory().setStackInSlot(3, new ItemStack(Items.COCOA_BEANS, 2));
        be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_CONTAINER, new ItemStack(Items.GLASS_BOTTLE, 3));
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            helper.assertTrue(sortie(be).is(ModItems.HOT_COCOA.get()) && sortie(be).getCount() == 3,
                    "trois chocolats chauds attendus, vu " + sortie(be));
            helper.assertTrue(case_(be, 0).isEmpty(), "le seau de lait devait être payé");
            helper.assertTrue(case_(be, 1).getCount() == 1 && case_(be, 2).getCount() == 1 && case_(be, 3).getCount() == 1,
                    "un de chaque autre ingrédient devait être payé");
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, Items.BUCKET) == 1, "le seau vide devait tomber, vu " + objets);
            helper.assertTrue(total(objets, Items.MILK_BUCKET) == 0, "le seau plein ne devait pas tomber");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void laProgressionSurvitAuRechargement(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 3, 3);
        alimenter(be);
        attendre(helper, 8, () -> {
            helper.assertTrue(be.progress() > 0, "la cuisson devait avoir avancé, vu " + be.progress());
            CompoundTag tag = be.saveWithoutMetadata();
            CanteenPotBlockEntity copie = new CanteenPotBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.progress() == be.progress(), "progression perdue : " + copie.progress());
            helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
            for (int i = 0; i < CanteenPotBlockEntity.SLOTS; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement");
            }
            // Recharger la machine en place, puis la laisser tourner : la recette suivie revient avec la progression.
            int sauvee = be.progress();
            be.load(tag);
            helper.assertTrue(be.progress() == sauvee, "progression changée par le rechargement");
        });
        attendre(helper, 12, () -> {
            helper.assertTrue(be.progress() > 8, "la cuisson devait reprendre là où elle en était, vue " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void casseElleRendSonContenu(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        chargerRagout(be, 3, 4);
        be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, new ItemStack(ragout(), 2));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, Items.BEEF) == 3, "3 bœufs attendus au sol");
            helper.assertTrue(total(objets, Items.CARROT) == 3, "3 carottes attendues au sol");
            helper.assertTrue(total(objets, Items.POTATO) == 3, "3 pommes de terre attendues au sol");
            helper.assertTrue(total(objets, Items.BOWL) == 4, "4 bols attendus au sol");
            helper.assertTrue(total(objets, ragout()) == 2, "2 ragoûts attendus au sol");
            helper.succeed();
        });
    }

    /** Le clic majuscule range chaque chose dans sa case, marque la machine modifiée, et ce qu'il a déplacé survit à une sauvegarde. */
    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void leClicMajusculeFonctionne(GameTestHelper helper) {
        CanteenPotBlockEntity be = poser(helper);
        alimenter(be);
        Player joueur = helper.makeMockPlayer();
        CanteenPotMenu menu = new CanteenPotMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        joueur.getInventory().setItem(0, new ItemStack(Items.BEEF, 4));
        joueur.getInventory().setItem(1, new ItemStack(Items.BOWL, 6));
        joueur.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
        // Quatre seaux de lait ne s'empilent pas : chacun doit trouver sa propre case.
        joueur.getInventory().setItem(3, new ItemStack(Items.MILK_BUCKET, 1));
        joueur.getInventory().setItem(4, new ItemStack(Items.MILK_BUCKET, 1));
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
        helper.runAfterDelay(6, () -> {
            chunk.setUnsaved(false);
            helper.assertTrue(!menu.quickMoveStack(joueur, HOTBAR_0).isEmpty(), "le bœuf devait passer dans la marmite");
            helper.assertTrue(case_(be, 0).is(Items.BEEF) && case_(be, 0).getCount() == 4, "4 bœufs attendus en première case");
            helper.assertTrue(chunk.isUnsaved(), "le clic majuscule devait marquer la marmite modifiée");
            helper.assertTrue(!menu.quickMoveStack(joueur, HOTBAR_0 + 1).isEmpty(), "les bols devaient passer dans la marmite");
            helper.assertTrue(case_(be, CanteenPotBlockEntity.SLOT_CONTAINER).is(Items.BOWL)
                            && case_(be, CanteenPotBlockEntity.SLOT_CONTAINER).getCount() == 6,
                    "les bols devaient aller dans leur case, vus " + case_(be, CanteenPotBlockEntity.SLOT_CONTAINER));
            helper.assertTrue(menu.quickMoveStack(joueur, HOTBAR_0 + 2).isEmpty(), "la pierre ne devait pas passer");
            helper.assertTrue(joueur.getInventory().getItem(2).getCount() == 3, "le joueur devait garder sa pierre");
            helper.assertTrue(!menu.quickMoveStack(joueur, HOTBAR_0 + 3).isEmpty()
                            && !menu.quickMoveStack(joueur, HOTBAR_0 + 4).isEmpty(),
                    "les deux seaux devaient passer");
            helper.assertTrue(case_(be, 1).is(Items.MILK_BUCKET) && case_(be, 2).is(Items.MILK_BUCKET),
                    "chaque seau devait prendre sa case, vus " + case_(be, 1) + " et " + case_(be, 2));
            be.inventory().setStackInSlot(CanteenPotBlockEntity.SLOT_OUTPUT, new ItemStack(ragout(), 2));
            helper.assertTrue(!menu.quickMoveStack(joueur, CanteenPotBlockEntity.SLOT_OUTPUT).isEmpty(), "la sortie devait ressortir");
            helper.assertTrue(sortie(be).isEmpty(), "la case de sortie devait être vidée");
            helper.assertTrue(!menu.getSlot(CanteenPotBlockEntity.SLOT_OUTPUT).mayPlace(new ItemStack(ragout())),
                    "la sortie ne reçoit rien");
            CompoundTag tag = be.saveWithoutMetadata();
            CanteenPotBlockEntity copie = new CanteenPotBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            for (int i = 0; i < CanteenPotBlockEntity.SLOTS; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement");
            }
            helper.succeed();
        });
    }

    /** La consommation que la config permet ne dépasse jamais ce que la réserve reçoit par tick ; les portions restent bornées. */
    @GameTest(template = "test/plancher", batch = "marmite2", setupTicks = 20, timeoutTicks = 200)
    public static void laConfigDeLaMarmiteEstBornee(GameTestHelper helper) {
        Object spec = FoodConfig.SPEC.getSpec().get("canteenPotEnergy");
        helper.assertTrue(spec instanceof ForgeConfigSpec.ValueSpec, "réglage canteenPotEnergy introuvable : " + spec);
        Object max = ((ForgeConfigSpec.ValueSpec) spec).getRange().getMax();
        helper.assertTrue(max instanceof Integer m && m <= CanteenPotBlockEntity.MAX_RECEIVE,
                "canteenPotEnergy doit être borné à la réception (" + CanteenPotBlockEntity.MAX_RECEIVE + "), borne vue " + max);
        helper.assertTrue(FoodConfig.canteenPotEnergy() == 30, "30 FE par tick par défaut, vus " + FoodConfig.canteenPotEnergy());
        helper.assertTrue(CanteenPotBlockEntity.CAPACITY == 6000 && CanteenPotBlockEntity.MAX_RECEIVE == 200,
                "réserve 6000 FE et réception 200 FE attendues");
        Object portions = FoodConfig.SPEC.getSpec().get("canteenPotPortions");
        helper.assertTrue(portions instanceof ForgeConfigSpec.ValueSpec, "réglage canteenPotPortions introuvable");
        ForgeConfigSpec.Range<?> bornes = ((ForgeConfigSpec.ValueSpec) portions).getRange();
        helper.assertTrue(Integer.valueOf(2).equals(bornes.getMin()) && Integer.valueOf(16).equals(bornes.getMax()),
                "portions entre 2 et 16 attendues, vues " + bornes.getMin() + " et " + bornes.getMax());
        helper.succeed();
    }
}
