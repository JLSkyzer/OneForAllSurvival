package dev.oneforallsurvival.dev;

import com.mojang.authlib.GameProfile;
import dev.oneforallsurvival.OneForAllSurvival;
import dev.oneforallsurvival.bench.BenchCatalog;
import dev.oneforallsurvival.bench.BenchOrders;
import dev.oneforallsurvival.bench.OrderResult;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.town.TownBlockRules;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.machine.MachineState;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaChunks;
import dev.oneforallsurvival.phone.net.AntennaItem;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.AntennaRules;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.repair.MaintenanceToolItem;
import dev.oneforallsurvival.repair.RepairRegistry;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * L'outil de maintenance et les droits de l'antenne (lot T2, tâche 4).
 *
 * <ul>
 *   <li>Lot {@code outil_maintenance} : l'outil se fabrique à l'établi, il est dans son tag ; l'ingénieur ne règle ni ne
 *       déplace l'antenne sans lui, le fait avec lui (une main ou l'autre) ; un citoyen qui le tient ne gagne rien ; le
 *       trésor et le tableau de la mairie exigent aussi l'outil pour l'ingénieur.
 *   <li>Lot {@code antenne_droits} : un sans-droit ne touche pas à l'antenne ; le fondateur, le maire et l'équipe oui,
 *       sans outil ; un faux joueur rien, même avec l'outil ; piston et explosion sans effet ; forme exacte ; seuls le
 *       fondateur, le maire et l'équipe fondent un opérateur au nom du poseur.
 * </ul>
 *
 * <p>Chaque essai est synchrone (sauf le piston) : le maire, réglage global, est nommé puis rendu dans la même fonction.
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class AntenneDroitsGameTests {

    private static final String LOT_DROITS = "antenne_droits";
    private static final String LOT_OUTIL = "outil_maintenance";

    private static final String MSG = AntennaBlock.MESSAGE;
    /** La première ligne du rapport de machine du testeur. */
    private static final String RAPPORT = "message.oneforallsurvival.report.condition";

    private static final BlockPos A = new BlockPos(2, 0, 2);
    private static final BlockPos B = new BlockPos(5, 0, 2);
    private static final BlockPos C = new BlockPos(2, 0, 5);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    /** La forme attendue, écrite en clair : semelle 10 x 2 x 10 et mât 4 x 16 x 4, centrés. */
    private static final VoxelShape FORME = Shapes.or(
            Shapes.box(3 / 16.0, 0, 3 / 16.0, 13 / 16.0, 2 / 16.0, 13 / 16.0),
            Shapes.box(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1.0, 10 / 16.0));

    private AntenneDroitsGameTests() {}

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

    /** Ce qu'un essai a ouvert (joueurs, rôles, maire, équipe, faux joueur) ; tout est rendu à la fermeture. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<UUID> roles = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final Map<UUID, String> rolesAvant = new LinkedHashMap<>();
        private boolean maireTouche;
        private UUID maireAvant;
        private FakePlayer faux;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        RoleBook livre() {
            return RoleSavedData.get(serveur()).book();
        }

        PhoneOperators registre() {
            return PhoneOperators.get(serveur());
        }

        /** Un citoyen de survie, au milieu de l'aire, sans opérateur d'un passage précédent. */
        JoueurDeTest citoyen(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            Vec3 ici = helper.absoluteVec(JOUEUR);
            j.joueur().teleportTo(ici.x, ici.y, ici.z);
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            roles.add(j.uuid());
            livre().assign(j.uuid(), RoleBook.DEFAULT_ROLE_ID);
            oublier(j.uuid());
            helper.assertTrue(!j.joueur().hasPermissions(2), "le joueur d'essai ne doit pas être de l'équipe");
            return j;
        }

        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = citoyen(nom);
            livre().assign(j.uuid(), RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        /** Le maire du serveur, jusqu'à la fermeture. */
        JoueurDeTest maire(String nom) {
            JoueurDeTest j = citoyen(nom);
            if (!maireTouche) {
                maireTouche = true;
                maireAvant = livre().mayor().orElse(null);
            }
            livre().appointMayor(j.uuid());
            return j;
        }

        /** Un joueur de l'équipe (niveau 4 écrit à la main, voir les leçons), jusqu'à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = citoyen(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        /** Le faux joueur du jeu, ingénieur le temps de l'essai, mains rendues vides à la fermeture. */
        FakePlayer fauxIngenieur() {
            if (faux == null) {
                faux = FakePlayerFactory.getMinecraft(helper.getLevel());
                rolesAvant.putIfAbsent(faux.getUUID(), livre().roleOf(faux.getUUID()).id());
                livre().assign(faux.getUUID(), RoleBook.ENGINEER_ROLE_ID);
            }
            return faux;
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
                OperateurDEssai.viderCaisse(registre, op);
                registre.remove(op.id());
            });
        }

        @Override
        public void close() {
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            if (maireTouche) {
                if (maireAvant != null) {
                    livre().appointMayor(maireAvant);
                } else {
                    livre().removeMayor();
                }
            }
            if (faux != null) {
                faux.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                faux.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            rolesAvant.forEach((uuid, id) -> livre().assign(uuid, id));
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            // Les blocs d'abord (leur retrait efface l'inscription et rend le ticket), puis les opérateurs.
            boite(helper);
            for (UUID uuid : roles) {
                livre().assign(uuid, RoleBook.DEFAULT_ROLE_ID);
                oublier(uuid);
            }
        }
    }

    private static ItemStack outil() {
        return new ItemStack(RepairRegistry.MAINTENANCE_TOOL.get());
    }

    private static ItemStack antenneNeuve() {
        return new ItemStack(PhoneRegistry.ANTENNA_ITEM.get());
    }

    /** Les deux mains de ce joueur. */
    private static void mains(JoueurDeTest j, ItemStack principale, ItemStack secondaire) {
        j.joueur().setItemInHand(InteractionHand.MAIN_HAND, principale);
        j.joueur().setItemInHand(InteractionHand.OFF_HAND, secondaire);
    }

    /** Pose comme le ferait le joueur : l'objet en main principale, un clic sur le sol sous {@code pos}. */
    private static InteractionResult poser(GameTestHelper helper, ServerPlayer p, BlockPos pos, ItemStack pile) {
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    /** Un clic droit du joueur sur le bloc, par le chemin du jeu (objet d'abord, puis bloc). */
    private static void clicDroit(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, tir);
    }

    /** Ce joueur ouvre-t-il le panneau de l'opérateur (une vue reçue, tâche 9) ? Messages et vues reçus sont vidés. */
    private static boolean ouvre(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        j.messagesRecus();
        j.panneauxOperateurRecus();
        clicDroit(helper, j, pos);
        boolean vue = !j.panneauxOperateurRecus().isEmpty();
        dev.oneforallsurvival.phone.net.OperatorPanels.close(j.uuid());
        return vue;
    }

    private static float progression(GameTestHelper helper, ServerPlayer p, BlockPos pos) {
        return helper.getBlockState(pos).getDestroyProgress(p, helper.getLevel(), helper.absolutePos(pos));
    }

    /** Le bris de ce joueur est-il annulé par le filet de sécurité ({@code BlockEvent.BreakEvent}) ? */
    private static boolean briseAnnule(GameTestHelper helper, ServerPlayer p, BlockPos pos) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), p);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static boolean estAntenne(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(PhoneRegistry.ANTENNA.get());
    }

    private static AntennaBlockEntity antenne(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(estAntenne(helper, pos) && helper.getBlockEntity(pos) instanceof AntennaBlockEntity,
                "pas d'antenne en " + pos);
        return (AntennaBlockEntity) helper.getBlockEntity(pos);
    }

    /** Une antenne posée par {@code j} en {@code pos}, puis fondée sous {@code nom} ; l'opérateur créé. */
    private static PhoneOperator fonder(GameTestHelper helper, JoueurDeTest j, BlockPos pos, String nom) {
        InteractionResult r = poser(helper, j.joueur(), pos, antenneNeuve());
        helper.assertTrue(r.consumesAction(), "l'antenne devait se poser : " + r);
        antenne(helper, pos);
        Optional<PhoneOperators.Result> fonde = AntennaBlock.found(j.joueur(), helper.absolutePos(pos), nom);
        helper.assertTrue(fonde.isPresent() && fonde.get().founded(), "fondation de " + nom + " : " + fonde);
        return fonde.get().operator();
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper));
    }

    /** L'ayant droit ouvre, a une progression et son bris n'est pas annulé. */
    private static void aLaMain(GameTestHelper helper, JoueurDeTest j, BlockPos pos, String qui) {
        helper.assertTrue(ouvre(helper, j, pos), qui + " ouvre le panneau");
        helper.assertTrue(progression(helper, j.joueur(), pos) > 0.0F, qui + " a une progression de casse");
        helper.assertTrue(!briseAnnule(helper, j.joueur(), pos), qui + " : le bris n'est pas annulé");
    }

    /** Le sans-droit n'ouvre pas, n'a aucune progression, son bris est annulé et sa casse échoue. */
    private static void sansLaMain(GameTestHelper helper, JoueurDeTest j, BlockPos pos, String qui) {
        helper.assertTrue(!ouvre(helper, j, pos), qui + " ne doit pas ouvrir le panneau");
        helper.assertTrue(progression(helper, j.joueur(), pos) == 0.0F,
                qui + " ne doit avoir aucune progression : " + progression(helper, j.joueur(), pos));
        helper.assertTrue(briseAnnule(helper, j.joueur(), pos), qui + " : le bris doit être annulé");
        helper.assertTrue(!j.joueur().gameMode.destroyBlock(helper.absolutePos(pos)) && estAntenne(helper, pos),
                qui + " ne doit pas casser l'antenne");
    }

    // ---------------------------------------------------------------------------------------------
    // Lot outil_maintenance
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void lOutilNeSeFabriqueQuALEtabli(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            ResourceLocation id = new ResourceLocation(OneForAllSurvival.MODID, "maintenance_tool");
            helper.assertTrue(!BenchCatalog.isTableOnly(id), "l'outil ne doit pas être dans table_only");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isEmpty(),
                    "la recette de l'outil ne doit pas rester sur la table");
            var aLEtabli = BenchCatalog.current().byId(id);
            helper.assertTrue(aLEtabli.isPresent() && aLEtabli.get().result().is(RepairRegistry.MAINTENANCE_TOOL.get())
                    && aLEtabli.get().result().getCount() == 1, "la recette de l'outil doit être au catalogue de l'établi");
            JoueurDeTest citoyen = essai.citoyen("om_fab_citoyen");
            JoueurDeTest ingenieur = essai.ingenieur("om_fab_ingenieur");
            BlockPos etabli = helper.absolutePos(A);
            helper.assertTrue(BenchOrders.order(citoyen.joueur(), id, 1, etabli) == OrderResult.NOT_ENGINEER,
                    "un citoyen ne commande pas l'outil");
            OrderResult parIngenieur = BenchOrders.order(ingenieur.joueur(), id, 1, etabli);
            helper.assertTrue(parIngenieur == OrderResult.MISSING_INGREDIENTS,
                    "l'ingénieur passe le contrôle du rôle (sans ingrédients) : " + parIngenieur);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void lOutilEstDansLeTag(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            helper.assertTrue(outil().is(MaintenanceToolItem.TAG), "l'outil doit être dans oneforallsurvival:maintenance_tools");
            helper.assertTrue(!new ItemStack(RepairRegistry.TESTER.get()).is(MaintenanceToolItem.TAG),
                    "le testeur n'est pas un outil de maintenance");
            helper.assertTrue(outil().getMaxStackSize() == 1 && !outil().isDamageableItem(), "un seul par case, sans usure");
            JoueurDeTest j = essai.citoyen("om_tag");
            mains(j, new ItemStack(RepairRegistry.TESTER.get()), new ItemStack(RepairRegistry.TESTER.get()));
            helper.assertTrue(!AntennaRules.hasMaintenanceTool(j.joueur()), "deux testeurs ne font pas un outil");
            mains(j, ItemStack.EMPTY, outil());
            helper.assertTrue(AntennaRules.hasMaintenanceTool(j.joueur()), "l'outil en main secondaire compte");
            mains(j, outil(), ItemStack.EMPTY);
            helper.assertTrue(AntennaRules.hasMaintenanceTool(j.joueur()), "l'outil en main principale compte");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void sansLOutilLIngenieurNeRegleNiNeDeplaceLAntenne(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("om_sans_fond");
            JoueurDeTest inge = essai.ingenieur("om_sans_inge");
            PhoneOperator op = fonder(helper, fondateur, A, "Sans Outil");
            mains(inge, ItemStack.EMPTY, ItemStack.EMPTY);
            inge.messagesRecus();
            sansLaMain(helper, inge, A, "l'ingénieur aux mains vides");
            inge.messagesRecus();
            clicDroit(helper, inge, A);
            helper.assertTrue(inge.messagesRecus().contains(MSG + "of"), "il lit à qui est l'antenne");
            // Le testeur en main principale donne le rapport de machine et jamais le panneau, à tout le monde (témoin : le
            // fondateur, ayant droit) ; ce sont la progression et le bris qui disent que l'ingénieur au testeur n'a pas la
            // main.
            for (JoueurDeTest testeur : List.of(fondateur, inge)) {
                String qui = testeur == inge ? "l'ingénieur au testeur" : "le fondateur au testeur";
                mains(testeur, new ItemStack(RepairRegistry.TESTER.get()), ItemStack.EMPTY);
                testeur.messagesRecus();
                testeur.panneauxOperateurRecus();
                clicDroit(helper, testeur, A);
                List<String> recus = testeur.messagesRecus();
                helper.assertTrue(recus.contains(RAPPORT) && testeur.panneauxOperateurRecus().isEmpty(),
                        qui + " : le rapport de machine, jamais le panneau : " + recus);
            }
            helper.assertTrue(progression(helper, inge.joueur(), A) == 0.0F, "l'ingénieur au testeur : aucune progression");
            helper.assertTrue(briseAnnule(helper, inge.joueur(), A), "l'ingénieur au testeur : le bris doit être annulé");
            helper.assertTrue(!inge.joueur().gameMode.destroyBlock(helper.absolutePos(A)) && estAntenne(helper, A),
                    "l'ingénieur au testeur ne doit pas casser l'antenne");
            helper.assertTrue(!AntennaRules.mayManage(inge.joueur(), op.founder()), "pas d'ayant droit sans l'outil");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void avecLOutilEnMainLIngenieurRegleEtDeplace(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("om_avec_fond");
            JoueurDeTest inge = essai.ingenieur("om_avec_inge");
            PhoneOperator op = fonder(helper, fondateur, A, "Avec Outil");
            PhoneOperators registre = essai.registre();

            mains(inge, outil(), ItemStack.EMPTY);
            aLaMain(helper, inge, A, "l'ingénieur, outil en main principale");
            mains(inge, ItemStack.EMPTY, outil());
            aLaMain(helper, inge, A, "l'ingénieur, outil en main secondaire");

            // Il démonte l'antenne (outil en main secondaire) : l'objet garde l'opérateur.
            helper.assertTrue(inge.joueur().gameMode.destroyBlock(helper.absolutePos(A)) && helper.getBlockState(A).isAir(),
                    "l'ingénieur outillé démonte l'antenne");
            List<ItemEntity> lache = objets(helper);
            helper.assertTrue(lache.size() == 1, "un objet tombé : " + lache);
            ItemStack objet = lache.get(0).getItem().copy();
            lache.get(0).discard();
            helper.assertTrue(AntennaItem.operatorOf(objet).equals(Optional.of(op.id())), "l'objet porte l'opérateur");

            // Reposée ailleurs par l'ingénieur outillé : même opérateur.
            helper.assertTrue(poser(helper, inge.joueur(), B, objet.copy()).consumesAction(), "repose par l'ingénieur");
            AntennaBlockEntity be = antenne(helper, B);
            helper.assertTrue(op.id().equals(be.operatorId()) && be.record().isPresent(),
                    "l'antenne reposée reprend son opérateur");
            helper.assertTrue(registre.antennasOf(op.id()).size() == 1, "une seule inscription");

            // Sans l'outil, il ne repose pas l'objet marqué.
            inge.joueur().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            inge.messagesRecus();
            helper.assertTrue(!poser(helper, inge.joueur(), C, objet.copy()).consumesAction() && helper.getBlockState(C).isAir(),
                    "sans l'outil, l'ingénieur ne pose pas l'antenne d'un autre");
            helper.assertTrue(inge.messagesRecus().contains(MSG + "not_yours"), "message de refus");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void unOutilVoleNeDonneRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("om_vol_fond");
            JoueurDeTest voleur = essai.citoyen("om_vol_cit");
            PhoneOperator op = fonder(helper, fondateur, A, "Outil Vole");
            for (boolean principale : new boolean[] {true, false}) {
                String qui = "un citoyen, outil en main " + (principale ? "principale" : "secondaire");
                mains(voleur, principale ? outil() : ItemStack.EMPTY, principale ? ItemStack.EMPTY : outil());
                helper.assertTrue(!AntennaRules.mayManage(voleur.joueur(), op.founder()), qui + " : pas d'ayant droit");
                sansLaMain(helper, voleur, A, qui);
                helper.assertTrue(!TownBlockRules.engineerMayMove(voleur.joueur()) && !TownBlockRules.mayMove(voleur.joueur()),
                        qui + " ne déplace pas les blocs de la ville");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_OUTIL)
    public static void deplacerLeTresorExigeLOutil(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest inge = essai.ingenieur("om_tresor_inge");
            ServerPlayer p = inge.joueur();
            ServerLevel level = helper.getLevel();
            BlockPos ici = helper.absolutePos(A);
            BlockState tresor = EconomyRegistry.TREASURY.get().defaultBlockState();
            BlockState tableau = EconomyRegistry.TOWN_BOARD.get().defaultBlockState();

            mains(inge, ItemStack.EMPTY, ItemStack.EMPTY);
            helper.assertTrue(!TownBlockRules.engineerMayMove(p) && !TownBlockRules.mayMove(p),
                    "sans l'outil, l'ingénieur ne déplace pas les blocs de la ville");
            helper.assertTrue("break_denied".equals(TownBlockRules.moveRefusal(p)), "refus dit");
            helper.assertTrue(tresor.getDestroyProgress(p, level, ici) == 0.0F, "aucune progression sur le trésor");
            helper.assertTrue(tableau.getDestroyProgress(p, level, ici) == 0.0F, "aucune progression sur le tableau");
            helper.assertTrue(!TownBlockRules.mayPlaceTreasury(p, false), "sans l'outil, il ne repose pas le trésor");

            // Un faux joueur qui porte l'UUID de l'ingénieur ou du maire (le déployeur de Create rend celui de son poseur)
            // ne déplace rien, même outillé.
            JoueurDeTest maire = essai.maire("om_tresor_maire");
            for (JoueurDeTest qui : List.of(inge, maire)) {
                FakePlayer faux = FakePlayerFactory.get(level, new GameProfile(qui.uuid(), "[Deployeur]"));
                faux.setItemInHand(InteractionHand.OFF_HAND, outil());
                boolean permis = TownBlockRules.mayMove(faux) || TownBlockRules.engineerMayMove(faux)
                        || TownBlockRules.mayPlaceTreasury(faux, false) || TownBlockRules.mayOpenTreasury(faux)
                        || tresor.getDestroyProgress(faux, level, ici) > 0.0F;
                faux.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                helper.assertTrue(!permis, "un faux joueur à l'UUID de " + qui.joueur().getGameProfile().getName()
                        + " ne touche pas aux blocs de la ville");
            }

            for (boolean principale : new boolean[] {true, false}) {
                String qui = "outil en main " + (principale ? "principale" : "secondaire");
                mains(inge, principale ? outil() : ItemStack.EMPTY, principale ? ItemStack.EMPTY : outil());
                helper.assertTrue(TownBlockRules.engineerMayMove(p) && TownBlockRules.mayMove(p)
                        && TownBlockRules.moveRefusal(p) == null, qui + " : l'ingénieur déplace");
                helper.assertTrue(tresor.getDestroyProgress(p, level, ici) > 0.0F, qui + " : progression sur le trésor");
                helper.assertTrue(tableau.getDestroyProgress(p, level, ici) > 0.0F, qui + " : progression sur le tableau");
                helper.assertTrue(TownBlockRules.mayPlaceTreasury(p, false) && !TownBlockRules.mayPlaceTreasury(p, true),
                        qui + " : il repose le trésor, seulement sans trésor vivant");
            }
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot antenne_droits
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_DROITS)
    public static void unSansDroitNeTouchePasALAntenne(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("ad_sans_fond");
            JoueurDeTest citoyen = essai.citoyen("ad_sans_cit");
            JoueurDeTest maire = essai.maire("ad_sans_maire");
            JoueurDeTest chef = essai.equipe("ad_sans_chef");
            PhoneOperator op = fonder(helper, fondateur, A, "Droits");
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), helper.absolutePos(A)) < 0, "dureté réservée (-1)");

            citoyen.messagesRecus();
            sansLaMain(helper, citoyen, A, "un citoyen");
            clicDroit(helper, citoyen, A);
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "of"), "il lit à qui est l'antenne");
            // Le début de la casse lui dit pourquoi elle n'avancera pas.
            citoyen.messagesRecus();
            etat.attack(helper.getLevel(), helper.absolutePos(A), citoyen.joueur());
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "break_denied"), "refus de casse dit");

            for (JoueurDeTest ayantDroit : List.of(fondateur, maire, chef)) {
                mains(ayantDroit, ItemStack.EMPTY, ItemStack.EMPTY);
                aLaMain(helper, ayantDroit, A, ayantDroit.joueur().getGameProfile().getName() + " (sans outil)");
            }
            helper.assertTrue(AntennaRules.mayManageCards(fondateur.joueur(), op)
                    && !AntennaRules.mayManageCards(maire.joueur(), op) && !AntennaRules.mayManageCards(citoyen.joueur(), op),
                    "les cartes : le fondateur seul");
            helper.assertTrue(AntennaRules.mayTakeTill(fondateur.joueur(), op) && AntennaRules.mayTakeTill(chef.joueur(), op)
                    && !AntennaRules.mayTakeTill(maire.joueur(), op) && !AntennaRules.mayTakeTill(citoyen.joueur(), op),
                    "la caisse : le fondateur et l'équipe");
            helper.assertTrue(AntennaRules.maySetTariff(fondateur.joueur(), op) && AntennaRules.maySetTariff(maire.joueur(), op)
                    && AntennaRules.maySetTariff(chef.joueur(), op) && !AntennaRules.maySetTariff(citoyen.joueur(), op),
                    "le tarif : le fondateur, le maire et l'équipe");
            // Le fondateur démonte la sienne.
            helper.assertTrue(fondateur.joueur().gameMode.destroyBlock(helper.absolutePos(A)) && helper.getBlockState(A).isAir(),
                    "le fondateur démonte son antenne");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_DROITS)
    public static void unFauxJoueurNeFaitRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("ad_faux_fond");
            PhoneOperator op = fonder(helper, fondateur, A, "Contre Automate");
            FakePlayer faux = essai.fauxIngenieur();
            ServerLevel level = helper.getLevel();
            faux.setItemInHand(InteractionHand.OFF_HAND, outil());
            helper.assertTrue(AntennaRules.hasMaintenanceTool(faux), "le faux joueur tient l'outil");

            // Ni pose : ni d'un objet neuf, ni de l'objet marqué de l'opérateur.
            ItemStack neuf = antenneNeuve();
            helper.assertTrue(!poser(helper, faux, B, neuf).consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas d'antenne");
            ItemStack marque = antenneNeuve();
            AntennaItem.markOperator(marque, op);
            helper.assertTrue(!poser(helper, faux, C, marque).consumesAction() && helper.getBlockState(C).isAir(),
                    "un faux joueur ne pose pas l'antenne d'un opérateur");
            faux.setItemInHand(InteractionHand.MAIN_HAND, outil());

            // Ni ouverture : aucun droit, pas même sur ce qui serait « à lui ».
            helper.assertTrue(!AntennaRules.mayManage(faux, op.founder()) && !AntennaRules.mayManage(faux, faux.getUUID())
                    && !AntennaRules.mayManage(faux, null), "un faux joueur n'est jamais ayant droit");

            // Ni casse.
            helper.assertTrue(progression(helper, faux, A) == 0.0F, "aucune progression pour un faux joueur");
            helper.assertTrue(briseAnnule(helper, faux, A), "le bris d'un faux joueur est annulé");
            helper.assertTrue(!faux.gameMode.destroyBlock(helper.absolutePos(A)) && estAntenne(helper, A),
                    "un faux joueur ne casse pas l'antenne");
            helper.assertTrue(antenne(helper, A).record().isPresent() && level.getBlockEntity(helper.absolutePos(A)) != null,
                    "l'antenne reste inscrite");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_DROITS, timeoutTicks = 100)
    public static void unPistonNePoussePasLAntenne(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos piston = new BlockPos(1, 0, 2);
        BlockPos antenne = new BlockPos(2, 0, 2);
        BlockPos pistonTemoin = new BlockPos(1, 0, 4);
        BlockPos pierre = new BlockPos(2, 0, 4);
        helper.setBlock(antenne, PhoneRegistry.ANTENNA.get());
        helper.setBlock(pierre, Blocks.STONE);
        helper.assertTrue(helper.getBlockState(antenne).getPistonPushReaction() == PushReaction.BLOCK, "piston bloqué");
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                // Le témoin : le même piston pousse une pierre.
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 4)).is(Blocks.STONE),
                        "le piston témoin devait pousser la pierre"))
                .thenExecute(() -> {
                    boolean enPlace = helper.getBlockState(antenne).is(PhoneRegistry.ANTENNA.get());
                    boolean rentre = !helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED);
                    boolean ailleurs = helper.getBlockState(new BlockPos(3, 0, 2)).is(PhoneRegistry.ANTENNA.get());
                    essai.close();
                    helper.assertTrue(enPlace && !ailleurs, "l'antenne ne devait pas bouger");
                    helper.assertTrue(rentre, "le piston bloqué ne devait pas s'étendre");
                })
                .thenSucceed();
    }

    /**
     * De l'eau posée contre l'antenne ne l'emporte pas ({@code forceSolidOn}) ; témoin : une torche de l'autre côté de la
     * source est emportée. Une fois la torche partie, l'eau a encore 40 ticks.
     */
    @GameTest(template = "test/plancher", batch = LOT_DROITS, timeoutTicks = 200)
    public static void lEauNEmportePasLAntenne(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos antenne = new BlockPos(2, 0, 2);
        BlockPos source = new BlockPos(2, 0, 3);
        BlockPos torche = new BlockPos(2, 0, 4);
        helper.setBlock(antenne, PhoneRegistry.ANTENNA.get());
        helper.setBlock(torche, Blocks.TORCH);
        helper.setBlock(source, Blocks.WATER);
        long[] fin = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!helper.getBlockState(torche).is(Blocks.TORCH),
                        "l'eau témoin devait emporter la torche"))
                .thenExecute(() -> fin[0] = helper.getTick() + 40)
                .thenWaitUntil(() -> helper.assertTrue(helper.getTick() >= fin[0], "attente de l'eau"))
                .thenExecute(() -> {
                    boolean enPlace = estAntenne(helper, antenne);
                    BlockState etat = helper.getBlockState(antenne);
                    essai.close();
                    helper.assertTrue(enPlace, "l'eau ne devait pas emporter l'antenne : " + etat);
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_DROITS)
    public static void uneExplosionNeCassePasLAntenne(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest fondateur = essai.citoyen("ad_boum_fond");
            BlockPos antenne = new BlockPos(3, 0, 3);
            BlockPos centre = new BlockPos(2, 0, 3);
            BlockPos temoin = new BlockPos(2, 0, 4);
            PhoneOperator op = fonder(helper, fondateur, antenne, "Blindee");
            AntennaBlockEntity be = antenne(helper, antenne);
            UUID id = be.antennaId();
            fondateur.quitter();
            helper.setBlock(temoin, Blocks.DIRT); // prouve que l'explosion porte jusqu'à côté de l'antenne
            BlockState etat = helper.getBlockState(antenne);
            helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE),
                    "le Wither et le dragon ne la cassent pas (tags)");
            Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
            helper.getLevel().explode(null, pos.x, pos.y, pos.z, 4.0F, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
            helper.assertTrue(estAntenne(helper, antenne), "l'antenne devait résister");
            AntennaBlockEntity apres = antenne(helper, antenne);
            helper.assertTrue(id != null && id.equals(apres.antennaId()) && op.id().equals(apres.operatorId())
                    && apres.record().isPresent(), "son inscription est intacte");
            helper.assertTrue(essai.registre().antennasOf(op.id()).size() == 1, "une seule inscription");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_DROITS)
    public static void laFormeDeLAntenne(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            BlockPos ici = helper.absolutePos(A);
            for (MachineState s : MachineState.values()) {
                BlockState etat = PhoneRegistry.ANTENNA.get().defaultBlockState().setValue(ElectricMachineBlock.STATE, s);
                VoxelShape forme = etat.getShape(helper.getLevel(), ici);
                helper.assertTrue(!Shapes.joinIsNotEmpty(forme, FORME, BooleanOp.NOT_SAME), s + " : forme " + forme);
                helper.assertTrue(!etat.canOcclude(), s + " : aucune occlusion");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_DROITS)
    public static void unIngenieurNeFondePasAuNomDUnAutre(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest poseur = essai.citoyen("ad_fonde_poseur");
            JoueurDeTest inge = essai.ingenieur("ad_fonde_inge");
            JoueurDeTest maire = essai.maire("ad_fonde_maire");
            PhoneOperators registre = essai.registre();
            BlockPos absA = helper.absolutePos(A);
            helper.assertTrue(poser(helper, poseur.joueur(), A, antenneNeuve()).consumesAction(), "pose du citoyen");

            for (boolean avecOutil : new boolean[] {false, true}) {
                mains(inge, avecOutil ? outil() : ItemStack.EMPTY, ItemStack.EMPTY);
                helper.assertTrue(AntennaBlock.found(inge.joueur(), absA, "Usurpé").isEmpty(),
                        "l'ingénieur " + (avecOutil ? "outillé " : "") + "ne fonde pas au nom du poseur");
            }
            helper.assertTrue(registre.byFounder(poseur.uuid()).isEmpty() && registre.byName("Usurpé").isEmpty(),
                    "rien n'est fondé");
            helper.assertTrue(antenne(helper, A).operatorId() == null, "l'antenne reste à fonder");

            Optional<PhoneOperators.Result> parLeMaire = AntennaBlock.found(maire.joueur(), absA, "Réseau du Poseur");
            helper.assertTrue(parLeMaire.isPresent() && parLeMaire.get().founded(), "le maire fonde : " + parLeMaire);
            helper.assertTrue(parLeMaire.get().operator().founder().equals(poseur.uuid()),
                    "fondé au nom du poseur, pas du maire");

            // Une antenne sans poseur noté (/setblock) : l'ingénieur outillé la fonde, à son nom ; sans l'outil, non.
            helper.setBlock(B, PhoneRegistry.ANTENNA.get());
            BlockPos absB = helper.absolutePos(B);
            helper.assertTrue(antenne(helper, B).placer() == null, "aucun poseur noté");
            mains(inge, ItemStack.EMPTY, ItemStack.EMPTY);
            helper.assertTrue(AntennaBlock.found(inge.joueur(), absB, "Sans Poseur").isEmpty(),
                    "sans l'outil, l'ingénieur ne fonde pas");
            mains(inge, ItemStack.EMPTY, outil());
            Optional<PhoneOperators.Result> parLIngenieur = AntennaBlock.found(inge.joueur(), absB, "Sans Poseur");
            helper.assertTrue(parLIngenieur.isPresent() && parLIngenieur.get().founded()
                    && parLIngenieur.get().operator().founder().equals(inge.uuid()), "l'ingénieur outillé fonde : " + parLIngenieur);
            helper.succeed();
        }
    }
}
