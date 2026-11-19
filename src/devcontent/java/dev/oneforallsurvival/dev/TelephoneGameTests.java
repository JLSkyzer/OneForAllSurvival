package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneService.Outcome;
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
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le téléphone côté serveur (lot T1, tâche 5) : la session ouverte, le code choisi puis demandé, le propriétaire seul
 * maître du code et de l'effacement, une vue verrouillée qui ne dit rien, les gestes d'une autre session ou sans
 * session ignorés, le blocage après cinq faux codes (une minute, partagé par les copies, gardé à la fermeture).
 *
 * <p>Chaque essai se déroule dans un seul tic : les gestes s'enchaînent en oubliant le frein entre deux
 * ({@code PhoneService.clearThrottle}, de visibilité paquet, appelé par réflexion), sauf l'essai du frein. Les états reçus sont décodés des octets réellement
 * envoyés ({@link JoueurDeTest#etatsTelephoneRecus}). Le lot {@value #LOT_HORLOGE} fige l'horloge de l'économie
 * ({@link EconomyClock}) et la rend après.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TelephoneGameTests {

    private static final String LOT_A = "telephone_a";
    private static final String LOT_HORLOGE = "telephone_horloge";
    private static final String LOT_RESET = "telephone_reset";

    private static final Vec3 POS_A = new Vec3(2.5, 1, 2.5);
    private static final Vec3 POS_B = new Vec3(4.5, 1, 2.5);
    private static final Vec3 POS_C = new Vec3(2.5, 1, 4.5);
    private static final Vec3 POS_D = new Vec3(4.5, 1, 4.5);
    private static final String CODE = "4821";

    /** L'heure lue par l'économie pendant le lot {@value #LOT_HORLOGE}. */
    private static volatile long horloge;

    private TelephoneGameTests() {}

    @BeforeBatch(batch = LOT_HORLOGE)
    public static void avantHorloge(ServerLevel level) {
        horloge = System.currentTimeMillis();
        EconomyClock.setClock(() -> horloge);
    }

    @AfterBatch(batch = LOT_HORLOGE)
    public static void apresHorloge(ServerLevel level) {
        EconomyClock.reset();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Ce qu'un essai a ouvert : joueurs déconnectés et téléphones effacés à la fin, même en cas d'échec. */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final MinecraftServer serveur;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final Set<UUID> telephones = new HashSet<>();
        private final List<JoueurDeTest> operateurs = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            this.serveur = helper.getLevel().getServer();
        }

        /** Un joueur neuf, au nom propre à cet essai (16 caractères au plus). */
        JoueurDeTest joueur(String court, Vec3 position) {
            String suffixe = Long.toString(System.nanoTime(), 36);
            String nom = court + "_" + suffixe.substring(Math.max(0, suffixe.length() - (15 - court.length())));
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(position));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            return j;
        }

        /** Un joueur de l'équipe (opérateur de niveau 4, retiré à la fin). */
        JoueurDeTest equipe(String court, Vec3 position) {
            JoueurDeTest j = joueur(court, position);
            serveur.getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            operateurs.add(j);
            return j;
        }

        /** Un téléphone chargé dans la main principale de {@code j} ; son identifiant. */
        UUID telephone(JoueurDeTest j) {
            ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(phone, PhoneConfig.capacity());
            UUID id = PhoneItem.ensurePhoneId(phone);
            telephones.add(id);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
            return id;
        }

        /** Passe le téléphone tenu par {@code de} dans la main de {@code a}. */
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
            for (JoueurDeTest j : operateurs) {
                serveur.getPlayerList().getOps().remove(j.joueur().getGameProfile());
            }
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

    /** Ouvre le téléphone tenu en main principale et rend le seul {@code OPEN} reçu. */
    private static PhoneStatePacket ouvrir(GameTestHelper helper, JoueurDeTest j) {
        j.etatsTelephoneRecus();
        Outcome r = PhoneService.open(j.joueur(), InteractionHand.MAIN_HAND);
        helper.assertTrue(r == Outcome.OPENED, "ouverture : " + r);
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.OPEN, "un OPEN attendu, vu " + recus);
        Optional<PhoneSession> session = PhoneService.session(j.uuid());
        helper.assertTrue(session.isPresent() && session.get().id() == recus.get(0).session(),
                "session ouverte du même numéro que l'OPEN");
        return recus.get(0);
    }

    private static int session(GameTestHelper helper, JoueurDeTest j) {
        Optional<PhoneSession> s = PhoneService.session(j.uuid());
        helper.assertTrue(s.isPresent(), "session ouverte attendue");
        return s.get().id();
    }

    /** Oublie le frein du joueur ({@code PhoneService.clearThrottle}, de visibilité paquet) par réflexion. */
    private static void oublierFrein(UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("frein du téléphone introuvable", e);
        }
    }

    /** Un geste de la session ouverte, frein oublié ; rend l'issue et le dernier état reçu (ou nul). */
    private record Reponse(Outcome outcome, @Nullable PhoneStatePacket etat) {}

    private static Reponse geste(GameTestHelper helper, JoueurDeTest j, Action action, String first, String second,
            boolean flag) {
        return geste(j, new PhoneActionPacket(session(helper, j), action, first, second, flag), true);
    }

    private static Reponse geste(GameTestHelper helper, JoueurDeTest j, Action action, String first) {
        return geste(helper, j, action, first, "", false);
    }

    private static Reponse geste(JoueurDeTest j, PhoneActionPacket packet, boolean oublierFrein) {
        j.etatsTelephoneRecus();
        if (oublierFrein) {
            oublierFrein(j.uuid());
        }
        Outcome r = PhoneService.act(j.joueur(), packet);
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        return new Reponse(r, recus.isEmpty() ? null : recus.get(recus.size() - 1));
    }

    private static void attendre(GameTestHelper helper, Reponse r, Kind kind, Lock lock, Result last, String quoi) {
        helper.assertTrue(r.etat() != null, quoi + " : aucun état reçu (issue " + r.outcome() + ")");
        PhoneStatePacket e = r.etat();
        helper.assertTrue(e.kind() == kind && e.lock() == lock && e.last() == last,
                quoi + " : attendu " + kind + "/" + lock + "/" + last + ", vu " + e.kind() + "/" + e.lock() + "/"
                        + e.last());
    }

    /** Choisit {@link #CODE} sur un téléphone neuf ouvert, puis le verrouille. */
    private static void choisirPuisVerrouiller(GameTestHelper helper, JoueurDeTest j) {
        attendre(helper, geste(helper, j, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
        attendre(helper, geste(helper, j, Action.LOCK, ""), Kind.UPDATE, Lock.LOCKED, Result.NONE, "LOCK");
    }

    /** Une valeur de ce NBT vaut-elle le code (texte ou nombre égal) ? Un sel qui le contient par hasard ne compte pas. */
    private static boolean porteLeCode(Tag tag) {
        if (tag instanceof StringTag s) {
            return s.getAsString().equals(CODE);
        }
        if (tag instanceof NumericTag n) {
            return n.getAsLong() == Long.parseLong(CODE);
        }
        if (tag instanceof CompoundTag c) {
            return c.getAllKeys().stream().anyMatch(k -> porteLeCode(c.get(k)));
        }
        if (tag instanceof CollectionTag<?> l) {
            return l.stream().anyMatch(t -> porteLeCode((Tag) t));
        }
        return false;
    }

    private static Reponse cinqFaux(GameTestHelper helper, JoueurDeTest j) {
        Reponse r = null;
        for (int i = 1; i <= 5; i++) {
            r = geste(helper, j, Action.UNLOCK, "0000");
            if (i < 5) {
                attendre(helper, r, Kind.UPDATE, Lock.LOCKED, Result.WRONG_CODE, "faux code " + i);
                helper.assertTrue(r.etat().attemptsLeft() == 5 - i,
                        "essais restants après " + i + " : " + r.etat().attemptsLeft());
            }
        }
        attendre(helper, r, Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT, "cinquième faux code");
        return r;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : code, propriétaire, sessions
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void premiereOuvertureDemandeUnCode(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_a", POS_A);
            UUID id = e.telephone(a);
            PhoneStatePacket open = ouvrir(helper, a);
            helper.assertTrue(open.lock() == Lock.SETUP, "téléphone neuf : SETUP, vu " + open.lock());
            helper.assertTrue(open.ownerName().isEmpty() && !open.owner(), "pas de propriétaire : " + open);
            helper.assertTrue(open.battery() == 100, "batterie pleine : " + open.battery());
            helper.assertTrue(open.attemptsLeft() == PhoneConfig.maxAttempts(), "essais : " + open.attemptsLeft());
            helper.assertTrue(e.donnees(id).owner() == null, "ouvrir n'enregistre aucun propriétaire");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leCodeChoisiVerrouilleLaReouverture(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_b", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            Reponse r = geste(helper, a, Action.SETUP, CODE);
            attendre(helper, r, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            helper.assertTrue(r.outcome() == Outcome.DONE, "SETUP fait : " + r.outcome());
            helper.assertTrue(r.etat().owner() && r.etat().ownerName().equals(a.joueur().getGameProfile().getName()),
                    "propriétaire montré : " + r.etat());
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(a.uuid().equals(rec.owner()) && rec.salt() != null && rec.codeHash() != null,
                    "propriétaire, sel et empreinte posés");
            // Le code n'est écrit ni dans la pile ni dans la sauvegarde.
            helper.assertTrue(!porteLeCode(PhoneData.get(e.serveur).save(new CompoundTag()))
                    && !porteLeCode(a.joueur().getMainHandItem().getOrCreateTag()),
                    "le code en clair est écrit quelque part");

            Reponse ferme = geste(helper, a, Action.CLOSE, "");
            helper.assertTrue(ferme.etat() == null && PhoneService.session(a.uuid()).isEmpty(), "fermé sans réponse");
            PhoneStatePacket vue = ouvrir(helper, a);
            helper.assertTrue(vue.lock() == Lock.LOCKED, "réouverture verrouillée : " + vue.lock());
            helper.assertTrue(vue.ownerName().isEmpty() && !vue.owner(),
                    "la vue verrouillée ne dit rien du propriétaire, même à lui : " + vue);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void mauvaisCodePuisBonCode(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_c", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            Reponse faux = geste(helper, a, Action.UNLOCK, "0000");
            attendre(helper, faux, Kind.UPDATE, Lock.LOCKED, Result.WRONG_CODE, "faux code");
            helper.assertTrue(faux.etat().attemptsLeft() == 4, "4 essais restants, vu " + faux.etat().attemptsLeft());
            helper.assertTrue(faux.etat().ownerName().isEmpty(), "vue verrouillée sans nom");
            helper.assertTrue(e.donnees(id).failures() == 1, "un échec compté : " + e.donnees(id).failures());
            Reponse bon = geste(helper, a, Action.UNLOCK, CODE);
            attendre(helper, bon, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "bon code");
            helper.assertTrue(e.donnees(id).failures() == 0, "échecs remis à zéro");
            helper.assertTrue(PhoneService.session(a.uuid()).get().lock() == Lock.UNLOCKED, "session déverrouillée");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unAutreJoueurNeVoitRienAvantLeCode(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_d", POS_A);
            JoueurDeTest b = e.joueur("tel_e", POS_B);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            attendre(helper, geste(helper, a, Action.WALLPAPER, "nuit"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "fond");
            Reponse avion = geste(helper, a, Action.AIRPLANE, "", "", true);
            helper.assertTrue(avion.etat() != null && avion.etat().airplane(), "mode avion montré au propriétaire");
            e.donner(a, b);

            PhoneStatePacket vue = ouvrir(helper, b);
            helper.assertTrue(vue.lock() == Lock.LOCKED, "verrouillé pour B : " + vue.lock());
            helper.assertTrue(vue.ownerName().isEmpty() && !vue.owner() && !vue.airplane(),
                    "la vue verrouillée ne dit rien : " + vue);
            helper.assertTrue("nuit".equals(vue.wallpaper()), "le fond reste visible : " + vue.wallpaper());

            Reponse fond = geste(helper, b, Action.WALLPAPER, "lagon");
            helper.assertTrue(fond.outcome() == Outcome.REFUSED, "fond de B refusé : " + fond.outcome());
            Reponse coupe = geste(helper, b, Action.AIRPLANE, "", "", false);
            helper.assertTrue(coupe.outcome() == Outcome.REFUSED, "avion de B refusé : " + coupe.outcome());
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue("nuit".equals(rec.wallpaper()) && rec.airplane(),
                    "PhoneData inchangé : " + rec.wallpaper() + " / " + rec.airplane());
            helper.assertTrue(coupe.etat() != null && coupe.etat().ownerName().isEmpty() && !coupe.etat().airplane(),
                    "la réponse au refus ne dit rien non plus");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void seulLeProprietaireChangeLeCode(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_f", POS_A);
            JoueurDeTest b = e.joueur("tel_g", POS_B);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            String empreinte = e.donnees(id).codeHash();
            e.donner(a, b);

            ouvrir(helper, b);
            Reponse ouvert = geste(helper, b, Action.UNLOCK, CODE);
            attendre(helper, ouvert, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "B déverrouille");
            helper.assertTrue(!ouvert.etat().owner(), "B n'est pas le propriétaire");
            Reponse change = geste(helper, b, Action.CHANGE_CODE, CODE, "1111", false);
            attendre(helper, change, Kind.UPDATE, Lock.UNLOCKED, Result.NOT_OWNER, "CHANGE_CODE de B");
            Reponse efface = geste(helper, b, Action.ERASE, CODE);
            attendre(helper, efface, Kind.UPDATE, Lock.UNLOCKED, Result.NOT_OWNER, "ERASE de B");
            helper.assertTrue(empreinte.equals(e.donnees(id).codeHash()) && a.uuid().equals(e.donnees(id).owner()),
                    "code et propriétaire inchangés");
            e.donner(b, a);

            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "A déverrouille");
            Reponse fauxAncien = geste(helper, a, Action.CHANGE_CODE, "0000", "1357", false);
            attendre(helper, fauxAncien, Kind.UPDATE, Lock.UNLOCKED, Result.WRONG_CODE, "ancien code faux");
            helper.assertTrue(e.donnees(id).failures() == 1 && fauxAncien.etat().attemptsLeft() == 4,
                    "l'ancien code faux compte un essai : " + e.donnees(id).failures());
            Reponse invalide = geste(helper, a, Action.CHANGE_CODE, CODE, "12a4", false);
            attendre(helper, invalide, Kind.UPDATE, Lock.UNLOCKED, Result.INVALID_CODE, "nouveau code invalide");
            Reponse bon = geste(helper, a, Action.CHANGE_CODE, CODE, "1357", false);
            attendre(helper, bon, Kind.UPDATE, Lock.UNLOCKED, Result.CODE_CHANGED, "CHANGE_CODE de A");
            attendre(helper, geste(helper, a, Action.LOCK, ""), Kind.UPDATE, Lock.LOCKED, Result.NONE, "LOCK");
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.LOCKED, Result.WRONG_CODE,
                    "l'ancien code ne déverrouille plus");
            attendre(helper, geste(helper, a, Action.UNLOCK, "1357"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "le nouveau code déverrouille");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void uneAutreSessionNeComptePas(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_h", POS_A);
            e.telephone(a);
            int ancienne = ouvrir(helper, a).session();
            choisirPuisVerrouiller(helper, a);
            PhoneStatePacket reouvert = ouvrir(helper, a);
            int courante = reouvert.session();
            helper.assertTrue(courante > ancienne, "numéro croissant : " + ancienne + " puis " + courante);
            helper.assertTrue(reouvert.lock() == Lock.LOCKED, "réouverture verrouillée");

            // Par le réseau, comme un client : le numéro périmé ne déverrouille rien et ne reçoit rien.
            oublierFrein(a.uuid());
            a.etatsTelephoneRecus();
            a.envoyer(new PhoneActionPacket(ancienne, Action.UNLOCK, CODE, "", false));
            helper.assertTrue(a.etatsTelephoneRecus().isEmpty(), "rien n'est renvoyé à une session périmée");
            helper.assertTrue(PhoneService.session(a.uuid()).get().lock() == Lock.LOCKED, "toujours verrouillé");
            Reponse direct = geste(a, new PhoneActionPacket(ancienne, Action.UNLOCK, CODE, "", false), true);
            helper.assertTrue(direct.outcome() == Outcome.NO_SESSION && direct.etat() == null,
                    "session périmée : " + direct.outcome());

            // Témoin : le même geste, du bon numéro et par le réseau, déverrouille.
            oublierFrein(a.uuid());
            a.envoyer(new PhoneActionPacket(courante, Action.UNLOCK, CODE, "", false));
            List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
            helper.assertTrue(recus.size() == 1 && recus.get(0).lock() == Lock.UNLOCKED,
                    "témoin : déverrouillé par le réseau, vu " + recus);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansSessionRienNeBouge(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_i", POS_A);
            UUID id = e.telephone(a);
            for (Action action : Action.values()) {
                Reponse r = geste(a, new PhoneActionPacket(1, action, CODE, "1357", true), true);
                helper.assertTrue(r.outcome() == Outcome.NO_SESSION && r.etat() == null,
                        action + " sans session : " + r.outcome() + " / " + r.etat());
            }
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "aucune session créée");
            helper.assertTrue(e.donnees(id).owner() == null && e.donnees(id).codeHash() == null,
                    "aucun propriétaire ni code posé");

            // Un téléphone ouvert puis passé dans une autre main ne reçoit plus aucun geste.
            ouvrir(helper, a);
            int s = session(helper, a);
            ItemStack phone = a.joueur().getMainHandItem();
            a.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            a.joueur().getInventory().setItem(8, phone);
            a.joueur().getInventory().selected = 0;
            Reponse r = geste(a, new PhoneActionPacket(s, Action.SETUP, CODE, "", false), true);
            helper.assertTrue(r.outcome() == Outcome.NO_SESSION && r.etat() == null,
                    "téléphone plus en main : " + r.outcome());
            helper.assertTrue(e.donnees(id).codeHash() == null, "aucun code posé sans téléphone en main");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void effacerRendLeTelephoneNeuf(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_j", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            attendre(helper, geste(helper, a, Action.ERASE, "0000"), Kind.UPDATE, Lock.UNLOCKED, Result.WRONG_CODE,
                    "effacer avec un faux code");
            helper.assertTrue(e.donnees(id).owner() != null, "rien d'effacé avec un faux code");
            Reponse r = geste(helper, a, Action.ERASE, CODE);
            helper.assertTrue(r.etat() != null && r.etat().kind() == Kind.CLOSE
                    && r.etat().reason() == CloseReason.ERASED, "CLOSE raison ERASED, vu " + r.etat());
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "session fermée");
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(rec.owner() == null && rec.codeHash() == null, "données effacées");
            helper.assertTrue(PhoneItem.phoneId(a.joueur().getMainHandItem()).equals(Optional.of(id)),
                    "la pile garde son identifiant");
            helper.assertTrue(ouvrir(helper, a).lock() == Lock.SETUP, "réouverture : SETUP");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot horloge : le blocage
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void cinqEssaisBloquentUneMinute(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_k", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            long depart = horloge;
            Reponse bloque = cinqFaux(helper, a);
            helper.assertTrue(bloque.etat().lockoutSeconds() == 60, "blocage de 60 s : " + bloque.etat().lockoutSeconds());
            helper.assertTrue(e.donnees(id).lockedUntil() == depart + 60_000L,
                    "blocage sauvegardé : " + (e.donnees(id).lockedUntil() - depart));

            horloge = depart + 30_000L;
            Reponse tot = geste(helper, a, Action.UNLOCK, CODE);
            attendre(helper, tot, Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT, "bon code à +30 s");
            helper.assertTrue(tot.etat().lockoutSeconds() == 30, "30 s restantes : " + tot.etat().lockoutSeconds());

            horloge = depart + 61_000L;
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "bon code à +61 s");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void deuxCopiesPartagentLeBlocage(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_l", POS_A);
            JoueurDeTest b = e.joueur("tel_m", POS_B);
            e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            // Une copie créative : même phone_id, deux piles.
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            cinqFaux(helper, a);

            PhoneStatePacket vue = ouvrir(helper, b);
            helper.assertTrue(vue.lock() == Lock.LOCKED && vue.lockoutSeconds() > 0,
                    "la copie s'ouvre bloquée : " + vue);
            attendre(helper, geste(helper, b, Action.UNLOCK, CODE), Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT,
                    "bon code sur la copie");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void leBlocageSurvitALaFermeture(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_n", POS_A);
            e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            cinqFaux(helper, a);
            Reponse ferme = geste(helper, a, Action.CLOSE, "");
            helper.assertTrue(ferme.etat() == null && PhoneService.session(a.uuid()).isEmpty(),
                    "CLOSE : fin de session, rien d'autre");

            PhoneStatePacket vue = ouvrir(helper, a);
            helper.assertTrue(vue.lock() == Lock.LOCKED && vue.lockoutSeconds() == 60, "rouvert bloqué : " + vue);
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT,
                    "premier essai après réouverture");
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void leFreinIgnoreUnGesteTropProche(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_o", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            int s = session(helper, a);
            Reponse premier = geste(a, new PhoneActionPacket(s, Action.UNLOCK, "0000", "", false), true);
            helper.assertTrue(premier.etat() != null && premier.etat().last() == Result.WRONG_CODE, "premier geste");
            // Même tic, sans oublier le frein : ignoré, rien n'est renvoyé ni compté.
            Reponse second = geste(a, new PhoneActionPacket(s, Action.UNLOCK, "0000", "", false), false);
            helper.assertTrue(second.outcome() == Outcome.THROTTLED && second.etat() == null,
                    "second geste freiné : " + second.outcome());
            helper.assertTrue(e.donnees(id).failures() == 1, "un seul échec compté : " + e.donnees(id).failures());
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot horloge (suite) : le blocage par les gestes du propriétaire
    // ---------------------------------------------------------------------------------------------

    /** Cinq faux codes donnés à {@code action} (ERASE ou CHANGE_CODE) depuis une session déverrouillée. */
    private static void cinqFauxDepuisDeverrouille(GameTestHelper helper, Essai e, JoueurDeTest a, UUID id,
            Action action) {
        ouvrir(helper, a);
        attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
        String empreinte = e.donnees(id).codeHash();
        long depart = horloge;
        Reponse r = null;
        for (int i = 1; i <= 5; i++) {
            r = geste(helper, a, action, "0000", "1357", false);
            if (i < 5) {
                attendre(helper, r, Kind.UPDATE, Lock.UNLOCKED, Result.WRONG_CODE, action + " faux " + i);
                helper.assertTrue(r.etat().attemptsLeft() == 5 - i, "essais restants : " + r.etat().attemptsLeft());
            }
        }
        attendre(helper, r, Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT, "cinquième " + action + " faux");
        helper.assertTrue(r.etat().lockoutSeconds() == 60, "blocage de 60 s : " + r.etat().lockoutSeconds());
        helper.assertTrue(PhoneService.session(a.uuid()).get().lock() == Lock.LOCKED, "session reverrouillée");
        PhoneData.PhoneRecord rec = e.donnees(id);
        helper.assertTrue(rec.lockedUntil() == depart + 60_000L, "blocage sauvegardé : " + (rec.lockedUntil() - depart));
        helper.assertTrue(a.uuid().equals(rec.owner()) && empreinte.equals(rec.codeHash()),
                "ni effacé ni changé : propriétaire et empreinte intacts");
        attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT,
                "bon code pendant le blocage");
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void cinqFauxEffacementsBloquent(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_p", POS_A);
            cinqFauxDepuisDeverrouille(helper, e, a, e.telephone(a), Action.ERASE);
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_HORLOGE)
    public static void cinqFauxChangementsDeCodeBloquent(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_q", POS_A);
            cinqFauxDepuisDeverrouille(helper, e, a, e.telephone(a), Action.CHANGE_CODE);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot reset : une session ne survit pas au code qui l'a ouverte
    // ---------------------------------------------------------------------------------------------

    /**
     * Le scénario de la revue, par la commande : deux copies, A déverrouillé, l'équipe fait {@code /phone reset} sur sa
     * copie, C pose son code. La session de A est fermée par le reset ({@code ERASED}) ; ses gestes suivants ne font rien
     * et il ne voit rien de C.
     */
    @GameTest(template = "test/plancher", batch = LOT_RESET)
    public static void leResetDeLEquipeFermeLesSessions(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_r", POS_A);
            JoueurDeTest t = e.equipe("tel_s", POS_B);
            JoueurDeTest c = e.joueur("tel_t", POS_C);
            UUID id = e.telephone(a);
            t.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            int sessionA = session(helper, a);

            a.etatsTelephoneRecus();
            int fait = e.serveur.getCommands().performPrefixedCommand(t.joueur().createCommandSourceStack(),
                    "phone reset");
            helper.assertTrue(fait == 1 && e.donnees(id).owner() == null, "reset fait par l'équipe : " + fait);
            List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
            helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.CLOSE
                    && recus.get(0).reason() == CloseReason.ERASED, "A reçoit CLOSE raison ERASED, vu " + recus);
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "session de A fermée");

            e.donner(t, c);
            ouvrir(helper, c);
            attendre(helper, geste(helper, c, Action.SETUP, "9999"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "C pose son code");
            String fond = e.donnees(id).wallpaper();
            for (PhoneActionPacket p : List.of(new PhoneActionPacket(sessionA, Action.WALLPAPER, "nuit", "", false),
                    new PhoneActionPacket(sessionA, Action.AIRPLANE, "", "", true))) {
                Reponse r = geste(a, p, true);
                helper.assertTrue(r.outcome() == Outcome.NO_SESSION && r.etat() == null,
                        p.action() + " de A après le reset : " + r.outcome() + " / " + r.etat());
            }
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(c.uuid().equals(rec.owner()) && fond.equals(rec.wallpaper()) && !rec.airplane(),
                    "le téléphone de C est inchangé : " + rec.wallpaper() + " / " + rec.airplane());
        });
    }

    /**
     * La même faille sans la commande ({@code PhoneData} effacé directement, comme toute écriture qui ne fermerait pas
     * les sessions) : la session restée ouverte de A n'est plus déverrouillée par le code de C, reconnu à son sel.
     */
    @GameTest(template = "test/plancher", batch = LOT_RESET)
    public static void unNouveauCodeReverrouilleUneSessionRestee(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_u", POS_A);
            JoueurDeTest c = e.joueur("tel_v", POS_C);
            UUID id = e.telephone(a);
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");

            PhoneData.get(e.serveur).erase(id);
            ouvrir(helper, c);
            attendre(helper, geste(helper, c, Action.SETUP, "9999"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "C pose son code");
            String fond = e.donnees(id).wallpaper();

            Reponse r = geste(helper, a, Action.WALLPAPER, "nuit");
            helper.assertTrue(r.outcome() == Outcome.REFUSED, "fond de A refusé : " + r.outcome());
            attendre(helper, r, Kind.UPDATE, Lock.LOCKED, Result.NONE, "A repasse verrouillé");
            helper.assertTrue(r.etat().ownerName().isEmpty() && !r.etat().owner() && !r.etat().airplane(),
                    "A ne voit rien de C : " + r.etat());
            Reponse avion = geste(helper, a, Action.AIRPLANE, "", "", true);
            helper.assertTrue(avion.outcome() == Outcome.REFUSED, "avion de A refusé : " + avion.outcome());
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(c.uuid().equals(rec.owner()) && fond.equals(rec.wallpaper()) && !rec.airplane(),
                    "le téléphone de C est inchangé : " + rec.wallpaper() + " / " + rec.airplane());
            // Témoin : la session de C, déverrouillée par le code en cours, règle bien le téléphone.
            attendre(helper, geste(helper, c, Action.WALLPAPER, "nuit"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "témoin : fond de C");
        });
    }

    /** Un essai d'ouverture refusé pour batterie vide ferme la session précédente du joueur. */
    @GameTest(template = "test/plancher", batch = LOT_RESET)
    public static void uneOuvertureRefuseeFermeLaPrecedente(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_w", POS_D);
            e.telephone(a);
            int avant = ouvrir(helper, a).session();
            ItemStack vide = new ItemStack(PhoneRegistry.PHONE.get());
            e.telephones.add(PhoneItem.ensurePhoneId(vide));
            a.joueur().setItemInHand(InteractionHand.OFF_HAND, vide);
            a.messagesRecus();
            Outcome r = PhoneService.open(a.joueur(), InteractionHand.OFF_HAND);
            helper.assertTrue(r == Outcome.EMPTY_BATTERY, "batterie vide : " + r);
            List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
            helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.CLOSE
                    && recus.get(0).session() == avant, "CLOSE de la session précédente, vu " + recus);
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "plus aucune session");
            helper.assertTrue(a.messagesRecus().contains(PhoneService.MESSAGE + "empty"), "message de batterie vide");
        });
    }
}
