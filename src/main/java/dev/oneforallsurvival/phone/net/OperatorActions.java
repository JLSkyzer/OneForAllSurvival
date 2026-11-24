package dev.oneforallsurvival.phone.net;

import com.mojang.logging.LogUtils;
import dev.oneforallsurvival.economy.CoinMath;
import dev.oneforallsurvival.economy.EconomyClock;
import dev.oneforallsurvival.phone.PhoneConfig;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Le seul jeu de règles des gestes de l'opérateur (lot T2, tâche 9) : le panneau du bloc, celui du téléphone et l'appli
 * « Antenne » envoient le même {@link OperatorActionPacket}, et tout est rejoué ici, sans rien croire du client.
 *
 * <p>Dans cet ordre : joueur parti ou faux joueur (rien) ; panneau ouvert par ce joueur sous ce numéro
 * ({@link OperatorPanels}, sinon rien) ; frein d'un geste tous les {@value #MIN_TICKS_BETWEEN} tics (rien) ; le panneau
 * tient toujours (antenne chargée, à {@value OperatorPanels#MAX_DISTANCE} blocs au plus, ayant droit, pour le bloc ;
 * session du téléphone ouverte, déverrouillée, carte {@code ACTIVE} de l'opérateur, une barre, hors mode avion, pour le
 * téléphone ; sinon il est fermé) ; puis les droits de la vue ({@link OperatorViews#build}, donc {@link AntennaRules}),
 * la carte, la demande ou l'antenne <b>de cet opérateur</b>. {@code NAME} et {@code OPEN_TILL} sur le bloc seulement,
 * {@code OPEN_TILL} sur l'antenne principale. Une carte {@code DEACTIVATED} ne se réactive pas. Tout geste, refusé ou
 * fait, est suivi de la vue (si elle a changé) ; un refus n'a pas d'autre effet qu'un message.
 *
 * <p>Ni jeton, ni téléphone dans un message ou un journal.
 */
public final class OperatorActions {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String MESSAGE = "message.oneforallsurvival.operator_panel.";
    /** Frein : un geste tous les {@value #MIN_TICKS_BETWEEN} tics par joueur, comme le téléphone. */
    public static final int MIN_TICKS_BETWEEN = 2;
    private static final Map<UUID, Integer> LAST_GESTURE = new ConcurrentHashMap<>();

    /** L'issue d'un geste, pour les essais. */
    public enum Result {
        /** Le geste est fait. */
        DONE,
        /** Refusé (droit, état, inventaire plein, tarif au-dessus du plafond…) : la vue seule. */
        REFUSED,
        /** Panneau absent ou d'un autre numéro, carte, demande ou antenne inconnue ou d'un autre opérateur. */
        STALE,
        /** Le panneau ne tient plus (trop loin, antenne disparue, téléphone périmé) : il est fermé. */
        CLOSED,
        /** Joueur parti ou faux joueur. */
        GONE,
        /** Trop tôt après le geste précédent. */
        THROTTLED
    }

    private OperatorActions() {}

    public static Result apply(ServerPlayer player, OperatorActionPacket packet) {
        if (gone(player)) {
            return Result.GONE;
        }
        Optional<OperatorPanels.Panel> open = OperatorPanels.current(player.getUUID());
        if (open.isEmpty() || open.get().session != packet.session()) {
            return Result.STALE;
        }
        OperatorPanels.Panel panel = open.get();
        if (throttled(player)) {
            return Result.THROTTLED;
        }
        if (packet.action() == OperatorActionPacket.Action.CLOSE) {
            OperatorPanels.close(player.getUUID());
            return Result.CLOSED;
        }
        if (!OperatorPanels.stillValid(player, panel)) {
            OperatorPanels.close(player.getUUID());
            return Result.CLOSED;
        }
        Optional<PhoneOperator> operator = OperatorPanels.operatorOf(player, panel);
        OperatorView view = OperatorPanels.view(player, panel);
        Result result = operator.isEmpty() ? unfounded(player, panel, view, packet)
                : act(player, panel, view, operator.get(), packet);
        if (result != Result.CLOSED && OperatorPanels.current(player.getUUID()).orElse(null) == panel) {
            OperatorPanels.send(player, panel);
        }
        return result;
    }

    /** Une antenne à fonder : seul le nom, sur le bloc, par qui peut la fonder. */
    private static Result unfounded(ServerPlayer player, OperatorPanels.Panel panel, OperatorView view,
            OperatorActionPacket packet) {
        if (packet.action() == OperatorActionPacket.Action.PAGE) {
            return Result.DONE;
        }
        if (packet.action() != OperatorActionPacket.Action.NAME) {
            return Result.STALE;
        }
        if (panel.source != OperatorView.Source.BLOCK || !view.rights().name()) {
            tell(player, Component.translatable(MESSAGE + "not_allowed"));
            return Result.REFUSED;
        }
        Optional<PhoneOperators.Result> founded = AntennaBlock.found(player, panel.pos, packet.arg());
        if (founded.isEmpty()) {
            tell(player, Component.translatable(MESSAGE + "not_allowed"));
            return Result.REFUSED;
        }
        PhoneOperators.Result r = founded.get();
        switch (r.status()) {
            case FOUNDED -> {
                panel.operatorId = r.operator().id();
                tell(player, Component.translatable(MESSAGE + "founded", r.operator().name()));
            }
            case BAD_NAME -> tell(player, Component.translatable(MESSAGE + "bad_name", 24));
            case NAME_TAKEN -> tell(player, Component.translatable(MESSAGE + "name_taken", packet.arg().strip()));
            case ALREADY_HAS_ONE -> tell(player, Component.translatable(MESSAGE + "already_has_one"));
        }
        return r.founded() ? Result.DONE : Result.REFUSED;
    }

    private static Result act(ServerPlayer player, OperatorPanels.Panel panel, OperatorView view, PhoneOperator op,
            OperatorActionPacket packet) {
        PhoneOperators registry = PhoneOperators.get(player.server);
        OperatorView.Rights rights = view.rights();
        return switch (packet.action()) {
            case NAME -> {
                tell(player, Component.translatable(MESSAGE + "not_allowed"));
                yield Result.REFUSED;
            }
            case SET_TARIFF -> setTariff(player, registry, op, rights, packet.value());
            case ISSUE, REFUSE -> request(player, registry, op, rights, packet);
            case ISSUE_ANONYMOUS -> {
                if (!rights.cards()) {
                    yield notAllowed(player);
                }
                Optional<ItemStack> issued = PhoneSims.issueTo(player, op, null, "", EconomyClock.now());
                if (issued.isEmpty()) {
                    tell(player, Component.translatable(MESSAGE + "inventory_full"));
                    yield Result.REFUSED;
                }
                tell(player, Component.translatable(MESSAGE + "issued_anonymous"));
                yield Result.DONE;
            }
            case SUSPEND, REACTIVATE, REPLACE -> card(player, registry, op, rights, packet);
            case SET_MAIN -> {
                if (!rights.cards()) {
                    yield notAllowed(player);
                }
                UUID id = uuid(packet.arg());
                if (id == null || registry.antenna(id).filter(a -> a.operatorId().equals(op.id())).isEmpty()) {
                    yield Result.STALE;
                }
                registry.setMainAntenna(op.id(), id);
                tell(player, Component.translatable(MESSAGE + "main_set"));
                yield Result.DONE;
            }
            case PAGE -> {
                if (packet.value() < 0L || packet.value() >= Math.max(1, view.cardPages())) {
                    yield Result.STALE;
                }
                panel.page = (int) packet.value();
                yield Result.DONE;
            }
            case OPEN_TILL -> {
                if (panel.source != OperatorView.Source.BLOCK || !rights.till()) {
                    tell(player, Component.translatable(MESSAGE + (AntennaRules.mayTakeTill(player, op)
                            ? "till_main_only" : "not_allowed")));
                    yield Result.REFUSED;
                }
                OperatorPanels.close(player.getUUID());
                OperatorTillMenu.open(player, panel.pos, op);
                yield Result.CLOSED;
            }
            case CLOSE -> Result.CLOSED;
        };
    }

    private static Result setTariff(ServerPlayer player, PhoneOperators registry, PhoneOperator op,
            OperatorView.Rights rights, long value) {
        if (!rights.tariff()) {
            return notAllowed(player);
        }
        long max = Math.min(PhoneConfig.phoneTariffMax(), registry.nextTariffCap().orElse(registry.tariffCap()));
        if (value < 0L || value > max || !registry.setNextTariff(op.id(), value)) {
            tell(player, Component.translatable(MESSAGE + "tariff_above_cap", CoinMath.format(max)));
            return Result.REFUSED;
        }
        tell(player, Component.translatable(MESSAGE + "tariff_set", CoinMath.format(value)));
        LOGGER.info("Telephone : {} fixe le tarif de {} a {} cuivres pour demain", player.getGameProfile().getName(),
                op.name(), value);
        return Result.DONE;
    }

    /** Émettre ou refuser une demande de cet opérateur ; le demandeur est prévenu (à sa connexion s'il est absent). */
    private static Result request(ServerPlayer player, PhoneOperators registry, PhoneOperator op,
            OperatorView.Rights rights, OperatorActionPacket packet) {
        if (!rights.cards()) {
            return notAllowed(player);
        }
        UUID id = uuid(packet.arg());
        Optional<SimRequest> request = id == null ? Optional.empty()
                : registry.requestsOf(op.id()).stream().filter(r -> r.id().equals(id)).findFirst();
        if (request.isEmpty()) {
            return Result.STALE;
        }
        SimRequest r = request.get();
        if (packet.action() == OperatorActionPacket.Action.ISSUE) {
            Optional<ItemStack> issued = PhoneSims.issueTo(player, op, r.player(), r.playerName(), EconomyClock.now());
            if (issued.isEmpty()) {
                tell(player, Component.translatable(MESSAGE + "inventory_full"));
                return Result.REFUSED;
            }
            registry.takeRequest(r.id());
            tell(player, Component.translatable(MESSAGE + "issued", r.playerName()));
            notify(player.server, registry, r.player(), Component.translatable(MESSAGE + "sim_issued", op.name()));
            return Result.DONE;
        }
        registry.takeRequest(r.id());
        tell(player, Component.translatable(MESSAGE + "refused", r.playerName()));
        notify(player.server, registry, r.player(), Component.translatable(MESSAGE + "sim_refused", op.name()));
        return Result.DONE;
    }

    /** Suspendre, réactiver ou remplacer une carte de cet opérateur. */
    private static Result card(ServerPlayer player, PhoneOperators registry, PhoneOperator op,
            OperatorView.Rights rights, OperatorActionPacket packet) {
        if (!rights.cards()) {
            return notAllowed(player);
        }
        UUID id = uuid(packet.arg());
        Optional<SimCard> card = id == null ? Optional.empty()
                : registry.card(id).filter(c -> c.operatorId().equals(op.id()));
        if (card.isEmpty()) {
            return Result.STALE;
        }
        SimCard c = card.get();
        String number = SimNumbers.format(c.number());
        switch (packet.action()) {
            case SUSPEND -> {
                if (c.state() != SimCard.State.ACTIVE || !registry.setState(c.simId(), SimCard.State.SUSPENDED)) {
                    return Result.REFUSED;
                }
                tell(player, Component.translatable(MESSAGE + "suspended", number));
            }
            case REACTIVATE -> {
                // Une carte désactivée l'est pour toujours : seule une suspendue revient.
                if (c.state() != SimCard.State.SUSPENDED || !registry.setState(c.simId(), SimCard.State.ACTIVE)) {
                    return Result.REFUSED;
                }
                tell(player, Component.translatable(MESSAGE + "reactivated", number));
            }
            default -> {
                if (c.state() == SimCard.State.DEACTIVATED) {
                    return Result.REFUSED;
                }
                if (PhoneSims.replaceCard(player, c.simId()).isEmpty()) {
                    tell(player, Component.translatable(MESSAGE + "inventory_full"));
                    return Result.REFUSED;
                }
                tell(player, Component.translatable(MESSAGE + "replaced", number));
                LOGGER.info("Telephone : {} remplace la carte {} de {}", player.getGameProfile().getName(), number,
                        op.name());
            }
        }
        return Result.DONE;
    }

    // ---------------------------------------------------------------------------------------------
    // Aides
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static UUID uuid(String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            UUID id = UUID.fromString(text);
            return id.toString().equals(text) ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Result notAllowed(ServerPlayer player) {
        tell(player, Component.translatable(MESSAGE + "not_allowed"));
        return Result.REFUSED;
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Prévient ce joueur : tout de suite s'il est en ligne, sinon à sa connexion (avis gardé au registre). */
    static void notify(MinecraftServer server, PhoneOperators registry, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(message);
        } else {
            registry.addNotice(player, message);
        }
    }

    private static boolean gone(@Nullable ServerPlayer player) {
        return player == null || player instanceof FakePlayer || player.connection == null
                || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player;
    }

    /** Vrai si ce joueur a déjà fait un geste il y a moins de {@link #MIN_TICKS_BETWEEN} tics ; sinon note ce tic. */
    private static boolean throttled(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer previous = LAST_GESTURE.get(player.getUUID());
        if (previous != null && now - previous >= 0 && now - previous < MIN_TICKS_BETWEEN) {
            return true;
        }
        LAST_GESTURE.put(player.getUUID(), now);
        return false;
    }

    /** Oublie le frein de ce joueur (déconnexion ; essais qui enchaînent des gestes dans le même tic). */
    public static void clearThrottle(UUID player) {
        LAST_GESTURE.remove(player);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearThrottle(event.getEntity().getUUID());
    }
}
