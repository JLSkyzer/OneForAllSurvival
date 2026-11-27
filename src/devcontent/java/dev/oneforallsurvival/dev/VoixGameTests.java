package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.dev.AppelGameTests.Essai;
import dev.oneforallsurvival.dev.AppelGameTests.Paire;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.voice.VoiceCodec;
import dev.oneforallsurvival.phone.voice.VoiceRouter;
import dev.oneforallsurvival.phone.voice.VoiceRoutes;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Flow;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.FlowKey;
import dev.oneforallsurvival.phone.voice.VoiceRoutes.Kind;
import dev.oneforallsurvival.phone.voice.VoiceService;
import dev.oneforallsurvival.phone.voice.VoiceSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La voix des appels (lot T3, tâches 5 et 6), sans client ni micro : l'instantané des routes calculé sur un vrai
 * serveur suit le décroché, le silence, le haut-parleur, les distances et la fin de l'appel ; une trame injectée dans
 * un routeur d'essai n'arrive qu'au bon auditeur. La voix réelle entre deux clients n'est pas jouée ici.
 *
 * <p>Deux joueurs d'essai en appel (M appelle L, à environ sept blocs l'un de l'autre, chacun sur son antenne) et des
 * voisins posés autour de M. Les essais d'appel ({@link AppelGameTests}) fournissent opérateurs, cartes et téléphones.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class VoixGameTests {

    private static final String LOT = "telephone_voix";
    private static final Predicate<UUID> AUCUN_GROUPE = id -> false;

    /** Le puits branché avant l'essai du tic, rebranché par l'{@code @AfterBatch} même après un échec. */
    private static Optional<VoiceSink> aRebrancher;

    private VoixGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        AppelGameTests.apres();
        rebrancher();
    }

    private static void rebrancher() {
        if (aRebrancher != null) {
            aRebrancher.ifPresentOrElse(VoiceService::attach, VoiceService::detach);
            aRebrancher = null;
        }
    }

    /** Puits d'essai : enregistre les trames et les fermetures, codec qui rend la trame telle quelle. */
    static final class Puits implements VoiceSink {
        record Trame(FlowKey key, int taille) {}

        final List<Trame> trames = new ArrayList<>();
        final List<FlowKey> fermes = new ArrayList<>();

        @Override
        public synchronized void send(Flow flow, byte[] opus) {
            trames.add(new Trame(flow.key(), opus.length));
        }

        @Override
        public void flush(Flow flow) {}

        @Override
        public synchronized void close(FlowKey key) {
            fermes.add(key);
        }

        @Override
        public VoiceCodec codec() {
            return new VoiceCodec() {
                @Override
                public short[] decode(byte[] opus) {
                    return new short[opus.length];
                }

                @Override
                public byte[] encode(short[] pcm) {
                    return new byte[pcm.length];
                }

                @Override
                public void reset() {}

                @Override
                public void close() {}
            };
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Compose de M vers L ; l'appel doit sonner. */
    private static int sonner(GameTestHelper helper, Paire p) {
        CallService.Dial d = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number());
        helper.assertTrue(d.outcome() == CallService.Outcome.RINGING && d.callId() > 0, "l'appel devait sonner : "
                + d.outcome());
        return d.callId();
    }

    private static VoiceRoutes routes(Paire p) {
        return VoiceService.compute(p.essai().serveur(), AUCUN_GROUPE);
    }

    /** Les auditeurs privés de la voix de {@code parleur} dans l'appel {@code id}. */
    private static Set<UUID> prives(VoiceRoutes r, UUID parleur, int id) {
        return r.from(parleur).stream().filter(f -> f.key().callId() == id && f.key().kind() == Kind.PRIVATE)
                .map(f -> f.key().listener()).collect(Collectors.toSet());
    }

    private static List<Flow> flux(VoiceRoutes r, UUID parleur, int id) {
        return r.from(parleur).stream().filter(f -> f.key().callId() == id).toList();
    }

    /** Un voisin de M, à {@code dx} blocs de lui sur l'axe x (même hauteur). */
    private static JoueurDeTest voisin(Essai essai, Paire p, String nom, double dx) {
        JoueurDeTest j = essai.joueur(nom);
        ServerPlayer m = p.m().joueur();
        j.joueur().teleportTo(m.getX() + dx, m.getY(), m.getZ());
        return j;
    }

    private static void decroche(GameTestHelper helper, Paire p, int id) {
        helper.assertTrue(CallService.answer(p.l().joueur(), id), "L décroche");
    }

    // ---------------------------------------------------------------------------------------------
    // Essais
    // ---------------------------------------------------------------------------------------------

    /** Sonnerie : aucun flux. Décroché : chacun entend l'autre en privé. Silence de M : sa voix ne part plus. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void lePriveSuitLeDecrocheEtLeSilence(GameTestHelper helper) {
        Paire p = AppelGameTests.paire(helper, "vx_prive");
        Essai essai = p.essai();
        UUID m = p.m().uuid();
        UUID l = p.l().uuid();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p);
                    VoiceRoutes r = routes(p);
                    helper.assertTrue(flux(r, m, id[0]).isEmpty() && flux(r, l, id[0]).isEmpty(),
                            "en sonnerie, aucun flux : " + r);
                    decroche(helper, p, id[0]);
                    r = routes(p);
                    helper.assertTrue(prives(r, m, id[0]).equals(Set.of(l)), "M part à L seul : " + r.from(m));
                    helper.assertTrue(prives(r, l, id[0]).equals(Set.of(m)), "L part à M seul : " + r.from(l));
                    helper.assertTrue(r.from(m).stream().noneMatch(Flow::crackle), "chacun sur son antenne : net");
                    helper.assertTrue(CallService.setMuted(p.m().joueur(), true).isPresent(), "silence de M");
                    r = routes(p);
                    helper.assertTrue(flux(r, m, id[0]).isEmpty(), "M en silence : sa voix ne part pas : "
                            + r.from(m));
                    helper.assertTrue(prives(r, l, id[0]).equals(Set.of(m)), "M entend toujours L");
                    CallService.setMuted(p.m().joueur(), false);
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "M raccroche");
                    r = routes(p);
                    helper.assertTrue(flux(r, m, id[0]).isEmpty() && flux(r, l, id[0]).isEmpty(),
                            "appel fini : aucun flux");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Haut-parleur de M : la voix de L sort de son téléphone, posée sur M, entendue par un voisin à trois blocs et
     * jamais par L (qui est pourtant à sept blocs) ; M ne la reçoit plus en privé ; le voisin part à L.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void leHautParleurSortDuTelephoneSansJamaisAtteindreLAutre(GameTestHelper helper) {
        Paire p = AppelGameTests.paire(helper, "vx_haut");
        Essai essai = p.essai();
        UUID m = p.m().uuid();
        UUID l = p.l().uuid();
        int[] id = new int[1];
        JoueurDeTest[] c = new JoueurDeTest[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p);
                    decroche(helper, p, id[0]);
                    c[0] = voisin(essai, p, "vx_haut_c", 3);
                    helper.assertTrue(CallService.setSpeaker(p.m().joueur(), true).isPresent(), "haut-parleur de M");
                    VoiceRoutes r = routes(p);
                    List<Flow> deL = flux(r, l, id[0]);
                    helper.assertTrue(deL.size() == 1 && deL.get(0).key().kind() == Kind.SPEAKER
                            && deL.get(0).key().listener().equals(m), "L ne sort plus que du haut-parleur de M : "
                            + deL);
                    Flow f = deL.get(0);
                    helper.assertTrue(f.hearBlocks() == PhoneConfig.speakerHearBlocks(), "portée " + f.hearBlocks());
                    helper.assertTrue(p.l().joueur().distanceTo(p.m().joueur()) < f.hearBlocks(),
                            "L est à portée du haut-parleur de M");
                    helper.assertTrue(!r.hears(f, l), "L ne s'entend jamais dans le haut-parleur de M");
                    helper.assertTrue(r.hears(f, m), "M entend L par son haut-parleur");
                    helper.assertTrue(r.hears(f, c[0].uuid()), "le voisin entend L");
                    helper.assertTrue(prives(r, c[0].uuid(), id[0]).equals(Set.of(l)), "le voisin part à L : "
                            + r.from(c[0].uuid()));
                    helper.assertTrue(prives(r, m, id[0]).equals(Set.of(l)), "M part toujours à L en privé");
                    helper.assertTrue(CallService.setMuted(p.m().joueur(), true).isPresent(), "silence de M");
                    r = routes(p);
                    helper.assertTrue(flux(r, c[0].uuid(), id[0]).isEmpty() && flux(r, m, id[0]).isEmpty(),
                            "M en silence : ni lui ni ce qui l'entoure ne part à L");
                    helper.assertTrue(flux(r, l, id[0]).size() == 1, "M entend toujours L par le haut-parleur");
                    CallService.hangUp(p.m().joueur(), id[0]);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Le haut-parleur capte jusqu'à 16 blocs et s'entend jusqu'à 8, bornes comprises ; un groupe fermé n'est pas capté. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void lesDistancesDuHautParleurSontAuxBornes(GameTestHelper helper) {
        Paire p = AppelGameTests.paire(helper, "vx_bornes");
        Essai essai = p.essai();
        UUID l = p.l().uuid();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p);
                    decroche(helper, p, id[0]);
                    int capte = PhoneConfig.speakerCaptureBlocks();
                    int entend = PhoneConfig.speakerHearBlocks();
                    JoueurDeTest c16 = voisin(essai, p, "vx_bornes_c16", -capte);
                    JoueurDeTest d16 = voisin(essai, p, "vx_bornes_d16", -(capte + 0.5));
                    JoueurDeTest e8 = voisin(essai, p, "vx_bornes_e8", -entend);
                    JoueurDeTest f8 = voisin(essai, p, "vx_bornes_f8", -(entend + 0.5));
                    CallService.setSpeaker(p.m().joueur(), true);
                    VoiceRoutes r = routes(p);
                    helper.assertTrue(prives(r, c16.uuid(), id[0]).equals(Set.of(l)), "à " + capte
                            + " blocs : capté");
                    helper.assertTrue(flux(r, d16.uuid(), id[0]).isEmpty(), "au-delà de " + capte + " : non capté");
                    Flow f = flux(r, l, id[0]).get(0);
                    helper.assertTrue(r.hears(f, e8.uuid()), "à " + entend + " blocs : entend le haut-parleur");
                    helper.assertTrue(!r.hears(f, f8.uuid()), "au-delà de " + entend + " : n'entend pas");
                    VoiceRoutes ferme = VoiceService.compute(essai.serveur(), c16.uuid()::equals);
                    helper.assertTrue(flux(ferme, c16.uuid(), id[0]).isEmpty(), "groupe fermé : non capté");
                    JoueurDeTest spectateur = voisin(essai, p, "vx_bornes_spec", -(capte - 2));
                    spectateur.joueur().setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    helper.assertTrue(flux(routes(p), spectateur.uuid(), id[0]).isEmpty(),
                            "un spectateur à portée n'est pas capté");
                    CallService.hangUp(p.m().joueur(), id[0]);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Une trame injectée dans un routeur d'essai n'arrive qu'au bon auditeur, en privé comme en haut-parleur. */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void uneTrameInjecteeNArriveQuAuBonAuditeur(GameTestHelper helper) {
        Paire p = AppelGameTests.paire(helper, "vx_trame");
        Essai essai = p.essai();
        UUID m = p.m().uuid();
        UUID l = p.l().uuid();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p);
                    decroche(helper, p, id[0]);
                    JoueurDeTest c = voisin(essai, p, "vx_trame_c", 40);
                    Puits puits = new Puits();
                    VoiceRouter router = new VoiceRouter(puits, 5L);
                    router.publish(routes(p));
                    router.microphone(m, new byte[] {1, 2, 3});
                    router.microphone(c.uuid(), new byte[] {4, 5});
                    List<FlowKey> recus = puits.trames.stream().map(Puits.Trame::key)
                            .filter(k -> k.callId() == id[0]).toList();
                    helper.assertTrue(recus.equals(List.of(new FlowKey(id[0], m, l, Kind.PRIVATE))),
                            "la trame de M ne va qu'à L, celle d'un passant nulle part : " + recus);
                    CallService.setSpeaker(p.l().joueur(), true);
                    router.publish(routes(p));
                    helper.assertTrue(puits.fermes.contains(new FlowKey(id[0], m, l, Kind.PRIVATE)),
                            "le flux privé disparu est fermé : " + puits.fermes);
                    router.microphone(m, new byte[] {1, 2, 3});
                    FlowKey dernier = puits.trames.get(puits.trames.size() - 1).key();
                    helper.assertTrue(dernier.equals(new FlowKey(id[0], m, l, Kind.SPEAKER)),
                            "haut-parleur de L : la voix de M sort de son téléphone : " + dernier);
                    router.stop();
                    CallService.hangUp(p.m().joueur(), id[0]);
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Le tic publie l'instantané au routeur branché : il suit le décroché et le silence au tic suivant, et se vide à la
     * fin de l'appel (canal fermé). Le puits réel, s'il y en avait un, est rebranché à la fin.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 200)
    public static void laPublicationDuTicSuitLAppel(GameTestHelper helper) {
        Paire p = AppelGameTests.paire(helper, "vx_tic");
        Essai essai = p.essai();
        UUID m = p.m().uuid();
        UUID l = p.l().uuid();
        int[] id = new int[1];
        Puits puits = new Puits();
        VoiceRouter[] router = new VoiceRouter[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(AppelGameTests.A)
                        && essai.enService(AppelGameTests.B), "antennes en service"))
                .thenExecute(() -> {
                    aRebrancher = VoiceService.router().map(VoiceRouter::sink);
                    router[0] = VoiceService.attach(puits);
                    id[0] = sonner(helper, p);
                    decroche(helper, p, id[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(prives(router[0].current(), m, id[0]).equals(Set.of(l)),
                            "décroché publié au tic suivant : " + router[0].current().from(m));
                    router[0].microphone(m, new byte[] {9});
                    CallService.setMuted(p.m().joueur(), true);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(flux(router[0].current(), m, id[0]).isEmpty(), "silence publié");
                    helper.assertTrue(puits.fermes.contains(new FlowKey(id[0], m, l, Kind.PRIVATE)),
                            "le flux de M, coupé par le silence, est fermé");
                    p.l().quitter();
                })
                .thenWaitUntil(() -> helper.assertTrue(AppelGameTests.fin(id[0]).isPresent(), "l'appel finit"))
                .thenIdle(2)
                .thenExecute(() -> {
                    AppelGameTests.finiPar(helper, id[0], End.PARTY_LEFT);
                    VoiceRoutes r = router[0].current();
                    helper.assertTrue(flux(r, m, id[0]).isEmpty() && flux(r, l, id[0]).isEmpty(),
                            "L parti : plus aucune route de cet appel");
                    rebrancher();
                    essai.fermer();
                })
                .thenSucceed();
    }
}
