package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlock;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.MeterLedger;
import dev.oneforallsurvival.grid.MeterLedger.Anomaly;
import dev.oneforallsurvival.grid.MeterLedger.EventKind;
import dev.oneforallsurvival.grid.MeterLedger.Reading;
import dev.oneforallsurvival.grid.MeterReadingItem;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import dev.oneforallsurvival.grid.SubscriberSavedData;
import dev.oneforallsurvival.machine.ElectricMachineBlockEntity;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le compteur. Il regarde vers le nord : sa sortie touche la machine d'essai posée au nord (1 000 FE de réserve,
 * 100 FE au plus par appel), son entrée est la face sud. Les joueurs sont de vrais joueurs non opérateurs, Ingénieur
 * ou Citoyen. Le registre des abonnés appartient au monde entier : chaque test a son lot et ses propres noms.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CompteurGameTests {

    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos COMPTEUR = new BlockPos(1, 1, 2);
    private static final BlockPos JOUEUR = new BlockPos(1, 1, 4);
    private static final Direction ENTREE = Direction.SOUTH;

    private CompteurGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static MeterBlockEntity poserCompteur(GameTestHelper helper) {
        helper.setBlock(COMPTEUR, GridRegistry.METER.get().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.NORTH));
        verifier(helper.getBlockEntity(COMPTEUR) instanceof MeterBlockEntity, "pas de compteur en " + COMPTEUR);
        return (MeterBlockEntity) helper.getBlockEntity(COMPTEUR);
    }

    private static TestMachineBlockEntity poserMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, DevBlocks.testMachine());
        verifier(helper.getBlockEntity(MACHINE) instanceof TestMachineBlockEntity, "pas de machine d'essai");
        return (TestMachineBlockEntity) helper.getBlockEntity(MACHINE);
    }

    /** Pousse {@code fe} FE dans l'entrée du compteur ; renvoie ce qui est passé. */
    private static int pousser(MeterBlockEntity be, int fe) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, ENTREE).resolve().orElse(null);
        verifier(cap != null, "pas de capability sur la face d'entrée du compteur");
        return cap.receiveEnergy(fe, false);
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
        BlockPos cible = helper.absolutePos(COMPTEUR);
        return new BlockHitResult(Vec3.atCenterOf(cible), Direction.SOUTH, cible, false);
    }

    /** Clic droit main vide sur le compteur. */
    private static InteractionResult cliquer(GameTestHelper helper, Player joueur) {
        joueur.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return helper.getBlockState(COMPTEUR).use(helper.getLevel(), joueur, InteractionHand.MAIN_HAND, viser(helper));
    }

    /** Le testeur en main, clic droit sur le compteur. */
    private static InteractionResult tester(GameTestHelper helper, Player joueur) {
        ItemStack testeur = new ItemStack(RepairRegistry.TESTER.get());
        joueur.setItemInHand(InteractionHand.MAIN_HAND, testeur);
        return testeur.getItem().onItemUseFirst(testeur, new UseOnContext(helper.getLevel(), joueur,
                InteractionHand.MAIN_HAND, testeur, viser(helper)));
    }

    /** Les relevés de l'inventaire du joueur, retirés de l'inventaire. */
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

    /** Le seul relevé de l'inventaire du joueur. */
    private static Reading releve(Player joueur) {
        List<Reading> r = releves(joueur);
        verifier(r.size() == 1, "un relevé attendu, vu " + r.size());
        return r.get(0);
    }

    private static MinecraftServer serveur(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = "compteur_compte")
    public static void ilCompteLesFEQuiPassent(GameTestHelper helper) {
        TestMachineBlockEntity machine = poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        int passe = pousser(be, 100) + pousser(be, 60);
        verifier(passe == 160, "retour : " + passe);
        verifier(machine.energy().getEnergyStored() == 160, "machine : " + machine.energy().getEnergyStored());
        verifier(be.totalTransferred() == 160, "index : " + be.totalTransferred());
        verifier(be.subscriber().isEmpty(), "un compteur neuf n'a pas d'abonné : " + be.subscriber());
        UUID numero = be.serial();
        verifier(numero != null && numero.equals(be.serial()), "le numéro doit être tiré une fois et gardé");
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = "compteur_inscription")
    public static void seulLIngenieurInscritLAbonne(GameTestHelper helper) {
        MeterBlockEntity be = poserCompteur(helper);
        BlockPos pos = helper.absolutePos(COMPTEUR);
        JoueurDeTest citoyen = null;
        JoueurDeTest ingenieur = null;
        try {
            citoyen = joueur(helper, "Badaud", false);
            ingenieur = joueur(helper, "Volta", true);
            citoyen.ecransAbonneRecus();
            ingenieur.ecransAbonneRecus();
            verifier(cliquer(helper, citoyen.joueur()).consumesAction(), "le clic du citoyen devait être pris");
            verifier(citoyen.ecransAbonneRecus().isEmpty(), "le citoyen ne devait pas recevoir l'écran d'inscription");
            verifier(cliquer(helper, ingenieur.joueur()).consumesAction(), "le clic de l'ingénieur devait être pris");
            List<SubscriberNamePacket.Open> ecrans = ingenieur.ecransAbonneRecus();
            verifier(ecrans.size() == 1 && ecrans.get(0).pos().equals(pos) && ecrans.get(0).current().isEmpty(),
                    "l'ingénieur devait recevoir l'écran du compteur, vu " + ecrans);
            citoyen.envoyer(new SubscriberNamePacket(pos, "Fraudeur"));
        } catch (RuntimeException e) {
            quitter(citoyen, ingenieur);
            throw e;
        }
        JoueurDeTest c = citoyen;
        JoueurDeTest i = ingenieur;
        helper.runAfterDelay(3, () -> {
            try {
                verifier(be.subscriber().isEmpty(), "le paquet du citoyen devait être refusé, abonné " + be.subscriber());
                i.envoyer(new SubscriberNamePacket(pos, "  Martin "));
            } catch (RuntimeException e) {
                quitter(c, i);
                throw e;
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                verifier("Martin".equals(be.subscriber()), "l'ingénieur devait inscrire Martin, vu " + be.subscriber());
                // Trop loin du compteur : refusé.
                Vec3 loin = helper.absoluteVec(Vec3.atBottomCenterOf(JOUEUR)).add(0, 0, 30);
                i.joueur().teleportTo(loin.x, loin.y, loin.z);
                i.envoyer(new SubscriberNamePacket(pos, "Lointain"));
            } catch (RuntimeException e) {
                quitter(c, i);
                throw e;
            }
        });
        helper.runAfterDelay(9, () -> {
            try {
                verifier("Martin".equals(be.subscriber()), "un ingénieur trop loin ne devait rien changer, vu "
                        + be.subscriber());
                helper.succeed();
            } finally {
                quitter(c, i);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "compteur_nom_invalide")
    public static void unNomInvalideEstRefuse(GameTestHelper helper) {
        MeterBlockEntity be = poserCompteur(helper);
        BlockPos pos = helper.absolutePos(COMPTEUR);
        JoueurDeTest ingenieur = joueur(helper, "Ampere", true);
        try {
            ingenieur.envoyer(new SubscriberNamePacket(pos, "Bob!"));
            ingenieur.envoyer(new SubscriberNamePacket(pos, "abcdefghijklmnopq"));
            ingenieur.envoyer(new SubscriberNamePacket(pos, "   "));
            ingenieur.envoyer(new SubscriberNamePacket(pos, "§cRouge"));
        } catch (RuntimeException e) {
            quitter(ingenieur);
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            try {
                verifier(be.subscriber().isEmpty(), "aucun nom invalide ne devait passer, vu " + be.subscriber());
                ingenieur.envoyer(new SubscriberNamePacket(pos, "Jean-Luc 2"));
            } catch (RuntimeException e) {
                quitter(ingenieur);
                throw e;
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                verifier("Jean-Luc 2".equals(be.subscriber()), "un nom valide devait passer, vu " + be.subscriber());
                helper.succeed();
            } finally {
                quitter(ingenieur);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "compteur_releve")
    public static void leReleveDonneLaConsommationDepuisLePrecedent(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        be.setSubscriber("Dupont");
        JoueurDeTest j = joueur(helper, "Releveur", true);
        try {
            pousser(be, 100);
            verifier(tester(helper, j.joueur()).consumesAction(), "le testeur devait agir sur le compteur");
            Reading premier = releve(j.joueur());
            verifier("Dupont".equals(premier.subscriber()), "abonné " + premier.subscriber());
            verifier(premier.index() == 100 && premier.consumption() == 100,
                    "premier relevé : index " + premier.index() + ", consommation " + premier.consumption());
            verifier(premier.anomalies().isEmpty(), "anomalies " + premier.anomalies());
            long jour = MeterLedger.gameDay(helper.getLevel().getDayTime());
            verifier(premier.day() == jour, "jour " + premier.day() + " au lieu de " + jour);
            verifier(premier.realDate().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}"), "date " + premier.realDate());

            pousser(be, 50);
            tester(helper, j.joueur());
            Reading second = releve(j.joueur());
            verifier(second.index() == 150 && second.consumption() == 50,
                    "second relevé : index " + second.index() + ", consommation " + second.consumption());
            verifier(second.anomalies().isEmpty(), "anomalies " + second.anomalies());
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    /**
     * Par le vrai paquet d'inscription : ce que le compteur a compté avant d'être inscrit n'est facturé à personne ;
     * changer d'abonné laisse à l'ancien ce qu'il n'avait pas payé, et le nouveau part de l'index du changement.
     */
    @GameTest(template = "test/plancher", batch = "compteur_inscription_index")
    public static void laFactureCommenceALInscriptionEtSuitLeChangementDAbonne(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        BlockPos pos = helper.absolutePos(COMPTEUR);
        UUID numero = be.serial();
        pousser(be, 100); // compté avant toute inscription
        JoueurDeTest ingenieur = joueur(helper, "Inscripteur", true);
        try {
            ingenieur.envoyer(new SubscriberNamePacket(pos, "Lefevre"));
        } catch (RuntimeException e) {
            quitter(ingenieur);
            throw e;
        }
        helper.runAfterDelay(3, () -> {
            try {
                verifier("Lefevre".equals(be.subscriber()), "l'ingénieur devait inscrire Lefevre, vu " + be.subscriber());
                SubscriberSavedData data = SubscriberSavedData.get(serveur(helper));
                MeterLedger.Previous depart = data.previous("Lefevre", numero);
                verifier(depart != null && depart.baseline() && depart.index() == 100,
                        "l'inscription devait noter l'index 100 au registre, vu " + depart);
                pousser(be, 30);
                tester(helper, ingenieur.joueur());
                Reading r = releve(ingenieur.joueur());
                verifier(r.index() == 130 && r.consumption() == 30,
                        "seul l'après-inscription est facturé : index " + r.index() + ", consommation " + r.consumption());
                verifier(r.anomalies().isEmpty(), "anomalies " + r.anomalies());
                pousser(be, 20); // non relevé quand le compteur change d'abonné
                ingenieur.envoyer(new SubscriberNamePacket(pos, "Moreau"));
            } catch (RuntimeException e) {
                quitter(ingenieur);
                throw e;
            }
        });
        helper.runAfterDelay(6, () -> {
            try {
                verifier("Moreau".equals(be.subscriber()), "le compteur devait passer à Moreau, vu " + be.subscriber());
                SubscriberSavedData data = SubscriberSavedData.get(serveur(helper));
                verifier(data.previous("Lefevre", numero) == null, "le compteur devait quitter le compte de Lefevre");
                List<MeterLedger.Event> events = data.events("Lefevre");
                verifier(events.size() == 1 && events.get(0).kind() == EventKind.RENAMED && events.get(0).carried() == 20
                        && numero.equals(events.get(0).serial()),
                        "Lefevre devait garder les 20 FE non relevés, vu " + events);
                pousser(be, 5);
                tester(helper, ingenieur.joueur());
                Reading r = releve(ingenieur.joueur());
                verifier("Moreau".equals(r.subscriber()) && r.index() == 155 && r.consumption() == 5,
                        "Moreau ne paie que depuis le changement : " + r.subscriber() + ", index " + r.index()
                                + ", consommation " + r.consumption());
                verifier(r.anomalies().isEmpty(), "anomalies " + r.anomalies());
                helper.succeed();
            } finally {
                quitter(ingenieur);
            }
        });
    }

    @GameTest(template = "test/plancher", batch = "compteur_citoyen")
    public static void unCitoyenNeReleveRien(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        be.setSubscriber("Bernard");
        pousser(be, 100);
        JoueurDeTest j = joueur(helper, "Curieux", false);
        try {
            tester(helper, j.joueur());
            verifier(releves(j.joueur()).isEmpty(), "le citoyen ne devait recevoir aucun relevé");
            verifier(SubscriberSavedData.get(serveur(helper)).previous("Bernard", be.serial()) == null,
                    "rien ne devait être écrit au registre");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "compteur_sans_abonne")
    public static void sansAbonnePasDeReleve(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        pousser(be, 100);
        JoueurDeTest j = joueur(helper, "Volta2", true);
        try {
            tester(helper, j.joueur());
            verifier(releves(j.joueur()).isEmpty(), "sans abonné, aucun relevé");
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "compteur_depose", setupTicks = 20)
    public static void deposeEtReposeIlGardeSonIndexEtLeReleveLeSignale(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        be.setSubscriber("Durand");
        UUID numero = be.serial();
        pousser(be, 100);
        JoueurDeTest j = joueur(helper, "Demonteur", true);
        try {
            tester(helper, j.joueur());
            verifier(releve(j.joueur()).consumption() == 100, "premier relevé faux");

            helper.getLevel().destroyBlock(helper.absolutePos(COMPTEUR), true);
            verifier(helper.getBlockState(COMPTEUR).isAir(), "le compteur devait être cassé");
            ItemStack objet = null;
            for (ItemEntity e : helper.getEntities(EntityType.ITEM, COMPTEUR, 3.0)) {
                if (e.getItem().is(GridRegistry.METER_ITEM.get())) {
                    objet = e.getItem().copy();
                    e.discard();
                }
            }
            verifier(objet != null, "le compteur cassé devait lâcher son objet");
            verifier(objet.getTagElement("BlockEntityTag") != null, "l'objet devait porter index, numéro et abonné");

            Player poseur = helper.makeMockPlayer();
            poseur.setYRot(180f); // regarde vers le nord : la sortie du compteur touche la machine
            poseur.setItemInHand(InteractionHand.MAIN_HAND, objet);
            BlockPos cible = helper.absolutePos(COMPTEUR);
            InteractionResult pose = objet.useOn(new UseOnContext(poseur, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(cible), Direction.UP, cible, false)));
            verifier(pose.consumesAction(), "la pose devait réussir, vu " + pose);
            verifier(helper.getBlockEntity(COMPTEUR) instanceof MeterBlockEntity, "pas de compteur reposé");
            MeterBlockEntity repose = (MeterBlockEntity) helper.getBlockEntity(COMPTEUR);
            verifier(repose.getBlockState().getValue(InlineEnergyBlock.FACING) == Direction.NORTH,
                    "orientation " + repose.getBlockState().getValue(InlineEnergyBlock.FACING));
            verifier(repose.totalTransferred() == 100, "index reposé " + repose.totalTransferred());
            verifier(numero.equals(repose.serial()), "le numéro devait suivre l'objet");
            verifier("Durand".equals(repose.subscriber()), "abonné reposé " + repose.subscriber());

            pousser(repose, 30);
            tester(helper, j.joueur());
            Reading r = releve(j.joueur());
            verifier(r.index() == 130 && r.consumption() == 30, "index " + r.index() + ", consommation " + r.consumption());
            verifier(r.anomalies().equals(List.of(Anomaly.METER_REMOVED)), "anomalies " + r.anomalies());
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "compteur_neuf")
    public static void unCompteurNeufALaPlaceEstSignale(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity ancien = poserCompteur(helper);
        ancien.setSubscriber("Petit");
        pousser(ancien, 100);
        JoueurDeTest j = joueur(helper, "Fraudeur2", true);
        try {
            tester(helper, j.joueur());
            releve(j.joueur());
            helper.setBlock(COMPTEUR, Blocks.AIR);
            MeterBlockEntity neuf = poserCompteur(helper);
            verifier(!neuf.serial().equals(ancien.serial()), "un compteur neuf a son propre numéro");
            neuf.setSubscriber("Petit");
            pousser(neuf, 40);
            tester(helper, j.joueur());
            Reading r = releve(j.joueur());
            verifier(r.consumption() == 40, "consommation " + r.consumption());
            verifier(r.anomalies().contains(Anomaly.METER_CHANGED), "le changement devait être signalé : " + r.anomalies());
            verifier(r.anomalies().contains(Anomaly.METER_REMOVED), "la dépose devait être signalée : " + r.anomalies());
            helper.succeed();
        } finally {
            quitter(j);
        }
    }

    @GameTest(template = "test/plancher", batch = "compteur_sauvegarde")
    public static void leRegistreSurvitAuRechargement(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        be.setSubscriber("Leroy");
        pousser(be, 70);
        JoueurDeTest j = joueur(helper, "Archiviste", true);
        try {
            tester(helper, j.joueur());
            releve(j.joueur());
        } finally {
            quitter(j);
        }
        SubscriberSavedData data = SubscriberSavedData.get(serveur(helper));
        // Un autre bloc (le disjoncteur) note un scellé brisé par cette méthode publique.
        SubscriberSavedData.note(serveur(helper), "Leroy", EventKind.SEAL_BROKEN);
        verifier(data.isDirty(), "le registre devait être marqué à sauvegarder");

        SubscriberSavedData relu = SubscriberSavedData.load(data.save(new CompoundTag()));
        verifier(relu.previous("leroy", be.serial()) != null && relu.previous("leroy", be.serial()).index() == 70,
                "dernier relevé relu : " + relu.previous("leroy", be.serial()));
        verifier(relu.previous("Leroy", be.serial()).serial().equals(be.serial()), "numéro relu faux");
        verifier(relu.events("Leroy").size() == 1 && relu.events("Leroy").get(0).kind() == EventKind.SEAL_BROKEN,
                "événements relus : " + relu.events("Leroy"));

        CompoundTag tag = be.saveWithoutMetadata();
        MeterBlockEntity copie = new MeterBlockEntity(helper.absolutePos(COMPTEUR), be.getBlockState());
        copie.load(tag);
        verifier(copie.totalTransferred() == 70, "index relu " + copie.totalTransferred());
        verifier(be.serial().equals(copie.serial()), "numéro relu " + copie.serial());
        verifier("Leroy".equals(copie.subscriber()), "abonné relu " + copie.subscriber());

        // Le scellé brisé ressort au relevé suivant.
        JoueurDeTest j2 = joueur(helper, "Archiviste2", true);
        try {
            tester(helper, j2.joueur());
            Reading r = releve(j2.joueur());
            verifier(r.anomalies().equals(List.of(Anomaly.SEAL_BROKEN)), "anomalies " + r.anomalies());
            helper.succeed();
        } finally {
            quitter(j2);
        }
    }

    @GameTest(template = "test/plancher", batch = "compteur_usure", setupTicks = 20)
    public static void ilNeSUsePas(GameTestHelper helper) {
        poserMachine(helper);
        MeterBlockEntity be = poserCompteur(helper);
        verifier(!((Object) be instanceof ElectricMachineBlockEntity), "le compteur n'est pas une machine à usure");
        verifier(((MeterBlock) GridRegistry.METER.get()).getTicker(helper.getLevel(), be.getBlockState(),
                GridRegistry.METER_BE.get()) == null, "le compteur ne tique pas : rien ne s'use");
        helper.runAfterDelay(1, () -> pousser(be, 100));
        helper.runAfterDelay(10, () -> {
            verifier(be.totalTransferred() == 100, "le courant devait passer : " + be.totalTransferred());
            CompoundTag tag = be.saveWithoutMetadata();
            verifier(!tag.contains("condition") && !tag.contains("fault"), "aucun état d'usure dans " + tag);
            helper.succeed();
        });
    }
}
