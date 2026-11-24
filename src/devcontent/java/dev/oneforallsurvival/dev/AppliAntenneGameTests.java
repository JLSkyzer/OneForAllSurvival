package dev.oneforallsurvival.dev;

import static dev.oneforallsurvival.dev.PanneauOperateurGameTests.A;
import static dev.oneforallsurvival.dev.PanneauOperateurGameTests.B;
import static dev.oneforallsurvival.dev.PanneauOperateurGameTests.CODE;
import static dev.oneforallsurvival.dev.PanneauOperateurGameTests.MSG;
import static dev.oneforallsurvival.dev.PanneauOperateurGameTests.RESERVE;

import com.google.gson.JsonObject;
import dev.oneforallsurvival.dev.PanneauOperateurGameTests.Essai;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneService.Outcome;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.client.OperatorViewJson;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorActions;
import dev.oneforallsurvival.phone.net.OperatorNoticePacket;
import dev.oneforallsurvival.phone.net.OperatorPanels;
import dev.oneforallsurvival.phone.net.OperatorTillMenu;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'appli « Antenne » (lot T2, tâche 10) et les corrections de la revue de la tâche 9, côté serveur : les paquets sont
 * traités sur place ({@link JoueurDeTest#envoyer}) ou rejoués par {@link OperatorActions#apply}, seul jeu de règles.
 *
 * <ul>
 *   <li>Lot {@code appli_antenne} : l'appli reçoit la vue du téléphone ; une appli périmée (numéro ancien, téléphone
 *       fermé, mode avion, carte retirée, plus de barre) ne fait rien et se ferme ; un non-fondateur n'ouvre rien ; la
 *       caisse ne se vide jamais par l'appli.
 *   <li>Lot {@code appli_antenne_b} (revue de la tâche 9) : {@code CLOSE} avant le frein ; le frein de 2 tics ; un joueur
 *       mort n'émet rien ; une caisse qui ne s'ouvre pas laisse le panneau ; {@code found} refuse un chunk non chargé
 *       sans le charger ; un changement de dimension ferme le panneau ; {@code SET_MAIN} réussi ; {@code PAGE}.
 * </ul>
 *
 * Aides et rangement : ceux de {@link PanneauOperateurGameTests} (noms d'opérateurs propres à chaque essai ; essai rouge
 * rangé par l'{@code @AfterBatch} de son lot, joueurs déconnectés même après un échec).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AppliAntenneGameTests {

    private static final String LOT = "appli_antenne";
    private static final String LOT_B = "appli_antenne_b";

    private AppliAntenneGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        PanneauOperateurGameTests.apres();
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        OperatorActions.installTillOpener(null);
        PanneauOperateurGameTests.apres();
    }

    private static OperatorActionPacket.Action op(String nom) {
        return OperatorActionPacket.Action.valueOf(nom);
    }

    /** Les clés de langue des retours reçus sur la ligne d'état (sans les fermetures). */
    private static List<String> cles(List<OperatorNoticePacket> avis) {
        return avis.stream().filter(a -> !a.closed())
                .map(a -> a.message().getContents() instanceof TranslatableContents t ? t.getKey() : a.message().getString())
                .toList();
    }

    private static boolean fermeParLeServeur(List<OperatorNoticePacket> avis, int session) {
        return avis.stream().anyMatch(a -> a.closed() && a.session() == session);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot appli_antenne
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void lAppliRecoitLaVueDuTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("aa_vue");
        PhoneOperator op = e.fonder(f, A, "Appli Vue", RESERVE);
        e.ligne(op, f);
        e.carte(op, null);
        e.registre().deposit(op.id(), Coin.SILVER, 4);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(vues.size() == 1, "une vue à l'ouverture de l'appli : " + vues.size());
                    OperatorView v = vues.get(0);
                    helper.assertTrue(v.source() == OperatorView.Source.PHONE && v.operatorId().equals(op.id())
                            && v.pos().equals(BlockPos.ZERO) && v.rights().cards() && v.rights().tariff()
                            && !v.rights().till() && !v.rights().name(), "la vue du fondateur, du téléphone : " + v);
                    helper.assertTrue(OperatorPanels.session(f.uuid()).filter(s -> s == v.session()).isPresent(),
                            "le panneau de ce numéro est ouvert");
                    // Ce que la page en recevra : ni caisse ni position, le numéro formaté.
                    JsonObject json = OperatorViewJson.toJson(v);
                    helper.assertTrue(!json.has("till") && !json.has("pos") && !json.has("operatorId")
                            && !json.getAsJsonObject("rights").has("till"), "ni caisse ni position : " + json);
                    String numero = json.getAsJsonObject("cards").getAsJsonArray("items").get(0).getAsJsonObject()
                            .get("number").getAsString();
                    helper.assertTrue(numero.matches("0[67]( \\d\\d){4}"), "numéro formaté : " + numero);
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 600)
    public static void uneAppliPerimeeNeFaitRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("aa_perime");
        PhoneOperator op = e.fonder(f, A, "Appli Perime", RESERVE);
        ItemStack phone = e.ligne(op, f);
        SimCard c = e.carte(op, null);
        String cible = c.simId().toString();
        int[] derniere = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> premiere = e.appli(f, true);
                    List<OperatorView> seconde = e.appli(f, false);
                    helper.assertTrue(premiere.size() == 1 && seconde.size() == 1, "deux ouvertures de l'appli");
                    int ancien = premiere.get(0).session();
                    int courant = seconde.get(0).session();
                    // Un numéro ancien : rien.
                    e.geste(f, ancien, op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "numéro ancien : rien");
                    // Témoin : le numéro courant passe.
                    e.geste(f, courant, op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "témoin : suspendue");
                    e.geste(f, courant, op("REACTIVATE"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "témoin : réactivée");
                    // Téléphone fermé : rien, et le panneau se ferme (le client le sait).
                    f.avisOperateurRecus();
                    PhoneService.close(f.joueur(), CloseReason.CLOSED);
                    e.geste(f, courant, op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "téléphone fermé : rien");
                    helper.assertTrue(OperatorPanels.session(f.uuid()).isEmpty(), "téléphone fermé : panneau fermé");
                    helper.assertTrue(fermeParLeServeur(f.avisOperateurRecus(), courant),
                            "téléphone fermé : la fermeture est dite au client");
                    // Mode avion depuis l'ouverture : rien.
                    helper.assertTrue(PhoneService.open(f.joueur(), InteractionHand.MAIN_HAND) == Outcome.OPENED,
                            "réouverture du téléphone");
                    e.telephoneGeste(f, Action.UNLOCK, CODE, false);
                    int avion = e.appli(f, false).get(0).session();
                    e.telephoneGeste(f, Action.AIRPLANE, "", true);
                    e.geste(f, avion, op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE
                            && OperatorPanels.session(f.uuid()).isEmpty(), "mode avion : rien, panneau fermé");
                    e.telephoneGeste(f, Action.AIRPLANE, "", false);
                    // Carte retirée depuis l'ouverture : rien.
                    Optional<SimCard> ligne = PhoneSims.line(e.serveur(), phone);
                    helper.assertTrue(ligne.isPresent(), "la ligne du fondateur");
                    int retiree = e.appli(f, false).get(0).session();
                    e.telephoneGeste(f, Action.REMOVE_SIM, "", false);
                    e.geste(f, retiree, op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE
                            && OperatorPanels.session(f.uuid()).isEmpty(), "carte retirée : rien, panneau fermé");
                    int ou = PanneauOperateurGameTests.caseDe(f.joueur(), ligne.get().simId());
                    helper.assertTrue(ou >= 0, "la carte retirée est dans l'inventaire");
                    helper.assertTrue(PhoneSims.insert(f.joueur(), f.joueur().getItemInHand(InteractionHand.MAIN_HAND),
                            ou) == PhoneSims.Insert.DONE, "la carte se remet");
                    // Plus de barre depuis l'ouverture : l'antenne vidée.
                    List<OperatorView> avantPanne = e.appli(f, false);
                    helper.assertTrue(avantPanne.size() == 1, "appli rouverte avant la panne");
                    derniere[0] = avantPanne.get(0).session();
                    e.antenne(A).drainEnergy(Integer.MAX_VALUE);
                })
                .thenWaitUntil(() -> helper.assertTrue(!e.enService(A), "antenne hors service"))
                .thenExecute(() -> {
                    e.geste(f, derniere[0], op("SUSPEND"), cible, 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE
                            && OperatorPanels.session(f.uuid()).isEmpty(), "plus de barre : rien, panneau fermé");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void unNonFondateurNOuvrePasLAppli(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("aa_nonf");
        JoueurDeTest maire = e.maire("aa_nonf_m");
        PhoneOperator op = e.fonder(f, A, "Appli Non Fondateur", RESERVE);
        e.ligne(op, maire);
        e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    maire.etatsTelephoneRecus();
                    List<OperatorView> vues = e.appli(maire, true);
                    PhoneStatePacket reponse = e.telephoneGeste(maire, Action.OPEN_OPERATOR_APP, "", false);
                    helper.assertTrue(vues.isEmpty() && OperatorPanels.session(maire.uuid()).isEmpty(),
                            "le maire, même avec une carte de cet opérateur, n'ouvre pas l'appli : " + vues);
                    helper.assertTrue(reponse != null && reponse.last() == Result.NO_NETWORK,
                            "le maire : refus : " + (reponse == null ? "aucune réponse" : reponse.last()));
                    // Témoin : le fondateur l'ouvre.
                    helper.assertTrue(e.appli(f, true).size() == 1, "témoin : le fondateur ouvre l'appli");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 300)
    public static void laCaisseNeSeVideJamaisParLAppli(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("aa_caisse");
        PhoneOperator op = e.fonder(f, A, "Appli Caisse", RESERVE);
        e.registre().deposit(op.id(), Coin.GOLD, 2);
        e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(vues.size() == 1 && !vues.get(0).rights().till(), "pas de caisse dans l'appli");
                    int s = vues.get(0).session();
                    f.avisOperateurRecus();
                    f.messagesRecus();
                    OperatorActions.clearThrottle(f.uuid());
                    OperatorActions.Result r = OperatorActions.apply(f.joueur(),
                            new OperatorActionPacket(s, op("OPEN_TILL"), "", 0L));
                    helper.assertTrue(r == OperatorActions.Result.REFUSED, "OPEN_TILL depuis le téléphone : " + r);
                    helper.assertTrue(!(f.joueur().containerMenu instanceof OperatorTillMenu), "aucun écran de caisse");
                    helper.assertTrue(e.registre().till(op.id()).getOrDefault(Coin.GOLD, 0L) == 2L,
                            "la caisse n'a pas bougé");
                    helper.assertTrue(OperatorPanels.session(f.uuid()).filter(x -> x == s).isPresent(),
                            "le panneau reste ouvert");
                    // Le retour se lit sur la ligne d'état de l'appli (revue de la tâche 9, mineur 2).
                    List<String> retours = cles(f.avisOperateurRecus());
                    helper.assertTrue(retours.contains(MSG + "till_main_only"),
                            "le refus se lit sur la ligne d'état : " + retours);
                    e.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot appli_antenne_b : revue de la tâche 9
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unCloseJusteApresUnGesteFermeLePanneau(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_close");
        PhoneOperator op = e.fonder(f, A, "Revue Close", RESERVE);
        SimCard c = e.carte(op, null);
        OperatorView vue = e.ouvrir(f, A);
        OperatorActions.clearThrottle(f.uuid());
        OperatorActions.Result geste = OperatorActions.apply(f.joueur(),
                new OperatorActionPacket(vue.session(), op("SUSPEND"), c.simId().toString(), 0L));
        // Même tic : Échap.
        OperatorActions.Result ferme = OperatorActions.apply(f.joueur(),
                new OperatorActionPacket(vue.session(), op("CLOSE"), "", 0L));
        helper.assertTrue(geste == OperatorActions.Result.DONE, "le geste : " + geste);
        helper.assertTrue(ferme == OperatorActions.Result.CLOSED, "CLOSE dans le même tic : " + ferme);
        helper.assertTrue(OperatorPanels.session(f.uuid()).isEmpty(), "le panneau est fermé côté serveur");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 100)
    public static void leFreinRefuseUnGesteImmediatPuisLAccepte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_frein");
        PhoneOperator op = e.fonder(f, A, "Revue Frein", RESERVE);
        SimCard c = e.carte(op, null);
        int[] session = {-1};
        helper.startSequence()
                .thenExecute(() -> {
                    session[0] = e.ouvrir(f, A).session();
                    OperatorActions.clearThrottle(f.uuid());
                    OperatorActions.Result r1 = OperatorActions.apply(f.joueur(),
                            new OperatorActionPacket(session[0], op("SUSPEND"), c.simId().toString(), 0L));
                    OperatorActions.Result r2 = OperatorActions.apply(f.joueur(),
                            new OperatorActionPacket(session[0], op("REACTIVATE"), c.simId().toString(), 0L));
                    helper.assertTrue(r1 == OperatorActions.Result.DONE, "premier geste : " + r1);
                    helper.assertTrue(r2 == OperatorActions.Result.THROTTLED, "geste immédiat : " + r2);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "le geste freiné n'a rien fait");
                })
                .thenIdle(OperatorActions.MIN_TICKS_BETWEEN)
                .thenExecute(() -> {
                    OperatorActions.Result r3 = OperatorActions.apply(f.joueur(),
                            new OperatorActionPacket(session[0], op("REACTIVATE"), c.simId().toString(), 0L));
                    helper.assertTrue(r3 == OperatorActions.Result.DONE, "après 2 tics : " + r3);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "après 2 tics : réactivée");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unJoueurMortNEmetRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_mort");
        PhoneOperator op = e.fonder(f, A, "Revue Mort", RESERVE);
        OperatorView vue = e.ouvrir(f, A);
        ServerPlayer p = f.joueur();
        float vie = p.getHealth();
        p.setHealth(0f);
        boolean mort = p.isDeadOrDying();
        OperatorActions.clearThrottle(f.uuid());
        OperatorActions.Result r = OperatorActions.apply(p,
                new OperatorActionPacket(vue.session(), op("ISSUE_ANONYMOUS"), "", 0L));
        p.setHealth(vie);
        helper.assertTrue(mort, "témoin : le joueur est mort (vie " + p.getHealth() + ")");
        helper.assertTrue(r == OperatorActions.Result.GONE, "un mort : " + r);
        helper.assertTrue(e.registre().cardsOf(op.id()).isEmpty()
                && PanneauOperateurGameTests.cartesPortees(p) == 0, "aucune carte émise dans l'inventaire d'un mort");
        // Témoin : vivant, la carte s'émet.
        OperatorActions.clearThrottle(f.uuid());
        OperatorActions.Result vivant = OperatorActions.apply(p,
                new OperatorActionPacket(vue.session(), op("ISSUE_ANONYMOUS"), "", 0L));
        helper.assertTrue(vivant == OperatorActions.Result.DONE && e.registre().cardsOf(op.id()).size() == 1,
                "témoin : vivant, une carte : " + vivant);
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void uneCaisseQuiNeSOuvrePasLaisseLePanneau(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_caisse");
        PhoneOperator op = e.fonder(f, A, "Revue Caisse", RESERVE);
        OperatorView vue = e.ouvrir(f, A);
        helper.assertTrue(vue.rights().till(), "témoin : la caisse de l'antenne principale");
        f.avisOperateurRecus();
        f.messagesRecus();
        OperatorActions.Result r;
        OperatorActions.installTillOpener((joueur, pos, o) -> false);
        try {
            OperatorActions.clearThrottle(f.uuid());
            r = OperatorActions.apply(f.joueur(), new OperatorActionPacket(vue.session(), op("OPEN_TILL"), "", 0L));
        } finally {
            OperatorActions.installTillOpener(null);
        }
        helper.assertTrue(r == OperatorActions.Result.REFUSED, "caisse qui ne s'ouvre pas : " + r);
        helper.assertTrue(OperatorPanels.session(f.uuid()).filter(s -> s == vue.session()).isPresent(),
                "le panneau reste ouvert");
        helper.assertTrue(f.messagesRecus().contains(MSG + "till_failed"), "le joueur est prévenu");
        List<OperatorNoticePacket> avis = f.avisOperateurRecus();
        helper.assertTrue(cles(avis).contains(MSG + "till_failed") && !fermeParLeServeur(avis, vue.session()),
                "sur la ligne d'état, sans fermeture : " + cles(avis));
        // Témoin : la vraie caisse s'ouvre, le panneau se ferme et le client le sait.
        OperatorActions.clearThrottle(f.uuid());
        OperatorActions.Result ouverte = OperatorActions.apply(f.joueur(),
                new OperatorActionPacket(vue.session(), op("OPEN_TILL"), "", 0L));
        helper.assertTrue(ouverte == OperatorActions.Result.CLOSED
                && f.joueur().containerMenu instanceof OperatorTillMenu, "témoin : la caisse s'ouvre : " + ouverte);
        helper.assertTrue(fermeParLeServeur(f.avisOperateurRecus(), vue.session()),
                "témoin : la fermeture du panneau est dite au client");
        f.joueur().closeContainer();
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void foundRefuseUnChunkNonCharge(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_chunk");
        ServerLevel level = helper.getLevel();
        // Loin de tout : la dernière rangée d'un chunk à un million de blocs, aucun joueur ni bloc de mod autour, une
        // frontière de chunk de chaque côté (leçons du 2026-11-11 et du 2026-11-19).
        BlockPos loin = new BlockPos(1_000_015, 64, 1_000_015);
        helper.assertTrue(level.getChunkSource().getChunkNow(loin.getX() >> 4, loin.getZ() >> 4) == null,
                "le chunk visé doit être déchargé au départ");
        String nom = "Revue Hors Chunk";
        e.registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(e.serveur(), reste.founder()));
        helper.assertTrue(AntennaBlock.found(f.joueur(), loin, nom).isEmpty(), "found refuse un chunk non chargé");
        helper.assertTrue(level.getChunkSource().getChunkNow(loin.getX() >> 4, loin.getZ() >> 4) == null,
                "found n'a pas chargé le chunk");
        helper.assertTrue(e.registre().byName(nom).isEmpty() && e.registre().byFounder(f.uuid()).isEmpty(),
                "aucun opérateur fondé");
        // Témoin : une antenne chargée, à côté, se fonde.
        PhoneOperator op = e.fonder(f, A, "Revue Chunk Temoin", RESERVE);
        helper.assertTrue(op != null && e.registre().byFounder(f.uuid()).isPresent(), "témoin : fondée");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unChangementDeDimensionFermeLePanneau(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_dimension");
        PhoneOperator op = e.fonder(f, A, "Revue Dimension", RESERVE);
        SimCard c = e.carte(op, null);
        OperatorView vue = e.ouvrir(f, A);
        ServerPlayer p = f.joueur();
        ServerLevel ici = helper.getLevel();
        ServerLevel nether = e.serveur().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "le Nether existe");
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        p.teleportTo(nether, x, 100, z, p.getYRot(), p.getXRot());
        OperatorActions.Result r;
        boolean ailleurs = p.level().dimension() == Level.NETHER;
        OperatorActions.clearThrottle(f.uuid());
        r = OperatorActions.apply(p, new OperatorActionPacket(vue.session(), op("SUSPEND"), c.simId().toString(), 0L));
        p.teleportTo(ici, x, y, z, p.getYRot(), p.getXRot());
        helper.assertTrue(ailleurs, "témoin : le joueur était dans le Nether");
        helper.assertTrue(r == OperatorActions.Result.CLOSED, "dans une autre dimension : " + r);
        helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE && OperatorPanels.session(f.uuid()).isEmpty(),
                "rien de fait, panneau fermé");
        // Témoin : revenu, le panneau rouvert, le geste passe.
        OperatorView pres = e.ouvrir(f, A);
        e.geste(f, pres.session(), op("SUSPEND"), c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "témoin : suspendue");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void designerLAntennePrincipale(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_principale");
        PhoneOperator op = e.fonder(f, A, "Revue Principale", RESERVE);
        e.poser(f, B);
        e.verser(B, RESERVE);
        UUID b = e.antenne(B).antennaId();
        UUID a = e.antenne(A).antennaId();
        helper.assertTrue(b != null && op.id().equals(e.antenne(B).operatorId()) && a.equals(e.op(op).mainAntenna()),
                "départ : A principale, B de l'opérateur");
        OperatorView surB = e.ouvrir(f, B);
        helper.assertTrue(!surB.rights().till(), "sur B, pas encore de caisse");
        OperatorView apres = e.geste(f, surB.session(), op("SET_MAIN"), b.toString(), 0L);
        helper.assertTrue(b.equals(e.op(op).mainAntenna()), "B devient l'antenne principale");
        helper.assertTrue(apres != null && apres.rights().till() && apres.antennas().stream()
                .anyMatch(l -> l.id().equals(b) && l.main()), "la vue suit, la caisse aussi : " + apres);
        helper.assertTrue(!e.ouvrir(f, A).rights().till(), "sur A, plus de caisse");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lesPagesDesCartes(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ab_pages");
        PhoneOperator op = e.fonder(f, A, "Revue Pages", RESERVE);
        for (int i = 0; i <= OperatorView.MAX_LIST; i++) {
            e.carte(op, null);
        }
        OperatorView vue = e.ouvrir(f, A);
        helper.assertTrue(vue.cardPages() == 2 && vue.cardPage() == 0 && vue.cards().size() == OperatorView.MAX_LIST
                && vue.cardTotal() == OperatorView.MAX_LIST + 1, "deux pages : " + vue.cardPage() + "/"
                + vue.cardPages() + " " + vue.cards().size());
        OperatorView seconde = e.geste(f, vue.session(), op("PAGE"), "", 1L);
        helper.assertTrue(seconde != null && seconde.cardPage() == 1 && seconde.cards().size() == 1,
                "la seconde page : " + (seconde == null ? "aucune vue" : seconde.cardPage() + " " + seconde.cards()));
        for (long hors : new long[] {2L, -1L, 64L}) {
            OperatorActions.clearThrottle(f.uuid());
            OperatorActions.Result r = OperatorActions.apply(f.joueur(),
                    new OperatorActionPacket(vue.session(), op("PAGE"), "", hors));
            helper.assertTrue(r == OperatorActions.Result.STALE, "page " + hors + " : " + r);
        }
        OperatorView retour = e.geste(f, vue.session(), op("PAGE"), "", 0L);
        helper.assertTrue(retour != null && retour.cardPage() == 0, "retour à la première page");
        e.fermer();
        helper.succeed();
    }
}
