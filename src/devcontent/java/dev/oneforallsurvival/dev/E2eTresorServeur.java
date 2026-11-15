package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.TreasuryAccount;
import dev.oneforallsurvival.economy.payroll.Payroll;
import dev.oneforallsurvival.economy.town.TreasuryActionPacket;
import dev.oneforallsurvival.economy.town.TreasuryBlock;
import dev.oneforallsurvival.economy.town.TreasuryLedger;
import dev.oneforallsurvival.role.RoleSavedData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout du trésor et des salaires (développement seulement, jamais empaqueté). Des
 * commandes réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur pendant la
 * séquence), sur le modèle d'{@link E2eCommerceServeur} :
 *
 * <ul>
 *   <li>{@code e2etresor agent entrer|quitter|etat|ouvrir|casser} : le joueur simulé {@code Agent} ({@link JoueurDeTest}),
 *       qui passe par les mêmes événements de connexion et de déconnexion qu'un vrai joueur ; ses gestes passent par les
 *       mêmes chemins que ceux d'un client ({@link TreasuryBlock#open}, {@link TreasuryActionPacket#apply}, casse du mode
 *       de jeu du serveur) ;
 *   <li>{@code e2etresor tresor | registre <depuis> | paie | coffre <nom> | attente <nom> | maire} : ce que le serveur
 *       garde, lu sans rien changer ;
 *   <li>{@code e2etresor horloge [<minutes> | frontiere <secondes>]} : le décalage de {@link EconomyClock}
 *       ({@link HorlogeDEssai}) ; l'essai n'attend jamais un vrai minuit.
 * </ul>
 *
 * <p>Chaque réponse est une suite {@code cle=valeur} (sans espace dans les valeurs, ou entre crochets) terminée par
 * {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eTresorServeur {

    static final String AGENT = "Agent";
    private static final String FIN = " FIN_E2E";
    /** Au plus autant de lignes du registre par réponse (une réponse RCON tient en 4 096 octets). */
    private static final int LIGNES_PAR_REPONSE = 20;

    private static JoueurDeTest agent;

    private E2eTresorServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2etresor").requires(s -> s.hasPermission(4))
                .then(Commands.literal("agent")
                        .then(Commands.literal("entrer").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eTresorServeur::entrer)))
                        .then(Commands.literal("quitter").executes(E2eTresorServeur::quitter))
                        .then(Commands.literal("etat").executes(E2eTresorServeur::etat))
                        .then(Commands.literal("ouvrir").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eTresorServeur::ouvrir)))
                        .then(Commands.literal("casser").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eTresorServeur::casser))))
                .then(Commands.literal("tresor").executes(E2eTresorServeur::tresor))
                .then(Commands.literal("registre").then(Commands.argument("depuis", IntegerArgumentType.integer(0))
                        .executes(E2eTresorServeur::registre)))
                .then(Commands.literal("paie").executes(E2eTresorServeur::paie))
                .then(Commands.literal("coffre").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTresorServeur::coffre)))
                .then(Commands.literal("attente").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eTresorServeur::attente)))
                .then(Commands.literal("maire").executes(E2eTresorServeur::maire))
                .then(Commands.literal("horloge").executes(E2eTresorServeur::horloge)
                        .then(Commands.literal("frontiere").then(Commands.argument("secondes", IntegerArgumentType.integer(1, 3_600))
                                .executes(E2eTresorServeur::horlogeFrontiere)))
                        .then(Commands.argument("minutes", LongArgumentType.longArg(-1_000_000L, 1_000_000L))
                                .executes(E2eTresorServeur::horlogeMinutes))));
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    static UUID uuidDe(String nom) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nom).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean present() {
        return agent != null && agent.connecte() && agent.joueur().server.getPlayerList().getPlayer(agent.uuid()) == agent.joueur();
    }

    private static String nettoyer(String texte) {
        return texte.replace(';', ',').replace('[', '(').replace(']', ')');
    }

    // --------------------------------------------------------------------------------------------- Agent

    private static int entrer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (present()) {
            return dire(ctx, "REFUS : Agent est deja connecte");
        }
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        long attenteAvant = CoinMath.valueOf(HeldGoods.get(monde.getServer()).pendingFor(uuidDe(AGENT)).coins());
        agent = JoueurDeTest.rejoindre(monde, AGENT, Vec3.atBottomCenterOf(pos), false, p -> p.setYRot(180F));
        ServerPlayer p = agent.joueur();
        // L'entrée en jeu relit la position du playerdata (celle de sa dernière sortie) : il est ramené ici, regard au nord.
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180F, 0F);
        p.setYHeadRot(180F);
        long attenteApres = CoinMath.valueOf(HeldGoods.get(monde.getServer()).pendingFor(uuidDe(AGENT)).coins());
        return dire(ctx, "AGENT porte=" + CoinMath.valueOf(PlayerCoins.held(p)) + " operateur=" + p.hasPermissions(2)
                + " attenteAvant=" + attenteAvant + " attenteApres=" + attenteApres + " connexion=["
                + nettoyer(String.join(",", agent.messagesALaConnexion())) + "] textes=["
                + nettoyer(String.join(" | ", agent.textesALaConnexion())) + "]");
    }

    private static int quitter(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            agent = null;
            return dire(ctx, "ABSENT");
        }
        long porte = CoinMath.valueOf(PlayerCoins.held(agent.joueur()));
        agent.quitter();
        agent = null;
        return dire(ctx, "PARTI porte=" + porte);
    }

    private static int etat(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = agent.joueur();
        return dire(ctx, "ETAT porte=" + CoinMath.valueOf(PlayerCoins.held(p)) + " role="
                + RoleSavedData.get(p.server).book().roleOf(p.getUUID()).id() + " menu=" + p.containerMenu.getClass().getSimpleName()
                + " messages=[" + nettoyer(String.join(",", agent.messagesRecus())) + "] textes=["
                + nettoyer(String.join(" | ", agent.textesRecus())) + "]");
    }

    /**
     * Agent, deux blocs au sud du trésor : le clic droit ({@link TreasuryBlock#open}, comme {@code use}), puis la demande
     * de retrait d'une pièce d'or qu'enverrait un client qui forge le paquet (motif valable).
     */
    private static int ouvrir(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = agent.joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 180F, 20F);
        agent.messagesRecus();
        boolean ouvert = TreasuryBlock.open(p, pos);
        String menu = p.containerMenu.getClass().getSimpleName();
        CoinActions.Result retrait = TreasuryActionPacket.apply(p,
                new TreasuryActionPacket(pos, CoinRequest.withdraw(Coin.GOLD, 1), "essai d'un citoyen"));
        return dire(ctx, "OUVRIR ouvert=" + ouvert + " menu=" + menu + " retrait=" + retrait + " messages=["
                + nettoyer(String.join(",", agent.messagesRecus())) + "]");
    }

    /** Agent casse le bloc comme le ferait un clic gauche tenu jusqu'au bout (le chemin du serveur : {@code destroyBlock}). */
    private static int casser(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = agent.joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        agent.messagesRecus();
        boolean casse = p.gameMode.destroyBlock(pos);
        return dire(ctx, "CASSER casse=" + casse + " tresor=" + monde.getBlockState(pos).is(EconomyRegistry.TREASURY.get())
                + " messages=[" + nettoyer(String.join(",", agent.messagesRecus())) + "]");
    }

    // --------------------------------------------------------------------------------------------- lectures

    private static int tresor(CommandContext<CommandSourceStack> ctx) {
        TreasuryAccount t = TreasuryAccount.get(ctx.getSource().getServer());
        String position = t.position().map(g -> g.pos().getX() + "," + g.pos().getY() + "," + g.pos().getZ()).orElse("aucune");
        return dire(ctx, "TRESOR total=" + t.total() + " position=" + position + " lignes=" + t.ledger().size());
    }

    /** Toutes les lignes du registre du trésor, de la plus ancienne à la plus récente. */
    static List<TreasuryLedger.Line> lignes(TreasuryAccount t) {
        List<TreasuryLedger.Line> out = new ArrayList<>();
        for (int page = t.ledger().pageCount() - 1; page >= 0; page--) {
            List<TreasuryLedger.Line> l = new ArrayList<>(t.ledger().page(page));
            Collections.reverse(l);
            out.addAll(l);
        }
        return out;
    }

    /** Les lignes du registre à partir de l'index {@code depuis} (0 : la plus ancienne), au plus vingt par réponse. */
    private static int registre(CommandContext<CommandSourceStack> ctx) {
        List<TreasuryLedger.Line> toutes = lignes(TreasuryAccount.get(ctx.getSource().getServer()));
        int depuis = IntegerArgumentType.getInteger(ctx, "depuis");
        StringBuilder sb = new StringBuilder();
        int fin = Math.min(toutes.size(), depuis + LIGNES_PAR_REPONSE);
        for (int i = depuis; i < fin; i++) {
            TreasuryLedger.Line l = toutes.get(i);
            sb.append(l.kind()).append('/').append(nettoyer(l.actorName())).append('/').append(l.amount()).append('/')
                    .append(nettoyer(l.reason())).append(';');
        }
        return dire(ctx, "REGISTRE taille=" + toutes.size() + " suite=" + fin + " lignes=[" + sb + "]");
    }

    /** La paie : dernier jour réglé, jour en cours, dettes par ancienneté, temps compté du jour pour Dev et Agent. */
    private static int paie(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Payroll paie = Payroll.get(serveur);
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), zone, EconomyConfig.payHour());
        StringBuilder dettes = new StringBuilder();
        for (Payroll.Debt d : paie.debts()) {
            dettes.append(d.id()).append(':').append(d.playerName()).append(':').append(d.key()).append(':').append(d.day())
                    .append(':').append(d.amount()).append(';');
        }
        StringBuilder temps = new StringBuilder();
        for (String nom : List.of("Dev", AGENT)) {
            Map<String, Long> secondes = paie.secondsFor(uuidDe(nom), jour);
            Map<String, Long> cuivreSecondes = paie.rateSecondsFor(uuidDe(nom), jour);
            for (Map.Entry<String, Long> e : secondes.entrySet()) {
                temps.append(nom).append(':').append(e.getKey()).append(':').append(e.getValue()).append(':')
                        .append(cuivreSecondes.getOrDefault(e.getKey(), 0L)).append(';');
            }
        }
        return dire(ctx, "PAIE regle=" + paie.lastClosed() + " jour=" + jour + " du=" + paie.owedTotal() + " nombre="
                + paie.debts().size() + " dettes=[" + dettes + "] temps=[" + temps + "] reel=" + System.currentTimeMillis());
    }

    private static int coffre(CommandContext<CommandSourceStack> ctx) {
        String nom = StringArgumentType.getString(ctx, "nom");
        SafeAccounts comptes = SafeAccounts.get(ctx.getSource().getServer());
        UUID id = uuidDe(nom);
        return dire(ctx, "COFFRE nom=" + nom + " total=" + comptes.total(id) + " pose=" + comptes.safePos(id).isPresent());
    }

    private static int attente(CommandContext<CommandSourceStack> ctx) {
        String nom = StringArgumentType.getString(ctx, "nom");
        HeldGoods.Pending p = HeldGoods.get(ctx.getSource().getServer()).pendingFor(uuidDe(nom));
        return dire(ctx, "ATTENTE nom=" + nom + " valeur=" + CoinMath.valueOf(p.coins()) + " objets=" + p.items().size());
    }

    private static int maire(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        Optional<UUID> maire = RoleSavedData.get(serveur).book().mayor();
        String nom = maire.map(id -> {
            ServerPlayer p = serveur.getPlayerList().getPlayer(id);
            if (p != null) {
                return p.getGameProfile().getName();
            }
            return id.equals(uuidDe("Dev")) ? "Dev" : id.equals(uuidDe(AGENT)) ? AGENT : id.toString();
        }).orElse("aucun");
        String roleDev = RoleSavedData.get(serveur).book().roleOf(uuidDe("Dev")).id();
        return dire(ctx, "MAIRE nom=" + nom + " roleDev=" + roleDev);
    }

    // --------------------------------------------------------------------------------------------- horloge

    private static String etatHorloge() {
        long ms = HorlogeDEssai.decalage();
        long now = EconomyClock.now();
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate jour = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
        long frontiere = EconomyCalendar.boundaryAfter(jour, zone, EconomyConfig.payHour());
        // En minutes entières, arrondi vers le haut : la relance passe ce nombre (plus deux jours) à -Pe2eHorloge et ne
        // peut ainsi jamais lire une heure antérieure à celle d'avant l'arrêt.
        long minutes = Math.floorDiv(ms + 59_999L, 60_000L);
        return "HORLOGE decalage=" + ms + " minutes=" + minutes + " maintenant=" + now + " jour=" + jour + " frontiere=" + frontiere
                + " dans=" + (frontiere - now);
    }

    private static int horloge(CommandContext<CommandSourceStack> ctx) {
        return dire(ctx, etatHorloge());
    }

    private static int horlogeMinutes(CommandContext<CommandSourceStack> ctx) {
        HorlogeDEssai.regler(LongArgumentType.getLong(ctx, "minutes") * 60_000L);
        return dire(ctx, etatHorloge());
    }

    /** Règle l'horloge pour que la frontière du jour de paie en cours tombe dans {@code secondes} secondes. */
    private static int horlogeFrontiere(CommandContext<CommandSourceStack> ctx) {
        int secondes = IntegerArgumentType.getInteger(ctx, "secondes");
        long reel = System.currentTimeMillis();
        long now = EconomyClock.now();
        ZoneId zone = EconomyCalendar.configuredZone();
        LocalDate jour = EconomyCalendar.payDay(now, zone, EconomyConfig.payHour());
        long frontiere = EconomyCalendar.boundaryAfter(jour, zone, EconomyConfig.payHour());
        HorlogeDEssai.regler(frontiere - secondes * 1_000L - reel);
        return dire(ctx, etatHorloge());
    }
}
