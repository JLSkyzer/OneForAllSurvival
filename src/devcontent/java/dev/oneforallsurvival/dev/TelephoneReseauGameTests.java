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
    private static final String LOT_ICONE = "telephone_icone";
    private static final String LOT_FUITE = "telephone_fuite";

    private static final BlockPos A = new BlockPos(1, 0, 1);
    /** La seconde antenne : celle d'un autre opérateur (essais de l'icône « Antenne »). */
    private static final BlockPos B = new BlockPos(6, 0, 6);
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

    @AfterBatch(batch = LOT_ICONE)
    public static void apresIcone(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_FUITE)
    public static void apresFuite(ServerLevel level) {
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
            return fonder(j, A, nom, fe);
        }

        /** La même, l'antenne en {@code ou}. */
        PhoneOperator fonder(JoueurDeTest j, BlockPos ou, String nom, int fe) {
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            ServerPlayer p = j.joueur();
            ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            BlockPos sol = helper.absolutePos(ou.below());
            BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
            InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
            p.setItemInHand(InteractionHand.MAIN_HAND, avant);
            helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(p, helper.absolutePos(ou), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            int reste = fe;
            while (reste > 0) {
                int recu = antenne(ou).energy().receiveEnergy(reste, false);
                if (recu <= 0) {
                    break;
                }
                reste -= recu;
            }
            return fonde.get().operator();
        }

        AntennaBlockEntity antenne() {
            return antenne(A);
        }

        AntennaBlockEntity antenne(BlockPos ou) {
            helper.assertTrue(helper.getBlockEntity(ou) instanceof AntennaBlockEntity, "pas d'antenne en " + ou);
            return (AntennaBlockEntity) helper.getBlockEntity(ou);
        }

        boolean enService() {
            return enService(A);
        }

        boolean enService(BlockPos ou) {
            AntennaBlockEntity be = antenne(ou);
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

        /** Rangement de la dernière étape : les antennes retirées du gabarit, puis le reste. */
        void fermer() {
            helper.setBlock(A, Blocks.AIR);
            helper.setBlock(B, Blocks.AIR);
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
                && n.number().isEmpty() && n.holder().isEmpty() && !n.founderApp() && !n.founderAppAvailable()
                && n.choices().isEmpty(),
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
                            && n.choices().size() == 1 && n.founderApp() && n.founderAppAvailable(),
                            "témoin déverrouillé : numéro, titulaire, une carte au choix, appli ouvrable : " + n);
                    PhoneStatePacket ferme = attendre(helper, geste(helper, f, Action.LOCK, ""), Lock.LOCKED,
                            Result.NONE, "LOCK");
                    PhoneStatePacket rouvert = ouvrir(helper, f);
                    for (PhoneStatePacket p : new PhoneStatePacket[] {ferme, rouvert}) {
                        PhoneNetView v = p.net();
                        helper.assertTrue(v.operator().equals(op.name()) && v.bars() == 4,
                                "verrouillé : l'opérateur et les barres se voient : " + v);
                        helper.assertTrue(v.number().isEmpty() && v.holder().isEmpty() && !v.founderApp()
                                && !v.founderAppAvailable() && v.choices().isEmpty() && v.requestable().isEmpty(),
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
                            r.ownerName(), "selneuf", r.codeHash(), r.failures(), r.lockedUntil(), r.lockoutLevel(),
                            r.wallpaper(),
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
                                && v.choices().isEmpty() && !v.founderApp() && !v.founderAppAvailable()
                                && v.operator().equals(op.name()),
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
                    helper.assertTrue(vue.net().bars() == 4 && vue.net().founderApp() && vue.net().founderAppAvailable()
                            && !vue.airplane(), "témoin : 4 barres, appli ouvrable, hors mode avion : " + vue);
                    PhoneStatePacket avion = attendre(helper, geste(helper, f, Action.AIRPLANE, "", true),
                            Lock.UNLOCKED, Result.NONE, "AIRPLANE vrai");
                    helper.assertTrue(avion.airplane() && avion.net().bars() == 0,
                            "mode avion : 0 barre dans le même UPDATE, vu " + avion.net().bars());
                    helper.assertTrue(avion.net().founderApp() && !avion.net().founderAppAvailable()
                            && avion.net().requestable().isEmpty(),
                            "mode avion : l'appli montrée mais grisée, aucun opérateur à demander : " + avion.net());
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
                    helper.assertTrue(vueF.net().founderApp() && vueF.net().founderAppAvailable(),
                            "le fondateur voit l'appli « Antenne », ouvrable : " + vueF.net());
                    PhoneStatePacket vueC = deverrouiller(helper, c);
                    helper.assertTrue(!vueC.net().founderApp() && !vueC.net().founderAppAvailable()
                            && vueC.net().sim() == PhoneSims.Validity.ACTIVE,
                            "un abonné ne voit pas l'appli : " + vueC.net());
                    // L'appli ouvre le panneau de l'opérateur du fondateur (tâche 9), source « téléphone ».
                    f.panneauxOperateurRecus();
                    attendre(helper, geste(helper, f, Action.OPEN_OPERATOR_APP, ""), Lock.UNLOCKED, Result.NONE,
                            "appli du fondateur");
                    helper.assertTrue(f.panneauxOperateurRecus().stream().anyMatch(v -> v.operatorId().equals(op.id())
                            && v.source() == dev.oneforallsurvival.phone.net.OperatorView.Source.PHONE),
                            "le panneau de l'opérateur, source téléphone");
                    dev.oneforallsurvival.phone.net.OperatorPanels.close(f.uuid());
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
                    helper.assertTrue(!autre.owner() && !n.founderApp() && !n.founderAppAvailable(),
                            "pas le propriétaire : pas d'appli : " + n);
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
        helper.assertTrue(n.choices().isEmpty() && n.requestable().isEmpty() && !n.founderApp()
                && !n.founderAppAvailable(), "ni cartes, ni opérateurs, ni appli : " + n);
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

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_icone (tâche 13b) : l'icône « Antenne » grisée exactement quand le serveur refuserait de l'ouvrir
    // ---------------------------------------------------------------------------------------------

    /**
     * La vue que le fondateur a sous les yeux (relue par un geste sans effet), puis un toucher sur l'icône : elle est
     * montrée, vive ({@code vive}) ou grisée, et le serveur refuse ({@code NO_NETWORK}, aucun panneau) exactement quand
     * elle est grisée. Le panneau ouvert est refermé. Rend la vue relue avant le toucher.
     */
    private static PhoneStatePacket icone(GameTestHelper helper, JoueurDeTest f, boolean vive, String cas) {
        PhoneStatePacket vue = attendre(helper, geste(helper, f, Action.WALLPAPER, "nuit"), Lock.UNLOCKED, Result.NONE,
                cas + " : relire la vue");
        helper.assertTrue(vue.net().founderApp(), cas + " : le fondateur voit toujours l'appli : " + vue.net());
        helper.assertTrue(vue.net().founderAppAvailable() == vive,
                cas + " : icône " + (vive ? "vive" : "grisée") + " attendue : " + vue.net());
        f.panneauxOperateurRecus();
        Reponse r = geste(helper, f, Action.OPEN_OPERATOR_APP, "");
        PhoneStatePacket apres = r.etat();
        helper.assertTrue(apres != null, cas + " : le toucher a une réponse (issue " + r.outcome() + ")");
        boolean refuse = apres != null && apres.last() == Result.NO_NETWORK;
        boolean ouvert = !f.panneauxOperateurRecus().isEmpty();
        helper.assertTrue(refuse == !vue.net().founderAppAvailable() && ouvert != refuse,
                cas + " : grisée et refus doivent coïncider : grisée " + !vue.net().founderAppAvailable() + ", refus "
                        + refuse + ", panneau " + ouvert);
        helper.assertTrue(r.outcome() == (vive ? Outcome.DONE : Outcome.REFUSED), cas + " : issue " + r.outcome());
        helper.assertTrue(apres != null && apres.net().founderAppAvailable() == vive,
                cas + " : la réponse au toucher porte le même état : " + apres);
        dev.oneforallsurvival.phone.net.OperatorPanels.close(f.uuid());
        return vue;
    }

    /**
     * Un {@code UPDATE} poussé par le relevé du signal porte cette icône, au plus un relevé après {@code depuis} (aucun
     * geste entre les deux). {@code recus} cumule les états d'un tic à l'autre.
     */
    private static void iconeEnDirect(GameTestHelper helper, JoueurDeTest f, List<PhoneStatePacket> recus, boolean vive,
            long[] depuis, String cas) {
        recus.addAll(f.etatsTelephoneRecus());
        helper.assertTrue(recus.stream().anyMatch(p -> p.kind() == Kind.UPDATE && p.net().founderApp()
                && p.net().founderAppAvailable() == vive),
                cas + " : un UPDATE à l'icône " + (vive ? "vive" : "grisée") + " attendu");
        long passe = helper.getLevel().getGameTime() - depuis[0];
        helper.assertTrue(passe <= PhoneConfig.signalRefreshTicks() + 1,
                cas + " : au relevé suivant (" + PhoneConfig.signalRefreshTicks() + " tics), vu " + passe);
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 200)
    public static void lIconeEstViveAvecSaCarteEtGriseeEnModeAvion(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_avion");
        PhoneOperator op = e.fonder(f, "Icone Avion", RESERVE);
        e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    deverrouiller(helper, f);
                    icone(helper, f, true, "sa carte et du signal");
                    attendre(helper, geste(helper, f, Action.AIRPLANE, "", true), Lock.UNLOCKED, Result.NONE,
                            "mode avion mis");
                    icone(helper, f, false, "mode avion");
                    attendre(helper, geste(helper, f, Action.AIRPLANE, "", false), Lock.UNLOCKED, Result.NONE,
                            "mode avion coupé");
                    icone(helper, f, true, "hors mode avion");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 200)
    public static void lIconeEstGriseeSansCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_sans");
        PhoneOperator op = e.fonder(f, "Icone Sans Carte", RESERVE);
        e.telephone(f);
        e.carte(op, f, f, 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    deverrouiller(helper, f);
                    PhoneStatePacket sans = icone(helper, f, false, "sans carte");
                    helper.assertTrue(sans.net().sim() == PhoneSims.Validity.NONE, "sans carte : " + sans.net());
                    PhoneStatePacket mise = attendre(helper, geste(helper, f, Action.INSERT_SIM, "5"), Lock.UNLOCKED,
                            Result.SIM_INSERTED, "INSERT_SIM");
                    helper.assertTrue(mise.net().founderAppAvailable(), "vive dès la carte glissée : " + mise.net());
                    icone(helper, f, true, "carte glissée");
                    PhoneStatePacket otee = attendre(helper, geste(helper, f, Action.REMOVE_SIM, ""), Lock.UNLOCKED,
                            Result.SIM_REMOVED, "REMOVE_SIM");
                    helper.assertTrue(!otee.net().founderAppAvailable(), "grisée dès la carte retirée : " + otee.net());
                    icone(helper, f, false, "carte retirée");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 200)
    public static void lIconeEstGriseeAvecLaCarteDUnAutreOperateur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_autre_f");
        JoueurDeTest g = e.joueur("ti_autre_g");
        PhoneOperator mien = e.fonder(f, A, "Icone Mienne", RESERVE);
        PhoneOperator voisin = e.fonder(g, B, "Icone Voisine", RESERVE);
        e.ligne(voisin, f);
        e.carte(mien, f, f, 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A) && e.enService(B), "antennes en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    deverrouiller(helper, f);
                    PhoneStatePacket vue = icone(helper, f, false, "carte d'un autre opérateur");
                    helper.assertTrue(vue.net().sim() == PhoneSims.Validity.ACTIVE && vue.net().bars() >= 1
                            && vue.net().operator().equals(voisin.name()),
                            "témoin : la carte active de l'autre, du signal : " + vue.net());
                    attendre(helper, geste(helper, f, Action.INSERT_SIM, "5"), Lock.UNLOCKED, Result.SIM_INSERTED,
                            "INSERT_SIM");
                    icone(helper, f, true, "sa propre carte");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 300)
    public static void lIconeEstGriseeAvecUneCarteSuspendue(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_susp");
        PhoneOperator op = e.fonder(f, "Icone Suspendue", RESERVE);
        ItemStack phone = e.ligne(op, f);
        List<PhoneStatePacket> recus = new ArrayList<>();
        long[] depuis = {-1L};
        UUID[] carte = {null};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    deverrouiller(helper, f);
                    icone(helper, f, true, "carte active");
                    carte[0] = exiger(helper, PhoneSims.line(e.serveur(), phone), "la ligne du fondateur").simId();
                    f.etatsTelephoneRecus();
                    helper.assertTrue(e.registre().setState(carte[0], SimCard.State.SUSPENDED), "suspension");
                    depuis[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> iconeEnDirect(helper, f, recus, false, depuis, "carte suspendue"))
                .thenIdle(3)
                .thenExecute(() -> {
                    PhoneStatePacket vue = icone(helper, f, false, "carte suspendue");
                    helper.assertTrue(vue.net().sim() == PhoneSims.Validity.SUSPENDED, "suspendue : " + vue.net());
                    recus.clear();
                    helper.assertTrue(e.registre().setState(carte[0], SimCard.State.ACTIVE), "réactivation");
                    depuis[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> iconeEnDirect(helper, f, recus, true, depuis, "carte réactivée"))
                .thenIdle(3)
                .thenExecute(() -> {
                    icone(helper, f, true, "carte réactivée");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 400)
    public static void lIconeSuitLeSignalEnDirect(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_signal");
        PhoneOperator op = e.fonder(f, "Icone Signal", RESERVE);
        e.ligne(op, f);
        List<PhoneStatePacket> recus = new ArrayList<>();
        long[] depuis = {-1L};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    deverrouiller(helper, f);
                    icone(helper, f, true, "sous l'antenne");
                    f.etatsTelephoneRecus();
                    e.placer(f, 300);
                    depuis[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> iconeEnDirect(helper, f, recus, false, depuis, "à 300 blocs"))
                .thenIdle(3)
                .thenExecute(() -> {
                    PhoneStatePacket loin = icone(helper, f, false, "0 barre");
                    helper.assertTrue(loin.net().bars() == 0 && loin.net().sim() == PhoneSims.Validity.ACTIVE,
                            "carte active, 0 barre : " + loin.net());
                    recus.clear();
                    e.placer(f, 0);
                    depuis[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> iconeEnDirect(helper, f, recus, true, depuis, "revenu sous l'antenne"))
                .thenIdle(3)
                .thenExecute(() -> {
                    icone(helper, f, true, "revenu");
                    // La source retirée : la réserve vidée, l'antenne s'arrête.
                    e.antenne().drainEnergy(Integer.MAX_VALUE);
                })
                .thenWaitUntil(() -> helper.assertTrue(!e.enService(), "antenne hors service"))
                .thenIdle(3)
                .thenExecute(() -> {
                    icone(helper, f, false, "antenne sans courant");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 200)
    public static void lIconeNExistePasPourUnNonFondateur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_non_f");
        JoueurDeTest c = e.joueur("ti_non_c");
        PhoneOperator op = e.fonder(f, "Icone Abonne", RESERVE);
        e.ligne(op, c);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(c, 0);
                    PhoneStatePacket vue = deverrouiller(helper, c);
                    helper.assertTrue(vue.net().sim() == PhoneSims.Validity.ACTIVE && vue.net().bars() == 4,
                            "témoin : un abonné avec sa carte et du signal : " + vue.net());
                    helper.assertTrue(!vue.net().founderApp() && !vue.net().founderAppAvailable(),
                            "pas fondateur : aucune icône : " + vue.net());
                    // Un toucher forgé : refusé sans réseau, aucun panneau.
                    c.panneauxOperateurRecus();
                    attendre(helper, geste(helper, c, Action.OPEN_OPERATOR_APP, ""), Lock.UNLOCKED, Result.NO_NETWORK,
                            "toucher d'un abonné");
                    helper.assertTrue(c.panneauxOperateurRecus().isEmpty(), "aucun panneau pour un abonné");
                    e.fermer();
                })
                .thenSucceed();
    }

    /**
     * Revue de la tâche 13b (mineur 3) : l'opérateur passe à un autre fondateur ({@code setFounder}, geste du maire) ;
     * au relevé suivant, l'icône quitte le téléphone de l'ancien et vient, vive, sur celui du nouveau, qui a une carte
     * active de l'opérateur. L'ancien qui touche encore l'appli est refusé sans panneau.
     */
    @GameTest(template = "test/plancher", batch = LOT_ICONE, timeoutTicks = 300)
    public static void lIconeSuitLeChangementDeFondateur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("ti_owner_f");
        JoueurDeTest h = e.joueur("ti_owner_h");
        PhoneOperator op = e.fonder(f, "Icone Heritier", RESERVE);
        e.ligne(op, f);
        e.ligne(op, h);
        List<PhoneStatePacket> recusF = new ArrayList<>();
        List<PhoneStatePacket> recusH = new ArrayList<>();
        long[] depuis = {-1L};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(), "antenne en service"))
                .thenExecute(() -> {
                    e.placer(f, 0);
                    e.placer(h, 1);
                    deverrouiller(helper, f);
                    icone(helper, f, true, "fondateur d'origine");
                    PhoneStatePacket vueH = deverrouiller(helper, h);
                    helper.assertTrue(vueH.net().sim() == PhoneSims.Validity.ACTIVE && !vueH.net().founderApp()
                            && !vueH.net().founderAppAvailable(), "témoin : abonné, aucune icône : " + vueH.net());
                    f.etatsTelephoneRecus();
                    h.etatsTelephoneRecus();
                    helper.assertTrue(e.registre().setFounder(op.id(), h.uuid(), "ti_owner_h"), "changement de fondateur");
                    depuis[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> {
                    recusF.addAll(f.etatsTelephoneRecus());
                    recusH.addAll(h.etatsTelephoneRecus());
                    helper.assertTrue(recusF.stream().anyMatch(p -> p.kind() == Kind.UPDATE && !p.net().founderApp()
                            && !p.net().founderAppAvailable()), "l'icône doit quitter le téléphone de l'ancien fondateur");
                    helper.assertTrue(recusH.stream().anyMatch(p -> p.kind() == Kind.UPDATE && p.net().founderApp()
                            && p.net().founderAppAvailable()), "l'icône vive doit venir sur le téléphone du nouveau");
                    long passe = helper.getLevel().getGameTime() - depuis[0];
                    helper.assertTrue(passe <= PhoneConfig.signalRefreshTicks() + 1,
                            "au relevé suivant (" + PhoneConfig.signalRefreshTicks() + " tics), vu " + passe);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    icone(helper, h, true, "nouveau fondateur");
                    f.panneauxOperateurRecus();
                    attendre(helper, geste(helper, f, Action.OPEN_OPERATOR_APP, ""), Lock.UNLOCKED, Result.NO_NETWORK,
                            "toucher de l'ancien fondateur");
                    helper.assertTrue(f.panneauxOperateurRecus().isEmpty(), "aucun panneau pour l'ancien fondateur");
                    e.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot telephone_fuite (revue finale, I1) : une fermeture n'envoie rien d'un autre téléphone
    // ---------------------------------------------------------------------------------------------

    /**
     * L'état de fermeture ne porte rien du téléphone tenu s'il n'est pas celui de la session : vue réseau vide
     * ({@link PhoneNetView#NONE} : ni numéro, ni titulaire, ni opérateur, ni barres, ni cartes, ni appli) et batterie 0.
     */
    private static void fermetureSansAutreTelephone(GameTestHelper helper, PhoneStatePacket p, CloseReason raison,
            String cas) {
        helper.assertTrue(p.kind() == Kind.CLOSE && p.reason() == raison,
                cas + " : un CLOSE " + raison + " attendu, vu " + p.kind() + "/" + p.reason());
        helper.assertTrue(p.net().equals(PhoneNetView.NONE),
                cas + " : la vue réseau de l'autre téléphone est partie au client : " + p.net());
        helper.assertTrue(p.battery() == 0, cas + " : la batterie de l'autre téléphone est partie : " + p.battery());
    }

    /**
     * A, déverrouillé sur son téléphone, prend en main le téléphone de B (carte active, numéro, titulaire) : au tic
     * suivant la session se ferme ({@code PHONE_GONE}) sans rien envoyer de la ligne de B.
     */
    @GameTest(template = "test/plancher", batch = LOT_FUITE, timeoutTicks = 100)
    public static void fermerParUnAutreTelephoneNeMontrePasSaLigne(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest a = e.joueur("tf_gone_a");
        JoueurDeTest b = e.joueur("tf_gone_b");
        PhoneOperator op = e.fonder(b, "Fuite Gone", RESERVE);
        ItemStack deB = e.ligne(op, b);
        e.telephone(a);
        deverrouiller(helper, a);
        ItemStack pris = deB.copy();
        b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.joueur().setItemInHand(InteractionHand.MAIN_HAND, pris);
        a.etatsTelephoneRecus();
        List<PhoneStatePacket> recus = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    recus.addAll(a.etatsTelephoneRecus());
                    helper.assertTrue(recus.stream().anyMatch(p -> p.kind() == Kind.CLOSE), "fermeture attendue");
                })
                .thenExecute(() -> {
                    helper.assertTrue(recus.size() == 1, "un seul état attendu, vu " + recus);
                    fermetureSansAutreTelephone(helper, recus.get(0), CloseReason.PHONE_GONE, "téléphone de B en main");
                    e.fermer();
                })
                .thenSucceed();
    }

    /** A, déverrouillé, essaie d'ouvrir le téléphone de B à batterie vide : la fermeture de sa session n'en dit rien. */
    @GameTest(template = "test/plancher", batch = LOT_FUITE)
    public static void ouvrirUnAutreTelephoneVideNeMontrePasSaLigne(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest a = e.joueur("tf_vide_a");
        JoueurDeTest b = e.joueur("tf_vide_b");
        PhoneOperator op = e.fonder(b, "Fuite Vide", RESERVE);
        ItemStack deB = e.ligne(op, b);
        e.telephone(a);
        deverrouiller(helper, a);
        ItemStack vide = deB.copy();
        PhoneItem.store(vide, 0);
        b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.joueur().setItemInHand(InteractionHand.MAIN_HAND, vide);
        a.etatsTelephoneRecus();
        Outcome r = PhoneService.open(a.joueur(), InteractionHand.MAIN_HAND);
        helper.assertTrue(r == Outcome.EMPTY_BATTERY, "ouverture d'un téléphone vide : " + r);
        List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
        helper.assertTrue(recus.size() == 1, "un seul état attendu, vu " + recus);
        fermetureSansAutreTelephone(helper, recus.get(0), CloseReason.CLOSED, "téléphone vide de B ouvert");
        e.fermer();
        helper.succeed();
    }

    /**
     * {@code /phone reset} du téléphone de A ({@code closeAll}) alors que A a changé de case dans le tic et tient le
     * téléphone de B : la fermeture n'en dit rien.
     */
    @GameTest(template = "test/plancher", batch = LOT_FUITE)
    public static void effacerApresUnChangementDeMainNeMontrePasLAutreLigne(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest a = e.joueur("tf_reset_a");
        JoueurDeTest b = e.joueur("tf_reset_b");
        PhoneOperator op = e.fonder(b, "Fuite Reset", RESERVE);
        ItemStack deB = e.ligne(op, b);
        ItemStack deA = e.telephone(a);
        UUID idA = exiger(helper, PhoneItem.phoneId(deA), "identifiant du téléphone de A");
        deverrouiller(helper, a);
        ItemStack pris = deB.copy();
        b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.joueur().setItemInHand(InteractionHand.MAIN_HAND, pris);
        a.etatsTelephoneRecus();
        PhoneService.closeAll(e.serveur(), idA, CloseReason.ERASED);
        List<PhoneStatePacket> recus = a.etatsTelephoneRecus();
        helper.assertTrue(recus.size() == 1, "un seul état attendu, vu " + recus);
        fermetureSansAutreTelephone(helper, recus.get(0), CloseReason.ERASED, "effacement, téléphone de B en main");
        e.fermer();
        helper.succeed();
    }
}
