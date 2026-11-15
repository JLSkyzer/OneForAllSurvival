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
import dev.oneforallsurvival.economy.town.TownBoardPart;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
 * <p>Le tableau fait 3 x 2 blocs (tour 1 de la tâche 6) : le lot C prouve la place exigée, la casse d'une partie qui
 * retire tout en rendant un seul objet, le créatif, {@code /setblock} et le tableau incomplet (ancien format).
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

    private static final String LOT_C = "tableau_mairie_c";

    /**
     * Les maîtresses des tableaux posés : un joueur qui regarde le sud pose un tableau tourné vers le nord, qui va de
     * x - 1 à x + 1 et de y 0 à 1 (la boîte d'essai est vide de x 1 à 6, de y 0 à 3).
     */
    private static final BlockPos A = new BlockPos(3, 0, 1);
    private static final BlockPos B = new BlockPos(3, 0, 6);
    private static final BlockPos C = new BlockPos(3, 0, 3);
    private static final Vec3 JOUEUR = new Vec3(5.5, 0, 4.5);

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
        p.setYRot(0.0F); // il regarde le sud : le tableau se tourne vers le nord, vers lui
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
        entier(helper, pos, Direction.NORTH);
    }

    private static TownBoardBlock bloc() {
        return (TownBoardBlock) EconomyRegistry.TOWN_BOARD.get();
    }

    /** Les six emplacements (relatifs) du tableau de maîtresse {@code maitresse}, par partie. */
    private static Map<TownBoardPart, BlockPos> parties(BlockPos maitresse, Direction face) {
        Map<TownBoardPart, BlockPos> out = new EnumMap<>(TownBoardPart.class);
        for (TownBoardPart partie : TownBoardPart.values()) {
            out.put(partie, partie.from(maitresse, face));
        }
        return out;
    }

    /** Les six parties sont là, tournées vers {@code face} ; seule la maîtresse porte une entité de bloc. */
    private static void entier(GameTestHelper helper, BlockPos maitresse, Direction face) {
        parties(maitresse, face).forEach((partie, pos) -> {
            BlockState etat = helper.getBlockState(pos);
            helper.assertTrue(etat.is(EconomyRegistry.TOWN_BOARD.get()) && etat.getValue(TownBoardBlock.PART) == partie
                    && etat.getValue(TownBoardBlock.FACING) == face, partie + " attendue en " + pos + ", vu " + etat);
            BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            helper.assertTrue(partie.isMaster() ? be instanceof TownBoardBlockEntity : be == null,
                    "entité de bloc de " + partie + " : " + be);
            helper.assertTrue(TownBoardBlock.masterPos(etat, helper.absolutePos(pos)).equals(helper.absolutePos(maitresse)),
                    partie + " renvoie vers la maîtresse");
        });
    }

    /** Aucune des six places du tableau ne porte plus une partie. */
    private static void plusRien(GameTestHelper helper, BlockPos maitresse, Direction face, String quoi) {
        parties(maitresse, face).forEach((partie, pos) -> helper.assertTrue(
                !helper.getBlockState(pos).is(EconomyRegistry.TOWN_BOARD.get()),
                quoi + " : " + partie + " restée en " + pos));
    }

    /** Pose un tableau entier, sans joueur (comme la démonstration). */
    private static void poserEntier(GameTestHelper helper, BlockPos maitresse, Direction face) {
        bloc().placeWhole(helper.getLevel(), helper.absolutePos(maitresse), face);
        entier(helper, maitresse, face);
    }

    private static long tableauxAuSol(GameTestHelper helper) {
        return objets(helper).stream().filter(o -> o.getItem().is(EconomyRegistry.TOWN_BOARD_ITEM.get()))
                .mapToLong(o -> o.getItem().getCount()).sum();
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
                plusRien(helper, A, Direction.NORTH, nom);
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
            BlockPos haut = helper.absolutePos(TownBoardPart.TOP_RIGHT.from(A, Direction.NORTH));
            helper.assertTrue(!citoyen.joueur().gameMode.destroyBlock(haut), "ni par une autre partie");
            entier(helper, A, Direction.NORTH);
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
            plusRien(helper, A, Direction.NORTH, "créatif accroupi");
            helper.assertTrue(objets(helper).isEmpty(), "en créatif, aucun objet ne tombe");
            helper.succeed();
        }
    }

    /** Les boîtes du modèle d'une partie, lues du JSON livré, en forme de voxels (orientation nord). */
    private static VoxelShape formeDuModele(TownBoardPart partie) {
        String chemin = "/assets/oneforallsurvival/models/block/town_board_" + partie.getSerializedName() + ".json";
        try (InputStream in = TableauMairieGameTests.class.getResourceAsStream(chemin)) {
            if (in == null) {
                throw new AssertionError("modèle introuvable : " + chemin);
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
            BlockPos abs = helper.absolutePos(A);
            for (TownBoardPart partie : TownBoardPart.values()) {
                VoxelShape modele = formeDuModele(partie);
                helper.assertTrue(!modele.isEmpty() && Shapes.joinIsNotEmpty(modele, Shapes.block(), BooleanOp.NOT_SAME),
                        partie + " : le modèle n'est ni vide ni un cube plein (le test doit pouvoir échouer)");
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState etat = bloc().partState(direction, partie);
                    VoxelShape forme = etat.getShape(helper.getLevel(), abs, CollisionContext.empty());
                    VoxelShape attendue = tourne(modele, direction);
                    helper.assertTrue(!Shapes.joinIsNotEmpty(forme, attendue, BooleanOp.NOT_SAME),
                            partie + " vers " + direction + " : la forme diffère du modèle : " + forme.bounds() + " / "
                                    + attendue.bounds());
                    helper.assertTrue(!etat.isSolidRender(helper.getLevel(), abs) && !etat.canOcclude(),
                            "le bloc n'occulte pas ses voisins (noOcclusion)");
                }
            }
            // Panneau mince sur pieds : un pied sous chaque colonne de côté, rien sous le milieu.
            VoxelShape basGauche = bloc().partState(Direction.NORTH, TownBoardPart.BOTTOM_LEFT)
                    .getShape(helper.getLevel(), abs, CollisionContext.empty());
            VoxelShape basMilieu = bloc().partState(Direction.NORTH, TownBoardPart.MASTER)
                    .getShape(helper.getLevel(), abs, CollisionContext.empty());
            VoxelShape sol = Shapes.box(0, 0, 0, 1, 0.25, 1);
            helper.assertTrue(Shapes.joinIsNotEmpty(basGauche, sol, BooleanOp.AND)
                    && !Shapes.joinIsNotEmpty(basMilieu, sol, BooleanOp.AND), "un pied sous la colonne de gauche, rien sous le milieu");
            helper.assertTrue(basMilieu.bounds().maxZ - basMilieu.bounds().minZ <= 2.0D / 16.0D + 1.0E-6,
                    "panneau mince : " + basMilieu.bounds());
            // Un bloc plein voisin garde sa face contre le tableau (leçon du 2026-11-10 : la capture collée à un mur).
            poserEntier(helper, C, Direction.NORTH);
            BlockPos cote = TownBoardPart.BOTTOM_LEFT.from(C, Direction.NORTH).east();
            helper.setBlock(cote, Blocks.STONE);
            helper.assertTrue(Block.shouldRenderFace(helper.getBlockState(cote), helper.getLevel(), helper.absolutePos(cote),
                    Direction.WEST, helper.absolutePos(cote.west())), "la face de la pierre contre le tableau reste affichée");
            BlockPos derriere = C.south();
            helper.setBlock(derriere, Blocks.STONE);
            helper.assertTrue(Block.shouldRenderFace(helper.getBlockState(derriere), helper.getLevel(),
                    helper.absolutePos(derriere), Direction.NORTH, helper.absolutePos(C)), "et celle du mur derrière");
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
        BlockPos temoin = new BlockPos(3, 0, 5);
        BlockPos centre = new BlockPos(3, 0, 4);
        poserEntier(helper, tableau, Direction.NORTH);
        helper.setBlock(temoin, Blocks.DIRT);
        BlockState etat = helper.getBlockState(tableau);
        helper.assertTrue(etat.is(BlockTags.WITHER_IMMUNE) && etat.is(BlockTags.DRAGON_IMMUNE), "tags Wither et dragon");
        helper.assertTrue(etat.getPistonPushReaction() == PushReaction.BLOCK, "le piston est bloqué");
        Vec3 pos = helper.absoluteVec(Vec3.atCenterOf(centre));
        helper.getLevel().explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(helper.getBlockState(temoin).isAir(), "le témoin devait sauter : l'explosion a eu lieu");
        entier(helper, tableau, Direction.NORTH);
        helper.assertTrue(tableauxAuSol(helper) == 0, "l'explosion ne fait tomber aucun tableau");
        boite(helper);
        // Un piston pousse vers l'ouest la partie du bas à gauche (à l'est de la maîtresse) : bloqué, rien ne bouge.
        BlockPos maitresse = new BlockPos(3, 0, 2);
        poserEntier(helper, maitresse, Direction.NORTH);
        BlockPos piston = new BlockPos(5, 0, 2);
        BlockPos pistonTemoin = new BlockPos(5, 0, 5);
        helper.setBlock(new BlockPos(4, 0, 5), Blocks.DIRT);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
        helper.setBlock(pistonTemoin, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
        helper.setBlock(new BlockPos(6, 0, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(new BlockPos(3, 0, 5)).is(Blocks.DIRT),
                        "le piston témoin devait pousser la terre"))
                .thenExecute(() -> {
                    try {
                        entier(helper, maitresse, Direction.NORTH);
                        helper.assertTrue(!helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
                                "le piston bloqué ne devait pas s'étendre");
                        helper.assertTrue(tableauxAuSol(helper) == 0, "aucun tableau au sol");
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
                // Une autre partie que la maîtresse répond de même.
                chef.textesRecus();
                clicDroit(helper, chef, TownBoardPart.TOP_RIGHT.from(A, Direction.NORTH));
                helper.assertTrue(chef.textesRecus().size() == 1, "la partie du haut donne aussi les comptes");
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

    // ---------------------------------------------------------------------------------------------
    // Lot C : le tableau de 3 x 2 (place, casse d'une partie, créatif, /setblock, tableau incomplet)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void laPoseDemandeSixPlacesLibres(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest maire = essai.maire("tm_place_maire");
            for (TownBoardPart partie : TownBoardPart.values()) {
                if (partie.isMaster()) {
                    continue;
                }
                BlockPos gene = partie.from(A, Direction.NORTH);
                helper.setBlock(gene, Blocks.STONE);
                maire.messagesRecus();
                ItemStack pile = new ItemStack(EconomyRegistry.TOWN_BOARD_ITEM.get());
                InteractionResult r = poser(helper, maire, pile, A);
                helper.assertTrue(!r.consumesAction() && helper.getBlockState(A).isAir(),
                        "pierre en " + partie + " : pose refusée (" + r + ")");
                helper.assertTrue(helper.getBlockState(gene).is(Blocks.STONE), "la pierre reste");
                plusRien(helper, A, Direction.NORTH, "pose refusée");
                helper.assertTrue(pile.getCount() == 1 && maire.joueur().getMainHandItem() == pile, "l'objet reste en main");
                helper.assertTrue(maire.messagesRecus().contains(MSG + "no_room"), "le manque de place est dit");
                helper.setBlock(gene, Blocks.AIR);
            }
            // Un emplacement remplaçable (une couche de neige) ne gêne pas : elle est remplacée.
            helper.setBlock(TownBoardPart.BOTTOM_LEFT.from(A, Direction.NORTH), Blocks.SNOW);
            poserEtVerifier(helper, maire, A);
            helper.assertTrue(maire.joueur().getMainHandItem().isEmpty(), "l'objet est posé");
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void casserUnePartieCasseToutEtRendUnSeulObjet(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest maire = essai.maire("tm_partie_maire");
            for (TownBoardPart partie : TownBoardPart.values()) {
                poserEtVerifier(helper, maire, A);
                BlockPos pos = helper.absolutePos(partie.from(A, Direction.NORTH));
                helper.assertTrue(maire.joueur().gameMode.destroyBlock(pos), "le maire casse " + partie);
                plusRien(helper, A, Direction.NORTH, "casse de " + partie);
                helper.assertTrue(tableauxAuSol(helper) == 1L, partie + " cassée : un seul tableau rendu, vu "
                        + tableauxAuSol(helper));
                ramasserLesObjets(helper);
            }
            helper.succeed();
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void enCreatifUnePartieCasseeNeRendRien(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            JoueurDeTest chef = essai.equipe("tm_partie_chef");
            for (TownBoardPart partie : TownBoardPart.values()) {
                chef.joueur().gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                poserEtVerifier(helper, chef, A);
                chef.joueur().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                BlockPos pos = helper.absolutePos(partie.from(A, Direction.NORTH));
                chef.joueur().setShiftKeyDown(false);
                helper.assertTrue(!chef.joueur().gameMode.destroyBlock(pos), "non accroupi : refusé (" + partie + ")");
                entier(helper, A, Direction.NORTH);
                chef.joueur().setShiftKeyDown(true);
                helper.assertTrue(chef.joueur().gameMode.destroyBlock(pos), "accroupi : cassé (" + partie + ")");
                plusRien(helper, A, Direction.NORTH, "créatif, " + partie);
                helper.assertTrue(tableauxAuSol(helper) == 0L, "en créatif, rien ne tombe (" + partie + ") : "
                        + tableauxAuSol(helper));
            }
            helper.succeed();
        }
    }

    private static void setblock(GameTestHelper helper, BlockPos pos, String bloc) {
        BlockPos abs = helper.absolutePos(pos);
        try {
            MinecraftServer serveur = helper.getLevel().getServer();
            serveur.getCommands().getDispatcher().execute("setblock " + abs.getX() + " " + abs.getY() + " " + abs.getZ()
                    + " " + bloc, serveur.createCommandSourceStack().withLevel(helper.getLevel()).withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @GameTest(template = "test/plancher", batch = LOT_C)
    public static void setblockNeDupliquePas(GameTestHelper helper) {
        try (Essai essai = new Essai(helper)) {
            // Une partie remplacée par de l'air : tout part, un seul objet (celui de la maîtresse).
            poserEntier(helper, A, Direction.NORTH);
            setblock(helper, TownBoardPart.TOP_RIGHT.from(A, Direction.NORTH), "minecraft:air");
            plusRien(helper, A, Direction.NORTH, "/setblock air");
            helper.assertTrue(tableauxAuSol(helper) == 1L, "/setblock air sur une partie : un seul objet, vu " + tableauxAuSol(helper));
            ramasserLesObjets(helper);
            // La maîtresse remplacée par de la pierre : tout part, rien ne tombe.
            poserEntier(helper, A, Direction.NORTH);
            setblock(helper, A, "minecraft:stone");
            plusRien(helper, A, Direction.NORTH, "/setblock pierre");
            helper.assertTrue(tableauxAuSol(helper) == 0L, "maîtresse remplacée : rien ne tombe");
            helper.setBlock(A, Blocks.AIR);
            // « destroy » sur une partie : comme une casse, un seul objet.
            poserEntier(helper, A, Direction.NORTH);
            setblock(helper, TownBoardPart.BOTTOM_LEFT.from(A, Direction.NORTH), "minecraft:air destroy");
            plusRien(helper, A, Direction.NORTH, "/setblock destroy");
            helper.assertTrue(tableauxAuSol(helper) == 1L, "/setblock destroy : un seul objet, vu " + tableauxAuSol(helper));
            helper.succeed();
        }
    }

    /**
     * Une maîtresse seule (ancien tableau d'un bloc relu, ou {@code /setblock}) : complétée si la place est libre,
     * sinon retirée en rendant un seul objet ; une autre partie seule se retire sans rien rendre.
     */
    @GameTest(template = "test/plancher", batch = LOT_C, timeoutTicks = 100)
    public static void unTableauIncompletSeCompleteOuSeRetire(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos libre = A;
        BlockPos gene = B;
        BlockPos orpheline = new BlockPos(3, 2, 3);
        helper.setBlock(libre, bloc().partState(Direction.NORTH, TownBoardPart.MASTER));
        helper.setBlock(gene, bloc().partState(Direction.NORTH, TownBoardPart.MASTER));
        helper.setBlock(TownBoardPart.TOP_LEFT.from(gene, Direction.NORTH), Blocks.STONE);
        helper.setBlock(orpheline, bloc().partState(Direction.NORTH, TownBoardPart.TOP_RIGHT));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(gene).isAir()
                        && helper.getBlockState(orpheline).isAir()
                        && helper.getBlockState(TownBoardPart.TOP_RIGHT.from(libre, Direction.NORTH))
                                .is(EconomyRegistry.TOWN_BOARD.get()), "en attente de la vérification"))
                .thenExecute(() -> {
                    try {
                        entier(helper, libre, Direction.NORTH);
                        plusRien(helper, gene, Direction.NORTH, "sans place");
                        helper.assertTrue(helper.getBlockState(TownBoardPart.TOP_LEFT.from(gene, Direction.NORTH))
                                .is(Blocks.STONE), "la pierre reste");
                        helper.assertTrue(tableauxAuSol(helper) == 1L, "un seul objet rendu (la maîtresse sans place) : "
                                + tableauxAuSol(helper));
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Lot des liquides : ni l'eau ni la lave ne remplacent un bloc de l'argent ou de la ville
    // ---------------------------------------------------------------------------------------------

    private static final String LOT_LIQUIDES = "tableau_mairie_liquides";

    /** Les places autour d'un groupe de blocs (devant, derrière, côtés, dessus), dans la boîte, hors de ces blocs. */
    private static List<BlockPos> autour(List<BlockPos> blocs) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos b : blocs) {
            for (Direction d : Direction.values()) {
                BlockPos p = b.relative(d);
                if (d != Direction.DOWN && !blocs.contains(p) && !out.contains(p) && p.getX() >= 1 && p.getX() <= 6
                        && p.getZ() >= 1 && p.getZ() <= 6 && p.getY() >= 0 && p.getY() <= 3) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Tout ce qui n'est pas {@code garder} dans l'intérieur de la boîte redevient de l'air (liquides compris). */
    private static void viderSauf(GameTestHelper helper, List<BlockPos> garder) {
        for (int x = 1; x <= 6; x++) {
            for (int z = 1; z <= 6; z++) {
                for (int y = 0; y <= 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!garder.contains(p)) {
                        helper.setBlock(p, Blocks.AIR);
                    }
                }
            }
        }
    }

    /**
     * Pour chaque bloc gardé : un seau vidé droit dessus ne passe pas, et aucun état ne se laisse remplacer par un
     * liquide ; puis des sources d'eau (ou de lave) tout autour et au-dessus, le temps que le liquide coule.
     */
    private static void liquidesSansEffet(GameTestHelper helper, Essai essai, List<BlockPos> blocs, Runnable intact,
            String quoi) {
        JoueurDeTest citoyen = essai.citoyen("tm_liq_" + quoi);
        for (BlockPos b : blocs) {
            BlockState etat = helper.getBlockState(b);
            helper.assertTrue(!etat.canBeReplaced(Fluids.WATER) && !etat.canBeReplaced(Fluids.LAVA),
                    quoi + " " + etat + " : un liquide peut le remplacer");
            for (Item seau : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
                boolean verse = ((BucketItem) seau).emptyContents(citoyen.joueur(), helper.getLevel(), helper.absolutePos(b),
                        null);
                helper.assertTrue(!verse, quoi + " : " + seau + " vidé dans " + etat);
                intact.run();
            }
        }
        List<BlockPos> sources = autour(blocs);
        List<Block> liquides = List.of(Blocks.WATER, Blocks.LAVA);
        helper.startSequence()
                .thenExecute(() -> sources.forEach(p -> helper.setBlock(p, liquides.get(0))))
                .thenIdle(40)
                .thenExecute(() -> {
                    intact.run();
                    helper.assertTrue(objets(helper).isEmpty(), quoi + " : l'eau a fait tomber " + objets(helper));
                    viderSauf(helper, blocs);
                    sources.forEach(p -> helper.setBlock(p, liquides.get(1)));
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    try {
                        intact.run();
                        helper.assertTrue(objets(helper).isEmpty(), quoi + " : la lave a fait tomber " + objets(helper));
                        viderSauf(helper, blocs);
                    } finally {
                        essai.close();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "test/plancher", batch = LOT_LIQUIDES, timeoutTicks = 300)
    public static void niEauNiLaveNeRemplacentLeTableau(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos maitresse = new BlockPos(3, 0, 3);
        poserEntier(helper, maitresse, Direction.NORTH);
        List<BlockPos> blocs = new ArrayList<>(parties(maitresse, Direction.NORTH).values());
        liquidesSansEffet(helper, essai, blocs, () -> entier(helper, maitresse, Direction.NORTH), "tableau");
    }

    @GameTest(template = "test/plancher", batch = LOT_LIQUIDES, timeoutTicks = 300)
    public static void niEauNiLaveNeRemplacentLEtalLeCoffreFortNiLeTresor(GameTestHelper helper) {
        Essai essai = new Essai(helper);
        BlockPos etal = new BlockPos(2, 0, 2);
        BlockPos coffre = new BlockPos(5, 0, 2);
        BlockPos tresor = new BlockPos(3, 0, 5);
        helper.setBlock(etal, EconomyRegistry.MARKET_STALL.get());
        helper.setBlock(coffre, EconomyRegistry.SAFE.get());
        helper.setBlock(tresor, EconomyRegistry.TREASURY.get());
        Map<BlockPos, Block> attendus = Map.of(etal, EconomyRegistry.MARKET_STALL.get(), coffre, EconomyRegistry.SAFE.get(),
                tresor, EconomyRegistry.TREASURY.get());
        liquidesSansEffet(helper, essai, List.of(etal, coffre, tresor), () -> attendus.forEach((pos, bloc) ->
                helper.assertTrue(helper.getBlockState(pos).is(bloc), bloc + " remplacé en " + pos + " : "
                        + helper.getBlockState(pos))), "etal");
    }
}
