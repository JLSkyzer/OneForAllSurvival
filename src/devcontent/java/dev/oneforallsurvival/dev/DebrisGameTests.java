package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.salvage.SalvageRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

/** Les débris d'avion : cassables seulement à l'outil, butin propre à chaque bloc, insensibles aux explosions. */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DebrisGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);

    private DebrisGameTests() {}

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

    @GameTest(template = "test/plancher", batch = "debris_sans_outil")
    public static void sansOutilLeDebrisNeSeCassePas(GameTestHelper helper) {
        helper.setBlock(POS, SalvageRegistry.DEBRIS_FUSELAGE.get());
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Fouilleur");
            ServerPlayer p = j.joueur();
            BlockPos abs = helper.absolutePos(POS);
            var etat = helper.getBlockState(POS);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            float vide = etat.getDestroyProgress(p, helper.getLevel(), abs);
            helper.assertTrue(vide == 0.0F, "main vide : progression 0 attendue, vue " + vide);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
            float pelle = etat.getDestroyProgress(p, helper.getLevel(), abs);
            helper.assertTrue(pelle == 0.0F, "pelle : progression 0 attendue, vue " + pelle);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            float pioche = etat.getDestroyProgress(p, helper.getLevel(), abs);
            helper.assertTrue(pioche > 0.0F, "pioche : progression positive attendue, vue " + pioche);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "debris_butin")
    public static void chaqueDebrisDonneSonButinPuisDisparait(GameTestHelper helper) {
        List<RegistryObject<Block>> debris = List.of(SalvageRegistry.DEBRIS_FUSELAGE, SalvageRegistry.DEBRIS_WIRING,
                SalvageRegistry.DEBRIS_INSTRUMENTS, SalvageRegistry.DEBRIS_BATTERY, SalvageRegistry.DEBRIS_CABIN);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, "Fouilleur");
            ServerPlayer p = j.joueur();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            for (RegistryObject<Block> bloc : debris) {
                helper.setBlock(POS, bloc.get());
                boolean casse = p.gameMode.destroyBlock(helper.absolutePos(POS));
                helper.assertTrue(casse, bloc.getId() + " : destroyBlock devait réussir");
                helper.assertTrue(helper.getBlockState(POS).is(Blocks.AIR), bloc.getId() + " devait disparaître");
                AABB zone = new AABB(helper.absolutePos(POS)).inflate(3.0);
                List<ItemEntity> objets = helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone);
                helper.assertTrue(!objets.isEmpty(), bloc.getId() + " devait laisser du butin");
                for (ItemEntity e : objets) {
                    helper.assertTrue(e.getItem().getItem() != bloc.get().asItem(),
                            bloc.getId() + " ne doit pas se laisser lui-même");
                    e.discard();
                }
            }
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "debris_explosion")
    public static void uneExplosionNeDetruitPasLeDebris(GameTestHelper helper) {
        helper.setBlock(POS, SalvageRegistry.DEBRIS_FUSELAGE.get());
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(POS.east(2)));
        helper.getLevel().explode(null, centre.x, centre.y, centre.z, 4.0F, Level.ExplosionInteraction.TNT);
        helper.assertTrue(helper.getBlockState(POS).is(SalvageRegistry.DEBRIS_FUSELAGE.get()),
                "le débris devait survivre à l'explosion");
        helper.succeed();
    }

    /** Ni le wither ni le dragon n'emportent une épave qu'une explosion ne déloge pas. */
    @GameTest(template = "test/plancher", batch = "debris_boss")
    public static void leWitherEtLeDragonNeCassentPasLesDebris(GameTestHelper helper) {
        for (RegistryObject<Block> debris : List.of(SalvageRegistry.DEBRIS_FUSELAGE, SalvageRegistry.DEBRIS_WIRING,
                SalvageRegistry.DEBRIS_INSTRUMENTS, SalvageRegistry.DEBRIS_BATTERY, SalvageRegistry.DEBRIS_CABIN)) {
            var etat = debris.get().defaultBlockState();
            helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE), debris.getId() + " doit résister au wither");
            helper.assertTrue(etat.is(BlockTags.DRAGON_IMMUNE), debris.getId() + " doit résister au dragon");
        }
        helper.succeed();
    }
}
