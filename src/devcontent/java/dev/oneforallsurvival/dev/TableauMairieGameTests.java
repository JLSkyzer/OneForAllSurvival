package dev.oneforallsurvival.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.town.TownBoardBlock;
import dev.oneforallsurvival.economy.town.TownBoardBlockEntity;
import dev.oneforallsurvival.economy.town.TownFinances;
import dev.oneforallsurvival.economy.town.TownFinances.Snapshot;
import dev.oneforallsurvival.economy.town.TownFinancesPacket;
import dev.oneforallsurvival.role.RoleBook;
import dev.oneforallsurvival.role.RoleSavedData;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Le tableau de la mairie (lot E3, tâche 5) : posé par le maire ou l'équipe (sans limite), déplacé par un ingénieur,
 * le maire ou l'équipe, forme exacte, instantané du trésor et des dettes, paquet vers les clients, clic droit,
 * {@code /treasury board}.
 *
 * <p>Le tableau ne touche ni l'emplacement ni le registre du trésor : seuls les essais qui lisent ou changent le solde
 * du serveur le rendent en fin de test. Les lots sont de 8 au plus ; le paquet a un lot à lui (il attend des ticks).
 */
@GameTestHolder("oneforallsurvival")
@PrefixGameTestTemplate(false)
public final class TableauMairieGameTests {

    private static final String LOT_A = "tableau_mairie_a";
    private static final String LOT_B = "tableau_mairie_b";
    private static final String LOT_PAQUET = "tableau_mairie_paquet";

    private static final String MSG = TownBoardBlock.MESSAGE;

    private static final BlockPos A = new BlockPos(1, 0, 1);
    private static final BlockPos B = new BlockPos(1, 0, 6);
    private static final BlockPos C = new BlockPos(6, 0, 1);
    private static final Vec3 JOUEUR = new Vec3(4.5, 0, 4.5);

    /** L'essai du lot du paquet, rendu par {@code @AfterBatch} s'il a échoué avant de se fermer. */
    @Nullable
    private static Essai paquetOuvert;

    private TableauMairieGameTests() {}

    @AfterBatch(batch = LOT_PAQUET)
    public static void apresPaquet(ServerLevel level) {
        if (paquetOuvert != null) {
            paquetOuvert.close();
            paquetOuvert = null;
        }
    }

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

    /** Ce qu'un test a changé : tout est rendu à la fermeture, même si le test a échoué. */
    private static final class Essai implements AutoCloseable {
        private final GameTestHelper helper;
        private final List<JoueurDeTest> joueurs = new ArrayList<>();
        private final List<GameProfile> equipe = new ArrayList<>();
        private final Map<UUID, String> rolesAvant = new LinkedHashMap<>();
        private boolean maireTouche;
        @Nullable
        private UUID maireAvant;
        @Nullable
        private EnumMap<Coin, Long> soldeAvant;
        private boolean ferme;

        Essai(GameTestHelper helper) {
            this.helper = helper;
            boite(helper);
        }

        MinecraftServer serveur() {
            return helper.getLevel().getServer();
        }

        TreasuryAccount tresor() {
            return TreasuryAccount.get(serveur());
        }

        RoleBook roles() {
            return RoleSavedData.get(serveur()).book();
        }

        /** Le solde du trésor sera rendu tel quel en fin de test. */
        TreasuryAccount garderTresor() {
            if (soldeAvant == null) {
                soldeAvant = tresor().balance();
            }
            return tresor();
        }

        JoueurDeTest citoyen(String nom) {
            JoueurDeTest j = JoueurDeTest.rejoindre(helper.getLevel(), nom, helper.absoluteVec(JOUEUR));
            j.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            joueurs.add(j);
            role(j, RoleBook.DEFAULT_ROLE_ID);
            return j;
        }

        JoueurDeTest ingenieur(String nom) {
            JoueurDeTest j = citoyen(nom);
            role(j, RoleBook.ENGINEER_ROLE_ID);
            return j;
        }

        JoueurDeTest maire(String nom) {
            JoueurDeTest j = citoyen(nom);
            if (!maireTouche) {
                maireTouche = true;
                maireAvant = roles().mayor().orElse(null);
            }
            roles().appointMayor(j.uuid());
            return j;
        }

