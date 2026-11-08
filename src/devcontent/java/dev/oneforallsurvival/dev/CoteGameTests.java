package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.coast.CoastDetector;
import dev.oneforallsurvival.coast.CoastSavedData;
import dev.oneforallsurvival.machine.MachineConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La détection de l'eau salée et son effet sur l'usure. Le gabarit des tests est posé à une hauteur
 * quelconque : l'eau est placée à la hauteur absolue {@link MachineConfig#seaLevel()}, dans une cuvette
 * de pierre pour qu'elle ne s'écoule pas, et chaque test remet le monde comme il l'a trouvé.
 *
 * <p>Le détecteur et les zones sont globaux au serveur : chaque test a son propre lot (les lots passent
 * l'un après l'autre) pour ne jamais voir l'eau ou les zones d'un voisin.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CoteGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos VOISINE = POS.east(2);
    private static final Block EAU = Blocks.WATER;

    private CoteGameTests() {}

    /** Ce que la cuvette a remplacé, pour le rendre. */
    static Map<BlockPos, BlockState> poserEau(ServerLevel level, BlockPos eau, boolean source) {
        Map<BlockPos, BlockState> avant = new LinkedHashMap<>();
        avant.put(eau, level.getBlockState(eau));
        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST,
                Direction.WEST}) {
            BlockPos p = eau.relative(d);
            avant.put(p, level.getBlockState(p));
            level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState etat = source ? EAU.defaultBlockState() : EAU.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
        level.setBlock(eau, etat, Block.UPDATE_CLIENTS);
        return avant;
    }

    static void rendre(ServerLevel level, Map<BlockPos, BlockState> avant) {
        avant.forEach((p, etat) -> level.setBlock(p, etat, Block.UPDATE_CLIENTS));
    }

    /** Une case d'eau à {@code decalage} blocs à l'est de la machine, au niveau de la mer absolu. */
    private static BlockPos eauA(GameTestHelper helper, int decalage) {
        BlockPos machine = helper.absolutePos(POS);
        return new BlockPos(machine.getX() + decalage, MachineConfig.seaLevel(), machine.getZ());
    }

    private static boolean cotier(GameTestHelper helper) {
        CoastDetector.clearCache();
        return CoastDetector.isCoastal(helper.getLevel(), helper.absolutePos(POS));
    }

    @GameTest(template = "test/plancher", batch = "cote_proche")
    public static void uneSourceProcheRendCotier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(!cotier(helper), "sans eau, le lieu ne devait pas être côtier");
        Map<BlockPos, BlockState> avant = poserEau(level, eauA(helper, 3), true);
        try {
            helper.assertTrue(cotier(helper), "une source proche devait rendre le lieu côtier");
        } finally {
            rendre(level, avant);
        }
        helper.assertTrue(!cotier(helper), "l'eau retirée, le lieu ne devait plus être côtier");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "cote_courante")
    public static void uneEauCouranteNeComptePas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<BlockPos, BlockState> avant = poserEau(level, eauA(helper, 3), false);
        try {
            helper.assertTrue(level.getBlockState(eauA(helper, 3)).is(EAU), "l'eau courante devait être posée");
            helper.assertTrue(!cotier(helper), "une eau courante ne devait pas rendre le lieu côtier");
        } finally {
            rendre(level, avant);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "cote_hors_rayon")
    public static void uneSourceHorsRayonNeCompte(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<BlockPos, BlockState> avant = poserEau(level, eauA(helper, MachineConfig.coastRadius() + 2), true);
        try {
            helper.assertTrue(!cotier(helper), "une source hors du rayon ne devait pas compter");
        } finally {
            rendre(level, avant);
        }
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "cote_zone")
    public static void uneZoneExclutLeLac(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoastSavedData donnees = CoastSavedData.get(level.getServer());
        ResourceLocation dimension = level.dimension().location();
        BlockPos ici = helper.absolutePos(POS);
        donnees.remove("test_loin");
        donnees.remove("test_ici");
        Map<BlockPos, BlockState> avant = poserEau(level, eauA(helper, 3), true);
        try {
            helper.assertTrue(cotier(helper), "la source proche devait rendre le lieu côtier");
            donnees.add("test_loin", dimension, ici.offset(500, 0, 500), ici.offset(510, 10, 510));
            helper.assertTrue(!cotier(helper), "une zone ailleurs devait exclure le lac proche");
            donnees.add("test_ici", dimension, ici.offset(-1, -1, -1), ici.offset(1, 1, 1));
            helper.assertTrue(cotier(helper), "une position dans une zone devait être côtière");
            donnees.remove("test_ici");
            donnees.remove("test_loin");
            helper.assertTrue(cotier(helper), "les zones retirées, la source devait de nouveau compter");
        } finally {
            donnees.remove("test_ici");
            donnees.remove("test_loin");
            rendre(level, avant);
            CoastDetector.clearCache();
        }
        helper.succeed();
    }

    /** Mesure sur {@code duree} ticks la perte d'état de deux machines côte à côte. */
    private static void comparer(GameTestHelper helper, TestMachineBlockEntity a, TestMachineBlockEntity b,
            String zone, double attendu, String message) {
        CoastSavedData donnees = CoastSavedData.get(helper.getLevel().getServer());
        for (TestMachineBlockEntity be : new TestMachineBlockEntity[] {a, b}) {
            for (int i = 0; i < 10; i++) {
                be.energy().receiveEnergy(100, false);
            }
        }
        helper.runAfterDelay(60, () -> {
            try {
                double perteA = 100.0 - a.condition();
                double perteB = 100.0 - b.condition();
                helper.assertTrue(perteB > 0, "la machine B devait s'user, perte " + perteB);
                double rapport = perteA / perteB;
                helper.assertTrue(rapport > attendu - 0.1 && rapport < attendu + 0.1,
                        message + " : rapport attendu " + attendu + ", vu " + rapport
                                + " (A " + perteA + ", B " + perteB + ")");
            } finally {
                donnees.remove(zone);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "cote_usure")
    public static void surLaCoteLaMachineSUseDeuxFoisPlusVite(GameTestHelper helper) {
        CoastSavedData donnees = CoastSavedData.get(helper.getLevel().getServer());
        donnees.remove("test_usure");
        BlockPos ici = helper.absolutePos(POS);
        // Une zone réduite à la case de A : B, à côté, reste hors zone donc hors côte.
        donnees.add("test_usure", helper.getLevel().dimension().location(), ici, ici);
        helper.setBlock(POS, DevBlocks.testMachine());
        helper.setBlock(VOISINE, DevBlocks.testMachine());
        TestMachineBlockEntity a = (TestMachineBlockEntity) helper.getBlockEntity(POS);
        TestMachineBlockEntity b = (TestMachineBlockEntity) helper.getBlockEntity(VOISINE);
        helper.assertTrue(a != null && b != null, "pas de block entity de la machine d'essai");
        comparer(helper, a, b, "test_usure", MachineConfig.coastalMultiplier(),
                "la machine côtière devait s'user plus vite");
    }

    @GameTest(template = "test/plancher", batch = "cote_protegee")
    public static void uneMachineProtegeeIgnoreLaCorrosion(GameTestHelper helper) {
        CoastSavedData donnees = CoastSavedData.get(helper.getLevel().getServer());
        donnees.remove("test_protegee");
        BlockPos ici = helper.absolutePos(POS);
        // Les deux machines sont en zone côtière ; seule la protection les distingue.
        donnees.add("test_protegee", helper.getLevel().dimension().location(), ici, ici.east(2));
        helper.setBlock(POS, DevBlocks.testMachine());
        helper.setBlock(VOISINE, DevBlocks.testMachine());
        TestMachineBlockEntity nue = (TestMachineBlockEntity) helper.getBlockEntity(POS);
        TestMachineBlockEntity protegee = (TestMachineBlockEntity) helper.getBlockEntity(VOISINE);
        helper.assertTrue(nue != null && protegee != null, "pas de block entity de la machine d'essai");
        helper.assertTrue(protegee.repair(protegee.nextFault(), true) != null && protegee.isProtected(),
                "la machine devait être traitée");
        comparer(helper, nue, protegee, "test_protegee", MachineConfig.coastalMultiplier(),
                "la machine protégée devait ignorer la corrosion");
    }
}
