package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.grid.supplier.PowerBilling;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneActionPacket.Action;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneService.Outcome;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.OperatorActionPacket;
import dev.oneforallsurvival.phone.net.OperatorActions;
import dev.oneforallsurvival.phone.net.OperatorDay;
import dev.oneforallsurvival.phone.net.OperatorPanels;
import dev.oneforallsurvival.phone.net.OperatorTillMenu;
import dev.oneforallsurvival.phone.net.OperatorView;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimRequest;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
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
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le panneau de l'opérateur (lot T2, tâche 9) : une seule vue ({@code OperatorViews.build}), un seul jeu de règles
 * ({@code OperatorActions.apply}), sur le bloc de l'antenne comme depuis le téléphone ; la caisse, en personne sur
 * l'antenne principale ; le tarif au lendemain (clôture de minuit {@link OperatorDay}, qui ne facture rien).
 *
 * <ul>
 *   <li>Lot {@code panneau_a} : nommer, la consommation en direct, la caisse (fondateur, équipe ; ni maire ni ingénieur ;
 *       antenne principale seulement), de loin rien, l'appli « Antenne » du téléphone.
 *   <li>Lot {@code panneau_cartes} : émettre, refuser, SIM sans nom, suspendre et réactiver ; rien pour un non-fondateur,
 *       pour la carte d'un autre opérateur, pour une session du téléphone périmée, ni inventaire plein.
 *   <li>Lot {@code panneau_vue} : ni jeton ni carte d'un autre opérateur dans les octets ; ni caisse ni nom depuis le
 *       téléphone ; le maire règle le tarif sans gérer les cartes ; le même geste par le bloc et par le téléphone ;
 *       « Remplacer la carte ».
 *   <li>Lot {@code panneau_tarif} (horloge fixée à 14 h par l'{@code @BeforeBatch}, rendue par l'{@code @AfterBatch}) :
 *       le tarif ne vaut qu'au lendemain, plafond, plafond abaissé, horloge qui recule, FE du jour.
 * </ul>
 *
 * <p>Les gestes passent par le canal du mod ({@link JoueurDeTest#envoyer}, traités sur place) ; les vues sont décodées des
 * octets envoyés. Chaque essai fonde ses opérateurs sous des noms qui lui sont propres (leçon du 2026-11-24) ; les joueurs
 * sont replacés après l'entrée ; dans une séquence, aucune exception autre qu'une assertion, et l'essai ne se range qu'à
 * sa dernière étape (leçon du 2026-11-20) ; un essai rouge est rangé par l'{@code @AfterBatch} de son lot. Les aides
 * ({@code Essai}) servent aussi aux essais de l'appli « Antenne » ({@link AppliAntenneGameTests}, tâche 10).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PanneauOperateurGameTests {

    private static final String LOT_A = "panneau_a";
    private static final String LOT_CARTES = "panneau_cartes";
    private static final String LOT_VUE = "panneau_vue";
    private static final String LOT_TARIF = "panneau_tarif";

    static final BlockPos A = new BlockPos(1, 0, 1);
    static final BlockPos B = new BlockPos(6, 0, 6);
    static final Vec3 JOUEUR = new Vec3(3.5, 0, 3.5);
    static final int RESERVE = 40_000;
    static final String CODE = "4821";
    static final String MSG = OperatorActions.MESSAGE;
    private static final long MINUTE = 60_000L;

    private static final List<Essai> OUVERTS = new ArrayList<>();

    private PanneauOperateurGameTests() {}

    @AfterBatch(batch = LOT_A)
    public static void apresA(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_CARTES)
    public static void apresCartes(ServerLevel level) {
        apres();
    }

    @AfterBatch(batch = LOT_VUE)
    public static void apresVue(ServerLevel level) {
        apres();
    }

    /** Le lot du tarif : l'horloge de l'économie fixée à 14 h aujourd'hui (moins d'un jour d'écart : la paie ne saute pas). */
    @BeforeBatch(batch = LOT_TARIF)
    public static void avantTarif(ServerLevel level) {
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate aujourdhui = LocalDate.now(zone);
        long quatorzeHeures = aujourdhui.atTime(14, 0).atZone(zone).toInstant().toEpochMilli();
        EconomyClock.setClock(() -> quatorzeHeures);
    }

    @AfterBatch(batch = LOT_TARIF)
    public static void apresTarif(ServerLevel level) {
        EconomyClock.reset();
        OperatorDay.install(OperatorDay.SERVER);
        PowerBilling.install(PowerBilling.SERVER);
        apres();
    }

    static void apres() {
        for (Essai e : new ArrayList<>(OUVERTS)) {
            e.rendre();
        }
        OUVERTS.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Ce qu'un essai a ouvert : joueurs, rôles, équipe, maire, opérateurs, téléphones ; tout est rendu, même après un échec. */
    static final class Essai {
        final GameTestHelper helper;
        final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> fondateurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final Set<UUID> telephones = new HashSet<>();
        private boolean maireTouche;
        @Nullable
        private UUID maireAvant;
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
            for (ItemEntity reste : helper.getLevel().getEntitiesOfClass(ItemEntity.class, boite())) {
                reste.discard();
            }
        }

        AABB boite() {
            BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
            BlockPos b = helper.absolutePos(new BlockPos(8, 4, 8));
            return new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        PhoneOperators registre() {
            return PhoneOperators.get(serveur());
        }

        RoleBook livre() {
            return RoleSavedData.get(serveur()).book();
        }

        /** Un citoyen de survie, sans opérateur, replacé dans le gabarit. */
        JoueurDeTest joueur(String nom) {
            Vec3 ici = helper.absoluteVec(JOUEUR);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, ici);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            fondateurs.add(j.uuid());
            livre().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            OperateurDEssai.oublier(serveur(), j.uuid());
            OperatorActions.clearThrottle(j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            return j;
        }

        JoueurDeTest maire(String nom) {
            JoueurDeTest j = joueur(nom);
            if (!maireTouche) {
                maireTouche = true;
                maireAvant = livre().mayor().orElse(null);
            }
            livre().appointMayor(j.uuid());
            return j;
        }

        /** Un ingénieur qui tient l'outil de maintenance en main principale. */
        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = joueur(nom);
            livre().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RepairRegistry.MAINTENANCE_TOOL.get()));
            return j;
        }

        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = joueur(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        /** Pose une antenne neuve par {@code j} en {@code pos} (un clic sur le sol). */
        void poser(JoueurDeTest j, BlockPos pos) {
            ServerPlayer p = j.joueur();
            ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack pile = new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, pile);
            BlockPos sol = helper.absolutePos(pos.below());
            BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
            InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
            p.setItemInHand(InteractionHand.MAIN_HAND, avant);
            helper.assertTrue(r.consumesAction(), "l'antenne devait se poser en " + pos + " : " + r);
        }

        /** Une antenne posée par {@code j} en {@code pos}, fondée sous {@code nom}, avec {@code fe} FE en réserve. */
        PhoneOperator fonder(JoueurDeTest j, BlockPos pos, String nom, int fe) {
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            poser(j, pos);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            verser(pos, fe);
            return fonde.get().operator();
        }

        void verser(BlockPos pos, int fe) {
            AntennaBlockEntity be = antenne(pos);
            int reste = fe;
            while (reste > 0) {
                int recu = be.energy().receiveEnergy(reste, false);
                if (recu <= 0) {
                    break;
                }
                reste -= recu;
            }
        }

        AntennaBlockEntity antenne(BlockPos pos) {
            helper.assertTrue(helper.getBlockEntity(pos) instanceof AntennaBlockEntity, "pas d'antenne en " + pos);
            return (AntennaBlockEntity) helper.getBlockEntity(pos);
        }

        boolean enService(BlockPos pos) {
            AntennaBlockEntity be = antenne(pos);
            Optional<AntennaRecord> r = be.antennaId() == null ? Optional.empty() : registre().antenna(be.antennaId());
            return r.isPresent() && AntennaBlockEntity.inService(r.get(), helper.getLevel().getGameTime());
        }

        PhoneOperator op(PhoneOperator op) {
            Optional<PhoneOperator> now = registre().byId(op.id());
            helper.assertTrue(now.isPresent(), "opérateur " + op.name() + " disparu");
            return now.get();
        }

        /** Une carte de {@code op} émise au registre au nom de {@code titulaire} (aucune pile). */
        SimCard carte(PhoneOperator op, @Nullable JoueurDeTest titulaire) {
            return registre().issue(op.id(), titulaire == null ? null : titulaire.uuid(),
                    titulaire == null ? "" : titulaire.joueur().getGameProfile().getName(), RandomSource.create(),
                    EconomyClock.now());
        }

        SimCard carte(SimCard c) {
            Optional<SimCard> now = registre().card(c.simId());
            helper.assertTrue(now.isPresent(), "carte disparue");
            return now.get();
        }

        /** Le clic droit du joueur sur l'antenne : la seule vue reçue (le panneau s'ouvre). */
        OperatorView ouvrir(JoueurDeTest j, BlockPos pos) {
            j.panneauxOperateurRecus();
            ServerPlayer p = j.joueur();
            BlockPos abs = helper.absolutePos(pos);
            BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                    tir);
            List<OperatorView> recues = j.panneauxOperateurRecus();
            helper.assertTrue(recues.size() == 1, "une vue à l'ouverture, vu " + recues.size());
            return recues.get(0);
        }

        /** Un geste du panneau, par le canal du mod (traité sur place) ; la dernière vue reçue, ou nulle. */
        @Nullable
        OperatorView geste(JoueurDeTest j, int session, OperatorActionPacket.Action action, String arg, long value) {
            j.panneauxOperateurRecus();
            OperatorActions.clearThrottle(j.uuid());
            j.envoyer(new OperatorActionPacket(session, action, arg, value));
            List<OperatorView> recues = j.panneauxOperateurRecus();
            return recues.isEmpty() ? null : recues.get(recues.size() - 1);
        }

        /** Un téléphone chargé dans la main principale de {@code j} ; la pile de la main. */
        ItemStack telephone(JoueurDeTest j) {
            ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
            PhoneItem.store(phone, PhoneConfig.capacity());
            telephones.add(PhoneItem.ensurePhoneId(phone));
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, phone);
            return j.joueur().getItemInHand(InteractionHand.MAIN_HAND);
        }

        /** Une ligne de {@code op} : une carte au nom de {@code j}, glissée dans un téléphone neuf tenu en main. */
        ItemStack ligne(PhoneOperator op, JoueurDeTest j) {
            ItemStack phone = telephone(j);
            SimCard c = carte(op, j);
            j.joueur().getInventory().setItem(9, SimCardItem.create(c, op.name()));
            PhoneSims.Insert r = PhoneSims.insert(j.joueur(), phone, 9);
            helper.assertTrue(r == PhoneSims.Insert.DONE, "insertion : " + r);
            return phone;
        }

        /** Ouvre le téléphone tenu, choisit le code, puis l'appli « Antenne » ; l'issue et les vues du panneau reçues. */
        List<OperatorView> appli(JoueurDeTest j, boolean deverrouiller) {
            if (deverrouiller) {
                PhoneService.close(j.joueur(), CloseReason.CLOSED);
                helper.assertTrue(PhoneService.open(j.joueur(), InteractionHand.MAIN_HAND) == Outcome.OPENED,
                        "ouverture du téléphone");
                telephoneGeste(j, Action.SETUP, CODE, false);
            }
            j.panneauxOperateurRecus();
            telephoneGeste(j, Action.OPEN_OPERATOR_APP, "", false);
            return j.panneauxOperateurRecus();
        }

        @Nullable
        PhoneStatePacket telephoneGeste(JoueurDeTest j, Action action, String first, boolean flag) {
            Optional<PhoneSession> s = PhoneService.session(j.uuid());
            helper.assertTrue(s.isPresent(), "session du téléphone attendue");
            oublierFreinTelephone(helper, j.uuid());
            j.etatsTelephoneRecus();
            PhoneService.act(j.joueur(), new PhoneActionPacket(s.get().id(), action, first, "", flag));
            List<PhoneStatePacket> recus = j.etatsTelephoneRecus();
            return recus.isEmpty() ? null : recus.get(recus.size() - 1);
        }

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
            for (GameProfile profil : equipe) {
                serveur.getPlayerList().getOps().remove(profil);
            }
            if (maireTouche) {
                if (maireAvant != null) {
                    livre().appointMayor(maireAvant);
                } else {
                    livre().removeMayor();
                }
            }
            for (JoueurDeTest j : joueurs) {
                OperatorPanels.close(j.uuid());
                if (j.connecte()) {
                    PhoneService.close(j.joueur(), CloseReason.CLOSED);
                    j.joueur().closeContainer();
                    j.joueur().getInventory().clearContent();
                    j.quitter();
                }
                registre.takeNotices(j.uuid());
                livre().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
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

    static void oublierFreinTelephone(GameTestHelper helper, UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            helper.fail("frein du téléphone introuvable : " + e);
        }
    }

    static int caseDe(ServerPlayer p, UUID simId) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (SimCardItem.simId(inv.getItem(i)).filter(simId::equals).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    static int cartesPortees(ServerPlayer p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof SimCardItem) {
                n++;
            }
        }
        return n;
    }

    static int session(GameTestHelper helper, JoueurDeTest j) {
        Optional<Integer> s = OperatorPanels.session(j.uuid());
        helper.assertTrue(s.isPresent(), "panneau ouvert attendu");
        return s.isPresent() ? s.get() : -1;
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

    /** Ni l'identifiant en binaire (deux moitiés), ni en texte (début et fin) dans ces octets. */
    private static boolean porte(byte[] octets, UUID id) {
        byte[] binaire = ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
        String texte = new String(octets, StandardCharsets.ISO_8859_1);
        return contient(octets, Arrays.copyOf(binaire, 8)) || contient(octets, Arrays.copyOfRange(binaire, 8, 16))
                || texte.contains(id.toString().substring(0, 8)) || texte.contains(id.toString().substring(28));
    }

    private static long minuit(LocalDate jour) {
        return EconomyCalendar.boundaryAfter(jour, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
    }

    // ---------------------------------------------------------------------------------------------
    // Lot panneau_a
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void nommerParLePanneau(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest p = e.joueur("pa_nom");
        JoueurDeTest inge = e.ingenieur("pa_nom_inge");
        String nom = "Panneau Nom";
        e.registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(e.serveur(), reste.founder()));
        e.poser(p, A);
        // L'ingénieur outillé voit l'antenne à fonder sans pouvoir la nommer.
        OperatorView vueInge = e.ouvrir(inge, A);
        helper.assertTrue(vueInge.operatorId().isEmpty() && !vueInge.rights().name(),
                "l'ingénieur ne nomme pas l'antenne d'un autre : " + vueInge.rights());
        e.geste(inge, vueInge.session(), OperatorActionPacket.Action.NAME, "Usurpe", 0L);
        helper.assertTrue(e.registre().byFounder(p.uuid()).isEmpty() && e.registre().byName("Usurpe").isEmpty(),
                "aucun opérateur fondé par l'ingénieur");
        OperatorView vue = e.ouvrir(p, A);
        helper.assertTrue(vue.operatorId().isEmpty() && vue.rights().name() && vue.source() == OperatorView.Source.BLOCK,
                "antenne à fonder : le champ du nom : " + vue);
        OperatorView apres = e.geste(p, vue.session(), OperatorActionPacket.Action.NAME, nom, 0L);
        Optional<PhoneOperator> op = e.registre().byFounder(p.uuid());
        helper.assertTrue(op.isPresent() && op.get().name().equals(nom), "opérateur fondé par le panneau : " + op);
        helper.assertTrue(apres != null && apres.operatorId().equals(op.get().id()) && apres.name().equals(nom)
                && !apres.rights().name(), "la vue suit : " + apres);
        helper.assertTrue(op.get().id().equals(e.antenne(A).operatorId()) && op.get().mainAntenna() != null,
                "l'antenne est inscrite, principale");
        // Un second nom ne renomme rien.
        e.geste(p, vue.session(), OperatorActionPacket.Action.NAME, "Autre Nom", 0L);
        helper.assertTrue(e.op(op.get()).name().equals(nom), "pas de second nom");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 300)
    public static void laConsommationEnDirect(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_direct");
        PhoneOperator op = e.fonder(f, A, "Panneau Direct", RESERVE);
        e.carte(op, f);
        long[] depart = {-1L};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    e.ouvrir(f, A);
                    depart[0] = helper.getLevel().getGameTime();
                })
                .thenIdle(70)
                .thenExecute(() -> {
                    List<OperatorView> vues = f.panneauxOperateurRecus();
                    helper.assertTrue(vues.size() >= 3 && vues.size() <= 4,
                            "en 70 tics, une vue toutes les 20 tics : vu " + vues.size());
                    for (int i = 1; i < vues.size(); i++) {
                        helper.assertTrue(!vues.get(i).equals(vues.get(i - 1)), "la même vue envoyée deux fois de suite");
                    }
                    OperatorView derniere = vues.get(vues.size() - 1);
                    helper.assertTrue(derniere.antennas().size() == 1, "une antenne : " + derniere.antennas());
                    OperatorView.AntennaLine ligne = derniere.antennas().get(0);
                    AntennaLoad.Breakdown reel = e.antenne(A).breakdown();
                    helper.assertTrue(ligne.load().equals(reel) && reel.clients() > 0,
                            "le détail de la vue est celui de l'antenne : " + ligne.load() + " / " + reel);
                    helper.assertTrue(ligne.main() && !ligne.overReceive() && ligne.feToday() > 0,
                            "principale, sous la réception, consommation du jour : " + ligne);
                    helper.assertTrue(vues.get(0).antennas().get(0).feToday() < ligne.feToday(),
                            "la consommation du jour monte d'une vue à l'autre");
                    e.fermer();
                })
                .thenSucceed();
    }

    /** La caisse ouverte par {@code j} sur le panneau de {@code pos} ; un retrait d'une pièce d'argent. */
    private static void viderLaCaisse(GameTestHelper helper, Essai e, JoueurDeTest j, PhoneOperator op, String qui) {
        e.registre().deposit(op.id(), Coin.SILVER, 5);
        long avant = CoinMath.valueOf(e.registre().till(op.id())) + CoinMath.valueOf(PlayerCoins.held(j.joueur()));
        OperatorView vue = e.ouvrir(j, A);
        helper.assertTrue(vue.rights().till() && vue.till() == CoinMath.valueOf(Coin.SILVER, 5),
                qui + " : la caisse se voit et s'ouvre : " + vue.rights() + " " + vue.till());
        e.geste(j, vue.session(), OperatorActionPacket.Action.OPEN_TILL, "", 0L);
        helper.assertTrue(j.joueur().containerMenu instanceof OperatorTillMenu, qui + " : l'écran de la caisse s'ouvre");
        OperatorTillMenu menu = (OperatorTillMenu) j.joueur().containerMenu;
        CoinActions.Result r = menu.apply(j.joueur(), PowerTillMenu.button(CoinRequest.withdraw(Coin.SILVER, 1)));
        helper.assertTrue(r == CoinActions.Result.DONE, qui + " : retrait " + r);
        helper.assertTrue(e.registre().till(op.id()).getOrDefault(Coin.SILVER, 0L) == 4L
                && PlayerCoins.held(j.joueur()).getOrDefault(Coin.SILVER, 0L) == 1L, qui + " : une pièce passe");
        long apres = CoinMath.valueOf(e.registre().till(op.id())) + CoinMath.valueOf(PlayerCoins.held(j.joueur()));
        helper.assertTrue(avant == apres, qui + " : la monnaie se conserve (" + avant + " puis " + apres + ")");
        r = menu.apply(j.joueur(), PowerTillMenu.button(CoinRequest.deposit(Coin.SILVER, 1)));
        helper.assertTrue(r == CoinActions.Result.BAD_REQUEST, qui + " : aucun dépôt dans la caisse : " + r);
        j.joueur().closeContainer();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leFondateurVideLaCaisse(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_caisse_f");
        PhoneOperator op = e.fonder(f, A, "Panneau Caisse F", RESERVE);
        viderLaCaisse(helper, e, f, op, "le fondateur");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lEquipeVideLaCaisse(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_caisse_o");
        JoueurDeTest eq = e.equipe("pa_caisse_eq");
        PhoneOperator op = e.fonder(f, A, "Panneau Caisse E", RESERVE);
        viderLaCaisse(helper, e, eq, op, "l'équipe");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void niLeMaireNiLIngenieurNeVidentLaCaisse(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_caisse_n");
        JoueurDeTest maire = e.maire("pa_caisse_m");
        JoueurDeTest inge = e.ingenieur("pa_caisse_i");
        PhoneOperator op = e.fonder(f, A, "Panneau Caisse N", RESERVE);
        e.registre().deposit(op.id(), Coin.GOLD, 3);
        for (JoueurDeTest j : List.of(maire, inge)) {
            String qui = j == maire ? "le maire" : "l'ingénieur";
            OperatorView vue = e.ouvrir(j, A);
            helper.assertTrue(!vue.rights().till(), qui + " ne vide pas la caisse : " + vue.rights());
            e.geste(j, vue.session(), OperatorActionPacket.Action.OPEN_TILL, "", 0L);
            helper.assertTrue(!(j.joueur().containerMenu instanceof OperatorTillMenu), qui + " : aucun écran de caisse");
            // Un menu de caisse forgé à son nom : le serveur refuse le retrait.
            OperatorTillMenu force = new OperatorTillMenu(99, j.joueur().getInventory(), helper.absolutePos(A),
                    e.registre(), op.id());
            j.joueur().containerMenu = force;
            CoinActions.Result r = force.apply(j.joueur(), PowerTillMenu.button(CoinRequest.withdraw(Coin.GOLD, 1)));
            j.joueur().containerMenu = j.joueur().inventoryMenu;
            helper.assertTrue(r != CoinActions.Result.DONE, qui + " : retrait refusé, vu " + r);
            helper.assertTrue(e.registre().till(op.id()).getOrDefault(Coin.GOLD, 0L) == 3L
                    && PlayerCoins.held(j.joueur()).isEmpty(), qui + " : la caisse n'a pas bougé");
        }
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void laCaisseNeSOuvreQueSurLAntennePrincipale(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_principale");
        PhoneOperator op = e.fonder(f, A, "Panneau Principale", RESERVE);
        e.poser(f, B);
        e.verser(B, RESERVE);
        helper.assertTrue(op.id().equals(e.antenne(B).operatorId()), "la seconde antenne rejoint l'opérateur");
        e.registre().deposit(op.id(), Coin.SILVER, 2);
        OperatorView secondaire = e.ouvrir(f, B);
        helper.assertTrue(!secondaire.rights().till() && secondaire.rights().cards(),
                "sur l'autre antenne : pas de caisse : " + secondaire.rights());
        e.geste(f, secondaire.session(), OperatorActionPacket.Action.OPEN_TILL, "", 0L);
        helper.assertTrue(!(f.joueur().containerMenu instanceof OperatorTillMenu), "aucune caisse sur l'autre antenne");
        helper.assertTrue(f.messagesRecus().contains(MSG + "till_main_only"), "on lui dit où elle s'ouvre");
        // Témoin : sur l'antenne principale, elle s'ouvre.
        OperatorView principale = e.ouvrir(f, A);
        helper.assertTrue(principale.rights().till(), "témoin : la caisse de l'antenne principale");
        ItemStack phone = e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(!vues.isEmpty() && vues.get(0).source() == OperatorView.Source.PHONE
                            && !vues.get(0).rights().till(), "depuis le téléphone : pas de caisse : " + vues);
                    e.geste(f, vues.get(0).session(), OperatorActionPacket.Action.OPEN_TILL, "", 0L);
                    helper.assertTrue(!(f.joueur().containerMenu instanceof OperatorTillMenu),
                            "aucune caisse depuis le téléphone");
                    helper.assertTrue(e.registre().till(op.id()).getOrDefault(Coin.SILVER, 0L) == 2L,
                            "la caisse n'a pas bougé");
                    helper.assertTrue(phone.is(PhoneRegistry.PHONE.get()), "téléphone");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void deLoinRienNePasse(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_loin");
        PhoneOperator op = e.fonder(f, A, "Panneau Loin", RESERVE);
        SimCard c = e.carte(op, f);
        OperatorView vue = e.ouvrir(f, A);
        BlockPos abs = helper.absolutePos(A);
        f.joueur().teleportTo(abs.getX() + 9.5, abs.getY(), abs.getZ() + 0.5);
        e.geste(f, vue.session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "à 9 blocs, la carte n'est pas suspendue");
        helper.assertTrue(OperatorPanels.session(f.uuid()).isEmpty(), "le panneau est fermé");
        // Revenu près de l'antenne, l'ancien panneau ne revit pas.
        f.joueur().teleportTo(abs.getX() + 2.5, abs.getY(), abs.getZ() + 0.5);
        e.geste(f, vue.session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "un panneau fermé ne revit pas");
        // Témoin : rouvert de près, le geste passe.
        OperatorView pres = e.ouvrir(f, A);
        e.geste(f, pres.session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "témoin : de près, suspendue");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 400)
    public static void lAppliAntenneOuvreLePanneau(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pa_appli");
        JoueurDeTest c = e.joueur("pa_appli_c");
        PhoneOperator op = e.fonder(f, A, "Panneau Appli", RESERVE);
        e.ligne(op, f);
        e.ligne(op, c);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(vues.size() == 1 && vues.get(0).source() == OperatorView.Source.PHONE
                            && vues.get(0).operatorId().equals(op.id()) && vues.get(0).pos().equals(BlockPos.ZERO),
                            "l'appli ouvre le panneau du fondateur : " + vues);
                    helper.assertTrue(OperatorPanels.session(f.uuid()).isPresent(), "panneau ouvert");
                    // Un abonné (pas fondateur) : refus sans réseau, aucun panneau.
                    List<OperatorView> abonne = e.appli(c, true);
                    helper.assertTrue(abonne.isEmpty() && OperatorPanels.session(c.uuid()).isEmpty(),
                            "un abonné n'ouvre rien : " + abonne);
                    // En mode avion : refus.
                    OperatorPanels.close(f.uuid());
                    e.telephoneGeste(f, Action.AIRPLANE, "", true);
                    PhoneStatePacket avion = e.telephoneGeste(f, Action.OPEN_OPERATOR_APP, "", false);
                    helper.assertTrue(avion != null && avion.last() == Result.NO_NETWORK
                            && f.panneauxOperateurRecus().isEmpty() && OperatorPanels.session(f.uuid()).isEmpty(),
                            "en mode avion : pas de réseau, aucun panneau");
                    e.telephoneGeste(f, Action.AIRPLANE, "", false);
                    // Sans barre : l'antenne vidée.
                    e.antenne(A).drainEnergy(Integer.MAX_VALUE);
                })
                .thenWaitUntil(() -> helper.assertTrue(!e.enService(A), "antenne hors service"))
                .thenExecute(() -> {
                    PhoneStatePacket sansBarre = e.telephoneGeste(f, Action.OPEN_OPERATOR_APP, "", false);
                    helper.assertTrue(sansBarre != null && sansBarre.last() == Result.NO_NETWORK
                            && f.panneauxOperateurRecus().isEmpty() && OperatorPanels.session(f.uuid()).isEmpty(),
                            "sans barre : pas de réseau, aucun panneau");
                    e.fermer();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot panneau_cartes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void emettreUneDemande(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_emettre");
        JoueurDeTest r = e.joueur("pc_emettre_r");
        PhoneOperator op = e.fonder(f, A, "Cartes Emettre", RESERVE);
        Optional<SimRequest> d = e.registre().request(op.id(), r.uuid(), "pc_emettre_r", EconomyClock.now());
        helper.assertTrue(d.isPresent(), "demande inscrite");
        OperatorView vue = e.ouvrir(f, A);
        helper.assertTrue(vue.requests().size() == 1 && vue.requests().get(0).id().equals(d.get().id()),
                "la demande se voit : " + vue.requests());
        r.messagesRecus();
        OperatorView apres = e.geste(f, vue.session(), OperatorActionPacket.Action.ISSUE, d.get().id().toString(), 0L);
        List<SimCard> cartes = e.registre().cardsOf(op.id());
        helper.assertTrue(cartes.size() == 1 && r.uuid().equals(cartes.get(0).holder())
                && cartes.get(0).state() == SimCard.State.ACTIVE, "une carte au nom du demandeur : " + cartes);
        helper.assertTrue(caseDe(f.joueur(), cartes.get(0).simId()) >= 0, "la carte est dans l'inventaire du fondateur");
        helper.assertTrue(e.registre().requestsOf(op.id()).isEmpty(), "la demande est retirée");
        helper.assertTrue(r.messagesRecus().contains(MSG + "sim_issued"), "le demandeur est prévenu");
        helper.assertTrue(apres != null && apres.requests().isEmpty() && apres.cards().size() == 1
                && apres.cardTotal() == 1, "la vue suit : " + apres);
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void refuserUneDemande(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_refuser");
        JoueurDeTest r = e.joueur("pc_refuser_r");
        PhoneOperator op = e.fonder(f, A, "Cartes Refuser", RESERVE);
        Optional<SimRequest> d = e.registre().request(op.id(), r.uuid(), "pc_refuser_r", EconomyClock.now());
        helper.assertTrue(d.isPresent(), "demande inscrite");
        OperatorView vue = e.ouvrir(f, A);
        r.messagesRecus();
        e.geste(f, vue.session(), OperatorActionPacket.Action.REFUSE, d.get().id().toString(), 0L);
        helper.assertTrue(e.registre().requestsOf(op.id()).isEmpty() && e.registre().cardsOf(op.id()).isEmpty()
                && cartesPortees(f.joueur()) == 0, "demande retirée, aucune carte");
        helper.assertTrue(r.messagesRecus().contains(MSG + "sim_refused"), "le demandeur est prévenu");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void emettreUneSimSansNom(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_sansnom");
        PhoneOperator op = e.fonder(f, A, "Cartes Sans Nom", RESERVE);
        OperatorView vue = e.ouvrir(f, A);
        e.geste(f, vue.session(), OperatorActionPacket.Action.ISSUE_ANONYMOUS, "", 0L);
        List<SimCard> cartes = e.registre().cardsOf(op.id());
        helper.assertTrue(cartes.size() == 1 && cartes.get(0).holder() == null && cartes.get(0).holderName().isEmpty(),
                "une carte sans nom : " + cartes);
        helper.assertTrue(caseDe(f.joueur(), cartes.get(0).simId()) >= 0, "dans l'inventaire du fondateur");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void suspendrePuisReactiver(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_suspendre");
        PhoneOperator op = e.fonder(f, A, "Cartes Suspendre", RESERVE);
        SimCard c = e.carte(op, f);
        SimCard morte = e.carte(op, null);
        e.registre().setState(morte.simId(), SimCard.State.DEACTIVATED);
        OperatorView vue = e.ouvrir(f, A);
        e.geste(f, vue.session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "suspendue");
        OperatorView apres = e.geste(f, vue.session(), OperatorActionPacket.Action.REACTIVATE, c.simId().toString(), 0L);
        helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "réactivée");
        helper.assertTrue(apres != null && apres.cards().stream().anyMatch(l -> l.simId().equals(c.simId())
                && l.state() == SimCard.State.ACTIVE), "la vue suit : " + apres);
        e.geste(f, vue.session(), OperatorActionPacket.Action.REACTIVATE, morte.simId().toString(), 0L);
        e.geste(f, vue.session(), OperatorActionPacket.Action.SUSPEND, morte.simId().toString(), 0L);
        helper.assertTrue(e.carte(morte).state() == SimCard.State.DEACTIVATED, "une carte désactivée ne revient pas");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void unPaquetDUnNonFondateurNEmetRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_nonf");
        JoueurDeTest maire = e.maire("pc_nonf_m");
        JoueurDeTest inge = e.ingenieur("pc_nonf_i");
        JoueurDeTest eq = e.equipe("pc_nonf_e");
        JoueurDeTest client = e.joueur("pc_nonf_c");
        PhoneOperator op = e.fonder(f, A, "Cartes Non Fondateur", RESERVE);
        SimCard active = e.carte(op, client);
        SimCard suspendue = e.carte(op, client);
        e.registre().setState(suspendue.simId(), SimCard.State.SUSPENDED);
        Optional<SimRequest> d = e.registre().request(op.id(), client.uuid(), "pc_nonf_c", EconomyClock.now());
        helper.assertTrue(d.isPresent(), "demande inscrite");
        UUID jeton = e.carte(active).token();
        int sessionFondateur = e.ouvrir(f, A).session();
        OperatorPanels.close(f.uuid());
        for (JoueurDeTest j : List.of(maire, inge, eq, client)) {
            String qui = j == maire ? "le maire" : j == inge ? "l'ingénieur outillé" : j == eq ? "l'équipe" : "le client";
            int session;
            if (j == client) {
                // Le client n'a pas la main : aucun panneau ; il forge le numéro d'un autre.
                j.panneauxOperateurRecus();
                ServerPlayer p = j.joueur();
                BlockPos abs = helper.absolutePos(A);
                p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND),
                        InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
                helper.assertTrue(j.panneauxOperateurRecus().isEmpty(), "le client n'a pas de panneau");
                session = sessionFondateur;
            } else {
                OperatorView vue = e.ouvrir(j, A);
                helper.assertTrue(!vue.rights().cards(), qui + " ne gère pas les cartes : " + vue.rights());
                session = vue.session();
            }
            e.geste(j, session, OperatorActionPacket.Action.ISSUE, d.get().id().toString(), 0L);
            e.geste(j, session, OperatorActionPacket.Action.ISSUE_ANONYMOUS, "", 0L);
            e.geste(j, session, OperatorActionPacket.Action.SUSPEND, active.simId().toString(), 0L);
            e.geste(j, session, OperatorActionPacket.Action.REACTIVATE, suspendue.simId().toString(), 0L);
            e.geste(j, session, OperatorActionPacket.Action.REPLACE, active.simId().toString(), 0L);
            helper.assertTrue(e.registre().cardsOf(op.id()).size() == 2, qui + " : aucune carte émise");
            helper.assertTrue(e.carte(active).state() == SimCard.State.ACTIVE
                    && e.carte(suspendue).state() == SimCard.State.SUSPENDED, qui + " : aucun état changé");
            helper.assertTrue(e.carte(active).token().equals(jeton), qui + " : aucune carte remplacée");
            helper.assertTrue(e.registre().requestsOf(op.id()).size() == 1, qui + " : la demande attend toujours");
            helper.assertTrue(cartesPortees(j.joueur()) == 0, qui + " : aucune carte dans son inventaire");
        }
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void uneCarteDUnAutreOperateurNeBougePas(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest fm = e.joueur("pc_autre_m");
        JoueurDeTest fl = e.joueur("pc_autre_l");
        JoueurDeTest r = e.joueur("pc_autre_r");
        PhoneOperator mairie = e.fonder(fm, A, "Autre Mairie", RESERVE);
        PhoneOperator lagon = e.fonder(fl, B, "Autre Lagon", RESERVE);
        SimCard deLagon = e.carte(lagon, r);
        UUID jeton = deLagon.token();
        Optional<SimRequest> d = e.registre().request(lagon.id(), r.uuid(), "pc_autre_r", EconomyClock.now());
        helper.assertTrue(d.isPresent(), "demande à Lagon");
        UUID antenneLagon = e.op(lagon).mainAntenna();
        OperatorView vue = e.ouvrir(fm, A);
        helper.assertTrue(vue.rights().cards(), "témoin : le fondateur de Mairie gère ses cartes");
        e.geste(fm, vue.session(), OperatorActionPacket.Action.SUSPEND, deLagon.simId().toString(), 0L);
        e.geste(fm, vue.session(), OperatorActionPacket.Action.REPLACE, deLagon.simId().toString(), 0L);
        e.geste(fm, vue.session(), OperatorActionPacket.Action.ISSUE, d.get().id().toString(), 0L);
        e.geste(fm, vue.session(), OperatorActionPacket.Action.REFUSE, d.get().id().toString(), 0L);
        e.geste(fm, vue.session(), OperatorActionPacket.Action.SET_MAIN, String.valueOf(antenneLagon), 0L);
        helper.assertTrue(e.carte(deLagon).state() == SimCard.State.ACTIVE && e.carte(deLagon).token().equals(jeton),
                "la carte de Lagon n'a pas bougé");
        helper.assertTrue(e.registre().requestsOf(lagon.id()).size() == 1 && e.registre().cardsOf(mairie.id()).isEmpty()
                && cartesPortees(fm.joueur()) == 0, "la demande à Lagon attend, rien d'émis");
        helper.assertTrue(antenneLagon != null && antenneLagon.equals(e.op(lagon).mainAntenna())
                && !antenneLagon.equals(e.op(mairie).mainAntenna()), "les antennes principales n'ont pas bougé");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES, timeoutTicks = 300)
    public static void uneSessionDuTelephonePerimeeNeFaitRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_perime");
        PhoneOperator op = e.fonder(f, A, "Cartes Perime", RESERVE);
        e.ligne(op, f);
        SimCard c = e.carte(op, null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(vues.size() == 1, "panneau du téléphone ouvert : " + vues);
                    int ancien = vues.get(0).session();
                    // Le téléphone rouvert : nouvelle session, l'ancien panneau ne vaut plus.
                    PhoneService.close(f.joueur(), CloseReason.CLOSED);
                    helper.assertTrue(PhoneService.open(f.joueur(), InteractionHand.MAIN_HAND) == Outcome.OPENED,
                            "réouverture");
                    e.telephoneGeste(f, Action.UNLOCK, CODE, false);
                    e.geste(f, ancien, OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "session périmée : rien");
                    helper.assertTrue(OperatorPanels.session(f.uuid()).isEmpty(), "le panneau périmé est fermé");
                    // Ouvert de nouveau, puis le téléphone verrouillé : rien.
                    List<OperatorView> encore = e.appli(f, false);
                    helper.assertTrue(encore.size() == 1, "panneau rouvert");
                    e.telephoneGeste(f, Action.LOCK, "", false);
                    e.geste(f, encore.get(0).session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "téléphone verrouillé : rien");
                    // Témoin : déverrouillé, appli rouverte, le geste passe.
                    e.telephoneGeste(f, Action.UNLOCK, CODE, false);
                    List<OperatorView> temoin = e.appli(f, false);
                    helper.assertTrue(temoin.size() == 1, "panneau du témoin");
                    e.geste(f, temoin.get(0).session(), OperatorActionPacket.Action.SUSPEND, c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "témoin : suspendue");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CARTES)
    public static void lInventairePleinDuFondateurNEmetRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pc_plein");
        JoueurDeTest r = e.joueur("pc_plein_r");
        PhoneOperator op = e.fonder(f, A, "Cartes Plein", RESERVE);
        SimCard c = e.carte(op, r);
        UUID jeton = c.token();
        Optional<SimRequest> d = e.registre().request(op.id(), r.uuid(), "pc_plein_r", EconomyClock.now());
        helper.assertTrue(d.isPresent(), "demande inscrite");
        OperatorView vue = e.ouvrir(f, A);
        Inventory inv = f.joueur().getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            inv.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        e.geste(f, vue.session(), OperatorActionPacket.Action.ISSUE, d.get().id().toString(), 0L);
        e.geste(f, vue.session(), OperatorActionPacket.Action.ISSUE_ANONYMOUS, "", 0L);
        e.geste(f, vue.session(), OperatorActionPacket.Action.REPLACE, c.simId().toString(), 0L);
        helper.assertTrue(e.registre().cardsOf(op.id()).size() == 1, "aucune carte émise");
        helper.assertTrue(e.registre().requestsOf(op.id()).size() == 1, "la demande attend toujours");
        helper.assertTrue(e.carte(c).token().equals(jeton), "la carte n'est pas remplacée (aucun jeton tiré)");
        helper.assertTrue(f.messagesRecus().contains(MSG + "inventory_full"), "on lui dit que l'inventaire est plein");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, e.boite()).isEmpty(),
                "rien au sol");
        e.fermer();
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot panneau_vue
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_VUE)
    public static void laVueNeContientNiJetonNiCarteDUnAutreOperateur(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest fm = e.joueur("pv_jeton_m");
        JoueurDeTest fl = e.joueur("pv_jeton_l");
        JoueurDeTest r = e.joueur("pv_jeton_r");
        PhoneOperator mairie = e.fonder(fm, A, "Jeton Mairie", RESERVE);
        PhoneOperator lagon = e.fonder(fl, B, "Jeton Lagon", RESERVE);
        // Une carte de Mairie logée dans un téléphone (jeton tourné, téléphone noté).
        ItemStack phone = e.ligne(mairie, fm);
        e.carte(mairie, r);
        SimCard l1 = e.carte(lagon, r);
        SimCard l2 = e.carte(lagon, null);
        Optional<SimRequest> versLagon = e.registre().request(lagon.id(), r.uuid(), "pv_jeton_r", EconomyClock.now());
        Optional<SimRequest> versMairie = e.registre().request(mairie.id(), r.uuid(), "pv_jeton_r", EconomyClock.now());
        helper.assertTrue(versLagon.isPresent() && versMairie.isPresent(), "deux demandes");
        fm.panneauxOperateurRecus();
        ServerPlayer p = fm.joueur();
        BlockPos abs = helper.absolutePos(A);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        List<byte[]> octets = fm.octetsPanneauxOperateurRecus();
        helper.assertTrue(octets.size() == 1, "une vue envoyée, vu " + octets.size());
        OperatorView vue = OperatorView.decode(new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.wrappedBuffer(octets.get(0))));
        Set<UUID> deMairie = new HashSet<>();
        e.registre().cardsOf(mairie.id()).forEach(c -> deMairie.add(c.simId()));
        helper.assertTrue(vue.cards().size() == 2 && vue.cards().stream().allMatch(l -> deMairie.contains(l.simId())),
                "seulement les cartes de Mairie : " + vue.cards());
        helper.assertTrue(vue.requests().size() == 1 && vue.requests().get(0).id().equals(versMairie.get().id()),
                "seulement la demande à Mairie : " + vue.requests());
        for (SimCard c : List.of(l1, l2)) {
            helper.assertTrue(!porte(octets.get(0), c.simId()), "une carte de Lagon dans les octets");
        }
        for (SimCard c : e.registre().cardsOf(mairie.id())) {
            helper.assertTrue(!porte(octets.get(0), c.token()), "un jeton de carte dans les octets");
            if (c.phoneId() != null) {
                helper.assertTrue(!porte(octets.get(0), c.phoneId()), "un téléphone dans les octets");
            }
        }
        for (SimCard c : e.registre().cardsOf(lagon.id())) {
            helper.assertTrue(!porte(octets.get(0), c.token()), "un jeton de Lagon dans les octets");
        }
        helper.assertTrue(e.registre().cardsOf(mairie.id()).stream().anyMatch(c -> c.phoneId() != null),
                "témoin : une carte de Mairie est logée dans un téléphone");
        helper.assertTrue(phone.is(PhoneRegistry.PHONE.get()), "téléphone");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VUE, timeoutTicks = 300)
    public static void laVueDuTelephoneNOffreNiCaisseNiNom(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_tel");
        PhoneOperator op = e.fonder(f, A, "Vue Telephone", RESERVE);
        e.registre().deposit(op.id(), Coin.SILVER, 3);
        e.ligne(op, f);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    List<OperatorView> vues = e.appli(f, true);
                    helper.assertTrue(vues.size() == 1, "panneau du téléphone : " + vues);
                    OperatorView v = vues.get(0);
                    helper.assertTrue(v.source() == OperatorView.Source.PHONE && !v.rights().till() && !v.rights().name()
                            && v.rights().cards() && v.rights().tariff() && v.pos().equals(BlockPos.ZERO),
                            "depuis le téléphone : ni caisse ni nom, ni position : " + v.rights() + " " + v.pos());
                    e.geste(f, v.session(), OperatorActionPacket.Action.OPEN_TILL, "", 0L);
                    e.geste(f, v.session(), OperatorActionPacket.Action.NAME, "Renomme", 0L);
                    helper.assertTrue(!(f.joueur().containerMenu instanceof OperatorTillMenu)
                            && e.op(op).name().equals("Vue Telephone")
                            && e.registre().till(op.id()).getOrDefault(Coin.SILVER, 0L) == 3L,
                            "ni caisse ouverte ni nom changé");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VUE)
    public static void unMaireVoitSansGererLesCartes(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_maire_f");
        JoueurDeTest maire = e.maire("pv_maire");
        PhoneOperator op = e.fonder(f, A, "Vue Maire", RESERVE);
        e.carte(op, f);
        OperatorView vue = e.ouvrir(maire, A);
        helper.assertTrue(!vue.rights().cards() && vue.rights().tariff() && !vue.rights().till(),
                "le maire : le tarif, ni les cartes ni la caisse : " + vue.rights());
        helper.assertTrue(vue.cards().size() == 1, "il voit les cartes : " + vue.cards());
        e.geste(maire, vue.session(), OperatorActionPacket.Action.ISSUE_ANONYMOUS, "", 0L);
        helper.assertTrue(e.registre().cardsOf(op.id()).size() == 1, "aucune carte émise par le maire");
        long tarif = Math.min(5L, e.registre().nextTariffCap().orElse(e.registre().tariffCap()));
        OperatorView apres = e.geste(maire, vue.session(), OperatorActionPacket.Action.SET_TARIFF, "", tarif);
        helper.assertTrue(Long.valueOf(tarif).equals(e.op(op).nextTariff()), "le maire règle le tarif de demain");
        helper.assertTrue(apres != null && Long.valueOf(tarif).equals(apres.nextTariff()), "la vue suit : " + apres);
        e.fermer();
        helper.succeed();
    }

    /** Une vue sans ce qui dépend de l'écran qui l'a ouverte (source, position, numéro, caisse). */
    private static OperatorView sansSource(OperatorView v) {
        return new OperatorView(OperatorView.Source.BLOCK, BlockPos.ZERO, 0, v.operatorId(), v.name(),
                new OperatorView.Rights(v.rights().cards(), v.rights().tariff(), false, v.rights().name()), v.tariff(),
                v.nextTariff(), v.cap(), v.till(), v.antennas(), v.cardPage(), v.cardPages(), v.cardTotal(), v.cards(),
                v.requests(), v.relay());
    }

    @GameTest(template = "test/plancher", batch = LOT_VUE, timeoutTicks = 300)
    public static void leMemeGesteParLeBlocEtParLeTelephone(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_meme");
        PhoneOperator op = e.fonder(f, A, "Vue Meme Geste", RESERVE);
        e.ligne(op, f);
        SimCard c = e.carte(op, null);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(e.enService(A), "antenne en service"))
                .thenExecute(() -> {
                    // Le téléphone déverrouillé d'abord (dans la main), puis le panneau du bloc.
                    PhoneService.close(f.joueur(), CloseReason.CLOSED);
                    helper.assertTrue(PhoneService.open(f.joueur(), InteractionHand.MAIN_HAND) == Outcome.OPENED,
                            "ouverture du téléphone");
                    e.telephoneGeste(f, Action.SETUP, CODE, false);
                    OperatorView bloc = e.ouvrir(f, A);
                    OperatorView parLeBloc = e.geste(f, bloc.session(), OperatorActionPacket.Action.SUSPEND,
                            c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "par le bloc : suspendue");
                    e.geste(f, bloc.session(), OperatorActionPacket.Action.REACTIVATE, c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.ACTIVE, "réactivée entre les deux");
                    List<OperatorView> tel = e.appli(f, false);
                    helper.assertTrue(tel.size() == 1, "panneau du téléphone : " + tel);
                    OperatorView parLeTelephone = e.geste(f, tel.get(0).session(), OperatorActionPacket.Action.SUSPEND,
                            c.simId().toString(), 0L);
                    helper.assertTrue(e.carte(c).state() == SimCard.State.SUSPENDED, "par le téléphone : suspendue");
                    helper.assertTrue(parLeBloc != null && parLeTelephone != null, "une vue après chaque geste");
                    helper.assertTrue(sansSource(parLeBloc).equals(sansSource(parLeTelephone)),
                            "la même vue rendue :\n" + sansSource(parLeBloc) + "\n" + sansSource(parLeTelephone));
                    helper.assertTrue(parLeBloc.source() == OperatorView.Source.BLOCK
                            && parLeTelephone.source() == OperatorView.Source.PHONE, "deux sources");
                    e.fermer();
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_VUE)
    public static void remplacerLaCarte(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pv_remplacer");
        JoueurDeTest h = e.joueur("pv_remplacer_h");
        PhoneOperator op = e.fonder(f, A, "Vue Remplacer", RESERVE);
        ItemStack ancienTelephone = e.ligne(op, h);
        Optional<SimCard> logee = PhoneSims.line(e.serveur(), ancienTelephone);
        helper.assertTrue(logee.isPresent() && PhoneSims.check(e.serveur(), ancienTelephone) == PhoneSims.Validity.ACTIVE,
                "témoin : la ligne de l'abonné est active");
        SimCard c = logee.get();
        OperatorView vue = e.ouvrir(f, A);
        e.geste(f, vue.session(), OperatorActionPacket.Action.REPLACE, c.simId().toString(), 0L);
        int ou = caseDe(f.joueur(), c.simId());
        helper.assertTrue(ou >= 0, "la nouvelle pile est dans l'inventaire du fondateur");
        SimCard neuve = e.carte(c);
        helper.assertTrue(neuve.number().equals(c.number()) && !neuve.token().equals(c.token())
                && neuve.phoneId() == null && neuve.state() == SimCard.State.ACTIVE,
                "même numéro, jeton neuf, aucun téléphone noté : " + neuve);
        helper.assertTrue(PhoneSims.check(e.serveur(), ancienTelephone) == PhoneSims.Validity.UNKNOWN,
                "l'ancienne pile est morte");
        // La nouvelle pile, glissée dans le téléphone de l'abonné, rend la ligne.
        ItemStack pile = f.joueur().getInventory().getItem(ou).copy();
        f.joueur().getInventory().setItem(ou, ItemStack.EMPTY);
        h.joueur().getInventory().setItem(10, pile);
        helper.assertTrue(PhoneSims.insert(h.joueur(), ancienTelephone, 10) == PhoneSims.Insert.DONE,
                "la nouvelle carte s'insère");
        helper.assertTrue(PhoneSims.check(e.serveur(), ancienTelephone) == PhoneSims.Validity.ACTIVE
                && PhoneSims.line(e.serveur(), ancienTelephone).map(SimCard::number).orElse("").equals(c.number()),
                "la ligne revient sous le même numéro");
        e.fermer();
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot panneau_tarif
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_TARIF)
    public static void unNouveauTarifNeVautQuAuLendemain(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pt_lendemain");
        JoueurDeTest present = e.joueur("pt_lendemain_p");
        JoueurDeTest absent = e.joueur("pt_lendemain_a");
        PhoneOperator op = e.fonder(f, A, "Tarif Lendemain", RESERVE);
        e.carte(op, present);
        e.carte(op, absent);
        String nomAbsent = absent.joueur().getGameProfile().getName();
        absent.quitter();
        long tarif = Math.min(15L, e.registre().nextTariffCap().orElse(e.registre().tariffCap()));
        helper.assertTrue(tarif > 0L, "un plafond nul : rien à essayer");
        OperatorView vue = e.ouvrir(f, A);
        helper.assertTrue(vue.rights().tariff() && vue.tariff() == 0L && vue.nextTariff() == null, "départ : " + vue);
        OperatorView apres = e.geste(f, vue.session(), OperatorActionPacket.Action.SET_TARIFF, "", tarif);
        helper.assertTrue(e.op(op).tariff() == 0L && Long.valueOf(tarif).equals(e.op(op).nextTariff()),
                "à 14 h : le tarif du jour ne change pas, celui de demain est noté");
        helper.assertTrue(apres != null && apres.tariff() == 0L && Long.valueOf(tarif).equals(apres.nextTariff()),
                "la vue le montre : " + apres);
        present.messagesRecus();
        LocalDate aujourdhui = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(),
                EconomyConfig.payHour());
        OperatorDay.SERVER.issue(e.serveur(), aujourdhui, minuit(aujourdhui) + MINUTE);
        helper.assertTrue(e.op(op).tariff() == tarif && e.op(op).nextTariff() == null,
                "à la clôture de minuit, le tarif entre en vigueur : " + e.op(op));
        helper.assertTrue(present.messagesRecus().contains(MSG + "tariff_changed"), "le titulaire présent est prévenu");
        JoueurDeTest revenu = JoueurDeTest.rejoindre(helper.getLevel(), nomAbsent, helper.absoluteVec(JOUEUR));
        e.joueurs.add(revenu);
        helper.assertTrue(revenu.messagesALaConnexion().contains(MSG + "tariff_changed"),
                "l'absent est prévenu à sa connexion : " + revenu.messagesALaConnexion());
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TARIF)
    public static void unTarifAuDessusDuPlafondEstRefuse(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pt_plafond");
        PhoneOperator op = e.fonder(f, A, "Tarif Plafond", RESERVE);
        long plafond = Math.min(PhoneConfig.phoneTariffMax(),
                e.registre().nextTariffCap().orElse(e.registre().tariffCap()));
        OperatorView vue = e.ouvrir(f, A);
        helper.assertTrue(vue.cap() == e.registre().nextTariffCap().orElse(e.registre().tariffCap()),
                "la vue montre le plafond de demain : " + vue.cap());
        e.geste(f, vue.session(), OperatorActionPacket.Action.SET_TARIFF, "", plafond + 1);
        helper.assertTrue(e.op(op).nextTariff() == null, "au-dessus du plafond : refusé");
        helper.assertTrue(f.messagesRecus().contains(MSG + "tariff_above_cap"), "on lui dit le plafond");
        e.geste(f, vue.session(), OperatorActionPacket.Action.SET_TARIFF, "", -1L);
        helper.assertTrue(e.op(op).nextTariff() == null, "négatif : refusé");
        e.geste(f, vue.session(), OperatorActionPacket.Action.SET_TARIFF, "", plafond);
        helper.assertTrue(Long.valueOf(plafond).equals(e.op(op).nextTariff()), "témoin : le plafond lui-même passe");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TARIF)
    public static void unTarifAuDessusDuNouveauPlafondEstRameneAMinuit(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pt_ramene");
        // Un registre à part : le plafond du maire est global, celui du serveur n'est pas touché.
        PhoneOperators registre = new PhoneOperators();
        PhoneOperators.Result fonde = registre.found(f.uuid(), "pt_ramene", "Tarif Ramene", EconomyClock.now());
        helper.assertTrue(fonde.founded(), "fondation");
        String id = fonde.operator().id();
        helper.assertTrue(registre.setNextTariff(id, 18L), "18 cuivres sous le plafond d'aujourd'hui");
        registre.setNextTariffCap(10L);
        f.messagesRecus();
        LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(),
                EconomyConfig.payHour());
        new OperatorDay(registre).issue(e.serveur(), jour, minuit(jour) + MINUTE);
        Optional<PhoneOperator> apres = registre.byId(id);
        helper.assertTrue(apres.isPresent() && apres.get().tariff() == 10L && registre.tariffCap() == 10L,
                "ramené au nouveau plafond à minuit : " + apres);
        helper.assertTrue(f.messagesRecus().contains(MSG + "tariff_capped"), "le fondateur est prévenu");
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TARIF)
    public static void uneHorlogeQuiReculeNAppliqueRien(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pt_recule");
        PhoneOperators registre = new PhoneOperators();
        String id = registre.found(f.uuid(), "pt_recule", "Tarif Recule", EconomyClock.now()).operator().id();
        registre.setNextTariff(id, 7L);
        InvoiceRegistry factures = InvoiceRegistry.load(new CompoundTag());
        LocalDate j = LocalDate.of(2026, 6, 14);
        factures.setLastDebitDay(j.minusDays(1));
        // Le temps de l'essai, la clôture du téléphone et celle de l'électricité sur des registres à part.
        OperatorDay.install(new OperatorDay(registre));
        PowerBilling.install(new PowerBilling(new PowerSuppliers(), factures));
        try {
            int clos = DailyDebit.closeDueDays(e.serveur(), factures, new Payroll(), minuit(j) - 180L * MINUTE);
            helper.assertTrue(clos == 0 && registre.byId(id).get().tariff() == 0L, "avant minuit : rien");
            clos = DailyDebit.closeDueDays(e.serveur(), factures, new Payroll(), minuit(j.minusDays(3)));
            helper.assertTrue(clos == 0 && registre.byId(id).get().tariff() == 0L, "horloge qui recule : rien");
            clos = DailyDebit.closeDueDays(e.serveur(), factures, new Payroll(), minuit(j) + MINUTE);
            helper.assertTrue(clos == 1 && registre.byId(id).get().tariff() == 7L,
                    "témoin : minuit passé, le tarif entre en vigueur (" + clos + ")");
            registre.setNextTariff(id, 9L);
            clos = DailyDebit.closeDueDays(e.serveur(), factures, new Payroll(), minuit(j) - 60L * MINUTE);
            helper.assertTrue(clos == 0 && registre.byId(id).get().tariff() == 7L,
                    "l'horloge recule après la clôture : le jour clos ne se rouvre pas");
            clos = DailyDebit.closeDueDays(e.serveur(), factures, new Payroll(), minuit(j) + 2L * MINUTE);
            helper.assertTrue(clos == 0 && registre.byId(id).get().tariff() == 7L, "le même jour ne se clôt pas deux fois");
        } finally {
            OperatorDay.install(OperatorDay.SERVER);
            PowerBilling.install(PowerBilling.SERVER);
        }
        e.fermer();
        helper.succeed();
    }

    @GameTest(template = "test/plancher", batch = LOT_TARIF)
    public static void lesFeDuJourRepartentAZero(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest f = e.joueur("pt_fe");
        PhoneOperators registre = new PhoneOperators();
        String id = registre.found(f.uuid(), "pt_fe", "Tarif Fe", EconomyClock.now()).operator().id();
        UUID antenne = UUID.randomUUID();
        registre.putAntenna(new AntennaRecord(antenne, id, helper.getLevel().dimension(), helper.absolutePos(A),
                EconomyClock.now(), false, 0L, 0L, 0L, null));
        AntennaLoad.Breakdown detail = new AntennaLoad.Breakdown(40, 6, 0);
        long t = helper.getLevel().getGameTime();
        for (int i = 0; i < 3; i++) {
            registre.noteTick(antenne, true, t + i, 46, detail);
        }
        helper.assertTrue(registre.antenna(antenne).get().feToday() == 138L, "trois tics comptés");
        LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(),
                EconomyConfig.payHour());
        new OperatorDay(registre).issue(e.serveur(), jour, minuit(jour) + MINUTE);
        AntennaRecord apres = registre.antenna(antenne).get();
        helper.assertTrue(apres.feToday() == 0L && apres.feYesterday() == 138L,
                "à minuit, le jour passe à hier et repart de zéro : " + apres);
        registre.noteTick(antenne, true, t + 3, 46, detail);
        helper.assertTrue(registre.antenna(antenne).get().feToday() == 46L, "le nouveau jour compte de zéro");
        e.fermer();
        helper.succeed();
    }
}
