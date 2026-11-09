package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.food.FoodRegistry;
import dev.oneforallsurvival.food.HotPlateBlock;
import dev.oneforallsurvival.food.HotPlateBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModItems;

/**
 * La plaque électrique, éprouvée avec les vrais ustensiles de Farmer's Delight posés dessus : c'est leur propre
 * {@code isHeated} qui juge, pas une copie de sa règle.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PlaqueGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private PlaqueGameTests() {}

    /** Pose la plaque, dégage la colonne au-dessus (le terrain d'essai y laisse parfois du gravier) et pose {@code dessus}. */
    private static HotPlateBlockEntity poser(GameTestHelper helper, Block dessus) {
        for (int haut = 1; haut <= 3; haut++) {
            helper.setBlock(POS.above(haut), Blocks.AIR);
        }
        helper.setBlock(POS, FoodRegistry.HOT_PLATE.get());
        if (dessus != null) {
            helper.setBlock(POS.above(), dessus);
        }
        HotPlateBlockEntity be = (HotPlateBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity de la plaque");
        return be;
    }

    private static void alimenter(HotPlateBlockEntity be) {
        for (int i = 0; i < 60; i++) {
            be.energy().receiveEnergy(HotPlateBlockEntity.MAX_RECEIVE, false);
        }
    }

    private static boolean allumee(GameTestHelper helper) {
        return helper.getBlockState(POS).getValue(HotPlateBlock.LIT);
    }

    private static CookingPotBlockEntity marmite(GameTestHelper helper, BlockPos pos) {
        return (CookingPotBlockEntity) helper.getBlockEntity(pos);
    }

    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void alimenteeElleChauffeLaMarmite(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.COOKING_POT.get());
        alimenter(be);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
            helper.assertTrue(allumee(helper), "la plaque devait être allumée");
            helper.assertTrue(marmite(helper, POS.above()).isHeated(), "la marmite devait être chauffée");
            helper.assertTrue(helper.getBlockState(POS).getLightEmission(helper.getLevel(), helper.absolutePos(POS)) == 7,
                    "la plaque allumée devait éclairer à 7");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void sansCourantElleNeChauffePlus(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.COOKING_POT.get());
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(!allumee(helper), "la plaque sans courant ne devait pas être allumée");
            helper.assertTrue(!marmite(helper, POS.above()).isHeated(), "la marmite ne devait pas être chauffée");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void enPanneElleNeChauffePlus(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.COOKING_POT.get());
        alimenter(be);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(allumee(helper), "la plaque devait d'abord être allumée");
            be.breakDown(FaultType.values()[0]);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(!allumee(helper), "la plaque en panne ne devait pas être allumée");
            helper.assertTrue(!marmite(helper, POS.above()).isHeated(), "la marmite ne devait pas être chauffée");
            helper.succeed();
        });
    }

    /** Rien dessus : ni courant consommé ni usure, même alimentée. */
    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void sansUstensileElleResteALarret(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, null);
        alimenter(be);
        int energie = be.energy().getEnergyStored();
        double usure = be.condition();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
            helper.assertTrue(!allumee(helper), "la plaque sans ustensile ne devait pas être allumée");
            helper.assertTrue(be.energy().getEnergyStored() == energie,
                    "aucune consommation attendue : " + energie + " puis " + be.energy().getEnergyStored());
            helper.assertTrue(be.condition() == usure, "aucune usure attendue : " + usure + " puis " + be.condition());
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void elleChauffeAussiLaPoele(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.SKILLET.get());
        alimenter(be);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(allumee(helper), "la plaque devait être allumée sous la poêle");
            helper.assertTrue(helper.getBlockEntity(POS.above()) instanceof SkilletBlockEntity poele && poele.isHeated(),
                    "la poêle devait être chauffée");
            helper.succeed();
        });
    }

    /** Un conducteur (une trémie) entre la plaque et l'ustensile : la chaleur passe, et la plaque s'allume. */
    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void unConducteurLaisseLaChaleurPasser(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, Blocks.HOPPER);
        helper.setBlock(POS.above(2), ModBlocks.COOKING_POT.get());
        alimenter(be);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(allumee(helper), "la plaque devait être allumée sous le conducteur");
            helper.assertTrue(marmite(helper, POS.above(2)).isHeated(),
                    "la marmite devait être chauffée à travers la trémie");
            helper.succeed();
        });
    }

    /** Un vrai ragoût de bœuf : la marmite cuit les deux cents ticks de la recette, la plaque pour seule source de chaleur ; avec un bol dans la marmite, la portion arrive en sortie. */
    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 400)
    public static void uneVraieRecetteCuit(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.COOKING_POT.get());
        CookingPotBlockEntity marmite = marmite(helper, POS.above());
        marmite.getInventory().setStackInSlot(0, new ItemStack(Items.BEEF));
        marmite.getInventory().setStackInSlot(1, new ItemStack(Items.CARROT));
        marmite.getInventory().setStackInSlot(2, new ItemStack(Items.POTATO));
        marmite.getInventory().setStackInSlot(CookingPotBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BOWL));
        helper.onEachTick(() -> be.energy().receiveEnergy(HotPlateBlockEntity.MAX_RECEIVE, false));
        helper.succeedWhen(() -> helper.assertTrue(
                marmite.getInventory().getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT).is(ModItems.BEEF_STEW.get()),
                "le ragoût de bœuf n'est pas encore servi"));
    }

    /** L'état se relit de la sauvegarde : l'énergie revient, la plaque reste allumée, chauffe et consomme encore. */
    @GameTest(template = "test/plancher", batch = "plaque", setupTicks = 20, timeoutTicks = 200)
    public static void lEtatSurvitAuRechargement(GameTestHelper helper) {
        HotPlateBlockEntity be = poser(helper, ModBlocks.COOKING_POT.get());
        alimenter(be);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(allumee(helper), "la plaque devait être allumée avant la sauvegarde");
            int energie = be.energy().getEnergyStored();
            CompoundTag tag = be.saveWithoutMetadata();
            be.drainEnergy(energie);
            helper.assertTrue(be.energy().getEnergyStored() == 0, "la réserve devait être vidée avant le rechargement");
            be.load(tag);
            helper.assertTrue(be.energy().getEnergyStored() == energie,
                    "l'énergie devait revenir : " + energie + " puis " + be.energy().getEnergyStored());
            helper.runAfterDelay(6, () -> {
                helper.assertTrue(allumee(helper) && marmite(helper, POS.above()).isHeated(),
                        "la plaque devait rester allumée et chauffer après le rechargement");
                helper.assertTrue(be.energy().getEnergyStored() < energie,
                        "la plaque devait continuer de consommer : " + energie + " puis " + be.energy().getEnergyStored());
                helper.succeed();
            });
        });
    }
}