        /** Un joueur de l'équipe (niveau 4 écrit à la main, voir les leçons), jusqu'à la fermeture. */
        JoueurDeTest equipe(String nom) {
            JoueurDeTest j = citoyen(nom);
            serveur().getPlayerList().getOps().add(new ServerOpListEntry(j.joueur().getGameProfile(), 4, false));
            equipe.add(j.joueur().getGameProfile());
            return j;
        }

        private void role(JoueurDeTest j, String id) {
            rolesAvant.putIfAbsent(j.uuid(), roles().roleOf(j.uuid()).id());
            roles().assign(j.uuid(), id);
        }

        @Override
        public void close() {
            if (ferme) {
                return;
            }
            ferme = true;
            for (GameProfile profil : equipe) {
                serveur().getPlayerList().getOps().remove(profil);
            }
            rolesAvant.forEach((uuid, id) -> roles().assign(uuid, id));
            if (maireTouche) {
                if (maireAvant != null) {
                    roles().appointMayor(maireAvant);
                } else {
                    roles().removeMayor();
                }
            }
            for (JoueurDeTest j : joueurs) {
                if (j.connecte()) {
                    j.quitter();
                }
            }
            boite(helper);
            if (soldeAvant != null) {
                TreasuryAccount tresor = tresor();
                for (Coin coin : Coin.values()) {
                    long diff = soldeAvant.get(coin) - tresor.count(coin);
                    if (diff < 0L) {
                        tresor.remove(coin, -diff);
                    } else if (diff > 0L) {
                        tresor.add(coin, diff);
                    }
                }
            }
        }
    }

