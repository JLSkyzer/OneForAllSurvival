package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.CoinActions;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.CoinRequest;
import dev.oneforallsurvival.economy.EconomyCalendar;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.economy.EconomyConfig;
import dev.oneforallsurvival.economy.EconomyLedger;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.MoneyTransfer;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.SafeAccounts;
import dev.oneforallsurvival.economy.SafeStore;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.grid.BreakerBlock;
import dev.oneforallsurvival.grid.GridRegistry;
import dev.oneforallsurvival.grid.supplier.PowerBilling;
import dev.oneforallsurvival.grid.supplier.PowerClient;
import dev.oneforallsurvival.grid.supplier.PowerCommands;
import dev.oneforallsurvival.grid.supplier.PowerSupplier;
import dev.oneforallsurvival.grid.supplier.PowerSuppliers;
import dev.oneforallsurvival.machine.MachineEnergyStorage;
import dev.oneforallsurvival.role.RoleSavedData;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout du prélèvement et du fournisseur d'électricité (lot E6, développement
 * seulement, jamais empaqueté). Des commandes réservées à la console (niveau 4, donc au RCON : le joueur de l'essai
 * n'est pas opérateur pendant ses gestes), sur le modèle d'{@link E2eQuetesServeur} et d'{@link E2eTresorServeur} :
 *
 * <ul>
 *   <li>{@code e2eelec entrer <nom> <pos> <depart>} et {@code quitter <nom>} : un joueur simulé ({@link JoueurDeTest}),
 *       qui passe par les mêmes événements de connexion et de déconnexion qu'un vrai joueur ; {@code depart} faux : la
 *       somme de départ est notée donnée avant sa première entrée (aucune pièce créée) ;
 *   <li>{@code coffre <nom> <pos>} : son coffre-fort posé par le crochet de pose du bloc ({@code setPlacedBy}, joueur
 *       réel), puis « Déposer tout » par le chemin des écrans ({@link CoinActions#apply}) ;
 *   <li>{@code accord <nom>} : le lien « [Accepter] » du message d'accord qu'il a reçu, joué en son nom (la commande du
 *       lien, par le répartiteur, source du joueur) ;
 *   <li>{@code intrus <nom> <disjoncteur> <compteur>} : le joueur tente de réenclencher le disjoncteur (clic droit, main
 *       vide) puis de casser le compteur (chemin de casse du serveur) ;
 *   <li>{@code maire plafond <cuivres>} : un maire simulé, élu le temps d'un tic, joue {@code /power cap} ;
 *   <li>{@code tarifs <fondateur>} : les tarifs du lendemain de son fournisseur entrent en vigueur tout de suite, par
 *       la clôture du jour du fournisseur ({@link PowerSuppliers#closeSupplierDay}), comme à minuit ;
 *   <li>{@code charge <pos> | charge stop} : la machine d'essai en {@code pos} est vidée à 20 FE à chaque tic (comme
 *       {@code laMesureNeDependPasDuCable}), pour qu'elle tire 100 FE par tic au lieu de 10 ;
 *   <li>{@code fournisseur <fondateur> | machine <pos> | factures <fondateur> | registre | journal <depuis> |
 *       chunk <pos> | jour} : ce que le serveur garde, lu sans rien changer.
 * </ul>
 *
 * <p>Chaque réponse est une suite {@code cle=valeur} (sans espace dans les valeurs, ou entre crochets) terminée par
 * {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eElectriciteServeur {

    private static final String FIN = " FIN_E2E";
    /** Ce que la vidange laisse dans la machine : elle travaille (10 FE par tic) et accepte ses 100 FE par tic. */
    private static final int RESTE_CHARGE = 20;

    private static final Map<String, JoueurDeTest> JOUEURS = new LinkedHashMap<>();
    private static final List<BlockPos> CHARGES = new ArrayList<>();
    private static long vidange;

    private E2eElectriciteServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2eelec").requires(s -> s.hasPermission(4))
                .then(Commands.literal("entrer").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("depart", BoolArgumentType.bool()).executes(E2eElectriciteServeur::entrer)))))
                .then(Commands.literal("quitter").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eElectriciteServeur::quitter)))
                .then(Commands.literal("coffre").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(E2eElectriciteServeur::coffre))))
                .then(Commands.literal("accord").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(E2eElectriciteServeur::accord)))
                .then(Commands.literal("intrus").then(Commands.argument("nom", StringArgumentType.word())
                        .then(Commands.argument("disjoncteur", BlockPosArgument.blockPos())
                                .then(Commands.argument("compteur", BlockPosArgument.blockPos())
                                        .executes(E2eElectriciteServeur::intrus)))))
                .then(Commands.literal("maire").then(Commands.literal("plafond")
                        .then(Commands.argument("cuivres", LongArgumentType.longArg(0L))
                                .executes(E2eElectriciteServeur::maire))))
                .then(Commands.literal("tarifs").then(Commands.argument("fondateur", StringArgumentType.word())
                        .executes(E2eElectriciteServeur::tarifs)))
                .then(Commands.literal("charge")
                        .then(Commands.literal("stop").executes(E2eElectriciteServeur::chargeStop))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(E2eElectriciteServeur::charge)))
                .then(Commands.literal("fournisseur").then(Commands.argument("fondateur", StringArgumentType.word())
                        .executes(E2eElectriciteServeur::fournisseur)))
                .then(Commands.literal("machine").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eElectriciteServeur::machine)))
                .then(Commands.literal("factures").then(Commands.argument("fondateur", StringArgumentType.word())
                        .executes(E2eElectriciteServeur::factures)))
                .then(Commands.literal("registre").executes(E2eElectriciteServeur::registre))
                .then(Commands.literal("journal").then(Commands.argument("depuis", LongArgumentType.longArg(0L))
                        .executes(E2eElectriciteServeur::journal)))
                .then(Commands.literal("chunk").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(E2eElectriciteServeur::chunk)))
                .then(Commands.literal("reseau").then(Commands.argument("poste", BlockPosArgument.blockPos())
                        .then(Commands.argument("a", BlockPosArgument.blockPos())
                                .then(Commands.argument("b", BlockPosArgument.blockPos())
                                        .then(Commands.argument("fils", BoolArgumentType.bool())
                                                .executes(E2eElectriciteServeur::reseau))))))
                .then(Commands.literal("jour").executes(E2eElectriciteServeur::jour)));
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    private static String nettoyer(String texte) {
        return texte.replace(';', ',').replace('[', '(').replace(']', ')').replace('\n', '|');
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

    private static String cles(List<Component> messages) {
        List<String> out = new ArrayList<>();
        for (Component c : messages) {
            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
                out.add(t.getKey());
            } else {
                out.add(nettoyer(c.getString()));
            }
        }
        return "[" + String.join(",", out) + "]";
    }

    private static String commandes(List<Component> messages) {
        List<String> out = new ArrayList<>();
        for (Component c : messages) {
            out.addAll(JoueurDeTest.commandes(c));
        }
        return "[" + nettoyer(String.join(",", out)).replace(' ', '_') + "]";
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
        EconomyLedger registre = EconomyLedger.get(monde.getServer());
        boolean depart = BoolArgumentType.getBool(ctx, "depart");
        boolean sansDepart = !depart && registre.markStartingGiven(id);
        JoueurDeTest j = JoueurDeTest.rejoindre(monde, nom, Vec3.atBottomCenterOf(pos), false, p -> {});
        ServerPlayer p = j.joueur();
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
        JOUEURS.put(nom, j);
        List<Component> connexion = j.composantsALaConnexion();
        return dire(ctx, "ENTRE nom=" + nom + " porte=" + CoinMath.valueOf(PlayerCoins.held(p)) + " sansDepart=" + sansDepart
                + " operateur=" + p.hasPermissions(2) + " role=" + RoleSavedData.get(p.server).book().roleOf(id).id()
                + " connexion=" + cles(connexion) + " liens=" + commandes(connexion));
    }

    private static int quitter(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        JOUEURS.remove(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        long porte = CoinMath.valueOf(PlayerCoins.held(j.get().joueur()));
        j.get().quitter();
        return dire(ctx, "PARTI nom=" + nom + " porte=" + porte);
    }

    /** Le coffre-fort du joueur, posé à son nom par le crochet de pose du bloc ; puis « Déposer tout ». */
    private static int coffre(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        ServerPlayer p = j.get().joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockState etat = EconomyRegistry.SAFE.get().defaultBlockState();
        monde.setBlock(pos, etat, 3);
        EconomyRegistry.SAFE.get().setPlacedBy(monde, pos, etat, p, new ItemStack(EconomyRegistry.SAFE.get()));
        long porteAvant = CoinMath.valueOf(PlayerCoins.held(p));
        SafeAccounts comptes = SafeAccounts.get(monde.getServer());
        CoinActions.Result r = porteAvant > 0
                ? CoinActions.apply(p, new SafeStore(comptes, p.getUUID()), CoinRequest.DEPOSIT_ALL, null, 0, false)
                : CoinActions.Result.NOTHING;
        return dire(ctx, "COFFRE nom=" + nom + " resultat=" + r + " porteAvant=" + porteAvant + " porte="
                + CoinMath.valueOf(PlayerCoins.held(p)) + " total=" + comptes.total(p.getUUID()) + " pose="
                + comptes.safePos(p.getUUID()).map(g -> g.pos().equals(pos)).orElse(false));
    }

    /** Le lien « [Accepter] » reçu (en jeu ou à la connexion), joué comme un clic du joueur : sa commande, en son nom. */
    private static int accord(CommandContext<CommandSourceStack> ctx) {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        List<Component> recus = new ArrayList<>(j.get().composantsALaConnexion());
        recus.addAll(j.get().composantsRecus());
        String lien = null;
        String demande = "";
        for (Component c : recus) {
            for (String commande : JoueurDeTest.commandes(c)) {
                if (commande.startsWith("/power consent ")) {
                    lien = commande;
                    demande = c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
                }
            }
        }
        if (lien == null) {
            return dire(ctx, "REFUS aucun lien d'accord recu : " + cles(recus));
        }
        ServerPlayer p = j.get().joueur();
        int r = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), lien.substring(1));
        List<Component> apres = j.get().composantsRecus();
        return dire(ctx, "ACCORD nom=" + nom + " demande=" + demande + " lien=" + lien.replace(' ', '_') + " resultat=" + r
                + " messages=" + cles(apres));
    }

    /** Le joueur tente de réenclencher le disjoncteur (clic droit, main vide), puis de casser le compteur. */
    private static int intrus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String nom = nom(ctx);
        Optional<JoueurDeTest> j = present(nom);
        if (j.isEmpty()) {
            return dire(ctx, "ABSENT nom=" + nom);
        }
        ServerPlayer p = j.get().joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos disj = BlockPosArgument.getLoadedBlockPos(ctx, "disjoncteur");
        BlockPos compteur = BlockPosArgument.getLoadedBlockPos(ctx, "compteur");
        j.get().composantsRecus();
        boolean fermeAvant = monde.getBlockState(disj).getValue(BreakerBlock.CLOSED);
        p.teleportTo(monde, disj.getX() + 0.5, disj.getY(), disj.getZ() + 1.5, 180F, 30F);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var clic = p.gameMode.useItemOn(p, monde, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(disj).add(0, 0, 0.5), Direction.SOUTH, disj, false));
        boolean fermeApres = monde.getBlockState(disj).getValue(BreakerBlock.CLOSED);
        String messagesClic = cles(j.get().composantsRecus());
        p.teleportTo(monde, compteur.getX() + 0.5, compteur.getY(), compteur.getZ() + 1.5, 180F, 30F);
        boolean casse = p.gameMode.destroyBlock(compteur);
        boolean compteurLa = monde.getBlockState(compteur).is(GridRegistry.METER.get());
        String messagesCasse = cles(j.get().composantsRecus());
        return dire(ctx, "INTRUS nom=" + nom + " fermeAvant=" + fermeAvant + " clic=" + clic + " fermeApres=" + fermeApres
                + " messagesClic=" + messagesClic + " casse=" + casse + " compteurLa=" + compteurLa + " messagesCasse=" + messagesCasse);
    }

    /**
     * Un maire simulé, élu et rendu dans le même tic (aucun temps de rôle compté pour la paie), joue {@code /power cap}
     * de sa propre source ; le maire d'avant est rendu.
     */
    private static int maire(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        ServerLevel monde = ctx.getSource().getLevel();
        long cuivres = LongArgumentType.getLong(ctx, "cuivres");
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
            return dire(ctx, "REFUS maire d'avant introuvable : " + avant.get());
        }
        EconomyLedger.get(serveur).markStartingGiven(E2eTresorServeur.uuidDe("Maire"));
        BlockPos ici = monde.getSharedSpawnPos();
        JoueurDeTest maire = JoueurDeTest.rejoindre(monde, "Maire", Vec3.atBottomCenterOf(ici), false, p -> {});
        var console = serveur.createCommandSourceStack();
        try {
            serveur.getCommands().performPrefixedCommand(console, "role mayor set Maire");
            boolean elu = RoleSavedData.get(serveur).book().mayor().map(maire.uuid()::equals).orElse(false);
            maire.composantsRecus();
            maire.clesRecues();
            maire.textesRecus();
            int r = serveur.getCommands().performPrefixedCommand(maire.joueur().createCommandSourceStack(), "power cap " + cuivres);
            List<String> recus = new ArrayList<>(maire.clesRecues());
            String texte = String.join(" | ", maire.textesRecus());
            return dire(ctx, "MAIRE elu=" + elu + " operateur=" + maire.joueur().hasPermissions(2) + " resultat=" + r
                    + " cles=[" + String.join(",", recus) + "] texte=[" + nettoyer(texte) + "] avant=" + (nomAvant == null ? "aucun" : nomAvant)
                    + " plafondDemain=" + PowerSuppliers.get(serveur).nextPriceCap().map(String::valueOf).orElse("-"));
        } finally {
            if (nomAvant == null) {
                serveur.getCommands().performPrefixedCommand(console, "role mayor remove");
            } else {
                serveur.getCommands().performPrefixedCommand(console, "role mayor set " + nomAvant);
            }
            maire.quitter();
        }
    }

    /** Les tarifs du lendemain entrent en vigueur tout de suite, par la clôture du jour du fournisseur (comme à minuit). */
    private static int tarifs(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        PowerSuppliers registre = PowerSuppliers.get(serveur);
        Optional<PowerSupplier> f = registre.byFounder(E2eTresorServeur.uuidDe(StringArgumentType.getString(ctx, "fondateur")));
        if (f.isEmpty()) {
            return dire(ctx, "REFUS aucun fournisseur");
        }
        if (f.get().outToday() != 0L || !registre.clients(f.get().id()).isEmpty()) {
            return dire(ctx, "REFUS le fournisseur a deja debite ou des clients : sortie " + f.get().outToday());
        }
        var clos = registre.closeSupplierDay(f.get().id(), 0L).orElseThrow();
        PowerSupplier apres = registre.byId(f.get().id()).orElseThrow();
        return dire(ctx, "TARIFS avant=" + clos.priceBefore() + "/" + clos.subscriptionBefore() + " prix=" + apres.price()
                + " abonnement=" + apres.subscription() + " plafonne=" + clos.capped());
    }

    // --------------------------------------------------------------------------------------------- charges

    private static int charge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (!CHARGES.contains(pos)) {
            CHARGES.add(pos.immutable());
        }
        return dire(ctx, "CHARGE n=" + CHARGES.size());
    }

    private static int chargeStop(CommandContext<CommandSourceStack> ctx) {
        CHARGES.clear();
        return dire(ctx, "CHARGE n=0 vidange=" + vidange);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHARGES.isEmpty()) {
            return;
        }
        ServerLevel monde = event.getServer().overworld();
        for (BlockPos pos : CHARGES) {
            if (monde.isLoaded(pos) && monde.getBlockEntity(pos) instanceof TestMachineBlockEntity m
                    && m.energy() instanceof MachineEnergyStorage reserve && reserve.getEnergyStored() > RESTE_CHARGE) {
                vidange += reserve.consume(reserve.getEnergyStored() - RESTE_CHARGE);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        CHARGES.clear();
        for (JoueurDeTest j : JOUEURS.values()) {
            if (j.connecte()) {
                j.quitter();
            }
        }
        JOUEURS.clear();
    }

    // --------------------------------------------------------------------------------------------- lectures

    private static String client(PowerClient c) {
        return c.subscriber() + ":" + c.playerName() + ":" + c.state() + ":" + c.feToday() + ":" + c.feYesterday() + ":" + c.cut()
                + ":" + c.breakers().size() + ":" + c.meterSerial();
    }

    private static int fournisseur(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        PowerSuppliers r = PowerSuppliers.get(serveur);
        Optional<PowerSupplier> f = r.byFounder(E2eTresorServeur.uuidDe(StringArgumentType.getString(ctx, "fondateur")));
        if (f.isEmpty()) {
            return dire(ctx, "AUCUN");
        }
        PowerSupplier s = f.get();
        StringBuilder clients = new StringBuilder();
        long compteurs = 0;
        for (PowerClient c : r.clients(s.id())) {
            clients.append(client(c)).append(';');
            compteurs += c.feToday();
        }
        return dire(ctx, "FOURNISSEUR id=" + s.id() + " nom=[" + nettoyer(s.name()) + "] prix=" + s.price() + " abonnement="
                + s.subscription() + " prixDemain=" + (s.nextPrice() == null ? "-" : s.nextPrice()) + " abonnementDemain="
                + (s.nextSubscription() == null ? "-" : s.nextSubscription()) + " plafond=" + r.priceCap() + " plafondDemain="
                + r.nextPriceCap().map(String::valueOf).orElse("-") + " taxe=" + r.taxPercent() + " caisse=" + s.tillValue()
                + " sortie=" + s.outToday() + " compteurs=" + compteurs + " sortieHier=" + s.outYesterday() + " compteursHier="
                + s.metersYesterday() + " poste=" + (s.station() == null ? "-" : s.station().pos().toShortString().replace(" ", ""))
                + " clients=[" + clients + "]");
    }

    private static int machine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (!monde.isLoaded(pos) || !(monde.getBlockEntity(pos) instanceof TestMachineBlockEntity m)) {
            return dire(ctx, "MACHINE absente=true charge=" + monde.isLoaded(pos));
        }
        return dire(ctx, "MACHINE energie=" + m.energy().getEnergyStored() + " progres=" + m.progress());
    }

    /** Les factures publiques dont ce fondateur est le créancier, de la plus ancienne à la plus récente. */
    private static int factures(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        InvoiceRegistry r = InvoiceRegistry.get(serveur);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (InvoiceRegistry.Invoice f : r.issuedBy(E2eTresorServeur.uuidDe(StringArgumentType.getString(ctx, "fondateur")))) {
            if (!f.isPublic()) {
                continue;
            }
            n++;
            var ref = PowerBilling.ref(f);
            sb.append(f.id()).append(':').append(f.debtorName()).append(':').append(f.amount()).append(':').append(f.status())
                    .append(':').append(ref.map(x -> LocalDate.ofEpochDay(x.epochDay()).toString()).orElse("?"))
                    .append(':').append(ref.map(x -> x.fe()).orElse(-1L)).append(':').append(ref.map(x -> x.price()).orElse(-1L))
                    .append(':').append(ref.map(x -> x.subscription()).orElse(-1L)).append(':').append(f.settledAt()).append(';');
        }
        return dire(ctx, "FACTURES n=" + n + " presente=" + r.lastDebitDay() + " emis=" + r.lastIssuedDay() + " liste=[" + sb + "]");
    }

    private static int registre(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer serveur = ctx.getSource().getServer();
        EconomyLedger l = EconomyLedger.get(serveur);
        InvoiceRegistry factures = InvoiceRegistry.get(serveur);
        PowerCommands.Board board = PowerCommands.board(PowerSuppliers.get(serveur), factures);
        long dues = 0;
        for (InvoiceRegistry.Invoice f : factures.duePublic()) {
            dues += f.amount();
        }
        return dire(ctx, "REGISTRE refus=" + l.refusedDebits() + " payees=" + l.electricityBillsPaid() + " paye=" + l.electricityPaidTotal()
                + " public=" + l.totalTransferred(MoneyTransfer.PUBLIC_BILL) + " taxe=" + l.totalTransferred(MoneyTransfer.POWER_TAX)
                + " lignes=" + l.entryCount() + " coffres=" + SafeAccounts.get(serveur).totalMass() + " caisses=" + board.tillTotal()
                + " fournisseurs=" + board.rows().size() + " dues=" + factures.duePublic().size() + " duesTotal=" + dues
                + " circulation=" + l.inCirculation() + " reel=" + System.currentTimeMillis());
    }

    /** Les lignes du journal de l'économie plus récentes que {@code depuis} (heure réelle, ms) : nombre et genres. */
    private static int journal(CommandContext<CommandSourceStack> ctx) {
        EconomyLedger l = EconomyLedger.get(ctx.getSource().getServer());
        long depuis = LongArgumentType.getLong(ctx, "depuis");
        Map<String, Integer> genres = new LinkedHashMap<>();
        int n = 0;
        boolean fini = false;
        for (int page = 0; page < l.pageCount() && !fini; page++) {
            for (EconomyLedger.Entry e : l.entries(page)) {
                if (e.epochMillis() <= depuis) {
                    fini = true;
                    break;
                }
                n++;
                genres.merge(e.kind(), 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        genres.forEach((k, v) -> sb.append(k).append(':').append(v).append(';'));
        return dire(ctx, "JOURNAL lignes=" + l.entryCount() + " nouvelles=" + n + " genres=[" + sb + "]");
    }

    private static int chunk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ServerLevel monde = ctx.getSource().getLevel();
        return dire(ctx, "CHUNK charge=" + monde.isLoaded(pos) + " joueurs=" + monde.getServer().getPlayerList().getPlayerCount());
    }

    /**
     * Le fil suspendu de Crafts &amp; Additions : {@code fils} faux, un connecteur sur la sortie du poste (face est) et un
     * sur l'entrée de chaque compteur (face ouest), posés comme par un joueur ({@link ReseauxDev#cliquer}) ; {@code fils}
     * vrai (un tic plus tard au moins : un connecteur n'a pas de réseau avant son premier tic), le connecteur du poste
     * reçoit en passif (« pull »), ceux des compteurs poussent (« push »), et deux fils sont tirés du poste à chaque maison
     * avec la vraie bobine de cuivre ({@link ReseauxDev#tirerFil}).
     */
    private static int reseau(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos poste = BlockPosArgument.getLoadedBlockPos(ctx, "poste");
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "a");
        BlockPos b = BlockPosArgument.getLoadedBlockPos(ctx, "b");
        List<String> journal = new ArrayList<>();
        BlockPos cPoste = poste.east();
        BlockPos cA = a.west();
        BlockPos cB = b.west();
        if (!BoolArgumentType.getBool(ctx, "fils")) {
            ReseauxDev.cliquer(monde, "createaddition:connector", poste, Direction.EAST, Direction.WEST, 1, journal);
            ReseauxDev.cliquer(monde, "createaddition:connector", a, Direction.WEST, Direction.EAST, 1, journal);
            ReseauxDev.cliquer(monde, "createaddition:connector", b, Direction.WEST, Direction.EAST, 1, journal);
        } else {
            monde.setBlock(cPoste, ReseauxDev.avec(monde.getBlockState(cPoste), "mode", "pull"), 3);
            monde.setBlock(cA, ReseauxDev.avec(monde.getBlockState(cA), "mode", "push"), 3);
            monde.setBlock(cB, ReseauxDev.avec(monde.getBlockState(cB), "mode", "push"), 3);
            ReseauxDev.tirerFil(monde, "createaddition:copper_spool", cPoste, cA, journal);
            ReseauxDev.tirerFil(monde, "createaddition:copper_spool", cPoste, cB, journal);
        }
        String etats = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(monde.getBlockState(cPoste).getBlock()).getPath()
                + ":" + ReseauxDev.propriete(monde.getBlockState(cPoste), "mode") + "," + ReseauxDev.propriete(monde.getBlockState(cA), "mode")
                + "," + ReseauxDev.propriete(monde.getBlockState(cB), "mode");
        return dire(ctx, "RESEAU etats=" + etats + " journal=[" + nettoyer(String.join(" | ", journal)).replace(' ', '_') + "]");
    }

    private static int jour(CommandContext<CommandSourceStack> ctx) {
        InvoiceRegistry r = InvoiceRegistry.get(ctx.getSource().getServer());
        LocalDate jour = EconomyCalendar.payDay(EconomyClock.now(), EconomyCalendar.configuredZone(), EconomyConfig.payHour());
        return dire(ctx, "JOUR jour=" + jour + " presente=" + r.lastDebitDay() + " emis=" + r.lastIssuedDay());
    }
}
