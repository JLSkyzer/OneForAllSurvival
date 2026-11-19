package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.WallSocketBlock;
import dev.oneforallsurvival.phone.WallSocketBlockEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La prise murale (lot T1, tâche 7) : elle recharge le téléphone qu'on y branche, au débit de
 * {@link PhoneConfig#socketChargePerTick()}, jusqu'à la capacité ; on branche et on reprend d'un clic droit ; une
 * prise occupée ou un autre objet est refusé ; casse, explosion et rechargement rendent ou gardent le téléphone, avec
 * son {@code phone_id} et sa charge, en un seul exemplaire.
 *
 * <p>Le mur derrière la prise est la source d'essai ({@link DevBlocks#testSource()}), un bloc plein d'un « autre
 * mod ». La source d'essai ne pousse rien d'elle-même (aucun bloc d'énergie ne le fait sans câble) : un câble
 * simulé, posé au début de l'essai par {@code onEachTick}, pousse {@value #DEBIT_CABLE} FE par tic dans la
 * capability de la prise, par la face du mur, comme le ferait un câble raccordé à un générateur.
 *
 * <p>Lots : {@value #LOT} (les huit essais du brief), {@value #LOT_RECETTES} (la fabrication à la table) et
 * {@value #LOT_MUR} (en plus du brief : mur retiré, faux joueur, pose contre un mur seulement et forme exacte).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PriseGameTests {

    private static final String LOT = "prise";
    private static final String LOT_RECETTES = "telephone_recettes";
    private static final String LOT_MUR = "prise_mur";

    private static final BlockPos MUR = new BlockPos(3, 1, 3);
    private static final BlockPos PRISE = new BlockPos(3, 1, 4);
    private static final Direction FACE = Direction.SOUTH;
    private static final Vec3 JOUEUR = new Vec3(3.5, 1, 6.5);
    private static final int DEBIT_CABLE = 200;

    private PriseGameTests() {}

    // --- outils ---

    private static ItemStack telephone(int charge) {
        ItemStack pile = new ItemStack(PhoneRegistry.PHONE.get());
        PhoneItem.ensurePhoneId(pile);
        PhoneItem.store(pile, charge);
        return pile;
    }

    private static UUID id(ItemStack pile) {
        return PhoneItem.phoneId(pile).orElseThrow(() -> new AssertionError("téléphone sans phone_id : " + pile));
    }

    /** La charge telle qu'elle est écrite dans la pile, sans la borne de lecture de {@code ItemBattery.stored}. */
    private static int chargeBrute(ItemStack pile) {
        return pile.hasTag() ? pile.getTag().getInt("energy") : 0;
    }

    private static WallSocketBlockEntity poser(GameTestHelper helper, net.minecraft.world.level.block.Block mur) {
        helper.setBlock(MUR, mur);
        helper.setBlock(PRISE, PhoneRegistry.WALL_SOCKET.get().defaultBlockState().setValue(WallSocketBlock.FACING, FACE));
        helper.assertTrue(helper.getBlockEntity(PRISE) instanceof WallSocketBlockEntity, "pas de block entity de la prise");
        return (WallSocketBlockEntity) helper.getBlockEntity(PRISE);
    }

    /** Le câble simulé : à chaque tic, {@value #DEBIT_CABLE} FE poussés par la face du mur. */
    private static void cable(GameTestHelper helper) {
        helper.onEachTick(() -> {
            if (helper.getBlockEntity(PRISE) instanceof WallSocketBlockEntity be) {
                IEnergyStorage entree = be.getCapability(ForgeCapabilities.ENERGY, FACE.getOpposite()).orElse(null);
                if (entree != null) {
                    entree.receiveEnergy(DEBIT_CABLE, false);
                }
            }
        });
    }

    private static boolean branchee(GameTestHelper helper) {
        BlockState etat = helper.getBlockState(PRISE);
        return etat.is(PhoneRegistry.WALL_SOCKET.get()) && etat.getValue(WallSocketBlock.PLUGGED);
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom) {
        // Le gabarit ne garde que son plancher ; au-dessus, le monde peut laisser de la pierre (relevé en y = 1).
        BlockPos pieds = BlockPos.containing(JOUEUR);
        helper.setBlock(pieds, Blocks.AIR);
        helper.setBlock(pieds.above(), Blocks.AIR);
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return j;
    }

    private static void quitter(JoueurDeTest j) {
        if (j != null && j.connecte()) {
            j.quitter();
        }
    }

    /** Un clic droit réel sur la face avant de la prise, par le jeu (événements de Forge compris). */
    private static void clicDroit(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(PRISE);
        Vec3 point = Vec3.atCenterOf(abs).relative(FACE.getOpposite(), 0.4);
        BlockHitResult tir = new BlockHitResult(point, FACE, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    /** Les téléphones au sol dans la boîte du gabarit. */
    private static List<ItemEntity> telephonesAuSol(GameTestHelper helper) {
        AABB boite = new AABB(helper.absolutePos(new BlockPos(0, -1, 0)), helper.absolutePos(new BlockPos(8, 6, 8)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, boite,
                e -> e.isAlive() && e.getItem().is(PhoneRegistry.PHONE.get()));
    }

    private static void unSeulTelephoneAuSol(GameTestHelper helper, UUID attendu, int charge) {
        List<ItemEntity> sol = telephonesAuSol(helper);
        helper.assertTrue(sol.size() == 1, "un seul téléphone au sol attendu, vu " + sol.size());
        ItemStack pile = sol.get(0).getItem();
        helper.assertTrue(pile.getCount() == 1, "un exemplaire attendu, vu " + pile.getCount());
        helper.assertTrue(attendu.equals(id(pile)), "même phone_id attendu : " + attendu + ", vu " + id(pile));
        helper.assertTrue(PhoneItem.stored(pile) == charge, "charge " + charge + " attendue, vue " + PhoneItem.stored(pile));
    }

    // --- lot prise ---

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void laPriseAlimenteeRecharge(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, DevBlocks.testSource());
        be.setPhone(telephone(0));
        cable(helper);
        int capacite = PhoneConfig.capacity();
        helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    int charge = PhoneItem.stored(be.phone());
                    helper.assertTrue(charge >= 3_600, "au moins 3 600 FE en 100 tics attendus, vus " + charge);
                    helper.assertTrue(be.state() == MachineState.RUNNING, "RUNNING attendu, vu " + be.state());
                    // Presque plein : le dernier versement doit s'arrêter à la capacité.
                    PhoneItem.store(be.phone(), capacite - 10);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.state() == MachineState.STOPPED,
                        "STOPPED attendu une fois plein, vu " + be.state()))
                .thenExecute(() -> {
                    int brute = chargeBrute(be.phone());
                    helper.assertTrue(brute == capacite, "charge = capacité " + capacite + " attendue, vue " + brute);
                    helper.assertTrue(helper.getBlockState(PRISE).getValue(WallSocketBlock.STATE) == MachineState.STOPPED,
                            "état de bloc STOPPED attendu");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void sansCourantRienNeCharge(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        be.setPhone(telephone(0));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
            helper.assertTrue(chargeBrute(be.phone()) == 0, "charge 0 attendue, vue " + chargeBrute(be.phone()));
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void brancherPuisReprendre(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        JoueurDeTest j = joueur(helper, "PriseBranche");
        ServerPlayer p = j.joueur();
        ItemStack tel = telephone(12_345);
        UUID attendu = id(tel);
        p.setItemInHand(InteractionHand.MAIN_HAND, tel);
        clicDroit(helper, p);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                            "la main devait être vide, vu " + p.getItemInHand(InteractionHand.MAIN_HAND));
                    helper.assertTrue(attendu.equals(PhoneItem.phoneId(be.phone()).orElse(null)),
                            "le téléphone devait être dans la prise, vu " + be.phone());
                })
                .thenWaitUntil(() -> helper.assertTrue(branchee(helper), "PLUGGED attendu"))
                .thenExecute(() -> {
                    clicDroit(helper, p);
                    ItemStack rendu = p.getItemInHand(InteractionHand.MAIN_HAND);
                    helper.assertTrue(rendu.is(PhoneRegistry.PHONE.get()), "le téléphone devait revenir en main, vu " + rendu);
                    helper.assertTrue(attendu.equals(id(rendu)), "même phone_id attendu, vu " + id(rendu));
                    helper.assertTrue(PhoneItem.stored(rendu) == 12_345, "charge 12 345 attendue, vue " + PhoneItem.stored(rendu));
                    helper.assertTrue(be.phone().isEmpty(), "la prise devait être vide, vu " + be.phone());
                })
                .thenWaitUntil(() -> helper.assertTrue(!branchee(helper), "PLUGGED faux attendu après la reprise"))
                .thenExecute(() -> quitter(j))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void unePriseOccupeeRefuse(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack premier = telephone(100);
        UUID idPremier = id(premier);
        be.setPhone(premier);
        JoueurDeTest j = joueur(helper, "PriseOccupee");
        try {
            ServerPlayer p = j.joueur();
            ItemStack second = telephone(200);
            UUID idSecond = id(second);
            p.setItemInHand(InteractionHand.MAIN_HAND, second);
            j.clesRecues();
            clicDroit(helper, p);
            ItemStack main = p.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(main.is(PhoneRegistry.PHONE.get()) && idSecond.equals(id(main)),
                    "le second téléphone devait rester en main, vu " + main);
            helper.assertTrue(PhoneItem.stored(main) == 200, "sa charge devait rester 200, vue " + PhoneItem.stored(main));
            helper.assertTrue(idPremier.equals(PhoneItem.phoneId(be.phone()).orElse(null)),
                    "le premier téléphone devait rester dans la prise, vu " + be.phone());
            List<String> cles = j.clesRecues();
            helper.assertTrue(cles.contains("message.oneforallsurvival.wall_socket.busy"),
                    "message de prise occupée attendu, vu " + cles);
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void unObjetQuelconqueNeSeBranchePas(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        JoueurDeTest j = joueur(helper, "PriseFer");
        ServerPlayer p = j.joueur();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
        j.clesRecues();
        clicDroit(helper, p);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    ItemStack main = p.getItemInHand(InteractionHand.MAIN_HAND);
                    helper.assertTrue(main.is(Items.IRON_INGOT) && main.getCount() == 1,
                            "le lingot devait rester en main, vu " + main);
                    helper.assertTrue(be.phone().isEmpty(), "rien ne devait être branché, vu " + be.phone());
                    helper.assertTrue(!branchee(helper), "PLUGGED faux attendu");
                    List<String> cles = j.clesRecues();
                    helper.assertTrue(cles.contains("message.oneforallsurvival.wall_socket.busy"),
                            "message de refus attendu, vu " + cles);
                })
                .thenExecute(() -> quitter(j))
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void casserLaPriseRendLeTelephone(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack tel = telephone(5_000);
        UUID attendu = id(tel);
        be.setPhone(tel);
        helper.getLevel().destroyBlock(helper.absolutePos(PRISE), true);
        helper.assertTrue(helper.getBlockState(PRISE).isAir(), "la prise devait être cassée");
        unSeulTelephoneAuSol(helper, attendu, 5_000);
        helper.runAfterDelay(5, () -> {
            unSeulTelephoneAuSol(helper, attendu, 5_000);
            telephonesAuSol(helper).forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    /**
     * L'explosion (puissance 4) a lieu au tic 160 : les autres essais du lot, voisins du gabarit, ont fini, et elle
     * ne peut ni casser leur prise ni pousser leur joueur.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void explosionRendLeTelephone(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack tel = telephone(7_000);
        UUID attendu = id(tel);
        be.setPhone(tel);
        helper.startSequence()
                .thenExecuteAfter(160, () -> {
                    Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(PRISE));
                    helper.getLevel().explode(null, centre.x, centre.y, centre.z, 4.0F, Level.ExplosionInteraction.BLOCK);
                    helper.assertTrue(!helper.getBlockState(PRISE).is(PhoneRegistry.WALL_SOCKET.get()),
                            "l'explosion devait emporter la prise");
                    unSeulTelephoneAuSol(helper, attendu, 7_000);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    unSeulTelephoneAuSol(helper, attendu, 7_000);
                    telephonesAuSol(helper).forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 100)
    public static void laChargeSurvitAuRechargement(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack tel = telephone(33_333);
        be.setPhone(tel);
        CompoundTag tag = be.saveWithoutMetadata();
        WallSocketBlockEntity copie = new WallSocketBlockEntity(be.getBlockPos(), be.getBlockState());
        copie.load(tag);
        helper.assertTrue(ItemStack.matches(copie.phone(), be.phone()),
                "même pile attendue après rechargement : " + be.phone() + " / " + copie.phone()
                        + " (" + be.phone().getTag() + " / " + copie.phone().getTag() + ")");
        helper.assertTrue(id(tel).equals(id(copie.phone())), "même phone_id attendu");
        helper.assertTrue(PhoneItem.stored(copie.phone()) == 33_333, "charge 33 333 attendue, vue "
                + PhoneItem.stored(copie.phone()));
        // Une prise vide relit une prise vide.
        be.setPhone(ItemStack.EMPTY);
        WallSocketBlockEntity vide = new WallSocketBlockEntity(be.getBlockPos(), be.getBlockState());
        vide.setPhone(telephone(1));
        vide.load(be.saveWithoutMetadata());
        helper.assertTrue(vide.phone().isEmpty(), "une prise vide devait se relire vide, vu " + vide.phone());
        helper.succeed();
    }

    // --- lot telephone_recettes ---

    @GameTest(template = "test/plancher", batch = LOT_RECETTES)
    public static void leTelephoneEtLaPriseSeFabriquentALaTable(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var access = helper.getLevel().registryAccess();
        for (Item item : List.of(PhoneRegistry.PHONE.get(), PhoneRegistry.WALL_SOCKET_ITEM.get())) {
            var cle = BuiltInRegistries.ITEM.getKey(item);
            helper.assertTrue(BenchCatalog.isTableOnly(cle), cle + " devait porter l'étiquette table_only");
            Optional<?> recette = manager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(r -> r.getResultItem(access).is(item)).findFirst();
            helper.assertTrue(recette.isPresent(), cle + " : aucune recette de table ne le donne");
        }
        helper.succeed();
    }

    // --- lot prise_mur (en plus du brief) ---

    /** Le mur retiré, la prise tombe et rend le téléphone. */
    @GameTest(template = "test/plancher", batch = LOT_MUR, timeoutTicks = 100)
    public static void sansMurLaPriseTombeEtRendLeTelephone(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack tel = telephone(4_242);
        UUID attendu = id(tel);
        be.setPhone(tel);
        helper.setBlock(MUR, Blocks.AIR);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(PRISE).isAir(), "la prise devait tomber"))
                .thenExecute(() -> {
                    unSeulTelephoneAuSol(helper, attendu, 4_242);
                    telephonesAuSol(helper).forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    /** Un faux joueur (une machine, comme le déployeur de Create) ne sort pas le téléphone, ni n'en branche un. */
    @GameTest(template = "test/plancher", batch = LOT_MUR, timeoutTicks = 100)
    public static void unFauxJoueurNeSortPasLeTelephone(GameTestHelper helper) {
        WallSocketBlockEntity be = poser(helper, Blocks.STONE);
        ItemStack tel = telephone(900);
        UUID attendu = id(tel);
        be.setPhone(tel);
        ServerPlayer faux = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        faux.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(PRISE);
        helper.getBlockState(PRISE).use(helper.getLevel(), faux, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), FACE, abs, false));
        helper.assertTrue(faux.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "le faux joueur ne devait rien recevoir, vu " + faux.getItemInHand(InteractionHand.MAIN_HAND));
        helper.assertTrue(attendu.equals(PhoneItem.phoneId(be.phone()).orElse(null)),
                "le téléphone devait rester branché, vu " + be.phone());
        helper.succeed();
    }

    /** Posée à la main : contre un mur oui, au sol ou au plafond non ; la forme est la plaque de 2 px. */
    @GameTest(template = "test/plancher", batch = LOT_MUR, timeoutTicks = 100)
    public static void seulementContreUnMur(GameTestHelper helper) {
        // Le gabarit ne garde que son plancher : ce qui est au-dessus vient du monde (relevé : de la pierre en y = 1).
        BlockPos dessusDuSol = new BlockPos(5, 1, 5);
        helper.setBlock(PRISE, Blocks.AIR);
        helper.setBlock(dessusDuSol, Blocks.AIR);
        helper.setBlock(MUR, Blocks.STONE);
        JoueurDeTest j = joueur(helper, "PrisePose");
        try {
            ServerPlayer p = j.joueur();
            // Au sol : clic sur le dessus du plancher.
            BlockPos sol = helper.absolutePos(new BlockPos(5, 0, 5));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhoneRegistry.WALL_SOCKET_ITEM.get()));
            p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
            helper.assertTrue(helper.getBlockState(dessusDuSol).isAir(),
                    "la prise ne se pose pas au sol, vu " + helper.getBlockState(dessusDuSol));
            // Contre le mur : clic sur la face sud du mur.
            BlockPos mur = helper.absolutePos(MUR);
            var resultat = p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND),
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(mur).add(0, 0, 0.5), Direction.SOUTH, mur, false));
            BlockState pose = helper.getBlockState(PRISE);
            helper.assertTrue(pose.is(PhoneRegistry.WALL_SOCKET.get()), "la prise devait se poser contre le mur, vu " + pose
                    + " (résultat " + resultat + ", mur " + helper.getBlockState(MUR) + ", main "
                    + p.getItemInHand(InteractionHand.MAIN_HAND) + ")");
            helper.assertTrue(pose.getValue(WallSocketBlock.FACING) == Direction.SOUTH,
                    "la prise devait regarder la pièce (sud), vu " + pose.getValue(WallSocketBlock.FACING));
            AABB forme = pose.getShape(helper.getLevel(), helper.absolutePos(PRISE)).bounds();
            helper.assertTrue(Math.abs(forme.minZ) < 1e-6 && Math.abs(forme.maxZ - 2 / 16.0) < 1e-6,
                    "plaque de 2 px contre le mur nord attendue, vu " + forme);
            helper.assertTrue(!pose.canOcclude(), "la prise ne doit pas cacher les faces voisines (noOcclusion)");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }
}
