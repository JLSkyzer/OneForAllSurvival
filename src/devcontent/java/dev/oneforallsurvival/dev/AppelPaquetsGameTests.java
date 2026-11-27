package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.CallUpdates;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le trajet des appels jusqu'au client (lot T3, tâche 4a) : gestes par paquet ({@link PhoneCallActionPacket}), états
 * reçus par les deux porteurs écran fermé compris ({@link PhoneCallStatePacket}, décodés depuis les octets envoyés),
 * carnet, sonnerie, tonalité, messages en barre d'action, batterie pendant l'appel. Deux joueurs d'essai sur deux
 * antennes d'opérateurs différents ({@link AppelGameTests#paire}).
 *
 * <p>{@link JoueurDeTest#envoyer} fait traiter un paquet sur place (leçon du 2026-11-12) : deux gestes envoyés dans le
 * même tic se suivent à moins de {@link PhoneService#MIN_TICKS_BETWEEN} tics, donc sous le frein.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AppelPaquetsGameTests {

    private static final String LOT = "telephone_appels_reseau";
    private static final String LOT_BATTERIE = "telephone_appels_batterie";
    private static final String CODE = "4821";
    private static final String BELL = "minecraft:block.note_block.bell";
    private static final String BIT = "minecraft:block.note_block.bit";

    private AppelPaquetsGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        AppelGameTests.apres();
    }

    @AfterBatch(batch = LOT_BATTERIE)
    public static void apresBatterie(ServerLevel level) {
        AppelGameTests.apres();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Oublie le frein des gestes de l'écran ({@code PhoneService.clearThrottle}, de visibilité paquet). */
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

    /** Ouvre le téléphone neuf de {@code j}, choisit {@link #CODE} : écran ouvert, déverrouillé. */
    private static void deverrouille(GameTestHelper helper, JoueurDeTest j) {
        ouvrir(helper, j);
        ecran(helper, j, PhoneActionPacket.Action.SETUP, CODE);
        helper.assertTrue(PhoneService.session(j.uuid()).map(PhoneSession::lock)
                .filter(PhoneStatePacket.Lock.UNLOCKED::equals).isPresent(), "déverrouillé");
    }

    /** Pose un code puis ferme l'écran : un téléphone verrouillé, écran fermé. */
    private static void codeEtFerme(GameTestHelper helper, JoueurDeTest j) {
        deverrouille(helper, j);
        ecran(helper, j, PhoneActionPacket.Action.CLOSE, "");
        helper.assertTrue(PhoneService.session(j.uuid()).isEmpty(), "écran fermé");
    }

    private static void geste(GameTestHelper helper, JoueurDeTest j, Action action, String first, String second) {
        j.envoyer(new PhoneCallActionPacket(session(helper, j), action, first, second));
    }

    private static PhoneCallStatePacket dernier(GameTestHelper helper, List<PhoneCallStatePacket> recus, String quoi) {
        helper.assertTrue(!recus.isEmpty(), quoi + " : aucun état d'appel reçu");
        return recus.get(recus.size() - 1);
    }

    /** Les seuls sons du téléphone (sonnerie, tonalité) : l'antenne voisine joue aussi les siens. */
    private static List<String> appel(List<String> sons) {
        return sons.stream().filter(s -> s.equals(BELL) || s.equals(BIT)).toList();
    }

    private static String cle(PhoneCallStatePacket p) {
        return p.message().getContents() instanceof TranslatableContents t ? t.getKey() : "";
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

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_appels_reseau
    // ---------------------------------------------------------------------------------------------

    /**
     * Composer par paquet ; l'appelé reçoit {@code INCOMING} écran fermé (avec la sonnerie et « Appel entrant »),
     * ouvre son téléphone verrouillé (l'appel lui est montré), décroche verrouillé : les deux reçoivent {@code ACTIVE} ;
     * il raccroche dans le même tic que le décroché (sous le frein) : les deux reçoivent la fin.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void composerDecrocherVerrouilleRaccrocherSousLeFrein(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "pq_base");
        AppelGameTests.Essai essai = p.essai();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    codeEtFerme(helper, p.l());
                    deverrouille(helper, p.m());
                    vider(p.m(), p.l());
                    oublierFrein(helper, p.m().uuid());
                    geste(helper, p.m(), Action.DIAL, p.carteL().number(), "");
                    helper.assertTrue(CallService.callOf(p.l().joueur()).isPresent(), "ça sonne chez l'appelé");
                    id[0] = CallService.callOf(p.l().joueur()).get().id();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    PhoneCallStatePacket m = dernier(helper, p.m().etatsAppelRecus(), "appelant");
                    helper.assertTrue(m.phase() == Phase.OUTGOING && m.callId() == id[0]
                            && m.number().equals(p.carteL().number()), "appelant : " + m);
                    PhoneCallStatePacket l = dernier(helper, p.l().etatsAppelRecus(), "appelé, écran fermé");
                    helper.assertTrue(l.phase() == Phase.INCOMING && l.callId() == id[0]
                            && l.number().equals(p.carteM().number()) && l.book() == null, "appelé : " + l);
                    helper.assertTrue(p.l().clesRecues().contains(CallUpdates.INCOMING),
                            "« Appel entrant » en barre d'action, écran fermé");
                    helper.assertTrue(p.l().sonsNommesRecus().contains(BELL), "la sonnerie chez l'appelé");
                    List<String> sonsM = p.m().sonsNommesRecus();
                    helper.assertTrue(sonsM.contains(BIT), "la tonalité chez l'appelant : " + sonsM);
                    helper.assertTrue(!p.m().clesRecues().contains(CallUpdates.INCOMING),
                            "l'appelant ne lit pas « Appel entrant »");
                    // L'appelé ouvre : verrouillé, l'appel lui est montré tout de suite.
                    ouvrir(helper, p.l());
                    helper.assertTrue(PhoneService.session(p.l().uuid()).map(PhoneSession::lock)
                            .filter(PhoneStatePacket.Lock.LOCKED::equals).isPresent(), "écran verrouillé");
                    PhoneCallStatePacket ouvert = dernier(helper, p.l().etatsAppelRecus(), "à l'ouverture");
                    helper.assertTrue(ouvert.phase() == Phase.INCOMING, "à l'ouverture : " + ouvert.phase());
                    // Décroche verrouillé puis raccroche dans le même tic : le second geste est sous le frein.
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.ANSWER, Integer.toString(id[0]), "");
                    PhoneCallStatePacket actifL = dernier(helper, p.l().etatsAppelRecus(), "décroché, appelé");
                    PhoneCallStatePacket actifM = dernier(helper, p.m().etatsAppelRecus(), "décroché, appelant");
                    helper.assertTrue(actifL.phase() == Phase.ACTIVE && actifM.phase() == Phase.ACTIVE
                            && actifL.answeredMillis() > 0 && actifL.answeredMillis() == actifM.answeredMillis(),
                            "les deux en ligne : " + actifL + " " + actifM);
                    geste(helper, p.l(), Action.HANGUP, Integer.toString(id[0]), "");
                    helper.assertTrue(CallService.callOf(p.l().joueur()).isEmpty(),
                            "raccrocher n'est jamais freiné");
                    AppelGameTests.finiPar(helper, id[0], End.HUNG_UP);
                    PhoneCallStatePacket finL = dernier(helper, p.l().etatsAppelRecus(), "fin, appelé");
                    PhoneCallStatePacket finM = dernier(helper, p.m().etatsAppelRecus(), "fin, appelant");
                    for (PhoneCallStatePacket fin : new PhoneCallStatePacket[] {finL, finM}) {
                        helper.assertTrue(fin.phase() == Phase.NONE && fin.callId() == id[0]
                                && cle(fin).equals(CallUpdates.ENDED + "hung_up"), "fin : " + fin + " " + cle(fin));
                    }
                    // L'appelé, verrouillé, ne reçoit pas le carnet ; l'appelant, déverrouillé, le reçoit (récents).
                    helper.assertTrue(finL.book() == null, "aucun carnet à un écran verrouillé");
                    helper.assertTrue(finM.book() != null && !finM.book().recents().isEmpty(),
                            "le carnet changé à l'écran déverrouillé : " + finM.book());
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Écran verrouillé, composer ne fait rien ; décroché, l'écran verrouillé ne coupe ni micro ni haut-parleur. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void composerEstRefuseEcranVerrouille(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "pq_verrou");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    codeEtFerme(helper, p.m());
                    ouvrir(helper, p.m());
                    vider(p.m(), p.l());
                    oublierFrein(helper, p.m().uuid());
                    geste(helper, p.m(), Action.DIAL, p.carteL().number(), "");
                    helper.assertTrue(CallService.callOf(p.m().joueur()).isEmpty()
                            && CallService.callOf(p.l().joueur()).isEmpty(), "rien ne sonne, écran verrouillé");
                    helper.assertTrue(p.m().etatsAppelRecus().isEmpty() && p.l().etatsAppelRecus().isEmpty(),
                            "aucun état envoyé");
                    // Sans session, rien non plus : un numéro de session faux.
                    p.m().envoyer(new PhoneCallActionPacket(session(helper, p.m()) + 1000, Action.OPEN_PHONE_APP,
                            "", ""));
                    helper.assertTrue(p.m().etatsAppelRecus().isEmpty(), "session fausse : rien");
                    // Verrouillé, ni l'application Téléphone ni le carnet.
                    oublierFrein(helper, p.m().uuid());
                    geste(helper, p.m(), Action.OPEN_PHONE_APP, "", "");
                    helper.assertTrue(p.m().etatsAppelRecus().isEmpty(), "verrouillé : pas de carnet");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Le carnet par paquet : enregistrer (et ses refus : nom, numéro), ouvrir l'application, retirer ; le nom donné par
     * l'appelé s'affiche chez lui quand l'appelant sonne, pas chez l'appelant. Ni l'identifiant de carte ni le jeton
     * dans les octets envoyés.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void leCarnetArriveEtLeNomSAffiche(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "pq_carnet");
        AppelGameTests.Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    deverrouille(helper, p.l());
                    vider(p.l());
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.SAVE_CONTACT, p.carteM().number(), "Maman");
                    PhoneCallStatePacket enregistre = dernier(helper, p.l().etatsAppelRecus(), "SAVE_CONTACT");
                    helper.assertTrue(enregistre.book() != null && enregistre.book().contacts()
                            .equals(List.of(new Contact(p.carteM().number(), "Maman"))) && cle(enregistre).isEmpty(),
                            "carnet reçu : " + enregistre.book());
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.SAVE_CONTACT, p.carteM().number(), "§§§");
                    helper.assertTrue(cle(dernier(helper, p.l().etatsAppelRecus(), "nom piégé"))
                            .equals("message.oneforallsurvival.call.contact.bad_name"), "nom refusé");
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.SAVE_CONTACT, "abc", "Garage");
                    helper.assertTrue(cle(dernier(helper, p.l().etatsAppelRecus(), "numéro faux"))
                            .equals("message.oneforallsurvival.call.contact.bad_number"), "numéro refusé");
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.OPEN_PHONE_APP, "", "");
                    PhoneCallStatePacket appli = dernier(helper, p.l().etatsAppelRecus(), "OPEN_PHONE_APP");
                    helper.assertTrue(appli.phase() == Phase.NONE && appli.book() != null
                            && appli.book().contacts().size() == 1, "application : " + appli.book());
                    sansCarte(helper, appli, p.carteL().simId());
                    // L'appelant sonne : le nom de l'appelé pour lui (« Maman ») chez l'appelé seulement.
                    CallService.Dial d = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number());
                    helper.assertTrue(d.callId() > 0, "ça sonne : " + d.outcome());
                    PhoneCallStatePacket chezL = dernier(helper, p.l().etatsAppelRecus(), "sonnerie, appelé");
                    PhoneCallStatePacket chezM = dernier(helper, p.m().etatsAppelRecus(), "sonnerie, appelant");
                    helper.assertTrue(chezL.phase() == Phase.INCOMING && chezL.name().equals("Maman"),
                            "nom chez l'appelé : « " + chezL.name() + " »");
                    helper.assertTrue(chezM.phase() == Phase.OUTGOING && chezM.name().isEmpty(),
                            "aucun nom chez l'appelant : « " + chezM.name() + " »");
                    sansCarte(helper, chezL, p.carteL().simId());
                    sansCarte(helper, chezL, p.carteM().simId());
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), d.callId()), "raccroché");
                    oublierFrein(helper, p.l().uuid());
                    geste(helper, p.l(), Action.REMOVE_CONTACT, p.carteM().number(), "");
                    PhoneCallStatePacket retire = dernier(helper, p.l().etatsAppelRecus(), "REMOVE_CONTACT");
                    helper.assertTrue(retire.book() != null && retire.book().contacts().isEmpty()
                            && retire.book().recents().size() == 1, "retiré, l'appel manqué reste : " + retire.book());
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Les octets d'un état ne contiennent pas l'identifiant de carte, ni en texte ni en binaire. */
    private static void sansCarte(GameTestHelper helper, PhoneCallStatePacket etat, UUID simId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            etat.encode(buf);
            byte[] octets = new byte[buf.readableBytes()];
            buf.getBytes(0, octets);
            String brut = new String(octets, StandardCharsets.ISO_8859_1);
            helper.assertTrue(!brut.contains(simId.toString()), "identifiant de carte envoyé en texte");
            FriendlyByteBuf uuid = new FriendlyByteBuf(Unpooled.buffer());
            uuid.writeUUID(simId);
            byte[] u = new byte[16];
            uuid.getBytes(0, u);
            uuid.release();
            helper.assertTrue(!brut.contains(new String(u, StandardCharsets.ISO_8859_1)),
                    "identifiant de carte envoyé en binaire");
        } finally {
            buf.release();
        }
    }

    /**
     * La sonnerie s'entend à {@value CallUpdates#RING_RANGE} blocs autour de l'appelé, pas au-delà ; elle revient toutes
     * les {@value CallUpdates#RING_PERIOD} tics ; la tonalité n'est que pour l'appelant. Écran ouvert, l'appelé ne lit
     * pas « Appel entrant ».
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void laSonnerieSEntendAHuitBlocs(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "pq_sonne");
        AppelGameTests.Essai essai = p.essai();
        JoueurDeTest loin = essai.joueur("ap_pq_sonne_x");
        JoueurDeTest pres = essai.joueur("ap_pq_sonne_y");
        int[] id = new int[1];
        int[] debut = new int[1];
        int[] vuA = new int[1];
        List<String> sonsL = new java.util.ArrayList<>();
        List<String> sonsM = new java.util.ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    // Le long de l'axe x depuis l'appelé (en B) : 9 blocs, puis 7. L'appelant, en A, est à 7,07 blocs.
                    essai.placer(loin, AppelGameTests.B, 9);
                    essai.placer(pres, AppelGameTests.B, 7);
                    ouvrir(helper, p.l());
                    vider(p.m(), p.l(), loin, pres);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    vider(p.m(), p.l(), loin, pres);
                    CallService.Dial d = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number());
                    helper.assertTrue(d.callId() > 0, "ça sonne : " + d.outcome() + " "
                            + (d.message() == null ? "" : d.message().getString()));
                    id[0] = d.callId();
                    debut[0] = helper.getLevel().getServer().getTickCount();
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(appel(p.l().sonsNommesRecus()).equals(List.of(BELL)), "une sonnerie chez l'appelé");
                    helper.assertTrue(appel(pres.sonsNommesRecus()).equals(List.of(BELL)), "le voisin à 7 blocs l'entend");
                    helper.assertTrue(appel(loin.sonsNommesRecus()).isEmpty(), "personne à 9 blocs");
                    List<String> premiersM = appel(p.m().sonsNommesRecus());
                    helper.assertTrue(premiersM.contains(BIT) && premiersM.contains(BELL) && premiersM.size() == 2,
                            "l'appelant, à 7 blocs : sa tonalité et la sonnerie voisine, vu " + premiersM);
                    helper.assertTrue(!p.l().clesRecues().contains(CallUpdates.INCOMING),
                            "écran ouvert : pas de « Appel entrant »");
                })
                .thenWaitUntil(() -> {
                    sonsL.addAll(appel(p.l().sonsNommesRecus()));
                    sonsM.addAll(appel(p.m().sonsNommesRecus()));
                    helper.assertTrue(sonsL.contains(BELL), "la seconde sonnerie se fait attendre");
                    vuA[0] = helper.getLevel().getServer().getTickCount();
                })
                .thenExecute(() -> {
                    int ecart = vuA[0] - debut[0];
                    helper.assertTrue(ecart >= CallUpdates.RING_PERIOD && ecart <= CallUpdates.RING_PERIOD + 1,
                            "seconde sonnerie lue " + ecart + " tics après la première");
                    helper.assertTrue(sonsL.equals(List.of(BELL)), "une seconde sonnerie, sans bip : " + sonsL);
                    helper.assertTrue(sonsM.contains(BIT), "un second bip : " + sonsM);
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_appels_batterie
    // ---------------------------------------------------------------------------------------------

    /**
     * En ligne, écran fermé, la batterie de l'appelé baisse au rythme de l'écran ouvert ; l'appelant, écran ouvert sur
     * ce téléphone, ne paie qu'une fois. À plat, l'appel finit ({@code PHONE_GONE}) et l'appelé, écran fermé, lit la
     * fin en barre d'action.
     */
    @GameTest(template = "test/plancher", batch = LOT_BATTERIE, timeoutTicks = 300)
    public static void laBatterieBaissePendantLAppelEtLeCoupeAZero(GameTestHelper helper) {
        AppelGameTests.Paire p = AppelGameTests.paire(helper, "pq_pile");
        AppelGameTests.Essai essai = p.essai();
        int[] id = new int[1];
        int[] avant = new int[2];
        int fenetre = 60;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number()).callId();
                    helper.assertTrue(id[0] > 0 && CallService.answer(p.l().joueur(), id[0]), "en ligne");
                    ouvrir(helper, p.m());
                })
                // Un bloc de vingt tics payé d'avance de part et d'autre : on mesure à partir d'une borne de bloc.
                .thenIdle(20)
                .thenExecute(() -> {
                    avant[0] = PhoneItem.stored(p.phoneM());
                    avant[1] = PhoneItem.stored(p.phoneL());
                })
                .thenIdle(fenetre)
                .thenExecute(() -> {
                    int coutM = avant[0] - PhoneItem.stored(p.phoneM());
                    int coutL = avant[1] - PhoneItem.stored(p.phoneL());
                    int attendu = PhoneConfig.drainPerTick() * fenetre;
                    helper.assertTrue(coutL == attendu, "écran fermé, l'appel coûte comme l'écran : " + coutL
                            + " au lieu de " + attendu);
                    helper.assertTrue(coutM == attendu, "écran ouvert, aucun double compte : " + coutM
                            + " au lieu de " + attendu);
                    vider(p.l());
                    PhoneItem.store(p.phoneL(), 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(AppelGameTests.fin(id[0]).isPresent(), "l'appel doit finir"))
                .thenExecute(() -> {
                    AppelGameTests.finiPar(helper, id[0], End.PHONE_GONE);
                    helper.assertTrue(PhoneItem.stored(p.phoneL()) == 0, "batterie à plat");
                    helper.assertTrue(p.l().clesRecues().contains(CallUpdates.ENDED + "interrupted"),
                            "fin en barre d'action, écran fermé");
                    PhoneCallStatePacket fin = dernier(helper, p.l().etatsAppelRecus(), "fin, appelé");
                    helper.assertTrue(fin.phase() == Phase.NONE && fin.callId() == id[0], "fin reçue : " + fin);
                    essai.fermer();
                })
                .thenSucceed();
    }
}
