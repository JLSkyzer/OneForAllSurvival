package dev.oneforallsurvival.phone.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.oneforallsurvival.phone.PhoneNetView;
import dev.oneforallsurvival.phone.PhoneStatePacket;
import dev.oneforallsurvival.phone.PhoneStatePacket.CloseReason;
import dev.oneforallsurvival.phone.PhoneStatePacket.Kind;
import dev.oneforallsurvival.phone.PhoneStatePacket.Lock;
import dev.oneforallsurvival.phone.PhoneStatePacket.Result;
import org.junit.jupiter.api.Test;

/**
 * L'issue de l'ouverture (Face ID) arrive avec l'{@code OPEN}, que la page n'est pas encore prête à montrer : un
 * {@code UPDATE} (batterie qui passe de 100 à 99 %) peut le remplacer avant. Elle doit atteindre la page une fois.
 */
class OpeningOutcomeTest {

    private static PhoneStatePacket etat(Kind kind, Lock lock, int batterie, Result last) {
        return new PhoneStatePacket(7, kind, lock, batterie, 1_800_000_000_000L, 3600, "aube", false,
                lock == Lock.UNLOCKED, lock == Lock.UNLOCKED ? "Alex" : "", 5, 0, last, CloseReason.NONE,
                PhoneNetView.NONE, lock == Lock.UNLOCKED);
    }

    @Test
    void leDeverrouillageParLeVisageSurvitAUnUpdateArriveAvantLaPage() {
        PhoneStatePacket ouverture = etat(Kind.OPEN, Lock.UNLOCKED, 100, Result.FACE_UNLOCKED);
        OpeningOutcome issue = new OpeningOutcome(ouverture);
        PhoneStatePacket batterie = etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.NONE);
        assertEquals(etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.FACE_UNLOCKED), issue.toShow(batterie),
                "le dernier état, avec l'issue de l'ouverture");
    }

    @Test
    void leVisageCouvertSurvitAussi() {
        OpeningOutcome issue = new OpeningOutcome(etat(Kind.OPEN, Lock.LOCKED, 100, Result.FACE_NOT_RECOGNIZED));
        assertEquals(Result.FACE_NOT_RECOGNIZED,
                issue.toShow(etat(Kind.UPDATE, Lock.LOCKED, 99, Result.NONE)).last());
    }

    @Test
    void sansUpdateLOuverturePasseTelleQuelle() {
        PhoneStatePacket ouverture = etat(Kind.OPEN, Lock.UNLOCKED, 80, Result.FACE_UNLOCKED);
        assertEquals(ouverture, new OpeningOutcome(ouverture).toShow(ouverture));
    }

    @Test
    void lIssueNAtteintLaPageQuUneFois() {
        PhoneStatePacket ouverture = etat(Kind.OPEN, Lock.UNLOCKED, 100, Result.FACE_UNLOCKED);
        OpeningOutcome issue = new OpeningOutcome(ouverture);
        issue.toShow(ouverture);
        // Page rechargée alors que l'OPEN est toujours le dernier état : l'animation ne se rejoue pas.
        assertEquals(Result.NONE, issue.toShow(ouverture).last(), "l'OPEN rejoué");
        assertEquals(Result.NONE, issue.toShow(etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.NONE)).last());
    }

    @Test
    void uneIssueQuiNeVautPlusNEstPasMontree() {
        OpeningOutcome issue = new OpeningOutcome(etat(Kind.OPEN, Lock.UNLOCKED, 100, Result.FACE_UNLOCKED));
        // Reverrouillé avant que la page soit prête (code changé sur une copie) : plus de déverrouillage à montrer.
        assertEquals(Result.NONE, issue.toShow(etat(Kind.UPDATE, Lock.LOCKED, 99, Result.NONE)).last());
        assertEquals(Result.NONE, issue.toShow(etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.NONE)).last(),
                "perdue pour de bon, jamais rejouée plus tard");
        OpeningOutcome couvert = new OpeningOutcome(etat(Kind.OPEN, Lock.LOCKED, 100, Result.FACE_NOT_RECOGNIZED));
        assertEquals(Result.NONE, couvert.toShow(etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.NONE)).last());
    }

    @Test
    void lesIssuesDesGestesPassentSansChangement() {
        OpeningOutcome ordinaire = new OpeningOutcome(etat(Kind.OPEN, Lock.LOCKED, 100, Result.NONE));
        PhoneStatePacket faux = etat(Kind.UPDATE, Lock.LOCKED, 99, Result.WRONG_CODE);
        assertEquals(faux, ordinaire.toShow(faux));
        assertEquals(faux, ordinaire.toShow(faux), "le client ne retire que les issues de l'ouverture");
        OpeningOutcome visage = new OpeningOutcome(etat(Kind.OPEN, Lock.UNLOCKED, 100, Result.FACE_UNLOCKED));
        PhoneStatePacket interrupteur = etat(Kind.UPDATE, Lock.UNLOCKED, 99, Result.FACE_ID_OFF);
        assertEquals(interrupteur, visage.toShow(interrupteur), "une issue plus récente l'emporte");
    }
}
