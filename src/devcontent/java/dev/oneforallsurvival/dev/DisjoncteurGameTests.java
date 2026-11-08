package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.BreakerBlockEntity;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.MeterLedger.Anomaly;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import dev.oneforallsurvival.grid.MeterReadingItem;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleGate;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le disjoncteur d'abonné et son scellé. Il regarde vers le nord : sa sortie touche la machine d'essai posée au nord
 * (1 000 FE de réserve, 100 FE au plus par appel), son entrée est la face sud. Les joueurs sont de vrais joueurs non
 * opérateurs, Ingénieur ou Citoyen ; chaque refus est vérifié par le message que le joueur a reçu.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class DisjoncteurGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos DISJONCTEUR = new BlockPos(1, 1, 2);
    private static final BlockPos COMPTEUR = new BlockPos(1, 1, 3);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 5);
    private static final Direction ENTREE = Direction.SOUTH;
    private static final String MSG = "message.oneforallsurvival.breaker.";

    private DisjoncteurGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static BlockState etat(boolean enclenche, boolean scelle) {
        return GridRegistry.BREAKER.get().defaultBlockState().setValue(InlineEnergyBlock.FACING, Direction.NORTH)
                .setValue(BreakerBlock.CLOSED, enclenche).setValue(BreakerBlock.SEALED, scelle);
    }

    private static BreakerBlockEntity poser(GameTestHelper helper, boolean enclenche, boolean scelle) {
        helper.setBlock(DISJONCTEUR, etat(enclenche, scelle));
        verifier(helper.getBlockEntity(DISJONCTEUR) instanceof BreakerBlockEntity, "pas de disjoncteur en " + DISJONCTEUR);
        return (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
    }

    private static TestMachineBlockEntity poserMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, DevBlocks.testMachine());
        verifier(helper.getBlockEntity(MACHINE) instanceof TestMachineBlockEntity, "pas de machine d'essai");
        return (TestMachineBlockEntity) helper.getBlockEntity(MACHINE);
    }

    /** Pousse {@code fe} FE dans l'entrée du disjoncteur ; renvoie ce qui est passé. */
    private static int pousser(GameTestHelper helper, int fe) {
        BreakerBlockEntity be = (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, ENTREE).resolve().orElse(null);
        verifier(cap != null, "pas de capability sur la face d'entrée du disjoncteur");
        return cap.receiveEnergy(fe, false);
    }

    private static boolean enclenche(GameTestHelper helper) {
        return helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED);
    }

    private static boolean scelle(GameTestHelper helper) {
        return helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.SEALED);
    }

    private static JoueurDeTest joueur(GameTestHelper helper, String nom, boolean ingenieur) {
        JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)));
        RoleSavedData.get(helper.getLevel().getServer()).book().assign(j.uuid(),
                ingenieur ? RoleBook.ENGINEER_ROLE_ID : RoleBook.DEFAULT_ROLE_ID);
        j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        verifier(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être opérateur");
        return j;
    }

    private static void quitter(JoueurDeTest... joueurs) {
        for (JoueurDeTest j : joueurs) {
            if (j != null && j.connecte()) {
                j.quitter();
            }
        }
    }

    private static BlockHitResult viser(GameTestHelper helper) {
        BlockPos cible = helper.absolutePos(DISJONCTEUR);
        return new BlockHitResult(Vec3.atCenterOf(cible), Direction.SOUTH, cible, false);
    }

    /** Clic droit sur le disjoncteur, {@code objet} en main (vide ou scellé), accroupi ou non. */
    private static InteractionResult cliquer(GameTestHelper helper, Player joueur, ItemStack objet, boolean accroupi) {
        joueur.setShiftKeyDown(accroupi);
        joueur.setItemInHand(InteractionHand.MAIN_HAND, objet);
        return helper.getBlockState(DISJONCTEUR).use(helper.getLevel(), joueur, InteractionHand.MAIN_HAND, viser(helper));
    }

    private static InteractionResult basculer(GameTestHelper helper, Player joueur) {
        return cliquer(helper, joueur, ItemStack.EMPTY, false);
    }

    private static ItemStack scelles(int n) {
        return new ItemStack(GridRegistry.SEAL.get(), n);
    }

    private static void verifierMessage(JoueurDeTest j, String cle, String contexte) {
        List<String> recus = j.messagesRecus();
        verifier(recus.contains(cle), contexte + " : message " + cle + " attendu, reçu " + recus);
    }

    private static int nombreDeScelles(Player joueur) {
        int n = 0;
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            ItemStack pile = joueur.getInventory().getItem(i);
            if (pile.is(GridRegistry.SEAL.get())) {
                n += pile.getCount();
            }
        }
        return n;
    }

    private static int scellesAuSol(GameTestHelper helper) {
        int n = 0;
        for (ItemEntity e : helper.getEntities(EntityType.ITEM, DISJONCTEUR, 4.0)) {
            if (e.getItem().is(GridRegistry.SEAL.get())) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "disjoncteur_coupe")
    public static void coupeIlNeLaissePasRien(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        poser(helper, true, false);
        verifier(pousser(helper, 100) == 100, "enclenché, le courant devait passer");
        verifier(machine.energy().getEnergyStored() == 100, "machine : " + machine.energy().getEnergyStored());
        BreakerBlockEntity be = (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
        verifier(be.isOpen(), "enclenché, il laisse passer");

        helper.setBlock(DISJONCTEUR, etat(false, false));
        be = (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
        verifier(!be.isOpen(), "coupé, il ne laisse plus passer");
        verifier(pousser(helper, 100) == 0, "coupé, rien ne devait passer");
        verifier(machine.energy().getEnergyStored() == 100, "la machine ne devait rien recevoir de plus : "
                + machine.energy().getEnergyStored());
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, ENTREE).resolve().orElse(null);
        verifier(cap != null && !cap.canReceive(), "coupé, l'entrée n'accepte rien");
        verifier(be.totalTransferred() == 100, "l'index ne compte que ce qui est passé : " + be.totalTransferred());
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_bascule")
    public static void toutLeMondePeutLeBasculer(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        poser(helper, true, false);
        JoueurDeTest citoyen = joueur(helper, "Passant", false);
        JoueurDeTest ingenieur = joueur(helper, "Ohm", true);
        try {
            verifier(basculer(helper, citoyen.joueur()).consumesAction(), "le clic du citoyen devait être pris");
            verifier(!enclenche(helper), "le citoyen devait couper");
            verifier(pousser(helper, 50) == 0, "coupé par le citoyen, rien ne passe");
            verifier(basculer(helper, ingenieur.joueur()).consumesAction(), "le clic de l'ingénieur devait être pris");
            verifier(enclenche(helper), "l'ingénieur devait réenclencher");
            verifier(pousser(helper, 50) == 50 && machine.energy().getEnergyStored() == 50, "le courant devait repasser");
            basculer(helper, citoyen.joueur());
            verifier(!enclenche(helper) && !scelle(helper), "le citoyen coupe de nouveau, sans sceller");
            helper.succeed();
        } finally {
            quitter(citoyen, ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_scelle_role")
    public static void seulLIngenieurScelle(GameTestHelper helper) {
        poser(helper, false, false);
        JoueurDeTest citoyen = joueur(helper, "Profane", false);
        JoueurDeTest ingenieur = joueur(helper, "Faraday", true);
        try {
            ItemStack aucun = scelles(2);
            cliquer(helper, citoyen.joueur(), aucun, false);
            verifier(!scelle(helper), "le citoyen ne scelle pas");
            verifier(aucun.getCount() == 2, "le scellé du citoyen ne devait pas être consommé");
            verifierMessage(citoyen, RoleGate.ENGINEER_ONLY_KEY, "scellé par un citoyen");

            // Inscrire l'abonné est réservé aussi : accroupi, main vide.
            citoyen.ecransAbonneRecus();
            cliquer(helper, citoyen.joueur(), ItemStack.EMPTY, true);
            verifier(citoyen.ecransAbonneRecus().isEmpty(), "le citoyen ne reçoit pas l'écran d'inscription");
            verifierMessage(citoyen, RoleGate.ENGINEER_ONLY_KEY, "inscription par un citoyen");

            ItemStack pile = scelles(2);
            verifier(cliquer(helper, ingenieur.joueur(), pile, false).consumesAction(), "le scellé devait agir");
            verifier(scelle(helper) && !enclenche(helper), "l'ingénieur scelle un disjoncteur coupé");
            verifier(pile.getCount() == 1, "un scellé consommé, reste " + pile.getCount());

            // Un disjoncteur déjà scellé n'en reprend pas un second.
            cliquer(helper, ingenieur.joueur(), pile, false);
            verifier(pile.getCount() == 1, "pas de second scellé sur un scellé");
            verifierMessage(ingenieur, MSG + "already_sealed", "second scellé");
            helper.succeed();
        } finally {
            quitter(citoyen, ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_scelle_creatif")
    public static void enCreatifLeScelleNEstPasConsomme(GameTestHelper helper) {
        poser(helper, false, false);
        JoueurDeTest ingenieur = joueur(helper, "Tesla", true);
        try {
            ingenieur.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            ItemStack pile = scelles(1);
            cliquer(helper, ingenieur.joueur(), pile, false);
            verifier(scelle(helper), "scellé en créatif");
            verifier(pile.getCount() == 1, "en créatif le scellé n'est pas consommé, reste " + pile.getCount());
            helper.succeed();
        } finally {
            quitter(ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_inscription")
    public static void lIngenieurInscritLAbonneAccroupi(GameTestHelper helper) {
        BreakerBlockEntity be = poser(helper, true, false);
        be.setSubscriber("Rousseau");
        BlockPos pos = helper.absolutePos(DISJONCTEUR);
        JoueurDeTest ingenieur = joueur(helper, "Joule", true);
        try {
            ingenieur.ecransAbonneRecus();
            cliquer(helper, ingenieur.joueur(), ItemStack.EMPTY, true);
            List<SubscriberNamePacket.Open> ecrans = ingenieur.ecransAbonneRecus();
            verifier(ecrans.size() == 1 && ecrans.get(0).pos().equals(pos) && "Rousseau".equals(ecrans.get(0).current()),
                    "l'écran du disjoncteur devait s'ouvrir, vu " + ecrans);
            verifier(enclenche(helper), "ouvrir l'écran ne bascule pas le disjoncteur");
            ingenieur.envoyer(new SubscriberNamePacket(pos, "Lambert"));
        } catch (RuntimeException e) {
            quitter(ingenieur);
            throw e;
        }
        helper.runAfterDelay(4, () -> {
            try {
                verifier("Lambert".equals(be.subscriber()), "abonné " + be.subscriber());
                helper.succeed();
            } finally {
                quitter(ingenieur);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_enclenche")
    public static void onNeScellePasUnDisjoncteurEnclenche(GameTestHelper helper) {
        poser(helper, true, false);
        JoueurDeTest ingenieur = joueur(helper, "Watt", true);
        try {
            ItemStack pile = scelles(3);
            cliquer(helper, ingenieur.joueur(), pile, false);
            verifier(!scelle(helper) && enclenche(helper), "un disjoncteur enclenché ne se scelle pas");
            verifier(pile.getCount() == 3, "le scellé ne devait pas être consommé");
            verifierMessage(ingenieur, MSG + "seal_needs_cut", "scellé sur un disjoncteur enclenché");
            helper.succeed();
        } finally {
            quitter(ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_reste_coupe")
    public static void scelleIlNeSeReenclenchePas(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        poser(helper, false, true);
        JoueurDeTest citoyen = joueur(helper, "Pressé", false);
        JoueurDeTest ingenieur = joueur(helper, "Henry", true);
        try {
            for (JoueurDeTest j : new JoueurDeTest[] {citoyen, ingenieur}) {
                j.messagesRecus();
                verifier(basculer(helper, j.joueur()).consumesAction(), "le clic devait être pris");
                verifier(!enclenche(helper) && scelle(helper), "un disjoncteur scellé ne se réenclenche pas");
                verifierMessage(j, MSG + "sealed", "clic sur un disjoncteur scellé");
            }
            verifier(pousser(helper, 100) == 0 && machine.energy().getEnergyStored() == 0, "rien ne doit passer");
            helper.succeed();
        } finally {
            quitter(citoyen, ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_retire")
    public static void retirerLeScelleLeDetruit(GameTestHelper helper) {
        poser(helper, false, true);
        JoueurDeTest citoyen = joueur(helper, "Voleur", false);
        JoueurDeTest ingenieur = joueur(helper, "Ampere2", true);
        try {
            cliquer(helper, citoyen.joueur(), ItemStack.EMPTY, true);
            verifier(scelle(helper), "le citoyen ne retire pas le scellé");
            verifierMessage(citoyen, RoleGate.ENGINEER_ONLY_KEY, "scellé retiré par un citoyen");
            verifier(nombreDeScelles(citoyen.joueur()) == 0, "le citoyen n'a rien récupéré");

            verifier(cliquer(helper, ingenieur.joueur(), ItemStack.EMPTY, true).consumesAction(),
                    "le geste de l'ingénieur devait être pris");
            verifier(!scelle(helper), "l'ingénieur retire le scellé");
            verifier(!enclenche(helper), "retirer le scellé ne réenclenche pas");
            verifier(nombreDeScelles(ingenieur.joueur()) == 0 && scellesAuSol(helper) == 0,
                    "le scellé retiré est détruit : aucun objet récupérable");
            // Sans scellé, tout le monde le rebascule.
            basculer(helper, citoyen.joueur());
            verifier(enclenche(helper), "sans scellé, il se rebascule");
            helper.succeed();
        } finally {
            quitter(citoyen, ingenieur);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_casse", setupTicks = 20)
    public static void casseScelleLeReleveLeSignale(GameTestHelper helper) {
        poserMachine(helper);
        helper.setBlock(COMPTEUR, GridRegistry.METER.get().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.NORTH));
        verifier(helper.getBlockEntity(COMPTEUR) instanceof MeterBlockEntity, "pas de compteur");
        MeterBlockEntity compteur = (MeterBlockEntity) helper.getBlockEntity(COMPTEUR);
        compteur.setSubscriber("Mercier");
        BreakerBlockEntity be = poser(helper, false, true);
        be.setSubscriber("Mercier");
        JoueurDeTest j = joueur(helper, "Releveur2", true);
        try {
            tester(helper, j.joueur());
            verifier(releves(j.joueur()).size() == 1, "premier relevé attendu");

            // Casser un disjoncteur non scellé ne laisse aucune trace.
            helper.setBlock(DISJONCTEUR, etat(true, false));
            helper.getLevel().destroyBlock(helper.absolutePos(DISJONCTEUR), false);
            tester(helper, j.joueur());
            List<Reading> rien = releves(j.joueur());
            verifier(rien.size() == 1 && rien.get(0).anomalies().isEmpty(),
                    "casser un disjoncteur non scellé est sans trace : " + rien);

            BreakerBlockEntity scelle = poser(helper, false, true);
            scelle.setSubscriber("Mercier");
            helper.getLevel().destroyBlock(helper.absolutePos(DISJONCTEUR), true);
            verifier(helper.getBlockState(DISJONCTEUR).isAir(), "le disjoncteur devait être cassé");
            ItemStack objet = null;
            for (ItemEntity e : helper.getEntities(EntityType.ITEM, DISJONCTEUR, 3.0)) {
                if (e.getItem().is(GridRegistry.BREAKER_ITEM.get())) {
                    objet = e.getItem().copy();
                }
                e.discard();
            }
            verifier(objet != null, "le disjoncteur cassé devait lâcher son objet");
            tester(helper, j.joueur());
            Reading r = releves(j.joueur()).get(0);
            verifier(r.anomalies().equals(List.of(Anomaly.SEAL_BROKEN)), "anomalies " + r.anomalies());

            // L'objet rendu n'a plus de scellé : posé, il est enclenché et libre, et garde son abonné.
            Player poseur = helper.makeMockPlayer();
            poseur.setYRot(180f);
            poseur.setItemInHand(InteractionHand.MAIN_HAND, objet);
            BlockPos cible = helper.absolutePos(DISJONCTEUR);
            InteractionResult pose = objet.useOn(new UseOnContext(poseur, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(cible), Direction.UP, cible, false)));
            verifier(pose.consumesAction(), "la pose devait réussir, vu " + pose);
            verifier(!scelle(helper), "l'objet rendu n'a pas de scellé");
            verifier("Mercier".equals(((BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR)).subscriber()),
                    "l'abonné devait suivre l'objet");
            tester(helper, j.joueur());
            verifier(releves(j.joueur()).get(0).anomalies().isEmpty(), "la trace est consommée par le relevé");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_sauvegarde")
    public static void lEtatSurvitAuRechargement(GameTestHelper helper) {
        BreakerBlockEntity be = poser(helper, false, true);
        be.setSubscriber("Garnier");
        BlockState etat = helper.getBlockState(DISJONCTEUR);
        BlockState relu = NbtUtils.readBlockState(helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                NbtUtils.writeBlockState(etat));
        verifier(!relu.getValue(BreakerBlock.CLOSED) && relu.getValue(BreakerBlock.SEALED),
                "coupé et scellé devait être relu : " + relu);
        CompoundTag tag = be.saveWithoutMetadata();
        BreakerBlockEntity copie = new BreakerBlockEntity(helper.absolutePos(DISJONCTEUR), relu);
        copie.load(tag);
        verifier("Garnier".equals(copie.subscriber()), "abonné relu " + copie.subscriber());
        verifier(!copie.isOpen(), "le disjoncteur relu coupé ne laisse pas passer");
        // Le bloc du monde, lui, garde son état d'un tick à l'autre.
        helper.runAfterDelay(5, () -> {
            verifier(!enclenche(helper) && scelle(helper), "l'état devait rester");
            helper.succeed();
        });
    }

    @GameTest(template = "test/plancher", batch = "disjoncteur_usure", setupTicks = 20)
    public static void ilNeSUsePas(GameTestHelper helper) {
        poserMachine(helper);
        BreakerBlockEntity be = poser(helper, true, false);
        verifier(!((Object) be instanceof ElectricMachineBlockEntity), "le disjoncteur n'est pas une machine à usure");
        verifier(((BreakerBlock) GridRegistry.BREAKER.get()).getTicker(helper.getLevel(), be.getBlockState(),
                GridRegistry.BREAKER_BE.get()) == null, "le disjoncteur ne tique pas : rien ne s'use");
        helper.runAfterDelay(1, () -> pousser(helper, 100));
        helper.runAfterDelay(10, () -> {
            verifier(be.totalTransferred() == 100, "le courant devait passer : " + be.totalTransferred());
            CompoundTag tag = be.saveWithoutMetadata();
            verifier(!tag.contains("condition") && !tag.contains("fault"), "aucun état d'usure dans " + tag);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Relevé (le compteur du lot, pour lire la trace du scellé brisé)
    // ---------------------------------------------------------------------------------------------

    private static void tester(GameTestHelper helper, Player joueur) {
        ItemStack testeur = new ItemStack(RepairRegistry.TESTER.get());
        joueur.setItemInHand(InteractionHand.MAIN_HAND, testeur);
        BlockPos cible = helper.absolutePos(COMPTEUR);
        testeur.getItem().onItemUseFirst(testeur, new UseOnContext(helper.getLevel(), joueur, InteractionHand.MAIN_HAND,
                testeur, new BlockHitResult(Vec3.atCenterOf(cible), Direction.SOUTH, cible, false)));
    }

    private static List<Reading> releves(Player joueur) {
        List<Reading> out = new ArrayList<>();
        for (int i = 0; i < joueur.getInventory().getContainerSize(); i++) {
            ItemStack pile = joueur.getInventory().getItem(i);
            if (pile.is(GridRegistry.METER_READING.get())) {
                Reading r = MeterReadingItem.reading(pile);
                verifier(r != null, "un relevé sans contenu");
                out.add(r);
                joueur.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        return out;
    }
}
