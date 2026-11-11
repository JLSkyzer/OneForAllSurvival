package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.explore.ExploreConfig;
import dev.oneforallsurvival.explore.ExploreRegistry;
import dev.oneforallsurvival.explore.HeadlampItem;
import dev.oneforallsurvival.explore.HeadlampLightData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La lampe frontale : un bloc de lumière suit la tête du porteur et ne reste jamais derrière lui. Les joueurs sont de
 * vrais {@link JoueurDeTest}. Le niveau ne les anime pas : chaque test publie à chaque tick l'évènement de tick de
 * joueur de Forge ({@link #pulser}), ce qui exerce l'abonnement de la lampe et non un appel direct. Chaque test joue
 * dans une boîte fermée (pourtour et plafond), par lots de six tests au plus. Un joueur d'essai n'a pas de client :
 * il ne tombe pas, il reste où le test le met. Les deux lots rendent les joueurs restés connectés après un échec.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class LampeGameTests {

    private static final String LOT_A = "lampe_a";
    private static final String LOT_B = "lampe_b";
    private static final int PLAFOND = 4;
    /** Debout au centre de la boîte : les yeux sont dans la case (3, 2, 3). */
    private static final Vec3 CENTRE = new Vec3(3.5, 1, 3.5);
    private static final int CHARGE = 1000;

    /** Les joueurs ouverts par les tests du lot en cours. */
    private static final List<JoueurDeTest> OUVERTS = new ArrayList<>();

    private LampeGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    @AfterBatch(batch = LOT_A)
    public static void apresLeLotA(ServerLevel level) {
        rendreLesJoueurs();
    }

    @AfterBatch(batch = LOT_B)
    public static void apresLeLotB(ServerLevel level) {
        rendreLesJoueurs();
    }

    private static void rendreLesJoueurs() {
        for (JoueurDeTest j : OUVERTS) {
            quitter(j);
        }
        OUVERTS.clear();
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    /**
     * Dégage la boîte, la couvre et la ferme (voir {@code DoucheGameTests.degager} pour les causes : gravier du monde,
     * visiteurs, eau courante). On joue entre les colonnes 1 et 6, sous le plafond.
     */
    private static void enclos(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 6, 8));
        for (Entity reste : helper.getLevel().getEntities((Entity) null,
                new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()), e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean bord = x == 0 || x == 7 || z == 0 || z == 7;
                for (int y = 0; y < PLAFOND; y++) {
                    if (bord) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    } else if (y >= 1) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
                helper.setBlock(new BlockPos(x, PLAFOND, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 12, 1), Blocks.GRAVEL);
    }

    /** Un joueur en survie au centre de la boîte, avec ou sans lampe à la tête. */
    private static JoueurDeTest joueur(GameTestHelper helper, String nom, int fe) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(CENTRE));
        // Le nom est celui d'un joueur d'essai déjà passé : le serveur le replace à sa dernière position sauvegardée.
        j.joueur().moveTo(helper.absoluteVec(CENTRE));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        j.joueur().getInventory().clearContent();
        if (fe > 0) {
            j.joueur().setItemSlot(EquipmentSlot.HEAD, lampe(fe));
        }
        OUVERTS.add(j);
        return j;
    }

    /**
     * Publie à chaque tick, comme le ferait le niveau pour un vrai joueur, l'évènement de fin de tick de ces joueurs.
     * Posé au début du test (jamais depuis un rappel : voir tasks/lessons.md).
     */
    private static void pulser(GameTestHelper helper, JoueurDeTest... joueurs) {
        helper.onEachTick(() -> {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, j.joueur()));
                }
            }
        });
    }

    private static ItemStack lampe(int fe) {
        ItemStack pile = new ItemStack(ExploreRegistry.HEADLAMP.get());
        HeadlampItem.store(pile, fe);
        return pile;
    }

    private static ItemStack lampePortee(ServerPlayer p) {
        return p.getItemBySlot(EquipmentSlot.HEAD);
    }

    /** Le bloc des yeux du joueur, en position absolue. */
    private static BlockPos oeil(ServerPlayer p) {
        return BlockPos.containing(p.getEyePosition());
    }

    private static BlockState etat(GameTestHelper helper, BlockPos abs) {
        return helper.getLevel().getBlockState(abs);
    }

    private static void assertLumiere(GameTestHelper helper, BlockPos abs) {
        BlockState etat = etat(helper, abs);
        helper.assertTrue(etat.is(Blocks.LIGHT), "un bloc de lumière attendu en " + abs + ", vu " + etat);
    }

    private static void assertSansLumiere(GameTestHelper helper, BlockPos abs) {
        BlockState etat = etat(helper, abs);
        helper.assertTrue(!etat.is(Blocks.LIGHT), "aucun bloc de lumière attendu en " + abs + ", vu " + etat);
    }

    private static HeadlampLightData notes(GameTestHelper helper) {
        return HeadlampLightData.get(helper.getLevel());
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void porteeElleEclaire(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeEclaire", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    BlockState etat = etat(helper, oeil(p));
                    helper.assertTrue(etat.getValue(LightBlock.LEVEL) == ExploreConfig.headlampLight(),
                            "niveau 14 attendu, vu " + etat.getValue(LightBlock.LEVEL));
                    helper.assertTrue(ExploreConfig.headlampLight() == 14, "la valeur de départ est 14");
                    helper.assertTrue(!etat.getValue(LightBlock.WATERLOGGED), "dans l'air, le bloc n'est pas imbibé");
                    helper.assertTrue(notes(helper).isOurs(oeil(p)), "le bloc posé doit être noté");
                    int depense = CHARGE - HeadlampItem.stored(lampePortee(p));
                    helper.assertTrue(depense > 0 && depense % ExploreConfig.headlampEnergy() == 0,
                            "la charge devait baisser par pas de " + ExploreConfig.headlampEnergy() + ", dépensé " + depense);
                    quitter(j);
                })
                .thenWaitUntil(() -> assertSansLumiere(helper, helper.absolutePos(new BlockPos(3, 2, 3))))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void enMarchantLAncienneLumiereDisparait(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeMarche", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] ancien = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    ancien[0] = oeil(p);
                    p.moveTo(helper.absoluteVec(new Vec3(5.5, 1, 5.5)));
                })
                .thenWaitUntil(() -> {
                    assertLumiere(helper, oeil(p));
                    helper.assertTrue(!oeil(p).equals(ancien[0]), "le joueur devait avoir changé de bloc");
                })
                .thenExecute(() -> {
                    assertSansLumiere(helper, ancien[0]);
                    helper.assertTrue(!notes(helper).isOurs(ancien[0]), "l'ancienne note devait être effacée");
                    quitter(j);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void retireeToutDisparait(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeRetiree", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> assertSansLumiere(helper, cible[0]))
                .thenExecute(() -> {
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la note devait être effacée");
                    quitter(j);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void videeToutDisparait(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeVidee", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    // Moins que ce qu'un tick coûte : la lampe ne s'allume plus.
                    HeadlampItem.store(lampePortee(p), ExploreConfig.headlampEnergy() - 1);
                })
                .thenWaitUntil(() -> assertSansLumiere(helper, cible[0]))
                .thenExecute(() -> {
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la note devait être effacée");
                    helper.assertTrue(HeadlampItem.stored(lampePortee(p)) == ExploreConfig.headlampEnergy() - 1,
                            "une lampe vide ne dépense rien");
                    quitter(j);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void elleNEcraseRien(GameTestHelper helper) {
        enclos(helper);
        // Une couche de verre autour de la tête : quelle que soit la case exacte des yeux, c'est du verre.
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.GLASS);
            }
        }
        JoueurDeTest j = joueur(helper, "LampeVerre", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    BlockState etat = etat(helper, oeil(p));
                    helper.assertTrue(etat.is(Blocks.GLASS), "le verre devait rester en " + oeil(p) + ", vu " + etat);
                    helper.assertTrue(!notes(helper).isOurs(oeil(p)), "rien ne devait être noté dans le verre");
                    helper.assertTrue(CHARGE - HeadlampItem.stored(lampePortee(p)) > 0,
                            "la lampe est allumée, elle dépense même sans pouvoir poser");
                    quitter(j);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void elleNeRetirePasLaLumiereDeLEquipe(GameTestHelper helper) {
        enclos(helper);
        // Des blocs de lumière de l'équipe, posés avant, à l'endroit des yeux (niveau 15, bien distinct du 14).
        BlockState equipe = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 2, z), equipe);
            }
        }
        JoueurDeTest j = joueur(helper, "LampeEquipe", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    cible[0] = oeil(p);
                    helper.assertTrue(etat(helper, cible[0]) == equipe,
                            "la lumière de l'équipe devait rester telle quelle, vu " + etat(helper, cible[0]));
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la lumière de l'équipe n'est pas la nôtre");
                    p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(etat(helper, cible[0]) == equipe,
                            "la lampe retirée ne devait pas toucher la lumière de l'équipe, vu " + etat(helper, cible[0]));
                    quitter(j);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void sousLEauLumiereImbibee(GameTestHelper helper) {
        enclos(helper);
        // Un bassin de 3 x 3 x 3 sources, scellé par une couronne de pierre : l'eau ne coule pas.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                boolean couronne = x == 1 || x == 5 || z == 1 || z == 5;
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), couronne ? Blocks.STONE : Blocks.WATER);
                }
            }
        }
        JoueurDeTest j = joueur(helper, "LampeEau", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertLumiere(helper, oeil(p));
                    helper.assertTrue(etat(helper, oeil(p)).getValue(LightBlock.WATERLOGGED),
                            "dans l'eau, le bloc de lumière devait être imbibé, vu " + etat(helper, oeil(p)));
                })
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> {
                    BlockState etat = etat(helper, cible[0]);
                    helper.assertTrue(etat.is(Blocks.WATER) && etat.getValue(LiquidBlock.LEVEL) == 0,
                            "l'eau source devait revenir, vu " + etat);
                })
                .thenExecute(() -> quitter(j))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deuxJoueursUnBlocPartage(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest premier = joueur(helper, "LampeDuo1", CHARGE);
        JoueurDeTest second = joueur(helper, "LampeDuo2", CHARGE);
        ServerPlayer p1 = premier.joueur();
        ServerPlayer p2 = second.joueur();
        pulser(helper, premier, second);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertLumiere(helper, oeil(p1));
                    helper.assertTrue(oeil(p1).equals(oeil(p2)), "les deux joueurs devaient partager la case des yeux");
                    // Les deux lampes ont dépensé : la seconde s'est appuyée sur le bloc de la première.
                    helper.assertTrue(HeadlampItem.stored(lampePortee(p2)) < CHARGE, "la seconde lampe devait être allumée");
                })
                .thenExecute(() -> {
                    cible[0] = oeil(p1);
                    p1.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                })
                .thenExecuteAfter(5, () -> {
                    assertLumiere(helper, cible[0]);
                    helper.assertTrue(notes(helper).isOurs(cible[0]), "le bloc devait rester noté pour le second");
                    p2.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> assertSansLumiere(helper, cible[0]))
                .thenExecute(() -> {
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "le dernier départ devait effacer la note");
                    quitter(premier);
                    quitter(second);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void deconnexionNettoie(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeDeco", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    j.quitter();
                    // Aucun tick n'a eu lieu depuis la sortie : seul l'évènement de déconnexion peut l'avoir retiré.
                    assertSansLumiere(helper, cible[0]);
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la note devait être effacée");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void mortNettoie(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeMort", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    p.kill();
                    helper.assertTrue(!p.isAlive(), "le joueur devait être mort");
                    // Aucun tick n'a eu lieu depuis la mort : seul l'évènement de mort peut l'avoir retiré.
                    assertSansLumiere(helper, cible[0]);
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la note devait être effacée");
                })
                .thenExecuteAfter(5, () -> {
                    assertSansLumiere(helper, cible[0]);
                    quitter(j);
                })
                .thenSucceed();
    }

    /**
     * Un vrai changement de dimension n'est pas simulé : l'évènement que Forge publie à l'arrivée est publié ici à la
     * main, le joueur étant resté dans la même dimension.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void changementDeDimensionNettoie(GameTestHelper helper) {
        enclos(helper);
        JoueurDeTest j = joueur(helper, "LampeDim", CHARGE);
        ServerPlayer p = j.joueur();
        pulser(helper, j);
        BlockPos[] cible = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertLumiere(helper, oeil(p)))
                .thenExecute(() -> {
                    cible[0] = oeil(p);
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
                    // Aucun tick n'a eu lieu depuis l'évènement : seul le gestionnaire peut l'avoir retiré.
                    assertSansLumiere(helper, cible[0]);
                    helper.assertTrue(!notes(helper).isOurs(cible[0]), "la note devait être effacée");
                    quitter(j);
                })
                .thenSucceed();
    }

    /**
     * Un chunk déchargé puis rechargé n'est pas simulé : l'évènement de chargement que Forge publie est publié ici à
     * la main pour un chunk déjà chargé. Une note sans joueur (plantage) part avec son bloc ; un bloc de lumière non
     * noté (équipe), dans le même chunk, reste.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void orphelineNettoyeeAuChargement(GameTestHelper helper) {
        enclos(helper);
        BlockPos orphelin = new BlockPos(3, 2, 3);
        BlockPos orphelinAbs = helper.absolutePos(orphelin);
        // La case voisine qui est dans le même chunk (la frontière de chunk ne peut pas passer des deux côtés).
        BlockPos equipe = (helper.absolutePos(new BlockPos(4, 2, 3)).getX() >> 4) == (orphelinAbs.getX() >> 4)
                ? new BlockPos(4, 2, 3) : new BlockPos(2, 2, 3);
        BlockPos equipeAbs = helper.absolutePos(equipe);
        BlockState lumiere = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, ExploreConfig.headlampLight());
        helper.setBlock(orphelin, lumiere);
        helper.setBlock(equipe, lumiere);
        notes(helper).placed(orphelinAbs);
        helper.assertTrue(notes(helper).isOurs(orphelinAbs), "la note de départ devait exister");
        // Les deux cases sont dans le même chunk que la case orpheline, pour le même balayage.
        LevelChunk chunk = helper.getLevel().getChunkAt(orphelinAbs);
        helper.assertTrue(chunk == helper.getLevel().getChunkAt(equipeAbs), "les deux cases devaient partager un chunk");
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        assertSansLumiere(helper, orphelinAbs);
        helper.assertTrue(!notes(helper).isOurs(orphelinAbs), "la note orpheline devait être effacée");
        helper.assertTrue(etat(helper, equipeAbs).is(Blocks.LIGHT), "le bloc de lumière de l'équipe devait rester");
        helper.setBlock(equipe, Blocks.AIR);
        helper.succeed();
    }
}
