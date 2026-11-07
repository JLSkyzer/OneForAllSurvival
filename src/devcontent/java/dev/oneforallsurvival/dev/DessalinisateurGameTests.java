package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.coast.CoastDetector;
import dev.oneforallsurvival.coast.CoastSavedData;
import dev.oneforallsurvival.food.DesalinatorBlockEntity;
import dev.oneforallsurvival.food.DesalinatorMenu;
import dev.oneforallsurvival.food.FoodConfig;
import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * Le dessalinisateur, son eau potable et son sel. Les chiffres viennent de {@link FoodConfig}. Le détecteur de côte
 * et ses zones sont globaux au serveur : les tests qui y touchent ont chacun leur lot, posent leur zone avant la
 * machine (qui relit la côte toutes les 200 ticks) et la retirent dans tous les cas. Tout GameTest qui exige que la
 * machine ait tourné attend {@code setupTicks = 20}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DessalinisateurGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);
    private static final String ZONE_COTE = "test_dessalinisateur_cote";
    private static final String ZONE_LOIN = "test_dessalinisateur_loin";
    private static final TagKey<Item> SEL = ItemTags.create(new ResourceLocation("forge", "salt"));

    private DessalinisateurGameTests() {}

    private static DesalinatorBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, FoodRegistry.DESALINATOR.get());
        DesalinatorBlockEntity be = (DesalinatorBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity du dessalinisateur");
        return be;
    }

    /** Remplit la réserve d'énergie à ras bord. */
    private static void alimenter(DesalinatorBlockEntity be) {
        for (int i = 0; i < 100; i++) {
            be.energy().receiveEnergy(DesalinatorBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static void eau(DesalinatorBlockEntity be, int mb) {
        be.waterTank().setFluid(new FluidStack(Fluids.WATER, mb));
    }

    private static void potable(DesalinatorBlockEntity be, int mb) {
        be.potableTank().setFluid(new FluidStack(FoodRegistry.POTABLE_WATER.get(), mb));
    }

    private static IFluidHandler tuyau(DesalinatorBlockEntity be, Direction face) {
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElseThrow(
                () -> new GameTestAssertException("pas de capability de fluides sur la face " + face));
    }

    private static IItemHandler tremie(DesalinatorBlockEntity be, Direction face) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElseThrow(
                () -> new GameTestAssertException("pas de capability d'objets sur la face " + face));
    }

    /** Les six faces, puis « aucune face » (ce que demandent certains mods). */
    private static Direction[] faces() {
        Direction[] toutes = new Direction[7];
        System.arraycopy(Direction.values(), 0, toutes, 0, 6);
        return toutes;
    }

    private static void poserZone(GameTestHelper helper, String id, BlockPos de, BlockPos a) {
        CoastSavedData donnees = CoastSavedData.get(helper.getLevel().getServer());
        donnees.remove(id);
        donnees.add(id, helper.getLevel().dimension().location(), de, a);
        CoastDetector.clearCache();
    }

    private static void retirerZone(GameTestHelper helper, String id) {
        CoastSavedData.get(helper.getLevel().getServer()).remove(id);
        CoastDetector.clearCache();
    }

    private static boolean eauDe(FluidStack pile, net.minecraft.world.level.material.Fluid fluide) {
        return !pile.isEmpty() && pile.getFluid() == fluide;
    }

    // ---------------------------------------------------------------------------------------------
    // Le travail
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 200)
    public static void sansEauRienNeSeFait(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        alimenter(be);
        int energie = be.energy().getEnergyStored();
        helper.assertTrue(energie == DesalinatorBlockEntity.CAPACITY, "réserve pleine attendue, vue " + energie);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu sans eau, vu " + be.state());
            helper.assertTrue(be.energy().getEnergyStored() == energie, "aucun FE ne devait partir sans eau");
            helper.assertTrue(be.progress() == 0 && be.cycles() == 0, "aucune progression sans eau");
            helper.assertTrue(be.waterTank().isEmpty() && be.potableTank().isEmpty(), "les cuves devaient rester vides");
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).isEmpty(), "pas de sel sans eau");
            helper.succeed();
        });
    }

    /** Un cycle, au FE près : 4 000 FE (40 par tick pendant 100 ticks), 250 mB d'eau pour 200 mB d'eau potable. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void unCycleConsommeEtProduit(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        int cycle = FoodConfig.desalinatorCycle();
        int cout = cycle * FoodConfig.desalinatorEnergy();
        // La réserve ne contient que le prix d'un cycle : elle doit être vide au dernier tick.
        alimenter(be);
        be.drainEnergy(be.energy().getEnergyStored() - cout);
        helper.assertTrue(be.energy().getEnergyStored() == cout, "le prix d'un cycle attendu en réserve, vu "
                + be.energy().getEnergyStored() + " pour " + cout);
        eau(be, 1000);
        helper.runAfterDelay(cycle + 10, () -> {
            int attenduEau = 1000 - FoodConfig.waterPerCycle();
            helper.assertTrue(be.waterTank().getFluidAmount() == attenduEau,
                    attenduEau + " mB d'eau attendus, vus " + be.waterTank().getFluidAmount());
            helper.assertTrue(eauDe(be.potableTank().getFluid(), FoodRegistry.POTABLE_WATER.get())
                            && be.potableTank().getFluidAmount() == FoodConfig.potablePerCycle(),
                    FoodConfig.potablePerCycle() + " mB d'eau potable attendus, vus " + be.potableTank().getFluid());
            helper.assertTrue(be.energy().getEnergyStored() == 0,
                    "tout le courant du cycle devait être payé, reste " + be.energy().getEnergyStored());
            helper.assertTrue(be.cycles() == 1 && be.progress() == 0,
                    "un cycle fait attendu, vu " + be.cycles() + " (progression " + be.progress() + ")");
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).isEmpty(),
                    "pas de sel au premier cycle");
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 900)
    public static void leSelTombeTousLesQuatreCycles(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        int cycle = FoodConfig.desalinatorCycle();
        int parSel = FoodConfig.cyclesPerSalt();
        eau(be, DesalinatorBlockEntity.TANK_CAPACITY);
        helper.onEachTick(() -> be.energy().receiveEnergy(DesalinatorBlockEntity.MAX_RECEIVE, false));
        helper.runAfterDelay((long) cycle * (parSel - 1) + cycle / 2, () -> {
            helper.assertTrue(be.cycles() == parSel - 1, (parSel - 1) + " cycles faits attendus, vus " + be.cycles());
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).isEmpty(),
                    "pas de sel avant le quatrième cycle");
        });
        helper.runAfterDelay((long) cycle * parSel + 10, () -> {
            ItemStack sel = be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT);
            helper.assertTrue(sel.is(FoodRegistry.SALT.get()) && sel.getCount() == 1,
                    "1 sel attendu au quatrième cycle, vu " + sel);
            helper.assertTrue(be.cycles() == parSel, parSel + " cycles faits attendus, vus " + be.cycles());
            helper.assertTrue(be.potableTank().getFluidAmount() == parSel * FoodConfig.potablePerCycle(),
                    "eau potable inattendue : " + be.potableTank().getFluidAmount());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "dessalinisateur")
    public static void leTagSaltEstPorte(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(FoodRegistry.SALT.get()).is(SEL), "le sel doit porter l'étiquette forge:salt");
        helper.assertTrue(!new ItemStack(Items.SUGAR).is(SEL), "le sucre n'est pas du sel");
        ItemStack reste = new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get()).getCraftingRemainingItem();
        helper.assertTrue(reste.is(Items.GLASS_BOTTLE), "la bouteille vidée doit rendre une bouteille de verre, vu " + reste);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // L'eau : la côte ou le tuyau
    // ---------------------------------------------------------------------------------------------

    /** 100 mB par seconde, soit 5 par tick, sans courant : la prise est gratuite. La zone est retirée dans tous les cas. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur_cote", setupTicks = 20, timeoutTicks = 300)
    public static void enZoneCotiereLEauArriveSeule(GameTestHelper helper) {
        BlockPos ici = helper.absolutePos(POS);
        poserZone(helper, ZONE_COTE, ici.offset(-2, -2, -2), ici.offset(2, 2, 2));
        try {
            DesalinatorBlockEntity be = poser(helper);
            helper.runAfterDelay(60, () -> {
                try {
                    int vu = be.waterTank().getFluidAmount();
                    helper.assertTrue(vu >= 5 * 50 && vu <= 5 * 61,
                            "environ 300 mB d'eau attendus après 60 ticks, vus " + vu);
                    helper.assertTrue(eauDe(be.waterTank().getFluid(), Fluids.WATER), "c'est de l'eau qui devait arriver");
                    helper.assertTrue(be.energy().getEnergyStored() == 0, "la prise ne coûte aucun courant");
                    be.waterTank().setFluid(new FluidStack(Fluids.WATER, DesalinatorBlockEntity.TANK_CAPACITY - 7));
                } catch (RuntimeException e) {
                    retirerZone(helper, ZONE_COTE);
                    throw e;
                }
                helper.runAfterDelay(10, () -> {
                    try {
                        helper.assertTrue(be.waterTank().getFluidAmount() == DesalinatorBlockEntity.TANK_CAPACITY,
                                "la cuve devait se remplir sans déborder, vue " + be.waterTank().getFluidAmount());
                    } finally {
                        retirerZone(helper, ZONE_COTE);
                    }
                    helper.succeed();
                });
            });
        } catch (RuntimeException e) {
            retirerZone(helper, ZONE_COTE);
            throw e;
        }
    }

    /** Hors zone (une zone ailleurs exclut ce lieu) : rien n'arrive seul, mais un tuyau remet la machine en marche. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur_hors_zone", setupTicks = 20, timeoutTicks = 300)
    public static void horsZoneIlFautUnTuyau(GameTestHelper helper) {
        BlockPos ici = helper.absolutePos(POS);
        poserZone(helper, ZONE_LOIN, ici.offset(500, 0, 500), ici.offset(510, 10, 510));
        try {
            DesalinatorBlockEntity be = poser(helper);
            alimenter(be);
            helper.runAfterDelay(60, () -> {
                try {
                    helper.assertTrue(be.waterTank().isEmpty(), "hors zone, aucune eau ne devait arriver, vu "
                            + be.waterTank().getFluidAmount());
                    helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu sans eau, vu " + be.state());
                    int recu = tuyau(be, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
                    helper.assertTrue(recu == 1000, "le tuyau devait verser 1000 mB, versé " + recu);
                } catch (RuntimeException e) {
                    retirerZone(helper, ZONE_LOIN);
                    throw e;
                }
                helper.runAfterDelay(5, () -> {
                    try {
                        helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu avec l'eau du tuyau, vu " + be.state());
                    } finally {
                        retirerZone(helper, ZONE_LOIN);
                    }
                    helper.succeed();
                });
            });
        } catch (RuntimeException e) {
            retirerZone(helper, ZONE_LOIN);
            throw e;
        }
    }

    @GameTest(template = "test/plancher", batch = "dessalinisateur")
    public static void leTuyauNeRemplitQueDeLEau(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        IFluidHandler h = tuyau(be, null);
        helper.assertTrue(h.getTanks() == 2, "deux cuves attendues, vues " + h.getTanks());
        helper.assertTrue(h.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "la lave doit être refusée");
        helper.assertTrue(h.fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 1000), FluidAction.EXECUTE) == 0,
                "l'eau potable ne doit pas rentrer : elle sort seulement");
        helper.assertTrue(be.waterTank().isEmpty() && be.potableTank().isEmpty(), "rien ne devait être entré");
        helper.assertTrue(h.isFluidValid(0, new FluidStack(Fluids.WATER, 1)) && !h.isFluidValid(0, new FluidStack(Fluids.LAVA, 1))
                && !h.isFluidValid(1, new FluidStack(FoodRegistry.POTABLE_WATER.get(), 1)),
                "isFluidValid : l'eau seule, et dans la première cuve seule");
        helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000, "simulation : 1000 attendus");
        helper.assertTrue(be.waterTank().isEmpty(), "une simulation ne remplit pas");
        helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000, "1000 mB d'eau attendus");
        // L'eau courante est de l'eau : elle se mêle à celle de la cuve (c'est le même fluide pour la cuve).
        helper.assertTrue(h.fill(new FluidStack(Fluids.FLOWING_WATER, 500), FluidAction.EXECUTE) == 500, "l'eau courante doit entrer");
        helper.assertTrue(be.waterTank().getFluidAmount() == 1500 && eauDe(be.waterTank().getFluid(), Fluids.WATER),
                "1500 mB d'eau dans la cuve, vus " + be.waterTank().getFluid());
        for (Direction face : faces()) {
            helper.assertTrue(tuyau(be, face).fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 100,
                    "la face " + face + " devait accepter l'eau");
        }
        int reste = DesalinatorBlockEntity.TANK_CAPACITY - 1500;
        helper.assertTrue(h.fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE) == reste,
                "la cuve ne doit pas déborder : " + reste + " attendus");
        helper.assertTrue(be.potableTank().isEmpty(), "rien ne devait entrer dans la cuve d'eau potable");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "dessalinisateur")
    public static void leTuyauNeVideQueLEauPotable(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        eau(be, 1000);
        IFluidHandler h = tuyau(be, null);
        helper.assertTrue(h.drain(1000, FluidAction.EXECUTE).isEmpty(), "l'eau salée ne doit pas sortir");
        helper.assertTrue(h.drain(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE).isEmpty(),
                "l'eau demandée par nom ne doit pas sortir non plus");
        helper.assertTrue(be.waterTank().getFluidAmount() == 1000, "l'eau devait rester");
        potable(be, 500);
        for (Direction face : faces()) {
            helper.assertTrue(tuyau(be, face).drain(10, FluidAction.SIMULATE).getAmount() == 10,
                    "la face " + face + " devait laisser sortir l'eau potable");
        }
        helper.assertTrue(be.potableTank().getFluidAmount() == 500, "une simulation ne vide pas");
        FluidStack sorti = h.drain(1000, FluidAction.EXECUTE);
        helper.assertTrue(eauDe(sorti, FoodRegistry.POTABLE_WATER.get()) && sorti.getAmount() == 500,
                "500 mB d'eau potable attendus, vus " + sorti);
        helper.assertTrue(be.potableTank().isEmpty(), "la cuve potable devait être vide");
        helper.assertTrue(be.waterTank().getFluidAmount() == 1000, "l'eau salée ne devait pas avoir bougé");
        potable(be, 300);
        FluidStack nomme = h.drain(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 200), FluidAction.EXECUTE);
        helper.assertTrue(nomme.getAmount() == 200 && be.potableTank().getFluidAmount() == 100,
                "200 mB nommés attendus, vus " + nomme);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Rien ne se perd
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void sansCourantRienNeSePerd(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        eau(be, 1000);
        alimenter(be);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 3));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 2));
        int[] vu = new int[1];
        helper.runAfterDelay(50, () -> {
            vu[0] = be.progress();
            helper.assertTrue(vu[0] >= 45 && vu[0] <= 50, "environ 50 ticks de travail attendus, vus " + vu[0]);
            be.drainEnergy(DesalinatorBlockEntity.CAPACITY);
            helper.assertTrue(be.energy().getEnergyStored() == 0, "la réserve devait être vidée");
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.progress() == vu[0], "la progression devait rester à " + vu[0] + ", vue " + be.progress());
            helper.assertTrue(be.waterTank().getFluidAmount() == 1000 && be.potableTank().isEmpty(),
                    "les cuves ne devaient pas bouger");
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).getCount() == 3
                            && be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).getCount() == 2,
                    "les objets devaient rester");
            alimenter(be);
        });
        // Le courant revient : le cycle reprend là où il était et s'achève après ce qui lui restait à faire.
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(be.cycles() == 1, "un cycle fait attendu après le retour du courant, vu " + be.cycles());
            helper.assertTrue(be.waterTank().getFluidAmount() == 1000 - FoodConfig.waterPerCycle(),
                    "250 mB d'eau consommés une seule fois, cuve " + be.waterTank().getFluidAmount());
            helper.assertTrue(be.potableTank().getFluidAmount() == FoodConfig.potablePerCycle(),
                    "200 mB produits une seule fois, vus " + be.potableTank().getFluidAmount());
            helper.succeed();
        });
    }

    /** Sel plein au cycle du sel : elle s'arrête sans rien perdre, puis repart dès qu'il y a de la place. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void pleinIlSArrete(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 64));
        be.debugSetCycles(FoodConfig.cyclesPerSalt() - 1);
        eau(be, 1000);
        alimenter(be);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, sel plein, vu " + be.state());
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).getCount() == 64, "le sel devait rester");
            helper.assertTrue(be.waterTank().getFluidAmount() == 1000 && be.potableTank().isEmpty(), "les cuves ne devaient pas bouger");
            helper.assertTrue(be.energy().getEnergyStored() == DesalinatorBlockEntity.CAPACITY, "aucun FE ne devait partir");
            helper.assertTrue(be.progress() == 0 && be.cycles() == FoodConfig.cyclesPerSalt() - 1, "rien ne devait avancer");
            be.inventory().extractItem(DesalinatorBlockEntity.SLOT_SALT, 10, false);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu une fois de la place faite, vu " + be.state());
            helper.succeed();
        });
    }

    /** Pas de place pour l'eau potable : même arrêt, sans perte. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void uneCuvePotablePleineArrete(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        potable(be, DesalinatorBlockEntity.TANK_CAPACITY - FoodConfig.potablePerCycle() + 1);
        eau(be, 1000);
        alimenter(be);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, cuve pleine, vu " + be.state());
            helper.assertTrue(be.waterTank().getFluidAmount() == 1000 && be.progress() == 0, "rien ne devait avancer");
            helper.assertTrue(be.energy().getEnergyStored() == DesalinatorBlockEntity.CAPACITY, "aucun FE ne devait partir");
            tuyau(be, null).drain(100, FluidAction.EXECUTE);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu une fois de la place faite, vu " + be.state());
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // La bouteille et les cases
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void laBouteilleSeRemplit(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        potable(be, 1000);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 3));
        helper.runAfterDelay(10, () -> {
            ItemStack pleines = be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_FILLED);
            helper.assertTrue(pleines.is(FoodRegistry.POTABLE_WATER_BOTTLE.get()) && pleines.getCount() == 3,
                    "3 bouteilles pleines attendues, vues " + pleines);
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).isEmpty(), "les vides devaient être prises");
            helper.assertTrue(be.potableTank().getFluidAmount() == 1000 - 3 * DesalinatorBlockEntity.BOTTLE_MB,
                    "250 mB par bouteille attendus, vu " + be.potableTank().getFluidAmount());
            helper.assertTrue(be.energy().getEnergyStored() == 0, "la bouteille ne coûte aucun courant");
            // Le bac de sortie est plein : la bouteille attend, rien ne se perd.
            be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 16));
            be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 1));
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).getCount() == 1
                            && be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_FILLED).getCount() == 16
                            && be.potableTank().getFluidAmount() == 250,
                    "sortie pleine : rien ne devait bouger");
            be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 15));
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).isEmpty()
                            && be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_FILLED).getCount() == 16
                            && be.potableTank().isEmpty(),
                    "la dernière bouteille devait se remplir");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "dessalinisateur")
    public static void lesCasesDObjetsRespectentLeurRole(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        for (Direction face : faces()) {
            IItemHandler h = tremie(be, face);
            helper.assertTrue(h.getSlots() == DesalinatorBlockEntity.SLOTS, "trois cases attendues sur " + face);
            for (int slot = 0; slot < h.getSlots(); slot++) {
                helper.assertTrue(!h.insertItem(slot, new ItemStack(FoodRegistry.SALT.get()), true).isEmpty(),
                        "le sel ne doit entrer dans aucune case (" + face + ", " + slot + ")");
                helper.assertTrue(!h.insertItem(slot, new ItemStack(Items.STONE), true).isEmpty(),
                        "la pierre ne doit entrer dans aucune case (" + face + ", " + slot + ")");
            }
            helper.assertTrue(h.insertItem(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 5), true).isEmpty(),
                    "la bouteille vide doit entrer dans la case d'entrée (" + face + ")");
            helper.assertTrue(!h.insertItem(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(Items.GLASS_BOTTLE), true).isEmpty()
                            && !h.insertItem(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(Items.GLASS_BOTTLE), true).isEmpty(),
                    "la bouteille vide ne doit pas entrer dans une case de sortie (" + face + ")");
            helper.assertTrue(!h.insertItem(DesalinatorBlockEntity.SLOT_FILLED,
                    new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get()), true).isEmpty(),
                    "la bouteille pleine ne s'y met pas à la main (" + face + ")");
        }
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 4));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 2));
        IItemHandler h = tremie(be, Direction.DOWN);
        helper.assertTrue(h.extractItem(DesalinatorBlockEntity.SLOT_SALT, 3, false).getCount() == 3, "3 sels devaient sortir");
        helper.assertTrue(h.extractItem(DesalinatorBlockEntity.SLOT_FILLED, 2, false).getCount() == 2, "2 bouteilles pleines devaient sortir");
        helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).getCount() == 1, "1 sel devait rester");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Sauvegarde, casse, menu
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20, timeoutTicks = 300)
    public static void lesReservoirsSurviventAuRechargement(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        eau(be, 3333);
        potable(be, 4444);
        be.debugSetCycles(2);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 7));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 3));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 5));
        alimenter(be);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.progress() >= 20, "la machine devait avoir avancé, progression " + be.progress());
            CompoundTag tag = be.saveWithoutMetadata();
            DesalinatorBlockEntity copie = new DesalinatorBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.waterTank().getFluidAmount() == be.waterTank().getFluidAmount()
                            && eauDe(copie.waterTank().getFluid(), Fluids.WATER),
                    "eau perdue : " + copie.waterTank().getFluid() + " contre " + be.waterTank().getFluid());
            helper.assertTrue(copie.potableTank().getFluidAmount() == be.potableTank().getFluidAmount()
                            && eauDe(copie.potableTank().getFluid(), FoodRegistry.POTABLE_WATER.get()),
                    "eau potable perdue : " + copie.potableTank().getFluid() + " contre " + be.potableTank().getFluid());
            helper.assertTrue(copie.progress() == be.progress() && copie.cycles() == be.cycles() && copie.cycles() == 2,
                    "progression ou cycles perdus : " + copie.progress() + "/" + copie.cycles());
            helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
            for (int i = 0; i < DesalinatorBlockEntity.SLOTS; i++) {
                helper.assertTrue(ItemStack.matches(be.inventory().getStackInSlot(i), copie.inventory().getStackInSlot(i)),
                        "case " + i + " différente après rechargement : " + be.inventory().getStackInSlot(i)
                                + " contre " + copie.inventory().getStackInSlot(i));
            }
            helper.succeed();
        });
    }

    private static int total(List<ItemEntity> objets, Item item) {
        return objets.stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Casser la machine rend ses objets, pas ses réservoirs. */
    @GameTest(template = "test/plancher", batch = "dessalinisateur", setupTicks = 20)
    public static void casseeElleRendSesObjets(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        eau(be, 5000);
        potable(be, 5000);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 5));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY, new ItemStack(Items.GLASS_BOTTLE, 3));
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_FILLED, new ItemStack(FoodRegistry.POTABLE_WATER_BOTTLE.get(), 2));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, FoodRegistry.SALT.get()) == 5, "5 sels attendus au sol, vus " + total(objets, FoodRegistry.SALT.get()));
            helper.assertTrue(total(objets, Items.GLASS_BOTTLE) == 3, "3 bouteilles vides attendues au sol");
            helper.assertTrue(total(objets, FoodRegistry.POTABLE_WATER_BOTTLE.get()) == 2, "2 bouteilles pleines attendues au sol");
            helper.assertTrue(total(objets, FoodRegistry.DESALINATOR_ITEM.get()) == 1, "le bloc lui-même devait tomber");
            helper.succeed();
        });
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

    /** Ouvre le menu comme le fait le clic droit : par le bloc lui-même. */
    private static DesalinatorMenu ouvrir(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(POS);
        helper.getLevel().getBlockState(abs).use(helper.getLevel(), p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.assertTrue(p.containerMenu instanceof DesalinatorMenu, "le menu devait s'ouvrir, vu " + p.containerMenu);
        return (DesalinatorMenu) p.containerMenu;
    }

    /**
     * Le menu : trois cases de la machine puis les 36 du joueur ; la bouteille vide entre (à la main comme au clic
     * majuscule), le sel et le reste non ; les nombres des jauges viennent de la machine.
     */
    @GameTest(template = "test/plancher", batch = "dessalinisateur_menu", setupTicks = 20, timeoutTicks = 200)
    public static void leMenuNeLaisseEntrerQueLesBouteilles(GameTestHelper helper) {
        DesalinatorBlockEntity be = poser(helper);
        eau(be, 2000);
        potable(be, 500);
        be.inventory().setStackInSlot(DesalinatorBlockEntity.SLOT_SALT, new ItemStack(FoodRegistry.SALT.get(), 4));
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Eaudouce");
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(0, new ItemStack(Items.GLASS_BOTTLE, 8));
            p.getInventory().setItem(1, new ItemStack(FoodRegistry.SALT.get(), 3));
            DesalinatorMenu menu = ouvrir(helper, p);
            helper.assertTrue(menu.slots.size() == DesalinatorBlockEntity.SLOTS + 36, "39 cases attendues, vues " + menu.slots.size());
            ItemStack bouteille = new ItemStack(Items.GLASS_BOTTLE);
            ItemStack sel = new ItemStack(FoodRegistry.SALT.get());
            helper.assertTrue(menu.getSlot(DesalinatorBlockEntity.SLOT_EMPTY).mayPlace(bouteille), "la bouteille vide doit entrer");
            helper.assertTrue(!menu.getSlot(DesalinatorBlockEntity.SLOT_EMPTY).mayPlace(sel), "le sel ne doit pas entrer dans l'entrée");
            helper.assertTrue(!menu.getSlot(DesalinatorBlockEntity.SLOT_SALT).mayPlace(sel)
                            && !menu.getSlot(DesalinatorBlockEntity.SLOT_FILLED).mayPlace(bouteille),
                    "les cases de sortie ne reçoivent rien");
            // Clic majuscule : la barre d'action du joueur est la dernière tranche (3 + 27 cases avant elle).
            int barre = DesalinatorBlockEntity.SLOTS + 27;
            helper.assertTrue(!menu.quickMoveStack(p, barre).isEmpty(), "les bouteilles devaient passer dans la machine");
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).getCount() == 8,
                    "8 bouteilles attendues en entrée, vues " + be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY));
            helper.assertTrue(menu.quickMoveStack(p, barre + 1).isEmpty(), "le sel ne doit pas passer");
            helper.assertTrue(p.getInventory().getItem(1).getCount() == 3, "le joueur devait garder son sel");
            // Dépôt à la main : la case ne doit pas refuser par une taille maximale nulle.
            menu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 5));
            menu.clicked(DesalinatorBlockEntity.SLOT_EMPTY, 0, ClickType.PICKUP, p);
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY).getCount() == 13 && menu.getCarried().isEmpty(),
                    "13 bouteilles et rien en main attendus, vu " + be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_EMPTY)
                            + " / " + menu.getCarried());
            // Le sel sort au clic majuscule.
            helper.assertTrue(!menu.quickMoveStack(p, DesalinatorBlockEntity.SLOT_SALT).isEmpty(), "le sel devait ressortir");
            helper.assertTrue(be.inventory().getStackInSlot(DesalinatorBlockEntity.SLOT_SALT).isEmpty(), "la case du sel devait être vidée");
            // Les nombres des jauges.
            helper.assertTrue(menu.waterAmount() == 2000 && menu.potableAmount() == 500, "jauges : " + menu.waterAmount() + "/" + menu.potableAmount());
            helper.assertTrue(menu.cycleLength() == FoodConfig.desalinatorCycle() && menu.capacity() == DesalinatorBlockEntity.CAPACITY,
                    "cycle ou capacité inattendus");
            helper.assertTrue(menu.stillValid(p), "le menu devait rester valide près de la machine");
            helper.getLevel().destroyBlock(helper.absolutePos(POS), false);
            helper.assertTrue(!menu.stillValid(p), "le menu devait se fermer quand la machine disparaît");
        } finally {
            quitter(j);
        }
        helper.succeed();
    }
}
