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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le téléphone côté serveur (lot T1, tâche 5) : la session ouverte, le code choisi puis demandé, le propriétaire seul
 * maître du code et de l'effacement, une vue verrouillée qui ne dit rien, les gestes d'une autre session ou sans
 * session ignorés, le blocage après cinq faux codes (une minute, partagé par les copies, gardé à la fermeture), puis,
 * au lot T3, deux fois plus long à chaque série jusqu'au prochain bon code (niveau sauvegardé avec le téléphone).
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
    private static final String LOT_B = "telephone_b";
    private static final String LOT_C = "telephone_c";
    private static final String LOT_ASSISTANT = "telephone_assistant";
    private static final String LOT_BLOCAGE = "telephone_blocage";

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

    @BeforeBatch(batch = LOT_BLOCAGE)
    public static void avantBlocage(ServerLevel level) {
        horloge = System.currentTimeMillis();
        EconomyClock.setClock(() -> horloge);
    }

    @AfterBatch(batch = LOT_BLOCAGE)
    public static void apresBlocage(ServerLevel level) {
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
        private final List<Runnable> aRendre = new ArrayList<>();

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

        /** Une règle du monde changée par l'essai : rendue à la fin. */
        void regleBooleenne(GameRules.Key<GameRules.BooleanValue> regle, boolean valeur) {
            GameRules.BooleanValue r = serveur.getGameRules().getRule(regle);
            boolean avant = r.get();
            r.set(valeur, serveur);
            aRendre.add(() -> r.set(avant, serveur));
        }

        @Override
        public void close() {
            aRendre.forEach(Runnable::run);
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
    // Lot blocage (lot T3) : chaque série ratée double la durée, le niveau est sauvegardé avec le téléphone
    // ---------------------------------------------------------------------------------------------

    /** Cinq faux codes à l'heure courante : le blocage dure {@code secondes}, sauvegardé au niveau {@code niveau}. */
    private static void serieBloque(GameTestHelper helper, Essai e, JoueurDeTest j, UUID id, int secondes,
            int niveau) {
        long depart = horloge;
        Reponse r = cinqFaux(helper, j);
        helper.assertTrue(r.etat().lockoutSeconds() == secondes,
                "série " + niveau + " : blocage de " + secondes + " s, vu " + r.etat().lockoutSeconds());
        PhoneData.PhoneRecord rec = e.donnees(id);
        helper.assertTrue(rec.lockedUntil() == depart + secondes * 1000L,
                "série " + niveau + " : fin sauvegardée à +" + (rec.lockedUntil() - depart) + " ms");
        helper.assertTrue(rec.lockoutLevel() == niveau,
                "série " + niveau + " : niveau sauvegardé " + rec.lockoutLevel());
    }

    /**
     * Les durées de série en série sur un vrai téléphone : 60, 120 puis 240 s. Ni le temps (une semaine), ni la
     * départ du porteur, ni un autre porteur ne baissent le niveau ; le bon code, lui, ramène à 60 s, y compris
     * quand il ne change rien d'autre que le niveau (blocage déjà expiré, aucun échec en cours).
     */
    @GameTest(template = "test/plancher", batch = LOT_BLOCAGE)
    public static void chaqueSerieRateeDoubleLeBlocage(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_1", POS_A);
            JoueurDeTest b = e.joueur("tel_2", POS_B);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            long depart = horloge;
            serieBloque(helper, e, a, id, 60, 1);

            horloge = depart + 60_000L;
            serieBloque(helper, e, a, id, 120, 2);
            horloge = depart + 60_000L + 119_000L;
            Reponse tot = geste(helper, a, Action.UNLOCK, CODE);
            attendre(helper, tot, Kind.UPDATE, Lock.LOCKED, Result.LOCKED_OUT, "bon code à +119 s de la 2e série");
            helper.assertTrue(tot.etat().lockoutSeconds() == 1, "1 s restante : " + tot.etat().lockoutSeconds());

            // Une semaine plus tard, le porteur est parti et un autre tient le téléphone : le niveau n'a pas bougé.
            horloge = depart + 604_800_000L;
            ItemStack phone = a.joueur().getMainHandItem().copy();
            a.quitter();
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
            ouvrir(helper, b);
            serieBloque(helper, e, b, id, 240, 3);

            horloge += 240_000L;
            attendre(helper, geste(helper, b, Action.UNLOCK, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "bon code après le blocage");
            helper.assertTrue(e.donnees(id).lockoutLevel() == 0,
                    "bon code : niveau 0, vu " + e.donnees(id).lockoutLevel());
            attendre(helper, geste(helper, b, Action.LOCK, ""), Kind.UPDATE, Lock.LOCKED, Result.NONE, "LOCK");
            serieBloque(helper, e, b, id, 60, 1);
        });
    }

    /**
     * Comme un redémarrage : après deux séries, {@code PhoneData} est écrit, relu, et ce qui a été relu remplace le
     * téléphone en mémoire. Le niveau revient, et la troisième série bloque 240 s.
     */
    @GameTest(template = "test/plancher", batch = LOT_BLOCAGE)
    public static void leNiveauSurvitAUneEcritureRelue(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_3", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            long depart = horloge;
            cinqFaux(helper, a);
            horloge = depart + 60_000L;
            cinqFaux(helper, a);

            PhoneData vivant = PhoneData.get(e.serveur);
            PhoneData.PhoneRecord relu = PhoneData.load(vivant.save(new CompoundTag())).record(id);
            helper.assertTrue(relu.lockoutLevel() == 2, "niveau relu : " + relu.lockoutLevel());
            vivant.put(id, relu);

            PhoneStatePacket vue = ouvrir(helper, a);
            helper.assertTrue(vue.lock() == Lock.LOCKED && vue.lockoutSeconds() == 120,
                    "relu : encore bloqué 120 s, vu " + vue.lockoutSeconds());
            horloge = depart + 60_000L + 120_000L;
            serieBloque(helper, e, a, id, 240, 3);
        });
    }

    /** {@code /phone reset} efface le niveau avec le reste : le code choisi ensuite repart à 60 s. */
    @GameTest(template = "test/plancher", batch = LOT_BLOCAGE)
    public static void lePhoneResetRemetLeNiveauAZero(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_4", POS_A);
            JoueurDeTest t = e.equipe("tel_5", POS_B);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            long depart = horloge;
            serieBloque(helper, e, a, id, 60, 1);
            horloge = depart + 60_000L;
            serieBloque(helper, e, a, id, 120, 2);

            t.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            int fait = e.serveur.getCommands().performPrefixedCommand(t.joueur().createCommandSourceStack(),
                    "phone reset");
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(fait == 1 && rec.lockoutLevel() == 0 && rec.lockedUntil() == 0L,
                    "reset : " + fait + ", niveau " + rec.lockoutLevel() + ", fin " + rec.lockedUntil());

            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            serieBloque(helper, e, a, id, 60, 1);
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

    /**
     * Deux copies ouvertes au choix du code : quand A pose son code, l'écran de C passe tout de suite au verrouillé
     * (un {@code UPDATE} immédiat, sans attendre un geste de C ni le tic).
     */
    @GameTest(template = "test/plancher", batch = LOT_RESET)
    public static void unCodePoseSurUneCopieReverrouilleLesAutresTouteDeSuite(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_x", POS_A);
            JoueurDeTest c = e.joueur("tel_y", POS_C);
            e.telephone(a);
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            ouvrir(helper, a);
            helper.assertTrue(ouvrir(helper, c).lock() == Lock.SETUP, "C ouvre au choix du code");
            int sessionC = session(helper, c);
            c.etatsTelephoneRecus();
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP de A");
            List<PhoneStatePacket> recus = c.etatsTelephoneRecus();
            helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.UPDATE
                    && recus.get(0).lock() == Lock.LOCKED && recus.get(0).session() == sessionC,
                    "C reçoit aussitôt un UPDATE verrouillé, vu " + recus);
            helper.assertTrue(recus.get(0).ownerName().isEmpty() && !recus.get(0).owner(),
                    "rien du propriétaire : " + recus.get(0));
        });
    }

    /**
     * Une session déverrouillée dont le téléphone a été effacé sans la fermer : quand une copie pose un nouveau code,
     * elle reçoit tout de suite une vue verrouillée.
     */
    @GameTest(template = "test/plancher", batch = LOT_RESET)
    public static void unNouveauCodeReverrouilleLaSessionResteeTouteDeSuite(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_z", POS_A);
            JoueurDeTest c = e.joueur("tel_0", POS_C);
            UUID id = e.telephone(a);
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            PhoneData.get(e.serveur).erase(id);
            ouvrir(helper, c);
            a.etatsTelephoneRecus();
            attendre(helper, geste(helper, c, Action.SETUP, "9999"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "C pose son code");
            List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
            helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.UPDATE
                    && recus.get(0).lock() == Lock.LOCKED, "A reçoit aussitôt un UPDATE verrouillé, vu " + recus);
            helper.assertTrue(recus.get(0).ownerName().isEmpty() && !recus.get(0).owner() && !recus.get(0).airplane(),
                    "A ne voit rien de C : " + recus.get(0));
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

    // ---------------------------------------------------------------------------------------------
    // Lot B : la batterie à l'usage (tâche 6). Les essais durent plusieurs tics : l'essai reste ouvert entre les étapes
    // et se ferme à la dernière, ou à la première étape qui échoue.
    // ---------------------------------------------------------------------------------------------

    /** Une étape d'un essai long ; {@code derniere} ferme l'essai et le réussit. Une étape qui échoue ferme l'essai. */
    private static void etape(GameTestHelper helper, Essai e, boolean derniere, Runnable corps) {
        boolean fini = false;
        try {
            corps.run();
            fini = true;
        } finally {
            if (!fini || derniere) {
                e.close();
            }
        }
        if (derniere) {
            helper.succeed();
        }
    }

    private static int charge(JoueurDeTest j) {
        return PhoneItem.stored(j.joueur().getMainHandItem());
    }

    /** Les {@code CLOSE} d'une liste d'états. */
    private static List<PhoneStatePacket> fermetures(List<PhoneStatePacket> etats) {
        return etats.stream().filter(p -> p.kind() == Kind.CLOSE).toList();
    }

    /** 72 000 FE, écran ouvert 200 tics : deux FE par tic, payés par blocs de vingt tics (400 à 440 FE sortis). */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 300)
    public static void laBatterieSeVideEcranOuvert(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_d", POS_A);
            e.telephone(a);
            helper.assertTrue(charge(a) == 72_000, "départ : " + charge(a));
            ouvrir(helper, a);
            helper.runAfterDelay(200, () -> etape(helper, e, true, () -> {
                int fin = charge(a);
                helper.assertTrue(fin >= 71_560 && fin <= 71_600, "après 200 tics d'écran : " + fin + " FE");
                helper.assertTrue(PhoneService.session(a.uuid()).isPresent(), "la session reste ouverte");
            }));
        });
    }

    /** Écran fermé (geste {@code CLOSE}) : rien ne sort de la batterie. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 300)
    public static void ecranFermeRienNeSeVide(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_e", POS_A);
            e.telephone(a);
            ouvrir(helper, a);
            Reponse ferme = geste(helper, a, Action.CLOSE, "");
            helper.assertTrue(ferme.outcome() == Outcome.DONE && PhoneService.session(a.uuid()).isEmpty(),
                    "écran fermé : " + ferme.outcome());
            int depart = charge(a);
            helper.runAfterDelay(200, () -> etape(helper, e, true, () -> {
                helper.assertTrue(charge(a) == depart, "écran fermé : la charge a bougé, " + depart + " puis "
                        + charge(a));
            }));
        });
    }

    /** Trente FE ne couvrent pas un bloc de quarante : la batterie tombe à zéro et le téléphone s'éteint. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 100)
    public static void batterieVideEteintLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_f", POS_A);
            e.telephone(a);
            PhoneItem.store(a.joueur().getMainHandItem(), 30);
            ouvrir(helper, a);
            a.messagesRecus();
            helper.runAfterDelay(40, () -> etape(helper, e, true, () -> {
                List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
                List<PhoneStatePacket> fermes = fermetures(recus);
                helper.assertTrue(fermes.size() == 1 && fermes.get(0).reason() == CloseReason.BATTERY_EMPTY,
                        "un CLOSE BATTERY_EMPTY attendu, vu " + recus);
                helper.assertTrue(charge(a) == 0, "charge mise à zéro : " + charge(a));
                helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "session fermée");
                helper.assertTrue(a.messagesRecus().contains(PhoneService.MESSAGE + "battery_empty"),
                        "message de batterie vide");
            }));
        });
    }

    /** Zéro FE : l'ouverture est refusée et aucun {@code OPEN} n'est envoyé. */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unTelephoneVideNeSOuvrePas(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_g", POS_A);
            e.telephone(a);
            PhoneItem.store(a.joueur().getMainHandItem(), 0);
            a.etatsTelephoneRecus();
            Outcome r = PhoneService.open(a.joueur(), InteractionHand.MAIN_HAND);
            helper.assertTrue(r == Outcome.EMPTY_BATTERY, "batterie vide : " + r);
            List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
            helper.assertTrue(recus.isEmpty(), "aucun OPEN attendu, vu " + recus);
            helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "aucune session");
        });
    }

    /** La pile passe dans une autre case : fermeture au tic suivant (PHONE_GONE), puis plus aucune dépense. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void changerDeMainFermeLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_h", POS_A);
            e.telephone(a);
            ouvrir(helper, a);
            helper.runAfterDelay(25, () -> etape(helper, e, false, () -> {
                ItemStack phone = a.joueur().getMainHandItem();
                helper.assertTrue(PhoneService.session(a.uuid()).isPresent(), "session encore ouverte à 25 tics");
                a.etatsTelephoneRecus();
                a.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                a.joueur().getInventory().setItem(5, phone);
                int depart = PhoneItem.stored(phone);
                helper.runAfterDelay(2, () -> etape(helper, e, false, () -> {
                    List<PhoneStatePacket> fermes = fermetures(a.etatsTelephoneRecus());
                    helper.assertTrue(fermes.size() == 1 && fermes.get(0).reason() == CloseReason.PHONE_GONE,
                            "un CLOSE PHONE_GONE attendu, vu " + fermes);
                    helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "session fermée");
                    int apres = PhoneItem.stored(phone);
                    helper.runAfterDelay(60, () -> etape(helper, e, true, () -> {
                        helper.assertTrue(PhoneItem.stored(phone) == apres,
                                "plus de dépense : " + apres + " puis " + PhoneItem.stored(phone));
                        helper.assertTrue(apres <= depart, "la charge ne remonte pas");
                    }));
                }));
            }));
        });
    }

    /** Le joueur meurt (inventaire gardé : la pile est encore en main) : la session se ferme et ne dépense plus. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void mourirFermeLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            e.regleBooleenne(GameRules.RULE_KEEPINVENTORY, true);
            JoueurDeTest a = e.joueur("tel_i", POS_A);
            e.telephone(a);
            ouvrir(helper, a);
            a.joueur().kill();
            helper.assertTrue(!a.joueur().isAlive(), "le joueur d'essai est mort");
            helper.assertTrue(a.joueur().getMainHandItem().getItem() instanceof PhoneItem,
                    "le téléphone est resté en main (inventaire gardé)");
            helper.runAfterDelay(5, () -> etape(helper, e, false, () -> {
                List<PhoneStatePacket> fermes = fermetures(a.etatsTelephoneRecus());
                helper.assertTrue(fermes.size() == 1, "un CLOSE attendu à la mort, vu " + fermes);
                helper.assertTrue(PhoneService.session(a.uuid()).isEmpty(), "session fermée");
                int apres = charge(a);
                helper.runAfterDelay(60, () -> etape(helper, e, true, () -> {
                    helper.assertTrue(charge(a) == apres, "plus de dépense après la mort : " + apres + " puis "
                            + charge(a));
                }));
            }));
        });
    }

    /** Le client part : la session disparaît et la pile garde la charge qu'elle avait. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void deconnexionFermeLaSession(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_j", POS_A);
            e.telephone(a);
            ItemStack phone = a.joueur().getMainHandItem();
            ouvrir(helper, a);
            UUID joueur = a.uuid();
            helper.runAfterDelay(25, () -> etape(helper, e, false, () -> {
                a.quitter();
                helper.assertTrue(PhoneService.session(joueur).isEmpty(), "session absente après la déconnexion");
                int fige = PhoneItem.stored(phone);
                helper.assertTrue(fige > 0 && fige < 72_000, "la batterie avait servi avant le départ : " + fige);
                helper.runAfterDelay(60, () -> etape(helper, e, true, () -> {
                    helper.assertTrue(PhoneItem.stored(phone) == fige, "charge figée : " + fige + " puis "
                            + PhoneItem.stored(phone));
                    helper.assertTrue(PhoneService.session(joueur).isEmpty(), "toujours aucune session");
                }));
            }));
        });
    }

    /**
     * 36 040 FE (50 %) : le premier bloc ne change pas le pourcentage, les suivants le font tomber à 49 puis 48 en
     * 400 tics (un point = 720 FE = 18 blocs). Un seul {@code UPDATE} par point perdu, jamais deux fois le même.
     */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 500)
    public static void lePourcentageSuitLaCharge(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_k", POS_A);
            e.telephone(a);
            PhoneItem.store(a.joueur().getMainHandItem(), 36_040);
            PhoneStatePacket open = ouvrir(helper, a);
            helper.assertTrue(open.battery() == 50, "ouverture à 50 % : " + open.battery());
            helper.runAfterDelay(400, () -> etape(helper, e, true, () -> {
                List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
                List<Integer> pourcents = recus.stream().filter(p -> p.kind() == Kind.UPDATE)
                        .map(PhoneStatePacket::battery).toList();
                helper.assertTrue(recus.size() == pourcents.size(), "que des UPDATE attendus, vu " + recus);
                helper.assertTrue(pourcents.equals(List.of(49, 48)), "un UPDATE par point perdu (49 puis 48), vu "
                        + pourcents);
            }));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : la vue qui se fie au sel
    // ---------------------------------------------------------------------------------------------

    /**
     * Une session déverrouillée dont le téléphone a été effacé, puis reçu un autre code : l'{@code UPDATE} poussé au tic
     * (pourcentage perdu) est une vue verrouillée, sans nom, sans mode avion, sans propriétaire, jamais ses anciens
     * réglages ni ceux du nouveau propriétaire.
     */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void unUpdatePousseNeMontreRienDuNouveauProprietaire(GameTestHelper helper) {
        Essai e = new Essai(helper);
        etape(helper, e, false, () -> {
            JoueurDeTest a = e.joueur("tel_l", POS_A);
            JoueurDeTest c = e.joueur("tel_m", POS_C);
            UUID id = e.telephone(a);
            PhoneItem.store(a.joueur().getMainHandItem(), 36_040);
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            ouvrir(helper, a);
            attendre(helper, geste(helper, a, Action.SETUP, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP");
            attendre(helper, geste(helper, a, Action.AIRPLANE, "", "", true), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "avion de A");

            PhoneData.get(e.serveur).erase(id);
            ouvrir(helper, c);
            attendre(helper, geste(helper, c, Action.SETUP, "9999"), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "C pose son code");
            attendre(helper, geste(helper, c, Action.AIRPLANE, "", "", true), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "avion de C");
            String nomC = c.joueur().getGameProfile().getName();
            a.etatsTelephoneRecus();
            helper.runAfterDelay(30, () -> etape(helper, e, true, () -> {
                List<PhoneStatePacket> pousses = a.etatsTelephoneRecus().stream()
                        .filter(p -> p.kind() == Kind.UPDATE).toList();
                helper.assertTrue(!pousses.isEmpty(), "A devait recevoir un UPDATE au tic (pourcentage perdu)");
                for (PhoneStatePacket p : pousses) {
                    helper.assertTrue(p.lock() == Lock.LOCKED, "vue verrouillée attendue, vu " + p.lock());
                    helper.assertTrue(p.ownerName().isEmpty() && !p.owner() && !p.airplane(),
                            "rien du nouveau propriétaire (" + nomC + ") : " + p);
                }
            }));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot assistant : le fond choisi par l'assistant de première configuration, porté par SETUP
    // ---------------------------------------------------------------------------------------------

    /** {@code SETUP} avec un fond ({@code second}) : code, propriétaire et fond posés par le même geste. */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void unSetupAvecFondPoseLeCodeEtLeFondEnsemble(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_a", POS_A);
            UUID id = e.telephone(a);
            PhoneStatePacket open = ouvrir(helper, a);
            helper.assertTrue(open.lock() == Lock.SETUP && open.wallpaper().equals("aube"),
                    "neuf : SETUP et fond par défaut, vu " + open.lock() + " " + open.wallpaper());
            Reponse r = geste(helper, a, Action.SETUP, CODE, "lagon", false);
            attendre(helper, r, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP avec fond");
            helper.assertTrue(r.outcome() == Outcome.DONE, "SETUP fait : " + r.outcome());
            helper.assertTrue(r.etat().wallpaper().equals("lagon"), "la vue montre le fond choisi : " + r.etat());
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(a.uuid().equals(rec.owner()) && rec.salt() != null && rec.codeHash() != null,
                    "code et propriétaire posés : " + rec);
            helper.assertTrue(rec.wallpaper().equals("lagon"), "fond enregistré : " + rec.wallpaper());
            helper.assertTrue(rec.failures() == 0 && !rec.airplane(), "le reste est celui d'un téléphone neuf : " + rec);
            // Le code est bien celui envoyé : verrouiller puis déverrouiller avec lui.
            attendre(helper, geste(helper, a, Action.LOCK, ""), Kind.UPDATE, Lock.LOCKED, Result.NONE, "LOCK");
            Reponse bon = geste(helper, a, Action.UNLOCK, CODE);
            attendre(helper, bon, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "UNLOCK avec le code choisi");
            helper.assertTrue(bon.etat().wallpaper().equals("lagon"), "fond gardé : " + bon.etat().wallpaper());
        });
    }

    /** Un fond hors de la liste (inconnu, majuscules, vide, avec espace) est ignoré : le code est posé quand même. */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void unFondInconnuEstIgnoreEtLeCodePose(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_b", POS_A);
            for (String faux : List.of("plage", "LAGON", "Nuit", "nuit ", "aube2", "x".repeat(16))) {
                UUID id = e.telephone(a);
                ouvrir(helper, a);
                Reponse r = geste(helper, a, Action.SETUP, CODE, faux, false);
                attendre(helper, r, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP avec le fond « " + faux + " »");
                helper.assertTrue(r.outcome() == Outcome.DONE, "SETUP fait malgré le fond « " + faux + " »");
                PhoneData.PhoneRecord rec = e.donnees(id);
                helper.assertTrue(rec.codeHash() != null && a.uuid().equals(rec.owner()),
                        "code posé malgré le fond « " + faux + " »");
                helper.assertTrue(rec.wallpaper().equals("aube") && r.etat().wallpaper().equals("aube"),
                        "fond « " + faux + " » : le fond par défaut reste, vu " + rec.wallpaper());
                geste(helper, a, Action.CLOSE, "");
            }
        });
    }

    /** Un code non valide avec un fond valide : rien n'est posé, ni code ni fond (le fond suit la validation du code). */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void unCodeNonValideNePoseNiCodeNiFond(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_c", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            for (String faux : List.of("12a4", "482", "48210", "")) {
                Reponse r = geste(helper, a, Action.SETUP, faux, "nuit", false);
                attendre(helper, r, Kind.UPDATE, Lock.SETUP, Result.INVALID_CODE, "SETUP « " + faux + " » avec fond");
                PhoneData.PhoneRecord rec = e.donnees(id);
                helper.assertTrue(rec.codeHash() == null && rec.owner() == null,
                        "code « " + faux + " » refusé : aucun code posé");
                helper.assertTrue(rec.wallpaper().equals("aube") && r.etat().wallpaper().equals("aube"),
                        "code « " + faux + " » refusé : le fond ne doit pas changer, vu " + rec.wallpaper());
            }
        });
    }

    /** {@code SETUP} hors de l'état {@code SETUP} (verrouillé, déverrouillé) : refusé comme avant, sans le fond. */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void unSetupHorsDuChoixDuCodeNAppliquePasLeFond(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_d", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            choisirPuisVerrouiller(helper, a);
            String sel = e.donnees(id).salt();
            Reponse verrouille = geste(helper, a, Action.SETUP, "9999", "nuit", false);
            attendre(helper, verrouille, Kind.UPDATE, Lock.LOCKED, Result.NONE, "SETUP verrouillé");
            helper.assertTrue(verrouille.outcome() == Outcome.REFUSED,
                    "SETUP verrouillé refusé : " + verrouille.outcome());
            attendre(helper, geste(helper, a, Action.UNLOCK, CODE), Kind.UPDATE, Lock.UNLOCKED, Result.NONE,
                    "l'ancien code ouvre toujours");
            Reponse ouvert = geste(helper, a, Action.SETUP, "9999", "jungle", false);
            attendre(helper, ouvert, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP déverrouillé");
            helper.assertTrue(ouvert.outcome() == Outcome.REFUSED, "SETUP déverrouillé refusé : " + ouvert.outcome());
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(rec.wallpaper().equals("aube") && ouvert.etat().wallpaper().equals("aube"),
                    "le fond n'est pas appliqué hors du choix du code, vu " + rec.wallpaper());
            helper.assertTrue(sel.equals(rec.salt()), "le code n'a pas changé");
        });
    }

    /**
     * Sans fond, {@code SETUP} se comporte comme avant : le fond enregistré reste (ici « nuit », posé dans les données
     * d'un téléphone sans code) ; un fond inconnu le laisse aussi en place, il ne le remet pas au fond par défaut.
     */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void sansFondLeChoixDuCodeResteCommeAvant(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_e", POS_A);
            UUID neuf = e.telephone(a);
            ouvrir(helper, a);
            Reponse r = geste(helper, a, Action.SETUP, CODE);
            attendre(helper, r, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP sans fond");
            helper.assertTrue(e.donnees(neuf).wallpaper().equals("aube") && r.etat().wallpaper().equals("aube"),
                    "sans fond : fond par défaut, vu " + e.donnees(neuf).wallpaper());
            geste(helper, a, Action.CLOSE, "");

            for (String fond : List.of("", "plage")) {
                UUID id = e.telephone(a);
                PhoneData.get(e.serveur).put(id, new PhoneData.PhoneRecord(null, "", null, null, 0, 0L, 0, "nuit",
                        false));
                ouvrir(helper, a);
                Reponse s = geste(helper, a, Action.SETUP, CODE, fond, false);
                attendre(helper, s, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "SETUP, fond « " + fond + " »");
                helper.assertTrue(e.donnees(id).wallpaper().equals("nuit") && s.etat().wallpaper().equals("nuit"),
                        "fond « " + fond + " » : le fond enregistré reste, vu " + e.donnees(id).wallpaper());
                geste(helper, a, Action.CLOSE, "");
            }
        });
    }

    /** Le fond d'un {@code SETUP} d'une autre session ne passe pas : rien n'est posé. */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void unSetupDUneAutreSessionNAppliquePasLeFond(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_f", POS_A);
            UUID id = e.telephone(a);
            ouvrir(helper, a);
            int s = session(helper, a);
            Reponse r = geste(a, new PhoneActionPacket(s + 1000, Action.SETUP, CODE, "lagon", false), true);
            helper.assertTrue(r.outcome() == Outcome.NO_SESSION && r.etat() == null, "autre session : " + r);
            PhoneData.PhoneRecord rec = e.donnees(id);
            helper.assertTrue(rec.codeHash() == null && rec.wallpaper().equals("aube"),
                    "ni code ni fond par une autre session : " + rec.wallpaper());
        });
    }

    /**
     * Deux copies d'un même téléphone neuf, chacune au choix du code : A pose {@code 4821} avec le fond « lagon » ; le
     * {@code SETUP} de C (code « 9999 », fond « nuit ») arrive ensuite et est refusé. La première l'emporte : code,
     * propriétaire et fond de A restent, et {@code 4821} rouvre le téléphone.
     */
    @GameTest(template = "test/plancher", batch = LOT_ASSISTANT)
    public static void deuxCopiesNeuvesLaPremiereLEmporteAvecSonFond(GameTestHelper helper) {
        essai(helper, e -> {
            JoueurDeTest a = e.joueur("tel_as_g", POS_A);
            JoueurDeTest c = e.joueur("tel_as_h", POS_C);
            UUID id = e.telephone(a);
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, a.joueur().getMainHandItem().copy());
            helper.assertTrue(ouvrir(helper, a).lock() == Lock.SETUP, "A ouvre au choix du code");
            helper.assertTrue(ouvrir(helper, c).lock() == Lock.SETUP, "C ouvre au choix du code");
            int sessionC = session(helper, c);
            attendre(helper, geste(helper, a, Action.SETUP, CODE, "lagon", false), Kind.UPDATE, Lock.UNLOCKED,
                    Result.NONE, "SETUP de A avec lagon");
            PhoneData.PhoneRecord apresA = e.donnees(id);
            helper.assertTrue(a.uuid().equals(apresA.owner()) && apresA.wallpaper().equals("lagon"),
                    "A propriétaire, fond lagon : " + apresA.wallpaper());

            Reponse deC = geste(c, new PhoneActionPacket(sessionC, Action.SETUP, "9999", "nuit", false), true);
            helper.assertTrue(deC.outcome() == Outcome.REFUSED, "SETUP de C après celui de A : " + deC.outcome());
            helper.assertTrue(deC.etat() != null && deC.etat().lock() == Lock.LOCKED
                    && deC.etat().ownerName().isEmpty(), "C voit un téléphone verrouillé, sans nom : " + deC.etat());
            PhoneData.PhoneRecord apres = e.donnees(id);
            helper.assertTrue(apresA.salt().equals(apres.salt()) && apresA.codeHash().equals(apres.codeHash()),
                    "le code de A a changé");
            helper.assertTrue(a.uuid().equals(apres.owner()), "propriétaire changé : " + apres.owner());
            helper.assertTrue(apres.wallpaper().equals("lagon"), "fond de A remplacé : " + apres.wallpaper());

            attendre(helper, geste(helper, a, Action.LOCK, ""), Kind.UPDATE, Lock.LOCKED, Result.NONE, "LOCK de A");
            Reponse rouvre = geste(helper, a, Action.UNLOCK, CODE);
            attendre(helper, rouvre, Kind.UPDATE, Lock.UNLOCKED, Result.NONE, "4821 rouvre le téléphone");
            helper.assertTrue(rouvre.etat().wallpaper().equals("lagon"), "fond vu : " + rouvre.etat().wallpaper());
        });
    }
}
