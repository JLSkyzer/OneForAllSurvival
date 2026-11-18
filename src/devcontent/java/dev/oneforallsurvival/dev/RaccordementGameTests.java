package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.BreakerBlockEntity;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.MeterBlockEntity;
import dev.oneforallsurvival.grid.SubscriberNamePacket;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerPairing;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.machine.InlineEnergyBlockEntity;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le raccordement des compteurs et des disjoncteurs à un fournisseur (lot E6, tâche 3) : jumelage au testeur par un
 * ayant droit, accord du joueur inscrit (par la vraie commande {@code /power}), acceptation, comptage, coupure à
 * distance, droits, casse, résiliation. Les joueurs sont de vrais joueurs d'essai non opérateurs (sauf celui de
 * l'équipe) ; chaque essai a ses noms et range ses fournisseurs à la fin.
 *
 * <p>Disposition (aire close, sol en y = -1) : le poste en {@link #POSTE} ; le compteur en {@link #COMPTEUR}, sortie à
 * l'est, puis le disjoncteur en {@link #DISJONCTEUR} et la charge d'essai en {@link #CHARGE} (ou la charge directement
 * après le compteur). Le courant est poussé dans l'entrée (face ouest) du compteur.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class RaccordementGameTests {

    private static final String LOT_A = "raccord_a";
    private static final String LOT_B = "raccord_b";

    private static final BlockPos POSTE = new BlockPos(2, 0, 2);
    private static final BlockPos COMPTEUR = new BlockPos(2, 0, 5);
    private static final BlockPos DISJONCTEUR = new BlockPos(3, 0, 5);
    private static final BlockPos CHARGE = new BlockPos(4, 0, 5);
    private static final BlockPos AUTRE_COMPTEUR = new BlockPos(2, 0, 3);
    private static final Vec3 JOUEUR = new Vec3(5.5, 0, 2.5);

    private RaccordementGameTests() {}

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
        helper.setBlock(new BlockPos(4, 4, 4), Blocks.GLOWSTONE);
        for (Entity reste : helper.getLevel().getEntities((Entity) null, zone(helper), e -> !(e instanceof ServerPlayer))) {
            reste.discard();
        }
    }

    private static AABB zone(GameTestHelper helper) {
        BlockPos a = helper.absolutePos(new BlockPos(0, -1, 0));
        BlockPos b = helper.absolutePos(new BlockPos(8, 8, 8));
        return new AABB(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()).inflate(1.0D);
    }

    /** Joueurs, rôles, équipe, maire et fournisseurs d'un essai : tout est rendu, même si l'essai échoue. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final List<UUID> uuids = new ArrayList<>();
        private Optional<UUID> maireAvant;
        private boolean maireChange;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        PowerSuppliers registre() {
            return PowerSuppliers.get(serveur());
        }

        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            Vec3 ici = helper.absoluteVec(JOUEUR);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            uuids.add(j.uuid());
            RoleSavedData.get(serveur()).book().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            oublier(j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe : " + nom);
            return j;
        }

        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = joueur(nom);
            RoleSavedData.get(serveur()).book().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        JoueurDeTest maire(String nom) {
            JoueurDeTest j = joueur(nom);
            RoleBook book = RoleSavedData.get(serveur()).book();
            if (!maireChange) {
                maireAvant = book.mayor();
                maireChange = true;
            }
            book.appointMayor(j.uuid());
            return j;
        }

        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = joueur(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        void oublier(UUID fondateur) {
            registre().byFounder(fondateur).ifPresent(s -> registre().removeIfEmpty(s.id()));
        }

        @Override
        public void close() {
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            RoleBook book = RoleSavedData.get(serveur()).book();
            if (maireChange) {
                if (maireAvant.isPresent()) {
                    book.appointMayor(maireAvant.get());
                } else {
                    book.removeMayor();
                }
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            boite(helper);
            for (UUID uuid : uuids) {
                book.assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                oublier(uuid);
            }
        }
    }

    /** Un poste posé par {@code j} (comme en jeu, l'objet en main) puis fondé sous {@code nom}. */
    private static PowerSupplier fonder(GameTestHelper helper, JoueurDeTest j, String nom) {
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

    private static MeterBlockEntity compteur(GameTestHelper helper, BlockPos pos, String abonne) {
        helper.setBlock(pos, GridRegistry.METER.get().defaultBlockState().setValue(InlineEnergyBlock.FACING, Direction.EAST));
        helper.assertTrue(helper.getBlockEntity(pos) instanceof MeterBlockEntity, "pas de compteur en " + pos);
        MeterBlockEntity be = (MeterBlockEntity) helper.getBlockEntity(pos);
        be.setSubscriber(abonne);
        be.serial();
        return be;
    }

    private static BreakerBlockEntity disjoncteur(GameTestHelper helper, String abonne) {
        helper.setBlock(DISJONCTEUR, GridRegistry.BREAKER.get().defaultBlockState()
                .setValue(InlineEnergyBlock.FACING, Direction.EAST));
        helper.assertTrue(helper.getBlockEntity(DISJONCTEUR) instanceof BreakerBlockEntity, "pas de disjoncteur");
        BreakerBlockEntity be = (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
        be.setSubscriber(abonne);
        return be;
    }

    private static TestMachineBlockEntity charge(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, DevBlocks.testMachine());
        helper.assertTrue(helper.getBlockEntity(pos) instanceof TestMachineBlockEntity, "pas de charge en " + pos);
        return (TestMachineBlockEntity) helper.getBlockEntity(pos);
    }

    /** Pousse {@code fe} FE dans l'entrée (face ouest) ; renvoie ce qui est passé. */
    private static int pousser(InlineEnergyBlockEntity be, int fe, boolean simuler) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).resolve().orElse(null);
        if (cap == null) {
            throw new IllegalStateException("pas de capability sur l'entrée");
        }
        return cap.receiveEnergy(fe, simuler);
    }

    private static BlockHitResult tir(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    /** Le testeur {@code testeur} en main, clic droit sur {@code pos}, comme le jeu (onItemUseFirst). */
    private static InteractionResult tester(GameTestHelper helper, ServerPlayer p, ItemStack testeur, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND, testeur);
        return testeur.getItem().onItemUseFirst(testeur, new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND,
                testeur, tir(helper, pos)));
    }

    private static ItemStack testeur() {
        return new ItemStack(RepairRegistry.TESTER.get());
    }

    /** Clic droit main vide, par le jeu. */
    private static void clicDroit(GameTestHelper helper, ServerPlayer p, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.gameMode.useItemOn(p, p.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, tir(helper, pos));
    }

    private static int commande(GameTestHelper helper, JoueurDeTest j, String commande) {
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(
                j.joueur().createCommandSourceStack(), commande);
    }

    /** Fondateur, poste, compteur au nom du client : jumelé par le testeur (poste puis compteur). */
    private static PowerSupplier jumeler(GameTestHelper helper, Essai essai, JoueurDeTest fondateur, String nomFournisseur,
            MeterBlockEntity compteur) {
        PowerSupplier s = fonder(helper, fondateur, nomFournisseur);
        ItemStack t = testeur();
        helper.assertTrue(tester(helper, fondateur.joueur(), t, POSTE).consumesAction(), "testeur sur le poste");
        helper.assertTrue(PowerPairing.heldSupplier(t, helper.getLevel().getGameTime()).equals(Optional.of(s.id())),
                "le testeur retient le fournisseur");
        helper.assertTrue(tester(helper, fondateur.joueur(), t, compteur.getBlockPos().subtract(helper.absolutePos(BlockPos.ZERO)))
                .consumesAction(), "testeur sur le compteur");
        return s;
    }

    private static PowerClient client(Essai essai, MeterBlockEntity compteur) {
        return essai.registre().clientOf(compteur.serial()).orElseThrow(
                () -> new IllegalStateException("le compteur devait être jumelé"));
    }

    /** Jumelé, accord donné par la vraie commande, accepté : le courant passe. */
    private static PowerSupplier raccorder(GameTestHelper helper, Essai essai, JoueurDeTest fondateur, JoueurDeTest client,
            String nomFournisseur, MeterBlockEntity compteur) {
        PowerSupplier s = jumeler(helper, essai, fondateur, nomFournisseur, compteur);
        String code = essai.registre().codeOf(compteur.serial()).orElseThrow();
        helper.assertTrue(commande(helper, client, "power consent " + code) == 1, "accord par /power consent");
        helper.assertTrue(PowerPairing.accept(fondateur.joueur(), compteur.serial()) == PowerPairing.Outcome.DONE,
                "acceptation");
        return s;
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : jumelage, accord, acceptation, comptage
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCompteurNonJumeleNeLaissePasPasser(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            MeterBlockEntity c = compteur(helper, COMPTEUR, "Personne");
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            IEnergyStorage entree = c.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).resolve().orElseThrow();
            helper.assertTrue(!c.isOpen() && !entree.canReceive(), "un compteur nu est fermé");
            helper.assertTrue(pousser(c, 100, true) == 0 && pousser(c, 100, false) == 0, "rien ne passe");
            helper.assertTrue(m.energy().getEnergyStored() == 0 && c.totalTransferred() == 0, "rien n'est reçu ni compté");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void jumeleSansAccordDuClientRienNePasse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_sa_fond");
            JoueurDeTest client = essai.joueur("rc_sa_client");
            JoueurDeTest ingenieur = essai.ingenieur("rc_sa_ing");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "");
            // Un pseudo avec « _ » s'inscrit par le vrai paquet de l'écran.
            ingenieur.envoyer(new SubscriberNamePacket(helper.absolutePos(COMPTEUR), "rc_sa_client"));
            helper.assertTrue(c.subscriber().equals("rc_sa_client"), "« _ » admis à l'inscription : " + c.subscriber());
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            client.messagesRecus();
            PowerSupplier s = jumeler(helper, essai, fondateur, "Sans Accord", c);
            PowerClient pc = client(essai, c);
            helper.assertTrue(pc.state() == PowerClient.State.AWAITING_CONSENT && pc.player().equals(client.uuid())
                    && pc.supplierId().equals(s.id()), "client en attente de son accord : " + pc);
            helper.assertTrue(client.messagesRecus().contains(PowerPairing.MESSAGE + "request"),
                    "le client reçoit la demande cliquable");
            helper.assertTrue(essai.registre().codeOf(c.serial()).isPresent(), "un code d'accord existe");
            helper.assertTrue(pousser(c, 100, false) == 0 && m.energy().getEnergyStored() == 0, "rien ne passe");
            helper.assertTrue(client(essai, c).feToday() == 0, "rien n'est compté");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void accordeMaisNonAccepteRienNePasse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_an_fond");
            JoueurDeTest client = essai.joueur("rc_an_client");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_an_client");
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            jumeler(helper, essai, fondateur, "Accord Seul", c);
            String code = essai.registre().codeOf(c.serial()).orElseThrow();
            fondateur.messagesRecus();
            helper.assertTrue(commande(helper, client, "power consent " + code) == 1, "accord par /power consent");
            helper.assertTrue(client(essai, c).state() == PowerClient.State.AWAITING_ACCEPT, "attend le fournisseur");
            helper.assertTrue(fondateur.messagesRecus().contains(PowerPairing.MESSAGE + "founder_consent"),
                    "le fondateur est prévenu");
            helper.assertTrue(essai.registre().codeOf(c.serial()).isEmpty()
                    && commande(helper, client, "power consent " + code) == 0, "le code ne sert qu'une fois");
            helper.assertTrue(pousser(c, 100, false) == 0 && m.energy().getEnergyStored() == 0, "rien ne passe");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void accepteLeCourantPasseEtSeCompte(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_ac_fond");
            JoueurDeTest client = essai.joueur("rc_ac_client");
            JoueurDeTest citoyen = essai.joueur("rc_ac_cit");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_ac_client");
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            jumeler(helper, essai, fondateur, "Courant Compte", c);
            String code = essai.registre().codeOf(c.serial()).orElseThrow();
            commande(helper, client, "power consent " + code);
            helper.assertTrue(PowerPairing.accept(citoyen.joueur(), c.serial()) == PowerPairing.Outcome.NOT_ALLOWED,
                    "un citoyen n'accepte pas");
            client.messagesRecus();
            helper.assertTrue(PowerPairing.accept(fondateur.joueur(), c.serial()) == PowerPairing.Outcome.DONE, "acceptation");
            helper.assertTrue(client(essai, c).state() == PowerClient.State.ACTIVE && c.isOpen(), "actif, ouvert");
            helper.assertTrue(client.messagesRecus().contains(PowerPairing.MESSAGE + "accepted"), "le client est prévenu");
            helper.assertTrue(pousser(c, 70, true) == 70 && client(essai, c).feToday() == 0, "une simulation ne compte rien");
            int passe = pousser(c, 100, false) + pousser(c, 60, false);
            for (int i = 0; i < 20; i++) {
                passe += pousser(c, 100, false);
            }
            long recu = m.energy().getEnergyStored();
            long jour = client(essai, c).feToday();
            helper.assertTrue(recu == 1000 && passe == 1000, "la charge devait saturer : reçu " + recu + ", passé " + passe);
            helper.assertTrue(jour == recu, "consommation du jour " + jour + ", reçu par la charge " + recu);

            // Une copie de ses données posée ailleurs (pioche créative) : même numéro, mais ni passage ni détachement.
            MeterBlockEntity copie = compteur(helper, AUTRE_COMPTEUR, "");
            copie.load(c.saveWithoutMetadata());
            TestMachineBlockEntity m2 = charge(helper, AUTRE_COMPTEUR.east());
            helper.assertTrue(copie.serial().equals(c.serial()) && !copie.isOpen() && pousser(copie, 50, false) == 0
                    && m2.energy().getEnergyStored() == 0, "une copie ailleurs ne laisse rien passer");
            helper.setBlock(AUTRE_COMPTEUR, Blocks.AIR);
            helper.assertTrue(essai.registre().clientOf(c.serial()).isPresent() && c.isOpen(),
                    "la casse de la copie ne détache pas le vrai compteur");
            helper.succeed();
        }
    }

    /**
     * Le fournisseur inscrit le nom d'un autre joueur : celui-ci refuse, le client est retiré, rien ne passe ni ne se
     * compte. Un tiers ne peut pas répondre à sa place. Un nom qu'aucun joueur connu ne porte : jumelage refusé.
     */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void personneNEstFactureSansSonAccord(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_pf_fond");
            JoueurDeTest victime = essai.joueur("rc_pf_victime");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_pf_victime");
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            PowerSupplier s = jumeler(helper, essai, fondateur, "Facture Forcee", c);
            String code = essai.registre().codeOf(c.serial()).orElseThrow();
            helper.assertTrue(commande(helper, fondateur, "power consent " + code) == 0
                    && PowerPairing.consent(fondateur.joueur(), code) == PowerPairing.Outcome.NOT_YOURS,
                    "le fournisseur ne consent pas à la place du joueur");
            FakePlayer faux = FakePlayerFactory.get(helper.getLevel(), victime.joueur().getGameProfile());
            helper.assertTrue(PowerPairing.consent(faux, code) == PowerPairing.Outcome.NOT_YOURS
                    && PowerPairing.refuse(faux, code) == PowerPairing.Outcome.NOT_YOURS,
                    "un faux joueur au nom du débiteur ne répond pas à sa place");
            helper.assertTrue(client(essai, c).state() == PowerClient.State.AWAITING_CONSENT, "toujours en attente");
            helper.assertTrue(commande(helper, victime, "power refuse " + code) == 1, "refus par /power refuse");
            helper.assertTrue(essai.registre().clientOf(c.serial()).isEmpty(), "le client est retiré");
            helper.assertTrue(pousser(c, 100, false) == 0 && m.energy().getEnergyStored() == 0, "rien ne passe");
            helper.assertTrue(PowerPairing.consent(victime.joueur(), code) == PowerPairing.Outcome.UNKNOWN_CODE,
                    "un code refusé ne revit pas");

            // Un nom inconnu du serveur : refusé, sans requête réseau.
            MeterBlockEntity inconnu = compteur(helper, AUTRE_COMPTEUR, "Zz9Inconnu");
            helper.assertTrue(PowerPairing.pairMeter(fondateur.joueur(), s, inconnu) == PowerPairing.PairResult.UNKNOWN_PLAYER
                    && essai.registre().clientOf(inconnu.serial()).isEmpty(), "joueur inconnu : jumelage refusé");

            // Sans abonné : refusé.
            inconnu.setSubscriber("");
            helper.assertTrue(PowerPairing.pairMeter(fondateur.joueur(), s, inconnu) == PowerPairing.PairResult.NO_SUBSCRIBER,
                    "sans abonné : refusé");

            // Un joueur déconnecté : connu par le cache des profils (jumelé, en attente de son accord). Le serveur de
            // GameTest n'a pas de cache (Services vides) : il n'y est pas connu, le jumelage est refusé (côté sûr).
            victime.quitter();
            inconnu.setSubscriber("rc_pf_victime");
            PowerPairing.PairResult absent = PowerPairing.pairMeter(fondateur.joueur(), s, inconnu);
            if (helper.getLevel().getServer().getProfileCache() == null) {
                helper.assertTrue(absent == PowerPairing.PairResult.UNKNOWN_PLAYER, "sans cache, absent inconnu : " + absent);
            } else {
                helper.assertTrue(absent == PowerPairing.PairResult.PAIRED
                        && client(essai, inconnu).state() == PowerClient.State.AWAITING_CONSENT
                        && !inconnu.isOpen(), "joueur absent mais connu : en attente de son accord");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void seulUnAyantDroitJumelle(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_ad_fond");
            JoueurDeTest client = essai.joueur("rc_ad_client");
            JoueurDeTest citoyen = essai.joueur("rc_ad_cit");
            JoueurDeTest ingenieur = essai.ingenieur("rc_ad_ing");
            MeterBlockEntity premier = compteur(helper, COMPTEUR, "rc_ad_client");
            charge(helper, DISJONCTEUR);
            PowerSupplier s = raccorder(helper, essai, fondateur, client, "Ayants Droit", premier);
            MeterBlockEntity second = compteur(helper, AUTRE_COMPTEUR, "rc_ad_client");

            // Le citoyen et le client : le testeur ne retient pas le fournisseur, et le jumelage direct est refusé.
            for (JoueurDeTest j : List.of(citoyen, client)) {
                ItemStack t = testeur();
                tester(helper, j.joueur(), t, POSTE);
                helper.assertTrue(PowerPairing.heldSupplier(t, helper.getLevel().getGameTime()).isEmpty(),
                        j.joueur().getGameProfile().getName() + " : le testeur ne retient rien");
                helper.assertTrue(PowerPairing.pairMeter(j.joueur(), s, second) == PowerPairing.PairResult.NOT_ALLOWED
                        && essai.registre().clientOf(second.serial()).isEmpty(),
                        j.joueur().getGameProfile().getName() + " ne jumelle pas");
            }
            // Un testeur échu ne jumelle plus : il refait le relevé du lot 4.
            ItemStack echu = testeur();
            tester(helper, ingenieur.joueur(), echu, POSTE);
            echu.getTag().putLong(PowerPairing.TAG_HOLD_UNTIL, helper.getLevel().getGameTime() - 1);
            tester(helper, ingenieur.joueur(), echu, AUTRE_COMPTEUR);
            helper.assertTrue(essai.registre().clientOf(second.serial()).isEmpty(), "un testeur échu ne jumelle pas");

            // L'ingénieur, si.
            ItemStack t = testeur();
            tester(helper, ingenieur.joueur(), t, POSTE);
            tester(helper, ingenieur.joueur(), t, AUTRE_COMPTEUR);
            helper.assertTrue(essai.registre().clientOf(second.serial()).map(PowerClient::supplierId)
                    .equals(Optional.of(s.id())), "l'ingénieur jumelle");
            helper.assertTrue(PowerPairing.pairMeter(fondateur.joueur(), s, second) == PowerPairing.PairResult.ALREADY_PAIRED,
                    "un compteur jumelé ne se rejumelle pas");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : disjoncteur, coupure, droits, casse, règles du lot 4, résiliation
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leDisjoncteurSeJumelleAuMemeClient(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_dj_fond");
            JoueurDeTest client = essai.joueur("rc_dj_client");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_dj_client");
            BreakerBlockEntity d = disjoncteur(helper, "RC_DJ_CLIENT");
            TestMachineBlockEntity m = charge(helper, CHARGE);
            PowerSupplier s = raccorder(helper, essai, fondateur, client, "Disjoncteurs", c);
            ItemStack t = testeur();
            tester(helper, fondateur.joueur(), t, POSTE);
            tester(helper, fondateur.joueur(), t, DISJONCTEUR);
            helper.assertTrue(d.hasSerial() && client(essai, c).breakers().contains(d.serial()),
                    "le disjoncteur rejoint le client du même nom (sans casse)");
            helper.assertTrue(essai.registre().clientOfBreaker(d.serial()).map(PowerClient::meterSerial)
                    .equals(Optional.of(c.serial())), "index du disjoncteur");
            helper.assertTrue(pousser(c, 50, false) == 50 && m.energy().getEnergyStored() == 50, "compteur puis disjoncteur");
            helper.assertTrue(PowerPairing.pairBreaker(fondateur.joueur(), s, d) == PowerPairing.PairResult.ALREADY_PAIRED,
                    "déjà jumelé");
            // Une copie de ses données ailleurs, puis cassée : le vrai reste jumelé.
            helper.setBlock(AUTRE_COMPTEUR, GridRegistry.BREAKER.get().defaultBlockState());
            helper.getBlockEntity(AUTRE_COMPTEUR).load(d.saveWithoutMetadata());
            helper.setBlock(AUTRE_COMPTEUR, Blocks.AIR);
            helper.assertTrue(client(essai, c).breakers().contains(d.serial()),
                    "la casse d'une copie ne défait pas le jumelage du vrai disjoncteur");

            // Un disjoncteur au nom de personne chez ce fournisseur : refusé.
            helper.setBlock(DISJONCTEUR, Blocks.AIR);
            BreakerBlockEntity autre = disjoncteur(helper, "Quelquun");
            helper.assertTrue(client(essai, c).breakers().isEmpty(), "le disjoncteur retiré quitte son client");
            helper.assertTrue(PowerPairing.pairBreaker(fondateur.joueur(), s, autre) == PowerPairing.PairResult.NO_CLIENT,
                    "aucun client de ce nom : refusé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lePosteCoupeADistance(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_cd_fond");
            // Le client est ingénieur (métier ouvert à tous) : il n'est pas maître de sa propre coupure pour autant.
            JoueurDeTest client = essai.ingenieur("rc_cd_client");
            JoueurDeTest autreIngenieur = essai.ingenieur("rc_cd_ing");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_cd_client");
            BreakerBlockEntity d = disjoncteur(helper, "rc_cd_client");
            TestMachineBlockEntity m = charge(helper, CHARGE);
            raccorder(helper, essai, fondateur, client, "Coupure", c);
            helper.assertTrue(PowerPairing.setCut(fondateur.joueur(), c.serial(), true) == PowerPairing.Outcome.NO_BREAKER,
                    "sans disjoncteur jumelé, pas de coupure");
            ItemStack t = testeur();
            tester(helper, fondateur.joueur(), t, POSTE);
            tester(helper, fondateur.joueur(), t, DISJONCTEUR);
            helper.assertTrue(pousser(c, 40, false) == 40, "le courant passe avant la coupure");

            helper.assertTrue(PowerPairing.setCut(client.joueur(), c.serial(), true) == PowerPairing.Outcome.NOT_ALLOWED,
                    "le client ne se coupe pas lui-même par l'API");
            helper.assertTrue(PowerPairing.setCut(fondateur.joueur(), c.serial(), true) == PowerPairing.Outcome.DONE, "coupure");
            BlockState etat = helper.getBlockState(DISJONCTEUR);
            helper.assertTrue(!etat.getValue(BreakerBlock.CLOSED) && client(essai, c).cut(), "le disjoncteur s'ouvre");
            helper.assertTrue(pousser(c, 40, false) == 0 && m.energy().getEnergyStored() == 40, "rien ne passe");

            // Le client (ingénieur) ne le réenclenche pas, ni par le bloc ni par l'API, ni ne le casse.
            clicDroit(helper, client.joueur(), DISJONCTEUR);
            helper.assertTrue(!helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED), "le client ne réenclenche pas");
            helper.assertTrue(PowerPairing.setCut(client.joueur(), c.serial(), false) == PowerPairing.Outcome.NOT_ALLOWED
                    && client(essai, c).cut(), "le client ingénieur ne se rétablit pas");
            helper.assertTrue(helper.getBlockState(DISJONCTEUR).getDestroyProgress(client.joueur(), helper.getLevel(),
                    helper.absolutePos(DISJONCTEUR)) == 0.0F && !client.joueur().gameMode.destroyBlock(
                    helper.absolutePos(DISJONCTEUR)), "le client ingénieur ne casse pas son disjoncteur");
            // Même refermé de force (état du bloc), un client coupé ne passe pas.
            helper.setBlock(DISJONCTEUR, helper.getBlockState(DISJONCTEUR).setValue(BreakerBlock.CLOSED, true));
            BreakerBlockEntity force = (BreakerBlockEntity) helper.getBlockEntity(DISJONCTEUR);
            helper.assertTrue(!force.isOpen() && pousser(c, 40, false) == 0, "coupé à distance : fermé quoi qu'il arrive");
            helper.setBlock(DISJONCTEUR, helper.getBlockState(DISJONCTEUR).setValue(BreakerBlock.CLOSED, false));

            // Le fournisseur le réenclenche : le client est rétabli.
            clicDroit(helper, fondateur.joueur(), DISJONCTEUR);
            helper.assertTrue(helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED) && !client(essai, c).cut(),
                    "le fournisseur réenclenche et rétablit");
            helper.assertTrue(pousser(c, 40, false) == 40 && m.energy().getEnergyStored() == 80, "le courant repasse");

            // Un autre ingénieur (pas le client) peut rétablir.
            PowerPairing.setCut(fondateur.joueur(), c.serial(), true);
            helper.assertTrue(PowerPairing.setCut(autreIngenieur.joueur(), c.serial(), false) == PowerPairing.Outcome.DONE
                    && !client(essai, c).cut() && helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED),
                    "un ingénieur qui n'est pas le client rétablit");

            // Contournement : coupé, puis la charge collée directement à la sortie du compteur, sans disjoncteur.
            PowerPairing.setCut(fondateur.joueur(), c.serial(), true);
            TestMachineBlockEntity directe = charge(helper, DISJONCTEUR);
            helper.assertTrue(!c.isOpen() && pousser(c, 40, false) == 0 && directe.energy().getEnergyStored() == 0,
                    "coupé : rien ne passe même en contournant le disjoncteur");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unSansDroitNeTouchePasAuCompteurNiAuDisjoncteur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_sd_fond");
            JoueurDeTest client = essai.joueur("rc_sd_client");
            JoueurDeTest citoyen = essai.joueur("rc_sd_cit");
            JoueurDeTest ingenieur = essai.ingenieur("rc_sd_ing");
            JoueurDeTest maire = essai.maire("rc_sd_maire");
            JoueurDeTest chef = essai.equipe("rc_sd_chef");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_sd_client");
            disjoncteur(helper, "rc_sd_client");
            charge(helper, CHARGE);
            raccorder(helper, essai, fondateur, client, "Sans Droit", c);
            ItemStack t = testeur();
            tester(helper, fondateur.joueur(), t, POSTE);
            tester(helper, fondateur.joueur(), t, DISJONCTEUR);
            ServerLevel level = helper.getLevel();
            BlockPos absC = helper.absolutePos(COMPTEUR);
            BlockPos absD = helper.absolutePos(DISJONCTEUR);
            helper.assertTrue(helper.getBlockState(COMPTEUR).getDestroySpeed(level, absC) < 0
                    && helper.getBlockState(DISJONCTEUR).getDestroySpeed(level, absD) < 0
                    && helper.getBlockState(COMPTEUR).getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK
                    && helper.getBlockState(DISJONCTEUR).getPistonPushReaction()
                            == net.minecraft.world.level.material.PushReaction.BLOCK,
                    "dureté -1 et poussée bloquée : aucun automate ne les casse ni ne les emporte");
            helper.assertTrue(helper.getBlockState(COMPTEUR).getExplosionResistance(level, absC, null) >= 3600000.0F
                    && helper.getBlockState(DISJONCTEUR).getExplosionResistance(level, absD, null) >= 3600000.0F,
                    "jumelés : aucune explosion ne les emporte");

            // Client et citoyen : ni ouvrir, ni inscrire, ni basculer, ni casser (même en créatif).
            for (JoueurDeTest j : List.of(client, citoyen)) {
                String nom = j.joueur().getGameProfile().getName();
                j.ecransAbonneRecus();
                j.messagesRecus();
                clicDroit(helper, j.joueur(), COMPTEUR);
                helper.assertTrue(j.ecransAbonneRecus().isEmpty(), nom + " n'ouvre pas l'écran du compteur");
                List<String> lus = j.messagesRecus();
                String attendu = PowerPairing.MESSAGE + (j == client ? "touch_client" : "touch_denied");
                helper.assertTrue(lus.contains(attendu), nom + " lit pourquoi : " + lus);
                j.envoyer(new SubscriberNamePacket(absC, "Pirate"));
                helper.assertTrue(c.subscriber().equals("rc_sd_client"), nom + " n'inscrit pas");
                clicDroit(helper, j.joueur(), DISJONCTEUR);
                helper.assertTrue(helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED), nom + " ne bascule pas");
                helper.assertTrue(helper.getBlockState(COMPTEUR).getDestroyProgress(j.joueur(), level, absC) == 0.0F
                        && helper.getBlockState(DISJONCTEUR).getDestroyProgress(j.joueur(), level, absD) == 0.0F,
                        nom + " : pas de progression");
                helper.assertTrue(!j.joueur().gameMode.destroyBlock(absC) && !j.joueur().gameMode.destroyBlock(absD),
                        nom + " ne casse pas");
                j.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                helper.assertTrue(!j.joueur().gameMode.destroyBlock(absC) && !j.joueur().gameMode.destroyBlock(absD),
                        nom + " en créatif ne casse pas");
                j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            }
            FakePlayer faux = FakePlayerFactory.getMinecraft(level);
            helper.assertTrue(!faux.gameMode.destroyBlock(absC) && !faux.gameMode.destroyBlock(absD),
                    "un faux joueur ne casse pas");
            clicDroit(helper, faux, DISJONCTEUR);
            helper.assertTrue(helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED), "un faux joueur ne bascule pas");
            helper.assertTrue(c.isOpen(), "rien n'a bougé");

            // Fournisseur, ingénieur, maire, équipe : basculer, progression ; pas de réinscription d'un compteur jumelé.
            for (JoueurDeTest j : List.of(fondateur, ingenieur, maire, chef)) {
                String nom = j.joueur().getGameProfile().getName();
                j.ecransAbonneRecus();
                j.messagesRecus();
                clicDroit(helper, j.joueur(), COMPTEUR);
                helper.assertTrue(j.ecransAbonneRecus().isEmpty() && j.messagesRecus()
                        .contains(PowerPairing.MESSAGE + "reinscribe_paired"), nom + " : détacher d'abord");
                helper.assertTrue(helper.getBlockState(COMPTEUR).getDestroyProgress(j.joueur(), level, absC) > 0.0F,
                        nom + " : progression");
                clicDroit(helper, j.joueur(), DISJONCTEUR);
                helper.assertTrue(!helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED), nom + " ouvre");
                clicDroit(helper, j.joueur(), DISJONCTEUR);
                helper.assertTrue(helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED), nom + " referme");
            }
            // Réinscrire un compteur jumelé est refusé, même au fondateur.
            fondateur.messagesRecus();
            fondateur.envoyer(new SubscriberNamePacket(absC, "Client Deux"));
            helper.assertTrue(c.subscriber().equals("rc_sd_client") && fondateur.messagesRecus()
                    .contains(PowerPairing.MESSAGE + "reinscribe_paired"), "réinscription refusée : " + c.subscriber());
            // Le maire démonte le disjoncteur, l'ingénieur le compteur.
            helper.assertTrue(maire.joueur().gameMode.destroyBlock(absD), "le maire démonte le disjoncteur");
            helper.assertTrue(ingenieur.joueur().gameMode.destroyBlock(absC), "l'ingénieur démonte le compteur");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unCompteurCasseEstDetache(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_cs_fond");
            JoueurDeTest client = essai.joueur("rc_cs_client");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_cs_client");
            charge(helper, DISJONCTEUR);
            PowerSupplier s = raccorder(helper, essai, fondateur, client, "Casse", c);
            UUID numero = c.serial();
            // Un autre compteur du même client, sans consommation, démonté : sa journée reste due (abonnement).
            MeterBlockEntity vide = compteur(helper, AUTRE_COMPTEUR, "rc_cs_client");
            FournisseurDEssai.raccorder(helper.getLevel().getServer(), vide, client.uuid(), "rc_cs_client");
            helper.setBlock(AUTRE_COMPTEUR, Blocks.AIR);
            helper.assertTrue(essai.registre().detached().stream().anyMatch(d -> d.meterSerial().equals(vide.serial())
                    && d.feToday() == 0), "détaché sans consommation : gardé pour la clôture");
            helper.assertTrue(pousser(c, 90, false) == 90 && client(essai, c).feToday() == 90, "90 FE comptés");
            client.messagesRecus();
            fondateur.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            helper.assertTrue(fondateur.joueur().gameMode.destroyBlock(helper.absolutePos(COMPTEUR)), "le fondateur démonte");
            helper.assertTrue(essai.registre().clientOf(numero).isEmpty(), "détaché");
            helper.assertTrue(essai.registre().detached().stream().anyMatch(d -> d.meterSerial().equals(numero)
                    && d.feToday() == 90 && d.supplierId().equals(s.id())), "la consommation du jour reste due");
            helper.assertTrue(client.messagesRecus().contains(PowerPairing.MESSAGE + "detached"), "le client est prévenu");
            ItemStack objet = null;
            for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper))) {
                if (e.getItem().is(GridRegistry.METER_ITEM.get())) {
                    objet = e.getItem().copy();
                    e.discard();
                }
            }
            helper.assertTrue(objet != null, "le compteur lâche son objet");

            // Reposé ailleurs : même numéro, mais plus jumelé ; rien ne passe.
            ServerPlayer p = fondateur.joueur();
            p.setYRot(-90f); // regarde vers l'est
            p.setItemInHand(InteractionHand.MAIN_HAND, objet);
            BlockPos sol = helper.absolutePos(AUTRE_COMPTEUR.below());
            InteractionResult r = objet.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, objet,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
            helper.assertTrue(r.consumesAction(), "repose : " + r);
            MeterBlockEntity repose = (MeterBlockEntity) helper.getBlockEntity(AUTRE_COMPTEUR);
            charge(helper, AUTRE_COMPTEUR.east());
            helper.assertTrue(numero.equals(repose.serial()), "le numéro suit l'objet");
            helper.assertTrue(!repose.isOpen() && pousser(repose, 50, false) == 0,
                    "reposé ailleurs, il ne facture plus son ancien client");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unCompteurNonJumeleGardeLesReglesDuLot4(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest citoyen = essai.joueur("rc_l4_cit");
            JoueurDeTest ingenieur = essai.ingenieur("rc_l4_ing");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "Rcl Quatre");
            disjoncteur(helper, "Rcl Quatre");
            ServerLevel level = helper.getLevel();
            BlockPos absC = helper.absolutePos(COMPTEUR);
            citoyen.ecransAbonneRecus();
            citoyen.messagesRecus();
            clicDroit(helper, citoyen.joueur(), COMPTEUR);
            helper.assertTrue(citoyen.ecransAbonneRecus().isEmpty() && citoyen.messagesRecus()
                    .contains(MeterBlockEntity.MESSAGE + "owner"), "citoyen : le nom de l'abonné, pas d'écran");
            ingenieur.ecransAbonneRecus();
            clicDroit(helper, ingenieur.joueur(), COMPTEUR);
            helper.assertTrue(ingenieur.ecransAbonneRecus().size() == 1, "l'ingénieur ouvre l'écran");
            clicDroit(helper, citoyen.joueur(), DISJONCTEUR);
            helper.assertTrue(!helper.getBlockState(DISJONCTEUR).getValue(BreakerBlock.CLOSED),
                    "tout le monde bascule un disjoncteur non jumelé");
            helper.assertTrue(helper.getBlockState(COMPTEUR).getExplosionResistance(level, absC, null) < 100.0F,
                    "résistance ordinaire");
            float attendu = citoyen.joueur().getDigSpeed(helper.getBlockState(COMPTEUR), absC) / 3.0F / 100.0F;
            helper.assertTrue(Math.abs(helper.getBlockState(COMPTEUR).getDestroyProgress(citoyen.joueur(), level, absC)
                    - attendu) < 1.0E-6F, "progression d'un bloc de dureté 3, à mains nues");
            helper.assertTrue(helper.getBlockState(COMPTEUR).getDestroyProgress(citoyen.joueur(), level, absC) > 0.0F
                    && citoyen.joueur().gameMode.destroyBlock(absC), "un citoyen casse un compteur non jumelé");
            helper.assertTrue(!c.hasSerial() || essai.registre().clientOf(c.serial()).isEmpty(), "jamais jumelé");
            helper.succeed();
        }
    }

    /** Avec la tâche 4 : ici, l'état de résiliation, et le courant qui passe encore jusqu'à la dernière facture. */
    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laResiliationCoupeApresLaDerniereFacture(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("rc_rs_fond");
            JoueurDeTest client = essai.joueur("rc_rs_client");
            MeterBlockEntity c = compteur(helper, COMPTEUR, "rc_rs_client");
            TestMachineBlockEntity m = charge(helper, DISJONCTEUR);
            raccorder(helper, essai, fondateur, client, "Resiliation", c);
            helper.assertTrue(commande(helper, essai.joueur("rc_rs_autre"), "power resign") == 0,
                    "un joueur sans raccordement ne résilie rien");
            fondateur.messagesRecus();
            helper.assertTrue(commande(helper, client, "power resign") == 1, "/power resign");
            helper.assertTrue(client(essai, c).state() == PowerClient.State.RESIGNING, "en résiliation");
            helper.assertTrue(fondateur.messagesRecus().contains(PowerPairing.MESSAGE + "founder_resign"),
                    "le fondateur est prévenu");
            helper.assertTrue(pousser(c, 30, false) == 30 && m.energy().getEnergyStored() == 30
                    && client(essai, c).feToday() == 30, "le courant passe encore et se compte");
            helper.assertTrue(commande(helper, client, "power list") == 1, "/power list montre le raccordement");
            helper.succeed();
        }
    }
}
