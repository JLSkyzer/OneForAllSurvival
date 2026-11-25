package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaChunks;
import dev.oneforallsurvival.phone.net.AntennaItem;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.NetworkStatus;
import dev.oneforallsurvival.phone.net.OperatorCommands;
import dev.oneforallsurvival.phone.net.OperatorDay;
import dev.oneforallsurvival.phone.net.OperatorNoticePacket;
import dev.oneforallsurvival.phone.net.OperatorPanels;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les gestes du maire et de l'équipe sur les opérateurs du téléphone (lot T2, tâche 11) : {@code /operator list, cap,
 * set … tariff, owner, remove} (avec confirmation), et la suppression complète d'un opérateur.
 *
 * <ul>
 *   <li>Lot {@code maire_telephone} : droits, tarif, propriétaire, suppression complète, caisse, antenne reposée. Les
 *       essais d'un seul appel nomment un maire (global) et le rendent avant qu'un autre ne tourne ; l'essai en séquence
 *       n'emploie que l'équipe (opérateurs du serveur, par joueur).</li>
 *   <li>Lot {@code maire_telephone_b} (horloge de l'économie fixée à 14 h par l'{@code @BeforeBatch}, rendue par
 *       l'{@code @AfterBatch}) : le plafond au lendemain, la confirmation et son expiration à 60 s.</li>
 * </ul>
 * Les commandes passent par le vrai répartiteur du serveur, avec la source d'un vrai joueur d'essai. Noms d'opérateur
 * propres à chaque passage (leçon du 2026-11-24) ; aucune exception autre qu'une assertion dans une étape de séquence
 * (leçon du 2026-11-20) ; ce qu'un essai rouge laisse est rangé par l'{@code @AfterBatch} de son lot.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MaireTelephoneGameTests {

    private static final String LOT = "maire_telephone";
    private static final String LOT_B = "maire_telephone_b";

    private static final String KEY = OperatorCommands.KEY;
    private static final String MSG = OperatorCommands.MESSAGE;

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(6, 0, 6);
    private static final BlockPos C = new BlockPos(5, 0, 1);
    private static final Vec3 JOUEUR = new Vec3(3.5, 0, 3.5);
    private static final int RESERVE = 40_000;
    private static final long MINUTE = 60_000L;

    /** Les essais ouverts du lot en cours : un essai rouge ne se range pas lui-même, l'{@code @AfterBatch} le fait. */
    private static final List<Essai> OUVERTS = new ArrayList<>();

    private MaireTelephoneGameTests() {}

    @AfterBatch(batch = LOT)
    public static void apres(ServerLevel level) {
        for (Essai e : new ArrayList<>(OUVERTS)) {
            e.close();
        }
        OUVERTS.clear();
    }

    /** L'horloge de l'économie fixée à 14 h aujourd'hui (moins d'un jour d'écart : la paie ne saute pas). */
    @BeforeBatch(batch = LOT_B)
    public static void avantB(ServerLevel level) {
        long quatorze = quatorzeHeures();
        EconomyClock.setClock(() -> quatorze);
    }

    @AfterBatch(batch = LOT_B)
    public static void apresB(ServerLevel level) {
        EconomyClock.reset();
        OperatorDay.install(OperatorDay.SERVER);
        apres(level);
    }

    private static long quatorzeHeures() {
        ZoneId zone = EconomyCalendar.configuredZone();
        return LocalDate.now(zone).atTime(14, 0).atZone(zone).toInstant().toEpochMilli();
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static String suffixe() {
        String s = Long.toString(System.nanoTime(), 36);
        return s.substring(Math.max(0, s.length() - 5));
    }

    /** Joueurs, rôles, équipe, maire, plafond, opérateurs, coffres-forts et antennes d'un essai : tout est rendu. */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final String sfx = suffixe();
        final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final List<UUID> uuids = new ArrayList<>();
        private final Optional<UUID> maireAvant;
        private final Optional<Long> plafondAvant;
        private boolean maireTouche;
        private boolean ferme;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            degager();
            this.maireAvant = livre().mayor();
            this.plafondAvant = registre().nextTariffCap();
            OUVERTS.add(this);
        }

        private void degager() {
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

        RoleBook livre() {
            return RoleSavedData.get(serveur()).book();
        }

        String nom(String court) {
            return court + " " + sfx;
        }

        JoueurDeTest joueur(String court) {
            String nom = court + "_" + sfx;
            Vec3 ici = helper.absoluteVec(JOUEUR);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, ici);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            uuids.add(j.uuid());
            EconomyLedger.get(serveur()).markStartingGiven(j.uuid());
            livre().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            OperateurDEssai.oublier(serveur(), j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe : " + nom);
            return j;
        }

        JoueurDeTest revenir(JoueurDeTest parti) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), parti.joueur().getGameProfile().getName(),
                    helper.absoluteVec(JOUEUR));
            joueurs.add(j);
            return j;
        }

        JoueurDeTest ingenieurOutille(String court) {
            JoueurDeTest j = joueur(court);
            livre().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            j.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RepairRegistry.MAINTENANCE_TOOL.get()));
            return j;
        }

        JoueurDeTest maire(String court) {
            JoueurDeTest j = joueur(court);
            livre().appointMayor(j.uuid());
            maireTouche = true;
            return j;
        }

        JoueurDeTest equipe(String court) {
            JoueurDeTest j = joueur(court);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            helper.assertTrue(j.joueur().hasPermissions(2), "le joueur de l'équipe doit être opérateur");
            return j;
        }

        int commande(JoueurDeTest j, String commande) {
            return serveur().getCommands().performPrefixedCommand(j.joueur().createCommandSourceStack(), commande);
        }

        int console(String commande) {
            return serveur().getCommands().performPrefixedCommand(serveur().createCommandSourceStack(), commande);
        }

        /** Un opérateur sans antenne, au nom de ce joueur. */
        PhoneOperator operateur(JoueurDeTest fondateur, String court) {
            String nom = nom(court);
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            PhoneOperators.Result r = registre().found(fondateur.uuid(), fondateur.joueur().getGameProfile().getName(),
                    nom, EconomyClock.now());
            helper.assertTrue(r.founded(), "fondation de " + nom + " : " + r.status());
            return r.operator();
        }

        /** Une antenne posée par {@code j} en {@code pos}, fondée sous {@code court}, réserve pleine. */
        PhoneOperator fonder(JoueurDeTest j, BlockPos pos, String court) {
            String nom = nom(court);
            registre().byName(nom).ifPresent(reste -> OperateurDEssai.oublier(serveur(), reste.founder()));
            InteractionResult r = poser(j, pos, new ItemStack(PhoneRegistry.ANTENNA_ITEM.get()));
            helper.assertTrue(r.consumesAction(), "l'antenne devait se poser en " + pos + " : " + r);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
            verser(antenne(pos), RESERVE);
            return fonde.get().operator();
        }

        InteractionResult poser(JoueurDeTest j, BlockPos pos, ItemStack pile) {
            ServerPlayer p = j.joueur();
            ItemStack avant = p.getItemInHand(InteractionHand.MAIN_HAND);
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

        PhoneOperator lire(PhoneOperator op) {
            Optional<PhoneOperator> o = registre().byId(op.id());
            helper.assertTrue(o.isPresent(), "opérateur disparu : " + op.name());
            return o.get();
        }

        /** La carte émise par le fondateur, passée au titulaire, glissée dans un téléphone neuf tenu en main. */
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
            return phone;
        }

        @Override
        public void close() {
            if (ferme) {
                return;
            }
            ferme = true;
            OUVERTS.remove(this);
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            if (maireTouche) {
                if (maireAvant.isPresent()) {
                    livre().appointMayor(maireAvant.get());
                } else {
                    livre().removeMayor();
                }
            }
            registre().resetNextTariffCap(plafondAvant.orElse(null));
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            // Les blocs d'abord (leur retrait efface l'inscription et rend le ticket), puis les opérateurs.
            degager();
            SafeAccounts coffres = SafeAccounts.get(serveur());
            for (UUID uuid : uuids) {
                livre().assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                OperateurDEssai.oublier(serveur(), uuid);
                EnumMap<Coin, Long> attente = HeldGoods.get(serveur()).pendingCoins(uuid);
                if (!attente.isEmpty()) {
                    HeldGoods.get(serveur()).takeCoins(uuid, attente);
                }
                coffres.balance(uuid).forEach((coin, n) -> coffres.withdraw(uuid, coin, n));
                coffres.forgetSafePos(uuid);
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

    private static String q(PhoneOperator op) {
        return "\"" + op.name() + "\"";
    }

    private static EconomyLedger.Entry derniereLigne(Essai e) {
        List<EconomyLedger.Entry> lignes = EconomyLedger.get(e.serveur()).entries(0);
        e.helper.assertTrue(!lignes.isEmpty(), "le journal est vide");
        return lignes.get(0);
    }

    private static boolean enService(Essai e, AntennaBlockEntity be) {
        Optional<AntennaRecord> r = be.antennaId() == null ? Optional.empty() : e.registre().antenna(be.antennaId());
        return r.isPresent() && AntennaBlockEntity.inService(r.get(), e.helper.getLevel().getGameTime());
    }

    /** Toutes les pièces de ce joueur, en cuivres : sur lui, dans son coffre-fort, en attente. */
    private static long avoir(Essai e, JoueurDeTest j) {
        long sur = j.connecte() ? CoinMath.valueOf(PlayerCoins.held(j.joueur())) : 0L;
        return sur + SafeAccounts.get(e.serveur()).total(j.uuid())
                + CoinMath.valueOf(HeldGoods.get(e.serveur()).pendingCoins(j.uuid()));
    }

    private static boolean fermeture(List<OperatorNoticePacket> avis) {
        return avis.stream().anyMatch(OperatorNoticePacket::closed);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot maire_telephone
    // ---------------------------------------------------------------------------------------------

    /**
     * Citoyen, ingénieur outillé, fondateur et faux joueur ne font aucun geste ({@code list, cap, set, owner, remove}) ;
     * le maire, l'équipe et la console, si.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void seulsLeMaireEtLEquipeFontCesGestes(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest citoyen = e.joueur("mt_c");
            JoueurDeTest ingenieur = e.ingenieurOutille("mt_i");
            JoueurDeTest fondateur = e.joueur("mt_f");
            JoueurDeTest repreneur = e.joueur("mt_r");
            JoueurDeTest maire = e.maire("mt_m");
            JoueurDeTest equipe = e.equipe("mt_e");
            PhoneOperator op = e.operateur(fondateur, "Droits");
            Optional<Long> plafond = e.registre().nextTariffCap();
            String repris = repreneur.joueur().getGameProfile().getName();
            helper.assertTrue(dev.oneforallsurvival.phone.net.AntennaRules.hasMaintenanceTool(ingenieur.joueur()),
                    "l'ingénieur tient l'outil");
            for (JoueurDeTest sansDroit : List.of(citoyen, ingenieur, fondateur)) {
                sansDroit.clesRecues();
                helper.assertTrue(e.commande(sansDroit, "operator list") == 0, "liste refusée");
                helper.assertTrue(e.commande(sansDroit, "operator cap 7") == 0, "plafond refusé");
                helper.assertTrue(e.commande(sansDroit, "operator set " + q(op) + " tariff 3") == 0, "tarif refusé");
                helper.assertTrue(e.commande(sansDroit, "operator owner " + q(op) + " " + repris) == 0, "owner refusé");
                helper.assertTrue(e.commande(sansDroit, "operator remove " + q(op) + " pour voir") == 0, "remove refusé");
                helper.assertTrue(e.commande(sansDroit, "operator remove " + q(op) + " confirm") == 0, "confirm refusé");
                List<String> cles = sansDroit.clesRecues();
                helper.assertTrue(cles.stream().filter((KEY + "not_mayor")::equals).count() == 6,
                        "chaque refus est clair : " + cles);
                PhoneOperator apres = e.lire(op);
                helper.assertTrue(e.registre().nextTariffCap().equals(plafond) && apres.nextTariff() == null
                        && apres.founder().equals(fondateur.uuid()), "rien n'a bougé : " + apres);
            }
            // Un faux joueur (automate) n'exécute rien, même au nom du maire.
            FakePlayer faux = FakePlayerFactory.get(helper.getLevel(), new GameProfile(maire.uuid(), "[Automate]"));
            helper.assertTrue(e.serveur().getCommands().performPrefixedCommand(faux.createCommandSourceStack(),
                    "operator cap 7") == 0 && e.registre().nextTariffCap().equals(plafond), "un faux joueur : refusé");

            helper.assertTrue(e.commande(maire, "operator list") >= 1, "le maire voit la liste");
            helper.assertTrue(e.commande(maire, "operator cap 7") == 1
                    && e.registre().nextTariffCap().equals(Optional.of(7L)), "le maire fixe le plafond");
            helper.assertTrue(e.commande(equipe, "operator cap 8") == 1
                    && e.registre().nextTariffCap().equals(Optional.of(8L)), "l'équipe fixe le plafond");
            helper.assertTrue(e.console("operator cap 6") == 1 && e.registre().nextTariffCap().equals(Optional.of(6L)),
                    "la console agit comme l'équipe");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_PHONE_CAP) && ligne.author() == null
                    && ligne.reason().startsWith("[") && ligne.amount() == 6L, "geste de la console au journal : " + ligne);
            helper.assertTrue(e.commande(equipe, "operator set " + q(op) + " tariff 2") == 1
                    && Long.valueOf(2L).equals(e.lire(op).nextTariff()), "l'équipe fixe un tarif");
            helper.succeed();
        }
    }

    /** {@code /operator set … tariff} : au lendemain, sous le plafond de demain ; refus clair au-dessus ; au journal. */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void leMaireFixeUnTarif(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mf_f");
            JoueurDeTest maire = e.maire("mf_m");
            PhoneOperator op = e.operateur(fondateur, "Tarif Maire");
            long max = OperatorCommands.nextCap(e.registre());
            long voulu = Math.min(max, 5L);
            fondateur.messagesRecus();
            maire.clesRecues();

            helper.assertTrue(e.commande(maire, "operator set " + q(op) + " tariff " + (max + 1)) == 0,
                    "au-dessus du plafond de demain : refusé");
            helper.assertTrue(maire.clesRecues().contains(KEY + "set_above_cap"), "le refus dit le plafond");
            helper.assertTrue(e.commande(maire, "operator set " + q(op) + " tariff -1") == 0, "négatif : refusé");
            helper.assertTrue(e.lire(op).nextTariff() == null, "rien n'est noté");
            helper.assertTrue(e.commande(maire, "operator set \"inconnu " + e.sfx + "\" tariff 1") == 0
                    && maire.clesRecues().contains(KEY + "unknown_operator"), "opérateur inconnu : dit");

            // Nom sans casse, entre guillemets (il a une espace).
            String nomEnMajuscules = "\"" + op.name().toUpperCase(java.util.Locale.ROOT) + "\"";
            helper.assertTrue(e.commande(maire, "operator set " + nomEnMajuscules + " tariff " + voulu) == 1,
                    "le maire fixe le tarif (nom sans casse)");
            PhoneOperator apres = e.lire(op);
            helper.assertTrue(Long.valueOf(voulu).equals(apres.nextTariff()) && apres.tariff() == 0L,
                    "au lendemain seulement, jamais le jour même : " + apres);
            helper.assertTrue(fondateur.messagesRecus().contains(MSG + "tariff_by_mayor"), "le fondateur est prévenu");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_PHONE_TARIFF) && maire.uuid().equals(ligne.author())
                    && ligne.amount() == voulu && ligne.reason().contains(op.name()), "au journal : " + ligne);
            helper.succeed();
        }
    }

    /**
     * {@code /operator owner} : le nouveau fondateur émet, l'ancien plus ; cartes et caisse suivent ; panneau de l'ancien
     * fermé ; les deux prévenus ; un joueur inconnu ou qui a déjà un opérateur est refusé.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void changerDeFondateur(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mo_f");
            JoueurDeTest repreneur = e.joueur("mo_r");
            JoueurDeTest occupe = e.joueur("mo_o");
            JoueurDeTest titulaire = e.joueur("mo_t");
            JoueurDeTest maire = e.maire("mo_m");
            PhoneOperator op = e.fonder(fondateur, A, "Change");
            e.operateur(occupe, "Occupe");
            ItemStack phone = e.ligne(op, titulaire);
            SimCard carte = PhoneSims.line(e.serveur(), phone).orElse(null);
            helper.assertTrue(carte != null, "le titulaire a une ligne");
            e.registre().deposit(op.id(), Coin.SILVER, 4L);
            OperatorPanels.openFromBlock(fondateur.joueur(), helper.absolutePos(A));
            helper.assertTrue(OperatorPanels.session(fondateur.uuid()).isPresent(), "panneau ouvert");
            fondateur.avisOperateurRecus();
            fondateur.messagesRecus();
            String nomRepreneur = repreneur.joueur().getGameProfile().getName();

            helper.assertTrue(e.commande(maire, "operator owner " + q(op) + " inconnu_" + e.sfx) == 0
                    && maire.clesRecues().contains(KEY + "owner_unknown_player"), "joueur inconnu : refusé");
            helper.assertTrue(e.commande(maire, "operator owner " + q(op) + " "
                    + occupe.joueur().getGameProfile().getName()) == 0
                    && maire.clesRecues().contains(KEY + "owner_has_operator"), "un joueur qui a déjà un opérateur");
            helper.assertTrue(e.commande(maire, "operator owner " + q(op) + " "
                    + fondateur.joueur().getGameProfile().getName()) == 0, "le même fondateur : refusé");
            helper.assertTrue(e.lire(op).founder().equals(fondateur.uuid()), "rien n'a bougé");

            helper.assertTrue(e.commande(maire, "operator owner " + q(op) + " " + nomRepreneur) == 1, "changement");
            PhoneOperator repris = e.lire(op);
            helper.assertTrue(repris.founder().equals(repreneur.uuid()) && repris.founderName().equals(nomRepreneur),
                    "nouveau fondateur : " + repris);
            helper.assertTrue(e.registre().byFounder(fondateur.uuid()).isEmpty(), "l'ancien n'a plus d'opérateur");
            helper.assertTrue(e.registre().till(op.id()).getOrDefault(Coin.SILVER, 0L) == 4L, "la caisse suit");
            helper.assertTrue(e.registre().card(carte.simId()).map(SimCard::state).orElse(null) == SimCard.State.ACTIVE
                    && e.registre().card(carte.simId()).get().operatorId().equals(op.id()), "les cartes suivent");
            helper.assertTrue(OperatorPanels.session(fondateur.uuid()).isEmpty()
                    && fermeture(fondateur.avisOperateurRecus()), "le panneau de l'ancien fondateur est fermé");
            helper.assertTrue(PhoneSims.issueTo(fondateur.joueur(), repris, null, "", EconomyClock.now()).isEmpty(),
                    "l'ancien fondateur n'émet plus");
            helper.assertTrue(PhoneSims.issueTo(repreneur.joueur(), repris, null, "", EconomyClock.now()).isPresent(),
                    "le nouveau émet");
            helper.assertTrue(fondateur.messagesRecus().contains(MSG + "owner_lost"), "l'ancien est prévenu");
            helper.assertTrue(repreneur.messagesRecus().contains(MSG + "owner_new"), "le nouveau est prévenu");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_PHONE_OWNER) && maire.uuid().equals(ligne.author())
                    && ligne.reason().contains(nomRepreneur), "au journal : " + ligne);
            helper.succeed();
        }
    }

    /**
     * Supprimé par l'équipe : cartes désactivées, barres 0, appel vers un de ses numéros « opérateur supprimé », plus
     * aucune ligne ; antennes retirées du registre, arrêtées sans consommation, ticket rendu ; demandes retirées ;
     * panneau fermé ; fondateur, titulaire et demandeur (absent : à sa connexion) prévenus.
     */
    @GameTest(template = "test/plancher", batch = LOT, timeoutTicks = 400)
    public static void unOperateurSupprimeNeLaisseRienDActif(GameTestHelper helper) {
        Essai e = new Essai(helper);
        JoueurDeTest equipe = e.equipe("ms_e");
        JoueurDeTest fondateur = e.joueur("ms_f");
        JoueurDeTest titulaire = e.joueur("ms_h");
        JoueurDeTest demandeur = e.joueur("ms_r");
        JoueurDeTest autreFondateur = e.joueur("ms_g");
        JoueurDeTest appelant = e.joueur("ms_c");
        PhoneOperator op = e.fonder(fondateur, A, "Supprime");
        PhoneOperator reste = e.fonder(autreFondateur, B, "Reste");
        ItemStack phoneTitulaire = e.ligne(op, titulaire);
        ItemStack phoneAppelant = e.ligne(reste, appelant);
        helper.assertTrue(e.registre().request(op.id(), demandeur.uuid(), "ms_r", EconomyClock.now()).isPresent(),
                "demande de carte");
        ServerLevel level = helper.getLevel();
        BlockPos absA = helper.absolutePos(A);
        MinecraftServer srv = e.serveur();
        long[] energie = new long[1];
        String[] numero = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(enService(e, e.antenne(A)) && enService(e, e.antenne(B)),
                        "antennes en service"))
                .thenExecute(() -> {
                    BlockPos ici = helper.absolutePos(A);
                    titulaire.joueur().teleportTo(ici.getX() + 1.5, ici.getY(), ici.getZ() + 0.5);
                    BlockPos la = helper.absolutePos(B);
                    appelant.joueur().teleportTo(la.getX() + 0.5, la.getY(), la.getZ() - 0.5);
                    Optional<SimCard> ligne = PhoneSims.line(srv, phoneTitulaire);
                    helper.assertTrue(ligne.isPresent(), "le titulaire a une ligne");
                    numero[0] = ligne.get().number();
                    helper.assertTrue(PhoneNetwork.status(titulaire.joueur(), phoneTitulaire, false).bars() > 0,
                            "avant : des barres");
                    helper.assertTrue(!PhoneConfig.antennaKeepsChunkLoaded() || AntennaChunks.isHeld(level, absA),
                            "avant : ticket tenu");
                    OperatorPanels.openFromBlock(fondateur.joueur(), absA);
                    helper.assertTrue(OperatorPanels.session(fondateur.uuid()).isPresent(), "panneau ouvert");
                    fondateur.avisOperateurRecus();
                    fondateur.messagesRecus();
                    titulaire.messagesRecus();
                    demandeur.quitter();

                    helper.assertTrue(e.commande(equipe, "operator remove " + q(op) + " antennes pirates") == 1,
                            "demande");
                    helper.assertTrue(e.registre().byId(op.id()).isPresent(), "rien ne bouge avant la confirmation");
                    helper.assertTrue(e.commande(equipe, "operator remove " + q(op) + " confirm") == 1, "confirmation");

                    helper.assertTrue(e.registre().byId(op.id()).isEmpty(), "l'opérateur n'existe plus");
                    helper.assertTrue(e.registre().cardsOf(op.id()).stream()
                            .allMatch(c -> c.state() == SimCard.State.DEACTIVATED)
                            && !e.registre().cardsOf(op.id()).isEmpty(), "toutes ses cartes sont désactivées");
                    NetworkStatus s = PhoneNetwork.status(titulaire.joueur(), phoneTitulaire, false);
                    helper.assertTrue(s.bars() == 0, "barres 0 : " + s);
                    helper.assertTrue(PhoneNetwork.lineOf(srv, numero[0]).isEmpty(), "plus aucune ligne active");
                    Either<RelayPlanner.Route, RelayFailure> appel = PhoneNetwork.route(srv, appelant.joueur(),
                            phoneAppelant, false, numero[0], GlobalPos.of(level.dimension(),
                                    titulaire.joueur().blockPosition()));
                    helper.assertTrue(appel.right().map(RelayFailure::reason).orElse(null)
                            == RelayFailure.Reason.CALLEE_OPERATOR_REMOVED, "appel : " + appel.right());
                    helper.assertTrue(e.registre().antennasOf(op.id()).isEmpty()
                            && e.registre().antenna(e.antenne(A).antennaId()).isEmpty(), "antennes retirées");
                    helper.assertTrue(!AntennaChunks.isHeld(level, absA), "ticket du chunk rendu");
                    helper.assertTrue(PhoneNetwork.emergencyAntenna(srv, GlobalPos.of(level.dimension(), absA))
                            .map(RelayPlanner.AntennaPoint::operatorId).filter(op.id()::equals).isEmpty(),
                            "l'antenne ne sert plus même aux urgences");
                    helper.assertTrue(e.registre().requestsOf(op.id()).isEmpty(), "demandes retirées");
                    helper.assertTrue(OperatorPanels.session(fondateur.uuid()).isEmpty()
                            && fermeture(fondateur.avisOperateurRecus()), "panneau fermé");
                    helper.assertTrue(fondateur.messagesRecus().contains(MSG + "removed_founder"), "fondateur prévenu");
                    helper.assertTrue(titulaire.messagesRecus().contains(MSG + "removed_holder"), "titulaire prévenu");
                    helper.assertTrue(e.registre().byId(reste.id()).isPresent(), "l'autre opérateur reste");
                    energie[0] = e.antenne(A).energy().getEnergyStored();
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(A).getValue(ElectricMachineBlock.STATE) == MachineState.STOPPED,
                            "le bloc reste, arrêté : " + helper.getBlockState(A));
                    helper.assertTrue(e.antenne(A).energy().getEnergyStored() == energie[0],
                            "plus aucune consommation : " + energie[0] + " puis " + e.antenne(A).energy().getEnergyStored());
                    helper.assertTrue(!AntennaChunks.isHeld(level, absA), "ticket toujours rendu");
                    JoueurDeTest revenu = e.revenir(demandeur);
                    helper.assertTrue(revenu.messagesALaConnexion().contains(MSG + "removed_request"),
                            "le demandeur absent l'apprend à sa connexion : " + revenu.messagesALaConnexion());
                    e.close();
                })
                .thenSucceed();
    }

    /**
     * La caisse va au fondateur, au cuivre près (caisse + coffre-fort + inventaire + attente, avant et après) ; un
     * versement qui échoue refuse toute la suppression, rien ne change.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void laCaisseVaAuFondateur(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mk_f");
            JoueurDeTest titulaire = e.joueur("mk_t");
            JoueurDeTest maire = e.maire("mk_m");
            PhoneOperator op = e.operateur(fondateur, "Caisse");
            SafeAccounts.get(e.serveur()).setSafePos(fondateur.uuid(),
                    GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(C)));
            ItemStack carte = OperateurDEssai.emettre(helper, op, titulaire);
            UUID simId = SimCardItem.simId(carte).orElse(null);
            helper.assertTrue(simId != null, "carte émise");
            Map<Coin, Long> caisse = Map.of(Coin.GOLD, 3L, Coin.SILVER, 7L, Coin.COPPER, 9L);
            caisse.forEach((coin, n) -> e.registre().deposit(op.id(), coin, n));
            long valeur = CoinMath.valueOf(e.registre().till(op.id()));
            long avant = valeur + avoir(e, fondateur);
            CommandSourceStack source = maire.joueur().createCommandSourceStack();

            // Un versement qui échoue : la suppression est refusée, rien n'a changé.
            helper.assertTrue(OperatorCommands.requestRemove(source, e.registre(), op.name(), "faillite",
                    EconomyClock.now()) == OperatorCommands.Result.REQUESTED, "demande");
            OperatorCommands.Payer panne = (serveur, qui, montant) -> {
                throw new IllegalStateException("versement en panne (essai)");
            };
            maire.clesRecues();
            helper.assertTrue(OperatorCommands.confirmRemove(source, e.registre(), op.name(), EconomyClock.now(), panne)
                    == OperatorCommands.Result.TILL_REFUSED, "versement en panne : refusé");
            helper.assertTrue(maire.clesRecues().contains(KEY + "remove_till_refused"), "le refus est dit");
            helper.assertTrue(e.registre().byId(op.id()).isPresent()
                    && CoinMath.valueOf(e.registre().till(op.id())) == valeur
                    && e.registre().card(simId).map(SimCard::state).orElse(null) == SimCard.State.ACTIVE
                    && avoir(e, fondateur) + valeur == avant, "rien n'a changé, aucune pièce perdue");

            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 1,
                    "la même demande se confirme ensuite");
            helper.assertTrue(e.registre().byId(op.id()).isEmpty(), "supprimé");
            long apres = avoir(e, fondateur);
            helper.assertTrue(apres == avant, "caisse au fondateur au cuivre près : " + apres + " / " + avant);
            helper.assertTrue(SafeAccounts.get(e.serveur()).total(fondateur.uuid()) == valeur,
                    "dans son coffre-fort d'abord");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_PHONE_REMOVE) && maire.uuid().equals(ligne.author())
                    && ligne.amount() == valeur && ligne.reason().contains("faillite")
                    && ligne.reason().contains(op.name()), "au journal : " + ligne);
            helper.assertTrue(!ligne.reason().contains(simId.toString()), "aucune carte au journal");
            helper.succeed();
        }
    }

    /**
     * L'objet d'antenne d'un opérateur supprimé (lâché par l'antenne restée posée) redevient, reposé, une antenne à
     * fonder pour l'ancien fondateur : aucun opérateur recréé, aucune inscription ; un tiers ne le repose pas.
     */
    @GameTest(template = "test/plancher", batch = LOT)
    public static void uneAntenneDUnOperateurSupprimeNeLeRessusciteRien(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mr_f");
            JoueurDeTest tiers = e.joueur("mr_t");
            JoueurDeTest tiersFondateur = e.joueur("mr_u");
            JoueurDeTest maire = e.maire("mr_m");
            PhoneOperator op = e.fonder(fondateur, A, "Ressuscite");
            PhoneOperator autre = e.operateur(tiersFondateur, "Voisin");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " fermeture") == 1
                    && e.commande(maire, "operator remove " + q(op) + " confirm") == 1, "suppression");
            int operateurs = e.registre().all().size();
            int inscrites = e.registre().antennas().size();

            // L'antenne restée posée appartient à l'ancien fondateur.
            PhoneOperators.FormerFounder proprietaire = e.antenne(A).idleOwner();
            helper.assertTrue(proprietaire != null && proprietaire.uuid().equals(fondateur.uuid()),
                    "l'antenne restée posée revient à l'ancien fondateur : " + proprietaire);
            // Démontée par lui : l'objet garde la marque de l'opérateur supprimé.
            helper.getLevel().destroyBlock(helper.absolutePos(A), true, fondateur.joueur());
            AABB boite = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 3, 8)));
            List<ItemEntity> lache = helper.getLevel().getEntitiesOfClass(ItemEntity.class, boite,
                    it -> it.getItem().is(PhoneRegistry.ANTENNA_ITEM.get()));
            helper.assertTrue(lache.size() == 1, "un objet d'antenne lâché, vu " + lache.size());
            ItemStack objet = lache.get(0).getItem().copy();
            lache.get(0).discard();
            helper.assertTrue(AntennaItem.operatorOf(objet).equals(Optional.of(op.id())),
                    "marqué de l'opérateur supprimé : " + objet.getTag());

            // Un tiers, même fondateur d'un opérateur, ne le repose pas (ni dans le sien).
            helper.assertTrue(!e.poser(tiersFondateur, C, objet.copy()).consumesAction()
                    && !(helper.getBlockEntity(C) instanceof AntennaBlockEntity), "un tiers fondateur : refusé");
            helper.assertTrue(!e.poser(tiers, C, objet.copy()).consumesAction()
                    && !(helper.getBlockEntity(C) instanceof AntennaBlockEntity), "un tiers : refusé");

            // Reposé par l'ancien fondateur : antenne à fonder pour lui, rien de recréé.
            helper.assertTrue(e.poser(fondateur, B, objet.copy()).consumesAction(), "l'ancien fondateur la repose");
            AntennaBlockEntity be = e.antenne(B);
            helper.assertTrue(be.operatorId() == null && be.antennaId() == null && be.operator().isEmpty(),
                    "antenne à fonder, sans opérateur");
            helper.assertTrue(fondateur.uuid().equals(be.placer()), "à fonder pour l'ancien fondateur");
            helper.assertTrue(e.registre().all().size() == operateurs && e.registre().byId(op.id()).isEmpty()
                    && e.registre().byFounder(fondateur.uuid()).isEmpty(), "aucun opérateur recréé");
            helper.assertTrue(e.registre().antennas().size() == inscrites, "aucune inscription");
            helper.assertTrue(e.registre().antennasOf(autre.id()).isEmpty(), "rien rattaché à l'opérateur voisin");

            // L'ancien fondateur la refonde : un opérateur neuf, distinct de l'ancien.
            Optional<PhoneOperators.Result> refonde = AntennaBlock.found(fondateur.joueur(), helper.absolutePos(B),
                    e.nom("Refonde"));
            helper.assertTrue(refonde.isPresent() && refonde.get().founded()
                    && !refonde.get().operator().id().equals(op.id())
                    && refonde.get().operator().founder().equals(fondateur.uuid()), "refondé par lui : " + refonde);
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot maire_telephone_b (horloge fixée)
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /operator cap} vaut au lendemain : le plafond du jour ne bouge pas, mais un tarif demandé aujourd'hui est
     * borné par celui de demain ; à minuit le plafond entre en vigueur et ramène les tarifs.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lePlafondVautAuLendemain(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("pl_f");
            JoueurDeTest maire = e.maire("pl_m");
            PhoneOperator op = e.operateur(fondateur, "Plafond");
            long aujourdhui = e.registre().tariffCap();
            long demain = aujourdhui >= 2L ? aujourdhui - 1L : 0L;
            helper.assertTrue(e.commande(maire, "operator cap " + (PhoneConfig.phoneTariffMax() + 1L)) == 0
                    && maire.clesRecues().contains(KEY + "cap_out_of_range"), "au-dessus de phoneTariffMax : refusé");
            helper.assertTrue(e.commande(maire, "operator cap -1") == 0, "négatif : refusé");
            helper.assertTrue(e.commande(maire, "operator cap " + demain) == 1, "le maire fixe le plafond");
            helper.assertTrue(e.registre().nextTariffCap().equals(Optional.of(demain))
                    && e.registre().tariffCap() == aujourdhui, "au lendemain : celui du jour ne bouge pas");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_PHONE_CAP) && maire.uuid().equals(ligne.author())
                    && ligne.amount() == demain, "au journal : " + ligne);
            helper.assertTrue(e.commande(maire, "operator set " + q(op) + " tariff " + (demain + 1)) == 0
                    && e.lire(op).nextTariff() == null, "un tarif pour demain est borné par le plafond de demain");

            // Minuit, sur un registre à part (le plafond du serveur est global) : le plafond entre en vigueur.
            PhoneOperators registre = new PhoneOperators();
            PhoneOperator local = registre.found(fondateur.uuid(), "pl_f", "Plafond local", EconomyClock.now())
                    .operator();
            CommandSourceStack source = maire.joueur().createCommandSourceStack();
            helper.assertTrue(OperatorCommands.setTariff(source, registre, local.name(), 15L)
                    == OperatorCommands.Result.DONE, "tarif 15 pour demain");
            helper.assertTrue(OperatorCommands.setCap(source, registre, 10L) == OperatorCommands.Result.DONE,
                    "plafond 10 pour demain");
            helper.assertTrue(registre.byId(local.id()).get().tariff() == 0L
                    && registre.tariffCap() == PhoneConfig.phoneDefaultTariffCap(), "rien le jour même");
            LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(),
                    EconomyConfig.payHour());
            long minuit = EconomyCalendar.boundaryAfter(jour, EconomyCalendar.configuredZone(), EconomyConfig.payHour());
            new OperatorDay(registre).issue(e.serveur(), jour, minuit + MINUTE);
            helper.assertTrue(registre.tariffCap() == 10L && registre.byId(local.id()).get().tariff() == 10L,
                    "à minuit : plafond en vigueur, tarif ramené : " + registre.byId(local.id()));
            helper.succeed();
        }
    }

    /**
     * Sans {@code confirm}, rien ne bouge ; la confirmation n'est valable que pour la même source et le même opérateur,
     * pendant 60 s (horloge de l'économie avancée, sans attendre).
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laSuppressionDemandeUneConfirmation(GameTestHelper helper) {
        long t0 = EconomyClock.now();
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mc_f");
            JoueurDeTest autreFondateur = e.joueur("mc_g");
            JoueurDeTest maire = e.maire("mc_m");
            JoueurDeTest equipe = e.equipe("mc_e");
            PhoneOperator op = e.operateur(fondateur, "Confirme");
            PhoneOperator autre = e.operateur(autreFondateur, "Autre");
            ItemStack carte = OperateurDEssai.emettre(helper, op, fondateur);
            UUID simId = SimCardItem.simId(carte).orElse(null);

            maire.clesRecues();
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 0
                    && maire.clesRecues().contains(KEY + "remove_no_request"), "confirmer sans demande : refusé, dit");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " fraude") == 1, "demande");
            helper.assertTrue(e.registre().byId(op.id()).isPresent()
                    && e.registre().card(simId).map(SimCard::state).orElse(null) == SimCard.State.ACTIVE,
                    "sans confirm : rien ne bouge, la carte reste active");
            helper.assertTrue(e.commande(equipe, "operator remove " + q(op) + " confirm") == 0,
                    "un autre ne confirme pas la demande du maire");
            helper.assertTrue(e.commande(maire, "operator remove " + q(autre) + " confirm") == 0
                    && e.registre().byId(autre.id()).isPresent(), "la confirmation ne vaut pas pour un autre opérateur");
            helper.assertTrue(e.registre().byId(op.id()).isPresent(), "toujours là");

            // Après 60 s : échue, et oubliée.
            EconomyClock.setClock(() -> t0 + OperatorCommands.CONFIRM_MILLIS + 1L);
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 0
                    && maire.clesRecues().contains(KEY + "remove_expired"), "après 60 s : échue");
            helper.assertTrue(e.registre().byId(op.id()).isPresent(), "échue : toujours là");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 0
                    && maire.clesRecues().contains(KEY + "remove_no_request"), "une demande échue est oubliée");

            // La console et un bloc de commande ne se confirment pas l'un l'autre.
            CommandSourceStack console = e.serveur().createCommandSourceStack();
            CommandSourceStack bloc = new CommandSourceStack(net.minecraft.commands.CommandSource.NULL, Vec3.ZERO,
                    Vec2.ZERO, helper.getLevel(), 2, "Bloc_" + e.sfx, Component.literal("Bloc"), e.serveur(), null);
            long now = EconomyClock.now();
            helper.assertTrue(OperatorCommands.requestRemove(console, e.registre(), op.name(), "console", now)
                    == OperatorCommands.Result.REQUESTED, "la console demande");
            helper.assertTrue(OperatorCommands.confirmRemove(bloc, e.registre(), op.name(), now)
                    == OperatorCommands.Result.NO_REQUEST && e.registre().byId(op.id()).isPresent(),
                    "un bloc de commande ne confirme pas la demande de la console");

            // Un motif vide après nettoyage (blancs, §, contrôles) : refusé, rien d'armé, et la demande d'avant oubliée.
            String vide = " " + (char) 0xA7 + (char) 0xA7 + (char) 7 + " ";
            helper.assertTrue(OperatorCommands.requestRemove(console, e.registre(), op.name(), vide, now)
                    == OperatorCommands.Result.REASON_REQUIRED, "motif vide : refusé");
            helper.assertTrue(OperatorCommands.confirmRemove(console, e.registre(), op.name(), now)
                    == OperatorCommands.Result.NO_REQUEST && e.registre().byId(op.id()).isPresent(),
                    "motif vide : la demande armée de la console est oubliée, rien n'est supprimé");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " fraude") == 1, "demande armée d'abord");
            maire.clesRecues();
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " " + (char) 0xA7 + (char) 0xA7) == 0
                    && maire.clesRecues().contains(KEY + "remove_reason_required"), "motif vide au chat : refusé, dit");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 0
                    && e.registre().byId(op.id()).isPresent()
                    && e.registre().card(simId).map(SimCard::state).orElse(null) == SimCard.State.ACTIVE,
                    "après un motif vide : rien à confirmer, la carte reste active");

            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " fraude") == 1, "nouvelle demande");
            EconomyClock.setClock(() -> t0 + 2L * OperatorCommands.CONFIRM_MILLIS);
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 1, "confirmée à temps");
            helper.assertTrue(e.registre().byId(op.id()).isEmpty()
                    && e.registre().card(simId).map(SimCard::state).orElse(null) == SimCard.State.DEACTIVATED,
                    "supprimé, carte désactivée");
            helper.assertTrue(e.commande(maire, "operator remove " + q(op) + " confirm") == 0, "une seule fois");
            helper.succeed();
        } finally {
            EconomyClock.setClock(() -> t0);
        }
    }
}
