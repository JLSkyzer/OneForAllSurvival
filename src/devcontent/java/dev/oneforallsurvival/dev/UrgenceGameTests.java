package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.dev.AppelGameTests.Essai;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.call.CallBook;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.CallUpdates;
import dev.oneforallsurvival.phone.call.EmergencyLog;
import dev.oneforallsurvival.phone.call.EmergencyService;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket;
import dev.oneforallsurvival.phone.call.PhoneCallStatePacket.Phase;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les urgences 17 et 15 (feature 95, lot T3, tâche 8). Tout secours connecté du bon rôle reçoit chaque urgence : les
 * essais qui en ont sont chacun seuls dans leur lot (les essais d'un lot tournent ensemble), et rendent le rôle de
 * citoyen à leurs joueurs.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class UrgenceGameTests {

    /** Deux secours, le premier prend ; et les refus qui viennent avant tout secours. */
    private static final String LOT = "telephone_urgences";
    /** Un secours sans téléphone, le délai, le journal et la commande. */
    private static final String LOT_B = "telephone_urgences_b";
    /** Aucun secours connecté : seul dans son lot. */
    private static final String LOT_C = "telephone_urgences_c";
    /**
     * Relecture des urgences : un secours médecin (15) dans un essai, un policier (17) dans l'autre ; chaque urgence ne
     * sonne que chez son rôle, les deux essais ne se gênent pas.
     */
    private static final String LOT_D = "telephone_urgences_d";
    /** La batterie pendant la sonnerie : un policier, seul dans son lot. */
    private static final String LOT_E = "telephone_urgences_e";

    private static final String ALERT = "message.oneforallsurvival.call.emergency.alert";
    private static final List<UUID> ROLES = new ArrayList<>();

    private UrgenceGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        rendre(level.getServer());
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        rendre(level.getServer());
    }

    @AfterBatch(batch = LOT_C)
    public static void apresC(ServerLevel level) {
        rendre(level.getServer());
    }

    @AfterBatch(batch = LOT_D)
    public static void apresD(ServerLevel level) {
        rendre(level.getServer());
    }

    @AfterBatch(batch = LOT_E)
    public static void apresE(ServerLevel level) {
        rendre(level.getServer());
    }

    private static void rendre(MinecraftServer srv) {
        AppelGameTests.apres();
        RoleBook book = RoleSavedData.get(srv).book();
        for (UUID u : ROLES) {
            book.assign(u, RoleBook.DEFAULT_ROLE_ID);
        }
        ROLES.clear();
        EmergencyService.forgetCooldown();
    }

    private static void role(MinecraftServer srv, JoueurDeTest j, String role) {
        RoleSavedData.get(srv).book().assign(j.uuid(), role);
        ROLES.add(j.uuid());
    }

    /** Un téléphone neuf sans carte, chargé, tenu en main. */
    private static ItemStack sansCarte(JoueurDeTest j) {
        ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
        PhoneItem.ensurePhoneId(phone);
        PhoneItem.store(phone, PhoneConfig.capacity());
        j.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
        return j.joueur().getItemInHand(InteractionHand.MAIN_HAND);
    }

    private static UUID id(ItemStack phone) {
        return PhoneItem.phoneId(phone).orElseThrow();
    }

    private static String cle(Optional<net.minecraft.network.chat.Component> c) {
        return c.filter(m -> m.getContents() instanceof TranslatableContents)
                .map(m -> ((TranslatableContents) m.getContents()).getKey()).orElse("aucun");
    }

    private static void refus(GameTestHelper helper, CallService.Dial d, CallService.Outcome attendu, String cle,
            String cas) {
        helper.assertTrue(d.outcome() == attendu && d.callId() == 0 && cle(Optional.ofNullable(d.message()))
                .equals(cle), cas + " : " + attendu + " / " + cle + " attendus, vu " + d.outcome() + " / "
                        + cle(Optional.ofNullable(d.message())));
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_urgences
    // ---------------------------------------------------------------------------------------------

    /**
     * Sans carte, écran verrouillé, le 17 composé par paquet : deux secours sonnent (alerte au chat, position
     * affichée) ; le second qui répond ne prend pas ; l'autre lit « Pris en charge » ; l'appel tient deux relevés sans
     * carte ; raccroché verrouillé, la consommation revient à la veille ; le journal dit qui a répondu.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 400)
    public static void deuxSecoursLePremierPrend(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_pr_f");
        JoueurDeTest c = essai.joueur("ur_pr_c");
        JoueurDeTest s1 = essai.joueur("ur_pr_s1");
        JoueurDeTest s2 = essai.joueur("ur_pr_s2");
        PhoneOperator op = essai.fonder(f, AppelGameTests.A, "Ur pr Mairie");
        ItemStack phoneS1 = essai.ligne(op, s1);
        ItemStack phoneS2 = sansCarte(s2);
        ItemStack phoneC = sansCarte(c);
        role(srv, s1, "policier");
        role(srv, s2, "policier");
        AntennaLoad.Breakdown[] veille = new AntennaLoad.Breakdown[1];
        int[] urgence = new int[1];
        int[] appel = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    veille[0] = essai.conso(AppelGameTests.A);
                    helper.assertTrue(veille[0].traffic() == 0, "aucun trafic avant");
                    AppelPaquetsGameTests.codeEtFerme(helper, c);
                    AppelPaquetsGameTests.ouvrir(helper, c);
                    AppelPaquetsGameTests.vider(c, s1, s2);
                    AppelPaquetsGameTests.oublierFrein(helper, c.uuid());
                    AppelPaquetsGameTests.geste(helper, c, Action.DIAL, "17", "");
                    PhoneCallStatePacket sortant = AppelPaquetsGameTests.dernier(helper, c.etatsAppelRecus(),
                            "appelant");
                    helper.assertTrue(sortant.phase() == Phase.OUTGOING && sortant.number().equals("17")
                            && sortant.detail().isEmpty(), "appelant sans carte, verrouillé : " + sortant);
                    urgence[0] = sortant.callId();
                    for (JoueurDeTest s : new JoueurDeTest[] {s1, s2}) {
                        PhoneCallStatePacket entrant = AppelPaquetsGameTests.dernier(helper, s.etatsAppelRecus(),
                                "secours");
                        helper.assertTrue(entrant.phase() == Phase.INCOMING && entrant.callId() == urgence[0]
                                && entrant.number().equals("17") && entrant.name().equals("ur_pr_c")
                                && entrant.detail().contains("ur_pr_c"), "chez le secours : " + entrant + " "
                                        + entrant.detail());
                        helper.assertTrue(s.clesRecues().contains(ALERT), "alerte au chat du secours");
                    }
                    helper.assertTrue(!c.clesRecues().contains(ALERT), "l'appelant n'a pas l'alerte");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(AppelGameTests.A).traffic() == PhoneConfig.antennaCallFe(),
                            "trafic ouvert dès la sonnerie : " + essai.conso(AppelGameTests.A));
                    helper.assertTrue(EmergencyService.answer(s2.joueur(), id(phoneS2), urgence[0]),
                            "le premier secours prend");
                    helper.assertTrue(!EmergencyService.answer(s1.joueur(), id(phoneS1), urgence[0]),
                            "le second ne prend pas aussi");
                    Optional<Call> enLigne = CallService.callOf(c.joueur());
                    helper.assertTrue(enLigne.isPresent() && enLigne.get().state() == CallBook.State.ACTIVE
                            && enLigne.get().callee().player().equals(s2.uuid()), "en ligne avec le premier");
                    appel[0] = enLigne.get().id();
                    helper.assertTrue(CallService.callOf(s1.joueur()).isEmpty()
                            && !EmergencyService.involved(s1.uuid()), "l'autre secours est libéré");
                    PhoneCallStatePacket libere = AppelPaquetsGameTests.dernier(helper, s1.etatsAppelRecus(),
                            "secours libéré");
                    helper.assertTrue(libere.phase() == Phase.NONE && AppelPaquetsGameTests.cle(libere)
                            .equals("message.oneforallsurvival.call.emergency.taken"), "« Pris en charge » : "
                                    + libere + " " + AppelPaquetsGameTests.cle(libere));
                    PhoneCallStatePacket actif = AppelPaquetsGameTests.dernier(helper, s2.etatsAppelRecus(), "pris");
                    helper.assertTrue(actif.phase() == Phase.ACTIVE && actif.detail().contains("ur_pr_c"),
                            "position sous le titre, en ligne : " + actif);
                    PhoneCallStatePacket actifC = AppelPaquetsGameTests.dernier(helper, c.etatsAppelRecus(),
                            "appelant en ligne");
                    helper.assertTrue(actifC.phase() == Phase.ACTIVE && actifC.number().equals("17")
                            && actifC.detail().isEmpty(), "l'appelant voit le service, sans position : " + actifC);
                })
                // Deux relevés : l'urgence tient sans carte.
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(AppelGameTests.fin(appel[0]).isEmpty(), "l'urgence tient sans carte : "
                            + AppelGameTests.fin(appel[0]).map(AppelGameTests.Fin::reason));
                    helper.assertTrue(essai.conso(AppelGameTests.A).traffic() == PhoneConfig.antennaCallFe(),
                            "une antenne pour les deux bouts, comptée une fois : " + essai.conso(AppelGameTests.A));
                    AppelPaquetsGameTests.oublierFrein(helper, c.uuid());
                    AppelPaquetsGameTests.geste(helper, c, Action.HANGUP, Integer.toString(appel[0]), "");
                    AppelGameTests.finiPar(helper, appel[0], End.HUNG_UP);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(AppelGameTests.A).equals(veille[0]),
                            "consommation revenue à la veille : " + essai.conso(AppelGameTests.A) + " au lieu de "
                                    + veille[0]);
                    EmergencyLog.Entry e = EmergencyService.latest(srv).get(0);
                    helper.assertTrue(e.playerName().equals("ur_pr_c") && e.service().equals("17")
                            && e.answeredBy().equals("ur_pr_s2"), "journal : " + e);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Mode avion, puis aucune antenne à portée : refusés avant tout secours, sans délai consommé. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void enAvionEtSansAntenneRefuse(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest c = essai.joueur("ur_av_c");
        ItemStack phone = sansCarte(c);
        helper.startSequence()
                .thenExecute(() -> {
                    refus(helper, CallService.dial(c.joueur(), phone, true, "15"), CallService.Outcome.FAILED,
                            "message.oneforallsurvival.relay.caller_airplane", "mode avion");
                    // Loin de toute antenne (le relevé ne lit que le registre : aucun chunk n'est chargé).
                    c.joueur().teleportTo(c.joueur().getX() + 20_000, c.joueur().getY(), c.joueur().getZ());
                    refus(helper, CallService.dial(c.joueur(), phone, false, "17"), CallService.Outcome.FAILED,
                            "message.oneforallsurvival.relay.caller_no_signal", "aucune antenne");
                    PhoneItem.store(phone, 0);
                    refus(helper, CallService.dial(c.joueur(), phone, false, "17"), CallService.Outcome.FAILED,
                            "message.oneforallsurvival.phone.battery_empty", "batterie vide");
                    helper.assertTrue(!EmergencyService.involved(c.uuid()), "rien ne sonne");
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_urgences_b
    // ---------------------------------------------------------------------------------------------

    /**
     * Un secours sans téléphone est prévenu au chat ; l'appelant attend ; recomposer pendant l'attente : « Raccrochez
     * d'abord » ; raccroché, la consommation revient ; recomposer dans la minute : délai ; le frein des appels
     * composés n'y est pour rien ; le journal et {@code /phone urgences} (équipe seulement).
     */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void secoursSansTelephoneDelaiEtJournal(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_dl_f");
        JoueurDeTest c = essai.joueur("ur_dl_c");
        JoueurDeTest s = essai.joueur("ur_dl_s");
        essai.fonder(f, AppelGameTests.A, "Ur dl Mairie");
        ItemStack phone = sansCarte(c);
        s.joueur().getInventory().clearContent();
        role(srv, s, "medecin");
        AntennaLoad.Breakdown[] veille = new AntennaLoad.Breakdown[1];
        int[] urgence = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    veille[0] = essai.conso(AppelGameTests.A);
                    AppelPaquetsGameTests.vider(c, s);
                    // Le frein des appels composés est plein : l'urgence passe quand même.
                    for (int i = 0; i < PhoneConfig.callDialsPerMinute(); i++) {
                        CallService.dial(c.joueur(), phone, false, "0700000000");
                    }
                    CallService.Dial d = CallService.dial(c.joueur(), phone, false, "15");
                    helper.assertTrue(d.outcome() == CallService.Outcome.RINGING && d.callId() > 0,
                            "l'urgence passe malgré le frein : " + d.outcome() + " " + cle(Optional.ofNullable(
                                    d.message())));
                    urgence[0] = d.callId();
                    helper.assertTrue(s.clesRecues().contains(ALERT), "secours sans téléphone prévenu au chat");
                    helper.assertTrue(s.etatsAppelRecus().isEmpty(), "rien ne sonne chez lui");
                    refus(helper, CallService.dial(c.joueur(), phone, false, "17"), CallService.Outcome.FAILED,
                            "message.oneforallsurvival.call.hang_up_first", "déjà en urgence");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(AppelGameTests.A).traffic() == PhoneConfig.antennaCallFe(),
                            "trafic pendant l'attente : " + essai.conso(AppelGameTests.A));
                    helper.assertTrue(EmergencyService.hangUp(c.joueur(), id(phone), urgence[0]), "raccroche");
                    PhoneCallStatePacket fin = AppelPaquetsGameTests.dernier(helper, c.etatsAppelRecus(), "fin");
                    helper.assertTrue(fin.phase() == Phase.NONE && AppelPaquetsGameTests.cle(fin)
                            .equals(CallUpdates.ENDED + "hung_up"), "fin : " + fin);
                    CallService.Dial lent = CallService.dial(c.joueur(), phone, false, "15");
                    refus(helper, lent, CallService.Outcome.TOO_FAST,
                            "message.oneforallsurvival.call.emergency.cooldown", "dans la minute");
                    Object[] args = ((TranslatableContents) lent.message().getContents()).getArgs();
                    helper.assertTrue(args.length == 1 && String.valueOf(args[0])
                            .equals(String.valueOf(PhoneConfig.emergencyCooldownSeconds())),
                            "le délai dit ses secondes : " + java.util.Arrays.toString(args));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(AppelGameTests.A).equals(veille[0]),
                            "consommation revenue à la veille : " + essai.conso(AppelGameTests.A));
                    EmergencyLog.Entry e = EmergencyService.latest(srv).get(0);
                    helper.assertTrue(e.playerName().equals("ur_dl_c") && e.service().equals("15")
                            && e.answeredBy().isEmpty() && e.dimension().equals("minecraft:overworld"),
                            "journal : " + e);
                    int lignes;
                    try {
                        lignes = srv.getCommands().getDispatcher().execute("phone urgences",
                                srv.createCommandSourceStack().withSuppressedOutput());
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                        lignes = -1;
                    }
                    helper.assertTrue(lignes >= 1 && lignes <= EmergencyLog.SHOWN, "/phone urgences : " + lignes);
                    boolean refusee;
                    try {
                        srv.getCommands().getDispatcher().execute("phone urgences",
                                c.joueur().createCommandSourceStack().withPermission(0).withSuppressedOutput());
                        refusee = false;
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                        refusee = true;
                    }
                    helper.assertTrue(refusee, "/phone urgences refusée à un joueur sans permission");
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_urgences_c
    // ---------------------------------------------------------------------------------------------

    /** Aucun policier connecté : « Aucun service disponible » tout de suite, ni trafic, ni journal, ni délai. */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void aucunSecoursDisponible(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_ns_f");
        JoueurDeTest c = essai.joueur("ur_ns_c");
        essai.fonder(f, AppelGameTests.A, "Ur ns Mairie");
        ItemStack phone = sansCarte(c);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    AntennaLoad.Breakdown veille = essai.conso(AppelGameTests.A);
                    int journal = EmergencyService.latest(srv).size();
                    long avant = EmergencyService.latest(srv).stream().findFirst().map(EmergencyLog.Entry::seq)
                            .orElse(0L);
                    for (int i = 0; i < 2; i++) {
                        refus(helper, CallService.dial(c.joueur(), phone, false, "17"), CallService.Outcome.FAILED,
                                "message.oneforallsurvival.call.emergency.none", "aucun secours, essai " + i);
                    }
                    helper.assertTrue(!EmergencyService.involved(c.uuid()), "rien ne sonne");
                    helper.assertTrue(essai.conso(AppelGameTests.A).equals(veille), "aucun trafic");
                    long apres = EmergencyService.latest(srv).stream().findFirst().map(EmergencyLog.Entry::seq)
                            .orElse(0L);
                    helper.assertTrue(apres == avant && EmergencyService.latest(srv).size() == journal,
                            "rien au journal");
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_urgences_d (relecture)
    // ---------------------------------------------------------------------------------------------

    /**
     * Un médecin perd son rôle pendant la sonnerie : il ne peut plus prendre ; personne ne prend, la sonnerie s'écoule
     * et l'appelant lit « Aucune réponse » (clé propre aux urgences).
     */
    @GameTest(template = "test/plancher", batch = LOT_D, timeoutTicks = 1000)
    public static void rolePerduPuisAucuneReponse(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_rp_f");
        JoueurDeTest c = essai.joueur("ur_rp_c");
        JoueurDeTest s = essai.joueur("ur_rp_s");
        essai.fonder(f, AppelGameTests.A, "Ur rp Mairie");
        ItemStack phoneS = sansCarte(s);
        ItemStack phoneC = sansCarte(c);
        role(srv, s, "medecin");
        int[] urgence = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    AppelPaquetsGameTests.vider(c, s);
                    CallService.Dial d = CallService.dial(c.joueur(), phoneC, false, "15");
                    helper.assertTrue(d.outcome() == CallService.Outcome.RINGING, "l'urgence sonne : " + d.outcome());
                    urgence[0] = d.callId();
                    PhoneCallStatePacket entrant = AppelPaquetsGameTests.dernier(helper, s.etatsAppelRecus(),
                            "médecin");
                    helper.assertTrue(entrant.phase() == Phase.INCOMING && entrant.callId() == urgence[0],
                            "sonne chez le médecin : " + entrant);
                    RoleSavedData.get(srv).book().assign(s.uuid(), RoleBook.DEFAULT_ROLE_ID);
                    helper.assertTrue(!EmergencyService.answer(s.joueur(), id(phoneS), urgence[0]),
                            "sans le rôle, il ne prend plus l'appel");
                    helper.assertTrue(CallService.callOf(c.joueur()).isEmpty(), "personne en ligne");
                })
                .thenIdle(PhoneConfig.callRingSeconds() * 20 + 25)
                .thenExecute(() -> {
                    helper.assertTrue(!EmergencyService.involved(c.uuid()), "la sonnerie est finie");
                    PhoneCallStatePacket fin = AppelPaquetsGameTests.dernier(helper, c.etatsAppelRecus(), "fin");
                    helper.assertTrue(fin.phase() == Phase.NONE && AppelPaquetsGameTests.cle(fin)
                            .equals(EmergencyService.MESSAGE + "no_answer"), "« Aucune réponse » : " + fin + " "
                                    + AppelPaquetsGameTests.cle(fin));
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Le carnet refuse la prise (une copie du téléphone du policier est déjà en ligne chez un autre) : l'appelant lit
     * la fin au lieu de rester sur la sonnerie, et le journal ne dit pas que l'appel a été pris.
     */
    @GameTest(template = "test/plancher", batch = LOT_D, timeoutTicks = 300)
    public static void unePriseRefuseeFinitChezLAppelant(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_pf_f");
        JoueurDeTest c = essai.joueur("ur_pf_c");
        JoueurDeTest s = essai.joueur("ur_pf_s");
        JoueurDeTest x = essai.joueur("ur_pf_x");
        JoueurDeTest y = essai.joueur("ur_pf_y");
        PhoneOperator op = essai.fonder(f, AppelGameTests.A, "Ur pf Mairie");
        ItemStack phoneS = essai.ligne(op, s);
        ItemStack phoneX = essai.ligne(op, x);
        ItemStack phoneC = sansCarte(c);
        String numeroS = AppelGameTests.exiger(helper, dev.oneforallsurvival.phone.net.PhoneSims.line(srv, phoneS),
                "ligne du policier").number();
        // Une copie exacte du téléphone du policier chez Y, l'original rangé hors de l'inventaire le temps de l'appel.
        y.joueur().setItemInHand(InteractionHand.MAIN_HAND, phoneS.copy());
        s.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        role(srv, s, "policier");
        int[] appel = new int[1];
        int[] urgence = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    CallService.Dial d = CallService.dial(x.joueur(), phoneX, false, numeroS);
                    helper.assertTrue(d.outcome() == CallService.Outcome.RINGING, "l'appel de X sonne chez Y : "
                            + d.outcome());
                    appel[0] = d.callId();
                    helper.assertTrue(CallService.callOf(y.joueur()).isPresent(), "la copie de Y sonne");
                    s.joueur().setItemInHand(InteractionHand.MAIN_HAND, phoneS);
                    AppelPaquetsGameTests.vider(c, s);
                    int journal = EmergencyService.latest(srv).size();
                    CallService.Dial u = CallService.dial(c.joueur(), phoneC, false, "17");
                    helper.assertTrue(u.outcome() == CallService.Outcome.RINGING, "l'urgence sonne : " + u.outcome());
                    urgence[0] = u.callId();
                    PhoneCallStatePacket entrant = AppelPaquetsGameTests.dernier(helper, s.etatsAppelRecus(),
                            "policier");
                    helper.assertTrue(entrant.phase() == Phase.INCOMING && entrant.callId() == urgence[0],
                            "sonne chez le policier : " + entrant);
                    helper.assertTrue(!EmergencyService.answer(s.joueur(), id(phoneS), urgence[0]),
                            "le carnet refuse la prise");
                    PhoneCallStatePacket fin = AppelPaquetsGameTests.dernier(helper, c.etatsAppelRecus(), "appelant");
                    helper.assertTrue(fin.phase() == Phase.NONE && fin.callId() == urgence[0]
                            && AppelPaquetsGameTests.cle(fin).equals(CallUpdates.ENDED + "interrupted"),
                            "l'appelant lit la fin : " + fin + " " + AppelPaquetsGameTests.cle(fin));
                    helper.assertTrue(!EmergencyService.involved(c.uuid()) && CallService.callOf(c.joueur()).isEmpty(),
                            "plus rien ne sonne chez l'appelant");
                    EmergencyLog.Entry e = EmergencyService.latest(srv).get(0);
                    helper.assertTrue(EmergencyService.latest(srv).size() >= journal && e.answeredBy().isEmpty(),
                            "le journal ne dit pas l'appel pris : " + e);
                    helper.assertTrue(CallService.hangUp(x.joueur(), appel[0]), "X raccroche");
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_urgences_e : la batterie pendant la sonnerie
    // ---------------------------------------------------------------------------------------------

    /**
     * Pendant la sonnerie d'une urgence, comme pour un appel ordinaire : l'appelant écran fermé et le secours qui sonne
     * paient au rythme de l'écran ouvert ; un appelant écran ouvert ne paie qu'une fois. Un secours qui refuse et un
     * appelant qui raccroche ne paient plus ; à plat, l'urgence finit.
     */
    @GameTest(template = "test/plancher", batch = LOT_E, timeoutTicks = 400)
    public static void laSonnerieDUneUrgenceDepenseLaBatterie(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        MinecraftServer srv = essai.serveur();
        JoueurDeTest f = essai.joueur("ur_bt_f");
        JoueurDeTest ferme = essai.joueur("ur_bt_c");
        JoueurDeTest ouvert = essai.joueur("ur_bt_o");
        JoueurDeTest s = essai.joueur("ur_bt_s");
        essai.fonder(f, AppelGameTests.A, "Ur bt Mairie");
        ItemStack phoneS = sansCarte(s);
        ItemStack phoneF = sansCarte(ferme);
        ItemStack phoneO = sansCarte(ouvert);
        role(srv, s, "policier");
        int[] urgence = new int[2];
        int[] avant = new int[3];
        int fenetre = 60;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A), "antenne en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    CallService.Dial d = CallService.dial(ferme.joueur(), phoneF, false, "17");
                    helper.assertTrue(d.outcome() == CallService.Outcome.RINGING, "sonne : " + d.outcome());
                    urgence[0] = d.callId();
                    AppelPaquetsGameTests.ouvrir(helper, ouvert);
                    // Le policier sonne déjà : la seconde urgence attend sans sonner chez personne.
                    CallService.Dial e = CallService.dial(ouvert.joueur(), phoneO, false, "17");
                    helper.assertTrue(e.outcome() == CallService.Outcome.RINGING, "attend : " + e.outcome());
                    urgence[1] = e.callId();
                })
                // Un bloc de vingt tics payé d'avance : on mesure sur un nombre entier de blocs.
                .thenIdle(20)
                .thenExecute(() -> {
                    avant[0] = PhoneItem.stored(phoneF);
                    avant[1] = PhoneItem.stored(phoneO);
                    avant[2] = PhoneItem.stored(phoneS);
                })
                .thenIdle(fenetre)
                .thenExecute(() -> {
                    int attendu = PhoneConfig.drainPerTick() * fenetre;
                    int coutF = avant[0] - PhoneItem.stored(phoneF);
                    int coutO = avant[1] - PhoneItem.stored(phoneO);
                    int coutS = avant[2] - PhoneItem.stored(phoneS);
                    helper.assertTrue(coutF == attendu && coutO == attendu && coutS == attendu,
                            "sonnerie d'urgence, " + attendu + " attendus de chacun : appelant écran fermé " + coutF
                                    + ", appelant écran ouvert " + coutO + ", secours " + coutS);
                    helper.assertTrue(EmergencyService.decline(s.joueur(), id(phoneS), urgence[0]), "refuse");
                    helper.assertTrue(EmergencyService.hangUp(ouvert.joueur(), id(phoneO), urgence[1]), "raccroche");
                    AppelPaquetsGameTests.ecran(helper, ouvert, PhoneActionPacket.Action.CLOSE, "");
                    avant[1] = PhoneItem.stored(phoneO);
                    avant[2] = PhoneItem.stored(phoneS);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(PhoneItem.stored(phoneS) == avant[2], "après le refus, le secours ne paie plus : "
                            + (avant[2] - PhoneItem.stored(phoneS)));
                    helper.assertTrue(PhoneItem.stored(phoneO) == avant[1], "après avoir raccroché, plus rien : "
                            + (avant[1] - PhoneItem.stored(phoneO)));
                    AppelPaquetsGameTests.vider(ferme);
                    PhoneItem.store(phoneF, 1);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(PhoneItem.stored(phoneF) == 0 && !EmergencyService.involved(ferme.uuid()),
                            "à plat, l'urgence finit : reste " + PhoneItem.stored(phoneF));
                    helper.assertTrue(ferme.clesRecues().contains(CallUpdates.ENDED + "interrupted"),
                            "fin en barre d'action, écran fermé");
                    essai.fermer();
                })
                .thenSucceed();
    }
}