    private static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, ItemStack pile, BlockPos pos) {
        ServerPlayer p = j.joueur();
        p.setItemInHand(InteractionHand.MAIN_HAND, pile);
        BlockPos sol = helper.absolutePos(pos.below());
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false);
        return pile.useOn(new UseOnContext(p.serverLevel(), p, InteractionHand.MAIN_HAND, pile, tir));
    }

    private static InteractionResult poser(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        return poser(helper, j, new ItemStack(EconomyRegistry.TOWN_BOARD_ITEM.get()), pos);
    }

    private static void poserEtVerifier(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        InteractionResult r = poser(helper, j, pos);
        helper.assertTrue(r.consumesAction() && helper.getBlockState(pos).is(EconomyRegistry.TOWN_BOARD.get()),
                "le tableau devait se poser : " + r + ", vu " + helper.getBlockState(pos));
    }

    private static InteractionResult clicDroit(GameTestHelper helper, JoueurDeTest j, BlockPos pos) {
        ServerPlayer p = j.joueur();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult tir = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return p.gameMode.useItemOn(p, p.serverLevel(), p.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, tir);
    }

    private static List<ItemEntity> objets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, zone(helper));
    }

    private static void seulLeTableauTombe(GameTestHelper helper) {
        List<ItemEntity> lache = objets(helper);
        helper.assertTrue(lache.size() == 1 && lache.get(0).getItem().is(EconomyRegistry.TOWN_BOARD_ITEM.get())
                && lache.get(0).getItem().getCount() == 1, "seul le tableau tombe : " + lache);
    }

    private static void ramasserLesObjets(GameTestHelper helper) {
        objets(helper).forEach(Entity::discard);
    }

    /** Les clés de langue d'un texte, dans l'ordre (sans dépendre de la langue du serveur d'essai). */
    private static List<String> cles(Component texte) {
        List<String> out = new ArrayList<>();
        if (texte.getContents() instanceof TranslatableContents t) {
            out.add(t.getKey());
        }
        for (Component enfant : texte.getSiblings()) {
            out.addAll(cles(enfant));
        }
        return out;
    }

    private static boolean contient(List<String> textes, String morceau) {
        return textes.stream().anyMatch(t -> t.contains(morceau));
    }

    // ---------------------------------------------------------------------------------------------
    // Lot A : pose, déplacement, créatif, forme, instantané, explosion et piston
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leMairePoseDesTableaux(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest maire = essai.maire("tm_pose_maire");
            JoueurDeTest chef = essai.equipe("tm_pose_chef");
            TreasuryAccount tresor = essai.tresor();
            int lignes = tresor.ledger().size();
            var emplacement = tresor.position();
            poserEtVerifier(helper, maire, A);
            poserEtVerifier(helper, maire, B);
            poserEtVerifier(helper, chef, C);
            for (BlockPos pos : List.of(A, B, C)) {
                helper.assertTrue(helper.getBlockEntity(pos) instanceof TownBoardBlockEntity,
                        "l'entité de bloc est là en " + pos);
            }
            helper.assertTrue(helper.getBlockState(A).getValue(TownBoardBlock.FACING).getAxis().isHorizontal(),
                    "orienté");
            helper.assertTrue(tresor.ledger().size() == lignes && tresor.position().equals(emplacement),
                    "le tableau ne touche ni le registre ni l'emplacement du trésor");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCitoyenNePosePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest citoyen = essai.citoyen("tm_nopose_citoyen");
            JoueurDeTest inge = essai.ingenieur("tm_nopose_inge");
            ServerLevel level = helper.getLevel();
            for (JoueurDeTest refuse : List.of(citoyen, inge)) {
                refuse.messagesRecus();
                ItemStack pile = new ItemStack(EconomyRegistry.TOWN_BOARD_ITEM.get());
                InteractionResult r = poser(helper, refuse, pile, A);
                helper.assertTrue(!r.consumesAction() && helper.getBlockState(A).isAir(),
                        refuse.joueur().getGameProfile().getName() + " ne pose pas : " + r);
                helper.assertTrue(refuse.messagesRecus().contains(MSG + "place_denied"), "message de refus attendu");
                helper.assertTrue(refuse.joueur().getMainHandItem() == pile && pile.getCount() == 1,
                        "l'objet reste en main");
            }
            ItemStack pile = new ItemStack(EconomyRegistry.TOWN_BOARD_ITEM.get());
            InteractionResult distributeur = ((BlockItem) pile.getItem())
                    .place(new DirectionalPlaceContext(level, helper.absolutePos(B), Direction.DOWN, pile, Direction.UP));
            helper.assertTrue(!distributeur.consumesAction() && helper.getBlockState(B).isAir(),
                    "sans joueur, rien ne se pose : " + distributeur);
            FakePlayer faux = FakePlayerFactory.getMinecraft(level);
            BlockPos sol = helper.absolutePos(B.below());
            InteractionResult automate = pile.useOn(new UseOnContext(level, faux, InteractionHand.MAIN_HAND, pile,
                    new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false)));
            helper.assertTrue(!automate.consumesAction() && helper.getBlockState(B).isAir(),
                    "un faux joueur ne pose pas : " + automate);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void ingenieurMaireEtEquipeLeDeplacent(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tm_casse_chef");
            JoueurDeTest maire = essai.maire("tm_casse_maire");
            JoueurDeTest inge = essai.ingenieur("tm_casse_inge");
            TreasuryAccount tresor = essai.tresor();
            int lignes = tresor.ledger().size();
            BlockPos abs = helper.absolutePos(A);
            for (JoueurDeTest casseur : List.of(inge, maire, chef)) {
                String nom = casseur.joueur().getGameProfile().getName();
                poserEtVerifier(helper, chef, A);
                BlockState etat = helper.getBlockState(A);
                helper.assertTrue(etat.getDestroySpeed(helper.getLevel(), abs) < 0, "dureté réservée (-1)");
                helper.assertTrue(etat.getDestroyProgress(casseur.joueur(), helper.getLevel(), abs) > 0.0F,
                        nom + " a une progression");
                helper.assertTrue(casseur.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                        nom + " le casse");
                seulLeTableauTombe(helper);
                ramasserLesObjets(helper);
            }
            helper.assertTrue(tresor.ledger().size() == lignes, "le registre du trésor n'a rien noté");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void unCitoyenNeLeCassePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tm_nocasse_chef");
            JoueurDeTest citoyen = essai.citoyen("tm_nocasse_citoyen");
            JoueurDeTest createur = essai.citoyen("tm_nocasse_creatif");
            createur.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            createur.joueur().setShiftKeyDown(true);
            poserEtVerifier(helper, chef, A);
            BlockPos abs = helper.absolutePos(A);
            BlockState etat = helper.getBlockState(A);
            helper.assertTrue(etat.getDestroyProgress(citoyen.joueur(), helper.getLevel(), abs) == 0.0F,
                    "pas de progression pour un citoyen");
            citoyen.messagesRecus();
            helper.assertTrue(!citoyen.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TOWN_BOARD.get()), "un citoyen ne le casse pas");
            helper.assertTrue(citoyen.messagesRecus().contains(MSG + "break_denied"), "message de refus attendu");
            helper.assertTrue(!createur.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TOWN_BOARD.get()), "un citoyen en créatif non plus");
            helper.assertTrue(objets(helper).isEmpty(), "rien au sol");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void enCreatifIlFautSAccroupir(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tm_creatif_chef");
            chef.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            poserEtVerifier(helper, chef, A);
            BlockPos abs = helper.absolutePos(A);
            chef.joueur().setShiftKeyDown(false);
            chef.messagesRecus();
            helper.assertTrue(!chef.joueur().gameMode.destroyBlock(abs)
                    && helper.getBlockState(A).is(EconomyRegistry.TOWN_BOARD.get()), "non accroupi : refusé");
            helper.assertTrue(chef.messagesRecus().contains(MSG + "crouch"), "message « accroupissez-vous »");
            chef.joueur().setShiftKeyDown(true);
            helper.assertTrue(chef.joueur().gameMode.destroyBlock(abs) && helper.getBlockState(A).isAir(),
                    "accroupi : cassé");
            helper.assertTrue(objets(helper).isEmpty(), "en créatif, aucun objet ne tombe");
            helper.succeed();
        }
    }

    /** Les boîtes du modèle de bloc, lues du JSON livré, en forme de voxels (orientation nord). */
    private static VoxelShape formeDuModele() {
        try (InputStream in = TableauMairieGameTests.class
                .getResourceAsStream("/assets/oneforallsurvival/models/block/town_board.json")) {
            if (in == null) {
                throw new AssertionError("modèle town_board.json introuvable");
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            VoxelShape forme = Shapes.empty();
            for (JsonElement e : json.getAsJsonArray("elements")) {
                JsonArray from = e.getAsJsonObject().getAsJsonArray("from");
                JsonArray to = e.getAsJsonObject().getAsJsonArray("to");
                forme = Shapes.or(forme, Shapes.box(from.get(0).getAsDouble() / 16.0D, from.get(1).getAsDouble() / 16.0D,
                        from.get(2).getAsDouble() / 16.0D, to.get(0).getAsDouble() / 16.0D, to.get(1).getAsDouble() / 16.0D,
                        to.get(2).getAsDouble() / 16.0D));
            }
            return forme;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Le modèle tourné comme le bloc d'état (la rotation du blockstate est horaire vue d'en haut). */
    private static VoxelShape tourne(VoxelShape forme, Direction direction) {
        int quarts = switch (direction) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < quarts; i++) {
            VoxelShape[] suivante = {Shapes.empty()};
            forme.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    suivante[0] = Shapes.or(suivante[0], Shapes.box(1.0D - z2, y1, x1, 1.0D - z1, y2, x2)));
            forme = suivante[0];
        }
        return forme;
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void laFormeSuitLeModele(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            VoxelShape modele = formeDuModele();
            helper.assertTrue(Shapes.joinIsNotEmpty(modele, Shapes.block(), BooleanOp.NOT_SAME),
                    "le modèle n'est pas un cube plein : le test doit pouvoir échouer");
            BlockPos abs = helper.absolutePos(A);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState etat = EconomyRegistry.TOWN_BOARD.get().defaultBlockState().setValue(TownBoardBlock.FACING,
                        direction);
                VoxelShape forme = etat.getShape(helper.getLevel(), abs, CollisionContext.empty());
                VoxelShape attendue = tourne(modele, direction);
                helper.assertTrue(!Shapes.joinIsNotEmpty(forme, attendue, BooleanOp.NOT_SAME),
                        "la forme diffère du modèle vers " + direction + " : " + forme.bounds() + " / " + attendue.bounds());
                helper.assertTrue(forme.bounds().maxY < 1.0D && forme.bounds().maxY > 0.5D, "ni cube ni vide : " + forme.bounds());
                helper.assertTrue(!etat.isSolidRender(helper.getLevel(), abs) && !etat.canOcclude(),
                        "le bloc n'occulte pas ses voisins (noOcclusion)");
            }
            // Un bloc plein voisin garde sa face contre le tableau (leçon du 2026-11-10 : la capture collée à un mur).
            helper.setBlock(A, EconomyRegistry.TOWN_BOARD.get());
            helper.setBlock(A.east(), Blocks.STONE);
            BlockState voisin = helper.getBlockState(A.east());
            helper.assertTrue(Block.shouldRenderFace(voisin, helper.getLevel(), helper.absolutePos(A.east()), Direction.WEST,
                    helper.absolutePos(A)), "la face de la pierre contre le tableau reste affichée");
            // Il se tient sur pieds : la forme laisse passer sous le panneau, entre les pieds.
            BlockState nord = EconomyRegistry.TOWN_BOARD.get().defaultBlockState().setValue(TownBoardBlock.FACING,
                    Direction.NORTH);
            VoxelShape forme = nord.getShape(helper.getLevel(), abs, CollisionContext.empty());
            helper.assertTrue(Shapes.joinIsNotEmpty(forme, Shapes.box(0.45, 0.0, 0.3, 0.55, 0.2, 0.7), BooleanOp.AND) == false,
                    "vide entre les pieds, sous le panneau");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A)
    public static void leSnapshotSuitLeTresorEtLesDettes(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            // Instances détachées : le solde et les dettes du serveur ne bougent pas.
            TreasuryAccount compte = TreasuryAccount.load(new CompoundTag());
            Payroll paie = Payroll.load(new CompoundTag());
            Snapshot vide = TownFinances.of(compte, paie);
            helper.assertTrue(vide.equals(new Snapshot(0L, 0L, 0)), "départ vide : " + vide);
            compte.add(Coin.GOLD, 2L);
            compte.add(Coin.COPPER, 5L);
            Snapshot garni = TownFinances.of(compte, paie);
            helper.assertTrue(garni.balance() == compte.total() && garni.balance() == 205L && garni.owedCount() == 0,
                    "le solde suit le trésor : " + garni);
            LocalDate jour = LocalDate.of(2999, 1, 1);
            UUID a = UUID.nameUUIDFromBytes("tm_snap_a".getBytes(StandardCharsets.UTF_8));
            UUID b = UUID.nameUUIDFromBytes("tm_snap_b".getBytes(StandardCharsets.UTF_8));
            paie.credit(a, "tm_snap_a", jour, "policier", 3600L, 60L, 86_400L);
            paie.credit(b, "tm_snap_b", jour, "mayor", 1800L, 60L, 86_400L);
            paie.closeDay(jour);
            Snapshot dette = TownFinances.of(compte, paie);
            helper.assertTrue(dette.owedTotal() == 90L && dette.owedCount() == 2 && dette.balance() == 205L,
                    "la dette de la ville : " + dette);
            helper.assertTrue(dette.owedTotal() == paie.owedTotal() && dette.owedCount() == paie.debts().size(),
                    "même total et même nombre que la paie");
            // Le texte : titre, solde, salaires dus ; ou « aucun salaire dû ».
            Component texte = TownFinances.describe(dette);
            List<String> cles = cles(texte);
            helper.assertTrue(cles.contains("gui.oneforallsurvival.town_board.title")
                    && cles.contains("gui.oneforallsurvival.town_board.balance")
                    && cles.contains("gui.oneforallsurvival.town_board.owed")
                    && !cles.contains("gui.oneforallsurvival.town_board.none"), "texte avec dettes : " + cles);
            String chaine = texte.getString();
            helper.assertTrue(chaine.contains(CoinMath.format(205L).getString())
                    && chaine.contains(CoinMath.format(90L).getString()), "montants du texte : " + chaine);
            List<String> sansDette = cles(TownFinances.describe(garni));
            helper.assertTrue(sansDette.contains("gui.oneforallsurvival.town_board.none")
                    && !sansDette.contains("gui.oneforallsurvival.town_board.owed"), "texte sans dette : " + sansDette);
            // Celui du serveur lit son trésor et sa paie.
            Snapshot serveur = TownFinances.of(essai.serveur());
            helper.assertTrue(serveur.balance() == essai.tresor().total()
                    && serveur.owedTotal() == Payroll.get(essai.serveur()).owedTotal(), "lit le serveur : " + serveur);
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_A, timeoutTicks = 100)
    public static void explosionEtPistonSansEffet(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos tableau = new BlockPos(3, 0, 3);
        BlockPos temoin = new BlockPos(2, 0, 3);
        BlockPos centre = new BlockPos(2, 0, 4);
        helper.setBlock(tableau, EconomyRegistry.TOWN_BOARD.get());
        helper.setBlock(temoin, Blocks.DIRT);
        BlockState etat = helper.getBlockState(tableau);
        helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE), "tags Wither et dragon");
        helper.assertTrue(etat.getPistonPushReaction() == PushReaction.BLOCK, "le piston est bloqué");
        Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
        helper.getLevel().explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
        helper.assertTrue(helper.getBlockState(tableau).is(EconomyRegistry.TOWN_BOARD.get()), "le tableau résiste");
        boite(helper);
        BlockPos piston = new BlockPos(1, 0, 2);
        BlockPos bloc = new BlockPos(2, 0, 2);
        BlockPos pistonTemoin = new BlockPos(1, 0, 4);
        helper.setBlock(bloc, EconomyRegistry.TOWN_BOARD.get());
        helper.setBlock(new BlockPos(2, 0, 4), Blocks.DIRT);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 4)).is(Blocks.DIRT),
                        "le piston témoin devait pousser la terre"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(helper.getBlockState(bloc).is(EconomyRegistry.TOWN_BOARD.get()),
                                "le tableau ne devait pas bouger");
                        helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                                "le piston bloqué ne devait pas s'étendre");
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot B : clic droit, commande, connexion
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void leClicDroitDitLesComptes(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tm_clic_chef");
            JoueurDeTest citoyen = essai.citoyen("tm_clic_citoyen");
            JoueurDeTest maire = essai.maire("tm_clic_maire");
            TreasuryAccount tresor = essai.garderTresor();
            tresor.add(Coin.GOLD, 3L);
            poserEtVerifier(helper, chef, A);
            // Une dette réelle, le temps du test (le tableau lit la paie du serveur) : retirée dans le même tick.
            Payroll paie = Payroll.get(essai.serveur());
            LocalDate jour = LocalDate.of(2999, 1, 1);
            UUID salarie = UUID.nameUUIDFromBytes("tm_clic_salarie".getBytes(StandardCharsets.UTF_8));
            paie.credit(salarie, "tm_clic_salarie", jour, "policier", 3600L, 60L, 86_400L);
            List<Payroll.Debt> dettes = paie.closeDay(jour);
            try {
                helper.assertTrue(dettes.size() == 1, "une dette posée : " + dettes);
                Snapshot attendu = TownFinances.of(essai.serveur());
                String solde = CoinMath.format(attendu.balance()).getString();
                String du = CoinMath.format(attendu.owedTotal()).getString();
                for (JoueurDeTest joueur : List.of(citoyen, maire, chef)) {
                    joueur.textesRecus();
                    InteractionResult r = clicDroit(helper, joueur, A);
                    helper.assertTrue(r.consumesAction(), "le clic est pris : " + r);
                    List<String> textes = joueur.textesRecus();
                    helper.assertTrue(textes.size() == 1, "un seul message dans le chat : " + textes);
                    String texte = textes.get(0);
                    helper.assertTrue(texte.contains(solde) && texte.contains(du) && attendu.owedCount() >= 1,
                            joueur.joueur().getGameProfile().getName() + " lit le solde et la dette : " + texte);
                    helper.assertTrue(joueur.joueur().containerMenu == joueur.joueur().inventoryMenu,
                            "aucun écran ne s'ouvre");
                }
                // Une pièce en main n'y change rien : le bloc est un usage permis.
                citoyen.joueur().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Coin.GOLD.item(), 1));
                citoyen.textesRecus();
                clicDroit(helper, citoyen, A);
                helper.assertTrue(citoyen.textesRecus().size() == 1, "même avec une pièce en main");
            } finally {
                dettes.forEach(d -> paie.removeDebt(d.id()));
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void treasuryBoardDonneUnTableauAuMaire(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest maire = essai.maire("tm_cmd_maire");
            JoueurDeTest chef = essai.equipe("tm_cmd_chef");
            JoueurDeTest citoyen = essai.citoyen("tm_cmd_citoyen");
            JoueurDeTest inge = essai.ingenieur("tm_cmd_inge");
            for (JoueurDeTest refuse : List.of(citoyen, inge)) {
                refuse.textesRecus();
                helper.assertTrue(commande(helper, refuse, "treasury board") == 0, "refusé");
                helper.assertTrue(!porte(refuse), "aucun tableau donné");
                helper.assertTrue(!refuse.textesRecus().isEmpty(), "le refus est dit");
            }
            for (JoueurDeTest autorise : List.of(maire, chef)) {
                String nom = autorise.joueur().getGameProfile().getName();
                helper.assertTrue(commande(helper, autorise, "treasury board") == 1, nom + " reçoit un tableau");
                helper.assertTrue(autorise.joueur().getInventory().countItem(EconomyRegistry.TOWN_BOARD_ITEM.get()) == 1,
                        nom + " en porte un");
                autorise.textesRecus();
                helper.assertTrue(commande(helper, autorise, "treasury board") == 0, nom + " : refusé, il en porte déjà un");
                helper.assertTrue(autorise.joueur().getInventory().countItem(EconomyRegistry.TOWN_BOARD_ITEM.get()) == 1,
                        nom + " n'en a pas reçu un second");
                helper.assertTrue(!autorise.textesRecus().isEmpty(), "le refus est dit");
                // Le tableau donné se pose.
                ItemStack donne = autorise.joueur().getInventory().items.stream()
                        .filter(p -> p.is(EconomyRegistry.TOWN_BOARD_ITEM.get())).findFirst().orElseThrow();
                BlockPos pos = autorise == maire ? A : B;
                InteractionResult r = poser(helper, autorise, donne, pos);
                helper.assertTrue(r.consumesAction() && helper.getBlockState(pos).is(EconomyRegistry.TOWN_BOARD.get()),
                        nom + " pose le tableau reçu : " + r);
            }
            helper.succeed();
        }
    }

    private static boolean porte(JoueurDeTest j) {
        return j.joueur().getInventory().countItem(EconomyRegistry.TOWN_BOARD_ITEM.get()) > 0;
    }

    private static int commande(GameTestHelper helper, JoueurDeTest j, String ligne) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(ligne,
                    j.joueur().createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            throw new AssertionError("/" + ligne + " : " + e.getMessage());
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_B)
    public static void laConnexionEnvoieLesComptes(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest nouveau = essai.citoyen("tm_connexion");
            List<TownFinancesPacket> recus = nouveau.financesALaConnexion();
            helper.assertTrue(recus.size() == 1, "un instantané à la connexion : " + recus);
            Snapshot serveur = TownFinances.of(essai.serveur());
            TownFinancesPacket paquet = recus.get(0);
            helper.assertTrue(paquet.balance() == serveur.balance() && paquet.owedTotal() == serveur.owedTotal()
                    && paquet.owedCount() == serveur.owedCount(), "les comptes du serveur : " + paquet + " / " + serveur);
            helper.succeed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lot du paquet : un dépôt au trésor du serveur part aux clients au plus 20 ticks après
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_PAQUET, timeoutTicks = 200)
    public static void lePaquetPartAQuandLesComptesChangent(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        paquetOuvert = essai;
        JoueurDeTest voisin = essai.citoyen("tm_paquet_voisin");
        TreasuryAccount tresor = essai.garderTresor();
        long[] depart = new long[2];
        long[] attendu = new long[1];
        List<TownFinancesPacket> recus = new ArrayList<>();
        helper.startSequence()
                // Une seconde pleine : ce que les tests d'avant ont changé est parti, et ce qui reste est dit.
                .thenIdle(25)
                .thenExecute(() -> {
                    voisin.financesRecues();
                    depart[0] = helper.getLevel().getServer().getTickCount();
                    tresor.add(Coin.GOLD, 1L);
                    attendu[0] = tresor.total();
                })
                .thenWaitUntil(() -> {
                    recus.addAll(voisin.financesRecues());
                    helper.assertTrue(!recus.isEmpty(), "le paquet n'est pas arrivé");
                })
                .thenExecute(() -> {
                    long ticks = helper.getLevel().getServer().getTickCount() - depart[0];
                    helper.assertTrue(ticks <= 21L, "le paquet est arrivé après " + ticks + " ticks");
                    TownFinancesPacket dernier = recus.get(recus.size() - 1);
                    helper.assertTrue(dernier.balance() == attendu[0], "le solde reçu : " + dernier + " / " + attendu[0]);
                    depart[1] = recus.size();
                })
                // Rien ne change : rien de plus.
                .thenIdle(45)
                .thenExecute(() -> {
                    List<TownFinancesPacket> en_plus = voisin.financesRecues();
                    helper.assertTrue(en_plus.isEmpty(), "rien n'a changé, rien ne doit repartir : " + en_plus);
                    essai.close();
                    paquetOuvert = null;
                })
                .thenSucceed();
    }
}
