package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.repair.GalvanicBathBlockEntity;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Le bain galvanique, éprouvé avec la recette d'essai de {@code devcontent} : une pièce d'essai plus un
 * lingot de fer donnent une pièce d'essai traitée en 20 ticks de travail, à 20 FE par tick. Chaque
 * contrôle a son propre tick : l'ordre des rappels d'un même tick n'est pas garanti.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class BainGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private BainGameTests() {}

    private static Item objet(String nom) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("oneforallsurvival", nom));
    }

    private static GalvanicBathBlockEntity poser(GameTestHelper helper) {
        helper.setBlock(POS, RepairRegistry.GALVANIC_BATH.get());
        GalvanicBathBlockEntity be = (GalvanicBathBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity du bain");
        return be;
    }

    private static void charger(GalvanicBathBlockEntity be, int pieces, int zinc) {
        be.inventory().setStackInSlot(0, new ItemStack(objet("test_part"), pieces));
        be.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, zinc));
    }

    private static void remplir(GalvanicBathBlockEntity be) {
        for (int i = 0; i < 20; i++) {
            be.energy().receiveEnergy(200, false);
        }
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void leBainTraiteUnePieceEtPaieSonZinc(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        charger(be, 2, 2);
        remplir(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(30, () -> {
            ItemStack sortie = be.inventory().getStackInSlot(2);
            helper.assertTrue(sortie.is(objet("test_part_done")) && sortie.getCount() == 1,
                    "une pièce traitée attendue en sortie, vu " + sortie);
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1,
                    "l'entrée devait perdre une pièce, vu " + be.inventory().getStackInSlot(0));
            helper.assertTrue(be.inventory().getStackInSlot(1).getCount() == 1,
                    "le zinc devait perdre un lingot, vu " + be.inventory().getStackInSlot(1));
            int consomme = avant - be.energy().getEnergyStored();
            helper.assertTrue(consomme >= 400, "20 ticks à 20 FE : 400 FE au moins, vus " + consomme);
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void sansCourantLaProgressionAttend(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        charger(be, 1, 1);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(be.progress() == 0, "rien ne devait avancer sans courant, vu " + be.progress());
        });
        // 100 FE à 20 par tick : cinq ticks de travail, puis plus rien.
        helper.runAfterDelay(5, () -> be.energy().receiveEnergy(100, false));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.progress() == 5, "cinq ticks de travail attendus, vus " + be.progress());
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(be.progress() == 5, "la progression devait attendre le courant, vu " + be.progress());
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1
                    && be.inventory().getStackInSlot(1).getCount() == 1, "l'entrée et le zinc devaient rester");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void sansZincRienNeDemarre(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        charger(be, 1, 0);
        remplir(be);
        int avant = be.energy().getEnergyStored();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(be.progress() == 0, "sans zinc rien ne devait avancer, vu " + be.progress());
            helper.assertTrue(be.energy().getEnergyStored() == avant, "sans zinc aucun courant ne se paie");
            helper.assertTrue(be.state() == MachineState.STOPPED, "STOPPED attendu, vu " + be.state());
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1, "la pièce devait rester");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void enPanneRienNeSePerd(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        charger(be, 1, 1);
        remplir(be);
        helper.runAfterDelay(6, () -> be.breakDown(FaultType.FUSE));
        int[] releve = new int[2];
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(be.state() == MachineState.FAULT, "FAULT attendu, vu " + be.state());
            helper.assertTrue(be.progress() > 0, "le bain avait commencé, vu " + be.progress());
            releve[0] = be.progress();
            releve[1] = be.energy().getEnergyStored();
        });
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(be.progress() == releve[0], "la progression devait être gardée en panne");
            helper.assertTrue(be.energy().getEnergyStored() == releve[1], "rien ne se consomme en panne");
            helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1
                    && be.inventory().getStackInSlot(1).getCount() == 1
                    && be.inventory().getStackInSlot(2).isEmpty(), "pièce et zinc devaient rester, sans sortie");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void retirerLaPieceNeCouteRien(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        charger(be, 1, 1);
        remplir(be);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(be.progress() > 0, "le bain devait avoir avancé, vu " + be.progress());
            ItemStack retiree = be.inventory().extractItem(0, 64, false);
            helper.assertTrue(retiree.is(objet("test_part")) && retiree.getCount() == 1, "la pièce devait sortir");
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(be.progress() == 0, "la progression devait revenir à 0, vue " + be.progress());
            helper.assertTrue(be.inventory().getStackInSlot(1).getCount() == 1, "le zinc devait rester intact");
            helper.assertTrue(be.inventory().getStackInSlot(2).isEmpty(), "aucune sortie attendue");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void casserLeBainRendSonContenu(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        be.inventory().setStackInSlot(0, new ItemStack(objet("test_part"), 3));
        be.inventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 2));
        be.inventory().setStackInSlot(2, new ItemStack(objet("test_part_done"), 1));
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(total(objets, objet("test_part")) == 3, "3 pièces attendues au sol");
            helper.assertTrue(total(objets, Items.IRON_INGOT) == 2, "2 lingots attendus au sol");
            helper.assertTrue(total(objets, objet("test_part_done")) == 1, "1 pièce traitée attendue au sol");
            helper.assertTrue(total(objets, RepairRegistry.GALVANIC_BATH_ITEM.get()) == 1,
                    "le bloc lui-même devait tomber");
            helper.succeed();
        });
    }

    private static int total(List<ItemEntity> objets, Item item) {
        return objets.stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = "test/plancher", batch = "bain")
    public static void lesEntonnoirsRespectentLesFaces(GameTestHelper helper) {
        GalvanicBathBlockEntity be = poser(helper);
        ItemStack piece = new ItemStack(objet("test_part"), 1);
        ItemStack lingot = new ItemStack(Items.IRON_INGOT, 1);
        ItemStack faite = new ItemStack(objet("test_part_done"), 1);
        be.inventory().setStackInSlot(2, faite.copy());

        IItemHandler dessous = handler(be, Direction.DOWN);
        helper.assertTrue(dessous.insertItem(0, piece.copy(), false).getCount() == 1, "le dessous n'insère pas la pièce");
        helper.assertTrue(dessous.insertItem(0, lingot.copy(), false).getCount() == 1, "le dessous n'insère pas le zinc");
        helper.assertTrue(dessous.insertItem(0, faite.copy(), false).getCount() == 1, "le dessous n'insère pas la sortie");
        ItemStack sortie = dessous.extractItem(0, 64, false);
        helper.assertTrue(sortie.is(objet("test_part_done")), "le dessous extrait la sortie, vu " + sortie);

        be.inventory().setStackInSlot(0, piece.copy());
        be.inventory().setStackInSlot(1, lingot.copy());
        helper.assertTrue(dessous.getSlots() == 1, "le dessous ne voit que la sortie");
        helper.assertTrue(dessous.extractItem(0, 64, false).isEmpty(), "plus de sortie à extraire");
        helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 1
                && be.inventory().getStackInSlot(1).getCount() == 1, "le dessous ne touche ni la pièce ni le zinc");

        IItemHandler dessus = handler(be, Direction.UP);
        helper.assertTrue(dessus.insertItem(0, lingot.copy(), false).getCount() == 1, "le dessus refuse le zinc");
        helper.assertTrue(dessus.insertItem(0, piece.copy(), false).isEmpty(), "le dessus accepte la pièce");
        helper.assertTrue(dessus.extractItem(0, 64, false).isEmpty(), "le dessus n'extrait rien");

        IItemHandler cote = handler(be, Direction.NORTH);
        helper.assertTrue(cote.insertItem(0, piece.copy(), false).getCount() == 1, "le côté refuse la pièce");
        helper.assertTrue(cote.insertItem(0, lingot.copy(), false).isEmpty(), "le côté accepte le zinc");
        helper.assertTrue(cote.extractItem(0, 64, false).isEmpty(), "le côté n'extrait rien");
        helper.assertTrue(be.inventory().getStackInSlot(0).getCount() == 2
                && be.inventory().getStackInSlot(1).getCount() == 2, "pièce et zinc entrés par leurs faces");
        helper.succeed();
    }

    private static IItemHandler handler(GalvanicBathBlockEntity be, Direction side) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(
                () -> new IllegalStateException("pas de capability objets au côté " + side));
    }
}
