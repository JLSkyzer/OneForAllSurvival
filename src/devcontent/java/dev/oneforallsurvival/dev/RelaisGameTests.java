package dev.oneforallsurvival.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.NetworkStatus;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimNumbers;
import dev.oneforallsurvival.phone.net.TrafficHandle;
import dev.oneforallsurvival.phone.net.TrafficKind;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * La façade du réseau (lot T2, tâche 6) : le signal d'un téléphone, le trajet d'un appel d'un opérateur à l'autre, la
 * raison lisible d'un échec, le trafic qui fait consommer les antennes et l'antenne d'urgence.
 *
 * <p>Chaque essai fonde ses propres opérateurs, sous des noms qui lui sont propres (les essais d'un lot tournent
 * ensemble, et un nom d'opérateur est unique sur le serveur). Les joueurs sont replacés par {@code teleportTo} après
 * l'entrée (leçon du 2026-11-14). Dans les séquences, aucune exception autre qu'une assertion (leçon du 2026-11-20) ;
 * l'essai se range à sa dernière étape.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RelaisGameTests {

    private static final String LOT_A = "relais_a";
    private static final String LOT_TRAFIC = "relais_trafic";

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(6, 0, 6);
    private static final BlockPos C = new BlockPos(6, 0, 1);
    private static final Vec3 JOUEUR = new Vec3(3.5, 0, 3.5);
    private static final int RESERVE = 40_000;

    private RelaisGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Sol de pierre, air au-dessus (leçon du 2026-11-20 : jamais supposer l'air au-dessus du plancher). */
    private static void degager(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** Ce qu'un essai a ouvert : joueurs, antennes, opérateurs ; tout est rendu à la fermeture. */
    private static final class Essai {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> fondateurs = new ArrayList<>();
        private final List<TrafficHandle> poignees = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            degager(helper);
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
            fondateurs.add(j.uuid());
            OperateurDEssai.oublier(serveur(), j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            return j;
        }

        /** Une antenne posée par {@code j} en {@code pos}, fondée sous {@code nom}, avec {@code fe} FE en réserve. */
        PhoneOperator fonder(JoueurDeTest j, BlockPos pos, String nom, int fe) {
            // Un opérateur du même nom laissé par un passage interrompu : oublié d'abord.
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            helper.assertTrue(poser(j, pos).consumesAction(), "l'antenne devait se poser en " + pos);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            verser(antenne(pos), fe);
            return fonde.get().operator();
        }

        /** Une antenne de plus pour l'opérateur de {@code j} (objet neuf posé par son fondateur). */
        void ajouter(JoueurDeTest j, BlockPos pos, PhoneOperator op) {
            helper.assertTrue(poser(j, pos).consumesAction(), "l'antenne devait se poser en " + pos);
            helper.assertTrue(op.id().equals(antenne(pos).operatorId()), "la seconde antenne rejoint " + op.name());
            verser(antenne(pos), RESERVE);
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

        /**
         * Une ligne de {@code op} pour {@code titulaire} : la carte émise par le fondateur, passée au titulaire, glissée
         * dans un téléphone neuf tenu en main. Rend le téléphone (la pile de la main).
         */
        ItemStack ligne(PhoneOperator op, JoueurDeTest titulaire) {
            ItemStack emise = OperateurDEssai.emettre(helper, op, titulaire);
            Optional<UUID> simId = SimCardItem.simId(emise);
            helper.assertTrue(simId.isPresent(), "carte émise sans identifiant");
            ServerPlayer fondateur = serveur().getPlayerList().getPlayer(op.founder());
            int ou = caseDe(fondateur, simId.get());
            helper.assertTrue(ou >= 0, "la carte est chez le fondateur");
            ItemStack carte = fondateur.getInventory().getItem(ou);
            fondateur.getInventory().setItem(ou, ItemStack.EMPTY);
            return glisser(titulaire, carte);
        }

        /** Glisse cette pile de carte dans un téléphone neuf, tenu en main par {@code j}. */
        ItemStack glisser(JoueurDeTest j, ItemStack carte) {
            ServerPlayer p = j.joueur();
            p.getInventory().setItem(9, carte);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhoneRegistry.PHONE.get()));
            ItemStack phone = p.getItemInHand(InteractionHand.MAIN_HAND);
            PhoneSims.Insert r = PhoneSims.insert(p, phone, 9);
            helper.assertTrue(r == PhoneSims.Insert.DONE, "insertion : " + r);
            return phone;
        }

        /** Le joueur se tient sur l'antenne en {@code pos} (décalé de {@code dx} blocs vers l'est). */
        void placer(JoueurDeTest j, BlockPos pos, int dx) {
            BlockPos ici = helper.absolutePos(pos).offset(dx, 0, 0);
            j.joueur().teleportTo(ici.getX() + 0.5, ici.getY(), ici.getZ() + 0.5);
        }

        TrafficHandle ouvrir(RelayPlanner.Route route, TrafficKind genre) {
            TrafficHandle h = PhoneNetwork.openTraffic(serveur(), route, genre);
            poignees.add(h);
            return h;
        }

        void fermer() {
            for (TrafficHandle h : poignees) {
                h.close();
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            // Les blocs d'abord (leur retrait efface l'inscription et rend le ticket), puis les opérateurs.
            for (BlockPos pos : new BlockPos[] {A, B, C}) {
                helper.setBlock(pos, Blocks.AIR);
            }
            for (UUID f : fondateurs) {
                OperateurDEssai.oublier(serveur(), f);
            }
        }
    }

    private static void verser(AntennaBlockEntity be, int fe) {
        int reste = fe;
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

    private static GlobalPos ou(JoueurDeTest j) {
        return GlobalPos.of(j.joueur().level().dimension(), j.joueur().blockPosition());
    }

    private static String numero(GameTestHelper helper, MinecraftServer serveur, ItemStack phone) {
        return exiger(helper, PhoneSims.line(serveur, phone), "le téléphone n'a pas de ligne").number();
    }

    private static UUID simDe(GameTestHelper helper, MinecraftServer serveur, ItemStack phone) {
        return exiger(helper, PhoneSims.line(serveur, phone), "le téléphone n'a pas de ligne").simId();
    }

    private static boolean enService(Essai essai, AntennaBlockEntity be) {
        Optional<AntennaRecord> r = be.antennaId() == null ? Optional.empty() : essai.registre().antenna(be.antennaId());
        return r.isPresent() && AntennaBlockEntity.inService(r.get(), essai.helper.getLevel().getGameTime());
    }

    private static AntennaLoad.Breakdown dernier(GameTestHelper helper, Essai essai, AntennaBlockEntity be) {
        helper.assertTrue(be.antennaId() != null, "antenne sans identifiant");
        return exiger(helper, essai.registre().antenna(be.antennaId()), "inscription absente").last();
    }

    private static RelayPlanner.Route trajet(GameTestHelper helper, Either<RelayPlanner.Route, RelayFailure> r) {
        helper.assertTrue(r.left().isPresent(), "l'appel devait passer : " + r.right().map(Object::toString).orElse(""));
        return r.left().get();
    }

    private static RelayFailure echec(GameTestHelper helper, Either<RelayPlanner.Route, RelayFailure> r) {
        helper.assertTrue(r.right().isPresent(), "l'appel ne devait pas passer : " + r.left().map(Object::toString).orElse(""));
        return r.right().get();
    }

    /** Le texte tel qu'un joueur en français le lit : le modèle de {@code fr_fr.json}, arguments posés. */
    private static String enFrancais(GameTestHelper helper, Component c) {
        helper.assertTrue(c.getContents() instanceof TranslatableContents, "texte non traduisible : " + c);
        TranslatableContents t = (TranslatableContents) c.getContents();
        JsonObject fr;
        try (InputStream in = RelaisGameTests.class.getResourceAsStream("/assets/oneforallsurvival/lang/fr_fr.json")) {
            helper.assertTrue(in != null, "fr_fr.json introuvable");
            fr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            helper.fail("lecture de fr_fr.json : " + e);
            return "";
        }
        helper.assertTrue(fr.has(t.getKey()), "clé absente de fr_fr : " + t.getKey());
        Object[] args = new Object[t.getArgs().length];
        for (int i = 0; i < args.length; i++) {
            args[i] = t.getArgs()[i] instanceof Component part ? part.getString() : String.valueOf(t.getArgs()[i]);
        }
        return String.format(Locale.ROOT, fr.get(t.getKey()).getAsString(), args);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot relais_a : signal, trajet, raisons d'échec
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void sansAntenneAlimenteeLeSignalEstNul(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_vide");
        PhoneOperator op = essai.fonder(f, A, "Relais Vide", 0);
        ItemStack phone = essai.ligne(op, f);
        essai.placer(f, A, 0);
        String num = numero(helper, essai.serveur(), phone);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    AntennaBlockEntity be = essai.antenne(A);
                    helper.assertTrue(be.state() == MachineState.NO_POWER, "sans courant : " + be.state());
                    NetworkStatus s = PhoneNetwork.status(f.joueur(), phone, false);
                    helper.assertTrue(s.sim() == PhoneSims.Validity.ACTIVE && s.operatorName().equals("Relais Vide")
                            && s.number().equals(num) && !s.airplane(), "statut : " + s);
                    helper.assertTrue(s.bars() == 0, "aucune antenne alimentée : 0 barre, vu " + s.bars());
                    verser(be, RESERVE);
                })
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A)), "l'antenne alimentée"))
                .thenExecute(() -> {
                    NetworkStatus s = PhoneNetwork.status(f.joueur(), phone, false);
                    helper.assertTrue(s.bars() == 4, "témoin : alimentée, 4 barres, vu " + s.bars());
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void leSignalBaisseAvecLaDistance(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_distance");
        PhoneOperator op = essai.fonder(f, A, "Relais Distance", RESERVE);
        ItemStack phone = essai.ligne(op, f);
        helper.assertTrue(PhoneConfig.antennaRange() == 270, "portée attendue 270 : " + PhoneConfig.antennaRange());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A)), "antenne en service"))
                .thenExecute(() -> {
                    int[] distances = {0, 100, 200, 300};
                    int[] barres = {4, 3, 2, 0};
                    for (int i = 0; i < distances.length; i++) {
                        essai.placer(f, A, distances[i]);
                        NetworkStatus s = PhoneNetwork.status(f.joueur(), phone, false);
                        helper.assertTrue(s.bars() == barres[i],
                                distances[i] + " blocs : " + barres[i] + " barres attendues, vu " + s.bars());
                    }
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unTelephoneNeParleQuAuxAntennesDeSonOperateur(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_seule_m");
        JoueurDeTest fl = essai.joueur("rl_seule_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Seule Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Seule Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        // L'antenne de Mairie démontée : sous l'antenne de Lagon, il ne reste que Lagon.
        helper.setBlock(A, Blocks.AIR);
        helper.assertTrue(essai.registre().antennasOf(mairie.id()).isEmpty(), "Mairie n'a plus d'antenne");
        essai.placer(fm, B, 0);
        essai.placer(fl, B, 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(B)), "antenne de Lagon"))
                .thenExecute(() -> {
                    NetworkStatus m = PhoneNetwork.status(fm.joueur(), phoneM, false);
                    helper.assertTrue(m.sim() == PhoneSims.Validity.ACTIVE && m.operatorName().equals("Seule Mairie"),
                            "carte de Mairie : " + m);
                    helper.assertTrue(m.bars() == 0, "carte de Mairie sous Lagon : 0 barre, vu " + m.bars());
                    NetworkStatus l = PhoneNetwork.status(fl.joueur(), phoneL, false);
                    helper.assertTrue(l.bars() == 4, "témoin : carte de Lagon, 4 barres, vu " + l.bars());
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unAppelEntreDeuxOperateursPasseQuandLesDeuxSontAlimentees(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_appel_m");
        JoueurDeTest fl = essai.joueur("rl_appel_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Appel Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Appel Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        essai.placer(fm, A, 0);
        essai.placer(fl, B, 0);
        MinecraftServer srv = essai.serveur();
        String numM = numero(helper, srv, phoneM);
        String numL = numero(helper, srv, phoneL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    UUID antenneM = essai.antenne(A).antennaId();
                    UUID antenneL = essai.antenne(B).antennaId();
                    RelayPlanner.Route r = trajet(helper,
                            PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    helper.assertTrue(r.caller().id().equals(antenneM) && r.callee().id().equals(antenneL),
                            "Mairie relaie l'appelant, Lagon l'appelé : " + r);
                    // Le numéro écrit avec ses espaces, et l'appel dans l'autre sens.
                    trajet(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, SimNumbers.format(numL), ou(fl)));
                    RelayPlanner.Route retour = trajet(helper,
                            PhoneNetwork.route(srv, fl.joueur(), phoneL, false, numM, ou(fm)));
                    helper.assertTrue(retour.caller().id().equals(antenneL) && retour.callee().id().equals(antenneM),
                            "retour : " + retour);
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void ilEchoueSiLAntenneDeLAppeleEstHorsTension(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_hs_m");
        JoueurDeTest fl = essai.joueur("rl_hs_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Hs Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        essai.placer(fm, A, 0);
        essai.placer(fl, B, 0);
        MinecraftServer srv = essai.serveur();
        String numL = numero(helper, srv, phoneL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    trajet(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    // La source de Lagon retirée : sa réserve vidée.
                    essai.antenne(B).drainEnergy(Integer.MAX_VALUE);
                })
                .thenWaitUntil(() -> helper.assertTrue(!enService(essai, essai.antenne(B)), "Lagon hors tension"))
                .thenExecute(() -> {
                    RelayFailure e = echec(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    helper.assertTrue(e.reason() == RelayFailure.Reason.CALLEE_ANTENNA_DOWN, "raison : " + e);
                    helper.assertTrue(e.operatorName().equals("Lagon"), "l'opérateur de l'appelé : " + e);
                    Component texte = e.describe();
                    String fr = enFrancais(helper, texte);
                    helper.assertTrue(fr.equals("Injoignable · Antenne de Lagon hors tension"), "en français : " + fr);
                    String lu = texte.getString();
                    BlockPos b = helper.absolutePos(B);
                    helper.assertTrue(lu.contains("Lagon") && !lu.contains("rl_hs_l")
                            && !lu.contains(String.valueOf(b.getX())), "ni fondateur ni position : " + lu);
                    helper.assertTrue(PhoneNetwork.status(fl.joueur(), phoneL, false).bars() == 0,
                            "l'appelé n'a plus de signal");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unOperateurSupprimeNeRelaiePlus(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_suppr_m");
        JoueurDeTest fl = essai.joueur("rl_suppr_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Suppr Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Suppr Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        essai.placer(fm, A, 0);
        essai.placer(fl, B, 0);
        MinecraftServer srv = essai.serveur();
        String numM = numero(helper, srv, phoneM);
        String numL = numero(helper, srv, phoneL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    trajet(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    helper.assertTrue(essai.registre().remove(lagon.id()), "Lagon retiré du registre");
                    RelayFailure versLagon = echec(helper,
                            PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    helper.assertTrue(versLagon.reason() == RelayFailure.Reason.CALLEE_OPERATOR_REMOVED,
                            "vers Lagon : " + versLagon);
                    RelayFailure depuisLagon = echec(helper,
                            PhoneNetwork.route(srv, fl.joueur(), phoneL, false, numM, ou(fm)));
                    helper.assertTrue(depuisLagon.reason() == RelayFailure.Reason.CALLER_OPERATOR_REMOVED,
                            "depuis Lagon : " + depuisLagon);
                    NetworkStatus s = PhoneNetwork.status(fl.joueur(), phoneL, false);
                    helper.assertTrue(s.bars() == 0 && s.sim() == PhoneSims.Validity.DEACTIVATED, "statut : " + s);
                    helper.assertTrue(PhoneNetwork.relayStates(srv).stream()
                            .noneMatch(x -> x.operatorId().equals(lagon.id())), "Lagon absent de l'état du relais");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockState(B).getValue(ElectricMachineBlock.STATE) == MachineState.STOPPED
                                && !enService(essai, essai.antenne(B)), "l'antenne de Lagon s'arrête"))
                .thenExecute(essai::fermer)
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void uneCarteSuspendueNAppellePlus(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_susp_m");
        JoueurDeTest fl = essai.joueur("rl_susp_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Susp Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Susp Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        essai.placer(fm, A, 0);
        essai.placer(fl, B, 0);
        MinecraftServer srv = essai.serveur();
        String numM = numero(helper, srv, phoneM);
        String numL = numero(helper, srv, phoneL);
        UUID simM = simDe(helper, srv, phoneM);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    PhoneOperators registre = essai.registre();
                    helper.assertTrue(registre.setState(simM, SimCard.State.SUSPENDED), "suspension");
                    RelayFailure e = echec(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    helper.assertTrue(e.reason() == RelayFailure.Reason.CALLER_SIM_INACTIVE, "appelant : " + e);
                    NetworkStatus s = PhoneNetwork.status(fm.joueur(), phoneM, false);
                    helper.assertTrue(s.sim() == PhoneSims.Validity.SUSPENDED && s.operatorName().equals("Susp Mairie")
                            && s.number().equals(numM) && s.bars() == 0, "suspendue : opérateur affiché, 0 barre : " + s);
                    // Suspendue, la ligne reste (le numéro est à elle) ; l'appeler échoue.
                    helper.assertTrue(PhoneNetwork.lineOf(srv, numM).isPresent(), "une suspendue garde sa ligne");
                    RelayFailure vers = echec(helper, PhoneNetwork.route(srv, fl.joueur(), phoneL, false, numM, ou(fm)));
                    helper.assertTrue(vers.reason() == RelayFailure.Reason.CALLEE_SIM_INACTIVE, "appelé : " + vers);
                    helper.assertTrue(enFrancais(helper, vers.describe()).equals("Injoignable · Carte suspendue"),
                            "texte : " + enFrancais(helper, vers.describe()));
                    // Désactivée : plus de ligne, numéro inconnu pour qui l'appelle.
                    helper.assertTrue(registre.setState(simM, SimCard.State.DEACTIVATED), "désactivation");
                    helper.assertTrue(PhoneNetwork.lineOf(srv, numM).isEmpty(), "une désactivée n'a pas de ligne");
                    RelayFailure inconnu = echec(helper,
                            PhoneNetwork.route(srv, fl.joueur(), phoneL, false, numM, ou(fm)));
                    helper.assertTrue(inconnu.reason() == RelayFailure.Reason.CALLEE_UNKNOWN, "désactivée : " + inconnu);
                    NetworkStatus d = PhoneNetwork.status(fm.joueur(), phoneM, false);
                    helper.assertTrue(d.sim() == PhoneSims.Validity.DEACTIVATED && d.bars() == 0 && d.number().isEmpty(),
                            "désactivée : " + d);
                    for (String faux : new String[] {"0600000001", "abc", ""}) {
                        if (registre.cardByNumber(faux).isEmpty()) {
                            RelayFailure x = echec(helper,
                                    PhoneNetwork.route(srv, fl.joueur(), phoneL, false, faux, ou(fm)));
                            helper.assertTrue(x.reason() == RelayFailure.Reason.CALLEE_UNKNOWN, faux + " : " + x);
                        }
                    }
                    helper.assertTrue(PhoneNetwork.lineOf(srv, numL).isPresent(), "témoin : Lagon garde sa ligne");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void leModeAvionCoupeTout(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_avion_m");
        JoueurDeTest fl = essai.joueur("rl_avion_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Avion Mairie", RESERVE);
        PhoneOperator lagon = essai.fonder(fl, B, "Avion Lagon", RESERVE);
        ItemStack phoneM = essai.ligne(mairie, fm);
        ItemStack phoneL = essai.ligne(lagon, fl);
        essai.placer(fm, A, 0);
        essai.placer(fl, B, 0);
        MinecraftServer srv = essai.serveur();
        String numL = numero(helper, srv, phoneL);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    NetworkStatus avion = PhoneNetwork.status(fm.joueur(), phoneM, true);
                    helper.assertTrue(avion.airplane() && avion.bars() == 0
                            && avion.operatorName().equals("Avion Mairie"), "mode avion : " + avion);
                    helper.assertTrue(PhoneNetwork.status(fm.joueur(), phoneM, false).bars() == 4, "témoin : 4 barres");
                    RelayFailure e = echec(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, true, numL, ou(fl)));
                    helper.assertTrue(e.reason() == RelayFailure.Reason.CALLER_AIRPLANE, "raison : " + e);
                    helper.assertTrue(enFrancais(helper, e.describe()).equals("Mode avion"), "texte");
                    trajet(helper, PhoneNetwork.route(srv, fm.joueur(), phoneM, false, numL, ou(fl)));
                    essai.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot relais_trafic : cartes comptées, trafic, messages, urgences, état du relais
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_TRAFIC, timeoutTicks = 200)
    public static void uneCarteCopieeNeCompteQuUneFois(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_copie");
        JoueurDeTest g = essai.joueur("rl_copie_b");
        PhoneOperator op = essai.fonder(f, A, "Copie Unique", RESERVE);
        ItemStack phoneA = essai.ligne(op, f);
        MinecraftServer srv = essai.serveur();
        String num = numero(helper, srv, phoneA);
        // La pile logée (jeton à jour) copiée et glissée dans un second téléphone.
        ItemStack phoneB = essai.glisser(g, PhoneItem.sim(phoneA).copy());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SimCard ligne = exiger(helper, PhoneNetwork.lineOf(srv, num), "la ligne du numéro");
                    helper.assertTrue(PhoneItem.phoneId(phoneB).equals(Optional.ofNullable(ligne.phoneId())),
                            "la ligne est dans le seul téléphone au bon jeton");
                    helper.assertTrue(PhoneSims.check(srv, phoneA) == PhoneSims.Validity.UNKNOWN
                            && PhoneSims.check(srv, phoneB) == PhoneSims.Validity.ACTIVE, "une seule carte vivante");
                    AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(A));
                    helper.assertTrue(d.clients() == PhoneConfig.antennaPerSimFe(),
                            "la carte ne compte qu'une fois : " + d);
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TRAFIC, timeoutTicks = 300)
    public static void laConsommationSuitVeilleCartesEtTrafic(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_conso");
        JoueurDeTest g = essai.joueur("rl_conso_b");
        PhoneOperator op = essai.fonder(f, A, "Conso", RESERVE);
        essai.ajouter(f, B, op);
        essai.placer(f, A, 0);
        essai.placer(g, B, 0);
        MinecraftServer srv = essai.serveur();
        ItemStack[] phones = new ItemStack[2];
        RelayPlanner.Route[] route = new RelayPlanner.Route[1];
        TrafficHandle[] poignees = new TrafficHandle[2];
        long[] mesure = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(p));
                        helper.assertTrue(d.total() == 40 && d.equals(new AntennaLoad.Breakdown(40, 0, 0)),
                                "veille seule en " + p + " : " + d);
                    }
                    phones[0] = essai.ligne(op, f);
                    phones[1] = essai.ligne(op, g);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(p));
                        helper.assertTrue(d.total() == 52, "2 cartes en " + p + " : 52 attendus, " + d);
                    }
                    route[0] = trajet(helper, PhoneNetwork.route(srv, f.joueur(), phones[0], false,
                            numero(helper, srv, phones[1]), ou(g)));
                    helper.assertTrue(route[0].caller().id().equals(essai.antenne(A).antennaId())
                            && route[0].callee().id().equals(essai.antenne(B).antennaId()), "trajet A → B");
                    poignees[0] = essai.ouvrir(route[0], TrafficKind.CALL);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(p));
                        helper.assertTrue(d.total() == 61 && d.traffic() == 9, "un appel en " + p + " : 61, " + d);
                    }
                    mesure[0] = essai.antenne(A).energy().getEnergyStored();
                    mesure[1] = helper.getLevel().getGameTime();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    long tics = helper.getLevel().getGameTime() - mesure[1];
                    long paye = mesure[0] - essai.antenne(A).energy().getEnergyStored();
                    helper.assertTrue(tics > 0 && paye == 61L * tics, "61 FE par tic : " + paye + " en " + tics);
                    poignees[1] = essai.ouvrir(route[0], TrafficKind.FACETIME);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(p));
                        helper.assertTrue(d.total() == 85, "appel et FaceTime en " + p + " : 85, " + d);
                    }
                    poignees[0].close();
                    poignees[1].close();
                    poignees[0].close();
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        AntennaLoad.Breakdown d = dernier(helper, essai, essai.antenne(p));
                        helper.assertTrue(d.total() == 52, "poignées fermées en " + p + " : 52, " + d);
                    }
                    // Les deux bouts sous la même antenne : l'appel n'y compte qu'une fois.
                    essai.placer(g, A, 0);
                    RelayPlanner.Route seule = trajet(helper, PhoneNetwork.route(srv, f.joueur(), phones[0], false,
                            numero(helper, srv, phones[1]), ou(g)));
                    helper.assertTrue(seule.caller().id().equals(seule.callee().id()), "même antenne : " + seule);
                    poignees[0] = essai.ouvrir(seule, TrafficKind.CALL);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    AntennaLoad.Breakdown a = dernier(helper, essai, essai.antenne(A));
                    AntennaLoad.Breakdown b = dernier(helper, essai, essai.antenne(B));
                    helper.assertTrue(a.total() == 61 && b.total() == 52, "une fois sur A, rien sur B : " + a + " " + b);
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TRAFIC, timeoutTicks = 200)
    public static void unMessageCouteUneFois(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_msg");
        JoueurDeTest g = essai.joueur("rl_msg_b");
        PhoneOperator op = essai.fonder(f, A, "Message", RESERVE);
        essai.ajouter(f, B, op);
        ItemStack phoneF = essai.ligne(op, f);
        ItemStack phoneG = essai.ligne(op, g);
        essai.placer(f, A, 0);
        essai.placer(g, B, 0);
        MinecraftServer srv = essai.serveur();
        String numG = numero(helper, srv, phoneG);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)), "les deux antennes en service"))
                .thenExecute(() -> {
                    int cout = PhoneConfig.antennaMessageFe();
                    helper.assertTrue(cout == 100, "100 FE par message : " + cout);
                    RelayPlanner.Route r = trajet(helper, PhoneNetwork.route(srv, f.joueur(), phoneF, false, numG, ou(g)));
                    int a = essai.antenne(A).energy().getEnergyStored();
                    int b = essai.antenne(B).energy().getEnergyStored();
                    PhoneNetwork.messageSent(srv, r);
                    int a2 = essai.antenne(A).energy().getEnergyStored();
                    int b2 = essai.antenne(B).energy().getEnergyStored();
                    helper.assertTrue(a - a2 == 100 && b - b2 == 100, "100 FE sur chaque antenne : A " + (a - a2)
                            + ", B " + (b - b2));
                    // Les deux bouts sous la même antenne : retirés une seule fois.
                    essai.placer(g, A, 0);
                    RelayPlanner.Route seule = trajet(helper,
                            PhoneNetwork.route(srv, f.joueur(), phoneF, false, numG, ou(g)));
                    PhoneNetwork.messageSent(srv, seule);
                    int a3 = essai.antenne(A).energy().getEnergyStored();
                    int b3 = essai.antenne(B).energy().getEnergyStored();
                    helper.assertTrue(a2 - a3 == 100 && b3 == b2, "une seule fois sur A : " + (a2 - a3) + ", B "
                            + (b2 - b3));
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TRAFIC, timeoutTicks = 200)
    public static void uneUrgencePasseParNimporteQuelleAntenne(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest f = essai.joueur("rl_urg");
        JoueurDeTest h = essai.joueur("rl_urg_h");
        PhoneOperator op = essai.fonder(f, A, "Urgence", RESERVE);
        essai.placer(h, A, 0);
        MinecraftServer srv = essai.serveur();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A)), "antenne en service"))
                .thenExecute(() -> {
                    UUID antenne = essai.antenne(A).antennaId();
                    // Sans carte ni téléphone : l'urgence passe par n'importe quelle antenne en service.
                    NetworkStatus s = PhoneNetwork.status(h.joueur(), ItemStack.EMPTY, false);
                    helper.assertTrue(s.sim() == PhoneSims.Validity.NONE && s.bars() == 0, "sans carte : " + s);
                    RelayPlanner.AntennaPoint p = exiger(helper, PhoneNetwork.emergencyAntenna(srv, ou(h)),
                            "une antenne d'urgence");
                    helper.assertTrue(p.id().equals(antenne) && p.inService(), "l'antenne la plus proche : " + p);
                    essai.placer(h, A, 1_000);
                    helper.assertTrue(PhoneNetwork.emergencyAntenna(srv, ou(h)).isEmpty(), "hors de toute portée");
                    essai.placer(h, A, 0);
                    // Un opérateur supprimé ne consomme plus : son antenne ne relaie plus rien, pas même une urgence.
                    helper.assertTrue(essai.registre().remove(op.id()), "opérateur retiré");
                    helper.assertTrue(PhoneNetwork.emergencyAntenna(srv, ou(h)).map(RelayPlanner.AntennaPoint::id)
                            .filter(antenne::equals).isEmpty(), "l'antenne d'un opérateur supprimé");
                    essai.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TRAFIC, timeoutTicks = 200)
    public static void lEtatDuRelais(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        JoueurDeTest fm = essai.joueur("rl_etat_m");
        JoueurDeTest fl = essai.joueur("rl_etat_l");
        PhoneOperator mairie = essai.fonder(fm, A, "Etat Mairie", RESERVE);
        essai.ajouter(fm, C, mairie);
        PhoneOperator lagon = essai.fonder(fl, B, "Etat Lagon", RESERVE);
        MinecraftServer srv = essai.serveur();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(essai, essai.antenne(A))
                        && enService(essai, essai.antenne(B)) && enService(essai, essai.antenne(C)),
                        "les trois antennes en service"))
                .thenExecute(() -> {
                    helper.assertTrue(PhoneNetwork.relayStates(srv).contains(
                            new PhoneNetwork.RelayState(mairie.id(), "Etat Mairie", 2, 2)), "Mairie 2/2");
                    helper.assertTrue(PhoneNetwork.relayStates(srv).contains(
                            new PhoneNetwork.RelayState(lagon.id(), "Etat Lagon", 1, 1)), "Lagon 1/1");
                    essai.antenne(B).drainEnergy(Integer.MAX_VALUE);
                })
                .thenWaitUntil(() -> helper.assertTrue(!enService(essai, essai.antenne(B)), "Lagon coupée"))
                .thenExecute(() -> {
                    List<PhoneNetwork.RelayState> etats = PhoneNetwork.relayStates(srv);
                    helper.assertTrue(etats.contains(new PhoneNetwork.RelayState(mairie.id(), "Etat Mairie", 2, 2))
                            && etats.contains(new PhoneNetwork.RelayState(lagon.id(), "Etat Lagon", 1, 0)),
                            "Mairie 2/2, Lagon 1/0 : " + etats);
                    essai.fermer();
                })
                .thenSucceed();
    }
}
