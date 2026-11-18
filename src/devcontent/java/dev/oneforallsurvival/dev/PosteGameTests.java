package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.OrderResult;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinAccountMenu;
import dev.oneforallsurvival.economy.CoinActions.Result;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.MoneyStorageRules;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerStationBlock;
import dev.oneforallsurvival.grid.supplier.PowerStationBlockEntity;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.grid.supplier.PowerTillMenu;
import dev.oneforallsurvival.machine.InlineEnergyBlock;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le poste de fournisseur (lot E6, tâche 2) : fabrication réservée à l'établi, fondation (nom, un par joueur), comptage
 * de ce qui sort, droits (ouvrir, casser, retirer de la caisse), caisse gardée par le serveur, casse et repose,
 * persistance du registre. Les joueurs sont de vrais joueurs non opérateurs (sauf celui de l'équipe, opérateur le temps
 * de l'essai) ; chaque essai a ses noms et range ses fournisseurs à la fin.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class PosteGameTests {

    private static final String LOT_A = "poste_a";
    private static final String LOT_B = "poste_b";

    /** Où les postes se posent (case d'air ; le sol est un bloc plus bas). */
    private static final BlockPos A = new BlockPos(2, 0, 2);
    private static final BlockPos B = new BlockPos(5, 0, 2);
    private static final BlockPos C = new BlockPos(2, 0, 5);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    private PosteGameTests() {}

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    /** Une aire close : sol, pourtour, plafond, lumière, vidée de tout ce qui n'est pas un joueur. */
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

    /** Ce qu'un essai a ouvert : joueurs, équipe, maire, fournisseurs ; tout est rendu même si l'essai échoue. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<com.mojang.authlib.GameProfile> equipe = new ArrayList<>();
        private final List<UUID> roles = new ArrayList<>();
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

        /** Un joueur de survie, citoyen, au milieu de l'aire, sans fournisseur d'un passage précédent. */
        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            Vec3 ici = helper.absoluteVec(JOUEUR);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            roles.add(j.uuid());
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

        /** Un joueur de l'équipe (niveau 2) : opérateur, jusqu'à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = joueur(nom);
            // Le niveau 4 est écrit à la main : le serveur d'essai donne 0 aux opérateurs qu'il crée par op().
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        /**
         * Retire le fournisseur de ce joueur, caisse vidée d'abord. Exception voulue à « ni créé ni détruit », propre aux
         * essais : leurs pièces sont posées dans la caisse sans source et en sortent sans destinataire.
         */
        void oublier(UUID fondateur) {
            PowerSuppliers registre = registre();
            registre.byFounder(fondateur).ifPresent(s -> {
                for (Coin coin : Coin.values()) {
                    registre.withdraw(s.id(), coin, registre.till(s.id()).getOrDefault(coin, 0L));
                }
                registre.removeIfEmpty(s.id());
            });
        }

        @Override
        public void close() {
            for (com.mojang.authlib.GameProfile profil : equipe) {
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
            // Les blocs d'abord (leur retrait efface l'emplacement noté), puis les fournisseurs.
            boite(helper);
            for (UUID uuid : roles) {
                book.assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                oublier(uuid);
            }
        }
    }

    private static ItemStack objetNeuf() {
        return new ItemStack(GridRegistry.POWER_STATION_ITEM.get());
    }

    /** Pose comme le ferait le joueur : l'objet en main, un clic sur le sol sous {@code pos}. */
    private static InteractionResult poser(GameTestHelper helper, ServerPlayer p, BlockPos pos, ItemStack pile) {
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    private static PowerStationBlockEntity poste(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getBlockState(pos).is(GridRegistry.POWER_STATION.get())
                && helper.getBlockEntity(pos) instanceof PowerStationBlockEntity, "pas de poste en " + pos);
        return (PowerStationBlockEntity) helper.getBlockEntity(pos);
    }

    /** Un poste posé par {@code j} en {@code pos}, puis fondé sous {@code nom} ; le fournisseur créé. */
    private static PowerSupplier fonder(GameTestHelper helper, JoueurDeTest j, BlockPos pos, String nom) {
        InteractionResult r = poser(helper, j.joueur(), pos, objetNeuf());
        helper.assertTrue(r.consumesAction(), "le poste devait se poser : " + r);
        poste(helper, pos);
        Optional<PowerSuppliers.Result> fonde = PowerStationBlock.found(j.joueur(), helper.absolutePos(pos), nom);
        helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
        return fonde.get().supplier();
    }

    /** Un clic droit réel, par le jeu, main vide. */
    private static void clicDroit(GameTestHelper helper, ServerPlayer p, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    /** La face d'entrée du poste (l'arrière) et la case de sortie (devant). */
    private static Direction sortie(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(InlineEnergyBlock.FACING);
    }

    /** Pousse {@code fe} FE dans l'entrée du poste ; renvoie ce qui est passé. */
    private static int pousser(PowerStationBlockEntity be, Direction sortie, int fe, boolean simuler) {
        IEnergyStorage cap = be.getCapability(ForgeCapabilities.ENERGY, sortie.getOpposite()).resolve().orElse(null);
        if (cap == null) {
            throw new IllegalStateException("pas de capability sur la face d'entrée du poste");
        }
        return cap.receiveEnergy(fe, simuler);
    }

    private static TestMachineBlockEntity charge(GameTestHelper helper, BlockPos poste) {
        BlockPos pos = poste.relative(sortie(helper, poste));
        helper.setBlock(pos, DevBlocks.testMachine());
        helper.assertTrue(helper.getBlockEntity(pos) instanceof TestMachineBlockEntity, "pas de machine d'essai");
        return (TestMachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper));
    }

    private static long valeurPortee(ServerPlayer p) {
        return CoinMath.valueOf(PlayerCoins.held(p));
    }

    private static long valeurCaisse(PowerSuppliers registre, String id) {
        return CoinMath.valueOf(registre.till(id));
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : fabrication, fondation, comptage
    // ---------------------------------------------------------------------------------------------

    /**
     * La recette du poste (de table, conditionnée à Create) n'est pas sur la table : le catalogue l'a sortie du
     * RecipeManager et la range à l'établi, réservé à l'ingénieur. Sans le pack (pas de Create), la recette n'existe pas
     * du tout ; le tag {@code table_only} et le refus de commande au citoyen se vérifient dans les deux cas.
     */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lePosteNeSeFabriqueQuALEtabli(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            ResourceLocation id = new ResourceLocation("oneforallsurvival", "power_station");
            helper.assertTrue(!objetNeuf().is(BenchCatalog.TABLE_ONLY), "le poste ne doit pas être dans table_only");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isEmpty(),
                    "la recette du poste ne doit pas rester sur la table");
            boolean create = ModList.get().isLoaded("create");
            var aLEtabli = BenchCatalog.current().byId(id);
            if (create) {
                helper.assertTrue(aLEtabli.isPresent() && aLEtabli.get().result().is(GridRegistry.POWER_STATION_ITEM.get()),
                        "avec Create, la recette du poste doit être à l'établi");
            } else {
                helper.assertTrue(aLEtabli.isEmpty(), "sans Create, la recette n'existe pas");
            }
            JoueurDeTest citoyen = essai.joueur("po_fab_citoyen");
            JoueurDeTest ingenieur = essai.ingenieur("po_fab_ingenieur");
            BlockPos etabli = helper.absolutePos(A);
            helper.assertTrue(BenchOrders.order(citoyen.joueur(), id, 1, etabli) == OrderResult.NOT_ENGINEER,
                    "un citoyen ne commande pas le poste");
            OrderResult parIngenieur = BenchOrders.order(ingenieur.joueur(), id, 1, etabli);
            helper.assertTrue(parIngenieur == (create ? OrderResult.MISSING_INGREDIENTS : OrderResult.UNKNOWN_RECIPE),
                    "l'ingénieur passe le contrôle du rôle (sans ingrédients) : " + parIngenieur);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void poserFondeLeFournisseur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_fonde_a");
            JoueurDeTest autre = essai.joueur("po_fonde_b");
            PowerSuppliers registre = essai.registre();
            ServerLevel level = helper.getLevel();

            InteractionResult r = poser(helper, fondateur.joueur(), A, objetNeuf());
            helper.assertTrue(r.consumesAction(), "le poste devait se poser : " + r);
            PowerStationBlockEntity be = poste(helper, A);
            helper.assertTrue(fondateur.uuid().equals(be.founder()) && be.supplierId() == null && !be.isOpen(),
                    "un poste neuf appartient à qui l'a posé, sans fournisseur");
            BlockPos abs = helper.absolutePos(A);

            // Noms refusés, et un autre joueur ne fonde pas le poste d'un autre.
            helper.assertTrue(PowerStationBlock.found(fondateur.joueur(), abs, "   ").map(PowerSuppliers.Result::status)
                    .equals(Optional.of(PowerSuppliers.Status.BAD_NAME)), "nom blanc");
            helper.assertTrue(PowerStationBlock.found(fondateur.joueur(), abs, "x".repeat(25)).map(PowerSuppliers.Result::status)
                    .equals(Optional.of(PowerSuppliers.Status.BAD_NAME)), "nom de 25 caractères");
            helper.assertTrue(PowerStationBlock.found(fondateur.joueur(), abs, "Volta§cRouge").map(PowerSuppliers.Result::status)
                    .equals(Optional.of(PowerSuppliers.Status.BAD_NAME)), "signe de mise en forme");
            helper.assertTrue(PowerStationBlock.found(autre.joueur(), abs, "Usurpateur").isEmpty(),
                    "un citoyen ne fonde pas le poste d'un autre");
            helper.assertTrue(registre.byFounder(autre.uuid()).isEmpty() && registre.byName("Usurpateur").isEmpty(),
                    "rien ne devait être fondé");

            // Le fondateur le nomme : le fournisseur existe, le poste est le sien.
            Optional<PowerSuppliers.Result> fonde = PowerStationBlock.found(fondateur.joueur(), abs, "  Volta Énergie ");
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation : " + fonde);
            PowerSupplier volta = fonde.get().supplier();
            helper.assertTrue(volta.name().equals("Volta Énergie") && volta.founder().equals(fondateur.uuid()),
                    "nom et fondateur : " + volta);
            helper.assertTrue(volta.id().equals(be.supplierId()) && be.isOpen(), "le poste porte son fournisseur");
            helper.assertTrue(registre.byId(volta.id()).map(PowerSupplier::station)
                    .equals(Optional.of(GlobalPos.of(level.dimension(), abs))), "le poste est noté");
            helper.assertTrue(registre.byName("VOLTA énergie").isPresent(), "le nom se cherche sans casse");
            helper.assertTrue(PowerStationBlock.found(fondateur.joueur(), abs, "Encore").isEmpty(),
                    "un poste fondé ne se refonde pas");

            // Un fournisseur par joueur ; un nom pris (sans casse) est refusé.
            helper.assertTrue(registre.found(fondateur.uuid(), "po_fonde_a", "Second").status()
                    == PowerSuppliers.Status.ALREADY_HAS_ONE, "un seul fournisseur par joueur");
            helper.assertTrue(poser(helper, autre.joueur(), B, objetNeuf()).consumesAction(), "le second poste se pose");
            helper.assertTrue(PowerStationBlock.found(autre.joueur(), helper.absolutePos(B), "volta ÉNERGIE")
                    .map(PowerSuppliers.Result::status).equals(Optional.of(PowerSuppliers.Status.NAME_TAKEN)),
                    "nom déjà pris");
            helper.assertTrue(PowerStationBlock.found(autre.joueur(), helper.absolutePos(B), "Ampère")
                    .map(PowerSuppliers.Result::founded).orElse(false), "un autre nom passe");

            // Un second poste du même fournisseur est refusé, avec un message.
            fondateur.messagesRecus();
            InteractionResult second = poser(helper, fondateur.joueur(), C, objetNeuf());
            helper.assertTrue(!second.consumesAction() && helper.getBlockState(C).isAir(),
                    "un second poste du même fournisseur ne se pose pas : " + second);
            helper.assertTrue(fondateur.messagesRecus().contains(PowerStationBlock.MESSAGE + "has_station"),
                    "message de refus attendu");
            helper.succeed();
        }
    }

    /** Source d'essai (les FE poussés dans l'entrée) → poste → charge : la sortie du jour est ce que la charge a reçu. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lePosteCompteCeQuiSort(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_compte");
            PowerSupplier s = fonder(helper, fondateur, A, "Compte Juste");
            PowerSuppliers registre = essai.registre();
            PowerStationBlockEntity be = poste(helper, A);
            Direction sortie = sortie(helper, A);
            TestMachineBlockEntity machine = charge(helper, A);

            helper.assertTrue(pousser(be, sortie, 70, true) == 70, "une simulation dit ce qui passerait");
            helper.assertTrue(registre.byId(s.id()).orElseThrow().outToday() == 0L, "une simulation ne compte rien");
            int passe = pousser(be, sortie, 100, false) + pousser(be, sortie, 60, false);
            helper.assertTrue(passe == 160, "retour : " + passe);
            // La charge sature (1 000 FE) : seul ce qu'elle accepte est compté.
            for (int i = 0; i < 20; i++) {
                passe += pousser(be, sortie, 100, false);
            }
            long recu = machine.energy().getEnergyStored();
            long sorti = registre.byId(s.id()).orElseThrow().outToday();
            helper.assertTrue(recu == 1000 && passe == 1000, "la charge devait saturer : reçu " + recu + ", passé " + passe);
            helper.assertTrue(sorti == recu, "sortie du jour " + sorti + ", reçu par la charge " + recu);
            helper.assertTrue(be.totalTransferred() == recu, "index du poste : " + be.totalTransferred());
            helper.succeed();
        }
    }

    /** Un poste neuf (sans fournisseur) et la copie d'un poste fondé ailleurs que son emplacement noté : rien ne passe. */
    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansFournisseurLePosteNeLaissePasser(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest poseur = essai.joueur("po_sans");
            helper.assertTrue(poser(helper, poseur.joueur(), A, objetNeuf()).consumesAction(), "pose");
            PowerStationBlockEntity neuf = poste(helper, A);
            TestMachineBlockEntity machine = charge(helper, A);
            Direction sortie = sortie(helper, A);
            IEnergyStorage entree = neuf.getCapability(ForgeCapabilities.ENERGY, sortie.getOpposite()).resolve().orElseThrow();
            helper.assertTrue(!entree.canReceive() && pousser(neuf, sortie, 100, false) == 0
                    && machine.energy().getEnergyStored() == 0, "un poste sans fournisseur ne laisse rien passer");

            // Une copie (par exemple un /setblock avec données) d'un poste fondé : pas son poste noté, rien ne passe.
            JoueurDeTest fondateur = essai.joueur("po_sans_vrai");
            PowerSupplier s = fonder(helper, fondateur, B, "Original");
            helper.setBlock(C, GridRegistry.POWER_STATION.get().defaultBlockState()
                    .setValue(InlineEnergyBlock.FACING, Direction.EAST));
            PowerStationBlockEntity copie = poste(helper, C);
            CompoundTag donnees = new CompoundTag();
            donnees.putString("supplier", s.id());
            copie.load(donnees);
            helper.assertTrue(s.id().equals(copie.supplierId()), "la copie porte l'identifiant");
            TestMachineBlockEntity machineCopie = charge(helper, C);
            helper.assertTrue(!copie.isOpen() && pousser(copie, Direction.EAST, 100, false) == 0
                    && machineCopie.energy().getEnergyStored() == 0, "la copie ne laisse rien passer");
            helper.assertTrue(essai.registre().byId(s.id()).orElseThrow().outToday() == 0L, "rien n'est compté");

            // Un objet neuf qui porterait l'identifiant dans ses données de bloc (pioche créative) ne le transmet pas.
            ItemStack trafique = objetNeuf();
            CompoundTag donneesBloc = new CompoundTag();
            donneesBloc.putString("supplier", s.id());
            trafique.getOrCreateTag().put("BlockEntityTag", donneesBloc);
            BlockPos e = new BlockPos(5, 0, 4);
            helper.assertTrue(poser(helper, poseur.joueur(), e, trafique).consumesAction(), "pose de l'objet trafiqué");
            helper.assertTrue(poste(helper, e).supplierId() == null && poseur.uuid().equals(poste(helper, e).founder()),
                    "un poste neuf ne garde aucun identifiant de l'objet : " + poste(helper, e).supplierId());

            // Un poste sans poseur noté (/setblock, structure) : ni un citoyen ni son voisin ne le fondent ; un ingénieur oui.
            BlockPos d = new BlockPos(5, 0, 6);
            helper.setBlock(d, GridRegistry.POWER_STATION.get().defaultBlockState());
            helper.assertTrue(poste(helper, d).founder() == null, "poste sans poseur");
            JoueurDeTest ingenieur = essai.ingenieur("po_sans_ing");
            BlockPos absD = helper.absolutePos(d);
            helper.assertTrue(PowerStationBlock.found(poseur.joueur(), absD, "Sans Maitre").isEmpty(),
                    "un citoyen ne fonde pas un poste sans poseur");
            Optional<PowerSuppliers.Result> parIngenieur = PowerStationBlock.found(ingenieur.joueur(), absD, "Poste Trouvé");
            helper.assertTrue(parIngenieur.map(PowerSuppliers.Result::founded).orElse(false)
                    && parIngenieur.get().supplier().founder().equals(ingenieur.uuid()),
                    "l'ingénieur le fonde, à son nom : " + parIngenieur);
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : droits, caisse, casse et repose, persistance
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void unSansDroitNeTouchePasAuPoste(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_droit_fond");
            JoueurDeTest citoyen = essai.joueur("po_droit_cit");
            JoueurDeTest ingenieur = essai.ingenieur("po_droit_ing");
            JoueurDeTest maire = essai.maire("po_droit_maire");
            JoueurDeTest chef = essai.equipe("po_droit_chef");
            PowerSupplier s = fonder(helper, fondateur, A, "Droits Gardés");
            PowerSuppliers registre = essai.registre();
            registre.deposit(s.id(), Coin.SILVER, 4);
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroySpeed(level, abs) < 0, "dureté réservée (-1)");
            helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE)
                    && etat.getPistonPushReaction() == PushReaction.BLOCK
                    && etat.getBlock().getExplosionResistance() >= 3600000.0F, "Wither, dragon, piston, explosion");

            // Le citoyen : ni ouvrir, ni progression, ni casse (même en créatif), ni pose d'un faux joueur.
            citoyen.messagesRecus();
            clicDroit(helper, citoyen.joueur(), A);
            helper.assertTrue(!(citoyen.joueur().containerMenu instanceof PowerTillMenu), "le citoyen n'ouvre pas");
            helper.assertTrue(citoyen.messagesRecus().contains(PowerStationBlock.MESSAGE + "of"), "il lit à qui est le poste");
            helper.assertTrue(etat.getDestroyProgress(citoyen.joueur(), level, abs) == 0.0F, "citoyen : pas de progression");
            helper.assertTrue(!citoyen.joueur().gameMode.destroyBlock(abs), "le citoyen ne casse pas");
            citoyen.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(!citoyen.joueur().gameMode.destroyBlock(abs) && poste(helper, A).isOpen(),
                    "le citoyen en créatif ne casse pas (filet du bris)");
            FakePlayer faux = FakePlayerFactory.getMinecraft(level);
            helper.assertTrue(etat.getDestroyProgress(faux, level, abs) == 0.0F && !faux.gameMode.destroyBlock(abs)
                    && poste(helper, A).isOpen(), "un faux joueur ne casse pas");
            clicDroit(helper, faux, A);
            helper.assertTrue(!(faux.containerMenu instanceof PowerTillMenu), "un faux joueur n'ouvre pas");
            helper.assertTrue(!poser(helper, faux, B, objetNeuf()).consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas");

            // Les ayants droit ouvrent ; seuls le fondateur et l'équipe peuvent retirer.
            for (JoueurDeTest j : List.of(fondateur, ingenieur, maire, chef)) {
                clicDroit(helper, j.joueur(), A);
                helper.assertTrue(j.joueur().containerMenu instanceof PowerTillMenu,
                        j.joueur().getGameProfile().getName() + " devait ouvrir la caisse");
                helper.assertTrue(etat.getDestroyProgress(j.joueur(), level, abs) > 0.0F,
                        j.joueur().getGameProfile().getName() + " : progression de casse");
            }
            for (JoueurDeTest j : List.of(ingenieur, maire)) {
                PowerTillMenu menu = (PowerTillMenu) j.joueur().containerMenu;
                helper.assertTrue(!menu.mayTake(), "l'écran grise le retrait");
                Result r = menu.apply(j.joueur(), PowerTillMenu.button(CoinRequest.withdraw(Coin.SILVER, 1)));
                helper.assertTrue(r == Result.NOT_ALLOWED, j.joueur().getGameProfile().getName() + " ne retire pas : " + r);
                r = menu.apply(j.joueur(), PowerTillMenu.button(CoinRequest.breakCoin(Coin.SILVER, false)));
                helper.assertTrue(r == Result.NOT_ALLOWED, "ni ne fait le change : " + r);
            }
            helper.assertTrue(registre.till(s.id()).getOrDefault(Coin.SILVER, 0L) == 4L, "la caisse est intacte");
            PowerTillMenu menuChef = (PowerTillMenu) chef.joueur().containerMenu;
            helper.assertTrue(menuChef.mayTake() && ((PowerTillMenu) fondateur.joueur().containerMenu).mayTake(),
                    "fondateur et équipe retirent");

            // Un ayant droit (ici l'ingénieur) le démonte : l'objet garde le fournisseur, la caisse reste au serveur.
            ingenieur.joueur().closeContainer();
            helper.assertTrue(ingenieur.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                    "l'ingénieur démonte le poste");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1 && PowerStationBlock.supplierOf(lache.get(0).getItem())
                    .equals(Optional.of(s.id())), "l'objet tombé porte le fournisseur : " + lache);
            helper.assertTrue(registre.till(s.id()).getOrDefault(Coin.SILVER, 0L) == 4L, "la caisse reste");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leFondateurVideLaCaisse(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_caisse");
            PowerSupplier s = fonder(helper, fondateur, A, "Caisse Pleine");
            PowerSuppliers registre = essai.registre();
            ServerPlayer p = fondateur.joueur();
            // Les pièces arrivent comme d'un prélèvement : par le registre, sans le bloc.
            registre.deposit(s.id(), Coin.GOLD, 3);
            registre.deposit(s.id(), Coin.COPPER, 25);
            // Hors de la main : le clic droit se fait main vide.
            p.getInventory().setItem(9, new ItemStack(Coin.SILVER.item(), 5));
            long total = valeurPortee(p) + valeurCaisse(registre, s.id());

            // Toutes les demandes de l'écran se codent et se relisent à l'identique ; un numéro hors bouton est refusé.
            for (Coin coin : Coin.values()) {
                for (int n : CoinRequest.amounts()) {
                    for (CoinRequest q : List.of(CoinRequest.withdraw(coin, n), CoinRequest.deposit(coin, n))) {
                        helper.assertTrue(PowerTillMenu.request(PowerTillMenu.button(q)).equals(Optional.of(q)), "codage " + q);
                    }
                }
                if (coin != Coin.COPPER) {
                    for (boolean all : new boolean[] {false, true}) {
                        CoinRequest q = CoinRequest.breakCoin(coin, all);
                        helper.assertTrue(PowerTillMenu.request(PowerTillMenu.button(q)).equals(Optional.of(q)), "codage " + q);
                    }
                }
            }
            helper.assertTrue(PowerTillMenu.request(9_999_999).isEmpty() && PowerTillMenu.request(-1).isEmpty(),
                    "numéros hors bouton");

            clicDroit(helper, p, A);
            helper.assertTrue(p.containerMenu instanceof PowerTillMenu menu && menu instanceof CoinAccountMenu,
                    "le fondateur ouvre l'écran commun de la caisse");
            PowerTillMenu menu = (PowerTillMenu) p.containerMenu;
            helper.assertTrue(MoneyStorageRules.isAllowedMenu(menu), "la caisse est un rangement permis");
            helper.assertTrue(menu.count(Coin.GOLD) == 3 && menu.count(Coin.COPPER) == 25, "l'écran lit la caisse");

            // Aucun dépôt : la caisse ne reçoit que des pièces prélevées.
            helper.assertTrue(menu.apply(p, PowerTillMenu.button(CoinRequest.depositAll())) == Result.BAD_REQUEST
                    && menu.apply(p, PowerTillMenu.button(CoinRequest.deposit(Coin.SILVER, 1))) == Result.BAD_REQUEST,
                    "dépôt refusé");
            helper.assertTrue(PlayerCoins.held(p).getOrDefault(Coin.SILVER, 0L) == 5L, "l'argent porté ne bouge pas");
            // Retraits, trop grand retrait, change, faux numéro.
            helper.assertTrue(menu.apply(p, PowerTillMenu.button(CoinRequest.withdraw(Coin.GOLD, 1))) == Result.DONE,
                    "retrait d'un or");
            helper.assertTrue(menu.apply(p, PowerTillMenu.button(CoinRequest.withdraw(Coin.GOLD, 10))) == Result.INSUFFICIENT,
                    "retrait trop grand");
            helper.assertTrue(menu.apply(p, PowerTillMenu.button(CoinRequest.groupAll())) == Result.DONE, "regroupement");
            helper.assertTrue(menu.apply(p, 9_999_999) == Result.BAD_REQUEST, "faux numéro");
            helper.assertTrue(menu.clickMenuButton(p, PowerTillMenu.button(CoinRequest.withdraw(Coin.SILVER, 1))),
                    "le bouton du jeu passe par le même chemin");
            EnumMap<Coin, Long> caisse = registre.till(s.id());
            helper.assertTrue(caisse.getOrDefault(Coin.GOLD, 0L) == 2L && caisse.getOrDefault(Coin.SILVER, 0L) == 1L
                    && caisse.getOrDefault(Coin.COPPER, 0L) == 5L, "caisse après retraits et change : " + caisse);
            helper.assertTrue(PlayerCoins.held(p).getOrDefault(Coin.GOLD, 0L) == 1L
                    && PlayerCoins.held(p).getOrDefault(Coin.SILVER, 0L) == 6L, "le fondateur porte ce qu'il a retiré");
            helper.assertTrue(valeurPortee(p) + valeurCaisse(registre, s.id()) == total,
                    "rien n'est créé ni détruit : " + total + " puis " + (valeurPortee(p) + valeurCaisse(registre, s.id())));

            // La caisse est gardée par le serveur : elle se lit et se remplit sans le chunk de son poste.
            BlockPos loin = new BlockPos(1_000_000, 64, 1_000_000);
            helper.assertTrue(!helper.getLevel().isLoaded(loin), "la cible doit être hors des chunks chargés");
            registre.setStation(s.id(), GlobalPos.of(helper.getLevel().dimension(), loin));
            registre.deposit(s.id(), Coin.RUBY, 2);
            helper.assertTrue(registre.till(s.id()).getOrDefault(Coin.RUBY, 0L) == 2L
                    && PowerStationBlock.hasLiveStation(essai.serveur(), registre.byId(s.id()).orElseThrow()),
                    "caisse lue zone déchargée, poste compté");
            helper.assertTrue(!helper.getLevel().isLoaded(loin), "la lecture ne charge pas le chunk");
            helper.assertTrue(!menu.stillValid(p), "le poste n'est plus celui noté : l'écran se ferme");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void casseEtReposeGardeLeFournisseur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_repose");
            JoueurDeTest citoyen = essai.joueur("po_repose_cit");
            PowerSupplier s = fonder(helper, fondateur, A, "Repose");
            PowerSuppliers registre = essai.registre();
            registre.deposit(s.id(), Coin.COPPER, 5);
            UUID compteur = UUID.randomUUID();
            registre.putClient(new PowerClient(compteur, s.id(), citoyen.uuid(), "po_repose_cit", "po_repose_cit",
                    PowerClient.State.ACTIVE, 0L, 0L, Set.of(), false));
            BlockPos absA = helper.absolutePos(A);

            helper.assertTrue(fondateur.joueur().gameMode.destroyBlock(absA) && helper.getBlockState(A).isAir(),
                    "le fondateur démonte son poste");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1, "un objet tombé : " + lache);
            ItemStack objet = lache.get(0).getItem().copy();
            lache.get(0).discard();
            helper.assertTrue(PowerStationBlock.supplierOf(objet).equals(Optional.of(s.id())), "l'objet porte le fournisseur");
            PowerSupplier apres = registre.byId(s.id()).orElseThrow();
            helper.assertTrue(apres.station() == null, "l'emplacement noté est effacé");
            helper.assertTrue(registre.till(s.id()).getOrDefault(Coin.COPPER, 0L) == 5L
                    && registre.clientOf(compteur).isPresent(), "caisse et clients restent au serveur");

            // Reposé ailleurs : il reprend le même fournisseur.
            helper.assertTrue(poser(helper, fondateur.joueur(), B, objet.copy()).consumesAction(), "repose");
            PowerStationBlockEntity be = poste(helper, B);
            helper.assertTrue(s.id().equals(be.supplierId()) && be.isOpen(), "le poste reposé reprend son fournisseur");
            helper.assertTrue(registre.byId(s.id()).map(PowerSupplier::station).equals(
                    Optional.of(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(B)))), "nouvel emplacement noté");

            // Un second poste du même fournisseur : refusé, que l'objet le porte ou qu'il soit neuf.
            fondateur.messagesRecus();
            helper.assertTrue(!poser(helper, fondateur.joueur(), C, objet.copy()).consumesAction()
                    && helper.getBlockState(C).isAir(), "copie de l'objet : second poste refusé");
            helper.assertTrue(!poser(helper, fondateur.joueur(), C, objetNeuf()).consumesAction()
                    && helper.getBlockState(C).isAir(), "objet neuf du même fondateur : refusé");
            helper.assertTrue(fondateur.messagesRecus().contains(PowerStationBlock.MESSAGE + "has_station"), "message");
            // Un citoyen qui aurait l'objet ne prend pas le fournisseur d'un autre.
            citoyen.messagesRecus();
            helper.assertTrue(!poser(helper, citoyen.joueur(), C, objet.copy()).consumesAction()
                    && helper.getBlockState(C).isAir(), "un citoyen ne pose pas le poste d'un autre");
            helper.assertTrue(citoyen.messagesRecus().contains(PowerStationBlock.MESSAGE + "not_yours"), "message");

            // Objet perdu : le fondateur repose un poste neuf, qui reprend son fournisseur.
            helper.setBlock(B, Blocks.AIR);
            helper.assertTrue(registre.byId(s.id()).orElseThrow().station() == null, "retrait par le jeu : note effacée");
            helper.assertTrue(poser(helper, fondateur.joueur(), C, objetNeuf()).consumesAction()
                    && s.id().equals(poste(helper, C).supplierId()), "un objet neuf reprend le fournisseur du fondateur");
            registre.removeClient(compteur);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void lesDonneesSurviventALaRelance(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("po_relance");
            PowerSupplier s = fonder(helper, fondateur, A, "Relance");
            PowerSuppliers registre = essai.registre();
            MinecraftServer serveur = essai.serveur();
            registre.deposit(s.id(), Coin.EMERALD, 2);
            registre.deposit(s.id(), Coin.COPPER, 7);
            registre.recordOut(s.id(), 1234);
            UUID actif = UUID.randomUUID();
            UUID attente = UUID.randomUUID();
            UUID disjoncteur = UUID.randomUUID();
            registre.putClient(new PowerClient(actif, s.id(), fondateur.uuid(), "po_relance", "Martin",
                    PowerClient.State.ACTIVE, 0L, 50L, Set.of(disjoncteur), true));
            registre.putClient(new PowerClient(attente, s.id(), fondateur.uuid(), "po_relance", "Martin",
                    PowerClient.State.AWAITING_CONSENT, 0L, 0L, Set.of(), false));
            registre.recordMeter(actif, 900);
            registre.recordMeter(attente, 10);
            registre.recordMeter(UUID.randomUUID(), 10);
            helper.assertTrue(registre.passes(actif) && !registre.passes(attente) && !registre.passes(UUID.randomUUID()),
                    "seul un client accepté laisse passer");
            try {
                // Écrit sur le disque, puis relu par un stockage neuf : c'est ce que fait un redémarrage.
                serveur.overworld().getDataStorage().save();
                File dossier = serveur.getWorldPath(LevelResource.ROOT).resolve("data").toFile();
                helper.assertTrue(new File(dossier, PowerSuppliers.KEY + ".dat").isFile(), "fichier du registre non écrit");
                PowerSuppliers relu = new DimensionDataStorage(dossier, serveur.getFixerUpper())
                        .get(PowerSuppliers::load, PowerSuppliers.KEY);
                helper.assertTrue(relu != null && relu != registre, "registre non relu du disque");
                PowerSupplier lu = relu.byId(s.id()).orElseThrow();
                PowerSupplier vrai = registre.byId(s.id()).orElseThrow();
                helper.assertTrue(lu.equals(vrai), "fournisseur relu : " + lu + " au lieu de " + vrai);
                helper.assertTrue(lu.outToday() == 1234L && lu.till().getOrDefault(Coin.EMERALD, 0L) == 2L,
                        "sortie et caisse relues");
                helper.assertTrue(relu.clientOf(actif).equals(registre.clientOf(actif))
                        && relu.clientOf(actif).orElseThrow().feToday() == 900L
                        && relu.clientOf(actif).orElseThrow().breakers().equals(Set.of(disjoncteur))
                        && relu.clientOf(actif).orElseThrow().cut(), "client relu : " + relu.clientOf(actif));
                helper.assertTrue(relu.clientOf(attente).orElseThrow().feToday() == 10L, "consommation d'un client en attente");
                helper.assertTrue(relu.passes(actif) && !relu.passes(attente), "règle de passage relue");
                helper.assertTrue(relu.priceCap() == registre.priceCap() && relu.taxPercent() == registre.taxPercent()
                        && relu.nextPriceCap().equals(registre.nextPriceCap())
                        && relu.nextTaxPercent().equals(registre.nextTaxPercent()), "plafond et taxe relus");
                // Plafond et taxe du lendemain, sur un registre détaché (ceux du monde appartiennent au maire).
                PowerSuppliers detache = new PowerSuppliers();
                detache.setNextPriceCap(15L);
                detache.setNextTaxPercent(5);
                PowerSuppliers reluDetache = PowerSuppliers.load(detache.save(new CompoundTag()));
                helper.assertTrue(reluDetache.nextPriceCap().equals(Optional.of(15L))
                        && reluDetache.nextTaxPercent().equals(Optional.of(5)), "valeurs du lendemain relues");

                // Un fichier abîmé ne fait ni planter ni gagner de pièces ; un état inconnu ne laisse rien passer.
                CompoundTag abime = registre.save(new CompoundTag());
                abime.getList("suppliers", 10).add(new CompoundTag());
                CompoundTag inconnu = new CompoundTag();
                UUID bizarre = UUID.randomUUID();
                inconnu.putUUID("meter", bizarre);
                inconnu.putUUID("player", fondateur.uuid());
                inconnu.putString("supplier", s.id());
                inconnu.putString("state", "PIRATE");
                abime.getList("clients", 10).add(inconnu);
                PowerSuppliers recharge = PowerSuppliers.load(abime);
                helper.assertTrue(recharge.all().size() == registre.all().size(), "ligne sans identifiant ignorée");
                helper.assertTrue(recharge.clientOf(bizarre).map(PowerClient::state)
                        .equals(Optional.of(PowerClient.State.AWAITING_CONSENT)) && !recharge.passes(bizarre),
                        "un état inconnu repart de l'accord");
                // Un fournisseur retiré (caisse vide seulement) ne laisse plus passer ses compteurs.
                helper.assertTrue(!registre.removeIfEmpty(s.id()), "une caisse pleine empêche le retrait");
            } finally {
                registre.removeClient(actif);
                registre.removeClient(attente);
            }
            helper.succeed();
        }
    }
}
