package dev.oneforallsurvival.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.explore.CacheBlockEntity;
import dev.oneforallsurvival.explore.CacheIndex;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.salvage.SalvageRegistry;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les caches enfouies : indexées à la pose, butin de leur table à la casse en survie (une fois, sans leur bloc), rien en
 * créatif, table réglable par commande. Les sept tests partagent un lot (huit au plus) ; chacun a sa case.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CacheGameTests {

    private static final String LOT = "cache";
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);
    private static final Set<Item> BUTIN_PAR_DEFAUT = Set.of(SalvageRegistry.SCRAP_METAL.get(),
            SalvageRegistry.AVIATION_WIRE.get(), SalvageRegistry.INSTRUMENT_CORE.get(),
            SalvageRegistry.BATTERY_CELL.get(), Items.IRON_NUGGET);
    private static final ResourceLocation AUTRE_TABLE = new ResourceLocation("minecraft", "chests/simple_dungeon");

    private CacheGameTests() {}

    private static JoueurDeTest joueur(GameTestHelper helper, GameType mode) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "Fouilleur", helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        j.joueur().gameMode.changeGameModeForPlayer(mode);
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(4.0));
    }

    private static void viderObjets(GameTestHelper helper) {
        objets(helper).forEach(ItemEntity::discard);
    }

    private static CacheIndex index(GameTestHelper helper) {
        return CacheIndex.get(helper.getLevel());
    }

    private static CacheBlockEntity cache(GameTestHelper helper) {
        helper.assertTrue(helper.getBlockEntity(POS) instanceof CacheBlockEntity, "une CacheBlockEntity était attendue");
        return (CacheBlockEntity) helper.getBlockEntity(POS);
    }

    private static int commande(GameTestHelper helper, String ligne) throws CommandSyntaxException {
        var serveur = helper.getLevel().getServer();
        CommandSourceStack source = serveur.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2)
                .withSuppressedOutput();
        return serveur.getCommands().getDispatcher().execute(ligne, source);
    }

    private static String coords(GameTestHelper helper) {
        BlockPos p = helper.absolutePos(POS);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void poseeElleEstIndexee(GameTestHelper helper) {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        helper.assertTrue(index(helper).contains(helper.absolutePos(POS)), "la cache posée devait être indexée");
        helper.assertTrue(index(helper).nearest(helper.absolutePos(POS).offset(3, 0, 0), 5.0)
                .equals(java.util.Optional.of(helper.absolutePos(POS))), "nearest devait la trouver à 3 blocs");
        helper.assertTrue(index(helper).nearest(helper.absolutePos(POS).offset(8, 0, 0), 5.0).isEmpty()
                || !index(helper).nearest(helper.absolutePos(POS).offset(8, 0, 0), 5.0).get().equals(helper.absolutePos(POS)),
                "nearest ne devait pas la trouver à 8 blocs pour une portée de 5");
        helper.setBlock(POS, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void casseeEnSurvieElleDonneSonButin(GameTestHelper helper) {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        viderObjets(helper);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, GameType.SURVIVAL);
            boolean casse = j.joueur().gameMode.destroyBlock(helper.absolutePos(POS));
            helper.assertTrue(casse, "destroyBlock devait réussir");
            helper.assertTrue(helper.getBlockState(POS).is(Blocks.AIR), "la cache devait disparaître");
            List<ItemEntity> butin = objets(helper);
            helper.assertTrue(!butin.isEmpty(), "du butin devait tomber");
            for (ItemEntity e : butin) {
                helper.assertTrue(BUTIN_PAR_DEFAUT.contains(e.getItem().getItem()),
                        "objet hors de la table par défaut : " + e.getItem());
                helper.assertTrue(e.getItem().getItem() != ExploreRegistry.CACHE_ITEM.get(),
                        "la cache ne doit pas se lâcher elle-même");
            }
            viderObjets(helper);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void casseeEnCreatifRien(GameTestHelper helper) {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        viderObjets(helper);
        JoueurDeTest j = null;
        try {
            j = joueur(helper, GameType.CREATIVE);
            j.joueur().gameMode.destroyBlock(helper.absolutePos(POS));
            helper.assertTrue(helper.getBlockState(POS).is(Blocks.AIR), "la cache devait disparaître");
            helper.assertTrue(objets(helper).isEmpty(), "rien ne devait tomber en créatif : " + objets(helper));
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void casseeElleQuitteLIndex(GameTestHelper helper) {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        helper.assertTrue(index(helper).contains(helper.absolutePos(POS)), "indexée après la pose");
        helper.setBlock(POS, Blocks.AIR);
        helper.assertTrue(!index(helper).contains(helper.absolutePos(POS)), "retirée de l'index après la casse");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laTableSeRegleParCommande(GameTestHelper helper) throws CommandSyntaxException {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        helper.assertTrue(cache(helper).lootTable().equals(CacheBlockEntity.DEFAULT_TABLE), "table par défaut attendue");
        int r = commande(helper, "cache loot " + coords(helper) + " " + AUTRE_TABLE);
        helper.assertTrue(r == 1, "la commande devait réussir, résultat " + r);
        helper.assertTrue(cache(helper).lootTable().equals(AUTRE_TABLE), "table réglée attendue, vue " + cache(helper).lootTable());
        helper.assertTrue(commande(helper, "cache list") >= 1, "cache list devait voir la cache");
        helper.setBlock(POS, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneTableInconnueEstRefusee(GameTestHelper helper) throws CommandSyntaxException {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        int r = commande(helper, "cache loot " + coords(helper) + " oneforallsurvival:caches/n_existe_pas");
        helper.assertTrue(r == 0, "la commande devait échouer, résultat " + r);
        helper.assertTrue(cache(helper).lootTable().equals(CacheBlockEntity.DEFAULT_TABLE),
                "la table devait rester celle par défaut, vue " + cache(helper).lootTable());
        helper.setBlock(POS, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void laTableSurvitAuRechargement(GameTestHelper helper) {
        helper.setBlock(POS, ExploreRegistry.CACHE.get());
        cache(helper).setLootTable(AUTRE_TABLE);
        CompoundTag tag = cache(helper).saveWithoutMetadata();
        CacheBlockEntity copie = new CacheBlockEntity(helper.absolutePos(POS), helper.getBlockState(POS));
        copie.load(tag);
        helper.assertTrue(copie.lootTable().equals(AUTRE_TABLE), "table rechargée attendue, vue " + copie.lootTable());
        CompoundTag vide = new CompoundTag();
        CacheBlockEntity neuve = new CacheBlockEntity(helper.absolutePos(POS), helper.getBlockState(POS));
        neuve.load(vide);
        helper.assertTrue(neuve.lootTable().equals(CacheBlockEntity.DEFAULT_TABLE), "sans donnée : table par défaut");
        // L'index se sauve et se recharge avec la dimension.
        CacheIndex rechargee = CacheIndex.get(helper.getLevel());
        CompoundTag sauve = rechargee.save(new CompoundTag());
        helper.assertTrue(sauve.getLongArray("Positions").length == rechargee.size(), "l'index sauvé devait garder toutes ses positions");
        helper.setBlock(POS, Blocks.AIR);
        helper.succeed();
    }
}
