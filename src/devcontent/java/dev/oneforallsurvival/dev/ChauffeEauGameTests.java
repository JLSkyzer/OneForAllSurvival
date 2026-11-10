package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.town.TownConfig;
import dev.oneforallsurvival.town.TownRegistry;
import dev.oneforallsurvival.town.WaterHeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le chauffe-eau : il ne prend que l'eau potable du mod, ne rend que l'eau chaude, et ne perd rien quand il ne
 * travaille pas. Tout GameTest qui exige que la machine ait tourné attend {@code setupTicks = 20}.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class ChauffeEauGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final String LOT = "chauffe_eau";
    private static final String LOT_USURE = "chauffe_eau_usure";

    private ChauffeEauGameTests() {}

    private static WaterHeaterBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, TownRegistry.WATER_HEATER.get());
        WaterHeaterBlockEntity be = (WaterHeaterBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity du chauffe-eau");
        return be;
    }

    /** Remplit la réserve d'énergie à ras bord. */
    private static void alimenter(WaterHeaterBlockEntity be) {
        for (int i = 0; i < 100; i++) {
            be.energy().receiveEnergy(WaterHeaterBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static void potable(WaterHeaterBlockEntity be, int mb) {
        be.potable().setFluid(new FluidStack(FoodRegistry.POTABLE_WATER.get(), mb));
    }

    private static void chaude(WaterHeaterBlockEntity be, int mb) {
        be.hot().setFluid(new FluidStack(TownRegistry.HOT_WATER.get(), mb));
    }

    private static boolean est(FluidStack pile, Fluid fluide) {
        return !pile.isEmpty() && pile.getFluid() == fluide;
    }

    /** Les six faces, puis « aucune face » (ce que demandent certains mods). */
    private static Direction[] faces() {
        Direction[] toutes = new Direction[7];
        System.arraycopy(Direction.values(), 0, toutes, 0, 6);
        return toutes;
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void lEauPotableDevientChaude(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        potable(be, 1000);
        helper.runAfterDelay(30, () -> {
            int chaude = be.hot().getFluidAmount();
            helper.assertTrue(chaude > 0, "de l'eau chaude attendue, vue " + chaude);
            helper.assertTrue(est(be.hot().getFluid(), TownRegistry.HOT_WATER.get()),
                    "la cuve chaude devait contenir de l'eau chaude, vu " + be.hot().getFluid());
            helper.assertTrue(be.potable().getFluidAmount() + chaude == 1000,
                    "rien ne devait se perdre : " + be.potable().getFluidAmount() + " + " + chaude);
            int pas = TownConfig.heaterMbPerTick();
            helper.assertTrue(chaude % pas == 0, "l'eau passe par pas de " + pas + " mB, vue " + chaude);
            int ticks = chaude / pas;
            helper.assertTrue(avant - be.energy().getEnergyStored() == ticks * TownConfig.heaterEnergy(),
                    ticks + " ticks à " + TownConfig.heaterEnergy() + " FE attendus, consommé "
                            + (avant - be.energy().getEnergyStored()));
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void lEauVanillaEstRefusee(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        for (Direction face : faces()) {
            IFluidHandler tuyau = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, face)
                    .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException(
                            "pas de capability de fluides sur la face " + face));
            for (Fluid eau : new Fluid[] {Fluids.WATER, Fluids.FLOWING_WATER}) {
                helper.assertTrue(tuyau.fill(new FluidStack(eau, 1000), FluidAction.SIMULATE) == 0,
                        "l'eau vanilla ne doit pas se simuler remplie (face " + face + ")");
                helper.assertTrue(tuyau.fill(new FluidStack(eau, 1000), FluidAction.EXECUTE) == 0,
                        "l'eau vanilla ne doit pas entrer (face " + face + ")");
            }
        }
        helper.assertTrue(be.potable().isEmpty() && be.hot().isEmpty(), "les cuves devaient rester vides");
        // L'eau potable, elle, entre bien : le refus ne vient pas d'une cuve condamnée.
        int pris = be.fluids().fill(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 1000), FluidAction.EXECUTE);
        helper.assertTrue(pris == 1000 && be.potable().getFluidAmount() == 1000, "l'eau potable devait entrer, prise " + pris);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laLaveEstRefusee(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        for (Fluid lave : new Fluid[] {Fluids.LAVA, Fluids.FLOWING_LAVA}) {
            helper.assertTrue(be.fluids().fill(new FluidStack(lave, 1000), FluidAction.SIMULATE) == 0,
                    "la lave ne doit pas se simuler remplie");
            helper.assertTrue(be.fluids().fill(new FluidStack(lave, 1000), FluidAction.EXECUTE) == 0,
                    "la lave ne doit pas entrer");
        }
        // L'eau chaude ne se verse pas non plus par l'entrée : seule l'eau potable y entre.
        helper.assertTrue(be.fluids().fill(new FluidStack(TownRegistry.HOT_WATER.get(), 1000), FluidAction.EXECUTE) == 0,
                "l'eau chaude ne doit pas entrer par le tuyau");
        helper.assertTrue(be.potable().isEmpty() && be.hot().isEmpty(), "les cuves devaient rester vides");
        helper.succeed();
    }

    /** Vider ne rend jamais d'eau potable, ni par quantité ni par type demandé. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void seuleLEauChaudeSort(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        potable(be, 1000);
        chaude(be, 500);
        IFluidHandler tuyau = be.fluids();
        helper.assertTrue(tuyau.drain(new FluidStack(FoodRegistry.POTABLE_WATER.get(), 1000), FluidAction.EXECUTE).isEmpty(),
                "l'eau potable ne doit pas sortir quand on la demande");
        helper.assertTrue(tuyau.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE).isEmpty(),
                "l'eau vanilla n'est pas là pour sortir");
        FluidStack sortie = tuyau.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        helper.assertTrue(est(sortie, TownRegistry.HOT_WATER.get()) && sortie.getAmount() == 500,
                "500 mB d'eau chaude attendus, vus " + sortie);
        helper.assertTrue(be.potable().getFluidAmount() == 1000, "l'eau potable devait rester : " + be.potable().getFluidAmount());
        FluidStack encore = tuyau.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        helper.assertTrue(encore.isEmpty(), "plus d'eau chaude : rien ne devait sortir, vu " + encore);
        helper.assertTrue(be.potable().getFluidAmount() == 1000, "vider ne touche jamais l'eau potable");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantRienNeSePerd(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        potable(be, 1000);
        chaude(be, 300);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.potable().getFluidAmount() == 1000 && be.hot().getFluidAmount() == 300,
                    "les cuves devaient rester telles quelles : " + be.potable().getFluidAmount() + "/"
                            + be.hot().getFluidAmount());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void enPanneRienNeSePerd(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        alimenter(be);
        be.breakDown(FaultType.FUSE);
        int energie = be.energy().getEnergyStored();
        potable(be, 1000);
        chaude(be, 300);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(be.energy().getEnergyStored() == energie, "aucun FE ne devait partir en panne");
            helper.assertTrue(be.potable().getFluidAmount() == 1000 && be.hot().getFluidAmount() == 300,
                    "les cuves devaient rester telles quelles : " + be.potable().getFluidAmount() + "/"
                            + be.hot().getFluidAmount());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void cuvePleineIlSArrete(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        alimenter(be);
        int energie = be.energy().getEnergyStored();
        potable(be, 1000);
        chaude(be, WaterHeaterBlockEntity.TANK_CAPACITY);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu cuve pleine, vu " + be.state());
            helper.assertTrue(be.energy().getEnergyStored() == energie, "aucun FE ne devait partir cuve pleine");
            helper.assertTrue(be.potable().getFluidAmount() == 1000
                            && be.hot().getFluidAmount() == WaterHeaterBlockEntity.TANK_CAPACITY,
                    "les cuves devaient rester telles quelles : " + be.potable().getFluidAmount() + "/"
                            + be.hot().getFluidAmount());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, setupTicks = 20, timeoutTicks = 200)
    public static void lesCuvesSurviventAuRechargement(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        potable(be, 3333);
        chaude(be, 1234);
        alimenter(be);
        helper.runAfterDelay(30, () -> {
            CompoundTag tag = be.saveWithoutMetadata();
            WaterHeaterBlockEntity copie = new WaterHeaterBlockEntity(be.getBlockPos(), be.getBlockState());
            copie.load(tag);
            helper.assertTrue(copie.potable().getFluidAmount() == be.potable().getFluidAmount()
                            && est(copie.potable().getFluid(), FoodRegistry.POTABLE_WATER.get()),
                    "eau potable perdue : " + copie.potable().getFluid() + " contre " + be.potable().getFluid());
            helper.assertTrue(copie.hot().getFluidAmount() == be.hot().getFluidAmount()
                            && est(copie.hot().getFluid(), TownRegistry.HOT_WATER.get()),
                    "eau chaude perdue : " + copie.hot().getFluid() + " contre " + be.hot().getFluid());
            helper.assertTrue(be.hot().getFluidAmount() > 1234, "la machine devait avoir chauffé de l'eau");
            helper.assertTrue(copie.energy().getEnergyStored() == be.energy().getEnergyStored(), "énergie perdue");
            helper.succeed();
        });
    }

    /** Usée (rendement 0,75), la machine paie chaque tick mais ne travaille que 3 ticks sur 4 : le pas reste entier. */
    @GameTest(template = "test/plancher", batch = LOT_USURE, setupTicks = 20, timeoutTicks = 200)
    public static void usureCompteeUneSeuleFois(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        be.takeDamage(60);
        helper.assertTrue(be.efficiency() < 1.0, "rendement réduit attendu, vu " + be.efficiency());
        alimenter(be);
        int avant = be.energy().getEnergyStored();
        potable(be, WaterHeaterBlockEntity.TANK_CAPACITY);
        helper.runAfterDelay(40, () -> {
            int consomme = avant - be.energy().getEnergyStored();
            int energie = TownConfig.heaterEnergy();
            helper.assertTrue(consomme > 0 && consomme % energie == 0, "FE consommés multiples de " + energie + " : " + consomme);
            int ticks = consomme / energie;
            int travail = (int) Math.floor(ticks * be.efficiency() + 1e-9);
            int attendu = TownConfig.heaterMbPerTick() * travail;
            helper.assertTrue(be.hot().getFluidAmount() == attendu,
                    attendu + " mB attendus (" + travail + " ticks de travail sur " + ticks + "), vus "
                            + be.hot().getFluidAmount());
            helper.assertTrue(be.potable().getFluidAmount() + be.hot().getFluidAmount() == WaterHeaterBlockEntity.TANK_CAPACITY,
                    "rien ne devait se perdre");
            helper.succeed();
        });
    }

    /** Le dernier pas peut être partiel : 3 mB ne restent pas coincés. */
    @GameTest(template = "test/plancher", batch = LOT_USURE, setupTicks = 20, timeoutTicks = 200)
    public static void unResteInferieurAuPasSEcoule(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        alimenter(be);
        potable(be, 3);
        chaude(be, WaterHeaterBlockEntity.TANK_CAPACITY - 2);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.hot().getFluidAmount() == WaterHeaterBlockEntity.TANK_CAPACITY
                            && be.potable().getFluidAmount() == 1,
                    "2 mB passés (place limitée), 1 mB resté : vus " + be.hot().getFluidAmount() + "/"
                            + be.potable().getFluidAmount());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_USURE, setupTicks = 20, timeoutTicks = 200)
    public static void unPetitResteDEauPotableSEcoule(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        alimenter(be);
        potable(be, 3);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.potable().isEmpty() && be.hot().getFluidAmount() == 3,
                    "3 mB d'eau chaude attendus, vus " + be.hot().getFluidAmount() + " (potable "
                            + be.potable().getFluidAmount() + ")");
            helper.succeed();
        });
    }

    /** L'eau chaude n'a ni seau ni bloc ; l'eau potable courante entre, l'eau vanilla courante non. */
    @GameTest(template = "test/plancher", batch = LOT_USURE)
    public static void leFluideNAPasDeSeauNiDeBloc(GameTestHelper helper) {
        WaterHeaterBlockEntity be = poser(helper);
        Fluid chaude = TownRegistry.HOT_WATER.get();
        helper.assertTrue(chaude.getBucket() == Items.AIR, "pas de seau d'eau chaude, vu " + chaude.getBucket());
        helper.assertTrue(chaude.defaultFluidState().createLegacyBlock().isAir(),
                "pas de bloc d'eau chaude, vu " + chaude.defaultFluidState().createLegacyBlock());
        helper.assertTrue(TownRegistry.HOT_WATER_FLOWING.get().getBucket() == Items.AIR, "pas de seau pour l'eau chaude courante");
        helper.assertTrue(be.fluids().fill(new FluidStack(Fluids.FLOWING_WATER, 100), FluidAction.EXECUTE) == 0,
                "l'eau vanilla courante est refusée");
        int pris = be.fluids().fill(new FluidStack(FoodRegistry.POTABLE_WATER_FLOWING.get(), 100), FluidAction.EXECUTE);
        helper.assertTrue(pris == 100, "l'eau potable courante est acceptée, prise " + pris);
        helper.succeed();
    }
}
