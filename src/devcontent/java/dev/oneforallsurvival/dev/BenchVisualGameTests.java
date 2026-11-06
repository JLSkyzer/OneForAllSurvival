package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.bench.EngineerBenchBlock;
import dev.oneforallsurvival.bench.PlayerWorkshop;
import dev.oneforallsurvival.bench.WorkshopEntry;
import dev.oneforallsurvival.bench.WorkshopSavedData;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** La recette, le butin et les états visibles de l'établi : ce que les textures et les modèles supposent du bloc. */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class BenchVisualGameTests {

    private static final ResourceLocation RECETTE = new ResourceLocation("oneforallsurvival", "engineer_bench");
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private BenchVisualGameTests() {}

    @GameTest(template = "test/plancher", batch = "etabli_visuels")
    public static void laRecetteResteAuManagerEtNeDemandeQueDuVanilla(GameTestHelper helper) {
        Recipe<?> recette = helper.getLevel().getServer().getRecipeManager().byKey(RECETTE).orElse(null);
        helper.assertTrue(recette != null, "la recette de l'établi a quitté le manager");
        helper.assertTrue(BenchCatalog.current().byId(RECETTE).isEmpty(), "la recette de l'établi est au catalogue");
        helper.assertTrue(recette.getResultItem(helper.getLevel().registryAccess())
                .is(BenchRegistry.ENGINEER_BENCH_ITEM.get()), "résultat inattendu");
        int cases = 0;
        for (Ingredient i : recette.getIngredients()) {
            if (i.isEmpty()) {
                continue;
            }
            cases++;
            JsonObject json = i.toJson().getAsJsonObject();
            String id = json.has("item") ? json.get("item").getAsString() : json.get("tag").getAsString();
            helper.assertTrue(id.startsWith("minecraft:"), "ingrédient hors vanilla : " + id);
        }
        helper.assertTrue(cases == 8,
                "la recette doit compter 8 ingrédients (3 planches, 2 fer, 2 cuivre, 1 table) : " + cases);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_visuels")
    public static void casserLEtabliRendLObjetEtLaFileResteIntacte(GameTestHelper helper) {
        helper.setBlock(POS, BenchRegistry.ENGINEER_BENCH.get());
        UUID joueur = UUID.randomUUID();
        var serveur = helper.getLevel().getServer();
        PlayerWorkshop atelier = WorkshopSavedData.get(serveur).of(joueur);
        UUID id = UUID.randomUUID();
        atelier.add(new WorkshopEntry(id, new ResourceLocation("oneforallsurvival", "test_bench_shaped"),
                new ItemStack(Items.STICK, 2), List.of(new ItemStack(Items.OAK_PLANKS)), List.of(), 200, 0.0,
                Level.OVERWORLD, helper.absolutePos(POS), false));
        BlockState etat = helper.getBlockState(POS);
        List<ItemStack> butin = Block.getDrops(etat, helper.getLevel(), helper.absolutePos(POS),
                helper.getBlockEntity(POS));
        if (butin.size() != 1 || !butin.get(0).is(BenchRegistry.ENGINEER_BENCH_ITEM.get())
                || butin.get(0).getCount() != 1) {
            atelier.cancel(id);
            helper.fail("butin inattendu : " + butin);
        }
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        // L'objet lâché entre dans le monde au tick suivant.
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(helper.getBlockState(POS).isAir(), "l'établi n'est pas cassé");
                helper.assertItemEntityPresent(BenchRegistry.ENGINEER_BENCH_ITEM.get(), POS, 2.0);
                helper.assertTrue(atelier.queue().size() == 1 && atelier.queue().get(0).id().equals(id),
                        "la file a changé avec le bloc : " + atelier.queue());
            } finally {
                atelier.cancel(id);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "etabli_visuels")
    public static void unChangementDEtatOuDOrientationGardeLeBlockEntity(GameTestHelper helper) {
        helper.setBlock(POS, BenchRegistry.ENGINEER_BENCH.get());
        BlockEntity avant = helper.getBlockEntity(POS);
        helper.assertTrue(avant != null, "pas de block entity");
        BlockPos abs = helper.absolutePos(POS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (MachineState s : MachineState.values()) {
                BlockState voulu = helper.getBlockState(POS).setValue(EngineerBenchBlock.FACING, d)
                        .setValue(ElectricMachineBlock.STATE, s);
                helper.getLevel().setBlock(abs, voulu, Block.UPDATE_CLIENTS);
                helper.assertTrue(helper.getBlockEntity(POS) == avant, "le block entity a changé pour " + d + " " + s);
                int lumiere = helper.getBlockState(POS).getLightEmission(helper.getLevel(), abs);
                helper.assertTrue((s == MachineState.RUNNING) == (lumiere > 0),
                        "lumière " + lumiere + " pour l'état " + s);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "etabli_visuels")
    public static void laFormeEtLaCollisionSuiventLOrientation(GameTestHelper helper) {
        BlockState bloc = BenchRegistry.ENGINEER_BENCH.get().defaultBlockState();
        VoxelShape nord = shape(helper, bloc, Direction.NORTH);
        VoxelShape est = shape(helper, bloc, Direction.EAST);
        VoxelShape sud = shape(helper, bloc, Direction.SOUTH);
        VoxelShape ouest = shape(helper, bloc, Direction.WEST);
        for (VoxelShape s : List.of(nord, est, sud, ouest)) {
            helper.assertTrue(!s.isEmpty() && s.max(Direction.Axis.Y) == 1.0 && s.min(Direction.Axis.Y) == 0.0,
                    "la forme doit remplir un bloc en hauteur : " + s.bounds());
            helper.assertTrue(s.max(Direction.Axis.X) == 1.0 && s.min(Direction.Axis.X) == 0.0
                    && s.max(Direction.Axis.Z) == 1.0 && s.min(Direction.Axis.Z) == 0.0, "emprise : " + s.bounds());
        }
        // L'étau (x 12 à 14, z 3 à 4, au nord) tourne avec l'établi : "y": 90 par quart de tour.
        helper.assertTrue(touche(nord, 12, 13, 3, 14, 14, 4), "étau absent au nord");
        helper.assertTrue(touche(est, 12, 13, 12, 13, 14, 14), "étau absent à l'est");
        helper.assertTrue(touche(sud, 2, 13, 12, 4, 14, 13), "étau absent au sud");
        helper.assertTrue(touche(ouest, 3, 13, 2, 4, 14, 4), "étau absent à l'ouest");
        helper.assertTrue(!touche(nord, 2, 13, 3, 4, 14, 4), "l'étau ne doit pas être à gauche");
        // Entre les tréteaux, sous le plateau et au-dessus de l'étagère de la bobine : vide.
        helper.assertTrue(!touche(nord, 4, 0, 3, 12, 3, 13), "le dessous doit rester ouvert");
        helper.succeed();
    }

    private static VoxelShape shape(GameTestHelper helper, BlockState bloc, Direction d) {
        BlockState etat = bloc.setValue(EngineerBenchBlock.FACING, d);
        VoxelShape forme = etat.getShape(helper.getLevel(), helper.absolutePos(POS));
        VoxelShape collision = etat.getCollisionShape(helper.getLevel(), helper.absolutePos(POS));
        helper.assertTrue(!Shapes.joinIsNotEmpty(forme, collision, BooleanOp.NOT_SAME),
                "collision différente de la forme");
        return forme;
    }

    private static boolean touche(VoxelShape forme, double x1, double y1, double z1, double x2, double y2,
            double z2) {
        return Shapes.joinIsNotEmpty(forme, Block.box(x1, y1, z1, x2, y2, z2), BooleanOp.AND);
    }
}
