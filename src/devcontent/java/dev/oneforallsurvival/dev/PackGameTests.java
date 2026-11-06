package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.repair.GalvanicRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

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
        ModList mods = ModList.get();
        // Sans le pack (pas de New Age, donc pas d'ESL), il n'y a rien à prouver : on passe.
        if (!mods.isLoaded("createaddition")) {
            helper.succeed();
            return;
        }
        // Avec le pack, ESL doit être chargé : sinon un coffre sans plantage ne prouverait rien.
        helper.assertTrue(mods.isLoaded("esl"), "le pack est chargé mais pas ESL : le mixin n'est pas testé");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity be = helper.getBlockEntity(pos);
        helper.assertTrue(be != null, "pas de block entity pour le coffre");
        // Un coffre n'expose pas d'énergie : une réponse vide est la bonne issue, un plantage non.
        boolean present = be.getCapability(ForgeCapabilities.ENERGY, null).isPresent();
        helper.assertTrue(!present, "un coffre ne doit pas exposer d'énergie");
        helper.succeed();
    }

    /**
     * Les quatre recettes du bain galvanique livrées avec le mod se chargent, chacune vers sa pièce
     * traitée. Avec Create, l'étiquette du zinc n'est pas vide : sans lui, le bain n'a rien à traiter
     * (ce que le pack, lui, doit prouver).
     */
    @GameTest(template = "test/plancher", batch = "pack_bain")
    public static void lesQuatreRecettesDuBainSontChargees(GameTestHelper helper) {
        boolean create = ModList.get().isLoaded("create");
        for (String nom : new String[] {"fuse", "coil", "bearing", "cleaning_kit"}) {
            ResourceLocation id = new ResourceLocation("oneforallsurvival", "galvanic/" + nom);
            var recette = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
            helper.assertTrue(recette instanceof GalvanicRecipe, "recette absente ou d'un autre type : " + id);
            GalvanicRecipe bain = (GalvanicRecipe) recette;
            helper.assertTrue(ForgeRegistries.ITEMS.getKey(bain.result().getItem())
                    .equals(new ResourceLocation("oneforallsurvival", "treated_" + nom)),
                    id + " devait donner treated_" + nom + ", vu " + bain.result());
            helper.assertTrue(bain.acceptsPiece(new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("oneforallsurvival", nom)))), id + " devait accepter la pièce " + nom);
            // Forge renvoie une barrière pour une étiquette vide : un vrai zinc n'en est pas une.
            helper.assertTrue(!create || !GalvanicRecipe.realStacks(bain.zinc()).isEmpty(),
                    "Create est chargé mais l'étiquette du zinc est vide : " + id);
            helper.assertTrue(!create || bain.acceptsZinc(new ItemStack(ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("create", "zinc_ingot")))), "le lingot de zinc de Create devait convenir : " + id);
        }
        helper.succeed();
    }
}
