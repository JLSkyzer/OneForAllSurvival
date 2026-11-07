package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodDecay;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.FoodStamp;
import dev.oneforallsurvival.food.FridgeBlockEntity;
import dev.oneforallsurvival.food.FridgeMenu;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * L'armoire frigorifique. Une zone déchargée se simule en reculant {@code lastTick} puis en rechargeant le
 * block entity depuis son propre NBT : c'est le chemin que prend un vrai chargement de chunk. Tout GameTest qui
 * exige que l'armoire ait tourné attend {@code setupTicks = 20}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ArmoireGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);
    /** Une date assez ancienne pour que le froid puisse la décaler sans dépasser maintenant, et encore fraîche. */
    private static final long ANCIENNE = -100000;

    private ArmoireGameTests() {}

    private static FridgeBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, FoodRegistry.FRIDGE.get());
        FridgeBlockEntity be = (FridgeBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de l'armoire");
        return be;
    }

    private static void alimenter(FridgeBlockEntity be) {
        for (int i = 0; i < 20; i++) {
            be.energy().receiveEnergy(100, false);
        }
    }

    private static ItemStack date(Item item, int nombre, long date) {
        ItemStack pile = new ItemStack(item, nombre);
        FoodStamp.setStamp(pile, date);
        return pile;
    }

    private static long maintenant(GameTestHelper helper) {
        return helper.getLevel().getGameTime();
    }

    /** Une date qui fait de l'aliment un aliment avarié maintenant. */
    private static long avarie() {
        return -(FoodConfig.freshTicks() + FoodConfig.staleTicks()) - 1000;
    }

    /**
     * Fait revivre l'armoire comme si elle avait été déchargée {@code ecart} ticks : {@code lastTick} recule, le
     * NBT est écrit puis relu. L'armoire doit avoir tourné au tick précédent pour avoir « été froide ».
     */
    private static void dechargerPuisRecharger(GameTestHelper helper, FridgeBlockEntity be, long ecart) {
        helper.assertTrue(be.state() == MachineState.RUNNING || be.state() == MachineState.NO_POWER,
                "l'armoire devait avoir tourné avant la décharge, état " + be.state());
        be.debugSetLastTick(maintenant(helper) - ecart);
        CompoundTag tag = be.saveWithoutMetadata();
        be.load(tag);
    }

    private static IItemHandler handler(FridgeBlockEntity be, Direction face) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElseThrow(
                () -> new AssertionError("pas de capability d'objets sur la face " + face));
    }

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

    /** Ouvre l'armoire comme le fait le clic droit : par le bloc lui-même. */
    private static FridgeMenu ouvrir(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(POS);
        helper.getLevel().getBlockState(abs).use(helper.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(p.containerMenu instanceof FridgeMenu, "le menu de l'armoire devait s'ouvrir, vu " + p.containerMenu);
        return (FridgeMenu) p.containerMenu;
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20, timeoutTicks = 200)
    public static void alimenteeLeFroidAvance(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 4, ANCIENNE));
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.assertTrue(avant == 2000, "2000 FE attendus à la charge pleine, vus " + avant);
        helper.runAfterDelay(100, () -> {
            long credit = be.coldCredit();
            helper.assertTrue(credit >= 74 && credit <= 75, "74 à 75 de crédit après 100 ticks, vu " + credit);
            helper.assertTrue(be.coldFraction() == 1 - 1 / FoodConfig.fridgeFactor(), "fraction de froid inattendue");
            int consomme = avant - be.energy().getEnergyStored();
            helper.assertTrue(consomme >= 396 && consomme <= 400, "4 FE par tick : 396 à 400 attendus, vus " + consomme);
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void leCreditDecaleLesDates(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 4, ANCIENNE));
        be.inventory().setStackInSlot(1, date(Items.COD, 2, ANCIENNE - 18000));
        be.inventory().setStackInSlot(2, new ItemStack(Items.STONE, 3));
        alimenter(be);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la décharge, vu " + be.state());
            dechargerPuisRecharger(helper, be, 32000);
        });
        helper.runAfterDelay(16, () -> {
            // 32000 ticks à 0,75 : 24000 de crédit, soit un quart d'heure (18000) de décalage et 6000 de reste.
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(0)) == ANCIENNE + FoodDecay.BUCKET_TICKS,
                    "la date du boeuf devait avancer d'un quart d'heure, vue " + FoodStamp.stamp(be.inventory().getStackInSlot(0)));
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(1)) == ANCIENNE,
                    "la date du poisson devait avancer d'un quart d'heure");
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 4 && be.inventory().getStackInSlot(1).getCount() == 2,
                    "les nombres ne devaient pas bouger");
            helper.assertTrue(!FoodStamp.isStamped(be.inventory().getStackInSlot(2)), "la pierre ne reçoit pas de date");
            long credit = be.coldCredit();
            helper.assertTrue(credit >= 6000 && credit <= 6030, "6000 de reste attendus, vus " + credit);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void leFroidNeDepassePasMaintenant(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        long recent = maintenant(helper) - 5000;
        be.inventory().setStackInSlot(0, date(Items.BEEF, 4, recent));
        alimenter(be);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la décharge, vu " + be.state());
            dechargerPuisRecharger(helper, be, 32000);
        });
        helper.runAfterDelay(16, () -> {
            long date = FoodStamp.stamp(be.inventory().getStackInSlot(0));
            helper.assertTrue(date > recent, "la date devait avancer, vue " + date);
            helper.assertTrue(date <= maintenant(helper), "la date ne dépasse jamais maintenant : " + date + " contre " + maintenant(helper));
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void quatreFoisPlusLentement(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        long duree = 200000;
        long depart = maintenant(helper) - duree;
        ItemStack temoin = date(Items.BEEF, 1, depart);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 1, depart));
        alimenter(be);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la décharge, vu " + be.state());
            dechargerPuisRecharger(helper, be, duree);
        });
        helper.runAfterDelay(16, () -> {
            long now = maintenant(helper);
            long ageTemoin = now - FoodStamp.stamp(temoin);
            long ageArmoire = now - FoodStamp.stamp(be.inventory().getStackInSlot(0));
            long attendu = ageTemoin / 4;
            helper.assertTrue(Math.abs(ageArmoire - attendu) <= FoodDecay.BUCKET_TICKS,
                    "un quart de l'âge du témoin (" + attendu + ") à un quart d'heure près, vu " + ageArmoire);
            helper.assertTrue(ageArmoire < ageTemoin / 2, "l'aliment de l'armoire devait être bien plus jeune que le témoin");
            helper.succeed();
        });
    }

    /** Le scénario du cahier des charges : une coupure ne coûte rien sur le coup, le froid s'arrête simplement. */
    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantRienNeSePerd(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 12, ANCIENNE));
        be.inventory().setStackInSlot(5, date(Items.COD, 5, ANCIENNE - 18000));
        be.energy().receiveEnergy(40, false); // dix ticks de travail à 4 FE, puis plus rien
        long[] vu = new long[4];
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            vu[0] = FoodStamp.stamp(be.inventory().getStackInSlot(0));
            vu[1] = FoodStamp.stamp(be.inventory().getStackInSlot(5));
            vu[2] = be.coldCredit();
            helper.assertTrue(vu[2] >= 6 && vu[2] <= 8, "dix ticks de froid : 7,5 de crédit attendus, vus " + vu[2]);
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 12 && be.inventory().getStackInSlot(5).getCount() == 5,
                    "les nombres devaient rester");
        });
        helper.runAfterDelay(130, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu encore, vu " + be.state());
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(0)) == vu[0]
                    && FoodStamp.stamp(be.inventory().getStackInSlot(5)) == vu[1],
                    "aucune date ne devait bouger sans courant (ni en avant ni en arrière)");
            helper.assertTrue(be.coldCredit() == vu[2], "le crédit devait rester stable, vu " + be.coldCredit() + " contre " + vu[2]);
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 12 && be.inventory().getStackInSlot(5).getCount() == 5,
                    "rien ne devait se perdre à la coupure");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void dechargeeElleRattrapeSiElleEtaitFroide(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 4, ANCIENNE));
        alimenter(be);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la décharge, vu " + be.state());
            dechargerPuisRecharger(helper, be, 1000);
        });
        int[] energie = new int[1];
        helper.runAfterDelay(13, () -> energie[0] = be.energy().getEnergyStored());
        helper.runAfterDelay(18, () -> {
            // 1000 ticks à 0,75 : 750 de crédit, pas encore de quart d'heure, et le rattrapage ne coûte aucune énergie.
            long credit = be.coldCredit();
            helper.assertTrue(credit >= 750 && credit <= 790, "750 de crédit rattrapé attendus, vus " + credit);
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(0)) == ANCIENNE, "pas encore de décalage");
            int paye = energie[0] - be.energy().getEnergyStored();
            helper.assertTrue(paye <= 4 * 5, "le rattrapage ne devait rien payer, payé " + paye);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void dechargeeElleNeRattrapePasSiElleEtaitCoupee(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 4, ANCIENNE));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            dechargerPuisRecharger(helper, be, 32000);
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(be.coldCredit() == 0, "coupée, elle ne rattrape rien, vu " + be.coldCredit());
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(0)) == ANCIENNE, "la date ne devait pas bouger");
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 4, "rien ne devait se perdre");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20, timeoutTicks = 200)
    public static void uneTremieYDepose(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        for (Direction face : Direction.values()) {
            helper.assertTrue(handler(be, face).getSlots() == 27, "27 cases attendues sur la face " + face);
        }
        helper.assertTrue(be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(), "pas de handler sans face");
        ItemStack reste = handler(be, Direction.UP).insertItem(0, new ItemStack(Items.BEEF, 3), false);
        helper.assertTrue(reste.isEmpty(), "la trémie devait tout déposer, reste " + reste);
        helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 3, "3 boeufs attendus");
        ItemStack sorti = handler(be, Direction.DOWN).extractItem(0, 1, true);
        helper.assertTrue(sorti.getCount() == 1, "une trémie sous l'armoire devait pouvoir extraire");
        helper.runAfterDelay(105, () -> {
            helper.assertTrue(FoodStamp.isStamped(be.inventory().getStackInSlot(0)),
                    "l'objet reçu devait être daté en moins de 100 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20, timeoutTicks = 200)
    public static void lAvarieYDevientDechet(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(3, date(Items.BEEF, 6, avarie()));
        be.inventory().setStackInSlot(4, date(Items.COD, 2, ANCIENNE));
        helper.runAfterDelay(105, () -> {
            ItemStack pile = be.inventory().getStackInSlot(3);
            helper.assertTrue(pile.is(FoodRegistry.SPOILED_FOOD.get()) && pile.getCount() == 6,
                    "6 déchets attendus sans menu ouvert, vu " + pile);
            helper.assertTrue(be.inventory().getStackInSlot(4).is(Items.COD), "le poisson sain devait rester");
            helper.succeed();
        });
    }

    /** À l'ouverture : les cases du menu sont des cases simples, donc le déchet y remplace bien l'avarié. */
    @GameTest(template = "test/plancher", batch = "armoire_menu", setupTicks = 20)
    public static void lAvarieDevientDechetALOuverture(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(3, date(Items.BEEF, 6, avarie()));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Frigoriste");
            ouvrir(helper, j.joueur());
            ItemStack pile = be.inventory().getStackInSlot(3);
            helper.assertTrue(pile.is(FoodRegistry.SPOILED_FOOD.get()) && pile.getCount() == 6,
                    "6 déchets attendus à l'ouverture, vu " + pile);
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** La fermeture regroupe les piles de l'armoire, mais pas celles de l'inventaire du joueur. */
    @GameTest(template = "test/plancher", batch = "armoire_menu", setupTicks = 20)
    public static void laFermetureRegroupeLesCasesDeLArmoire(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 30, 0));
        be.inventory().setStackInSlot(5, date(Items.BEEF, 20, 72000));
        be.inventory().setStackInSlot(7, date(Items.BEEF, 10, 36000));
        be.inventory().setStackInSlot(9, new ItemStack(Items.COD, 4));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Rangeuse");
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(0, date(Items.BEEF, 5, 0));
            p.getInventory().setItem(1, date(Items.BEEF, 5, 72000));
            ouvrir(helper, p);
            p.closeContainer();
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 60,
                    "60 attendus dans la première case, vu " + be.inventory().getStackInSlot(0));
            helper.assertTrue(be.inventory().getStackInSlot(5).isEmpty() && be.inventory().getStackInSlot(7).isEmpty(),
                    "les autres piles devaient être vidées");
            helper.assertTrue(FoodStamp.stamp(be.inventory().getStackInSlot(0)) == 18000,
                    "date 18000 attendue, vue " + FoodStamp.stamp(be.inventory().getStackInSlot(0)));
            helper.assertTrue(be.inventory().getStackInSlot(9).getCount() == 4, "le poisson ne devait pas bouger");
            helper.assertTrue(p.getInventory().getItem(0).getCount() == 5 && p.getInventory().getItem(1).getCount() == 5,
                    "l'inventaire du joueur n'est pas regroupé à la fermeture");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    /** Le clic majuscule dépose dans les 27 cases et marque l'armoire modifiée, dans les deux sens. */
    @GameTest(template = "test/plancher", batch = "armoire_menu", setupTicks = 20)
    public static void leClicMajusculeEchangeAvecLArmoire(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Porteur");
            ServerPlayer p = j.joueur();
            int deDepart = total(p, Items.BEEF);
            p.getInventory().setItem(0, date(Items.BEEF, 5, ANCIENNE));
            FridgeMenu menu = ouvrir(helper, p);
            // Case 54 : la barre d'action du joueur (27 cases d'armoire, 27 de sac, puis la barre).
            helper.assertTrue(!menu.quickMoveStack(p, 54).isEmpty(), "le boeuf devait passer dans l'armoire");
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 5, "5 boeufs attendus en case 0");
            helper.assertTrue(total(p, Items.BEEF) == deDepart, "le joueur devait avoir tout donné");
            helper.assertTrue(!menu.quickMoveStack(p, 0).isEmpty(), "le boeuf devait ressortir");
            helper.assertTrue(be.inventory().getStackInSlot(0).isEmpty(), "l'armoire devait être vidée");
            helper.assertTrue(total(p, Items.BEEF) == deDepart + 5, "les 5 boeufs devaient revenir chez le joueur, vus "
                    + total(p, Items.BEEF) + " pour " + deDepart + " au départ");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }

    private static int total(ServerPlayer joueur, Item item) {
        int somme = 0;
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            ItemStack pile = joueur.getInventory().getItem(i);
            if (pile.is(item)) {
                somme += pile.getCount();
            }
        }
        return somme;
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void videElleNeConsommeRien(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.energy().getEnergyStored() == avant, "vide, elle ne consomme rien");
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
            helper.assertTrue(be.coldCredit() == 0, "vide, aucun crédit ne s'acquiert, vu " + be.coldCredit());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void casseeElleRendSonContenu(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 12, ANCIENNE));
        be.inventory().setStackInSlot(26, date(Items.COD, 3, ANCIENNE));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, Items.BEEF) == 12, "12 boeufs attendus au sol, vus " + total(objets, Items.BEEF));
            helper.assertTrue(total(objets, Items.COD) == 3, "3 poissons attendus au sol");
            helper.assertTrue(total(objets, FoodRegistry.FRIDGE_ITEM.get()) == 1, "le bloc lui-même devait tomber");
            helper.succeed();
        });
    }

    private static int total(List<ItemEntity> objets, Item item) {
        return objets.stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "test/plancher", batch = "armoire", setupTicks = 20)
    public static void lEtatSurvitAuRechargement(GameTestHelper helper) {
        FridgeBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, date(Items.BEEF, 7, ANCIENNE));
        be.inventory().setStackInSlot(13, date(Items.COD, 2, ANCIENNE - 36000));
        alimenter(be);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avant la décharge, vu " + be.state());
            dechargerPuisRecharger(helper, be, 32000);
        });
        helper.runAfterDelay(18, () -> {
            CompoundTag tag = be.saveWithoutMetadata();
            FridgeBlockEntity copie = new FridgeBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            for (int i = 0; i < 27; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement : " + be.inventory().getStackInSlot(i)
                                + " contre " + copie.inventory().getStackInSlot(i));
            }
            helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
            helper.assertTrue(copie.coldCredit() == be.coldCredit() && copie.coldCredit() >= 6000,
                    "crédit de froid perdu : " + copie.coldCredit() + " contre " + be.coldCredit());
            helper.succeed();
        });
    }
}
