package dev.oneforallsurvival.dev;

import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.OrderResult;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaChunks;
import dev.oneforallsurvival.phone.net.AntennaItem;
import dev.oneforallsurvival.phone.net.AntennaLoad;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'antenne (lot T2, tâche 3) : fabrication à l'établi, fondation de l'opérateur par le premier nom, consommation
 * (veille, cartes actives), service daté, objet marqué, et chunk gardé chargé.
 *
 * <p>Le courant : la source d'essai ({@code DevBlocks.testSource()}) ne pousse rien (elle se laisse tirer, et une machine
 * ne tire pas) ; les essais versent donc les FE dans la réserve de l'antenne par sa capability, comme le ferait un
 * câble, et « retirer la source » veut dire cesser de verser.
 *
 * <p>Les essais de chargement posent l'antenne à un million de blocs du gabarit, sur la dernière rangée de son chunk : ni
 * gabarit (dont le jeu force les chunks), ni joueur, ni bloc d'un mod qui force des chunks n'est près d'elle (leçons du
 * 2026-11-11 et du 2026-11-19). Le joueur fondateur reste dans le gabarit, de l'autre côté d'un million de blocs.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AntenneGameTests {

    private static final String LOT_A = "antenne_a";
    private static final String LOT_CHUNK = "antenne_chunk";
    /** Un lot à lui : il coupe {@code antennaKeepsChunkLoaded}, réglage global, le temps de l'essai. */
    private static final String LOT_CHUNK_SANS = "antenne_chunk_sans";

    /** Où les antennes se posent (case d'air ; le sol est un bloc plus bas). */
    private static final BlockPos A = new BlockPos(2, 0, 2);
    private static final BlockPos B = new BlockPos(5, 0, 2);
    private static final BlockPos C = new BlockPos(2, 0, 5);
    private static final BlockPos D = new BlockPos(5, 0, 6);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    /** La forme attendue, écrite en clair (semelle 10 x 2 x 10, mât 4 x 16 x 4, centrés). */
    private static final VoxelShape FORME = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(6, 0, 6, 10, 16, 10));

    private AntenneGameTests() {}

    @BeforeBatch(batch = LOT_CHUNK_SANS)
    public static void avantSansChunk(ServerLevel level) {
        PhoneConfig.overrideAntennaKeepsChunkLoaded(null);
    }

    @AfterBatch(batch = LOT_CHUNK_SANS)
    public static void apresSansChunk(ServerLevel level) {
        PhoneConfig.overrideAntennaKeepsChunkLoaded(null);
    }

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

    /** Ce qu'un essai a ouvert : joueurs, opérateurs, antennes lointaines ; tout est rendu à la fermeture. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> roles = new ArrayList<>();
        private final List<BlockPos> lointaines = new ArrayList<>();

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        PhoneOperators registre() {
            return PhoneOperators.get(serveur());
        }

        /** Un joueur de survie, citoyen, au milieu de l'aire, sans opérateur d'un passage précédent. */
        JoueurDeTest joueur(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            installer(j);
            roles.add(j.uuid());
            oublier(j.uuid());
            return j;
        }

        /** Le même joueur qui revient, avec ce qu'il portait (son playerdata relu), replacé dans l'aire. */
        JoueurDeTest revenir(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR), false, p -> {});
            installer(j);
            return j;
        }

        private void installer(JoueurDeTest j) {
            Vec3 ici = helper.absoluteVec(JOUEUR);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            RoleSavedData.get(serveur()).book().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
        }

        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = joueur(nom);
            RoleSavedData.get(serveur()).book().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        /** Note une antenne posée hors de l'aire, retirée à la fermeture. */
        void lointaine(BlockPos absolu) {
            lointaines.add(absolu);
        }

        /** Retire l'opérateur de ce joueur et les inscriptions de ses antennes (tickets rendus). */
        void oublier(UUID fondateur) {
            PhoneOperators registre = registre();
            registre.byFounder(fondateur).ifPresent(op -> {
                for (AntennaRecord antenne : registre.antennasOf(op.id())) {
                    registre.removeAntenna(antenne.id());
                    ServerLevel level = serveur().getLevel(antenne.dimension());
                    if (level != null) {
                        AntennaChunks.release(level, antenne.pos());
                    }
                }
                registre.remove(op.id());
            });
        }

        /** Les joueurs quittent (l'opérateur et ses antennes restent). */
        void quitterTous() {
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
        }

        @Override
        public void close() {
            quitterTous();
            // Les blocs d'abord (leur retrait efface l'inscription et rend le ticket), puis les opérateurs.
            boite(helper);
            for (BlockPos loin : lointaines) {
                helper.getLevel().setBlock(loin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            RoleBook book = RoleSavedData.get(serveur()).book();
            for (UUID uuid : roles) {
                book.assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                oublier(uuid);
            }
        }
    }

    private static ItemStack objetNeuf() {
        return new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
    }

    /** Pose comme le ferait le joueur : l'objet en main, un clic sur le sol sous {@code pos}. */
    private static InteractionResult poser(GameTestHelper helper, ServerPlayer p, BlockPos pos, ItemStack pile) {
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    private static AntennaBlockEntity antenne(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getBlockState(pos).is(PhoneRegistry.ANTENNA.get())
                && helper.getBlockEntity(pos) instanceof AntennaBlockEntity, "pas d'antenne en " + pos);
        return (AntennaBlockEntity) helper.getBlockEntity(pos);
    }

    /** Une antenne posée par {@code j} en {@code pos}, puis fondée sous {@code nom} ; l'opérateur créé. */
    private static PhoneOperator fonder(GameTestHelper helper, JoueurDeTest j, BlockPos pos, String nom) {
        InteractionResult r = poser(helper, j.joueur(), pos, objetNeuf());
        helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
        antenne(helper, pos);
        Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
        helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
        return fonde.get().operator();
    }

    /** Verse {@code fe} FE dans la réserve, par la capability (paquets de la réception maximale). */
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

    /**
     * La valeur, ou un échec d'essai : dans une séquence, toute autre exception qu'un échec d'assertion arrête le serveur
     * d'essai.
     */
    private static <T> T exiger(GameTestHelper helper, Optional<T> valeur, String message) {
        helper.assertTrue(valeur.isPresent(), message);
        return valeur.get();
    }

    private static AntennaRecord lointaineInscrite(GameTestHelper helper, Essai essai, BlockPos absolu) {
        return exiger(helper, essai.registre().antennas().stream().filter(x -> x.pos().equals(absolu)).findFirst(),
                "aucune inscription en " + absolu);
    }

    private static Optional<AntennaRecord> inscription(PhoneOperators registre, AntennaBlockEntity be) {
        return be.antennaId() == null ? Optional.empty() : registre.antenna(be.antennaId());
    }

    private static boolean inscriteEn(PhoneOperators registre, ServerLevel level, BlockPos absolu) {
        return registre.antennas().stream()
                .anyMatch(r -> r.dimension().equals(level.dimension()) && r.pos().equals(absolu));
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper));
    }

    private static MachineState etatDuBloc(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(ElectricMachineBlock.STATE);
    }

    /**
     * Une position à un million de blocs du gabarit, sur la dernière rangée (z) de son chunk ; {@code dx} sépare les
     * essais d'un même lot.
     */
    private static BlockPos loin(GameTestHelper helper, int dx) {
        BlockPos o = helper.absolutePos(new BlockPos(1, 0, 1));
        return new BlockPos(o.getX() + 1_000_000 + dx, o.getY(), (o.getZ() >> 4 << 4) + 15);
    }

    /** Pose une antenne là-bas comme la poserait {@code j} (bloc, puis {@code setPlacedBy} du jeu). */
    private static AntennaBlockEntity poserLoin(Essai essai, ServerLevel level, JoueurDeTest j, BlockPos absolu) {
        BlockState etat = PhoneRegistry.ANTENNA.get().defaultBlockState();
        level.setBlock(absolu, etat, Block.UPDATE_ALL);
        essai.lointaine(absolu);
        etat.getBlock().setPlacedBy(level, absolu, etat, j.joueur(), objetNeuf());
        if (!(level.getBlockEntity(absolu) instanceof AntennaBlockEntity be)) {
            throw new IllegalStateException("pas d'antenne en " + absolu);
        }
        return be;
    }

    private static boolean tique(ServerLevel level, BlockPos absolu) {
        return level.getChunkSource().isPositionTicking(ChunkPos.asLong(absolu));
    }

    private static boolean joueurPres(ServerLevel level, BlockPos absolu, double rayon) {
        return level.players().stream().anyMatch(p -> p.distanceToSqr(Vec3.atCenterOf(absolu)) < rayon * rayon);
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : fabrication, fondation, consommation, service, objet
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void lAntenneNeSeFabriqueQuALEtabli(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            ResourceLocation id = new ResourceLocation(OneForAllSurvival.MODID, "antenna");
            helper.assertTrue(!objetNeuf().is(BenchCatalog.TABLE_ONLY), "l'antenne ne doit pas être dans table_only");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isEmpty(),
                    "la recette de l'antenne ne doit pas rester sur la table");
            var aLEtabli = BenchCatalog.current().byId(id);
            helper.assertTrue(aLEtabli.isPresent() && aLEtabli.get().result().is(PhoneRegistry.ANTENNA_ITEM.get()),
                    "la recette de l'antenne doit être au catalogue de l'établi");
            JoueurDeTest citoyen = essai.joueur("an_fab_citoyen");
            JoueurDeTest ingenieur = essai.ingenieur("an_fab_ingenieur");
            BlockPos etabli = helper.absolutePos(A);
            helper.assertTrue(BenchOrders.order(citoyen.joueur(), id, 1, etabli) == OrderResult.NOT_ENGINEER,
                    "un citoyen ne commande pas l'antenne");
            OrderResult parIngenieur = BenchOrders.order(ingenieur.joueur(), id, 1, etabli);
            helper.assertTrue(parIngenieur == OrderResult.MISSING_INGREDIENTS,
                    "l'ingénieur passe le contrôle du rôle (sans ingrédients) : " + parIngenieur);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void poserPuisNommerFondeLOperateur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("an_fonde_a");
            JoueurDeTest autre = essai.joueur("an_fonde_b");
            PhoneOperators registre = essai.registre();
            ServerLevel level = helper.getLevel();

            // Un autre joueur a déjà pris « Mairie ».
            fonder(helper, autre, C, "Mairie");

            InteractionResult r = poser(helper, fondateur.joueur(), A, objetNeuf());
            helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
            AntennaBlockEntity be = antenne(helper, A);
            BlockPos absA = helper.absolutePos(A);
            helper.assertTrue(be.operatorId() == null && be.antennaId() == null && fondateur.uuid().equals(be.placer()),
                    "une antenne neuve appartient à qui l'a posée, sans opérateur");
            helper.assertTrue(!inscriteEn(registre, level, absA), "une antenne neuve n'est pas inscrite");

            // Modèle hors du cube : aucune occlusion, forme exacte.
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(!etat.canOcclude(), "l'antenne ne doit cacher aucune face voisine (noOcclusion)");
            VoxelShape forme = etat.getShape(level, absA);
            helper.assertTrue(!Shapes.joinIsNotEmpty(forme, FORME, BooleanOp.NOT_SAME), "forme : " + forme);

            // Le premier panneau est le nom : pris (sans casse) refusé, un autre joueur ne fonde pas l'antenne d'un autre.
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), absA, "mairie").map(PhoneOperators.Result::status)
                    .equals(Optional.of(PhoneOperators.Status.NAME_TAKEN)), "« Mairie » est pris");
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), absA, "   ").map(PhoneOperators.Result::status)
                    .equals(Optional.of(PhoneOperators.Status.BAD_NAME)), "nom blanc");
            helper.assertTrue(AntennaBlock.found(autre.joueur(), absA, "Usurpateur").isEmpty(),
                    "un citoyen ne fonde pas l'antenne d'un autre");

            // Une seconde antenne posée avant la fondation reste à fonder.
            helper.assertTrue(poser(helper, fondateur.joueur(), B, objetNeuf()).consumesAction(), "seconde pose");

            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(fondateur.joueur(), absA, "  Réseau Ouest ");
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation : " + fonde);
            PhoneOperator op = fonde.get().operator();
            helper.assertTrue(op.name().equals("Réseau Ouest") && op.founder().equals(fondateur.uuid()),
                    "nom et fondateur : " + op);
            helper.assertTrue(op.id().equals(be.operatorId()) && be.antennaId() != null, "l'antenne porte son opérateur");
            AntennaRecord inscrite = exiger(helper, inscription(registre, be), "inscription absente");
            helper.assertTrue(inscrite.operatorId().equals(op.id()) && inscrite.pos().equals(absA)
                    && inscrite.dimension().equals(level.dimension()), "inscription : " + inscrite);
            helper.assertTrue(be.antennaId().equals(exiger(helper, registre.byId(op.id()), "opérateur absent").mainAntenna()),
                    "la première antenne est la principale");
            helper.assertTrue(AntennaChunks.isHeld(level, absA), "son chunk est tenu");
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), absA, "Encore").isEmpty(),
                    "une antenne fondée ne se refonde pas");

            // Un second opérateur du même joueur est refusé.
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), helper.absolutePos(B), "Second Réseau")
                    .map(PhoneOperators.Result::status).equals(Optional.of(PhoneOperators.Status.ALREADY_HAS_ONE)),
                    "un seul opérateur par joueur");
            helper.assertTrue(registre.byName("Second Réseau").isEmpty(), "rien ne devait être fondé");

            // Une antenne posée ensuite par le fondateur rejoint son opérateur.
            helper.assertTrue(poser(helper, fondateur.joueur(), D, objetNeuf()).consumesAction(), "troisième pose");
            AntennaBlockEntity seconde = antenne(helper, D);
            helper.assertTrue(op.id().equals(seconde.operatorId()) && inscription(registre, seconde).isPresent(),
                    "la seconde antenne du fondateur rejoint son opérateur");
            helper.assertTrue(registre.antennasOf(op.id()).size() == 2
                    && be.antennaId().equals(exiger(helper, registre.byId(op.id()), "opérateur absent").mainAntenna()),
                    "deux antennes, la principale ne change pas");
            helper.succeed();
        }
    }


    /*
     * Dans une séquence, une étape qui échoue n'arrête pas les suivantes, et toute exception autre qu'un échec
     * d'assertion arrête le serveur d'essai : les étapes n'utilisent que des assertions (exiger) et ne rangent qu'à la
     * dernière ; ce qu'un essai raté laisse est rangé au passage suivant (aire refaite, opérateur oublié).
     */

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void sansNomLAntenneNeConsommeRien(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        try {
            JoueurDeTest poseur = essai.joueur("an_sans_nom");
            helper.assertTrue(poser(helper, poseur.joueur(), A, objetNeuf()).consumesAction(), "pose");
            AntennaBlockEntity be = antenne(helper, A);
            verser(be, 4000);
            helper.assertTrue(be.energy().getEnergyStored() == 4000, "réserve : " + be.energy().getEnergyStored());
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    try {
                        AntennaBlockEntity be = antenne(helper, A);
                        helper.assertTrue(be.state() == MachineState.STOPPED && etatDuBloc(helper, A) == MachineState.STOPPED,
                                "STOPPED attendu, vu " + be.state());
                        helper.assertTrue(be.energy().getEnergyStored() == 4000,
                                "réserve intacte attendue, vue " + be.energy().getEnergyStored());
                        helper.assertTrue(!inscriteEn(essai.registre(), helper.getLevel(), helper.absolutePos(A)),
                                "aucune inscription");
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void laVeilleCoute40(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        long[] avant = new long[2];
        try {
            JoueurDeTest fondateur = essai.joueur("an_veille");
            fonder(helper, fondateur, A, "Veille Seule");
            verser(antenne(helper, A), 4000);
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    avant[0] = antenne(helper, A).energy().getEnergyStored();
                    avant[1] = level.getGameTime();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    try {
                        AntennaBlockEntity be = antenne(helper, A);
                        long tics = level.getGameTime() - avant[1];
                        long paye = avant[0] - be.energy().getEnergyStored();
                        helper.assertTrue(tics > 0 && paye == 40L * tics,
                                "40 FE par tic attendus : " + paye + " FE en " + tics + " tics");
                        helper.assertTrue(be.state() == MachineState.RUNNING && etatDuBloc(helper, A) == MachineState.RUNNING,
                                "RUNNING attendu, vu " + be.state());
                        AntennaRecord r = exiger(helper, inscription(essai.registre(), be), "inscription absente");
                        helper.assertTrue(r.running() && AntennaBlockEntity.inService(r, level.getGameTime()),
                                "en service : " + r);
                        helper.assertTrue(r.last().equals(new AntennaLoad.Breakdown(40, 0, 0)), "détail : " + r.last());
                        helper.assertTrue(r.feToday() >= paye, "consommation du jour : " + r.feToday());
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void chaqueCarteActiveCoute6(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        long[] mesure = new long[2];
        UUID[] cartes = new UUID[4];
        try {
            JoueurDeTest fondateur = essai.joueur("an_cartes");
            PhoneOperator op = fonder(helper, fondateur, A, "Quatre Cartes");
            PhoneOperators registre = essai.registre();
            for (int i = 0; i < 4; i++) {
                cartes[i] = registre.issue(op.id(), fondateur.uuid(), "an_cartes", helper.getLevel().getRandom(), 0L)
                        .simId();
            }
            helper.assertTrue(registre.setState(cartes[3], SimCard.State.SUSPENDED), "suspension");
            verser(antenne(helper, A), 4000);
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        ServerLevel level = helper.getLevel();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    mesure[0] = antenne(helper, A).energy().getEnergyStored();
                    mesure[1] = level.getGameTime();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    AntennaBlockEntity be = antenne(helper, A);
                    long tics = level.getGameTime() - mesure[1];
                    long paye = mesure[0] - be.energy().getEnergyStored();
                    helper.assertTrue(tics > 0 && paye == 58L * tics,
                            "58 FE par tic attendus (3 actives, 1 suspendue) : " + paye + " FE en " + tics + " tics");
                    AntennaRecord r = exiger(helper, inscription(essai.registre(), be), "inscription absente");
                    helper.assertTrue(r.last().equals(new AntennaLoad.Breakdown(40, 18, 0)), "détail : " + r.last());
                    // Une carte de plus suspendue : le tic suivant le voit déjà (aucun cache en retard).
                    helper.assertTrue(essai.registre().setState(cartes[2], SimCard.State.SUSPENDED), "seconde suspension");
                    verser(be, 4000);
                    mesure[0] = be.energy().getEnergyStored();
                    mesure[1] = level.getGameTime();
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        AntennaBlockEntity be = antenne(helper, A);
                        long tics = level.getGameTime() - mesure[1];
                        long paye = mesure[0] - be.energy().getEnergyStored();
                        helper.assertTrue(tics > 0 && paye == 52L * tics,
                                "52 FE par tic attendus (2 actives) : " + paye + " FE en " + tics + " tics");
                        helper.assertTrue(exiger(helper, inscription(essai.registre(), be), "inscription absente").last()
                                .equals(new AntennaLoad.Breakdown(40, 12, 0)), "détail après la suspension");
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 200)
    public static void sansCourantHorsService(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        boolean[] source = {true};
        try {
            JoueurDeTest fondateur = essai.joueur("an_courant");
            fonder(helper, fondateur, A, "Sans Courant");
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        ServerLevel level = helper.getLevel();
        // La « source » : 40 FE versés à chaque tic, tant qu'elle est raccordée (posé au début, pas depuis un rappel).
        helper.onEachTick(() -> {
            if (source[0] && helper.getBlockEntity(A) instanceof AntennaBlockEntity be) {
                be.energy().receiveEnergy(40, false);
            }
        });
        long[] coupure = new long[1];
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    AntennaBlockEntity be = antenne(helper, A);
                    helper.assertTrue(be.state() == MachineState.RUNNING && etatDuBloc(helper, A) == MachineState.RUNNING,
                            "alimentée, RUNNING attendu, vu " + be.state());
                    AntennaRecord r = exiger(helper, inscription(essai.registre(), be), "inscription absente");
                    helper.assertTrue(AntennaBlockEntity.inService(r, level.getGameTime()), "en service : " + r);
                    source[0] = false;
                    coupure[0] = level.getGameTime();
                })
                .thenIdle(41)
                .thenExecute(() -> {
                    try {
                        AntennaBlockEntity be = antenne(helper, A);
                        helper.assertTrue(level.getGameTime() - coupure[0] >= 40, "40 tics au moins depuis la coupure");
                        helper.assertTrue(be.state() == MachineState.NO_POWER, "NO_POWER attendu, vu " + be.state());
                        helper.assertTrue(etatDuBloc(helper, A) == MachineState.NO_POWER, "voyant éteint : état du bloc "
                                + etatDuBloc(helper, A));
                        AntennaRecord r = exiger(helper, inscription(essai.registre(), be), "inscription absente");
                        helper.assertTrue(!r.running() && !AntennaBlockEntity.inService(r, level.getGameTime()),
                                "hors service : " + r);
                        helper.assertTrue(level.getGameTime() - r.seenGameTime() <= 1,
                                "l'antenne tique encore et se dit vue : " + r.seenGameTime());
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void uneAntenneDemonteeGardeSonOperateur(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("an_demonte");
            JoueurDeTest citoyen = essai.joueur("an_demonte_cit");
            PhoneOperator op = fonder(helper, fondateur, A, "Démontable");
            PhoneOperators registre = essai.registre();
            ServerLevel level = helper.getLevel();
            BlockPos absA = helper.absolutePos(A);
            helper.assertTrue(AntennaChunks.isHeld(level, absA), "chunk tenu avant la casse");

            helper.assertTrue(fondateur.joueur().gameMode.destroyBlock(absA) && helper.getBlockState(A).isAir(),
                    "le fondateur démonte son antenne");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1, "un objet tombé : " + lache);
            ItemStack objet = lache.get(0).getItem().copy();
            lache.get(0).discard();
            helper.assertTrue(AntennaItem.operatorOf(objet).equals(Optional.of(op.id())), "l'objet porte l'opérateur");
            helper.assertTrue(registre.antennasOf(op.id()).isEmpty() && !inscriteEn(registre, level, absA),
                    "l'inscription est effacée");
            helper.assertTrue(registre.byId(op.id()).isPresent(), "l'opérateur reste");
            helper.assertTrue(!AntennaChunks.isHeld(level, absA), "le ticket est rendu");

            // Reposée ailleurs par le fondateur : même opérateur.
            helper.assertTrue(poser(helper, fondateur.joueur(), B, objet.copy()).consumesAction(), "repose");
            AntennaBlockEntity be = antenne(helper, B);
            helper.assertTrue(op.id().equals(be.operatorId()) && inscription(registre, be).isPresent(),
                    "l'antenne reposée reprend son opérateur");
            helper.assertTrue(be.antennaId().equals(registre.byId(op.id()).map(PhoneOperator::mainAntenna).orElse(null)),
                    "seule antenne : principale");

            // Par un joueur sans droit : refusée, avec un message.
            citoyen.messagesRecus();
            helper.assertTrue(!poser(helper, citoyen.joueur(), C, objet.copy()).consumesAction()
                    && helper.getBlockState(C).isAir(), "un citoyen ne pose pas l'antenne d'un autre");
            helper.assertTrue(citoyen.messagesRecus().contains(AntennaBlock.MESSAGE + "not_yours"), "message de refus");
            helper.assertTrue(registre.antennasOf(op.id()).size() == 1, "rien de plus n'est inscrit");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unJoueurQuiPartAvecLAntenneNeLaissePasDeFantome(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("an_part");
            PhoneOperator op = fonder(helper, fondateur, A, "Voyageur");
            PhoneOperators registre = essai.registre();
            ServerLevel level = helper.getLevel();
            BlockPos absA = helper.absolutePos(A);

            helper.assertTrue(fondateur.joueur().gameMode.destroyBlock(absA), "casse");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1, "un objet tombé : " + lache);
            ItemStack objet = lache.get(0).getItem().copy();
            lache.get(0).discard();
            helper.assertTrue(fondateur.joueur().getInventory().add(objet), "l'objet dans l'inventaire");
            fondateur.quitter();

            helper.assertTrue(!inscriteEn(registre, level, absA) && registre.antennasOf(op.id()).isEmpty(),
                    "aucune inscription à l'ancienne place");
            helper.assertTrue(registre.byId(op.id()).isPresent(), "l'opérateur reste");

            JoueurDeTest revenu = essai.revenir("an_part");
            int caseObjet = -1;
            for (int i = 0; i < revenu.joueur().getInventory().getContainerSize(); i++) {
                if (revenu.joueur().getInventory().getItem(i).is(PhoneRegistry.ANTENNA_ITEM.get())) {
                    caseObjet = i;
                }
            }
            helper.assertTrue(caseObjet >= 0, "l'antenne est revenue avec le joueur");
            ItemStack pile = revenu.joueur().getInventory().removeItemNoUpdate(caseObjet);
            helper.assertTrue(AntennaItem.operatorOf(pile).equals(Optional.of(op.id())), "toujours marquée");
            helper.assertTrue(poser(helper, revenu.joueur(), B, pile).consumesAction(), "repose après le retour");
            AntennaBlockEntity be = antenne(helper, B);
            helper.assertTrue(op.id().equals(be.operatorId()) && inscription(registre, be).isPresent(),
                    "il reprend son opérateur");
            helper.assertTrue(registre.antennasOf(op.id()).size() == 1, "une seule inscription");
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lots du chargement
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_CHUNK, timeoutTicks = 300)
    public static void uneAntenneLoinDeTousResteEnService(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos la = loin(helper, 0);
        Essai essai = new Essai(helper);
        long[] vue = new long[1];
        try {
            helper.assertTrue(level.getChunkSource().getChunkNow(la.getX() >> 4, la.getZ() >> 4) == null,
                    "le chunk lointain devait être non chargé au départ");
            JoueurDeTest fondateur = essai.joueur("an_loin");
            AntennaBlockEntity be = poserLoin(essai, level, fondateur, la);
            Optional<PhoneOperators.Result> fonde = AntennaBlock.found(fondateur.joueur(), la, "Loin De Tous");
            helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation : " + fonde);
            verser(be, 40_000);
            helper.assertTrue(AntennaChunks.isHeld(level, la), "le chunk de l'antenne doit être tenu");
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(!joueurPres(level, la, 256), "aucun joueur près de l'antenne");
                    helper.assertTrue(tique(level, la), "son chunk doit tiquer");
                    AntennaRecord r = lointaineInscrite(helper, essai, la);
                    helper.assertTrue(level.getGameTime() - r.seenGameTime() <= 1, "vue au dernier tic : " + r);
                    helper.assertTrue(AntennaBlockEntity.inService(r, level.getGameTime()), "en service : " + r);
                    vue[0] = r.seenGameTime();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    try {
                        AntennaRecord r = lointaineInscrite(helper, essai, la);
                        helper.assertTrue(r.seenGameTime() >= vue[0] + 39, "seenGameTime avance : " + vue[0] + " puis "
                                + r.seenGameTime());
                        helper.assertTrue(AntennaBlockEntity.inService(r, level.getGameTime()), "toujours en service");
                    } finally {
                        essai.close();
                    }
                    helper.assertTrue(!AntennaChunks.isHeld(level, la), "retirée, le ticket est rendu");
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_CHUNK)
    public static void lesTicketsDUneAntenneDisparueSontRendus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.joueur("an_tickets");
            PhoneOperators registre = essai.registre();
            BlockPos p1 = loin(helper, 256);
            BlockPos p2 = loin(helper, 288);
            AntennaBlockEntity premiere = poserLoin(essai, level, fondateur, p1);
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), p1, "Tickets Rendus")
                    .map(PhoneOperators.Result::founded).orElse(false), "fondation");
            AntennaBlockEntity seconde = poserLoin(essai, level, fondateur, p2);
            helper.assertTrue(seconde.operatorId() != null && seconde.operatorId().equals(premiere.operatorId()),
                    "la seconde rejoint l'opérateur");
            helper.assertTrue(AntennaChunks.isHeld(level, p1) && AntennaChunks.isHeld(level, p2), "deux tickets tenus");

            // L'inscription retirée à la main : la validation rend son ticket, et garde celui de l'autre.
            registre.removeAntenna(premiere.antennaId());
            helper.assertTrue(AntennaChunks.isHeld(level, p1), "rien ne bouge avant la validation");
            int rendus = AntennaChunks.validate(level);
            helper.assertTrue(rendus >= 1 && !AntennaChunks.isHeld(level, p1), "ticket rendu par la validation : " + rendus);
            helper.assertTrue(AntennaChunks.isHeld(level, p2), "le ticket d'une antenne inscrite reste");

            // Casse normale : ticket rendu aussitôt, inscription effacée.
            level.setBlock(p2, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(!AntennaChunks.isHeld(level, p2), "ticket rendu à la casse");
            helper.assertTrue(registre.antenna(seconde.antennaId()).isEmpty(), "inscription effacée à la casse");
            helper.succeed();
        }
    }

    /**
     * Le réglage coupé le temps de l'essai : une antenne d'abord en service (chunk tenu) rend son ticket à sa révision
     * suivante ; son chunk cesse de tiquer, elle n'est plus vue, et passe hors tension une fois sa dernière nouvelle
     * plus vieille que {@code antennaStaleTicks}, alors même que le registre la note encore « en marche ».
     */
    @GameTest(template = "test/plancher", batch = LOT_CHUNK_SANS, timeoutTicks = 400)
    public static void sansChunkForceUneAntenneDechargeeEstHorsTension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos la = loin(helper, 512);
        Essai essai = new Essai(helper);
        try {
            JoueurDeTest fondateur = essai.joueur("an_sans_chunk");
            AntennaBlockEntity be = poserLoin(essai, level, fondateur, la);
            helper.assertTrue(AntennaBlock.found(fondateur.joueur(), la, "Sans Chunk")
                    .map(PhoneOperators.Result::founded).orElse(false), "fondation");
            verser(be, 40_000);
            essai.quitterTous();
        } catch (RuntimeException e) {
            essai.close();
            throw e;
        }
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    AntennaRecord r = lointaineInscrite(helper, essai, la);
                    helper.assertTrue(AntennaBlockEntity.inService(r, level.getGameTime()),
                            "d'abord en service, chunk tenu : " + r);
                    PhoneConfig.overrideAntennaKeepsChunkLoaded(false);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(!AntennaChunks.isHeld(level, la), "ticket encore tenu");
                    helper.assertTrue(!tique(level, la), "le chunk tique encore");
                    AntennaRecord r = lointaineInscrite(helper, essai, la);
                    helper.assertTrue(level.getGameTime() - r.seenGameTime() > PhoneConfig.antennaStaleTicks(),
                            "vue il y a " + (level.getGameTime() - r.seenGameTime()) + " tics seulement");
                    helper.assertTrue(r.running() && !AntennaBlockEntity.inService(r, level.getGameTime()),
                            "notée en marche mais hors tension, attendu : " + r);
                })
                .thenExecute(() -> {
                    PhoneConfig.overrideAntennaKeepsChunkLoaded(null);
                    essai.close();
                })
                .thenSucceed();
    }
}
