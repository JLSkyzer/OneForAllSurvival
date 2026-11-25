package dev.oneforallsurvival.phone.net;

import dev.oneforallsurvival.bench.BenchNetwork;
import dev.oneforallsurvival.phone.PhoneData;
import dev.oneforallsurvival.phone.PhoneItem;
import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.PhoneSession;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Les panneaux de l'opérateur ouverts (lot T2, tâche 9), côté serveur seulement et jamais sauvegardés : un par joueur au
 * plus, sous un numéro qui grandit à chaque ouverture (un geste d'un autre numéro est périmé).
 *
 * <ul>
 *   <li>Depuis le bloc ({@link #openFromBlock}) : le joueur doit rester à {@value #MAX_DISTANCE} blocs au plus du centre
 *       de l'antenne, dont le chunk reste chargé, et garder la main sur elle ({@link AntennaRules#mayManage}).
 *   <li>Depuis le téléphone ({@link #openFromPhone}) : le fondateur, téléphone ouvert et déverrouillé par lui, toujours en
 *       main, carte {@code ACTIVE} de son opérateur, une barre au moins, hors mode avion.
 * </ul>
 *
 * Toutes les {@value #REFRESH_TICKS} tics, chaque panneau est revérifié (fermé sinon) et sa vue renvoyée si elle a
 * changé : jamais deux fois de suite la même. Fermé à la déconnexion, au geste {@code CLOSE} et à l'ouverture de la
 * caisse. Fil du serveur.
 */
public final class OperatorPanels {

    public static final int REFRESH_TICKS = 20;
    public static final int MAX_DISTANCE = 8;
    public static final double MAX_DISTANCE_SQR = (double) MAX_DISTANCE * MAX_DISTANCE;

    /** Un panneau ouvert. {@code operatorId} : l'opérateur qu'il montre (nul : antenne à fonder, sur le bloc). */
    static final class Panel {
        final int session;
        final OperatorView.Source source;
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        /** Le numéro de la session du téléphone qui l'a ouvert (source {@code PHONE}). */
        final int phoneSession;
        @Nullable
        String operatorId;
        int page;
        @Nullable
        OperatorView sent;

        Panel(int session, OperatorView.Source source, ResourceKey<Level> dimension, BlockPos pos, int phoneSession,
                @Nullable String operatorId) {
            this.session = session;
            this.source = source;
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.phoneSession = phoneSession;
            this.operatorId = operatorId;
        }
    }

    private static final Map<UUID, Panel> OPEN = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();

    private OperatorPanels() {}

    // ---------------------------------------------------------------------------------------------
    // Ouverture et fermeture
    // ---------------------------------------------------------------------------------------------

    /**
     * Ouvre le panneau de l'antenne en {@code pos} (clic droit d'un ayant droit, vérifié par l'appelant : l'antenne de
     * {@link AntennaBlock#use}) et envoie sa vue. Remplace le panneau déjà ouvert de ce joueur.
     */
    public static void openFromBlock(ServerPlayer player, BlockPos pos) {
        AntennaBlockEntity be = OperatorViews.loadedAntenna(player.serverLevel(), pos);
        String operatorId = be == null ? null : be.operator().map(PhoneOperator::id).orElse(null);
        Panel panel = new Panel(NEXT.incrementAndGet(), OperatorView.Source.BLOCK, player.level().dimension(), pos, 0,
                operatorId);
        OPEN.put(player.getUUID(), panel);
        send(player, panel);
    }

    /**
     * L'appli « Antenne » : ouvre le panneau du fondateur depuis son téléphone ({@code session}, ouverte par lui) et
     * envoie sa vue.
     *
     * @return faux, et rien ne s'ouvre, exactement quand {@link #canOpenFromPhone} est faux : le joueur ne fonde pas
     *         d'opérateur, la session n'est pas déverrouillée, il n'est pas le propriétaire du téléphone, mode avion, pas
     *         de carte {@code ACTIVE} de son opérateur ou aucune barre
     */
    public static boolean openFromPhone(ServerPlayer player, PhoneSession session) {
        Optional<PhoneOperator> op = appOperator(player, session);
        if (op.isEmpty()) {
            return false;
        }
        Panel panel = new Panel(NEXT.incrementAndGet(), OperatorView.Source.PHONE, player.level().dimension(),
                BlockPos.ZERO, session.id(), op.get().id());
        OPEN.put(player.getUUID(), panel);
        send(player, panel);
        return true;
    }

    /**
     * Le serveur ouvrirait-il l'appli « Antenne » depuis ce téléphone maintenant ? La vue du téléphone grise l'icône
     * d'après cette réponse ({@code PhoneNetView.founderAppAvailable}) et {@link #openFromPhone} refuse d'après la même :
     * l'icône grisée et le refus ne peuvent pas diverger.
     */
    public static boolean canOpenFromPhone(ServerPlayer player, PhoneSession session) {
        return appOperator(player, session).isPresent();
    }

    /**
     * La seule condition de l'appli « Antenne » (lot T2, tâche 13b) : l'opérateur que ce joueur fonde, si le téléphone de
     * cette session vaut l'appli ({@link #phoneValid}) ; vide sinon. Tout changement de la règle se fait ici ou dans
     * {@code phoneValid}.
     */
    private static Optional<PhoneOperator> appOperator(ServerPlayer player, PhoneSession session) {
        if (player instanceof FakePlayer || session == null) {
            return Optional.empty();
        }
        return PhoneOperators.get(player.server).byFounder(player.getUUID())
                .filter(op -> phoneValid(player, session, op.id()));
    }

    /** Le panneau ouvert de ce joueur. */
    static Optional<Panel> current(UUID player) {
        return Optional.ofNullable(OPEN.get(player));
    }

    /** Le numéro du panneau ouvert de ce joueur (essais et écrans) ; vide s'il n'en a pas. */
    public static Optional<Integer> session(UUID player) {
        return current(player).map(p -> p.session);
    }

    /** Ferme le panneau de ce joueur sans rien envoyer (le client l'a demandé, ou il est parti). */
    public static void close(UUID player) {
        OPEN.remove(player);
    }

    /**
     * Le serveur ferme le panneau de ce joueur (il ne tient plus, ou la caisse s'ouvre) et le dit au client
     * ({@link OperatorNoticePacket}, {@code closed}) : l'écran ou l'appli ne reste pas sur un numéro mort.
     */
    static void closeByServer(ServerPlayer player, Panel panel) {
        if (OPEN.remove(player.getUUID(), panel)) {
            sendNotice(player, new OperatorNoticePacket(panel.session, true, net.minecraft.network.chat.Component.empty()));
        }
    }

    /**
     * Ferme tout de suite (le client en est prévenu) les panneaux ouverts qui montrent cet opérateur, sur un bloc ou dans
     * l'appli « Antenne » d'un téléphone ; seulement ceux de {@code onlyPlayer} s'il n'est pas nul (geste du maire,
     * tâche 11 : opérateur supprimé, ou ancien fondateur après un changement de propriétaire).
     *
     * @return le nombre de panneaux fermés
     */
    public static int closeOperator(MinecraftServer server, String operatorId, @Nullable UUID onlyPlayer) {
        int closed = 0;
        for (Map.Entry<UUID, Panel> entry : new ArrayList<>(OPEN.entrySet())) {
            Panel panel = entry.getValue();
            if (!operatorId.equals(panel.operatorId) || (onlyPlayer != null && !onlyPlayer.equals(entry.getKey()))) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                OPEN.remove(entry.getKey(), panel);
            } else {
                closeByServer(player, panel);
            }
            closed++;
        }
        return closed;
    }

    /** Le retour d'un geste, pour la ligne d'état du panneau ouvert de ce joueur (rien s'il n'en a pas). */
    static void tellPanel(ServerPlayer player, net.minecraft.network.chat.Component message) {
        Panel panel = OPEN.get(player.getUUID());
        if (panel != null) {
            sendNotice(player, new OperatorNoticePacket(panel.session, false, message));
        }
    }

    private static void sendNotice(ServerPlayer player, OperatorNoticePacket notice) {
        if (player.connection != null) {
            BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), notice);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Validité
    // ---------------------------------------------------------------------------------------------

    /** Le panneau tient-il toujours (voir la documentation de la classe) ? */
    static boolean stillValid(ServerPlayer player, Panel panel) {
        if (panel.source == OperatorView.Source.PHONE) {
            Optional<PhoneSession> session = PhoneService.session(player.getUUID());
            return panel.operatorId != null && session.isPresent() && session.get().id() == panel.phoneSession
                    && PhoneOperators.get(player.server).byId(panel.operatorId)
                            .filter(op -> op.founder().equals(player.getUUID())).isPresent()
                    && phoneValid(player, session.get(), panel.operatorId);
        }
        if (!player.level().dimension().equals(panel.dimension) || !player.serverLevel().isLoaded(panel.pos)
                || player.distanceToSqr(Vec3.atCenterOf(panel.pos)) > MAX_DISTANCE_SQR) {
            return false;
        }
        AntennaBlockEntity be = OperatorViews.loadedAntenna(player.serverLevel(), panel.pos);
        return be != null && AntennaRules.mayManage(player, AntennaBlock.owner(player.serverLevel(), panel.pos));
    }

    /**
     * Le téléphone de cette session vaut-il encore une appli « Antenne » : déverrouillé, toujours en main, propriétaire,
     * hors mode avion, carte {@code ACTIVE} de l'opérateur {@code operatorId}, une barre au moins.
     */
    static boolean phoneValid(ServerPlayer player, PhoneSession session, String operatorId) {
        if (session.lock() != PhoneStatePacket.Lock.UNLOCKED) {
            return false;
        }
        ItemStack stack = player.getItemInHand(session.hand());
        if (!PhoneItem.phoneId(stack).equals(Optional.of(session.phoneId()))) {
            return false;
        }
        PhoneData.PhoneRecord record = PhoneData.get(player.server).record(session.phoneId());
        if (!player.getUUID().equals(record.owner()) || record.airplane()) {
            return false;
        }
        Optional<SimCard> line = PhoneSims.line(player.server, stack);
        if (line.isEmpty() || line.get().state() != SimCard.State.ACTIVE
                || !line.get().operatorId().equals(operatorId)) {
            return false;
        }
        return PhoneNetwork.status(player, stack, false).bars() >= 1;
    }

    // ---------------------------------------------------------------------------------------------
    // La vue
    // ---------------------------------------------------------------------------------------------

    /** L'opérateur que montre le panneau maintenant : celui de l'antenne (sur le bloc), ou celui du fondateur. */
    static Optional<PhoneOperator> operatorOf(ServerPlayer player, Panel panel) {
        PhoneOperators registry = PhoneOperators.get(player.server);
        if (panel.source == OperatorView.Source.BLOCK) {
            AntennaBlockEntity be = OperatorViews.loadedAntenna(player.serverLevel(), panel.pos);
            Optional<PhoneOperator> op = be == null ? Optional.empty() : be.operator();
            panel.operatorId = op.map(PhoneOperator::id).orElse(null);
            return op;
        }
        return registry.byId(panel.operatorId).filter(op -> op.founder().equals(player.getUUID()));
    }

    /** La vue du panneau, telle que {@link OperatorViews#build} la rend maintenant. */
    static OperatorView view(ServerPlayer player, Panel panel) {
        return OperatorViews.build(player, PhoneOperators.get(player.server), operatorOf(player, panel).orElse(null),
                panel.source, panel.pos, panel.session, panel.page);
    }

    /** Envoie la vue du panneau si elle n'est pas celle que le client a déjà. */
    static void send(ServerPlayer player, Panel panel) {
        OperatorView view = view(player, panel);
        if (view.equals(panel.sent)) {
            return;
        }
        panel.sent = view;
        BenchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OperatorPanelPacket(view));
    }

    // ---------------------------------------------------------------------------------------------
    // Bus de Forge
    // ---------------------------------------------------------------------------------------------

    /** Toutes les {@value #REFRESH_TICKS} tics : chaque panneau revérifié, fermé s'il ne tient plus, sinon sa vue. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || OPEN.isEmpty()
                || event.getServer().getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Map.Entry<UUID, Panel> entry : new ArrayList<>(OPEN.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Panel panel = entry.getValue();
            if (player == null) {
                OPEN.remove(entry.getKey(), panel);
            } else if (!stillValid(player, panel)) {
                closeByServer(player, panel);
            } else if (OPEN.get(entry.getKey()) == panel) {
                send(player, panel);
            }
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }
}
