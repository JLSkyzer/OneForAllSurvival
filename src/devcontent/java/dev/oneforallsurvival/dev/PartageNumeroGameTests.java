package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.call.Contact;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket;
import dev.oneforallsurvival.phone.call.PhoneDirectory;
import dev.oneforallsurvival.phone.call.PhoneSharePacket;
import dev.oneforallsurvival.phone.call.ShareBook;
import dev.oneforallsurvival.phone.call.ShareService;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * « Partager mon numéro » (« AirDrop », retours d'essai du 27/11) : chaque règle jouée par de vrais paquets (les gestes
 * de l'écran, {@link PhoneCallActionPacket} par {@link JoueurDeTest#envoyer}) et par la commande des liens du chat
 * ({@code /phoneshare}, tapée comme le joueur). Les listes et les messages reçus sont décodés des octets envoyés.
 * Négatifs : distance, mur, spectateur, invisible, sans carte, envoyeur sans carte active, réponse d'un autre joueur,
 * demande expirée, départ, carte perdue, débit, verrouillé ; reprise de la relecture : le téléphone d'un autre, la liste
 * sans carte active, une réponse après 60 s avant tout relevé, une nouvelle offre avant la fin des 60 s.
 *
 * <p>Le décor est celui des appels ({@link AppelGameTests.Essai} : plancher de pierre, une antenne qui fonde un
 * opérateur, des cartes émises et logées) ; un opérateur au nom propre à chaque essai. Le lot {@value #LOT_HORLOGE}
 * fige l'horloge de l'économie.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PartageNumeroGameTests {

    private static final String LOT_A = "partage_a";
    private static final String LOT_B = "partage_b";
    private static final String LOT_C = "partage_c";
    private static final String LOT_HORLOGE = "partage_horloge";
    private static final String CODE = "1470";
    private static final String ACCEPTED = ShareService.MESSAGE + "accepted";

    private static volatile long horloge;

    private PartageNumeroGameTests() {}

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        AppelGameTests.apres();
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        AppelGameTests.apres();
    }

    @AfterBatch(batch = LOT_C)
    public static void apresC(ServerLevel level) {
        AppelGameTests.apres();
    }

    @BeforeBatch(batch = LOT_HORLOGE)
    public static void avantHorloge(ServerLevel level) {
        horloge = System.currentTimeMillis();
        EconomyClock.setClock(() -> horloge);
    }

    @AfterBatch(batch = LOT_HORLOGE)
    public static void apresHorloge(ServerLevel level) {
        EconomyClock.reset();
        AppelGameTests.apres();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Le décor : un opérateur fondé par l'envoyeur, l'envoyeur en (2, 5) avec sa ligne et son écran déverrouillé. */
    private static final class Decor {
        final GameTestHelper helper;
        final AppelGameTests.Essai essai;
        final PhoneOperator op;
        final JoueurDeTest a;
        final ItemStack phoneA;
        final SimCard carteA;

        Decor(GameTestHelper helper, String prefixe) {
            this.helper = helper;
            this.essai = new AppelGameTests.Essai(helper);
            this.a = essai.joueur("pn_" + prefixe + "_a");
            this.op = essai.fonder(a, AppelGameTests.A, "Pn " + prefixe);
            this.phoneA = essai.ligne(op, a);
            this.carteA = AppelGameTests.exiger(helper, PhoneSims.line(serveur(), phoneA), "ligne de A");
            aller(a, 2.5, 5.5);
            deverrouiller(a);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        /** Un joueur avec sa ligne de l'opérateur, un téléphone neuf en main, posé en (x, z). */
        JoueurDeTest avecLigne(String court, double x, double z) {
            JoueurDeTest j = essai.joueur(court);
            essai.ligne(op, j);
            aller(j, x, z);
            return j;
        }

        void aller(JoueurDeTest j, double x, double z) {
            BlockPos o = helper.absolutePos(BlockPos.ZERO);
            j.joueur().teleportTo(o.getX() + x, o.getY(), o.getZ() + z);
        }

        /** Le code posé sur le téléphone tenu, écran ouvert et déverrouillé (paquets du mod). */
        void deverrouiller(JoueurDeTest j) {
            j.etatsTelephoneRecus();
            helper.assertTrue(PhoneService.open(j.joueur(), InteractionHand.MAIN_HAND) == PhoneService.Outcome.OPENED,
                    "ouverture du téléphone de " + nom(j));
            oublierFrein(j.uuid());
            j.envoyer(new PhoneActionPacket(session(j), PhoneActionPacket.Action.SETUP, CODE, "", false));
            List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
            helper.assertTrue(!recus.isEmpty() && recus.get(recus.size() - 1).lock() == PhoneStatePacket.Lock.UNLOCKED,
                    "écran déverrouillé attendu : " + recus);
        }

        int session(JoueurDeTest j) {
            Optional<PhoneSession> s = PhoneService.session(j.uuid());
            helper.assertTrue(s.isPresent(), "session ouverte attendue pour " + nom(j));
            return s.get().id();
        }

        /** Un geste de partage de {@code j} (vrai paquet, frein oublié) ; la dernière liste reçue, ou nulle. */
        PhoneSharePacket geste(JoueurDeTest j, PhoneCallActionPacket.Action action, String first) {
            j.partagesRecus();
            oublierFrein(j.uuid());
            j.envoyer(new PhoneCallActionPacket(session(j), action, first, ""));
            List<PhoneSharePacket> recus = j.partagesRecus();
            return recus.isEmpty() ? null : recus.get(recus.size() - 1);
        }

        PhoneSharePacket liste(JoueurDeTest j) {
            PhoneSharePacket p = geste(j, PhoneCallActionPacket.Action.SHARE_SCAN, "");
            helper.assertTrue(p != null, "une liste attendue pour " + nom(j));
            return p;
        }

        /** Envoie à {@code b} ; rend la clé du mot reçu. */
        String envoyer(JoueurDeTest j, JoueurDeTest b) {
            PhoneSharePacket p = geste(j, PhoneCallActionPacket.Action.SHARE_SEND, Integer.toString(b.joueur().getId()));
            helper.assertTrue(p != null, "une réponse à l'envoi attendue");
            return cle(p.message());
        }

        boolean liste(JoueurDeTest j, JoueurDeTest b) {
            return liste(j).rows().stream().anyMatch(r -> r.id() == b.joueur().getId());
        }

        /** La commande tapée par {@code j} ; rend les clés des messages reçus. */
        List<String> commande(JoueurDeTest j, String ligne) {
            j.clesRecues();
            serveur().getCommands().performPrefixedCommand(j.joueur().createCommandSourceStack(), ligne);
            return j.clesRecues();
        }

        /** La demande en attente de {@code de} vers {@code vers}. */
        Optional<ShareBook.Request> demande(JoueurDeTest de, JoueurDeTest vers) {
            return ShareService.pending().stream()
                    .filter(r -> r.sender().equals(de.uuid()) && r.receiver().equals(vers.uuid())).findFirst();
        }

        List<Contact> contacts(ItemStack phone) {
            SimCard c = AppelGameTests.exiger(helper, PhoneSims.line(serveur(), phone), "ligne du destinataire");
            return PhoneDirectory.get(serveur()).contacts(c.simId());
        }

        Optional<String> fiche(ItemStack phone, String numero) {
            return contacts(phone).stream().filter(c -> c.number().equals(numero)).map(Contact::name).findFirst();
        }

        /** L'horloge de l'économie a été avancée par l'essai (lots à l'heure réelle) : rendue à la fin. */
        boolean horlogeAvancee;

        /** Ferme les écrans ; le départ des joueurs fait tomber leurs demandes ({@code ShareService.onLoggedOut}). */
        void fermer() {
            if (a.connecte()) {
                PhoneService.close(a.joueur(), CloseReason.CLOSED);
            }
            essai.fermer();
            if (horlogeAvancee) {
                EconomyClock.reset();
            }
        }
    }

    private interface Corps {
        void run(Decor d);
    }

    private static void essai(GameTestHelper helper, String prefixe, Corps corps) {
        Decor d = new Decor(helper, prefixe);
        try {
            corps.run(d);
        } finally {
            d.fermer();
        }
        helper.succeed();
    }

    private static String nom(JoueurDeTest j) {
        return j.joueur().getGameProfile().getName();
    }

    private static String cle(Component c) {
        return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey()
                : c.getString();
    }

    private static void oublierFrein(UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("frein du téléphone introuvable", e);
        }
    }

    private static String mot(String suffixe) {
        return ShareService.MESSAGE + suffixe;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A
    // ---------------------------------------------------------------------------------------------

    /**
     * Le chemin entier : la liste montre B sous le nom de son téléphone ; l'envoi le prévient (chat cliquable et son) ;
     * « Accepter » ajoute la fiche (numéro de A, nom du propriétaire du téléphone de A) au carnet de B ; A l'apprend.
     */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void envoyerPuisAccepter(GameTestHelper helper) {
        essai(helper, "ok", d -> {
            JoueurDeTest b = d.avecLigne("pn_ok_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            PhoneSharePacket liste = d.liste(d.a);
            Optional<PhoneSharePacket.Row> ligneB = liste.rows().stream()
                    .filter(r -> r.id() == b.joueur().getId()).findFirst();
            helper.assertTrue(ligneB.isPresent(), "B dans la liste : " + liste.rows());
            helper.assertTrue(liste.rows().stream().noneMatch(r -> r.id() == d.a.joueur().getId()),
                    "l'envoyeur n'est pas dans sa propre liste");
            helper.assertTrue(ligneB.get().name().equals(nom(b)),
                    "téléphone sans propriétaire : le nom du porteur, vu " + ligneB.get().name());
            b.composantsRecus();
            b.sonsNommesRecus();
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "mot de l'envoi");
            List<Component> recus = b.composantsRecus();
            helper.assertTrue(recus.size() == 1, "un message à B : " + recus);
            List<String> liens = JoueurDeTest.commandes(recus.get(0));
            ShareBook.Request r = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande en attente");
            helper.assertTrue(liens.equals(List.of("/phoneshare accept " + r.id(), "/phoneshare decline " + r.id())),
                    "liens Accepter et Refuser : " + liens);
            helper.assertTrue(b.sonsNommesRecus().contains("minecraft:block.note_block.chime"), "le son de l'offre");
            helper.assertTrue(r.number().equals(d.carteA.number()) && r.name().equals(nom(d.a)),
                    "le numéro de A sous le nom du propriétaire de son téléphone");
            d.a.clesRecues();
            List<String> cles = d.commande(b, liens.get(0).substring(1));
            helper.assertTrue(cles.contains(mot("saved")), "B : fiche ajoutée, vu " + cles);
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).equals(Optional.of(nom(d.a))),
                    "fiche au carnet de B : " + d.contacts(phoneB));
            helper.assertTrue(d.a.clesRecues().contains(ACCEPTED), "A apprend l'acceptation");
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "plus rien en attente");
        });
    }

    /** Huit blocs compris ; au-delà, ni dans la liste ni à l'envoi. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void huitBlocsAuPlus(GameTestHelper helper) {
        essai(helper, "loin", d -> {
            // Le trajet des yeux reste dans l'aire de l'essai (les gabarits voisins sont à côté).
            d.aller(d.a, 0.0, 7.0);
            JoueurDeTest b = d.avecLigne("pn_lo_b", 8.0, 7.0);
            helper.assertTrue(d.liste(d.a, b), "à 8 blocs pile : listé");
            d.aller(b, 7.0, 3.0); // racine de 65 : 8,06 blocs
            helper.assertTrue(!d.liste(d.a, b), "à 8,06 blocs : pas listé");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("gone")), "à 8,06 blocs : refusé à l'envoi");
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "aucune demande");
        });
    }

    /** Un mur entre les deux : ni listé ni envoyé. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void unMurEntreLesDeux(GameTestHelper helper) {
        essai(helper, "mur", d -> {
            JoueurDeTest b = d.avecLigne("pn_mu_b", 6.5, 5.5);
            helper.assertTrue(d.liste(d.a, b), "sans mur : listé");
            for (int y = 0; y <= 2; y++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
                }
            }
            helper.assertTrue(!d.liste(d.a, b), "derrière un mur : pas listé");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("gone")), "derrière un mur : refusé");
            for (int y = 0; y <= 2; y++) {
                for (int z = 3; z <= 7; z++) {
                    helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
                }
            }
        });
    }

    /** Un spectateur n'est pas dans la liste. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void pasDeSpectateur(GameTestHelper helper) {
        essai(helper, "spec", d -> {
            JoueurDeTest b = d.avecLigne("pn_sp_b", 6.5, 5.5);
            helper.assertTrue(d.liste(d.a, b), "listé au départ");
            b.joueur().setGameMode(GameType.SPECTATOR);
            helper.assertTrue(!d.liste(d.a, b), "spectateur : pas listé");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("gone")), "spectateur : refusé");
            b.joueur().setGameMode(GameType.SURVIVAL);
        });
    }

    /** Un joueur invisible n'est pas dans la liste. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void pasDInvisible(GameTestHelper helper) {
        essai(helper, "invis", d -> {
            JoueurDeTest c = d.avecLigne("pn_in_c", 5.5, 3.5);
            helper.assertTrue(d.liste(d.a, c), "listé au départ");
            c.joueur().setInvisible(true);
            helper.assertTrue(!d.liste(d.a, c), "invisible : pas listé");
            helper.assertTrue(d.envoyer(d.a, c).equals(mot("gone")), "invisible : refusé");
            c.joueur().setInvisible(false);
        });
    }

    /** Sans téléphone, ou un téléphone sans carte : pas listé. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void sansCarteOnNeRecoitRien(GameTestHelper helper) {
        essai(helper, "nue", d -> {
            JoueurDeTest b = d.avecLigne("pn_nu_b", 6.5, 5.5);
            helper.assertTrue(d.liste(d.a, b), "avec sa carte : listé");
            ItemStack phone = b.joueur().getMainHandItem();
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(!d.liste(d.a, b), "sans téléphone : pas listé");
            ItemStack vide = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(vide, PhoneConfig.capacity());
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, vide);
            helper.assertTrue(!d.liste(d.a, b), "téléphone sans carte : pas listé");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("gone")), "sans carte : refusé");
            b.joueur().getInventory().add(phone);
            helper.assertTrue(d.liste(d.a, b), "la carte dans le sac : listé de nouveau");
        });
    }

    /** L'envoyeur sans carte active (suspendue, puis retirée) : rien ne part. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void lEnvoyeurDoitAvoirUneCarteActive(GameTestHelper helper) {
        essai(helper, "act", d -> {
            JoueurDeTest b = d.avecLigne("pn_ac_b", 6.5, 5.5);
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.SUSPENDED),
                    "carte suspendue");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("no_line")), "carte suspendue : refusé");
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "aucune demande");
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.ACTIVE), "carte active");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "carte active : envoyé");
        });
    }

    /** Une réponse ne vaut que pour son destinataire : un tiers et l'envoyeur lui-même sont refusés. */
    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void uneReponseNeVautQuePourSonDestinataire(GameTestHelper helper) {
        essai(helper, "tiers", d -> {
            JoueurDeTest b = d.avecLigne("pn_ti_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_ti_c", 5.5, 3.5);
            ItemStack phoneC = c.joueur().getMainHandItem();
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "envoyé à B");
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            helper.assertTrue(d.commande(c, "phoneshare accept " + id).contains(mot("expired")), "C refusé");
            helper.assertTrue(d.commande(d.a, "phoneshare accept " + id).contains(mot("expired")), "A refusé");
            helper.assertTrue(d.commande(c, "phoneshare decline " + id).contains(mot("expired")), "C ne refuse pas");
            helper.assertTrue(d.fiche(phoneC, d.carteA.number()).isEmpty(), "rien chez C");
            helper.assertTrue(d.demande(d.a, b).isPresent(), "la demande attend toujours B");
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("saved")), "B accepte");
        });
    }

    /** Refuser : rien n'est ajouté, l'envoyeur l'apprend. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void refuser(GameTestHelper helper) {
        essai(helper, "non", d -> {
            JoueurDeTest b = d.avecLigne("pn_no_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            d.a.clesRecues();
            helper.assertTrue(d.commande(b, "phoneshare decline " + id).contains(mot("declined")), "B refuse");
            helper.assertTrue(d.a.clesRecues().contains(mot("refused")), "A apprend le refus");
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).isEmpty(), "rien au carnet de B");
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("expired")), "plus valable");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B
    // ---------------------------------------------------------------------------------------------

    /** Un numéro déjà au carnet : la fiche existante est gardée, on le dit. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void unNumeroDejaConnuGardeSaFiche(GameTestHelper helper) {
        essai(helper, "connu", d -> {
            JoueurDeTest b = d.avecLigne("pn_co_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            SimCard carteB = AppelGameTests.exiger(helper, PhoneSims.line(d.serveur(), phoneB), "ligne de B");
            PhoneDirectory.get(d.serveur()).saveContact(carteB.simId(), d.carteA.number(), "Maman");
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("known")), "déjà connu");
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).equals(Optional.of("Maman")), "fiche gardée");
        });
    }

    /** La fiche va à la carte du téléphone de la main principale, puis de la seconde main. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void laMainPrincipaleDAbord(GameTestHelper helper) {
        essai(helper, "main", d -> {
            JoueurDeTest b = d.avecLigne("pn_ma_b", 6.5, 5.5);
            ItemStack premier = b.joueur().getMainHandItem();
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // Le premier téléphone en case 1 de la barre, la main sur la case 5 : parcourir le sac dans l'ordre des
            // cases trouverait le premier d'abord.
            b.joueur().getInventory().setItem(1, premier);
            b.joueur().getInventory().selected = 5;
            d.essai.ligne(d.op, b); // un second, avec sa carte, en main principale
            ItemStack second = b.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("saved")), "accepté");
            helper.assertTrue(d.fiche(second, d.carteA.number()).isPresent(), "dans la carte de la main principale");
            helper.assertTrue(d.fiche(premier, d.carteA.number()).isEmpty(), "pas dans celle du sac");
            // Seconde main seulement : c'est elle qui reçoit, avant le sac.
            PhoneDirectory.get(d.serveur()).removeContact(
                    PhoneSims.line(d.serveur(), second).orElseThrow().simId(), d.carteA.number());
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            b.joueur().setItemInHand(InteractionHand.OFF_HAND, second);
            horlogeApres(d, ShareBook.TTL_MILLIS); // 60 s avant une nouvelle offre au même destinataire
            d.envoyer(d.a, b);
            int id2 = AppelGameTests.exiger(helper, d.demande(d.a, b), "seconde demande").id();
            helper.assertTrue(d.commande(b, "phoneshare accept " + id2).contains(mot("saved")), "accepté");
            helper.assertTrue(d.fiche(second, d.carteA.number()).isPresent(), "dans la carte de la seconde main");
            helper.assertTrue(d.fiche(premier, d.carteA.number()).isEmpty(), "toujours pas dans celle du sac");
        });
    }

    /** Avance l'horloge de l'économie au-delà de l'écart du frein de l'envoi (3 s). */
    private static void horlogeDuDebit(Decor d) {
        horlogeApres(d, ShareBook.GAP_MILLIS + 1);
    }

    /**
     * Avance l'horloge de l'économie de {@code millis}, sans attendre : le lot B tourne à l'heure réelle, et
     * {@code Decor.fermer} la rend à la fin de l'essai.
     */
    private static void horlogeApres(Decor d, long millis) {
        long apres = EconomyClock.now() + millis;
        EconomyClock.setClock(() -> apres);
        d.horlogeAvancee = true;
    }

    /** L'acceptation n'exige plus la proximité (lot à l'horloge figée par place : elle n'y joue aucun rôle). */
    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, timeoutTicks = 200)
    public static void accepterDeLoin(GameTestHelper helper) {
        essai(helper, "loin2", d -> {
            JoueurDeTest b = d.avecLigne("pn_l2_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            d.aller(b, 6.5, 5.5 + 60);
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("saved")), "accepté de loin");
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).isPresent(), "fiche ajoutée");
        });
    }

    /** Une demande toutes les 3 s par envoyeur. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void uneDemandeToutesLesTroisSecondes(GameTestHelper helper) {
        essai(helper, "debit", d -> {
            JoueurDeTest b = d.avecLigne("pn_de_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_de_c", 5.5, 3.5);
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "première demande");
            helper.assertTrue(d.envoyer(d.a, c).equals(mot("wait")), "une seconde dans les 3 s : freinée");
            helper.assertTrue(d.demande(d.a, c).isEmpty(), "rien vers C");
            horlogeDuDebit(d);
            helper.assertTrue(d.envoyer(d.a, c).equals(mot("sent")), "après 3 s, vers C : envoyée");
        });
    }

    /** Une seule demande en attente d'un envoyeur vers un même destinataire. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void uneSeuleDemandeEnAttente(GameTestHelper helper) {
        essai(helper, "double", d -> {
            JoueurDeTest b = d.avecLigne("pn_db_b", 6.5, 5.5);
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "première demande");
            horlogeDuDebit(d);
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("pending")), "vers B, une demande attend déjà");
            helper.assertTrue(ShareService.pending().stream().filter(r -> r.sender().equals(d.a.uuid())).count() == 1,
                    "une seule demande");
        });
    }

    /** Écran verrouillé : ni liste ni envoi. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void verrouilleRienNePart(GameTestHelper helper) {
        essai(helper, "verrou", d -> {
            JoueurDeTest b = d.avecLigne("pn_ve_b", 6.5, 5.5);
            oublierFrein(d.a.uuid());
            d.a.envoyer(new PhoneActionPacket(d.session(d.a), PhoneActionPacket.Action.LOCK, "", "", false));
            helper.assertTrue(d.geste(d.a, PhoneCallActionPacket.Action.SHARE_SCAN, "") == null, "aucune liste");
            helper.assertTrue(d.geste(d.a, PhoneCallActionPacket.Action.SHARE_SEND,
                    Integer.toString(b.joueur().getId())) == null, "aucun envoi");
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "aucune demande");
        });
    }

    /** Le départ du destinataire fait tomber la demande (« sans réponse ») ; celui de l'envoyeur aussi. */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void unDepartFaitTomberLaDemande(GameTestHelper helper) {
        essai(helper, "part", d -> {
            JoueurDeTest b = d.avecLigne("pn_pa_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_pa_c", 5.5, 3.5);
            d.envoyer(d.a, b);
            helper.assertTrue(d.demande(d.a, b).isPresent(), "demande vers B");
            d.a.clesRecues();
            b.quitter();
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "B parti : demande tombée");
            helper.assertTrue(d.a.clesRecues().contains(mot("no_answer")), "A : sans réponse");
            // C envoie à A, puis A part.
            d.deverrouiller(c);
            helper.assertTrue(d.envoyer(c, d.a).equals(mot("sent")), "C envoie à A");
            int id = AppelGameTests.exiger(helper, d.demande(c, d.a), "demande vers A").id();
            ShareBook.Request r = d.demande(c, d.a).get();
            PhoneService.close(c.joueur(), CloseReason.CLOSED);
            c.quitter();
            helper.assertTrue(ShareService.pending().stream().noneMatch(x -> x.id() == r.id()),
                    "C parti : sa demande tombe");
            helper.assertTrue(d.commande(d.a, "phoneshare accept " + id).contains(mot("expired")), "plus valable");
        });
    }

    /** L'envoyeur qui n'a plus sa carte : la demande tombe (au relevé comme à la réponse). */
    @GameTest(template = "test/plancher", batch = LOT_B, timeoutTicks = 200)
    public static void lEnvoyeurPerdSaCarte(GameTestHelper helper) {
        essai(helper, "perd", d -> {
            JoueurDeTest b = d.avecLigne("pn_pe_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            PhoneService.close(d.a.joueur(), CloseReason.CLOSED);
            ServerPlayer pa = d.a.joueur();
            ItemStack phone = pa.getMainHandItem();
            pa.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // le téléphone de A n'est plus sur lui
            ShareService.sweep(d.serveur());
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "le relevé fait tomber la demande");
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("expired")), "plus valable");
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).isEmpty(), "rien au carnet de B");
            pa.setItemInHand(InteractionHand.MAIN_HAND, phone);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot C : reprise de la relecture
    // ---------------------------------------------------------------------------------------------

    /**
     * « Son téléphone » : enregistré à son nom, ou sans propriétaire. Celui d'un autre, même en main principale, ne met
     * pas dans la liste, ne reçoit aucune fiche et ne dit rien de son carnet ; celui du joueur, dans le sac, passe
     * devant lui.
     */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void leTelephoneDUnAutreNeComptePas(GameTestHelper helper) {
        essai(helper, "autre", d -> {
            JoueurDeTest b = d.avecLigne("pn_au_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_au_c", 5.5, 3.5);
            // Chacun devient propriétaire du sien (code posé par les paquets du mod).
            d.deverrouiller(b);
            d.deverrouiller(c);
            ItemStack aB = b.joueur().getMainHandItem();
            ItemStack deC = c.joueur().getMainHandItem();
            PhoneService.close(b.joueur(), CloseReason.CLOSED);
            PhoneService.close(c.joueur(), CloseReason.CLOSED);
            SimCard carteC = AppelGameTests.exiger(helper, PhoneSims.line(d.serveur(), deC), "ligne de C");
            PhoneDirectory.get(d.serveur()).saveContact(carteC.simId(), d.carteA.number(), "Secret de C");
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "B, son téléphone en main : envoyé");
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();

            // B ne porte plus que le téléphone de C, verrouillé : ni liste, ni fiche, ni « déjà enregistré ».
            c.joueur().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            b.joueur().setItemInHand(InteractionHand.MAIN_HAND, deC);
            helper.assertTrue(!d.liste(d.a, b), "le téléphone d'un autre : pas listé");
            List<String> cles = d.commande(b, "phoneshare accept " + id);
            helper.assertTrue(cles.contains(mot("no_card")), "le téléphone d'un autre ne reçoit pas : " + cles);
            helper.assertTrue(d.fiche(deC, d.carteA.number()).equals(Optional.of("Secret de C")), "carnet de C intact");
            helper.assertTrue(d.demande(d.a, b).isPresent(), "la demande attend encore");

            // Le sien dans le sac : listé sous son nom, et c'est lui qui reçoit, pas celui de la main.
            b.joueur().getInventory().setItem(20, aB);
            PhoneSharePacket liste = d.liste(d.a);
            helper.assertTrue(liste.rows().stream()
                    .anyMatch(r -> r.id() == b.joueur().getId() && r.name().equals(nom(b))),
                    "listé sous le nom de son téléphone : " + liste.rows());
            cles = d.commande(b, "phoneshare accept " + id);
            helper.assertTrue(cles.contains(mot("saved")), "reçu sur le sien : " + cles);
            helper.assertTrue(d.fiche(aB, d.carteA.number()).equals(Optional.of(nom(d.a))), "fiche chez B");
            helper.assertTrue(d.fiche(deC, d.carteA.number()).equals(Optional.of("Secret de C")), "carnet de C intact");
        });
    }

    /** L'envoyeur sans carte active (suspendue, puis retirée) : la liste des voisins est vide, et on dit pourquoi. */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 200)
    public static void sansCarteActiveAucuneListe(GameTestHelper helper) {
        essai(helper, "scan", d -> {
            JoueurDeTest b = d.avecLigne("pn_sc_b", 6.5, 5.5);
            PhoneSharePacket avant = d.liste(d.a);
            helper.assertTrue(avant.rows().stream().anyMatch(r -> r.id() == b.joueur().getId())
                    && avant.message().equals(Component.empty()), "carte active : B listé, sans mot");
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.SUSPENDED),
                    "carte suspendue");
            PhoneSharePacket p = d.liste(d.a);
            helper.assertTrue(p.rows().isEmpty(), "suspendue : aucune personne, vu " + p.rows());
            helper.assertTrue(cle(p.message()).equals(mot("no_line")), "suspendue : le mot, vu " + p.message());
            PhoneSharePacket envoi = d.geste(d.a, PhoneCallActionPacket.Action.SHARE_SEND,
                    Integer.toString(b.joueur().getId()));
            helper.assertTrue(envoi != null && envoi.rows().isEmpty() && cle(envoi.message()).equals(mot("no_line")),
                    "à l'envoi : ni liste ni envoi, vu " + (envoi == null ? "rien" : envoi.rows()));
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.ACTIVE), "carte active");
            helper.assertTrue(d.liste(d.a, b), "réactivée : B de nouveau listé");
            helper.assertTrue(PhoneSims.remove(d.a.joueur(), d.a.joueur().getMainHandItem()) == PhoneSims.Remove.DONE,
                    "carte retirée");
            p = d.liste(d.a);
            helper.assertTrue(p.rows().isEmpty() && cle(p.message()).equals(mot("no_line")),
                    "sans carte : liste vide et le mot, vu " + p.rows());
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Lot à l'horloge figée
    // ---------------------------------------------------------------------------------------------

    /** Passé 60 s, la réponse est refusée même si le relevé n'a pas encore retiré la demande. */
    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, timeoutTicks = 200)
    public static void uneReponseApresSoixanteSecondesEstRefusee(GameTestHelper helper) {
        essai(helper, "age", d -> {
            JoueurDeTest b = d.avecLigne("pn_ag_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_ag_c", 5.5, 3.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            ItemStack phoneC = c.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            horloge += ShareBook.TTL_MILLIS; // aucun relevé
            d.a.clesRecues();
            List<String> cles = d.commande(b, "phoneshare accept " + id);
            helper.assertTrue(cles.contains(mot("expired")), "à 60 s : plus valable, vu " + cles);
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).isEmpty(), "rien au carnet de B");
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "retirée");
            List<String> chezA = d.a.clesRecues();
            helper.assertTrue(chezA.contains(mot("no_answer")) && !chezA.contains(ACCEPTED),
                    "A : sans réponse, vu " + chezA);
            // Témoin : une milliseconde avant la fin, la réponse passe.
            helper.assertTrue(d.envoyer(d.a, c).equals(mot("sent")), "vers C");
            int id2 = AppelGameTests.exiger(helper, d.demande(d.a, c), "demande vers C").id();
            horloge += ShareBook.TTL_MILLIS - 1;
            helper.assertTrue(d.commande(c, "phoneshare accept " + id2).contains(mot("saved")), "à 59,999 s");
            helper.assertTrue(d.fiche(phoneC, d.carteA.number()).isPresent(), "fiche chez C");
        });
    }

    /**
     * Une demande tombée parce que l'envoyeur a perdu sa carte : pas de nouvelle offre au même destinataire (ni message
     * ni son) avant la fin des 60 s de la précédente.
     */
    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, timeoutTicks = 200)
    public static void pasDeNouvelleOffreAvantLaFinDesSoixanteSecondes(GameTestHelper helper) {
        essai(helper, "redit", d -> {
            JoueurDeTest b = d.avecLigne("pn_re_b", 6.5, 5.5);
            JoueurDeTest c = d.avecLigne("pn_re_c", 5.5, 3.5);
            long depart = horloge;
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "première offre");
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.SUSPENDED),
                    "carte perdue");
            ShareService.sweep(d.serveur());
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "la demande est tombée");
            helper.assertTrue(d.essai.registre().setState(d.carteA.simId(), SimCard.State.ACTIVE), "carte revenue");
            horloge = depart + ShareBook.GAP_MILLIS + 1;
            b.composantsRecus();
            b.sonsNommesRecus();
            String retour = d.envoyer(d.a, b);
            helper.assertTrue(retour.equals(mot("recent")), "trop tôt, vu " + retour);
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "aucune demande");
            List<Component> recus = b.composantsRecus();
            helper.assertTrue(recus.isEmpty(), "aucun message à B : " + recus);
            helper.assertTrue(!b.sonsNommesRecus().contains("minecraft:block.note_block.chime"), "aucun son");
            helper.assertTrue(d.envoyer(d.a, c).equals(mot("sent")), "un autre destinataire passe");
            horloge = depart + ShareBook.TTL_MILLIS - 1;
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("recent")), "à 59,999 s : encore trop tôt");
            horloge = depart + ShareBook.TTL_MILLIS;
            b.composantsRecus();
            helper.assertTrue(d.envoyer(d.a, b).equals(mot("sent")), "à 60 s : une nouvelle offre");
            helper.assertTrue(b.composantsRecus().size() == 1, "un message à B");
        });
    }

    /** Au bout de 60 s sans réponse, la demande tombe : l'envoyeur l'apprend, le lien ne sert plus. */
    @GameTest(template = "test/plancher", batch = LOT_HORLOGE, timeoutTicks = 200)
    public static void uneDemandeExpireApresSoixanteSecondes(GameTestHelper helper) {
        essai(helper, "expire", d -> {
            JoueurDeTest b = d.avecLigne("pn_ex_b", 6.5, 5.5);
            ItemStack phoneB = b.joueur().getMainHandItem();
            d.envoyer(d.a, b);
            int id = AppelGameTests.exiger(helper, d.demande(d.a, b), "demande").id();
            horloge += ShareBook.TTL_MILLIS - 1;
            ShareService.sweep(d.serveur());
            helper.assertTrue(d.demande(d.a, b).isPresent(), "59,999 s : toujours là");
            d.a.clesRecues();
            horloge += 1;
            ShareService.sweep(d.serveur());
            helper.assertTrue(d.demande(d.a, b).isEmpty(), "60 s : tombée");
            helper.assertTrue(d.a.clesRecues().contains(mot("no_answer")), "A : sans réponse");
            helper.assertTrue(d.commande(b, "phoneshare accept " + id).contains(mot("expired")), "plus valable");
            helper.assertTrue(d.fiche(phoneB, d.carteA.number()).isEmpty(), "rien au carnet de B");
        });
    }
}
