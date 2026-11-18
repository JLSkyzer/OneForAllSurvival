package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyCommands;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.debit.DailyDebit;
import dev.oneforallsurvival.economy.debit.DebitPayees;
import dev.oneforallsurvival.economy.debit.DirectDebit;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Invoice;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry.Status;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.supplier.PowerActionPacket;
import dev.oneforallsurvival.grid.supplier.PowerBilling;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerCommands;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les gestes du maire et de l'équipe sur l'électricité (lot E6, tâche 6, features 83 et 107 du maire) : plafond du
 * prix et taxe au lendemain, modification et suppression d'un fournisseur (avec confirmation), propriétaire changé,
 * tableau de bord ({@code /eco power}, {@code /eco stats}), et deux reports des tâches précédentes : {@code /eco salary
 * resetday} ramène aussi le jour du prélèvement, et un genre de facture inscrit après le démarrage n'annule pas ses
 * factures.
 *
 * <p>Chaque essai tient dans un seul appel (rien n'attend un tic) : le plafond, la taxe et le maire, qui sont ceux du
 * serveur, sont rendus avant qu'un autre essai ne tourne. Les commandes passent par le vrai répartiteur du serveur,
 * avec la source d'un vrai joueur d'essai (non opérateur, sauf ceux de l'équipe).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class MaireElectriciteGameTests {

    private static final String LOT_A = "maire_elec_a";
    private static final String LOT_B = "maire_elec_b";

    private static final String KEY = "command.oneforallsurvival.power.";
    private static final String MESSAGE = PowerPairing.MESSAGE;

    private static final BlockPos POSTE = new BlockPos(2, 0, 2);
    private static final BlockPos COMPTEUR = new BlockPos(2, 0, 5);
    private static final BlockPos AUTRE_COMPTEUR = new BlockPos(5, 0, 5);
    private static final Vec3 JOUEUR = new Vec3(5.5, 0, 2.5);

    private static final LocalDate J = LocalDate.of(2026, 6, 14);
    private static final long MINUTE = 60_000L;

    private MaireElectriciteGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    private static void boite(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean bord = x == 0 || x == 7 || z == 0 || z == 7;
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), bord ? Blocks.STONE : Blocks.AIR);
                }
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
            }
        }
    }

    /** Un suffixe court qui rend les noms d'un essai uniques d'un passage à l'autre. */
    private static String suffixe() {
        String s = Long.toString(System.nanoTime(), 36);
        return s.substring(Math.max(0, s.length() - 5));
    }

    /**
     * Joueurs, rôles, équipe, maire, fournisseurs, plafond et taxe du lendemain d'un essai : tout est rendu, même si
     * l'essai échoue.
     */
    private static final class Essai implements AutoCloseable {
        final GameTestHelper helper;
        final String sfx = suffixe();
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final List<UUID> uuids = new ArrayList<>();
        private final Optional<UUID> maireAvant;
        private final Optional<Long> plafondAvant;
        private final Optional<Integer> taxeAvant;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
            this.maireAvant = RoleSavedData.get(serveur()).book().mayor();
            this.plafondAvant = registre().nextPriceCap();
            this.taxeAvant = registre().nextTaxPercent();
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        PowerSuppliers registre() {
            return PowerSuppliers.get(serveur());
        }

        RoleBook livre() {
            return RoleSavedData.get(serveur()).book();
        }

        String nom(String court) {
            return court + "_" + sfx;
        }

        JoueurDeTest joueur(String court) {
            String nom = nom(court);
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            Vec3 ici = helper.absoluteVec(JOUEUR);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            uuids.add(j.uuid());
            EconomyLedger.get(serveur()).markStartingGiven(j.uuid());
            livre().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe : " + nom);
            return j;
        }

        /** Le même joueur, reconnecté (il garde son nom, donc son identifiant). */
        JoueurDeTest revenir(JoueurDeTest parti) {
            String nom = parti.joueur().getGameProfile().getName();
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            joueurs.add(j);
            return j;
        }

        JoueurDeTest ingenieur(String court) {
            JoueurDeTest j = joueur(court);
            livre().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        JoueurDeTest maire(String court) {
            JoueurDeTest j = joueur(court);
            livre().appointMayor(j.uuid());
            return j;
        }

        JoueurDeTest equipe(String court) {
            JoueurDeTest j = joueur(court);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            helper.assertTrue(j.joueur().hasPermissions(2), "le joueur de l'équipe doit être opérateur");
            return j;
        }

        /** Un fournisseur sans poste, au nom de ce joueur. */
        PowerSupplier fournisseur(JoueurDeTest fondateur, String nom) {
            PowerSuppliers.Result r = registre().found(fondateur.uuid(), fondateur.joueur().getGameProfile().getName(),
                    nom(nom));
            helper.assertTrue(r.founded(), "fondation de " + nom + " : " + r.status());
            return r.supplier();
        }

        PowerSupplier lire(PowerSupplier s) {
            return registre().byId(s.id()).orElseThrow(() -> new AssertionError("fournisseur disparu : " + s.name()));
        }

        /** Un client actif de ce fournisseur, sur un compteur fictif (rien de posé). */
        UUID client(PowerSupplier s, JoueurDeTest joueur) {
            UUID compteur = UUID.randomUUID();
            String nom = joueur.joueur().getGameProfile().getName();
            registre().putClient(new PowerClient(compteur, s.id(), joueur.uuid(), nom, nom, PowerClient.State.ACTIVE,
                    0L, 0L, Set.of(), false));
            return compteur;
        }

        /** Un compteur posé en {@code pos}, jumelé et actif chez ce fournisseur, au nom de ce joueur. */
        MeterBlockEntity compteurRaccorde(PowerSupplier s, BlockPos pos, UUID joueur, String nom) {
            helper.setBlock(pos, GridRegistry.METER.get().defaultBlockState()
                    .setValue(InlineEnergyBlock.FACING, Direction.EAST));
            MeterBlockEntity be = (MeterBlockEntity) helper.getBlockEntity(pos);
            be.setSubscriber(nom);
            registre().putClient(new PowerClient(be.serial(), s.id(), joueur, nom, nom, PowerClient.State.ACTIVE, 0L,
                    0L, Set.of(), false));
            GlobalPos ici = GlobalPos.of(helper.getLevel().dimension(), be.getBlockPos());
            registre().setPairedAt(be.serial(), ici);
            registre().noteMeterLoaded(be.serial(), ici);
            return be;
        }

        int commande(JoueurDeTest j, String commande) {
            return serveur().getCommands().performPrefixedCommand(j.joueur().createCommandSourceStack(), commande);
        }

        /** Vide la caisse (pièces des essais) puis retire le fournisseur de ce joueur, s'il en a un. */
        void ranger(UUID fondateur) {
            registre().byFounder(fondateur).ifPresent(s -> {
                s.till().forEach((coin, n) -> registre().withdraw(s.id(), coin, n));
                registre().removeIfEmpty(s.id());
            });
        }

        @Override
        public void close() {
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            if (maireAvant.isPresent()) {
                livre().appointMayor(maireAvant.get());
            } else {
                livre().removeMayor();
            }
            registre().resetNextCapAndTax(plafondAvant.orElse(null), taxeAvant.orElse(null));
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            for (UUID uuid : uuids) {
                livre().assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                ranger(uuid);
                EnumMap<Coin, Long> attente = HeldGoods.get(serveur()).pendingCoins(uuid);
                if (!attente.isEmpty()) {
                    HeldGoods.get(serveur()).takeCoins(uuid, attente);
                }
            }
            boite(helper);
        }
    }

    /** Un poste posé par {@code j} (l'objet en main) puis fondé sous {@code nom}. */
    private static PowerSupplier poste(GameTestHelper helper, JoueurDeTest j, String nom) {
        ServerPlayer p = j.joueur();
        ItemStack pile = new ItemStack(GridRegistry.POWER_STATION_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(POSTE.below());
        InteractionResult r = pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile,
                new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
        helper.assertTrue(r.consumesAction(), "le poste devait se poser : " + r);
        Optional<PowerSuppliers.Result> fonde = PowerStationBlock.found(p, helper.absolutePos(POSTE), nom);
        helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
        return fonde.get().supplier();
    }

    private static PowerActionPacket.Result panneau(ServerPlayer p, PowerActionPacket paquet) {
        PowerActionPacket.clearThrottle(p.getUUID());
        return PowerActionPacket.apply(p, paquet);
    }

    /** Pousse {@code fe} FE dans l'entrée (face ouest) ; renvoie ce qui est passé. */
    private static int pousser(InlineEnergyBlockEntity be, int fe) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).resolve().orElse(null);
        return cap == null ? 0 : cap.receiveEnergy(fe, true);
    }

    private static String q(PowerSupplier s) {
        return "\"" + s.name() + "\"";
    }

    private static EnumMap<Coin, Long> pieces(Object... paires) {
        EnumMap<Coin, Long> out = new EnumMap<>(Coin.class);
        for (int i = 0; i < paires.length; i += 2) {
            out.put((Coin) paires[i], ((Number) paires[i + 1]).longValue());
        }
        return out;
    }

    /** Une facture d'électricité due de ce fournisseur pour ce compteur et ce joueur, dans ce registre. */
    private static Invoice facture(InvoiceRegistry registre, PowerSupplier s, UUID compteur, UUID joueur, String nom,
            long montant, long now) {
        PowerBilling.Ref ref = new PowerBilling.Ref(s.id(), compteur, 0, J.toEpochDay(), 100_000L, montant, 100_000L,
                0L, s.name());
        return registre.issuePublic(PowerBilling.KIND, ref.encode(), s.founder(), s.founderName(), joueur, nom,
                montant, s.name(), now);
    }

    private static Status statut(InvoiceRegistry registre, Invoice f) {
        return registre.get(f.id()).map(Invoice::status).orElse(null);
    }

    /** Les arguments du premier texte de clé {@code key} dans ce composant (et ses enfants), ou nul. */
    private static Object[] arguments(Component c, String key) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key)) {
                return t.getArgs();
            }
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner) {
                    Object[] found = arguments(inner, key);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        for (Component sibling : c.getSiblings()) {
            Object[] found = arguments(sibling, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String texte(Object arg) {
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    private static EconomyLedger.Entry derniereLigne(Essai e) {
        List<EconomyLedger.Entry> lignes = EconomyLedger.get(e.serveur()).entries(0);
        if (lignes.isEmpty()) {
            throw new AssertionError("le journal est vide");
        }
        return lignes.get(0);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : plafond, droits, modification, suppression
    // ---------------------------------------------------------------------------------------------

    /** Le plafond du lendemain borne le prix au panneau comme par {@code /power set}. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unFournisseurNePeutPasDepasserLePlafond(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mp_f");
            JoueurDeTest maire = e.maire("mp_m");
            PowerSupplier s = poste(helper, fondateur, e.nom("Plafond"));
            BlockPos poste = helper.absolutePos(POSTE);
            long plafondDuJour = e.registre().priceCap();

            helper.assertTrue(e.commande(maire, "power cap 5") == 1, "le maire fixe le plafond");
            helper.assertTrue(e.registre().nextPriceCap().equals(Optional.of(5L)), "plafond du lendemain : 5");
            helper.assertTrue(e.registre().priceCap() == plafondDuJour, "le plafond du jour ne bouge pas");

            helper.assertTrue(panneau(fondateur.joueur(), PowerActionPacket.price(poste, 6L))
                    == PowerActionPacket.Result.REFUSED, "panneau : 6 au-dessus du plafond de 5, refusé");
            helper.assertTrue(e.lire(s).nextPrice() == null, "panneau : rien n'est noté");
            helper.assertTrue(panneau(fondateur.joueur(), PowerActionPacket.price(poste, 5L))
                    == PowerActionPacket.Result.DONE && e.lire(s).nextPrice() == 5L, "panneau : le plafond pile passe");

            maire.messagesRecus();
            helper.assertTrue(e.commande(maire, "power set " + q(s) + " price 6") == 0,
                    "/power set : 6 au-dessus du plafond, refusé");
            helper.assertTrue(maire.messagesRecus().contains(KEY + "set_above_cap"), "le refus est dit");
            helper.assertTrue(e.lire(s).nextPrice() == 5L, "/power set refusé : le prix du lendemain reste 5");
            helper.assertTrue(e.commande(maire, "power set " + q(s) + " price 4") == 1 && e.lire(s).nextPrice() == 4L,
                    "/power set : 4 sous le plafond, accepté pour demain");
            helper.assertTrue(e.lire(s).price() == 0L, "le prix du jour ne bouge pas");
            helper.assertTrue(e.commande(maire, "power set " + q(s) + " subscription "
                    + (EconomyConfig.powerMaxSubscription() + 1L)) == 0, "abonnement au-dessus du maximum : refusé");
            helper.assertTrue(e.commande(maire, "power set " + q(s) + " price -1") == 0, "prix négatif : refusé");
            helper.succeed();
        }
    }

    /** Citoyen, ingénieur et fondateur ne règlent ni le plafond ni la taxe ; le maire et l'équipe, si. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void seulsLeMaireEtLEquipeReglentLePlafond(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest citoyen = e.joueur("ms_c");
            JoueurDeTest ingenieur = e.ingenieur("ms_i");
            JoueurDeTest fondateur = e.joueur("ms_f");
            JoueurDeTest maire = e.maire("ms_m");
            JoueurDeTest equipe = e.equipe("ms_e");
            PowerSupplier s = e.fournisseur(fondateur, "Droits");
            Optional<Long> plafond = e.registre().nextPriceCap();
            Optional<Integer> taxe = e.registre().nextTaxPercent();
            for (JoueurDeTest sansDroit : List.of(citoyen, ingenieur, fondateur)) {
                sansDroit.messagesRecus();
                helper.assertTrue(e.commande(sansDroit, "power cap 7") == 0, "plafond refusé à " + sansDroit.uuid());
                helper.assertTrue(e.commande(sansDroit, "power tax 5") == 0, "taxe refusée");
                helper.assertTrue(e.commande(sansDroit, "power set " + q(s) + " price 1") == 0, "set refusé");
                helper.assertTrue(e.commande(sansDroit, "power suppliers") == 0, "liste refusée");
                helper.assertTrue(sansDroit.messagesRecus().contains(KEY + "not_mayor"), "le refus est clair");
                helper.assertTrue(e.registre().nextPriceCap().equals(plafond) && e.registre().nextTaxPercent().equals(taxe)
                        && e.lire(s).nextPrice() == null, "rien n'a bougé");
            }
            helper.assertTrue(e.commande(maire, "power cap 7") == 1 && e.registre().nextPriceCap().equals(Optional.of(7L)),
                    "le maire fixe le plafond");
            helper.assertTrue(e.commande(maire, "power tax 5") == 1
                    && e.registre().nextTaxPercent().equals(Optional.of(5)), "le maire fixe la taxe");
            int max = EconomyConfig.powerTaxMaxPercent();
            helper.assertTrue(e.commande(maire, "power tax " + (max + 1)) == 0
                    && e.registre().nextTaxPercent().equals(Optional.of(5)), "taxe au-dessus du maximum : refusée");
            helper.assertTrue(e.commande(equipe, "power cap 8") == 1 && e.registre().nextPriceCap().equals(Optional.of(8L)),
                    "l'équipe fixe le plafond");
            helper.assertTrue(e.commande(equipe, "power tax " + max) == 1
                    && e.registre().nextTaxPercent().equals(Optional.of(max)), "l'équipe fixe la taxe au maximum");
            helper.succeed();
        }
    }

    /** Le maire, client du fournisseur, règle son tarif par commande, et en change le propriétaire. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leMaireModifieUnFournisseur(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mm_f");
            JoueurDeTest maire = e.maire("mm_m");
            JoueurDeTest repreneur = e.joueur("mm_r");
            JoueurDeTest occupe = e.joueur("mm_o");
            PowerSupplier s = e.fournisseur(fondateur, "Modifie");
            e.fournisseur(occupe, "Occupe");
            e.client(s, maire);
            helper.assertTrue(!PowerActionPacket.mayTariff(maire.joueur(), s, e.registre()),
                    "le maire client ne règle pas le tarif au panneau");
            fondateur.messagesRecus();
            maire.messagesRecus();

            helper.assertTrue(e.commande(maire, "power set " + q(s) + " price 3") == 1, "le maire fixe le prix");
            helper.assertTrue(e.commande(maire, "power set " + q(s) + " subscription 10") == 1, "et l'abonnement");
            PowerSupplier apres = e.lire(s);
            helper.assertTrue(apres.nextPrice() == 3L && apres.nextSubscription() == 10L && apres.price() == 0L
                    && apres.subscription() == 0L, "au lendemain seulement : " + apres);
            helper.assertTrue(fondateur.messagesRecus().contains(MESSAGE + "tariff_by_mayor"), "le fondateur est prévenu");
            helper.assertTrue(maire.messagesRecus().contains(PowerActionPacket.MESSAGE + "tariff_next"),
                    "les clients connectés sont prévenus (le maire en est un)");

            helper.assertTrue(e.commande(maire, "power suppliers") >= 2, "le maire voit les fournisseurs");

            helper.assertTrue(e.commande(maire, "power owner " + q(s) + " inconnu_" + e.sfx) == 0,
                    "un joueur inconnu ne reprend pas un fournisseur");
            helper.assertTrue(e.commande(maire, "power owner " + q(s) + " " + occupe.joueur().getGameProfile().getName())
                    == 0, "un joueur qui a déjà un fournisseur n'en reprend pas un second");
            helper.assertTrue(e.lire(s).founder().equals(fondateur.uuid()), "rien n'a bougé");
            helper.assertTrue(e.commande(maire, "power owner " + q(s) + " "
                    + repreneur.joueur().getGameProfile().getName()) == 1, "le maire change le propriétaire");
            PowerSupplier repris = e.lire(s);
            helper.assertTrue(repris.founder().equals(repreneur.uuid())
                    && repris.founderName().equals(repreneur.joueur().getGameProfile().getName()), "nouveau fondateur");
            helper.assertTrue(e.registre().byFounder(fondateur.uuid()).isEmpty()
                    && e.registre().byFounder(repreneur.uuid()).map(PowerSupplier::id).equals(Optional.of(s.id())),
                    "un fournisseur par joueur");
            helper.assertTrue(fondateur.messagesRecus().contains(MESSAGE + "owner_lost"), "l'ancien est prévenu");
            helper.assertTrue(repreneur.messagesRecus().contains(MESSAGE + "owner_new"), "le nouveau est prévenu");
            helper.assertTrue(e.registre().clients(s.id()).size() == 1, "les clients restent");
            helper.succeed();
        }
    }

    /**
     * Supprimé par le maire : clients détachés, prévenus (absents à leur connexion) et coupés ; factures dues annulées ;
     * caisse au fondateur (absent : en attente), au cuivre près ; jumelages oubliés.
     */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unFournisseurSupprimeParLeMaireNeFacturePlusPersonne(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mr_f");
            JoueurDeTest present = e.joueur("mr_p");
            JoueurDeTest absent = e.joueur("mr_a");
            JoueurDeTest maire = e.maire("mr_m");
            JoueurDeTest autreFondateur = e.joueur("mr_t");
            PowerSupplier s = e.fournisseur(fondateur, "Supprime");
            PowerSupplier autre = e.fournisseur(autreFondateur, "Reste");
            String nomPresent = present.joueur().getGameProfile().getName();
            String nomAbsent = absent.joueur().getGameProfile().getName();
            MeterBlockEntity c1 = e.compteurRaccorde(s, COMPTEUR, present.uuid(), nomPresent);
            MeterBlockEntity c2 = e.compteurRaccorde(s, AUTRE_COMPTEUR, absent.uuid(), nomAbsent);
            UUID enAttente = UUID.randomUUID();
            e.registre().putClient(new PowerClient(enAttente, s.id(), absent.uuid(), nomAbsent, nomAbsent,
                    PowerClient.State.AWAITING_CONSENT, 0L, 0L, Set.of(), false));
            String code = e.registre().newConsentCode(enAttente, helper.getLevel().getRandom());
            helper.setBlock(COMPTEUR.east(), DevBlocks.testMachine());
            helper.assertTrue(c1.isOpen() && pousser(c1, 100) == 100, "avant : le courant passe");

            EnumMap<Coin, Long> caisse = pieces(Coin.GOLD, 3L, Coin.SILVER, 7L, Coin.COPPER, 9L);
            caisse.forEach((coin, n) -> e.registre().deposit(s.id(), coin, n));
            long valeur = CoinMath.valueOf(caisse);
            helper.assertTrue(e.lire(s).tillValue() == valeur, "caisse : " + valeur);

            InvoiceRegistry factures = InvoiceRegistry.get(e.serveur());
            long now = EconomyClock.now();
            Invoice f1 = facture(factures, s, c1.serial(), present.uuid(), nomPresent, 120L, now);
            Invoice f2 = facture(factures, s, c2.serial(), absent.uuid(), nomAbsent, 35L, now);
            Invoice fAutre = facture(factures, autre, UUID.randomUUID(), present.uuid(), nomPresent, 50L, now);
            try {
                fondateur.quitter();
                absent.quitter();
                present.messagesRecus();

                helper.assertTrue(e.commande(maire, "power remove " + q(s) + " compteurs truqués") == 1,
                        "demande de suppression");
                helper.assertTrue(e.registre().byId(s.id()).isPresent() && statut(factures, f1) == Status.DUE,
                        "rien ne bouge avant la confirmation");
                helper.assertTrue(e.commande(maire, "power remove " + q(s) + " confirm") == 1, "confirmation");

                helper.assertTrue(e.registre().byId(s.id()).isEmpty(), "le fournisseur n'existe plus");
                helper.assertTrue(e.registre().clients(s.id()).isEmpty() && e.registre().clientOf(c1.serial()).isEmpty()
                        && e.registre().clientOf(c2.serial()).isEmpty() && e.registre().clientOf(enAttente).isEmpty(),
                        "tous les clients sont détachés");
                GlobalPos ici1 = GlobalPos.of(helper.getLevel().dimension(), c1.getBlockPos());
                GlobalPos ici2 = GlobalPos.of(helper.getLevel().dimension(), c2.getBlockPos());
                helper.assertTrue(!e.registre().isPairedAt(c1.serial(), ici1) && !e.registre().isPairedAt(c2.serial(), ici2),
                        "les jumelages sont oubliés");
                helper.assertTrue(e.registre().meterOfCode(code).isEmpty(), "le code d'accord en cours ne vaut plus");
                helper.assertTrue(!c1.isOpen() && pousser(c1, 100) == 0, "le courant est coupé");
                e.registre().recordMeter(c1.serial(), 5_000);
                helper.assertTrue(e.registre().clientOf(c1.serial()).isEmpty(), "plus rien ne se compte");
                helper.assertTrue(e.registre().detached().stream().noneMatch(c -> c.supplierId().equals(s.id())),
                        "rien n'attend la clôture pour ce fournisseur");

                helper.assertTrue(statut(factures, f1) == Status.CANCELLED && statut(factures, f2) == Status.CANCELLED,
                        "les factures dues sont annulées");
                helper.assertTrue(statut(factures, fAutre) == Status.DUE, "celle d'un autre fournisseur reste due");

                long attente = CoinMath.valueOf(HeldGoods.get(e.serveur()).pendingCoins(fondateur.uuid()));
                helper.assertTrue(attente == valeur, "caisse au fondateur absent, en attente, au cuivre près : "
                        + attente + " / " + valeur);

                helper.assertTrue(present.messagesRecus().contains(MESSAGE + "supplier_removed_client"),
                        "le client connecté est prévenu");
                JoueurDeTest absentRevenu = e.revenir(absent);
                helper.assertTrue(absentRevenu.messagesALaConnexion().contains(MESSAGE + "supplier_removed_client"),
                        "le client absent l'apprend à sa connexion : " + absentRevenu.messagesALaConnexion());
                JoueurDeTest fondateurRevenu = e.revenir(fondateur);
                helper.assertTrue(fondateurRevenu.messagesALaConnexion().contains(MESSAGE + "supplier_removed_founder"),
                        "le fondateur absent l'apprend à sa connexion : " + fondateurRevenu.messagesALaConnexion());
                helper.assertTrue(e.registre().byId(autre.id()).isPresent(), "l'autre fournisseur reste");
                helper.succeed();
            } finally {
                factures.cancel(fAutre.id(), now);
                factures.cancel(f1.id(), now);
                factures.cancel(f2.id(), now);
            }
        }
    }

    /** La suppression attend une confirmation de la même personne, pour le même fournisseur, dans les 60 s. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void laSuppressionDemandeUneConfirmation(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest fondateur = e.joueur("mc_f");
            JoueurDeTest autreFondateur = e.joueur("mc_g");
            JoueurDeTest maire = e.maire("mc_m");
            JoueurDeTest equipe = e.equipe("mc_e");
            JoueurDeTest citoyen = e.joueur("mc_c");
            PowerSupplier s = e.fournisseur(fondateur, "Confirme");
            PowerSupplier autre = e.fournisseur(autreFondateur, "Autre");
            CommandSourceStack source = maire.joueur().createCommandSourceStack();
            PowerSuppliers registre = e.registre();
            InvoiceRegistry factures = InvoiceRegistry.get(e.serveur());

            maire.messagesRecus();
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " confirm") == 0, "confirmer sans demande");
            helper.assertTrue(maire.messagesRecus().contains(KEY + "remove_no_request"), "le refus est dit");
            helper.assertTrue(e.commande(citoyen, "power remove " + q(s) + " pour rire") == 0, "un citoyen ne demande pas");
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " fraude") == 1, "demande");
            helper.assertTrue(registre.byId(s.id()).isPresent(), "demandé : toujours là");
            helper.assertTrue(e.commande(equipe, "power remove " + q(s) + " confirm") == 0,
                    "un autre ne confirme pas la demande du maire");
            helper.assertTrue(e.commande(maire, "power remove " + q(autre) + " confirm") == 0,
                    "la confirmation vaut pour le fournisseur demandé seulement");
            helper.assertTrue(registre.byId(autre.id()).isPresent(), "l'autre reste");

            long now = EconomyClock.now();
            helper.assertTrue(PowerCommands.requestRemove(source, registre, s.name(), "fraude", now)
                    == PowerCommands.Result.REQUESTED, "demande datée");
            helper.assertTrue(PowerCommands.confirmRemove(source, registre, factures, s.name(),
                    now + PowerCommands.CONFIRM_MILLIS + 1L) == PowerCommands.Result.EXPIRED, "après 60 s : échue");
            helper.assertTrue(registre.byId(s.id()).isPresent(), "échue : toujours là");
            helper.assertTrue(PowerCommands.confirmRemove(source, registre, factures, s.name(), now + 2L)
                    == PowerCommands.Result.NO_REQUEST, "une demande échue est oubliée");

            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " fraude") == 1, "nouvelle demande");
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " confirm") == 1, "confirmée à temps");
            helper.assertTrue(registre.byId(s.id()).isEmpty(), "supprimé");
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " confirm") == 0, "une seule fois");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : équipe sans maire, registre, tableau de bord, reports
    // ---------------------------------------------------------------------------------------------

    /** Sans maire, l'équipe (et la console) fait tous ces gestes ; un ancien maire, plus aucun. */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void sansMaireLEquipeFaitCesGestes(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest ancienMaire = e.maire("mn_x");
            e.livre().removeMayor();
            JoueurDeTest equipe = e.equipe("mn_e");
            JoueurDeTest fondateur = e.joueur("mn_f");
            JoueurDeTest repreneur = e.joueur("mn_r");
            PowerSupplier s = e.fournisseur(fondateur, "SansMaire");
            helper.assertTrue(e.livre().mayor().isEmpty(), "aucun maire");

            helper.assertTrue(e.commande(ancienMaire, "power cap 9") == 0, "un ancien maire n'a plus la main");
            helper.assertTrue(e.commande(equipe, "power cap 9") == 1 && e.registre().nextPriceCap().equals(Optional.of(9L)),
                    "l'équipe fixe le plafond");
            helper.assertTrue(e.commande(equipe, "power tax 3") == 1 && e.registre().nextTaxPercent().equals(Optional.of(3)),
                    "la taxe");
            helper.assertTrue(e.commande(equipe, "power set " + q(s) + " price 2") == 1 && e.lire(s).nextPrice() == 2L,
                    "le prix d'un fournisseur");
            helper.assertTrue(e.commande(equipe, "power suppliers") >= 1, "la liste");
            int console = e.serveur().getCommands().performPrefixedCommand(e.serveur().createCommandSourceStack(),
                    "power cap 6");
            helper.assertTrue(console == 1 && e.registre().nextPriceCap().equals(Optional.of(6L)), "la console aussi");
            helper.assertTrue(e.commande(equipe, "power owner " + q(s) + " "
                    + repreneur.joueur().getGameProfile().getName()) == 1
                    && e.lire(s).founder().equals(repreneur.uuid()), "le propriétaire");
            helper.assertTrue(e.commande(equipe, "power remove " + q(s) + " essai") == 1
                    && e.commande(equipe, "power remove " + q(s) + " confirm") == 1
                    && e.registre().byId(s.id()).isEmpty(), "la suppression");
            helper.succeed();
        }
    }

    /** Chaque geste accepté laisse une ligne au journal du registre, avec son auteur ; un geste refusé, aucune. */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void chaqueGesteEstAuRegistre(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest maire = e.maire("mj_m");
            JoueurDeTest citoyen = e.joueur("mj_c");
            JoueurDeTest fondateur = e.joueur("mj_f");
            JoueurDeTest repreneur = e.joueur("mj_r");
            PowerSupplier s = e.fournisseur(fondateur, "Journal");
            EconomyLedger journal = EconomyLedger.get(e.serveur());

            int lignes = journal.entryCount();
            helper.assertTrue(e.commande(citoyen, "power cap 4") == 0 && journal.entryCount() == lignes,
                    "un geste refusé n'écrit rien");

            record Attendu(String commande, String genre, long montant, String motif) {}
            List<Attendu> gestes = List.of(
                    new Attendu("power cap 4", EconomyLedger.KIND_POWER_CAP, 4L, null),
                    new Attendu("power tax 2", EconomyLedger.KIND_POWER_TAX, 0L, "2"),
                    new Attendu("power set " + q(s) + " price 3", EconomyLedger.KIND_POWER_PRICE, 3L, s.name()),
                    new Attendu("power set " + q(s) + " subscription 11", EconomyLedger.KIND_POWER_SUBSCRIPTION, 11L,
                            s.name()),
                    new Attendu("power owner " + q(s) + " " + repreneur.joueur().getGameProfile().getName(),
                            EconomyLedger.KIND_POWER_OWNER, 0L, repreneur.joueur().getGameProfile().getName()));
            for (Attendu geste : gestes) {
                helper.assertTrue(e.commande(maire, geste.commande()) == 1, "geste : " + geste.commande());
                EconomyLedger.Entry ligne = derniereLigne(e);
                helper.assertTrue(ligne.kind().equals(geste.genre()) && maire.uuid().equals(ligne.author())
                        && ligne.amount() == geste.montant()
                        && (geste.motif() == null || ligne.reason().contains(geste.motif())),
                        "ligne de « " + geste.commande() + " » : " + ligne);
            }
            e.registre().deposit(s.id(), Coin.SILVER, 4L);
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " ligne pirate") == 1, "demande");
            helper.assertTrue(e.commande(maire, "power remove " + q(s) + " confirm") == 1, "confirmation");
            EconomyLedger.Entry ligne = derniereLigne(e);
            helper.assertTrue(ligne.kind().equals(EconomyLedger.KIND_POWER_REMOVE) && maire.uuid().equals(ligne.author())
                    && ligne.amount() == CoinMath.valueOf(Coin.SILVER, 4L) && ligne.reason().contains("ligne pirate")
                    && ligne.reason().contains(s.name()), "ligne de la suppression : " + ligne);
            helper.succeed();
        }
    }

    /**
     * {@code /eco stats} : factures publiques dues (nombre, total), caisses des postes, électricité (factures payées et
     * taxe) ; {@code /eco power} : par fournisseur, caisse, écart d'hier, factures dues.
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void ecoStatsCompteLesFacturesDues(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            MinecraftServer serveur = e.serveur();
            JoueurDeTest equipe = e.equipe("mt_e");
            PowerSuppliers registre = new PowerSuppliers();
            InvoiceRegistry factures = InvoiceRegistry.load(new CompoundTag());
            UUID fa = UUID.randomUUID();
            UUID fb = UUID.randomUUID();
            PowerSupplier a = registre.found(fa, "Fa", "Stats A " + e.sfx).supplier();
            PowerSupplier b = registre.found(fb, "Fb", "Stats B " + e.sfx).supplier();
            registre.deposit(a.id(), Coin.GOLD, 3L);
            registre.deposit(b.id(), Coin.SILVER, 2L);
            registre.recordOut(a.id(), 500_000);
            registre.closeSupplierDay(a.id(), 300_000L);
            long now = EconomyClock.now();
            UUID debiteur = UUID.randomUUID();
            facture(factures, a, UUID.randomUUID(), debiteur, "D", 120L, now);
            facture(factures, a, UUID.randomUUID(), debiteur, "D", 35L, now);
            facture(factures, b, UUID.randomUUID(), debiteur, "D", 50L, now);
            Invoice payee = facture(factures, a, UUID.randomUUID(), debiteur, "D", 70L, now);
            factures.markPaid(payee.id(), now);
            EconomyLedger journal = EconomyLedger.get(serveur);
            long payeesAvant = journal.electricityBillsPaid();
            long totalAvant = journal.electricityPaidTotal();
            new PowerBilling(registre, factures).receive(serveur, factures.get(payee.id()).orElseThrow(), 70L);
            helper.assertTrue(journal.electricityBillsPaid() == payeesAvant + 1
                    && journal.electricityPaidTotal() == totalAvant + 70L, "une facture d'électricité payée est comptée");

            PowerCommands.Board tableau = PowerCommands.board(registre, factures);
            long caisses = CoinMath.valueOf(Coin.GOLD, 3L) + 70L + CoinMath.valueOf(Coin.SILVER, 2L);
            helper.assertTrue(tableau.tillTotal() == caisses && tableau.dueCount() == 3 && tableau.dueTotal() == 205L,
                    "totaux : " + tableau);
            PowerCommands.SupplierRow ligneA = tableau.rows().stream().filter(r -> r.supplier().id().equals(a.id()))
                    .findFirst().orElseThrow();
            helper.assertTrue(ligneA.dueCount() == 2 && ligneA.dueTotal() == 155L && ligneA.gapYesterday() == 200_000L,
                    "ligne de A : " + ligneA);

            Component stats = EconomyCommands.statsReport(serveur, factures, registre);
            Object[] dues = arguments(stats, "command.oneforallsurvival.eco.stats.public_due");
            helper.assertTrue(dues != null && texte(dues[0]).equals("3")
                    && texte(dues[1]).equals(CoinMath.format(205L).getString()), "factures publiques dues : 3 (205)");
            Object[] tills = arguments(stats, "command.oneforallsurvival.eco.stats.tills");
            helper.assertTrue(tills != null && texte(tills[0]).equals(CoinMath.format(caisses).getString())
                    && texte(tills[1]).equals("2"), "caisses des postes");
            Object[] elec = arguments(stats, "command.oneforallsurvival.eco.stats.electricity");
            helper.assertTrue(elec != null && texte(elec[0]).equals(String.valueOf(journal.electricityBillsPaid()))
                    && texte(elec[1]).equals(CoinMath.format(journal.electricityPaidTotal()).getString())
                    && texte(elec[2]).equals(CoinMath.format(journal.totalTransferred(MoneyTransfer.POWER_TAX))
                            .getString()), "électricité : payées et taxe");
            Component tableauTexte = PowerCommands.boardReport(serveur, registre, factures);
            Object[] ligne = arguments(tableauTexte, "command.oneforallsurvival.eco.power.line");
            helper.assertTrue(ligne != null, "/eco power a une ligne par fournisseur");

            helper.assertTrue(e.commande(equipe, "eco stats") == 1, "/eco stats répond");
            helper.assertTrue(e.commande(equipe, "eco power") == 1, "/eco power répond");
            JoueurDeTest citoyen = e.joueur("mt_c");
            helper.assertTrue(e.commande(citoyen, "eco power") == 0, "/eco power est réservé à l'équipe");
            helper.succeed();
        }
    }

    /**
     * {@code /eco salary resetday} ramène aussi le dernier jour du prélèvement à la veille quand il était dans le futur
     * (sinon le prélèvement sauterait les jours jusque-là) ; un jour en retard n'est pas avancé (il serait sauté).
     */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void resetdayRameneAussiLePrelevement(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            InvoiceRegistry factures = InvoiceRegistry.get(e.serveur());
            LocalDate avant = factures.lastDebitDay();
            LocalDate aujourdhui = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(),
                    EconomyConfig.payHour());
            CommandSourceStack console = e.serveur().createCommandSourceStack();
            try {
                factures.setLastDebitDay(aujourdhui.plusDays(3));
                helper.assertTrue(e.serveur().getCommands().performPrefixedCommand(console, "eco salary resetday") == 1,
                        "resetday répond");
                helper.assertTrue(aujourdhui.minusDays(1).equals(factures.lastDebitDay()),
                        "jour du prélèvement ramené à la veille : " + factures.lastDebitDay());

                factures.setLastDebitDay(aujourdhui.minusDays(3));
                e.serveur().getCommands().performPrefixedCommand(console, "eco salary resetday");
                helper.assertTrue(aujourdhui.minusDays(3).equals(factures.lastDebitDay()),
                        "un jour en retard reste à clore : " + factures.lastDebitDay());

                InvoiceRegistry detache = InvoiceRegistry.load(new CompoundTag());
                detache.setLastDebitDay(J.plusDays(5));
                DailyDebit.rewindTo(detache, J);
                helper.assertTrue(J.minusDays(1).equals(detache.lastDebitDay()), "rewindTo : la veille");
                List<LocalDate> jours = new ArrayList<>();
                String genre = "resetday" + e.sfx;
                DailyDebit.registerIssuer(genre, (s, jour, n) -> jours.add(jour));
                long apresMinuit = EconomyCalendar.boundaryAfter(J, EconomyCalendar.configuredZone(),
                        EconomyConfig.payHour()) + MINUTE;
                helper.assertTrue(DailyDebit.closeDueDays(e.serveur(), detache, new Payroll(), apresMinuit) == 1
                        && jours.equals(List.of(J)), "après la remise, minuit clôt le jour, une fois : " + jours);
            } finally {
                factures.setLastDebitDay(avant);
            }
            helper.succeed();
        }
    }

    /** Une facture d'un genre pas encore inscrit reste due (jamais annulée) ; inscrit plus tard, il est prélevé. */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unGenreInscritApresLeDemarrageNAnnulePasSesFactures(GameTestHelper helper) {
        try (Essai e = new Essai(helper)) {
            JoueurDeTest debiteur = e.joueur("mg_d");
            PlayerCoins.receive(debiteur.joueur(), Coin.GOLD, 1L);
            long porte = CoinMath.valueOf(Coin.GOLD, 1L);
            InvoiceRegistry factures = InvoiceRegistry.load(new CompoundTag());
            String genre = "tardif" + e.sfx;
            String ref = PayeurDEssai.creancier();
            try {
                Invoice f = factures.issuePublic(genre, ref, new UUID(0L, 7L), "Tardif", debiteur.uuid(),
                        debiteur.joueur().getGameProfile().getName(), 30L, "essai", EconomyClock.now());
                DirectDebit.Outcome avant = DirectDebit.present(e.serveur(), factures, f, EconomyClock.now());
                helper.assertTrue(avant == DirectDebit.Outcome.NOT_DUE && statut(factures, f) == Status.DUE,
                        "genre non inscrit : la facture reste due, vu " + avant + " / " + statut(factures, f));
                helper.assertTrue(CoinMath.valueOf(PlayerCoins.held(debiteur.joueur())) == porte, "rien n'est pris");
                DebitPayees.register(genre, PayeurDEssai.INSTANCE);
                DirectDebit.Outcome apres = DirectDebit.present(e.serveur(), factures, f, EconomyClock.now());
                helper.assertTrue(apres == DirectDebit.Outcome.PAID_HELD && statut(factures, f) == Status.PAID,
                        "inscrit : prélevée, vu " + apres);
                helper.assertTrue(PayeurDEssai.recu(ref) == 30L
                        && CoinMath.valueOf(PlayerCoins.held(debiteur.joueur())) == porte - 30L, "30 pris, 30 reçus");
            } finally {
                PayeurDEssai.oublier(ref);
            }
            helper.succeed();
        }
    }
}
