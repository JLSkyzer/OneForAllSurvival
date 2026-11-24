package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneService.Outcome;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimNumbers;
import dev.oneforallsurvival.phone.net.SimRequest;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Le téléphone en réseau, côté serveur (lot T2, tâche 7) : la vue réseau envoyée avec l'état du téléphone (carte,
 * opérateur, barres, numéro, titulaire, cartes de l'inventaire, opérateurs demandables, appli « Antenne »), réduite à la
 * carte, l'opérateur et les barres hors {@code UNLOCKED} ; le signal rafraîchi sans jamais renvoyer la même vue ; le mode
 * avion qui prend effet ; les gestes de carte (glisser, retirer, demander) rejoués par le serveur ; le protocole « 11 ».
 *
 * <p>Les états sont décodés des octets réellement envoyés ({@link JoueurDeTest#etatsTelephoneRecus}). Les gestes d'un
 * même tic oublient le frein entre deux ({@code PhoneService.clearThrottle}, par réflexion), sauf l'essai du frein.
 * Chaque essai fonde ses opérateurs sous des noms qui lui sont propres (leçon du 2026-11-24) ; les joueurs sont replacés
 * par {@code teleportTo} après l'entrée (leçon du 2026-11-14) ; dans les séquences, aucune exception autre qu'une
 * assertion, et l'essai ne se range qu'à sa dernière étape (leçon du 2026-11-20) ; un essai rouge est rangé par
 * l'{@code @AfterBatch} de son lot (joueurs déconnectés, opérateurs oubliés, téléphones effacés).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TelephoneReseauGameTests {

    private static final String LOT_RESEAU = "telephone_reseau";
    private static final String LOT_SIM = "telephone_sim";

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final Vec3 JOUEUR = new Vec3(3.5, 0, 3.5);
    private static final int RESERVE = 40_000;
    private static final String CODE = "4821";

    /** Les essais du lot en cours : un essai rouge ne se range pas lui-même ; {@link #apres} le fait. Fil du serveur. */
    private static final List<Essai> OUVERTS = new ArrayList<>();

    private TelephoneReseauGameTests() {}

    @AfterBatch(batch = LOT_RESEAU)
    public static void apresReseau(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_SIM)
    public static void apresSim(ServerLevel level) {
        apres();
    }

    private static void apres() {
        for (Essai e : new ArrayList<>(OUVERTS)) {
            e.rendre();
        }
        OUVERTS.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Ce qu'un essai a ouvert : joueurs, opérateurs, téléphones ; tout est rendu à la fermeture, même après un échec. */
    private static final class Essai {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> fondateurs = new ArrayList<>();
        private final Set<UUID> telephones = new HashSet<>();
        private boolean rendu;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            OUVERTS.add(this);
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

        /** Un joueur neuf en survie, sans opérateur, replacé dans le gabarit. */
        JoueurDeTest joueur(String nom) {
            Vec3 ici = helper.absoluteVec(JOUEUR);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, ici);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            fondateurs.add(j.uuid());
            OperateurDEssai.oublier(serveur(), j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            return j;
        }

        /** Une antenne posée par {@code j} en {@link #A}, fondée sous {@code nom}, avec {@code fe} FE en réserve. */
        PhoneOperator fonder(JoueurDeTest j, String nom, int fe) {
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            ServerPlayer p = j.joueur();
            ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            BlockPos sol = helper.absolutePos(A.below());
            BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
            InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
            p.setItemInHand(InteractionHand.MAIN_HAND, avant);
            helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(p, helper.absolutePos(A), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            int reste = fe;
            while (reste > 0) {
                int recu = antenne().energy().receiveEnergy(reste, false);
                if (recu <= 0) {
                    break;
                }
                reste -= recu;
            }
            return fonde.get().operator();
        }

        AntennaBlockEntity antenne() {
            helper.assertTrue(helper.getBlockEntity(A) instanceof AntennaBlockEntity, "pas d'antenne en " + A);
            return (AntennaBlockEntity) helper.getBlockEntity(A);
        }

        boolean enService() {
            AntennaBlockEntity be = antenne();
            Optional<AntennaRecord> r = be.antennaId() == null ? Optional.empty() : registre().antenna(be.antennaId());
            return r.isPresent() && AntennaBlockEntity.inService(r.get(), helper.getLevel().getGameTime());
        }

        /** Un téléphone chargé dans la main principale de {@code j} ; la pile de la main. */
        ItemStack telephone(JoueurDeTest j) {
            ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(phone, PhoneConfig.capacity());
            telephones.add(PhoneItem.ensurePhoneId(phone));
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
            return j.joueur().getItemInHand(InteractionHand.MAIN_HAND);
        }

        /** Une carte de {@code op} émise au nom de {@code titulaire}, posée dans la case {@code slot} de {@code a}. */
        ItemStack carte(PhoneOperator op, JoueurDeTest titulaire, JoueurDeTest a, int slot) {
            ItemStack emise = OperateurDEssai.emettre(helper, op, titulaire);
            Optional<UUID> simId = SimCardItem.simId(emise);
            helper.assertTrue(simId.isPresent(), "carte émise sans identifiant");
            ServerPlayer fondateur = serveur().getPlayerList().getPlayer(op.founder());
            int ou = caseDe(fondateur, simId.get());
            helper.assertTrue(ou >= 0, "la carte est chez le fondateur");
            ItemStack pile = fondateur.getInventory().getItem(ou);
            fondateur.getInventory().setItem(ou, ItemStack.EMPTY);
            a.joueur().getInventory().setItem(slot, pile);
            return a.joueur().getInventory().getItem(slot);
        }

        /** Une ligne de {@code op} : la carte de {@code j}, glissée dans un téléphone neuf tenu en main. */
        ItemStack ligne(PhoneOperator op, JoueurDeTest j) {
            ItemStack phone = telephone(j);
            carte(op, j, j, 9);
            PhoneSims.Insert r = PhoneSims.insert(j.joueur(), phone, 9);
            helper.assertTrue(r == PhoneSims.Insert.DONE, "insertion : " + r);
            return phone;
        }

        /** Le joueur se tient à {@code dx} blocs à l'est de l'antenne. */
        void placer(JoueurDeTest j, int dx) {
            BlockPos ici = helper.absolutePos(A).offset(dx, 0, 0);
            j.joueur().teleportTo(ici.getX() + 0.5, ici.getY(), ici.getZ() + 0.5);
        }

        /** Ferme tout ce que l'essai a ouvert (à sa dernière étape, ou par l'{@code @AfterBatch}). Idempotent. */
        void rendre() {
            if (rendu) {
                return;
            }
            rendu = true;
            OUVERTS.remove(this);
            MinecraftServer serveur = serveur();
            PhoneOperators registre = registre();
            for (UUID f : fondateurs) {
                registre.byFounder(f).ifPresent(op -> {
                    for (SimRequest d : registre.requestsOf(op.id())) {
                        registre.takeRequest(d.id());
                    }
                });
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    PhoneService.close(j.joueur(), CloseReason.CLOSED);
                    j.joueur().getInventory().clearContent();
                    j.quitter();
                }
                registre.takeNotices(j.uuid());
            }
            for (UUID f : fondateurs) {
                OperateurDEssai.oublier(serveur, f);
            }
            PhoneData data = PhoneData.get(serveur);
            telephones.forEach(data::erase);
        }

        /** Rangement de la dernière étape : l'antenne retirée du gabarit, puis le reste. */
        void fermer() {
            helper.setBlock(A, Blocks.AIR);
            rendre();
        }
    }

    /** La valeur, ou un échec d'assertion (jamais {@code orElseThrow}, leçon du 2026-11-20). */
    private static <T> T exiger(GameTestHelper helper, Optional<T> valeur, String message) {
        helper.assertTrue(valeur.isPresent(), message);
        return valeur.get();
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

    /** Ouvre le téléphone tenu en main principale et rend le seul {@code OPEN} reçu. */
    private static PhoneStatePacket ouvrir(GameTestHelper helper, JoueurDeTest j) {
        j.etatsTelephoneRecus();
        Outcome r = PhoneService.open(j.joueur(), InteractionHand.MAIN_HAND);
        helper.assertTrue(r == Outcome.OPENED, "ouverture : " + r);
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        helper.assertTrue(recus.size() == 1 && recus.get(0).kind() == Kind.OPEN, "un OPEN attendu, vu " + recus);
        return recus.get(0);
    }

    private static int session(GameTestHelper helper, JoueurDeTest j) {
        Optional<PhoneSession> s = PhoneService.session(j.uuid());
        helper.assertTrue(s.isPresent(), "session ouverte attendue");
        return s.isPresent() ? s.get().id() : -1;
    }

    /** Oublie le frein du joueur ({@code PhoneService.clearThrottle}, de visibilité paquet) par réflexion. */
    private static void oublierFrein(GameTestHelper helper, UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            helper.fail("frein du téléphone introuvable : " + e);
        }
    }

    /** Un geste et sa réponse : l'issue et le dernier état reçu (ou nul). */
    private record Reponse(Outcome outcome, @Nullable PhoneStatePacket etat) {}

    private static Reponse geste(GameTestHelper helper, JoueurDeTest j, Action action, String first, boolean flag) {
        return geste(helper, j, new PhoneActionPacket(session(helper, j), action, first, "", flag), true);
    }

    private static Reponse geste(GameTestHelper helper, JoueurDeTest j, Action action, String first) {
        return geste(helper, j, action, first, false);
    }

    private static Reponse geste(GameTestHelper helper, JoueurDeTest j, PhoneActionPacket packet,
            boolean oublierFrein) {
        j.etatsTelephoneRecus();
        if (oublierFrein) {
            oublierFrein(helper, j.uuid());
        }
        Outcome r = PhoneService.act(j.joueur(), packet);
        List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
        return new Reponse(r, recus.isEmpty() ? null : recus.get(recus.size() - 1));
    }

    /** L'état de la réponse, de ce verrou et de cette issue ; échoue sinon (jamais d'autre exception). */
    private static PhoneStatePacket attendre(GameTestHelper helper, Reponse r, Lock lock, Result last, String quoi) {
        helper.assertTrue(r.etat() != null, quoi + " : aucun état reçu (issue " + r.outcome() + ")");
        PhoneStatePacket e = r.etat();
        helper.assertTrue(e.kind() == Kind.UPDATE && e.lock() == lock && e.last() == last,
                quoi + " : attendu UPDATE/" + lock + "/" + last + ", vu " + e.kind() + "/" + e.lock() + "/" + e.last());
        return e;
    }

    /** Ouvre le téléphone neuf de {@code j} et choisit {@link #CODE} : la vue déverrouillée. */
    private static PhoneStatePacket deverrouiller(GameTestHelper helper, JoueurDeTest j) {
        ouvrir(helper, j);
        return attendre(helper, geste(helper, j, Action.SETUP, CODE), Lock.UNLOCKED, Result.NONE, "SETUP");
    }

    /** L'état sans l'heure (qui change à chaque envoi) : deux états égaux ainsi montrent la même chose. */
    private static PhoneStatePacket sansHeure(PhoneStatePacket p) {
        return new PhoneStatePacket(p.session(), p.kind(), p.lock(), p.battery(), 0L, p.zoneOffsetSeconds(),
                p.wallpaper(), p.airplane(), p.owner(), p.ownerName(), p.attemptsLeft(), p.lockoutSeconds(), p.last(),
                p.reason(), p.net());
    }

    /** Deux états de suite qui montrent la même chose : un envoi pour rien (spam). */
    private static void jamaisDeuxFoisLaMeme(GameTestHelper helper, List<PhoneStatePacket> recus) {
        for (int i = 1; i < recus.size(); i++) {
            helper.assertTrue(!sansHeure(recus.get(i)).equals(sansHeure(recus.get(i - 1))),
                    "le même état envoyé deux fois de suite (n° " + i + " sur " + recus.size() + ") : " + recus.get(i));
        }
    }

    /** Le contenu d'un inventaire, case par case, pour vérifier que rien n'a bougé. */
    private static List<ItemStack> copie(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(inv.getItem(i).copy());
        }
        return out;
    }

    private static boolean pareil(List<ItemStack> avant, ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!ItemStack.matches(avant.get(i), inv.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    /** Les octets que ce paquet met sur le réseau (décodé puis réécrit : la lecture consomme tout, c'est le même). */
    private static byte[] octets(PhoneStatePacket p) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            p.encode(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static boolean contient(byte[] tas, byte[] aiguille) {
        outer:
        for (int i = 0; i + aiguille.length <= tas.length; i++) {
            for (int k = 0; k < aiguille.length; k++) {
                if (tas[i + k] != aiguille[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** Le jeton de cette carte n'est dans les octets envoyés ni en binaire, ni en texte (même tronqué). */
    private static void sansJeton(GameTestHelper helper, PhoneStatePacket p, SimCard carte) {
        byte[] envoye = octets(p);
        UUID jeton = carte.token();
        byte[] binaire = ByteBuffer.allocate(16).putLong(jeton.getMostSignificantBits())
                .putLong(jeton.getLeastSignificantBits()).array();
        String texte = new String(envoye, StandardCharsets.ISO_8859_1);
        helper.assertTrue(!contient(envoye, Arrays.copyOf(binaire, 8)) && !contient(envoye, Arrays.copyOfRange(binaire, 8, 16))
                && !texte.contains(jeton.toString().substring(0, 8))
                && !texte.contains(jeton.toString().substring(28)), "le jeton de la carte est parti au client");
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_reseau : la vue réseau, le signal, le mode avion, l'appli « Antenne », le protocole
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_RESEAU)
    public static void unTelephoneDuLotT1MontreAucuneCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest j = e.joueur("tr_t1");
        e.telephone(j);
        PhoneStatePacket open = ouvrir(helper, j);
        helper.assertTrue(open.net().sim() == PhoneSims.Validity.NONE && open.net().bars() == 0
                && open.net().operator().isEmpty(), "téléphone neuf sans carte : " + open.net());
        PhoneStatePacket vue = deverrouiller(helper, j);
        PhoneNetView n = vue.net();
        helper.assertTrue(n.sim() == PhoneSims.Validity.NONE && n.bars() == 0 && n.operator().isEmpty()
                && n.number().isEmpty() && n.holder().isEmpty() && !n.founderApp() && n.choices().isEmpty(),
                "déverrouillé, sans carte : aucune carte, 0 barre, rien à choisir : " + n);
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 200)
    public static void leSignalSAfficheALOuverture(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_ouverture");
        PhoneOperator op = e.fonder(f, "Reseau Ouverture", RESERVE);
        e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 100);
                    PhoneStatePacket open = ouvrir(helper, f);
                    PhoneNetView n = open.net();
                    helper.assertTrue(n.bars() == 3, "à 100 blocs, 3 barres à l'ouverture, vu " + n.bars());
                    helper.assertTrue(n.sim() == PhoneSims.Validity.ACTIVE && n.operator().equals(op.name()),
                            "carte active de " + op.name() + " : " + n);
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 300)
    public static void leSignalSuitLeJoueur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_suit");
        PhoneOperator op = e.fonder(f, "Reseau Suit", RESERVE);
        e.ligne(op, f);
        List<PhoneStatePacket> recus = new ArrayList<>();
        long[] depart = {-1L};
        long[] vu = {-1L};
        int rafraichi = PhoneConfig.signalRefreshTicks();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    PhoneStatePacket vue = deverrouiller(helper, f);
                    helper.assertTrue(vue.net().bars() == 4, "sur l'antenne, 4 barres, vu " + vue.net().bars());
                    recus.add(vue);
                    e.placer(f, 300);
                    depart[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    recus.addAll(f.etatsTelephoneRecus());
                    if (vu[0] < 0 && recus.stream().anyMatch(p -> p.kind() == Kind.UPDATE && p.net().bars() == 0)) {
                        vu[0] = helper.getLevel().getGameTime();
                    }
                    helper.assertTrue(vu[0] >= 0, "à 300 blocs, un UPDATE à 0 barre attendu");
                })
                .thenIdle(3 * rafraichi)
                .thenExecute(() -> {
                    recus.addAll(f.etatsTelephoneRecus());
                    helper.assertTrue(vu[0] - depart[0] <= rafraichi + 1,
                            "le signal devait suivre en " + rafraichi + " tics au plus, vu " + (vu[0] - depart[0]));
                    jamaisDeuxFoisLaMeme(helper, recus);
                    helper.assertTrue(recus.get(recus.size() - 1).net().bars() == 0, "toujours 0 barre à 300 blocs");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 200)
    public static void verrouilleOnVoitLOperateurEtLesBarresSeulement(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_verrou");
        PhoneOperator op = e.fonder(f, "Reseau Verrou", RESERVE);
        ItemStack phone = e.ligne(op, f);
        // Une seconde carte dans l'inventaire : elle figurerait dans les choix si la vue verrouillée les portait.
        e.carte(op, f, f, 5);
        List<PhoneStatePacket> recus = new ArrayList<>();
        long[] change = {-1L};
        int rafraichi = PhoneConfig.signalRefreshTicks();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    PhoneStatePacket ouverte = deverrouiller(helper, f);
                    PhoneNetView n = ouverte.net();
                    helper.assertTrue(!n.number().isEmpty() && n.holder().equals(f.joueur().getGameProfile().getName())
                            && n.choices().size() == 1 && n.founderApp(),
                            "témoin déverrouillé : numéro, titulaire, une carte au choix, appli : " + n);
                    PhoneStatePacket ferme = attendre(helper, geste(helper, f, Action.LOCK, ""), Lock.LOCKED,
                            Result.NONE, "LOCK");
                    PhoneStatePacket rouvert = ouvrir(helper, f);
                    for (PhoneStatePacket p : new PhoneStatePacket[] {ferme, rouvert}) {
                        PhoneNetView v = p.net();
                        helper.assertTrue(v.operator().equals(op.name()) && v.bars() == 4,
                                "verrouillé : l'opérateur et les barres se voient : " + v);
                        helper.assertTrue(v.number().isEmpty() && v.holder().isEmpty() && !v.founderApp()
                                && v.choices().isEmpty() && v.requestable().isEmpty(),
                                "verrouillé : ni numéro, ni titulaire, ni cartes, ni opérateurs, ni appli : " + v);
                        helper.assertTrue(p.ownerName().isEmpty() && !p.owner(), "rien du propriétaire : " + p);
                    }
                    // Déverrouillé de nouveau, puis le code change sous la session (sel neuf, posé sans prévenir les
                    // sessions) : au relevé suivant, la session est reverrouillée et ne reçoit que la vue réduite.
                    PhoneStatePacket encore = attendre(helper, geste(helper, f, Action.UNLOCK, CODE), Lock.UNLOCKED,
                            Result.NONE, "UNLOCK");
                    helper.assertTrue(!encore.net().number().isEmpty() && encore.net().choices().size() == 1,
                            "témoin déverrouillé de nouveau : " + encore.net());
                })
                // Le premier bloc de batterie de la session payé (100 % puis 99 %, un UPDATE qui relit le sel) : le
                // relevé du signal sera seul à pouvoir reverrouiller (prochain point de batterie dans 720 tics).
                .thenIdle(3)
                .thenExecute(() -> {
                    Optional<UUID> id = PhoneItem.phoneId(phone);
                    helper.assertTrue(id.isPresent(), "téléphone sans identifiant");
                    PhoneData data = PhoneData.get(e.serveur());
                    PhoneData.PhoneRecord r = data.record(id.isPresent() ? id.get() : new UUID(0, 0));
                    data.put(id.isPresent() ? id.get() : new UUID(0, 0), new PhoneData.PhoneRecord(r.owner(),
                            r.ownerName(), "selneuf", r.codeHash(), r.failures(), r.lockedUntil(), r.wallpaper(),
                            r.airplane()));
                    f.etatsTelephoneRecus();
                    change[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    recus.addAll(f.etatsTelephoneRecus());
                    helper.assertTrue(recus.stream().anyMatch(p -> p.lock() == Lock.LOCKED),
                            "sel changé : un UPDATE verrouillé attendu");
                })
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().getGameTime() - change[0] <= rafraichi + 1,
                            "reverrouillé au relevé suivant (" + rafraichi + " tics), vu "
                                    + (helper.getLevel().getGameTime() - change[0]));
                    // Un UPDATE de batterie a pu partir avant le relevé (encore déverrouillé) : seuls comptent ceux d'après.
                    int premier = 0;
                    while (premier < recus.size() && recus.get(premier).lock() != Lock.LOCKED) {
                        premier++;
                    }
                    for (PhoneStatePacket p : recus.subList(premier, recus.size())) {
                        PhoneNetView v = p.net();
                        helper.assertTrue(p.lock() == Lock.LOCKED && v.number().isEmpty() && v.holder().isEmpty()
                                && v.choices().isEmpty() && !v.founderApp() && v.operator().equals(op.name()),
                                "après le changement de sel, seulement la vue réduite : " + p);
                    }
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 200)
    public static void leModeAvionPrendEffet(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_avion");
        PhoneOperator op = e.fonder(f, "Reseau Avion", RESERVE);
        ItemStack phone = e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    PhoneStatePacket vue = deverrouiller(helper, f);
                    helper.assertTrue(vue.net().bars() == 4 && vue.net().founderApp() && !vue.airplane(),
                            "témoin : 4 barres, appli, hors mode avion : " + vue);
                    PhoneStatePacket avion = attendre(helper, geste(helper, f, Action.AIRPLANE, "", true),
                            Lock.UNLOCKED, Result.NONE, "AIRPLANE vrai");
                    helper.assertTrue(avion.airplane() && avion.net().bars() == 0,
                            "mode avion : 0 barre dans le même UPDATE, vu " + avion.net().bars());
                    helper.assertTrue(!avion.net().founderApp() && avion.net().requestable().isEmpty(),
                            "mode avion : ni appli ni opérateur à demander : " + avion.net());
                    helper.assertTrue(avion.net().operator().equals(op.name()) && !avion.net().number().isEmpty(),
                            "la ligne reste affichée : " + avion.net());
                    // La façade est d'accord : en mode avion, aucun trajet.
                    Optional<UUID> id = PhoneItem.phoneId(phone);
                    helper.assertTrue(id.isPresent(), "téléphone sans identifiant");
                    PhoneData.PhoneRecord rec = PhoneData.get(e.serveur()).record(id.isPresent() ? id.get() : new UUID(0, 0));
                    Optional<RelayFailure> refus = PhoneNetwork.route(e.serveur(), f.joueur(), phone, rec.airplane(),
                            "0600000001", GlobalPos.of(f.joueur().level().dimension(), f.joueur().blockPosition()))
                            .right();
                    helper.assertTrue(refus.isPresent() && refus.get().reason() == RelayFailure.Reason.CALLER_AIRPLANE,
                            "trajet en mode avion : " + refus);
                    helper.assertTrue(PhoneNetwork.status(f.joueur(), phone, rec.airplane()).bars() == 0,
                            "statut en mode avion : 0 barre");
                    attendre(helper, geste(helper, f, Action.REQUEST_SIM, op.id()), Lock.UNLOCKED, Result.NO_NETWORK,
                            "demande en mode avion");
                    attendre(helper, geste(helper, f, Action.OPEN_OPERATOR_APP, ""), Lock.UNLOCKED, Result.NO_NETWORK,
                            "appli en mode avion");
                    helper.assertTrue(e.registre().requestsOf(op.id()).isEmpty(), "aucune demande inscrite");
                    PhoneStatePacket retour = attendre(helper, geste(helper, f, Action.AIRPLANE, "", false),
                            Lock.UNLOCKED, Result.NONE, "AIRPLANE faux");
                    helper.assertTrue(!retour.airplane() && retour.net().bars() == 4,
                            "hors mode avion : 4 barres, vu " + retour.net().bars());
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 300)
    public static void laCoupureDeLAntenneSeVoit(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_coupure");
        PhoneOperator op = e.fonder(f, "Reseau Coupure", RESERVE);
        e.ligne(op, f);
        List<PhoneStatePacket> recus = new ArrayList<>();
        long[] coupure = {-1L};
        long[] vu = {-1L};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    PhoneStatePacket vue = deverrouiller(helper, f);
                    helper.assertTrue(vue.net().bars() == 4, "alimentée : 4 barres, vu " + vue.net().bars());
                    recus.add(vue);
                    // La source retirée : la réserve vidée, rien ne la remplit plus.
                    e.antenne().drainEnergy(Integer.MAX_VALUE);
                    coupure[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    recus.addAll(f.etatsTelephoneRecus());
                    if (vu[0] < 0 && recus.stream().anyMatch(p -> p.kind() == Kind.UPDATE && p.net().bars() == 0)) {
                        vu[0] = helper.getLevel().getGameTime();
                    }
                    helper.assertTrue(vu[0] >= 0, "antenne coupée : un UPDATE à 0 barre attendu");
                })
                .thenExecute(() -> {
                    helper.assertTrue(vu[0] - coupure[0] <= 60, "0 barre en 60 tics au plus, vu " + (vu[0] - coupure[0]));
                    jamaisDeuxFoisLaMeme(helper, recus);
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_RESEAU, timeoutTicks = 200)
    public static void lAppliAntenneNeSeVoitQueDuFondateur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("tr_app_f");
        JoueurDeTest c = e.joueur("tr_app_c");
        PhoneOperator op = e.fonder(f, "Reseau Appli", RESERVE);
        e.ligne(op, f);
        ItemStack phoneC = e.ligne(op, c);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    e.placer(c, 1);
                    PhoneStatePacket vueF = deverrouiller(helper, f);
                    helper.assertTrue(vueF.net().founderApp(), "le fondateur voit l'appli « Antenne » : " + vueF.net());
                    PhoneStatePacket vueC = deverrouiller(helper, c);
                    helper.assertTrue(!vueC.net().founderApp() && vueC.net().sim() == PhoneSims.Validity.ACTIVE,
                            "un abonné ne voit pas l'appli : " + vueC.net());
                    // L'appli ne s'ouvre pas encore (tâche 9) : refus sans réseau, même pour le fondateur.
                    attendre(helper, geste(helper, f, Action.OPEN_OPERATOR_APP, ""), Lock.UNLOCKED, Result.NO_NETWORK,
                            "appli avant la tâche 9");
                    // Le fondateur déverrouille le téléphone de l'abonné avec son code : il voit la ligne, pas l'appli.
                    PhoneService.close(c.joueur(), CloseReason.CLOSED);
                    c.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    PhoneService.close(f.joueur(), CloseReason.CLOSED);
                    f.joueur().setItemInHand(InteractionHand.MAIN_HAND, phoneC);
                    PhoneStatePacket verrouille = ouvrir(helper, f);
                    helper.assertTrue(verrouille.lock() == Lock.LOCKED, "téléphone de l'abonné verrouillé");
                    PhoneStatePacket autre = attendre(helper, geste(helper, f, Action.UNLOCK, CODE), Lock.UNLOCKED,
                            Result.NONE, "UNLOCK par un autre");
                    PhoneNetView n = autre.net();
                    helper.assertTrue(!autre.owner() && !n.founderApp(), "pas le propriétaire : pas d'appli : " + n);
                    helper.assertTrue(n.holder().equals(c.joueur().getGameProfile().getName()) && !n.number().isEmpty(),
                            "il voit la ligne du téléphone : " + n);
                    e.fermer();
                })
                .thenSucceed();
    }

    /**
     * Le canal du mod est au protocole « 11 » (une seule montée pour le lot T2) : un client ou un serveur du protocole
     * « 10 » (lot T1) est refusé par Forge à la connexion, celui du « 11 » accepté.
     */
    @GameTest(template = "test/plancher", batch = LOT_RESEAU)
    public static void unClientDuProtocole10EstRefuse(GameTestHelper helper) {
        try {
            Field champ = SimpleChannel.class.getDeclaredField("instance");
            champ.setAccessible(true);
            Object instance = champ.get(BenchNetwork.CHANNEL);
            Method version = instance.getClass().getDeclaredMethod("getNetworkProtocolVersion");
            Method client = instance.getClass().getDeclaredMethod("tryClientVersionOnServer", String.class);
            Method serveur = instance.getClass().getDeclaredMethod("tryServerVersionOnClient", String.class);
            version.setAccessible(true);
            client.setAccessible(true);
            serveur.setAccessible(true);
            helper.assertTrue("11".equals(version.invoke(instance)), "protocole « 11 » attendu, vu "
                    + version.invoke(instance));
            helper.assertTrue(!(Boolean) client.invoke(instance, "10") && !(Boolean) serveur.invoke(instance, "10"),
                    "un client ou un serveur du protocole « 10 » doit être refusé");
            helper.assertTrue((Boolean) client.invoke(instance, "11") && (Boolean) serveur.invoke(instance, "11"),
                    "le protocole « 11 » est accepté");
        } catch (ReflectiveOperationException ex) {
            helper.fail("canal du mod illisible : " + ex);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_sim : glisser, retirer, demander, sessions et verrou
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void glisserParLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_gl_f");
        JoueurDeTest h = e.joueur("ts_gl_h");
        PhoneOperator op = e.fonder(f, "Sim Glisser", RESERVE);
        ItemStack phone = e.telephone(h);
        ItemStack carte = e.carte(op, h, h, 5);
        UUID simId = exiger(helper, SimCardItem.simId(carte), "carte sans identifiant");
        PhoneStatePacket vue = deverrouiller(helper, h);
        SimCard avant = exiger(helper, e.registre().card(simId), "carte absente du registre");
        String numero = SimNumbers.format(avant.number());
        // Ni la carte proposée, ni la carte logée ensuite ne fait partir son jeton.
        sansJeton(helper, vue, avant);
        helper.assertTrue(vue.net().choices().size() == 1 && vue.net().choices().get(0).slot() == 5
                && vue.net().choices().get(0).number().equals(numero)
                && vue.net().choices().get(0).validity() == PhoneSims.Validity.ACTIVE,
                "la carte de la case 5 est proposée : " + vue.net().choices());
        Reponse r = geste(helper, h, Action.INSERT_SIM, "5");
        PhoneStatePacket apres = attendre(helper, r, Lock.UNLOCKED, Result.SIM_INSERTED, "INSERT_SIM 5");
        helper.assertTrue(r.outcome() == Outcome.DONE, "geste fait : " + r.outcome());
        sansJeton(helper, apres, exiger(helper, e.registre().card(simId), "carte absente du registre"));
        PhoneNetView n = apres.net();
        helper.assertTrue(n.sim() == PhoneSims.Validity.ACTIVE && n.operator().equals(op.name())
                && n.number().equals(numero) && n.holder().equals(h.joueur().getGameProfile().getName())
                && n.choices().isEmpty(), "vue après insertion : " + n);
        helper.assertTrue(h.joueur().getInventory().getItem(5).isEmpty(), "la case 5 est vide");
        helper.assertTrue(PhoneSims.check(e.serveur(), phone) == PhoneSims.Validity.ACTIVE, "la carte est logée");
        // Le frein : un second geste dans le même tic est ignoré, sans réponse ni effet.
        Reponse vite = geste(helper, h, new PhoneActionPacket(session(helper, h), Action.REMOVE_SIM, "", "", false),
                false);
        helper.assertTrue(vite.outcome() == Outcome.THROTTLED && vite.etat() == null,
                "geste trop tôt : ignoré, vu " + vite.outcome());
        helper.assertTrue(PhoneSims.check(e.serveur(), phone) == PhoneSims.Validity.ACTIVE, "la carte reste logée");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void uneCaseSansCarteNeFaitRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_vide_f");
        JoueurDeTest h = e.joueur("ts_vide_h");
        PhoneOperator op = e.fonder(f, "Sim Vide", RESERVE);
        ItemStack phone = e.telephone(h);
        e.carte(op, h, h, 5);
        h.joueur().getInventory().setItem(6, new ItemStack(Items.STONE, 3));
        deverrouiller(helper, h);
        List<ItemStack> avant = copie(h.joueur());
        for (String slot : new String[] {"6", "7"}) {
            Reponse r = geste(helper, h, Action.INSERT_SIM, slot);
            attendre(helper, r, Lock.UNLOCKED, Result.SIM_UNKNOWN, "INSERT_SIM " + slot);
            helper.assertTrue(r.outcome() == Outcome.REFUSED, "refusé : " + r.outcome());
        }
        // Une case mal écrite (client trafiqué) : refusée, rien ne bouge.
        for (String slot : new String[] {"41", "-1", "05", "5 ", "abc", ""}) {
            Reponse r = geste(helper, h, Action.INSERT_SIM, slot);
            helper.assertTrue(r.outcome() == Outcome.REFUSED, "case « " + slot + " » : refusée, vu " + r.outcome());
        }
        helper.assertTrue(pareil(avant, h.joueur()), "l'inventaire n'a pas bougé");
        helper.assertTrue(PhoneItem.sim(phone).isEmpty(), "aucune carte logée");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void retirerParLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_ret_f");
        JoueurDeTest h = e.joueur("ts_ret_h");
        PhoneOperator op = e.fonder(f, "Sim Retirer", RESERVE);
        ItemStack phone = e.telephone(h);
        UUID simId = exiger(helper, SimCardItem.simId(e.carte(op, h, h, 5)), "carte sans identifiant");
        deverrouiller(helper, h);
        attendre(helper, geste(helper, h, Action.INSERT_SIM, "5"), Lock.UNLOCKED, Result.SIM_INSERTED, "INSERT_SIM");
        Reponse r = geste(helper, h, Action.REMOVE_SIM, "");
        PhoneStatePacket apres = attendre(helper, r, Lock.UNLOCKED, Result.SIM_REMOVED, "REMOVE_SIM");
        helper.assertTrue(apres.net().sim() == PhoneSims.Validity.NONE && apres.net().number().isEmpty()
                && apres.net().choices().size() == 1, "vue après retrait : " + apres.net());
        helper.assertTrue(PhoneItem.sim(phone).isEmpty() && caseDe(h.joueur(), simId) >= 0,
                "la carte est revenue dans l'inventaire");
        helper.assertTrue(exiger(helper, e.registre().card(simId), "carte absente du registre").phoneId() == null, "la carte est déliée du téléphone");
        // Inventaire plein : la carte reste dans le téléphone.
        int ou = caseDe(h.joueur(), simId);
        attendre(helper, geste(helper, h, Action.INSERT_SIM, Integer.toString(ou)), Lock.UNLOCKED, Result.SIM_INSERTED,
                "INSERT_SIM de nouveau");
        Inventory inv = h.joueur().getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, new ItemStack(Items.STONE, 64));
            }
        }
        List<ItemStack> avant = copie(h.joueur());
        attendre(helper, geste(helper, h, Action.REMOVE_SIM, ""), Lock.UNLOCKED, Result.INVENTORY_FULL, "inventaire plein");
        helper.assertTrue(PhoneSims.check(e.serveur(), phone) == PhoneSims.Validity.ACTIVE && pareil(avant, h.joueur()),
                "inventaire plein : la carte reste logée, rien ne bouge");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_SIM, timeoutTicks = 200)
    public static void demanderUneCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_dem_f");
        JoueurDeTest h = e.joueur("ts_dem_h");
        JoueurDeTest h2 = e.joueur("ts_dem_h2");
        PhoneOperator op = e.fonder(f, "Sim Demande", RESERVE);
        e.telephone(h);
        e.telephone(h2);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(h, 0);
                    e.placer(h2, 1);
                    PhoneStatePacket vue = deverrouiller(helper, h);
                    Optional<PhoneNetView.OperatorChoice> choix = vue.net().requestable().stream()
                            .filter(o -> o.id().equals(op.id())).findFirst();
                    helper.assertTrue(choix.isPresent() && choix.get().name().equals(op.name())
                            && choix.get().bars() == 4, "l'opérateur qui couvre est proposé : " + vue.net().requestable());
                    // Mode avion, sans carte (la liste n'est vide que par lui) : plus d'opérateur à demander.
                    PhoneStatePacket avion = attendre(helper, geste(helper, h, Action.AIRPLANE, "", true),
                            Lock.UNLOCKED, Result.NONE, "AIRPLANE vrai");
                    helper.assertTrue(avion.net().sim() == PhoneSims.Validity.NONE && avion.net().requestable().isEmpty(),
                            "mode avion, sans carte : aucun opérateur proposé : " + avion.net());
                    PhoneStatePacket retour = attendre(helper, geste(helper, h, Action.AIRPLANE, "", false),
                            Lock.UNLOCKED, Result.NONE, "AIRPLANE faux");
                    helper.assertTrue(retour.net().requestable().stream().anyMatch(o -> o.id().equals(op.id())),
                            "hors mode avion : l'opérateur est de nouveau proposé : " + retour.net());
                    f.clesRecues();
                    Reponse r = geste(helper, h, Action.REQUEST_SIM, op.id());
                    attendre(helper, r, Lock.UNLOCKED, Result.SIM_REQUESTED, "REQUEST_SIM");
                    List<SimRequest> demandes = e.registre().requestsOf(op.id());
                    helper.assertTrue(demandes.size() == 1 && demandes.get(0).player().equals(h.uuid()),
                            "une demande inscrite au nom du demandeur : " + demandes);
                    helper.assertTrue(f.clesRecues().contains("message.oneforallsurvival.phone.sim_request.received"),
                            "le fondateur en ligne est prévenu");
                    attendre(helper, geste(helper, h, Action.REQUEST_SIM, op.id()), Lock.UNLOCKED,
                            Result.ALREADY_REQUESTED, "seconde demande");
                    helper.assertTrue(e.registre().requestsOf(op.id()).size() == 1, "toujours une seule demande");
                    // Un opérateur inconnu, ou un identifiant mal écrit : rien d'inscrit.
                    attendre(helper, geste(helper, h, Action.REQUEST_SIM, "000000000000"), Lock.UNLOCKED,
                            Result.NO_NETWORK, "opérateur inconnu");
                    Reponse mal = geste(helper, h, Action.REQUEST_SIM, op.id().toUpperCase(java.util.Locale.ROOT) + "x");
                    helper.assertTrue(mal.outcome() == Outcome.REFUSED, "identifiant mal écrit : " + mal.outcome());
                    // Le fondateur absent : l'avis l'attend.
                    e.registre().takeNotices(f.uuid());
                    f.quitter();
                    // La file de l'opérateur remplie par des demandeurs fictifs : refus dit, rien d'inscrit.
                    int max = PhoneConfig.simRequestsMax();
                    List<UUID> fictifs = new ArrayList<>();
                    while (e.registre().requestsOf(op.id()).size() < max) {
                        Optional<SimRequest> d = e.registre().request(op.id(), UUID.randomUUID(), "fictif", 0L);
                        helper.assertTrue(d.isPresent(), "demande fictive refusée avant la borne");
                        if (d.isEmpty()) {
                            break;
                        }
                        fictifs.add(d.get().id());
                    }
                    deverrouiller(helper, h2);
                    attendre(helper, geste(helper, h2, Action.REQUEST_SIM, op.id()), Lock.UNLOCKED,
                            Result.REQUESTS_FULL, "file pleine");
                    helper.assertTrue(e.registre().requestsOf(op.id()).size() == max
                            && e.registre().requestsOf(op.id()).stream().noneMatch(d -> d.player().equals(h2.uuid())),
                            "file pleine : rien d'inscrit pour le demandeur");
                    helper.assertTrue(e.registre().takeNotices(f.uuid()).isEmpty(), "file pleine : aucun avis");
                    helper.assertTrue(!fictifs.isEmpty(), "au moins une demande fictive");
                    e.registre().takeRequest(fictifs.get(0));
                    attendre(helper, geste(helper, h2, Action.REQUEST_SIM, op.id()), Lock.UNLOCKED,
                            Result.SIM_REQUESTED, "demande, fondateur absent");
                    helper.assertTrue(e.registre().takeNotices(f.uuid()).size() == 1,
                            "un avis gardé pour le fondateur absent");
                    helper.assertTrue(e.registre().requestsOf(op.id()).stream().anyMatch(d -> d.player().equals(h2.uuid())),
                            "la demande du second est inscrite");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void uneSessionPerimeeNeGlissePasDeCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_per_f");
        JoueurDeTest h = e.joueur("ts_per_h");
        PhoneOperator op = e.fonder(f, "Sim Perimee", RESERVE);
        ItemStack phone = e.telephone(h);
        e.carte(op, h, h, 5);
        deverrouiller(helper, h);
        int ancienne = session(helper, h);
        // Rouvert : la première session est périmée.
        ouvrir(helper, h);
        attendre(helper, geste(helper, h, Action.UNLOCK, CODE), Lock.UNLOCKED, Result.NONE, "UNLOCK");
        List<ItemStack> avant = copie(h.joueur());
        for (Action a : new Action[] {Action.INSERT_SIM, Action.REMOVE_SIM, Action.REQUEST_SIM}) {
            String first = a == Action.INSERT_SIM ? "5" : a == Action.REQUEST_SIM ? op.id() : "";
            Reponse r = geste(helper, h, new PhoneActionPacket(ancienne, a, first, "", false), true);
            helper.assertTrue(r.outcome() == Outcome.NO_SESSION && r.etat() == null,
                    a + " d'une session périmée : ignoré sans réponse, vu " + r.outcome());
        }
        // Sans session du tout : rien non plus.
        PhoneService.close(h.joueur(), CloseReason.CLOSED);
        Reponse sans = geste(helper, h, new PhoneActionPacket(ancienne + 1, Action.INSERT_SIM, "5", "", false), true);
        helper.assertTrue(sans.outcome() == Outcome.NO_SESSION, "sans session : " + sans.outcome());
        helper.assertTrue(pareil(avant, h.joueur()) && PhoneItem.sim(phone).isEmpty()
                && e.registre().requestsOf(op.id()).isEmpty(), "rien n'a bougé");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void verrouilleAucunGesteDeCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_ver_f");
        JoueurDeTest h = e.joueur("ts_ver_h");
        PhoneOperator op = e.fonder(f, "Sim Verrou", RESERVE);
        ItemStack phone = e.telephone(h);
        e.carte(op, h, h, 5);
        // Téléphone neuf (choix du code), puis verrouillé : aucun geste de carte.
        ouvrir(helper, h);
        List<ItemStack> avant = copie(h.joueur());
        for (Action a : new Action[] {Action.INSERT_SIM, Action.REMOVE_SIM, Action.REQUEST_SIM,
                Action.OPEN_OPERATOR_APP}) {
            String first = a == Action.INSERT_SIM ? "5" : a == Action.REQUEST_SIM ? op.id() : "";
            Reponse r = geste(helper, h, a, first);
            helper.assertTrue(r.outcome() == Outcome.REFUSED && r.etat() != null && r.etat().lock() == Lock.SETUP
                    && r.etat().last() == Result.NONE, a + " au choix du code : refusé, vu " + r);
        }
        attendre(helper, geste(helper, h, Action.SETUP, CODE), Lock.UNLOCKED, Result.NONE, "SETUP");
        attendre(helper, geste(helper, h, Action.LOCK, ""), Lock.LOCKED, Result.NONE, "LOCK");
        for (Action a : new Action[] {Action.INSERT_SIM, Action.REMOVE_SIM, Action.REQUEST_SIM,
                Action.OPEN_OPERATOR_APP}) {
            String first = a == Action.INSERT_SIM ? "5" : a == Action.REQUEST_SIM ? op.id() : "";
            Reponse r = geste(helper, h, a, first);
            helper.assertTrue(r.outcome() == Outcome.REFUSED && r.etat() != null && r.etat().lock() == Lock.LOCKED
                    && r.etat().last() == Result.NONE, a + " verrouillé : refusé, vu " + r);
        }
        helper.assertTrue(pareil(avant, h.joueur()) && PhoneItem.sim(phone).isEmpty()
                && e.registre().requestsOf(op.id()).isEmpty(), "rien n'a bougé");
        e.fermer();
        helper.succeed();
    }

    /**
     * Un autre que le propriétaire, qui a déverrouillé avec le code : il voit la ligne (numéro, titulaire, opérateur,
     * barres), mais ni les cartes de son inventaire, ni les opérateurs demandables, et aucun geste de carte ne passe
     * ({@code NOT_OWNER}) : la carte reste à son propriétaire (choix prudent de la tâche 7, le plan étant muet).
     */
    @GameTest(template = "test/plancher", batch = LOT_SIM)
    public static void unAutreQueLeProprietaireNeTouchepasALaCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ts_aut_f");
        JoueurDeTest h = e.joueur("ts_aut_h");
        JoueurDeTest x = e.joueur("ts_aut_x");
        PhoneOperator op = e.fonder(f, "Sim Autre", RESERVE);
        ItemStack phone = e.ligne(op, h);
        deverrouiller(helper, h);
        PhoneService.close(h.joueur(), CloseReason.CLOSED);
        h.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        x.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
        ItemStack enMain = x.joueur().getItemInHand(InteractionHand.MAIN_HAND);
        e.carte(op, x, x, 5);
        ouvrir(helper, x);
        PhoneStatePacket vue = attendre(helper, geste(helper, x, Action.UNLOCK, CODE), Lock.UNLOCKED, Result.NONE,
                "UNLOCK par un autre");
        PhoneNetView n = vue.net();
        helper.assertTrue(n.sim() == PhoneSims.Validity.ACTIVE && !n.number().isEmpty()
                && n.holder().equals(h.joueur().getGameProfile().getName()), "il voit la ligne : " + n);
        helper.assertTrue(n.choices().isEmpty() && n.requestable().isEmpty() && !n.founderApp(),
                "ni cartes, ni opérateurs, ni appli : " + n);
        List<ItemStack> avant = copie(x.joueur());
        UUID avantCarte = exiger(helper, SimCardItem.simId(PhoneItem.sim(enMain)), "aucune carte logée");
        for (Action a : new Action[] {Action.INSERT_SIM, Action.REMOVE_SIM, Action.REQUEST_SIM}) {
            String first = a == Action.INSERT_SIM ? "5" : a == Action.REQUEST_SIM ? op.id() : "";
            Reponse r = geste(helper, x, a, first);
            attendre(helper, r, Lock.UNLOCKED, Result.NOT_OWNER, a + " par un autre");
        }
        helper.assertTrue(pareil(avant, x.joueur())
                && SimCardItem.simId(PhoneItem.sim(enMain)).filter(avantCarte::equals).isPresent()
                && e.registre().requestsOf(op.id()).isEmpty(), "rien n'a bougé");
        e.fermer();
        helper.succeed();
    }
}
