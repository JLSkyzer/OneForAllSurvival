package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchRegistry;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.FaultType;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.machine.RepairOutcome;
import dev.oneforallsurvival.repair.RepairRegistry;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Casser puis reposer une machine ne la répare pas : l'objet lâché porte l'usure (état, panne, prochaine
 * panne, protection) et la pose la relit par le {@code BlockEntityTag} de vanilla. La casse est la vraie
 * (loot table, {@code destroyBlock}, joueur, explosion) et la pose passe par {@code ItemStack.useOn}.
 * Chaque test a son lot et le joueur qui pose attend la pose avant que le serveur ne tique : on laisse
 * {@code setupTicks = 20} pour que le morceau de monde soit déjà actif quand on mesure.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CasseReposeGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 3);

    private CasseReposeGameTests() {}

    private static ElectricMachineBlockEntity poser(GameTestHelper helper, Block bloc) {
        helper.setBlock(POS, bloc);
        ElectricMachineBlockEntity be = (ElectricMachineBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de block entity pour " + bloc);
        return be;
    }

    /** Recharge la machine avec son propre état modifié (le test n'a pas d'accès direct à l'usure d'un établi). */
    private static void abimer(ElectricMachineBlockEntity be, double etat) {
        CompoundTag tag = be.saveWithoutMetadata();
        tag.putDouble("condition", etat);
        be.load(tag);
    }

    /** Ramasse (et retire du monde) l'objet de ce bloc lâché près de POS. */
    private static ItemStack ramasser(GameTestHelper helper, Block bloc) {
        List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
        for (ItemEntity e : objets) {
            if (e.getItem().is(bloc.asItem())) {
                ItemStack pile = e.getItem().copy();
                e.discard();
                return pile;
            }
        }
        throw new IllegalStateException("aucun objet " + bloc + " lâché, vu " + objets);
    }

    /** Casse comme le monde le fait (loot table, block entity transmis), sans joueur. */
    private static ItemStack casser(GameTestHelper helper, Block bloc) {
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertTrue(helper.getBlockState(POS).isAir(), "le bloc devait être cassé");
        return ramasser(helper, bloc);
    }

    /** Pose l'objet comme un joueur (un clic droit) ; renvoie la machine posée. */
    private static ElectricMachineBlockEntity reposer(GameTestHelper helper, ItemStack pile) {
        Player joueur = helper.makeMockPlayer();
        joueur.setItemInHand(InteractionHand.MAIN_HAND, pile.copy());
        // Viser la case vide elle-même : un bloc remplaçable est occupé là où on le vise.
        BlockPos cible = helper.absolutePos(POS);
        BlockHitResult visee = new BlockHitResult(Vec3.atCenterOf(cible), Direction.UP, cible, false);
        InteractionResult resultat = joueur.getItemInHand(InteractionHand.MAIN_HAND)
                .useOn(new UseOnContext(joueur, InteractionHand.MAIN_HAND, visee));
        helper.assertTrue(resultat.consumesAction(), "la pose devait réussir, vu " + resultat);
        ElectricMachineBlockEntity be = (ElectricMachineBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(be != null, "pas de machine après la pose : " + helper.getBlockState(POS) + " en POS, "
                + helper.getBlockState(POS.below()) + " dessous, " + helper.getBlockState(POS.above()) + " dessus");
        return be;
    }

    private static CompoundTag etiquette(ItemStack pile) {
        return pile.getTagElement("BlockEntityTag");
    }

    /** Une machine usée, cassée puis reposée, garde son état et sa prochaine panne. */
    private static void useeGardeSonEtat(GameTestHelper helper, Block bloc) {
        ElectricMachineBlockEntity be = poser(helper, bloc);
        abimer(be, 37.5);
        FaultType prochaine = be.nextFault();
        be.energy().receiveEnergy(100, false);
        ItemStack pile = casser(helper, bloc);
        helper.assertTrue(etiquette(pile) != null, "l'objet d'une machine usée devait porter son état");
        ElectricMachineBlockEntity neuve = reposer(helper, pile);
        helper.assertTrue(neuve.condition() == 37.5, "état attendu 37.5, vu " + neuve.condition());
        helper.assertTrue(neuve.nextFault() == prochaine,
                "prochaine panne attendue " + prochaine + ", vue " + neuve.nextFault());
        helper.assertTrue(neuve.fault() == null, "aucune panne attendue, vue " + neuve.fault());
        helper.assertTrue(neuve.energy().getEnergyStored() == 0,
                "l'énergie ne voyage pas, vue " + neuve.energy().getEnergyStored());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "casse_usee_essai", setupTicks = 20)
    public static void uneMachineUseeReposeeGardeSonEtat(GameTestHelper helper) {
        useeGardeSonEtat(helper, DevBlocks.testMachine());
    }

    @GameTest(template = "test/plancher", batch = "casse_usee_etabli", setupTicks = 20)
    public static void unEtabliUseReposeGardeSonEtat(GameTestHelper helper) {
        useeGardeSonEtat(helper, BenchRegistry.ENGINEER_BENCH.get());
    }

    @GameTest(template = "test/plancher", batch = "casse_usee_bain", setupTicks = 20)
    public static void unBainUseReposeGardeSonEtat(GameTestHelper helper) {
        useeGardeSonEtat(helper, RepairRegistry.GALVANIC_BATH.get());
    }

    /** Une machine en panne reposée reste en panne, de la même pièce, et demande la même pièce. */
    private static void panneReste(GameTestHelper helper, Block bloc) {
        ElectricMachineBlockEntity be = poser(helper, bloc);
        FaultType prochaine = be.nextFault();
        be.breakDown(prochaine);
        ItemStack pile = casser(helper, bloc);
        ElectricMachineBlockEntity neuve = reposer(helper, pile);
        helper.assertTrue(neuve.fault() == prochaine, "panne attendue " + prochaine + ", vue " + neuve.fault());
        helper.assertTrue(neuve.condition() == 0.0, "état attendu 0, vu " + neuve.condition());
        helper.assertTrue(neuve.expectedPart() == prochaine, "pièce attendue " + prochaine + ", vue " + neuve.expectedPart());
        FaultType autre = prochaine == FaultType.FUSE ? FaultType.BEARING : FaultType.FUSE;
        helper.assertTrue(neuve.previewRepair(autre, false) == RepairOutcome.WRONG_PART, "une autre pièce ne convient pas");
        // Le bloc passe en FAULT au tick suivant, comme partout ailleurs.
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getBlockState(POS).getValue(ElectricMachineBlock.STATE) == MachineState.FAULT,
                    "FAULT attendu sur le bloc, vu " + helper.getBlockState(POS).getValue(ElectricMachineBlock.STATE));
            helper.assertTrue(neuve.state() == MachineState.FAULT, "FAULT attendu, vu " + neuve.state());
            helper.assertTrue(neuve.repair(prochaine, false) == RepairOutcome.REPAIRED, "la bonne pièce devait réparer");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "casse_panne_essai", setupTicks = 20)
    public static void uneMachineEnPanneReposeeResteEnPanne(GameTestHelper helper) {
        panneReste(helper, DevBlocks.testMachine());
    }

    @GameTest(template = "test/plancher", batch = "casse_panne_etabli", setupTicks = 20)
    public static void unEtabliEnPanneReposeResteEnPanne(GameTestHelper helper) {
        panneReste(helper, BenchRegistry.ENGINEER_BENCH.get());
    }

    @GameTest(template = "test/plancher", batch = "casse_panne_bain", setupTicks = 20)
    public static void unBainEnPanneReposeResteEnPanne(GameTestHelper helper) {
        panneReste(helper, RepairRegistry.GALVANIC_BATH.get());
    }

    /** Une machine traitée reste traitée, même à l'état plein. */
    private static void protegeeReste(GameTestHelper helper, Block bloc) {
        ElectricMachineBlockEntity be = poser(helper, bloc);
        helper.assertTrue(be.repair(be.nextFault(), true) == RepairOutcome.REPAIRED, "la réparation traitée devait réussir");
        helper.assertTrue(be.condition() == 100.0 && be.isProtected(), "machine traitée à l'état plein attendue");
        FaultType prochaine = be.nextFault();
        ItemStack pile = casser(helper, bloc);
        helper.assertTrue(etiquette(pile) != null, "une machine traitée n'est pas « neuve » : l'objet doit porter l'état");
        ElectricMachineBlockEntity neuve = reposer(helper, pile);
        helper.assertTrue(neuve.isProtected(), "la protection devait survivre");
        helper.assertTrue(neuve.nextFault() == prochaine, "prochaine panne attendue " + prochaine + ", vue " + neuve.nextFault());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "casse_protegee_essai", setupTicks = 20)
    public static void uneMachineProtegeeReposeeResteProtegee(GameTestHelper helper) {
        protegeeReste(helper, DevBlocks.testMachine());
    }

    @GameTest(template = "test/plancher", batch = "casse_protegee_bain", setupTicks = 20)
    public static void unBainProtegeReposeResteProtege(GameTestHelper helper) {
        protegeeReste(helper, RepairRegistry.GALVANIC_BATH.get());
    }

    /** Une machine neuve laisse un objet nu, qui s'empile avec un objet tout juste fabriqué. */
    private static void neuveEstNue(GameTestHelper helper, Block bloc) {
        ElectricMachineBlockEntity be = poser(helper, bloc);
        be.energy().receiveEnergy(100, false);
        ItemStack pile = casser(helper, bloc);
        helper.assertTrue(!pile.hasTag(), "une machine neuve devait laisser un objet sans étiquette, vu " + pile.getTag());
        helper.assertTrue(ItemStack.isSameItemSameTags(pile, new ItemStack(bloc.asItem())),
                "l'objet devait s'empiler avec un objet neuf");
        ElectricMachineBlockEntity neuve = reposer(helper, pile);
        helper.assertTrue(neuve.condition() == 100.0 && neuve.fault() == null && !neuve.isProtected(),
                "machine neuve attendue");
        helper.assertTrue(neuve.energy().getEnergyStored() == 0, "l'énergie ne voyage pas");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "casse_neuve_essai", setupTicks = 20)
    public static void uneMachineNeuveLaisseUnObjetNu(GameTestHelper helper) {
        neuveEstNue(helper, DevBlocks.testMachine());
    }

    @GameTest(template = "test/plancher", batch = "casse_neuve_etabli", setupTicks = 20)
    public static void unEtabliNeufLaisseUnObjetNu(GameTestHelper helper) {
        neuveEstNue(helper, BenchRegistry.ENGINEER_BENCH.get());
    }

    @GameTest(template = "test/plancher", batch = "casse_neuve_bain", setupTicks = 20)
    public static void unBainNeufLaisseUnObjetNu(GameTestHelper helper) {
        neuveEstNue(helper, RepairRegistry.GALVANIC_BATH.get());
    }

    /** L'étiquette ne contient que l'usure : ni énergie, ni progression, ni reliquat de travail. */
    @GameTest(template = "test/plancher", batch = "casse_etiquette", setupTicks = 20)
    public static void lEtiquetteNePorteQueLUsure(GameTestHelper helper) {
        TestMachineBlockEntity be = (TestMachineBlockEntity) poser(helper, DevBlocks.testMachine());
        be.energy().receiveEnergy(100, false);
        be.etat(60);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.progress() > 0, "la machine devait avoir avancé, le test ne prouve rien sinon");
            ItemStack pile = casser(helper, DevBlocks.testMachine());
            CompoundTag tag = etiquette(pile);
            helper.assertTrue(tag != null, "l'objet devait porter l'état");
            for (String cle : List.of("energy", "progress", "workCredit")) {
                helper.assertTrue(!tag.contains(cle), "l'étiquette ne doit pas porter « " + cle + " »");
            }
            for (String cle : List.of("condition", "next_fault", "protected")) {
                helper.assertTrue(tag.contains(cle), "l'étiquette doit porter « " + cle + " »");
            }
            TestMachineBlockEntity neuve = (TestMachineBlockEntity) reposer(helper, pile);
            helper.assertTrue(neuve.progress() == 0, "la progression ne voyage pas, vue " + neuve.progress());
            helper.assertTrue(neuve.energy().getEnergyStored() == 0, "l'énergie ne voyage pas");
            helper.assertTrue(neuve.condition() < 100.0 && neuve.condition() > 50.0, "état usé attendu, vu " + neuve.condition());
            helper.succeed();
        });
    }

    /** Un vrai joueur en survie qui casse la machine usée : même objet. */
    @GameTest(template = "test/plancher", batch = "casse_joueur", setupTicks = 20)
    public static void leJoueurQuiCasseObtientLObjetUse(GameTestHelper helper) {
        ElectricMachineBlockEntity be = poser(helper, DevBlocks.testMachine());
        abimer(be, 42);
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "casseur",
                helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        try {
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            boolean casse = j.joueur().gameMode.destroyBlock(helper.absolutePos(POS));
            helper.assertTrue(casse, "le joueur devait casser la machine");
            ItemStack pile = ramasser(helper, DevBlocks.testMachine());
            helper.assertTrue(etiquette(pile) != null, "l'objet cassé par un joueur devait porter l'état");
            helper.assertTrue(reposer(helper, pile).condition() == 42.0, "état 42 attendu");
        } finally {
            j.quitter();
        }
        helper.succeed();
    }

    /** En créatif, casser ne lâche rien : pas d'objet de plus, ni neuf ni usé. */
    @GameTest(template = "test/plancher", batch = "casse_creatif", setupTicks = 20)
    public static void enCreatifCasserNeLacheRien(GameTestHelper helper) {
        ElectricMachineBlockEntity be = poser(helper, DevBlocks.testMachine());
        abimer(be, 42);
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), "creatif",
                helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        try {
            j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(j.joueur().gameMode.destroyBlock(helper.absolutePos(POS)), "le créatif devait casser");
            helper.assertTrue(helper.getBlockState(POS).isAir(), "le bloc devait avoir disparu");
        } finally {
            j.quitter();
        }
        helper.runAfterDelay(3, () -> {
            List<ItemEntity> objets = helper.getEntities(EntityType.ITEM, POS, 3.0);
            helper.assertTrue(objets.stream().noneMatch(e -> e.getItem().is(DevBlocks.testMachine().asItem())),
                    "aucun objet ne devait être lâché en créatif, vu " + objets);
            helper.succeed();
        });
    }

    /** Une explosion (facteur de survie 1, donc sûre) lâche aussi l'objet usé. */
    @GameTest(template = "test/plancher", batch = "casse_explosion", setupTicks = 20)
    public static void uneExplosionLacheLObjetUse(GameTestHelper helper) {
        ElectricMachineBlockEntity be = poser(helper, DevBlocks.testMachine());
        abimer(be, 42);
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(POS));
        helper.getLevel().explode(null, centre.x, centre.y, centre.z, 1.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(POS).isAir(), "l'explosion devait détruire la machine");
        ItemStack pile = ramasser(helper, DevBlocks.testMachine());
        helper.assertTrue(etiquette(pile) != null, "l'objet d'une explosion devait porter l'état");
        helper.assertTrue(reposer(helper, pile).condition() == 42.0, "état 42 attendu");
        helper.succeed();
    }
}
