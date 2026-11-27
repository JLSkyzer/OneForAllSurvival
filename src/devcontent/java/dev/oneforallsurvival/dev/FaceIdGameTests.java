package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Face ID (retours d'essai du 27/11) : chaque règle de droit jouée par de vrais paquets. L'ouverture passe par le
 * paquet vanilla d'usage d'un objet ({@link ServerboundUseItemPacket}, écrit puis relu), les gestes de l'écran par le
 * paquet du mod ({@link JoueurDeTest#envoyer}) ; les états reçus sont décodés des octets envoyés. Négatifs : tête
 * couverte, quelqu'un d'autre que le propriétaire (même à côté de lui), attente après des codes faux, écran rallumé
 * après « Verrouiller », activation sans le bon code, effacement et nouveau propriétaire.
 *
 * <p>Le frein des gestes est oublié entre deux gestes d'un même tic ({@code PhoneService.clearThrottle}, par
 * réflexion, comme {@code TelephoneGameTests}). Le lot {@value #LOT_ATTENTE} fige l'horloge de l'économie.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class FaceIdGameTests {

    private static final String LOT = "faceid";
    private static final String LOT_ATTENTE = "faceid_attente";
    private static final Vec3 POS_A = new Vec3(2.5, 1, 2.5);
    private static final Vec3 POS_B = new Vec3(3.5, 1, 2.5);
    private static final String CODE = "2580";

    private static volatile long horloge;
    private static final List<JoueurDeTest> OUVERTS = new ArrayList<>();

    private FaceIdGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        quitterTous();
    }

    @BeforeBatch(batch = LOT_ATTENTE)
    public static void avantAttente(ServerLevel level) {
        horloge = System.currentTimeMillis();
        EconomyClock.setClock(() -> horloge);
    }

    @AfterBatch(batch = LOT_ATTENTE)
    public static void apresAttente(ServerLevel level) {
        EconomyClock.reset();
        quitterTous();
    }

    private static void quitterTous() {
        for (JoueurDeTest j : OUVERTS) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        OUVERTS.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer serveur;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final Set<UUID> telephones = new HashSet<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            this.serveur = helper.getLevel().getServer();
        }

        JoueurDeTest joueur(String court, Vec3 position) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            OUVERTS.add(j);
            return j;
        }

        UUID telephone(JoueurDeTest j) {
            ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(phone, PhoneConfig.capacity());
            UUID id = PhoneItem.ensurePhoneId(phone);
            telephones.add(id);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
            return id;
        }

        void donner(JoueurDeTest de, JoueurDeTest a) {
            ItemStack phone = de.joueur().getMainHandItem();
            de.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
        }

        PhoneData.PhoneRecord donnees(UUID phone) {
            return PhoneData.get(serveur).record(phone);
        }

        @Override
        public void close() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    PhoneService.close(j.joueur(), CloseReason.CLOSED);
                    j.joueur().getInventory().clearContent();
                    j.quitter();
                }
            }
            PhoneData data = PhoneData.get(serveur);
            telephones.forEach(data::erase);
        }
    }

    private interface Corps {
        void run(Essai essai);
    }

    private static void essai(GameTestHelper helper, Corps corps) {
        try (Essai essai = new Essai(helper)) {
            corps.run(essai);
        }
        helper.succeed();
    }

    /** Ouvre le téléphone de la main principale par le vrai paquet d'usage d'un objet ; rend l'OPEN reçu. */
    private static PhoneStatePacket ouvrir(GameTestHelper helper, JoueurDeTest j) {
        j.etatsTelephoneRecus();
        j.envoyerVanille(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0), ServerboundUseItemPacket::new);
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        helper.assertTrue(!recus.isEmpty() && recus.get(0).kind() == Kind.OPEN, "un OPEN attendu, vu " + recus);
        return recus.get(0);
    }

    private static int session(GameTestHelper helper, JoueurDeTest j) {
        Optional<PhoneSession> s = PhoneService.session(j.uuid());
        helper.assertTrue(s.isPresent(), "session ouverte attendue");
        return s.get().id();
    }

    private static void oublierFrein(UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("frein du téléphone introuvable", e);
        }
    }

    /** Un geste de la session ouverte, par le vrai paquet du mod, frein oublié ; le dernier état reçu (ou nul). */
    @Nullable
    private static PhoneStatePacket geste(GameTestHelper helper, JoueurDeTest j, Action action, String first,
            boolean flag) {
        int s = session(helper, j);
        j.etatsTelephoneRecus();
        oublierFrein(j.uuid());
        j.envoyer(new PhoneActionPacket(s, action, first, "", flag));
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        return recus.isEmpty() ? null : recus.get(recus.size() - 1);
    }

    private static void attendre(GameTestHelper helper, @Nullable PhoneStatePacket e, Lock lock, Result last,
            String quoi) {
        helper.assertTrue(e != null, quoi + " : aucun état reçu");
        helper.assertTrue(e.lock() == lock && e.last() == last,
                quoi + " : attendu " + lock + "/" + last + ", vu " + e.kind() + "/" + e.lock() + "/" + e.last());
    }

    /** Ferme l'écran comme le client (geste CLOSE). */
    private static void fermer(GameTestHelper helper, JoueurDeTest j) {
        int s = session(helper, j);
        j.envoyer(new PhoneActionPacket(s, Action.CLOSE, "", "", false));
        helper.assertTrue(PhoneService.session(j.uuid()).isEmpty(), "l'écran devait se fermer");
        j.etatsTelephoneRecus();
    }

    /** {@code j} pose {@link #CODE} sur un téléphone neuf, active Face ID et ferme l'écran. */
    private static void codePuisFaceId(GameTestHelper helper, JoueurDeTest j) {
        PhoneStatePacket open = ouvrir(helper, j);
        helper.assertTrue(open.lock() == Lock.SETUP, "téléphone neuf : SETUP, vu " + open.lock());
        attendre(helper, geste(helper, j, Action.SETUP, CODE, false), Lock.UNLOCKED, Result.NONE, "code");
        PhoneStatePacket on = geste(helper, j, Action.FACE_ID, CODE, true);
        attendre(helper, on, Lock.UNLOCKED, Result.FACE_ID_ON, "Face ID activé");
        helper.assertTrue(on.faceId(), "la vue du propriétaire dit Face ID actif");
        fermer(helper, j);
    }

    // ---------------------------------------------------------------------------------------------
    // Les règles
    // ---------------------------------------------------------------------------------------------

    /** Le propriétaire, visage découvert, ouvre déverrouillé, avec l'issue qui fait jouer l'animation. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void leProprietaireVisageDecouvertOuvreDeverrouille(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_ok", POS_A);
            UUID id = e.telephone(a);
            codePuisFaceId(helper, a);
            helper.assertTrue(e.donnees(id).faceId(), "Face ID gardé avec le téléphone");
            PhoneStatePacket open = ouvrir(helper, a);
            attendre(helper, open, Lock.UNLOCKED, Result.FACE_UNLOCKED, "réouverture");
            helper.assertTrue(open.owner() && open.faceId(), "vue du propriétaire : " + open);
            // La session est vraiment déverrouillée : un geste réservé au déverrouillé passe.
            attendre(helper, geste(helper, a, Action.AIRPLANE, "", true), Lock.UNLOCKED, Result.NONE, "mode avion");
            helper.assertTrue(e.donnees(id).airplane(), "le geste déverrouillé a été joué");
        });
    }

    /** Une coiffe (casque, citrouille) couvre le visage : pavé et « Visage non reconnu ». */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void visageCouvertLePaveSAffiche(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_tete", POS_A);
            e.telephone(a);
            codePuisFaceId(helper, a);
            for (ItemStack coiffe : new ItemStack[] {new ItemStack(Items.IRON_HELMET),
                    new ItemStack(Items.CARVED_PUMPKIN)}) {
                a.joueur().setItemSlot(EquipmentSlot.HEAD, coiffe);
                PhoneStatePacket open = ouvrir(helper, a);
                attendre(helper, open, Lock.LOCKED, Result.FACE_NOT_RECOGNIZED, "tête couverte (" + coiffe + ")");
                helper.assertTrue(!open.faceId() && !open.owner(), "verrouillé, rien des réglages : " + open);
                fermer(helper, a);
            }
            // Le code reste la voie ordinaire.
            a.joueur().setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE, false), Lock.UNLOCKED, Result.NONE, "par le code");
        });
    }

    /** Quelqu'un d'autre n'en profite jamais, même tout près du propriétaire, le téléphone braqué sur lui. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void unAutreNOuvrePasParLeVisage(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_prop", POS_A);
            JoueurDeTest b = e.joueur("fa_autre", POS_B);
            e.telephone(a);
            codePuisFaceId(helper, a);
            e.donner(a, b);
            // B, visage découvert, braque le téléphone sur A, visage découvert lui aussi.
            b.joueur().lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    a.joueur().getEyePosition());
            PhoneStatePacket open = ouvrir(helper, b);
            attendre(helper, open, Lock.LOCKED, Result.NONE, "ouvert par un autre");
        });
    }

    /** Déverrouillé par le code, un autre que le propriétaire ne voit pas si Face ID est actif. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void laVueDUnAutreNeDitRienDeFaceId(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_vue_a", POS_A);
            JoueurDeTest b = e.joueur("fa_vue_b", POS_B);
            e.telephone(a);
            codePuisFaceId(helper, a);
            e.donner(a, b);
            ouvrir(helper, b);
            PhoneStatePacket deverrouille = geste(helper, b, Action.UNLOCK, CODE, false);
            attendre(helper, deverrouille, Lock.UNLOCKED, Result.NONE, "B par le code");
            helper.assertTrue(!deverrouille.faceId() && !deverrouille.owner(),
                    "la vue de B ne dit rien de Face ID : " + deverrouille);
        });
    }

    /** Même avec le code, l'interrupteur reste au propriétaire : ni couper ni activer pour un autre. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void seulLeProprietaireRegleFaceId(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_reg_a", POS_A);
            JoueurDeTest b = e.joueur("fa_reg_b", POS_B);
            UUID id = e.telephone(a);
            codePuisFaceId(helper, a);
            e.donner(a, b);
            ouvrir(helper, b);
            attendre(helper, geste(helper, b, Action.UNLOCK, CODE, false), Lock.UNLOCKED, Result.NONE, "B par le code");
            attendre(helper, geste(helper, b, Action.FACE_ID, "", false), Lock.UNLOCKED, Result.NOT_OWNER,
                    "B coupe Face ID");
            helper.assertTrue(e.donnees(id).faceId(), "Face ID du propriétaire inchangé");
            attendre(helper, geste(helper, b, Action.FACE_ID, CODE, true), Lock.UNLOCKED, Result.NOT_OWNER,
                    "B active Face ID");
            helper.assertTrue(e.donnees(id).faceId(), "Face ID du propriétaire inchangé");
        });
    }

    /** Activer demande le code (un faux code compte comme ailleurs) ; couper, non. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void activerDemandeLeCodeCouperNon(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_code", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE, false), Lock.UNLOCKED, Result.NONE, "code");
            attendre(helper, geste(helper, a, Action.FACE_ID, "0000", true), Lock.UNLOCKED, Result.WRONG_CODE,
                    "faux code");
            helper.assertTrue(!e.donnees(id).faceId(), "Face ID reste coupé après un faux code");
            helper.assertTrue(e.donnees(id).failures() == 1, "le faux code compte : " + e.donnees(id).failures());
            attendre(helper, geste(helper, a, Action.FACE_ID, "", true), Lock.UNLOCKED, Result.WRONG_CODE,
                    "sans code");
            helper.assertTrue(!e.donnees(id).faceId(), "Face ID reste coupé sans code");
            PhoneStatePacket on = geste(helper, a, Action.FACE_ID, CODE, true);
            attendre(helper, on, Lock.UNLOCKED, Result.FACE_ID_ON, "bon code");
            helper.assertTrue(on.faceId() && e.donnees(id).faceId() && e.donnees(id).failures() == 0,
                    "activé, essais remis à zéro");
            PhoneStatePacket off = geste(helper, a, Action.FACE_ID, "", false);
            attendre(helper, off, Lock.UNLOCKED, Result.FACE_ID_OFF, "couper sans code");
            helper.assertTrue(!off.faceId() && !e.donnees(id).faceId(), "coupé");
            fermer(helper, a);
            attendre(helper, ouvrir(helper, a), Lock.LOCKED, Result.NONE, "coupé : réouverture verrouillée");
        });
    }

    /** « Verrouiller » puis l'écran rallumé (sans nouvelle ouverture) : verrouillé, Face ID ne joue pas. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void verrouillerNeRouvrePasParLeVisage(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_lock", POS_A);
            e.telephone(a);
            codePuisFaceId(helper, a);
            attendre(helper, ouvrir(helper, a), Lock.UNLOCKED, Result.FACE_UNLOCKED, "ouverture");
            attendre(helper, geste(helper, a, Action.LOCK, "", false), Lock.LOCKED, Result.NONE, "Verrouiller");
            // Ce que la page reçoit ensuite (batterie, réseau) ne déverrouille pas non plus.
            attendre(helper, geste(helper, a, Action.WALLPAPER, "nuit", false), Lock.LOCKED, Result.NONE,
                    "un geste refusé, verrouillé");
            helper.assertTrue(PhoneService.session(a.uuid()).map(PhoneSession::lock).orElse(null) == Lock.LOCKED,
                    "la session reste verrouillée");
        });
    }

    /** Effacer le téléphone, puis un nouveau propriétaire : Face ID est oublié. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void effacementEtNouveauProprietaireOublient(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_eff", POS_A);
            JoueurDeTest b = e.joueur("fa_neuf", POS_B);
            UUID id = e.telephone(a);
            codePuisFaceId(helper, a);
            attendre(helper, ouvrir(helper, a), Lock.UNLOCKED, Result.FACE_UNLOCKED, "ouverture");
            a.etatsTelephoneRecus();
            oublierFrein(a.uuid());
            a.envoyer(new PhoneActionPacket(session(helper, a), Action.ERASE, CODE, "", false));
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "l'effacement ferme l'écran");
            helper.assertTrue(!e.donnees(id).faceId() && e.donnees(id).owner() == null, "tout est oublié");
            e.donner(a, b);
            PhoneStatePacket neuf = ouvrir(helper, b);
            helper.assertTrue(neuf.lock() == Lock.SETUP && neuf.last() == Result.NONE, "neuf pour B : " + neuf);
            PhoneStatePacket pose = geste(helper, b, Action.SETUP, "1111", false);
            attendre(helper, pose, Lock.UNLOCKED, Result.NONE, "code de B");
            helper.assertTrue(!pose.faceId() && !e.donnees(id).faceId(), "le nouveau propriétaire n'a pas Face ID");
            fermer(helper, b);
            attendre(helper, ouvrir(helper, b), Lock.LOCKED, Result.NONE, "B rouvre : verrouillé");
        });
    }

    /** Pendant l'attente après trop de codes faux, Face ID ne déverrouille pas ; après l'attente, si. */
    @GameTest(template = "test/plancher", batch = LOT_ATTENTE)
    public static void pasDeFaceIdPendantLAttente(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("fa_att", POS_A);
            e.telephone(a);
            codePuisFaceId(helper, a);
            ouvrir(helper, a); // déverrouillé par le visage
            attendre(helper, geste(helper, a, Action.LOCK, "", false), Lock.LOCKED, Result.NONE, "Verrouiller");
            PhoneStatePacket r = null;
            for (int i = 0; i < PhoneConfig.maxAttempts(); i++) {
                r = geste(helper, a, Action.UNLOCK, "0000", false);
            }
            attendre(helper, r, Lock.LOCKED, Result.LOCKED_OUT, "codes faux");
            fermer(helper, a);
            PhoneStatePacket open = ouvrir(helper, a);
            attendre(helper, open, Lock.LOCKED, Result.NONE, "pendant l'attente");
            helper.assertTrue(open.lockoutSeconds() > 0, "l'attente court : " + open.lockoutSeconds());
            fermer(helper, a);
            horloge += PhoneConfig.lockoutSeconds() * 1000L + 1000L;
            attendre(helper, ouvrir(helper, a), Lock.UNLOCKED, Result.FACE_UNLOCKED, "après l'attente");
        });
    }
}
