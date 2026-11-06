package dev.oneforallsurvival.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Garde-fous du pack de développement. Ces essais ne prouvent quelque chose que lancés avec le
 * pack complet (`-PwithPack`) : sans lui, aucun mod tiers n'est chargé et ils passent trivialement.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PackGameTests {

    private PackGameTests() {}

    /**
     * Le mixin d'ESL (embarqué dans New Age) s'applique à tout `getCapability(ENERGY, ...)`. Il
     * appelle des méthodes de BlockEntity sous leurs noms de développement : si l'ESL chargé est
     * resté en noms SRG, la requête plante en NoSuchMethodError, même sur un coffre vanilla.
     */
    @GameTest(template = "test/plancher", batch = "pack_esl")
    public static void leMixinEslSAppliqueSansPlanter(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity pour le coffre");
        // Un coffre n'expose pas d'énergie : une réponse vide est la bonne issue, un plantage non.
        boolean present = be.getCapability(ForgeCapabilities.ENERGY, null).isPresent();
        helper.assertTrue(!present, "un coffre ne doit pas exposer d'énergie");
        helper.succeed();
    }
}
