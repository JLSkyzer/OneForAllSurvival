package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.machine.ElectricMachineBlock;
import dev.oneforallsurvival.phone.PhoneActionPacket;
import dev.oneforallsurvival.phone.PhoneConfig;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneRegistry;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.net.AntennaBlock;
import dev.oneforallsurvival.phone.net.AntennaBlockEntity;
import dev.oneforallsurvival.phone.net.AntennaChunks;
import dev.oneforallsurvival.phone.net.AntennaRecord;
import dev.oneforallsurvival.phone.net.OperatorPanels;
import dev.oneforallsurvival.phone.net.PhoneNetwork;
import dev.oneforallsurvival.phone.net.PhoneOperator;
import dev.oneforallsurvival.phone.net.PhoneOperators;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.RelayFailure;
import dev.oneforallsurvival.phone.net.RelayPlanner;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimCardItem;
import dev.oneforallsurvival.phone.net.SimRequest;
import dev.oneforallsurvival.role.RoleSavedData;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout du réseau téléphonique (lot T2, tâche 13 ; développement seulement, jamais
 * empaqueté). Des commandes réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur
 * pendant ses gestes), sur le modèle d'{@link E2eElectriciteServeur} :
 *
 * <ul>
 *   <li>{@code e2etel entrer <nom> <pos>} et {@code quitter <nom>} : un joueur simulé ({@link JoueurDeTest}), entré sans
 *       somme de départ (aucune pièce créée : les étapes de l'économie relisent leurs totaux) ;
 *   <li>{@code fonder <nom> <pos> <operateur>} : il pose une antenne (crochet de pose du bloc) et fonde l'opérateur par le
 *       chemin du panneau ({@link AntennaBlock#found}) ;
 *   <li>{@code source <pos> <marche>} : un bloc d'énergie créative de Crafts &amp; Additions, ou de la pierre ;
 *       {@code vider <antenne>} : la réserve de l'antenne vidée (une source coupée laisse sinon 40 000 FE à brûler) ;
 *       {@code fil <source> <antenne> <etape>} : le fil suspendu de Crafts &amp; Additions (connecteurs sur le dessus,
 *       puis la bobine de cuivre) ;
 *   <li>{@code telephone <nom>}, {@code demander <nom> <operateur>}, {@code glisser <nom>} : le téléphone du joueur simulé,
 *       configuré, et ses gestes par le chemin des paquets ({@link PhoneService#act}) ;
 *   <li>{@code donner <nom> <titulaire>} : la carte émise au nom de {@code titulaire}, retirée de l'inventaire de
 *       {@code nom} (le fondateur) et remise au titulaire, comme une carte tendue de la main à la main ;
 *       {@code copier <nom> <vers>} : une copie de la pile de carte de {@code nom} (clic milieu du créatif) chez
 *       {@code vers} ; {@code cartes <operateur> <n>} : {@code n} cartes de plus émises par la console (pages) ;
 *   <li>{@code placer <nom> <pos>} ({@code teleportTo}) ; {@code etat <nom>} : ce que voit son téléphone ;
 *       {@code appel <de> <vers>} : {@link PhoneNetwork#route} de {@code de} vers la ligne de {@code vers} ;
 *   <li>{@code intrus <nom> <antenne>} : il tente d'ouvrir l'antenne (clic droit) puis de la casser ;
 *   <li>{@code maire supprimer <operateur> <motif>} et {@code maire motifvide <operateur>} : un maire simulé, élu le
 *       temps d'un tic, joue {@code /operator remove} (puis {@code confirm}) ;
 *   <li>{@code operateur <nom>}, {@code antenne <pos>}, {@code attente <nom>}, {@code jour} : ce que le serveur garde,
 *       lu sans rien changer.
 * </ul>
 *
 * <p>Ni numéro de carte ni jeton n'entre dans une réponse (une réponse finit au journal du client). Chaque réponse est une
 * suite {@code cle=valeur} (sans espace dans les valeurs, ou entre crochets) terminée par {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eTelephoneServeur {

    private static final String FIN = " FIN_E2E";
    static final String CODE = "2468";
    private static final Map<String, JoueurDeTest> JOUEURS = new LinkedHashMap<>();

    private E2eTelephoneServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2etel").requires(s -> s.hasPermission(4))
                .then(Commands.literal("entrer").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(E2eTelephoneServeur::entrer))))
                .then(Commands.literal("quitter").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::quitter)))
                .then(Commands.literal("fonder").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("operateur", StringArgumentType.word())
                                        .executes(E2eTelephoneServeur::fonder)))))
                .then(Commands.literal("source").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("marche", BoolArgumentType.bool()).executes(E2eTelephoneServeur::source))))
                .then(Commands.literal("vider").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eTelephoneServeur::vider)))
                .then(Commands.literal("fil").then(Commands.argument("source", BlockPosArgument.blockPos())
                        .then(Commands.argument("antenne", BlockPosArgument.blockPos())
                                .then(Commands.argument("etape", IntegerArgumentType.integer(1, 2))
                                        .executes(E2eTelephoneServeur::fil)))))
                .then(Commands.literal("telephone").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::telephone)))
                .then(Commands.literal("demander").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("operateur", StringArgumentType.word())
                                .executes(E2eTelephoneServeur::demander))))
                .then(Commands.literal("glisser").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::glisser)))
                .then(Commands.literal("emettre").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::emettre)))
                .then(Commands.literal("donner").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("titulaire", StringArgumentType.word())
                                .executes(E2eTelephoneServeur::donner))))
                .then(Commands.literal("copier").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("vers", StringArgumentType.word()).executes(E2eTelephoneServeur::copier))))
                .then(Commands.literal("carte").then(Commands.argument("id", StringArgumentType.string())
                        .executes(E2eTelephoneServeur::carte)))
                .then(Commands.literal("remettre").then(Commands.argument("operateur", StringArgumentType.word())
                        .then(Commands.argument("titulaire", StringArgumentType.word()).executes(E2eTelephoneServeur::remettre))))
                .then(Commands.literal("cartes").then(Commands.argument("operateur", StringArgumentType.word())
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 100)).executes(E2eTelephoneServeur::cartes))))
                .then(Commands.literal("placer").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(E2eTelephoneServeur::placer))))
                .then(Commands.literal("etat").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::etat)))
                .then(Commands.literal("appel").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("vers", StringArgumentType.word()).executes(E2eTelephoneServeur::appel))))
                .then(Commands.literal("intrus").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(E2eTelephoneServeur::intrus))))
                .then(Commands.literal("maire")
                        .then(Commands.literal("supprimer").then(Commands.argument("operateur", StringArgumentType.word())
                                .then(Commands.argument("motif", StringArgumentType.greedyString())
                                        .executes(ctx -> maire(ctx, StringArgumentType.getString(ctx, "motif"))))))
                        .then(Commands.literal("motifvide").then(Commands.argument("operateur", StringArgumentType.word())
                                .executes(ctx -> maire(ctx, null)))))
                .then(Commands.literal("operateur").then(Commands.argument("operateur", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::operateur)))
                .then(Commands.literal("antenne").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eTelephoneServeur::antenne)))
                .then(Commands.literal("attente").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTelephoneServeur::attente)))
                .then(Commands.literal("jour").executes(E2eTelephoneServeur::jour)));
    }

    // --------------------------------------------------------------------------------------------- outils

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    private static String nettoyer(String texte) {
        return texte.replace(';', ',').replace('[', '(').replace(']', ')').replace('\n', '|').replace(' ', '_');
    }

    private static String nom(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "nom");
    }

    private static Optional<JoueurDeTest> present(String nom) {
        JoueurDeTest j = JOUEURS.get(nom);
        if (j == null || !j.connecte() || j.joueur().server.getPlayerList().getPlayer(j.uuid()) != j.joueur()) {
            return Optional.empty();
        }
        return Optional.of(j);
    }

    /** Un joueur, simulé (de cette console) ou réel (Dev) ; vide s'il n'est pas en ligne. */
    private static Optional<ServerPlayer> joueur(MinecraftServer serveur, String nom) {
        Optional<JoueurDeTest> j = present(nom);
        if (j.isPresent()) {
            return Optional.of(j.get().joueur());
        }
        return Optional.ofNullable(serveur.getPlayerList().getPlayerByName(nom));
    }

    private static String cles(List<Component> messages) {
        List<String> out = new ArrayList<>();
        for (Component c : messages) {
            if (c.getContents() instanceof TranslatableContents t) {
                out.add(t.getKey());
            } else {
                out.add(nettoyer(c.getString()));
            }
        }
        return "[" + String.join(",", out) + "]";
    }

    private static Optional<PhoneOperator> operateurNomme(MinecraftServer serveur, String nom) {
        return PhoneOperators.get(serveur).byName(nom);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    /** Oublie le frein du téléphone ({@code PhoneService.clearThrottle}, de visibilité paquet) : deux gestes d'un tic. */
    private static void sansFrein(UUID joueur) {
        try {
            Method m = PhoneService.class.getDeclaredMethod("clearThrottle", UUID.class);
            m.setAccessible(true);
            m.invoke(null, joueur);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("frein du téléphone introuvable", e);
        }
    }

    /** La session du téléphone tenu en main principale, ouverte (et déverrouillée) si besoin. */
    private static Optional<PhoneSession> session(ServerPlayer p) {
        Optional<PhoneSession> s = PhoneService.session(p.getUUID());
        if (s.isEmpty()) {
            PhoneService.open(p, InteractionHand.MAIN_HAND);
            s = PhoneService.session(p.getUUID());
            if (s.isPresent()) {
                sansFrein(p.getUUID());
                PhoneService.act(p, new PhoneActionPacket(s.get().id(), PhoneActionPacket.Action.UNLOCK, CODE, "", false));
            }
        }
        return s;
    }

    private static PhoneService.Outcome geste(ServerPlayer p, PhoneActionPacket.Action action, String premier) {
        Optional<PhoneSession> s = session(p);
        if (s.isEmpty()) {
            return null;
        }
        sansFrein(p.getUUID());
        return PhoneService.act(p, new PhoneActionPacket(s.get().id(), action, premier, "", false));
    }

    // --------------------------------------------------------------------------------------------- joueurs simulés

    private static int entrer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String nom = nom(ctx);
        if (present(nom).isPresent()) {
            return dire(ctx, "REFUS " + nom + " deja connecte");
        }
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        UUID id = E2eTresorServeur.uuidDe(nom);
        // Aucune pièce créée : la somme de départ est notée donnée avant la première entrée.
        boolean sansDepart = EconomyLedger.get(monde.getServer()).markStartingGiven(id);
        JoueurDeTest j = JoueurDeTest.rejoindre(monde, nom, Vec3.atBottomCenterOf(pos), false, p -> {});
        ServerPlayer p = j.joueur();
        // L'entrée relit la position du playerdata (leçon du 2026-11-14) : il est ramené ici.
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
        JOUEURS.put(nom, j);
        return dire(ctx, "ENTRE nom=" + nom + " sansDepart=" + sansDepart + " operateur=" + p.hasPermissions(2) + " role="
                + RoleSavedData.get(p.server).book().roleOf(id).id() + " connexion=" + cles(j.composantsALaConnexion())
                + " porte=" + CoinMath.valueOf(dev.oneforallsurvival.economy.PlayerCoins.held(p)));
    }

    private static int quitter(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        JOUEURS.remove(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        PhoneService.close(j.get().joueur(), dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason.CLOSED);
        j.get().quitter();
        return dire(ctx, "PARTI nom=" + nom);
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (JoueurDeTest j : JOUEURS.values()) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        JOUEURS.clear();
    }

    /** Le joueur pose une antenne (crochet de pose, comme un vrai clic) et fonde son opérateur par le chemin du panneau. */
    private static int fonder(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String nom = nom(ctx);
        // Simulé, ou le vrai client des captures (qui fonde ainsi l'opérateur de sa propre appli « Antenne »).
        Optional<ServerPlayer> j = joueur(ctx.getSource().getServer(), nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        ServerPlayer p = j.get();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockState etat = PhoneRegistry.ANTENNA.get().defaultBlockState();
        monde.setBlock(pos, etat, 3);
        PhoneRegistry.ANTENNA.get().setPlacedBy(monde, pos, etat, p, new ItemStack(PhoneRegistry.ANTENNA_ITEM.get()));
        Optional<PhoneOperators.Result> r = AntennaBlock.found(p, pos, StringArgumentType.getString(ctx, "operateur"));
        return dire(ctx, "FONDER nom=" + nom + " resultat=" + r.map(x -> x.status().name()).orElse("REFUSE") + " id="
                + r.map(x -> x.operator() == null ? "-" : x.operator().id()).orElse("-"));
    }

    // --------------------------------------------------------------------------------------------- énergie

    private static Block creatif() {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation("createaddition", "creative_energy"));
        return b == null || b == Blocks.AIR ? null : b;
    }

    private static int source(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        boolean marche = BoolArgumentType.getBool(ctx, "marche");
        Block c = creatif();
        if (marche && c == null) {
            return dire(ctx, "REFUS pas de bloc d'energie creative (pack absent)");
        }
        monde.setBlockAndUpdate(pos, marche ? c.defaultBlockState() : Blocks.STONE.defaultBlockState());
        return dire(ctx, "SOURCE pos=" + xyz(pos) + " bloc=" + BuiltInRegistries.BLOCK.getKey(monde.getBlockState(pos).getBlock()));
    }

    private static int vider(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (!(monde.getBlockEntity(pos) instanceof AntennaBlockEntity be)) {
            return dire(ctx, "REFUS pas d'antenne en " + xyz(pos));
        }
        int avant = be.energy().getEnergyStored();
        be.drainEnergy(Integer.MAX_VALUE);
        return dire(ctx, "VIDER avant=" + avant + " apres=" + be.energy().getEnergyStored());
    }

    /**
     * Le fil suspendu de Crafts &amp; Additions : étape 1, un connecteur sur le dessus de la source et un sur le dessus de
     * l'antenne (posés comme par un joueur) ; étape 2 (un tic plus tard au moins), le connecteur de la source tire
     * (« pull »), celui de l'antenne pousse (« push »), et la bobine de cuivre relie les deux.
     */
    private static int fil(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos source = BlockPosArgument.getLoadedBlockPos(ctx, "source");
        BlockPos antenne = BlockPosArgument.getLoadedBlockPos(ctx, "antenne");
        List<String> journal = new ArrayList<>();
        BlockPos cs = source.above();
        BlockPos ca = antenne.above();
        if (IntegerArgumentType.getInteger(ctx, "etape") == 1) {
            ReseauxDev.cliquer(monde, "createaddition:connector", source, Direction.UP, Direction.DOWN, 1, journal);
            ReseauxDev.cliquer(monde, "createaddition:connector", antenne, Direction.UP, Direction.DOWN, 1, journal);
        } else {
            monde.setBlock(cs, ReseauxDev.avec(monde.getBlockState(cs), "mode", "pull"), 3);
            monde.setBlock(ca, ReseauxDev.avec(monde.getBlockState(ca), "mode", "push"), 3);
            ReseauxDev.tirerFil(monde, "createaddition:copper_spool", cs, ca, journal);
        }
        String etats = BuiltInRegistries.BLOCK.getKey(monde.getBlockState(cs).getBlock()).getPath() + ":"
                + ReseauxDev.propriete(monde.getBlockState(cs), "mode") + ","
                + BuiltInRegistries.BLOCK.getKey(monde.getBlockState(ca).getBlock()).getPath() + ":"
                + ReseauxDev.propriete(monde.getBlockState(ca), "mode");
        return dire(ctx, "FIL etats=" + etats + " journal=[" + nettoyer(String.join(" | ", journal)) + "]");
    }

    // --------------------------------------------------------------------------------------------- téléphones

    /** Un téléphone chargé en main principale, ouvert et configuré (code {@value #CODE}), puis fermé. */
    private static int telephone(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        ServerPlayer p = j.get().joueur();
        ItemStack phone = new ItemStack(PhoneRegistry.PHONE.get());
        PhoneItem.store(phone, PhoneConfig.capacity());
        Inventory inv = p.getInventory();
        if (!inv.getItem(inv.selected).isEmpty() && !inv.add(inv.getItem(inv.selected).copy())) {
            return dire(ctx, "REFUS inventaire plein");
        }
        inv.setItem(inv.selected, phone);
        PhoneService.Outcome ouvert = PhoneService.open(p, InteractionHand.MAIN_HAND);
        Optional<PhoneSession> s = PhoneService.session(p.getUUID());
        PhoneService.Outcome code = null;
        if (s.isPresent()) {
            sansFrein(p.getUUID());
            code = PhoneService.act(p, new PhoneActionPacket(s.get().id(), PhoneActionPacket.Action.SETUP, CODE, "", false));
        }
        return dire(ctx, "TELEPHONE nom=" + nom + " ouvert=" + ouvert + " code=" + code + " id="
                + PhoneItem.phoneId(p.getMainHandItem()).map(UUID::toString).orElse("-"));
    }

    /** Le joueur demande une carte à cet opérateur par sa feuille « Carte SIM » (geste {@code REQUEST_SIM}). */
    private static int demander(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        Optional<PhoneOperator> op = operateurNomme(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "operateur"));
        if (j.isEmpty() || op.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom + " operateur=" + op.isPresent());
        }
        ServerPlayer p = j.get().joueur();
        j.get().etatsTelephoneRecus();
        PhoneService.Outcome r = geste(p, PhoneActionPacket.Action.REQUEST_SIM, op.get().id());
        List<dev.oneforallsurvival.phone.PhoneStatePacket> recus = j.get().etatsTelephoneRecus();
        String dernier = recus.isEmpty() ? "-" : recus.get(recus.size() - 1).last().name();
        int demandes = (int) PhoneOperators.get(p.server).requestsOf(op.get().id()).stream()
                .filter(d -> d.player().equals(p.getUUID())).count();
        return dire(ctx, "DEMANDER nom=" + nom + " issue=" + r + " dernier=" + dernier + " demandes=" + demandes);
    }

    /** Le joueur glisse dans son téléphone la première carte de son inventaire (geste {@code INSERT_SIM}). */
    private static int glisser(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        ServerPlayer p = j.get().joueur();
        int ou = -1;
        for (int i = 0; i < p.getInventory().getContainerSize() && ou < 0; i++) {
            if (p.getInventory().getItem(i).getItem() instanceof SimCardItem) {
                ou = i;
            }
        }
        if (ou < 0) {
            return dire(ctx, "REFUS aucune carte dans l'inventaire");
        }
        j.get().etatsTelephoneRecus();
        PhoneService.Outcome r = geste(p, PhoneActionPacket.Action.INSERT_SIM, Integer.toString(ou));
        List<dev.oneforallsurvival.phone.PhoneStatePacket> recus = j.get().etatsTelephoneRecus();
        String dernier = recus.isEmpty() ? "-" : recus.get(recus.size() - 1).last().name();
        return dire(ctx, "GLISSER nom=" + nom + " case=" + ou + " issue=" + r + " dernier=" + dernier + " validite="
                + PhoneSims.check(p.server, p.getMainHandItem()) + " cartesRestantes=" + compterCartes(p));
    }

    /** Le fondateur s'émet une carte de son opérateur, à son nom ({@link PhoneSims#issueTo}, comme le panneau). */
    private static int emettre(CommandContext<CommandSourceStack> ctx) {
        Optional<JoueurDeTest> j = present(nom(ctx));
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = j.get().joueur();
        Optional<PhoneOperator> op = PhoneOperators.get(p.server).byFounder(p.getUUID());
        if (op.isEmpty()) {
            return dire(ctx, "REFUS pas fondateur");
        }
        boolean emise = PhoneSims.issueTo(p, op.get(), p.getUUID(), p.getGameProfile().getName(), EconomyClock.now()).isPresent();
        return dire(ctx, "EMETTRE emise=" + emise + " cartes=" + compterCartes(p));
    }

    private static int compterCartes(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof SimCardItem) {
                n++;
            }
        }
        return n;
    }

    /** La carte émise au nom de {@code titulaire}, retirée de l'inventaire du fondateur et remise au titulaire. */
    private static int donner(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Optional<ServerPlayer> de = joueur(serveur, nom(ctx));
        Optional<JoueurDeTest> vers = present(StringArgumentType.getString(ctx, "titulaire"));
        if (de.isEmpty() || vers.isEmpty()) {
            return dire(ctx, "ABSENT de=" + de.isPresent() + " vers=" + vers.isPresent());
        }
        UUID titulaire = vers.get().uuid();
        PhoneOperators registre = PhoneOperators.get(serveur);
        Inventory inv = de.get().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            Optional<SimCard> carte = SimCardItem.simId(s).flatMap(registre::card);
            if (carte.isPresent() && titulaire.equals(carte.get().holder())) {
                ItemStack pile = inv.removeItemNoUpdate(i);
                inv.setChanged();
                de.get().containerMenu.broadcastChanges();
                boolean mis = vers.get().joueur().getInventory().add(pile);
                return dire(ctx, "DONNER case=" + i + " remis=" + mis + " etat=" + carte.get().state());
            }
        }
        return dire(ctx, "REFUS aucune carte au nom du titulaire chez " + nom(ctx));
    }

    /** Une copie de la première pile de carte de {@code nom} chez {@code vers} (le clic milieu du créatif). */
    private static int copier(CommandContext<CommandSourceStack> ctx) {
        Optional<JoueurDeTest> de = present(nom(ctx));
        Optional<JoueurDeTest> vers = present(StringArgumentType.getString(ctx, "vers"));
        if (de.isEmpty() || vers.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        Inventory inv = de.get().joueur().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof SimCardItem) {
                boolean mis = vers.get().joueur().getInventory().add(inv.getItem(i).copyWithCount(1));
                return dire(ctx, "COPIER case=" + i + " mise=" + mis);
            }
        }
        return dire(ctx, "REFUS aucune carte a copier");
    }

    /** {@code n} cartes de plus émises par la console pour cet opérateur, à des titulaires fictifs (pages de cartes). */
    private static int cartes(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Optional<PhoneOperator> op = operateurNomme(serveur, StringArgumentType.getString(ctx, "operateur"));
        if (op.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        PhoneOperators registre = PhoneOperators.get(serveur);
        int n = IntegerArgumentType.getInteger(ctx, "n");
        for (int i = 0; i < n; i++) {
            registre.issue(op.get().id(), UUID.randomUUID(), "Essai " + (i + 1), net.minecraft.util.RandomSource.create(),
                    EconomyClock.now());
        }
        return dire(ctx, "CARTES total=" + registre.cardsOf(op.get().id()).size());
    }

    /** L'état d'une carte par son identifiant (jamais son numéro). */
    private static int carte(CommandContext<CommandSourceStack> ctx) {
        UUID id;
        try {
            id = UUID.fromString(StringArgumentType.getString(ctx, "id"));
        } catch (IllegalArgumentException e) {
            return dire(ctx, "REFUS identifiant illisible");
        }
        Optional<SimCard> c = PhoneOperators.get(ctx.getSource().getServer()).card(id);
        return dire(ctx, "CARTE etat=" + c.map(x -> x.state().name()).orElse("ABSENTE") + " titulaire="
                + c.map(x -> x.holderName().isEmpty() ? "-" : nettoyer(x.holderName())).orElse("-"));
    }

    /**
     * Une carte de cet opérateur émise par la console au nom de {@code titulaire} (un joueur en ligne, réel ou simulé) et
     * remise dans son inventaire, comme si le fondateur la lui avait tendue (le vrai client des captures n'est pas
     * fondateur de tous les opérateurs).
     */
    private static int remettre(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Optional<PhoneOperator> op = operateurNomme(serveur, StringArgumentType.getString(ctx, "operateur"));
        Optional<ServerPlayer> p = joueur(serveur, StringArgumentType.getString(ctx, "titulaire"));
        if (op.isEmpty() || p.isEmpty()) {
            return dire(ctx, "ABSENT operateur=" + op.isPresent() + " titulaire=" + p.isPresent());
        }
        PhoneOperators registre = PhoneOperators.get(serveur);
        SimCard c = registre.issue(op.get().id(), p.get().getUUID(), p.get().getGameProfile().getName(), p.get().getRandom(),
                EconomyClock.now());
        boolean mise = p.get().getInventory().add(SimCardItem.create(c, op.get().name()));
        p.get().containerMenu.broadcastChanges();
        return dire(ctx, "REMETTRE mise=" + mise + " id=" + c.simId());
    }

    private static int placer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<JoueurDeTest> j = present(nom(ctx));
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ServerPlayer p = j.get().joueur();
        p.teleportTo(ctx.getSource().getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
        // Relevé dans la même commande : le joueur simulé n'a pas le temps de tomber d'une place sans sol.
        ItemStack main = p.getMainHandItem();
        var st = main.is(PhoneRegistry.PHONE.get()) ? PhoneNetwork.status(p, main, false) : null;
        return dire(ctx, "PLACER pos=" + xyz(p.blockPosition()) + " sim=" + (st == null ? "-" : st.sim()) + " operateur="
                + (st == null ? "-" : nettoyer(st.operatorName())) + " barres=" + (st == null ? -1 : st.bars()));
    }

    /** Ce que voit le téléphone tenu par ce joueur (simulé ou réel) : carte, opérateur, barres, position. */
    private static int etat(CommandContext<CommandSourceStack> ctx) {
        Optional<ServerPlayer> j = joueur(ctx.getSource().getServer(), nom(ctx));
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = j.get();
        StringBuilder phones = new StringBuilder();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(PhoneRegistry.PHONE.get())) {
                var st = PhoneNetwork.status(p, s, false);
                phones.append(i).append(':').append(st.sim()).append(':').append(nettoyer(st.operatorName())).append(':')
                        .append(st.bars()).append(';');
            }
        }
        StringBuilder cartes = new StringBuilder();
        // Une carte n'est authentique que connue du registre ET porteuse de son jeton courant (PhoneSims, UNKNOWN sinon).
        for (PhoneSims.SimChoice c : PhoneSims.choices(p)) {
            cartes.append(c.slot()).append(':').append(c.validity()).append(':')
                    .append(c.validity() != PhoneSims.Validity.UNKNOWN).append(';');
        }
        ItemStack main = p.getMainHandItem();
        var st = main.is(PhoneRegistry.PHONE.get()) ? PhoneNetwork.status(p, main, false) : null;
        return dire(ctx, "ETAT nom=" + p.getGameProfile().getName() + " pos=" + xyz(p.blockPosition()) + " sim="
                + (st == null ? "-" : st.sim()) + " operateur=" + (st == null ? "-" : nettoyer(st.operatorName())) + " barres="
                + (st == null ? -1 : st.bars()) + " titulaire=" + (st == null ? "-" : nettoyer(st.holderName())) + " numero="
                + (st == null || st.number().isEmpty() ? "absent" : "present") + " telephones=[" + phones + "] cartes=[" + cartes
                + "] session=" + PhoneService.session(p.getUUID()).isPresent() + " panneau="
                + OperatorPanels.session(p.getUUID()).isPresent());
    }

    /** Le trajet d'un appel de {@code nom} vers la ligne de {@code vers} (téléphone en main de chacun). */
    private static int appel(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Optional<ServerPlayer> de = joueur(serveur, nom(ctx));
        Optional<ServerPlayer> vers = joueur(serveur, StringArgumentType.getString(ctx, "vers"));
        if (de.isEmpty() || vers.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        Optional<SimCard> ligne = PhoneSims.line(serveur, vers.get().getMainHandItem());
        if (ligne.isEmpty()) {
            return dire(ctx, "REFUS l'appele n'a pas de ligne");
        }
        GlobalPos ou = GlobalPos.of(vers.get().level().dimension(), vers.get().blockPosition());
        Either<RelayPlanner.Route, RelayFailure> r = PhoneNetwork.route(serveur, de.get(), de.get().getMainHandItem(), false,
                ligne.get().number(), ou);
        if (r.left().isPresent()) {
            return dire(ctx, "APPEL trajet=oui appelant=" + xyz(r.left().get().caller().pos()) + " appele="
                    + xyz(r.left().get().callee().pos()));
        }
        RelayFailure f = r.right().orElseThrow();
        Component texte = f.describe();
        String cle = texte.getContents() instanceof TranslatableContents t ? t.getKey() : "-";
        return dire(ctx, "APPEL trajet=non raison=" + f.reason() + " operateur=" + nettoyer(f.operatorName()) + " cle=" + cle);
    }

    /** Le joueur (sans droit) tente d'ouvrir l'antenne d'un clic droit, main vide, puis de la casser. */
    private static int intrus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<JoueurDeTest> j = present(nom(ctx));
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = j.get().joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ItemStack tenu = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 180F, 20F);
        j.get().composantsRecus();
        var clic = p.gameMode.useItemOn(p, monde, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, 0.3), Direction.SOUTH, pos, false));
        boolean panneau = OperatorPanels.session(p.getUUID()).isPresent();
        String messagesClic = cles(j.get().composantsRecus());
        boolean casse = p.gameMode.destroyBlock(pos);
        boolean la = monde.getBlockState(pos).is(PhoneRegistry.ANTENNA.get());
        String messagesCasse = cles(j.get().composantsRecus());
        p.setItemInHand(InteractionHand.MAIN_HAND, tenu);
        return dire(ctx, "INTRUS clic=" + clic + " panneau=" + panneau + " messagesClic=" + messagesClic + " casse=" + casse
                + " antenneLa=" + la + " messagesCasse=" + messagesCasse);
    }

    /**
     * Un maire simulé, élu et rendu dans le même tic, joue {@code /operator remove <operateur> <motif>} puis
     * {@code confirm} de sa propre source ; {@code motif} nul : un motif fait de signes de mise en forme seulement, qui
     * doit être refusé (rien d'armé, la confirmation qui suit ne trouve rien).
     */
    private static int maire(CommandContext<CommandSourceStack> ctx, String motif) {
        MinecraftServer serveur = ctx.getSource().getServer();
        ServerLevel monde = ctx.getSource().getLevel();
        String op = StringArgumentType.getString(ctx, "operateur");
        Optional<UUID> avant = RoleSavedData.get(serveur).book().mayor();
        String nomAvant = avant.map(id -> {
            ServerPlayer en = serveur.getPlayerList().getPlayer(id);
            if (en != null) {
                return en.getGameProfile().getName();
            }
            for (String n : List.of("Dev", E2eTresorServeur.AGENT)) {
                if (id.equals(E2eTresorServeur.uuidDe(n))) {
                    return n;
                }
            }
            return serveur.getProfileCache() == null ? null
                    : serveur.getProfileCache().get(id).map(g -> g.getName()).orElse(null);
        }).orElse(null);
        if (avant.isPresent() && nomAvant == null) {
            return dire(ctx, "REFUS maire d'avant introuvable");
        }
        EconomyLedger.get(serveur).markStartingGiven(E2eTresorServeur.uuidDe("Maire"));
        JoueurDeTest maire = JoueurDeTest.rejoindre(monde, "Maire", Vec3.atBottomCenterOf(monde.getSharedSpawnPos()), false,
                p -> {});
        var console = serveur.createCommandSourceStack();
        try {
            serveur.getCommands().performPrefixedCommand(console, "role mayor set Maire");
            boolean elu = RoleSavedData.get(serveur).book().mayor().map(maire.uuid()::equals).orElse(false);
            maire.composantsRecus();
            maire.clesRecues();
            // Deux signes de mise en forme (U+00A7) : rien ne reste après le nettoyage du motif.
            String m = motif == null ? String.valueOf((char) 0xA7).repeat(2) : motif;
            int r1 = serveur.getCommands().performPrefixedCommand(maire.joueur().createCommandSourceStack(),
                    "operator remove " + op + " " + m);
            List<String> cles1 = new ArrayList<>(maire.clesRecues());
            int r2 = serveur.getCommands().performPrefixedCommand(maire.joueur().createCommandSourceStack(),
                    "operator remove " + op + " confirm");
            List<String> cles2 = new ArrayList<>(maire.clesRecues());
            return dire(ctx, "MAIRE elu=" + elu + " operateurMc=" + maire.joueur().hasPermissions(2) + " demande=" + r1
                    + " clesDemande=[" + String.join(",", cles1) + "] confirme=" + r2 + " clesConfirme=["
                    + String.join(",", cles2) + "] existe=" + operateurNomme(serveur, op).isPresent());
        } finally {
            if (nomAvant == null) {
                serveur.getCommands().performPrefixedCommand(console, "role mayor remove");
            } else {
                serveur.getCommands().performPrefixedCommand(console, "role mayor set " + nomAvant);
            }
            maire.quitter();
        }
    }

    // --------------------------------------------------------------------------------------------- lectures

    /**
     * L'opérateur : tarifs, plafond, caisse, demandes, cartes (état et titulaire, jamais le numéro), antennes (en service,
     * consommation du dernier tic et du jour). {@code ABSENT} s'il n'existe pas.
     */
    private static int operateur(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        PhoneOperators r = PhoneOperators.get(serveur);
        Optional<PhoneOperator> found = operateurNomme(serveur, StringArgumentType.getString(ctx, "operateur"));
        if (found.isEmpty()) {
            return dire(ctx, "ABSENT plafond=" + r.tariffCap());
        }
        PhoneOperator op = found.get();
        long now = serveur.overworld().getGameTime();
        StringBuilder antennes = new StringBuilder();
        int enService = 0;
        for (AntennaRecord a : r.antennasOf(op.id())) {
            boolean service = AntennaBlockEntity.inService(a, now);
            enService += service ? 1 : 0;
            antennes.append(xyz(a.pos())).append(':').append(service).append(':').append(a.last().standby()).append('+')
                    .append(a.last().clients()).append('+').append(a.last().traffic()).append(':').append(a.feToday()).append(':')
                    .append(a.id().equals(op.mainAntenna())).append(';');
        }
        StringBuilder cartes = new StringBuilder();
        Map<String, Integer> etats = new LinkedHashMap<>();
        for (SimCard c : r.cardsOf(op.id())) {
            etats.merge(c.state().name(), 1, Integer::sum);
            if (!c.holderName().startsWith("Essai ")) {
                cartes.append(c.simId()).append(':').append(c.holderName().isEmpty() ? "-" : nettoyer(c.holderName())).append(':')
                        .append(c.state()).append(':').append(c.phoneId() != null).append(';');
            }
        }
        StringBuilder demandes = new StringBuilder();
        for (SimRequest d : r.requestsOf(op.id())) {
            demandes.append(d.id()).append(':').append(nettoyer(d.playerName())).append(';');
        }
        return dire(ctx, "OPERATEUR id=" + op.id() + " nom=[" + op.name() + "] fondateur=" + op.founderName() + " tarif="
                + op.tariff() + " tarifDemain=" + (op.nextTariff() == null ? "-" : op.nextTariff()) + " plafond=" + r.tariffCap()
                + " caisse=" + CoinMath.valueOf(op.till()) + " actives=" + r.activeCards(op.id()) + " etats=" + etats.toString()
                .replace(' ', '_') + " enService=" + enService + " antennes=[" + antennes + "] cartes=[" + cartes + "] demandes=["
                + demandes + "]");
    }

    /** Le bloc en {@code pos} : antenne ou non, état, réserve, en service, ticket, chunk, distance du joueur le plus proche. */
    private static int antenne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        boolean charge = monde.isLoaded(pos);
        double plusPres = Double.MAX_VALUE;
        for (ServerPlayer p : monde.getServer().getPlayerList().getPlayers()) {
            if (p.level() == monde) {
                plusPres = Math.min(plusPres, Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(pos))));
            }
        }
        String base = " charge=" + charge + " ticket=" + AntennaChunks.isHeld(monde, pos) + " joueurs="
                + monde.getServer().getPlayerList().getPlayerCount() + " plusPres=" + (plusPres == Double.MAX_VALUE ? -1 : (long) plusPres);
        if (!charge || !(monde.getBlockEntity(pos) instanceof AntennaBlockEntity be)) {
            return dire(ctx, "ANTENNE absente=true" + base);
        }
        boolean service = be.record().map(r -> AntennaBlockEntity.inService(r, monde.getGameTime())).orElse(false);
        return dire(ctx, "ANTENNE absente=false etat=" + monde.getBlockState(pos).getValue(ElectricMachineBlock.STATE)
                + " reserve=" + be.energy().getEnergyStored() + " inscrite=" + be.record().isPresent() + " enService=" + service
                + " operateur=" + be.operator().map(PhoneOperator::name).orElse("-") + " parTic=" + be.breakdown().total() + base);
    }

    private static int attente(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        HeldGoods.Pending p = HeldGoods.get(ctx.getSource().getServer()).pendingFor(E2eTresorServeur.uuidDe(nom));
        return dire(ctx, "ATTENTE nom=" + nom + " valeur=" + CoinMath.valueOf(p.coins()) + " objets=" + p.items().size());
    }

    private static int jour(CommandContext<CommandSourceStack> ctx) {
        LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(), EconomyConfig.payHour());
        long minutes = Math.floorDiv(HorlogeDEssai.decalage() + 59_999L, 60_000L);
        return dire(ctx, "JOUR jour=" + jour + " decalage=" + minutes);
    }
}
