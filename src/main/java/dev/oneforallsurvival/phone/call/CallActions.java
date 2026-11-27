package dev.oneforallsurvival.phone.call;

import dev.oneforallsurvival.phone.PhoneService;
import dev.oneforallsurvival.phone.call.CallBook.Call;
import dev.oneforallsurvival.phone.call.CallBook.Party;
import dev.oneforallsurvival.phone.call.PhoneCallActionPacket.Action;
import dev.oneforallsurvival.phone.net.PhoneSims;
import dev.oneforallsurvival.phone.net.SimCard;
import dev.oneforallsurvival.phone.net.SimNumbers;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Les gestes d'appel et de carnet de l'écran du téléphone (lot T3, tâche 4a), rejoués par le serveur. D'abord les
 * contrôles de {@link PhoneService#act} ({@link PhoneService#held} : vrai joueur, session de ce numéro, même téléphone
 * dans la même main), sinon rien n'est envoyé. Puis :
 *
 * <ul>
 *   <li>{@link Action#HANGUP} et {@link Action#DECLINE} ne passent jamais par le frein ; tous les autres gestes, oui (le
 *       même que les gestes de l'écran) ;
 *   <li>{@link Action#ANSWER}, {@link Action#DECLINE}, {@link Action#HANGUP} sont permis écran verrouillé ; tout le
 *       reste exige l'écran déverrouillé (les urgences viendront à la tâche 8) ;
 *   <li>un geste sur un appel ne vaut que pour l'appel de ce joueur, porté par le téléphone de la session.
 * </ul>
 *
 * <p>Réponses : un appel composé qui ne sonne pas renvoie l'état actuel avec son message ; {@code SAVE_CONTACT},
 * {@code REMOVE_CONTACT} et {@code OPEN_PHONE_APP} renvoient l'état actuel avec le carnet de la carte logée (et le
 * message d'échec : numéro, nom, carnet plein). Les changements d'appel partent par {@link CallUpdates}.
 */
public final class CallActions {

    /** Préfixe des messages du carnet : {@code bad_number}, {@code bad_name}, {@code full}, {@code no_sim}. */
    public static final String CONTACT = CallService.MESSAGE + "contact.";

    /** Un numéro d'appel : entier positif écrit sans zéro en tête. */
    private static final Pattern CALL_ID = Pattern.compile("[1-9][0-9]{0,9}");

    private CallActions() {}

    public static void act(ServerPlayer player, PhoneCallActionPacket packet) {
        Optional<PhoneService.Held> found = PhoneService.held(player, packet.session());
        if (found.isEmpty()) {
            return;
        }
        PhoneService.Held held = found.get();
        Action action = packet.action();
        boolean neverThrottled = action == Action.HANGUP || action == Action.DECLINE;
        if (!neverThrottled && PhoneService.throttle(player)) {
            return;
        }
        // Écran verrouillé : décrocher, refuser, raccrocher, et composer le 17 ou le 15 (bouton « Urgence »).
        boolean emergency = action == Action.DIAL && Emergency.Service.of(packet.first()).isPresent();
        boolean whileLocked = neverThrottled || action == Action.ANSWER || emergency;
        if (!whileLocked && !held.unlocked()) {
            return;
        }
        UUID phoneId = held.session().phoneId();
        switch (action) {
            case DIAL -> {
                CallService.Dial d = CallService.dial(player, held.phone(), held.record().airplane(), packet.first());
                if (d.outcome() != CallService.Outcome.RINGING) {
                    CallUpdates.send(player, CallUpdates.current(player, d.message(), null));
                }
            }
            case ANSWER -> callOnThisPhone(player, held, packet.first()).ifPresentOrElse(
                    call -> CallService.answer(player, call.id()),
                    () -> pendingId(packet.first()).ifPresent(id -> EmergencyService.answer(player, phoneId, id)));
            case DECLINE -> callOnThisPhone(player, held, packet.first()).ifPresentOrElse(
                    call -> CallService.decline(player, call.id()),
                    () -> pendingId(packet.first()).ifPresent(id -> EmergencyService.decline(player, phoneId, id)));
            case HANGUP -> callOnThisPhone(player, held, packet.first()).ifPresentOrElse(
                    call -> CallService.hangUp(player, call.id()),
                    () -> pendingId(packet.first()).ifPresent(id -> EmergencyService.hangUp(player, phoneId, id)));
            case MUTE -> onOff(packet.first()).ifPresent(on -> {
                if (callOnThisPhone(player, held).isPresent()) {
                    CallService.setMuted(player, on);
                }
            });
            case SPEAKER -> onOff(packet.first()).ifPresent(on -> {
                if (callOnThisPhone(player, held).isPresent()) {
                    CallService.setSpeaker(player, on);
                }
            });
            case SAVE_CONTACT -> saveContact(player, held, packet.first(), packet.second());
            case REMOVE_CONTACT -> removeContact(player, held, packet.first());
            case OPEN_PHONE_APP -> replyWithBook(player, held, null);
        }
    }

    /** L'appel de ce joueur, s'il est porté par le téléphone de la session. */
    private static Optional<Call> callOnThisPhone(ServerPlayer player, PhoneService.Held held) {
        UUID phoneId = held.session().phoneId();
        return CallService.callOf(player)
                .filter(call -> call.partyOf(player.getUUID()).map(Party::phoneId).filter(phoneId::equals).isPresent());
    }

    /** Comme {@link #callOnThisPhone(ServerPlayer, PhoneService.Held)}, et du numéro écrit dans le geste. */
    private static Optional<Call> callOnThisPhone(ServerPlayer player, PhoneService.Held held, String idText) {
        if (!CALL_ID.matcher(idText).matches()) {
            return Optional.empty();
        }
        long id = Long.parseLong(idText);
        return callOnThisPhone(player, held).filter(call -> call.id() == id);
    }

    /** Le numéro d'une urgence qui sonne, écrit dans le geste. */
    private static Optional<Integer> pendingId(String idText) {
        if (!CALL_ID.matcher(idText).matches()) {
            return Optional.empty();
        }
        long id = Long.parseLong(idText);
        return id >= EmergencyService.ID_BASE && id <= Integer.MAX_VALUE ? Optional.of((int) id) : Optional.empty();
    }

    private static Optional<Boolean> onOff(String text) {
        return switch (text) {
            case "on" -> Optional.of(true);
            case "off" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private static Optional<SimCard> line(ServerPlayer player, PhoneService.Held held) {
        return PhoneSims.line(player.server, held.phone());
    }

    private static void saveContact(ServerPlayer player, PhoneService.Held held, String number, String name) {
        Optional<SimCard> line = line(player, held);
        if (line.isEmpty()) {
            replyWithBook(player, held, Component.translatable(CONTACT + "no_sim"));
            return;
        }
        PhoneDirectory.SaveResult r = PhoneDirectory.get(player.server).saveContact(line.get().simId(), number, name);
        Component message = switch (r) {
            case SAVED -> null;
            case BAD_NUMBER -> Component.translatable(CONTACT + "bad_number");
            case BAD_NAME -> Component.translatable(CONTACT + "bad_name");
            // Carte morte ou registre des carnets plein : pour le joueur, le carnet est plein.
            case FULL, DEAD_CARD, DIRECTORY_FULL -> Component.translatable(CONTACT + "full");
        };
        replyWithBook(player, held, message);
    }

    private static void removeContact(ServerPlayer player, PhoneService.Held held, String rawNumber) {
        Optional<SimCard> line = line(player, held);
        Optional<String> number = SimNumbers.parse(rawNumber);
        if (line.isPresent() && number.isPresent()) {
            PhoneDirectory.get(player.server).removeContact(line.get().simId(), number.get());
        }
        replyWithBook(player, held, line.isEmpty() ? Component.translatable(CONTACT + "no_sim") : null);
    }

    /** L'état actuel avec le carnet de la carte logée (vide sans carte active). */
    private static void replyWithBook(ServerPlayer player, PhoneService.Held held, Component message) {
        Optional<SimCard> line = line(player, held);
        PhoneCallStatePacket.Book book = line.map(card -> CallUpdates.book(player.server, card.simId()))
                .orElseGet(() -> new PhoneCallStatePacket.Book(null, null));
        CallUpdates.send(player, CallUpdates.current(player, message, book));
    }
}
