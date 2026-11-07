package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import dev.oneforallsurvival.food.DecayState;
import dev.oneforallsurvival.food.DryerBlockEntity;
import dev.oneforallsurvival.food.DryerMenu;
import dev.oneforallsurvival.food.DryingRecipe;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodEvents;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le séchoir électrique et ses six rations, éprouvés avec les vraies recettes {@code drying/*.json} (1 200 ticks par
 * défaut). Pour ne pas attendre une minute, un test qui doit voir la fin d'un séchage sauvegarde la machine, avance la
 * progression à un tick de la fin dans la sauvegarde et la recharge : c'est le chemin même du rechargement d'un monde.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class SechoirGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private SechoirGameTests() {}

    private static DryerBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, FoodRegistry.DRYER.get());
        DryerBlockEntity be = (DryerBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity du séchoir");
        return be;
    }

    private static void alimenter(DryerBlockEntity be) {
        for (int i = 0; i < 40; i++) {
            be.energy().receiveEnergy(DryerBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static void charger(DryerBlockEntity be, ItemStack entree, ItemStack sel) {
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, entree);
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_SALT, sel);
    }

    /** Amène le séchage à un tick de sa fin, par la sauvegarde. La durée est lue avant : la lire fixe la recette. */
    private static void presqueFini(DryerBlockEntity be) {
        int total = be.data().get(DryerBlockEntity.DATA_TOTAL);
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putInt("progress", total - 1); // la clé de DryerBlockEntity.TAG_PROGRESS
        be.load(tag);
        if (be.progress() != total - 1) {
            throw new GameTestAssertException("la clé de progression de la sauvegarde est fausse : " + be.progress());
        }
    }

    private static ItemStack sortie(DryerBlockEntity be) {
        return be.inventory().getStackInSlot(DryerBlockEntity.SLOT_OUTPUT);
    }

    private static void attendre(GameTestHelper helper, int ticks, Runnable controle) {
        helper.runAfterDelay(ticks, controle);
    }

    private static void sortieAttendue(GameTestHelper helper, DryerBlockEntity be, Item attendu, String quoi) {
        ItemStack out = sortie(be);
        helper.assertTrue(out.is(attendu) && out.getCount() == 1, quoi + " : " + attendu + " attendu en sortie, vu " + out);
        helper.assertTrue(out.getTag() == null, quoi + " : le résultat ne doit porter aucune donnée, vu " + out.getTag());
    }

    // ---------------------------------------------------------------------------------------------
    // Le séchage
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leBoeufDonneDeLaViandeSechee(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 2), ItemStack.EMPTY);
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            sortieAttendue(helper, be, FoodRegistry.DRIED_MEAT.get(), "sans sel");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 1,
                    "l'entrée devait perdre un bœuf, vu " + be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT));
            // Le second bœuf sèche déjà depuis quelques ticks : la progression a bien repris de zéro, pas de 1 199.
            helper.assertTrue(be.progress() < 10, "la progression devait repartir de zéro, vue " + be.progress());
            helper.succeed();
        });
    }

    /**
     * Le séchage va à son terme par ses seuls ticks, sans rechargement : recette d'essai de {@code devcontent}
     * (une pièce d'essai, 20 ticks), qui rend les mêmes rations. Le courant se paie à chaque tick.
     */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leSechageVaAuBoutParSesTicks(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("oneforallsurvival", "test_part")), 2),
                new ItemStack(FoodRegistry.SALT.get(), 1));
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        attendre(helper, 10, () -> {
            helper.assertTrue(be.progress() > 0 && be.progress() < 20, "séchage en cours attendu, vu " + be.progress());
            helper.assertTrue(sortie(be).isEmpty(), "rien ne devait sortir avant la fin");
        });
        attendre(helper, 30, () -> {
            sortieAttendue(helper, be, FoodRegistry.SALTED_MEAT.get(), "séchage complet");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 1, "une pièce payée");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT).isEmpty(), "le sel payé");
            int consomme = avant - be.energy().getEnergyStored();
            helper.assertTrue(consomme >= 20 * FoodConfig.dryerEnergy(), "20 ticks de courant au moins, vus " + consomme);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void avecDuSelCestSale(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 1), new ItemStack(FoodRegistry.SALT.get(), 2));
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            sortieAttendue(helper, be, FoodRegistry.SALTED_MEAT.get(), "avec sel");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT).getCount() == 1,
                    "un seul sel devait être consommé, vu " + be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT));
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).isEmpty(), "le bœuf devait être payé");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void lePoissonEtLeFruit(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.COD, 1), ItemStack.EMPTY);
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            sortieAttendue(helper, be, FoodRegistry.DRIED_FISH.get(), "morue");
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            charger(be, new ItemStack(Items.SALMON, 1), new ItemStack(FoodRegistry.SALT.get(), 1));
            presqueFini(be);
        });
        attendre(helper, 14, () -> {
            sortieAttendue(helper, be, FoodRegistry.SALTED_FISH.get(), "saumon salé");
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            charger(be, new ItemStack(Items.APPLE, 1), ItemStack.EMPTY);
            presqueFini(be);
        });
        attendre(helper, 22, () -> {
            sortieAttendue(helper, be, FoodRegistry.DRIED_FRUIT.get(), "pomme");
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            charger(be, new ItemStack(Items.SWEET_BERRIES, 1), new ItemStack(FoodRegistry.SALT.get(), 1));
            presqueFini(be);
        });
        attendre(helper, 30, () -> {
            sortieAttendue(helper, be, FoodRegistry.SALTED_FRUIT.get(), "baies salées");
            helper.succeed();
        });
    }

    /** Un résultat de séchage ne garde aucune date, même si l'entrée était rassise. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void unAlimentRassisSeSecheAussi(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        long now = FoodStamp.now(helper.getLevel());
        ItemStack rassis = new ItemStack(Items.BEEF, 1);
        FoodStamp.setStamp(rassis, now - FoodConfig.freshTicks() - 1);
        helper.assertTrue(FoodStamp.state(rassis, now) == DecayState.STALE, "le bœuf d'essai devait être rassis");
        charger(be, rassis, new ItemStack(FoodRegistry.SALT.get(), 1));
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            ItemStack out = sortie(be);
            helper.assertTrue(out.is(FoodRegistry.SALTED_MEAT.get()), "le bœuf rassis devait se saler, vu " + out);
            helper.assertTrue(!FoodStamp.isStamped(out) && out.getTag() == null,
                    "la ration ne devait garder aucune date, vu " + out.getTag());
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Les rations
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leSecheEstLent(GameTestHelper helper) {
        long fraisPlusRassis = FoodConfig.freshTicks() + FoodConfig.staleTicks();
        long maintenant = fraisPlusRassis * 3;
        ItemStack viande = new ItemStack(Items.COOKED_RABBIT);
        ItemStack seche = new ItemStack(FoodRegistry.DRIED_MEAT.get());
        FoodStamp.setStamp(viande, 0);
        FoodStamp.setStamp(seche, 0);
        helper.assertTrue(FoodStamp.isPerishable(seche), "la viande séchée doit périmer, lentement");
        helper.assertTrue(FoodStamp.state(viande, maintenant) == DecayState.SPOILED,
                "le lapin cuit aurait dû être avarié, vu " + FoodStamp.state(viande, maintenant));
        helper.assertTrue(FoodStamp.state(seche, maintenant) == DecayState.FRESH
                        || FoodStamp.state(seche, maintenant) == DecayState.STALE,
                "la viande séchée ne devait pas être avariée à trois fois la durée, vu " + FoodStamp.state(seche, maintenant));
        for (Item ration : List.of(FoodRegistry.DRIED_MEAT.get(), FoodRegistry.DRIED_FISH.get(),
                FoodRegistry.DRIED_FRUIT.get())) {
            ItemStack pile = new ItemStack(ration);
            FoodStamp.setStamp(pile, 0);
            long limite = Math.round(fraisPlusRassis * FoodConfig.slowFactor());
            helper.assertTrue(FoodStamp.state(pile, limite - 1) != DecayState.SPOILED, ration + " périme trop tôt");
            helper.assertTrue(FoodStamp.state(pile, limite + 1) == DecayState.SPOILED, ration + " ne périme jamais");
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leSaleNePerimeJamais(GameTestHelper helper) {
        for (Item ration : List.of(FoodRegistry.SALTED_MEAT.get(), FoodRegistry.SALTED_FISH.get(),
                FoodRegistry.SALTED_FRUIT.get())) {
            ItemStack pile = new ItemStack(ration, 3);
            helper.assertTrue(!FoodStamp.isPerishable(pile), ration + " ne devait pas être périssable");
            long loin = FoodConfig.freshTicks() * 100000L;
            ItemStack vue = FoodEvents.observe(pile, loin);
            helper.assertTrue(vue == pile && !FoodStamp.isStamped(vue), ration + " ne devait jamais être daté ni avarié");
            helper.assertTrue(FoodStamp.state(pile, loin) == DecayState.FRESH, ration + " devait rester frais");
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leTableauDesRationsEstJuste(GameTestHelper helper) {
        record Ration(Item item, int faim) {}
        for (Ration r : List.of(new Ration(FoodRegistry.DRIED_MEAT.get(), 4), new Ration(FoodRegistry.SALTED_MEAT.get(), 4),
                new Ration(FoodRegistry.DRIED_FISH.get(), 3), new Ration(FoodRegistry.SALTED_FISH.get(), 3),
                new Ration(FoodRegistry.DRIED_FRUIT.get(), 2), new Ration(FoodRegistry.SALTED_FRUIT.get(), 2))) {
            var food = r.item().getFoodProperties(new ItemStack(r.item()), null);
            helper.assertTrue(food != null && food.getNutrition() == r.faim() && food.getSaturationModifier() == 0.4f,
                    r.item() + " : nourriture " + r.faim() + " et saturation 0.4 attendues");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Ce qui entre, ce qui reste
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void unDechetEstRefuse(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        IItemHandler dessus = handler(be, Direction.UP);
        ItemStack dechet = new ItemStack(FoodRegistry.SPOILED_FOOD.get(), 4);
        helper.assertTrue(dessus.insertItem(0, dechet.copy(), false).getCount() == 4, "le déchet ne doit pas entrer");
        // Un aliment déjà avarié mais pas encore devenu déchet n'entre pas non plus : il blanchirait en séchant.
        ItemStack avarie = new ItemStack(Items.BEEF, 1);
        FoodStamp.setStamp(avarie, FoodStamp.now(helper.getLevel()) - FoodConfig.freshTicks() - FoodConfig.staleTicks() - 1);
        helper.assertTrue(FoodStamp.state(avarie, FoodStamp.now(helper.getLevel())) == DecayState.SPOILED, "bœuf d'essai non avarié");
        helper.assertTrue(dessus.insertItem(0, avarie, false).getCount() == 1, "l'aliment avarié ne doit pas entrer");
        helper.assertTrue(dessus.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "la pierre ne doit pas entrer");
        helper.assertTrue(dessus.insertItem(0, new ItemStack(Items.BEEF), false).isEmpty(), "le bœuf frais doit entrer");
        IItemHandler cote = handler(be, Direction.NORTH);
        helper.assertTrue(cote.insertItem(0, new ItemStack(Items.BEEF), false).getCount() == 1, "le côté refuse la viande");
        helper.assertTrue(cote.insertItem(0, new ItemStack(Items.SUGAR), false).getCount() == 1, "le côté refuse le sucre");
        helper.assertTrue(cote.insertItem(0, new ItemStack(FoodRegistry.SALT.get()), false).isEmpty(), "le côté accepte le sel");
        helper.assertTrue(handler(be, Direction.DOWN).insertItem(0, new ItemStack(FoodRegistry.SALT.get()), false).getCount() == 1,
                "le dessous ne reçoit rien");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void sansCourantLaProgressionReste(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 1), ItemStack.EMPTY);
        attendre(helper, 3, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.progress() == 0, "rien ne devait avancer sans courant, vu " + be.progress());
        });
        int cout = FoodConfig.dryerEnergy();
        // Cinq ticks de courant, puis plus rien.
        attendre(helper, 5, () -> be.energy().receiveEnergy(cout * 5, false));
        attendre(helper, 20, () -> {
            helper.assertTrue(be.progress() == 5, "cinq ticks de travail attendus, vus " + be.progress());
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
        });
        attendre(helper, 40, () -> {
            helper.assertTrue(be.progress() == 5, "la progression devait attendre le courant, vue " + be.progress());
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 1
                    && sortie(be).isEmpty(), "le bœuf devait rester, sans sortie");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void laProgressionSurvitAuRechargement(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 3), new ItemStack(FoodRegistry.SALT.get(), 1));
        alimenter(be);
        attendre(helper, 8, () -> {
            helper.assertTrue(be.progress() > 0, "le séchage devait avoir avancé, vu " + be.progress());
            CompoundTag tag = be.saveWithoutMetadata();
            DryerBlockEntity copie = new DryerBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.progress() == be.progress(), "progression perdue : " + copie.progress());
            helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
            for (int i = 0; i < DryerBlockEntity.SLOTS; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement");
            }
            helper.succeed();
        });
    }

    /** Poser d'autres aliments à la souris, ou retirer la pile et la remettre à l'identique, ne coûte pas la progression. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leClicNeRemetPasLaProgressionAZero(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 1), ItemStack.EMPTY);
        alimenter(be);
        Player joueur = helper.makeMockPlayer();
        DryerMenu menu = new DryerMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        int[] avant = new int[1];
        attendre(helper, 8, () -> {
            avant[0] = be.progress();
            helper.assertTrue(avant[0] > 0, "le séchage devait avoir avancé, vu " + avant[0]);
            menu.setCarried(new ItemStack(Items.BEEF, 1));
            menu.clicked(DryerBlockEntity.SLOT_INPUT, 0, ClickType.PICKUP, joueur);
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 2,
                    "le bœuf devait être posé dans la case, vu " + be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT));
        });
        attendre(helper, 11, () -> {
            helper.assertTrue(be.progress() > avant[0], "la progression devait continuer (avant " + avant[0] + "), vue " + be.progress());
        });
        // Changer vraiment de pièce remet à zéro : un autre aliment ne profite pas du séchage du premier.
        attendre(helper, 12, () -> be.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.COD, 1)));
        attendre(helper, 16, () -> {
            helper.assertTrue(be.progress() > 0 && be.progress() < 5,
                    "la morue devait repartir de zéro, progression vue " + be.progress());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void casseIlRendSonContenu(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.BEEF, 3));
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 2));
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_MEAT.get(), 1));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, Items.BEEF) == 3, "3 bœufs attendus au sol");
            helper.assertTrue(total(objets, FoodRegistry.SALT.get()) == 2, "2 sels attendus au sol");
            helper.assertTrue(total(objets, FoodRegistry.DRIED_MEAT.get()) == 1, "1 viande séchée attendue au sol");
            helper.assertTrue(total(objets, FoodRegistry.DRYER_ITEM.get()) == 1, "le bloc lui-même devait tomber");
            helper.succeed();
        });
    }

    /** Le clic majuscule range chaque chose dans sa case, marque la machine modifiée, et ce qu'il a déplacé survit à une sauvegarde. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void leClicMajusculeFonctionne(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_INPUT, new ItemStack(Items.BEEF, 1));
        alimenter(be);
        Player joueur = helper.makeMockPlayer();
        DryerMenu menu = new DryerMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        joueur.getInventory().setItem(0, new ItemStack(Items.BEEF, 4)); // case de menu 30 : barre d'action
        joueur.getInventory().setItem(1, new ItemStack(FoodRegistry.SALT.get(), 3));
        joueur.getInventory().setItem(2, new ItemStack(Items.STONE, 3));
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
        helper.runAfterDelay(6, () -> {
            chunk.setUnsaved(false);
            helper.assertTrue(!menu.quickMoveStack(joueur, 30).isEmpty(), "le bœuf devait passer dans le séchoir");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 5,
                    "5 bœufs attendus en entrée, vus " + be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT));
            helper.assertTrue(chunk.isUnsaved(), "le clic majuscule devait marquer le séchoir modifié");
            helper.assertTrue(!menu.quickMoveStack(joueur, 31).isEmpty(), "le sel devait passer dans le séchoir");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT).getCount() == 3
                    && be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 5,
                    "le sel devait aller dans sa case et nulle part ailleurs");
            helper.assertTrue(menu.quickMoveStack(joueur, 32).isEmpty(), "la pierre ne devait pas passer");
            helper.assertTrue(joueur.getInventory().getItem(2).getCount() == 3, "le joueur devait garder sa pierre");
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_MEAT.get(), 2));
            helper.assertTrue(!menu.quickMoveStack(joueur, DryerBlockEntity.SLOT_OUTPUT).isEmpty(), "la sortie devait ressortir");
            helper.assertTrue(sortie(be).isEmpty(), "la case de sortie devait être vidée");
            helper.assertTrue(!menu.getSlot(DryerBlockEntity.SLOT_OUTPUT).mayPlace(new ItemStack(FoodRegistry.DRIED_MEAT.get())),
                    "la sortie ne reçoit rien");
        });
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(be.progress() > 0, "le séchage devait avoir avancé, vu " + be.progress());
            CompoundTag tag = be.saveWithoutMetadata();
            DryerBlockEntity copie = new DryerBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.progress() == be.progress(), "progression perdue");
            for (int i = 0; i < DryerBlockEntity.SLOTS; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement");
            }
            helper.succeed();
        });
    }

    /** Un séchoir cassé puis reposé au même endroit n'est plus celui du menu ouvert : le menu se ferme. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void unMenuSurUnSechoirRemplaceNEstPlusValide(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        Player joueur = helper.makeMockPlayer();
        var pres = helper.absoluteVec(net.minecraft.world.phys.Vec3.atCenterOf(POS.south(2)));
        joueur.moveTo(pres.x, pres.y, pres.z);
        DryerMenu menu = new DryerMenu(1, joueur.getInventory(), be, helper.absolutePos(POS));
        helper.assertTrue(menu.stillValid(joueur), "le menu devait être valide devant le séchoir en place");
        helper.getLevel().destroyBlock(helper.absolutePos(POS), false);
        helper.assertTrue(!menu.stillValid(joueur), "le menu devait se fermer quand le séchoir disparaît");
        helper.setBlock(POS, FoodRegistry.DRYER.get());
        helper.assertTrue(helper.getBlockEntity(POS) != be, "le séchoir reposé devait être une autre instance");
        helper.assertTrue(!menu.stillValid(joueur), "le menu ouvert sur l'ancien séchoir devait rester invalide");
        helper.succeed();
    }

    /** La consommation que la config permet ne dépasse jamais ce que la réserve reçoit par tick. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void laConfigNeDepasseJamaisLaReception(GameTestHelper helper) {
        Object spec = FoodConfig.SPEC.getSpec().get("dryerEnergy");
        helper.assertTrue(spec instanceof ForgeConfigSpec.ValueSpec, "réglage dryerEnergy introuvable : " + spec);
        Object max = ((ForgeConfigSpec.ValueSpec) spec).getRange().getMax();
        helper.assertTrue(max instanceof Integer m && m <= DryerBlockEntity.MAX_RECEIVE,
                "dryerEnergy doit être borné à la réception (" + DryerBlockEntity.MAX_RECEIVE + "), borne vue " + max);
        helper.assertTrue(DryerBlockEntity.MAX_RECEIVE <= DryerBlockEntity.CAPACITY, "la réception ne doit pas dépasser la réserve");
        helper.assertTrue(DryerBlockEntity.CAPACITY == 3000, "réserve du séchoir : 3000 FE, vu " + DryerBlockEntity.CAPACITY);
        helper.succeed();
    }

    /** Les recettes livrées sont chargées par le gestionnaire du serveur : au moins trois, une ration par aliment. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void lesRecettesLivreesSontChargees(GameTestHelper helper) {
        var recettes = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(FoodRegistry.DRYING_TYPE.get()).stream()
                .filter(r -> !r.getId().getPath().startsWith("drying/test_")).toList();
        helper.assertTrue(recettes.size() >= 3, "au moins trois recettes de séchage attendues, vues " + recettes.size());
        for (var r : recettes) {
            helper.assertTrue(r.resultFor(false).getCount() == 1 && r.resultFor(true).getCount() == 1,
                    r.getId() + " : une ration pour un aliment");
            helper.assertTrue(!r.resultFor(false).is(r.resultFor(true).getItem()), r.getId() + " : sec et salé distincts");
        }
        helper.succeed();
    }

    /** Sans {@code ticks}, la recette dure 1 200 ticks ; un {@code ticks} qui n'est pas un entier d'au moins 1 est rejeté. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void laDureeParDefautEstDe1200Ticks(GameTestHelper helper) {
        var serialiseur = new DryingRecipe.Serializer();
        var id = new ResourceLocation("oneforallsurvival", "drying/essai_json");
        helper.assertTrue(DryingRecipe.DEFAULT_TICKS == 1200, "durée par défaut : 1200 attendus, vus " + DryingRecipe.DEFAULT_TICKS);
        JsonObject json = recetteJson();
        helper.assertTrue(serialiseur.fromJson(id, json).ticks() == 1200,
                "sans ticks, 1200 attendus, vus " + serialiseur.fromJson(id, json).ticks());
        json.addProperty("ticks", 300);
        helper.assertTrue(serialiseur.fromJson(id, json).ticks() == 300, "ticks explicite non respecté");
        for (int invalide : new int[] {0, -5}) {
            json.addProperty("ticks", invalide);
            try {
                serialiseur.fromJson(id, json);
                throw new GameTestAssertException("ticks " + invalide + " aurait dû être rejeté");
            } catch (JsonSyntaxException attendu) {
                // rejeté : c'est le comportement voulu
            }
        }
        helper.succeed();
    }

    private static JsonObject recetteJson() {
        JsonObject json = new JsonObject();
        JsonObject entree = new JsonObject();
        entree.addProperty("item", "minecraft:beef");
        json.add("ingredient", entree);
        json.add("result", sortieJson("oneforallsurvival:dried_meat"));
        json.add("salted_result", sortieJson("oneforallsurvival:salted_meat"));
        return json;
    }

    private static JsonObject sortieJson(String item) {
        JsonObject o = new JsonObject();
        o.addProperty("item", item);
        return o;
    }

    /** Le poisson vanilla n'est pas une viande pour les loups : ses rations non plus. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void seulLaViandeEstUneViande(GameTestHelper helper) {
        for (Item ration : List.of(FoodRegistry.DRIED_MEAT.get(), FoodRegistry.SALTED_MEAT.get())) {
            helper.assertTrue(ration.getFoodProperties(new ItemStack(ration), null).isMeat(), ration + " devrait être une viande");
        }
        for (Item ration : List.of(FoodRegistry.DRIED_FISH.get(), FoodRegistry.SALTED_FISH.get(),
                FoodRegistry.DRIED_FRUIT.get(), FoodRegistry.SALTED_FRUIT.get())) {
            helper.assertTrue(!ration.getFoodProperties(new ItemStack(ration), null).isMeat(), ration + " ne devrait pas être une viande");
        }
        helper.succeed();
    }

    /**
     * Une sortie occupée par une autre ration, ou par la version opposée, bloque la machine : elle reste à l'arrêt,
     * sans courant consommé, et ni l'aliment ni le sel ne sont payés (sinon la sortie grossirait d'un autre objet).
     */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void uneSortieOccupeeBloqueLaMachine(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        // Du sel : la ration serait salée, la sortie contient la version séchée.
        charger(be, new ItemStack(Items.BEEF, 1), new ItemStack(FoodRegistry.SALT.get(), 1));
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_MEAT.get(), 1));
        alimenter(be);
        presqueFini(be);
        int energie = be.energy().getEnergyStored();
        attendre(helper, 6, () -> {
            bloquee(helper, be, FoodRegistry.DRIED_MEAT.get(), energie, true);
            // Sans sel : la ration serait séchée, la sortie contient une autre ration.
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_SALT, ItemStack.EMPTY);
            be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_FISH.get(), 1));
        });
        attendre(helper, 12, () -> {
            bloquee(helper, be, FoodRegistry.DRIED_FISH.get(), energie, false);
            helper.succeed();
        });
    }

    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);

    /** Ouvre le menu comme le fait le clic droit : par le bloc lui-même, ce qui date les cases que FoodEvents voit. */
    private static DryerMenu ouvrir(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(POS);
        helper.getLevel().getBlockState(abs).use(helper.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(p.containerMenu instanceof DryerMenu, "le menu devait s'ouvrir, vu " + p.containerMenu);
        return (DryerMenu) p.containerMenu;
    }

    /**
     * Ouvrir le menu date la ration séchée qui attend en sortie. Le résultat neuf, sans date, doit pourtant s'y
     * empiler : la sortie passe à 2, avec la date moyenne des deux, et ni l'aliment ni la machine n'attendent.
     */
    @GameTest(template = "test/plancher", batch = "sechoir_menu", setupTicks = 20, timeoutTicks = 200)
    public static void uneSortieDateeAccepteLeResultatNeuf(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 2), ItemStack.EMPTY);
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_MEAT.get(), 1));
        JoueurDeTest j = null;
        try {
            j = JoueurDeTest.rejoindre(helper.getLevel(), "Seche", helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            ouvrir(helper, j.joueur());
            helper.assertTrue(FoodStamp.isStamped(sortie(be)),
                    "l'ouverture du menu devait dater la ration en sortie, vu " + sortie(be).getTag());
        } finally {
            if (j != null && j.connecte()) {
                j.quitter();
            }
        }
        long ancienne = FoodDecay.roundDown(FoodStamp.now(helper.getLevel())) - 72000;
        FoodStamp.setStamp(sortie(be), ancienne);
        alimenter(be);
        presqueFini(be);
        attendre(helper, 8, () -> {
            ItemStack out = sortie(be);
            helper.assertTrue(out.is(FoodRegistry.DRIED_MEAT.get()) && out.getCount() == 2,
                    "2 viandes séchées attendues en sortie, vu " + out);
            long maintenant = FoodDecay.roundDown(FoodStamp.now(helper.getLevel()));
            long attendue = FoodDecay.weightedStamp(ancienne, 1, maintenant, 1);
            long attendueAvant = FoodDecay.weightedStamp(ancienne, 1, maintenant - 18000, 1); // quart d'heure franchi entre-temps
            helper.assertTrue(FoodStamp.isStamped(out) && (FoodStamp.stamp(out) == attendue || FoodStamp.stamp(out) == attendueAvant),
                    "date moyenne attendue " + attendue + ", vue " + out.getTag());
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 1,
                    "un seul bœuf devait être payé, vu " + be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT));
            helper.assertTrue(be.state() != MachineState.STOPPED || be.progress() < 10, "la machine devait avoir repris");
            helper.succeed();
        });
    }

    /** Une sortie sans date reste sans date quand le résultat s'y ajoute. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void uneSortieSansDateResteSansDate(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        charger(be, new ItemStack(Items.BEEF, 1), ItemStack.EMPTY);
        be.inventory().setStackInSlot(DryerBlockEntity.SLOT_OUTPUT, new ItemStack(FoodRegistry.DRIED_MEAT.get(), 1));
        alimenter(be);
        presqueFini(be);
        attendre(helper, 6, () -> {
            ItemStack out = sortie(be);
            helper.assertTrue(out.is(FoodRegistry.DRIED_MEAT.get()) && out.getCount() == 2 && out.getTag() == null,
                    "2 viandes séchées sans date attendues, vu " + out + " " + out.getTag());
            helper.succeed();
        });
    }

    /** Un aliment qui avarie pendant le séchage arrête la machine : il n'est pas changé en ration, rien n'est perdu. */
    @GameTest(template = "test/plancher", batch = "sechoir")
    public static void unAlimentQuiAvarieEnRouteArreteLaMachine(GameTestHelper helper) {
        DryerBlockEntity be = poser(helper);
        long now = FoodStamp.now(helper.getLevel());
        ItemStack boeuf = new ItemStack(Items.BEEF, 1);
        // Rassis, et avarié une trentaine de ticks plus tard.
        FoodStamp.setStamp(boeuf, now - FoodConfig.freshTicks() - FoodConfig.staleTicks() + 30);
        helper.assertTrue(FoodStamp.state(boeuf, now) == DecayState.STALE, "le bœuf d'essai devait commencer rassis");
        charger(be, boeuf, new ItemStack(FoodRegistry.SALT.get(), 1));
        alimenter(be);
        int[] releve = new int[2];
        attendre(helper, 6, () -> helper.assertTrue(be.state() == MachineState.RUNNING && be.progress() > 0,
                "le séchage devait avoir commencé, état " + be.state()));
        attendre(helper, 50, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu une fois avarié, vu " + be.state());
            releve[0] = be.progress();
            releve[1] = be.energy().getEnergyStored();
        });
        attendre(helper, 80, () -> {
            helper.assertTrue(be.progress() == releve[0] && be.energy().getEnergyStored() == releve[1],
                    "plus rien ne devait avancer ni se consommer");
            helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).is(Items.BEEF)
                    && be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT).getCount() == 1
                    && sortie(be).isEmpty(), "le bœuf et le sel devaient rester, sans sortie");
            helper.succeed();
        });
    }

    private static void bloquee(GameTestHelper helper, DryerBlockEntity be, Item occupant, int energie, boolean sel) {
        ItemStack out = sortie(be);
        helper.assertTrue(out.is(occupant) && out.getCount() == 1, "la sortie devait rester intacte (" + occupant + "), vue " + out);
        helper.assertTrue(be.inventory().getStackInSlot(DryerBlockEntity.SLOT_INPUT).getCount() == 1, "l'aliment ne devait pas être payé");
        helper.assertTrue(!sel || be.inventory().getStackInSlot(DryerBlockEntity.SLOT_SALT).getCount() == 1, "le sel ne devait pas être payé");
        helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
        helper.assertTrue(be.energy().getEnergyStored() == energie, "aucun courant ne devait être consommé");
    }

    private static int total(List<ItemEntity> objets, Item item) {
        return objets.stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static IItemHandler handler(DryerBlockEntity be, Direction side) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(
                () -> new GameTestAssertException("pas de capability objets au côté " + side));
    }
}
