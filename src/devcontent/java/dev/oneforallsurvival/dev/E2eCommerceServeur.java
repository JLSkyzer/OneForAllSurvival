package dev.oneforallsurvival.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.oneforallsurvival.economy.Coin;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyRegistry;
import dev.oneforallsurvival.economy.HeldGoods;
import dev.oneforallsurvival.economy.PlayerCoins;
import dev.oneforallsurvival.economy.Receipt;
import dev.oneforallsurvival.economy.invoice.InvoiceItem;
import dev.oneforallsurvival.economy.invoice.InvoiceRegistry;
import dev.oneforallsurvival.economy.invoice.InvoiceService;
import dev.oneforallsurvival.economy.stall.MarketStallBlock;
import dev.oneforallsurvival.economy.stall.MarketStallBlockEntity;
import dev.oneforallsurvival.economy.stall.StallActionPacket;
import dev.oneforallsurvival.economy.stall.StallBuyback;
import dev.oneforallsurvival.economy.stall.StallCashPacket;
import dev.oneforallsurvival.economy.stall.StallLedger;
import dev.oneforallsurvival.economy.stall.StallOffer;
import dev.oneforallsurvival.economy.stall.StallOwnerMenu;
import dev.oneforallsurvival.economy.trade.TradeMenu;
import dev.oneforallsurvival.economy.trade.TradeService;
import dev.oneforallsurvival.economy.trade.TradeSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le côté serveur de l'essai de bout en bout du commerce (développement seulement, jamais empaqueté). Des commandes
 * réservées à la console (niveau 4, donc au RCON : le joueur de l'essai n'est pas opérateur) pour le second joueur,
 * {@code Autre}, qui n'est pas un vrai client : une connexion simulée ({@link JoueurDeTest}) qui passe par les mêmes
 * événements de connexion et de déconnexion qu'un vrai joueur. Il reste connecté d'une commande à l'autre jusqu'à
 * {@code autre quitter}. Ses gestes passent par les mêmes chemins que les paquets d'un client : clics de menu
 * ({@code clicked}), {@link TradeSession#confirm}, {@link StallActionPacket#apply}, {@link StallCashPacket#apply},
 * {@link InvoiceService#issue} et {@link InvoiceService#pay}, commandes lancées en son nom.
 *
 * <p>Chaque réponse est une suite {@code cle=valeur} (sans espace dans les valeurs) terminée par {@code FIN_E2E}.
 */
@Mod.EventBusSubscriber(modid = "oneforallsurvival", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class E2eCommerceServeur {

    private static final String AUTRE = "Autre";
    private static final String FIN = " FIN_E2E";

    /** Le second joueur, tant qu'il est connecté. */
    private static JoueurDeTest autre;

    private E2eCommerceServeur() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("e2ecommerce").requires(s -> s.hasPermission(4))
                .then(Commands.literal("autre")
                        .then(Commands.literal("entrer").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> entrer(ctx, false))))
                        .then(Commands.literal("retour").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> entrer(ctx, true))))
                        .then(Commands.literal("quitter").executes(E2eCommerceServeur::quitter))
                        .then(Commands.literal("etat").executes(E2eCommerceServeur::etat))
                        .then(Commands.literal("donner").then(Commands.argument("objet", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 64))
                                        .executes(E2eCommerceServeur::donner))))
                        .then(Commands.literal("commande").then(Commands.argument("texte", StringArgumentType.greedyString())
                                .executes(E2eCommerceServeur::commande))))
                .then(Commands.literal("echange")
                        .then(Commands.literal("poser").then(Commands.argument("objet", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 64))
                                        .executes(E2eCommerceServeur::poser))))
                        .then(Commands.literal("confirmer").executes(E2eCommerceServeur::confirmer)))
                .then(Commands.literal("etal")
                        .then(Commands.literal("poser").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eCommerceServeur::poserEtal)))
                        .then(Commands.literal("garnir").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eCommerceServeur::garnir)))
                        .then(Commands.literal("lire").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(E2eCommerceServeur::lireEtal))))
                .then(Commands.literal("facture")
                        .then(Commands.literal("payer").executes(E2eCommerceServeur::payer))
                        .then(Commands.literal("emettre").then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("montant", LongArgumentType.longArg(1))
                                        .then(Commands.argument("motif", StringArgumentType.greedyString())
                                                .executes(E2eCommerceServeur::emettre)))))
                        .then(Commands.literal("copie").then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(E2eCommerceServeur::copie)))))
                .then(Commands.literal("factures").executes(E2eCommerceServeur::factures))
                .then(Commands.literal("comptes").executes(E2eCommerceServeur::comptes)));
    }

    /** Les comptes du serveur en cuivres, sans mise en forme : ce que {@code /eco stats} affiche en pièces. */
    private static int comptes(CommandContext<CommandSourceStack> ctx) {
        var serveur = ctx.getSource().getServer();
        var registre = dev.oneforallsurvival.economy.EconomyLedger.get(serveur);
        StringBuilder sb = new StringBuilder("COMPTES circulation=" + registre.inCirculation()
                + " coffres=" + dev.oneforallsurvival.economy.SafeAccounts.get(serveur).totalMass()
                + " tresor=" + dev.oneforallsurvival.economy.TreasuryAccount.get(serveur).total()
                + " attente=" + HeldGoods.get(serveur).pendingMass());
        for (var genre : dev.oneforallsurvival.economy.MoneyTransfer.values()) {
            sb.append(' ').append(genre.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(registre.totalTransferred(genre));
        }
        return dire(ctx, sb.append(" lignes=").append(registre.entryCount()).toString());
    }

    private static int dire(CommandContext<CommandSourceStack> ctx, String texte) {
        ctx.getSource().sendSuccess(() -> Component.literal(texte + FIN), false);
        return 1;
    }

    private static boolean present() {
        return autre != null && autre.connecte()
                && autre.joueur().server.getPlayerList().getPlayer(autre.uuid()) == autre.joueur();
    }

    /** Ce qu'Autre porte (pièces et bourses), et le compte des objets de l'essai dans son inventaire. */
    private static String inventaire(ServerPlayer p) {
        Map<String, Integer> objets = new TreeMap<>();
        StringBuilder factures = new StringBuilder();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty() || CoinMath.valueOf(s) > 0) {
                continue;
            }
            objets.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
            if (s.is(EconomyRegistry.INVOICE.get())) {
                factures.append(factures.length() == 0 ? "" : ",").append(i).append(':')
                        .append(InvoiceItem.idOf(s).map(UUID::toString).orElse("?")).append(':').append(InvoiceItem.amount(s));
            }
        }
        StringBuilder sb = new StringBuilder();
        objets.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ",").append(k).append(':').append(v));
        return "porte=" + CoinMath.valueOf(PlayerCoins.held(p)) + " objets=[" + sb + "] factures=[" + factures + "]";
    }

    // --------------------------------------------------------------------------------------------- Autre

    private static int entrer(CommandContext<CommandSourceStack> ctx, boolean retour) throws CommandSyntaxException {
        if (present()) {
            return dire(ctx, "REFUS : Autre est deja connecte");
        }
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + AUTRE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long attenteAvant = CoinMath.valueOf(HeldGoods.get(monde.getServer()).pendingFor(id).coins());
        autre = JoueurDeTest.rejoindre(monde, AUTRE, Vec3.atBottomCenterOf(pos), false, p -> p.setYRot(180F));
        ServerPlayer p = autre.joueur();
        // L'entrée en jeu relit la position du playerdata (celle de sa dernière sortie) : il est ramené ici, regard au nord.
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180F, 0F);
        p.setYHeadRot(180F);
        long attenteApres = CoinMath.valueOf(HeldGoods.get(monde.getServer()).pendingFor(id).coins());
        return dire(ctx, "AUTRE " + inventaire(p) + " operateur=" + p.hasPermissions(2) + " attenteAvant=" + attenteAvant
                + " attenteApres=" + attenteApres + (retour ? " connexion=" + String.join(",", autre.messagesALaConnexion()) : ""));
    }

    private static int quitter(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            autre = null;
            return dire(ctx, "ABSENT");
        }
        String etat = inventaire(autre.joueur());
        autre.quitter();
        autre = null;
        return dire(ctx, "PARTI " + etat);
    }

    private static int etat(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        return dire(ctx, "ETAT " + inventaire(p) + " menu=" + p.containerMenu.getClass().getSimpleName() + " messages="
                + String.join(",", autre.messagesRecus()));
    }

    private static int donner(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        var objet = BuiltInRegistries.ITEM.get(net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "objet"));
        int n = IntegerArgumentType.getInteger(ctx, "nombre");
        boolean pris = autre.joueur().getInventory().add(new ItemStack(objet, n));
        return dire(ctx, "DONNE pris=" + pris + " " + inventaire(autre.joueur()));
    }

    private static int commande(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        String texte = StringArgumentType.getString(ctx, "texte");
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), texte);
        return dire(ctx, "COMMANDE menu=" + p.containerMenu.getClass().getSimpleName() + " messages=" + String.join(",", autre.messagesRecus()));
    }

    /**
     * Sélectionne une case vide de la barre d'action. Jamais d'{@code Inventory.add} de ce qui était en main : il fusionne
     * dans la pile même qu'on remplace ensuite (premier passage : les 7 pièces d'or d'Autre ont été perdues ainsi).
     */
    private static boolean enMainVide(ServerPlayer p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).isEmpty()) {
                p.getInventory().selected = i;
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------- échange

    private static int caseInventaire(ServerPlayer p, net.minecraft.world.item.Item objet) {
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).is(objet)) {
                return i;
            }
        }
        return -1;
    }

    /** Autre prend la pile au curseur, en pose une à une {@code nombre} dans sa première case libre (ou de même objet), rend le reste. */
    private static int poser(CommandContext<CommandSourceStack> ctx) {
        if (!present() || !(autre.joueur().containerMenu instanceof TradeMenu menu)) {
            return dire(ctx, "REFUS : pas de fenetre d'echange chez Autre");
        }
        ServerPlayer p = autre.joueur();
        var objet = BuiltInRegistries.ITEM.get(net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "objet"));
        int n = IntegerArgumentType.getInteger(ctx, "nombre");
        int origine = caseInventaire(p, objet);
        if (origine < 0) {
            return dire(ctx, "REFUS : Autre n'a pas cet objet");
        }
        int cible = -1;
        for (int s = TradeMenu.OWN_START; s < TradeMenu.OWN_START + TradeMenu.OFFER_SIZE && cible < 0; s++) {
            ItemStack dans = menu.getSlot(s).getItem();
            if (dans.isEmpty() || dans.is(objet)) {
                cible = s;
            }
        }
        int menuOrigine = TradeMenu.slotOfInventory(origine);
        menu.clicked(menuOrigine, 0, ClickType.PICKUP, p);
        for (int i = 0; i < n; i++) {
            menu.clicked(cible, 1, ClickType.PICKUP, p);
        }
        menu.clicked(menuOrigine, 0, ClickType.PICKUP, p);
        menu.broadcastChanges();
        TradeSession session = TradeService.sessionOf(p.getUUID());
        return dire(ctx, "POSE case=" + cible + " dans=" + menu.getSlot(cible).getItem().getCount() + " curseur=" + menu.getCarried().getCount()
                + " version=" + (session == null ? -1 : session.version()));
    }

    private static int confirmer(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        TradeSession session = TradeService.sessionOf(p.getUUID());
        if (session == null) {
            return dire(ctx, "REFUS : aucun echange");
        }
        TradeSession.ConfirmResult r = session.confirm(p, session.version());
        return dire(ctx, "CONFIRME resultat=" + r + " " + inventaire(p) + " menu=" + p.containerMenu.getClass().getSimpleName()
                + " messages=" + String.join(",", autre.messagesRecus()));
    }

    // --------------------------------------------------------------------------------------------- étal

    /** Autre pose l'étal (l'objet donné en main) sur le bloc sous {@code pos}, comme un clic droit sur sa face du dessus. */
    private static int poserEtal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        ServerLevel monde = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        p.teleportTo(monde, pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 180F, 30F);
        if (!enMainVide(p)) {
            return dire(ctx, "REFUS : aucune case libre dans la barre d'action d'Autre");
        }
        p.getInventory().setItem(p.getInventory().selected, new ItemStack(EconomyRegistry.MARKET_STALL_ITEM.get()));
        BlockPos sol = pos.below();
        var r = p.gameMode.useItemOn(p, monde, p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sol).add(0, 0.5, 0), Direction.UP, sol, false));
        boolean pose = monde.getBlockEntity(pos) instanceof MarketStallBlockEntity be && AUTRE.equals(be.ownerName());
        return dire(ctx, "ETAL_POSE resultat=" + r + " pose=" + pose + " bloc=" + monde.getBlockState(pos).getBlock().getDescriptionId()
                + " main=" + p.getMainHandItem().getCount());
    }

    /**
     * Autre ouvre sa gestion, définit l'offre de vente (le pain, 4 pour 30 cuivres) et l'offre de rachat (le blé, 8
     * pour 16 cuivres, 5 lots) avec le modèle tenu au curseur, range son pain au stock (majuscule-clic), puis ouvre la
     * caisse et y dépose 1 or (100 cuivres).
     */
    private static int garnir(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (!MarketStallBlock.openManage(p, pos) || !(p.containerMenu instanceof StallOwnerMenu menu)) {
            return dire(ctx, "REFUS : la gestion ne s'ouvre pas pour Autre");
        }
        List<String> resultats = new ArrayList<>();
        int pain = caseInventaire(p, Items.BREAD);
        int ble = caseInventaire(p, Items.WHEAT);
        if (pain < 0 || ble < 0) {
            return dire(ctx, "REFUS : pain " + pain + ", ble " + ble);
        }
        int casePain = StallOwnerMenu.INVENTORY_START + (pain < 9 ? 27 + pain : pain - 9);
        int caseBle = StallOwnerMenu.INVENTORY_START + (ble < 9 ? 27 + ble : ble - 9);
        menu.clicked(casePain, 0, ClickType.PICKUP, p);
        resultats.add("offre:" + StallActionPacket.apply(p, StallActionPacket.setOffer(pos, 0, 4, 30L)));
        menu.clicked(casePain, 0, ClickType.PICKUP, p);
        menu.clicked(caseBle, 0, ClickType.PICKUP, p);
        resultats.add("rachat:" + StallActionPacket.apply(p, StallActionPacket.setBuyback(pos, 0, 8, 16L, 5)));
        menu.clicked(caseBle, 0, ClickType.PICKUP, p);
        menu.clicked(casePain, 0, ClickType.QUICK_MOVE, p);
        menu.broadcastChanges();
        p.closeContainer();
        if (!MarketStallBlock.openCash(p, pos)) {
            return dire(ctx, "REFUS : la caisse ne s'ouvre pas pour Autre");
        }
        resultats.add("caisse:" + StallCashPacket.apply(p, StallCashPacket.deposit(pos, Coin.GOLD, 1)));
        p.closeContainer();
        return dire(ctx, "GARNI " + String.join(",", resultats) + " curseur=" + p.containerMenu.getCarried().getCount() + " "
                + inventaire(p) + " " + etal(p.serverLevel(), pos));
    }

    /** L'état complet d'un étal lu sur le serveur : propriétaire, stock, caisse, offres, rachats, registre. */
    static String etal(ServerLevel monde, BlockPos pos) {
        if (!(monde.getBlockEntity(pos) instanceof MarketStallBlockEntity be)) {
            return "etal=ABSENT";
        }
        Map<String, Integer> stock = new TreeMap<>();
        for (int i = 0; i < be.stock().getSlots(); i++) {
            ItemStack s = be.stock().getStackInSlot(i);
            if (!s.isEmpty()) {
                stock.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("proprietaire=" + be.ownerName() + " stock=[");
        StringBuilder st = new StringBuilder();
        stock.forEach((k, v) -> st.append(st.length() == 0 ? "" : ",").append(k).append(':').append(v));
        sb.append(st).append("] caisse=").append(be.cashValue()).append(" offres=[");
        List<StallOffer> offres = be.offers();
        for (int i = 0; i < offres.size(); i++) {
            if (offres.get(i) != null) {
                StallOffer o = offres.get(i);
                sb.append(i).append(':').append(BuiltInRegistries.ITEM.getKey(o.template().getItem()).getPath()).append('x').append(o.lot())
                        .append('@').append(o.price()).append(';');
            }
        }
        sb.append("] rachats=[");
        List<StallBuyback> rachats = be.buybacks();
        for (int i = 0; i < rachats.size(); i++) {
            if (rachats.get(i) != null) {
                StallBuyback b = rachats.get(i);
                sb.append(i).append(':').append(BuiltInRegistries.ITEM.getKey(b.template().getItem()).getPath()).append('x').append(b.lot())
                        .append('@').append(b.price()).append(",reste").append(b.lotsLeft()).append(';');
            }
        }
        sb.append("] registre=").append(be.ledger().size()).append(":[");
        List<StallLedger.Line> lignes = new ArrayList<>();
        for (int page = be.ledger().pages() - 1; page >= 0; page--) {
            List<StallLedger.Line> l = new ArrayList<>(be.ledger().page(page));
            java.util.Collections.reverse(l);
            lignes.addAll(l);
        }
        for (StallLedger.Line l : lignes) {
            sb.append(l.kind()).append('/').append(l.actorName()).append('/').append(l.itemId().replace("minecraft:", "")).append('/')
                    .append(l.count()).append('/').append(l.amount()).append(';');
        }
        return sb.append(']').toString();
    }

    private static int lireEtal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return dire(ctx, "ETAL " + etal(ctx.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")));
    }

    // --------------------------------------------------------------------------------------------- factures

    /** Autre paie la première facture de son inventaire (le chemin du bouton « Payer » : la case et l'identifiant vus). */
    private static int payer(CommandContext<CommandSourceStack> ctx) {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(EconomyRegistry.INVOICE.get())) {
                long avant = CoinMath.valueOf(PlayerCoins.held(p));
                InvoiceService.PayResult r = InvoiceService.pay(p, i, InvoiceItem.idOf(s).orElse(null));
                long apres = CoinMath.valueOf(PlayerCoins.held(p));
                boolean recu = p.getInventory().getItem(i).getItem() instanceof dev.oneforallsurvival.economy.ReceiptItem;
                return dire(ctx, "PAYE resultat=" + r + " avant=" + avant + " apres=" + apres + " recu=" + recu + " genre="
                        + (recu ? Receipt.kind(p.getInventory().getItem(i)) : "-") + " " + inventaire(p));
            }
        }
        return dire(ctx, "REFUS : aucune facture chez Autre " + inventaire(p));
    }

    /** Autre, le carnet en main, émet une facture au joueur (le chemin du formulaire : {@link InvoiceService#issue}). */
    private static int emettre(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!present()) {
            return dire(ctx, "ABSENT");
        }
        ServerPlayer p = autre.joueur();
        ServerPlayer debiteur = EntityArgument.getPlayer(ctx, "joueur");
        if (!p.getMainHandItem().is(EconomyRegistry.INVOICE_BOOK.get())) {
            if (!enMainVide(p)) {
                return dire(ctx, "REFUS : aucune case libre dans la barre d'action d'Autre");
            }
            p.getInventory().setItem(p.getInventory().selected, new ItemStack(EconomyRegistry.INVOICE_BOOK.get()));
        }
        InvoiceService.IssueResult r = InvoiceService.issue(p, debiteur.getUUID(), LongArgumentType.getLong(ctx, "montant"),
                StringArgumentType.getString(ctx, "motif"));
        return dire(ctx, "EMISE resultat=" + r + " distance=" + String.format(java.util.Locale.ROOT, "%.1f", p.distanceTo(debiteur))
                + " messages=" + String.join(",", autre.messagesRecus()));
    }

    /** La console remet au joueur une copie de la facture {@code id}, telle que le registre la connaît. */
    private static int copie(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer joueur = EntityArgument.getPlayer(ctx, "joueur");
        UUID id = UUID.fromString(StringArgumentType.getString(ctx, "id"));
        var facture = InvoiceRegistry.get(joueur.server).get(id);
        if (facture.isEmpty()) {
            return dire(ctx, "REFUS : facture inconnue");
        }
        boolean pris = joueur.getInventory().add(InvoiceItem.of(facture.get()));
        return dire(ctx, "COPIE pris=" + pris + " statut=" + facture.get().status());
    }

    /** Le registre des factures entre les deux joueurs de l'essai, trié par identifiant. */
    private static int factures(CommandContext<CommandSourceStack> ctx) {
        var serveur = ctx.getSource().getServer();
        InvoiceRegistry registre = InvoiceRegistry.get(serveur);
        UUID autreId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + AUTRE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID devId = UUID.nameUUIDFromBytes(("OfflinePlayer:Dev").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, String> lignes = new TreeMap<>();
        for (UUID emetteur : List.of(autreId, devId)) {
            for (InvoiceRegistry.Invoice f : registre.issuedBy(emetteur)) {
                if (f.isPublic()) {
                    continue; // les factures d'électricité (lot E6), dont Dev est le créancier : contrôlées par leur étape
                }
                lignes.put(f.id().toString(), f.issuerName() + ">" + f.debtorName() + ":" + f.amount() + ":" + f.status());
            }
        }
        StringBuilder sb = new StringBuilder();
        lignes.forEach((k, v) -> sb.append(k, 0, 8).append('=').append(v).append(';'));
        return dire(ctx, "FACTURES nombre=" + lignes.size() + " liste=[" + sb + "]");
    }
}
