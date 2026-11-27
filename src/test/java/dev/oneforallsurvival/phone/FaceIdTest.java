package dev.oneforallsurvival.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.oneforallsurvival.phone.FaceId.Verdict;
import org.junit.jupiter.api.Test;

/**
 * Face ID (retours d'essai du 27/11), la règle pure de l'ouverture : seul le propriétaire, visage découvert, sur un
 * téléphone codé, Face ID actif et hors blocage, est déverrouillé ; visage couvert, « non reconnu » ; tout le reste,
 * rien (et un autre que le propriétaire n'apprend même pas que Face ID est actif).
 */
class FaceIdTest {

    @Test
    void chaqueCombinaison() {
        for (int bits = 0; bits < 32; bits++) {
            boolean enabled = (bits & 1) != 0;
            boolean hasCode = (bits & 2) != 0;
            boolean owner = (bits & 4) != 0;
            boolean uncovered = (bits & 8) != 0;
            boolean lockedOut = (bits & 16) != 0;
            Verdict expected = !(enabled && hasCode && owner && !lockedOut) ? Verdict.NONE
                    : uncovered ? Verdict.UNLOCK : Verdict.NOT_RECOGNIZED;
            assertEquals(expected, FaceId.atOpen(enabled, hasCode, owner, uncovered, lockedOut),
                    "actif=" + enabled + " code=" + hasCode + " propriétaire=" + owner + " découvert=" + uncovered
                            + " bloqué=" + lockedOut);
        }
    }

    @Test
    void lesCasNommes() {
        assertEquals(Verdict.UNLOCK, FaceId.atOpen(true, true, true, true, false));
        assertEquals(Verdict.NOT_RECOGNIZED, FaceId.atOpen(true, true, true, false, false));
        assertEquals(Verdict.NONE, FaceId.atOpen(true, true, false, true, false), "un autre que le propriétaire");
        assertEquals(Verdict.NONE, FaceId.atOpen(true, true, false, false, false),
                "un autre, tête couverte : pas même « non reconnu »");
        assertEquals(Verdict.NONE, FaceId.atOpen(true, true, true, true, true), "pendant une attente");
        assertEquals(Verdict.NONE, FaceId.atOpen(false, true, true, true, false), "coupé");
        assertEquals(Verdict.NONE, FaceId.atOpen(true, false, true, true, false), "téléphone sans code");
    }
}
