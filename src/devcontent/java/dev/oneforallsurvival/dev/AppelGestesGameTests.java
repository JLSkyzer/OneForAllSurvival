package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.CallUpdates;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.call.PhoneDirectory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tour 1 de la relecture de la tâche 4a du lot T3 : les gestes d'appel par paquet et ce qui les refuse (écran
 * verrouillé, session fausse, téléphone hors de la main, autre téléphone, numéro d'appel faux), refuser sous le frein,
 * fermer sous le frein, l'état d'appel rattaché au téléphone de l'écran ({@link PhoneCallStatePacket#here()}), le
 * silence de la sonnerie une fois décroché ou raccroché. Chaque refus est jugé seul : frein oublié avant le geste,
 * écran déverrouillé sauf quand c'est le verrou que l'on juge, et un témoin qui passe dans les mêmes conditions.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AppelGestesGameTests {

    private static final String LOT = "telephone_appels_gestes";
    private static final String CODE = "4821";
    private static final String BELL = "minecraft:block.note_block.bell";
    private static final String BIT = "minecraft:block.note_block.bit";
    /** Un numéro de carte bien formé que personne n'a. */
    private static final String AUTRE = "0600000001";

    private AppelGestesGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        AppelGameTests.apres();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void oublierFrein(GameTestHelper helper, UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            helper.fail("frein du téléphone introuvable : " + e);
        }
    }

    private static int session(GameTestHelper helper, JoueurDeTest j) {
        Optional<PhoneSession> s = PhoneService.session(j.uuid());
        helper.assertTrue(s.isPresent(), "session ouverte attendue pour " + j.joueur().getGameProfile().getName());
        return s.isPresent() ? s.get().id() : -1;
    }

    private static void ouvrir(GameTestHelper helper, JoueurDeTest j) {
        PhoneService.Outcome r = PhoneService.open(j.joueur(), InteractionHand.MAIN_HAND);
        helper.assertTrue(r == PhoneService.Outcome.OPENED, "ouverture : " + r);
    }

    private static void ecran(GameTestHelper helper, JoueurDeTest j, PhoneActionPacket.Action action, String first) {
        oublierFrein(helper, j.uuid());
        PhoneService.act(j.joueur(), new PhoneActionPacket(session(helper, j), action, first, "", false));
    }

    private static boolean deverrouille(JoueurDeTest j) {
        return PhoneService.session(j.uuid()).map(PhoneSession::lock)
                .filter(PhoneStatePacket.Lock.UNLOCKED::equals).isPresent();
    }

    /** Pose un code puis ferme l'écran : un téléphone verrouillé, écran fermé. */
    private static void codeEtFerme(GameTestHelper helper, JoueurDeTest j) {
        ouvrir(helper, j);
        ecran(helper, j, PhoneActionPacket.Action.SETUP, CODE);
        helper.assertTrue(deverrouille(j), "déverrouillé après le choix du code");
        ecran(helper, j, PhoneActionPacket.Action.CLOSE, "");
        helper.assertTrue(PhoneService.session(j.uuid()).isEmpty(), "écran fermé");
    }

    /** Un geste d'appel, frein oublié juste avant : seul le motif jugé peut le refuser. */
    private static void geste(GameTestHelper helper, JoueurDeTest j, Action action, String first, String second) {
        oublierFrein(helper, j.uuid());
        j.envoyer(new PhoneCallActionPacket(session(helper, j), action, first, second));
    }

    private static Optional<Call> appelDe(JoueurDeTest j) {
        return CallService.callOf(j.joueur());
    }

    private static boolean sonne(JoueurDeTest j) {
        return appelDe(j).map(c -> c.state() == dev.oneforallsurvival.phone.call.CallBook.State.RINGING).orElse(false);
    }

    private static boolean enLigne(JoueurDeTest j) {
        return appelDe(j).map(c -> c.state() == dev.oneforallsurvival.phone.call.CallBook.State.ACTIVE).orElse(false);
    }

    private static PhoneCallStatePacket dernier(GameTestHelper helper, List<PhoneCallStatePacket> recus, String quoi) {
        helper.assertTrue(!recus.isEmpty(), quoi + " : aucun état d'appel reçu");
        return recus.get(recus.size() - 1);
    }

    private static String cle(PhoneCallStatePacket p) {
        return p.message().getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static List<String> appel(List<String> sons) {
        return sons.stream().filter(s -> s.equals(BELL) || s.equals(BIT)).toList();
    }

    private static void vider(JoueurDeTest... joueurs) {
        for (JoueurDeTest j : joueurs) {
            j.etatsAppelRecus();
            j.clesRecues();
            j.messagesRecus();
            j.sonsNommesRecus();
            j.sonsRecus();
            j.etatsTelephoneRecus();
        }
    }

    private static int sonner(GameTestHelper helper, AppelGameTests.Paire p) {
        CallService.Dial d = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number());
        helper.assertTrue(d.outcome() == CallService.Outcome.RINGING && d.callId() > 0,
                "l'appel devait sonner : " + d.outcome());
        return d.callId();
    }

    /** Un second téléphone, neuf et chargé, dans la main de {@code j} ; le sien passe en case 4 (toujours porté). */
    private static ItemStack autreTelephoneEnMain(JoueurDeTest j, ItemStack sien) {
        ItemStack autre = new ItemStack(PhoneRegistry.PHONE.get());
        PhoneItem.store(autre, PhoneConfig.capacity());
        j.joueur().getInventory().setItem(4, sien);
        j.joueur().setItemInHand(InteractionHand.MAIN_HAND, autre);
        return autre;
    }

    private static void attendreAntennes(GameTestHelper helper, AppelGameTests.Essai essai) {
        helper.assertTrue(essai.enService(AppelGameTests.A) && essai.enService(AppelGameTests.B),
                "antennes en service");
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    /**
     * Écran verrouillé, en ligne : ni enregistrer, ni retirer un contact, ni couper le micro, ni le haut-parleur (et
     * aucun carnet envoyé). Déverrouillé, les quatre mêmes gestes agissent (témoin).
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void verrouilleNiCarnetNiInterrupteurs(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_verrou");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    PhoneDirectory carnet = PhoneDirectory.get(essai.serveur());
                    UUID carte = p.carteL().simId();
                    helper.assertTrue(carnet.saveContact(carte, p.carteM().number(), "Maman")
                            == PhoneDirectory.SaveResult.SAVED, "contact posé d'avance");
                    codeEtFerme(helper, p.l());
                    int id = sonner(helper, p);
                    helper.assertTrue(CallService.answer(p.l().joueur(), id), "en ligne");
                    ouvrir(helper, p.l());
                    helper.assertTrue(!deverrouille(p.l()), "écran verrouillé");
                    vider(p.l());
                    List<Contact> avant = carnet.contacts(carte);
                    geste(helper, p.l(), Action.SAVE_CONTACT, AUTRE, "Garage");
                    helper.assertTrue(carnet.contacts(carte).equals(avant), "verrouillé : aucun contact enregistré");
                    geste(helper, p.l(), Action.REMOVE_CONTACT, p.carteM().number(), "");
                    helper.assertTrue(carnet.contacts(carte).equals(avant), "verrouillé : aucun contact retiré");
                    geste(helper, p.l(), Action.MUTE, "on", "");
                    helper.assertTrue(!appelDe(p.l()).map(Call::calleeMuted).orElse(true), "verrouillé : micro gardé");
                    geste(helper, p.l(), Action.SPEAKER, "on", "");
                    helper.assertTrue(!appelDe(p.l()).map(Call::calleeSpeaker).orElse(true),
                            "verrouillé : haut-parleur gardé");
                    helper.assertTrue(p.l().etatsAppelRecus().isEmpty(), "verrouillé : aucun état, aucun carnet");
                    // Témoin : déverrouillé, les quatre mêmes gestes agissent.
                    ecran(helper, p.l(), PhoneActionPacket.Action.UNLOCK, CODE);
                    helper.assertTrue(deverrouille(p.l()), "déverrouillé");
                    geste(helper, p.l(), Action.SAVE_CONTACT, AUTRE, "Garage");
                    helper.assertTrue(carnet.contacts(carte).size() == avant.size() + 1, "déverrouillé : enregistré");
                    geste(helper, p.l(), Action.REMOVE_CONTACT, p.carteM().number(), "");
                    helper.assertTrue(carnet.contacts(carte).size() == avant.size(), "déverrouillé : retiré");
                    geste(helper, p.l(), Action.MUTE, "on", "");
                    helper.assertTrue(appelDe(p.l()).map(Call::calleeMuted).orElse(false), "déverrouillé : micro coupé");
                    geste(helper, p.l(), Action.SPEAKER, "on", "");
                    helper.assertTrue(appelDe(p.l()).map(Call::calleeSpeaker).orElse(false), "déverrouillé : haut-parleur");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Un numéro de session faux, puis le bon numéro avec le téléphone hors de la main : décrocher ne fait rien. Remis en
     * main, le même geste décroche (témoin).
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void sessionFausseOuTelephoneHorsDeLaMain(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_main");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    ouvrir(helper, p.l());
                    int id = sonner(helper, p);
                    String texte = Integer.toString(id);
                    oublierFrein(helper, p.l().uuid());
                    p.l().envoyer(new PhoneCallActionPacket(session(helper, p.l()) + 1000, Action.ANSWER, texte, ""));
                    helper.assertTrue(sonne(p.l()), "session fausse : toujours en sonnerie");
                    p.l().joueur().getInventory().setItem(4, p.phoneL());
                    p.l().joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    geste(helper, p.l(), Action.ANSWER, texte, "");
                    helper.assertTrue(sonne(p.l()), "téléphone hors de la main : toujours en sonnerie");
                    p.l().joueur().getInventory().setItem(4, ItemStack.EMPTY);
                    p.l().joueur().setItemInHand(InteractionHand.MAIN_HAND, p.phoneL());
                    geste(helper, p.l(), Action.ANSWER, texte, "");
                    helper.assertTrue(enLigne(p.l()), "remis en main : décroché");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * L'appelé tient un autre téléphone, configuré et déverrouillé, écran ouvert dessus, le sien en poche : l'appel lui
     * arrive marqué « ailleurs » (à l'ouverture aussi), « Appel entrant » et la fin passent en barre d'action, et ni
     * décrocher ni refuser depuis cet écran n'agit, même au bon numéro d'appel. Ouvrir l'application Téléphone sur cet
     * autre téléphone joint le carnet de sa carte (aucune ici : vide), marqué ailleurs (revue de la tâche 4b, I2), et
     * rien du carnet de la carte en appel ; la fin, elle, n'en joint aucun, écran déverrouillé compris.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void unAutreTelephoneOuvertNePrendPasLAppel(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_autre");
        AppelGameTests.Essai essai = p.essai();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    helper.assertTrue(PhoneDirectory.get(essai.serveur()).saveContact(p.carteL().simId(),
                            p.carteM().number(), "Maman") == PhoneDirectory.SaveResult.SAVED,
                            "un contact au carnet de la carte qui va sonner");
                    autreTelephoneEnMain(p.l(), p.phoneL());
                    ouvrir(helper, p.l());
                    ecran(helper, p.l(), PhoneActionPacket.Action.SETUP, CODE);
                    helper.assertTrue(deverrouille(p.l()), "l'autre téléphone, configuré, déverrouillé");
                    vider(p.l());
                    id[0] = sonner(helper, p);
                    PhoneCallStatePacket entrant = dernier(helper, p.l().etatsAppelRecus(), "sonnerie");
                    helper.assertTrue(entrant.phase() == Phase.INCOMING && !entrant.here(),
                            "l'appel arrive marqué ailleurs : " + entrant);
                    // Rouvert sur l'autre téléphone pendant la sonnerie : toujours ailleurs.
                    ouvrir(helper, p.l());
                    PhoneCallStatePacket rouvert = dernier(helper, p.l().etatsAppelRecus(), "à l'ouverture");
                    helper.assertTrue(rouvert.phase() == Phase.INCOMING && !rouvert.here(),
                            "à l'ouverture, ailleurs : " + rouvert);
                    ecran(helper, p.l(), PhoneActionPacket.Action.UNLOCK, CODE);
                    helper.assertTrue(deverrouille(p.l()), "rouvert puis déverrouillé");
                    vider(p.l());
                    geste(helper, p.l(), Action.OPEN_PHONE_APP, "", "");
                    PhoneCallStatePacket appli = dernier(helper, p.l().etatsAppelRecus(), "ouvrir l'application");
                    helper.assertTrue(appli.phase() == Phase.INCOMING && !appli.here() && appli.book() != null,
                            "l'application sur l'autre téléphone : un carnet, marqué ailleurs : " + appli);
                    helper.assertTrue(appli.book() != null && appli.book().contacts().isEmpty()
                            && appli.book().recents().isEmpty(),
                            "le carnet du téléphone tenu (sans carte), rien de la carte en appel : " + appli);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(p.l().clesRecues().contains(CallUpdates.INCOMING),
                            "écran ouvert sur un autre téléphone : « Appel entrant » en barre d'action");
                    String texte = Integer.toString(id[0]);
                    geste(helper, p.l(), Action.ANSWER, texte, "");
                    helper.assertTrue(sonne(p.l()), "décrocher depuis l'autre téléphone : toujours en sonnerie");
                    geste(helper, p.l(), Action.DECLINE, texte, "");
                    helper.assertTrue(sonne(p.l()), "refuser depuis l'autre téléphone : toujours en sonnerie");
                    helper.assertTrue(deverrouille(p.l()), "à la fin, l'écran ouvert ailleurs est déverrouillé");
                    vider(p.l());
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché par l'appelant");
                    PhoneCallStatePacket fin = dernier(helper, p.l().etatsAppelRecus(), "fin");
                    helper.assertTrue(fin.phase() == Phase.NONE && !fin.here() && fin.book() == null,
                            "fin marquée ailleurs, sans carnet : " + fin);
                    helper.assertTrue(p.l().clesRecues().contains(CallUpdates.ENDED + "hung_up"),
                            "la fin en barre d'action, écran ouvert ailleurs");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Décrocher avec un numéro d'appel faux (voisin, zéro, négatif, lettres, zéro en tête, vide) ne fait rien ; le bon
     * décroche. Puis fermer l'écran juste après un geste, sous le frein : l'écran se ferme quand même.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void unNumeroDAppelFauxNAgitPasEtFermerEchappeAuFrein(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_numero");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    ouvrir(helper, p.l());
                    int id = sonner(helper, p);
                    for (String faux : new String[] {Integer.toString(id + 1), "0", "-" + id, "abc", "0" + id, ""}) {
                        geste(helper, p.l(), Action.ANSWER, faux, "");
                        helper.assertTrue(sonne(p.l()), "numéro d'appel « " + faux + " » : toujours en sonnerie");
                    }
                    geste(helper, p.l(), Action.ANSWER, Integer.toString(id), "");
                    helper.assertTrue(enLigne(p.l()), "le bon numéro décroche");
                    // Le geste d'avant a noté le frein ; fermer dans le même tic n'y passe pas.
                    PhoneService.Outcome ferme = PhoneService.act(p.l().joueur(), new PhoneActionPacket(
                            session(helper, p.l()), PhoneActionPacket.Action.CLOSE, "", "", false));
                    helper.assertTrue(ferme == PhoneService.Outcome.DONE && PhoneService.session(p.l().uuid()).isEmpty(),
                            "fermer n'est jamais freiné : " + ferme);
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Écran verrouillé, juste après un geste (frein noté) : refuser par paquet refuse quand même. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void refuserVerrouilleSousLeFrein(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_refus");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    codeEtFerme(helper, p.l());
                    int id = sonner(helper, p);
                    ouvrir(helper, p.l());
                    helper.assertTrue(!deverrouille(p.l()), "écran verrouillé");
                    // Un geste refusé verrouillé note quand même le frein ; le refus part dans le même tic.
                    geste(helper, p.l(), Action.OPEN_PHONE_APP, "", "");
                    p.l().envoyer(new PhoneCallActionPacket(session(helper, p.l()), Action.DECLINE,
                            Integer.toString(id), ""));
                    helper.assertTrue(appelDe(p.l()).isEmpty(), "refusé, verrouillé et sous le frein");
                    AppelGameTests.finiPar(helper, id, End.DECLINED);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Décroché, plus de deux périodes de sonnerie : ni cloche ni bip. Raccroché pendant la sonnerie : non plus. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 400)
    public static void niClocheNiBipApresDecrocheOuRaccroche(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_silence");
        AppelGameTests.Essai essai = p.essai();
        int[] id = new int[1];
        int attente = CallUpdates.RING_PERIOD * 2 + 5;
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p);
                    helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "décroché");
                    vider(p.m(), p.l());
                })
                .thenIdle(attente)
                .thenExecute(() -> {
                    List<String> l = appel(p.l().sonsNommesRecus());
                    List<String> m = appel(p.m().sonsNommesRecus());
                    helper.assertTrue(l.isEmpty() && m.isEmpty(), "en ligne, ni cloche ni bip : " + l + " " + m);
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché");
                    id[0] = sonner(helper, p);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(appel(p.l().sonsNommesRecus()).contains(BELL), "la première sonnerie");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché pendant la sonnerie");
                    vider(p.m(), p.l());
                })
                .thenIdle(attente)
                .thenExecute(() -> {
                    List<String> l = appel(p.l().sonsNommesRecus());
                    List<String> m = appel(p.m().sonsNommesRecus());
                    helper.assertTrue(l.isEmpty() && m.isEmpty(), "raccroché, ni cloche ni bip : " + l + " " + m);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Après un appel fini sur son téléphone (message, numéro), l'appelé ouvre un autre téléphone : il reçoit un état
     * vide qui remplace l'ancien (aucun message, aucun numéro, aucun carnet). Et une fin d'appel n'envoie pas le carnet
     * à un écran déverrouillé dont le téléphone a quitté la main.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void ouvrirUnAutreTelephoneOublieLAppelFini(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "ge_oubli");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> attendreAntennes(helper, essai))
                .thenExecute(() -> {
                    ouvrir(helper, p.l());
                    ecran(helper, p.l(), PhoneActionPacket.Action.SETUP, CODE);
                    helper.assertTrue(deverrouille(p.l()), "déverrouillé");
                    int id = sonner(helper, p);
                    helper.assertTrue(CallService.answer(p.l().joueur(), id), "en ligne");
                    // Le téléphone quitte la main, l'écran reste ouvert : la fin ne lui donne pas le carnet.
                    p.l().joueur().getInventory().setItem(4, p.phoneL());
                    p.l().joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    vider(p.l());
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id), "raccroché");
                    PhoneCallStatePacket fin = dernier(helper, p.l().etatsAppelRecus(), "fin");
                    helper.assertTrue(fin.phase() == Phase.NONE && !cle(fin).isEmpty(), "fin avec message : " + fin);
                    helper.assertTrue(fin.book() == null, "téléphone hors de la main : pas de carnet");
                    p.l().joueur().getInventory().setItem(4, ItemStack.EMPTY);
                    p.l().joueur().setItemInHand(InteractionHand.MAIN_HAND, p.phoneL());
                    autreTelephoneEnMain(p.l(), p.phoneL());
                    vider(p.l());
                    ouvrir(helper, p.l());
                    PhoneCallStatePacket ouvert = dernier(helper, p.l().etatsAppelRecus(), "ouverture d'un autre");
                    helper.assertTrue(ouvert.phase() == Phase.NONE && ouvert.callId() == 0 && ouvert.number().isEmpty()
                            && ouvert.name().isEmpty() && cle(ouvert).isEmpty() && ouvert.book() == null
                            && ouvert.here(), "un état vide à l'ouverture : " + ouvert);
                    essai.fermer();
                })
                .thenSucceed();
    }
}
