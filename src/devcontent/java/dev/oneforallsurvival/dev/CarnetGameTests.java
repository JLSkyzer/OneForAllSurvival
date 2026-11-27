package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneDirectory;
import dev.oneforallsurvival.phone.call.Recent;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le carnet de la carte SIM (lot T3, tâche 2) : contacts et récents gardés par le serveur, relus du fichier du monde
 * tels qu'un redémarrage les relirait, et rangés par carte ; ils la suivent donc quand elle est remplacée.
 *
 * <p>Tous les essais sont synchrones. Chaque carnet est celui d'une carte tirée au hasard : un passage ne lit rien du
 * précédent.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class CarnetGameTests {

    private static final String LOT = "telephone_carnet";

    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    private static final String MAMAN = "0611223344";
    private static final String GARAGE = "0699887766";

    /** Les essais pas encore rendus : {@link #apres} les rend si un essai ne l'a pas fait. Fil du serveur. */
    private static final List<Essai> OUVERTS = new ArrayList<>();

    private CarnetGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        for (Essai essai : new ArrayList<>(OUVERTS)) {
            essai.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Un joueur d'essai et son opérateur, rendus à la fermeture. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final JoueurDeTest joueur;

        Essai(GameTestHelper helper, String nom) {
            this.helper = helper;
            joueur = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            OUVERTS.add(this);
        }

        @Override
        public void close() {
            if (!OUVERTS.remove(this)) {
                return;
            }
            if (joueur.connecte()) {
                joueur.quitter();
            }
            OperateurDEssai.ranger(helper, joueur.uuid());
        }
    }

    /** Le carnet tel qu'un redémarrage le relirait : un stockage neuf sur le dossier des données du monde. */
    private static PhoneDirectory relu(GameTestHelper helper) {
        MinecraftServer serveur = helper.getLevel().getServer();
        File dossier = serveur.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
        helper.assertTrue(new File(dossier, PhoneDirectory.KEY + ".dat").isFile(),
                "le fichier du carnet n'est pas écrit");
        PhoneDirectory relu = new DimensionDataStorage(dossier, serveur.getFixerUpper())
                .get(PhoneDirectory::load, PhoneDirectory.KEY);
        helper.assertTrue(relu != null && relu != PhoneDirectory.get(serveur), "carnet non relu du disque");
        return relu;
    }

    /** Écrit ce qu'une sauvegarde du monde écrit (les données changées), puis relit le fichier. */
    private static PhoneDirectory ecrireEtRelire(GameTestHelper helper) {
        helper.getLevel().getServer().overworld().getDataStorage().save();
        return relu(helper);
    }

    private static void memeCarnet(GameTestHelper helper, UUID carte, PhoneDirectory vu, List<Contact> contacts,
            List<Recent> recents, String moment) {
        helper.assertTrue(vu.contacts(carte).equals(contacts),
                moment + " : contacts " + vu.contacts(carte) + " au lieu de " + contacts);
        helper.assertTrue(vu.recents(carte).equals(recents),
                moment + " : récents " + vu.recents(carte) + " au lieu de " + recents);
    }

    private static ItemStack telephone() {
        return new ItemStack(PhoneRegistry.PHONE.get());
    }

    /** La case de l'inventaire qui porte la carte {@code simId}. */
    private static int caseDe(GameTestHelper helper, ServerPlayer p, UUID simId) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (SimCardItem.simId(p.getInventory().getItem(i)).filter(simId::equals).isPresent()) {
                return i;
            }
        }
        helper.fail("la carte " + simId + " n'est pas dans l'inventaire");
        return -1;
    }

    private static UUID emettre(GameTestHelper helper, PhoneOperator op, JoueurDeTest joueur) {
        Optional<UUID> id = SimCardItem.simId(OperateurDEssai.emettre(helper, op, joueur));
        helper.assertTrue(id.isPresent(), "la pile émise n'est pas une carte");
        return id.get();
    }

    private static SimCard ligne(GameTestHelper helper, ItemStack phone, String qui) {
        Optional<SimCard> ligne = PhoneSims.line(helper.getLevel().getServer(), phone);
        helper.assertTrue(ligne.isPresent(), qui + " n'a pas de ligne");
        return ligne.get();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_carnet
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT)
    public static void leCarnetSEcritEtSeReluDuFichier(GameTestHelper helper) {
        try (Essai essai = new Essai(helper, "carnet_fichier")) {
            ecritEtRelit(helper, essai);
        }
    }

    private static void ecritEtRelit(GameTestHelper helper, Essai essai) {
        MinecraftServer serveur = helper.getLevel().getServer();
        PhoneDirectory carnet = PhoneDirectory.get(serveur);
        helper.assertTrue(PhoneDirectory.get(serveur) == carnet, "un seul carnet par serveur");
        // Une vraie carte du registre : une carte inconnue ne reçoit pas de carnet (revue de la tâche 2, I1).
        PhoneOperator op = OperateurDEssai.fonder(helper, essai.joueur, "Carnet Fichier");
        UUID carte = emettre(helper, op, essai.joueur);
        helper.assertTrue(carnet.saveContact(UUID.randomUUID(), MAMAN, "Maman") == PhoneDirectory.SaveResult.DEAD_CARD,
                "une carte inconnue du registre n'a pas de carnet");
        long maintenant = EconomyClock.now();

        helper.assertTrue(carnet.saveContact(carte, "06 11 22 33 44", "  Maman ") == PhoneDirectory.SaveResult.SAVED,
                "contact Maman");
        helper.assertTrue(carnet.saveContact(carte, GARAGE, "garage d'Élie") == PhoneDirectory.SaveResult.SAVED,
                "contact garage");
        Recent manque = new Recent(GARAGE, Recent.Kind.MISSED, maintenant, 0);
        Recent passe = new Recent(MAMAN, Recent.Kind.OUTGOING, maintenant + 1_000L, 42);
        carnet.log(carte, manque);
        carnet.log(carte, passe);
        List<Contact> contacts = List.of(new Contact(GARAGE, "garage d'Élie"), new Contact(MAMAN, "Maman"));
        memeCarnet(helper, carte, ecrireEtRelire(helper), contacts, List.of(passe, manque), "première écriture");
        helper.assertTrue(!carnet.isDirty(), "écrit : plus rien en attente");

        // Chaque geste seul, après une écriture, doit atteindre le disque à son tour.
        carnet.saveContact(carte, MAMAN, "Mamie");
        contacts = List.of(new Contact(GARAGE, "garage d'Élie"), new Contact(MAMAN, "Mamie"));
        memeCarnet(helper, carte, ecrireEtRelire(helper), contacts, List.of(passe, manque), "après un renommage");

        helper.assertTrue(carnet.removeContact(carte, GARAGE), "retrait");
        contacts = List.of(new Contact(MAMAN, "Mamie"));
        memeCarnet(helper, carte, ecrireEtRelire(helper), contacts, List.of(passe, manque), "après un retrait");

        Recent recu = new Recent(MAMAN, Recent.Kind.INCOMING, maintenant + 2_000L, 7);
        carnet.log(carte, recu);
        PhoneDirectory fin = ecrireEtRelire(helper);
        memeCarnet(helper, carte, fin, contacts, List.of(recu, passe, manque), "après un appel");
        helper.assertTrue(fin.nameOf(carte, MAMAN).equals(Optional.of("Mamie")),
                "nom relu : " + fin.nameOf(carte, MAMAN));
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneCarteRemplaceeGardeSonCarnet(GameTestHelper helper) {
        try (Essai essai = new Essai(helper, "carnet_rempl")) {
            PhoneOperator op = OperateurDEssai.fonder(helper, essai.joueur, "Carnet Remplace");
            ServerPlayer p = essai.joueur.joueur();
            p.setItemInHand(InteractionHand.MAIN_HAND, telephone());
            ItemStack a = p.getItemInHand(InteractionHand.MAIN_HAND);
            p.getInventory().setItem(10, telephone());
            ItemStack b = p.getInventory().getItem(10);
            UUID carte = emettre(helper, op, essai.joueur);
            helper.assertTrue(PhoneSims.insert(p, a, caseDe(helper, p, carte)) == PhoneSims.Insert.DONE, "dans A");

            PhoneDirectory carnet = PhoneDirectory.get(essai.joueur.joueur().server);
            UUID deA = ligne(helper, a, "A").simId();
            helper.assertTrue(carnet.saveContact(deA, MAMAN, "Maman") == PhoneDirectory.SaveResult.SAVED, "contact");
            Recent appel = new Recent(MAMAN, Recent.Kind.INCOMING, EconomyClock.now(), 12);
            carnet.log(deA, appel);
            List<Contact> contacts = List.of(new Contact(MAMAN, "Maman"));

            // La carte est remplacée : l'ancienne pile est morte, la neuve garde l'identifiant, donc le carnet.
            helper.assertTrue(PhoneSims.replaceCard(p, carte).isPresent(), "remplacement");
            helper.assertTrue(PhoneSims.line(p.server, a).isEmpty(), "l'ancienne pile n'a plus de ligne");
            helper.assertTrue(PhoneSims.insert(p, b, caseDe(helper, p, carte)) == PhoneSims.Insert.DONE,
                    "la neuve dans B");
            UUID deB = ligne(helper, b, "B").simId();
            helper.assertTrue(deB.equals(deA), "même identifiant de carte : " + deA + " puis " + deB);
            memeCarnet(helper, deB, carnet, contacts, List.of(appel), "carte remplacée");

            // Le carnet est celui de la carte, pas du téléphone : une autre carte dans A part d'un carnet vide.
            UUID autre = emettre(helper, op, essai.joueur);
            helper.assertTrue(PhoneSims.insert(p, a, caseDe(helper, p, autre)) == PhoneSims.Insert.DONE,
                    "une autre carte dans A");
            UUID nouvelle = ligne(helper, a, "A").simId();
            helper.assertTrue(nouvelle.equals(autre), "A porte l'autre carte");
            memeCarnet(helper, nouvelle, carnet, List.of(), List.of(), "autre carte");

            memeCarnet(helper, deA, ecrireEtRelire(helper), contacts, List.of(appel), "relu du disque");
            helper.succeed();
        }
    }
}
