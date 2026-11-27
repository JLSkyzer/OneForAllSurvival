package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.call.CallBook;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.End;
import dev.oneforallsurvival.phone.call.CallListener;
import dev.oneforallsurvival.phone.call.CallService;
import dev.oneforallsurvival.phone.call.PhoneDirectory;
import dev.oneforallsurvival.phone.call.Recent;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les appels côté serveur (lot T3, tâche 3) : sonnerie, décroché, raccroché, et tout ce qui coupe un appel ; le trafic
 * des antennes revient à ce qu'il était avant l'appel. Aucune voix, aucun écran.
 *
 * <p>Chaque essai fonde ses propres opérateurs sous des noms qui lui sont propres. Dans les séquences, aucune exception
 * autre qu'une assertion (leçon du 2026-11-20) ; l'essai se range à sa dernière étape, et l'{@code @AfterBatch} fait
 * partir les joueurs restés (ce qui finit leurs appels et ferme leur trafic).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AppelGameTests {

    private static final String LOT_A = "telephone_appels_a";
    private static final String LOT_B = "telephone_appels_b";
    /**
     * L'arrêt du serveur finit <b>tous</b> les appels : son essai est seul dans son lot, sinon il couperait ceux des
     * essais qui tournent avec lui.
     */
    private static final String LOT_ARRET = "telephone_appels_arret";
    /** Tour 1 de la tâche 3 : frein gardé, réentrance, mort, mode avion et batterie en cours d'appel. */
    private static final String LOT_C = "telephone_appels_c";
    /** Les deux essais qui passent la sonnerie entière (plus de 600 tics), joués ensemble. */
    private static final String LOT_LONGS = "telephone_appels_longs";

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(6, 0, 6);
    private static final BlockPos C = new BlockPos(6, 0, 1);
    private static final Vec3 JOUEUR = new Vec3(3.5, 0, 3.5);
    private static final int RESERVE = 40_000;

    /** Une fin d'appel vue par l'écouteur d'essai. */
    private record Fin(Call call, End reason, int tick) {}

    /** Les fins et les changements vus depuis le démarrage du serveur d'essai ; fil du serveur. */
    private static final List<Fin> FINS = new ArrayList<>();
    private static final List<Call> CHANGES = new ArrayList<>();
    private static boolean ecoute;

    /** Les joueurs ouverts par le lot en cours, rendus par l'{@code @AfterBatch} même après un échec. */
    private static final List<JoueurDeTest> OUVERTS = new ArrayList<>();

    private AppelGameTests() {}

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_ARRET)
    public static void apresArret(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_C)
    public static void apresC(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_LONGS)
    public static void apresLongs(ServerLevel level) {
        apres();
    }

    private static void apres() {
        for (JoueurDeTest j : OUVERTS) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        OUVERTS.clear();
    }

    private static void ecouter(MinecraftServer serveur) {
        if (ecoute) {
            return;
        }
        ecoute = true;
        CallService.addListener(new CallListener() {
            @Override
            public void changed(Call call) {
                CHANGES.add(call);
            }

            @Override
            public void ended(Call call, End reason) {
                FINS.add(new Fin(call, reason, serveur.getTickCount()));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static final class Essai {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> fondateurs = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            ecouter(serveur());
            for (int x = 0; x <= 7; x++) {
                for (int z = 0; z <= 7; z++) {
                    helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                    for (int y = 0; y <= 2; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        PhoneOperators registre() {
            return PhoneOperators.get(serveur());
        }

        JoueurDeTest joueur(String nom) {
            Vec3 ici = helper.absoluteVec(JOUEUR);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, ici);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            joueurs.add(j);
            OUVERTS.add(j);
            fondateurs.add(j.uuid());
            OperateurDEssai.oublier(serveur(), j.uuid());
            return j;
        }

        PhoneOperator fonder(JoueurDeTest j, BlockPos pos, String nom) {
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            helper.assertTrue(poser(j, pos).consumesAction(), "l'antenne devait se poser en " + pos);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            verser(antenne(pos));
            return fonde.get().operator();
        }

        void ajouter(JoueurDeTest j, BlockPos pos, PhoneOperator op) {
            helper.assertTrue(poser(j, pos).consumesAction(), "l'antenne devait se poser en " + pos);
            helper.assertTrue(op.id().equals(antenne(pos).operatorId()), "la seconde antenne rejoint " + op.name());
            verser(antenne(pos));
        }

        private InteractionResult poser(JoueurDeTest j, BlockPos pos) {
            ServerPlayer p = j.joueur();
            ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            BlockPos sol = helper.absolutePos(pos.below());
            BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
            InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
            p.setItemInHand(InteractionHand.MAIN_HAND, avant);
            return r;
        }

        AntennaBlockEntity antenne(BlockPos pos) {
            helper.assertTrue(helper.getBlockEntity(pos) instanceof AntennaBlockEntity, "pas d'antenne en " + pos);
            return (AntennaBlockEntity) helper.getBlockEntity(pos);
        }

        /** Une ligne de {@code op} pour {@code titulaire}, dans un téléphone neuf chargé, tenu en main. */
        ItemStack ligne(PhoneOperator op, JoueurDeTest titulaire) {
            ItemStack emise = OperateurDEssai.emettre(helper, op, titulaire);
            Optional<UUID> simId = SimCardItem.simId(emise);
            helper.assertTrue(simId.isPresent(), "carte émise sans identifiant");
            ServerPlayer fondateur = serveur().getPlayerList().getPlayer(op.founder());
            int ou = caseDe(fondateur, simId.get());
            helper.assertTrue(ou >= 0, "la carte est chez le fondateur");
            ItemStack carte = fondateur.getInventory().getItem(ou);
            fondateur.getInventory().setItem(ou, ItemStack.EMPTY);
            ServerPlayer p = titulaire.joueur();
            p.getInventory().setItem(9, carte);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhoneRegistry.PHONE.get()));
            ItemStack phone = p.getItemInHand(InteractionHand.MAIN_HAND);
            PhoneSims.Insert r = PhoneSims.insert(p, phone, 9);
            helper.assertTrue(r == PhoneSims.Insert.DONE, "insertion : " + r);
            PhoneItem.store(phone, PhoneConfig.capacity());
            return phone;
        }

        void placer(JoueurDeTest j, BlockPos pos, int dx) {
            BlockPos ici = helper.absolutePos(pos).offset(dx, 0, 0);
            j.joueur().teleportTo(ici.getX() + 0.5, ici.getY(), ici.getZ() + 0.5);
        }

        boolean enService(BlockPos pos) {
            AntennaBlockEntity be = antenne(pos);
            Optional<AntennaRecord> r = be.antennaId() == null ? Optional.empty() : registre().antenna(be.antennaId());
            return r.isPresent() && AntennaBlockEntity.inService(r.get(), helper.getLevel().getGameTime());
        }

        AntennaLoad.Breakdown conso(BlockPos pos) {
            AntennaBlockEntity be = antenne(pos);
            helper.assertTrue(be.antennaId() != null, "antenne sans identifiant");
            Optional<AntennaRecord> r = registre().antenna(be.antennaId());
            helper.assertTrue(r.isPresent(), "inscription absente en " + pos);
            return r.get().last();
        }

        void fermer() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            AABB boite = new AABB(helper.absolutePos(new BlockPos(-1, -1, -1)), helper.absolutePos(new BlockPos(9, 4, 9)));
            for (ItemEntity objet : helper.getLevel().getEntitiesOfClass(ItemEntity.class, boite)) {
                objet.discard();
            }
            for (BlockPos pos : new BlockPos[] {A, B, C}) {
                helper.setBlock(pos, Blocks.AIR);
            }
            for (UUID f : fondateurs) {
                OperateurDEssai.oublier(serveur(), f);
            }
        }
    }

    /** Deux opérateurs, chacun son fondateur et sa ligne, l'appelant sur A, l'appelé sur B. */
    private record Paire(Essai essai, JoueurDeTest m, JoueurDeTest l, ItemStack phoneM, ItemStack phoneL,
            SimCard carteM, SimCard carteL) {}

    private static Paire paire(GameTestHelper helper, String prefixe) {
        Essai essai = new Essai(helper);
        JoueurDeTest m = essai.joueur("ap_" + prefixe + "_m");
        JoueurDeTest l = essai.joueur("ap_" + prefixe + "_l");
        PhoneOperator mairie = essai.fonder(m, A, "Ap " + prefixe + " Mairie");
        PhoneOperator lagon = essai.fonder(l, B, "Ap " + prefixe + " Lagon");
        ItemStack phoneM = essai.ligne(mairie, m);
        ItemStack phoneL = essai.ligne(lagon, l);
        essai.placer(m, A, 0);
        essai.placer(l, B, 0);
        MinecraftServer srv = essai.serveur();
        return new Paire(essai, m, l, phoneM, phoneL, exiger(helper, PhoneSims.line(srv, phoneM), "ligne de M"),
                exiger(helper, PhoneSims.line(srv, phoneL), "ligne de L"));
    }

    private static void verser(AntennaBlockEntity be) {
        int reste = RESERVE;
        while (reste > 0) {
            int recu = be.energy().receiveEnergy(reste, false);
            if (recu <= 0) {
                break;
            }
            reste -= recu;
        }
    }

    private static int caseDe(ServerPlayer p, UUID simId) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (SimCardItem.simId(inv.getItem(i)).filter(simId::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    private static <T> T exiger(GameTestHelper helper, Optional<T> valeur, String message) {
        helper.assertTrue(valeur.isPresent(), message);
        return valeur.get();
    }

    private static Optional<Fin> fin(int callId) {
        return FINS.stream().filter(f -> f.call().id() == callId).findFirst();
    }

    private static long fins(int callId) {
        return FINS.stream().filter(f -> f.call().id() == callId).count();
    }

    /** Compose ; l'appel doit sonner. Rend son numéro. */
    private static int sonner(GameTestHelper helper, JoueurDeTest qui, ItemStack phone, String numero) {
        CallService.Dial d = CallService.dial(qui.joueur(), phone, false, numero);
        helper.assertTrue(d.outcome() == CallService.Outcome.RINGING && d.callId() > 0 && d.message() == null,
                "l'appel devait sonner : " + d.outcome() + " " + (d.message() == null ? "" : d.message().getString()));
        return d.callId();
    }

    private static void issue(GameTestHelper helper, CallService.Dial d, CallService.Outcome attendu, String cle,
            String cas) {
        helper.assertTrue(d.outcome() == attendu && d.callId() == 0, cas + " : " + attendu + " attendu, vu "
                + d.outcome());
        helper.assertTrue(d.message() != null && d.message().getContents() instanceof TranslatableContents t
                && t.getKey().equals(cle), cas + " : message " + cle + " attendu, vu "
                + (d.message() == null ? "aucun" : d.message().getContents()));
    }

    private static void finiPar(GameTestHelper helper, int callId, End attendu) {
        Optional<Fin> f = fin(callId);
        helper.assertTrue(f.isPresent(), "l'appel " + callId + " n'a pas fini");
        helper.assertTrue(f.get().reason() == attendu, "fin " + attendu + " attendue, vu " + f.get().reason());
        helper.assertTrue(fins(callId) == 1, "une seule fin annoncée : " + fins(callId));
    }

    private static Recent dernierRecent(GameTestHelper helper, MinecraftServer srv, SimCard carte, String qui) {
        List<Recent> recents = PhoneDirectory.get(srv).recents(carte.simId());
        helper.assertTrue(!recents.isEmpty(), "aucun récent pour " + qui);
        return recents.get(0);
    }

    /** Les deux antennes portent un appel (9 FE par tic de trafic chacune). */
    private static void enLigne(GameTestHelper helper, Essai essai) {
        for (BlockPos p : new BlockPos[] {A, B}) {
            AntennaLoad.Breakdown d = essai.conso(p);
            helper.assertTrue(d.traffic() == PhoneConfig.antennaCallFe(), "un appel en " + p + " : " + d);
        }
    }

    /**
     * Un appel coupé par {@code coupure} à l'étape suivante (ou par le relevé) : il doit finir par {@code attendu}, et
     * la consommation des deux antennes revenir à la veille (ce qu'elle était avant la sonnerie).
     */
    private static void couper(GameTestHelper helper, String prefixe, boolean decrocher, Consumer<Paire> coupure,
            End attendu, Consumer<Paire> controle) {
        Paire p = paire(helper, prefixe);
        Essai essai = p.essai();
        AntennaLoad.Breakdown[] veille = new AntennaLoad.Breakdown[2];
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    veille[0] = essai.conso(A);
                    veille[1] = essai.conso(B);
                    helper.assertTrue(veille[0].traffic() == 0 && veille[1].traffic() == 0, "aucun trafic avant");
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    if (decrocher) {
                        helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "l'appelé décroche");
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    enLigne(helper, essai);
                    coupure.accept(p);
                })
                .thenWaitUntil(() -> helper.assertTrue(fin(id[0]).isPresent(), "l'appel doit finir"))
                .thenIdle(2)
                .thenExecute(() -> {
                    finiPar(helper, id[0], attendu);
                    helper.assertTrue(CallService.callOf(p.m().joueur()).isEmpty()
                            && CallService.callOf(p.l().joueur()).isEmpty(), "plus personne en appel");
                    helper.assertTrue(essai.conso(A).equals(veille[0]) && essai.conso(B).equals(veille[1]),
                            "consommation revenue à la veille : " + essai.conso(A) + " " + essai.conso(B) + " au lieu de "
                                    + veille[0] + " " + veille[1]);
                    controle.accept(p);
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_appels_a : un appel et ce qui le coupe
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void sonneDecrocheRaccroche(GameTestHelper helper) {
        Paire p = paire(helper, "base");
        Essai essai = p.essai();
        MinecraftServer srv = essai.serveur();
        AntennaLoad.Breakdown[] veille = new AntennaLoad.Breakdown[2];
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    veille[0] = essai.conso(A);
                    veille[1] = essai.conso(B);
                    // Le numéro écrit avec ses espaces.
                    id[0] = sonner(helper, p.m(), p.phoneM(), SimNumbers.format(p.carteL().number()));
                    Optional<Call> chezL = CallService.callOf(p.l().joueur());
                    helper.assertTrue(chezL.isPresent() && chezL.get().id() == id[0]
                            && chezL.get().state() == CallBook.State.RINGING, "ça sonne chez l'appelé");
                    helper.assertTrue(chezL.get().caller().number().equals(p.carteM().number())
                            && chezL.get().callee().simId().equals(p.carteL().simId()), "les deux bouts");
                    helper.assertTrue(CHANGES.stream().anyMatch(c -> c.id() == id[0]
                            && c.state() == CallBook.State.RINGING), "la sonnerie est annoncée");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    enLigne(helper, essai);
                    helper.assertTrue(!CallService.hangUp(p.l().joueur(), id[0]), "l'appelé ne raccroche pas en sonnerie");
                    helper.assertTrue(!CallService.answer(p.m().joueur(), id[0]), "l'appelant ne décroche pas");
                    helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "l'appelé décroche");
                    helper.assertTrue(CallService.callOf(p.m().joueur()).map(Call::state)
                            .filter(CallBook.State.ACTIVE::equals).isPresent(), "en ligne");
                })
                // Deux relevés passent : un appel en règle n'est pas coupé.
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(fin(id[0]).isEmpty(), "l'appel tient : " + fin(id[0]).map(Fin::reason));
                    enLigne(helper, essai);
                    helper.assertTrue(CallService.hangUp(p.l().joueur(), id[0]), "l'appelé raccroche en ligne");
                    finiPar(helper, id[0], End.HUNG_UP);
                    helper.assertTrue(!CallService.hangUp(p.m().joueur(), id[0]), "déjà fini");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(A).equals(veille[0]) && essai.conso(B).equals(veille[1]),
                            "consommation revenue à la veille : " + essai.conso(A) + " " + essai.conso(B));
                    Recent m = dernierRecent(helper, srv, p.carteM(), "l'appelant");
                    Recent l = dernierRecent(helper, srv, p.carteL(), "l'appelé");
                    helper.assertTrue(m.kind() == Recent.Kind.OUTGOING && m.number().equals(p.carteL().number())
                            && m.seconds() >= 2, "récent de l'appelant : " + m.kind() + " " + m.seconds());
                    helper.assertTrue(l.kind() == Recent.Kind.INCOMING && l.number().equals(p.carteM().number())
                            && l.seconds() == m.seconds(), "récent de l'appelé : " + l.kind() + " " + l.seconds());
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppelantSeDeconnecte(GameTestHelper helper) {
        couper(helper, "depart", true, ici -> {
            int id = exiger(helper, CallService.callOf(ici.m().joueur()), "appel en cours").id();
            ici.m().quitter();
            // Dans le même tic que le départ, sans attendre le relevé (revue de la tâche 3, I3).
            finiPar(helper, id, End.PARTY_LEFT);
        }, End.PARTY_LEFT, p -> {
            Recent l = dernierRecent(helper, p.essai().serveur(), p.carteL(), "l'appelé");
            helper.assertTrue(l.kind() == Recent.Kind.INCOMING, "récent de l'appelé : " + l.kind());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppeleMeurt(GameTestHelper helper) {
        couper(helper, "mort", true, ici -> {
            int id = exiger(helper, CallService.callOf(ici.l().joueur()), "appel en cours").id();
            ici.l().joueur().kill();
            // Dans le même tic que la mort, sans attendre le relevé (revue de la tâche 3, I3).
            finiPar(helper, id, End.DIED);
        }, End.DIED, p -> { });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppeleChangeDeDimension(GameTestHelper helper) {
        couper(helper, "dim", false, ici -> {
            ServerPlayer l = ici.l().joueur();
            ServerLevel nether = l.server.getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "le Nether existe");
            l.teleportTo(nether, l.getX(), 120, l.getZ(), 0f, 0f);
        }, End.CHANGED_DIMENSION, p -> {
            Recent l = dernierRecent(helper, p.essai().serveur(), p.carteL(), "l'appelé");
            helper.assertTrue(l.kind() == Recent.Kind.MISSED && l.seconds() == 0, "appel manqué : " + l.kind());
        });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppeleJetteSonTelephonePendantLaSonnerie(GameTestHelper helper) {
        couper(helper, "jete", false, ici -> {
            ServerPlayer l = ici.l().joueur();
            helper.assertTrue(l.getMainHandItem().getItem() instanceof PhoneItem, "le téléphone est en main");
            helper.assertTrue(l.drop(true), "le téléphone est jeté");
        }, End.PHONE_GONE, p -> { });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppeleRetireSaCarte(GameTestHelper helper) {
        couper(helper, "carte", true, ici -> {
            PhoneSims.Remove r = PhoneSims.remove(ici.l().joueur(), ici.phoneL());
            helper.assertTrue(r == PhoneSims.Remove.DONE, "carte retirée : " + r);
        }, End.PHONE_GONE, p -> { });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppeleSortDeLaPortee(GameTestHelper helper) {
        couper(helper, "portee", true, ici -> ici.essai().placer(ici.l(), B, 1_000), End.NO_SIGNAL, p -> { });
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void lAppelantRaccrochePendantLaSonnerie(GameTestHelper helper) {
        couper(helper, "annule", false, ici -> {
            int id = exiger(helper, CallService.callOf(ici.m().joueur()), "appel en cours").id();
            helper.assertTrue(CallService.hangUp(ici.m().joueur(), id), "l'appelant raccroche");
        }, End.HUNG_UP, p -> {
            Recent l = dernierRecent(helper, p.essai().serveur(), p.carteL(), "l'appelé");
            helper.assertTrue(l.kind() == Recent.Kind.MISSED, "appel manqué : " + l.kind());
            Recent m = dernierRecent(helper, p.essai().serveur(), p.carteM(), "l'appelant");
            helper.assertTrue(m.kind() == Recent.Kind.OUTGOING && m.seconds() == 0, "appel passé : " + m.kind());
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_appels_b : appels refusés, occupé, frein, arrêt, copies, trajet, sonnerie
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void lesNumerosRefuses(GameTestHelper helper) {
        Paire p = paire(helper, "refus");
        Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    ServerPlayer m = p.m().joueur();
                    for (String faux : new String[] {"abc", "", "17", "15", "0711111111"}) {
                        issue(helper, CallService.dial(m, p.phoneM(), false, faux), CallService.Outcome.BAD_NUMBER,
                                "message.oneforallsurvival.call.bad_number", "« " + faux + " »");
                    }
                    issue(helper, CallService.dial(m, p.phoneM(), false, SimNumbers.format(p.carteM().number())),
                            CallService.Outcome.SELF, "message.oneforallsurvival.call.self", "soi-même");
                    String libre = "0600000001";
                    helper.assertTrue(essai.registre().cardByNumber(libre).isEmpty(), "le numéro témoin est libre");
                    issue(helper, CallService.dial(m, p.phoneM(), false, libre), CallService.Outcome.FAILED,
                            RelayFailure.Reason.CALLEE_UNKNOWN.langKey(), "numéro inconnu");
                    issue(helper, CallService.dial(m, p.phoneM(), true, p.carteL().number()),
                            CallService.Outcome.FAILED, RelayFailure.Reason.CALLER_AIRPLANE.langKey(), "appelant en avion");
                    helper.assertTrue(CallService.callOf(m).isEmpty() && CallService.callOf(p.l().joueur()).isEmpty(),
                            "aucun appel ouvert");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void occupeEtRefus(GameTestHelper helper) {
        Paire p = paire(helper, "occupe");
        Essai essai = p.essai();
        MinecraftServer srv = essai.serveur();
        JoueurDeTest x = essai.joueur("ap_occupe_x");
        PhoneOperator lagon = exiger(helper, essai.registre().byName("Ap occupe Lagon"), "Lagon");
        ItemStack phoneX = essai.ligne(lagon, x);
        essai.placer(x, B, 0);
        SimCard carteX = exiger(helper, PhoneSims.line(srv, phoneX), "ligne de X");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    int id = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    issue(helper, CallService.dial(x.joueur(), phoneX, false, p.carteL().number()),
                            CallService.Outcome.BUSY, "message.oneforallsurvival.call.busy", "appelé occupé");
                    issue(helper, CallService.dial(x.joueur(), phoneX, false, p.carteM().number()),
                            CallService.Outcome.BUSY, "message.oneforallsurvival.call.busy", "appelant occupé");
                    issue(helper, CallService.dial(p.m().joueur(), p.phoneM(), false, carteX.number()),
                            CallService.Outcome.FAILED, "message.oneforallsurvival.call.in_call", "déjà en appel");
                    // L'appelé refuse : manqué pour lui, et sa ligne est libre aussitôt.
                    helper.assertTrue(!CallService.decline(p.m().joueur(), id), "l'appelant ne refuse pas");
                    helper.assertTrue(CallService.decline(p.l().joueur(), id), "l'appelé refuse");
                    finiPar(helper, id, End.DECLINED);
                    helper.assertTrue(!CallService.answer(p.l().joueur(), id), "un appel refusé ne se décroche plus");
                    Recent l = dernierRecent(helper, srv, p.carteL(), "l'appelé");
                    helper.assertTrue(l.kind() == Recent.Kind.MISSED, "refusé : manqué, vu " + l.kind());
                    int suivant = sonner(helper, x, phoneX, p.carteL().number());
                    helper.assertTrue(CallService.callOf(p.l().joueur()).map(Call::id).filter(i -> i == suivant)
                            .isPresent(), "un nouvel appel sonne");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void unAppeleInjoignable(GameTestHelper helper) {
        Paire p = paire(helper, "injoignable");
        Essai essai = p.essai();
        MinecraftServer srv = essai.serveur();
        String cle = "message.oneforallsurvival.call.unreachable";
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    ServerPlayer m = p.m().joueur();
                    String num = p.carteL().number();
                    UUID phoneId = exiger(helper, PhoneItem.phoneId(p.phoneL()), "téléphone de L");
                    PhoneData data = PhoneData.get(srv);
                    PhoneData.PhoneRecord r = data.record(phoneId);
                    data.put(phoneId, new PhoneData.PhoneRecord(r.owner(), r.ownerName(), r.salt(), r.codeHash(),
                            r.failures(), r.lockedUntil(), r.lockoutLevel(), r.wallpaper(), true));
                    issue(helper, CallService.dial(m, p.phoneM(), false, num), CallService.Outcome.UNREACHABLE, cle,
                            "appelé en mode avion");
                    data.put(phoneId, r);
                    PhoneItem.store(p.phoneL(), 0);
                    issue(helper, CallService.dial(m, p.phoneM(), false, num), CallService.Outcome.UNREACHABLE, cle,
                            "batterie de l'appelé vide");
                    PhoneItem.store(p.phoneL(), PhoneConfig.capacity());
                    // Témoin : rechargé, il sonne ; puis le porteur part.
                    int id = sonner(helper, p.m(), p.phoneM(), num);
                    helper.assertTrue(CallService.hangUp(m, id), "l'appelant raccroche");
                    p.l().quitter();
                    issue(helper, CallService.dial(m, p.phoneM(), false, num), CallService.Outcome.UNREACHABLE, cle,
                            "aucun porteur connecté");
                    // La batterie de l'appelant vide : il ne compose plus.
                    PhoneItem.store(p.phoneM(), 0);
                    issue(helper, CallService.dial(m, p.phoneM(), false, num), CallService.Outcome.FAILED,
                            "message.oneforallsurvival.phone.battery_empty", "batterie de l'appelant vide");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void leDebitEstFreine(GameTestHelper helper) {
        Paire p = paire(helper, "debit");
        Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    int limite = PhoneConfig.callDialsPerMinute();
                    helper.assertTrue(limite == 10, "10 appels par minute : " + limite);
                    ServerPlayer m = p.m().joueur();
                    for (int i = 0; i < limite; i++) {
                        issue(helper, CallService.dial(m, p.phoneM(), false, "06000000" + (10 + i)),
                                CallService.Outcome.FAILED, RelayFailure.Reason.CALLEE_UNKNOWN.langKey(), "essai " + i);
                    }
                    // Freiné, même vers un numéro qui existe : rien n'apprend s'il existe.
                    issue(helper, CallService.dial(m, p.phoneM(), false, p.carteL().number()),
                            CallService.Outcome.TOO_FAST, "message.oneforallsurvival.call.too_fast", "11e appel");
                    issue(helper, CallService.dial(m, p.phoneM(), false, "abc"),
                            CallService.Outcome.TOO_FAST, "message.oneforallsurvival.call.too_fast", "12e appel");
                    helper.assertTrue(CallService.callOf(m).isEmpty(), "rien ne sonne");
                    // Un autre joueur n'est pas freiné.
                    int id = sonner(helper, p.l(), p.phoneL(), p.carteM().number());
                    helper.assertTrue(CallService.hangUp(p.l().joueur(), id), "raccrocher n'est jamais freiné");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ARRET, timeoutTicks = 200)
    public static void lArretDuServeurFinitTout(GameTestHelper helper) {
        Paire p = paire(helper, "arret");
        Essai essai = p.essai();
        AntennaLoad.Breakdown[] veille = new AntennaLoad.Breakdown[2];
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    veille[0] = essai.conso(A);
                    veille[1] = essai.conso(B);
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "décroché");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    enLigne(helper, essai);
                    // Un écouteur qui recompose depuis la fin d'arrêt (réentrance, revue de la tâche 3, m1).
                    List<CallService.Dial> recompose = new ArrayList<>();
                    CallListener rappel = new CallListener() {
                        @Override
                        public void changed(Call call) {}

                        @Override
                        public void ended(Call call, End reason) {
                            if (call.id() == id[0]) {
                                recompose.add(CallService.dial(p.m().joueur(), p.phoneM(), false,
                                        p.carteL().number()));
                            }
                        }
                    };
                    CallService.addListener(rappel);
                    // L'inscription du mod (CallService.register) sur un bus à part, où seul l'arrêt est publié : on
                    // prouve que ServerStoppingEvent finit les appels sans réveiller les autres écouteurs du jeu
                    // (revue de la tâche 3, I3). Seul dans son lot : aucun autre essai n'a d'appel ouvert.
                    IEventBus bus = BusBuilder.builder().build();
                    CallService.register(bus);
                    try {
                        bus.post(new ServerStoppingEvent(essai.serveur()));
                    } finally {
                        CallService.removeListener(rappel);
                    }
                    finiPar(helper, id[0], End.SERVER_STOPPING);
                    helper.assertTrue(recompose.size() == 1, "l'écouteur a recomposé une fois : " + recompose.size());
                    CallService.Dial d = recompose.get(0);
                    helper.assertTrue(d.outcome() != CallService.Outcome.RINGING || fin(d.callId()).isPresent(),
                            "un appel composé pendant l'arrêt ne disparaît pas sans fin : " + d.outcome());
                    helper.assertTrue(CallService.callOf(p.m().joueur()).isEmpty()
                            && CallService.callOf(p.l().joueur()).isEmpty(), "aucun appel fantôme");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(A).equals(veille[0]) && essai.conso(B).equals(veille[1]),
                            "aucune poignée restante : " + essai.conso(A) + " " + essai.conso(B));
                    // Après l'arrêt, on rappelle comme au premier jour.
                    int encore = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), encore), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void deuxCopiesUnSeulSonne(GameTestHelper helper) {
        Paire p = paire(helper, "copie");
        Essai essai = p.essai();
        JoueurDeTest y = essai.joueur("ap_copie_y");
        essai.placer(y, B, 0);
        // Une copie exacte du téléphone de L (même identifiant, même carte, même jeton) chez Y.
        y.joueur().getInventory().setItem(5, p.phoneL().copy());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    helper.assertTrue(PhoneSims.check(essai.serveur(), y.joueur().getInventory().getItem(5))
                            == PhoneSims.Validity.ACTIVE, "la copie a une carte vivante");
                    int id = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    boolean chezL = CallService.callOf(p.l().joueur()).isPresent();
                    boolean chezY = CallService.callOf(y.joueur()).isPresent();
                    helper.assertTrue(chezL != chezY, "un seul sonne : L " + chezL + ", Y " + chezY);
                    ServerPlayer autre = chezL ? y.joueur() : p.l().joueur();
                    helper.assertTrue(!CallService.answer(autre, id), "l'autre porteur ne décroche pas");
                    helper.assertTrue(CallService.answer(chezL ? p.l().joueur() : y.joueur(), id), "le porteur décroche");
                    helper.assertTrue(CallService.callOf(autre).isEmpty(), "toujours un seul en ligne");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id), "raccroché");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 300)
    public static void leTrajetSuitLAppele(GameTestHelper helper) {
        Paire p = paire(helper, "trajet");
        Essai essai = p.essai();
        PhoneOperator lagon = exiger(helper, essai.registre().byName("Ap trajet Lagon"), "Lagon");
        essai.ajouter(p.l(), C, lagon);
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B) && essai.enService(C),
                        "antennes en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "décroché");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    enLigne(helper, essai);
                    helper.assertTrue(essai.conso(C).traffic() == 0, "C ne porte rien : " + essai.conso(C));
                    essai.placer(p.l(), C, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(essai.conso(C).traffic() == PhoneConfig.antennaCallFe()
                        && essai.conso(B).traffic() == 0, "le trafic passe de B à C : B " + essai.conso(B) + ", C "
                        + essai.conso(C)))
                .thenExecute(() -> {
                    helper.assertTrue(essai.conso(A).traffic() == PhoneConfig.antennaCallFe(), "A porte toujours");
                    helper.assertTrue(fin(id[0]).isEmpty(), "l'appel continue");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos pos : new BlockPos[] {A, B, C}) {
                        helper.assertTrue(essai.conso(pos).traffic() == 0, "plus de trafic en " + pos);
                    }
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Sans réponse, la sonnerie cesse au relevé qui tombe exactement {@code callRingSeconds × 20} tics après son début :
     * l'appel est composé sur un tic de relevé, la borne est donc comprise ({@code >=}, revue de la tâche 3, I2).
     */
    @GameTest(template = "test/plancher", batch = LOT_LONGS, timeoutTicks = 900)
    public static void sansReponseLaSonnerieCesse(GameTestHelper helper) {
        Paire p = paire(helper, "sonnerie");
        Essai essai = p.essai();
        MinecraftServer srv = essai.serveur();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenWaitUntil(() -> {
                    // Composé sur un tic de relevé : le relevé du 600e tic après lui juge la borne.
                    helper.assertTrue(srv.getTickCount() % CallService.CHECK_PERIOD == 0, "tic de relevé attendu");
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                })
                .thenIdle(300)
                .thenExecute(() -> {
                    // 55 FE par tic sur A : la réserve tiendrait à peine les 600 tics. On la remplit à mi-chemin.
                    helper.assertTrue(fin(id[0]).isEmpty(), "ça sonne encore à mi-chemin");
                    verser(essai.antenne(A));
                    verser(essai.antenne(B));
                })
                .thenWaitUntil(() -> helper.assertTrue(fin(id[0]).isPresent(), "la sonnerie doit cesser"))
                .thenExecute(() -> {
                    finiPar(helper, id[0], End.NO_ANSWER);
                    Fin f = fin(id[0]).get();
                    long duree = f.tick() - f.call().startedTick();
                    int sonnerie = PhoneConfig.callRingSeconds() * 20;
                    helper.assertTrue(sonnerie == 600, "30 s de sonnerie : " + sonnerie);
                    helper.assertTrue(f.call().startedTick() % CallService.CHECK_PERIOD == 0,
                            "composé sur un tic de relevé : " + f.call().startedTick());
                    helper.assertTrue(duree == sonnerie, "fin au relevé du 600e tic (borne comprise), vu " + duree);
                    Recent l = dernierRecent(helper, srv, p.carteL(), "l'appelé");
                    helper.assertTrue(l.kind() == Recent.Kind.MISSED && l.seconds() == 0, "manqué : " + l.kind());
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Un appel décroché passe la durée de sonnerie sans être coupé : seule une sonnerie expire (revue, I2). */
    @GameTest(template = "test/plancher", batch = LOT_LONGS, timeoutTicks = 1000)
    public static void unAppelDecrocheTientAuDelaDeLaSonnerie(GameTestHelper helper) {
        Paire p = paire(helper, "tient");
        Essai essai = p.essai();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    helper.assertTrue(CallService.answer(p.l().joueur(), id[0]), "décroché");
                })
                .thenIdle(300)
                .thenExecute(() -> {
                    verser(essai.antenne(A));
                    verser(essai.antenne(B));
                })
                .thenIdle(400)
                .thenExecute(() -> {
                    helper.assertTrue(fin(id[0]).isEmpty(), "l'appel décroché tient 700 tics : "
                            + fin(id[0]).map(Fin::reason));
                    helper.assertTrue(CallService.callOf(p.m().joueur()).map(Call::state)
                            .filter(CallBook.State.ACTIVE::equals).isPresent(), "toujours en ligne");
                    helper.assertTrue(CallService.hangUp(p.m().joueur(), id[0]), "raccroché");
                    finiPar(helper, id[0], End.HUNG_UP);
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_appels_c : tour 1 de la tâche 3
    // ---------------------------------------------------------------------------------------------

    /** Quitter puis revenir ne remet pas le frein à zéro (revue de la tâche 3, I1). */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void leFreinSurvitALaReconnexion(GameTestHelper helper) {
        Paire p = paire(helper, "frein");
        Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    ServerPlayer m = p.m().joueur();
                    for (int i = 0; i < PhoneConfig.callDialsPerMinute(); i++) {
                        issue(helper, CallService.dial(m, p.phoneM(), false, "06000001" + (10 + i)),
                                CallService.Outcome.FAILED, RelayFailure.Reason.CALLEE_UNKNOWN.langKey(), "essai " + i);
                    }
                    p.m().quitter();
                    JoueurDeTest revenu = essai.joueur("ap_frein_m");
                    helper.assertTrue(revenu.uuid().equals(m.getUUID()), "le même joueur revient");
                    issue(helper, CallService.dial(revenu.joueur(), ItemStack.EMPTY, false, p.carteL().number()),
                            CallService.Outcome.TOO_FAST, "message.oneforallsurvival.call.too_fast", "après le retour");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /**
     * Un écouteur qui raccroche dès la sonnerie annoncée : les écouteurs suivants ne reçoivent pas cette sonnerie
     * périmée (revue de la tâche 3, m1).
     */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void unEcouteurQuiRaccrocheArreteLaDiffusion(GameTestHelper helper) {
        Paire p = paire(helper, "reentre");
        Essai essai = p.essai();
        UUID m = p.m().uuid();
        List<Call> vusApres = new ArrayList<>();
        CallListener raccroche = new CallListener() {
            @Override
            public void changed(Call call) {
                if (call.caller().player().equals(m) && call.state() == CallBook.State.RINGING) {
                    CallService.hangUp(p.m().joueur(), call.id());
                }
            }

            @Override
            public void ended(Call call, End reason) {}
        };
        CallListener temoin = new CallListener() {
            @Override
            public void changed(Call call) {
                if (call.caller().player().equals(m)) {
                    vusApres.add(call);
                }
            }

            @Override
            public void ended(Call call, End reason) {}
        };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    CallService.addListener(raccroche);
                    CallService.addListener(temoin);
                    CallService.Dial d;
                    try {
                        d = CallService.dial(p.m().joueur(), p.phoneM(), false, p.carteL().number());
                    } finally {
                        CallService.removeListener(raccroche);
                        CallService.removeListener(temoin);
                    }
                    helper.assertTrue(d.callId() > 0, "l'appel a sonné : " + d.outcome());
                    finiPar(helper, d.callId(), End.HUNG_UP);
                    helper.assertTrue(vusApres.isEmpty(), "une sonnerie déjà raccrochée a été diffusée : " + vusApres);
                    helper.assertTrue(CallService.callOf(p.m().joueur()).isEmpty(), "plus d'appel");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** Un joueur mort (vie à 0) ne sonne pas, ne décroche pas, ne compose pas (revue de la tâche 3, m2). */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void unMortNeSonneNeDecrocheNeComposePas(GameTestHelper helper) {
        Paire p = paire(helper, "mort2");
        Essai essai = p.essai();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    ServerPlayer m = p.m().joueur();
                    ServerPlayer l = p.l().joueur();
                    // Vie à 0 sans mort jouée (le joueur d'essai ne tique pas) : le téléphone reste dans la main.
                    l.setHealth(0f);
                    helper.assertTrue(!l.isAlive() && l.getMainHandItem().getItem() instanceof PhoneItem,
                            "l'appelé est mort, téléphone en main");
                    issue(helper, CallService.dial(m, p.phoneM(), false, p.carteL().number()),
                            CallService.Outcome.UNREACHABLE, "message.oneforallsurvival.call.unreachable", "appelé mort");
                    l.setHealth(20f);
                    int id = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    l.setHealth(0f);
                    helper.assertTrue(!CallService.answer(l, id), "un mort ne décroche pas");
                    l.setHealth(20f);
                    helper.assertTrue(CallService.hangUp(m, id), "raccroché");
                    m.setHealth(0f);
                    CallService.Dial d = CallService.dial(m, p.phoneM(), false, p.carteL().number());
                    m.setHealth(20f);
                    helper.assertTrue(d.outcome() == CallService.Outcome.FAILED && d.callId() == 0,
                            "un mort ne compose pas : " + d.outcome());
                    helper.assertTrue(CallService.callOf(l).isEmpty(), "rien ne sonne");
                    essai.fermer();
                })
                .thenSucceed();
    }

    /** L'appelé passe en mode avion pendant la sonnerie : il ne décroche plus, l'appel reste manqué (m3). */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void enModeAvionOnNeDecrochePas(GameTestHelper helper) {
        Paire p = paire(helper, "avion_d");
        Essai essai = p.essai();
        int[] id = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(essai.enService(A) && essai.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    id[0] = sonner(helper, p.m(), p.phoneM(), p.carteL().number());
                    avion(essai.serveur(), p.phoneL(), true);
                    helper.assertTrue(!CallService.answer(p.l().joueur(), id[0]), "en mode avion, on ne décroche pas");
                })
                .thenWaitUntil(() -> helper.assertTrue(fin(id[0]).isPresent(), "l'appel doit finir"))
                .thenExecute(() -> {
                    finiPar(helper, id[0], End.NO_SIGNAL);
                    Recent l = dernierRecent(helper, essai.serveur(), p.carteL(), "l'appelé");
                    helper.assertTrue(l.kind() == Recent.Kind.MISSED, "manqué : " + l.kind());
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 300)
    public static void leModeAvionEnCoursDAppelCoupe(GameTestHelper helper) {
        couper(helper, "avion", true, ici -> avion(ici.essai().serveur(), ici.phoneL(), true), End.NO_SIGNAL, p -> { });
    }

    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 300)
    public static void laBatterieVideEnCoursDAppelCoupe(GameTestHelper helper) {
        couper(helper, "pile", true, ici -> PhoneItem.store(ici.phoneL(), 0), End.PHONE_GONE, p -> { });
    }

    /** Met le téléphone en mode avion (ou l'en sort), comme le réglage de l'écran. */
    private static void avion(MinecraftServer srv, ItemStack phone, boolean on) {
        // Jamais d'exception autre qu'une assertion dans une séquence : un téléphone sans identifiant ne change rien.
        PhoneItem.phoneId(phone).ifPresent(phoneId -> {
            PhoneData data = PhoneData.get(srv);
            PhoneData.PhoneRecord r = data.record(phoneId);
            data.put(phoneId, new PhoneData.PhoneRecord(r.owner(), r.ownerName(), r.salt(), r.codeHash(),
                    r.failures(), r.lockedUntil(), r.lockoutLevel(), r.wallpaper(), on));
        });
    }
}
