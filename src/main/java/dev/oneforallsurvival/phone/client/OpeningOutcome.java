package dev.oneforallsurvival.phone.client;

import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;

/**
 * L'issue de l'ouverture (Face ID : {@code FACE_UNLOCKED}, {@code FACE_NOT_RECOGNIZED}) gardée jusqu'à ce que la page la
 * reçoive, règle pure. Elle arrive avec l'{@code OPEN}, que la page n'est pas encore prête à montrer : un
 * {@code UPDATE} (la batterie qui passe de 100 à 99 %) peut le remplacer avant. Chaque état montré passe par
 * {@link #toShow} : l'issue y est une fois, jamais deux (page rechargée comprise).
 */
final class OpeningOutcome {

    private Result due;

    OpeningOutcome(PhoneStatePacket opening) {
        due = ofOpening(opening.last()) ? opening.last() : Result.NONE;
    }

    /**
     * L'état à montrer maintenant : {@code state}, avec l'issue de l'ouverture si elle n'a pas encore été montrée, que
     * {@code state} n'en porte pas une autre et que le verrou la dit toujours vraie (sinon elle est abandonnée). Une
     * issue d'ouverture déjà montrée est retirée.
     */
    PhoneStatePacket toShow(PhoneStatePacket state) {
        Result opening = due;
        due = Result.NONE;
        Result last = ofOpening(state.last()) ? Result.NONE : state.last();
        if (last == Result.NONE && holds(opening, state.lock())) {
            last = opening;
        }
        return last == state.last() ? state : state.withLast(last);
    }

    private static boolean ofOpening(Result r) {
        return r == Result.FACE_UNLOCKED || r == Result.FACE_NOT_RECOGNIZED;
    }

    private static boolean holds(Result opening, Lock lock) {
        return opening == Result.FACE_UNLOCKED && lock == Lock.UNLOCKED
                || opening == Result.FACE_NOT_RECOGNIZED && lock == Lock.LOCKED;
    }
}
